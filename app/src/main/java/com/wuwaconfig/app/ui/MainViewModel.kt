package com.wuwaconfig.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.WuWaConfigApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.config.ChipsetDetector
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.model.ConfigBackup
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

    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying.asStateFlow()

    val chipsetInfo = chipsetDetector.detect()
    val gameConfigDir = "/storage/emulated/0/Android/data/com.kurogame.wutheringwaves.global/files/UE4Game/Client/Client/Saved/Config/Android"

    private val prefs = application.getSharedPreferences("wuwaconfig", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
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
        val targetDir = java.io.File("/storage/emulated/0/Download/wuwap42/backup")
        var ok = targetDir.mkdirs() || targetDir.exists()
        if (!ok) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p ${targetDir.absolutePath}"))
                proc.waitFor()
                ok = targetDir.exists()
            } catch (_: Exception) {}
        }
        if (ok) changeBackupDir(targetDir.absolutePath)
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

            val backend = app.backend
            val result = backend.connect()

            if (result.isSuccess) {
                _backendStatus.value = BackendStatus(method = method, connected = true)
                addLog("Connected via ${method.name}!")
                loadBackups()
            } else {
                val message = friendlyBackendError(result.exceptionOrNull()?.message)
                _backendStatus.value = BackendStatus(
                    method = method, connected = false,
                    errorMessage = message
                )
                addLog("ERROR: $message")
            }
        }
    }

    private fun friendlyBackendError(message: String?): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("ECONNREFUSED", ignoreCase = true) ->
                "ADB connection refused. Enable Wireless Debugging and retry."
            raw.contains("timed out", ignoreCase = true) || raw.contains("after 5000ms", ignoreCase = true) ->
                "ADB connection timed out. Check Wireless Debugging."
            raw.contains("ADB daemon not found", ignoreCase = true) ->
                "ADB daemon not found. Enable Wireless Debugging."
            raw.isBlank() -> "Connection failed"
            else -> raw.take(120)
        }
    }

    fun disconnect() {
        app.backend.disconnect()
        _backendStatus.value = BackendStatus(method = _backendStatus.value.method)
        addLog("Disconnected.")
    }

    fun createBackup(name: String) {
        if (!_backendStatus.value.connected) return
        viewModelScope.launch {
            addLog("Creating backup: $name...")
            val result = configManager.createBackup(name)
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

    private val _logAnalysis = MutableStateFlow<com.wuwaconfig.app.config.LogInfo?>(null)
    val logAnalysis: StateFlow<com.wuwaconfig.app.config.LogInfo?> = _logAnalysis.asStateFlow()

    private val _brainRecommendation = MutableStateFlow<com.wuwaconfig.app.config.BrainRecommendation?>(null)
    val brainRecommendation: StateFlow<com.wuwaconfig.app.config.BrainRecommendation?> = _brainRecommendation.asStateFlow()

    fun analyzeClientLog() {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            addLog("Reading Client.log from device...")
            val result = configManager.readClientLogTextWithMetadata()
            if (result.isSuccess) {
                val (text, decrypted) = result.getOrThrow()
                addLog(if (decrypted) "Encrypted log detected; decrypted successfully." else "Plain log detected.")
                analyzeLogText(text)
            } else {
                addLog("FAILED: ${result.exceptionOrNull()?.message}")
            }
            _isApplying.value = false
        }
    }

    fun analyzeClientLogBytes(bytes: ByteArray) {
        if (_isApplying.value) return
        viewModelScope.launch {
            _isApplying.value = true
            val (text, decrypted) = com.wuwaconfig.app.config.LogParser.decodeLogBytes(bytes)
            addLog(if (decrypted) "Encrypted imported log decrypted successfully." else "Imported plain log.")
            analyzeLogText(text)
            _isApplying.value = false
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
        }
    }

    fun deployGeneratedConfigs(ini: com.wuwaconfig.app.model.GeneratedIni) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            addLog("Deploying generated configs...")
            val result = configManager.applyCustomConfigs(
                engineIni = ini.engine,
                deviceProfilesIni = ini.deviceProfiles,
                gameUserSettingsIni = ini.gameUserSettings,
            ) { msg -> addLog(msg) }
            if (result.isSuccess) addLog("SUCCESS: ${result.getOrThrow()}")
            else addLog("FAILED: ${result.exceptionOrNull()?.message}")
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
        _logs.value = _logs.value + "[$ts] $message"
    }

    override fun onCleared() {
        super.onCleared()
        app.backend.disconnect()
    }
}
