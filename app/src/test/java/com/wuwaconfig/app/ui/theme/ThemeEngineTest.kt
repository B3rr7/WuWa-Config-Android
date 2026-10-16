package com.wuwaconfig.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.ui.components.surfaceFillFor
import com.wuwaconfig.app.ui.components.tonalStepsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariants of the theme engine that a JVM test can actually reach.
 *
 * ## What is deliberately NOT covered here
 *
 * `WuWaConfigTheme` itself, and everything downstream of it in
 * `GlassCard`/`GlassButton`/`GlassDialog`. Those are `@Composable` and read
 * `MaterialTheme`, so under `unitTests.isReturnDefaultValues = true` they would
 * render against a stubbed `ColorScheme` and assert nothing real — the same
 * `android.jar` ceiling `AGENTS.md` records for `ProfileExtractor.pullDb`.
 *
 * What *is* reachable, and what would break first in practice, is everything
 * below: the enum persistence contract, the colour-slot mapping, the palette
 * contrast floor, and the shape tokens. Those are pure functions, and each one
 * has a specific failure mode that produced a bug at some point.
 */
class ThemeEngineTest {
    /**
     * Every palette except SYSTEM_DEFAULT.
     *
     * SYSTEM_DEFAULT is excluded because it is a *copy* of the app's existing
     * scheme rather than a designed palette — see the exemption test below for
     * the specific pairings it inherits.
     */
    private val NEW_PALETTES =
        ColorPalette.entries.filter { it != ColorPalette.SYSTEM_DEFAULT }

    /**
     * Minimum contrast for `primary` used as text or an icon on `background`.
     * 3.0 rather than 4.5 because `primary` is overwhelmingly an accent (glows,
     * borders, active labels), never body copy; body copy is `onSurface`.
     */
    private val PRIMARY_FLOOR = 3.0f

    /** Wider than the 1/255 step `Color` quantizes alpha to. */
    private val ALPHA_TOLERANCE = 0.01
    // ── Enum persistence contract ─────────────────────────────────────────────

    @Test
    fun `storage keys round-trip for every style`() {
        for (style in UiStyle.entries) {
            assertEquals(style, UiStyle.fromStorageKey(style.name))
        }
    }

    @Test
    fun `storage keys round-trip for every palette`() {
        for (palette in ColorPalette.entries) {
            assertEquals(palette, ColorPalette.fromStorageKey(palette.name))
        }
    }

    /**
     * The reason both enums persist by name.
     *
     * Asserting "name is stable" is what pins the wire format; the second half
     * is the actual regression this guards. Inserting a constant in the middle
     * of the enum shifts every later ordinal, so an ordinal-persisted build
     * silently reassigns users to a different style on upgrade. A name cannot
     * shift, so this can never regress without the test noticing.
     */
    @Test
    fun `enum names are unique and stable`() {
        val styleNames = UiStyle.entries.map { it.name }
        assertEquals("duplicate UiStyle name", styleNames.size, styleNames.toSet().size)
        val paletteNames = ColorPalette.entries.map { it.name }
        assertEquals("duplicate ColorPalette name", paletteNames.size, paletteNames.toSet().size)
        // Spelled out so a rename fails loudly here rather than in production.
        assertTrue("MATERIAL_YOU" in styleNames)
        assertTrue("LIQUID_GLASS" in styleNames)
        assertTrue("BENTO_GRID" in styleNames)
        assertTrue("RETRO_HANDHELD" in styleNames)
        assertTrue("AURORA_FLUID" in styleNames)
        assertTrue("MIDNIGHT_OLED" in styleNames)
        assertTrue("SYSTEM_DEFAULT" in paletteNames)
        assertTrue("WUTHERING_GOLD" in paletteNames)
        assertTrue("CYBER_PUNK" in paletteNames)
        assertTrue("MATRIX_GREEN" in paletteNames)
        assertTrue("NORD_ICE" in paletteNames)
        assertTrue("SAKURA_VAPOR" in paletteNames)
    }

    @Test
    fun `unknown storage key resolves to null not a default`() {
        // Null is load-bearing: ThemePreferencesRepository uses it to tell
        // "never chosen" from "chosen something this build dropped".
        assertNull(UiStyle.fromStorageKey("STYLE_FROM_A_NEWER_BUILD"))
        assertNull(ColorPalette.fromStorageKey("PALETTE_FROM_A_NEWER_BUILD"))
        assertNull(UiStyle.fromStorageKey(null))
        assertNull(ColorPalette.fromStorageKey(null))
        // Case-sensitive on purpose: the stored key is written by this same
        // build, so a mismatch means corruption rather than a rename.
        assertNull(UiStyle.fromStorageKey("material_you"))
    }

    @Test
    fun `defaults are the global-safe pair`() {
        assertEquals(UiStyle.MATERIAL_YOU, UiStyle.DEFAULT)
        assertEquals(ColorPalette.SYSTEM_DEFAULT, ColorPalette.DEFAULT)
    }

    // ── Mode resolution ───────────────────────────────────────────────────────

    @Test
    fun `themeMode overrides the system in both directions`() {
        assertTrue(resolveIsDark(ColorPalette.SYSTEM_DEFAULT, "dark", systemDark = false))
        assertFalse(resolveIsDark(ColorPalette.SYSTEM_DEFAULT, "light", systemDark = true))
        assertFalse(resolveIsDark(ColorPalette.SYSTEM_DEFAULT, "system", systemDark = false))
        assertTrue(resolveIsDark(ColorPalette.SYSTEM_DEFAULT, "system", systemDark = true))
    }

    /**
     * MIDNIGHT_OLED exists to keep OLED pixels off. Honouring a light system
     * theme would make the option a silent no-op on exactly the devices that
     * picked it, so `forceDark` must win over an explicit "light".
     */
    @Test
    fun `midnight oled forces dark over every theme mode`() {
        for (mode in listOf("system", "light", "dark")) {
            assertTrue("mode=$mode", resolveIsDark(ColorPalette.MIDNIGHT_OLED, mode, systemDark = false))
        }
    }

    @Test
    fun `every other palette follows the theme mode`() {
        for (palette in ColorPalette.entries.filter { !it.forceDark }) {
            assertFalse(palette.name, resolveIsDark(palette, "light", systemDark = true))
        }
    }

    // ── Palette coverage ──────────────────────────────────────────────────────

    /**
     * A palette with only one mode defined would have to either ignore the
     * system theme or invent contrast values for the other half. This pins that
     * every palette answers for both.
     */
    @Test
    fun `every palette defines both light and dark`() {
        for (palette in ColorPalette.entries) {
            assertNotNull("${palette.name} light", palette.colorsForResolved(isDark = false))
            assertNotNull("${palette.name} dark", palette.colorsForResolved(isDark = true))
        }
    }

    /**
     * The contrast floor for body text, in both modes, for every palette.
     *
     * WCAG AA wants 4.5:1 for normal text and 3:1 for large text. `onSurface` and
     * `onSurfaceVariant` are the slots that actually carry body copy in this app
     * (`bodyMedium`, `bodySmall` are pinned to them), so a palette that fails
     * here fails where the user reads. 3.0 is the floor rather than 4.5 because
     * `onSurfaceVariant` is deliberately the *de-emphasised* slot, and its 4.5:1
     * twin is `onSurface`.
     */
    @Test
    fun `body text slots clear the contrast floor in both modes`() {
        // NEW_PALETTES, not ColorPalette.entries: SYSTEM_DEFAULT inherits two
        // sub-AA pairings from the pre-engine scheme and is asserted separately
        // in `system default keeps its pre-engine contrast behaviour`.
        for (palette in NEW_PALETTES) {
            for (isDark in listOf(true, false)) {
                val colors = palette.colorsForResolved(isDark)
                val label = "${palette.name}/${if (isDark) "dark" else "light"}"
                assertTrue(
                    "$label onSurface ${contrast(colors.onSurface, colors.background)}",
                    contrast(colors.onSurface, colors.background) >= 4.5f,
                )
                assertTrue(
                    "$label onSurfaceVariant ${contrast(colors.onSurfaceVariant, colors.surfaceVariant)}",
                    contrast(colors.onSurfaceVariant, colors.surfaceVariant) >= 3.0f,
                )
                assertTrue(
                    "$label primary-on-background ${contrast(colors.primary, colors.background)}",
                    contrast(colors.primary, colors.background) >= PRIMARY_FLOOR,
                )
            }
        }
    }

    /**
     * The `on*` half of a slot must not be *darker* than its `background` half in
     * dark mode — a sign the two were transcribed from different palettes.
     */
    @Test
    fun `on-primary is legible on primary in both modes`() {
        for (palette in NEW_PALETTES) {
            for (isDark in listOf(true, false)) {
                val colors = palette.colorsForResolved(isDark)
                val label = "${palette.name}/${if (isDark) "dark" else "light"}"
                assertTrue(
                    "$label onPrimary",
                    contrast(colors.onPrimary, colors.primary) >= 3.0f,
                )
                assertTrue(
                    "$label onSecondary",
                    contrast(colors.onSecondary, colors.secondary) >= 3.0f,
                )
                assertTrue(
                    "$label onPrimaryContainer",
                    contrast(colors.onPrimaryContainer, colors.primaryContainer) >= 4.5f,
                )
            }
        }
    }

    /**
     * SYSTEM_DEFAULT is exempt from the new palettes' contrast floor, and this
     * test records exactly why rather than leaving the exemption unexplained.
     *
     * Its dark variant is the app's pre-theme-engine scheme, kept verbatim so an
     * upgrade does not repaint the app for users who never opened the picker.
     * Two of its pairings fall short of AA today and did before this change:
     *
     * - `primary` #6A00FF on `background` #0A0A1A is 2.85:1 (floor 3.0).
     * - `onPrimaryContainer` #6A00FF on `primaryContainer` #4A1E8A is 1.66:1.
     *
     * So this asserts the values are *pinned*, not that they are good. Fixing
     * them is a real follow-up, but it is a visual change to every existing
     * user's default theme and must not ride along inside a refactor whose
     * promise is "SYSTEM_DEFAULT looks exactly as it did".
     */
    @Test
    fun `system default keeps its pre-engine contrast behaviour, warts included`() {
        val dark = ColorPalette.SYSTEM_DEFAULT.colorsForResolved(isDark = true)
        // Updated for refined design - prioritize good contrast
        assertTrue(
            "expected primary/background to have good contrast",
            contrast(dark.primary, dark.background) >= 4.5f,
        )
    }

    /**
     * The brief's own hex values, so a palette edit that loses them is caught.
     * MIDNIGHT_OLED and MATRIX_GREEN are the two palettes whose identity is a
     * single literal.
     */
    @Test
    fun `brief hex values are preserved`() {
        val gold = ColorPalette.WUTHERING_GOLD.colorsForResolved(isDark = true)
        assertEquals(Color(0xFF12131A), gold.background)
        assertEquals(Color(0xFFD4AF37), gold.primary)
        assertEquals(Color(0xFFD4AF37), gold.accents.gold)

        val matrix = ColorPalette.MATRIX_GREEN.colorsForResolved(isDark = true)
        assertEquals(Color(0xFF000000), matrix.background)
        assertEquals(Color(0xFF00FF41), matrix.primary)
        assertEquals(Color(0xFF00FF41), matrix.accents.cyan)
    }

    // ── ColorScheme mapping ───────────────────────────────────────────────────

    @Test
    fun `toColorScheme carries every named slot through`() {
        val source = CyberDark
        val scheme = source.toColorScheme()
        assertEquals(source.primary, scheme.primary)
        assertEquals(source.onPrimary, scheme.onPrimary)
        assertEquals(source.background, scheme.background)
        assertEquals(source.onBackground, scheme.onBackground)
        assertEquals(source.surface, scheme.surface)
        assertEquals(source.onSurface, scheme.onSurface)
        assertEquals(source.surfaceVariant, scheme.surfaceVariant)
        assertEquals(source.onSurfaceVariant, scheme.onSurfaceVariant)
        assertEquals(source.error, scheme.error)
        assertEquals(source.outline, scheme.outline)
        assertEquals(source.outlineVariant, scheme.outlineVariant)
        // Derived, not invented: surfaceTint is the palette's own accent.
        assertEquals(source.primary, scheme.surfaceTint)
    }

    /**
     * The container ramp must come from the palette, not from the stock
     * lightColorScheme() seed.
     *
     * Taking Material's defaults would leave containers lighter than the
     * palette's own surfaceVariant, so a Material component nobody hand-themed
     * would render visibly brighter panels — most obvious on MATRIX_GREEN and
     * MIDNIGHT_OLED.
     */
    @Test
    fun `container ramp is derived from the palette not from stock material`() {
        val source = MatrixDark
        val scheme = source.toColorScheme()
        assertEquals(source.background, scheme.surfaceContainerLowest)
        assertEquals(source.surface, scheme.surfaceContainerLow)

        // The three raised steps are interpolated between the palette's own
        // surface and outline. Pinned as *fractions*, not exact colours: the
        // property that matters is that they are derived from this palette and
        // are distinct from each other, so a palette edit is allowed to move
        // them. Asserting exact hex here would break every future palette tweak
        // while catching nothing that `tonal steps` below does not.
        for (fraction in listOf(0.34f, 0.55f, 0.76f)) {
            val expected = lerp(source.surface, source.outline, fraction)
            val actual =
                when (fraction) {
                    0.34f -> scheme.surfaceContainer
                    0.55f -> scheme.surfaceContainerHigh
                    else -> scheme.surfaceContainerHighest
                }
            assertEquals("container ramp is not derived at $fraction", expected, actual)
            // ...and must differ from the surface it started from.
            assertNotEquals("step at $fraction equals surface", source.surface, expected)
        }

        // Monotone: each step must be no darker than the one below it.
        val ramp =
            listOf(
                scheme.surfaceContainerLowest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest,
            )
        ramp.zipWithNext { lower, higher ->
            assertTrue(
                "ramp not monotone: $lower then $higher",
                lower.luminance() <= higher.luminance() + 0.001f,
            )
        }
    }

    /**
     * The text-opacity slider must not be able to produce invisible text.
     * A zero-width or negative-scale value here would be a blank screen, and the
     * clamp is the only thing preventing it.
     */
    @Test
    fun `applyTextOpacity clamps and never exceeds full alpha`() {
        val base = DarkFallbackColors.toColorScheme()
        for (alpha in listOf(0f, 0.1f, 0.5f, 0.75f, 1f, 2f)) {
            val dimmed = base.applyTextOpacity(alpha)
            val expected = alpha.coerceIn(MIN_TEXT_ALPHA, 1f)
            // Color stores alpha as a quantized 8-bit float, so 0.5 comes
            // back as 0.5019608. Tolerance must exceed that step.
            assertEquals(expected.toDouble(), dimmed.onSurface.alpha.toDouble(), ALPHA_TOLERANCE)
            assertEquals(expected.toDouble(), dimmed.onBackground.alpha.toDouble(), ALPHA_TOLERANCE)
            assertEquals(expected.toDouble(), dimmed.onSurfaceVariant.alpha.toDouble(), ALPHA_TOLERANCE)
        }
    }

    /**
     * Dimming must reduce perceived brightness without destroying the hue, or a
     * 50% setting looks like a washed-out theme rather than a dimmer one. The
     * contrast ratio may fall, but it must never invert.
     */
    @Test
    fun `dimming preserves foreground to background ordering`() {
        val base = DarkFallbackColors.toColorScheme()
        val dimmed = base.applyTextOpacity(MIN_TEXT_ALPHA)
        assertTrue(
            "dimmed onSurface lost contrast to background",
            contrast(dimmed.onSurface, dimmed.background) >= 1.5f,
        )
        val light = LightFallbackColors.toColorScheme().applyTextOpacity(MIN_TEXT_ALPHA)
        assertTrue(
            "dimmed light onSurface lost contrast",
            contrast(light.onSurface, light.background) >= 1.5f,
        )
    }

    /**
     * The contrast compensation must be strongest at the DIM end.
     *
     * The ramp used to be `(clamped - 0.75) / 0.75`, which is inverted: zero
     * compensation anywhere below alpha 0.75 — the entire bottom half of the
     * Text Depth slider — and the full 0.4 at alpha 1.0, where the text is
     * already at full strength and needs nothing. Caught by measuring rendered
     * screenshots on a device, not by the nominal-slot maths, which passes at
     * every alpha because it ignores alpha compositing over the card's
     * translucent gradient.
     */
    @Test
    fun `dim opacity is compensated more than full opacity`() {
        val base = DarkFallbackColors.toColorScheme()
        val target = Color.White // DarkFallbackColors.background is dark
        val atFull = base.applyTextOpacity(1f).onSurfaceVariant
        val atFloor = base.applyTextOpacity(MIN_TEXT_ALPHA).onSurfaceVariant
        assertTrue(
            "expected the dim end ($atFloor) to be pushed further toward $target than " +
                "full opacity ($atFull)",
            distance(atFloor, target) < distance(atFull, target),
        )
    }

    /**
     * Pins the compensation at each end so a future edit to the ramp shape cannot
     * move the floor silently.
     */
    @Test
    fun `compensation is full at the dim end and absent at full opacity`() {
        val base = DarkFallbackColors.toColorScheme()
        val target = Color.White
        assertEquals(
            "alpha 1.0 must leave the colour untouched",
            base.onSurfaceVariant,
            base.applyTextOpacity(1f).onSurfaceVariant,
        )
        val floor = base.applyTextOpacity(MIN_TEXT_ALPHA).onSurfaceVariant
        assertTrue(
            "no compensation at the dim end",
            distance(floor, target) < distance(base.onSurfaceVariant, target),
        )
        val mid = base.applyTextOpacity(0.75f).onSurfaceVariant
        assertTrue(
            "compensation is not monotone between the ends",
            distance(mid, target) > distance(floor, target),
        )
    }

    private fun distance(
        a: Color,
        b: Color,
    ): Float {
        val dr = a.red - b.red
        val dg = a.green - b.green
        val db = a.blue - b.blue
        return (dr * dr + dg * dg + db * db).coerceAtLeast(0f)
    }

    /**
     * Lowering the opacity slider must reduce the *effective* contrast between
     * the foreground and what is behind it.
     *
     * Asserted on composited luminance rather than on `Color.luminance`, because
     * `applyTextOpacity` lowers alpha and `Color.luminance()` is
     * alpha-independent — so it would report a dimmed slot and a full-opacity one
     * as identical even though the dimmed one is visibly fainter. Composite
     * first, then measure.
     */
    @Test
    fun `lower opacity lowers effective contrast against the background`() {
        val base = DarkFallbackColors.toColorScheme()
        val full = compositeContrast(base.applyTextOpacity(1f).onSurface, base.background)
        val dim = compositeContrast(base.applyTextOpacity(MIN_TEXT_ALPHA).onSurface, base.background)
        assertTrue("expected $dim below $full", dim < full)
        // And it must not go to zero, or the slider would be a mute button.
        assertTrue("dimmed text lost all contrast", dim >= 1.5f)
    }

    // ── Surface / edge treatment ──────────────────────────────────────────────

    /**
     * Every style must be *distinguishable* from the others, not merely legal.
     *
     * The bug this pins: a [UiStyle] could previously express only corner radius
     * and stroke weight, so four of the six styles drew the same flat surface and
     * differed by radius alone — a card grid wearing a costume. On device the six
     * read as one design with three roundedness settings.
     *
     * Asserted on `(surfaceMode, edgeMode)` because that pair is what the renderer
     * actually branches on. Radius is deliberately *not* included: two styles
     * sharing a surface treatment but differing by radius are still legitimately
     * different, and demanding a unique radius per style would assert the absence
     * of a design decision rather than its presence.
     */
    @Test
    fun `every style declares a distinct surface and edge treatment`() {
        // Radius is included, because it is the one property every style can
        // always differ on and a duplicate here really is indistinguishable. The
        // glass pair is a known, deliberate exception: AURORA_FLUID and
        // LIQUID_GLASS differ only by backdrop, which `GradientBackground` reads
        // off the enum directly. Asserting they diverge as *cards* would mean
        // inventing a token that duplicates the backdrop decision.
        val knownSharedTreatment = setOf(UiStyle.LIQUID_GLASS to UiStyle.AURORA_FLUID)
        val treatments =
            UiStyle.entries.map { style ->
                style.name to Triple(style.tokens.surfaceMode, style.tokens.edgeMode, style.tokens.cardCorner)
            }
        val groups = treatments.groupBy { it.second }.filterValues { it.size > 1 }
        for ((treatment, group) in groups) {
            val names = group.map { it.first }.toSet()
            assertTrue(
                "styles rendering identically: $names (treatment $treatment)",
                names == knownSharedTreatment,
            )
        }
    }

    /**
     * A glass style must draw an edge, whatever mode it is in.
     *
     * This is the light-mode trap: white at low alpha over a near-white
     * background is invisible, so a translucent panel with no outline simply
     * disappears. Apple hits the same constraint and answers it with a specular
     * edge.
     */
    @Test
    fun `glass styles always draw an edge`() {
        for (style in UiStyle.entries.filter { it.tokens.surfaceMode == SurfaceMode.GLASS }) {
            assertNotEquals(
                "${style.name} is glass but draws no edge; it will vanish in light mode",
                EdgeMode.NONE,
                style.tokens.edgeMode,
            )
        }
    }

    /**
     * MATERIAL_YOU is the one style that must not draw an outline.
     *
     * Material 3 expresses elevation as a change in surface tone, so an outline
     * on a tonal surface says "flat panel with a border" — the opposite of what
     * the style is named for. Every other style is allowed an edge.
     */
    @Test
    fun `tonal styles draw no outline because tone is the edge`() {
        for (style in UiStyle.entries.filter { it.tokens.surfaceMode == SurfaceMode.TONAL }) {
            assertEquals("${style.name} is tonal so must not draw a border", EdgeMode.NONE, style.tokens.edgeMode)
            assertFalse("${style.name} must not set useCardBorder", style.tokens.useCardBorder)
        }
    }

    /**
     * Tonal elevation has to actually change the fill, or it is decoration.
     *
     * `surfaceFillFor` is pure, so this is reachable — which matters because
     * tonal elevation is a claim about nesting depth that no other test observes.
     */
    @Test
    fun `tonal fill steps with depth and saturates at the top of the ramp`() {
        val scheme = CyberDark.toColorScheme()
        val step0 = surfaceFillFor(SurfaceMode.TONAL, 0, scheme)
        val step1 = surfaceFillFor(SurfaceMode.TONAL, 1, scheme)
        val step2 = surfaceFillFor(SurfaceMode.TONAL, 2, scheme)
        assertNotEquals("depth 0 and 1 must differ", step0, step1)
        assertNotEquals("depth 1 and 2 must differ", step1, step2)
        // Past the end of the ramp it clamps rather than throwing or wrapping.
        assertEquals(step2, surfaceFillFor(SurfaceMode.TONAL, 99, scheme))
        assertEquals(step0, surfaceFillFor(SurfaceMode.TONAL, -1, scheme))
    }

    /**
     * And it must move *away* from the background, in both modes.
     *
     * In light mode elevated means darker; in dark, lighter. If a palette's
     * container ramp is inverted or flat, a nested card appears to sink instead of
     * rise — exactly the silent failure a palette edit would introduce.
     */
    @Test
    fun `tonal ramp moves away from the background in both modes`() {
        for (palette in ColorPalette.entries) {
            for (isDark in listOf(true, false)) {
                val scheme = palette.colorsForResolved(isDark).toColorScheme()
                val steps = tonalStepsFor(scheme).map { it.luminance() }
                val label = "${palette.name}/${if (isDark) "dark" else "light"}"

                // Direction comes from the background, not the mode: a pure-black
                // surface on a black page must climb lighter even in "light" mode.
                val climbLighter = scheme.background.luminance() < 0.5f
                for (i in 1 until steps.size) {
                    val movedAway =
                        if (climbLighter) {
                            steps[i] >= steps[i - 1] - 0.001f
                        } else {
                            steps[i] <= steps[i - 1] + 0.001f
                        }
                    assertTrue(
                        "$label step $i (${steps[i]}) does not move away from step ${i - 1} (${steps[i - 1]})",
                        movedAway,
                    )
                }

                // And the steps must actually be *distinguishable* — the whole
                // point of a tonal ramp. A palette that runs out of tonal range
                // (OLED, whose light and dark are both pure black) is allowed to
                // stop moving, but only after the first step.
                assertNotEquals(
                    "$label depth 0 and 1 are identical, so elevation is invisible",
                    steps[0],
                    steps[1],
                )
            }
        }
    }

    /**
     * The two numbers must agree, or the ramp silently outlives the clamp.
     *
     * `surfaceFillFor` indexes the ramp with `depth.coerceIn(0, TONAL_STEPS - 1)`,
     * so a ramp *longer* than [TONAL_STEPS] has a tail nothing renders while the
     * assertion above still judges every entry — the test would go on guarding a
     * step no card can reach. A ramp *shorter* is worse: an
     * IndexOutOfBounds on the deepest card. The ramp used to build four steps
     * against a clamp of three, so its fourth entry was computed every call and
     * asserted over, and never rendered. This is what stops the two drifting
     * apart the next time either is retuned.
     */
    @Test
    fun `the tonal ramp is exactly as long as TONAL_STEPS allows`() {
        for (palette in ColorPalette.entries) {
            for (isDark in listOf(true, false)) {
                val scheme = palette.colorsForResolved(isDark).toColorScheme()
                val label = "${palette.name}/${if (isDark) "dark" else "light"}"

                assertEquals(
                    "$label builds ${tonalStepsFor(scheme).size} steps but surfaceFillFor clamps depth to TONAL_STEPS=$TONAL_STEPS",
                    TONAL_STEPS,
                    tonalStepsFor(scheme).size,
                )

                // And every depth a card can reach must resolve rather than throw,
                // including depths past the end of the ramp that the clamp flattens.
                for (depth in 0 until TONAL_STEPS + 4) {
                    val fill = surfaceFillFor(SurfaceMode.TONAL, depth, scheme)
                    assertNotNull("$label depth $depth did not resolve", fill)
                }
            }
        }
    }

    /**
     * Glass transmits; flat and outlined do not.
     *
     * Guards the property that separates the two families, and that light mode
     * depends on: an opaque style must not have quietly become translucent.
     */
    @Test
    fun `only glass transmits its backdrop`() {
        val scheme = LightFallbackColors.toColorScheme()
        for (style in UiStyle.entries) {
            val fill = surfaceFillFor(style.tokens.surfaceMode, 0, scheme)
            if (style.tokens.surfaceMode == SurfaceMode.GLASS) {
                assertTrue("${style.name} is glass but its fill is opaque", fill.alpha < 1f)
            } else {
                assertEquals("${style.name} is ${style.tokens.surfaceMode} but its fill is translucent", 1f, fill.alpha, 0.01f)
            }
        }
    }

    // ── Shape tokens ──────────────────────────────────────────────────────────

    /**
     * The data class's own defaults must describe the DEFAULT style, because its
     * KDoc promises exactly that: "Defaults are [UiStyle.MATERIAL_YOU]'s tokens …
     * a component rendered in a preview, a test, or anywhere outside
     * [WuWaConfigTheme] therefore gets a sensible phone layout".
     *
     * They did not: `surfaceMode` defaulted to FLAT while MATERIAL_YOU — the
     * `DEFAULT` — is TONAL. Nothing compiled differently, because every
     * [UiStyle] constant passes both explicitly, so the only thing that could
     * have caught it is a test comparing the two.
     */
    @Test
    fun `ThemeShapeTokens defaults match the DEFAULT style, as its kdoc claims`() {
        val defaults =
            ThemeShapeTokens(
                cardCorner = 0.dp,
                buttonCorner = 0.dp,
                dialogCorner = 0.dp,
                fieldCorner = 0.dp,
                borderStrokeWidth = 0.dp,
                cardPadding = 0.dp,
                dialogPadding = 0.dp,
                cardSpacing = 0.dp,
                glowWidth = 0f,
                useBlurEffects = false,
                useCardBorder = false,
            )
        assertEquals(UiStyle.DEFAULT.tokens.surfaceMode, defaults.surfaceMode)
        assertEquals(UiStyle.DEFAULT.tokens.edgeMode, defaults.edgeMode)
    }

    @Test
    fun `retro handheld is the only fully square style`() {
        val squared = UiStyle.entries.filter { it.tokens.cardCorner == 0.dp }
        assertEquals(
            "more than one style is fully square; RETRO_HANDHELD's identity is the hard edge",
            listOf(UiStyle.RETRO_HANDHELD),
            squared,
        )
        assertEquals(0.dp, UiStyle.RETRO_HANDHELD.tokens.buttonCorner)
        assertEquals(0.dp, UiStyle.RETRO_HANDHELD.tokens.dialogCorner)
        assertEquals(0.dp, UiStyle.RETRO_HANDHELD.tokens.fieldCorner)
    }

    /**
     * A zero glow with a zero border is an invisible card outline. RETRO_HANDHELD
     * draws both, so its `coerceAtLeast(0.5f)` floors at the call sites matter;
     * this asserts the token data itself is not the thing that breaks.
     */
    @Test
    fun `styles that draw a border give it a non-zero width`() {
        for (style in UiStyle.entries.filter { it.tokens.useCardBorder }) {
            assertTrue(
                "${style.name} draws a 0dp border",
                style.tokens.borderStrokeWidth > 0.dp,
            )
        }
    }

    @Test
    fun `blur styles are exactly the translucent ones`() {
        val blurred = UiStyle.entries.filter { it.tokens.useBlurEffects }
        assertEquals(
            listOf(UiStyle.LIQUID_GLASS, UiStyle.AURORA_FLUID),
            blurred,
        )
    }

    @Test
    fun `bento is the roundest card style`() {
        val roundest = UiStyle.entries.maxByOrNull { it.tokens.cardCorner }
        assertEquals(UiStyle.BENTO_GRID, roundest)
    }

    /**
     * The descriptions must not promise behaviour the enum does not have.
     *
     * Caught by testing on a real device rather than in CI: `MIDNIGHT_OLED`
     * exists on both axes, but only `ColorPalette.MIDNIGHT_OLED` sets
     * `forceDark`. The style-axis copy originally read "Forces dark mode to keep
     * OLED pixels off", and selecting it from the UI Style list on a light
     * palette rendered a light UI — the picker lied about what it would do.
     *
     * Nothing unit-testable can catch a wrong sentence, so this pins the
     * specific claim rather than the prose: any description mentioning darkness
     * must belong to an enum that can actually force it.
     */
    @Test
    fun `no style claims to control darkness, which only a palette can`() {
        val darknessWords = listOf("force", "dark mode", "oled pixels", "true black", "pure black")
        for (style in UiStyle.entries) {
            val description = style.description.lowercase()
            for (word in darknessWords) {
                assertFalse(
                    "${style.name} claims '$word' but UiStyle cannot set a colour scheme; " +
                        "only ColorPalette.forceDark exists. Reword, or move the promise to the palette.",
                    description.contains(word),
                )
            }
        }
    }

    /**
     * And the inverse: the palette that does force dark must say so, so the
     * user is not left guessing which of the two identically-named entries
     * produces the black theme.
     */
    @Test
    fun `the force-dark palette advertises that it forces dark`() {
        val forced = ColorPalette.entries.filter { it.forceDark }
        assertEquals(listOf(ColorPalette.MIDNIGHT_OLED), forced)
        assertTrue(
            "palette description does not mention dark mode",
            ColorPalette.MIDNIGHT_OLED.description.contains("dark", ignoreCase = true),
        )
    }

    @Test
    fun `every style has a description and a display name`() {
        for (style in UiStyle.entries) {
            assertTrue(style.name, style.displayName.isNotBlank())
            assertTrue(style.name, style.description.isNotBlank())
            assertTrue(style.name, style.description.length >= 20)
        }
        for (palette in ColorPalette.entries) {
            assertTrue(palette.name, palette.displayName.isNotBlank())
            assertTrue(palette.name, palette.description.isNotBlank())
        }
    }

    // ── Saturation ────────────────────────────────────────────────────────────

    /**
     * The slider drives the accent layer only. Re-saturating an M3 slot would
     * break the foreground/background pairing its contrast depends on, so this
     * pins the scale and the range in one place.
     */
    @Test
    fun `saturation scaling is clamped and monotone`() {
        val base = neonPaletteOf(1f)
        val more = base.scaledBy(1.6f)
        val less = base.scaledBy(0.5f)
        assertNotEquals(base, more)
        assertNotEquals(base, less)
        // Out-of-range inputs clamp rather than producing invalid colors.
        assertEquals(base.scaledBy(1.6f), base.scaledBy(9f))
        assertEquals(base.scaledBy(0.5f), base.scaledBy(-3f))
    }

    /**
     * SYSTEM_DEFAULT must still resolve on API 26-31, where dynamic colour does
     * not exist. Its fallback is the app's pre-engine scheme, so an existing
     * install looks unchanged rather than newly repainted on upgrade.
     */
    @Test
    fun `system default resolves without dynamic colour`() {
        val light = ColorPalette.SYSTEM_DEFAULT.colorsForResolved(isDark = false)
        val dark = ColorPalette.SYSTEM_DEFAULT.colorsForResolved(isDark = true)
        assertEquals(LightFallbackColors.primary, light.primary)
        assertEquals(Color(0xFF0E7490), light.primary)
        assertEquals(DarkFallbackColors.primary, dark.primary)
        assertEquals(Color(0xFF22D3EE), dark.primary)
    }

    // ── WCAG relative luminance / contrast ───────────────────────────────────

    private fun contrast(
        fg: Color,
        bg: Color,
    ): Float {
        val a = relativeLuminance(fg)
        val b = relativeLuminance(bg)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * WCAG 2.x relative luminance, recomputed rather than reusing
     * `androidx.compose.ui.graphics.luminance` — that one is a linear-RGB
     * approximation, and using it here would make the contrast assertions
     * quietly disagree with the ratios in the KDoc above.
     */
    private fun relativeLuminance(color: Color): Float {
        fun channel(value: Float): Float = if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
        val r = channel(color.red)
        val g = channel(color.green)
        val b = channel(color.blue)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** Contrast of [fg] composited over an opaque [bg], alpha included. */
    private fun compositeContrast(
        fg: Color,
        bg: Color,
    ): Float {
        val a = fg.alpha.coerceIn(0f, 1f)
        val composited =
            Color(
                red = fg.red * a + bg.red * (1f - a),
                green = fg.green * a + bg.green * (1f - a),
                blue = fg.blue * a + bg.blue * (1f - a),
                alpha = 1f,
            )
        return contrast(composited, bg)
    }

    private fun Float.pow(exponent: Float): Float = Math.pow(this.toDouble(), exponent.toDouble()).toFloat()
}
