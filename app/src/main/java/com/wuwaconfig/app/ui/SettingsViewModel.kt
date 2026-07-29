package com.wuwaconfig.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.wuwaconfig.app.WuWaConfigApp
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = WuWaConfigApp.instance

    val themeMode: StateFlow<String> = app.themeMode
    val deployHistoryEnabled: StateFlow<Boolean> = app.deployHistoryEnabled
    val colorfulUi: StateFlow<Boolean> = app.colorfulUi
    val textOpacity: StateFlow<Float> = app.textOpacity

    fun setThemeMode(mode: String) = app.setThemeMode(mode)

    fun setDeployHistoryEnabled(enabled: Boolean) = app.setDeployHistoryEnabled(enabled)

    fun setColorfulUi(enabled: Boolean) = app.setColorfulUi(enabled)

    fun setTextOpacity(value: Float) = app.setTextOpacity(value)

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
}
