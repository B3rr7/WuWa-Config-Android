package com.wuwaconfig.app.ui.theme

import androidx.compose.ui.unit.dp

// The two independent axes of the theme engine.
//
// [UiStyle] is *structure*: corner radii, stroke weights, padding, whether blur
// and glow are drawn at all. [ColorPalette] is *pigment*: which hex values land
// in the Material 3 colour slots. They are deliberately orthogonal — a user who
// picks MIDNIGHT_OLED (pure black) and MATRIX_GREEN (lime accents) gets black
// panels with lime text, and neither enum has to know about the other.
//
// Both enums are persisted by **name**, never by ordinal. Renaming a constant is
// then a saved-state change for every user who had selected it, whereas
// `prefs[uiStyle.ordinal]` silently reassigns them to a different style the next
// time a constant is inserted. [UiStyle.fromStorageKey] treats an unknown or
// absent key as absent rather than throwing, so a downgrade cannot brick startup.

/**
 * Structural skin. Selects the [ThemeShapeTokens] every card, button, field and
 * dialog in the app reads.
 *
 * [MATERIAL_YOU] is the default on purpose: it is the only style that inherits
 * the device's own shape language and dynamic colour, so it is the safe choice
 * on an unfamiliar ROM where a hand-tuned skin might fight the system.
 */
enum class UiStyle(
    val displayName: String,
    val description: String,
    val tokens: ThemeShapeTokens,
) {
    /** Follows the device. Rounded-but-neutral, no decorative blur or glow. */
    MATERIAL_YOU(
        displayName = "Material You",
        description = "Native Android look. Follows your wallpaper colours and the system shape scale.",
        tokens =
            ThemeShapeTokens(
                cardCorner = 16.dp,
                buttonCorner = 20.dp,
                dialogCorner = 28.dp,
                fieldCorner = 12.dp,
                borderStrokeWidth = 0.dp,
                cardPadding = 16.dp,
                dialogPadding = 24.dp,
                cardSpacing = 12.dp,
                glowWidth = 0f,
                dividerThickness = 1.dp,
                useBlurEffects = false,
                useCardBorder = false,
            ),
    ),

    /** The app's existing glassmorphism layer: translucent fills, hairline glow. */
    LIQUID_GLASS(
        displayName = "Liquid Glass",
        description = "Translucent panels with soft accent glow. The original WuWa look.",
        tokens =
            ThemeShapeTokens(
                cardCorner = 8.dp,
                buttonCorner = 8.dp,
                dialogCorner = 28.dp,
                fieldCorner = 8.dp,
                borderStrokeWidth = 0.5.dp,
                cardPadding = 16.dp,
                dialogPadding = 24.dp,
                cardSpacing = 12.dp,
                glowWidth = 0.5f,
                dividerThickness = 1.dp,
                useBlurEffects = true,
                useCardBorder = true,
            ),
    ),

    /** Oversized radii and heavier separation between independent panels. */
    BENTO_GRID(
        displayName = "Bento Grid",
        description = "Extra rounded modular tiles. Dense metrics split into separate blocks.",
        tokens =
            ThemeShapeTokens(
                cardCorner = 24.dp,
                buttonCorner = 16.dp,
                dialogCorner = 32.dp,
                fieldCorner = 16.dp,
                borderStrokeWidth = 1.dp,
                cardPadding = 18.dp,
                dialogPadding = 26.dp,
                cardSpacing = 14.dp,
                glowWidth = 0f,
                dividerThickness = 1.dp,
                useBlurEffects = false,
                useCardBorder = true,
            ),
    ),

    /** Hard edges, square everything, thick dividers. Lowest draw cost. */
    RETRO_HANDHELD(
        displayName = "Retro Handheld",
        description = "Hard edges and flat pixel panels. No blur, no shadow, no glow.",
        tokens =
            ThemeShapeTokens(
                cardCorner = 0.dp,
                buttonCorner = 0.dp,
                dialogCorner = 0.dp,
                fieldCorner = 0.dp,
                borderStrokeWidth = 2.dp,
                cardPadding = 12.dp,
                dialogPadding = 16.dp,
                cardSpacing = 8.dp,
                glowWidth = 0f,
                dividerThickness = 2.dp,
                useBlurEffects = false,
                useCardBorder = true,
            ),
    ),

    /** Animated multi-layer gradient backdrop; radii sit between Liquid and Bento. */
    AURORA_FLUID(
        displayName = "Aurora Fluid",
        description = "Slow drifting gradient layers behind soft translucent panels.",
        tokens =
            ThemeShapeTokens(
                cardCorner = 20.dp,
                buttonCorner = 20.dp,
                dialogCorner = 28.dp,
                fieldCorner = 16.dp,
                borderStrokeWidth = 0.5.dp,
                cardPadding = 18.dp,
                dialogPadding = 24.dp,
                cardSpacing = 14.dp,
                glowWidth = 0.25f,
                dividerThickness = 1.dp,
                useBlurEffects = true,
                useCardBorder = true,
            ),
    ),

    /**
     * Restrained flat skin. Deliberately NOT the OLED theme.
     *
     * This shares its display name with [ColorPalette.MIDNIGHT_OLED] on
     * purpose — they are the two halves of that look, and the palette picker
     * offers it too. But only the *palette* carries `forceDark`, because "does
     * the app render dark" is a colour question a shape enum cannot answer.
     *
     * Verified on device: selecting this from the UI Style list while on any
     * light palette renders a light UI with 12dp corners. So the description
     * must not promise darkness, which is why it points at the palette instead.
     */
    MIDNIGHT_OLED(
        displayName = "Midnight OLED",
        description = "Restrained flat panels with no glow. Pair with the OLED palette below for an all-black screen.",
        tokens =
            ThemeShapeTokens(
                cardCorner = 12.dp,
                buttonCorner = 12.dp,
                dialogCorner = 24.dp,
                fieldCorner = 8.dp,
                borderStrokeWidth = 1.dp,
                cardPadding = 16.dp,
                dialogPadding = 22.dp,
                cardSpacing = 12.dp,
                glowWidth = 0f,
                dividerThickness = 1.dp,
                useBlurEffects = false,
                useCardBorder = true,
            ),
    ),
    ;

    companion object {
        /** The global default: the most predictable, least opinionated style. */
        val DEFAULT: UiStyle = MATERIAL_YOU

        /**
         * Resolve a persisted name, or null when it is absent or unrecognised.
         *
         * In the companion, not on the enum body, because the call site is
         * `UiStyle.fromStorageKey(...)` — there is no instance to resolve it on
         * before the value has been read back from storage.
         *
         * Returning null rather than a silent default matters for the migration
         * path: it lets the repository tell "the user never chose" apart from
         * "the user chose something this build no longer has", and only write
         * in the former case.
         */
        fun fromStorageKey(key: String?): UiStyle? = entries.firstOrNull { it.name == key }
    }
}

/**
 * Pigment only. Which colours the app wears, independent of shape.
 *
 * [SYSTEM_DEFAULT] is resolved from the platform dynamic scheme on Android 12+
 * and falls back to the app's existing hand-tuned scheme below that, so it is
 * never "unimplemented" on an older device.
 *
 * Every palette defines **both** a light and a dark variant. A palette that only
 * defined one would have to either ignore the system theme or pick contrast
 * values at random for the other mode, and the second is how a palette ends up
 * with 3:1 body text.
 */
enum class ColorPalette(
    val displayName: String,
    val description: String,
    /** True when this palette must render dark whatever the system says. */
    val forceDark: Boolean = false,
    private val lightColors: PaletteColors? = null,
    private val darkColors: PaletteColors? = null,
) {
    /** Material You dynamic colour, or the app's original palette pre-Android 12. */
    SYSTEM_DEFAULT(
        displayName = "System Default",
        description = "Your wallpaper's colours on Android 12+, the app's original palette below that.",
        lightColors = LightFallbackColors,
        darkColors = DarkFallbackColors,
    ),

    /** Dark charcoal #12131A with gold #D4AF37. */
    WUTHERING_GOLD(
        displayName = "Wuthering Gold",
        description = "Dark charcoal with brushed gold accents.",
        lightColors = GoldLight,
        darkColors = GoldDark,
    ),

    /** Neon pink and cyan on near-black. */
    CYBER_PUNK(
        displayName = "Cyber Punk",
        description = "Neon pink and cyan on near-black. High-energy and saturated.",
        lightColors = CyberLight,
        darkColors = CyberDark,
    ),

    /** Terminal black with lime green. */
    MATRIX_GREEN(
        displayName = "Matrix Green",
        description = "Terminal black with lime green. A monochrome readout.",
        lightColors = MatrixLight,
        darkColors = MatrixDark,
    ),

    /** Icy arctic blue scale. */
    NORD_ICE(
        displayName = "Nord Ice",
        description = "Icy arctic blues over a muted slate base. Low contrast, calm.",
        lightColors = NordLight,
        darkColors = NordDark,
    ),

    /** Vibrant twilight pastel pink and purple. */
    SAKURA_VAPOR(
        displayName = "Sakura Vapor",
        description = "Twilight pastels in pink and purple. Soft and bright.",
        lightColors = SakuraLight,
        darkColors = SakuraDark,
    ),

    /**
     * True black in both modes.
     *
     * This is the one palette with a *third* role: the brief lists MIDNIGHT_OLED
     * under the structural axis, and it genuinely is a skin (the pure-black
     * surfaces are its own tokens). But "forces dark mode" is a colour fact, not
     * a shape one, so the enforcement lives here — a [UiStyle] cannot decide
     * whether the app renders dark. Registered on both axes deliberately, so
     * either picker can select it and the two always agree.
     */
    MIDNIGHT_OLED(
        displayName = "Midnight OLED",
        description = "True black surfaces for OLED panels. Forces dark in both modes.",
        forceDark = true,
        lightColors = OledLight,
        darkColors = OledDark,
    ),
    ;

    /**
     * The palette's colours for [isDark], or null when the platform's dynamic
     * scheme should be used instead — which is only [SYSTEM_DEFAULT].
     */
    fun colorsFor(isDark: Boolean): PaletteColors? = if (isDark) darkColors else lightColors

    companion object {
        /**
         * The global default.
         *
         * SYSTEM_DEFAULT rather than a named palette: on Android 12+ it is the
         * user's own wallpaper colours, which is what every existing install
         * already gets today. Defaulting to a named palette here would repaint
         * the app on upgrade for every user who never opened the theme picker.
         */
        val DEFAULT: ColorPalette = SYSTEM_DEFAULT

        /** See [UiStyle.fromStorageKey]; same companion-object reasoning. */
        fun fromStorageKey(key: String?): ColorPalette? = entries.firstOrNull { it.name == key }
    }
}
