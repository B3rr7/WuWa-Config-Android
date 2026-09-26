package com.wuwaconfig.app.ui

import java.io.File
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wuwaconfig.app.PREFS_NAME
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app: WuWaConfigApp =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("MainViewModel requires WuWaConfigApp application")

    // Gson is expensive to construct (reflection metadata); reuse one instance
    // instead of instantiating per save/load call.
    private val gson = Gson()

    val configGenerator get() = app.configGenerator

    private val configManager: ConfigManager by lazy { ConfigManager(app, { app.backend }) }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val deployHistoryEnabled: StateFlow<Boolean> = app.deployHistoryEnabled
    val colorfulUi: StateFlow<Boolean> = app.colorfulUi
    val gameConfigDir: String = app.gameConfigDir

    private val defaultBackupDir = application.filesDir.resolve("backups").absolutePath

    // Cached in-memory mirrors of the two prefs the composition layer reads
    // (MainActivity start destination + SetupScreen field). Reading
    // SharedPreferences on every recomposition was the NIT; the flows also let
    // finishSetup() push changes without a full restart.
    private val _backupStorageDir =
        MutableStateFlow(prefs.getString("backup_dir", defaultBackupDir) ?: defaultBackupDir)
    val backupStorageDir: StateFlow<String> = _backupStorageDir.asStateFlow()

    private val _isSetupDone = MutableStateFlow(prefs.getBoolean("setup_done", false))
    val isSetupDone: StateFlow<Boolean> = _isSetupDone.asStateFlow()

    companion object {
        private const val TERMS_VERSION = 1
        private const val KEY_REVIEW_TUNE_PENDING = "review_tune_pending"
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val cached = app.profileStore.load()
            if (cached != null) {
                addLog("Cached profile loaded")
            }
        }
    }

    fun finishSetup(backupDir: String) {
        prefs.edit().putBoolean("setup_done", true).putString("backup_dir", backupDir).apply()
        _isSetupDone.value = true
        _backupStorageDir.value = backupDir
    }

    fun changeBackupDir(newDir: String) {
        prefs.edit().putString("backup_dir", newDir).apply()
        _backupStorageDir.value = newDir
        addLog("Backup dir changed to $newDir")
    }

    fun initDownloadBackupDir() {
        if (prefs.getBoolean("setup_done", false) && prefs.contains("backup_dir")) return
        // DEFAULT to app-private storage, unconditionally.
        //
        // This used to be getExternalFilesDir("backups"), which resolves to
        // /storage/emulated/0/Android/data/<pkg>/files/backups — world-readable to
        // any app holding READ_EXTERNAL_STORAGE on API 26-29 (a normal, install-time
        // permission there). That directory received backups/{id}.json AND the
        // collected plaintext Client.log. filesDir is 0700 and cannot be traversed
        // by another app on any API level, and it needs no runtime grant at all.
        //
        // Users who explicitly want backups in Downloads can still pick that
        // directory in Settings; it just is no longer the silent default.
        val targetDir = File(getApplication<Application>().filesDir, "backups")
        runCatching { targetDir.mkdirs() }
        changeBackupDir(targetDir.absolutePath)
    }

    fun saveGeneratorOptions(opts: GeneratorOptions) {
        try {
            prefs.edit().putString("last_generator_options", gson.toJson(opts)).apply()
        } catch (e: Exception) {
            Log.e("WuWaConfig", "saveGeneratorOptions failed", e)
        }
    }

    fun loadGeneratorOptions(): GeneratorOptions? {
        return try {
            val json = prefs.getString("last_generator_options", null) ?: return null
            return gson.fromJson(json, GeneratorOptions::class.java)
        } catch (e: Exception) {
            Log.e("WuWaConfig", "loadGeneratorOptions failed", e)
            null
        }
    }

    data class ReviewTunePayload(
        val engine: String = "",
        val deviceProfiles: String = "",
        val gameUserSettings: String = "",
        val scalability: String = "",
        val hardware: String = "",
    )

    private val _reviewTunePayload = MutableStateFlow(ReviewTunePayload())
    val reviewTunePayload: StateFlow<ReviewTunePayload> = _reviewTunePayload.asStateFlow()

    private val _reviewTuneNewFiles = MutableStateFlow<Map<String, String>>(emptyMap())
    val reviewTuneNewFiles: StateFlow<Map<String, String>> = _reviewTuneNewFiles.asStateFlow()

    private val _reviewTuneCurrentDeviceLoading = MutableStateFlow<String?>(null)
    val reviewTuneCurrentDeviceLoading: StateFlow<String?> = _reviewTuneCurrentDeviceLoading.asStateFlow()

    private val _reviewTuneCurrentDeviceError = MutableStateFlow<Map<String, String?>>(emptyMap())
    val reviewTuneCurrentDeviceError: StateFlow<Map<String, String?>> = _reviewTuneCurrentDeviceError.asStateFlow()

    private val _reviewTuneCurrentDevice = MutableStateFlow<Map<String, String>>(emptyMap())
    val reviewTuneCurrentDevice: StateFlow<Map<String, String>> = _reviewTuneCurrentDevice.asStateFlow()

    private val _reviewTuneOptions = MutableStateFlow(GeneratorOptions())
    val reviewTuneOptions: StateFlow<GeneratorOptions> = _reviewTuneOptions.asStateFlow()

    fun openReviewTune(
        payload: ReviewTunePayload,
        options: GeneratorOptions,
    ) {
        // The payload lives only in this ViewModel, so a low-memory kill while
        // the screen is open loses it. Persist a breadcrumb so ReviewTuneScreen
        // can say "the generated config was lost" instead of the generic
        // "press Generate" hint.
        prefs.edit().putBoolean(KEY_REVIEW_TUNE_PENDING, true).apply()
        _reviewTunePayload.value = payload
        _reviewTuneOptions.value = options
        _reviewTuneNewFiles.value =
            mapOf(
                "Engine.ini" to payload.engine,
                "DeviceProfiles.ini" to payload.deviceProfiles,
                "GameUserSettings.ini" to payload.gameUserSettings,
                "Scalability.ini" to payload.scalability,
                "Hardware.ini" to payload.hardware,
            )
        _reviewTuneCurrentDevice.value = emptyMap()
        _reviewTuneCurrentDeviceError.value = emptyMap()
    }

    fun updateReviewTuneFile(
        fileName: String,
        content: String,
    ) {
        val cur = _reviewTuneNewFiles.value.toMutableMap()
        if (cur[fileName] == content) return
        cur[fileName] = content
        _reviewTuneNewFiles.value = cur
    }

    fun reloadDeviceFileForReview(fileName: String) {
        viewModelScope.launch {
            _reviewTuneCurrentDeviceLoading.value = fileName
            _reviewTuneCurrentDeviceError.value = _reviewTuneCurrentDeviceError.value - fileName
            configManager.readCurrentConfig(fileName)
                .onSuccess { content ->
                    val cur = _reviewTuneCurrentDevice.value.toMutableMap()
                    cur[fileName] = content
                    _reviewTuneCurrentDevice.value = cur
                }
                .onFailure { e ->
                    _reviewTuneCurrentDeviceError.value = _reviewTuneCurrentDeviceError.value + (fileName to (e.message ?: "unknown error"))
                }
            _reviewTuneCurrentDeviceLoading.value = null
        }
    }

    /**
     * True exactly once after a process death that happened while the Review &
     * Tune screen held a generated payload. Callers must treat the result as
     * consumed (it is cleared here).
     */
    fun consumeReviewTuneLostWarning(): Boolean {
        val pending = prefs.getBoolean(KEY_REVIEW_TUNE_PENDING, false)
        if (pending) prefs.edit().putBoolean(KEY_REVIEW_TUNE_PENDING, false).apply()
        return pending
    }

    fun addLog(
        message: String,
        level: LogLevel = detectLevel(message),
    ) {
        LogRepository.add(message, level)
    }

    private fun detectLevel(message: String): LogLevel =
        when {
            message.startsWith("SUCCESS:") || message.startsWith("SUCCESS ") -> LogLevel.SUCCESS
            message.startsWith("WARNING:") -> LogLevel.WARNING
            message.startsWith("ERROR:") || message.startsWith("FAILED:") || message.startsWith("CRASH:") -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
}
