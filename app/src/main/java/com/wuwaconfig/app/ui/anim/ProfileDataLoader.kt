package com.wuwaconfig.app.ui.anim

/*
 * Player-profile data loader.
 *
 * A dedicated visual for ProfileViewModel's real extraction progress. The
 * loader reads like a compact diagnostics console: a central extraction dial,
 * four data-source nodes (UID, level, device and config), and a progress rail
 * that resolves into a clean success state or a broken failure state.
 *
 * Colors follow the active application palette. Reduced motion freezes the
 * scanner at a complete frame while keeping the real percentage and terminal
 * state visible.
 */

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuwaconfig.app.ui.theme.NeonPurple
import com.wuwaconfig.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private data class ProfileLoaderPalette(
    val primary: Color,
    val secondary: Color,
    val success: Color,
    val failure: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val panel: Color,
    val track: Color,
    val grid: Color,
    val background: Brush,
)

@Composable
private fun rememberProfileLoaderPalette(): ProfileLoaderPalette {
    val theme = rememberAnimTheme()
    val violetHue = hueOf(NeonPurple)
    return remember(theme, violetHue) {
        ProfileLoaderPalette(
            primary = hsl(theme.idleHue, 88f, 62f),
            secondary = hsl(violetHue, 84f, 67f),
            success = hsl(theme.okHue, 88f, 61f),
            failure = hsl(theme.failHue, 88f, 61f),
            textPrimary = Color(0xFFF4F7FB),
            textSecondary = Color(0xFF91A3BA),
            panel = Color(0xCC091724),
            track = Color(0xFF29415B),
            grid = Color(0xFF29405C),
            background =
                Brush.linearGradient(
                    listOf(Color(0xFF050C16), Color(0xFF071923), Color(0xFF0B1020)),
                ),
        )
    }
}

/**
 * Player-profile extraction visual.
 *
 * [progress] is the real 0..1 extraction percentage when known. A null value
 * switches the dial to indeterminate scan mode. [outcome] drives the success
 * and failure presentation; [onSettled] fires once after the terminal state
 * has been shown long enough to read.
 */
@Composable
internal fun ProfileDataLoader(
    progress: Float?,
    outcome: LoadOutcome?,
    modifier: Modifier = Modifier,
    onSettled: (Boolean) -> Unit = {},
) {
    val reducedMotion = rememberReducedMotion()
    val palette = rememberProfileLoaderPalette()
    val succeeded = outcome is LoadOutcome.Success
    val failed = outcome is LoadOutcome.Failed
    val indeterminate = progress == null && !succeeded
    val targetProgress =
        when {
            succeeded -> 1f
            else -> (progress ?: 0f).coerceIn(0f, 1f)
        }
    val progressState by
        animateFloatAsState(
            targetValue = targetProgress,
            animationSpec = tween(if (reducedMotion) 0 else 360),
            label = "profileDataProgress",
        )
    val phaseState =
        if (reducedMotion) {
            remember { mutableStateOf(0.22f) }
        } else {
            rememberInfiniteTransition(label = "profileDataScanner").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 4200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "phase",
            )
        }
    val accentTarget =
        when {
            succeeded -> palette.success
            failed -> palette.failure
            else -> palette.primary
        }
    val accent by
        animateColorAsState(
            targetValue = accentTarget,
            animationSpec = tween(if (reducedMotion) 0 else 420),
            label = "profileDataAccent",
        )
    val callback by rememberUpdatedState(onSettled)

    LaunchedEffect(outcome, reducedMotion) {
        when (outcome) {
            is LoadOutcome.Success -> {
                delay(if (reducedMotion) 120L else 1100L)
                callback(true)
            }
            is LoadOutcome.Failed -> {
                delay(if (reducedMotion) 120L else 1400L)
                callback(false)
            }
            null -> Unit
        }
    }

    val failureReason = (outcome as? LoadOutcome.Failed)?.reason
    val description =
        when {
            succeeded -> "Player profile data loaded successfully."
            failed -> "Player profile data load failed: $failureReason"
            indeterminate -> "Extracting player profile data from the device."
            else -> "Extracting player profile data, " + (progressState * 100f).roundToInt() + " percent."
        }
    val phaseLabel =
        when {
            succeeded -> "COMPLETE"
            failed -> "INTERRUPTED"
            else -> "EXTRACTING"
        }
    val centerLabel =
        when {
            succeeded -> "PROFILE READY"
            failed -> "READ FAILED"
            indeterminate -> "LIVE SCAN"
            else -> "READING DATA"
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(palette.background)
                .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(progressState, 0f..1f)
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        ProfileLoaderGrid(palette.grid)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            ProfileLoaderHeader(
                phaseLabel = phaseLabel,
                accent = accent,
                palette = palette,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                ProfileScanner(
                    progress = progressState,
                    phase = phaseState.value,
                    accent = accent,
                    secondary = palette.secondary,
                    palette = palette,
                    indeterminate = indeterminate,
                    succeeded = succeeded,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text =
                            if (indeterminate) {
                                "LIVE"
                            } else {
                                (progressState * 100f).roundToInt().toString() + "%"
                            },
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = centerLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = accent.copy(alpha = 0.86f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            ProfileLoaderFooter(
                progress = progressState,
                accent = accent,
                secondary = palette.secondary,
                palette = palette,
                indeterminate = indeterminate,
                succeeded = succeeded,
            )
        }
    }
}

@Composable
private fun ProfileLoaderHeader(
    phaseLabel: String,
    accent: Color,
    palette: ProfileLoaderPalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.11f))
                    .border(1.dp, accent.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "PLAYER PROFILE",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = palette.textPrimary,
            )
            Text(
                text = "LOCAL DATA EXTRACTION",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = palette.textSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.10f))
                    .border(1.dp, accent.copy(alpha = 0.26f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
        ) {
            Text(
                text = phaseLabel,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = accent,
            )
        }
    }
}

@Composable
private fun ProfileScanner(
    progress: Float,
    phase: Float,
    accent: Color,
    secondary: Color,
    palette: ProfileLoaderPalette,
    indeterminate: Boolean,
    succeeded: Boolean,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) * 0.38f
        val outerRadius = radius * 1.18f
        val nodeRadius = radius * 1.14f

        drawCircle(
            color = accent.copy(alpha = 0.045f),
            radius = outerRadius,
            center = center,
        )
        drawCircle(
            color = palette.track.copy(alpha = 0.55f),
            radius = radius,
            center = center,
            style = Stroke(1.dp.toPx()),
        )
        drawCircle(
            color = palette.track.copy(alpha = 0.34f),
            radius = radius * 0.72f,
            center = center,
            style = Stroke(1.dp.toPx()),
        )

        val segmentCount = 18
        val segmentStep = 360f / segmentCount
        for (index in 0 until segmentCount) {
            val start = phase * 360f + index * segmentStep
            val alpha = if (index % 3 == 0) 0.58f else 0.16f
            drawArc(
                color = secondary.copy(alpha = alpha),
                startAngle = start,
                sweepAngle = segmentStep * 0.42f,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2f, outerRadius * 2f),
                style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        if (indeterminate) {
            drawArc(
                color = accent,
                startAngle = -90f + phase * 360f,
                sweepAngle = 74f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
        } else {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
            if (progress > 0.005f) {
                val angle = Math.toRadians((-90f + 360f * progress).toDouble())
                val head =
                    Offset(
                        x = center.x + cos(angle).toFloat() * radius,
                        y = center.y + sin(angle).toFloat() * radius,
                    )
                drawCircle(color = accent, radius = 4.dp.toPx(), center = head)
                drawCircle(
                    color = accent.copy(alpha = 0.22f),
                    radius = 8.dp.toPx(),
                    center = head,
                )
            }
        }

        val nodeAngles = floatArrayOf(-135f, -45f, 45f, 135f)
        val nodeThresholds = floatArrayOf(0.12f, 0.32f, 0.54f, 0.76f)
        for (index in nodeAngles.indices) {
            val angle = Math.toRadians(nodeAngles[index].toDouble())
            val directionX = cos(angle).toFloat()
            val directionY = sin(angle).toFloat()
            val node =
                Offset(
                    x = center.x + directionX * nodeRadius,
                    y = center.y + directionY * nodeRadius,
                )
            val active = succeeded || (!indeterminate && progress >= nodeThresholds[index])
            val nodeColor = if (active) accent else palette.track
            drawLine(
                color = nodeColor.copy(alpha = if (active) 0.34f else 0.16f),
                start = center,
                end = node,
                strokeWidth = 1.dp.toPx(),
            )
            drawCircle(
                color = nodeColor.copy(alpha = if (active) 0.18f else 0.07f),
                radius = 10.dp.toPx(),
                center = node,
            )
            drawCircle(
                color = nodeColor,
                radius = 4.5.dp.toPx(),
                center = node,
                style = Stroke(1.5.dp.toPx()),
            )
            if (active) {
                val packet = ((phase + index * 0.23f) % 1f)
                val packetPosition =
                    Offset(
                        x = node.x + (center.x - node.x) * packet,
                        y = node.y + (center.y - node.y) * packet,
                    )
                drawCircle(
                    color = secondary,
                    radius = 2.5.dp.toPx(),
                    center = packetPosition,
                )
            }
        }

        drawCircle(
            color = palette.panel,
            radius = radius * 0.54f,
            center = center,
        )
        drawCircle(
            color = accent.copy(alpha = 0.28f),
            radius = radius * 0.54f,
            center = center,
            style = Stroke(1.dp.toPx()),
        )
    }
}

@Composable
private fun ProfileLoaderFooter(
    progress: Float,
    accent: Color,
    secondary: Color,
    palette: ProfileLoaderPalette,
    indeterminate: Boolean,
    succeeded: Boolean,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "EXTRACTION PROGRESS",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
                color = palette.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (indeterminate) "LIVE" else (progress * 100f).roundToInt().toString() + "%",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.track.copy(alpha = 0.58f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = if (indeterminate) 0.32f else progress.coerceIn(0f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.72f), secondary))),
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProfileSourceChip("UID", 0.12f, progress, indeterminate, succeeded, accent, palette)
            ProfileSourceChip("LEVEL", 0.32f, progress, indeterminate, succeeded, accent, palette)
            ProfileSourceChip("DEVICE", 0.54f, progress, indeterminate, succeeded, accent, palette)
            ProfileSourceChip("CONFIG", 0.76f, progress, indeterminate, succeeded, accent, palette)
        }
    }
}

@Composable
private fun RowScope.ProfileSourceChip(
    label: String,
    threshold: Float,
    progress: Float,
    indeterminate: Boolean,
    succeeded: Boolean,
    accent: Color,
    palette: ProfileLoaderPalette,
) {
    val active = succeeded || (!indeterminate && progress >= threshold)
    Box(
        modifier =
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(7.dp))
                .background(if (active) accent.copy(alpha = 0.10f) else palette.track.copy(alpha = 0.22f))
                .border(
                    1.dp,
                    if (active) accent.copy(alpha = 0.28f) else palette.track.copy(alpha = 0.42f),
                    RoundedCornerShape(7.dp),
                )
                .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            color = if (active) accent else palette.textSecondary.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun ProfileLoaderGrid(grid: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 28.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = grid.copy(alpha = 0.10f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = grid.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }
    }
}
