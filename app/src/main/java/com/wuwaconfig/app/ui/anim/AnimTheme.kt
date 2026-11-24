package com.wuwaconfig.app.ui.anim

/*
 * Bridges the theme engine's active accent palette into the Canvas sims.
 *
 * The sims were written with hardcoded HSL hues (205 blue idle, 140 green
 * success, 0 red failure, 38 amber warning). This file derives the same hue
 * values from the live `NeonXxx` accents so a palette switch (SYSTEM_DEFAULT,
 * WUTHERING_GOLD, MATRIX_GREEN, ...) recolors the animations instead of
 * leaving them frozen blue.
 *
 * `hueOf` is hand-written max/min HSV math for the same reason
 * `adjustSaturation` exists: android.graphics.Color.colorToHSV is a stubbed
 * android.jar method under unit tests and would return garbage here.
 *
 * Captured once per composition via [rememberAnimTheme]; a palette change
 * mid-run does NOT restart the sim (the overlay is transient — seconds long —
 * so the stale hue self-corrects on the next run).
 */

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.wuwaconfig.app.ui.theme.NeonAmber
import com.wuwaconfig.app.ui.theme.NeonBlue
import com.wuwaconfig.app.ui.theme.NeonCyan
import com.wuwaconfig.app.ui.theme.NeonGreen
import com.wuwaconfig.app.ui.theme.NeonRed

/**
 * The five hue roles the sims actually read. Each field defaults to the
 * original hardcoded value, so [builtin] is byte-for-byte the pre-theme look.
 */
data class AnimTheme(
    /** Idle / laser / file-glow accent. Was 205f. */
    val idleHue: Float,
    /** Success / matrix rain / "BRAIN SYNCED". Was 140f. */
    val okHue: Float,
    /** Glitch, shatter, "LOAD FAILED", "OFFLINE". Was 0f. */
    val failHue: Float,
    /** Mid-load warning tint and the glitch lead-in. Was 38f. */
    val warnHue: Float,
    /** The chromatic ghost file drawn during glitch. Was 185f. */
    val glitch2Hue: Float,
) {
    companion object {
        /** The authors' original hues, for fallbacks and previews. */
        fun builtin(): AnimTheme =
            AnimTheme(
                idleHue = 205f,
                okHue = 140f,
                failHue = 0f,
                warnHue = 38f,
                glitch2Hue = 185f,
            )
    }
}

/**
 * Pure HSV hue from a Compose Color. Returns 0..360; grey / alpha-flat colors
 * degenerate to 0f, which only happens if a palette ships a desaturated slot —
 * the "warn" clamp in SATURATION keeps every palette's slots chromatic.
 */
internal fun hueOf(c: Color): Float {
    val r = c.red
    val g = c.green
    val b = c.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    if (d <= 1e-5f) return 0f
    val h =
        when (max) {
            r -> ((g - b) / d).mod6()
            g -> ((b - r) / d + 2f).mod6()
            else -> ((r - g) / d + 4f).mod6()
        }
    return h * 60f
}

private fun Float.mod6(): Float {
    var v = this % 6f
    if (v < 0f) v += 6f
    return v
}

@Composable
fun rememberAnimTheme(): AnimTheme {
    val cyan = NeonCyan
    val green = NeonGreen
    val red = NeonRed
    val amber = NeonAmber
    val blue = NeonBlue
    return remember(cyan, green, red, amber, blue) {
        AnimTheme(
            idleHue = hueOf(cyan),
            okHue = hueOf(green),
            failHue = hueOf(red),
            warnHue = hueOf(amber),
            glitch2Hue = hueOf(blue),
        )
    }
}
