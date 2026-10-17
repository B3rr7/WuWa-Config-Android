package com.wuwaconfig.app.ui.anim

/*
 * client.log -> SmartBrain scan overlay (native Jetpack Compose, no WebView).
 *
 * Replaces the previous frame-driven BrainSim (1.3k lines, every glyph painted
 * through android.graphics, so TalkBack saw nothing). The job this animation
 * actually does: hold full screen for the seconds a log pull/parse takes, show
 * the REAL read percent, land on the verdict, dismiss itself.
 *
 * Anatomy:
 *  - SCANNING  — a contained waveform of bars (the app's waveform motif) under
 *    a slow scanline; the percent readout is the device's own readingProgress.
 *  - DELIVERED — bars lock, the extracted KEY=value lines appear as REAL Text
 *    composables (screen-reader reachable), a ring gauge sweeps to the
 *    SmartBrain score, then it settles and exits.
 *  - FAILED    — bars collapse toward the fail hue with the reason shown.
 *
 * Palette-aware through AnimTheme/rememberAnimTheme(); the six category hues
 * stay fixed because they encode data classes, like element colors.
 *
 * Reduced motion: hosts gate by not composing this at all (ConfigGenScreen
 * keeps the plain progress UI). Inside the overlay it also softens itself —
 * static bars, no typing cascade, snapped ring — so it is safe even if a host
 * still shows it.
 *
 * Usage:
 *   if (shown) Box(Modifier.fillMaxSize()) { LogBrainAnimation(result, ...) }
 */

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuwaconfig.app.config.BrainRecommendation
import com.wuwaconfig.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.sin

// ======================================================================
// Public API — unchanged; BrainMapping.kt and ConfigGenScreen feed these.
// ======================================================================

enum class LogCategory(val label: String, val hue: Float) {
    OS("OS", 270f),
    DEVICE("DEVICE", 190f),
    GPU("GPU", 40f),
    API("GRAPHICS API", 150f),
    MEMORY("MEMORY", 320f),
    CVARS("UE4 CVARS", 215f),
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

@Composable
fun LogBrainAnimation(
    result: LogReadResult?,
    modifier: Modifier = Modifier,
    secondsPerLine: Float = 0.34f,
    /**
     * The app's REAL log-read progress (0..100, `LogInsightsViewModel.readingProgress`)
     * while the pull/parse is still running. Rendered as the percent readout;
     * null leaves the wave running blind.
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
    val pal = rememberAnimTheme()
    val reducedMotion = rememberReducedMotion()
    val arrived = result != null
    val failed = result is LogReadResult.Failed
    val settled by rememberUpdatedState(onSettled)

    // Typing cascade for the delivered rows: one list mutation per line. The
    // Canvas deliberately stays out of text — these rows are real composables,
    // which is what made the old canvas-glyph version unreadable by TalkBack.
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
                    r.entries.forEachIndexed { i, line ->
                        revealed.add(line)
                        // The first row sets the tone; the rest read briskly.
                        delay(if (i == 0) 350L else (secondsPerLine * 1000).toLong())
                    }
                    delay(1900)
                    settled(true)
                }
            is LogReadResult.Failed -> {
                delay(if (reducedMotion) 1200 else 1700)
                settled(false)
            }
            null -> Unit
        }
    }

    // One phase drive; self-pauses when the overlay leaves the composition,
    // which is cheaper than the old while-loop of withFrameNanos writes.
    val phase =
        rememberInfiniteTransition(label = "resonanceScan").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 5200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "phase",
        )
    // Lock-in: bars go speculative -> resolved once the parser answers.
    val lock by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 350),
        label = "lock",
    )
    val ringFraction by animateFloatAsState(
        targetValue =
            when {
                !arrived -> 0f
                recommendation != null -> recommendation.score.coerceIn(0, 100) / 100f
                else -> 1f
            },
        animationSpec = tween(if (reducedMotion) 0 else 900),
        label = "ring",
    )

    // Speech: the overlay reads as one node with its state in a sentence.
    val desc =
        when {
            failed -> "Log analysis failed: ${(result as LogReadResult.Failed).reason}"
            arrived ->
                "Log analysis complete." +
                    (recommendation?.let { " Recommended ${it.preset}, score ${it.score}." } ?: "")
            else -> readProgress?.let { "Reading game log, $it percent." } ?: "Reading game log."
        }

    Box(
        modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .semantics { contentDescription = desc },
    ) {
        WaveScan(
            phase = if (reducedMotion) 0f else phase.value,
            lock = lock,
            failed = failed,
            pal = pal,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(260.dp),
        )
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !arrived -> {
                    Text(
                        text = readProgress?.let { "$it%" } ?: "SCAN",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        color = hsl(pal.idleHue, 80f, 70f),
                    )
                    // Seat the readout and caption above/below the centered wave.
                    Spacer(Modifier.height(206.dp))
                    Text(
                        "DECRYPTING client.log",
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 3.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                failed -> {
                    val failure = result as LogReadResult.Failed
                    Spacer(Modifier.height(250.dp))
                    Text(
                        "READ FAILED",
                        color = hsl(pal.failHue, 80f, 60f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        failure.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    val rec = recommendation
                    Spacer(Modifier.height(30.dp))
                    RingScore(fraction = ringFraction, rec = rec, pal = pal)
                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (line in revealed) {
                            MonoRow(line)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Waveform band. Bar heights derive from a seeded standing wave + traveling
 * ripple; per-frame cost is BAR_COUNT sin() calls and BAR_COUNT round-rects,
 * no allocations, no state writes (phase is the only moving input). Locking
 * flattens jitter into the "resolved" shape so landing reads as convergence;
 * failure decays amplitude instead of freezing it.
 */
@Composable
private fun WaveScan(
    phase: Float,
    lock: Float,
    failed: Boolean,
    pal: AnimTheme,
    modifier: Modifier = Modifier,
) {
    val seeds = remember { FloatArray(BAR_COUNT) { i -> (i * 7919 % 100) / 100f } }
    val accent = if (failed) hsl(pal.failHue, 85f, 58f) else hsl(pal.idleHue, 85f, 62f)
    Canvas(modifier = modifier) {
        val barW = size.width / (BAR_COUNT * 1.7f)
        val gap = barW * 0.7f
        val total = BAR_COUNT * (barW + gap)
        val startX = (size.width - total) / 2f
        val midY = size.height * 0.46f
        for (i in 0 until BAR_COUNT) {
            val s = seeds[i]
            val ambient = 0.30f + 0.22f * (1f + sin(phase * TAU + i * 0.55f + s * 6f)) / 2f
            val ripple = 0.14f * (1f + sin(phase * TAU * 1.7f + i * 0.21f))
            val resolve = if (failed) 0f else lock * 0.18f * (0.4f + s)
            // On failure the wave dies toward the center line instead of only
            // recoloring — the panel reads as "signal lost", not "loss recolored".
            val dieOut = if (failed) 1f - 0.78f * lock else 1f
            val amp = ((ambient + ripple) * (1f - 0.35f * lock) + resolve) * dieOut
            val h = amp * size.height
            if (h <= 1f) continue
            val x = startX + i * (barW + gap)
            drawRoundRect(
                color = accent.copy(alpha = if (failed) 0.85f else (0.55f + 0.45f * lock)),
                topLeft = Offset(x, midY - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
        if (!failed && lock < 0.98f) {
            val scanY = midY - size.height * 0.44f + (phase % 1f) * size.height * 0.88f
            drawLine(
                color = accent.copy(alpha = 0.6f),
                start = Offset(startX, scanY),
                end = Offset(startX + total - gap, scanY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val BAR_COUNT = 56
private const val TAU = 2f * Math.PI.toFloat()

/** Ring gauge for the delivered phase: score sweep + the preset name beside it. */
@Composable
private fun RingScore(
    fraction: Float,
    rec: BrainRecommendation?,
    pal: AnimTheme,
) {
    // Named colors read BEFORE the draw scope: MaterialTheme cannot be read
    // inside DrawScope, and the sim keeps its palette contract via AnimTheme.
    val track = hsl(pal.idleHue, 12f, 46f, 0.22f)
    val ringColor = if (rec != null) hsl(pal.okHue, 85f, 60f) else hsl(pal.idleHue, 85f, 62f)
    val labelColor = if (rec != null) hsl(pal.okHue, 85f, 60f) else hsl(pal.idleHue, 85f, 62f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
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
                if (fraction > 0.005f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arc,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            if (rec != null) {
                Text(
                    "${rec.score}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                "SMART BRAIN",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                rec?.preset?.uppercase() ?: "LOG PARSED",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = labelColor,
            )
        }
    }
}

/** One extracted `KEY=value` row, mono, with a category-hued tick. */
@Composable
private fun MonoRow(line: BrainLine) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(width = 4.dp, height = 16.dp)) {
            drawRoundRect(
                color = hsl(line.category.hue, 85f, 60f),
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            line.text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            // Fixed light-on-dark: the host wraps this overlay in a permanent dark
            // scrim (ConfigGenScreen's 0xE6060A12), so scheme-wise onSurface would
            // invert wrongly under a light palette.
            color = Color(0xFFE6EDF3),
            maxLines = 1,
        )
    }
}
