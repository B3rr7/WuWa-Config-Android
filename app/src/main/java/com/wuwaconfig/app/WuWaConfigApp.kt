package com.wuwaconfig.app

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

const val PREFS_NAME = "wuwaconfig"

class WuWaConfigApp : Application() {
    init {
        // MUST be the very first thing that runs in this class. Property
        // initialisers below execute before onCreate(), and LogRepository's
        // fallbackBaseDir() dereferences WuWaConfigApp.instance — a LogRepository.add()
        // from any initialiser that lands on that path would otherwise throw
        // UninitializedPropertyAccessException during Application construction,
        // which is unrecoverable. Today that path merely tolerates a null
        // logFile, but that safety is incidental, not designed.
        instance = this
    }

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

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

    /**
     * App-scoped serializer for device operations plus the connection status it
     * guards. Shared by all ViewModels: the backend is app-scoped, so the busy
     * flag, cancel flag, and connection status must be too.
     */
    val deviceOps by lazy { com.wuwaconfig.app.ui.DeviceOps() }

    private val _backendStatus = MutableStateFlow(com.wuwaconfig.app.backend.BackendStatus())
    val backendStatus: kotlinx.coroutines.flow.StateFlow<com.wuwaconfig.app.backend.BackendStatus> = _backendStatus.asStateFlow()

    internal fun setBackendStatus(status: com.wuwaconfig.app.backend.BackendStatus) {
        _backendStatus.value = status
    }

    val backendStatusValue: com.wuwaconfig.app.backend.BackendStatus get() = _backendStatus.value

    // Background appearance (shared with GradientBackground)
    val backgroundImageUri = MutableStateFlow<String?>(null)
    val backgroundVideoUri = MutableStateFlow<String?>(null)
    val backgroundOpacity = MutableStateFlow(0.25f)

    // Cross-cutting settings (shared across ViewModels)
    val themeMode = MutableStateFlow("system")
    val textOpacity = MutableStateFlow(1f)
    val fontFamilyName = MutableStateFlow("Default")
    val fontScale = MutableStateFlow(1f)
    val colorSaturation = MutableStateFlow(1f)
    val colorfulUi = MutableStateFlow(true)
    val deployHistoryEnabled = MutableStateFlow(true)
    val hashMonitorEnabled = MutableStateFlow(true)
    val allowRestrictedCvarsEnabled = MutableStateFlow(true)
    val forceCSharpEnv = MutableStateFlow(false)
    val chipsetInfo = ChipsetDetector.detect()
    val gameConfigDir = GamePaths.TARGET_DIR

    override fun onCreate() {
        // Idempotent re-assertion of the init {} block; see the comment there.
        instance = this
        super.onCreate()
        adbCrypto = AdbCrypto(this)
        // RSA key generation + EncryptedFile I/O is heavy; pre-load off the main
        // thread so it never blocks cold start or the first ADB connection.
        appScope.launch(Dispatchers.IO) { adbCrypto.warmUp() }
        LogRepository.init()
        cvarDatabase = CvarDatabase(assets)
        configGenerator = ConfigGenerator(cvarDatabase)
        // Disk stats + Downloads listing have no business on the main thread.
        appScope.launch(Dispatchers.IO) { cleanupOldClientLogs() }
        appScope.launch { cvarDatabase.load() }
        deployHistoryStore = DeployHistoryStore(File(filesDir, "deploy_history.json"))
        profileStore = ProfileStore(File(filesDir, "player_profile.json"))
        backgroundImageUri.value = prefs.getString("bg_image_uri", null)
        backgroundVideoUri.value = prefs.getString("bg_video_uri", null)
        backgroundOpacity.value = prefs.getFloat("bg_opacity", 0.25f)
        themeMode.value = prefs.getString("theme_mode", "system") ?: "system"
        textOpacity.value = prefs.getFloat("text_opacity", 1f)
        fontFamilyName.value = prefs.getString("font_family", "Default") ?: "Default"
        fontScale.value = prefs.getFloat("font_scale", 1f)
        colorSaturation.value = prefs.getFloat("color_saturation", 1f)
        colorfulUi.value = prefs.getBoolean("colorful_ui", true)
        deployHistoryEnabled.value = prefs.getBoolean("deploy_history", true)
        hashMonitorEnabled.value = prefs.getBoolean("hash_monitor_enabled", true)
        allowRestrictedCvarsEnabled.value = prefs.getBoolean("allow_restricted_cvars", true)
        forceCSharpEnv.value = prefs.getBoolean("force_csharp_env", false)

        // Warm the default backend off the main thread. The lazy `backend`
        // getter otherwise performs construction (and, for SAF, a
        // SharedPreferences disk read) on whatever thread touches it first,
        // which can be the main thread via a ViewModel init. This changes no
        // observable connect semantics: the getter is unchanged and still
        // creates the backend on demand if this has not landed yet, and
        // switchTo() can still replace it at any time.
        appScope.launch(Dispatchers.IO) {
            val needed =
                synchronized(backendLock) {
                    _backend == null && currentMethod == AccessMethod.ADB
                }
            if (!needed) return@launch
            val created = createBackend(AccessMethod.ADB)
            val discard =
                synchronized(backendLock) {
                    if (_backend == null && currentMethod == AccessMethod.ADB) {
                        _backend = created
                        false
                    } else {
                        true
                    }
                }
            if (discard) created.disconnect()
        }
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

    fun setFontFamily(name: String) {
        prefs.edit().putString("font_family", name).apply()
        fontFamilyName.value = name
    }

    fun setFontScale(value: Float) {
        val clamped = value.coerceIn(0.75f, 1.5f)
        prefs.edit().putFloat("font_scale", clamped).apply()
        fontScale.value = clamped
    }

    fun setColorSaturation(value: Float) {
        val clamped = value.coerceIn(0.5f, 1.6f)
        prefs.edit().putFloat("color_saturation", clamped).apply()
        colorSaturation.value = clamped
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

    fun setAllowRestrictedCvarsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("allow_restricted_cvars", enabled).apply()
        allowRestrictedCvarsEnabled.value = enabled
    }

    fun setForceCSharpEnv(enabled: Boolean) {
        prefs.edit().putBoolean("force_csharp_env", enabled).apply()
        forceCSharpEnv.value = enabled
    }

    fun switchTo(method: AccessMethod): AccessBackend {
        // Both halves of this method are I/O: disconnect() closes a live ADB
        // socket / unbinds the Shizuku UserService, and createBackend() for SAF
        // reads SharedPreferences. Doing either under `backendLock` holds a
        // global lock across disk/network work that the `backend` getter can
        // contend with from the main thread — an ANR risk. So: take the old
        // backend and publish the new method under the lock, then do the slow
        // work outside it, then swap the reference under the lock again.
        val previous =
            synchronized(backendLock) {
                val old = _backend
                currentMethod = method
                // Publish null first so a concurrent `backend` read never
                // constructs a SECOND backend for the old method.
                _backend = null
                old
            }
        previous?.disconnect()

        val created = createBackend(method)
        val active =
            synchronized(backendLock) {
                if (_backend == null) {
                    _backend = created
                    null
                } else {
                    // A concurrent switchTo() won the race; its backend is
                    // already active, so drop ours rather than leak it.
                    _backend
                }
            }
        if (active != null) {
            created.disconnect()
            return active
        }
        return created
    }

    private fun createBackend(method: AccessMethod): AccessBackend {
        return when (method) {
            AccessMethod.ADB -> AdbBackend(adbCrypto)
            AccessMethod.SHIZUKU -> ShizukuBackend(this)
            AccessMethod.ROOT -> RootBackend()
            AccessMethod.SAF -> SafBackend(this).also { it.restoreTreeUri() }
        }
    }

    private fun cleanupOldClientLogs() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val dirs =
            listOfNotNull(
                File(filesDir, "backups"),
                LogRepository.publicBaseDir()?.let { File(it.absolutePath) },
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
