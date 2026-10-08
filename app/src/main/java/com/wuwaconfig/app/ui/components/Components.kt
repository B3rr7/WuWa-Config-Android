package com.wuwaconfig.app.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.BlurMaskFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.asImage
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.ui.theme.*
import com.wuwaconfig.app.util.isLocalOnlyImageUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Themed card surface.
 *
 * Modifier contract: [GlassCard]'s own `fillMaxWidth()` is applied FIRST, then
 * the caller's [modifier] chain. Callers must therefore pass only decoration
 * (padding, background, clip, clickable, semantics) — passing another width
 * constraint such as `weight(1f)` or `width(...)` is redundant and, combined
 * with the built-in `fillMaxWidth`, produces conflicting width constraints.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    shape: Shape = RoundedCornerShape(LocalThemeShapeTokens.current.cardCorner),
    blurRadius: Int = 6,
    glowWidth: Float = LocalThemeShapeTokens.current.glowWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    if (isLight) {
        NeumorphicCard(
            modifier = modifier,
            accentColor = accentColor,
            shape = shape,
            content = content,
        )
        return
    }

    // Translucent fills are the glass effect itself, so they follow the style's
    // blur flag rather than the palette: LIQUID_GLASS is translucent on any
    // colour scheme, and MATERIAL_YOU is opaque on any palette.
    val translucency = if (tokens.useBlurEffects) 1f else 0.55f
    val cardStart = accentColor.copy(alpha = 0.06f * translucency)
    val cardEnd = Color.White.copy(alpha = 0.02f * translucency)
    val borderColor = accentColor.copy(alpha = 0.15f)

    Card(
        modifier = Modifier.fillMaxWidth().then(modifier),
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (glowWidth > 0f) {
                            shadowElevation = 6f * glowWidth
                            this.shape = shape
                            clip = true
                        }
                    }
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                colors = listOf(cardStart, cardEnd),
                            ),
                        shape = shape,
                    )
                    .then(
                        if (tokens.useCardBorder) {
                            Modifier.border(maxOf(glowWidth.dp, 0.5f.dp), borderColor, shape)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            if (tokens.useBlurEffects) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(
                                brush =
                                    Brush.linearGradient(
                                        colors = listOf(accentColor.copy(alpha = 0.04f), Color.Transparent),
                                        start = androidx.compose.ui.geometry.Offset.Zero,
                                        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                                    ),
                                shape = shape,
                            ),
                )
            }
            Column(modifier = Modifier.padding(tokens.cardPadding)) {
                content()
            }
        }
    }
}

val NeuBase = Color(0xFFE8ECF3)
private val NeuDarkShadow = Color(0xFFBAC4D6)
private val NeuLightShadow = Color(0xFFFFFFFF)
private val NeuCorner = 22.dp

// Single shared Paint. This is safe ONLY because Compose runs all drawing on one
// thread (the UI thread), so the `frame.color` / `frame.maskFilter` mutations
// below can never interleave with another neumorphic draw. Do not hoist this
// into a background/parallel draw path without making the paint local.
//
// Deliberately an android.graphics.Paint, not a Compose Paint: the draw block
// talks to the native canvas directly (drawRoundRect + BlurMaskFilter), and
// `Paint().asFrameworkPaint()` — the Compose-side way to reach that object — is
// deprecated in favour of using android.graphics.Paint directly.
private val neuPaint = android.graphics.Paint()

fun Modifier.neumorphic(
    cornerRadius: Dp = NeuCorner,
    elevation: Dp = 7.dp,
    base: Color = NeuBase,
    lightShadow: Color = NeuLightShadow,
    darkShadow: Color = NeuDarkShadow,
): Modifier =
    this.drawBehind {
        val cr = cornerRadius.toPx()
        val off = elevation.toPx()
        val blur = elevation.toPx() * 1.6f
        drawIntoCanvas { canvas ->
            val frame = neuPaint
            frame.isAntiAlias = true
            frame.maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            frame.color = darkShadow.toArgb()
            canvas.nativeCanvas.drawRoundRect(off, off, size.width + off, size.height + off, cr, cr, frame)
            frame.color = lightShadow.toArgb()
            canvas.nativeCanvas.drawRoundRect(-off, -off, size.width - off, size.height - off, cr, cr, frame)
        }
        drawRoundRect(
            color = base,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr, cr),
        )
    }

@Composable
private fun NeumorphicCard(
    modifier: Modifier = Modifier,
    accentColor: Color,
    shape: Shape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val corner = if (tokens.useBlurEffects) 22.dp else tokens.cardCorner
    val roundShape = RoundedCornerShape(corner)
    // Insets match the dark GlassCard branch exactly (both draw with no extra
    // padding) so card padding does not differ by 10dp between themes. Callers
    // that need breathing room add it themselves — see TerminalLogCard.
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(modifier)
                // Neumorphism is a soft-shadow treatment; a hard-edged style has
                // no business drawing it, so RETRO_HANDHELD gets a flat panel
                // instead of a square card with rounded shadows.
                .then(if (tokens.useBlurEffects) Modifier.neumorphic(cornerRadius = corner) else Modifier)
                .clip(roundShape),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        brush =
                            Brush.linearGradient(
                                colors = listOf(accentColor.copy(alpha = 0.07f), Color.Transparent),
                            ),
                    ),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

/**
 * Opacity of the scrim behind [GlassTopBar].
 *
 * High enough to hide scrolled content passing underneath the bar and the status-bar
 * inset, low enough that the gradient background still reads through as "glass".
 */
private const val SCRIM_ALPHA = 0.94f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val bar: @Composable () -> Unit = {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = accentColor,
                    navigationIconContentColor = accentColor,
                    actionIconContentColor = accentColor,
                ),
        )
    }

    if (isLight) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    // Scrim, drawn UNDER the accent gradient. Without it the bar is
                    // fully transparent and scrolled content renders straight through
                    // the title and the status bar.
                    .background(MaterialTheme.colorScheme.background.copy(alpha = SCRIM_ALPHA))
                    .neumorphic(cornerRadius = 0.dp, elevation = 5.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.10f), Color.Transparent),
                                ),
                        ),
            )
            bar()
        }
        return
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // See the light branch above. Scaffold's contentPadding only offsets
                // the list at rest; once scrolled, items pass under the bar, so the
                // bar itself has to occlude them. 0.94 still reads as glass against
                // the gradient background while hiding text behind it.
                .background(MaterialTheme.colorScheme.background.copy(alpha = SCRIM_ALPHA))
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    accentColor.copy(alpha = 0.10f),
                                    Color.White.copy(alpha = 0.02f),
                                    Color.Transparent,
                                ),
                        ),
                ),
    ) {
        bar()
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        accentColor.copy(alpha = 0.5f),
                                        Color.Transparent,
                                    ),
                            ),
                    ),
        )
    }
}

// The terminal card keeps its dark palette in BOTH themes on purpose: it is a
// literal log-console mock (monospace output on a near-black surface) and the
// same "recent.log" / "status.log" chrome appears in light and dark mode. The
// 10dp horizontal inset in light theme is the neumorphic-light compensation the
// dark branch does not need.
private val TerminalBg = Color(0xFF0C0E14)
private val TerminalBorder = Color(0xFF1E2530)

@Composable
fun TerminalLogCard(
    modifier: Modifier = Modifier,
    title: String = "recent.log",
    accentColor: Color = NeonGreen,
    maxLines: Int = 5,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val logs by LogRepository.entries.collectAsStateWithLifecycle()
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(modifier)
                .then(if (isLight) Modifier.padding(horizontal = 10.dp) else Modifier)
                .clip(shape)
                .background(TerminalBg)
                .border(1.dp, TerminalBorder, shape)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(NeonRed.copy(alpha = 0.85f)))
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(NeonAmber.copy(alpha = 0.85f)))
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(NeonGreen.copy(alpha = 0.85f)))
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.weight(1f))
            trailing?.invoke(this)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            if (logs.isEmpty()) {
                Text(
                    "\u276F no logs yet.",
                    fontSize = 10.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.4f),
                )
            } else {
                logs.takeLast(maxLines).forEach { log ->
                    val c =
                        when (log.level) {
                            LogLevel.SUCCESS -> NeonGreen
                            LogLevel.ERROR -> NeonRed
                            LogLevel.WARNING -> NeonAmber
                            LogLevel.INFO -> Color(0xFFB6C2D9)
                        }
                    Row {
                        Text(
                            "\u276F ",
                            fontSize = 10.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            color = accentColor.copy(alpha = 0.7f),
                        )
                        Text(
                            "[${log.timestamp}] ${log.message}",
                            fontSize = 10.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            color = c,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Themed toggle used throughout the Config Generator.
 * Dark theme -> frosted glass pill; Light theme -> inset/outset neumorphic pill.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    enabled: Boolean = true,
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val trackWidth = 54.dp
    val trackHeight = 30.dp
    val thumbSize = 24.dp
    val gap = (trackHeight - thumbSize) / 2

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - gap else gap,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "thumbOffset",
    )
    val trackShape = RoundedCornerShape(trackHeight / 2)

    if (isLight) {
        // Neumorphic: soft inset groove track + clearly raised thumb.
        val grooveTop = if (checked) accentColor.copy(alpha = 0.30f) else NeuDarkShadow.copy(alpha = 0.55f)
        val grooveBottom = if (checked) accentColor.copy(alpha = 0.14f) else NeuBase
        val thumbFill =
            if (!enabled) {
                Color(0xFFCED4DE)
            } else if (checked) {
                accentColor
            } else {
                Color.White
            }
        Box(
            modifier =
                modifier
                    .width(trackWidth)
                    .height(trackHeight)
                    .clip(trackShape)
                    .background(brush = Brush.verticalGradient(listOf(grooveTop, grooveBottom)))
                    .border(1.dp, if (checked) accentColor.copy(alpha = 0.45f) else NeuDarkShadow.copy(alpha = 0.7f), trackShape)
                    .toggleable(
                        value = checked,
                        onValueChange = onCheckedChange,
                        role = Role.Switch,
                        enabled = enabled,
                    ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier
                        .offset(x = thumbOffset)
                        .size(thumbSize)
                        .shadow(5.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(thumbFill)
                        .border(1.dp, if (checked) Color.White.copy(alpha = 0.7f) else NeuDarkShadow.copy(alpha = 0.5f), CircleShape),
            )
        }
        return
    }

    // Dark: frosted glass track with glow when on; bright raised thumb.
    Box(
        modifier =
            modifier
                .width(trackWidth)
                .height(trackHeight)
                .clip(trackShape)
                .background(
                    brush =
                        Brush.horizontalGradient(
                            colors =
                                if (checked) {
                                    listOf(accentColor.copy(alpha = 0.85f), accentColor.copy(alpha = 0.55f))
                                } else {
                                    listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
                                },
                        ),
                ).border(
                    1.dp,
                    if (checked) accentColor.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.22f),
                    trackShape,
                ).toggleable(
                    value = checked,
                    onValueChange = onCheckedChange,
                    role = Role.Switch,
                    enabled = enabled,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .shadow(6.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(if (enabled) Color.White else Color.White.copy(alpha = 0.35f))
                    .border(1.dp, if (checked) accentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.3f), CircleShape),
        )
    }
}

@Composable
fun GlassCardHeader(
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.8f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = NeonCyan,
    contentColor: Color = Color.Black,
    height: Dp = 52.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val buttonContainer = if (isLight) accentColor.copy(alpha = 0.20f) else accentColor.copy(alpha = 0.12f)
    val disabledContainer = if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.04f)
    val disabledContent = if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.25f)
    val resolvedContentColor = if (isLight) accentColor else contentColor
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "btnScale",
    )

    val buttonShape = RoundedCornerShape(tokens.buttonCorner)
    Button(
        onClick = onClick,
        modifier = modifier.height(height).graphicsLayer(scaleX = scale, scaleY = scale),
        enabled = enabled,
        shape = buttonShape,
        interactionSource = interactionSource,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = buttonContainer,
                contentColor = resolvedContentColor,
                disabledContainerColor = disabledContainer,
                disabledContentColor = disabledContent,
            ),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 2.dp,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                colors = listOf(accentColor.copy(alpha = 0.08f), Color.Transparent),
                            ),
                        shape = buttonShape,
                    )
                    .then(
                        if (tokens.useCardBorder) {
                            Modifier.border(
                                maxOf(tokens.borderStrokeWidth, 0.5f.dp),
                                accentColor.copy(alpha = 0.2f),
                                buttonShape,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = NeonRed,
    height: Dp = 52.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalThemeShapeTokens.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val disabledContent = if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.25f)
    val borderColor = if (isLight) accentColor.copy(alpha = 0.65f) else accentColor.copy(alpha = 0.3f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "outBtnScale",
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(height).graphicsLayer(scaleX = scale, scaleY = scale),
        enabled = enabled,
        shape = RoundedCornerShape(tokens.buttonCorner),
        interactionSource = interactionSource,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = accentColor,
                disabledContentColor = disabledContent,
            ),
        border = BorderStroke(maxOf(tokens.borderStrokeWidth, 0.5f.dp), borderColor),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun BackendStatusCard(
    status: BackendStatus,
    onToggle: () -> Unit,
) {
    val accentColor by animateColorAsState(
        targetValue =
            when {
                status.connected -> NeonGreen
                status.errorMessage.isNotBlank() -> NeonRed
                else -> NeonAmber
            },
        label = "accent",
    )

    GlassCard(accentColor = accentColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor)
                            .border(1.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Access: ${status.method.name}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            status.connected -> "Connected"
                            status.errorMessage.isNotBlank() -> status.errorMessage
                            else -> "Not connected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            SuggestionChip(
                onClick = onToggle,
                label = {
                    val text =
                        if (status.connected) {
                            "Switch"
                        } else {
                            when (status.method) {
                                AccessMethod.ADB -> "SHIZUKU"
                                AccessMethod.SHIZUKU -> "ROOT"
                                AccessMethod.ROOT -> "SAF"
                                AccessMethod.SAF -> "ADB"
                            }
                        }
                    Text(text, fontWeight = FontWeight.Bold)
                },
                colors =
                    SuggestionChipDefaults.suggestionChipColors(
                        containerColor = accentColor.copy(alpha = 0.1f),
                        labelColor = accentColor,
                    ),
                border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = accentColor.copy(alpha = 0.2f)),
            )
        }
    }
}

@Composable
fun MiniLogViewer(modifier: Modifier = Modifier) {
    // No second collection of LogRepository.entries here: this used to exist only
    // to hide the card when empty, while TerminalLogCard collects the identical
    // flow itself — two subscriptions and two invalidations per log line. The
    // "no logs yet" placeholder inside TerminalLogCard covers the empty case.
    TerminalLogCard(modifier = modifier, title = "status.log", accentColor = NeonAmber)
}

@Composable
fun BouncingOrb(
    color: Color,
    index: Int,
) {
    val transition = rememberInfiniteTransition(label = "orb$index")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 520, delayMillis = index * 160, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "offset$index",
    )
    Box(
        Modifier
            .size(14.dp)
            .offset(y = offset.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.radialGradient(listOf(color, color.copy(alpha = 0.35f))),
            ),
    )
}

@Composable
fun OrbLoadingCard(
    text: String,
    accentColor: Color,
    colors: List<Color> = listOf(NeonCyan, NeonGold, NeonPurple),
    progress: Int = 0,
) {
    GlassCard(accentColor = accentColor) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                colors.forEachIndexed { index, color ->
                    BouncingOrb(color, index)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
        }
    }
}

/**
 * App-scoped background preference holder. Exposed as a CompositionLocal rather
 * than reaching into the `WuWaConfigApp.instance` global, so [GradientBackground]
 * has no hidden dependency on `Application.onCreate` having already run (and no
 * UninitializedPropertyAccessException if a preview or an early composition gets
 * there first). MainActivity provides the real value.
 */
data class BackgroundSettings(
    val imageUri: String?,
    val videoUri: String?,
    val opacity: Float,
)

val LocalBackgroundSettings = staticCompositionLocalOf { BackgroundSettings(null, null, 1f) }

@Composable
fun GradientBackground(content: @Composable () -> Unit) {
    val themeBg = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val bg = LocalBackgroundSettings.current
    val imageUri = bg.imageUri
    val videoUri = bg.videoUri
    val bgAlpha = bg.opacity

    val hasVideo = videoUri != null
    val hasImage = !hasVideo && imageUri != null

    Box(modifier = Modifier.fillMaxSize().background(themeBg)) {
        if (hasVideo) {
            VideoBackground(
                videoUri = videoUri!!,
                alpha = bgAlpha,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors = listOf(themeBg.copy(alpha = 0.15f), surface.copy(alpha = 0.15f)),
                                ),
                        ),
            )
        } else if (hasImage) {
            val bgImageContext = LocalContext.current
            // LocalResources, not LocalContext: reading a resource through the
            // Context does not invalidate this composition when the Configuration
            // changes, so a locale or density change would leave a stale drawable.
            val bgImageResources = LocalResources.current
            val imageRequest =
                remember(imageUri) {
                    // Fail closed to the error drawable for anything that is not
                    // local-only, so a persisted or injected remote URL can never
                    // become an image request.
                    ImageRequest.Builder(bgImageContext)
                        .data(if (isLocalOnlyImageUri(imageUri)) imageUri else null)
                        .crossfade(true)
                        .error(
                            requireNotNull(
                                bgImageResources.getDrawable(
                                    android.R.drawable.stat_notify_error,
                                    bgImageContext.theme,
                                ),
                            ).asImage(),
                        )
                        .build()
                }
            val painter = rememberAsyncImagePainter(imageRequest)
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = bgAlpha),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors = listOf(themeBg.copy(alpha = 0.15f), surface.copy(alpha = 0.15f)),
                                ),
                        ),
            )
        } else {
            // AURORA_FLUID is the one style whose identity is the backdrop, so it
            // gets animated layers; every other style keeps the cheap static
            // gradient it always had. The branch is on the enum rather than on a
            // token because "is this backdrop animated" is not a dimension.
            if (LocalUiStyle.current == UiStyle.AURORA_FLUID) {
                AuroraBackdrop()
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(themeBg, surface),
                                    ),
                            ),
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

/**
 * Three large, slowly drifting radial colour blobs behind a translucent wash.
 *
 * Drawn rather than composed from `Box`es so the whole backdrop is one draw and
 * one layer: three overlapping translucent Boxes would each force their own
 * offscreen pass, which on the low-end GPUs this app targets is the difference
 * between a smooth background and a dropped frame on every animation.
 *
 * Driven by [rememberInfiniteTransition] rather than a coroutine + StateFlow, so
 * it stops producing frames the moment nothing is observing the composition —
 * a continuous `setState` loop here would keep the screen awake.
 */
@Composable
private fun AuroraBackdrop() {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val themeBg = MaterialTheme.colorScheme.background
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "auroraPhase",
    )

    Canvas(modifier = Modifier.fillMaxSize().background(themeBg)) {
        // Fixed blob geometry; only the phase moves. Scaled off the canvas size
        // so the composition reads the same on a phone and a tablet.
        val w = size.width
        val h = size.height
        val unit = minOf(w, h)
        val blobs =
            listOf(
                Triple(primary, Offset(w * (0.25f + 0.18f * sin(phase * TWO_PI)), h * (0.20f + 0.14f * cos(phase * TWO_PI))), unit * 0.85f),
                Triple(secondary, Offset(w * (0.75f + 0.15f * cos(phase * TWO_PI + 1f)), h * (0.35f + 0.16f * sin(phase * TWO_PI + 2f))), unit * 0.75f),
                Triple(tertiary, Offset(w * (0.50f + 0.20f * sin(phase * TWO_PI + 3f)), h * (0.78f + 0.12f * cos(phase * TWO_PI + 4f))), unit * 0.70f),
            )
        blobs.forEach { (color, center, radius) ->
            drawCircle(
                brush = Brush.radialGradient(colors = listOf(color.copy(alpha = 0.22f), Color.Transparent), center = center, radius = radius),
                radius = radius,
                center = center,
            )
        }
    }
}

private const val TWO_PI = 6.2831855f

@Composable
// `UnstableApi` is an `androidx.annotation.RequiresOptIn` marker, NOT a
// `kotlin.RequiresOptIn` one, so Kotlin's `@OptIn` has no effect on it (and
// the compiler warned accordingly). The opt-in that actually applies here is
// `@SuppressLint("UnsafeOptInUsageError")` below — there is no
// `androidx.annotation.OptIn` class in androidx.annotation 1.9.1, verified with
// javap — so the Kotlin-side marker is a plain string-keyed @Suppress.
@Suppress("UnstableApiUsage")
@SuppressLint("UnsafeOptInUsageError")
private fun VideoBackground(
    videoUri: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // The player is BUILT here, not in composition: creating an ExoPlayer is a
    // side effect (codec/IO init) and used to happen inside `remember(videoUri)`.
    // The holder is a state so the AndroidView below can render it once ready.
    val playerHolder = remember(videoUri) { mutableStateOf<ExoPlayer?>(null) }
    DisposableEffect(videoUri) {
        val player =
            try {
                ExoPlayer.Builder(context)
                    .build()
                    .apply {
                        setMediaItem(MediaItem.fromUri(videoUri))
                        repeatMode = Player.REPEAT_MODE_ALL
                        volume = 0f
                        prepare()
                        playWhenReady = true
                    }
            } catch (e: Exception) {
                LogRepository.add("ExoPlayer init failed: ${e.message}", LogLevel.WARNING)
                null
            }
        playerHolder.value = player
        if (player != null) {
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> player.pause()
                        Lifecycle.Event.ON_RESUME -> player.play()
                        else -> {}
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                player.release()
                playerHolder.value = null
            }
        } else {
            onDispose { playerHolder.value = null }
        }
    }

    val player = playerHolder.value

    if (player == null) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                setPlayer(player)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { view -> if (view.getPlayer() != player) view.setPlayer(player) },
        modifier = modifier,
    )
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - alpha)))
}

val GLITCH_NAMES =
    listOf(
        "WuWaConfig",
        "Rover's Tool",
        "Config Forge",
        "Pulse Engine",
        "Wave Weaver",
        "Crystal Core",
        "Echo Terminal",
        "Signal Boost",
        "Resonance Kit",
        "Tuning Fork",
    )

@Composable
fun GlitchText(
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    intervalMs: Long = 30000L,
    names: List<String> = GLITCH_NAMES,
    style: androidx.compose.ui.text.TextStyle? = null,
) {
    var currentIndex by remember { mutableStateOf(0) }
    var displayText by remember { mutableStateOf(names.getOrElse(0) { "" }) }
    var glitchActive by remember { mutableStateOf(false) }
    var shakeOffsetX by remember { mutableStateOf(0f) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(names, intervalMs) {
        currentIndex = 0
        displayText = names.getOrElse(0) { "" }
        // repeatOnLifecycle replaces the old 500ms poll of currentState — no
        // timer wakeups while the screen is stopped, and the effect is torn down
        // by the framework instead of a hand-rolled isActive loop.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(intervalMs)
                if (names.size < 2) continue
                val nextIndex = (currentIndex + 1) % names.size
                glitchActive = true
                shakeOffsetX = Random.nextFloat() * 4f - 2f
                val target = names[nextIndex]

                val len = maxOf(displayText.length, target.length)
                val scrambleStart = System.currentTimeMillis()
                while (System.currentTimeMillis() - scrambleStart < 600) {
                    val sb = StringBuilder()
                    for (i in 0 until len) {
                        when {
                            i >= target.length -> sb.append('█')
                            Random.nextFloat() < 0.3f -> {
                                val chars = "!@#$%^&*{}[]|\\/~`\"':;?><"
                                sb.append(chars[Random.nextInt(chars.length)])
                            }
                            else -> sb.append(target[i])
                        }
                    }
                    displayText = sb.toString()
                    delay(50 + Random.nextLong(80))
                }

                displayText = target
                currentIndex = nextIndex
                glitchActive = false
            }
        }
    }

    val offsetX by animateFloatAsState(
        targetValue = if (glitchActive) shakeOffsetX else 0f,
        animationSpec = if (glitchActive) tween(80) else spring(dampingRatio = 0.3f),
    )

    val finalMod =
        modifier.then(
            if (glitchActive) Modifier.offset(x = offsetX.dp) else Modifier,
        )
    if (style != null) {
        Text(text = displayText, fontWeight = fontWeight, style = style, modifier = finalMod)
    } else {
        Text(text = displayText, fontWeight = fontWeight, modifier = finalMod)
    }
}

// Refcounted decorView blur. Previously every dialog independently set AND
// cleared the blur, so two simultaneously-open dialogs shared one effect and
// disposing either one cleared the blur for both. The depth counter keeps the
// effect alive until the last dialog goes away.
private var dialogBlurDepth = 0

private fun acquireDialogBlur(activity: Activity?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || activity == null) return
    if (dialogBlurDepth++ == 0) {
        activity.window.decorView.setRenderEffect(
            RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP),
        )
    }
}

private fun releaseDialogBlur(activity: Activity?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || activity == null) return
    if (--dialogBlurDepth <= 0) {
        dialogBlurDepth = 0
        activity.window.decorView.setRenderEffect(null)
    }
}

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    accentColor: Color = NeonCyan,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    val tokens = LocalThemeShapeTokens.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val activity = LocalView.current.context as? Activity
    DisposableEffect(isLight, activity) {
        val blurs = !isLight
        if (blurs) acquireDialogBlur(activity)
        onDispose { if (blurs) releaseDialogBlur(activity) }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        val shape = RoundedCornerShape(tokens.dialogCorner)
        val titleColor = accentColor
        val bodyColor =
            if (isLight) Color(0xFF1C1B1F).copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)

        if (isLight) {
            Box(
                modifier =
                    Modifier
                        .widthIn(min = 260.dp, max = 380.dp)
                        .padding(14.dp)
                        .neumorphic(cornerRadius = 28.dp, elevation = 9.dp)
                        .clip(shape)
                        .border(1.dp, accentColor.copy(alpha = 0.18f), shape),
            ) {
                GlassDialogContent(accentColor, isLight, titleColor, bodyColor, icon, title, text, confirmButton, dismissButton)
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .widthIn(min = 260.dp, max = 380.dp)
                        .shadow(24.dp, shape, clip = false)
                        .clip(shape)
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.White.copy(alpha = 0.14f),
                                            Color.White.copy(alpha = 0.06f),
                                            accentColor.copy(alpha = 0.05f),
                                        ),
                                ),
                            shape = shape,
                        )
                        .border(
                            width = 1.dp,
                            brush =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.White.copy(alpha = 0.4f),
                                            Color.White.copy(alpha = 0.08f),
                                            accentColor.copy(alpha = 0.25f),
                                        ),
                                ),
                            shape = shape,
                        ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.10f), Color.Transparent),
                                    radius = 700f,
                                ),
                            ),
                )
                GlassDialogContent(accentColor, isLight, titleColor, bodyColor, icon, title, text, confirmButton, dismissButton)
            }
        }
    }
}

@Composable
private fun GlassDialogContent(
    accentColor: Color,
    isLight: Boolean,
    titleColor: Color,
    bodyColor: Color,
    icon: @Composable (() -> Unit)?,
    title: @Composable (() -> Unit)?,
    text: @Composable (() -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(LocalThemeShapeTokens.current.dialogPadding)) {
        icon?.let {
            Box(
                modifier =
                    Modifier
                        .padding(bottom = 12.dp)
                        .align(Alignment.CenterHorizontally),
            ) { it() }
        }
        title?.let {
            CompositionLocalProvider(LocalContentColor provides titleColor) {
                Box(
                    modifier =
                        Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth(),
                ) { it() }
            }
        }
        // The body is height-capped and scrollable so tall dialogs (e.g. Backup's
        // "text field + 5 checkboxes") can never push the button row off-screen on
        // a small display or in landscape. The buttons stay OUTSIDE the scroll
        // container so they remain reachable.
        text?.let {
            CompositionLocalProvider(LocalContentColor provides bodyColor) {
                Box(
                    modifier =
                        Modifier
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                ) { it() }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dismissButton != null) {
                dismissButton()
                Spacer(Modifier.width(8.dp))
            }
            confirmButton()
        }
    }
}

/**
 * Renders a RAM figure given in MB.
 *
 * Single formatter because the same `LogInfo.ramMb` was rendered two ways: the
 * Profile screen did integer division (`it / 1024`), so a device reporting
 * 5642 MB — 6 GB of RAM — was shown as "5 GB", while ConfigGen showed the raw
 * "5642 MB". `%.1f` keeps the real figure visible instead of rounding down to a
 * different whole number of gigabytes.
 */
fun formatRam(ramMb: Int): String = "%.1f GB".format(ramMb / 1024.0)
