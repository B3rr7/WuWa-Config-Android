package com.wuwaconfig.app.ui.anim

/*
 * client.log -> SmartBrain scan overlay (native Jetpack Compose, no WebView).
 *
 * The overlay has two deliberate phases:
 *  - SCANNING  — a responsive waveform shows the REAL read progress while
 *    client.log is pulled and parsed. The scanner head follows the same
 *    progress value, so the motion and the percentage can never disagree.
 *  - DELIVERED — the waveform resolves, the extracted KEY=value signals are
 *    revealed, and the SmartBrain recommendation lands in a score gauge.
 *
 * The visual system is a midnight cyan/violet console rather than the old
 * flat-blue screen. It still follows the active app palette through
 * AnimTheme/rememberAnimTheme(), so SYSTEM_DEFAULT, MATRIX_GREEN, and every
 * other theme recolor the primary, success, and failure accents coherently.
 *
 * Reduced motion removes the looping waveform and typing cascade, but keeps
 * the progress, verdict, and extracted signals fully legible.
 */

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuwaconfig.app.config.BrainRecommendation
import com.wuwaconfig.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// ======================================================================
// Public API — BrainMapping.kt and ConfigGenScreen feed these.
// ======================================================================

enum class LogCategory(val label: String, val hue: Float) {
    OS("OS", 285f),
    DEVICE("DEVICE", 190f),
    GPU("GPU", 38f),
    API("GRAPHICS API", 145f),
    MEMORY("MEMORY", 330f),
    CVARS("UE4 CVARS", 218f),
}

/** One extracted line, e.g. BrainLine("GPU=Mali-G710", LogCategory.GPU). */
data class BrainLine(val text: String, val category: LogCategory)

sealed interface LogReadResult {
    /** Parser finished. Up to 10 entries are shown. */
    data class Success(val entries: List<BrainLine>) : LogReadResult

    /** Parser failed; [reason] is shown while the wave dies out. */
    data class Failed(
        val parsed: List<BrainLine> = emptyList(),
        val reason: String = "client.log unreadable",
    ) : LogReadResult
}

private data class LogPalette(
    val primary: Color,
    val secondary: Color,
    val success: Color,
    val failure: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val panel: Color,
    val panelStrong: Color,
    val grid: Color,
    val background: Brush,
)

@Composable
private fun rememberLogPalette(pal: AnimTheme): LogPalette =
    remember(pal) {
        val primary = hsl(pal.idleHue, 88f, 62f)
        val secondary = hsl((pal.idleHue + 52f).wrapHue(), 84f, 67f)
        val success = hsl(pal.okHue, 88f, 61f)
        val failure = hsl(pal.failHue, 88f, 61f)
        LogPalette(
            primary = primary,
            secondary = secondary,
            success = success,
            failure = failure,
            textPrimary = Color(0xFFF4F7FB),
            textSecondary = Color(0xFF91A3BA),
            panel = Color(0xCC091321),
            panelStrong = Color(0xF20A1527),
            grid = Color(0xFF263A55),
            background =
                Brush.linearGradient(
                    listOf(Color(0xFF050B16), Color(0xFF0B1022), Color(0xFF170B25)),
                ),
        )
    }

private fun Float.wrapHue(): Float = ((this % 360f) + 360f) % 360f

@Composable
fun LogBrainAnimation(
    result: LogReadResult?,
    modifier: Modifier = Modifier,
    secondsPerLine: Float = 0.34f,
    /**
     * The app's REAL log-read progress (0..100, `LogInsightsViewModel.readingProgress`)
     * while the pull/parse is still running. Rendered as the percent readout;
     * null leaves the wave running in indeterminate scan mode.
     */
    readProgress: Int? = null,
    /**
     * The actual SmartBrain recommendation for THIS log. Drives the ring gauge
     * and preset name in the delivered phase; null falls back to a neutral
     * full sweep and a "LOG PARSED" label.
     */
    recommendation: BrainRecommendation? = null,
    onSettled: (success: Boolean) -> Unit = {},
) {
    val animTheme = rememberAnimTheme()
    val palette = rememberLogPalette(animTheme)
    val reducedMotion = rememberReducedMotion()
    val arrived = result != null
    val failed = result is LogReadResult.Failed
    val settled by rememberUpdatedState(onSettled)

    // Typing cascade for the delivered rows: one list mutation per line. The
    // Canvas deliberately stays out of text — these rows are real composables,
    // which keeps the evidence readable by TalkBack.
    val revealed = remember { mutableStateListOf<BrainLine>() }
    LaunchedEffect(result, secondsPerLine, reducedMotion) {
        revealed.clear()
        when (val r = result) {
            is LogReadResult.Success ->
                if (reducedMotion) {
                    revealed.addAll(r.entries)
                    delay(1200)
                    settled(true)
                } else {
                    r.entries.forEachIndexed { index, line ->
                        revealed.add(line)
                        // The first row sets the tone; the rest read briskly.
                        delay(if (index == 0) 280L else (secondsPerLine * 1000).toLong())
                    }
                    delay(1700)
                    settled(true)
                }
            is LogReadResult.Failed -> {
                if (reducedMotion) {
                    revealed.addAll(r.parsed)
                    delay(1200)
                } else {
                    r.parsed.forEachIndexed { index, line ->
                        revealed.add(line)
                        delay(if (index == 0) 240L else (secondsPerLine * 900).toLong())
                    }
                    delay(1500)
                }
                settled(false)
            }
            null -> Unit
        }
    }

    // One phase drive; self-pauses when the overlay leaves the composition.
    // The State is passed DOWN and read inside WaveScan's draw lambda, so
    // animation frames redraw only the waveform instead of recomposing the
    // whole overlay. `null` is the reduced-motion contract.
    val phaseState =
        if (reducedMotion) {
            null
        } else {
            rememberInfiniteTransition(label = "clientLogScan").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 4600, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "phase",
            )
        }

    // Lock-in: bars go speculative -> resolved once the parser answers.
    val lockState =
        animateFloatAsState(
            targetValue = if (arrived) 1f else 0f,
            animationSpec = tween(if (reducedMotion) 0 else 420),
            label = "lock",
        )
    val ringFraction =
        animateFloatAsState(
            targetValue =
                when {
                    !arrived -> 0f
                    recommendation != null -> recommendation.score.coerceIn(0, 100) / 100f
                    else -> 1f
                },
            animationSpec = tween(if (reducedMotion) 0 else 900),
            label = "ring",
        )
    val progressState =
        animateFloatAsState(
            targetValue = (readProgress ?: 0).coerceIn(0, 100) / 100f,
            animationSpec = tween(if (reducedMotion) 0 else 260),
            label = "readProgress",
        )
    val animatedProgress by
        animateIntAsState(
            targetValue = (readProgress ?: 0).coerceIn(0, 100),
            animationSpec = tween(if (reducedMotion) 0 else 260),
            label = "readProgressLabel",
        )

    // Speech: the overlay reads as one live node with its state in a sentence.
    val description =
        when {
            failed -> "Log analysis failed: ${(result as LogReadResult.Failed).reason}"
            arrived ->
                "Log analysis complete." +
                    (recommendation?.let { " Recommended ${it.preset}, score ${it.score}." } ?: "")
            readProgress != null -> "Reading game log, $readProgress percent."
            else -> "Reading game log."
        }
    val phaseLabel =
        when {
            failed -> "INTERRUPTED"
            arrived -> "COMPLETE"
            else -> "SCANNING"
        }
    val phaseColor =
        when {
            failed -> palette.failure
            arrived -> palette.success
            else -> palette.primary
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        ScanGrid(grid = palette.grid)

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogHeader(
                phaseLabel = phaseLabel,
                phaseColor = phaseColor,
                palette = palette,
            )
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                WaveScan(
                    phaseState = phaseState,
                    lockState = lockState,
                    progressState = if (readProgress == null) null else progressState,
                    failed = failed,
                    primary = palette.primary,
                    secondary = palette.secondary,
                    failure = palette.failure,
                    modifier = Modifier.fillMaxSize(),
                )

                when {
                    !arrived ->
                        ScanCore(
                            progress = if (readProgress == null) null else animatedProgress,
                            palette = palette,
                        )
                    failed ->
                        FailureCore(
                            reason = (result as LogReadResult.Failed).reason,
                            palette = palette,
                        )
                    else ->
                        VerdictCard(
                            fraction = ringFraction,
                            recommendation = recommendation,
                            palette = palette,
                        )
                }
            }

            if (result is LogReadResult.Success || result is LogReadResult.Failed) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    SignalPanel(
                        lines = revealed,
                        failed = failed,
                        palette = palette,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogHeader(
    phaseLabel: String,
    phaseColor: Color,
    palette: LogPalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(9.dp)
                    .background(phaseColor, CircleShape),
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                text = "CLIENT.LOG",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = palette.textPrimary,
            )
            Text(
                text = "SMART BRAIN DIAGNOSTICS",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = palette.textSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = phaseLabel,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = phaseColor,
        )
    }
}

@Composable
private fun ScanCore(
    progress: Int?,
    palette: LogPalette,
) {
    Box(
        modifier =
            Modifier
                .widthIn(min = 176.dp, max = 260.dp)
                .background(palette.panelStrong, RoundedCornerShape(24.dp))
                .border(1.dp, palette.primary.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                .padding(horizontal = 28.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = progress?.let { "$it%" } ?: "SCAN",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = palette.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "DECRYPTING",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun FailureCore(
    reason: String,
    palette: LogPalette,
) {
    Box(
        modifier =
            Modifier
                .widthIn(min = 210.dp, max = 320.dp)
                .background(palette.panelStrong, RoundedCornerShape(24.dp))
                .border(1.dp, palette.failure.copy(alpha = 0.42f), RoundedCornerShape(24.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "READ FAILED",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = palette.failure,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VerdictCard(
    fraction: State<Float>,
    recommendation: BrainRecommendation?,
    palette: LogPalette,
) {
    Box(
        modifier =
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(palette.panelStrong, RoundedCornerShape(26.dp))
                .border(1.dp, palette.success.copy(alpha = 0.28f), RoundedCornerShape(26.dp))
                .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        RingScore(
            fraction = fraction,
            recommendation = recommendation,
            palette = palette,
        )
    }
}

/**
 * Responsive waveform band. The scanner head follows REAL read progress when
 * available; otherwise it travels continuously. Bar heights derive from a
 * seeded standing wave plus a traveling ripple, so there are no allocations
 * or state writes in the hot draw loop.
 */
@Composable
private fun WaveScan(
    phaseState: State<Float>?,
    lockState: State<Float>,
    progressState: State<Float>?,
    failed: Boolean,
    primary: Color,
    secondary: Color,
    failure: Color,
    modifier: Modifier = Modifier,
) {
    val seeds = remember { FloatArray(BAR_COUNT) { index -> (index * 7919 % 100) / 100f } }
    Canvas(modifier = modifier) {
        val phase = phaseState?.value ?: 0f
        val lock = lockState.value
        val progress = progressState?.value
        val barWidth = size.width / (BAR_COUNT * 1.65f)
        val gap = barWidth * 0.65f
        val total = BAR_COUNT * (barWidth + gap)
        val startX = (size.width - total) / 2f
        val midY = size.height * 0.50f
        val maxHalfHeight = size.height * 0.24f
        val progressCut = progress ?: 1f

        drawLine(
            color = primary,
            alpha = 0.10f,
            start = Offset(startX, midY),
            end = Offset(startX + total - gap, midY),
            strokeWidth = 1.dp.toPx(),
        )

        for (index in 0 until BAR_COUNT) {
            val seed = seeds[index]
            val normalized = if (BAR_COUNT == 1) 0f else index / (BAR_COUNT - 1f)
            val ambient = 0.28f + 0.22f * (1f + sin(phase * TAU + index * 0.55f + seed * 6f)) / 2f
            val ripple = 0.13f * (1f + sin(phase * TAU * 1.7f + index * 0.21f))
            val resolved = 0.43f + 0.09f * sin(index * 0.48f)
            val shape = lerp(ambient + ripple, resolved, lock * 0.82f)
            val dieOut = if (failed) 1f - 0.78f * lock else 1f
            val height = (shape * dieOut * maxHalfHeight).coerceAtLeast(1.5f)
            val x = startX + index * (barWidth + gap)
            val pastProgress = normalized > progressCut
            val alpha =
                when {
                    failed -> 0.82f
                    pastProgress -> 0.10f
                    else -> 0.46f + 0.38f * lock
                }
            val color = if (index % 5 == 0) secondary else primary
            drawRoundRect(
                color = color,
                alpha = alpha,
                topLeft = Offset(x, midY - height / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }

        val scanFraction = progress ?: phase % 1f
        val scanX = startX + total * scanFraction
        val top = midY - maxHalfHeight
        val bottom = midY + maxHalfHeight
        drawLine(
            color = if (failed) failure else primary,
            alpha = if (failed) 0.22f else 0.46f,
            start = Offset(scanX, top),
            end = Offset(scanX, bottom),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = if (failed) failure else primary,
            alpha = if (failed) 0.35f else 0.90f,
            radius = 3.5.dp.toPx(),
            center = Offset(scanX, midY),
        )
    }
}

private const val BAR_COUNT = 56
private const val TAU = 2f * PI.toFloat()

@Composable
private fun ScanGrid(grid: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 34.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = grid,
                alpha = 0.12f,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = grid,
                alpha = 0.09f,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }
    }
}

/** Ring gauge for the delivered phase: score sweep + the preset name beside it. */
@Composable
private fun RingScore(
    fraction: State<Float>,
    recommendation: BrainRecommendation?,
    palette: LogPalette,
) {
    val track = palette.primary.copy(alpha = 0.12f)
    val ringColor = if (recommendation != null) palette.success else palette.primary
    val labelColor = if (recommendation != null) palette.success else palette.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
            Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                val stroke = size.minDimension * 0.10f
                val inset = stroke / 2f
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = track,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                val value = fraction.value
                if (value > 0.005f) {
                    val sweep = 360f * value
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arc,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    if (sweep > 10f) {
                        drawArc(
                            color = palette.secondary,
                            startAngle = -90f + sweep - 8f,
                            sweepAngle = 8f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arc,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            if (recommendation != null) {
                Text(
                    text = "${recommendation.score}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SMART BRAIN VERDICT",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = recommendation?.preset?.uppercase() ?: "LOG PARSED",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SignalPanel(
    lines: List<BrainLine>,
    failed: Boolean,
    palette: LogPalette,
) {
    if (lines.isEmpty()) return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 232.dp)
                .background(palette.panel, RoundedCornerShape(20.dp))
                .border(1.dp, palette.primary.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (failed) "PARTIAL SIGNALS" else "EXTRACTED SIGNALS",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = palette.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${lines.size}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (failed) palette.failure else palette.primary,
            )
        }
        Spacer(Modifier.height(7.dp))
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            for (line in lines) {
                AnimatedVisibility(
                    visible = true,
                    enter =
                        fadeIn(animationSpec = tween(220)) +
                            slideInVertically(animationSpec = tween(260)) { height -> height / 3 },
                ) {
                    MonoRow(line = line, palette = palette)
                }
            }
        }
    }
}

/** One extracted `KEY=value` row, mono, with a category-hued marker. */
@Composable
private fun MonoRow(
    line: BrainLine,
    palette: LogPalette,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(width = 3.dp, height = 17.dp)) {
            drawRoundRect(
                color = hsl(line.category.hue, 86f, 63f),
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(1.5f, 1.5f),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = line.category.label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = hsl(line.category.hue, 82f, 68f),
            maxLines = 1,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
