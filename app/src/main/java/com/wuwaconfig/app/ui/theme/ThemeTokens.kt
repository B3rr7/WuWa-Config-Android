package com.wuwaconfig.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

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
 */
data class ThemeShapeTokens(
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
    /** Thickness of dividers. Also the snap-grid unit for RETRO_HANDHELD. */
    val dividerThickness: Dp,
    /** Whether translucent fills and background blur may be drawn. */
    val useBlurEffects: Boolean,
    /** Whether cards draw an outline at all. False means surface-only. */
    val useCardBorder: Boolean,
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
 * The active [ColorPalette].
 *
 * Static is safe *only* for the same reason it is unsafe above: nothing in the
 * app needs to recompose purely because the palette changed — every reader also
 * reads [androidx.compose.material3.MaterialTheme.colorScheme], which is itself
 * a new object when the palette changes and already invalidates them. Kept as a
 * composition local anyway so screens (the palette picker in particular) can read
 * the active value without recomputing the scheme.
 */
val LocalColorPalette = staticCompositionLocalOf { ColorPalette.SYSTEM_DEFAULT }

/**
 * [UiStyle.MATERIAL_YOU]'s tokens, hoisted so the CompositionLocal default does
 * not allocate a fresh data class on every miss.
 */
private val MaterialYouTokens = UiStyle.MATERIAL_YOU.tokens
