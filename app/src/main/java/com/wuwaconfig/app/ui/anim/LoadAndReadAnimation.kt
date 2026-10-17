package com.wuwaconfig.app.ui.anim

/*
 * Stage 1 (LOAD): binary file drops in, loads with your real progress, color follows the progress.
 *                 fails  -> red glitch -> explosion, broken files everywhere
 *                 succeeds -> turns green, matrix runs inside
 * Stage 2 (READ): fades into the client.log -> smart brain animation (LogBrainAnimation.kt).
 *
 * Needs LogBrainAnimation.kt in the same package (shares its helpers and BrainSim).
 * Colors follow the active theme palette (see AnimTheme.kt). Reduced-motion
 * hosts fall back to the plain progress UI — this component animates
 * unconditionally.
 *
 *   LoadAndReadAnimation(
 *       loadProgress = downloadProgress,          // 0f..1f, or null if unknown (it creeps toward 90%)
 *       loadOutcome  = loadOutcome,               // null while loading, then Success / Failed
 *       readResult   = readResult,                // null while parsing, then Success(entries) / Failed(...)
 *       onSettled    = { stage, ok -> ... },
 *   )
 */

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ======================================================================
// Public API
// ======================================================================

enum class AnimStage { LOAD, READ }

sealed interface LoadOutcome {
    data object Success : LoadOutcome

    data class Failed(val reason: String = "data load failed") : LoadOutcome
}

@Composable
fun LoadAndReadAnimation(
    loadProgress: Float?,
    loadOutcome: LoadOutcome?,
    readResult: LogReadResult?,
    modifier: Modifier = Modifier,
    secondsPerLine: Float = 0.5f,
    startStage: AnimStage = AnimStage.LOAD,
    onSettled: (stage: AnimStage, success: Boolean) -> Unit = { _, _ -> },
) {
    val flow = remember { StageFlow(startStage) }
    val pal = rememberAnimTheme()
    val loadSim = remember { LoadSim(pal) }
    val readSim = remember { BrainSim(secondsPerLine, pal) }
    val cb by rememberUpdatedState(onSettled)

    loadSim.onSettled = { ok ->
        cb(AnimStage.LOAD, ok)
        if (ok) flow.beginSwitch()
    }
    readSim.onSettled = { ok -> cb(AnimStage.READ, ok) }
    SideEffect { loadSim.input(loadProgress, loadOutcome) }
    LaunchedEffect(readResult) { if (readResult != null) readSim.deliver(readResult) }

    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.04f)
            last = now
            if (flow.stage == AnimStage.LOAD) loadSim.update(dt) else readSim.update(dt)
            flow.step(dt)
            frame = now
        }
    }

    Canvas(modifier.fillMaxSize().graphicsLayer { alpha = 1f - flow.veil }) {
        if (flow.stage == AnimStage.LOAD) loadSim.draw(this, frame) else readSim.draw(this, frame)
    }
}

/** Only the loading animation (no read stage). */
@Composable
fun LoadFileAnimation(
    progress: Float?,
    outcome: LoadOutcome?,
    modifier: Modifier = Modifier,
    onSettled: (success: Boolean) -> Unit = {},
) = LoadAndReadAnimation(
    loadProgress = progress,
    loadOutcome = outcome,
    readResult = null,
    modifier = modifier,
    onSettled = { _, ok -> onSettled(ok) },
)

private class StageFlow(var stage: AnimStage) {
    var veil by mutableFloatStateOf(0f)
    private var dir = 0f

    fun beginSwitch() {
        dir = 1f
    }

    fun step(dt: Float) {
        if (dir == 0f) return
        veil = (veil + dir * dt / .4f).coerceIn(0f, 1f)
        if (dir > 0f && veil >= 1f) {
            stage = AnimStage.READ
            dir = -1f
        } else if (dir < 0f && veil <= 0f) dir = 0f
    }
}

// ======================================================================
// Loader simulation
// ======================================================================

private const val LVW = 360f
private const val LVH = 580f
private const val LCX = 180f
private const val LCY = 250f
private const val LGL = "01アイウエオカキクケコサシスセソタチツテトナニヌネ"

private enum class LPhase { DROP, LOAD, GLITCH, BOOM, OK }

private class LDrop(var x: Float, var y: Float, val v: Float, val c: Char, val a: Float)

private class LShard(var x: Float, var y: Float, var vx: Float, var vy: Float, val s: Float, var r: Float, var vr: Float, val ch: String) {
    var life = 0f
}

private class LSpark(var x: Float, var y: Float, var vx: Float, var vy: Float, val max: Float, val hue: Float) {
    var life = 0f
}

private class LRing(var r: Float, var a: Float, val flat: Boolean)

internal class LoadSim(pal: AnimTheme) {
    var onSettled: (Boolean) -> Unit = {}
    private val pal: AnimTheme = pal
    private val rnd = Random(System.nanoTime())
    private var progressIn: Float? = null
    private var outcome: LoadOutcome? = null

    fun input(
        p: Float?,
        o: LoadOutcome?,
    ) {
        progressIn = p
        outcome = o
    }

    private var ph = LPhase.DROP
    private var t = 0f
    private var tp = 0f
    private var hue = pal.idleHue
    private var dp = 0f
    private var y = -FH
    private var flash = 0f
    private var cam = 0f
    private var failed = false
    private var settled = false
    private var spawn = 0f
    private val drops = ArrayList<LDrop>()
    private val shards = ArrayList<LShard>()
    private val sparks = ArrayList<LSpark>()
    private val rings = ArrayList<LRing>()
    private val ry = FloatArray(14) { -rnd.nextFloat() * FH }
    private val rv = FloatArray(14) { 70f + rnd.nextFloat() * 120f }

    private val pathA = Path()
    private val pathB = Path()
    private val monoP =
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    private val sansP =
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
    private val buf = CharArray(1)

    private fun setPhase(p: LPhase) {
        ph = p
        tp = 0f
    }

    private fun easeOutBack(k: Float): Float {
        val c1 = 1.4f
        return 1f + (c1 + 1f) * (k - 1f).pow(3) + c1 * (k - 1f).pow(2)
    }

    private fun explode() {
        flash = 1f
        cam = 1f
        rings.add(LRing(10f, 1f, false))
        rings.add(LRing(4f, .7f, false))
        for (i in 0 until 80) {
            val a = rnd.nextFloat() * 6.283f
            val v = 260f + rnd.nextFloat() * 1000f
            shards.add(
                LShard(
                    LCX + (rnd.nextFloat() - .5f) * 80f,
                    y + (rnd.nextFloat() - .5f) * 90f,
                    cos(a) * v,
                    sin(a) * v - 380f,
                    12f + rnd.nextFloat() * 30f,
                    rnd.nextFloat() * 6f,
                    (rnd.nextFloat() - .5f) * 20f,
                    if (rnd.nextBoolean()) "0" else "1",
                ),
            )
        }
        for (i in 0 until 140) {
            val a = rnd.nextFloat() * 6.283f
            val v = 200f + rnd.nextFloat() * 1200f
            sparks.add(LSpark(LCX, y, cos(a) * v, sin(a) * v, .4f + rnd.nextFloat() * .7f, 5f + rnd.nextFloat() * 30f))
        }
    }

    fun update(dt: Float) {
        t += dt
        tp += dt
        var th = lerp(pal.idleHue, pal.warnHue, eo(dp))
        var lam = 5f
        when (ph) {
            LPhase.GLITCH -> {
                th = lerp(pal.warnHue, pal.failHue, cl(tp / .5f))
                lam = 14f
            }
            LPhase.BOOM -> {
                th = pal.failHue
                lam = 14f
            }
            LPhase.OK -> th = pal.okHue
            else -> {}
        }
        hue = damp(hue, th, lam, dt)

        when (ph) {
            LPhase.DROP -> {
                val k = cl(tp / 1.5f)
                y = lerp(-FH, LCY, easeOutBack(k))
                if (k >= 1f) {
                    rings.add(LRing(20f, .5f, true))
                    setPhase(LPhase.LOAD)
                }
            }
            LPhase.LOAD -> {
                y = LCY
                val o = outcome
                val target = if (o is LoadOutcome.Success) 1f else (progressIn ?: (.9f * (1f - exp(-tp / 6f))))
                dp = max(dp, damp(dp, cl(target), 4f, dt))
                if (o is LoadOutcome.Failed) {
                    hue = pal.warnHue
                    cam = .5f
                    failed = true
                    setPhase(LPhase.GLITCH)
                } else if (o is LoadOutcome.Success && dp > .985f) {
                    dp = 1f
                    flash = .5f
                    rings.add(LRing(40f, .8f, false))
                    setPhase(LPhase.OK)
                }
            }
            LPhase.GLITCH ->
                if (tp > 1.1f) {
                    explode()
                    setPhase(LPhase.BOOM)
                }
            LPhase.OK -> {
                for (i in ry.indices) {
                    ry[i] += rv[i] * dt
                    if (ry[i] > FH + 13f * 14f) {
                        ry[i] = -rnd.nextFloat() * 60f
                        rv[i] = 70f + rnd.nextFloat() * 120f
                    }
                }
                if (!settled && tp > 1.8f) {
                    settled = true
                    onSettled(true)
                }
            }
            LPhase.BOOM ->
                if (!settled && tp > 4.8f) {
                    settled = true
                    onSettled(false)
                }
        }

        if (ph == LPhase.DROP || ph == LPhase.LOAD) {
            spawn += dt * (if (ph == LPhase.DROP) 70f else 45f)
            while (spawn > 1f) {
                spawn -= 1f
                drops.add(
                    LDrop(
                        LCX + (rnd.nextFloat() - .5f) * FW * 1.1f,
                        -12f,
                        200f + rnd.nextFloat() * 280f,
                        if (rnd.nextBoolean()) '0' else '1',
                        .35f + rnd.nextFloat() * .45f,
                    ),
                )
            }
        }
        val top = y - FH / 2f
        drops.forEach { it.y += it.v * dt }
        drops.removeAll { if (ph == LPhase.DROP) it.y > LVH else !(it.y < top + 2f && ph == LPhase.LOAD) }

        cam = damp(cam, 0f, 4f, dt)
        flash = damp(flash, 0f, 5f, dt)
        rings.forEach {
            it.r += dt * (if (it.flat) 160f else 620f)
            it.a -= dt * (if (it.flat) .9f else 1.1f)
        }
        rings.removeAll { it.a <= 0f }
        for (s in shards) {
            s.life += dt
            s.vy += 1300f * dt
            s.vx *= exp(-.45f * dt)
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.r += s.vr * dt
            if (s.y > LVH - 12f) {
                s.y = LVH - 12f
                s.vy *= -.38f
                s.vx *= .78f
                s.vr *= .55f
            }
            if (s.x < 0f) {
                s.x = 0f
                s.vx *= -.5f
            }
            if (s.x > LVW) {
                s.x = LVW
                s.vx *= -.5f
            }
        }
        for (s in sparks) {
            s.life += dt
            s.vy += 900f * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.vx *= exp(-1.2f * dt)
        }
        sparks.removeAll { it.life >= it.max }
    }

    // ---------------------------------------------------------------- drawing
    private fun DrawScope.txt(
        s: String,
        x: Float,
        yy: Float,
        size: Float,
        color: Color,
        bold: Boolean = false,
        ls: Float = 0f,
        align: Paint.Align = Paint.Align.CENTER,
    ) {
        val p = if (bold) sansP else monoP
        p.textSize = size
        p.color = color.toArgb()
        p.textAlign = align
        p.letterSpacing = if (size > 0f) ls / size else 0f
        drawContext.canvas.nativeCanvas.drawText(s, x + if (align == Paint.Align.CENTER) ls / 2f else 0f, yy, p)
    }

    private fun DrawScope.ch(
        c: Char,
        x: Float,
        yy: Float,
        size: Float,
        color: Color,
    ) {
        monoP.textSize = size
        monoP.color = color.toArgb()
        monoP.textAlign = Paint.Align.CENTER
        monoP.letterSpacing = 0f
        buf[0] = c
        drawContext.canvas.nativeCanvas.drawText(buf, 0, 1, x, yy, monoP)
    }

    fun draw(
        scope: DrawScope,
        @Suppress("UNUSED_PARAMETER") tick: Long,
    ) {
        with(scope) {
            val w = size.width
            val h = size.height
            val sc = min(w / LVW, h / LVH)
            val ox = (w - LVW * sc) / 2f
            val oy = max(0f, (h - LVH * sc) / 2f)

            // The sim lives on a dark device screen; paint it so hosts without
            // a scrim of their own don't show light-on-light status ink.
            drawRect(SCREEN_BG)

            val shake = cam * 16f
            withTransform({
                translate(ox + (rnd.nextFloat() - .5f) * shake, oy + (rnd.nextFloat() - .5f) * shake)
                scale(sc, sc, Offset.Zero)
            }) {
                drawDrops()
                for (r in rings) {
                    val c = hsl(hue, 85f, 65f, r.a)
                    if (r.flat) {
                        drawOval(c, Offset(LCX - r.r, y + FH / 2f - r.r * .22f), Size(r.r * 2f, r.r * .44f), style = Stroke(2f))
                    } else {
                        drawCircle(c, r.r, Offset(LCX, y), style = Stroke(2f))
                    }
                }
                if (ph != LPhase.BOOM) drawFileScene()
                drawShards()
                drawStatus()
            }
            if (flash > .01f) drawRect(if (failed) hsl(pal.failHue, 87f, 55f, cl(flash * .45f)) else hsl(pal.okHue, 85f, 65f, cl(flash * .3f)))
        }
    }

    private fun DrawScope.drawDrops() {
        val top = y - FH / 2f
        for (d in drops) {
            var a = d.a
            if (ph == LPhase.LOAD) a *= cl((top - d.y) / 110f)
            ch(d.c, d.x, d.y, 13f, hsl(hue, 85f, 72f, a))
        }
        if (ph == LPhase.LOAD) {
            drawOval(hsl(hue, 85f, 70f, .18f), Offset(LCX - FW / 2f, top - 9f), Size(FW, 18f))
        }
    }

    private fun DrawScope.drawFileScene() {
        val bob = if (ph == LPhase.LOAD || ph == LPhase.OK) sin(t * 1.8f) * 2.5f else 0f
        val fy = y + bob
        val col = hsl(hue, 85f, 58f)
        if (ph == LPhase.GLITCH) {
            val a = cl(tp / 1.1f)
            val q = floor(t * 28f).toInt()
            val jx = (hashI(q, 1) % 100 / 100f - .5f) * 18f * a
            val jy = (hashI(q, 2) % 100 / 100f - .5f) * 8f * a
            drawLFile(LCX - 6f * a, fy, hsl(pal.failHue, 90f, 55f, .7f), false)
            drawLFile(LCX + 6f * a, fy, hsl(pal.glitch2Hue, 90f, 60f, .7f), false)
            drawLFile(LCX + jx, fy + jy, col, true)
            if (hashI(q, 3) % 3 == 0) {
                drawRect(hsl(pal.failHue, 90f, 60f, .5f), Offset(LCX - FW / 2f - 10f, fy - FH / 2f + hashI(q, 4) % FH.toInt()), Size(FW + 20f, 3f))
            }
        } else {
            drawLFile(LCX, fy, col, true)
        }
    }

    private fun DrawScope.drawLFile(
        fx: Float,
        fy: Float,
        col: Color,
        full: Boolean,
    ) {
        val path = filePath(pathA, fx, fy, FW, FH, FOLD)
        if (full) drawPath(path, Brush.verticalGradient(listOf(Color(0xFF111B2E), Color(0xFF09101C)), fy - FH / 2f, fy + FH / 2f))
        glowPath(path, col, 2f, if (full) 1f else .4f)
        if (!full) return
        val left = fx - FW / 2f
        val bottom = fy + FH / 2f
        val fillTop = bottom - FH * dp
        val tick = floor(t * 14f).toInt()
        clipPath(path) {
            if (ph != LPhase.OK) {
                if (dp > .003f) {
                    drawRect(
                        Brush.verticalGradient(listOf(hsl(hue, 85f, 58f, .12f), hsl(hue, 85f, 58f, .5f)), fillTop, bottom),
                        Offset(left, fillTop),
                        Size(FW, FH * dp + 1f),
                    )
                }
                if (dp > .005f && dp < .999f) {
                    drawRect(hsl(hue, 90f, 70f, .25f), Offset(left, fillTop - 4f), Size(FW, 8f))
                    drawRect(hsl(hue, 85f, 82f, .95f), Offset(left, fillTop - 1f), Size(FW, 2f))
                }
                val slow = floor(t * 2f).toInt()
                for (r in 0 until 8) for (q in 0 until 10) {
                    val px = left + 10f + q * (FW - 24f) / 10f
                    val py = fy - FH / 2f + 34f + r * 10.5f
                    val bit = hashI(r * 10 + q, if (hashI(r, q) % 3 == 0) slow else 0) and 1
                    ch(if (bit == 1) '1' else '0', px, py, 11f, if (py > fillTop) hsl(hue, 85f, 78f, .85f) else hsl(hue, 85f, 60f, .2f))
                }
            } else {
                val k = eo(cl(tp / .7f))
                drawRect(hsl(pal.okHue, 92f, 3f).copy(alpha = k), Offset(left, fy - FH / 2f), Size(FW, FH))
                for (i in 0 until 9) {
                    val cx = left + 8f + i * (FW - 16f) / 9f
                    for (j in 0 until 13) {
                        val yy = fy - FH / 2f + ry[i] - j * 14f
                        if (yy < fy - FH / 2f - 14f || yy > bottom + 14f) continue
                        val c = LGL[hashI(i * 31 + j, if (j == 0) tick else tick / 3) % LGL.length]
                        val col2 = if (j == 0) hsl(pal.okHue, 88f, 92f, k) else hsl(pal.okHue, 100f, 46f, k * (1f - j / 13f).pow(1.6f))
                        ch(c, cx, yy, 12f, col2)
                    }
                }
            }
        }
        val r = fx + FW / 2f
        val tt = fy - FH / 2f
        pathB.reset()
        pathB.moveTo(r - FOLD, tt)
        pathB.lineTo(r - FOLD, tt + FOLD)
        pathB.lineTo(r, tt + FOLD)
        pathB.close()
        drawPath(pathB, hsl(hue, 85f, 58f, .35f))
        drawPath(pathB, col, style = Stroke(1.5f))
    }

    private fun DrawScope.drawShards() {
        for (s in shards) {
            val a = cl(1f - (s.life - 3.2f) / 1.4f)
            withTransform({
                translate(s.x, s.y)
                rotate(s.r * 57.29578f, Offset.Zero)
            }) {
                filePath(pathA, 0f, 0f, s.s, s.s * 1.3f, s.s * .28f)
                drawPath(pathA, Color(0xFF180809).copy(alpha = a))
                glowPath(pathA, hsl(pal.failHue, 88f, 58f, a), 1.6f, .6f)
                txt(s.ch, 0f, s.s * .15f, s.s * .42f, hsl(pal.failHue, 88f, 66f, .9f * a))
            }
        }
        for (s in sparks) {
            val a = 1f - s.life / s.max
            drawLine(hsl(s.hue, 100f, 60f, a), Offset(s.x, s.y), Offset(s.x - s.vx * .025f, s.y - s.vy * .025f), 1.6f, blendMode = BlendMode.Plus)
        }
    }

    private fun DrawScope.drawStatus() {
        val pct = (dp * 100f).roundToInt().coerceIn(0, 100)
        if (ph == LPhase.BOOM) {
            val a = eo(cl((tp - .5f) / .8f))
            val reason = (outcome as? LoadOutcome.Failed)?.reason ?: "data load failed"
            txt("LOAD FAILED", LCX, LCY - 40f, 22f, hsl(pal.failHue, 88f, 64f, a), true, 6f)
            txt(reason.uppercase() + " · AT $pct%", LCX, LCY - 12f, 10f, hsl(pal.failHue, 60f, 80f, .7f * a), true, 2f)
            return
        }
        val lb =
            when (ph) {
                LPhase.DROP -> "RECEIVING DATA"
                LPhase.LOAD -> "LOADING DATA"
                LPhase.GLITCH -> "ERROR · DATA CORRUPTED"
                else -> "LOAD COMPLETE"
            }
        val barY = y + FH / 2f + 34f
        val col = hsl(hue, 85f, 58f)
        txt(lb, LCX, barY, 11f, hsl(hue, 70f, 72f, .9f), true, 3f)
        txt("$pct%", LCX, barY + 40f, 34f, Color(.95f, .965f, 1f), true, 1f)
        drawRoundRect(Color(0x14FFFFFF), Offset(LCX - 100f, barY + 56f), Size(200f, 4f), CornerRadius(2f))
        val bw = max(4f, 200f * dp)
        drawRoundRect(col.copy(alpha = .25f), Offset(LCX - 100f, barY + 53f), Size(bw, 10f), CornerRadius(5f))
        drawRoundRect(col, Offset(LCX - 100f, barY + 56f), Size(bw, 4f), CornerRadius(2f))
        if (ph == LPhase.OK) txt("data ready", LCX, barY + 80f, 10f, Color(.745f, .804f, .9f, .6f), false, 1f)
    }
}
