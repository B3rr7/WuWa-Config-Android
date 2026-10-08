package com.wuwaconfig.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
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
import com.wuwaconfig.app.ui.theme.LocalThemeShapeTokens
import com.wuwaconfig.app.ui.theme.PaletteColors
import com.wuwaconfig.app.ui.theme.UiStyle

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
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(tokens.cardSpacing),
            ) {
                item {
                    GlassCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        UiStyleSection(selected = config.uiStyle, onSelect = viewModel::setUiStyle)
                    }
                }
                item {
                    GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                        ColorPaletteSection(
                            selected = config.colorPalette,
                            onSelect = viewModel::setColorPalette,
                        )
                    }
                }
                item {
                    GlassCard(modifier = Modifier.padding(horizontal = 16.dp)) {
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
    GlassCardHeader("UI Style Engine", MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(tokens.cardSpacing / 2))
    UiStyle.entries.forEach { style ->
        UiStyleRow(
            style = style,
            isSelected = style == selected,
            onClick = { onSelect(style) },
        )
    }
}

@Composable
private fun UiStyleRow(
    style: UiStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val ringAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "styleRing",
    )
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(tokens.fieldCorner)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(shape)
                .background(if (isSelected) accent.copy(alpha = 0.10f) else Color.Transparent)
                .border(
                    // coerceAtLeast so an unselected row never draws a hairline
                    // even in styles whose border token is non-zero: RETRO_HANDHELD
                    // wants a 2dp border on real surfaces, not on every list row.
                    width = if (isSelected) tokens.borderStrokeWidth.coerceAtLeast(1.dp) else 0.dp,
                    color = accent.copy(alpha = ringAlpha * 0.45f),
                    shape = shape,
                ).clickable(
                    role = Role.RadioButton,
                    onClick = onClick,
                ).semantics {
                    contentDescription = "${style.displayName}. ${style.description}"
                    stateDescription = if (isSelected) "Selected" else "Not selected"
                }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SelectionDot(isSelected = isSelected, accent = accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = style.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
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
    onSelect: (ColorPalette) -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    GlassCardHeader("Color Palettes", MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(tokens.cardSpacing / 2))

    // Every palette is previewed against dark. The neon/terminal/accent-heavy
    // schemes are judged in dark, and rendering all six against one fixed
    // backdrop keeps the strip comparable left to right instead of having each
    // circle flip polarity mid-row.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
}

/**
 * One palette swatch: a multi-colour sweep clipped to a circle, with an
 * animated ring and scale when selected.
 */
@Composable
private fun PaletteCircle(
    palette: ColorPalette,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "paletteScale",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "paletteRing",
    )
    val ringColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        label = "paletteRingColor",
    )
    val swatches = palettePreviewSwatches(palette)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(58.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .clickable(
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
            if (isSelected) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = ringColor.copy(alpha = ringAlpha),
                        style = Stroke(width = 3.dp.toPx()),
                        radius = size.minDimension / 2f - 2.dp.toPx(),
                    )
                }
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
                .size(20.dp)
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
    GlassCardHeader("Reset", MaterialTheme.colorScheme.error)
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
