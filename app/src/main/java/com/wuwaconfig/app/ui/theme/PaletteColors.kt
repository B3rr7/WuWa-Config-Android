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
        // Darkened one step from Tailwind cyan-600 (0x0891B2): as filled-button
        // containers these slots carry a white `on*`, and cyan-600/white measured
        // 3.68:1 — under the 4.5:1 AA floor. cyan-700 with white is 5.36:1.
        primary = Color(0xFF0E7490),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0F7FA),
        onPrimaryContainer = Color(0xFF00363F),
        // Amber keeps its brighter fill, so the on-colour flips to near-black
        // (Material does the same for yellow tertiaries): white measured 3.19:1.
        secondary = Color(0xFFD97706),
        onSecondary = Color(0xFF231000),
        secondaryContainer = Color(0xFFFFF3E0),
        onSecondaryContainer = Color(0xFF3E2723),
        // Green darkened green-600 -> green-700 for the same white-on reason
        // (3.77:1 -> 5.48:1).
        tertiary = Color(0xFF047857),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE8F5E9),
        onTertiaryContainer = Color(0xFF1B5E20),
        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),
        surface = Color.White,
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFE2E8F0),
        outlineVariant = Color(0xFFCBD5E1),
        error = Color(0xFFDC2626),
        onError = Color.White,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF991B1B),
        // Accent layer mirrors the slots that carry its hue; every value here is
        // used as text on light cards, so each clears 4.5:1 against white.
        accents =
            neonPaletteOf(
                purple = Color(0xFF0E7490),
                cyan = Color(0xFF0E7490),
                pink = Color(0xFFB45309),
                green = Color(0xFF047857),
                red = Color(0xFFDC2626),
                amber = Color(0xFFB45309),
                blue = Color(0xFF2563EB),
                gold = Color(0xFFB45309),
            ),
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
        primary = Color(0xFF22D3EE),
        onPrimary = Color(0xFF001216),
        primaryContainer = Color(0xFF0C2A32),
        onPrimaryContainer = Color(0xFF22D3EE),
        secondary = Color(0xFFF59E0B),
        onSecondary = Color(0xFF1C1400),
        secondaryContainer = Color(0xFF2C2000),
        onSecondaryContainer = Color(0xFFF59E0B),
        tertiary = Color(0xFF10B981),
        onTertiary = Color(0xFF001A12),
        tertiaryContainer = Color(0xFF002A1C),
        onTertiaryContainer = Color(0xFF10B981),
        background = Color(0xFF050510),
        onBackground = Color(0xFFE6EDF3),
        surface = Color(0xFF0B1026),
        onSurface = Color(0xFFE6EDF3),
        surfaceVariant = Color(0xFF111827),
        onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF334155),
        outlineVariant = Color(0xFF1E293B),
        error = Color(0xFFEF4444),
        // White on red-500 measured 3.76:1 — restores the pre-redesign polarity
        // (dark on red), which measured 5.09:1 here.
        onError = Color(0xFF1C0A0A),
        errorContainer = Color(0xFF450A0A),
        onErrorContainer = Color(0xFFEF4444),
        accents =
            neonPaletteOf(
                purple = Color(0xFF22D3EE),
                cyan = Color(0xFF22D3EE),
                pink = Color(0xFFF59E0B),
                green = Color(0xFF10B981),
                red = Color(0xFFEF4444),
                amber = Color(0xFFF59E0B),
                blue = Color(0xFF3B82F6),
                gold = Color(0xFFF59E0B),
            ),
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

// ── Soft Clay (Neumorphism's natural pigment) ─────────────────────────────────
// Warm putty greys: soft UI needs surface == background so a relief card reads
// as the page bulging out, which is why light mode repeats the same tone in
// both slots. Terracotta for the accent.
internal val ClayLight =
    PaletteColors(
        primary = Color(0xFFA6543B),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF4DDD3),
        onPrimaryContainer = Color(0xFF3D1408),
        secondary = Color(0xFF52664D),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDE7D8),
        onSecondaryContainer = Color(0xFF1F2A1C),
        tertiary = Color(0xFF4E5D66),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDCE5EA),
        onTertiaryContainer = Color(0xFF17242B),
        // Same tone as `surface` on purpose: the RELIEF surface mode paints
        // cards with it, and a pillow only reads when fill == page.
        background = Color(0xFFEFEAE3),
        onBackground = Color(0xFF2E2A24),
        surface = Color(0xFFEFEAE3),
        onSurface = Color(0xFF2E2A24),
        surfaceVariant = Color(0xFFE2DCD2),
        onSurfaceVariant = Color(0xFF5A5248),
        outline = Color(0xFFC6BEB0),
        outlineVariant = Color(0xFFD8D1C5),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
        accents =
            neonPaletteOf(
                purple = Color(0xFF6C5A46),
                cyan = Color(0xFF3F6472),
                pink = Color(0xFFA6543B),
                green = Color(0xFF52664D),
                red = Color(0xFFB3261E),
                amber = Color(0xFF8A6D1F),
                blue = Color(0xFF3F5C8A),
                gold = Color(0xFF8A6D1F),
            ),
    )

internal val ClayDark =
    PaletteColors(
        primary = Color(0xFFD98664),
        onPrimary = Color(0xFF2A1206),
        primaryContainer = Color(0xFF572A1B),
        onPrimaryContainer = Color(0xFFF6D3C2),
        secondary = Color(0xFFA8C2A6),
        onSecondary = Color(0xFF1A2418),
        secondaryContainer = Color(0xFF35443A),
        onSecondaryContainer = Color(0xFFD7E7DA),
        tertiary = Color(0xFF7FA0B8),
        onTertiary = Color(0xFF10222E),
        tertiaryContainer = Color(0xFF283A46),
        onTertiaryContainer = Color(0xFFCBE1F0),
        background = Color(0xFF211D18),
        onBackground = Color(0xFFF0E9DF),
        surface = Color(0xFF211D18),
        onSurface = Color(0xFFF0E9DF),
        surfaceVariant = Color(0xFF2B2620),
        onSurfaceVariant = Color(0xFFB4AA9C),
        outline = Color(0xFF4A4136),
        outlineVariant = Color(0xFF352E26),
        error = Color(0xFFE57373),
        onError = Color(0xFF2B0908),
        errorContainer = Color(0xFF5C1F1D),
        onErrorContainer = Color(0xFFFFDAD6),
        accents =
            neonPaletteOf(
                purple = Color(0xFFC4A77D),
                cyan = Color(0xFF7FA0B8),
                pink = Color(0xFFD98664),
                green = Color(0xFFA8C2A6),
                red = Color(0xFFE57373),
                amber = Color(0xFFD3A93C),
                blue = Color(0xFF6E8FC2),
                gold = Color(0xFFD3A93C),
            ),
    )

// ── Brushed Copper (Skeuomorphism's natural pigment) ──────────────────────────
// Machined steel base with copper hardware accents; the SUNKEN wells want a
// metal-toned surfaceVariant rather than a colour wash.
internal val CopperLight =
    PaletteColors(
        primary = Color(0xFF8C4A2F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF3D9CC),
        onPrimaryContainer = Color(0xFF3A150A),
        secondary = Color(0xFF5D6063),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2E3E5),
        onSecondaryContainer = Color(0xFF232527),
        tertiary = Color(0xFF42576B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD9E3EC),
        onTertiaryContainer = Color(0xFF0F1D29),
        background = Color(0xFFE8E7E5),
        onBackground = Color(0xFF2A2724),
        surface = Color(0xFFF2F1EF),
        onSurface = Color(0xFF2A2724),
        surfaceVariant = Color(0xFFDDDAD7),
        onSurfaceVariant = Color(0xFF55504B),
        outline = Color(0xFFBAB5B0),
        outlineVariant = Color(0xFFD6D2CE),
        error = Color(0xFFB3402E),
        onError = Color.White,
        errorContainer = Color(0xFFF9DAD3),
        onErrorContainer = Color(0xFF44100A),
        accents =
            neonPaletteOf(
                purple = Color(0xFF7A4E60),
                cyan = Color(0xFF3D6A78),
                pink = Color(0xFFA35349),
                green = Color(0xFF4F6A4F),
                red = Color(0xFFB3402E),
                amber = Color(0xFF96732B),
                blue = Color(0xFF46627E),
                gold = Color(0xFF96732B),
            ),
    )

internal val CopperDark =
    PaletteColors(
        primary = Color(0xFFD98F5E),
        onPrimary = Color(0xFF2B1304),
        primaryContainer = Color(0xFF552812),
        onPrimaryContainer = Color(0xFFF6CDB0),
        secondary = Color(0xFFB7BBBE),
        onSecondary = Color(0xFF1E2124),
        secondaryContainer = Color(0xFF31363A),
        onSecondaryContainer = Color(0xFFE1E7EC),
        tertiary = Color(0xFF7FA3C4),
        onTertiary = Color(0xFF0E1E2E),
        tertiaryContainer = Color(0xFF1E3547),
        onTertiaryContainer = Color(0xFFCFE3F5),
        background = Color(0xFF131110),
        onBackground = Color(0xFFEDE7E1),
        surface = Color(0xFF191715),
        onSurface = Color(0xFFEDE7E1),
        surfaceVariant = Color(0xFF221F1C),
        onSurfaceVariant = Color(0xFFA9A19A),
        outline = Color(0xFF4D4641),
        outlineVariant = Color(0xFF2C2926),
        error = Color(0xFFEF8A7E),
        onError = Color(0xFF2B0B07),
        errorContainer = Color(0xFF5C1A14),
        onErrorContainer = Color(0xFFFFDAD3),
        accents =
            neonPaletteOf(
                purple = Color(0xFFB98CA6),
                cyan = Color(0xFF7FA3C4),
                pink = Color(0xFFD98F74),
                green = Color(0xFF8FBB8F),
                red = Color(0xFFEF8A7E),
                amber = Color(0xFFD9A447),
                blue = Color(0xFF7FA3C4),
                gold = Color(0xFFD9A447),
            ),
    )

// ── Flat Pop (Flat 2.0's natural pigment) ─────────────────────────────────────
// Poster-flat: coral red and ink blue on paper white; dark mode rescues the
// same hues at high lightness over a deep neutral.
internal val FlatPopLight =
    PaletteColors(
        primary = Color(0xFFC04025),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFBE0D7),
        onPrimaryContainer = Color(0xFF3D1006),
        secondary = Color(0xFF1E3A5F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDE5EE),
        onSecondaryContainer = Color(0xFF12233A),
        tertiary = Color(0xFF17693F),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD2EDDD),
        onTertiaryContainer = Color(0xFF0A2A18),
        background = Color(0xFFFAFAF8),
        onBackground = Color(0xFF211F1C),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF211F1C),
        surfaceVariant = Color(0xFFF0EFEC),
        onSurfaceVariant = Color(0xFF57534E),
        outline = Color(0xFFE2E0DB),
        outlineVariant = Color(0xFFE8E6E2),
        error = Color(0xFFC04025),
        onError = Color.White,
        errorContainer = Color(0xFFFBE0D7),
        onErrorContainer = Color(0xFF3D1006),
        accents =
            neonPaletteOf(
                purple = Color(0xFF6D4FA3),
                cyan = Color(0xFF1D8A8A),
                pink = Color(0xFFC2467E),
                green = Color(0xFF3B8A4E),
                red = Color(0xFFC04025),
                amber = Color(0xFFA26B0E),
                blue = Color(0xFF3557A6),
                gold = Color(0xFFA26B0E),
            ),
    )

internal val FlatPopDark =
    PaletteColors(
        primary = Color(0xFFFF7A5C),
        onPrimary = Color(0xFF2B0B04),
        primaryContainer = Color(0xFF571E0F),
        onPrimaryContainer = Color(0xFFFFD9CB),
        secondary = Color(0xFF8FB4E3),
        onSecondary = Color(0xFF0E2036),
        secondaryContainer = Color(0xFF1C3049),
        onSecondaryContainer = Color(0xFFDCE8F6),
        tertiary = Color(0xFF66C79A),
        onTertiary = Color(0xFF0F2A1D),
        tertiaryContainer = Color(0xFF174531),
        onTertiaryContainer = Color(0xFFCEE9D8),
        background = Color(0xFF121114),
        onBackground = Color(0xFFECE9E6),
        surface = Color(0xFF191820),
        onSurface = Color(0xFFECE9E6),
        surfaceVariant = Color(0xFF24232B),
        onSurfaceVariant = Color(0xFFA8A4A0),
        outline = Color(0xFF46444A),
        outlineVariant = Color(0xFF2A2931),
        error = Color(0xFFFF7A5C),
        onError = Color(0xFF2B0B04),
        errorContainer = Color(0xFF571E0F),
        onErrorContainer = Color(0xFFFFD9CB),
        accents =
            neonPaletteOf(
                purple = Color(0xFFB48CE8),
                cyan = Color(0xFF5BC0BF),
                pink = Color(0xFFFF7FA8),
                green = Color(0xFF6FCB8B),
                red = Color(0xFFFF7A5C),
                amber = Color(0xFFF2B94B),
                blue = Color(0xFF8FB4E3),
                gold = Color(0xFFF2B94B),
            ),
    )

// ── Neo-Brutal Acid (Neo-Brutalism's natural pigment) ─────────────────────────
// Acid yellow-ink green + near-black ink. The light primary is deliberately the
// INK (Brutalism prints black), with the acid reserved for containers/accents;
// onPrimary is the acid, which is the stamped-poster polarity.
internal val AcidBrutLight =
    PaletteColors(
        primary = Color(0xFF33300A),
        onPrimary = Color(0xFFE9FF3B),
        primaryContainer = Color(0xFFEDEFB9),
        onPrimaryContainer = Color(0xFF23220B),
        secondary = Color(0xFF4C5B21),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2EBD2),
        onSecondaryContainer = Color(0xFF1D260C),
        tertiary = Color(0xFF8A2E2E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF3D5D0),
        onTertiaryContainer = Color(0xFF3A0F0F),
        background = Color(0xFFFFFDF0),
        onBackground = Color(0xFF171507),
        surface = Color(0xFFFFFBE4),
        onSurface = Color(0xFF171507),
        surfaceVariant = Color(0xFFF3EDCD),
        onSurfaceVariant = Color(0xFF53503C),
        outline = Color(0xFFD9D3AE),
        outlineVariant = Color(0xFFE9E5C6),
        error = Color(0xFF8A2E2E),
        onError = Color.White,
        errorContainer = Color(0xFFF3D5D0),
        onErrorContainer = Color(0xFF3A0F0F),
        accents =
            neonPaletteOf(
                purple = Color(0xFF6B5230),
                cyan = Color(0xFF2E6B2E),
                pink = Color(0xFF8A2E5E),
                green = Color(0xFF3E6B21),
                red = Color(0xFF8A2E2E),
                amber = Color(0xFF6E5A00),
                blue = Color(0xFF2E4D73),
                gold = Color(0xFF6E5A00),
            ),
    )

internal val AcidBrutDark =
    PaletteColors(
        primary = Color(0xFFE4FF3D),
        onPrimary = Color(0xFF1A1D00),
        primaryContainer = Color(0xFF3A3F0B),
        onPrimaryContainer = Color(0xFFEFFF9C),
        secondary = Color(0xFFC6C98E),
        onSecondary = Color(0xFF22240F),
        secondaryContainer = Color(0xFF2E3018),
        onSecondaryContainer = Color(0xFFECEDC6),
        tertiary = Color(0xFFE08A8A),
        onTertiary = Color(0xFF2A0808),
        tertiaryContainer = Color(0xFF4E1B1B),
        onTertiaryContainer = Color(0xFFFFD9D9),
        background = Color(0xFF0E0E06),
        onBackground = Color(0xFFF2F0DC),
        surface = Color(0xFF14140A),
        onSurface = Color(0xFFF2F0DC),
        surfaceVariant = Color(0xFF1E1E12),
        onSurfaceVariant = Color(0xFFAAA895),
        outline = Color(0xFF3F3F2A),
        outlineVariant = Color(0xFF23230F),
        error = Color(0xFFE08A8A),
        onError = Color(0xFF2A0808),
        errorContainer = Color(0xFF4E1B1B),
        onErrorContainer = Color(0xFFFFD9D9),
        accents =
            neonPaletteOf(
                purple = Color(0xFFCEB4F5),
                cyan = Color(0xFF6FE3C7),
                pink = Color(0xFFF2A0C8),
                green = Color(0xFFB6E36C),
                red = Color(0xFFFF6B5E),
                amber = Color(0xFFF5D34E),
                blue = Color(0xFF9DB9F5),
                gold = Color(0xFFF5D34E),
            ),
    )

// ── Paper Ink (Minimalism's natural pigment) ──────────────────────────────────
// Near-monochrome ink slates on paper; a single blue-grey accent family instead
// of a palette-wide hue sweep, because minimalist surface should not compete
// with pigment.
internal val PaperInkLight =
    PaletteColors(
        primary = Color(0xFF3F4C5E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E5EC),
        onPrimaryContainer = Color(0xFF131A24),
        secondary = Color(0xFF566070),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE7EAEE),
        onSecondaryContainer = Color(0xFF23282F),
        tertiary = Color(0xFF57706B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDFE7E8),
        onTertiaryContainer = Color(0xFF1A2628),
        background = Color(0xFFF7F6F4),
        onBackground = Color(0xFF20242A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF20242A),
        surfaceVariant = Color(0xFFEFEDEA),
        onSurfaceVariant = Color(0xFF565C66),
        outline = Color(0xFFD8D6D3),
        outlineVariant = Color(0xFFE3E1DE),
        error = Color(0xFFA93322),
        onError = Color.White,
        errorContainer = Color(0xFFF6DBD7),
        onErrorContainer = Color(0xFF3E1009),
        accents =
            neonPaletteOf(
                purple = Color(0xFF74688F),
                cyan = Color(0xFF496F7E),
                pink = Color(0xFF96606C),
                green = Color(0xFF4E7A63),
                red = Color(0xFFA93322),
                amber = Color(0xFF7E6320),
                blue = Color(0xFF4A6FA5),
                gold = Color(0xFF7E6320),
            ),
    )

internal val PaperInkDark =
    PaletteColors(
        primary = Color(0xFFA9BAD1),
        onPrimary = Color(0xFF131B26),
        primaryContainer = Color(0xFF2C3646),
        onPrimaryContainer = Color(0xFFD7E2F1),
        secondary = Color(0xFFC2C8D2),
        onSecondary = Color(0xFF1C2026),
        secondaryContainer = Color(0xFF32363E),
        onSecondaryContainer = Color(0xFFE4E7EC),
        tertiary = Color(0xFF9FB6B1),
        onTertiary = Color(0xFF14201E),
        tertiaryContainer = Color(0xFF243532),
        onTertiaryContainer = Color(0xFFD3E7E2),
        background = Color(0xFF101215),
        onBackground = Color(0xFFE9ECEF),
        surface = Color(0xFF17191D),
        onSurface = Color(0xFFE9ECEF),
        surfaceVariant = Color(0xFF212328),
        onSurfaceVariant = Color(0xFFA2A8B0),
        outline = Color(0xFF42464E),
        outlineVariant = Color(0xFF26282E),
        error = Color(0xFFE89088),
        onError = Color(0xFF2A0B07),
        errorContainer = Color(0xFF54201A),
        onErrorContainer = Color(0xFFF6DBD7),
        accents =
            neonPaletteOf(
                purple = Color(0xFFB7A9DC),
                cyan = Color(0xFF83BFCE),
                pink = Color(0xFFDFA3B0),
                green = Color(0xFF8FC7A8),
                red = Color(0xFFE89088),
                amber = Color(0xFFD9BC72),
                blue = Color(0xFF8FA5D2),
                gold = Color(0xFFD9BC72),
            ),
    )

// ── Retrowave (Retro-Futurism's natural pigment) ──────────────────────────────
// Synthwave magenta and violet with a sunset orange tertiary. Neon only in the
// dark variant; light is a lavender-paper base with deepened magenta that can
// actually read on white.
internal val RetrowaveDark =
    PaletteColors(
        primary = Color(0xFFFF5FA2),
        onPrimary = Color(0xFF2B0714),
        primaryContainer = Color(0xFF57163A),
        onPrimaryContainer = Color(0xFFFFD1E5),
        secondary = Color(0xFF9E93E8),
        onSecondary = Color(0xFF150E30),
        secondaryContainer = Color(0xFF2A2350),
        onSecondaryContainer = Color(0xFFDCD6F8),
        tertiary = Color(0xFFFF9840),
        onTertiary = Color(0xFF2E1200),
        tertiaryContainer = Color(0xFF5C2A02),
        onTertiaryContainer = Color(0xFFFFCCB8),
        background = Color(0xFF130912),
        onBackground = Color(0xFFF5E6F2),
        surface = Color(0xFF1A0C18),
        onSurface = Color(0xFFF5E6F2),
        surfaceVariant = Color(0xFF221022),
        onSurfaceVariant = Color(0xFFB1A0B4),
        outline = Color(0xFF4E2748),
        outlineVariant = Color(0xFF2E1730),
        error = Color(0xFFFF6B62),
        onError = Color(0xFF2B0705),
        errorContainer = Color(0xFF571612),
        onErrorContainer = Color(0xFFFFD5D1),
        accents =
            neonPaletteOf(
                purple = Color(0xFF9E93E8),
                cyan = Color(0xFF66C7D9),
                pink = Color(0xFFFF5FA2),
                green = Color(0xFF79D9A0),
                red = Color(0xFFFF6B62),
                amber = Color(0xFFFF9840),
                blue = Color(0xFF7E9BF2),
                gold = Color(0xFFFFC145),
            ),
    )

internal val RetrowaveLight =
    PaletteColors(
        primary = Color(0xFFA11758),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFBD6E7),
        onPrimaryContainer = Color(0xFF3A0722),
        secondary = Color(0xFF4B3F94),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2DDF2),
        onSecondaryContainer = Color(0xFF191143),
        tertiary = Color(0xFFB35A10),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE0C5),
        onTertiaryContainer = Color(0xFF341100),
        background = Color(0xFFF7F1F6),
        onBackground = Color(0xFF231A24),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF231A24),
        surfaceVariant = Color(0xFFEFEBF0),
        onSurfaceVariant = Color(0xFF5E5260),
        outline = Color(0xFFDBD2DC),
        outlineVariant = Color(0xFFE8E1EA),
        error = Color(0xFFBE3325),
        onError = Color.White,
        errorContainer = Color(0xFFFBD9D4),
        onErrorContainer = Color(0xFF3F0C07),
        accents =
            neonPaletteOf(
                purple = Color(0xFF6C3D9E),
                cyan = Color(0xFF1F7E8C),
                pink = Color(0xFFBE2E70),
                green = Color(0xFF2E7D5B),
                red = Color(0xFFBE3325),
                amber = Color(0xFFA5690F),
                blue = Color(0xFF40529E),
                gold = Color(0xFFA5690F),
            ),
    )
