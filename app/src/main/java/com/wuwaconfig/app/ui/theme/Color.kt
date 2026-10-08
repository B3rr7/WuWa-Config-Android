package com.wuwaconfig.app.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

val DarkBg = Color(0xFF0A0A1A)
val DarkSurface = Color(0xFF12122A)
val CardSurface = Color(0xFF1A1A3A)

val LightBg = Color(0xFFF4F5F8)
val LightSurface = Color(0xFFF1F2F6)
val LightSurfaceVariant = Color(0xFFE7E9EF)

val GlassCardBg = Color(0x1AFFFFFF)
val GlassCardBorder = Color(0x28FFFFFF)
val GlassSurface = Color(0x12FFFFFF)

data class NeonPalette(
    val purple: Color,
    val cyan: Color,
    val pink: Color,
    val green: Color,
    val red: Color,
    val amber: Color,
    val blue: Color,
    val gold: Color,
)

/**
 * The app's original eight accent hues at a saturation of [factor].
 *
 * Kept as the implementation of SYSTEM_DEFAULT, so the pre-theme-engine look
 * survives the refactor as a palette rather than being deleted by it. Reads
 * [neonBases] directly; a [ColorPalette] supplies its own hues through the
 * named-argument overload below.
 */
fun neonPaletteOf(factor: Float): NeonPalette {
    val f = factor.coerceIn(MIN_SATURATION, MAX_SATURATION)
    return NeonPalette(
        purple = adjustSaturation(neonBases[0], f),
        cyan = adjustSaturation(neonBases[1], f),
        pink = adjustSaturation(neonBases[2], f),
        green = adjustSaturation(neonBases[3], f),
        red = adjustSaturation(neonBases[4], f),
        amber = adjustSaturation(neonBases[5], f),
        blue = adjustSaturation(neonBases[6], f),
        gold = adjustSaturation(neonBases[7], f),
    )
}

/**
 * A palette's own eight hues, passed through unchanged.
 *
 * Deliberately does NOT route through [adjustSaturation] even though the palette
 * is defined at saturation 1.0. Two reasons:
 *
 * - It is a no-op. `adjustSaturation(c, 1f)` multiplies HSV saturation by 1 and
 *   converts back, so the round-trip through `android.graphics.Color.colorToHSV`
 *   exists only to lose precision.
 * - It made the accents **unassertable**. `colorToHSV` is one of the stubbed
 *   `android.jar` methods under `unitTests.isReturnDefaultValues = true`, so it
 *   returns without touching its output array and `HSVToColor` yields
 *   `Color(0,0,0,0)`. Every named palette's accents therefore came back
 *   transparent black in tests, which would have shipped eight invisible accent
 *   colours for all ~500 `NeonXxx` readers with a green test run. The palette
 *   constructors are now pure data, so they are testable at all.
 */
fun neonPaletteOf(
    purple: Color,
    cyan: Color,
    pink: Color,
    green: Color,
    red: Color,
    amber: Color,
    blue: Color,
    gold: Color,
): NeonPalette =
    NeonPalette(
        purple = purple,
        cyan = cyan,
        pink = pink,
        green = green,
        red = red,
        amber = amber,
        blue = blue,
        gold = gold,
    )

/** Applies a saturation factor to all eight accents. Used by the slider. */
fun NeonPalette.scaledBy(factor: Float): NeonPalette {
    val f = factor.coerceIn(MIN_SATURATION, MAX_SATURATION)
    return NeonPalette(
        purple = adjustSaturation(purple, f),
        cyan = adjustSaturation(cyan, f),
        pink = adjustSaturation(pink, f),
        green = adjustSaturation(green, f),
        red = adjustSaturation(red, f),
        amber = adjustSaturation(amber, f),
        blue = adjustSaturation(blue, f),
        gold = adjustSaturation(gold, f),
    )
}

/**
 * Multiplies [color]'s HSV saturation by [factor], leaving hue, value and alpha
 * alone.
 *
 * ## Why this is not `android.graphics.Color.colorToHSV`
 *
 * Two reasons, and the second is the one that matters.
 *
 * 1. **It was untestable.** `colorToHSV` is a stubbed `android.jar` method under
 *    `unitTests.isReturnDefaultValues = true`: it returns without writing its
 *    output array, and `HSVToColor` then yields `Color(0, 0, 0, 0)`. So the
 *    saturation slider — a user-facing setting — had no reachable test at all,
 *    and a regression in it would have looked exactly like a working slider in
 *    production and a null palette in CI.
 * 2. **It lost precision.** The platform pair quantizes through 8-bit
 *    `Int` channels, so an unchanged colour does not reliably come back out
 *    unchanged. Saturation 1.0 is meant to be the identity, and it was not.
 *
 * The HSV round-trip below is in float and leaves the value untouched at
 * factor 1.0, which is the property the tests pin.
 */
fun adjustSaturation(
    color: Color,
    factor: Float,
): Color {
    val s = factor.coerceIn(MIN_SATURATION, MAX_SATURATION)
    if (s == 1f) return color

    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    // Achromatic: hue is undefined and saturation is 0, so there is nothing to
    // scale. Returning the input also avoids dividing by zero below.
    if (delta == 0f) return color

    val hue =
        when (max) {
            r -> ((g - b) / delta).mod(6f)
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        } * 60f
    val saturation = (delta / max).coerceIn(0f, 1f)
    val value = max

    val newSaturation = (saturation * s).coerceIn(0f, 1f)
    val chroma = value * newSaturation
    val secondary = chroma * (1f - kotlin.math.abs(((hue / 60f) % 2f) - 1f))
    val match = value - chroma

    val (nr, ng, nb) =
        when {
            hue < 60f -> Triple(chroma, secondary, 0f)
            hue < 120f -> Triple(secondary, chroma, 0f)
            hue < 180f -> Triple(0f, chroma, secondary)
            hue < 240f -> Triple(0f, secondary, chroma)
            hue < 300f -> Triple(secondary, 0f, chroma)
            else -> Triple(chroma, 0f, secondary)
        }
    return Color(nr + match, ng + match, nb + match, color.alpha)
}

private val BaseNeonPurple = Color(0xFF6A00FF)
private val BaseNeonCyan = Color(0xFF00B0C7)
private val BaseNeonPink = Color(0xFFE0007A)
private val BaseNeonGreen = Color(0xFF00B248)
private val BaseNeonRed = Color(0xFFD50000)
private val BaseNeonAmber = Color(0xFFED6C00)
private val BaseNeonBlue = Color(0xFF1565FF)
private val BaseNeonGold = Color(0xFFFFB300)

/**
 * The pre-theme-engine accents, kept as the source for [neonPaletteOf]'s
 * factor overload. [PaletteColors] objects supply their own bases instead.
 */
internal val neonBases =
    arrayOf(
        BaseNeonPurple,
        BaseNeonCyan,
        BaseNeonPink,
        BaseNeonGreen,
        BaseNeonRed,
        BaseNeonAmber,
        BaseNeonBlue,
        BaseNeonGold,
    )

internal const val MIN_SATURATION = 0.5f
internal const val MAX_SATURATION = 1.6f

/**
 * Single state holder for the accent layer.
 *
 * Previously a module-level `mutableStateListOf` was mutated 8 times for a single
 * user action (one state write per colour slot), and every one of ~500 call
 * sites read it through `val NeonCyan: Color get() = neonColors[1]`. One
 * immutable palette behind one state means one write and one invalidation per
 * action. The [ColorPalette] that produced it is held alongside so a palette
 * change can re-derive the accents without re-reading the persisted selection.
 *
 * [Stable] because the whole object is replaced on each change, never mutated
 * in place — without it Compose would treat the read as a per-composition
 * candidate and could skip consumers.
 */
@Stable
private data class ActiveAccents(
    val palette: NeonPalette,
    val saturation: Float,
)

private val activeAccentState = mutableStateOf(ActiveAccents(neonPaletteOf(1f), 1f))

/**
 * Repoints the whole accent layer at [palette].
 *
 * Called by [WuWaConfigTheme] rather than by the repository, because the accent
 * bases are owned by the resolved colour scheme — including the Android 12+
 * dynamic case, where there is no [ColorPalette.colorsFor] result at all.
 *
 * No-ops when the palette and saturation already match, so the common case of a
 * recomposition (rather than a user action) writes no state.
 */
internal fun setAccentPalette(
    palette: NeonPalette,
    saturation: Float,
) {
    val factor = saturation.coerceIn(MIN_SATURATION, MAX_SATURATION)
    val next = ActiveAccents(palette.scaledBy(factor), factor)
    if (next == activeAccentState.value) return
    activeAccentState.value = next
}

// Public accessor names are load-bearing (~500 call sites across ui/). They read
// through the single palette state object above.
val NeonPurple: Color get() = activeAccentState.value.palette.purple
val NeonCyan: Color get() = activeAccentState.value.palette.cyan
val NeonPink: Color get() = activeAccentState.value.palette.pink
val NeonGreen: Color get() = activeAccentState.value.palette.green
val NeonRed: Color get() = activeAccentState.value.palette.red
val NeonAmber: Color get() = activeAccentState.value.palette.amber
val NeonBlue: Color get() = activeAccentState.value.palette.blue
val NeonGold: Color get() = activeAccentState.value.palette.gold
