package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.DeployComparison
import com.wuwaconfig.app.model.LogInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `CvarOptimizer.optimizeProfile` is a 145-cognitive-complexity decision table —
 * the worst function in the app by the graph's metric — and until now only
 * `getGPUTier` and `adjustProfile` had any coverage at all. It runs whenever
 * `useAdvancedGen` is on, so it decides the numbers that land in a user's
 * Engine.ini.
 *
 * Two properties are worth more than any single expected value, and both are
 * asserted as sweeps rather than table rows:
 *
 *  - **Monotonicity in RAM.** More RAM must never produce a *lower* setting.
 *  - **Monotonicity in stress.** More thermal events / OOM / texture errors must
 *    never produce a *higher* setting.
 *
 * Those catch the class of bug where one field's ladder disagrees with another's,
 * which is exactly how this function got its complexity in the first place.
 */
class CvarOptimizerTest {
    private fun info(
        gpu: String? = "Adreno 830",
        ramMb: Int? = 16000,
        thermalEvents: Int = 0,
        gpuOom: Int = 0,
        textureErrors: Int = 0,
    ) = LogInfo(
        gpu = gpu,
        ramMb = ramMb,
        thermalEvents = thermalEvents,
        gpuOom = gpuOom,
        textureErrors = textureErrors,
    )

    // Representative GPU string per tier produced by GPU_TIER_PATTERNS.
    private val flagship = "Adreno 830"
    private val high = "Adreno 750"
    private val midHigh = "Adreno 720"
    private val mid = "Adreno 618"
    private val midLow = "Adreno 512"
    private val low = "Adreno 306"
    private val unknownGpu: String? = null

    private fun optimize(
        gpu: String?,
        ramMb: Int?,
        thermal: Int = 0,
        oom: Int = 0,
        texture: Int = 0,
    ) = CvarOptimizer.optimizeProfile(info(gpu, ramMb, thermal, oom, texture))

    private fun tierOf(gpu: String?) = CvarOptimizer.getGPUTier(gpu)

    // ── getGPUTier ──

    @Test
    fun `getGPUTier hoisted patterns map known gpus`() {
        assertEquals("flagship", CvarOptimizer.getGPUTier("Adreno 830"))
        assertEquals("high", CvarOptimizer.getGPUTier("Adreno 750"))
        assertEquals("mid", CvarOptimizer.getGPUTier("Adreno 618"))
        assertEquals("unknown", CvarOptimizer.getGPUTier(null))
    }

    @Test
    fun `getGPUTier covers every tier the decision table branches on`() {
        // optimizeProfile branches on all six; if a tier stopped being reachable,
        // half the `when` branches would be dead and this would fail.
        assertEquals("flagship", tierOf(flagship))
        assertEquals("high", tierOf(high))
        assertEquals("mid_high", tierOf(midHigh))
        assertEquals("mid", tierOf(mid))
        assertEquals("mid_low", tierOf(midLow))
        assertEquals("low", tierOf(low))
        assertEquals("unknown", tierOf(unknownGpu))
    }

    @Test
    fun `getGPUTier is case insensitive`() {
        assertEquals("flagship", CvarOptimizer.getGPUTier("ADRENO 830"))
        assertEquals("high", CvarOptimizer.getGPUTier("adreno 750"))
    }

    // ── clean baseline: every tier, no stress, ample RAM ──

    @Test
    fun `a healthy flagship is maxed`() {
        val p = optimize(flagship, 16000)
        assertEquals(100, p.screen)
        assertEquals(5, p.shadow)
        assertEquals(2048, p.shadowRes)
        assertEquals(4, p.ssr)
        assertEquals(0, p.mipbias)
        assertEquals(4.0, p.streaming, 1e-9)
        assertEquals(3.0, p.vd, 1e-9)
        assertEquals(3.0, p.flod, 1e-9)
        // 16 GB crosses the >=12000 band, so a flagship tops out at detail 7.
        assertEquals(7, p.detail)
        assertEquals(0, p.lod_bias)
        assertEquals(30000, p.grasscull)
    }

    @Test
    fun `a healthy high tier lands one step below flagship`() {
        val p = optimize(high, 16000)
        assertEquals(100, p.screen)
        assertEquals(4, p.shadow)
        assertEquals(2048, p.shadowRes)
        assertEquals(2, p.ssr)
        assertEquals(3.0, p.streaming, 1e-9)
        assertEquals(5, p.detail)
        assertEquals(20000, p.grasscull)
    }

    @Test
    fun `lower tiers scale down screen, shadow and detail`() {
        assertEquals(80, optimize(midHigh, 16000).screen)
        assertEquals(2, optimize(midHigh, 16000).shadow)
        assertEquals(2, optimize(midHigh, 16000).detail)

        assertEquals(80, optimize(mid, 16000).screen)
        assertEquals(2, optimize(mid, 16000).shadow)
        assertEquals(1, optimize(mid, 16000).detail)
    }

    @Test
    fun `low and mid_low get the minimal screen and no shadows`() {
        for (gpu in listOf(midLow, low, unknownGpu)) {
            val p = optimize(gpu, 16000)
            assertEquals("gpu=$gpu", 60, p.screen)
            assertEquals("gpu=$gpu", 0, p.shadow)
            assertEquals("gpu=$gpu", 0, p.ssr)
            assertEquals("gpu=$gpu", 0, p.detail)
        }
    }

    @Test
    fun `shadowRes follows the shadow rank ladder`() {
        assertEquals(2048, optimize(flagship, 16000).shadowRes) // shadow 5
        assertEquals(2048, optimize(high, 16000).shadowRes) // shadow 4
        assertEquals(1024, optimize(midHigh, 16000).shadowRes) // shadow 2
        assertEquals(256, optimize(low, 16000).shadowRes) // shadow 0
    }

    // ── stress thresholds, at the boundary ──

    @Test
    fun `one thermal event is not yet thermal`() {
        // The gate is >= 3, so 2 must behave exactly like 0.
        assertEquals(optimize(flagship, 16000), optimize(flagship, 16000, thermal = 2))
    }

    @Test
    fun `three thermal events cross the gate and hard-limit a low tier`() {
        val clean = optimize(low, 16000)
        val hot = optimize(low, 16000, thermal = 3)
        assertTrue("thermal must reduce screen on a low tier", hot.screen < clean.screen)
        assertEquals("hard-limited floors the screen", 50, hot.screen)
        assertEquals(0, hot.shadow)
        assertEquals(5, hot.lod_bias)
    }

    @Test
    fun `thermal only constrains a mid tier rather than hard-limiting it`() {
        // isHardLimited fires for thermal only on mid_low/low/unknown, so `mid`
        // stays capable. Pinning both halves: the tier gate is the whole point of
        // the isHardLimited expression.
        val hot = optimize(mid, 16000, thermal = 3)
        assertEquals("constrained, not the hard-limited floor", 60, hot.screen)
        assertEquals(0, hot.detail)

        // Exactly three tiers hard-limit on thermal; mid_high is NOT one of them,
        // which is easy to misread as an oversight since it sits between "mid" and
        // the two low tiers in the ladder.
        for (gpu in listOf(midLow, low, unknownGpu)) {
            assertEquals("gpu=$gpu must hard-limit", 50, optimize(gpu, 16000, thermal = 3).screen)
        }
        // A constrained flagship still gets 80 (the `isConstrained && flagship`
        // branch); every other constrained tier falls to 60.
        assertEquals(80, optimize(flagship, 16000, thermal = 3).screen)
        for (gpu in listOf(high, midHigh, mid)) {
            assertEquals("gpu=$gpu is only constrained", 60, optimize(gpu, 16000, thermal = 3).screen)
        }
    }

    @Test
    fun `a single GPU OOM is enough to hard-limit`() {
        val oom = optimize(flagship, 16000, oom = 1)
        assertEquals(50, oom.screen)
        assertEquals(0, oom.shadow)
        assertEquals(0, oom.ssr)
        assertEquals(0, oom.detail)
    }

    @Test
    fun `five texture errors cross the gate`() {
        assertEquals(optimize(mid, 16000), optimize(mid, 16000, texture = 4))
        val stressed = optimize(mid, 16000, texture = 5)
        assertEquals("constrained (not hard-limited) on a mid tier", 60, stressed.screen)
        assertEquals(0, stressed.detail)
    }

    @Test
    fun `texture errors alone do not hard-limit even a flagship`() {
        // Texture errors mean "constrained", not "the GPU is dying": a flagship
        // keeps a 80 screen and 2 shadows rather than collapsing to the floor.
        val p = optimize(flagship, 16000, texture = 50)
        assertEquals(80, p.screen)
        assertEquals(2, p.shadow)
    }

    @Test
    fun `thermal on a flagship constrains rather than hard-limits`() {
        // isHardLimited only fires for thermal on mid_low/low/unknown, so a hot
        // flagship stays capable and is asked to cool.
        val p = optimize(flagship, 16000, thermal = 10)
        assertEquals(80, p.screen)
        assertEquals(2, p.shadow)
        assertTrue("not the hard-limited floor", p.screen > 50)
    }

    @Test
    fun `the documented hard-limited floor is exactly this`() {
        // The values recorded in CvarOptimizer's own comment as the floor the
        // degrade ladder must not go below.
        val floor =
            CvarOptimizer.OptimizedProfile(
                screen = 50, shadow = 0, shadowRes = 256, ssr = 0, mipbias = 3,
                streaming = 0.3, vd = 0.3, flod = 0.4, detail = 0, lod_bias = 5, grasscull = 1500,
            )
        // An OOM on a low tier produces precisely the floor.
        assertEquals(floor, optimize(low, 16000, oom = 1))
    }

    @Test
    fun `the floor is reachable from any low tier via OOM`() {
        val floor = optimize(low, 16000, oom = 1)
        assertEquals(floor, optimize(midLow, 16000, oom = 1))
        assertEquals(floor, optimize(unknownGpu, 16000, oom = 1))
    }

    // ── RAM boundaries ──

    @Test
    fun `streaming steps at each RAM band`() {
        assertEquals(0.5, optimize(flagship, 3999).streaming, 1e-9)
        assertEquals(1.0, optimize(flagship, 4000).streaming, 1e-9)
        assertEquals(2.0, optimize(flagship, 6000).streaming, 1e-9)
        assertEquals(2.0, optimize(flagship, 7999).streaming, 1e-9)
        assertEquals(4.0, optimize(flagship, 8000).streaming, 1e-9)
    }

    @Test
    fun `a null RAM reading is treated as 4 GB`() {
        // The default exists because a log without PhysicalMemoryMB is common;
        // 4096 lands in the 4000-6000 band, so it must not be treated as ample.
        val p = optimize(flagship, null)
        assertEquals(1.0, p.streaming, 1e-9)
        assertEquals(3, p.mipbias)
    }

    @Test
    fun `mipbias is 3 below 6 GB and 0 at or above`() {
        assertEquals(3, optimize(flagship, 3999).mipbias)
        assertEquals(3, optimize(flagship, 5999).mipbias)
        assertEquals(0, optimize(flagship, 6000).mipbias)
        assertEquals(0, optimize(flagship, 16000).mipbias)
    }

    @Test
    fun `detail scales with RAM on the two upper tiers`() {
        assertEquals(5, optimize(flagship, 7999).detail)
        assertEquals(6, optimize(flagship, 8000).detail)
        assertEquals(7, optimize(flagship, 12000).detail)
        assertEquals(4, optimize(high, 7999).detail)
        assertEquals(5, optimize(high, 8000).detail)
    }

    @Test
    fun `ssr needs 8 GB on the upper tiers`() {
        assertEquals(1, optimize(flagship, 7999).ssr)
        assertEquals(4, optimize(flagship, 8000).ssr)
        assertEquals(1, optimize(high, 7999).ssr)
        assertEquals(2, optimize(high, 8000).ssr)
    }

    @Test
    fun `low RAM alone constrains a mid tier without hard-limiting it`() {
        val p = optimize(mid, 4000)
        assertEquals("constrained, not hard-limited", 60, p.screen)
        assertEquals(0, p.detail)
    }

    // ── detail / lod_bias / grasscull coupling ──

    @Test
    fun `lod_bias is 5 when hard-limited and 3 at detail 0`() {
        assertEquals(5, optimize(low, 16000, oom = 1).lod_bias)
        // detail 0 without hard-limitation: a low-tier device with ample RAM.
        assertEquals(3, optimize(low, 16000).lod_bias)
        assertEquals(0, optimize(flagship, 16000).lod_bias)
    }

    @Test
    fun `grasscull follows the tier once detail is above zero`() {
        assertEquals(30000, optimize(flagship, 16000).grasscull)
        assertEquals(20000, optimize(high, 16000).grasscull)
        assertEquals(15000, optimize(midHigh, 16000).grasscull)
        assertEquals(15000, optimize(mid, 16000).grasscull)
    }

    @Test
    fun `grasscull drops to 4500 at detail 0 ahead of the tier`() {
        // detail==0 outranks the tier ladder, so even a flagship that is merely
        // constrained gets the small cull distance rather than 30000.
        val p = optimize(flagship, 8000, texture = 5)
        assertEquals(2, p.detail)
        assertEquals(30000, p.grasscull)

        val zero = optimize(mid, 16000, texture = 5)
        assertEquals(0, zero.detail)
        assertEquals(4500, zero.grasscull)
    }

    // ── monotonicity sweeps ──

    @Test
    fun `more RAM never lowers any field`() {
        val gpus = listOf(flagship, high, midHigh, mid, midLow, low, unknownGpu)
        val rams = listOf(2048, 4096, 5000, 6000, 8000, 12000, 16000, 24000)
        for (gpu in gpus) {
            for (ram in rams.drop(1)) {
                val lower = optimize(gpu, rams[rams.indexOf(ram) - 1])
                val higher = optimize(gpu, ram)
                val ctx = "gpu=$gpu ${lower.streaming}->$higher.streaming ram=$ram"
                assertTrue("$ctx screen", higher.screen >= lower.screen)
                assertTrue("$ctx shadow", higher.shadow >= lower.shadow)
                assertTrue("$ctx shadowRes", higher.shadowRes >= lower.shadowRes)
                assertTrue("$ctx ssr", higher.ssr >= lower.ssr)
                assertTrue("$ctx streaming", higher.streaming >= lower.streaming)
                assertTrue("$ctx vd", higher.vd >= lower.vd)
                assertTrue("$ctx flod", higher.flod >= lower.flod)
                assertTrue("$ctx detail", higher.detail >= lower.detail)
                assertTrue("$ctx grasscull", higher.grasscull >= lower.grasscull)
                // mipbias and lod_bias are inverted scales (higher = cheaper), so
                // more RAM may only lower them.
                assertTrue("$ctx mipbias", higher.mipbias <= lower.mipbias)
                assertTrue("$ctx lod_bias", higher.lod_bias <= lower.lod_bias)
            }
        }
    }

    @Test
    fun `more stress never raises any field`() {
        val gpus = listOf(flagship, high, midHigh, mid, midLow, low, unknownGpu)
        // Coordinate-wise non-decreasing: (thermal, oom, texture). An earlier
        // draft stepped one axis at a time in both directions, so it compared
        // (thermal=3, texture=0) against (thermal=0, texture=5) — less total
        // stress, not more — and the sweep failed on its own premise.
        val stressors =
            listOf<Triple<Int, Int, Int>>(
                Triple(0, 0, 0),
                Triple(0, 0, 5),
                Triple(0, 0, 20),
                Triple(3, 0, 20),
                Triple(10, 0, 20),
                Triple(10, 1, 20),
                Triple(10, 3, 20),
            )
        for (gpu in gpus) {
            for (i in stressors.indices.drop(1)) {
                val (t0, o0, x0) = stressors[i - 1]
                val (t1, o1, x1) = stressors[i]
                val lower = optimize(gpu, 8000, thermal = t0, oom = o0, texture = x0)
                val higher = optimize(gpu, 8000, thermal = t1, oom = o1, texture = x1)
                val ctx = "gpu=$gpu ($t0,$o0,$x0)->($t1,$o1,$x1)"
                assertTrue("$ctx screen", higher.screen <= lower.screen)
                assertTrue("$ctx shadow", higher.shadow <= lower.shadow)
                assertTrue("$ctx ssr", higher.ssr <= lower.ssr)
                assertTrue("$ctx streaming", higher.streaming <= lower.streaming)
                assertTrue("$ctx detail", higher.detail <= lower.detail)
                assertTrue("$ctx grasscull", higher.grasscull <= lower.grasscull)
                // Inverted scales may only rise.
                assertTrue("$ctx mipbias", higher.mipbias >= lower.mipbias)
                assertTrue("$ctx lod_bias", higher.lod_bias >= lower.lod_bias)
            }
        }
    }

    @Test
    fun `every tier and every RAM band yields a profile in the expected ranges`() {
        // A cheap invariant sweep: nothing may be negative, and the fields the
        // INI builders index into must stay inside their ladder.
        for (gpu in listOf(flagship, high, midHigh, mid, midLow, low, unknownGpu)) {
            for (ram in listOf(2048, 4096, 6000, 8000, 12000, 24000)) {
                for (stress in listOf(0, 5, 12)) {
                    val p = optimize(gpu, ram, thermal = stress, oom = stress / 6)
                    val ctx = "gpu=$gpu ram=$ram stress=$stress -> $p"
                    assertTrue("$ctx screen", p.screen in listOf(50, 60, 80, 100))
                    assertTrue("$ctx shadow", p.shadow in 0..5)
                    assertTrue("$ctx shadowRes", p.shadowRes in listOf(256, 512, 1024, 2048))
                    assertTrue("$ctx ssr", p.ssr in 0..4)
                    assertTrue("$ctx mipbias", p.mipbias in listOf(0, 3))
                    assertTrue("$ctx streaming", p.streaming > 0.0)
                    assertTrue("$ctx vd", p.vd > 0.0)
                    assertTrue("$ctx flod", p.flod > 0.0)
                    assertTrue("$ctx detail", p.detail in 0..7)
                    assertTrue("$ctx lod_bias", p.lod_bias in listOf(0, 3, 5))
                    assertTrue("$ctx grasscull", p.grasscull > 0)
                    assertNotNull(p)
                }
            }
        }
    }

    @Test
    fun `shadowRes is always derived from the shadow rank`() {
        // shadowRes is computed from shadow rather than independently; a drift
        // between the two would emit a shadow map size the profile does not
        // support.
        for (gpu in listOf(flagship, high, midHigh, mid, midLow, low, unknownGpu)) {
            for (ram in listOf(2048, 6000, 16000)) {
                for (stress in listOf(0, 8)) {
                    val p = optimize(gpu, ram, thermal = stress, oom = stress / 5)
                    val expected =
                        when {
                            p.shadow >= 4 -> 2048
                            p.shadow >= 2 -> 1024
                            p.shadow >= 1 -> 512
                            else -> 256
                        }
                    assertEquals("gpu=$gpu ram=$ram stress=$stress", expected, p.shadowRes)
                }
            }
        }
    }

    // ── toPresetProfile: the bridge into generated INIs ──

    @Test
    fun `toPresetProfile derives the fine detail fields from detail`() {
        val opt =
            CvarOptimizer.OptimizedProfile(
                screen = 100, shadow = 5, shadowRes = 2048, ssr = 4, mipbias = 0,
                streaming = 4.0, vd = 3.0, flod = 3.0, detail = 7, lod_bias = 0, grasscull = 30000,
            )
        val p = CvarOptimizer.toPresetProfile(opt)
        assertEquals(3, p.characterDetail)
        assertEquals(3, p.postProcess)
        assertEquals(3, p.cutsceneQuality)
        assertTrue("static lighting follows the shadow rank", p.staticLighting)
    }

    @Test
    fun `toPresetProfile does not leave the expensive fields at their defaults when detail is 0`() {
        // This is the bug the comment at CvarOptimizer:59 records: leaving
        // characterDetail/postProcess at PresetProfile's default of 2 while
        // detail=0 and shadow=0 emitted the weakest path with the most expensive
        // settings alongside it.
        val opt =
            CvarOptimizer.OptimizedProfile(
                screen = 50, shadow = 0, shadowRes = 256, ssr = 0, mipbias = 3,
                streaming = 0.3, vd = 0.3, flod = 0.4, detail = 0, lod_bias = 5, grasscull = 1500,
            )
        val p = CvarOptimizer.toPresetProfile(opt)
        assertEquals(0, p.characterDetail)
        assertEquals(0, p.postProcess)
        assertEquals(0, p.cutsceneQuality)
        assertTrue("no static lighting on a shadowless profile", !p.staticLighting)
    }

    @Test
    fun `toPresetProfile copies the optimised fields through unchanged`() {
        val opt =
            CvarOptimizer.OptimizedProfile(
                screen = 80, shadow = 2, shadowRes = 1024, ssr = 1, mipbias = 0,
                streaming = 2.0, vd = 1.5, flod = 2.0, detail = 2, lod_bias = 0, grasscull = 15000,
            )
        val p = CvarOptimizer.toPresetProfile(opt)
        assertEquals(80, p.screen)
        assertEquals(2, p.shadow)
        assertEquals(1024, p.shadowRes)
        assertEquals(1, p.ssr)
        assertEquals(0, p.mipbias)
        assertEquals(2.0, p.streaming, 1e-9)
        assertEquals(1.5, p.vd, 1e-9)
        assertEquals(2.0, p.flod, 1e-9)
        assertEquals(2, p.detail)
        assertEquals(0, p.lod_bias)
        assertEquals(15000, p.grasscull)
    }

    // ── adjustProfile (pre-existing coverage, kept alongside) ──

    @Test
    fun `degraded profile scales shadowRes by new shadow rank`() {
        val current =
            CvarOptimizer.OptimizedProfile(
                screen = 100, shadow = 5, shadowRes = 4096, ssr = 4, mipbias = 0,
                streaming = 6.0, vd = 4.0, flod = 4.0, detail = 4, lod_bias = 0, grasscull = 40000,
            )
        val degraded = DeployComparison(fpsDelta = -10f, thermalDelta = 0, oomDelta = 0, dropFramesDelta = 0)
        val out = CvarOptimizer.adjustProfile(current, degraded)
        // shadow drops 5 -> 3, so shadowRes follows the 1024 ladder instead of being
        // forced to 256 (the old bug that collapsed high-res shadows on degradation).
        assertEquals(3, out.shadow)
        assertEquals(1024, out.shadowRes)
        assertEquals(3, out.detail)
    }

    @Test
    fun `degraded profile on lowest shadow keeps small shadowRes`() {
        val current =
            CvarOptimizer.OptimizedProfile(
                screen = 60, shadow = 1, shadowRes = 512, ssr = 0, mipbias = 3,
                streaming = 0.3, vd = 0.3, flod = 0.4, detail = 1, lod_bias = 3, grasscull = 1500,
            )
        val degraded = DeployComparison(fpsDelta = -20f, thermalDelta = 5, oomDelta = 0, dropFramesDelta = 10)
        val out = CvarOptimizer.adjustProfile(current, degraded)
        assertEquals(0, out.shadow)
        assertEquals(128, out.shadowRes)
    }
}
