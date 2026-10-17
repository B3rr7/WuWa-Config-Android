package com.wuwaconfig.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuwaconfig.app.ui.ThemeViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.ColorPalette
import com.wuwaconfig.app.ui.theme.DarkFallbackColors
import com.wuwaconfig.app.ui.theme.LocalSpacing
import com.wuwaconfig.app.ui.theme.LocalThemeShapeTokens
import com.wuwaconfig.app.ui.theme.PaletteColors
import com.wuwaconfig.app.ui.theme.UiStyle
import com.wuwaconfig.app.ui.theme.WuWaConfigTheme

/**
 * The theme engine's two-axis picker.
 *
 * Split into exactly two sections because the axes are independent and compose:
 * choosing a structural skin must not reset the palette, and vice versa. A
 * single list of "themes" would force one combined selection and would have to
 * enumerate 6 × 6 combinations to cover the space honestly.
 */
@Composable
fun ThemeSettingsScreen(
    viewModel: ThemeViewModel,
    onBack: () -> Unit,
) {
    val config by viewModel.themeConfig.collectAsStateWithLifecycle()
    val tokens = LocalThemeShapeTokens.current
    val spacing = LocalSpacing.current

    // GradientBackground is NOT optional. WuWaConfigTheme deliberately stopped
    // calling window.setBackgroundDrawable(), on the understanding that every
    // screen paints its own backdrop. A screen that omits it renders over the
    // raw window background (#FAFAFA), and since GlassCard's dark branch is a
    // *translucent* accent gradient, that white shows straight through the card
    // — which is exactly what happened here: a MATRIX_GREEN card rendered as a
    // pale mint panel with pale green text on it, unreadable. Every other screen
    // in this package has this wrapper for the same reason.
    GradientBackground {
        Column {
            GlassTopBar(
                title = { Text("Appearance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
            // Bottom padding must clear the navigation-bar inset: the last
            // card is the destructive "Reset to defaults" button, which with
            // 3-button nav (~48dp) was partially obscured under the bar —
            // and that card is reachable nowhere else.
            val density = LocalDensity.current
            val navbarBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp + navbarBottom),
                verticalArrangement = Arrangement.spacedBy(tokens.cardSpacing),
            ) {
                item {
                    GlassCard(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm)) {
                        UiStyleSection(selected = config.uiStyle, onSelect = viewModel::setUiStyle)
                    }
                }
                item {
                    GlassCard(modifier = Modifier.padding(horizontal = spacing.lg)) {
                        ColorPaletteSection(
                            selected = config.colorPalette,
                            dynamicColor = config.dynamicColor,
                            onToggleDynamicColor = viewModel::setDynamicColor,
                            onSelect = viewModel::setColorPalette,
                        )
                    }
                }
                item {
                    GlassCard(modifier = Modifier.padding(horizontal = spacing.lg)) {
                        ResetSection(onReset = viewModel::resetToDefaults)
                    }
                }
            }
        }
    }
}

/**
 * Section A — the structural axis.
 *
 * A vertical radio list rather than a second LazyRow: these are the options a
 * user reads before touching, each with a sentence of explanation, and a
 * horizontal strip would truncate every description to one unreadable line.
 *
 * Built from `UiStyle.entries` rather than a hand-written list, so adding a
 * constant is the only edit needed to make a style selectable. The same is true
 * of the descriptions — they live on the enum, next to the tokens they describe.
 */
@Composable
private fun UiStyleSection(
    selected: UiStyle,
    onSelect: (UiStyle) -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val spacing = LocalSpacing.current
    SectionHeader(title = "UI Style Engine", hint = "Structure: corners, strokes, depth")
    Spacer(Modifier.height(tokens.cardSpacing / 2))
    // Where the separator starts: past the dot and its gap, so the rules sit
    // under the text and the radio column stays visually quiet. Derived from the
    // same two values the row uses — a literal `44.dp` here would drift the
    // moment the row's inner padding or its dot width changed.
    val ruleInset = SELECTION_DOT_SIZE + spacing.md * 2
    UiStyle.entries.forEach { style ->
        UiStyleRow(
            style = style,
            isSelected = style == selected,
            onClick = { onSelect(style) },
        )
        if (style != UiStyle.entries.last()) {
            // A hairline between rows, not a card per row. Six boxes would make
            // the list read as six competing surfaces; one continuous column with
            // separators reads as one decision. The rule is *inside* the card and
            // taken from the scheme's own outline token, so it follows the style
            // theme rather than imposing Material's.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = ruleInset)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.height(spacing.xs))
        }
    }
}

/**
 * One section's title and its subtitle.
 *
 * The subtitle is the point. "UI Style Engine" names the axis but not what it
 * changes; without "Structure: corners, strokes, depth" a user has no way to
 * know that picking Bento Grid will not recolour the app. Both axes answer a
 * different question, so both say which one.
 */
@Composable
private fun SectionHeader(
    title: String,
    hint: String,
) {
    Column(Modifier.fillMaxWidth()) {
        GlassCardHeader(title, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/** Diameter of [SelectionDot]. Also the inset the section-A separators use. */
private val SELECTION_DOT_SIZE = 20.dp

/**
 * One structural style.
 *
 * The selected row animates three things, and each has a reason:
 *
 * - **fill alpha**, spring — so switching styles feels like the app answering
 *   rather than snapping. It was a hard `if (isSelected)` branch before, which
 *   is the single most common cause of a picker feeling cheap.
 * - **press scale**, spring, 0.98 — the row is pressable, and an interactive
 *   element that gives no feedback on touch reads as broken. The platform ripple
 *   stays on; the scale is additive feedback, not a replacement.
 * - **the ring**, whose alpha rides the same spring as the fill. The border is
 *   0dp wide when unselected, so the alpha only earns its keep during the
 *   transition.
 */
@Composable
private fun UiStyleRow(
    style: UiStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val spacing = LocalSpacing.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // One animation, not two. `fillAlpha` and `ringAlpha` were separate
    // `animateFloatAsState` states with identical targets and identical specs —
    // two animation objects driving one bit of "is this row selected". They are
    // interpolated at different weights below, but they must never be allowed to
    // disagree, so they share a single source value.
    val selectedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "styleSelection",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "stylePress",
    )
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(tokens.fieldCorner)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clip(shape)
                .background(accent.copy(alpha = 0.10f * selectedAlpha))
                .border(
                    // coerceAtLeast so an unselected row never draws a hairline
                    // even in styles whose border token is non-zero: RETRO_HANDHELD
                    // wants a 2dp border on real surfaces, not on every list row.
                    width = if (isSelected) tokens.borderStrokeWidth.coerceAtLeast(1.dp) else 0.dp,
                    color = accent.copy(alpha = 0.45f * selectedAlpha),
                    shape = shape,
                )
                .clickable(
                    interactionSource = interactionSource,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).semantics {
                    contentDescription = "${style.displayName}. ${style.description}"
                    stateDescription = if (isSelected) "Selected" else "Not selected"
                }.padding(horizontal = spacing.md, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SelectionDot(isSelected = isSelected, accent = accent)
        Spacer(Modifier.width(spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = style.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = style.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Section B — the pigment axis.
 *
 * Each circle is drawn from the palette's own [PaletteColors] rather than from
 * swatch hexes defined here. A swatch defined in this file would be a third copy
 * of the palette: the circle could show "Cyber Punk" as blue while the app
 * renders pink, and nothing would catch it.
 */
@Composable
private fun ColorPaletteSection(
    selected: ColorPalette,
    dynamicColor: Boolean,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelect: (ColorPalette) -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val spacing = LocalSpacing.current
    SectionHeader(title = "Color Palettes", hint = "Pigment: every app colour, both modes")
    Spacer(Modifier.height(tokens.cardSpacing / 2))

    // Every palette is previewed against dark. The neon/terminal/accent-heavy
    // schemes are judged in dark, and rendering all six against one fixed
    // backdrop keeps the strip comparable left to right instead of having each
    // circle flip polarity mid-row.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xs / 2)) {
        items(ColorPalette.entries, key = { it.name }) { palette ->
            PaletteCircle(
                palette = palette,
                isSelected = palette == selected,
                onClick = { onSelect(palette) },
            )
        }
    }

    Spacer(Modifier.height(tokens.cardSpacing / 2))
    Text(
        text = selected.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // The wallpaper-colours switch. `setDynamicColor` and the ThemeConfig field
    // existed since the beginning, but nothing ever called the setter — so
    // SYSTEM_DEFAULT (the default palette) permanently rendered its static
    // fallbacks while promising "Your wallpaper's colours on Android 12+" in
    // its description. This is the writer that closes that dead-pref trap.
    Spacer(Modifier.height(tokens.cardSpacing / 2))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = dynamicColor,
                    role = Role.Switch,
                    onValueChange = onToggleDynamicColor,
                ),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Follow wallpaper colours",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Material You dynamic scheme on Android 12+. Overrides the palette above when on.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(spacing.md))
        Switch(checked = dynamicColor, onCheckedChange = null)
    }
}

/**
 * One palette swatch: a multi-colour sweep clipped to a circle, with an
 * animated ring and scale when selected.
 *
 * The press scale mirrors [UiStyleRow]'s so the two sections feel like one
 * surface rather than two widgets that happen to share a screen. The ring fades
 * on `Modifier.alpha` rather than by animating the stroke colour, so the
 * transition rides the render node; it was a hard `if (isSelected)` before, which
 * made selection snap on — the exact cheapness [UiStyleRow] avoids.
 */
@Composable
private fun PaletteCircle(
    palette: ColorPalette,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "paletteScale",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "palettePress",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "paletteRing",
    )
    val combinedScale = scale * pressScale
    val ringColor = MaterialTheme.colorScheme.primary
    val swatches = palettePreviewSwatches(palette)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(58.dp)
                    .scale(combinedScale)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        role = Role.RadioButton,
                        onClick = onClick,
                    ).semantics {
                        contentDescription = "${palette.displayName}. ${palette.description}"
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // Sweep rather than a linear split: four or more colours in a
                // 56dp circle only read as a wheel, and a two-tone half-circle
                // would hide two of the palette's own slots.
                drawCircle(brush = Brush.sweepGradient(colors = swatches, center = center))
            }
            // Always composed, gated by alpha: drawing one circle at alpha 0 is
            // free next to the sweep gradient underneath it, and composing it
            // unconditionally is what lets the fade run in *both* directions.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .alpha(ringAlpha),
            ) {
                drawCircle(
                    color = ringColor,
                    style = Stroke(width = 3.dp.toPx()),
                    radius = size.minDimension / 2f - 2.dp.toPx(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = palette.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

/**
 * The colours a palette's circle is drawn with.
 *
 * SYSTEM_DEFAULT is special-cased to the *live* scheme: on API 31+ it has no
 * fixed colours at all — they come from the user's wallpaper — so any literal
 * would be a lie on exactly the platform where dynamic colour is the point.
 */
@Composable
private fun palettePreviewSwatches(palette: ColorPalette): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return if (palette == ColorPalette.SYSTEM_DEFAULT) {
        remember(scheme) { listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.error) }
    } else {
        val colors = palette.colorsFor(isDark = true) ?: DarkFallbackColors
        remember(palette) { swatchesOf(colors) }
    }
}

/**
 * The four most identity-bearing slots, in sweep order.
 *
 * `error` is included deliberately rather than a fourth "nice" colour: it is the
 * slot a palette most often gets wrong, and including it means the circle
 * disagrees with the app if the palette's error colour is off.
 */
private fun swatchesOf(colors: PaletteColors): List<Color> =
    listOf(
        colors.primary,
        colors.secondary,
        colors.tertiary,
        colors.error,
    )

/** Filled/hollow radio dot for Section A's rows. */
@Composable
private fun SelectionDot(
    isSelected: Boolean,
    accent: Color,
) {
    Box(
        modifier =
            Modifier
                .padding(top = 2.dp)
                .size(SELECTION_DOT_SIZE)
                .clip(CircleShape)
                .border(width = 2.dp, color = accent, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun ResetSection(onReset: () -> Unit) {
    val tokens = LocalThemeShapeTokens.current
    SectionHeader(title = "Reset", hint = "Back to Material You with system colours")
    Spacer(Modifier.height(tokens.cardSpacing / 2))
    Text(
        text = "Return every appearance setting to its default: Material You with system colours.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(tokens.cardSpacing / 2))
    GlassButton(
        onClick = onReset,
        accentColor = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Reset to defaults")
    }
}

@Preview(name = "ThemeSettings Light", showBackground = true)
@Composable
private fun ThemeSettingsScreenLightPreview() {
    WuWaConfigTheme(themeMode = "light") {
        GradientBackground {
            Text("ThemeSettings Preview")
        }
    }
}

@Preview(name = "ThemeSettings Dark", showBackground = true)
@Composable
private fun ThemeSettingsScreenDarkPreview() {
    WuWaConfigTheme(themeMode = "dark") {
        GradientBackground {
            Text("ThemeSettings Preview")
        }
    }
}
