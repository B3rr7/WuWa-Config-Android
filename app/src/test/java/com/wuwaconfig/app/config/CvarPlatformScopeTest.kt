package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.LogInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The platform-availability checker marks a CVar dead only when a rule's scope
 * PROVABLY excludes the detected platform. These tests pin both directions: that
 * the genuine platform families get marked, and — more importantly — that the
 * large valid families do NOT.
 */
class CvarPlatformScopeTest {
    // ── Platform detection ────────────────────────────────────────────────

    @Test
    fun `android plus OpenGL ES resolves to ANDROID_GLES`() {
        assertEquals(
            TargetPlatform.ANDROID_GLES,
            detectPlatform(LogInfo(androidVersion = "16", api = "OpenGL ES")),
        )
    }

    @Test
    fun `android plus Vulkan resolves to ANDROID_VULKAN`() {
        assertEquals(
            TargetPlatform.ANDROID_VULKAN,
            detectPlatform(LogInfo(androidVersion = "16", api = "Vulkan")),
        )
    }

    @Test
    fun `Vulkan without an android version is desktop Linux, not Android`() {
        // Both platforms report Vulkan, so androidVersion is the only discriminator.
        assertEquals(
            TargetPlatform.LINUX_VULKAN,
            detectPlatform(LogInfo(androidVersion = null, api = "Vulkan")),
        )
    }

    @Test
    fun `DirectX and Metal resolve to desktop targets`() {
        assertEquals(TargetPlatform.WINDOWS, detectPlatform(LogInfo(api = "DirectX")))
        assertEquals(TargetPlatform.APPLE, detectPlatform(LogInfo(api = "Metal")))
    }

    @Test
    fun `no log yields UNKNOWN and marks nothing`() {
        // This is the real generate-without-log path: ConfigGenScreen passes
        // `logInfo ?: LogInfo()`.
        assertEquals(TargetPlatform.UNKNOWN, detectPlatform(LogInfo()))
        assertEquals(CvarVerdict.Alive, classifyCvar("r.Kuro.GlobalLightQuality_PC", TargetPlatform.UNKNOWN))
        assertEquals(CvarVerdict.Alive, classifyCvar("r.Metal.OptLevel", TargetPlatform.UNKNOWN))
    }

    @Test
    fun `gameApi is used when api is absent`() {
        assertEquals(
            TargetPlatform.ANDROID_VULKAN,
            detectPlatform(LogInfo(androidVersion = "16", gameApi = "Vulkan")),
        )
    }

    // ── The regression that motivated exact tokens ────────────────────────

    @Test
    fun `PSO cvars are NOT PlayStation and must stay alive on Android`() {
        // r.PSO.* is Pipeline State Objects, which works on Vulkan. The generator
        // really does emit two of these (r.PSO.CompilationMode, r.PSO.CacheEvictScheme).
        // A loose `r.PS` prefix rule would have marked them dead.
        for (
        name in
        listOf(
            "r.PSO.CompilationMode",
            "r.PSO.CacheEvictScheme",
            "r.PSO.FixPSOCrash1",
            "r.PSO.BackgroundPreompilingRTPSO",
        )
        ) {
            assertEquals("$name must stay alive", CvarVerdict.Alive, classifyCvar(name, TargetPlatform.ANDROID_GLES))
            assertEquals("$name must stay alive on Vulkan", CvarVerdict.Alive, classifyCvar(name, TargetPlatform.ANDROID_VULKAN))
        }
    }

    @Test
    fun `the PS4 CVar IS PlayStation and must die on Android`() {
        assertTrue(classifyCvar("r.PS4MixedModeShaderDebugInfo", TargetPlatform.ANDROID_GLES) is CvarVerdict.Dead)
    }

    // ── The two big families that must never be flagged ───────────────────

    @Test
    fun `all 250 mobile cvars stay alive on Android`() {
        // The largest family in the corpus. A "looks platform specific" heuristic
        // would have flagged every one of them.
        val mobile = readCorpus().filter { it.lowercase().startsWith("r.mobile") }
        assertTrue("expected ~250 r.Mobile names, found ${mobile.size}", mobile.size >= 200)
        val wronglyMarked = mobile.filter { classifyCvar(it, TargetPlatform.ANDROID_GLES) is CvarVerdict.Dead }
        assertTrue("no r.Mobile CVar may be marked dead, but got ${wronglyMarked.take(5)}", wronglyMarked.isEmpty())
    }

    @Test
    fun `vulkan cvars are alive on Vulkan and dead on OpenGL ES`() {
        // This is the two-dimensional case that motivates the whole model: the same
        // CVar flips verdict purely on the graphics API.
        for (name in listOf("r.Vulkan.SSR", "r.Vulkan.AmdGpuLevel", "r.Vulkan.OcclusionCulling")) {
            assertEquals("$name on Vulkan", CvarVerdict.Alive, classifyCvar(name, TargetPlatform.ANDROID_VULKAN))
            assertTrue("$name on GLES", classifyCvar(name, TargetPlatform.ANDROID_GLES) is CvarVerdict.Dead)
        }
    }

    @Test
    fun `opengl cvars are alive on GLES and dead on Vulkan`() {
        for (name in listOf("r.OpenGL.ForceGCM", "r.OpenGL.StrictZNearEqual")) {
            assertEquals("$name on GLES", CvarVerdict.Alive, classifyCvar(name, TargetPlatform.ANDROID_GLES))
            assertTrue("$name on Vulkan", classifyCvar(name, TargetPlatform.ANDROID_VULKAN) is CvarVerdict.Dead)
        }
    }

    // ── The three the engine actually reported as unrecognised ───────────

    @Test
    fun `the PC-only Kuro CVars are marked dead on Android`() {
        // Both appear in the engine's own "not recognised" list from a real deploy.
        for (name in listOf("r.Kuro.GlobalLightQuality_PC", "r.Kuro.GlobalLightShadowQuality_PC")) {
            val verdict = classifyCvar(name, TargetPlatform.ANDROID_GLES)
            assertTrue("$name should be dead, got $verdict", verdict is CvarVerdict.Dead)
            assertEquals(PlatformScope.PC, (verdict as CvarVerdict.Dead).scope)
        }
    }

    @Test
    fun `the three UE5-only TAA CVars are marked dead`() {
        for (
        name in
        listOf("r.TemporalAA.Algorithm", "r.TemporalAA.Upsampling", "r.TemporalAACatmullRom")
        ) {
            assertTrue("$name should be dead", classifyCvar(name, TargetPlatform.ANDROID_GLES) is CvarVerdict.Dead)
        }
    }

    @Test
    fun `legitimate UE4 TAA CVars stay alive`() {
        // Guards against prefix-matching the whole r.TemporalAA. family: these are
        // real UE4 CVars and marking them would have cost ~9 working lines.
        for (
        name in
        listOf(
            "r.TemporalAA.Sharpness",
            "r.TemporalAA.MobileFrameWeight",
            "r.TemporalAA.MobileStaticFrameWeight",
            "r.TemporalAAPauseCorrect",
            "r.TemporalAAFilterSize",
            "r.TemporalAACurrentFrameWeight",
        )
        ) {
            assertEquals("$name must stay alive", CvarVerdict.Alive, classifyCvar(name, TargetPlatform.ANDROID_GLES))
        }
    }

    @Test
    fun `Apple-only and DirectX-only names are marked dead on Android`() {
        for (name in listOf("r.Metal.OptLevel", "r.metal.earlyzpassonlymaterialmasking", "r.D3D.ForceDXC")) {
            assertTrue("$name should be dead on Android", classifyCvar(name, TargetPlatform.ANDROID_GLES) is CvarVerdict.Dead)
        }
    }

    // ── Corpus-wide safety: no false positives at scale ───────────────────

    @Test
    fun `the checker never marks a name the corpus has no rule for`() {
        // Sweeps all 5,368 real names and asserts every verdict it produces is
        // explainable by a rule, so a future edit to the table cannot silently start
        // flagging arbitrary names.
        //
        // readCorpus() drops `;`-prefixed lines, so the CVars that 3.7.0 no longer
        // registers — retained commented-out in the asset rather than deleted — are
        // correctly absent here.
        val corpus = readCorpus()
        assertTrue("corpus should be ~5,372 active names, found ${corpus.size}", corpus.size > 5000)
        val marked = corpus.filter { classifyCvar(it, TargetPlatform.ANDROID_GLES) is CvarVerdict.Dead }
        for (name in marked) {
            val lower = name.lowercase()
            val explainable =
                lower.endsWith("_pc") ||
                    lower.startsWith("r.d3d") ||
                    lower.startsWith("r.metal") ||
                    lower.startsWith("r.vulkan") ||
                    lower.startsWith("r.ps4") ||
                    lower.startsWith("r.ps5") ||
                    lower.startsWith("r.xsx") ||
                    lower in setOf("r.temporalaa.upsampling", "r.temporalaa.algorithm", "r.temporalaacatmullrom")
            assertTrue("$name was marked dead but matches no rule", explainable)
        }
        // Sanity: the rule set should light up a meaningful but small slice.
        // The floor was 90 when the corpus was the full 5,889-name 3.6.1 set; commenting
        // out CVars that 3.7.0 no longer registers removed 21 platform-scoped names (18
        // r.vulkan.*, 4 r.temporalaa*, 2 r.metal.*), leaving 89. Kept at 85 rather than
        // pinned to 89 so the next version bump does not fail this for the same reason —
        // a genuinely broken rule table collapses the count to ~0, which 85 still catches.
        assertTrue("expected a non-trivial number of dead names, got ${marked.size}", marked.size >= 85)
        assertTrue("expected fewer than 200 dead names, got ${marked.size}", marked.size < 200)
    }

    @Test
    fun `every corpus name is alive on an UNKNOWN target`() {
        for (name in readCorpus()) {
            if (classifyCvar(name, TargetPlatform.UNKNOWN) !is CvarVerdict.Alive) {
                throw AssertionError("$name must not be judged without a platform")
            }
        }
    }

    private fun readCorpus(): List<String> = realActiveCorpusLines()

    // ── engine generation ──

    @Test
    fun `banner UE4 maps to UE4 and UE5 to UE5`() {
        assertEquals(EngineGeneration.UE4, EngineGeneration.fromBanner("UE4+Release-4.27-CL-1234"))
        assertEquals(EngineGeneration.UE5, EngineGeneration.fromBanner("UE5+5.3-0"))
    }

    @Test
    fun `an absent or unrecognisable banner is UNKNOWN`() {
        // UNKNOWN is the common case: the banner appears once at startup, so any
        // rotated or tail-truncated log lacks it.
        assertEquals(EngineGeneration.UNKNOWN, EngineGeneration.fromBanner(null))
        assertEquals(EngineGeneration.UNKNOWN, EngineGeneration.fromBanner(""))
        assertEquals(EngineGeneration.UNKNOWN, EngineGeneration.fromBanner("Release-4.27"))
        assertEquals(EngineGeneration.UNKNOWN, EngineGeneration.fromBanner("UE3+Release"))
    }

    @Test
    fun `detectEngineGeneration reads LogInfo and survives a null`() {
        assertEquals(
            EngineGeneration.UE4,
            detectEngineGeneration(LogInfo(engineVersion = "UE4+Release-4.27-CL-1")),
        )
        assertEquals(EngineGeneration.UNKNOWN, detectEngineGeneration(LogInfo()))
    }

    @Test
    fun `UE5-only CVars are alive when the log reports UE5`() {
        for (name in listOf("r.temporalaa.upsampling", "r.temporalaa.algorithm", "r.temporalaacatmullrom")) {
            assertEquals(
                "$name must not be marked dead on a UE5 build",
                CvarVerdict.Alive,
                classifyCvar(name, TargetPlatform.ANDROID_VULKAN, EngineGeneration.UE5),
            )
        }
    }

    @Test
    fun `UE5-only CVars stay dead on a UE4 build`() {
        assertTrue(
            classifyCvar("r.temporalaa.upsampling", TargetPlatform.ANDROID_VULKAN, EngineGeneration.UE4)
                is CvarVerdict.Dead,
        )
    }

    @Test
    fun `an UNKNOWN engine keeps the pre-existing UE4 assumption`() {
        // The default must not silently flip: marking these alive on an
        // unrecognised banner would resurrect three inert CVars.
        assertTrue(
            classifyCvar("r.temporalaa.upsampling", TargetPlatform.ANDROID_VULKAN, EngineGeneration.UNKNOWN)
                is CvarVerdict.Dead,
        )
    }

    @Test
    fun `the engine axis does not affect platform-scoped CVars`() {
        // Engine detection must not become a way to un-dead a PC-only CVar.
        for (engine in EngineGeneration.entries) {
            assertTrue(
                "r.Metal.OptLevel must stay dead under $engine",
                classifyCvar("r.Metal.OptLevel", TargetPlatform.ANDROID_GLES, engine) is CvarVerdict.Dead,
            )
        }
    }
}
