package com.wuwaconfig.app.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app: WuWaConfigApp =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("MainViewModel requires WuWaConfigApp application")

    val configGenerator get() = app.configGenerator
    val cvarDatabase get() = app.cvarDatabase

    private val configManager: ConfigManager
        get() = ConfigManager(app, app.backend)

    private val prefs = application.getSharedPreferences("wuwaconfig", Context.MODE_PRIVATE)

    val deployHistoryEnabled: StateFlow<Boolean> = app.deployHistoryEnabled
    val colorfulUi: StateFlow<Boolean> = app.colorfulUi
    val chipsetInfo = app.chipsetInfo
    val gameConfigDir: String = app.gameConfigDir

    val backupStorageDir: String
        get() = prefs.getString("backup_dir", defaultBackupDir) ?: defaultBackupDir

    private val defaultBackupDir = application.filesDir.resolve("backups").absolutePath

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
        val cached = app.profileStore.load()
        if (cached != null) {
            addLog("Cached profile loaded")
        }
    }

    fun finishSetup(backupDir: String) {
        prefs.edit().putBoolean("setup_done", true).putString("backup_dir", backupDir).apply()
    }

    fun changeBackupDir(newDir: String) {
        prefs.edit().putString("backup_dir", newDir).apply()
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

    fun saveGeneratorOptions(opts: GeneratorOptions) {
        try {
            prefs.edit().putString("last_generator_options", Gson().toJson(opts)).apply()
        } catch (e: Exception) {
            Log.e("WuWaConfig", "saveGeneratorOptions failed", e)
        }
    }

    fun loadGeneratorOptions(): GeneratorOptions? {
        return try {
            val json = prefs.getString("last_generator_options", null) ?: return null
            Gson().fromJson(json, GeneratorOptions::class.java)
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

    private val _reviewTuneCurrentDeviceError = MutableStateFlow<String?>(null)
    val reviewTuneCurrentDeviceError: StateFlow<String?> = _reviewTuneCurrentDeviceError.asStateFlow()

    private val _reviewTuneCurrentDevice = MutableStateFlow<Map<String, String>>(emptyMap())
    val reviewTuneCurrentDevice: StateFlow<Map<String, String>> = _reviewTuneCurrentDevice.asStateFlow()

    private val _reviewTuneOptions = MutableStateFlow(GeneratorOptions())
    val reviewTuneOptions: StateFlow<GeneratorOptions> = _reviewTuneOptions.asStateFlow()

    fun openReviewTune(
        payload: ReviewTunePayload,
        options: GeneratorOptions,
    ) {
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
        _reviewTuneCurrentDeviceError.value = null
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
            _reviewTuneCurrentDeviceError.value = null
            configManager.readCurrentConfig(fileName)
                .onSuccess { content ->
                    val cur = _reviewTuneCurrentDevice.value.toMutableMap()
                    cur[fileName] = content
                    _reviewTuneCurrentDevice.value = cur
                }
                .onFailure { e ->
                    _reviewTuneCurrentDeviceError.value = "$fileName: ${e.message ?: "unknown error"}"
                }
            _reviewTuneCurrentDeviceLoading.value = null
        }
    }

    fun addLog(
        message: String,
        level: LogLevel = detectLevel(message),
    ) {
        com.wuwaconfig.app.model.LogRepository.add(message, level)
    }

    private fun detectLevel(message: String): LogLevel =
        when {
            message.startsWith("SUCCESS:") || message.startsWith("SUCCESS ") -> LogLevel.SUCCESS
            message.startsWith("WARNING:") -> LogLevel.WARNING
            message.startsWith("ERROR:") || message.startsWith("FAILED:") || message.startsWith("CRASH:") -> LogLevel.ERROR
            else -> LogLevel.INFO
        }
}
