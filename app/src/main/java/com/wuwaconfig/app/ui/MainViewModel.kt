package com.wuwaconfig.app.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.adb.PortScanner
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.AdbBackend
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.backend.SafBackend
import com.wuwaconfig.app.config.ChipsetDetector
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.config.DeployHistoryStore
import com.wuwaconfig.app.config.GachaApi
import com.wuwaconfig.app.config.GachaHistoryStore
import com.wuwaconfig.app.config.ProfileStore
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.BattleStatsStore
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.DeployRecord
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaHistoryEntry
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogAnalysisStore
import com.wuwaconfig.app.model.LogInfo
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.service.AdbConnectionService
import com.wuwaconfig.app.service.GachaPollService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app: WuWaConfigApp =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("MainViewModel requires WuWaConfigApp application")
    private val chipsetDetector = ChipsetDetector

    val configGenerator get() = app.configGenerator
    val cvarDatabase get() = app.cvarDatabase

    private var _configManager: ConfigManager? = null
    private val configManager: ConfigManager get() =
        synchronized(this) {
            _configManager ?: ConfigManager(getApplication(), app.backend, backupStorageDir).also { _configManager = it }
        }

    private val _backendStatus = MutableStateFlow(BackendStatus())
    val backendStatus: StateFlow<BackendStatus> = _backendStatus.asStateFlow()

    private val _backups = MutableStateFlow<List<ConfigBackup>>(emptyList())
    val backups: StateFlow<List<ConfigBackup>> = _backups.asStateFlow()

    private val _backupFeedback = MutableStateFlow<String?>(null)
    val backupFeedback: StateFlow<String?> = _backupFeedback.asStateFlow()

    fun clearBackupFeedback() {
        _backupFeedback.value = null
    }

    private val _deployResult = MutableStateFlow<String?>(null)
    val deployResult: StateFlow<String?> = _deployResult.asStateFlow()

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

    private val _verificationReport = MutableStateFlow<com.wuwaconfig.app.model.VerificationReport?>(null)
    val verificationReport: StateFlow<com.wuwaconfig.app.model.VerificationReport?> = _verificationReport.asStateFlow()

    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying.asStateFlow()

    private val _readingProgress = MutableStateFlow(0)
    val readingProgress: StateFlow<Int> = _readingProgress.asStateFlow()

    private val prefs = application.getSharedPreferences("wuwaconfig", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _deployRecords = MutableStateFlow<List<DeployRecord>>(DeployHistoryStore.getAllRecords())
    val deployRecords: StateFlow<List<DeployRecord>> = _deployRecords.asStateFlow()

    private val _deployHistoryEnabled = MutableStateFlow(prefs.getBoolean("deploy_history", true))
    val deployHistoryEnabled: StateFlow<Boolean> = _deployHistoryEnabled.asStateFlow()

    fun clearDeployResult() {
        _deployResult.value = null
    }

    private val _editingFileName = MutableStateFlow<String?>(null)
    val editingFileName: StateFlow<String?> = _editingFileName.asStateFlow()
    private val _iniEditorContent = MutableStateFlow<String?>(null)
    val iniEditorContent: StateFlow<String?> = _iniEditorContent.asStateFlow()
    private val _iniEditorLoading = MutableStateFlow(false)
    val iniEditorLoading: StateFlow<Boolean> = _iniEditorLoading.asStateFlow()
    private val _iniEditorError = MutableStateFlow<String?>(null)
    val iniEditorError: StateFlow<String?> = _iniEditorError.asStateFlow()
    private val _iniEditorSuccess = MutableStateFlow<String?>(null)
    val iniEditorSuccess: StateFlow<String?> = _iniEditorSuccess.asStateFlow()

    fun clearIniEditorError() {
        _iniEditorError.value = null
    }

    fun clearIniEditorSuccess() {
        _iniEditorSuccess.value = null
    }

    fun readIniFile(fileName: String) {
        viewModelScope.launch {
            _iniEditorLoading.value = true
            _iniEditorError.value = null
            addLog("INI Editor: reading $fileName from device")
            configManager.readCurrentConfig(fileName).onSuccess { content ->
                _editingFileName.value = fileName
                _iniEditorContent.value = content
                addLog("INI Editor: $fileName loaded (${content.length} chars)", LogLevel.SUCCESS)
            }.onFailure { e ->
                _iniEditorContent.value = null
                _iniEditorError.value = "Failed to read $fileName: ${e.message}"
                addLog("INI Editor: failed to read $fileName: ${e.message}", LogLevel.ERROR)
            }
            _iniEditorLoading.value = false
        }
    }

    fun returnToFileList() {
        _editingFileName.value = null
        _iniEditorContent.value = null
        _iniEditorError.value = null
    }

    fun saveIniFile(content: String) {
        val fileName = _editingFileName.value ?: return
        viewModelScope.launch {
            _iniEditorLoading.value = true
            _iniEditorError.value = null
            addLog("INI Editor: saving $fileName to device")
            val preSnapshot = configManager.snapshotHashFile().getOrNull()
            configManager.pushSingleFile(fileName, content) {}
                .onSuccess {
                    addLog("INI Editor: $fileName pushed, refreshing hashes...", LogLevel.SUCCESS)
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { hashMsg ->
                        addLog("$fileName saved. $hashMsg", LogLevel.SUCCESS)
                        _iniEditorSuccess.value = "$fileName saved successfully"
                    }.onFailure { e ->
                        addLog("INI Editor: hash refresh warning: ${e.message}", LogLevel.WARNING)
                        _iniEditorSuccess.value = "$fileName saved (hash refresh: ${e.message})"
                    }
                }.onFailure { e ->
                    _iniEditorError.value = "Failed to save $fileName: ${e.message}"
                    addLog("INI Editor: failed to save $fileName: ${e.message}", LogLevel.ERROR)
                }
            _iniEditorLoading.value = false
        }
    }

    private val _gachaHistory = MutableStateFlow<GachaHistoryEntry?>(null)
    val gachaHistory: StateFlow<GachaHistoryEntry?> = _gachaHistory.asStateFlow()

    private val _playerProfile = MutableStateFlow<com.wuwaconfig.app.model.PlayerProfile?>(null)
    val playerProfile: StateFlow<com.wuwaconfig.app.model.PlayerProfile?> = _playerProfile.asStateFlow()

    private val _configModifyCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val configModifyCounts: StateFlow<Map<String, Int>> = _configModifyCounts.asStateFlow()

    val chipsetInfo = chipsetDetector.detect()
    val gameConfigDir = com.wuwaconfig.app.model.GamePaths.TARGET_DIR

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    init {
        val savedUri = prefs.getString("bg_image_uri", null)
        app.backgroundImageUri.value = savedUri
        val savedVideoUri = prefs.getString("bg_video_uri", null)
        app.backgroundVideoUri.value = savedVideoUri
        app.backgroundOpacity.value = prefs.getFloat("bg_opacity", 0.25f)
    }

    val backgroundImageUri: StateFlow<String?> = app.backgroundImageUri
    val backgroundVideoUri: StateFlow<String?> = app.backgroundVideoUri
    val backgroundOpacity: StateFlow<Float> = app.backgroundOpacity

    fun setBackgroundImageUri(uri: String?) {
        if (uri != null) {
            prefs.edit().putString("bg_image_uri", uri).apply()
        } else {
            prefs.edit().remove("bg_image_uri").apply()
        }
        app.backgroundImageUri.value = uri
    }

    fun setBackgroundVideoUri(uri: String?) {
        if (uri != null) {
            prefs.edit().putString("bg_video_uri", uri).apply()
        } else {
            prefs.edit().remove("bg_video_uri").apply()
        }
        app.backgroundVideoUri.value = uri
    }

    fun setBackgroundOpacity(opacity: Float) {
        prefs.edit().putFloat("bg_opacity", opacity).apply()
        app.backgroundOpacity.value = opacity
    }

    fun setDeployHistoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("deploy_history", enabled).apply()
        _deployHistoryEnabled.value = enabled
    }

    fun deleteDeployRecord(id: String) {
        DeployHistoryStore.deleteRecord(id)
        _deployRecords.value = DeployHistoryStore.getAllRecords()
        addLog("Deleted deploy record")
    }

    fun clearDeployHistory() {
        DeployHistoryStore.clear()
        _deployRecords.value = DeployHistoryStore.getAllRecords()
        addLog("Cleared all deploy history")
    }

    fun compareDeployOutcome(id: String) {
        viewModelScope.launch {
            if (DeployHistoryStore.getRecord(id) == null) return@launch
            addLog("Pulling Client.log for deploy outcome comparison...")
            val result = configManager.readClientLogContent()
            if (result.isFailure) {
                addLog("Failed to pull Client.log: ${result.exceptionOrNull()?.message}")
                return@launch
            }
            val logText = result.getOrThrow()
            val parsed = com.wuwaconfig.app.config.LogParser.parseLog(logText)
            DeployHistoryStore.updateOutcome(id, parsed, logText.take(2048))
            _deployRecords.value = DeployHistoryStore.getAllRecords()
            val comparison = DeployHistoryStore.compare(id)
            if (comparison != null) {
                val lines = mutableListOf<String>()
                comparison.fpsDelta?.let { lines.add("FPS: ${if (it >= 0) "+" else ""}${"%.1f".format(it)}") }
                comparison.thermalDelta?.let { lines.add("Thermal: ${if (it <= 0) "-" else "+"}$it") }
                comparison.oomDelta?.let { lines.add("OOM: ${if (it <= 0) "-" else "+"}$it") }
                comparison.dropFramesDelta?.let { lines.add("Drops: ${if (it <= 0) "-" else "+"}$it") }
                addLog("Comparison: ${lines.joinToString(", ")}")
            }
        }
    }

    fun retuneAndDeploy(recordId: String) {
        viewModelScope.launch {
            val record = DeployHistoryStore.getRecord(recordId) ?: return@launch
            val profile =
                record.optimizedProfile ?: run {
                    addLog("No tuning profile found in record — can't retune")
                    return@launch
                }
            val comparison =
                DeployHistoryStore.compare(recordId) ?: run {
                    addLog("No comparison data — run Compare Now first")
                    return@launch
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
            deployGeneratedConfigs(generated, opts, adjustedProfile)
        }
    }

    private val defaultBackupDir = application.filesDir.resolve("backups").absolutePath

    val backupStorageDir: String
        get() = prefs.getString("backup_dir", defaultBackupDir) ?: defaultBackupDir

    val isSetupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)

    companion object {
        private const val TERMS_VERSION = 1
    }

    val termsAccepted: Boolean
        get() = prefs.getBoolean("terms_accepted", false)

    val termsVersionAccepted: Int
        get() = prefs.getInt("terms_version", 0)

    fun needsTermsAccept(): Boolean = !termsAccepted || termsVersionAccepted < TERMS_VERSION

    fun acceptTerms() {
        prefs.edit().putBoolean("terms_accepted", true).putInt("terms_version", TERMS_VERSION).apply()
    }

    fun postAcceptInit() {
        loadBackups()
        try {
            LocalBroadcastManager.getInstance(getApplication()).registerReceiver(
                gachaReceiver,
                IntentFilter("com.wuwaconfig.app.GACHA_DATA_READY"),
            )
        } catch (_: Exception) {
        }
        _gachaHistory.value = GachaHistoryStore.load(getApplication())
        val cached = ProfileStore.load(getApplication())
        if (cached != null) {
            _playerProfile.value = cached
            addLog("Cached profile loaded")
        }
    }

    private val gachaReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val json = intent.getStringExtra("json")
                if (json != null) {
                    try {
                        val type = object : TypeToken<GachaData>() {}.type
                        val data = Gson().fromJson<GachaData>(json, type)
                        _gachaData.value = data
                        _conveneUrl.value = "found"
                        GachaHistoryStore.save(getApplication(), data)
                        _gachaHistory.value = GachaHistoryStore.load(getApplication())
                        addLog("Background poll: loaded ${data.totalPulls} pulls (${data.fiveStars}★5)")
                    } catch (_: Exception) {
                    }
                }
            }
        }

    init {
        if (prefs.getBoolean("terms_accepted", false)) {
            postAcceptInit()
        }
    }

    fun startBackgroundPoll() {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(Intent(ctx, GachaPollService::class.java))
        addLog("Background polling started (notification active)")
    }

    fun clearGachaHistory() {
        GachaHistoryStore.delete(getApplication())
        _gachaHistory.value = null
        addLog("Gacha history cleared")
    }

    fun gachaHistoryRemainingHours(): Long = GachaHistoryStore.getRemainingHours(getApplication())

    fun restoreGachaFromHistory() {
        val entry = _gachaHistory.value ?: return
        try {
            val type = object : TypeToken<GachaData>() {}.type
            val data = Gson().fromJson<GachaData>(entry.fullDataJson, type)
            _gachaData.value = data
            addLog("Restored history: ${data.totalPulls} pulls")
        } catch (e: Exception) {
            addLog("Failed to restore history: ${e.message}")
        }
    }

    fun finishSetup(backupDir: String) {
        prefs.edit().putBoolean("setup_done", true).putString("backup_dir", backupDir).apply()
    }

    fun changeBackupDir(newDir: String) {
        prefs.edit().putString("backup_dir", newDir).apply()
        _configManager = null
        loadBackups()
        addLog("Backup dir changed to $newDir")
    }

    fun initDownloadBackupDir() {
        if (prefs.getBoolean("setup_done", false) && prefs.contains("backup_dir")) return
        val targetDir = getApplication<Application>().getExternalFilesDir("backups")
        if (targetDir != null) {
            targetDir.mkdirs()
            changeBackupDir(targetDir.absolutePath)
        }
    }

    fun switchTo(method: AccessMethod) {
        if (_backendStatus.value.connected) disconnect()
        app.switchTo(method)
        _configManager = null
        _backendStatus.value = BackendStatus(method = method)
        addLog("Switched to ${method.name} mode")
    }

    fun connect() {
        viewModelScope.launch {
            val method = _backendStatus.value.method
            _backendStatus.value = BackendStatus(method = method)
            addLog("Connecting via ${method.name}...")

            when (method) {
                AccessMethod.SHIZUKU -> {
                    try {
                        if (Shizuku.getVersion() < 0 || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            _backendStatus.value = BackendStatus(method = method, errorMessage = "Shizuku not running or permission not granted.")
                            addLog("ERROR: Shizuku not available")
                            return@launch
                        }
                        addLog("Shizuku is ready!")
                    } catch (_: Exception) {
                        _backendStatus.value = BackendStatus(method = method, errorMessage = "Shizuku not running. Start Shizuku first.")
                        addLog("ERROR: Shizuku not running")
                        return@launch
                    }
                }
                AccessMethod.SAF -> {
                    val saf = app.backend as? SafBackend
                    if (saf == null || saf.treeUri == null) {
                        _backendStatus.value = BackendStatus(method = method, errorMessage = "No SAF directory selected. Tap Pick Directory to choose the game config folder.")
                        addLog("ERROR: SAF directory not selected")
                        return@launch
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
                    if (p > 0) p else _backendStatus.value.port
                } ?: _backendStatus.value.port

            if (result.isSuccess) {
                _backendStatus.value = BackendStatus(method = method, connected = true, host = ip, port = port)
                addLog("Connected via ${method.name}!")
                if (method == AccessMethod.ADB) {
                    try {
                        getApplication<Application>().startForegroundService(Intent(getApplication(), AdbConnectionService::class.java))
                    } catch (_: Exception) {
                    }
                    val testAccess = backend.fileExists("$gameConfigDir/Engine.ini")
                    if (testAccess.isSuccess) {
                        addLog(if (testAccess.getOrThrow()) "Game config directory accessible." else "Config files not found (first run?).")
                    } else {
                        addLog("WARNING: ADB cannot access game data directory.")
                        addLog("On Android 13+ this is blocked. Use ROOT, Shizuku, or SAF instead.")
                    }
                }
                loadBackups()
                syncConfigHashes()
            } else {
                val message = friendlyBackendError(result.exceptionOrNull()?.message)
                _backendStatus.value =
                    BackendStatus(
                        method = method, connected = false, host = ip,
                        errorMessage = message,
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
        } catch (_: Exception) {
        }
    }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001) {
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    addLog("Shizuku permission granted!")
                    connect()
                } else {
                    _backendStatus.value = _backendStatus.value.copy(errorMessage = "Shizuku permission denied")
                    addLog("ERROR: Shizuku permission denied")
                }
            }
        }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Exception) {
        }
        app.backend.disconnect()
    }

    fun connectAdbManual(
        host: String,
        portText: String,
    ) {
        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            _backendStatus.value =
                BackendStatus(
                    method = AccessMethod.ADB, errorMessage = "Invalid port. Enter a number between 1-65535.",
                )
            return
        }
        viewModelScope.launch {
            _backendStatus.value = BackendStatus(method = AccessMethod.ADB)
            addLog("Connecting to $host:$port...")
            val backend = app.backend
            if (backend is AdbBackend) {
                val result = backend.connectTo(host, port)
                if (result.isSuccess) {
                    _backendStatus.value = BackendStatus(method = AccessMethod.ADB, connected = true, host = host, port = port)
                    addLog("Connected to $host:$port!")
                    try {
                        getApplication<Application>().startForegroundService(Intent(getApplication(), AdbConnectionService::class.java))
                    } catch (_: Exception) {
                    }
                    loadBackups()
                } else {
                    val msg = friendlyBackendError(result.exceptionOrNull()?.message)
                    _backendStatus.value = BackendStatus(method = AccessMethod.ADB, host = host, errorMessage = msg)
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
        if (!_isApplying.value) return
        app.backend.disconnect()
        _isApplying.value = false
        addLog("Operation cancelled.")
    }

    fun disconnect() {
        app.backend.disconnect()
        val method = _backendStatus.value.method
        if (method == AccessMethod.SAF) {
            val saf = app.backend as? SafBackend
            saf?.clearTreeUri()
        }
        if (method == AccessMethod.ADB) {
            try {
                getApplication<Application>().stopService(Intent(getApplication(), AdbConnectionService::class.java))
            } catch (_: Exception) {
            }
        }
        _backendStatus.value = BackendStatus(method = method)
        _isApplying.value = false
        addLog("Disconnected.")
    }

    fun createBackup(
        name: String,
        selectedFiles: Set<String>? = null,
    ) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                addLog("Creating backup: $name...")
                val result = configManager.createBackup(name, selectedFiles = selectedFiles)
                if (result.isSuccess) {
                    addLog("Backup created")
                    _backupFeedback.value = "Backup '$name' created (${selectedFiles?.size ?: 5} files)"
                    loadBackups()
                } else {
                    _backupFeedback.value = "Backup failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _backupFeedback.value = "Backup failed: ${e.message}"
            } finally {
                _isApplying.value = false
            }
        }
    }

    fun restoreBackup(
        backup: ConfigBackup,
        selectedFiles: Set<String>? = null,
    ) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                addLog("Restoring backup: ${backup.name}...")
                val preSnapshot = configManager.snapshotHashFile().getOrNull()
                val result = configManager.restoreBackup(backup, { msg -> addLog(msg) }, selectedFiles = selectedFiles)
                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                    _backupFeedback.value = "Backup '${backup.name}' restored"
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { addLog(it) }
                        .onFailure { e -> addLog("Hash refresh failed: ${e.message}", LogLevel.ERROR) }
                } else {
                    _backupFeedback.value = "Restore failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _backupFeedback.value = "Restore failed: ${e.message}"
                Log.e("WuWaConfig", "restoreBackup crashed", e)
            } finally {
                _isApplying.value = false
                loadBackups()
            }
        }
    }

    fun deleteBackup(backup: ConfigBackup) {
        viewModelScope.launch {
            try {
                addLog("Deleting backup: ${backup.name}...")
                configManager.deleteLocalBackup(backup)
                loadBackups()
                addLog("Backup deleted")
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "deleteBackup crashed", e)
            }
        }
    }

    fun collectClientLog() {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                addLog("Collecting Client.log...")
                val result = configManager.collectClientLog { msg -> addLog(msg) }
                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                } else {
                    addLog("FAILED: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "collectClientLog crashed", e)
            } finally {
                _isApplying.value = false
            }
        }
    }

    private val _logAnalysis = MutableStateFlow<LogInfo?>(null)
    val logAnalysis: StateFlow<LogInfo?> = _logAnalysis.asStateFlow()

    private val _brainRecommendation = MutableStateFlow<com.wuwaconfig.app.config.BrainRecommendation?>(null)
    val brainRecommendation: StateFlow<com.wuwaconfig.app.config.BrainRecommendation?> = _brainRecommendation.asStateFlow()

    private val _conveneUrl = MutableStateFlow<String?>(null)
    val conveneUrl: StateFlow<String?> = _conveneUrl.asStateFlow()

    private val _conveneUrlLoading = MutableStateFlow(false)
    val conveneUrlLoading: StateFlow<Boolean> = _conveneUrlLoading.asStateFlow()

    private val _profileLoading = MutableStateFlow(false)
    val profileLoading: StateFlow<Boolean> = _profileLoading.asStateFlow()

    private val _battleStats = MutableStateFlow<BattleStats?>(null)
    val battleStats: StateFlow<BattleStats?> = _battleStats.asStateFlow()
    private val _battleStatsFromCache = MutableStateFlow(false)
    val battleStatsFromCache: StateFlow<Boolean> = _battleStatsFromCache.asStateFlow()

    private val _battleStatsLoading = MutableStateFlow(false)
    val battleStatsLoading: StateFlow<Boolean> = _battleStatsLoading.asStateFlow()

    private val _gachaData = MutableStateFlow<GachaData?>(null)
    val gachaData: StateFlow<GachaData?> = _gachaData.asStateFlow()

    private val _gachaLoading = MutableStateFlow(false)
    val gachaLoading: StateFlow<Boolean> = _gachaLoading.asStateFlow()

    fun analyzeClientLog(allowRestrictedCvars: Boolean = true) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
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
                    addLog(if (decrypted) "Encrypted log detected; decrypted successfully." else "Plain log detected.")

                    _readingProgress.value = 75
                    val initialInfo = withContext(Dispatchers.Default) { com.wuwaconfig.app.config.LogParser.parseLog(text) }
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
                    doAnalyzeLogText(analysisText, allowRestrictedCvars)
                } else {
                    addLog("FAILED: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "analyzeClientLog crashed", e)
            } finally {
                _readingProgress.value = 0
                _isApplying.value = false
            }
        }
    }

    fun analyzeClientLogBytes(
        bytes: ByteArray,
        allowRestrictedCvars: Boolean = true,
    ) {
        if (_isApplying.value) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                _readingProgress.value = 0
                addLog("Decoding imported log...")
                val (text, decrypted) = com.wuwaconfig.app.config.LogParser.decodeLogBytes(bytes)
                addLog(if (decrypted) "Encrypted imported log decrypted successfully." else "Imported plain log.")
                _readingProgress.value = 95
                doAnalyzeLogText(text, allowRestrictedCvars)
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "analyzeClientLogBytes crashed", e)
            } finally {
                _readingProgress.value = 0
                _isApplying.value = false
            }
        }
    }

    private suspend fun doAnalyzeLogText(
        text: String,
        allowRestrictedCvars: Boolean = true,
    ) {
        _logAnalysis.value = null
        _brainRecommendation.value = null
        try {
            addLog("Parsing log...")
            val info =
                withContext(Dispatchers.Default) {
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

            withContext(Dispatchers.Default) {
                val battleStats = com.wuwaconfig.app.config.LogParser.parseBattleStats(text)
                BattleStatsStore.save(getApplication(), battleStats)
                LogAnalysisStore.save(getApplication(), info, brain)
            }
            addLog("Analysis cached for quick viewing")
        } catch (e: Exception) {
            addLog("CRASH: ${e.message}")
            Log.e("WuWaConfig", "doAnalyzeLogText crashed", e)
        }
    }

    fun restoreAnalysisFromCache() {
        val cached = LogAnalysisStore.load(getApplication())
        if (cached != null) {
            _logAnalysis.value = cached.logInfo
            _brainRecommendation.value = cached.brainRecommendation
        }
    }

    fun extractConveneUrl(retryCount: Int = 6) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _conveneUrl.value = null
            _gachaData.value = null
            _conveneUrlLoading.value = true
            var remaining = retryCount
            while (remaining >= 0) {
                addLog(
                    "Reading Client.log for Convene URL${if (remaining < retryCount) " (attempt ${retryCount - remaining + 1}/$retryCount)" else ""}...",
                )
                try {
                    val result =
                        configManager.readClientLogTextWithMetadata { pct ->
                            if (pct % 25 == 0 && remaining == retryCount) addLog("Reading... $pct%")
                        }
                    if (result.isSuccess) {
                        val (text, _) = result.getOrThrow()
                        val url =
                            withContext(Dispatchers.Default) {
                                com.wuwaconfig.app.config.LogParser.extractConveneUrl(text)
                            }
                        if (url != null) {
                            addLog("Found Convene URL")
                            _conveneUrl.value = url
                            _conveneUrlLoading.value = false
                            fetchGachaData(url)
                            return@launch
                        }
                    }
                    if (remaining > 0) {
                        addLog("URL not found yet — retrying in 10s...")
                        kotlinx.coroutines.delay(10_000)
                    } else {
                        addLog("No Convene URL found after $retryCount attempts.")
                        addLog("Open Convene History in-game, wait a moment, then tap again.")
                    }
                } catch (e: Exception) {
                    addLog("CRASH: ${e.message}")
                    Log.e("WuWaConfig", "extractConveneUrl crashed", e)
                    break
                }
                remaining--
            }
            _conveneUrlLoading.value = false
        }
    }

    private suspend fun fetchGachaData(url: String) {
        _gachaLoading.value = true
        addLog("Parsing gacha URL...")
        try {
            val params = GachaApi.parseUrl(url)
            if (params == null) {
                addLog("Failed to parse gacha URL")
                return
            }
            addLog("Fetching gacha records from server...")
            val result =
                withContext(Dispatchers.IO) {
                    GachaApi.fetchAllRecords(params)
                }
            if (result.isSuccess) {
                val data = result.getOrThrow()
                _gachaData.value = data
                GachaHistoryStore.save(getApplication(), data)
                _gachaHistory.value = GachaHistoryStore.load(getApplication())
                addLog("Loaded ${data.totalPulls} pulls (${data.fiveStars}★5, ${data.fourStars}★4)")
                if (data.poolsWithData.isNotEmpty()) {
                    addLog("Pools: ${data.poolsWithData.size} with records")
                }
            } else {
                addLog("API failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            addLog("CRASH: ${e.message}")
            Log.e("WuWaConfig", "fetchGachaData crashed", e)
        } finally {
            _gachaLoading.value = false
        }
    }

    fun loadConfigModifyCounts() {
        if (!_backendStatus.value.connected) return
        viewModelScope.launch {
            val result = configManager.readConfigModifyCounts()
            if (result.isSuccess) {
                _configModifyCounts.value = result.getOrThrow().associate { it.fileName to it.modifyCount }
            } else {
                _configModifyCounts.value = emptyMap()
                addLog("Modify counts unavailable: ${result.exceptionOrNull()?.message}", LogLevel.WARNING)
            }
        }
    }

    fun loadProfile(forceRefresh: Boolean = false) {
        if (_profileLoading.value || !_backendStatus.value.connected) return
        if (!forceRefresh && _playerProfile.value != null) return
        viewModelScope.launch {
            if (forceRefresh) _playerProfile.value = null
            _profileLoading.value = true
            addLog(if (forceRefresh) "Refreshing player profile..." else "Reading player profile (read-only)...")
            try {
                val result = configManager.readProfile()
                if (result.isSuccess) {
                    val profile = result.getOrThrow()
                    _playerProfile.value = profile
                    ProfileStore.save(getApplication(), profile)
                    addLog("Profile loaded")
                    loadConfigModifyCounts()
                } else {
                    addLog("FAILED: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "loadProfile crashed", e)
            } finally {
                _profileLoading.value = false
            }
        }
    }

    fun loadBattleStatsFromCache(): Boolean {
        val cached = BattleStatsStore.load(getApplication())
        if (cached != null) {
            _battleStats.value = cached
            _battleStatsFromCache.value = true
            return true
        }
        return false
    }

    fun refreshBattleStats() {
        BattleStatsStore.clear(getApplication())
        _battleStats.value = null
        _battleStatsFromCache.value = false
        loadBattleStats()
    }

    fun loadBattleStats() {
        if (_battleStatsLoading.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
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
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "loadBattleStats crashed", e)
            } finally {
                _battleStatsLoading.value = false
            }
        }
    }

    fun deployGeneratedConfigs(
        ini: com.wuwaconfig.app.model.GeneratedIni,
        opts: com.wuwaconfig.app.model.GeneratorOptions = com.wuwaconfig.app.model.GeneratorOptions(),
        retuneProfile: com.wuwaconfig.app.config.CvarOptimizer.OptimizedProfile? = null,
    ) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
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
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { addLog(it) }
                        .onFailure { e -> addLog("Hash refresh failed: ${e.message}", LogLevel.ERROR) }
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
                    if (_deployHistoryEnabled.value) {
                        val cachedLogInfo = _logAnalysis.value ?: LogAnalysisStore.load(getApplication())?.logInfo
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
                            )
                        DeployHistoryStore.addRecord(record)
                        _deployRecords.value = DeployHistoryStore.getAllRecords()
                        addLog("Deploy record saved (id: ${recordId.take(8)}...)")
                    }
                    _readingProgress.value = 0
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    addLog("FAILED: $err")
                    _deployResult.value = "Failed: $err"
                }
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "deployGeneratedConfigs crashed", e)
            } finally {
                _isApplying.value = false
            }
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
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
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
                    loadBackups()
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { addLog(it) }
                        .onFailure { e -> addLog("Hash refresh failed: ${e.message}", LogLevel.ERROR) }
                } else {
                    addLog("FAILED: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "applyCustomFiles crashed", e)
            } finally {
                _isApplying.value = false
            }
        }
    }

    fun cleanConfigFiles() {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                addLog("Cleaning config files...")
                val preSnapshot = configManager.snapshotHashFile().getOrNull()
                val result = configManager.cleanConfigFiles { msg -> addLog(msg) }
                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                    loadBackups()
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { addLog(it) }
                        .onFailure { e -> addLog("Hash refresh failed: ${e.message}", LogLevel.ERROR) }
                } else {
                    addLog(result.exceptionOrNull()?.message ?: "Clean failed")
                }
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "cleanConfigFiles crashed", e)
            } finally {
                _isApplying.value = false
            }
        }
    }

    fun deleteSelectedConfigFiles(selectedFiles: Set<String>) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                addLog("Deleting ${selectedFiles.size} config file(s): ${selectedFiles.joinToString(", ")}")
                val preSnapshot = configManager.snapshotHashFile().getOrNull()
                val result = configManager.deleteConfigFiles(selectedFiles)
                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                    loadBackups()
                    configManager.reconcileAfterModify(preSnapshot).onSuccess { addLog(it) }
                        .onFailure { e -> addLog("Hash refresh failed: ${e.message}", LogLevel.ERROR) }
                } else {
                    addLog(result.exceptionOrNull()?.message ?: "Delete failed")
                }
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "deleteSelectedConfigFiles crashed", e)
            } finally {
                _isApplying.value = false
            }
        }
    }

    private suspend fun computeIniHash(name: String): Result<String> {
        val path = "${GamePaths.TARGET_DIR}/$name"
        val bytesResult = app.backend.readFileBytes(path)
        if (bytesResult.isFailure) {
            addLog("Hash sync: readFileBytes FAILED for $name: ${bytesResult.exceptionOrNull()?.message}", LogLevel.ERROR)
            return Result.failure(bytesResult.exceptionOrNull()!!)
        }
        val bytes = bytesResult.getOrThrow()
        val md5 = java.security.MessageDigest.getInstance("MD5")
        val hash = md5.digest(bytes).joinToString("") { "%02x".format(it) }
        addLog("Hash sync: computed hash for $name = $hash (${bytes.size} bytes)")
        return Result.success(hash)
    }

    fun syncConfigHashes(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            addLog("Hash sync: checking device config hashes...")
            val hashContent = app.backend.readFile(GamePaths.HASH_MONITOR_PATH).getOrDefault("")
            if (hashContent.isBlank()) {
                addLog("Hash sync: no hash file found, creating fresh...")
                configManager.refreshConfigHashes().onSuccess { addLog(it) }
                onResult(true)
                return@launch
            }
            var needsRefresh = false
            for (name in GamePaths.MONITORED_FILES) {
                val actualHashResult = computeIniHash(name)
                if (actualHashResult.isFailure) {
                    addLog("Hash sync: SKIPPING $name — cannot compute hash", LogLevel.ERROR)
                    needsRefresh = true
                    continue
                }
                val actualHash = actualHashResult.getOrThrow()
                val storedHash = extractHash(hashContent, name)
                if (storedHash != null && storedHash != actualHash) {
                    addLog("Hash sync: $name hash mismatch (stored=$storedHash, actual=$actualHash)", LogLevel.WARNING)
                    needsRefresh = true
                } else if (storedHash == null) {
                    addLog("Hash sync: $name has no stored hash", LogLevel.WARNING)
                    needsRefresh = true
                } else {
                    addLog("Hash sync: $name hash OK ($actualHash)")
                }
            }
            if (needsRefresh) {
                addLog("Hash sync: refreshing to match current files...")
                configManager.refreshConfigHashes().onSuccess { addLog(it) }
            } else {
                addLog("Hash sync: all hashes match", LogLevel.SUCCESS)
            }
            onResult(needsRefresh)
        }
    }

    private fun extractHash(
        hashContent: String,
        fileName: String,
    ): String? {
        var inSection = false
        val iniSectionRegex = Regex("^\\[[A-Za-z0-9_\\-]+\\.ini\\]$", RegexOption.IGNORE_CASE)
        for (line in hashContent.lines()) {
            val t = line.trim()
            if (t.equals("[$fileName]", ignoreCase = true)) {
                inSection = true
                continue
            }
            if (inSection && t.matches(iniSectionRegex)) break
            if (inSection && t.startsWith("Hash=")) return t.removePrefix("Hash=").trim()
        }
        return null
    }

    suspend fun executeShellCommand(cmd: String): Result<String> {
        return app.backend.executeShellCommand(cmd)
    }

    fun readUriContent(uri: Uri): Result<String> {
        return try {
            val ctx = getApplication<Application>()
            val stream =
                ctx.contentResolver.openInputStream(uri)
                    ?: return Result.failure(Exception("Cannot open file"))
            val text = stream.use { BufferedReader(InputStreamReader(it)).readText() }
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun readUriBytes(uri: Uri): Result<ByteArray> {
        return try {
            val ctx = getApplication<Application>()
            val stream =
                ctx.contentResolver.openInputStream(uri)
                    ?: return Result.failure(Exception("Cannot open file"))
            val bytes = stream.use { it.readBytes() }
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getFileName(uri: Uri): String? {
        return try {
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

    private fun loadBackups() {
        _backups.value = configManager.getLocalBackups()
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
