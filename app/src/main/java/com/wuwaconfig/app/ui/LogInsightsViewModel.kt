package com.wuwaconfig.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.PREFS_NAME
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.BattleStatsStore
import com.wuwaconfig.app.model.LogAnalysisStore
import com.wuwaconfig.app.model.LogInfo
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.util.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Log analysis, SmartBrain recommendations, and battle stats — extracted from
 * DeployHistoryViewModel. Device work is serialized through the app-scoped
 * [DeviceOps] so it can never interleave with a deploy.
 */
class LogInsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val app: WuWaConfigApp =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("LogInsightsViewModel requires WuWaConfigApp application")

    private val ops = app.deviceOps
    private val cvarDatabase get() = app.cvarDatabase

    val configManager: ConfigManager by lazy {
        ConfigManager(getApplication(), { app.backend }, ::backupStorageDir)
    }

    private fun backupStorageDir(): String {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        return prefs.getString("backup_dir", null)
            ?: getApplication<Application>().getExternalFilesDir("backups")?.absolutePath
            ?: getApplication<Application>().filesDir.resolve("backups").absolutePath
    }

    private val _logAnalysis = MutableStateFlow<LogInfo?>(null)
    val logAnalysis: StateFlow<LogInfo?> = _logAnalysis.asStateFlow()

    private val _brainRecommendation = MutableStateFlow<com.wuwaconfig.app.config.BrainRecommendation?>(null)
    val brainRecommendation: StateFlow<com.wuwaconfig.app.config.BrainRecommendation?> = _brainRecommendation.asStateFlow()

    private val _battleStats = MutableStateFlow<BattleStats?>(null)
    val battleStats: StateFlow<BattleStats?> = _battleStats.asStateFlow()

    private val _battleStatsSummary = MutableStateFlow<com.wuwaconfig.app.model.BattleStatsSummary?>(null)
    val battleStatsSummary: StateFlow<com.wuwaconfig.app.model.BattleStatsSummary?> = _battleStatsSummary.asStateFlow()

    private val _battleStatsFromCache = MutableStateFlow(false)
    val battleStatsFromCache: StateFlow<Boolean> = _battleStatsFromCache.asStateFlow()

    /**
     * Whether the currently displayed analysis came from the 24h cache rather
     * than a read of the device.
     *
     * The cache exists so the ConfigGen panel is not empty on entry, but a
     * cached LogInfo is up to 24h stale: it describes a previous session's
     * hardware and settings. Without this flag the panel renders it as current
     * ("Loaded — GPU • RAM") and the generator merges its CVars into a fresh
     * Engine.ini, so a day-old log silently shapes today's config.
     */
    private val _analysisFromCache = MutableStateFlow(false)
    val analysisFromCache: StateFlow<Boolean> = _analysisFromCache.asStateFlow()

    private val _analysisAgeHours = MutableStateFlow<Long?>(null)

    /** Age of the cached analysis in whole hours, or null when not from cache. */
    val analysisAgeHours: StateFlow<Long?> = _analysisAgeHours.asStateFlow()

    /**
     * Failure message for the last analysis attempt, or null. Unlike the
     * app-wide [LogRepository] terminal, which a screen-level panel cannot
     * read, this surfaces the failure on the screen that performed the work —
     * previously a failed read/decode silently reverted the panel to neutral
     * copy while erasing the previously loaded analysis with no error shown.
     */
    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    fun clearAnalysisError() {
        _analysisError.value = null
    }

    private val _battleStatsLoading = MutableStateFlow(false)
    val battleStatsLoading: StateFlow<Boolean> = _battleStatsLoading.asStateFlow()

    private val _battleStatsProgress = MutableStateFlow(0)
    val battleStatsProgress: StateFlow<Int> = _battleStatsProgress.asStateFlow()

    /** Progress for the log-pull pipeline; deploy verification uses its own. */
    private val _readingProgress = MutableStateFlow(0)
    val readingProgress: StateFlow<Int> = _readingProgress.asStateFlow()

    /** Shared device connection state (owned by WuWaConfigApp, updated by the deploy VM). */
    val backendStatus: StateFlow<com.wuwaconfig.app.backend.BackendStatus> = app.backendStatus

    private val connected: Boolean get() = app.backendStatusValue.connected

    private fun addLog(
        message: String,
        level: LogLevel = LogLevel.INFO,
    ) {
        LogRepository.add(message, level)
    }

    fun analyzeClientLog(allowRestrictedCvars: Boolean = true) {
        if (_battleStatsLoading.value || !connected) return
        // A fresh read supersedes the cache; leaving the flag set would badge
        // new data as stale and, worse, keep the generator from trusting it.
        _analysisFromCache.value = false
        _analysisAgeHours.value = null
        _analysisError.value = null
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            // The states are nulled here on purpose: the ConfigGen brain
            // overlay detects a READ failure as "info stayed null while the
            // op ended". But a failure must not erase what the user already
            // had — remember what was on screen and put it back once the op
            // settles with failure.
            val previousAnalysis = _logAnalysis.value
            val previousBrain = _brainRecommendation.value
            val restorePrevious: () -> Unit = {
                _logAnalysis.value = previousAnalysis
                _brainRecommendation.value = previousBrain
                _analysisFromCache.value = previousAnalysis != null
                _analysisAgeHours.value = null
            }
            _logAnalysis.value = null
            _brainRecommendation.value = null
            try {
                _readingProgress.value = 0
                addLog("Pulling full Client.log from device...")
                _readingProgress.value = 10
                // The game rotates Client.log at ~20 MB, so a single-file read sees only
                // the tail of the newest session. This merged every recent log instead.
                val result = configManager.readMergedClientLog()
                if (result.isSuccess) {
                    _readingProgress.value = 60
                    val (text, report) = result.getOrThrow()
                    addLog("Encrypted log detected; decrypted successfully.")
                    addLog("Read ${report.used.size} log file(s), ${formatBytes(report.bytesRead)}, newest first:")
                    report.used.take(6).forEach { addLog("  + $it") }
                    if (report.skipped.isNotEmpty()) {
                        addLog("Skipped ${report.skipped.size}: ${report.skipped.take(3).joinToString("; ")}", LogLevel.WARNING)
                    }

                    _readingProgress.value = 75
                    val analysisText = text
                    _readingProgress.value = 95
                    doAnalyzeLogText(analysisText, allowRestrictedCvars, null)
                } else {
                    val reason = result.exceptionOrNull()?.message ?: "unknown error"
                    addLog("FAILED: $reason")
                    _analysisError.value = "Could not read client.log from device: $reason"
                    restorePrevious()
                }
            } catch (e: SecurityException) {
                Log.e("LogInsights", "analyzeClientLog permission denied", e)
                addLog("CRASH: permission denied: ${e.message}")
                _analysisError.value = "client.log read denied: ${e.message}"
                restorePrevious()
            } catch (e: java.io.IOException) {
                Log.e("LogInsights", "analyzeClientLog I/O error", e)
                addLog("CRASH: I/O error: ${e.message}")
                _analysisError.value = "client.log read failed: ${e.message}"
                restorePrevious()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("LogInsights", "analyzeClientLog crashed", e)
                _analysisError.value = "Analysis failed: ${e.message}"
                restorePrevious()
            } finally {
                _readingProgress.value = 0
                ops.setApplying(false)
            }
        }
    }

    fun analyzeClientLogBytes(
        bytes: ByteArray,
        allowRestrictedCvars: Boolean = true,
    ) {
        // Imported log text is fresh even though it did not come from the device.
        _analysisFromCache.value = false
        _analysisAgeHours.value = null
        _analysisError.value = null
        if (ops.isApplying.value) return
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            // Same failure contract as analyzeClientLog: a failed import keeps
            // the previously displayed analysis instead of blanking the panel.
            val previousAnalysis = _logAnalysis.value
            val previousBrain = _brainRecommendation.value
            val restorePrevious: () -> Unit = {
                _logAnalysis.value = previousAnalysis
                _brainRecommendation.value = previousBrain
                _analysisFromCache.value = previousAnalysis != null
                _analysisAgeHours.value = null
            }
            try {
                _readingProgress.value = 0
                addLog("Decoding imported log...")
                val (text, decrypted) = com.wuwaconfig.app.config.LogParser.decodeLogBytes(bytes)
                addLog(if (decrypted == com.wuwaconfig.app.config.LogParser.DecodeResult.DECRYPTED) "Encrypted imported log decrypted successfully." else "Imported plain log.")
                _readingProgress.value = 95
                doAnalyzeLogText(text, allowRestrictedCvars)
            } catch (e: java.io.IOException) {
                Log.e("LogInsights", "analyzeClientLogBytes I/O error", e)
                addLog("CRASH: I/O error: ${e.message}")
                _analysisError.value = "Import failed: ${e.message}"
                restorePrevious()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("LogInsights", "analyzeClientLogBytes crashed", e)
                _analysisError.value = "Import failed: ${e.message}"
                restorePrevious()
            } finally {
                _readingProgress.value = 0
                ops.setApplying(false)
            }
        }
    }

    private suspend fun doAnalyzeLogText(
        text: String,
        allowRestrictedCvars: Boolean = true,
        preParsedLogInfo: LogInfo? = null,
    ) {
        // The display states are NOT nulled here any more: overwriting them
        // directly on success is enough, and nulling first wiped the panel
        // whenever anything below threw (the parse itself, scoring, storage) —
        // the caller's catch now owns failure handling.
        addLog("Parsing log...")
        val info =
            preParsedLogInfo
                ?: withContext(Dispatchers.Default) {
                    com.wuwaconfig.app.config.LogParser.parseLog(text)
                }
        _logAnalysis.value = info
        addLog("GPU: ${info.gpu ?: "unknown"}, RAM: ${info.ramMb ?: "?"}MB")
        _readingProgress.value = 98
        val brain =
            withContext(Dispatchers.Default) {
                com.wuwaconfig.app.config.SmartBrain.scoreRecommendation(info, cvarDatabase, allowRestrictedCvars)
            }
        _brainRecommendation.value = brain
        addLog("Brain recommends: ${brain.preset} (score: ${brain.score})")

        // Caching the result is best-effort: a storage failure must not take
        // down an analysis the user has on screen (it was swallowed before, but
        // as part of the parse try-block — which also swallowed genuine parse
        // failures, blanking the panel with no error).
        runCatching {
            withContext(Dispatchers.IO) {
                val summary = com.wuwaconfig.app.config.LogParser.parseBattleStatsSummary(text)
                BattleStatsStore.save(getApplication(), summary.total, summary)
                LogAnalysisStore.save(getApplication(), info, brain)
                val report =
                    com.wuwaconfig.app.config.SmartBrain.buildReportText(info, brain, cvarDatabase)
                LogRepository.saveSmartBrainReport(report)
                _battleStatsSummary.value = summary
            }
        }.onFailure { e ->
            if (e is CancellationException) throw e
            Log.e("LogInsights", "analysis caching failed", e)
            addLog("Analysis caching failed: ${e.message}", LogLevel.WARNING)
        }
        addLog("Analysis cached for quick viewing")
    }

    fun restoreAnalysisFromCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = LogAnalysisStore.load(getApplication())
            if (cached != null) {
                _logAnalysis.value = cached.logInfo
                _brainRecommendation.value = cached.brainRecommendation
                _analysisFromCache.value = true
                _analysisAgeHours.value =
                    (System.currentTimeMillis() - cached.timestamp) / (60L * 60L * 1000L)
            }
        }
    }

    suspend fun loadBattleStatsFromCache(): Boolean =
        withContext(Dispatchers.IO) {
            val cached = BattleStatsStore.load(getApplication())
            val summary = BattleStatsStore.loadSummary(getApplication())
            if (cached != null) {
                _battleStats.value = cached
                _battleStatsSummary.value = summary
                _battleStatsFromCache.value = true
                true
            } else {
                false
            }
        }

    fun refreshBattleStats() {
        viewModelScope.launch(Dispatchers.IO) { BattleStatsStore.clear(getApplication()) }
        _battleStats.value = null
        _battleStatsFromCache.value = false
        loadBattleStats()
    }

    fun loadBattleStats() {
        if (_battleStatsLoading.value || !connected || ops.isApplying.value) return
        // Same shape as analyzeClientLogBytes: raise the global busy flag up
        // front so Deploy/Clean/Backup cannot be started mid-read.
        ops.setApplying(true)
        _battleStats.value = null
        _battleStatsLoading.value = true
        _battleStatsProgress.value = 0
        val job =
            ops.launchBackendOp(managesBusyFlag = true) {
                addLog("Reading Client.log for battle stats...")
                try {
                    val result = configManager.readBattleStatsSummary(onProgress = { _battleStatsProgress.value = it })
                    if (result.isSuccess) {
                        val summary = result.getOrThrow()
                        _battleStats.value = summary.total
                        _battleStatsSummary.value = summary
                        BattleStatsStore.save(getApplication(), summary.total, summary)
                        addLog("Battle stats loaded")
                    } else {
                        addLog("FAILED: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: SecurityException) {
                    Log.e("LogInsights", "loadBattleStats permission denied", e)
                    addLog("CRASH: permission denied: ${e.message}")
                } catch (e: java.io.IOException) {
                    Log.e("LogInsights", "loadBattleStats I/O error", e)
                    addLog("CRASH: I/O error: ${e.message}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    addLog("CRASH: ${e.message}")
                    Log.e("LogInsights", "loadBattleStats crashed", e)
                }
            }
        // Toggles live out here (not inside the DeviceOps coroutine) so the
        // busy-path early return cannot clobber them — invokeOnCompletion runs
        // for the dropped job too, which is exactly when the reset is needed.
        job.invokeOnCompletion {
            _battleStatsProgress.value = 0
            _battleStatsLoading.value = false
            ops.setApplying(false)
        }
    }

    fun cancelBattleStats() {
        ops.requestCancel(
            disconnect = { app.backend.disconnect() },
            resetBackendStatus = { app.setBackendStatus(com.wuwaconfig.app.backend.BackendStatus(method = app.backendStatusValue.method)) },
        )
    }
}
