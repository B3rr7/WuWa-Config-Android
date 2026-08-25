package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.DeployComparison
import org.junit.Assert.assertEquals
import org.junit.Test

class CvarOptimizerTest {
    @Test
    fun `getGPUTier hoisted patterns map known gpus`() {
        assertEquals("flagship", CvarOptimizer.getGPUTier("Adreno 830"))
        assertEquals("high", CvarOptimizer.getGPUTier("Adreno 750"))
        assertEquals("mid", CvarOptimizer.getGPUTier("Adreno 618"))
        assertEquals("unknown", CvarOptimizer.getGPUTier(null))
    }

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
