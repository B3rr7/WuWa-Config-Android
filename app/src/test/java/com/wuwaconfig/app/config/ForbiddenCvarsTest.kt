package com.wuwaconfig.app.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForbiddenCvarsTest {
    @Test
    fun `all known forbidden CVars are detected`() {
        for (cvar in ForbiddenCvars.ALL) {
            assertTrue("$cvar should be forbidden", ForbiddenCvars.isForbidden(cvar))
        }
    }

    @Test
    fun `variants with plus and minus prefix are detected`() {
        assertTrue(ForbiddenCvars.isForbidden("+r.Streaming.Boost"))
        assertTrue(ForbiddenCvars.isForbidden("-r.Streaming.Boost"))
        assertTrue(ForbiddenCvars.isForbidden("+r.ScreenPercentage"))
        assertTrue(ForbiddenCvars.isForbidden("-r.ScreenPercentage"))
    }

    @Test
    fun `common CVars are not forbidden`() {
        assertFalse(ForbiddenCvars.isForbidden("r.ShadowQuality"))
        assertFalse(ForbiddenCvars.isForbidden("r.MobileMSAA"))
        assertFalse(ForbiddenCvars.isForbidden("r.FramePace"))
        assertFalse(ForbiddenCvars.isForbidden("sg.ResolutionQuality"))
        assertFalse(ForbiddenCvars.isForbidden("r.BloomQuality"))
        assertFalse(ForbiddenCvars.isForbidden("r.TemporalAA.Upsampling"))
        assertFalse(ForbiddenCvars.isForbidden("r.PostProcessAAQuality"))
        assertFalse(ForbiddenCvars.isForbidden("r.VSync"))
    }

    @Test
    fun `forbidden CVars with r_ prefix variant`() {
        assertTrue(ForbiddenCvars.isForbidden("r.ScreenPercentage"))
        assertTrue(ForbiddenCvars.isForbidden("r.ViewDistanceScale"))
    }

    @Test
    fun `stripForbiddenCvars removes forbidden lines`() {
        val input =
            """
            [SystemSettings]
            r.ShadowQuality=3
            r.Streaming.Boost=1
            r.BloomQuality=4
            r.ScreenPercentage=100
            r.FramePace=60
            """.trimIndent()

        val result = ForbiddenCvars.stripForbiddenCvars(input)
        assertTrue(result.contains("r.ShadowQuality=3"))
        assertTrue(result.contains("r.BloomQuality=4"))
        assertTrue(result.contains("r.FramePace=60"))
        assertFalse(result.contains("r.Streaming.Boost"))
        assertFalse(result.contains("r.ScreenPercentage"))
    }

    @Test
    fun `stripForbiddenCvars preserves comments and sections`() {
        val input =
            """
            [SystemSettings]
            ; This is a comment
            r.ShadowQuality=3
            r.Streaming.Boost=1
            # Another comment
            """.trimIndent()

        val result = ForbiddenCvars.stripForbiddenCvars(input)
        assertTrue(result.contains("[SystemSettings]"))
        assertTrue(result.contains("; This is a comment"))
        assertTrue(result.contains("# Another comment"))
        assertTrue(result.contains("r.ShadowQuality=3"))
        assertFalse(result.contains("r.Streaming.Boost"))
    }

    @Test
    fun `non forbidden CVars with similar names are not blocked`() {
        assertFalse(ForbiddenCvars.isForbidden("r.Streaming.MipBias"))
        assertFalse(ForbiddenCvars.isForbidden("r.Streaming.PoolSizeForMeshes"))
        assertFalse(ForbiddenCvars.isForbidden("r.DetailMode2"))
    }
}
