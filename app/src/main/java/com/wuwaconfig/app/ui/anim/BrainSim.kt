package com.wuwaconfig.app.ui.anim

/*
 * BrainSim — the neuron-network sim engine. Rehomed verbatim from
 * LogBrainAnimation.kt when that overlay was rewritten lighter: LoadFileAnimation
 * (LoadAndReadAnimation.kt) drives this class, so it stays exactly as delivered
 * (Phase/Node/Edge/... + the draw BrainSim performs on the host Canvas). The
 * rewritten LogBrainAnimation no longer uses it.
 */

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.wuwaconfig.app.config.BrainRecommendation
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Simulation state
// ======================================================================

// Private geometry/consts that partner BrainSim — moved with it from
// LogBrainAnimation.kt (nothing else consumes them).
private const val N = 8
private const val RH = 10.5f
private const val GARB = "▒░▓#%?@\$&"
private const val FAIL_FILL = "ERR_UNEXPECTED_EOF"

private fun rr(t: Float) = 1f + .04f * sin(4f * t + .5f) + .025f * sin(9f * t + 2f) + .015f * sin(13f * t)

private class CardR(val x: Float, val y: Float, val w: Float, val h: Float)

private class Lay(
    val vw: Float,
    val vh: Float,
    val fx: Float,
    val fy: Float,
    val bx: Float,
    val bcy: Float,
    val br: Float,
    val sx: Float,
    val sy: Float,
    val cards: List<CardR>,
    val wide: Boolean,
)

private val LAY_WIDE =
    Lay(
        900f, 470f, 150f, 205f, 450f, 195f, 105f, 450f, 392f,
        List(6) { CardR(690f, 70f + it * 56f, 190f, 44f) }, true,
    )
private val LAY_NARROW =
    Lay(
        // Tiny file (112x146 of a 360-wide viewport) high-center; big spiral brain
        // (84R) as the hero below it; cards left/right; status at bottom.
        360f, 700f, 180f, 76f, 180f, 296f, 84f, 180f, 614f,
        List(6) { CardR(if (it % 2 == 1) 184f else 10f, 434f + (it / 2) * 48f, 166f, 40f) }, false,
    )

private enum class Phase { WAIT, READ, GLITCH, DONE, DEAD }

private class Node(val u: Float, val v: Float) {
    var e = 0f
    var k = 0f
    var h = 200f
    val nb = ArrayList<Int>()

    var fall = false
    var rel = false
    var px = 0f
    var py = 0f
    var vx = 0f
    var vy = 0f
    var dl = 0f
}

private class Edge(val a: Int, val b: Int, val len: Float) {
    var broken = false
}

private class Chip(val i: Int) {
    var k = 0f
}

private class Pend(val i: Int, val t: Float, val h: Float)

private class Sig(val a: Int, val b: Int, val sp: Float, val h: Float, val d: Int) {
    var k = 0f
}

private class Shard(var x: Float, var y: Float, var vx: Float, var vy: Float, val s: Float, var r: Float, var vr: Float, val ch: String) {
    var life = 0f
}

private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, val max: Float, val hue: Float) {
    var life = 0f
}

private class Seg(val lx: FloatArray, val ly: FloatArray, var x: Float, var y: Float, var vx: Float, var vy: Float, var vr: Float, val dl: Float) {
    var r = 0f
}

private class CardState {
    var cnt = 0
    var v = ""
    var f = 0f
    var fl = 0f
}

private class Ring(var r: Float, var a: Float, val hue: Float, val onFile: Boolean)

internal class BrainSim(private val rowSeconds: Float, pal: AnimTheme) {
    var onSettled: (Boolean) -> Unit = {}

    /** Live inputs from the host: real read progress and the real recommendation. */
    internal var waitProgress: Int? = null
    internal var recommendation: BrainRecommendation? = null
    private val pal: AnimTheme = pal
    private val rnd = Random(System.nanoTime())

    // brain graph
    private val nodes = ArrayList<Node>()
    private val edges = ArrayList<Edge>()
    private var entry = 0
    private val px = FloatArray(64)
    private val py = FloatArray(64)

    init {
        // Spiral neuron layout: 1 hub + 43 neurons down a 2.2-turn eased spiral
        // (outer rim -> hub), riding just inside the glowing spiral cortex drawn
        // in drawBrain (smoothstep radius: bunched at the hub, airy at the rim).
        // Wiring = the spiral spine + one layer link per node to the node ~a full
        // turn deeper + hub spokes at the inner end + sparse chords.
        // (v1: Random point cloud = ameba; v2: rings = bullseye; v3: this.)
        val jit = Random(7)
        val turns = 2.2f
        val count = 43
        val startAng = Math.PI.toFloat() + .35f
        nodes.add(Node(0f, 0f))
        for (i in 0 until count) {
            val f = (count - 1 - i) / (count - 1).toFloat() // 1.0 = rim, 0 = hub side
            val e = f * f * (3f - 2f * f) // smoothstep, matches the drawn cortex
            val ang = startAng + f * 6.2832f * turns + (jit.nextFloat() - .5f) * .14f
            val r = lerp(.24f, 1.03f, e) * (1f + (jit.nextFloat() - .5f) * .05f)
            nodes.add(Node(cos(ang) * 1.18f * r, sin(ang) * .9f * r))
        }

        fun wire(
            i: Int,
            j: Int,
        ) {
            if (i == j) return
            val d = hypot(nodes[i].u - nodes[j].u, nodes[i].v - nodes[j].v)
            edges.add(Edge(i, j, d))
            nodes[i].nb.add(j)
            nodes[j].nb.add(i)
        }

        // spine + mesh: consecutive neurons on the spiral plus a skip-one rib
        // (diamond mesh); every node also links ~half a turn deeper so the
        // network breathes across layers without spokes slashing the spiral
        for (i in 1 until nodes.size - 1) wire(i, i + 1)
        for (i in 1 until nodes.size - 2 step 2) wire(i, i + 2)
        val halfTurn = (count / (turns * 2f)).roundToInt()
        for (i in 1 + halfTurn until nodes.size) wire(i, i - halfTurn)
        // hub spokes at the inner end
        wire(0, nodes.size - 1)
        wire(0, nodes.size - 2)

        // The reading chip enters at the outer rim, on the file's side.
        entry = (1 until nodes.size).minByOrNull { hypot(nodes[it].u + 1.02f * 1.18f, nodes[it].v) } ?: 1
    }

    // timeline
    private var phase = Phase.WAIT
    private var t = 0f
    private var tp = 0f
    private var hue = pal.idleHue
    private var prog = 0f

    // WAIT-phase scan cursor: anchors to the real reported row and sweeps the
    // remaining file instead of sitting pinned to one row.
    private var scanPos = 2f
    private var readDelay = 0f
    private var entries: List<BrainLine> = emptyList()
    private var m = 0
    private var fail = false
    private var failRow = 0
    private var reason = ""
    private var delivered = false
    private var settledCalled = false

    private val sp = BooleanArray(N)
    private val chips = ArrayList<Chip>()
    private val pend = ArrayList<Pend>()
    private val sigs = ArrayList<Sig>()
    private val shards = ArrayList<Shard>()
    private val sparks = ArrayList<Spark>()
    private val segs = ArrayList<Seg>()
    private val cards = List(6) { CardState() }
    private var ring: Ring? = null
    private var ring2: Ring? = null
    private var flash = 0f
    private var cam = 0f
    private var fires = 0
    private var br = 1f
    private var lay = LAY_NARROW

    // "running network" hues drift from success back to idle instead of the
    // old fixed green->blue ramp so themed palettes stay coherent.
    private val runH =
        floatArrayOf(
            pal.okHue,
            lerp(pal.okHue, pal.idleHue, .33f),
            lerp(pal.okHue, pal.idleHue, .66f),
            pal.idleHue,
        )

    private val pathA = Path()
    private val pathB = Path()
    private val pathC = Path()

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

    private fun setPhase(p: Phase) {
        phase = p
        tp = 0f
    }

    fun deliver(r: LogReadResult) {
        if (delivered) return
        delivered = true
        when (r) {
            is LogReadResult.Success -> {
                entries = r.entries.take(N)
                fail = entries.isEmpty()
                reason = "client.log had no readable data"
            }
            is LogReadResult.Failed -> {
                entries = r.parsed.take(N - 1)
                fail = true
                reason = r.reason
            }
        }
        m = entries.size
        failRow = m
        prog = 0f
        readDelay = .35f
        setPhase(Phase.READ)
    }

    // ---------------------------------------------------------------- logic
    private fun fire(
        i: Int,
        h: Float,
        d: Int,
        learn: Boolean = true,
    ) {
        val n = nodes[i]
        n.e = 1f
        n.h = h
        if (learn) {
            n.k = 1f
            fires++
        }
        if (d <= 0 || sigs.size > 200) return
        for (j in n.nb) if (rnd.nextFloat() < .45f) sigs.add(Sig(i, j, 1.6f + rnd.nextFloat() * 1.4f, h, d - 1))
    }

    private fun pulse(h: Float) {
        val seen = HashSet<Int>().apply { add(entry) }
        var front = listOf(entry)
        var hop = 0
        while (front.isNotEmpty() && hop < 5) {
            for (i in front) pend.add(Pend(i, t + hop * .11f, h))
            val next = ArrayList<Int>()
            for (i in front) for (j in nodes[i].nb) if (j !in seen && rnd.nextFloat() < .6f) {
                seen.add(j)
                next.add(j)
            }
            front = next
            hop++
        }
    }

    private fun entryPt() = Offset(lay.bx + nodes[entry].u * lay.br, lay.bcy + nodes[entry].v * lay.br)

    private fun chipPos(
        i: Int,
        k: Float,
    ): Offset {
        val sx = lay.fx + FW / 2f - 14f
        val sy = lay.fy - FH / 2f + 34f + i * RH + RH / 2f
        val e = entryPt()
        val cx = (sx + e.x) / 2f
        val cy = min(sy, e.y) - 70f
        val q = 1f - k
        return Offset(q * q * sx + 2f * q * k * cx + k * k * e.x, q * q * sy + 2f * q * k * cy + k * k * e.y)
    }

    private fun shatter() {
        // Same burst language as the loader's file explosion: white flash, twin
        // shock rings, 0/1 shards torn outward, warm sparks. The brain then
        // collapses its segments on top of it (collapse()).
        flash = 1f
        cam = 1f
        ring = Ring(10f, 1f, pal.failHue, true)
        ring2 = Ring(4f, .7f, pal.failHue, true)
        for (i in 0 until 80) {
            val a = rnd.nextFloat() * 6.283f
            val v = 260f + rnd.nextFloat() * 1000f
            shards.add(
                Shard(
                    lay.fx + (rnd.nextFloat() - .5f) * FW * .7f,
                    lay.fy + (rnd.nextFloat() - .5f) * FH * .6f,
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
            sparks.add(Spark(lay.fx, lay.fy, cos(a) * v, sin(a) * v, .4f + rnd.nextFloat() * .7f, 5f + rnd.nextFloat() * 30f))
        }
    }

    private fun collapse() {
        val R = lay.br
        val pts =
            Array(72) { i ->
                val a = i / 72f * 6.2832f
                val r = rr(a)
                floatArrayOf(lay.bx + R * 1.18f * r * cos(a), lay.bcy + R * .9f * r * sin(a))
            }
        for (q in 0 until 24) {
            val ids = Array(4) { k -> pts[(q * 3 + k) % 72] }
            val cx = ids.sumOf { it[0].toDouble() }.toFloat() / 4f
            val cy = ids.sumOf { it[1].toDouble() }.toFloat() / 4f
            segs.add(
                Seg(
                    FloatArray(4) { ids[it][0] - cx },
                    FloatArray(4) { ids[it][1] - cy },
                    cx,
                    cy,
                    (cx - lay.bx) * .6f + (rnd.nextFloat() - .5f) * 70f,
                    -rnd.nextFloat() * 140f,
                    (rnd.nextFloat() - .5f) * 6f,
                    rnd.nextFloat() * .7f,
                ),
            )
        }
        val e = nodes[entry]
        sigs.clear()
        for (n in nodes) {
            n.fall = true
            n.rel = false
            n.px = lay.bx + n.u * R
            n.py = lay.bcy + n.v * R
            n.vx = (n.px - lay.bx) * .5f + (rnd.nextFloat() - .5f) * 80f
            n.vy = -rnd.nextFloat() * 120f
            n.dl = hypot(n.u - e.u, n.v - e.v) * .35f + rnd.nextFloat() * .5f
        }
    }

    fun update(dt: Float) {
        t += dt
        tp += dt
        var th = pal.idleHue
        var lam = 5f
        if (phase == Phase.DONE) th = pal.okHue
        if (phase == Phase.GLITCH || phase == Phase.DEAD) {
            th = pal.failHue
            lam = 10f
        }
        hue = damp(hue, th, lam, dt)

        when (phase) {
            Phase.WAIT -> {
                val rp = waitProgress
                prog =
                    if (rp != null && rp > 0) {
                        // The scan's floor reflects what the device actually reported
                        // (10..98). It sits just under a full row so it never fakes a
                        // completed read.
                        (rp.coerceAtMost(97) / 100f * N).coerceAtLeast(0.15f)
                    } else {
                        (t * 1.1f) % N
                    }
                // The laser sweeps the remaining rows and bounces back — the file
                // keeps looking scanned between the coarse progress taps (10→60→75)
                // without ever overtaking the real position again.
                val unscanned = (N - prog).coerceAtLeast(.2f)
                scanPos = prog + (.5f + .5f * sin(t * 1.4f)) * unscanned
                if (rnd.nextFloat() < dt * 3f) fire(rnd.nextInt(nodes.size), pal.idleHue, 1, learn = false)
            }
            Phase.READ -> {
                if (readDelay > 0f) {
                    readDelay -= dt
                } else {
                    prog = min(if (fail) failRow + 1f else m.toFloat(), prog + dt / rowSeconds)
                }
                for (i in 0 until m) if (!sp[i] && prog >= i + 1) {
                    sp[i] = true
                    chips.add(Chip(i))
                }
                if (fail && prog >= failRow + .55f) {
                    hue = pal.warnHue
                    cam = .5f
                    setPhase(Phase.GLITCH)
                } else if (!fail && prog >= m) {
                    flash = .5f
                    ring = Ring(20f, .9f, pal.okHue, false)
                    setPhase(Phase.DONE)
                    nodes.forEachIndexed { i, n -> pend.add(Pend(i, t + hypot(n.u + 1.1f, n.v) * .28f, pal.okHue)) }
                }
            }
            Phase.GLITCH ->
                if (tp > 1.1f) {
                    // shatter() owns the whole burst (flash, twin rings, shards,
                    // sparks); collapse() adds the shell segments underneath it.
                    shatter()
                    collapse()
                    chips.clear()
                    setPhase(Phase.DEAD)
                }
            else -> {}
        }

        // chips arriving at the brain
        chips.forEach { it.k += dt / .8f }
        val landed = chips.filter { it.k >= 1f }
        chips.removeAll { it.k >= 1f }
        for (c in landed) {
            val en = entries[c.i]
            val cd = cards[en.category.ordinal]
            cd.cnt++
            cd.f = 0f
            cd.fl = 1f
            cd.v = en.text.substringAfter('=')
            pulse(en.category.hue)
        }

        // brain node pulses
        pend.removeAll { p ->
            if (t >= p.t) {
                nodes[p.i].e = 1f
                nodes[p.i].h = p.h
                nodes[p.i].k = 1f
                true
            } else {
                false
            }
        }
        br = if (phase == Phase.DONE) 1f + .02f * sin(tp * 3.2f) else 1f
        for (n in nodes) {
            n.e *= exp(-2.2f * dt)
            if (phase == Phase.GLITCH && rnd.nextFloat() < dt * 9f) {
                n.e = 1f
                n.h = pal.failHue
            }
            if (phase == Phase.DEAD) {
                n.h = damp(n.h, pal.failHue, 6f, dt)
                n.k *= exp(-.9f * dt)
                if (tp > n.dl) {
                    if (!n.rel) {
                        n.rel = true
                        n.e = 1f
                        n.h = 0f
                    }
                    n.vy += 1000f * dt
                    n.px += n.vx * dt
                    n.py += n.vy * dt
                    if (n.py > lay.vh - 14f) {
                        n.py = lay.vh - 14f
                        n.vy *= -.3f
                        n.vx *= .7f
                    }
                    if (n.px < 4f || n.px > lay.vw - 4f) n.vx *= -.5f
                }
            }
        }
        if (phase == Phase.DEAD) {
            for (e in edges) if (!e.broken) {
                val a = nodes[e.a]
                val b = nodes[e.b]
                if ((a.rel || b.rel) && hypot(a.px - b.px, a.py - b.py) > e.len * lay.br * 1.5f) {
                    e.broken = true
                    if (rnd.nextFloat() < .35f) {
                        sparks.add(
                            Spark(
                                (a.px + b.px) / 2f,
                                (a.py + b.py) / 2f,
                                (rnd.nextFloat() - .5f) * 260f,
                                -rnd.nextFloat() * 200f,
                                .3f + rnd.nextFloat() * .3f,
                                pal.warnHue + rnd.nextFloat() * 20f,
                            ),
                        )
                    }
                }
            }
            for (g in segs) if (tp > g.dl) {
                g.vy += 1000f * dt
                g.x += g.vx * dt
                g.y += g.vy * dt
                g.r += g.vr * dt
                if (g.y > lay.vh - 12f) {
                    g.y = lay.vh - 12f
                    g.vy *= -.3f
                    g.vx *= .7f
                    g.vr *= .5f
                }
            }
            if (tp < 1f && rnd.nextFloat() < .5f) {
                val n = nodes[rnd.nextInt(nodes.size)]
                sparks.add(Spark(n.px, n.py, (rnd.nextFloat() - .5f) * 300f, -rnd.nextFloat() * 250f, .3f + rnd.nextFloat() * .4f, 10f + rnd.nextFloat() * 25f))
            }
        }

        // the running neural network
        val live = phase == Phase.WAIT || phase == Phase.DONE
        if (phase == Phase.DONE && tp > .6f && rnd.nextFloat() < dt * 16f) {
            fire(rnd.nextInt(nodes.size), runH[rnd.nextInt(runH.size)], 3)
        }
        sigs.forEach { it.k += dt * it.sp }
        val hit = sigs.filter { it.k >= 1f }
        sigs.removeAll { it.k >= 1f || !live }
        if (live) hit.forEach { fire(it.b, it.h, it.d) }

        cards.forEach {
            it.f = min(1f, it.f + dt * 3f)
            it.fl *= exp(-3f * dt)
        }
        ring?.let {
            it.r += dt * 380f
            it.a -= dt * 1.1f
            if (it.a <= 0f) ring = null
        }
        ring2?.let {
            it.r += dt * 380f
            it.a -= dt * 1.1f
            if (it.a <= 0f) ring2 = null
        }
        cam = damp(cam, 0f, 4f, dt)
        flash = damp(flash, 0f, 5f, dt)
        for (s in shards) {
            s.life += dt
            s.vy += 1200f * dt
            s.vx *= exp(-.4f * dt)
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.r += s.vr * dt
            if (s.y > lay.vh - 12f) {
                s.y = lay.vh - 12f
                s.vy *= -.35f
                s.vx *= .8f
                s.vr *= .5f
            }
            if (s.x < 0f) {
                s.x = 0f
                s.vx *= -.5f
            }
            if (s.x > lay.vw) {
                s.x = lay.vw
                s.vx *= -.5f
            }
        }
        for (s in sparks) {
            s.life += dt
            s.vy += 900f * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
        }
        sparks.removeAll { it.life >= it.max }

        if (!settledCalled) {
            if (phase == Phase.DONE && tp > 1.2f) {
                settledCalled = true
                onSettled(true)
            }
            if (phase == Phase.DEAD && tp > 4.8f) {
                settledCalled = true
                onSettled(false)
            }
        }
    }

    // ---------------------------------------------------------------- drawing
    private fun DrawScope.txt(
        s: String,
        x: Float,
        y: Float,
        size: Float,
        color: Color,
        bold: Boolean = false,
        ls: Float = 0f,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        val p = if (bold) sansP else monoP
        p.textSize = size
        p.color = color.toArgb()
        p.textAlign = align
        p.letterSpacing = if (size > 0f) ls / size else 0f
        val ox = if (align == Paint.Align.CENTER) ls / 2f else 0f
        drawContext.canvas.nativeCanvas.drawText(s, x + ox, y, p)
    }

    private fun DrawScope.ch(
        c: Char,
        x: Float,
        y: Float,
        size: Float,
        color: Color,
    ) {
        monoP.textSize = size
        monoP.color = color.toArgb()
        monoP.textAlign = Paint.Align.LEFT
        monoP.letterSpacing = 0f
        buf[0] = c
        drawContext.canvas.nativeCanvas.drawText(buf, 0, 1, x, y, monoP)
    }

    fun draw(
        scope: DrawScope,
        @Suppress("UNUSED_PARAMETER") tick: Long,
    ) {
        with(scope) {
            val w = size.width
            val h = size.height
            lay = if (w / density >= 720f) LAY_WIDE else LAY_NARROW
            val sc = min(w / lay.vw, h / lay.vh)
            val ox = (w - lay.vw * sc) / 2f
            val oy = max(6f, (h - lay.vh * sc) / 2f)

            // The sim lives on a dark device screen; paint it so hosts without
            // a scrim of their own don't show light-on-light status ink.
            drawRect(SCREEN_BG)

            val shake = cam * 10f
            withTransform({
                translate(ox + (rnd.nextFloat() - .5f) * shake, oy + (rnd.nextFloat() - .5f) * shake)
                scale(sc, sc, Offset.Zero)
            }) {
                drawLinks()
                drawCards()
                drawBrain()
                ring?.let { r ->
                    val o = if (r.onFile) Offset(lay.fx, lay.fy) else Offset(lay.bx, lay.bcy)
                    drawCircle(hsl(r.hue, 80f, 60f, r.a), r.r, o, style = Stroke(2f))
                }
                ring2?.let { r ->
                    val o = if (r.onFile) Offset(lay.fx, lay.fy) else Offset(lay.bx, lay.bcy)
                    drawCircle(hsl(r.hue, 80f, 60f, r.a), r.r, o, style = Stroke(2f))
                }
                if (phase != Phase.DEAD) drawFileScene()
                drawChips()
                drawShards()
                drawStatus()
            }

            if (flash > .01f) drawRect(if (fail) hsl(pal.failHue, 87f, 55f, cl(flash * .4f)) else hsl(pal.okHue, 85f, 65f, cl(flash * .25f)))
        }
    }

    private fun DrawScope.drawFileScene() {
        val enter = eo(cl(t / .7f))
        val col = hsl(hue, 85f, 58f)
        val dy = (1f - enter) * -24f
        withTransform({ translate(0f, dy) }) {
            if (phase == Phase.GLITCH) {
                val a = cl(tp / 1.1f)
                val q = floor(t * 28f).toInt()
                val jx = (hashI(q, 1) % 100 / 100f - .5f) * 16f * a
                drawFile(lay.fx - 6f * a, lay.fy, hsl(pal.failHue, 90f, 55f, .7f), false)
                drawFile(lay.fx + 6f * a, lay.fy, hsl(pal.glitch2Hue, 90f, 60f, .7f), false)
                drawFile(lay.fx + jx, lay.fy, col, true)
                if (hashI(q, 3) % 3 == 0) {
                    drawRect(hsl(pal.failHue, 90f, 60f, .5f), Offset(lay.fx - FW / 2f - 10f, lay.fy - FH / 2f + hashI(q, 4) % FH.toInt()), Size(FW + 20f, 3f))
                }
            } else {
                drawFile(lay.fx, lay.fy, col, true)
            }
        }
    }

    private fun DrawScope.drawFile(
        fx: Float,
        fy: Float,
        col: Color,
        full: Boolean,
    ) {
        val path = filePath(pathA, fx, fy, FW, FH, FOLD)
        if (full) drawPath(path, Brush.verticalGradient(listOf(Color(0xFF111B2E), Color(0xFF09101C)), fy - FH / 2f, fy + FH / 2f))
        glowPath(path, col, 2f, if (full) 1f else .4f)
        if (!full) return

        val rowsTop = fy - FH / 2f + 34f
        val laserEnd =
            when {
                phase == Phase.WAIT -> N.toFloat()
                fail -> failRow + 1f
                else -> m.toFloat()
            }
        // WAIT drives the scan from the sweeping cursor; every real phase (READ,
        // GLITCH...) keeps linear prog so the laser always matches delivered rows.
        val scanRow = if (phase == Phase.WAIT) scanPos else prog
        val tick = floor(t * 14f).toInt()
        clipPath(path) {
            txt("client.log", fx - FW / 2f + 14f, fy - FH / 2f + 20f, 10f, hsl(hue, 70f, 75f, .85f), true, 1.5f)
            drawRect(Color(0x14FFFFFF), Offset(fx - FW / 2f, fy - FH / 2f + 28f), Size(FW, 1f))
            val ai = floor(scanRow).toInt()
            if (scanRow > 0f && scanRow < laserEnd && (phase == Phase.READ || phase == Phase.WAIT) && ai < N) {
                drawRect(
                    Brush.horizontalGradient(
                        0f to hsl(hue, 85f, 58f, 0f),
                        .3f to hsl(hue, 85f, 58f, .2f),
                        1f to hsl(hue, 85f, 58f, .04f),
                        startX = fx - FW / 2f,
                        endX = fx + FW / 2f,
                    ),
                    Offset(fx - FW / 2f, rowsTop + ai * RH),
                    Size(FW, RH),
                )
            }
            for (i in 0 until N) {
                val isF = fail && i == failRow && phase != Phase.WAIT
                val text =
                    when {
                        isF -> FAIL_FILL
                        i < m -> entries[i].text
                        else -> ""
                    }
                val len = (if (text.isNotEmpty()) text.length else 10 + hashI(i, 3) % 8).coerceAtMost(13)
                val catHue = if (!isF && i < m) entries[i].category.hue else hue
                var rp = if (phase == Phase.WAIT) 0f else cl(prog - i)
                if (isF) rp = min(rp, .55f)
                val waitActive = phase == Phase.WAIT && floor(scanRow).toInt() == i
                val y = rowsTop + i * RH + RH - 4f
                val x0 = fx - FW / 2f + 10f
                val glRow = phase == Phase.GLITCH && hashI(i, tick) % 5 == 0
                for (j in 0 until len) {
                    val f = j.toFloat() / len
                    val c: Char
                    var fill: Color
                    if (isF && prog >= failRow + .45f && f >= .5f) {
                        c = GARB[hashI(i * 30 + j, tick) % GARB.length]
                        fill = hsl(pal.failHue, 90f, 62f, .95f)
                    } else if (text.isNotEmpty() && (rp >= 1f || f < rp)) {
                        c = text[j]
                        fill = if (rp >= 1f) hsl(catHue, 75f, 74f, .95f) else Color.White
                    } else {
                        val live = rp > 0f || waitActive
                        c = if ((hashI(i * 30 + j, if (live) tick else 0) and 1) == 1) '1' else '0'
                        fill = if (live) hsl(catHue, 85f, 65f, .9f) else hsl(hue, 40f, 60f, .2f)
                    }
                    if (glRow) fill = hsl(pal.failHue, 90f, 62f, .8f)
                    ch(c, x0 + j * 6.1f, y, 10f, fill)
                }
            }
        }

        // folded corner
        val r = fx + FW / 2f
        val tt = fy - FH / 2f
        pathB.reset()
        pathB.moveTo(r - FOLD, tt)
        pathB.lineTo(r - FOLD, tt + FOLD)
        pathB.lineTo(r, tt + FOLD)
        pathB.close()
        drawPath(pathB, hsl(hue, 85f, 58f, .35f))
        drawPath(pathB, col, style = Stroke(1.5f))

        // laser
        if (scanRow > 0f && scanRow < laserEnd && phase != Phase.DONE) {
            val jit = if (phase == Phase.GLITCH) (rnd.nextFloat() - .5f) * 5f else 0f
            val ly = rowsTop + scanRow * RH + jit
            val fade = cl(scanRow * 3f) * cl((laserEnd - scanRow) * 3f)
            clipPath(filePath(pathA, fx, fy, FW, FH, FOLD)) {
                drawRect(
                    Brush.verticalGradient(listOf(hsl(hue, 90f, 60f, 0f), hsl(hue, 90f, 60f, .28f * fade)), ly - 26f, ly),
                    Offset(fx - FW / 2f, ly - 26f),
                    Size(FW, 26f),
                )
            }
            drawRect(hsl(hue, 90f, 70f, .18f * fade), Offset(fx - FW / 2f - 14f, ly - 5f), Size(FW + 28f, 10f))
            drawRect(hsl(hue, 90f, 82f, fade), Offset(fx - FW / 2f - 14f, ly - 1f), Size(FW + 28f, 2f))
            pathB.reset()
            pathB.moveTo(fx - FW / 2f - 14f, ly - 6f)
            pathB.lineTo(fx - FW / 2f - 4f, ly)
            pathB.lineTo(fx - FW / 2f - 14f, ly + 6f)
            pathB.close()
            drawPath(pathB, hsl(hue, 90f, 82f, fade))
        }
    }

    private fun DrawScope.drawLinks() {
        if (phase != Phase.DONE || tp < .6f) return
        val al = cl((tp - .6f) / .8f)
        for ((i, cd) in lay.cards.withIndex()) {
            val ex = if (lay.wide) cd.x else cd.x + cd.w / 2f
            val ey = if (lay.wide) cd.y + cd.h / 2f else cd.y
            val dx = ex - lay.bx
            val dy = ey - lay.bcy
            val d = hypot(dx, dy)
            val sx = lay.bx + dx / d * lay.br * 1.02f
            val sy = lay.bcy + dy / d * lay.br * .95f
            val mx = (sx + ex) / 2f
            val my = (sy + ey) / 2f - if (lay.wide) 24f else 0f
            val hh = LogCategory.values()[i].hue
            pathC.reset()
            pathC.moveTo(sx, sy)
            pathC.quadraticBezierTo(mx, my, ex, ey)
            drawPath(
                pathC,
                hsl(hh, 80f, 62f, .4f * al),
                style = Stroke(1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f), -t * 34f)),
            )
            val k = (t * .5f + i * .19f) % 1f
            val q = 1f - k
            val qx = q * q * sx + 2f * q * k * mx + k * k * ex
            val qy = q * q * sy + 2f * q * k * my + k * k * ey
            halo(qx, qy, 12f, hsl(hh, 90f, 62f, .5f * al))
            drawCircle(hsl(hh, 90f, 75f, al), 2.6f, Offset(qx, qy))
        }
    }

    private fun DrawScope.drawCards() {
        for ((i, cd) in lay.cards.withIndex()) {
            val cat = LogCategory.values()[i]
            val st = cards[i]
            val on = st.cnt > 0
            val bad = phase == Phase.DEAD && on
            val hh = if (bad) pal.failHue else cat.hue
            val pr = if (phase == Phase.DONE) .2f + .2f * sin(t * 3f + i * 1.1f) else 0f
            val tl = Offset(cd.x, cd.y)
            val sz = Size(cd.w, cd.h)
            if (on) drawRoundRect(hsl(hh, 85f, 60f, .10f + .25f * st.fl + pr * .4f), Offset(cd.x - 3f, cd.y - 3f), Size(cd.w + 6f, cd.h + 6f), CornerRadius(12f))
            drawRoundRect(Color(0xFF0A111D), tl, sz, CornerRadius(9f))
            drawRoundRect(if (on) hsl(hh, 85f, 60f, .85f) else Color(0x1AFFFFFF), tl, sz, CornerRadius(9f), style = Stroke(1.2f))
            txt(cat.label, cd.x + 12f, cd.y + 16f, 9f, hsl(hh, 75f, 70f, if (on) .95f else .4f), true, 2f)
            val value: String = if (!on || st.v.isEmpty()) "—" else st.v
            val vc = if (!on) Color(0x4DBECDE6) else (if (bad) Color(1f, .6f, .6f, eo(st.f)) else Color(.91f, .945f, 1f, eo(st.f)))
            txt(value, cd.x + 12f, cd.y + cd.h - 9f, 12f, vc)
            if (bad) txt("LOST", cd.x + cd.w - 12f, cd.y + 16f, 9f, hsl(pal.failHue, 80f, 65f, .8f), true, 2f, Paint.Align.RIGHT)
        }
    }

    private fun DrawScope.drawBrain() {
        val R = lay.br * br
        val dead = phase == Phase.DEAD
        val run = phase == Phase.DONE && tp > .6f
        val fl = dead && tp < 1.6f
        val jt = if (phase == Phase.GLITCH) 4f else if (run) 3f else 0f
        val fade = if (dead) cl(1f - (tp - 4.6f) / 1.4f) else 1f

        fun c(
            h: Float,
            s: Float,
            l: Float,
            a: Float,
        ) = hsl(h, s, l, a * fade)

        for (i in nodes.indices) {
            val n = nodes[i]
            if (n.fall) {
                px[i] = n.px
                py[i] = n.py
            } else if (run) {
                px[i] = lay.bx + n.u * R + sin(t * 1.3f + i) * jt
                py[i] = lay.bcy + n.v * R + cos(t * 1.1f + i * 1.7f) * jt
            } else {
                px[i] = lay.bx + n.u * R + (rnd.nextFloat() - .5f) * jt
                py[i] = lay.bcy + n.v * R + (rnd.nextFloat() - .5f) * jt
            }
        }

        if (!dead) {
            // Airy body — fill only, no drawn outline: the spiral stroke itself
            // carries the silhouette (outline+fill was what made it look solid).
            pathC.reset()
            for (i in 0..72) {
                val a = i / 72f * 6.2832f
                val x = lay.bx + R * 1.06f * cos(a)
                val y = lay.bcy + R * .9f * sin(a)
                if (i == 0) pathC.moveTo(x, y) else pathC.lineTo(x, y)
            }
            pathC.close()
            drawPath(pathC, c(hue, 85f, 58f, .05f + if (run) .02f * sin(t * 4f) else 0f))

            // Spiral spine: neurons ride this faint Archimedean guide.
            pathB.reset()
            val turns = 2.2f
            val steps = 220
            val startAng = Math.PI.toFloat() + .35f
            for (k in 0..steps) {
                val f = k / steps.toFloat()
                val ang = startAng + f * 6.2832f * turns
                val r = lerp(.22f, 1.08f, f * f * (3f - 2f * f)) * R // smoothstep: dense hub, airy rim
                val x = lay.bx + cos(ang) * 1.18f * r
                val y = lay.bcy + sin(ang) * .9f * r
                if (k == 0) pathB.moveTo(x, y) else pathB.lineTo(x, y)
            }
            glowPath(pathB, c(hue, 80f, 62f, .34f), 1.2f, if (run) 1f + .35f * sin(t * 4f) else .8f)

            // Rotating-square spiral shell (the hypnosis look): square outlines,
            // each rotated a fixed step more than the last while shrinking toward
            // the hub — their corners trace the swirl, overlapping into the moire
            // feel of the reference art. Outer squares are brightest; while the
            // network runs the whole dial slowly turns.
            val LEVELS = 20
            val drift = if (run) t * .16f else 0f
            for (q in 0 until LEVELS) {
                val f = q / (LEVELS - 1).toFloat()
                val e = f * f * (3f - 2f * f)
                val half = lerp(1.05f, .14f, e) * R
                val rot = startAng + q * .3f + drift
                pathB.reset()
                for (cnr in 0..3) {
                    val a = rot + cnr * 1.5707964f
                    val x = lay.bx + cos(a) * half
                    val y = lay.bcy + sin(a) * half
                    if (cnr == 0) pathB.moveTo(x, y) else pathB.lineTo(x, y)
                }
                pathB.close()
                drawPath(pathB, c(hue, 80f, 62f, .09f + .2f * (1f - e)), style = Stroke(1f))
            }
        } else {
            for (g in segs) {
                val a = cl(1f - (tp - 3.6f) / 1.2f)
                withTransform({
                    translate(g.x, g.y)
                    rotate(g.r * 57.29578f, Offset.Zero)
                }) {
                    pathC.reset()
                    for (k in 0 until 4) if (k == 0) pathC.moveTo(g.lx[k], g.ly[k]) else pathC.lineTo(g.lx[k], g.ly[k])
                    glowPath(pathC, hsl(pal.failHue, 88f, 58f, .95f * a), 2f, .8f)
                }
            }
        }

        for (e in edges) {
            if (e.broken) continue
            val a = nodes[e.a]
            val b = nodes[e.b]
            val al = .07f + .6f * max(a.e, b.e) + .25f * min(a.k, b.k) + if (run) .1f else 0f
            drawLine(c(if (a.e > b.e) a.h else b.h, 85f, 62f, al), Offset(px[e.a], py[e.a]), Offset(px[e.b], py[e.b]), 1f)
        }
        for (i in nodes.indices) {
            val n = nodes[i]
            val f = if ((fl && rnd.nextFloat() < .45f) || (dead && tp < n.dl && rnd.nextFloat() < .3f)) .25f else 1f
            val a = (.3f + .7f * max(n.e, n.k * .6f)) * f
            if (n.e > .25f) halo(px[i], py[i], 16f * n.e + 5f, c(n.h, 90f, 60f, .5f * n.e))
            drawCircle(c(n.h, 85f, 62f + n.e * 20f, a), 2.4f + 3.6f * n.e + n.k, Offset(px[i], py[i]))
        }
        for (s in sigs) {
            val ax = px[s.a]
            val ay = py[s.a]
            val bx = px[s.b]
            val byy = py[s.b]
            val x = lerp(ax, bx, s.k)
            val y = lerp(ay, byy, s.k)
            val k0 = max(0f, s.k - .22f)
            drawLine(hsl(s.h, 90f, 65f, .55f), Offset(lerp(ax, bx, k0), lerp(ay, byy, k0)), Offset(x, y), 1.6f, blendMode = BlendMode.Plus)
            halo(x, y, 9f, hsl(s.h, 95f, 60f, .5f), BlendMode.Plus)
            drawCircle(hsl(s.h, 95f, 80f), 2.2f, Offset(x, y), blendMode = BlendMode.Plus)
        }
        txt(
            if (dead) "OFFLINE" else "SMART BRAIN",
            lay.bx,
            lay.bcy + lay.br * 1.02f + 16f,
            10f,
            if (dead) hsl(pal.failHue, 70f, 65f, .8f * fade) else hsl(hue, 70f, 72f, .8f),
            true,
            3f,
            Paint.Align.CENTER,
        )
    }

    private fun DrawScope.drawChips() {
        for (o in chips) {
            val k = eio(cl(o.k))
            val p = chipPos(o.i, k)
            val en = entries[o.i]
            val w = en.text.length * 6f + 14f
            val a = 1f - k.pow(8)
            val hh = en.category.hue
            drawRoundRect(hsl(hh, 85f, 62f, .15f * a), Offset(p.x - w / 2f - 3f, p.y - 12f), Size(w + 6f, 22f), CornerRadius(11f))
            drawRoundRect(hsl(hh, 85f, 58f, .25f * a), Offset(p.x - w / 2f, p.y - 9f), Size(w, 16f), CornerRadius(8f))
            drawRoundRect(hsl(hh, 85f, 62f, a), Offset(p.x - w / 2f, p.y - 9f), Size(w, 16f), CornerRadius(8f), style = Stroke(1f))
            txt(en.text, p.x - w / 2f + 7f, p.y + 3f, 10f, Color(1f, 1f, 1f, a))
        }
    }

    private fun DrawScope.drawShards() {
        for (s in shards) {
            val a = cl(1f - (s.life - 3.4f) / 1.2f)
            withTransform({
                translate(s.x, s.y)
                rotate(s.r * 57.29578f, Offset.Zero)
            }) {
                filePath(pathA, 0f, 0f, s.s, s.s * 1.3f, s.s * .28f)
                drawPath(pathA, Color(0xFF180809).copy(alpha = a))
                glowPath(pathA, hsl(pal.failHue, 88f, 58f, a), 1.6f, .6f)
                txt(s.ch, 0f, s.s * .15f, s.s * .42f, hsl(pal.failHue, 88f, 66f, .9f * a), false, 0f, Paint.Align.CENTER)
            }
        }
        for (s in sparks) {
            val a = 1f - s.life / s.max
            drawLine(hsl(s.hue, 100f, 60f, a), Offset(s.x, s.y), Offset(s.x - s.vx * .025f, s.y - s.vy * .025f), 1.6f, blendMode = BlendMode.Plus)
        }
    }

    private fun DrawScope.drawStatus() {
        val sx = lay.sx
        val sy = lay.sy
        val dead = phase == Phase.DEAD
        val failPct = (prog / N * 100f).roundToInt().coerceIn(0, 100)
        val okPct = (prog / max(m, 1) * 100f).roundToInt().coerceIn(0, 100)
        val pct: Int
        val lb: String
        val sub: String
        when (phase) {
            Phase.WAIT -> {
                // Show the number the device actually reported; unknown stays 0.
                val rp = waitProgress
                pct = rp?.coerceIn(0, 100) ?: 0
                lb = "ANALYZING client.log"
                sub =
                    if (rp != null && rp > 0) {
                        "pulling encrypted logs from your device"
                    } else {
                        "scanning for device, GPU, graphics API and cvars"
                    }
            }
            Phase.READ -> {
                pct = if (fail) failPct else okPct
                lb = "READING client.log"
                sub = "line ${min(max(m, 1), ceil(prog).toInt())} / ${max(m, 1)}  ·  parsing UE4 cvars, device, graphics API"
            }
            Phase.GLITCH -> {
                pct = failPct
                lb = "READ ERROR · UNEXPECTED BYTES"
                sub = "client.log is corrupted"
            }
            Phase.DONE -> {
                pct = 100
                lb = if (tp < 1.2f) "BRAIN SYNCED" else "NEURAL NETWORK ACTIVE"
                // Only real data. The old hardcoded fiction ("tuning r.ScreenPercentage
                // for GPU", "selecting Vulkan render path"...) never matched the actual
                // device; what shows now is the actual SmartBrain recommendation.
                val rec = recommendation
                val detail =
                    when {
                        tp < 1.2f -> "$m records learned"
                        rec != null ->
                            buildString {
                                append("recommended ")
                                append(rec.preset)
                                append(" · score ")
                                append(rec.score)
                                // A real warning outranks a supporting signal.
                                val note = rec.warnings.firstOrNull() ?: rec.signals.firstOrNull()
                                note?.let { sig ->
                                    append(" · ")
                                    append(sig)
                                }
                            }
                        else -> "$m records learned"
                    }
                sub = "$detail  ·  $fires spikes"
            }
            Phase.DEAD -> {
                pct = failPct
                lb = "READ FAILED · NETWORK DOWN"
                sub = "$reason  ·  $m / $N lines parsed  ·  brain lost sync"
            }
        }
        val da = if (dead) eo(cl((tp - .3f) / .6f)) else 1f
        val col = hsl(hue, 85f, 58f, da)
        txt(lb, sx, sy, 11f, hsl(hue, 70f, 72f, .95f * da), true, 3f, Paint.Align.CENTER)
        // Percent shows whenever it is a real number: during the read, or in WAIT
        // once the device started reporting (previously WAIT hid the pct entirely).
        if (phase != Phase.WAIT || pct > 0) txt("$pct%", sx, sy + 36f, 30f, Color(.95f, .965f, 1f, da), true, 1f, Paint.Align.CENTER)
        val barY = sy + 50f
        drawRoundRect(Color(0x14FFFFFF), Offset(sx - 100f, barY), Size(200f, 4f), CornerRadius(2f))
        if (phase == Phase.WAIT) {
            val seg = ((t * .9f) % 1f) * 260f - 60f
            clipRect(sx - 100f, barY, sx + 100f, barY + 4f) {
                drawRoundRect(col, Offset(sx - 100f + seg, barY), Size(60f, 4f), CornerRadius(2f))
            }
        } else {
            val bw = max(4f, 200f * pct / 100f)
            drawRoundRect(col.copy(alpha = .25f * da), Offset(sx - 100f, barY - 3f), Size(bw, 10f), CornerRadius(5f))
            drawRoundRect(col, Offset(sx - 100f, barY), Size(bw, 4f), CornerRadius(2f))
        }
        txt(sub, sx, sy + 74f, 10f, Color(.745f, .804f, .9f, .55f * da), false, 1f, Paint.Align.CENTER)
    }
}
