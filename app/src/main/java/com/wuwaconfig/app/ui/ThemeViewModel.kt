package com.wuwaconfig.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wuwaconfig.app.data.ThemeConfig
import com.wuwaconfig.app.data.ThemePreferencesRepository
import com.wuwaconfig.app.ui.theme.ColorPalette
import com.wuwaconfig.app.ui.theme.UiStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the theme configuration for the whole process.
 *
 * ## Why one StateFlow and not seven
 *
 * The theme is applied as a unit. Seven independent flows would let
 * `WuWaConfigTheme` recompose with the new palette resolved against the old
 * mode — a visible flash on any change that touches two keys at once, and a
 * guaranteed mismatch if the user taps quickly. [ThemeConfig] makes that
 * intermediate state unrepresentable.
 *
 * ## Why the repository is resolved here and not injected
 *
 * This project has no Hilt (see `AGENTS.md`: the AppFunction service uses the
 * same service-locator pattern for the same reason), so the repository is built
 * against `getApplication()`. It takes `Context`, not `WuWaConfigApp`, so it
 * stays unit-testable with a bare application context.
 *
 * ## Why `WhileSubscribed(5_000)`
 *
 * The theme must not be dropped when the last collector goes away: this
 * ViewModel is Activity-scoped and survives every navigation, and the config is
 * needed again the moment the app returns to the foreground. A 5s grace period
 * also absorbs the collection gap during a configuration change.
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ThemePreferencesRepository(application)

    /**
     * Seeded with [ThemeConfig]'s defaults, which are the enum defaults, which
     * are `MATERIAL_YOU` + `SYSTEM_DEFAULT`.
     *
     * This is what renders before DataStore has loaded — one frame on a cold
     * start. It is deliberately the same value the user would get by default, so
     * the pre-load frame and the loaded state agree and there is no flash even
     * for a user who did pick something else.
     */
    val themeConfig: StateFlow<ThemeConfig> =
        repository.flow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ThemeConfig(),
        )

    init {
        // Recovery has to happen before anything reads the config, otherwise the
        // first emission carries defaults for a user who had real settings in
        // SharedPreferences. Running it in init rather than lazily is the point:
        // the migration is idempotent (guarded by its own flag), so a second
        // launch costs one DataStore read and no writes.
        viewModelScope.launch {
            repository.migrateLegacyIfNeeded()
        }
    }

    fun setUiStyle(style: UiStyle) = persist { repository.setUiStyle(style) }

    fun setColorPalette(palette: ColorPalette) = persist { repository.setColorPalette(palette) }

    fun setThemeMode(mode: String) = persist { repository.setThemeMode(mode) }

    fun setTextOpacity(value: Float) = persist { repository.setTextOpacity(value) }

    fun setFontFamily(name: String) = persist { repository.setFontFamily(name) }

    fun setFontScale(value: Float) = persist { repository.setFontScale(value) }

    fun setColorSaturation(value: Float) = persist { repository.setColorSaturation(value) }

    fun setDynamicColor(enabled: Boolean) = persist { repository.setDynamicColor(enabled) }

    fun resetToDefaults() = persist { repository.reset() }

    /**
     * Every write goes through here so a disk failure cannot silently desync the
     * toggle from the rendered theme.
     *
     * Without this, `setColorPalette` would update the DataStore optimistically
     * in memory and then throw on disk; the flow would keep the new value for
     * this process and the old one after a restart, which is the "toggle shows
     * ON with nothing behind it" failure `AGENTS.md` already calls out for
     * `allowRestrictedCvars`. The write is logged and dropped — there is no
     * second source of truth to reconcile against.
     */
    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { error ->
                    android.util.Log.w("ThemeViewModel", "Could not persist theme setting", error)
                }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
