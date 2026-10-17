package com.wuwaconfig.app.ui.anim

/*
 * Shared math + draw helpers for the Canvas sims (LoadFileAnimation,
 * LogBrainAnimation). These used to live in LogBrainAnimation.kt; when that
 * file was rewritten as a lighter overlay, the pieces both sims depend on
 * moved here unchanged so the two files stay independent of each other's
 * structure. Keep them dependency-free beyond compose-graphics.
 */

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

internal fun cl(
    v: Float,
    a: Float = 0f,
    b: Float = 1f,
) = v.coerceIn(a, b)

internal fun lerp(
    a: Float,
    b: Float,
    k: Float,
) = a + (b - a) * k

internal fun damp(
    a: Float,
    b: Float,
    l: Float,
    dt: Float,
) = a + (b - a) * (1f - exp(-l * dt))

internal fun eo(t: Float) = 1f - (1f - t).pow(3)

internal fun eio(t: Float) = if (t < .5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f

internal fun hashI(
    a: Int,
    b: Int,
): Int {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return (h xor (h ushr 16)) and 0x7fffffff
}

internal fun hsl(
    h: Float,
    s: Float,
    l: Float,
    a: Float = 1f,
): Color {
    val hh = ((h % 360f) + 360f) % 360f
    val ss = s / 100f
    val ll = l / 100f
    val c = (1f - abs(2f * ll - 1f)) * ss
    val x = c * (1f - abs((hh / 60f) % 2f - 1f))
    val m = ll - c / 2f
    val (r, g, b) =
        when {
            hh < 60f -> Triple(c, x, 0f)
            hh < 120f -> Triple(x, c, 0f)
            hh < 180f -> Triple(0f, c, x)
            hh < 240f -> Triple(0f, x, c)
            hh < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    return Color(cl(r + m), cl(g + m), cl(b + m), cl(a))
}

/** File-card silhouette shared by both sims (W x H with a dog-eared top-right fold). */
internal const val FW = 104f
internal const val FH = 132f
internal const val FOLD = 16f

internal fun filePath(
    p: Path,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
    f: Float,
): Path {
    val l = cx - w / 2f
    val r = cx + w / 2f
    val t = cy - h / 2f
    val b = cy + h / 2f
    p.reset()
    p.moveTo(l + 6f, t)
    p.lineTo(r - f, t)
    p.lineTo(r, t + f)
    p.lineTo(r, b - 6f)
    p.quadraticBezierTo(r, b, r - 6f, b)
    p.lineTo(l + 6f, b)
    p.quadraticBezierTo(l, b, l, b - 6f)
    p.lineTo(l, t + 6f)
    p.quadraticBezierTo(l, t, l + 6f, t)
    p.close()
    return p
}

/** Fake neon glow (setShadowLayer is unreliable on a hardware canvas): 3 strokes of growing width and fading alpha. */
internal fun DrawScope.glowPath(
    p: Path,
    c: Color,
    w: Float,
    glow: Float = 1f,
) {
    drawPath(p, c.copy(alpha = c.alpha * .10f * glow), style = Stroke(w * 7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(p, c.copy(alpha = c.alpha * .22f * glow), style = Stroke(w * 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(p, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

internal fun DrawScope.halo(
    cx: Float,
    cy: Float,
    r: Float,
    c: Color,
    blend: BlendMode = BlendMode.SrcOver,
) {
    val o = Offset(cx, cy)
    drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), o, r), r, o, blendMode = blend)
}
