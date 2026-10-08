package com.wuwaconfig.app.data

import com.wuwaconfig.app.ui.theme.ColorPalette
import com.wuwaconfig.app.ui.theme.UiStyle

/**
 * The pure decision logic behind [ThemePreferencesRepository].
 *
 * ## Why this is separated out
 *
 * Everything here decides *which value wins*, and all of it was buried inside a
 * `suspend` function that needs a real `PreferencesDataStore` — whose file
 * writer goes through stubbed `android.jar` methods under
 * `unitTests.isReturnDefaultValues = true`. That left the whole migration path
 * untestable without adding Robolectric.
 *
 * The alternative was a new dependency plus a `@Config`-annotated test class for
 * logic that is really table lookups. So the tables are extracted here as pure
 * functions over primitives, unit-tested exhaustively, and the DataStore
 * plumbing in the repository is reduced to reading values and applying the
 * returned edits. What stays untestable is then Google's own serialisation,
 * which is not where our bugs live.
 *
 * See `ThemePersistenceTest`.
 */
internal object ThemeDefaults {
    const val TEXT_OPACITY_MIN = 0.5f
    const val TEXT_OPACITY_MAX = 1f
    const val FONT_SCALE_MIN = 0.75f
    const val FONT_SCALE_MAX = 1.5f
    const val SATURATION_MIN = 0.5f
    const val SATURATION_MAX = 1.6f

    fun clampTextOpacity(value: Float): Float = value.coerceIn(TEXT_OPACITY_MIN, TEXT_OPACITY_MAX)

    fun clampFontScale(value: Float): Float = value.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)

    fun clampSaturation(value: Float): Float = value.coerceIn(SATURATION_MIN, SATURATION_MAX)
}

/** The five keys that used to live in SharedPreferences, under these literals. */
internal object LegacyThemeKeys {
    const val THEME_MODE = "theme_mode"
    const val TEXT_OPACITY = "text_opacity"
    const val FONT_FAMILY = "font_family"
    const val FONT_SCALE = "font_scale"
    const val COLOR_SATURATION = "color_saturation"

    val ALL = listOf(THEME_MODE, TEXT_OPACITY, FONT_FAMILY, FONT_SCALE, COLOR_SATURATION)
}

/**
 * Builds a [ThemeConfig] from raw stored values.
 *
 * Every parameter is nullable because that is exactly what a missing key looks
 * like, and because the failure modes differ per field:
 *
 * - **Enums** fall back when the stored name is absent *or* unrecognised. The
 *   second case is a downgrade: a build that no longer knows the name must not
 *   crash on startup over a cosmetic setting.
 * - **Floats** are clamped *and* NaN-guarded. `coerceIn` propagates NaN, because
 *   both bounds compare false against it, so a NaN read off a corrupted
 *   protobuf would survive clamping and reach the `LocalDensity` override — where
 *   it makes every measurement NaN and the screen renders nothing at all.
 *   Verified against a real device's DataStore file, which is why this is a
 *   guard and not a comment.
 * - **themeMode** is *not* validated against an allow-list. It is a free string
 *   that [com.wuwaconfig.app.ui.theme.WuWaConfigTheme] resolves with a `when`
 *   whose `else` is the system setting, so an unknown value already degrades to
 *   "system" without needing a fallback here.
 */
@Suppress("LongParameterList")
internal fun buildThemeConfig(
    uiStyleName: String?,
    colorPaletteName: String?,
    themeMode: String?,
    textOpacity: Float?,
    fontFamilyName: String?,
    fontScale: Float?,
    colorSaturation: Float?,
    dynamicColor: Boolean?,
): ThemeConfig {
    val d = ThemeConfig()

    fun Float?.safeClamp(
        fallback: Float,
        clamp: (Float) -> Float,
    ): Float {
        val raw = this ?: return fallback
        // NaN fails every comparison, so it passes straight through coerceIn.
        if (raw.isNaN()) return fallback
        return clamp(raw)
    }

    return ThemeConfig(
        uiStyle = UiStyle.fromStorageKey(uiStyleName) ?: d.uiStyle,
        colorPalette = ColorPalette.fromStorageKey(colorPaletteName) ?: d.colorPalette,
        themeMode = themeMode ?: d.themeMode,
        textOpacity = textOpacity.safeClamp(d.textOpacity, ThemeDefaults::clampTextOpacity),
        fontFamilyName = fontFamilyName ?: d.fontFamilyName,
        fontScale = fontScale.safeClamp(d.fontScale, ThemeDefaults::clampFontScale),
        colorSaturation = colorSaturation.safeClamp(d.colorSaturation, ThemeDefaults::clampSaturation),
        dynamicColor = dynamicColor ?: d.dynamicColor,
    )
}

/**
 * Decides what the legacy migration should write.
 *
 * Two rules, and the second is the subtle one:
 *
 * 1. **Do nothing if already migrated.** Keyed on the flag, not on key
 *    presence — otherwise a user who deliberately cleared a setting would have
 *    it silently restored on every launch.
 * 2. **Never overwrite an existing key.** The flag and the keys are written in
 *    one transaction today, so this only bites if a future change writes keys
 *    before migrating. Keeping it makes that ordering safe instead of a
 *    data-loss bug waiting to happen.
 *
 * Values are clamped on the way through, because the legacy store enforced no
 * bounds on the sliders — an older build could have written anything, and
 * `SharedPreferences.getFloat` returns it happily.
 *
 * The caller is responsible for deleting the legacy keys only after this plan
 * has been applied, so a failed write retries next launch rather than stranding
 * the settings in the old store.
 */
internal fun planLegacyMigration(
    alreadyMigrated: Boolean,
    presentKeys: Set<String>,
    legacy: Map<String, Any?>,
): Map<String, Any?> {
    if (alreadyMigrated) return emptyMap()

    val edits = LinkedHashMap<String, Any?>()
    val d = ThemeConfig()

    fun putIfAbsent(
        key: String,
        value: Any?,
    ) {
        if (key !in presentKeys) edits[key] = value
    }

    /**
     * Float keys need an explicit fallback, unlike the string ones.
     *
     * `(legacy[key] as? Float)?.clampOrDefault(...)` evaluates to **null** when
     * the key is absent, not to `fallback` — `?.` short-circuits on the null
     * receiver. That put a literal `null` into the returned plan for an empty
     * legacy store, which the repository then silently skipped, leaving the key
     * absent. The end result still read back as the default, so the bug was
     * invisible in the app and only showed up here.
     */
    fun putFloatIfAbsent(
        key: String,
        raw: Any?,
        fallback: Float,
        clamp: (Float) -> Float,
    ) {
        if (key in presentKeys) return
        val value = raw as? Float
        edits[key] = if (value == null || value.isNaN()) fallback else clamp(value)
    }

    putIfAbsent(LegacyThemeKeys.THEME_MODE, legacy[LegacyThemeKeys.THEME_MODE] as? String ?: d.themeMode)
    putFloatIfAbsent(LegacyThemeKeys.TEXT_OPACITY, legacy[LegacyThemeKeys.TEXT_OPACITY], d.textOpacity, ThemeDefaults::clampTextOpacity)
    putIfAbsent(LegacyThemeKeys.FONT_FAMILY, legacy[LegacyThemeKeys.FONT_FAMILY] as? String ?: d.fontFamilyName)
    putFloatIfAbsent(LegacyThemeKeys.FONT_SCALE, legacy[LegacyThemeKeys.FONT_SCALE], d.fontScale, ThemeDefaults::clampFontScale)
    putFloatIfAbsent(
        LegacyThemeKeys.COLOR_SATURATION,
        legacy[LegacyThemeKeys.COLOR_SATURATION],
        d.colorSaturation,
        ThemeDefaults::clampSaturation,
    )
    return edits
}
