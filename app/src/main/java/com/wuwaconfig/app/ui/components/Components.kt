package com.wuwaconfig.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.wuwaconfig.app.WuWaConfigApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.ui.theme.*

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    shape: Shape = RoundedCornerShape(8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardStart = if (isLight) MaterialTheme.colorScheme.surface else accentColor.copy(alpha = 0.06f)
    val cardEnd = if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.02f)
    val borderColor = if (isLight) accentColor.copy(alpha = 0.32f) else accentColor.copy(alpha = 0.15f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(if (isLight) 1.dp else 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            cardStart,
                            cardEnd
                        )
                    ),
                    shape = shape
                )
                .border(0.5.dp, borderColor, shape)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun GlassCardHeader(
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor.copy(alpha = 0.8f))
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
    content: @Composable RowScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val buttonContainer = if (isLight) accentColor.copy(alpha = 0.16f) else accentColor.copy(alpha = 0.12f)
    val disabledContainer = if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.04f)
    val disabledContent = if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.25f)
    val resolvedContentColor = if (isLight) accentColor else contentColor

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonContainer,
            contentColor = resolvedContentColor,
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.08f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(0.5.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = NeonRed,
    content: @Composable RowScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val disabledContent = if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.25f)
    val borderColor = if (isLight) accentColor.copy(alpha = 0.55f) else accentColor.copy(alpha = 0.3f)

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = accentColor,
            disabledContentColor = disabledContent
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

    @Composable
    fun BackendStatusCard(status: BackendStatus, onToggle: () -> Unit) {
        val accentColor by animateColorAsState(
            targetValue = when {
                status.connected -> NeonGreen
                status.errorMessage.isNotBlank() -> NeonRed
                else -> NeonAmber
            },
            label = "accent"
        )

        GlassCard(accentColor = accentColor) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor)
                            .border(1.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Access: ${status.method.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                status.connected -> "Connected"
                                status.errorMessage.isNotBlank() -> status.errorMessage
                                else -> "Not connected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                SuggestionChip(
                    onClick = onToggle,
                    label = {
                        val text = if (status.connected) "Switch"
                        else when (status.method) {
                            AccessMethod.ADB -> "SHIZUKU"
                            AccessMethod.SHIZUKU -> "ROOT"
                            AccessMethod.ROOT -> "SAF"
                            AccessMethod.SAF -> "ADB"
                        }
                        Text(text, fontWeight = FontWeight.Bold)
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = accentColor.copy(alpha = 0.1f),
                        labelColor = accentColor
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = accentColor.copy(alpha = 0.2f))
                )
            }
        }
    }

@Composable
fun LogViewer(logs: List<String>, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, accentColor = NeonCyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NeonCyan.copy(alpha = 0.8f))
            )
            Spacer(Modifier.width(8.dp))
            Text("Log", style = MaterialTheme.typography.titleSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 200.dp)
        ) {
            if (logs.isEmpty()) {
                Text("No logs yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    logs.reversed().forEach { log ->
                        val c = when {
                            log.contains("SUCCESS") -> NeonGreen
                            log.contains("FAILED") || log.contains("ERROR") -> NeonRed
                            log.contains("Applying") || log.contains("Connected") -> NeonCyan
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(log, style = MaterialTheme.typography.labelMedium, color = c, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GradientBackground(content: @Composable () -> Unit) {
    val themeBg = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    val app = WuWaConfigApp.instance
    val imageUri by app.backgroundImageUri.collectAsState()
    val videoUri by app.backgroundVideoUri.collectAsState()
    val bgAlpha by app.backgroundOpacity.collectAsState()

    val hasVideo = videoUri != null
    val hasImage = !hasVideo && imageUri != null

    Box(modifier = Modifier.fillMaxSize().background(themeBg)) {
        if (hasVideo) {
            VideoBackground(
                videoUri = videoUri!!,
                alpha = bgAlpha,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(themeBg.copy(alpha = 0.15f), surface.copy(alpha = 0.15f))
                        )
                    )
            )
        } else if (hasImage) {
            val painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(imageUri)
                    .crossfade(true)
                    .build()
            )
            Box(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = bgAlpha)) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(themeBg.copy(alpha = 0.15f), surface.copy(alpha = 0.15f))
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(themeBg, surface)
                        )
                    )
            )
        }
        Box(modifier = Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun VideoBackground(
    videoUri: String,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(videoUri) {
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
        val observer = LifecycleEventObserver { _, event ->
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
        modifier = modifier.graphicsLayer(alpha = alpha)
    )
}

val GLITCH_NAMES = listOf(
    "WuWaP42",
    "Rover's Tool",
    "Config Forge",
    "Pulse Engine",
    "Wave Weaver",
    "Crystal Core",
    "Echo Terminal",
    "Signal Boost",
    "Resonance Kit",
    "Tuning Fork"
)

@Composable
fun GlitchText(
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Bold,
    intervalMs: Long = 30000L,
    names: List<String> = GLITCH_NAMES,
    style: androidx.compose.ui.text.TextStyle? = null
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
        animationSpec = if (glitchActive) tween(80) else spring(dampingRatio = 0.3f)
    )

    val finalMod = modifier.then(
        if (glitchActive) Modifier.offset(x = offsetX.dp) else Modifier
    )
    if (style != null) {
        Text(text = displayText, fontWeight = fontWeight, style = style, modifier = finalMod)
    } else {
        Text(text = displayText, fontWeight = fontWeight, modifier = finalMod)
    }
}
