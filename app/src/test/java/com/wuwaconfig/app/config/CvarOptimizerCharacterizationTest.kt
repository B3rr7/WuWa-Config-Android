package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.LogInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization tests for [CvarOptimizer.optimizeProfile] (cognitive 145 — the
 * worst function in the app). These pin the *exact* output for representative
 * inputs so a future refactor (flattening the when-tables, extracting helpers)
 * cannot silently change a number that lands in a user's Engine.ini.
 *
 * They are deliberately exhaustive over the tier × RAM × stress branches that
 * the monotonicity sweeps in [CvarOptimizerTest] do not pin to a value. If a
 * refactor changes any expected value here, that is a behaviour change and must
 * be reviewed — not "fixed" by updating the golden.
 *
 * Regenerate a golden only after confirming the new value is correct by hand.
 */
class CvarOptimizerCharacterizationTest {
    private fun info(
        gpu: String?,
        ramMb: Int?,
        thermal: Int = 0,
        oom: Int = 0,
        texture: Int = 0,
    ) = LogInfo(gpu = gpu, ramMb = ramMb, thermalEvents = thermal, gpuOom = oom, textureErrors = texture)

    private fun opt(
        gpu: String?,
        ramMb: Int?,
        thermal: Int = 0,
        oom: Int = 0,
        texture: Int = 0,
    ) = CvarOptimizer.optimizeProfile(info(gpu, ramMb, thermal, oom, texture))

    // Representative GPU strings per tier (mirrors CvarOptimizerTest).
    private val flagship = "Adreno 830"
    private val high = "Adreno 750"
    private val midHigh = "Adreno 720"
    private val mid = "Adreno 618"
    private val midLow = "Adreno 512"
    private val low = "Adreno 306"

    @Test
    fun `flagship 16GB clean`() =
        assertEquals(
            CvarOptimizer.OptimizedProfile(
                screen = 100, shadow = 5, shadowRes = 2048, ssr = 4, mipbias = 0,
                streaming = 4.0, vd = 3.0, flod = 3.0, detail = 7, lod_bias = 0, grasscull = 30000,
            ),
            opt(flagship, 16000),
        )

    @Test
    fun `flagship 8GB constrained (thermal)`() =
        assertEquals(
            CvarOptimizer.OptimizedProfile(
                screen = 80, shadow = 2, shadowRes = 1024, ssr = 4, mipbias = 0,
                streaming = 4.0, vd = 3.0, flod = 3.0, detail = 2, lod_bias = 0, grasscull = 30000,
            ),
            opt(flagship, 8000, thermal = 3),
        )

    @Test
    fun `flagship 4GB hard-limited (oom)`() =
        assertEquals(
            CvarOptimizer.OptimizedProfile(
                screen = 50, shadow = 0, shadowRes = 256, ssr = 0, mipbias = 3,
                streaming = 0.3, vd = 0.3, flod = 0.4, detail = 0, lod_bias = 5, grasscull = 1500,
            ),
            opt(flagship, 4000, oom = 1),
        )

    @Test
    fun `high 8GB clean`() =
        assertEquals(
            CvarOptimizer.OptimizedProfile(
                screen = 100, shadow = 4, shadowRes = 2048, ssr = 2, mipbias = 0,
                streaming = 3.0, vd = 2.0, flod = 2.5, detail = 5, lod_bias = 0, grasscull = 20000,
            ),
            opt(high, 8000),
        )

    @Test
    fun `mid_high 8GB clean`() =
        assertEquals(
            CvarOptimizer.OptimizedProfile(
                screen = 80, shadow = 2, shadowRes = 1024, ssr = 0, mipbias = 0,
                streaming = 2.0, vd = 1.5, flod = 2.0, detail = 2, lod_bias = 0, grasscull = 15000,
            ),
            opt(midHigh, 8000),
        )

    @Test
    fun `mid 8GB clean`() =
        assertEquals(
            CvarOptimizer.OptimizedProfile(
                screen = 80, shadow = 2, shadowRes = 1024, ssr = 0, mipbias = 0,
                streaming = 2.0, vd = 0.5, flod = 0.6, detail = 1, lod_bias = 0, grasscull = 15000,
            ),
            opt(mid, 8000),
        )

    @Test
    fun `mid_low 4GB constrained (ram)`() =
        assertEquals(
            CvarOptimizer.OptimizedProfile(
                screen = 60, shadow = 0, shadowRes = 256, ssr = 0, mipbias = 3,
                streaming = 1.0, vd = 0.5, flod = 0.6, detail = 0, lod_bias = 3, grasscull = 4500,
            ),
            opt(midLow, 4000),
        )

    @Test
    fun `low 4GB clean`() =
        assertEquals(
            CvarOptimizer.OptimizedProfile(
                screen = 60, shadow = 0, shadowRes = 256, ssr = 0, mipbias = 3,
                streaming = 1.0, vd = 0.5, flod = 0.6, detail = 0, lod_bias = 3, grasscull = 4500,
            ),
            opt(low, 4000),
        )

    @Test
    fun `unknown gpu 16GB clean`() =
        assertEquals(
            CvarOptimizer.OptimizedProfile(
                screen = 60, shadow = 0, shadowRes = 256, ssr = 0, mipbias = 0,
                streaming = 2.0, vd = 0.5, flod = 0.6, detail = 0, lod_bias = 3, grasscull = 4500,
            ),
            opt(null, 16000),
        )

    @Test
    fun `unknown gpu 4GB hard-limited (thermal on low tier)`() =
        assertEquals(
            CvarOptimizer.OptimizedProfile(
                screen = 50, shadow = 0, shadowRes = 256, ssr = 0, mipbias = 3,
                streaming = 0.3, vd = 0.3, flod = 0.4, detail = 0, lod_bias = 5, grasscull = 1500,
            ),
            opt(null, 4000, thermal = 3),
        )
}
