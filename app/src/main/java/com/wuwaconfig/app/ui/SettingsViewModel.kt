package com.wuwaconfig.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.BuildConfig
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class Available(val info: UpdateManager.UpdateInfo) : UpdateState

    data class Downloading(val progress: Int) : UpdateState

    data class Ready(val file: File, val notes: String, val versionName: String) : UpdateState

    data object NoUpdate : UpdateState

    data class Error(val message: String) : UpdateState
}

/** Live state of the game's C# optimization environment (auto-checked). */
sealed interface CSharpEnvState {
    data object Enabled : CSharpEnvState

    data object Disabled : CSharpEnvState

    data object Unknown : CSharpEnvState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app =
        application as? WuWaConfigApp
            ?: throw IllegalStateException("SettingsViewModel requires WuWaConfigApp application")

    val themeMode: StateFlow<String> = app.themeMode
    val deployHistoryEnabled: StateFlow<Boolean> = app.deployHistoryEnabled
    val colorfulUi: StateFlow<Boolean> = app.colorfulUi
    val hashMonitorEnabled: StateFlow<Boolean> = app.hashMonitorEnabled
    val textOpacity: StateFlow<Float> = app.textOpacity
    val fontFamilyName: StateFlow<String> = app.fontFamilyName
    val fontScale: StateFlow<Float> = app.fontScale
    val colorSaturation: StateFlow<Float> = app.colorSaturation
    val forceCSharpEnv: StateFlow<Boolean> = app.forceCSharpEnv

    // Read by MainActivity, which feeds them into LocalBackgroundSettings so
    // GradientBackground no longer has to reach for the app singleton itself.
    val backgroundImageUri: StateFlow<String?> = app.backgroundImageUri
    val backgroundVideoUri: StateFlow<String?> = app.backgroundVideoUri
    val backgroundOpacity: StateFlow<Float> = app.backgroundOpacity

    private val configManager: ConfigManager by lazy {
        ConfigManager(getApplication(), { app.backend }, { null })
    }

    /**
     * Live read of the game's command-line file. Auto-checked on Settings open so
     * the card reflects reality even if the file was changed outside the app.
     */
    private val _csharpEnvState = MutableStateFlow<CSharpEnvState>(CSharpEnvState.Unknown)
    val csharpEnvState: StateFlow<CSharpEnvState> = _csharpEnvState.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val ops get() = app.deviceOps

    private var csharpEnvJob: Job? = null

    fun setThemeMode(mode: String) = app.setThemeMode(mode)

    fun setDeployHistoryEnabled(enabled: Boolean) = app.setDeployHistoryEnabled(enabled)

    fun setColorfulUi(enabled: Boolean) = app.setColorfulUi(enabled)

    fun setHashMonitorEnabled(enabled: Boolean) = app.setHashMonitorEnabled(enabled)

    /**
     * Toggles the game's runtime C# optimization environment. Preference is
     * persisted optimistically; if the file operation fails the live state is
     * refreshed and the preference is reverted so toggle and game state stay in
     * sync (critical for disable path — user must not see OFF while file still
     * exists due to permission/transport error).
     *
     * Serialised through [DeviceOps] and guarded by [csharpEnvJob]: two rapid
     * taps used to race on the filesystem, and a concurrent deploy could
     * interleave a device write mid-toggle.
     */
    fun setForceCSharpEnv(enabled: Boolean) {
        if (csharpEnvJob?.isActive == true) return
        csharpEnvJob =
            ops.launchBackendOp(managesBusyFlag = false) {
                // The optimistic pref write lives INSIDE the locked block: if the
                // request is dropped on the busy path, nothing must be written at
                // all, or the toggle would show ON with the file untouched.
                app.setForceCSharpEnv(enabled)
                withContext(Dispatchers.IO) {
                    val result = configManager.syncForceCSharpEnv(enabled)
                    if (result.isFailure) {
                        // Revert preference — file didn't move, don't lie to UI.
                        app.setForceCSharpEnv(!enabled)
                    }
                }
                // Always refresh live game state after the file operation settles.
                refreshCSharpEnvState()
            }
    }

    /**
     * Auto-check: reads the game's UE4CommandLine.txt and reports the live state.
     * - [CSharpEnvState.Enabled]  → file contains -ForceEnableCSharpEnvironment (C# is on)
     * - [CSharpEnvState.Disabled] → absent / default uproject path (old JS path)
     * - [CSharpEnvState.Unknown]  → file unreadable (no backend, permission denied, etc.)
     */
    fun refreshCSharpEnvState() {
        viewModelScope.launch(Dispatchers.IO) {
            _csharpEnvState.value =
                when (configManager.readForceCSharpEnv().getOrNull()) {
                    true -> CSharpEnvState.Enabled
                    false -> CSharpEnvState.Disabled
                    null -> CSharpEnvState.Unknown
                }
        }
    }

    fun setTextOpacity(value: Float) = app.setTextOpacity(value)

    fun setFontFamily(name: String) = app.setFontFamily(name)

    fun setFontScale(value: Float) = app.setFontScale(value)

    fun setColorSaturation(value: Float) = app.setColorSaturation(value)

    fun setBackgroundImageUri(uri: String?) {
        app.backgroundImageUri.value = uri
        app.setBackgroundState(uri, app.backgroundVideoUri.value, app.backgroundOpacity.value)
    }

    fun setBackgroundVideoUri(uri: String?) {
        app.backgroundVideoUri.value = uri
        app.setBackgroundState(app.backgroundImageUri.value, uri, app.backgroundOpacity.value)
    }

    fun setBackgroundOpacity(opacity: Float) {
        app.backgroundOpacity.value = opacity
        app.setBackgroundState(app.backgroundImageUri.value, app.backgroundVideoUri.value, opacity)
    }

    fun checkForUpdates() {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            val result = UpdateManager.fetchLatest()
            if (result.isFailure) {
                _updateState.value = UpdateState.Error(result.exceptionOrNull()?.message ?: "Update check failed")
                return@launch
            }
            val info = result.getOrThrow()
            if (UpdateManager.isNewer(info.tag, BuildConfig.VERSION_NAME)) {
                _updateState.value = UpdateState.Available(info)
            } else {
                _updateState.value = UpdateState.NoUpdate
            }
        }
    }

    fun downloadAndInstall() {
        val info = (_updateState.value as? UpdateState.Available)?.info ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState.Downloading(0)
            val dest = UpdateManager.downloadedApk(getApplication())
            val download =
                UpdateManager.download(info.apkUrl, dest) { progress ->
                    _updateState.value = UpdateState.Downloading(progress)
                }
            if (download.isFailure) {
                _updateState.value = UpdateState.Error(download.exceptionOrNull()?.message ?: "Download failed")
                return@launch
            }
            if (!UpdateManager.verifySignatureMatchesInstalled(getApplication(), dest)) {
                dest.delete()
                _updateState.value = UpdateState.Error("Update verification failed (signature mismatch)")
                return@launch
            }
            _updateState.value = UpdateState.Ready(dest, info.notes, info.versionName)
        }
    }

    /**
     * Emits the system "allow from this source" screen when the per-app install
     * permission is missing. A ViewModel must not call startActivity, so this is a
     * one-shot event that MainActivity collects — the same shape as the existing
     * All-Files-Access flow in MainActivity.requestStoragePermissions().
     */
    private val _installPermissionRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val installPermissionRequest: SharedFlow<Unit> = _installPermissionRequest.asSharedFlow()

    /** The update we were trying to install, held while the user grants the permission. */
    private var pendingInstall: UpdateState.Ready? = null

    fun installNow() {
        val ready = (_updateState.value as? UpdateState.Ready) ?: return
        openInstaller(ready)
    }

    /**
     * Call from MainActivity's activity-result callback. Retries the install if the
     * grant was granted, and reports it plainly if it was not — otherwise the user is
     * left tapping a button that silently does nothing.
     */
    fun onInstallPermissionResult() {
        val pending = pendingInstall ?: return
        pendingInstall = null
        val context = getApplication<Application>()
        val stillBlocked =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
        if (stillBlocked) {
            _updateState.value = UpdateState.Error("Permission to install apps was not granted")
            return
        }
        _updateState.value = pending
        openInstaller(pending)
    }

    private fun openInstaller(ready: UpdateState.Ready) {
        val context = getApplication<Application>()
        UpdateManager.openForInstall(context, ready.file).onFailure { error ->
            val message = error.message
            if (message == UpdateManager.NEEDS_INSTALL_PERMISSION) {
                // Route to the per-app "allow from this source" screen rather than
                // surfacing the internal marker as user-facing error text.
                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    )
                // Kiosk / stripped / some Chinese ROMs may ship no Settings handler for
                // this action — fail with a clear message instead of crashing on
                // ActivityNotFoundException (same guard as UpdateManager).
                if (intent.resolveActivity(context.packageManager) == null) {
                    _updateState.value =
                        UpdateState.Error("This device has no screen for granting install permission")
                    return@onFailure
                }
                pendingInstall = ready
                _updateState.value =
                    UpdateState.Error("Grant permission to install apps, then tap Install again")
                _installPermissionRequest.tryEmit(Unit)
            } else {
                _updateState.value = UpdateState.Error(message ?: "Could not open installer")
            }
        }
    }
}
