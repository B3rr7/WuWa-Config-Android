package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.LogInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        for (name in
            listOf(
                "r.PSO.CompilationMode",
                "r.PSO.CacheEvictScheme",
                "r.PSO.FixPSOCrash1",
                "r.PSO.BackgroundPreompilingRTPSO",
            )) {
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
        for (name in
            listOf("r.TemporalAA.Algorithm", "r.TemporalAA.Upsampling", "r.TemporalAACatmullRom")) {
            assertTrue("$name should be dead", classifyCvar(name, TargetPlatform.ANDROID_GLES) is CvarVerdict.Dead)
        }
    }

    @Test
    fun `legitimate UE4 TAA CVars stay alive`() {
        // Guards against prefix-matching the whole r.TemporalAA. family: these are
        // real UE4 CVars and marking them would have cost ~9 working lines.
        for (name in
            listOf(
                "r.TemporalAA.Sharpness",
                "r.TemporalAA.MobileFrameWeight",
                "r.TemporalAA.MobileStaticFrameWeight",
                "r.TemporalAAPauseCorrect",
                "r.TemporalAAFilterSize",
                "r.TemporalAACurrentFrameWeight",
            )) {
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
        // Sweeps all 5,889 real names and asserts every verdict it produces is
        // explainable by a rule, so a future edit to the table cannot silently start
        // flagging arbitrary names.
        val corpus = readCorpus()
        assertTrue("corpus should be ~5,889 names, found ${corpus.size}", corpus.size > 5000)
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
        assertTrue("expected a non-trivial number of dead names, got ${marked.size}", marked.size >= 90)
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

    private fun readCorpus(): List<String> {
        val candidates =
            listOf(
                "src/main/assets/cvars/libUE4_cvars.txt",
                "app/src/main/assets/cvars/libUE4_cvars.txt",
            )
        val file = candidates.map { java.io.File(it) }.firstOrNull { it.isFile }
            ?: throw AssertionError("libUE4_cvars.txt not found from ${System.getProperty("user.dir")}")
        return file.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }
}
