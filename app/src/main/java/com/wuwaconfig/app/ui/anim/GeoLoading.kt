package com.wuwaconfig.app.ui.anim

/*
 * Geometric loading patterns — a DIFFERENT motif per job, one Canvas each:
 *
 *  - DeployBeaconLoading — "push to device" wait (ConfigGen / Home apply):
 *    a device chip radiating expanding signal rings while packet diamonds
 *    fly inbound along three lanes. Reads as "files in flight".
 *  - PityOrbitLoading — pity tracker / gacha fetch (PityScreen): diamonds
 *    orbit a pulsing counter-diamond against a counter-rotating gauge arc —
 *    an odometer of pulls.
 *  - BattleRadarLoading — battle stats read (BattleStatsScreen): a radar
 *    sweep inside hex rings; blips ignite as the beam passes, the inner arc
 *    tracks the real read progress.
 *
 * Accents come from the host's Neon color, which is palette-supplied, so the
 * patterns follow the active color palette without reading AnimTheme here.
 *
 * Reduced motion: GeoLoadingCard drops back to the classic OrbLoadingCard
 * for these hosts; inside the canvases the phase is frozen at a fixed frame
 * so a host that still composes them never chases the user. Each freezes
 * into a complete static composition (rings at 3 radii, sweep at a fixed
 * angle, blips lit) rather than an empty placeholder.
 */

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.OrbLoadingCard
import com.wuwaconfig.app.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Which job the loading card stands in for; picks the motif. */
enum class GeoLoadingKind { DEPLOY, PITY, BATTLE }

/**
 * The loading card with a geometric motif. Reduced-motion hosts get the
 * classic bouncing-orb card verbatim — same text, same percent readout —
 * so the accessible fallback stays identical to what it replaced.
 */
@Composable
fun GeoLoadingCard(
    kind: GeoLoadingKind,
    text: String,
    accentColor: Color,
    progress: Int = 0,
) {
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        OrbLoadingCard(text, accentColor, progress = progress)
        return
    }
    GlassCard(accentColor = accentColor) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (kind) {
                GeoLoadingKind.DEPLOY -> DeployBeaconLoading(accentColor)
                GeoLoadingKind.PITY -> PityOrbitLoading(accentColor)
                GeoLoadingKind.BATTLE -> BattleRadarLoading(accentColor, progress)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
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
 * One shared 0..1 phase per motif: an infinite transition that self-pauses
 * when the composable leaves composition, or a frozen frame under reduced
 * motion (a value mid-phase so the static variant still reads complete).
 */
@Composable
private fun geoPhase(reduced: Boolean): State<Float> =
    if (reduced) {
        remember { mutableStateOf(0.42f) }
    } else {
        rememberInfiniteTransition(label = "geoLoading").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 4200, easing = LinearEasing),
                ),
            label = "phase",
        )
    }

// =====================================================================
// DEPLOY wait — device chip + beacon rings + inbound packet diamonds
// =====================================================================

@Composable
fun DeployBeaconLoading(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val phase by geoPhase(rememberReducedMotion())
    Canvas(modifier = modifier.fillMaxWidth().height(150.dp)) {
        val cx = size.width / 2f
        val cy = size.height * 0.54f
        val devW = 64.dp.toPx()
        val devH = 46.dp.toPx()
        val maxR = minOf(cx, cy - 6f)

        // Expanding beacon rings: each rides its own third of the cycle so
        // three radii are always visible, alpha falling as they widen.
        for (k in 0 until 3) {
            val t = (phase + k / 3f) % 1f
            val r = devW * 0.55f + (maxR - devW * 0.55f) * t
            drawCircle(
                color = accent.copy(alpha = (1f - t) * (1f - t) * 0.5f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(1.2.dp.toPx()),
            )
        }

        // Device silhouette: rounded chip, screen bar rack, four pins below.
        drawRoundRect(
            color = accent.copy(alpha = 0.9f),
            topLeft = Offset(cx - devW / 2f, cy - devH / 2f),
            size = Size(devW, devH),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round),
        )
        for (i in 0 until 3) {
            drawRoundRect(
                color = accent.copy(alpha = 0.45f - i * 0.12f),
                topLeft = Offset(cx - devW * 0.30f, cy - devH * 0.24f + i * 9.dp.toPx()),
                size = Size(devW * 0.60f * (1f - i * 0.16f) + phase * devW * 0.06f, 2.4.dp.toPx()),
                cornerRadius = CornerRadius(2f),
            )
        }
        for (i in 0 until 4) {
            val px0 = cx - devW / 2f + devW * (0.22f + 0.19f * i)
            drawLine(
                color = accent.copy(alpha = 0.5f),
                start = Offset(px0, cy + devH / 2f),
                end = Offset(px0, cy + devH / 2f + 5.dp.toPx()),
                strokeWidth = 1.4f,
                cap = StrokeCap.Round,
            )
        }

        // Packet diamonds fly inbound along three lanes, shrinking and
        // flashing out as they dock onto the chip edge.
        val lanes = floatArrayOf(-124f, -56f, 168f)
        lanes.forEachIndexed { i, deg ->
            val t = (phase * 0.85f + i * 0.33f) % 1f
            val dist = (maxR * 1.02f - devW * 0.62f) * (1f - eo(t)) + devW * 0.62f
            val rad = deg * PI.toFloat() / 180f
            val px = cx + cos(rad) * dist
            val py = cy + sin(rad) * dist
            val s = (7f - 4f * t).dp.toPx()
            rotate(degrees = 45f, pivot = Offset(px, py)) {
                drawRoundRect(
                    color = accent.copy(alpha = 0.15f + 0.7f * sin(PI.toFloat() * t)),
                    topLeft = Offset(px - s / 2f, py - s / 2f),
                    size = Size(s, s),
                    cornerRadius = CornerRadius(2f),
                )
            }
        }
    }
}

// =====================================================================
// PITY tracker wait — orbiting diamonds around a pulsing counter-diamond
// =====================================================================

@Composable
fun PityOrbitLoading(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val phase by geoPhase(rememberReducedMotion())
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val orbitR = minOf(cx, cy) * 0.78f

        // Track + counter-rotating gauge arc: the arc sweeps backwards while
        // the counter spins forwards, a needle against the odometer.
        drawCircle(
            color = accent.copy(alpha = 0.10f),
            radius = orbitR,
            center = Offset(cx, cy),
            style = Stroke(1f),
        )
        drawArc(
            color = accent.copy(alpha = 0.55f),
            startAngle = -phase * 360f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(cx - orbitR, cy - orbitR),
            size = Size(orbitR * 2f, orbitR * 2f),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )

        // Eight orbiting pips, big/small alternating like pity counter slots,
        // each trailing a faint mote right behind it.
        for (i in 0 until 8) {
            val a = (i * 45f + phase * 360f) % 360f
            val rad = a * PI.toFloat() / 180f
            val px = cx + cos(rad) * orbitR
            val py = cy + sin(rad) * orbitR
            val big = i % 2 == 0
            val s = (if (big) 9f else 6f).dp.toPx()
            rotate(degrees = 45f, pivot = Offset(px, py)) {
                drawRoundRect(
                    color = accent.copy(alpha = if (big) 0.9f else 0.5f),
                    topLeft = Offset(px - s / 2f, py - s / 2f),
                    size = Size(s, s),
                    cornerRadius = CornerRadius(2f),
                )
            }
            val a2 = (a - 14f) * PI.toFloat() / 180f
            drawCircle(
                color = accent.copy(alpha = if (big) 0.28f else 0.16f),
                radius = 1.6.dp.toPx(),
                center = Offset(cx + cos(a2) * orbitR, cy + sin(a2) * orbitR),
            )
        }

        // Center counter-diamond: slow 120° spin + breathing size.
        val breathe = 0.5f + 0.5f * sin(phase * (2f * PI.toFloat()) * 2.5f)
        val cs = (11f + 5f * breathe).dp.toPx()
        rotate(degrees = (phase * 120f + 45f) % 360f, pivot = Offset(cx, cy)) {
            drawRoundRect(
                color = accent.copy(alpha = 0.85f + 0.15f * breathe),
                topLeft = Offset(cx - cs / 2f, cy - cs / 2f),
                size = Size(cs, cs),
                cornerRadius = CornerRadius(2.5f),
            )
        }
    }
}

// =====================================================================
// BATTLE stats wait — radar sweep in hex rings, blips ignite on pass
// =====================================================================

@Composable
fun BattleRadarLoading(
    accent: Color,
    progress: Int = 0,
    modifier: Modifier = Modifier,
) {
    val phase by geoPhase(rememberReducedMotion())
    val hex = remember { Path() }
    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = minOf(cx, cy) * 0.86f

        ring(hex, cx, cy, outerR)
        drawPath(hex, accent.copy(alpha = 0.22f), style = Stroke(1.2.dp.toPx()))
        ring(hex, cx, cy, outerR * 0.62f)
        drawPath(hex, accent.copy(alpha = 0.10f), style = Stroke(1f))

        val ang = phase * 360f

        // Trailing sweep: three fading segments + the beam itself, drawn on
        // the circle the hexagon encloses.
        for (j in 0 until 3) {
            drawArc(
                color = accent.copy(alpha = 0.28f * (1f - j * 0.3f)),
                startAngle = ang - (j + 1) * 18f,
                sweepAngle = 18f,
                useCenter = false,
                topLeft = Offset(cx - outerR, cy - outerR),
                size = Size(outerR * 2f, outerR * 2f),
                style = Stroke(6.dp.toPx()),
            )
        }
        val rad = ang * PI.toFloat() / 180f
        drawLine(
            color = accent,
            start = Offset(cx, cy),
            end = Offset(cx + cos(rad) * outerR, cy + sin(rad) * outerR),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Blips on the inner ring ignite as the beam passes, then decay over
        // a 70° corridor behind it; a lit blip pings a hairline echo ring.
        for (k in 0 until 6) {
            val b = 30f + k * 60f
            val dist = ((ang - b) % 360f + 360f) % 360f
            val lit = if (dist < 70f) 1f - dist / 70f else 0f
            val bx = cx + cos(b * PI.toFloat() / 180f) * outerR * 0.55f
            val by = cy + sin(b * PI.toFloat() / 180f) * outerR * 0.55f
            drawCircle(
                color = accent.copy(alpha = 0.15f + 0.7f * lit),
                radius = (2f.dp.toPx() + 1.5f.dp.toPx() * lit),
                center = Offset(bx, by),
            )
            if (lit > 0.15f) {
                drawCircle(
                    color = accent.copy(alpha = 0.4f * lit),
                    radius = (3f + lit * 9f).dp.toPx(),
                    center = Offset(bx, by),
                    style = Stroke(1f),
                )
            }
        }

        // Real read progress inside: a quiet thickening inner arc.
        if (progress > 0 && progress < 100) {
            val pr = outerR * 0.38f
            drawArc(
                color = accent.copy(alpha = 0.8f),
                startAngle = -90f,
                sweepAngle = progress.coerceIn(0, 100) * 3.6f,
                useCenter = false,
                topLeft = Offset(cx - pr, cy - pr),
                size = Size(pr * 2f, pr * 2f),
                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** Closed hexagon into a reused path (transcription of the sims' reset-and-rebuild pattern). */
private fun ring(
    p: Path,
    cx: Float,
    cy: Float,
    r: Float,
) {
    p.reset()
    for (k in 0 until 6) {
        val a = (k * 60f) * PI.toFloat() / 180f
        val x = cx + cos(a) * r
        val y = cy + sin(a) * r
        if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
}

// eo comes from AnimHelpers.kt (shared with the other sims).
