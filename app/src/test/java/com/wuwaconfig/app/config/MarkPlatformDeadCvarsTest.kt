package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.LogInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkPlatformDeadCvarsTest {
    private fun mark(
        text: String,
        platform: TargetPlatform = TargetPlatform.ANDROID_GLES,
    ): String = markPlatformDeadCvars(text, platform, "Engine.ini")

    @Test
    fun `a dead CVar is commented out with the marker, not deleted`() {
        val input = "[SystemSettings]\nr.Kuro.GlobalLightQuality_PC=3\nr.ShadowQuality=2"
        val out = mark(input)

        // The value is still present — nothing was removed.
        assertTrue("the original text must survive", out.contains("r.Kuro.GlobalLightQuality_PC=3"))
        // It is now a UE4 comment, so the engine ignores it.
        assertTrue("the line must be commented out", out.contains(";r.Kuro.GlobalLightQuality_PC=3"))
        // With the reason and the scope.
        assertTrue(out.contains(DEAD_CVAR_MARKER))
        assertTrue(out.contains("PC-only variant"))
        // The live CVar is untouched.
        assertTrue("a live CVar must not be touched", out.contains("\nr.ShadowQuality=2"))
        assertFalse(out.contains(";r.ShadowQuality=2"))
    }

    @Test
    fun `a marked line is a valid UE4 comment so the engine ignores it`() {
        val out = mark("[SystemSettings]\nr.Metal.OptLevel=3")
        val line = out.lines().first { it.contains("r.Metal.OptLevel") }
        assertTrue("must start with ';' for UE4 to treat it as a comment", line.trimStart().startsWith(";"))
    }

    @Test
    fun `uncommenting restores the original line exactly`() {
        // The whole point of marking rather than deleting: one character removed
        // puts the CVar back.
        val input = "[SystemSettings]\nr.Kuro.GlobalLightQuality_PC=3"
        val marked = mark(input).lines().first { it.contains("r.Kuro") }
        val restored = marked.trim().removePrefix(";").substringBefore(" ; [")
        assertEquals("r.Kuro.GlobalLightQuality_PC=3", restored)
    }

    @Test
    fun `section headers and the Core-System path lines are never touched`() {
        val input =
            """
            [Core.System]
            Paths=../../../Engine/Plugins
            Paths=../../../Engine/Content
            [SystemSettings]
            r.Metal.OptLevel=3
            """.trimIndent()
        val out = mark(input)
        assertTrue(out.contains("[Core.System]"))
        assertTrue(out.contains("Paths=../../../Engine/Plugins"))
        assertTrue(out.contains("Paths=../../../Engine/Content"))
        assertTrue(out.contains("[SystemSettings]"))
    }

    @Test
    fun `indentation is preserved so nested +CVars lines stay aligned`() {
        val out = mark("[MyDevice DeviceProfile]\n    +CVars=r.Metal.OptLevel=1")
        assertTrue("leading whitespace must survive", out.contains("    ;+CVars=r.Metal.OptLevel=1"))
    }

    @Test
    fun `the +CVars directive form is marked too`() {
        // DeviceProfiles.ini writes CVars as +CVars=r.X=1 inside a DeviceProfile
        // section. If the directive were not stripped before classification, the
        // "key" would be "+CVars" and the CVar would never be matched.
        val out = mark("[MyDevice DeviceProfile]\n+CVars=r.Metal.OptLevel=1")
        assertTrue("the +CVars form must be marked", out.contains(";+CVars=r.Metal.OptLevel=1"))
    }

    @Test
    fun `an already-commented line is not marked twice`() {
        val once = mark("[SystemSettings]\n;r.Metal.OptLevel=3")
        val twice = mark(once)
        assertEquals(once, twice)
        assertFalse(twice.contains(DEAD_CVAR_MARKER + "] " + DEAD_CVAR_MARKER))
    }

    @Test
    fun `UNKNOWN platform marks nothing`() {
        val input = "[SystemSettings]\nr.Kuro.GlobalLightQuality_PC=3\nr.Metal.OptLevel=3"
        assertEquals(input, mark(input, TargetPlatform.UNKNOWN))
    }

    @Test
    fun `re-evaluating after the platform changes revives the cvars`() {
        // The user's requirement: marks are re-checked on every generate, and a
        // CVar found not dead is used again. Switching the game to Vulkan makes the
        // whole r.Vulkan family live again, with no stored state to clear.
        val input = "[SystemSettings]\nr.Vulkan.SSR=1\nr.Metal.OptLevel=1"

        val onGles = mark(input, TargetPlatform.ANDROID_GLES)
        assertTrue("Vulkan CVar dead on GLES", onGles.contains(";r.Vulkan.SSR=1"))
        assertTrue("Metal CVar dead on GLES", onGles.contains(";r.Metal.OptLevel=1"))

        // Second generate, same input text, platform now Vulkan.
        val onVulkan = mark(input, TargetPlatform.ANDROID_VULKAN)
        assertTrue("Vulkan CVar must be live again", onVulkan.contains("\nr.Vulkan.SSR=1"))
        assertFalse("Vulkan CVar must not be marked", onVulkan.contains(";r.Vulkan.SSR=1"))
        assertTrue("Metal CVar stays dead on Vulkan", onVulkan.contains(";r.Metal.OptLevel=1"))
    }

    @Test
    fun `accumulators report per-file counts and reasons`() {
        val counts = mutableMapOf<String, Int>()
        val reasons = mutableSetOf<String>()
        markPlatformDeadCvars(
            "[SystemSettings]\nr.Metal.OptLevel=1\nr.D3D.ForceDXC=1\nr.ShadowQuality=2",
            TargetPlatform.ANDROID_GLES,
            "Engine.ini",
            counts,
            reasons,
        )
        markPlatformDeadCvars("[X]\nr.Metal.OptLevel=1", TargetPlatform.ANDROID_GLES, "Scalability.ini", counts, reasons)

        assertEquals(2, counts["Engine.ini"])
        assertEquals(1, counts["Scalability.ini"])
        assertEquals(3, counts.values.sum())
        assertTrue(reasons.contains("Metal-only"))
        assertTrue(reasons.contains("DirectX-only"))
    }

    @Test
    fun `the summary states the mode so marks are never ambiguous`() {
        val unknown = DeadCvarPassResult(TargetPlatform.UNKNOWN, emptyMap(), 0, emptySet())
        assertTrue(unknown.summary().contains("UNKNOWN"))
        assertTrue(unknown.summary().contains("no CVar marked dead"))

        val none = DeadCvarPassResult(TargetPlatform.ANDROID_GLES, emptyMap(), 0, emptySet())
        assertTrue(none.summary().contains("no CVar marked dead"))

        val some =
            DeadCvarPassResult(
                TargetPlatform.ANDROID_GLES,
                mapOf("Engine.ini" to 2),
                2,
                setOf("PC-only variant"),
            )
        assertTrue(some.summary().contains("ANDROID_GLES"))
        assertTrue(some.summary().contains("Engine.ini:2"))
        assertTrue(some.summary().contains("PC-only variant"))
    }

    @Test
    fun `line count is unchanged so the file does not grow`() {
        val input = "[SystemSettings]\nr.Metal.OptLevel=1\nr.D3D.ForceDXC=1\nr.ShadowQuality=2"
        assertEquals(input.lines().size, mark(input).lines().size)
    }

    /**
     * End-to-end against the real profile from a saved device report
     * (moto g60, Android 16, OpenGL ES, Vulkan not_available), using the CVar set the
     * engine actually reported as unrecognised. Pins the two behaviours that matter:
     * the five rejected ones go inert, and the verified-good neighbours stay live.
     */
    @Test
    fun `end to end on the reported device profile`() {
        val log =
            LogInfo(
                gpu = "Adreno (TM) 618",
                deviceModel = "motorola(moto g(60))",
                ramMb = 5120,
                androidVersion = "16",
                resolution = "2456x1080",
                api = "OpenGL ES",
                vulkanStatus = "not_available",
            )
        val platform = detectPlatform(log)
        assertEquals(TargetPlatform.ANDROID_GLES, platform)

        val ini =
            """
            [SystemSettings]
            r.Kuro.GlobalLightQuality_PC=3
            r.Kuro.GlobalLightShadowQuality_PC=2
            r.TemporalAA.Algorithm=1
            r.TemporalAA.Upsampling=1
            r.TemporalAACatmullRom=1
            r.TemporalAA.Sharpness=0
            r.PSO.CompilationMode=0
            r.PSO.CacheEvictScheme=1
            r.Mobile.AllowHZBOcclusion=1
            r.Vulkan.SSR=0
            r.ShadowQuality=2
            """.trimIndent()

        val counts = mutableMapOf<String, Int>()
        val reasons = mutableSetOf<String>()
        val out = markPlatformDeadCvars(ini, platform, "Engine.ini", counts, reasons)

        // The five the engine rejected are now inert.
        for (
        dead in
        listOf(
            "r.Kuro.GlobalLightQuality_PC",
            "r.Kuro.GlobalLightShadowQuality_PC",
            "r.TemporalAA.Algorithm",
            "r.TemporalAA.Upsampling",
            "r.TemporalAACatmullRom",
        )
        ) {
            val line = out.lines().first { it.contains(dead) }
            assertTrue("$dead must be commented out: $line", line.trimStart().startsWith(";"))
        }
        // ... and the Vulkan one, because this device is on OpenGL ES.
        assertTrue(out.lines().first { it.contains("r.Vulkan.SSR") }.trimStart().startsWith(";"))

        // Verified-good neighbours untouched.
        for (
        live in
        listOf(
            "r.TemporalAA.Sharpness",
            "r.PSO.CompilationMode",
            "r.PSO.CacheEvictScheme",
            "r.Mobile.AllowHZBOcclusion",
            "r.ShadowQuality",
        )
        ) {
            val line = out.lines().first { it.contains(live) }
            assertFalse("$live must stay live: $line", line.trimStart().startsWith(";"))
        }

        assertEquals(6, counts["Engine.ini"])
        assertEquals(ini.lines().size, out.lines().size)
        // Dead CVars leave the verify denominator instead of inflating it.
        assertEquals(5, extractCvarNames(out).size)

        // Next generate, the user turns Vulkan on in the game: the Vulkan CVar comes
        // back with no stored state to clear, and the PC/UE5 marks stay.
        val vulkan = markPlatformDeadCvars(ini, detectPlatform(log.copy(api = "Vulkan")), "Engine.ini")
        assertTrue("Vulkan CVar must be live again", vulkan.lines().first { it.contains("r.Vulkan.SSR") }.startsWith("r.Vulkan.SSR"))
        assertTrue("PC mark must persist", vulkan.lines().first { it.contains("GlobalLightQuality_PC") }.trimStart().startsWith(";"))
        // 6 live on Vulkan: the 5 from before plus the revived r.Vulkan.SSR. The
        // verify denominator therefore GROWS when the platform improves, which is
        // the visible signal that previously-dead CVars are back in play.
        assertEquals(6, extractCvarNames(vulkan).size)
    }
}
