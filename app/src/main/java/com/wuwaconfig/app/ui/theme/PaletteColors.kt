package com.wuwaconfig.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A full set of Material 3 colour slots plus the eight accent colours that the
 * app's existing `NeonXxx` call sites read.
 *
 * The two groups are kept in one object on purpose. A palette that set the M3
 * slots but not the accents would theme every `Card` while leaving every
 * `elementAccent` result and every `NeonCyan` default painting the old colour —
 * so the change would be visible in the settings screen and nowhere else.
 */
data class PaletteColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    /** The legacy accent layer. `NeonPurple`/`NeonCyan`/... read from this. */
    val accents: NeonPalette,
)

/**
 * The app's pre-theme-engine light scheme, verbatim from the old `Theme.kt`.
 *
 * Kept as SYSTEM_DEFAULT's light fallback so that on API 26-31 — where dynamic
 * colour does not exist — the app looks exactly as it did before this refactor.
 * "Exactly" matters: SYSTEM_DEFAULT is the default palette, so any drift here
 * would be a visible unrequested repaint for every user on those API levels.
 */
internal val LightFallbackColors =
    PaletteColors(
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
        outlineVariant = Color(0xFFE0E1EC),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        accents = neonPaletteOf(1f),
    )

/**
 * The app's pre-theme-engine dark scheme, verbatim from the old `Theme.kt`.
 *
 * Identical to [LightFallbackColors] for the accent layer because the old dark
 * branch used the same `neonPaletteOf(colorSaturation)` — a single palette shared
 * by both modes. The new engine fixes that asymmetry without changing what the
 * SYSTEM_DEFAULT default looks like.
 */
internal val DarkFallbackColors =
    PaletteColors(
        primary = Color(0xFF6A00FF),
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF4A1E8A),
        onPrimaryContainer = Color(0xFF6A00FF),
        secondary = Color(0xFF00B0C7),
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF006880),
        onSecondaryContainer = Color(0xFF00B0C7),
        tertiary = Color(0xFFE0007A),
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF680020),
        onTertiaryContainer = Color(0xFFE0007A),
        background = DarkBg,
        onBackground = Color.White,
        surface = DarkSurface,
        onSurface = Color.White,
        surfaceVariant = CardSurface,
        onSurfaceVariant = Color(0xFFECE8FF),
        outline = Color(0xFF3A3A5C),
        outlineVariant = Color(0xFF252550),
        error = Color(0xFFD50000),
        onError = Color.Black,
        errorContainer = Color(0xFF680010),
        onErrorContainer = Color(0xFFD50000),
        accents = neonPaletteOf(1f),
    )

// ── Wuthering Gold ────────────────────────────────────────────────────────────
// Charcoal #12131A base, gold #D4AF37 accent. The dark variant uses the brief's
// exact hex values; the light variant darkens the gold substantially because
// #D4AF37 on #FAF7F0 is roughly 1.9:1 — well under the 4.5:1 body-text floor.
internal val GoldDark =
    PaletteColors(
        primary = Color(0xFFD4AF37),
        onPrimary = Color(0xFF12131A),
        primaryContainer = Color(0xFF3D3417),
        onPrimaryContainer = Color(0xFFF0DFA8),
        secondary = Color(0xFFB8974A),
        onSecondary = Color(0xFF12131A),
        secondaryContainer = Color(0xFF3A3119),
        onSecondaryContainer = Color(0xFFEBD9A8),
        tertiary = Color(0xFF8FA36B),
        onTertiary = Color(0xFF12131A),
        tertiaryContainer = Color(0xFF2F3626),
        onTertiaryContainer = Color(0xFFC7D6A8),
        background = Color(0xFF12131A),
        onBackground = Color(0xFFEDE7D8),
        surface = Color(0xFF1A1B23),
        onSurface = Color(0xFFEDE7D8),
        surfaceVariant = Color(0xFF23242E),
        onSurfaceVariant = Color(0xFFB9B3A3),
        outline = Color(0xFF4A4636),
        outlineVariant = Color(0xFF2C2D38),
        error = Color(0xFFE5645F),
        onError = Color(0xFF12131A),
        errorContainer = Color(0xFF5C1F1D),
        onErrorContainer = Color(0xFFFFDAD6),
        accents =
            neonPaletteOf(
                purple = Color(0xFFB8974A),
                cyan = Color(0xFF6FA8C7),
                pink = Color(0xFFC98BA0),
                green = Color(0xFF8FA36B),
                red = Color(0xFFE5645F),
                amber = Color(0xFFD4AF37),
                blue = Color(0xFF6C86B8),
                gold = Color(0xFFD4AF37),
            ),
    )

internal val GoldLight =
    PaletteColors(
        primary = Color(0xFF7A5F10),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF2E4BC),
        onPrimaryContainer = Color(0xFF2A2003),
        secondary = Color(0xFF6B5420),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEFE1BE),
        onSecondaryContainer = Color(0xFF241B05),
        tertiary = Color(0xFF4B5A33),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD2E4BA),
        onTertiaryContainer = Color(0xFF17210A),
        background = Color(0xFFFAF7F0),
        onBackground = Color(0xFF1C1B18),
        surface = Color(0xFFFFFDF8),
        onSurface = Color(0xFF1C1B18),
        surfaceVariant = Color(0xFFEFE9DC),
        onSurfaceVariant = Color(0xFF4E493C),
        outline = Color(0xFF817A68),
        outlineVariant = Color(0xFFD6D0C0),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        accents =
            neonPaletteOf(
                purple = Color(0xFF7A5F10),
                cyan = Color(0xFF1F5C78),
                pink = Color(0xFF8C3A57),
                green = Color(0xFF4B5A33),
                red = Color(0xFFBA1A1A),
                amber = Color(0xFF8A5A00),
                blue = Color(0xFF2F4E8C),
                gold = Color(0xFF7A5F10),
            ),
    )

// ── Cyber Punk ────────────────────────────────────────────────────────────────
// Neon pink #FF2E88 and cyan #00E5FF on near-black. Those exact hues only exist
// in the dark variant; the light variant is a deep ink base with darker magenta
// and teal, because neon on white cannot reach contrast at any lightness.
internal val CyberDark =
    PaletteColors(
        primary = Color(0xFFFF2E88),
        onPrimary = Color(0xFF1A0009),
        primaryContainer = Color(0xFF6B0A34),
        onPrimaryContainer = Color(0xFFFFD3E4),
        secondary = Color(0xFF00E5FF),
        onSecondary = Color(0xFF001518),
        secondaryContainer = Color(0xFF004F57),
        onSecondaryContainer = Color(0xFFB2F1F8),
        tertiary = Color(0xFF9B5CFF),
        onTertiary = Color(0xFF12002B),
        tertiaryContainer = Color(0xFF3A1C66),
        onTertiaryContainer = Color(0xFFE7D6FF),
        background = Color(0xFF0A0A14),
        onBackground = Color(0xFFE8F6FF),
        surface = Color(0xFF12121F),
        onSurface = Color(0xFFE8F6FF),
        surfaceVariant = Color(0xFF1C1C2E),
        onSurfaceVariant = Color(0xFFA8B4CE),
        outline = Color(0xFF3A3D57),
        outlineVariant = Color(0xFF23253A),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF1A0009),
        errorContainer = Color(0xFF6B1414),
        onErrorContainer = Color(0xFFFFDAD6),
        accents =
            neonPaletteOf(
                purple = Color(0xFF9B5CFF),
                cyan = Color(0xFF00E5FF),
                pink = Color(0xFFFF2E88),
                green = Color(0xFF00FF9D),
                red = Color(0xFFFF4D4D),
                amber = Color(0xFFFFC400),
                blue = Color(0xFF2979FF),
                gold = Color(0xFFFFD54A),
            ),
    )

internal val CyberLight =
    PaletteColors(
        primary = Color(0xFFB3005C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E6),
        onPrimaryContainer = Color(0xFF3E0019),
        secondary = Color(0xFF006874),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFB2EBF2),
        onSecondaryContainer = Color(0xFF001F24),
        tertiary = Color(0xFF5B2EA8),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE7D6FF),
        onTertiaryContainer = Color(0xFF1E003F),
        background = Color(0xFFF4F7FB),
        onBackground = Color(0xFF0D1117),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0D1117),
        surfaceVariant = Color(0xFFE3E8F0),
        onSurfaceVariant = Color(0xFF41495A),
        outline = Color(0xFF717A8C),
        outlineVariant = Color(0xFFC4CCDA),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        accents =
            neonPaletteOf(
                purple = Color(0xFF5B2EA8),
                cyan = Color(0xFF006874),
                pink = Color(0xFFB3005C),
                green = Color(0xFF006B3C),
                red = Color(0xFFBA1A1A),
                amber = Color(0xFF8A5A00),
                blue = Color(0xFF1F4FB8),
                gold = Color(0xFF8A6D12),
            ),
    )

// ── Matrix Green ───────────────────────────────────────────────────────────────
// Terminal black with lime #00FF41. Lime is the brightest legible colour against
// black (~15:1) which is why this palette can afford pure #000000 backgrounds.
internal val MatrixDark =
    PaletteColors(
        primary = Color(0xFF00FF41),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF00531B),
        onPrimaryContainer = Color(0xFFB6FFC9),
        secondary = Color(0xFF00CC33),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF004414),
        onSecondaryContainer = Color(0xFFA6F5BA),
        tertiary = Color(0xFF7CFF9B),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF124D22),
        onTertiaryContainer = Color(0xFFC8FFDA),
        background = Color(0xFF000000),
        onBackground = Color(0xFFC8FFC8),
        surface = Color(0xFF050805),
        onSurface = Color(0xFFC8FFC8),
        surfaceVariant = Color(0xFF0D140D),
        onSurfaceVariant = Color(0xFF8FBF95),
        outline = Color(0xFF1E3A26),
        outlineVariant = Color(0xFF11200F),
        error = Color(0xFFFF4D4D),
        onError = Color(0xFF000000),
        errorContainer = Color(0xFF5C1010),
        onErrorContainer = Color(0xFFFFDAD6),
        accents =
            neonPaletteOf(
                purple = Color(0xFF2BD97A),
                cyan = Color(0xFF00FF41),
                pink = Color(0xFF00C853),
                green = Color(0xFF00FF41),
                red = Color(0xFFFF4D4D),
                amber = Color(0xFFB2FF59),
                blue = Color(0xFF00B0C7),
                gold = Color(0xFFFFD54A),
            ),
    )

internal val MatrixLight =
    PaletteColors(
        primary = Color(0xFF006B1D),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB7F5C6),
        onPrimaryContainer = Color(0xFF00210A),
        secondary = Color(0xFF005222),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFB7F5C6),
        onSecondaryContainer = Color(0xFF001A07),
        tertiary = Color(0xFF3F6B12),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC6E8A6),
        onTertiaryContainer = Color(0xFF122100),
        background = Color(0xFFF2FFF5),
        onBackground = Color(0xFF0A1A0E),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0A1A0E),
        surfaceVariant = Color(0xFFDFF3E4),
        onSurfaceVariant = Color(0xFF3C5241),
        outline = Color(0xFF6C8474),
        outlineVariant = Color(0xFFC0D8C7),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        accents =
            neonPaletteOf(
                purple = Color(0xFF006B3C),
                cyan = Color(0xFF006B1D),
                pink = Color(0xFF006B4F),
                green = Color(0xFF006B1D),
                red = Color(0xFFBA1A1A),
                amber = Color(0xFF5C6B00),
                blue = Color(0xFF00604A),
                gold = Color(0xFF4B6B00),
            ),
    )

// ── Nord Ice ───────────────────────────────────────────────────────────────────
// The Nord palette: #2E3440 base, #88C0D0 frost, #5E81AC steel, #A3BE8C moss.
internal val NordDark =
    PaletteColors(
        primary = Color(0xFF88C0D0),
        onPrimary = Color(0xFF2E3440),
        primaryContainer = Color(0xFF3C4A56),
        onPrimaryContainer = Color(0xFFC3DCEA),
        secondary = Color(0xFF5E81AC),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF3A4A5C),
        onSecondaryContainer = Color(0xFFC6D5E6),
        tertiary = Color(0xFFA3BE8C),
        onTertiary = Color(0xFF2E3440),
        tertiaryContainer = Color(0xFF3E4A36),
        onTertiaryContainer = Color(0xFFD2E3C1),
        background = Color(0xFF2E3440),
        onBackground = Color(0xFFE5E9F0),
        surface = Color(0xFF3B4252),
        onSurface = Color(0xFFE5E9F0),
        surfaceVariant = Color(0xFF434C5E),
        onSurfaceVariant = Color(0xFFD8DEE9),
        outline = Color(0xFF4C566A),
        outlineVariant = Color(0xFF3B4252),
        error = Color(0xFFBF616A),
        onError = Color(0xFF2E3440),
        errorContainer = Color(0xFF5A2A2E),
        onErrorContainer = Color(0xFFF7D6D8),
        accents =
            neonPaletteOf(
                purple = Color(0xFFB48EAD),
                cyan = Color(0xFF88C0D0),
                pink = Color(0xFFB48EAD),
                green = Color(0xFFA3BE8C),
                red = Color(0xFFBF616A),
                amber = Color(0xFFEBCB8B),
                blue = Color(0xFF5E81AC),
                gold = Color(0xFFEBCB8B),
            ),
    )

internal val NordLight =
    PaletteColors(
        primary = Color(0xFF3B6E9E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFCFE6F2),
        onPrimaryContainer = Color(0xFF123449),
        secondary = Color(0xFF4C5F8A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD8E2EF),
        onSecondaryContainer = Color(0xFF1B2740),
        tertiary = Color(0xFF5E7A4A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDCE7CB),
        onTertiaryContainer = Color(0xFF1F2C12),
        background = Color(0xFFECEFF4),
        onBackground = Color(0xFF2E3440),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF2E3440),
        surfaceVariant = Color(0xFFE5E9F0),
        onSurfaceVariant = Color(0xFF4C566A),
        outline = Color(0xFF8993A6),
        outlineVariant = Color(0xFFD0D7E1),
        error = Color(0xFFA03238),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        accents =
            neonPaletteOf(
                purple = Color(0xFF7D5C78),
                cyan = Color(0xFF3B6E9E),
                pink = Color(0xFF7D5C78),
                green = Color(0xFF5E7A4A),
                red = Color(0xFFA03238),
                amber = Color(0xFF8A6D2F),
                blue = Color(0xFF3B5A8C),
                gold = Color(0xFF8A6D2F),
            ),
    )

// ── Sakura Vapor ───────────────────────────────────────────────────────────────
// Twilight pastel pink #FF9ECD and purple #C79BFF. The lightest of the custom
// palettes in dark mode, so it deliberately keeps more chroma in its containers.
internal val SakuraDark =
    PaletteColors(
        primary = Color(0xFFFF9ECD),
        onPrimary = Color(0xFF3B0A22),
        primaryContainer = Color(0xFF6E2A4E),
        onPrimaryContainer = Color(0xFFFFDCEB),
        secondary = Color(0xFFC79BFF),
        onSecondary = Color(0xFF2B1145),
        secondaryContainer = Color(0xFF522E73),
        onSecondaryContainer = Color(0xFFEBDDFF),
        tertiary = Color(0xFF7FD4E8),
        onTertiary = Color(0xFF00333D),
        tertiaryContainer = Color(0xFF1B5461),
        onTertiaryContainer = Color(0xFFC4ECF5),
        background = Color(0xFF2B1B3D),
        onBackground = Color(0xFFF6ECFF),
        surface = Color(0xFF3A2550),
        onSurface = Color(0xFFF6ECFF),
        surfaceVariant = Color(0xFF4A3063),
        onSurfaceVariant = Color(0xFFD9C7EC),
        outline = Color(0xFF6A4A88),
        outlineVariant = Color(0xFF38244C),
        error = Color(0xFFFF6B8A),
        onError = Color(0xFF3B0A22),
        errorContainer = Color(0xFF6B1F33),
        onErrorContainer = Color(0xFFFFD9E2),
        accents =
            neonPaletteOf(
                purple = Color(0xFFC79BFF),
                cyan = Color(0xFF7FD4E8),
                pink = Color(0xFFFF9ECD),
                green = Color(0xFF9BE8C4),
                red = Color(0xFFFF6B8A),
                amber = Color(0xFFFFC97A),
                blue = Color(0xFF9BB8FF),
                gold = Color(0xFFFFD9A0),
            ),
    )

internal val SakuraLight =
    PaletteColors(
        primary = Color(0xFFB24A85),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9EA),
        onPrimaryContainer = Color(0xFF3E0A26),
        secondary = Color(0xFF7A4FB8),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9DDFF),
        onSecondaryContainer = Color(0xFF28094A),
        tertiary = Color(0xFF1F7A8C),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC6ECF5),
        onTertiaryContainer = Color(0xFF04272F),
        background = Color(0xFFFDF3F8),
        onBackground = Color(0xFF2B1B3D),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF2B1B3D),
        surfaceVariant = Color(0xFFF6E7F3),
        onSurfaceVariant = Color(0xFF5E4A72),
        outline = Color(0xFF8D7399),
        outlineVariant = Color(0xFFDCC9E6),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        accents =
            neonPaletteOf(
                purple = Color(0xFF7A4FB8),
                cyan = Color(0xFF1F7A8C),
                pink = Color(0xFFB24A85),
                green = Color(0xFF1F7A4F),
                red = Color(0xFFB3261E),
                amber = Color(0xFF8A5A00),
                blue = Color(0xFF3B5A9E),
                gold = Color(0xFF9E6B1F),
            ),
    )

// ── Midnight OLED ──────────────────────────────────────────────────────────────
// True black in BOTH modes. The point of an OLED panel is that unlit pixels draw
// no power, so #000000 is not a stylistic choice here — it is the only colour
// that satisfies the panel, which is also why this palette forces dark.
internal val OledDark =
    PaletteColors(
        primary = Color(0xFFE8E8F0),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF1C1C22),
        onPrimaryContainer = Color(0xFFF2F2F7),
        secondary = Color(0xFF9A9AA8),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF16161B),
        onSecondaryContainer = Color(0xFFD8D8E2),
        tertiary = Color(0xFF6E6E7C),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF141419),
        onTertiaryContainer = Color(0xFFC6C6D2),
        background = Color(0xFF000000),
        onBackground = Color(0xFFEDEDF2),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFEDEDF2),
        surfaceVariant = Color(0xFF141419),
        onSurfaceVariant = Color(0xFF9E9EAE),
        outline = Color(0xFF3C3C46),
        outlineVariant = Color(0xFF1A1A20),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF000000),
        errorContainer = Color(0xFF3D0F0F),
        onErrorContainer = Color(0xFFFFDAD6),
        accents =
            neonPaletteOf(
                purple = Color(0xFFC9C9E0),
                cyan = Color(0xFF9FD8E8),
                pink = Color(0xFFE0B8D0),
                green = Color(0xFFA8D8A8),
                red = Color(0xFFFF6B6B),
                amber = Color(0xFFE8C48A),
                blue = Color(0xFFA8BEE8),
                gold = Color(0xFFD8D8E2),
            ),
    )

internal val OledLight =
    PaletteColors(
        // Still black background — see the note above. The light "variant"
        // exists only so colorsFor(isDark = false) has something coherent to
        // return; forceDark means the theme never actually selects it.
        primary = Color(0xFFEDEDF2),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF1C1C22),
        onPrimaryContainer = Color(0xFFF2F2F7),
        secondary = Color(0xFFB4B4C0),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF16161B),
        onSecondaryContainer = Color(0xFFD8D8E2),
        tertiary = Color(0xFF8A8A98),
        onTertiary = Color(0xFF000000),
        tertiaryContainer = Color(0xFF141419),
        onTertiaryContainer = Color(0xFFC6C6D2),
        background = Color(0xFF000000),
        onBackground = Color(0xFFEDEDF2),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFEDEDF2),
        surfaceVariant = Color(0xFF141419),
        onSurfaceVariant = Color(0xFF9E9EAE),
        outline = Color(0xFF3C3C46),
        outlineVariant = Color(0xFF1A1A20),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF000000),
        errorContainer = Color(0xFF3D0F0F),
        onErrorContainer = Color(0xFFFFDAD6),
        accents = OledDark.accents,
    )
