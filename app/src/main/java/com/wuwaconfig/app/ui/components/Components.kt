package com.wuwaconfig.app.ui.components

import android.app.Activity
import android.graphics.BlurMaskFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.model.LogEntry
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    shape: Shape = RoundedCornerShape(8.dp),
    blurRadius: Int = 6,
    glowWidth: Float = 0.5f,
    content: @Composable ColumnScope.() -> Unit,
) {
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

    val cardStart = accentColor.copy(alpha = 0.06f)
    val cardEnd = Color.White.copy(alpha = 0.02f)
    val borderColor = accentColor.copy(alpha = 0.15f)

    Card(
        modifier = modifier.fillMaxWidth(),
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
                    .border(glowWidth.dp, borderColor, shape),
        ) {
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
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

private val GlassDialogSolid = Color(0xFF1B1B30)
val NeuBase = Color(0xFFE8ECF3)
private val NeuDarkShadow = Color(0xFFBAC4D6)
private val NeuLightShadow = Color(0xFFFFFFFF)
private val NeuCorner = 22.dp

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
            val paint = androidx.compose.ui.graphics.Paint()
            val frame = paint.asFrameworkPaint()
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
    val corner = 22.dp
    val roundShape = RoundedCornerShape(corner)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(10.dp)
                .neumorphic(cornerRadius = corner)
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
    val logs = LogRepository.entries
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
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
                    .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier),
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
                ).then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier),
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
    content: @Composable RowScope.() -> Unit,
) {
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

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp).graphicsLayer(scaleX = scale, scaleY = scale),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
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
                        shape = RoundedCornerShape(8.dp),
                    )
                    .border(0.5.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
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
    content: @Composable RowScope.() -> Unit,
) {
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
        modifier = modifier.height(52.dp).graphicsLayer(scaleX = scale, scaleY = scale),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        interactionSource = interactionSource,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor = accentColor,
                disabledContentColor = disabledContent,
            ),
        border = BorderStroke(1.dp, borderColor),
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
fun LogViewer(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    onSave: (() -> Unit)? = null,
) {
    GlassCard(modifier = modifier, accentColor = NeonCyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonCyan.copy(alpha = 0.8f)),
            )
            Spacer(Modifier.width(8.dp))
            Text("Log", style = MaterialTheme.typography.titleSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
            if (onSave != null) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSave, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Save, contentDescription = "Save log", tint = NeonCyan, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 200.dp),
        ) {
            if (logs.isEmpty()) {
                Text(
                    "No logs yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    logs.reversed().forEach { log ->
                        val c =
                            when (log.level) {
                                LogLevel.SUCCESS -> NeonGreen
                                LogLevel.ERROR -> NeonRed
                                LogLevel.WARNING -> NeonAmber
                                LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        Text(
                            "[${log.timestamp}] ${log.message}",
                            style = MaterialTheme.typography.labelMedium,
                            color = c,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniLogViewer(modifier: Modifier = Modifier) {
    if (LogRepository.entries.isEmpty()) return
    TerminalLogCard(modifier = modifier, title = "status.log", accentColor = NeonAmber)
}

@Composable
fun GradientBackground(content: @Composable () -> Unit) {
    val themeBg = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val app = WuWaConfigApp.instance
    val imageUri by app.backgroundImageUri.collectAsStateWithLifecycle()
    val videoUri by app.backgroundVideoUri.collectAsStateWithLifecycle()
    val bgAlpha by app.backgroundOpacity.collectAsStateWithLifecycle()

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
            val painter =
                rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data(imageUri)
                        .crossfade(true)
                        .build(),
                )
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
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun VideoBackground(
    videoUri: String,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player =
        remember(videoUri) {
            ExoPlayer.Builder(context)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(videoUri))
                    repeatMode = Player.REPEAT_MODE_ALL
                    volume = 0f
                    prepare()
                    playWhenReady = true
                }
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> player.pause()
                    Lifecycle.Event.ON_RESUME -> player.play()
                    Lifecycle.Event.ON_DESTROY -> player.release()
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                setPlayer(player)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
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
    var displayText by remember { mutableStateOf(names[0]) }
    var glitchActive by remember { mutableStateOf(false) }
    var shakeOffsetX by remember { mutableStateOf(0f) }

    val glitchLifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(names) {
        currentIndex = 0
        displayText = names[0]
        while (isActive) {
            while (!glitchLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                delay(500)
                if (!isActive) return@LaunchedEffect
            }
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

@Composable
fun AnimatedListItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter =
            slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(delayMillis = index * 40, durationMillis = 300),
            ) + fadeIn(animationSpec = tween(delayMillis = index * 40, durationMillis = 300)),
        modifier = modifier,
    ) {
        content()
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
    val view = LocalView.current
    SideEffect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (view.context as? Activity)?.window?.decorView?.setRenderEffect(
                RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP),
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (view.context as? Activity)?.window?.decorView?.setRenderEffect(null)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
        val shape = RoundedCornerShape(28.dp)
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
    Column(modifier = Modifier.padding(24.dp)) {
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
        text?.let {
            CompositionLocalProvider(LocalContentColor provides bodyColor) {
                Box(
                    modifier =
                        Modifier
                            .padding(bottom = 20.dp)
                            .fillMaxWidth(),
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
