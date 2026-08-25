package com.wuwaconfig.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.PREFS_NAME
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.adb.PortScanner
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.AdbBackend
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.backend.SafBackend
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.config.DeployHistoryStore
import com.wuwaconfig.app.config.HashSync
import com.wuwaconfig.app.model.DeployRecord
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.model.LogAnalysisStore
import com.wuwaconfig.app.model.LogInfo
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.model.VerificationReport
import com.wuwaconfig.app.service.AdbConnectionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeployHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app: WuWaConfigApp =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("DeployHistoryViewModel requires WuWaConfigApp application")

    val configGenerator get() = app.configGenerator
    val cvarDatabase get() = app.cvarDatabase

    private val ops get() = app.deviceOps

    val configManager: ConfigManager by lazy {
        ConfigManager(getApplication(), { app.backend }, backupStorageDir)
    }

    // Device session state lives in WuWaConfigApp so every ViewModel observes
    // the same truth; these are forwarding shims for existing call sites.
    val backendStatus: StateFlow<BackendStatus> = app.backendStatus
    val isApplying: StateFlow<Boolean> = ops.isApplying
    val operationCancelled: StateFlow<Boolean> = ops.operationCancelled

    private val _deployResult = MutableStateFlow<String?>(null)
    val deployResult: StateFlow<String?> = _deployResult.asStateFlow()

    private val _deployHashSync = MutableStateFlow<String?>(null)
    val deployHashSync: StateFlow<String?> = _deployHashSync.asStateFlow()

    private val _customDeploySuccess = MutableStateFlow<String?>(null)
    val customDeploySuccess: StateFlow<String?> = _customDeploySuccess.asStateFlow()

    fun clearCustomDeploySuccess() {
        _customDeploySuccess.value = null
    }

    private val _logsFeedback = MutableStateFlow<String?>(null)
    val logsFeedback: StateFlow<String?> = _logsFeedback.asStateFlow()

    fun clearLogsFeedback() {
        _logsFeedback.value = null
    }

    private val _verificationReport = MutableStateFlow<VerificationReport?>(null)
    val verificationReport: StateFlow<VerificationReport?> = _verificationReport.asStateFlow()

    /** Deploy-verify progress (log analysis has its own in LogInsightsViewModel). */
    private val _readingProgress = MutableStateFlow(0)
    val readingProgress: StateFlow<Int> = _readingProgress.asStateFlow()

    /** Composition-root hook: invoked after device mutations so backup lists refresh. */
    var onDeviceMutated: (() -> Unit)? = null

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val deployHistoryEnabled: StateFlow<Boolean> = app.deployHistoryEnabled
    val colorfulUi: StateFlow<Boolean> = app.colorfulUi
    val chipsetInfo = app.chipsetInfo
    val gameConfigDir: String = app.gameConfigDir

    private val deployHistoryStore: DeployHistoryStore = app.deployHistoryStore
    private val _deployRecords = MutableStateFlow<List<DeployRecord>>(deployHistoryStore.getAllRecords())
    val deployRecords: StateFlow<List<DeployRecord>> = _deployRecords.asStateFlow()

    fun clearDeployResult() {
        _deployResult.value = null
        _deployHashSync.value = null
    }

    private val defaultBackupDir = application.filesDir.resolve("backups").absolutePath

    val backupStorageDir: String
        get() = prefs.getString("backup_dir", defaultBackupDir) ?: defaultBackupDir

    val isSetupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)

    val termsAccepted: Boolean
        get() = prefs.getBoolean("terms_accepted", false)

    val termsVersionAccepted: Int
        get() = prefs.getInt("terms_version", 0)

    fun switchTo(method: AccessMethod) {
        if (ops.isApplying.value || ops.mutex.isLocked) {
            addLog("Cannot switch backend while an operation is running", LogLevel.WARNING)
            return
        }
        if (app.backendStatusValue.connected) disconnect()
        app.switchTo(method)
        app.setBackendStatus(BackendStatus(method = method))
        addLog("Switched to ${method.name} mode")
    }

    fun connect() {
        if (app.backendStatusValue.connected) {
            addLog("Already connected")
            return
        }
        val method = app.backendStatusValue.method
        app.setBackendStatus(BackendStatus(method = method))
        addLog("Connecting via ${method.name}...")
        ops.launchBackendOp(managesBusyFlag = false) {
            when (method) {
                AccessMethod.SHIZUKU -> {
                    try {
                        if (Shizuku.getVersion() < 0 || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            app.setBackendStatus(BackendStatus(method = method, errorMessage = "Shizuku not running or permission not granted."))
                            addLog("ERROR: Shizuku not available")
                            return@launchBackendOp
                        }
                        addLog("Shizuku is ready!")
                    } catch (_: Exception) {
                        app.setBackendStatus(BackendStatus(method = method, errorMessage = "Shizuku not running. Start Shizuku first."))
                        addLog("ERROR: Shizuku not running")
                        return@launchBackendOp
                    }
                }
                AccessMethod.SAF -> {
                    val saf = app.backend as? SafBackend
                    if (saf == null || saf.treeUri == null) {
                        app.setBackendStatus(BackendStatus(method = method, errorMessage = "No SAF directory selected. Tap Pick Directory to choose the game config folder."))
                        addLog("ERROR: SAF directory not selected")
                        return@launchBackendOp
                    }
                }
                else -> {}
            }

            val backend = app.backend
            val result = backend.connect()
            val ip = if (method == AccessMethod.ADB) withContext(Dispatchers.IO) { PortScanner.getDeviceIp() } else ""
            val port =
                com.wuwaconfig.app.adb.PortScanner.lastAdbPort?.let {
                        p ->
                    if (p > 0) p else app.backendStatusValue.port
                } ?: app.backendStatusValue.port

            if (result.isSuccess) {
                app.setBackendStatus(BackendStatus(method = method, connected = true, host = ip, port = port))
                addLog("Connected via ${method.name}!")
                if (method == AccessMethod.ADB) {
                    try {
                        getApplication<Application>().startForegroundService(Intent(getApplication(), AdbConnectionService::class.java))
                    } catch (e: Exception) {
                        addLog("WARN: failed to start ADB connection service: ${e.message}", LogLevel.WARNING)
                    }
                    val testAccess = backend.fileExists("${com.wuwaconfig.app.model.GamePaths.TARGET_DIR}/Engine.ini")
                    if (testAccess.isSuccess) {
                        addLog(if (testAccess.getOrThrow()) "Game config directory accessible." else "Config files not found (first run?).")
                    } else {
                        addLog("WARNING: ADB cannot access game data directory.")
                        addLog("On Android 13+ this is blocked. Use ROOT, Shizuku, or SAF instead.")
                    }
                }
                onDeviceMutated?.invoke()
                syncConfigHashes()
            } else {
                val message = friendlyBackendError(result.exceptionOrNull()?.message)
                app.setBackendStatus(
                    BackendStatus(
                        method = method,
                        connected = false,
                        host = ip,
                        errorMessage = message,
                    ),
                )
                addLog("ERROR: $message")
            }
        }
    }

    fun saveSafTreeUri(uri: Uri) {
        val backend = app.backend
        if (backend is SafBackend) {
            backend.saveTreeUri(uri)
            addLog("SAF directory set. Connecting...")
            connect()
        }
    }

    fun requestShizukuPermission() {
        try {
            Shizuku.requestPermission(1001)
        } catch (e: Exception) {
            addLog("WARN: Shizuku.requestPermission failed: ${e.message}", LogLevel.WARNING)
        }
    }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001) {
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    addLog("Shizuku permission granted!")
                    connect()
                } else {
                    app.setBackendStatus(app.backendStatusValue.copy(errorMessage = "Shizuku permission denied"))
                    addLog("ERROR: Shizuku permission denied")
                }
            }
        }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            addLog("WARN: Shizuku listener register failed: ${e.message}", LogLevel.WARNING)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            addLog("WARN: Shizuku listener unregister failed: ${e.message}", LogLevel.WARNING)
        }
        try {
            if (app.backend is AdbBackend) {
                getApplication<Application>().stopService(Intent(getApplication(), AdbConnectionService::class.java))
            }
        } catch (e: Exception) {
            addLog("WARN: failed to stop ADB connection service: ${e.message}", LogLevel.WARNING)
        }
        app.backend.disconnect()
    }

    fun connectAdbManual(
        host: String,
        portText: String,
    ) {
        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            app.setBackendStatus(
                BackendStatus(
                    method = AccessMethod.ADB,
                    errorMessage = "Invalid port. Enter a number between 1-65535.",
                ),
            )
            return
        }
        if (app.backendStatusValue.connected) {
            addLog("Already connected")
            return
        }
        ops.launchBackendOp(managesBusyFlag = false) {
            app.setBackendStatus(BackendStatus(method = AccessMethod.ADB))
            addLog("Connecting to $host:$port...")
            val backend = app.backend
            if (backend is AdbBackend) {
                val result = backend.connectTo(host, port)
                if (result.isSuccess) {
                    app.setBackendStatus(BackendStatus(method = AccessMethod.ADB, connected = true, host = host, port = port))
                    addLog("Connected to $host:$port!")
                    try {
                        getApplication<Application>().startForegroundService(Intent(getApplication(), AdbConnectionService::class.java))
                    } catch (e: Exception) {
                        addLog("WARN: failed to start ADB connection service: ${e.message}", LogLevel.WARNING)
                    }
                    onDeviceMutated?.invoke()
                } else {
                    val msg = friendlyBackendError(result.exceptionOrNull()?.message)
                    app.setBackendStatus(BackendStatus(method = AccessMethod.ADB, host = host, errorMessage = msg))
                    addLog("ERROR: $msg")
                }
            }
        }
    }

    private fun friendlyBackendError(message: String?): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("No SAF directory selected", ignoreCase = true) ->
                "Pick a directory with the game config files."
            raw.contains("SAF directory no longer", ignoreCase = true) ->
                "SAF directory access lost. Pick again."
            raw.contains("Shell commands not available in SAF", ignoreCase = true) ->
                "Shell commands not supported on SAF. Use another method for this operation."
            raw.contains("Shizuku is not running", ignoreCase = true) ->
                "Shizuku not running. Start Shizuku app first."
            raw.contains("Shizuku permission", ignoreCase = true) ->
                "Shizuku permission not granted."
            raw.contains("Shizuku not available", ignoreCase = true) ->
                "Shizuku not installed. Install Shizuku from GitHub."
            raw.contains("ECONNREFUSED", ignoreCase = true) ->
                "ADB connection refused. Enable Wireless Debugging and retry."
            raw.contains("timed out", ignoreCase = true) || raw.contains("after 5000ms", ignoreCase = true) ->
                "ADB connection timed out. Check Wireless Debugging."
            raw.contains("ADB port not found", ignoreCase = true) ->
                "ADB not found. Enter IP:port from Developer Options > Wireless Debugging."
            raw.contains("ADB key not trusted", ignoreCase = true) ->
                "ADB key not trusted. First connect from a computer via USB, or use ROOT mode."
            raw.contains("Permission denied", ignoreCase = true) ->
                "Shell can't access game data. Use ROOT mode."
            raw.isBlank() -> "Connection failed"
            else -> raw.take(120)
        }
    }

    fun cancelOperation() {
        ops.requestCancel(
            disconnect = { app.backend.disconnect() },
            resetBackendStatus = { app.setBackendStatus(BackendStatus(method = app.backendStatusValue.method)) },
        )
    }

    fun disconnect() {
        if (ops.isApplying.value || ops.mutex.isLocked) {
            addLog("Cannot disconnect while an operation is running — use Cancel instead", LogLevel.WARNING)
            return
        }
        app.backend.disconnect()
        val method = app.backendStatusValue.method
        if (method == AccessMethod.SAF) {
            val saf = app.backend as? SafBackend
            saf?.clearTreeUri()
        }
        if (method == AccessMethod.ADB) {
            try {
                getApplication<Application>().stopService(Intent(getApplication(), AdbConnectionService::class.java))
            } catch (e: Exception) {
                addLog("WARN: failed to stop ADB connection service: ${e.message}", LogLevel.WARNING)
            }
        }
        app.setBackendStatus(BackendStatus(method = method))
        ops.setApplying(false)
        addLog("Disconnected.")
    }

    fun collectClientLog() {
        if (ops.isApplying.value || !app.backendStatusValue.connected) return
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            try {
                addLog("Collecting Client.log...")
                val result = configManager.collectClientLog { msg -> addLog(msg) }
                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                } else {
                    addLog("FAILED: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: SecurityException) {
                Log.e("WuWaConfig", "collectClientLog permission denied", e)
                addLog("CRASH: permission denied: ${e.message}")
            } catch (e: java.io.IOException) {
                Log.e("WuWaConfig", "collectClientLog I/O error", e)
                addLog("CRASH: I/O error: ${e.message}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "collectClientLog crashed", e)
            } finally {
                ops.setApplying(false)
            }
        }
    }

    fun deleteDeployRecord(id: String) {
        deployHistoryStore.deleteRecord(id)
        _deployRecords.value = deployHistoryStore.getAllRecords()
        addLog("Deleted deploy record")
    }

    fun clearDeployHistory() {
        deployHistoryStore.clear()
        _deployRecords.value = deployHistoryStore.getAllRecords()
        addLog("Cleared all deploy history")
    }

    fun compareDeployOutcome(id: String) {
        ops.launchBackendOp(managesBusyFlag = false) {
            try {
                if (deployHistoryStore.getRecord(id) == null) return@launchBackendOp
                addLog("Pulling Client.log for deploy outcome comparison...")
                val result = configManager.readClientLogContent()
                if (result.isFailure) {
                    addLog("Failed to pull Client.log: ${result.exceptionOrNull()?.message}")
                    return@launchBackendOp
                }
                val logText = result.getOrThrow()
                val parsed = com.wuwaconfig.app.config.LogParser.parseLog(logText)
                deployHistoryStore.updateOutcome(id, parsed, logText.take(2048))
                _deployRecords.value = deployHistoryStore.getAllRecords()
                val comparison = deployHistoryStore.compare(id)
                if (comparison != null) {
                    val lines = mutableListOf<String>()
                    comparison.fpsDelta?.let { lines.add("FPS: ${if (it >= 0) "+" else ""}${"%.1f".format(it)}") }
                    comparison.thermalDelta?.let { lines.add("Thermal: ${if (it <= 0) "-" else "+"}$it") }
                    comparison.oomDelta?.let { lines.add("OOM: ${if (it <= 0) "-" else "+"}$it") }
                    comparison.dropFramesDelta?.let { lines.add("Drops: ${if (it <= 0) "-" else "+"}$it") }
                    addLog("Comparison: ${lines.joinToString(", ")}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "compareDeployOutcome crashed", e)
            }
        }
    }

    fun retuneAndDeploy(recordId: String) {
        ops.launchBackendOp(managesBusyFlag = false) {
            try {
                val record = deployHistoryStore.getRecord(recordId) ?: return@launchBackendOp
                val profile =
                    record.optimizedProfile ?: run {
                        addLog("No tuning profile found in record — can't retune")
                        return@launchBackendOp
                    }
                val comparison =
                    deployHistoryStore.compare(recordId) ?: run {
                        addLog("No comparison data — run Compare Now first")
                        return@launchBackendOp
                    }

                addLog(
                    "Retuning based on comparison Δ: FPS ${comparison.fpsDelta?.let { "%.1f".format(it) } ?: "?"}, " +
                        "Thermal ${comparison.thermalDelta ?: "?"}, OOM ${comparison.oomDelta ?: "?"}, " +
                        "Drops ${comparison.dropFramesDelta ?: "?"}",
                )

                val adjustedProfile = com.wuwaconfig.app.config.CvarOptimizer.adjustProfile(profile, comparison)

                val opts =
                    com.wuwaconfig.app.model.GeneratorOptions(
                        fps = 60,
                        generateEngine = record.filesDeployed.contains("Engine.ini"),
                        generateDeviceProfiles = record.filesDeployed.contains("DeviceProfiles.ini"),
                        generateGameUserSettings = record.filesDeployed.contains("GameUserSettings.ini"),
                        generateScalability = record.filesDeployed.contains("Scalability.ini"),
                        generateHardware = record.filesDeployed.contains("Hardware.ini"),
                        useAdvancedGen = false,
                        optimizeWithCvarDb = true,
                    )
                val profileOverride = com.wuwaconfig.app.config.CvarOptimizer.toPresetProfile(adjustedProfile)
                val generated = configGenerator.generate(record.presetName, opts, profileOverride = profileOverride)
                ops.setApplying(true)
                performDeploy(generated, opts, adjustedProfile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "retuneAndDeploy crashed", e)
            }
        }
    }

    fun deployGeneratedConfigs(
        ini: com.wuwaconfig.app.model.GeneratedIni,
        opts: com.wuwaconfig.app.model.GeneratorOptions = com.wuwaconfig.app.model.GeneratorOptions(),
        retuneProfile: com.wuwaconfig.app.config.CvarOptimizer.OptimizedProfile? = null,
    ): Boolean {
        if (ops.isApplying.value || !app.backendStatusValue.connected) {
            val why = if (!app.backendStatusValue.connected) "not connected" else "another operation is running"
            addLog("Deploy skipped: $why", LogLevel.WARNING)
            return false
        }
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            performDeploy(ini, opts, retuneProfile)
        }
        return true
    }

    /**
     * Deploy pipeline shared by [deployGeneratedConfigs] and [retuneAndDeploy].
     * Callers must already hold [opMutex] (directly or via launchBackendOp).
     */
    private suspend fun performDeploy(
        ini: com.wuwaconfig.app.model.GeneratedIni,
        opts: com.wuwaconfig.app.model.GeneratorOptions,
        retuneProfile: com.wuwaconfig.app.config.CvarOptimizer.OptimizedProfile?,
    ) {
        try {
            _verificationReport.value = null
            addLog("Deploying generated configs...")
            val preSnapshot = configManager.snapshotHashFile().getOrNull()

            val existingResult = configManager.readCurrentConfig("Engine.ini")
            val corePaths =
                if (existingResult.isSuccess) {
                    val extracted = configGenerator.extractCoreSystemPaths(existingResult.getOrThrow())
                    addLog("Found ${extracted.size - 1} [Core.System] paths on device")
                    extracted
                } else {
                    val fromBackup =
                        configManager.getLocalBackups().firstOrNull { backup ->
                            backup.files.any { it.name == "Engine.ini" }
                        }?.files?.firstOrNull { it.name == "Engine.ini" }?.content
                    if (fromBackup != null) {
                        addLog("Device Engine.ini missing, using paths from backup")
                        configGenerator.extractCoreSystemPaths(fromBackup)
                    } else {
                        addLog("Using default [Core.System] paths")
                        configGenerator.DEFAULT_CORE_SYSTEM
                    }
                }

            var lastGeneratedCvars: Set<String> = emptySet()
            var lastActivePreset: String = "balanced"

            val engineWithPaths =
                if (opts.generateEngine) {
                    val sourceEngine =
                        ini.engine.ifBlank {
                            val result =
                                configGenerator.generateWithCorePaths(
                                    lastActivePreset,
                                    opts,
                                    corePaths,
                                )
                            lastGeneratedCvars = result.cvarNames
                            lastActivePreset = result.activePreset
                            result.ini.engine
                        }
                    val replaced = configGenerator.replaceCoreSystemPaths(sourceEngine, corePaths)
                    if (sourceEngine == ini.engine) {
                        lastGeneratedCvars = configGenerator.extractCvarNames(replaced)
                    }
                    replaced
                } else {
                    ""
                }

            val result =
                configManager.applyCustomConfigs(
                    engineIni = if (opts.generateEngine) engineWithPaths else null,
                    deviceProfilesIni = if (opts.generateDeviceProfiles) ini.deviceProfiles else null,
                    gameUserSettingsIni = if (opts.generateGameUserSettings) ini.gameUserSettings else null,
                    scalabilityIni = if (opts.generateScalability && ini.scalability.isNotBlank()) ini.scalability else null,
                    hardwareIni = if (opts.generateHardware && ini.hardware.isNotBlank()) ini.hardware else null,
                ) { msg -> addLog(msg) }
            if (result.isSuccess) {
                addLog("SUCCESS: ${result.getOrThrow()}")
                _deployResult.value = result.getOrThrow()
                configManager.reconcileAfterModify(preSnapshot)
                    .onSuccess {
                        addLog(it)
                        _deployHashSync.value = it
                    }
                    .onFailure { e ->
                        addLog("Hash refresh failed: ${e.message}", LogLevel.ERROR)
                        _deployHashSync.value = "Hash refresh failed: ${e.message}"
                    }
                if (opts.generateEngine) {
                    addLog("Verifying deployed CVars against ConfigMonitor...")
                    _readingProgress.value = 50
                    configManager.verifyDeployedCvars(lastGeneratedCvars).onSuccess { report ->
                        val cvarValues = cvarDatabase.extractCvarValues(engineWithPaths)
                        val details =
                            cvarDatabase.buildCvarDetails(
                                lastGeneratedCvars,
                                cvarValues,
                            )
                        _verificationReport.value = report.copy(cvarDetails = details)
                        _readingProgress.value = 100
                        addLog("VERIFY: ${report.recognizedCount}/${report.totalCount} CVars accepted by engine")
                        if (details.values.count { it.matchesDefault } > 0) {
                            addLog("CVar DB: ${details.values.count { it.matchesDefault }} redundant CVars (match game defaults)")
                        }
                        if (report.rejected.isNotEmpty()) {
                            val sample = report.rejected.take(5).joinToString(", ")
                            addLog("Unrecognized (sample): $sample${if (report.rejected.size > 5) "..." else ""}")
                        }
                    }.onFailure { e ->
                        addLog("Verify skipped: ${e.message}")
                    }
                }
                if (prefs.getBoolean("deploy_history", true)) {
                    val cachedLogInfo = LogAnalysisStore.load(getApplication())?.logInfo
                    val baselinePair: Pair<LogInfo, String> =
                        if (cachedLogInfo != null) {
                            cachedLogInfo to ""
                        } else {
                            addLog("Reading device log for deploy history baseline...")
                            val baselineResult = configManager.readClientLogContent()
                            if (baselineResult.isSuccess) {
                                val text = baselineResult.getOrThrow()
                                com.wuwaconfig.app.config.LogParser.parseLog(text) to text.take(2048)
                            } else {
                                LogInfo() to ""
                            }
                        }
                    val baselineLog = baselinePair.first
                    val baselineSnippet = baselinePair.second
                    val report = _verificationReport.value
                    val fileList = mutableListOf<String>()
                    if (opts.generateEngine) fileList.add("Engine.ini")
                    if (opts.generateDeviceProfiles) fileList.add("DeviceProfiles.ini")
                    if (opts.generateGameUserSettings) fileList.add("GameUserSettings.ini")
                    if (opts.generateScalability) fileList.add("Scalability.ini")
                    if (opts.generateHardware) fileList.add("Hardware.ini")
                    val recordId = java.util.UUID.randomUUID().toString()
                    val record =
                        DeployRecord(
                            id = recordId,
                            timestamp = System.currentTimeMillis(),
                            presetName = lastActivePreset,
                            generationMethod = if (opts.useAdvancedGen) "advanced" else "classic",
                            filesDeployed = fileList,
                            acceptedCount = report?.recognizedCount ?: 0,
                            totalCount = report?.totalCount ?: 0,
                            redundantCount = report?.redundantCount ?: 0,
                            unknownCount = report?.unknownCount ?: 0,
                            monitoredCount = report?.monitoredCount ?: 0,
                            baselineFps = baselineLog.fpsActual,
                            baselineThermal = baselineLog.thermalEvents,
                            baselineOom = baselineLog.gpuOom,
                            baselineDrops = baselineLog.dropFrames,
                            baselineClientLogSnippet = baselineSnippet,
                            optimizedProfile = retuneProfile ?: if (opts.useAdvancedGen) com.wuwaconfig.app.config.CvarOptimizer.optimizeProfile(baselineLog) else null,
                            options = opts,
                        )
                    deployHistoryStore.addRecord(record)
                    _deployRecords.value = deployHistoryStore.getAllRecords()
                    addLog("Deploy record saved (id: ${recordId.take(8)}...)")
                }
                _readingProgress.value = 0
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                addLog("FAILED: $err")
                _deployResult.value = "Failed: $err"
            }
        } catch (e: SecurityException) {
            Log.e("WuWaConfig", "deployGeneratedConfigs permission denied", e)
            addLog("CRASH: permission denied: ${e.message}")
        } catch (e: java.io.IOException) {
            Log.e("WuWaConfig", "deployGeneratedConfigs I/O error", e)
            addLog("CRASH: I/O error: ${e.message}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            addLog("CRASH: ${e.message}")
            Log.e("WuWaConfig", "deployGeneratedConfigs crashed", e)
        } finally {
            ops.setApplying(false)
        }
    }

    fun applyCustomFiles(
        engineIni: String?,
        deviceProfilesIni: String?,
        gameUserSettingsIni: String?,
        scalabilityIni: String? = null,
        hardwareIni: String? = null,
        backupAllInis: Boolean = false,
    ) {
        if (ops.isApplying.value || !app.backendStatusValue.connected) return
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            try {
                val preSnapshot = configManager.snapshotHashFile().getOrNull()
                val fileNames =
                    mapOf(
                        "Engine.ini" to engineIni,
                        "DeviceProfiles.ini" to deviceProfilesIni,
                        "GameUserSettings.ini" to gameUserSettingsIni,
                        "Scalability.ini" to scalabilityIni,
                        "Hardware.ini" to hardwareIni,
                    )
                val selected = fileNames.filterValues { it != null && it.isNotBlank() }.keys
                val skipped = fileNames.filterValues { it == null || it.isBlank() }.keys

                addLog("Applying custom configs...")
                if (skipped.isNotEmpty()) addLog("Skipped (not provided): ${skipped.joinToString(", ")}")

                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                if (prefs.getBoolean("backup_before_apply", true)) {
                    addLog("Backing up current configs...")
                    val backupFiles = if (backupAllInis) null else selected
                    val backupResult = configManager.createBackup("Auto-backup $ts", type = "auto", selectedFiles = backupFiles)
                    if (backupResult.isSuccess) {
                        addLog("Backup saved: ${backupResult.getOrThrow().name}")
                    } else {
                        addLog("(no existing configs to back up)")
                    }
                }

                val result =
                    configManager.applyCustomConfigs(
                        engineIni = engineIni,
                        deviceProfilesIni = deviceProfilesIni,
                        gameUserSettingsIni = gameUserSettingsIni,
                        scalabilityIni = scalabilityIni,
                        hardwareIni = hardwareIni,
                    ) { msg -> addLog(msg) }

                if (result.isSuccess) {
                    addLog("SUCCESS: ${selected.size} file(s) applied (${selected.joinToString(", ")})")
                    _customDeploySuccess.value = "${selected.size} file(s) deployed: ${selected.joinToString(", ")}"
                    onDeviceMutated?.invoke()
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { addLog(it) }
                        .onFailure { e -> addLog("Hash refresh failed: ${e.message}", LogLevel.ERROR) }
                } else {
                    addLog("FAILED: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: SecurityException) {
                Log.e("WuWaConfig", "applyCustomFiles permission denied", e)
                addLog("CRASH: permission denied: ${e.message}")
            } catch (e: java.io.IOException) {
                Log.e("WuWaConfig", "applyCustomFiles I/O error", e)
                addLog("CRASH: I/O error: ${e.message}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "applyCustomFiles crashed", e)
            } finally {
                ops.setApplying(false)
            }
        }
    }

    fun cleanConfigFiles(selectedFiles: Set<String>? = null) {
        if (ops.isApplying.value || !app.backendStatusValue.connected) return
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            try {
                addLog("Cleaning config files...")
                val preSnapshot = configManager.snapshotHashFile().getOrNull()
                val result = configManager.cleanConfigFiles(selectedFiles = selectedFiles) { msg -> addLog(msg) }
                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                    addLog("Config files cleaned", LogLevel.SUCCESS)
                    onDeviceMutated?.invoke()
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { addLog(it) }
                        .onFailure { e -> addLog("Hash refresh failed: ${e.message}", LogLevel.ERROR) }
                } else {
                    addLog(result.exceptionOrNull()?.message ?: "Clean failed")
                }
            } catch (e: SecurityException) {
                Log.e("WuWaConfig", "cleanConfigFiles permission denied", e)
                addLog("CRASH: permission denied: ${e.message}")
            } catch (e: java.io.IOException) {
                Log.e("WuWaConfig", "cleanConfigFiles I/O error", e)
                addLog("CRASH: I/O error: ${e.message}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "cleanConfigFiles crashed", e)
            } finally {
                ops.setApplying(false)
            }
        }
    }

    fun deleteSelectedConfigFiles(selectedFiles: Set<String>) {
        if (ops.isApplying.value || !app.backendStatusValue.connected) return
        ops.setApplying(true)
        ops.launchBackendOp(managesBusyFlag = true) {
            try {
                addLog("Deleting ${selectedFiles.size} config file(s): ${selectedFiles.joinToString(", ")}")
                val preSnapshot = configManager.snapshotHashFile().getOrNull()
                val result = configManager.deleteConfigFiles(selectedFiles)
                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                    addLog("Deleted ${selectedFiles.size} config file(s)", LogLevel.SUCCESS)
                    onDeviceMutated?.invoke()
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { addLog(it) }
                        .onFailure { e -> addLog("Hash refresh failed: ${e.message}", LogLevel.ERROR) }
                } else {
                    addLog(result.exceptionOrNull()?.message ?: "Delete failed")
                }
            } catch (e: SecurityException) {
                Log.e("WuWaConfig", "deleteSelectedConfigFiles permission denied", e)
                addLog("CRASH: permission denied: ${e.message}")
            } catch (e: java.io.IOException) {
                Log.e("WuWaConfig", "deleteSelectedConfigFiles I/O error", e)
                addLog("CRASH: I/O error: ${e.message}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "deleteSelectedConfigFiles crashed", e)
            } finally {
                ops.setApplying(false)
            }
        }
    }

    private val hashSync: HashSync by lazy { HashSync({ app.backend }, configManager) }

    /**
     * Unified device-hash check. [onResult] receives `true` when the hashes were
     * out of sync and a refresh ran (shared contract with the INI editor).
     */
    fun syncConfigHashes(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                onResult(hashSync.syncIfNeeded())
            } catch (e: Exception) {
                addLog("Hash sync: error: ${e.message}", LogLevel.ERROR)
                onResult(false)
            }
        }
    }

    suspend fun executeShellCommand(cmd: String): Result<String> {
        return app.backend.executeShellCommand(cmd)
    }

    suspend fun readUriContent(uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val stream =
                    ctx.contentResolver.openInputStream(uri)
                        ?: return@withContext Result.failure(Exception("Cannot open file"))
                val text = stream.use { java.io.BufferedReader(java.io.InputStreamReader(it)).readText() }
                Result.success(text)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun readUriBytes(uri: Uri): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val stream =
                    ctx.contentResolver.openInputStream(uri)
                        ?: return@withContext Result.failure(Exception("Cannot open file"))
                val bytes = stream.use { it.readBytes() }
                Result.success(bytes)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getFileName(uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val cursor = ctx.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) it.getString(idx) else null
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

    fun addLog(
        message: String,
        level: LogLevel = detectLevel(message),
    ) {
        LogRepository.add(message, level)
    }

    fun clearLogs() {
        LogRepository.clear()
        addLog("Log cleared.", level = LogLevel.INFO)
        _logsFeedback.value = "Logs cleared"
    }

    fun saveLogs() {
        viewModelScope.launch {
            val file = LogRepository.saveSnapshot()
            if (file != null) {
                addLog("Log saved: ${file.absolutePath}", level = LogLevel.SUCCESS)
                _logsFeedback.value = "Log saved"
            } else {
                addLog("No logs to save.", level = LogLevel.WARNING)
            }
        }
    }

    private fun detectLevel(message: String): LogLevel =
        when {
            message.startsWith("SUCCESS:") || message.startsWith("SUCCESS ") -> LogLevel.SUCCESS
            message.startsWith("WARNING:") -> LogLevel.WARNING
            message.startsWith("ERROR:") || message.startsWith("FAILED:") || message.startsWith("CRASH:") -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
}
