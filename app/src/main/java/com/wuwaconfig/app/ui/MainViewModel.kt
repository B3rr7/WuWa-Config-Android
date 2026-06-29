package com.wuwaconfig.app.ui

import android.app.Application
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import com.wuwaconfig.app.config.GachaApi
import com.wuwaconfig.app.config.GachaHistoryStore
import com.wuwaconfig.app.config.ProfileStore
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaHistoryEntry
import com.wuwaconfig.app.model.LogEntry
import com.wuwaconfig.app.model.LogInfo
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.service.AdbConnectionService
import com.wuwaconfig.app.service.GachaPollService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WuWaConfigApp
    private val chipsetDetector = ChipsetDetector

    private var _configManager: ConfigManager? = null
    private val configManager: ConfigManager get() = synchronized(this) {
        _configManager ?: ConfigManager(getApplication(), app.backend, backupStorageDir).also { _configManager = it }
    }

    private val _backendStatus = MutableStateFlow(BackendStatus())
    val backendStatus: StateFlow<BackendStatus> = _backendStatus.asStateFlow()

    private val _backups = MutableStateFlow<List<ConfigBackup>>(emptyList())
    val backups: StateFlow<List<ConfigBackup>> = _backups.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _deployResult = MutableStateFlow<String?>(null)
    val deployResult: StateFlow<String?> = _deployResult.asStateFlow()

    private val _verificationReport = MutableStateFlow<com.wuwaconfig.app.model.VerificationReport?>(null)
    val verificationReport: StateFlow<com.wuwaconfig.app.model.VerificationReport?> = _verificationReport.asStateFlow()

    private val _isApplying = MutableStateFlow(false)
    val isApplying: StateFlow<Boolean> = _isApplying.asStateFlow()

    private val _readingProgress = MutableStateFlow(0)
    val readingProgress: StateFlow<Int> = _readingProgress.asStateFlow()

    fun clearDeployResult() { _deployResult.value = null; _verificationReport.value = null }
    fun clearConveneUrl() { _conveneUrl.value = null; _gachaData.value = null }

    private val _gachaHistory = MutableStateFlow<GachaHistoryEntry?>(null)
    val gachaHistory: StateFlow<GachaHistoryEntry?> = _gachaHistory.asStateFlow()

    private val _playerProfile = MutableStateFlow<com.wuwaconfig.app.model.PlayerProfile?>(null)
    val playerProfile: StateFlow<com.wuwaconfig.app.model.PlayerProfile?> = _playerProfile.asStateFlow()

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

    companion object {
        private const val TERMS_VERSION = 1
    }

    val termsAccepted: Boolean
        get() = prefs.getBoolean("terms_accepted", false)

    val termsVersionAccepted: Int
        get() = prefs.getInt("terms_version", 0)

    fun needsTermsAccept(): Boolean =
        !termsAccepted || termsVersionAccepted < TERMS_VERSION

    fun acceptTerms() {
        prefs.edit().putBoolean("terms_accepted", true).putInt("terms_version", TERMS_VERSION).apply()
    }

    fun postAcceptInit() {
        loadBackups()
        try {
            val filter = IntentFilter("com.wuwaconfig.app.GACHA_DATA_READY")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplication<Application>().registerReceiver(gachaReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                getApplication<Application>().registerReceiver(gachaReceiver, filter)
            }
        } catch (_: Exception) { }
        _gachaHistory.value = GachaHistoryStore.load(getApplication())
        val cached = ProfileStore.load(getApplication())
        if (cached != null) {
            _playerProfile.value = cached
            addLog("Cached profile loaded")
        }
    }

    private val gachaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
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
                } catch (_: Exception) { }
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

    fun gachaHistoryRemainingHours(): Long =
        GachaHistoryStore.getRemainingHours(getApplication())

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
            val port = com.wuwaconfig.app.adb.PortScanner.lastAdbPort?.let { p -> if (p > 0) p else _backendStatus.value.port } ?: _backendStatus.value.port

            if (result.isSuccess) {
                _backendStatus.value = BackendStatus(method = method, connected = true, host = ip, port = port)
                addLog("Connected via ${method.name}!")
                if (method == AccessMethod.ADB) {
                    try {
                        getApplication<Application>().startForegroundService(Intent(getApplication(), AdbConnectionService::class.java))
                    } catch (_: Exception) {}
                    val testAccess = backend.fileExists("$gameConfigDir/Engine.ini")
                    if (testAccess.isSuccess) {
                        addLog(if (testAccess.getOrThrow()) "Game config directory accessible." else "Config files not found (first run?).")
                    } else {
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
                    try {
                        getApplication<Application>().startForegroundService(Intent(getApplication(), AdbConnectionService::class.java))
                    } catch (_: Exception) {}
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
            } catch (_: Exception) {}
        }
        _backendStatus.value = BackendStatus(method = method)
        _isApplying.value = false
        addLog("Disconnected.")
    }

    fun createBackup(name: String) {
        Log.d("MainViewModel", "createBackup: name='$name' connected=${_backendStatus.value.connected}")
        if (_isApplying.value || !_backendStatus.value.connected) {
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
            try {
                addLog("Restoring backup: ${backup.name}...")
                val result = configManager.restoreBackup(backup) { msg -> addLog(msg) }
                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                    configManager.refreshConfigHashes().onSuccess { addLog(it) }
                } else addLog("FAILED: ${result.exceptionOrNull()?.message}")
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "restoreBackup crashed", e)
            } finally {
                _isApplying.value = false
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
                if (result.isSuccess) addLog("SUCCESS: ${result.getOrThrow()}")
                else addLog("FAILED: ${result.exceptionOrNull()?.message}")
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

    private val _battleStatsLoading = MutableStateFlow(false)
    val battleStatsLoading: StateFlow<Boolean> = _battleStatsLoading.asStateFlow()

    private val _gachaData = MutableStateFlow<GachaData?>(null)
    val gachaData: StateFlow<GachaData?> = _gachaData.asStateFlow()

    private val _gachaLoading = MutableStateFlow(false)
    val gachaLoading: StateFlow<Boolean> = _gachaLoading.asStateFlow()

    fun analyzeClientLog() {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                _readingProgress.value = 0
                addLog("Reading Client.log from device...")
                val result = configManager.readClientLogTextWithMetadata { pct ->
                    _readingProgress.value = pct
                }
                if (result.isSuccess) {
                    _readingProgress.value = 95
                    val (text, decrypted) = result.getOrThrow()
                    addLog(if (decrypted) "Encrypted log detected; decrypted successfully." else "Plain log detected.")
                    doAnalyzeLogText(text)
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

    fun analyzeClientLogBytes(bytes: ByteArray) {
        if (_isApplying.value) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                _readingProgress.value = 0
                addLog("Decoding imported log...")
                val (text, decrypted) = com.wuwaconfig.app.config.LogParser.decodeLogBytes(bytes)
                addLog(if (decrypted) "Encrypted imported log decrypted successfully." else "Imported plain log.")
                _readingProgress.value = 95
                doAnalyzeLogText(text)
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "analyzeClientLogBytes crashed", e)
            } finally {
                _readingProgress.value = 0
                _isApplying.value = false
            }
        }
    }

    private suspend fun doAnalyzeLogText(text: String) {
        try {
            addLog("Parsing log...")
            val info = withContext(Dispatchers.Default) {
                com.wuwaconfig.app.config.LogParser.parseLog(text)
            }
            com.wuwaconfig.app.config.ConfigGenerator.logInfo = info
            _logAnalysis.value = info
            addLog("GPU: ${info.gpu ?: "unknown"}, RAM: ${info.ramMb ?: "?"}MB")
            _readingProgress.value = 98
            val brain = withContext(Dispatchers.Default) {
                com.wuwaconfig.app.config.SmartBrain.scoreRecommendation(info)
            }
            _brainRecommendation.value = brain
            addLog("Brain recommends: ${brain.preset} (score: ${brain.score})")
        } catch (e: Exception) {
            addLog("CRASH: ${e.message}")
            Log.e("WuWaConfig", "doAnalyzeLogText crashed", e)
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
                addLog("Reading Client.log for Convene URL${if (remaining < retryCount) " (attempt ${retryCount - remaining + 1}/$retryCount)" else ""}...")
                try {
                    val result = configManager.readClientLogTextWithMetadata { pct ->
                        if (pct % 25 == 0 && remaining == retryCount) addLog("Reading... $pct%")
                    }
                    if (result.isSuccess) {
                        val (text, _) = result.getOrThrow()
                        val url = withContext(Dispatchers.Default) {
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
            val result = withContext(Dispatchers.IO) {
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

    fun openConveneUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<android.app.Application>().startActivity(intent)
        } catch (e: Exception) {
            addLog("Failed to open URL: ${e.message}")
        }
    }

    fun copyToClipboard(text: String) {
        try {
            val ctx = getApplication<android.app.Application>()
            val clip = android.content.ClipData.newPlainText("label", text)
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(clip)
            addLog("Copied to clipboard")
        } catch (e: Exception) {
            addLog("Copy failed: ${e.message}")
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
        opts: com.wuwaconfig.app.model.GeneratorOptions = com.wuwaconfig.app.model.GeneratorOptions()
    ) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
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
                    engineIni = if (opts.generateEngine) engineWithPaths else null,
                    deviceProfilesIni = if (opts.generateDeviceProfiles) ini.deviceProfiles else null,
                    gameUserSettingsIni = if (opts.generateGameUserSettings) ini.gameUserSettings else null,
                    scalabilityIni = if (opts.generateScalability && ini.scalability.isNotBlank()) ini.scalability else null,
                    hardwareIni = if (opts.generateHardware && ini.hardware.isNotBlank()) ini.hardware else null,
                ) { msg -> addLog(msg) }
                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                    _deployResult.value = result.getOrThrow()
                    configManager.refreshConfigHashes().onSuccess { addLog(it) }
                    addLog("Verifying deployed CVars against ConfigMonitor...")
                    _readingProgress.value = 50
                    configManager.verifyDeployedCvars(com.wuwaconfig.app.config.ConfigGenerator.lastGeneratedCvars).onSuccess { report ->
                        _verificationReport.value = report
                        _readingProgress.value = 100
                        addLog("VERIFY: ${report.recognizedCount}/${report.totalCount} CVars accepted by engine")
                        if (report.rejected.isNotEmpty()) {
                            val sample = report.rejected.take(5).joinToString(", ")
                            addLog("Unrecognized (sample): $sample${if (report.rejected.size > 5) "..." else ""}")
                        }
                    }.onFailure { e ->
                        addLog("Verify skipped: ${e.message}")
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

    fun applyCustomFiles(engineIni: String?, deviceProfilesIni: String?, gameUserSettingsIni: String?, scalabilityIni: String? = null, hardwareIni: String? = null) {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                addLog("Applying custom configs...")

                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                if (prefs.getBoolean("backup_before_apply", true)) {
                    addLog("Backing up current configs...")
                    val backupResult = configManager.createBackup("Auto-backup $ts", type = "auto")
                    if (backupResult.isSuccess) {
                        addLog("Backup saved: ${backupResult.getOrThrow().name}")
                    } else {
                        addLog("(no existing configs to back up)")
                    }
                }

                val result = configManager.applyCustomConfigs(
                    engineIni = engineIni,
                    deviceProfilesIni = deviceProfilesIni,
                    gameUserSettingsIni = gameUserSettingsIni,
                    scalabilityIni = scalabilityIni,
                    hardwareIni = hardwareIni,
                ) { msg -> addLog(msg) }

                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                    loadBackups()
                    configManager.refreshConfigHashes().onSuccess { addLog(it) }
                } else addLog("FAILED: ${result.exceptionOrNull()?.message}")
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "applyCustomFiles crashed", e)
            } finally {
                _isApplying.value = false
            }
        }
    }

    fun deleteConfigFiles() {
        if (_isApplying.value || !_backendStatus.value.connected) return
        viewModelScope.launch {
            _isApplying.value = true
            try {
                addLog("Deleting config files...")
                val result = configManager.deleteConfigFiles { msg -> addLog(msg) }
                if (result.isSuccess) {
                    addLog("SUCCESS: ${result.getOrThrow()}")
                    loadBackups()
                } else addLog("FAILED: ${result.exceptionOrNull()?.message}")
            } catch (e: Exception) {
                addLog("CRASH: ${e.message}")
                Log.e("WuWaConfig", "deleteConfigFiles crashed", e)
            } finally {
                _isApplying.value = false
            }
        }
    }

    suspend fun executeShellCommand(cmd: String): Result<String> {
        return app.backend.executeShellCommand(cmd)
    }

    fun readUriContent(uri: Uri): Result<String> {
        return try {
            val ctx = getApplication<Application>()
            val stream = ctx.contentResolver.openInputStream(uri)
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
            val stream = ctx.contentResolver.openInputStream(uri)
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
                } else null
            }
        } catch (_: Exception) { null }
    }

    private fun loadBackups() {
        _backups.value = configManager.getLocalBackups()
    }

    fun addLog(message: String, level: LogLevel = detectLevel(message)) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _logs.value = (_logs.value + LogEntry(message, ts, level)).takeLast(200)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun saveLogs() {
        viewModelScope.launch {
            val entries = _logs.value
            if (entries.isEmpty()) {
                addLog("No logs to save.", level = LogLevel.WARNING)
                return@launch
            }
            val content = entries.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
            try {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WuWaConfig").also { it.mkdirs() }
                val fileName = "WuWaConfig_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.txt"
                val file = File(dir, fileName)
                file.writeText(content)
                addLog("Log saved: ${file.absolutePath}", level = LogLevel.SUCCESS)
            } catch (e: Exception) {
                addLog("Failed to save log: ${e.message}", level = LogLevel.ERROR)
            }
        }
    }

    private fun detectLevel(message: String): LogLevel = when {
        message.startsWith("SUCCESS:") || message.startsWith("SUCCESS ") -> LogLevel.SUCCESS
        message.startsWith("WARNING:") -> LogLevel.WARNING
        message.startsWith("ERROR:") || message.startsWith("FAILED:") || message.startsWith("CRASH:") -> LogLevel.ERROR
        else -> LogLevel.INFO
    }

}
