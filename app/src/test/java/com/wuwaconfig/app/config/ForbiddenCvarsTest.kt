package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
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

    // ── line-count preservation ──
    // stripForbiddenCvars used to build its output with appendLine() per RETAINED line,
    // so every invocation appended a trailing newline. Called 5x per deploy (once per
    // generated INI, and again on any re-strip) the line count crept upward, and
    // ConfigGenerator's "stripped N" arithmetic (in.lines().size - out.lines().size)
    // could go NEGATIVE. The output must contain exactly one line per retained input line.

    /** Newline-terminated INI text — the shape every ConfigGenerator builder emits. */
    private fun ini(vararg lines: String): String = lines.joinToString("\n") + "\n"

    private val sampleIni =
        ini(
            "[SystemSettings]",
            "r.ShadowQuality=3",
            "r.Streaming.Boost=1",
            "r.BloomQuality=4",
            "r.ScreenPercentage=100",
            "r.FramePace=60",
            "",
        )

    @Test
    fun `stripForbiddenCvars removes exactly the forbidden lines and nothing else`() {
        val (stripped, removed) = ForbiddenCvars.stripForbiddenCvarsWithReport(sampleIni)

        // Two forbidden keys were actually removed, so exactly two lines must disappear.
        assertEquals(listOf("r.Streaming.Boost", "r.ScreenPercentage"), removed)
        assertEquals(
            "strip must remove exactly one line per removed CVar, not add or drop any",
            sampleIni.lines().size - removed.size,
            stripped.lines().size,
        )
    }

    @Test
    fun `stripForbiddenCvars preserves the line count when nothing is forbidden`() {
        val input = ini("[SystemSettings]", "r.ShadowQuality=3", "r.BloomQuality=4")
        val stripped = ForbiddenCvars.stripForbiddenCvars(input)
        assertEquals(input.lines().size, stripped.lines().size)
    }

    @Test
    fun `stripForbiddenCvars line count does not drift over repeated invocations`() {
        // The original symptom was 5 invocations inflating the count. Re-stripping an
        // already-stripped INI must be a no-op, so all 5 passes agree.
        val first = ForbiddenCvars.stripForbiddenCvars(sampleIni)
        var current = first
        repeat(4) {
            current = ForbiddenCvars.stripForbiddenCvars(current)
            assertEquals("re-stripping must not change the line count", first.lines().size, current.lines().size)
        }
        assertEquals(first, current)
    }

    @Test
    fun `stripForbiddenCvars is idempotent`() {
        val input =
            ini(
                "[SystemSettings]",
                "; keep this comment",
                "r.ShadowQuality=3",
                "r.Streaming.Boost=1",
                "r.DetailMode=1",
                "# and this one",
                "r.FramePace=60",
            )

        val once = ForbiddenCvars.stripForbiddenCvars(input)
        val twice = ForbiddenCvars.stripForbiddenCvars(once)
        assertEquals(once, twice)
        assertEquals(input.lines().size - 2, once.lines().size)
    }

    @Test
    fun `stripForbiddenCvars does not report a removal for a CVars directive line`() {
        // "+CVars=r.Streaming.Boost=1" carries a forbidden key, but stripping it must count
        // as exactly ONE removed line, so the caller's arithmetic stays honest.
        val input = ini("[ConsoleVariables]", "+CVars=r.Streaming.Boost=1", "r.ShadowQuality=3")
        val (stripped, removed) = ForbiddenCvars.stripForbiddenCvarsWithReport(input)
        assertEquals(listOf("r.Streaming.Boost"), removed)
        assertEquals(input.lines().size - 1, stripped.lines().size)
    }
}
