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
        description = "Native Android look. Cards step up in tone as they nest, the way Material 3 does elevation.",
        tokens =
            ThemeShapeTokens(
                // The one style whose identity is Material's tonal-elevation
                // mechanism rather than a decoration, so it is the only one
                // that must draw no outline: the tone *is* the edge.
                surfaceMode = SurfaceMode.TONAL,
                edgeMode = EdgeMode.NONE,
                cardCorner = 20.dp,
                buttonCorner = 16.dp,
                dialogCorner = 28.dp,
                fieldCorner = 12.dp,
                borderStrokeWidth = 0.dp,
                cardPadding = 16.dp,
                dialogPadding = 24.dp,
                cardSpacing = 16.dp,
                glowWidth = 0f,
                useBlurEffects = false,
                useCardBorder = false,
            ),
    ),

    /** The app's existing glassmorphism layer: translucent fills, hairline glow. */
    LIQUID_GLASS(
        displayName = "Liquid Glass",
        description = "Translucent panels with a specular edge that catches the light.",
        tokens =
            ThemeShapeTokens(
                surfaceMode = SurfaceMode.GLASS,
                edgeMode = EdgeMode.SPECULAR,
                cardCorner = 20.dp,
                buttonCorner = 16.dp,
                dialogCorner = 28.dp,
                fieldCorner = 12.dp,
                borderStrokeWidth = 0.5.dp,
                cardPadding = 16.dp,
                dialogPadding = 24.dp,
                cardSpacing = 16.dp,
                glowWidth = 0.25f,
                useBlurEffects = true,
                useCardBorder = true,
            ),
    ),

    /**
     * Flat 2.0 — the modern flat (the 2017–2020 "big tech flat" era). Bold
     * rounding and generous whitespace, otherwise soberly flat and borderless.
     */
    FLAT_DESIGN_2(
        displayName = "Flat 2.0",
        description = "Clean flat planes, generous whitespace, no borders, no glow. Colour and type do the separating.",
        tokens =
            ThemeShapeTokens(
                surfaceMode = SurfaceMode.FLAT,
                edgeMode = EdgeMode.NONE,
                cardCorner = 14.dp,
                buttonCorner = 12.dp,
                dialogCorner = 20.dp,
                fieldCorner = 10.dp,
                borderStrokeWidth = 0.dp,
                cardPadding = 16.dp,
                dialogPadding = 22.dp,
                cardSpacing = 16.dp,
                glowWidth = 0f,
                useBlurEffects = false,
                useCardBorder = false,
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

    /** Warm putty + terracotta, tailored to NEUMORPHIC_SOFT. */
    SOFT_CLAY(
        displayName = "Soft Clay",
        description = "Warm putty greys with terracotta. Made for Soft UI relief.",
        lightColors = ClayLight,
        darkColors = ClayDark,
    ),

    /** Machined steel + copper hardware, tailored to SKEUOMORPHIC. */
    BRUSHED_COPPER(
        displayName = "Brushed Copper",
        description = "Machined steel base with copper hardware accents.",
        lightColors = CopperLight,
        darkColors = CopperDark,
    ),

    /** Poster-flat coral + ink blue, tailored to FLAT_DESIGN_2. */
    FLAT_POP(
        displayName = "Flat Pop",
        description = "Bold poster flat: coral and ink blue on crisp paper.",
        lightColors = FlatPopLight,
        darkColors = FlatPopDark,
    ),

    /** Acid yellow-green + ink, tailored to NEO_BRUTALISM. */
    ACID_BRUT(
        displayName = "Neo Brutal Acid",
        description = "Acid highlighter over raw ink. Made for Neo-Brutalism.",
        lightColors = AcidBrutLight,
        darkColors = AcidBrutDark,
    ),

    /** Near-monochrome ink slates, tailored to MINIMALIST. */
    PAPER_INK(
        displayName = "Paper Ink",
        description = "Paper white, ink slate, one quiet accent family.",
        lightColors = PaperInkLight,
        darkColors = PaperInkDark,
    ),

    /** Synthwave magenta + sunset orange, tailored to RETRO_FUTURISM. */
    RETROWAVE(
        displayName = "Retrowave",
        description = "Synthwave magenta, violet and sunset orange. Made for the neon grid.",
        lightColors = RetrowaveLight,
        darkColors = RetrowaveDark,
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
