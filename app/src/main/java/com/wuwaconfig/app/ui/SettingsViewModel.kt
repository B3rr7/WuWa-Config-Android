package com.wuwaconfig.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.BuildConfig
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.ConfigManager
import com.wuwaconfig.app.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val configManager: ConfigManager by lazy {
        ConfigManager(getApplication(), { app.backend }, null)
    }

    /**
     * Live read of the game's command-line file. Auto-checked on Settings open so
     * the card reflects reality even if the file was changed outside the app.
     */
    private val _csharpEnvState = MutableStateFlow<CSharpEnvState>(CSharpEnvState.Unknown)
    val csharpEnvState: StateFlow<CSharpEnvState> = _csharpEnvState.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun setThemeMode(mode: String) = app.setThemeMode(mode)

    fun setDeployHistoryEnabled(enabled: Boolean) = app.setDeployHistoryEnabled(enabled)

    fun setColorfulUi(enabled: Boolean) = app.setColorfulUi(enabled)

    fun setHashMonitorEnabled(enabled: Boolean) = app.setHashMonitorEnabled(enabled)

    /**
     * Toggles the game's runtime C# optimization environment. The preference is
     * persisted immediately; the command-line file is rewritten in the background
     * so the change takes effect on the next game launch.
     */
    fun setForceCSharpEnv(enabled: Boolean) {
        app.setForceCSharpEnv(enabled)
        viewModelScope.launch(Dispatchers.IO) { configManager.syncForceCSharpEnv(enabled) }
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

    fun installNow() {
        val ready = (_updateState.value as? UpdateState.Ready) ?: return
        UpdateManager.openForInstall(getApplication(), ready.file)
    }
}
