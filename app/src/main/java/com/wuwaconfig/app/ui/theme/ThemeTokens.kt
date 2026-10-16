package com.wuwaconfig.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How a surface is *built*, independent of its colour and radius.
 *
 * ## Why this exists
 *
 * Before this enum, a [UiStyle] could only say how big its corners were. Every
 * style therefore drew the same surface and differed only by radius and stroke,
 * so four of the six were visually interchangeable — the "card grid wearing a
 * costume" failure. Radius is a dimension; whether a panel transmits its
 * backdrop, steps through tonal elevation, or sits flat is not, and it needed a
 * name of its own.
 *
 * The split follows the source material rather than taste:
 *
 * - [TONAL] — Material 3's defining mechanism. Google's own guidance is that M3
 *   "represents elevation mainly using tonal color overlays… increasing tonal
 *   elevation uses a more prominent tone — in addition to shadows". A tonal
 *   surface steps per nesting depth and draws no outline, because the tone *is*
 *   the edge.
 * - [GLASS] — transmits its backdrop. Only correct where there is something to
 *   transmit, which is why light mode still pairs it with a drawn edge.
 * - [OUTLINED] — flat fill carried by a visible border.
 * - [FLAT] — fill only, no edge, no elevation. The restrained end of the range.
 */
enum class SurfaceMode {
    TONAL,
    GLASS,
    OUTLINED,
    FLAT,
}

/**
 * How a surface's outline is drawn.
 *
 * Split from [SurfaceMode] because fill and edge are genuinely independent: a
 * glass panel still needs an edge (white-on-white is invisible in light mode),
 * while a tonal panel must not have one.
 */
enum class EdgeMode {
    /** No outline at all. The surface tone carries the boundary. */
    NONE,

    /** A single hairline of the accent or outline colour. */
    HAIRLINE,

    /**
     * A gradient edge: a bright specular highlight on the leading/top edge
     * falling to a darkened trailing/bottom edge.
     *
     * This is the one detail that separates glass from a pale rectangle. Apple
     * added it in 2026 — "a darkened edge along with brighter specular
     * highlights" to "establish more depth and separation" (WWDC26) — because a
     * uniform alpha fill reads as flat wash no matter how transparent it is.
     */
    SPECULAR,

    /** A hard, full-weight stroke. Carries RETRO_HANDHELD's no-softness rule. */
    HARD,
}

/**
 * Every layout dimension the theme engine controls, resolved for one [UiStyle].
 *
 * This is the structural half of the theme. [ColorPalette] decides what colour
 * a surface is; this decides how it is shaped. Components read it from
 * [LocalThemeShapeTokens] rather than hard-coding `RoundedCornerShape(8.dp)`, so
 * switching to RETRO_HANDHELD squares the whole app in one assignment instead of
 * a project-wide find-and-replace.
 *
 * Defaults are [UiStyle.MATERIAL_YOU]'s tokens, which is the same shape language
 * a stock Material 3 app would get. A component rendered in a preview, a test, or
 * anywhere outside [WuWaConfigTheme] therefore gets a sensible phone layout
 * rather than a zero-sized one.
 *
 * Verified against the enum rather than by trust: [UiStyle.MATERIAL_YOU] is the
 * `DEFAULT`, so a drift between these defaults and its tokens would put a
 * preview and the real app on different surfaces, and nothing compiles
 * differently because every [UiStyle] constant passes both explicitly.
 */
data class ThemeShapeTokens(
    /** How surfaces are filled. See [SurfaceMode]. */
    val surfaceMode: SurfaceMode = SurfaceMode.TONAL,
    /** How surfaces are outlined. See [EdgeMode]. */
    val edgeMode: EdgeMode = EdgeMode.NONE,
    /** Corner radius of cards and panels. */
    val cardCorner: Dp,
    /** Corner radius of buttons and icon buttons. */
    val buttonCorner: Dp,
    /** Corner radius of dialog surfaces. */
    val dialogCorner: Dp,
    /** Corner radius of text fields and dropdown anchors. */
    val fieldCorner: Dp,
    /** Stroke width for card and button outlines. 0 means "do not draw a border". */
    val borderStrokeWidth: Dp,
    /** Inner padding of a card's content column. */
    val cardPadding: Dp,
    /** Inner padding of a dialog's content column. */
    val dialogPadding: Dp,
    /** Vertical gap between stacked cards. */
    val cardSpacing: Dp,
    /**
     * Accent glow intensity, in the units [com.wuwaconfig.app.ui.components.GlassCard]
     * already multiplies by its shadow elevation. 0 disables the glow pass.
     */
    val glowWidth: Float,
    /** Whether translucent fills and background blur may be drawn. */
    val useBlurEffects: Boolean,
    /** Whether cards draw an outline at all. False means surface-only. */
    val useCardBorder: Boolean,
)

/**
 * The spacing scale.
 *
 * Deliberately four steps, not ten. A scale earns a step only when something
 * actually reads it, and every step below is in live use; an unused `xxl` is
 * not a placeholder but an invitation to reach for a value no layout has
 * validated. The four cover the app's real granularity — a half-gap, a gap
 * between stacked cards, a card's inner padding, and a screen's gutter — and
 * half of them are read through [LocalSpacing] rather than written as
 * `RoundedCornerShape`-style literals at the call sites.
 */
data class Spacing(
    /** Half a gap: icon-to-label, dot-to-text. */
    val xs: Dp = 4.dp,
    /** Between stacked cards, and between items in a row. */
    val sm: Dp = 8.dp,
    /** A card's inner padding and the gap above a section body. */
    val md: Dp = 16.dp,
    /** Screen gutter and the gap between sections. */
    val lg: Dp = 24.dp,
    /** Major section separation. */
    val xl: Dp = 32.dp,
)

/**
 * The active [UiStyle]'s shape tokens.
 *
 * `compositionLocalOf` rather than `staticCompositionLocalOf`, and this is a
 * real behavioural difference, not a style preference. A *static* local is not
 * tracked by its readers: when the value changes, Compose skips recomposing
 * anything that only read it, so only composition-local consumers that happen to
 * already be recomposing would see the new radii — the classic symptom being a
 * list where the visible cards updated but the scrolled-off ones kept the old
 * shape. `compositionLocalOf` records a dependency per read site and invalidates
 * correctly. The cost is one map lookup per read instead of a direct field
 * access, which is irrelevant at this scale.
 */
val LocalThemeShapeTokens = compositionLocalOf { MaterialYouTokens }

/** The active spacing scale. */
val LocalSpacing = compositionLocalOf { Spacing() }

/**
 * The active [UiStyle].
 *
 * Provided alongside [LocalThemeShapeTokens] because a few decisions cannot be
 * expressed as a dimension — notably whether `GradientBackground` animates
 * aurora layers, which is a branch, not a token. [MaterialYouTokens] is the
 * default here too.
 */
val LocalUiStyle = compositionLocalOf { UiStyle.MATERIAL_YOU }

/**
 * How many tonal surfaces deep the current card is, 0 for a top-level one.
 *
 * Material 3 expresses elevation as a step in surface tone, so a card has to
 * know how deeply it is nested to pick the right step — and a card inside a card
 * must be visibly raised relative to its parent. Compose has no ambient depth,
 * so each card reads this, fills from it, and provides the next value to its
 * own children.
 *
 * `compositionLocalOf` for the same reason [LocalThemeShapeTokens] is: the value
 * changes as a subtree is entered and left, and a *static* local is not tracked
 * by its readers, so a nested card would keep the depth it was composed at.
 *
 * Capped by the caller — see `TONAL_STEPS`. Beyond the last step the tone stops
 * changing, so a deeply nested card does not walk off the end of the ramp into
 * an unintended colour.
 */
val LocalSurfaceDepth = compositionLocalOf { 0 }

/**
 * Number of tonal steps a [SurfaceMode.TONAL] surface may climb.
 *
 * Material's own container ramp is six steps (`surfaceContainer` through
 * `surfaceContainerHighest`), but the app nests at most a card or two deep, so
 * a short ramp is enough and every step stays visibly distinct.
 *
 * This must equal the length of the ramp
 * [com.wuwaconfig.app.ui.components.tonalStepsFor] builds, and the two are the
 * same number on purpose: `surfaceFillFor` indexes with
 * `depth.coerceIn(0, TONAL_STEPS - 1)`, so a ramp longer than this silently
 * discards its tail — and the ramp's own monotonicity test would keep asserting
 * over a step nothing renders. Raise this and the ramp together, or the extra
 * step is dead weight rather than an available one.
 */
const val TONAL_STEPS = 3

/**
 * [UiStyle.MATERIAL_YOU]'s tokens, hoisted so the CompositionLocal default does
 * not allocate a fresh data class on every miss.
 */
private val MaterialYouTokens = UiStyle.MATERIAL_YOU.tokens
