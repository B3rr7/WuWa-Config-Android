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

/**
 * Resolves the guide's numeric element id to its name.
 *
 * Live `/role/avatar/list` and `/introduction/info` rows carry only `element.gbId` and the
 * two CDN pictures — the `element.texts` array the build parser looked for is not present.
 * The ids below were read from the guide's own roster: 1 Glacio, 2 Fusion, 3 Electro,
 * 4 Aero, 5 Spectro, 6 Havoc. Nothing else is guessed: an unknown id returns null so callers
 * can fall back to the scheme's own text colour.
 */
fun elementNameForGbId(gbId: String): String? =
    when (gbId.trim()) {
        "1" -> "Glacio"
        "2" -> "Fusion"
        "3" -> "Electro"
        "4" -> "Aero"
        "5" -> "Spectro"
        "6" -> "Havoc"
        else -> null
    }
