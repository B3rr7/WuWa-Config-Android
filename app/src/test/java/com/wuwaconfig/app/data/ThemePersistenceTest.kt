package com.wuwaconfig.app.data

import com.wuwaconfig.app.ui.theme.ColorPalette
import com.wuwaconfig.app.ui.theme.UiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the theme persistence decision logic.
 *
 * ## What is deliberately NOT covered
 *
 * The `PreferencesDataStore` file I/O itself. Its writer goes through
 * `android.jar` methods that are stubbed under
 * `unitTests.isReturnDefaultValues = true`, so testing it needs Robolectric —
 * a new dependency plus `@Config` annotations, for Google's own serialisation.
 * The parts that are *ours* (which value wins, what gets clamped, what happens
 * on a downgrade) are pure and live in [ThemePersistence], which is what these
 * tests exercise. This is the same split `AGENTS.md` records for
 * `ProfileExtractor.pullDb`, which is also unreachable without a real runtime.
 *
 * The one behaviour that genuinely needs a device was verified there: a real
 * DataStore file written by a real install, dumped with `strings`, showing
 * `theme_mode`/`text_opacity`/`font_family`/`font_scale`/`color_saturation` and
 * `legacy_migrated` all present and the legacy `wuwaconfig.xml` stripped of
 * those five keys while keeping its unrelated ones.
 */
class ThemePersistenceTest {
    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    fun `absent values fall back to the defaults`() {
        val c = buildThemeConfig(null, null, null, null, null, null, null, null)
        assertEquals(ThemeConfig(), c)
    }

    @Test
    fun `stored values round-trip`() {
        val c =
            buildThemeConfig(
                uiStyleName = UiStyle.RETRO_HANDHELD.name,
                colorPaletteName = ColorPalette.MATRIX_GREEN.name,
                themeMode = "dark",
                textOpacity = 0.75f,
                fontFamilyName = "Monospace",
                fontScale = 1.25f,
                colorSaturation = 1.3f,
                dynamicColor = true,
            )
        assertEquals(UiStyle.RETRO_HANDHELD, c.uiStyle)
        assertEquals(ColorPalette.MATRIX_GREEN, c.colorPalette)
        assertEquals("dark", c.themeMode)
        assertEquals(0.75f, c.textOpacity)
        assertEquals("Monospace", c.fontFamilyName)
        assertEquals(1.25f, c.fontScale)
        assertEquals(1.3f, c.colorSaturation)
        assertTrue(c.dynamicColor)
    }

    // ── Downgrade safety ──────────────────────────────────────────────────────

    /**
     * The scenario that matters most for an upgrade: a user runs a newer build,
     * picks a style this build has never heard of, then rolls back. Startup must
     * not throw, and must fall back to the default rather than leaving the app
     * unable to read its own settings.
     */
    @Test
    fun `an enum name from a newer build falls back instead of throwing`() {
        val c =
            buildThemeConfig(
                uiStyleName = "HOLOGRAPHIC_GLASS_V3",
                colorPaletteName = "HOVER_PASTEL",
                themeMode = null,
                textOpacity = null,
                fontFamilyName = null,
                fontScale = null,
                colorSaturation = null,
                dynamicColor = null,
            )
        assertEquals(UiStyle.DEFAULT, c.uiStyle)
        assertEquals(ColorPalette.DEFAULT, c.colorPalette)
    }

    @Test
    fun `an unrecognised themeMode is passed through for the theme to resolve`() {
        // themeMode is a free string, not an enum, so it is NOT validated here.
        // WuWaConfigTheme's `when` has an `else` branch, so this degrades to the
        // system setting at the point of use rather than being second-guessed here.
        val c =
            buildThemeConfig(
                uiStyleName = null,
                colorPaletteName = null,
                themeMode = "sepia",
                textOpacity = null,
                fontFamilyName = null,
                fontScale = null,
                colorSaturation = null,
                dynamicColor = null,
            )
        assertEquals("sepia", c.themeMode)
    }

    // ── Clamping and NaN ──────────────────────────────────────────────────────

    @Test
    fun `floats outside the slider range are clamped on read`() {
        // A legacy build could have written any value: the SharedPreferences
        // sliders were not range-enforced, and `getFloat` returns whatever is
        // there.
        val low =
            buildThemeConfig(
                uiStyleName = null,
                colorPaletteName = null,
                themeMode = null,
                textOpacity = -5f,
                fontFamilyName = null,
                fontScale = 0f,
                colorSaturation = -1f,
                dynamicColor = null,
            )
        assertEquals(ThemeDefaults.TEXT_OPACITY_MIN, low.textOpacity)
        assertEquals(ThemeDefaults.FONT_SCALE_MIN, low.fontScale)
        assertEquals(ThemeDefaults.SATURATION_MIN, low.colorSaturation)

        val high =
            buildThemeConfig(
                uiStyleName = null,
                colorPaletteName = null,
                themeMode = null,
                textOpacity = 99f,
                fontFamilyName = null,
                fontScale = 99f,
                colorSaturation = 99f,
                dynamicColor = null,
            )
        assertEquals(ThemeDefaults.TEXT_OPACITY_MAX, high.textOpacity)
        assertEquals(ThemeDefaults.FONT_SCALE_MAX, high.fontScale)
        assertEquals(ThemeDefaults.SATURATION_MAX, high.colorSaturation)
    }

    /**
     * The NaN case, which `coerceIn` does not catch.
     *
     * `coerceIn` propagates NaN because every comparison against it is false, so
     * a NaN read off a corrupted protobuf would reach the `LocalDensity`
     * override in `WuWaConfigTheme`. There, `Density(density, NaN)` makes every
     * measurement NaN and the screen renders nothing at all — a blank app from a
     * corrupt file, with no crash to explain it.
     */
    @Test
    fun `NaN floats fall back rather than poisoning the density override`() {
        val c =
            buildThemeConfig(
                uiStyleName = null,
                colorPaletteName = null,
                themeMode = null,
                textOpacity = Float.NaN,
                fontFamilyName = null,
                fontScale = Float.NaN,
                colorSaturation = Float.NaN,
                dynamicColor = null,
            )
        assertEquals(ThemeConfig().textOpacity, c.textOpacity)
        assertEquals(ThemeConfig().fontScale, c.fontScale)
        assertEquals(ThemeConfig().colorSaturation, c.colorSaturation)
        assertTrue("NaN survived into the config", !c.fontScale.isNaN())
    }

    @Test
    fun `positive infinity is clamped rather than passed through`() {
        val c =
            buildThemeConfig(
                uiStyleName = null,
                colorPaletteName = null,
                themeMode = null,
                textOpacity = Float.POSITIVE_INFINITY,
                fontFamilyName = null,
                fontScale = Float.NEGATIVE_INFINITY,
                colorSaturation = Float.POSITIVE_INFINITY,
                dynamicColor = null,
            )
        assertEquals(ThemeDefaults.TEXT_OPACITY_MAX, c.textOpacity)
        assertEquals(ThemeDefaults.FONT_SCALE_MIN, c.fontScale)
        assertEquals(ThemeDefaults.SATURATION_MAX, c.colorSaturation)
    }

    // ── Legacy migration ──────────────────────────────────────────────────────

    /**
     * The case the device could not exercise: an install that already had
     * non-default theme settings before DataStore existed.
     */
    @Test
    fun `migration carries the user's real legacy settings across`() {
        val plan =
            planLegacyMigration(
                alreadyMigrated = false,
                presentKeys = emptySet(),
                legacy =
                    mapOf(
                        LegacyThemeKeys.THEME_MODE to "light",
                        LegacyThemeKeys.TEXT_OPACITY to 0.8f,
                        LegacyThemeKeys.FONT_FAMILY to "Rajdhani",
                        LegacyThemeKeys.FONT_SCALE to 1.4f,
                        LegacyThemeKeys.COLOR_SATURATION to 1.2f,
                    ),
            )
        assertEquals(5, plan.size)
        assertEquals("light", plan[LegacyThemeKeys.THEME_MODE])
        assertEquals(0.8f, plan[LegacyThemeKeys.TEXT_OPACITY])
        assertEquals("Rajdhani", plan[LegacyThemeKeys.FONT_FAMILY])
        assertEquals(1.4f, plan[LegacyThemeKeys.FONT_SCALE])
        assertEquals(1.2f, plan[LegacyThemeKeys.COLOR_SATURATION])
    }

    /**
     * The flag, not key presence, is what stops the migration. Keying on
     * presence instead would silently restore any setting the user deliberately
     * cleared, on every single launch, forever.
     */
    @Test
    fun `migration is a no-op once the flag is set even with legacy data present`() {
        val plan =
            planLegacyMigration(
                alreadyMigrated = true,
                presentKeys = emptySet(),
                legacy =
                    mapOf(
                        LegacyThemeKeys.THEME_MODE to "dark",
                        LegacyThemeKeys.TEXT_OPACITY to 0.6f,
                    ),
            )
        assertTrue("expected no edits, got $plan", plan.isEmpty())
    }

    @Test
    fun `migration never overwrites a key that already exists`() {
        // Protects a future change that writes keys before migrating: without
        // this, that ordering would discard the user's newer choice.
        val plan =
            planLegacyMigration(
                alreadyMigrated = false,
                presentKeys = setOf(LegacyThemeKeys.THEME_MODE, LegacyThemeKeys.FONT_SCALE),
                legacy =
                    mapOf(
                        LegacyThemeKeys.THEME_MODE to "legacy-dark",
                        LegacyThemeKeys.FONT_SCALE to 3f,
                        LegacyThemeKeys.TEXT_OPACITY to 0.7f,
                        LegacyThemeKeys.FONT_FAMILY to "Serif",
                        LegacyThemeKeys.COLOR_SATURATION to 1.1f,
                    ),
            )
        assertTrue("themeMode must not be rewritten", LegacyThemeKeys.THEME_MODE !in plan)
        assertTrue("fontScale must not be rewritten", LegacyThemeKeys.FONT_SCALE !in plan)
        assertEquals("textOpacity should still migrate", 0.7f, plan[LegacyThemeKeys.TEXT_OPACITY])
        assertEquals("Serif", plan[LegacyThemeKeys.FONT_FAMILY])
        assertEquals(1.1f, plan[LegacyThemeKeys.COLOR_SATURATION])
    }

    /** The path the test device actually took: no legacy keys at all. */
    @Test
    fun `migration writes defaults when the legacy store is empty`() {
        val plan = planLegacyMigration(alreadyMigrated = false, presentKeys = emptySet(), legacy = emptyMap())
        assertEquals(5, plan.size)
        assertEquals(ThemeConfig().themeMode, plan[LegacyThemeKeys.THEME_MODE])
        assertEquals(ThemeConfig().fontFamilyName, plan[LegacyThemeKeys.FONT_FAMILY])
        assertEquals(ThemeConfig().textOpacity, plan[LegacyThemeKeys.TEXT_OPACITY])
    }

    @Test
    fun `migration tolerates a wrong-typed or NaN legacy value`() {
        val plan =
            planLegacyMigration(
                alreadyMigrated = false,
                presentKeys = emptySet(),
                legacy =
                    mapOf(
                        // A Float stored under a String key, or the reverse:
                        // possible if a build changed the type it wrote.
                        LegacyThemeKeys.TEXT_OPACITY to "0.9",
                        LegacyThemeKeys.FONT_SCALE to Float.NaN,
                        LegacyThemeKeys.COLOR_SATURATION to 12f,
                        LegacyThemeKeys.THEME_MODE to 42,
                    ),
            )
        assertEquals(ThemeConfig().textOpacity, plan[LegacyThemeKeys.TEXT_OPACITY])
        assertEquals(ThemeConfig().fontScale, plan[LegacyThemeKeys.FONT_SCALE])
        assertEquals(ThemeConfig().themeMode, plan[LegacyThemeKeys.THEME_MODE])
        // Out-of-range but well-typed is still clamped, not discarded.
        assertEquals(ThemeDefaults.SATURATION_MAX, plan[LegacyThemeKeys.COLOR_SATURATION])
    }

    /**
     * Every literal the migration reads must be a key the repository writes.
     *
     * The migration is typed as `Map<String, Any?>` because the legacy literals
     * are Strings while the DataStore keys are typed objects, so a typo would
     * otherwise compile cleanly and silently drop that setting. This is the only
     * thing standing between a renamed literal and a user's settings quietly
     * vanishing on upgrade.
     */
    @Test
    fun `every legacy key is one the repository actually writes`() {
        val written =
            setOf(
                LegacyThemeKeys.THEME_MODE,
                LegacyThemeKeys.TEXT_OPACITY,
                LegacyThemeKeys.FONT_FAMILY,
                LegacyThemeKeys.FONT_SCALE,
                LegacyThemeKeys.COLOR_SATURATION,
            )
        assertEquals(written, LegacyThemeKeys.ALL.toSet())
        assertEquals("duplicate legacy key", written.size, LegacyThemeKeys.ALL.size)
    }

    // ── Clamp bounds agree with the theme engine ──────────────────────────────

    /**
     * The repository's clamps and the theme engine's own clamps must not drift.
     *
     * Both existed independently before this refactor — `WuWaConfigTheme`
     * coerced `fontScale` to 0.75..1.5 and `ThemePreferencesRepository`
     * coerced it again to the same range. Nothing asserted they agreed, so a
     * future widening of one would silently make the other the effective bound,
     * with the value the user set rejected on write.
     */
    @Test
    fun `repository clamp bounds match the theme engine`() {
        assertEquals(com.wuwaconfig.app.ui.theme.MIN_TEXT_ALPHA, ThemeDefaults.TEXT_OPACITY_MIN)
        assertEquals(0.75f, ThemeDefaults.FONT_SCALE_MIN)
        assertEquals(1.5f, ThemeDefaults.FONT_SCALE_MAX)
        assertEquals(0.5f, ThemeDefaults.SATURATION_MIN)
        assertEquals(1.6f, ThemeDefaults.SATURATION_MAX)
    }
}
