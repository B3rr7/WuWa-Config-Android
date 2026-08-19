package com.wuwaconfig.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wuwaconfig.app.R

// Only the bold Rajdhani file is bundled, so register it for every weight the UI
// uses — otherwise Normal/Medium/SemiBold text silently falls back to the system
// font and Rajdhani looks identical to the default family.
val RajdhaniBold =
    FontFamily(
        Font(R.font.rajdhani_bold, FontWeight.Normal),
        Font(R.font.rajdhani_bold, FontWeight.Medium),
        Font(R.font.rajdhani_bold, FontWeight.SemiBold),
        Font(R.font.rajdhani_bold, FontWeight.Bold),
    )

// Bundled to guarantee distinct rendering regardless of the device's generic
// font fallback (some ROMs map both serif and monospace to the same Noto face).
val SerifFamily =
    FontFamily(
        Font(R.font.serif, FontWeight.Normal),
        Font(R.font.serif, FontWeight.Medium),
        Font(R.font.serif, FontWeight.SemiBold),
        Font(R.font.serif, FontWeight.Bold),
    )

val MonospaceFamily =
    FontFamily(
        Font(R.font.monospace, FontWeight.Normal),
        Font(R.font.monospace, FontWeight.Medium),
        Font(R.font.monospace, FontWeight.SemiBold),
        Font(R.font.monospace, FontWeight.Bold),
    )

val DisplayBold =
    TextStyle(
        fontFamily = RajdhaniBold,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    )

val Typography =
    Typography(
        headlineLarge =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
        headlineMedium =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleLarge =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp,
            ),
        titleMedium =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyLarge =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodySmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
    )
