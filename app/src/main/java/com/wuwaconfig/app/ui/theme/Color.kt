package com.wuwaconfig.app.ui.theme

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import android.graphics.Color as AndroidColor

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

fun neonPaletteOf(factor: Float): NeonPalette {
    val f = factor.coerceIn(0.5f, 1.6f)
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

fun setNeonSaturation(value: Float) {
    val factor = value.coerceIn(0.5f, 1.6f)
    val next = neonPaletteOf(factor)
    if (next == neonState.value) return
    neonState.value = next
}

fun adjustSaturation(
    color: Color,
    factor: Float,
): Color {
    val argb =
        ((color.alpha * 255).toInt() shl 24) or
            ((color.red * 255).toInt() shl 16) or
            ((color.green * 255).toInt() shl 8) or
            (color.blue * 255).toInt()
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(argb, hsv)
    hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f)
    return Color(AndroidColor.HSVToColor((color.alpha * 255).toInt(), hsv))
}

private val BaseNeonPurple = Color(0xFF6A00FF)
private val BaseNeonCyan = Color(0xFF00B0C7)
private val BaseNeonPink = Color(0xFFE0007A)
private val BaseNeonGreen = Color(0xFF00B248)
private val BaseNeonRed = Color(0xFFD50000)
private val BaseNeonAmber = Color(0xFFED6C00)
private val BaseNeonBlue = Color(0xFF1565FF)
private val BaseNeonGold = Color(0xFFFFB300)

private val neonBases =
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

/**
 * Single state holder for the neon palette. Previously a module-level
 * `mutableStateListOf` was mutated 8 times for a single user action (one state
 * write per colour slot), and every one of ~500 call sites read it through
 * `val NeonCyan: Color get() = neonColors[1]`. One immutable palette behind one
 * state means one write and one invalidation per action.
 */
private val neonState = mutableStateOf(neonPaletteOf(1f))

// Public accessor names are load-bearing (~500 call sites across ui/). They read
// through the single palette state object below.
val NeonPurple: Color get() = neonState.value.purple
val NeonCyan: Color get() = neonState.value.cyan
val NeonPink: Color get() = neonState.value.pink
val NeonGreen: Color get() = neonState.value.green
val NeonRed: Color get() = neonState.value.red
val NeonAmber: Color get() = neonState.value.amber
val NeonBlue: Color get() = neonState.value.blue
val NeonGold: Color get() = neonState.value.gold
