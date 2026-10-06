package com.wuwaconfig.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Maps a Wuthering Waves element name to the neon accent used to theme a
 * character's screen. Matching is case-insensitive over the trimmed name so a
 * differently-cased label still resolves; unknown or blank elements fall back to
 * [NeonCyan] rather than mis-colouring the whole screen.
 */
fun elementAccent(element: String): Color {
    val name = element.trim().lowercase()
    return when (name) {
        "glacio" -> NeonCyan
        "fusion" -> NeonAmber
        "aero" -> NeonGreen
        "electro" -> NeonPurple
        "spectro" -> NeonGold
        "havoc" -> NeonPink
        else -> NeonCyan
    }
}
