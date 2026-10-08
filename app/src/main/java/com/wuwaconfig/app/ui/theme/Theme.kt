package com.wuwaconfig.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density

internal fun fontFamilyForName(name: String): FontFamily =
    when (name) {
        "Rajdhani" -> RajdhaniBold
        "Serif" -> SerifFamily
        "Monospace" -> MonospaceFamily
        else -> FontFamily.Default
    }

// Bake the selected family into every material text style. Relying only on the
// ambient LocalTextStyle override leaves the typography styles (body/subtitle)
// with fontFamily=null, so they keep the system font — wiring it here makes the
// choice apply to titles, subtitles, body text, and quick-action labels alike.
//
// Do NOT "fix" the user-facing Size slider by adding fontSize here: this copy
// deliberately only overrides fontFamily. Scaling is applied via the
// LocalDensity override in WuWaConfigTheme (below), which is what actually makes
// the Size slider affect styles that pin an explicit fontSize. Changing one
// mechanism without the other will make them disagree.
private fun typographyWithFont(family: FontFamily): Typography =
    Typography.copy(
        displayLarge = Typography.displayLarge.copy(fontFamily = family),
        displayMedium = Typography.displayMedium.copy(fontFamily = family),
        displaySmall = Typography.displaySmall.copy(fontFamily = family),
        headlineLarge = Typography.headlineLarge.copy(fontFamily = family),
        headlineMedium = Typography.headlineMedium.copy(fontFamily = family),
        headlineSmall = Typography.headlineSmall.copy(fontFamily = family),
        titleLarge = Typography.titleLarge.copy(fontFamily = family),
        titleMedium = Typography.titleMedium.copy(fontFamily = family),
        titleSmall = Typography.titleSmall.copy(fontFamily = family),
        bodyLarge = Typography.bodyLarge.copy(fontFamily = family),
        bodyMedium = Typography.bodyMedium.copy(fontFamily = family),
        bodySmall = Typography.bodySmall.copy(fontFamily = family),
        labelLarge = Typography.labelLarge.copy(fontFamily = family),
        labelMedium = Typography.labelMedium.copy(fontFamily = family),
        labelSmall = Typography.labelSmall.copy(fontFamily = family),
    )

internal const val MIN_TEXT_ALPHA = 0.5f

/** The maximum fraction the lerp may move a colour toward pure black/white. */
private const val MAX_CONTRAST_SHIFT = 0.4f

/**
 * True when [this] is light enough that dark foreground content is needed.
 *
 * Note the polarity: it returns `true` for a **light** colour. Every caller wants
 * "is this a light background", so naming it that way is the only way the call
 * sites stay readable — `if (isLight) NeumorphicCard() else …` is the existing
 * idiom in `GlassCard`, and this is the same test.
 */
internal fun Color.isLight(): Boolean = luminance() > 0.5f

/**
 * Whether the app renders dark.
 *
 * Split out of the scheme resolution so the accent layer and the settings screen
 * can ask the identical question and cannot disagree with it.
 */
internal fun resolveIsDark(
    colorPalette: ColorPalette,
    themeMode: String,
    systemDark: Boolean,
): Boolean {
    // forceDark is checked first: MIDNIGHT_OLED is only meaningful on an OLED,
    // and a light system theme would otherwise make the option a no-op on
    // exactly the devices that picked it.
    if (colorPalette.forceDark) return true
    return when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
}

/**
 * This palette's colours for [isDark], never null.
 *
 * A palette that only defined one mode would have to either ignore the system
 * theme or invent contrast values for the other; falling back to the app's
 * original scheme for the missing half is the third option, and the one that
 * cannot produce an unreadable body-text contrast ratio.
 */
internal fun ColorPalette.colorsForResolved(isDark: Boolean): PaletteColors = colorsFor(isDark) ?: if (isDark) DarkFallbackColors else LightFallbackColors

/**
 * Maps a [PaletteColors] onto the Material 3 slots.
 *
 * Pure and platform-free, so it is unit-testable without a `Context` — the same
 * reason the rest of this package avoids `android.graphics` outside [Color.kt].
 *
 * Slots the palette does not name (the `surfaceContainer*` ramp, `inverse*`)
 * are left to Material's defaults rather than being invented here: deriving a
 * six-step container ramp by hand is how a palette ends up with a container
 * lighter than its own surface.
 */
internal fun PaletteColors.toColorScheme(): ColorScheme =
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
    ).copy(
        surfaceTint = primary,
        inverseSurface = onSurface,
        inverseOnSurface = surface,
        // The container ramp must be overridden too. Taking it from a stock
        // lightColorScheme() would leave containers *lighter* than the palette's
        // own surfaceVariant — and on a dark palette, visibly brighter panels
        // from a Material component nobody themed by hand. Deriving the ramp
        // from the palette's own two surface values keeps it monotone in both
        // modes instead.
        surfaceBright = surfaceVariant,
        surfaceDim = background,
        surfaceContainer = surfaceVariant,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = outlineVariant,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background,
    )

/**
 * Applies the text-opacity slider to the six `on*` slots.
 *
 * Two things happen at once, and both are load-bearing:
 *
 * - **Alpha** is what call sites previously applied themselves via `.copy(alpha)`.
 * - **A lerp toward the mode's max-contrast colour.** Clamping alpha alone means
 *   a "50%" setting renders *paler* text rather than *dimmer* text, which on a
 *   dark background reduces perceived brightness instead of contrast. Deepening
 *   the RGB first keeps a dimmed label readable.
 *
 * ## The compensation is strongest where it is needed
 *
 * `shift = (1 - clamped) / (1 - MIN_TEXT_ALPHA) * MAX_CONTRAST_SHIFT`, so the
 * least opaque setting gets the most correction and full opacity gets none.
 *
 * This was originally `(clamped - 0.75) / 0.25`, which is **inverted**: it gave
 * zero compensation anywhere below alpha 0.75 — the whole bottom half of the
 * slider — and the full 0.4 at alpha 1.0, where the text is already at full
 * strength and needs nothing. Measured on a moto g60, that put the description
 * text of NORD_ICE (the weakest palette) at **4.48:1** against the card fill at
 * the bottom of the slider, just under the 4.5:1 AA floor, while the bold title
 * in the same row stayed at 17.5:1. The nominal `onSurfaceVariant`/`surfaceVariant`
 * pair passes at 6.06:1, so the unit tests could not see it — the rendered card
 * fill is a translucent accent gradient over `background`, not `surfaceVariant`,
 * and alpha compositing costs more than the nominal ratio suggests. Found by
 * measuring the screenshot, not by reading the maths.
 *
 * `inversePrimary` is deliberately not touched: it is a scheme-level pairing
 * token, not a foreground.
 */
internal fun ColorScheme.applyTextOpacity(alpha: Float): ColorScheme {
    val clamped = alpha.coerceIn(MIN_TEXT_ALPHA, 1f)
    val target = if (background.isLight()) Color(0xFF000000) else Color.White
    val span = (1f - MIN_TEXT_ALPHA).takeIf { it > 0f } ?: 1f
    val shift = ((1f - clamped) / span).coerceIn(0f, 1f) * MAX_CONTRAST_SHIFT

    fun Color.dim(): Color = lerp(this, target, shift).copy(alpha = clamped)

    return copy(
        onBackground = onBackground.dim(),
        onSurface = onSurface.dim(),
        onSurfaceVariant = onSurfaceVariant.dim(),
        onPrimaryContainer = onPrimaryContainer.dim(),
        onSecondaryContainer = onSecondaryContainer.dim(),
        onTertiaryContainer = onTertiaryContainer.dim(),
    )
}

/**
 * Samples the eight accent colours from a platform dynamic scheme.
 *
 * On Android 12+ with SYSTEM_DEFAULT there is no compiled-in palette for the
 * device's actual colours, so the accents have to come from the scheme itself.
 *
 * Material gives no guarantee that a wallpaper-derived scheme's slots differ by
 * hue — a monochrome wallpaper yields a near-grey primary, secondary and
 * tertiary — so the mapping reuses distinct slots and never assumes separation
 * that may not exist. `gold` falls back to the scheme's own on-surface rather
 * than a fixed hue, because a fixed gold would be the one accent that ignored
 * the user's wallpaper.
 */
private fun dynamicAccents(
    scheme: ColorScheme,
    isDark: Boolean,
): NeonPalette =
    neonPaletteOf(
        purple = scheme.primary,
        cyan = scheme.secondary,
        pink = scheme.tertiary,
        green = scheme.tertiary,
        red = scheme.error,
        amber = scheme.primary,
        blue = scheme.secondary,
        gold = scheme.onSurface,
    )

@Suppress("ktlint:standard:function-naming")
@Composable
fun WuWaConfigTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = false,
    textOpacity: Float = 1f,
    fontFamilyName: String = "Default",
    fontScale: Float = 1f,
    colorSaturation: Float = 1f,
    uiStyle: UiStyle = UiStyle.DEFAULT,
    colorPalette: ColorPalette = ColorPalette.DEFAULT,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = resolveIsDark(colorPalette, themeMode, systemDark)

    // Dynamic colour is a switch separate from ColorPalette.SYSTEM_DEFAULT on
    // purpose: a user can want wallpaper colours *with* a structural skin, and
    // can want the app's own palette even on Android 12+. Collapsing the two
    // into one flag makes "follow the wallpaper" and "pick a palette" mutually
    // exclusive, which is not what either one says.
    val dynamicScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            remember(context, isDark) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
        } else {
            null
        }

    val paletteColors = remember(colorPalette, isDark) { colorPalette.colorsForResolved(isDark) }

    val colorScheme =
        remember(dynamicScheme, paletteColors, textOpacity) {
            (dynamicScheme ?: paletteColors.toColorScheme()).applyTextOpacity(textOpacity)
        }

    // Repoint the ~500 `NeonXxx` readers. In a LaunchedEffect rather than during
    // composition, because writing state that those readers observe from inside
    // the composition that observes it would invalidate this frame.
    val accentBases =
        remember(dynamicScheme, paletteColors, isDark) {
            if (dynamicScheme != null) dynamicAccents(dynamicScheme, isDark) else paletteColors.accents
        }
    LaunchedEffect(accentBases, colorSaturation) {
        setAccentPalette(accentBases, colorSaturation)
    }

    // Window/system-bar plumbing.
    //
    // The previous version ran a SideEffect after EVERY successful
    // recomposition (i.e. every frame of a settings-slider drag) and called
    // window.setBackgroundDrawable(), invalidating the window background and
    // forcing a relayout each time. It also duplicated enableEdgeToEdge()
    // (MainActivity) and did an UNGUARDED `(view.context as Activity)` cast
    // that threw if the theme was composed outside an Activity.
    //
    // - setDecorFitsSystemWindows(false): dropped, enableEdgeToEdge() owns it.
    // - setBackgroundDrawable(...): dropped, every screen paints its own
    //   (transparent) Scaffold container over GradientBackground.
    // - statusBarColor / navigationBarColor: deprecated no-ops on API 35+, so
    //   they are only applied below that, and are keyed on the background so a
    //   slider drag does not re-run them every frame.
    val view = LocalView.current
    val activity = view.context as? Activity
    if (!view.isInEditMode && activity != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        // statusBarColor / navigationBarColor are deprecated no-ops on API 35+
        // (the platform forces transparent system bars), but the app still
        // needs them on API 26-34: enableEdgeToEdge() picks the status-bar icon
        // polarity from the *system* theme, not the app's, so on a light system
        // theme with a dark app theme the status bar would stay unreadable
        // unless we paint it with the app background colour ourselves. Do NOT
        // delete these — the SDK_INT guard above already makes them no-ops
        // where they are meaningless. Narrowly suppressed, not migrated.
        @Suppress("DEPRECATION")
        LaunchedEffect(colorScheme.background) {
            val window = activity.window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
        }
    }

    val family = fontFamilyForName(fontFamilyName)
    // MaterialTheme is the root: rebuilding 18 Typography styles on every
    // recomposition (e.g. font-scale slider drags) is wasteful. Memoize it.
    val typography = remember(fontFamilyName) { typographyWithFont(family) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
    ) {
        val density = LocalDensity.current
        val scale = fontScale.coerceIn(0.75f, 1.5f)
        val scaledDensity = remember(density, scale) { Density(density.density, scale) }
        CompositionLocalProvider(
            LocalThemeShapeTokens provides uiStyle.tokens,
            LocalUiStyle provides uiStyle,
            LocalColorPalette provides colorPalette,
            LocalDensity provides scaledDensity,
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = family),
        ) {
            content()
        }
    }
}
