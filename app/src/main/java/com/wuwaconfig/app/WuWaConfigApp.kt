package com.wuwaconfig.app

import android.app.Application
import android.content.Context
import android.os.Environment
import com.wuwaconfig.app.adb.AdbCrypto
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.AdbBackend
import com.wuwaconfig.app.backend.RootBackend
import com.wuwaconfig.app.backend.SafBackend
import com.wuwaconfig.app.backend.ShizukuBackend
import com.wuwaconfig.app.config.ChipsetDetector
import com.wuwaconfig.app.config.ConfigGenerator
import com.wuwaconfig.app.config.CvarDatabase
import com.wuwaconfig.app.config.DeployHistoryStore
import com.wuwaconfig.app.config.ProfileStore
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class WuWaConfigApp : Application() {
    private val prefs by lazy { getSharedPreferences("wuwaconfig", Context.MODE_PRIVATE) }

    lateinit var adbCrypto: AdbCrypto
        private set

    lateinit var cvarDatabase: CvarDatabase
        private set

    lateinit var configGenerator: ConfigGenerator
        private set

    lateinit var deployHistoryStore: DeployHistoryStore
        private set

    lateinit var profileStore: ProfileStore
        private set

    private var _backend: AccessBackend? = null
    private val backendLock = Any()
    val backend: AccessBackend get() {
        synchronized(backendLock) {
            if (_backend == null) {
                _backend = createBackend(currentMethod)
            }
            return _backend!!
        }
    }

    var currentMethod: AccessMethod = AccessMethod.ADB
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Background appearance (shared with GradientBackground)
    val backgroundImageUri = MutableStateFlow<String?>(null)
    val backgroundVideoUri = MutableStateFlow<String?>(null)
    val backgroundOpacity = MutableStateFlow(0.25f)

    // Cross-cutting settings (shared across ViewModels)
    val themeMode = MutableStateFlow("system")
    val textOpacity = MutableStateFlow(1f)
    val colorfulUi = MutableStateFlow(true)
    val deployHistoryEnabled = MutableStateFlow(true)
    val hashMonitorEnabled = MutableStateFlow(true)
    val chipsetInfo = ChipsetDetector.detect()
    val gameConfigDir = GamePaths.TARGET_DIR

    override fun onCreate() {
        super.onCreate()
        adbCrypto = AdbCrypto(this)
        instance = this
        _backend = null
        LogRepository.init()
        cleanupOldClientLogs()
        cvarDatabase = CvarDatabase(assets)
        configGenerator = ConfigGenerator(cvarDatabase)
        appScope.launch { cvarDatabase.load() }
        deployHistoryStore = DeployHistoryStore(File(filesDir, "deploy_history.json"))
        profileStore = ProfileStore(File(filesDir, "player_profile.json"))
        backgroundImageUri.value = prefs.getString("bg_image_uri", null)
        backgroundVideoUri.value = prefs.getString("bg_video_uri", null)
        backgroundOpacity.value = prefs.getFloat("bg_opacity", 0.25f)
        themeMode.value = prefs.getString("theme_mode", "system") ?: "system"
        textOpacity.value = prefs.getFloat("text_opacity", 1f)
        colorfulUi.value = prefs.getBoolean("colorful_ui", true)
        deployHistoryEnabled.value = prefs.getBoolean("deploy_history", true)
        hashMonitorEnabled.value = prefs.getBoolean("hash_monitor_enabled", true)
    }

    fun setBackgroundState(
        imageUri: String?,
        videoUri: String?,
        opacity: Float,
    ) {
        if (imageUri != null) {
            prefs.edit().putString("bg_image_uri", imageUri).apply()
        } else {
            prefs.edit().remove("bg_image_uri").apply()
        }
        if (videoUri != null) {
            prefs.edit().putString("bg_video_uri", videoUri).apply()
        } else {
            prefs.edit().remove("bg_video_uri").apply()
        }
        prefs.edit().putFloat("bg_opacity", opacity).apply()
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        themeMode.value = mode
    }

    fun setTextOpacity(value: Float) {
        val clamped = value.coerceIn(0.5f, 1f)
        prefs.edit().putFloat("text_opacity", clamped).apply()
        textOpacity.value = clamped
    }

    fun setColorfulUi(enabled: Boolean) {
        prefs.edit().putBoolean("colorful_ui", enabled).apply()
        colorfulUi.value = enabled
    }

    fun setDeployHistoryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("deploy_history", enabled).apply()
        deployHistoryEnabled.value = enabled
    }

    fun setHashMonitorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("hash_monitor_enabled", enabled).apply()
        hashMonitorEnabled.value = enabled
    }

    fun switchTo(method: AccessMethod): AccessBackend {
        synchronized(backendLock) {
            currentMethod = method
            _backend?.disconnect()
            _backend = null
            val newBackend = createBackend(method)
            _backend = newBackend
            return newBackend
        }
    }

    private fun createBackend(method: AccessMethod): AccessBackend {
        return when (method) {
            AccessMethod.ADB -> AdbBackend(adbCrypto)
            AccessMethod.SHIZUKU -> ShizukuBackend()
            AccessMethod.ROOT -> RootBackend()
            AccessMethod.SAF -> SafBackend(this).also { it.restoreTreeUri() }
        }
    }

    private fun cleanupOldClientLogs() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val dirs =
            listOf(
                File(filesDir, "backups"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WuWaConfig"),
            )
        for (dir in dirs) {
            val file = File(dir, "Client.log")
            if (file.exists() && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    companion object {
        lateinit var instance: WuWaConfigApp
            private set
    }
}
