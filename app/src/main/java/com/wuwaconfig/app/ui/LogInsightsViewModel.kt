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
        ConfigManager(getApplication(), { app.backend }, backupStorageDir())
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

    private val _battleStatsFromCache = MutableStateFlow(false)
    val battleStatsFromCache: StateFlow<Boolean> = _battleStatsFromCache.asStateFlow()

    private val _battleStatsLoading = MutableStateFlow(false)
    val battleStatsLoading: StateFlow<Boolean> = _battleStatsLoading.asStateFlow()

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
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            _logAnalysis.value = null
            _brainRecommendation.value = null
            try {
                _readingProgress.value = 0
                addLog("Pulling full Client.log from device...")
                _readingProgress.value = 10
                val result = configManager.readFullClientLogWithMetadata()
                if (result.isSuccess) {
                    _readingProgress.value = 60
                    val (text, decrypted) = result.getOrThrow()
                    addLog(
                        if (decrypted == com.wuwaconfig.app.config.LogParser.DecodeResult.DECRYPTED) "Encrypted log detected; decrypted successfully." else "Plain log detected.",
                    )

                    _readingProgress.value = 75
                    val initialInfo =
                        withContext(Dispatchers.Default) { com.wuwaconfig.app.config.LogParser.parseLog(text) }
                    val analysisText =
                        if (initialInfo.gpu == null && initialInfo.deviceModel == null && initialInfo.cpuName == null && initialInfo.ramMb == null) {
                            addLog("No device data in current log, checking backup logs...")
                            _readingProgress.value = 80
                            val backupResult = configManager.readFullLatestBackupLog()
                            if (backupResult.isSuccess) {
                                val (backupText, _) = backupResult.getOrThrow()
                                addLog("Merging backup log with current log for complete analysis")
                                "$backupText\n$text"
                            } else {
                                addLog("Backup log not available: ${backupResult.exceptionOrNull()?.message}", LogLevel.WARNING)
                                text
                            }
                        } else {
                            text
                        }
                    _readingProgress.value = 95
                    doAnalyzeLogText(analysisText, allowRestrictedCvars, if (analysisText == text) initialInfo else null)
                } else {
                    addLog("FAILED: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: SecurityException) {
                Log.e("LogInsights", "analyzeClientLog permission denied", e)
                addLog("CRASH: permission denied: ${e.message}")
            } catch (e: java.io.IOException) {
                Log.e("LogInsights", "analyzeClientLog I/O error", e)
                addLog("CRASH: I/O error: ${e.message}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("LogInsights", "analyzeClientLog crashed", e)
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
        if (ops.isApplying.value) return
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("LogInsights", "analyzeClientLogBytes crashed", e)
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
        _logAnalysis.value = null
        _brainRecommendation.value = null
        try {
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

            withContext(Dispatchers.IO) {
                val battleStats = com.wuwaconfig.app.config.LogParser.parseBattleStats(text)
                BattleStatsStore.save(getApplication(), battleStats)
                LogAnalysisStore.save(getApplication(), info, brain)
                val report =
                    com.wuwaconfig.app.config.SmartBrain.buildReportText(info, brain, cvarDatabase)
                LogRepository.saveSmartBrainReport(report)
            }
            addLog("Analysis cached for quick viewing")
        } catch (e: java.io.IOException) {
            Log.e("LogInsights", "doAnalyzeLogText I/O error", e)
            addLog("CRASH: I/O error: ${e.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            addLog("CRASH: ${e.message}")
            Log.e("LogInsights", "doAnalyzeLogText crashed", e)
        }
    }

    fun restoreAnalysisFromCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = LogAnalysisStore.load(getApplication())
            if (cached != null) {
                _logAnalysis.value = cached.logInfo
                _brainRecommendation.value = cached.brainRecommendation
            }
        }
    }

    suspend fun loadBattleStatsFromCache(): Boolean =
        withContext(Dispatchers.IO) {
            val cached = BattleStatsStore.load(getApplication())
            if (cached != null) {
                _battleStats.value = cached
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
        if (_battleStatsLoading.value || !connected) return
        ops.launchBackendOp(managesBusyFlag = false) {
            _battleStats.value = null
            _battleStatsLoading.value = true
            addLog("Reading Client.log for battle stats...")
            try {
                val result = configManager.readBattleStats()
                if (result.isSuccess) {
                    _battleStats.value = result.getOrThrow()
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
            } finally {
                _battleStatsLoading.value = false
            }
        }
    }
}
