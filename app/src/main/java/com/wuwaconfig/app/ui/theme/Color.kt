package com.wuwaconfig.app.ui.theme

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

private var _neonSaturation = 1f

fun setNeonSaturation(value: Float) {
    _neonSaturation = value.coerceIn(0.5f, 1.6f)
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

val NeonPurple: Color get() = adjustSaturation(BaseNeonPurple, _neonSaturation)
val NeonCyan: Color get() = adjustSaturation(BaseNeonCyan, _neonSaturation)
val NeonPink: Color get() = adjustSaturation(BaseNeonPink, _neonSaturation)
val NeonGreen: Color get() = adjustSaturation(BaseNeonGreen, _neonSaturation)
val NeonRed: Color get() = adjustSaturation(BaseNeonRed, _neonSaturation)
val NeonAmber: Color get() = adjustSaturation(BaseNeonAmber, _neonSaturation)
val NeonBlue: Color get() = adjustSaturation(BaseNeonBlue, _neonSaturation)
val NeonGold: Color get() = adjustSaturation(BaseNeonGold, _neonSaturation)

val GlassDialogBg = Color(0xCC12122A)
val GlassDialogBorder = Color(0x28FFFFFF)
