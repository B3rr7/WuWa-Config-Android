package com.wuwaconfig.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.adb.PortScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.AdbBackend
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.backend.SafBackend
import rikka.shizuku.Shizuku
import com.wuwaconfig.app.config.ChipsetDetector
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.LogInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WuWaConfigApp
    private val chipsetDetector = ChipsetDetector

    private var _configManager: ConfigManager? = null
    private val configManager: ConfigManager get() {
        if (_configManager == null) {
            _configManager = ConfigManager(getApplication(), app.backend, backupStorageDir)
        }
        return _configManager!!
    }

    private val _backendStatus = MutableStateFlow(BackendStatus())
    val backendStatus: StateFlow<BackendStatus> = _backendStatus.asStateFlow()

    private val _backups = MutableStateFlow<List<ConfigBackup>>(emptyList())
    val backups: StateFlow<List<ConfigBackup>> = _backups.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _deployResult = MutableStateFlow<String?>(null)
    val deployResult: StateFlow<String?> = _deployResult.asStateFlow()

    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying.asStateFlow()

    private val _readingProgress = MutableStateFlow(0)
    val readingProgress: StateFlow<Int> = _readingProgress.asStateFlow()

    fun clearDeployResult() { _deployResult.value = null }

    val chipsetInfo = chipsetDetector.detect()
    val gameConfigDir = com.wuwaconfig.app.model.GamePaths.TARGET_DIR

    private val prefs = application.getSharedPreferences("wuwaconfig", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

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
        if (uri != null) prefs.edit().putString("bg_image_uri", uri).apply()
        else prefs.edit().remove("bg_image_uri").apply()
        app.backgroundImageUri.value = uri
    }

    fun setBackgroundVideoUri(uri: String?) {
        if (uri != null) prefs.edit().putString("bg_video_uri", uri).apply()
        else prefs.edit().remove("bg_video_uri").apply()
        app.backgroundVideoUri.value = uri
    }

    fun setBackgroundOpacity(opacity: Float) {
        prefs.edit().putFloat("bg_opacity", opacity).apply()
        app.backgroundOpacity.value = opacity
    }

    private val defaultBackupDir = application.filesDir.resolve("backups").absolutePath

    val backupStorageDir: String
        get() = prefs.getString("backup_dir", defaultBackupDir) ?: defaultBackupDir

    val isSetupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)

    val termsAccepted: Boolean
        get() = prefs.getBoolean("terms_accepted", false)

    fun acceptTerms() {
        prefs.edit().putBoolean("terms_accepted", true).apply()
    }

    init {
        loadBackups()
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
            val ip = if (method == AccessMethod.ADB) PortScanner.getDeviceIp() else ""
            val port = _backendStatus.value.port

            if (result.isSuccess) {
                _backendStatus.value = BackendStatus(method = method, connected = true, host = ip, port = port)
                addLog("Connected via ${method.name}!")
                if (method == AccessMethod.ADB) {
                    val testAccess = backend.fileExists("$gameConfigDir/Engine.ini")
                    if (testAccess.isSuccess) {
                        addLog(if (testAccess.getOrThrow()) "Game config directory accessible." else "Config files not found (first run?).")
                    } else {
                        val err = testAccess.exceptionOrNull()?.message ?: ""
                        addLog("WARNING: ADB cannot access game data directory.")
                        addLog("On Android 13+ this is blocked. Use ROOT, Shizuku, or SAF instead.")
                    }
                }
                loadBackups()
            } else {
                val message = friendlyBackendError(result.exceptionOrNull()?.message)
                _backendStatus.value = BackendStatus(
                    method = method, connected = false, host = ip,
                    errorMessage = message
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
        } catch (_: Exception) {}
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
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
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (_: Exception) {}
        app.backend.disconnect()
    }

    fun connectAdbManual(host: String, portText: String) {
        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            _backendStatus.value = BackendStatus(
                method = AccessMethod.ADB, errorMessage = "Invalid port. Enter a number between 1-65535."
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
        _backendStatus.value = BackendStatus(method = method)
        _isApplying.value = false
        addLog("Disconnected.")
    }

    fun createBackup(name: String) {
        Log.d("MainViewModel", "createBackup: name='$name' connected=${_backendStatus.value.connected}")
        if (!_backendStatus.value.connected) {
            Log.d("MainViewModel", "createBackup: not connected, returning")
            return
        }
        viewModelScope.launch {
            addLog("Creating backup: $name...")
            Log.d("MainViewModel", "createBackup: calling configManager.createBackup")
            val result = configManager.createBackup(name)
            Log.d("MainViewModel", "createBackup: result success=${result.isSuccess} error=${result.exceptionOrNull()?.message}")
            if (result.isSuccess) { addLog("Backup created"); loadBackups() }
            else addLog("Backup failed: ${result.exceptionOrNull()?.message}")
        }
    }

    fun restoreBackup(backup: ConfigBackup) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            addLog("Restoring backup: ${backup.name}...")
            val result = configManager.restoreBackup(backup) { msg -> addLog(msg) }
            if (result.isSuccess) addLog("SUCCESS: ${result.getOrThrow()}")
            else addLog("FAILED: ${result.exceptionOrNull()?.message}")
            _isApplying.value = false
        }
    }

    fun deleteBackup(backup: ConfigBackup) {
        viewModelScope.launch {
            addLog("Deleting backup: ${backup.name}...")
            configManager.deleteLocalBackup(backup)
            loadBackups()
            addLog("Backup deleted")
        }
    }

    fun collectClientLog() {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            addLog("Collecting Client.log...")
            val result = configManager.collectClientLog { msg -> addLog(msg) }
            if (result.isSuccess) addLog("SUCCESS: ${result.getOrThrow()}")
            else addLog("FAILED: ${result.exceptionOrNull()?.message}")
            _isApplying.value = false
        }
    }

    private val _logAnalysis = MutableStateFlow<LogInfo?>(null)
    val logAnalysis: StateFlow<LogInfo?> = _logAnalysis.asStateFlow()

    private val _brainRecommendation = MutableStateFlow<com.wuwaconfig.app.config.BrainRecommendation?>(null)
    val brainRecommendation: StateFlow<com.wuwaconfig.app.config.BrainRecommendation?> = _brainRecommendation.asStateFlow()

    fun analyzeClientLog() {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            _readingProgress.value = 0
            addLog("Reading Client.log from device...")
            val result = configManager.readClientLogTextWithMetadata { pct ->
                _readingProgress.value = pct
            }
            if (result.isSuccess) {
                _readingProgress.value = 95
                val (text, decrypted) = result.getOrThrow()
                addLog(if (decrypted) "Encrypted log detected; decrypted successfully." else "Plain log detected.")
                analyzeLogText(text)
                return@launch
            } else {
                addLog("FAILED: ${result.exceptionOrNull()?.message}")
            }
            _readingProgress.value = 0
            _isApplying.value = false
        }
    }

    fun analyzeClientLogBytes(bytes: ByteArray) {
        if (_isApplying.value) return
        viewModelScope.launch {
            _isApplying.value = true
            _readingProgress.value = 0
            addLog("Decoding imported log...")
            val (text, decrypted) = com.wuwaconfig.app.config.LogParser.decodeLogBytes(bytes)
            addLog(if (decrypted) "Encrypted imported log decrypted successfully." else "Imported plain log.")
            _readingProgress.value = 95
            analyzeLogText(text)
            return@launch
        }
    }

    private fun analyzeLogText(text: String) {
        viewModelScope.launch {
            addLog("Parsing log...")
            val info = withContext(Dispatchers.Default) {
                com.wuwaconfig.app.config.LogParser.parseLog(text)
            }
            com.wuwaconfig.app.config.ConfigGenerator.logInfo = info
            _logAnalysis.value = info
            addLog("GPU: ${info.gpu ?: "unknown"}, RAM: ${info.ramMb ?: "?"}MB")
            val brain = withContext(Dispatchers.Default) {
                com.wuwaconfig.app.config.SmartBrain.scoreRecommendation(info)
            }
            _brainRecommendation.value = brain
            addLog("Brain recommends: ${brain.preset} (score: ${brain.score})")
            _readingProgress.value = 0
            _isApplying.value = false
        }
    }

    fun deployGeneratedConfigs(
        ini: com.wuwaconfig.app.model.GeneratedIni,
        opts: com.wuwaconfig.app.model.GeneratorOptions = com.wuwaconfig.app.model.GeneratorOptions()
    ) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            addLog("Deploying generated configs...")

            val existingResult = configManager.readCurrentConfig("Engine.ini")
            val corePaths = if (existingResult.isSuccess) {
                val extracted = com.wuwaconfig.app.config.ConfigGenerator.extractCoreSystemPaths(existingResult.getOrThrow())
                addLog("Found ${extracted.size - 1} [Core.System] paths on device")
                extracted
            } else {
                val fromBackup = configManager.getLocalBackups().firstOrNull { backup ->
                    backup.files.any { it.name == "Engine.ini" }
                }?.files?.firstOrNull { it.name == "Engine.ini" }?.content
                if (fromBackup != null) {
                    addLog("Device Engine.ini missing, using paths from backup")
                    com.wuwaconfig.app.config.ConfigGenerator.extractCoreSystemPaths(fromBackup)
                } else {
                    addLog("Using default [Core.System] paths")
                    com.wuwaconfig.app.config.ConfigGenerator.DEFAULT_CORE_SYSTEM
                }
            }

            val engineWithPaths = com.wuwaconfig.app.config.ConfigGenerator.generateWithCorePaths(
                com.wuwaconfig.app.config.ConfigGenerator.activePreset,
                opts,
                corePaths
            ).engine

            val result = configManager.applyCustomConfigs(
                engineIni = engineWithPaths,
                deviceProfilesIni = ini.deviceProfiles,
                gameUserSettingsIni = ini.gameUserSettings,
            ) { msg -> addLog(msg) }
            if (result.isSuccess) {
                addLog("SUCCESS: ${result.getOrThrow()}")
                _deployResult.value = result.getOrThrow()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                addLog("FAILED: $err")
                _deployResult.value = "Failed: $err"
            }
            _isApplying.value = false
        }
    }

    fun applyCustomFiles(engineIni: String?, deviceProfilesIni: String?, gameUserSettingsIni: String?) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            addLog("Applying custom configs...")

            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            addLog("Backing up current configs...")
            val backupResult = configManager.createBackup("Auto-backup $ts", type = "auto")
            if (backupResult.isSuccess) {
                addLog("Backup saved: ${backupResult.getOrThrow().name}")
            } else {
                addLog("(no existing configs to back up)")
            }

            val result = configManager.applyCustomConfigs(
                engineIni = engineIni,
                deviceProfilesIni = deviceProfilesIni,
                gameUserSettingsIni = gameUserSettingsIni,
            ) { msg -> addLog(msg) }

            if (result.isSuccess) {
                addLog("SUCCESS: ${result.getOrThrow()}")
                loadBackups()
            } else addLog("FAILED: ${result.exceptionOrNull()?.message}")
            _isApplying.value = false
        }
    }

    fun deleteConfigFiles() {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            addLog("Deleting config files...")
            val result = configManager.deleteConfigFiles { msg -> addLog(msg) }
            if (result.isSuccess) {
                addLog("SUCCESS: ${result.getOrThrow()}")
                loadBackups()
            } else addLog("FAILED: ${result.exceptionOrNull()?.message}")
            _isApplying.value = false
        }
    }

    fun readUriContent(uri: Uri): Result<String> {
        return try {
            val ctx = getApplication<Application>()
            val inputStream = ctx.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))
            val text = BufferedReader(InputStreamReader(inputStream)).readText()
            inputStream.close()
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun readUriBytes(uri: Uri): Result<ByteArray> {
        return try {
            val ctx = getApplication<Application>()
            val inputStream = ctx.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Cannot open file"))
            val bytes = inputStream.readBytes()
            inputStream.close()
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
                } else null
            }
        } catch (_: Exception) { null }
    }

    private fun loadBackups() {
        _backups.value = configManager.getLocalBackups()
    }

    fun addLog(message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _logs.value = (_logs.value + "[$ts] $message").takeLast(200)
    }

}
