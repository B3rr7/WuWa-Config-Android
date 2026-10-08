package com.wuwaconfig.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wuwaconfig.app.ui.theme.ColorPalette
import com.wuwaconfig.app.ui.theme.UiStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The single persisted theme configuration, as one immutable value.
 *
 * One object rather than seven separate flows because the theme is applied as a
 * unit in `WuWaConfigTheme`: with independent flows, changing the palette and the
 * mode in quick succession produces a frame where the new palette has been
 * resolved against the old mode. A single state object makes that
 * intermediate-frame unrepresentable.
 *
 * Persisted by **name** for the two enums, never by ordinal — see [UiStyle].
 */
data class ThemeConfig(
    val uiStyle: UiStyle = UiStyle.DEFAULT,
    val colorPalette: ColorPalette = ColorPalette.DEFAULT,
    val themeMode: String = "system",
    val textOpacity: Float = 1f,
    val fontFamilyName: String = "Default",
    val fontScale: Float = 1f,
    val colorSaturation: Float = 1f,
    val dynamicColor: Boolean = false,
)

private const val DATASTORE_NAME = "theme_preferences"

/**
 * Must match `WuWaConfigApp.PREFS_NAME`, which is a top-level `const val` in
 * `com.wuwaconfig.app` and therefore importable — so it is referenced rather
 * than duplicated. A literal copy here would silently stop matching the day
 * someone changed the name, and the migration would then read an empty store
 * and reset every existing user's theme.
 */
private const val LEGACY_PREFS_NAME = com.wuwaconfig.app.PREFS_NAME

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = DATASTORE_NAME)

private object Keys {
    val UI_STYLE = stringPreferencesKey("ui_style")
    val COLOR_PALETTE = stringPreferencesKey("color_palette")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val TEXT_OPACITY = floatPreferencesKey("text_opacity")
    val FONT_FAMILY = stringPreferencesKey("font_family")
    val FONT_SCALE = floatPreferencesKey("font_scale")
    val COLOR_SATURATION = floatPreferencesKey("color_saturation")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val LEGACY_MIGRATED = booleanPreferencesKey("legacy_migrated")

    /**
     * Name-to-key lookup for the migration, which is typed as a
     * `Map<String, Any?>` and therefore cannot hold the typed keys directly.
     *
     * Deliberately does not return null for an unknown name: every key it is
     * asked about comes from [LegacyThemeKeys], a closed set of five literals
     * asserted against this table by `ThemePersistenceTest`. An unknown name
     * there would be a programming error, and returning a silently-inert key
     * would drop a user setting instead of failing loudly.
     */
    fun stringKey(name: String) = stringPreferencesKey(name)

    fun floatKey(name: String) = floatPreferencesKey(name)
}

/**
 * Persistence for the theme engine.
 *
 * ## Why DataStore and not the project's SharedPreferences
 *
 * Every other preference in this app uses SharedPreferences, so this is the odd
 * one out. Two reasons justify it:
 *
 * 1. **Read-before-first-frame.** `WuWaConfigTheme` needs the selection before
 *    the first composition, and a `StateFlow` seeded from DataStore gives a
 *    suspend `first()` that is already in memory once [flow] has been collected
 *    — versus a synchronous `prefs.getString` on the main thread during
 *    `Application.onCreate`.
 * 2. **Atomic multi-key writes.** A `ThemeConfig` is written with one `edit {}`
 *    transaction. SharedPreferences' `apply()` is fire-and-forget and a
 *    `commit()` on the main thread is a StrictMode disk-write violation, so a
 *    five-key config change could be observed half-written.
 *
 * ## The legacy read-through
 *
 * Five of these keys ([Keys.THEME_MODE] and below) previously lived in
 * SharedPreferences under different names, written by `WuWaConfigApp`. They are
 * read once on first launch and copied forward, then the old values are removed
 * so there is exactly one source of truth. Without this, every existing install
 * would come back with a default theme mode and a default font — a silent
 * settings loss on upgrade, which is the failure mode `AGENTS.md` flags for
 * unreadable-but-written state.
 */
class ThemePreferencesRepository(
    private val context: Context,
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE),
) {
    /**
     * The current configuration, including anything recovered from the legacy
     * store.
     *
     * `catch` maps a read IOException to the defaults rather than propagating:
     * a corrupt or truncated `theme_preferences.preferences_pb` must not stop the
     * app from starting. Every `IOException` here means "the file is
     * unreadable", and rethrowing would leave the user with a permanently
     * unopenable app over a cosmetic setting.
     */
    val flow: Flow<ThemeConfig> =
        context.themeDataStore.data
            .catch { cause ->
                if (cause is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw cause
            }.map { prefs -> prefs.toThemeConfig() }

    /**
     * One-shot read, for the seeding call before the first frame.
     *
     * [flow] must have been collected at least once for DataStore to have loaded;
     * calling this first is safe because DataStore serves [first] from its
     * in-memory actor either way.
     */
    suspend fun current(): ThemeConfig = flow.first()

    suspend fun setUiStyle(style: UiStyle) = put { it[Keys.UI_STYLE] = style.name }

    suspend fun setColorPalette(palette: ColorPalette) = put { it[Keys.COLOR_PALETTE] = palette.name }

    suspend fun setThemeMode(mode: String) = put { it[Keys.THEME_MODE] = mode }

    suspend fun setTextOpacity(value: Float) = put { it[Keys.TEXT_OPACITY] = ThemeDefaults.clampTextOpacity(value) }

    suspend fun setFontFamily(name: String) = put { it[Keys.FONT_FAMILY] = name }

    suspend fun setFontScale(value: Float) = put { it[Keys.FONT_SCALE] = ThemeDefaults.clampFontScale(value) }

    suspend fun setColorSaturation(value: Float) = put { it[Keys.COLOR_SATURATION] = ThemeDefaults.clampSaturation(value) }

    suspend fun setDynamicColor(enabled: Boolean) = put { it[Keys.DYNAMIC_COLOR] = enabled }

    /**
     * Replaces the whole configuration in one transaction.
     *
     * Used by the "reset to defaults" action. A single `edit` so the theme can
     * never be observed with, say, a restored palette and a stale mode.
     */
    suspend fun reset() {
        context.themeDataStore.edit { prefs ->
            val defaults = ThemeConfig()
            prefs[Keys.UI_STYLE] = defaults.uiStyle.name
            prefs[Keys.COLOR_PALETTE] = defaults.colorPalette.name
            prefs[Keys.THEME_MODE] = defaults.themeMode
            prefs[Keys.TEXT_OPACITY] = defaults.textOpacity
            prefs[Keys.FONT_FAMILY] = defaults.fontFamilyName
            prefs[Keys.FONT_SCALE] = defaults.fontScale
            prefs[Keys.COLOR_SATURATION] = defaults.colorSaturation
            prefs[Keys.DYNAMIC_COLOR] = defaults.dynamicColor
        }
    }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.themeDataStore.edit(block)
    }

    /**
     * Copies the five pre-existing SharedPreferences keys forward, once.
     *
     * Guarded by a flag rather than by key presence so that a key the user
     * legitimately deleted from DataStore (there is no such path today, but a
     * "reset" that omitted a key would otherwise be undone by this running
     * again) is not resurrected on every launch.
     *
     * The old keys are removed on success. On failure the flag is not written,
     * so the next launch retries — which is what we want, because a failed
     * migration would otherwise leave the settings permanently stranded in the
     * legacy store.
     */
    suspend fun migrateLegacyIfNeeded() {
        if (context.themeDataStore.data.first()[Keys.LEGACY_MIGRATED] == true) return

        // Read the legacy store eagerly: SharedPreferences.getX is a disk read
        // the first time, and doing it inside the edit{} block would run it on
        // DataStore's transaction thread while holding its lock.
        val legacyValues =
            LegacyThemeKeys.ALL.associateWith { key ->
                when (key) {
                    LegacyThemeKeys.THEME_MODE, LegacyThemeKeys.FONT_FAMILY ->
                        legacyPrefs.getString(key, null)

                    else -> legacyPrefs.getFloat(key, Float.NaN).takeUnless { it.isNaN() }
                }
            }

        val existing = context.themeDataStore.data.first()
        val present =
            LegacyThemeKeys.ALL.filterTo(mutableSetOf()) { key -> legacyKeyIsPresent(existing, key) }
        val plan = planLegacyMigration(alreadyMigrated = false, presentKeys = present, legacy = legacyValues)

        context.themeDataStore.edit { prefs ->
            for ((key, value) in plan) {
                when (value) {
                    is Float -> prefs[Keys.floatKey(key)] = value
                    is String -> prefs[Keys.stringKey(key)] = value
                    else -> Unit
                }
            }
            prefs[Keys.LEGACY_MIGRATED] = true
        }

        // Only now that the copy has landed. If the edit above throws, the flag
        // is never written and this removal never runs, so the next launch
        // retries rather than leaving the settings stranded in the old store.
        legacyPrefs.edit().apply {
            for (key in LegacyThemeKeys.ALL) remove(key)
        }.apply()
    }

    /**
     * Whether the migration should treat [legacyKey] as already migrated in.
     *
     * Typed rather than reflective: the legacy literals are a different type
     * (`String`) from the DataStore keys (`Preferences.Key<String>`), so
     * `prefs[key]` cannot be looked up by name without a table.
     */
    private fun legacyKeyIsPresent(
        prefs: Preferences,
        legacyKey: String,
    ): Boolean =
        when (legacyKey) {
            LegacyThemeKeys.THEME_MODE -> prefs[Keys.THEME_MODE] != null
            LegacyThemeKeys.TEXT_OPACITY -> prefs[Keys.TEXT_OPACITY] != null
            LegacyThemeKeys.FONT_FAMILY -> prefs[Keys.FONT_FAMILY] != null
            LegacyThemeKeys.FONT_SCALE -> prefs[Keys.FONT_SCALE] != null
            LegacyThemeKeys.COLOR_SATURATION -> prefs[Keys.COLOR_SATURATION] != null
            else -> false
        }

    private fun Preferences.toThemeConfig(): ThemeConfig =
        buildThemeConfig(
            uiStyleName = this[Keys.UI_STYLE],
            colorPaletteName = this[Keys.COLOR_PALETTE],
            themeMode = this[Keys.THEME_MODE],
            textOpacity = this[Keys.TEXT_OPACITY],
            fontFamilyName = this[Keys.FONT_FAMILY],
            fontScale = this[Keys.FONT_SCALE],
            colorSaturation = this[Keys.COLOR_SATURATION],
            dynamicColor = this[Keys.DYNAMIC_COLOR],
        )
}
