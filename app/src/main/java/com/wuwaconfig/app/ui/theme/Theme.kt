package com.wuwaconfig.app.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
    darkColorScheme(
        primary = NeonPurple,
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF4A1E8A),
        onPrimaryContainer = NeonPurple,
        secondary = NeonCyan,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF006880),
        onSecondaryContainer = NeonCyan,
        tertiary = NeonPink,
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF680020),
        onTertiaryContainer = NeonPink,
        background = DarkBg,
        onBackground = Color.White,
        surface = DarkSurface,
        onSurface = Color.White,
        surfaceVariant = CardSurface,
        onSurfaceVariant = Color(0xFFECE8FF),
        outline = Color(0xFF3A3A5C),
        outlineVariant = Color(0xFF252550),
        error = NeonRed,
        onError = Color.Black,
        errorContainer = Color(0xFF680010),
        onErrorContainer = NeonRed,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF7C4DFF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADEFF),
        onPrimaryContainer = Color(0xFF2A0080),
        secondary = Color(0xFF008394),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFB3EBFF),
        onSecondaryContainer = Color(0xFF001F29),
        tertiary = Color(0xFFBF3C6B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD9E2),
        onTertiaryContainer = Color(0xFF3E001D),
        background = LightBg,
        onBackground = Color(0xFF1C1B1F),
        surface = LightSurface,
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFFC4C6D0),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

@Suppress("ktlint:standard:function-naming")
@Composable
fun WuWaConfigTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = false,
    textOpacity: Float = 1f,
    content: @Composable () -> Unit,
) {
    val isDark =
        when (themeMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    val baseScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            isDark -> DarkColorScheme
            else -> LightColorScheme
        }
    val alpha = textOpacity.coerceIn(0.5f, 1f)
    // Deepen text: lerp the RGB toward the max-contrast color so even dimmed
    // (.copy(alpha=...)) call-site texts become more readable at higher settings.
    val contrastTarget = if (isDark) Color.White else Color(0xFF000000)
    val deepenFraction = ((alpha - 0.75f) / 0.25f).coerceIn(0f, 1f) * 0.4f

    fun tune(base: Color): Color = lerp(base, contrastTarget, deepenFraction).copy(alpha = alpha)

    val colorScheme =
        baseScheme.copy(
            onBackground = tune(baseScheme.onBackground),
            onSurface = tune(baseScheme.onSurface),
            onSurfaceVariant = tune(baseScheme.onSurfaceVariant),
            onPrimaryContainer = tune(baseScheme.onPrimaryContainer),
            onSecondaryContainer = tune(baseScheme.onSecondaryContainer),
            onTertiaryContainer = tune(baseScheme.onTertiaryContainer),
        )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.setBackgroundDrawable(ColorDrawable(colorScheme.background.toArgb()))
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
