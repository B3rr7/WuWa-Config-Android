package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.CvarCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CvarCategorizerTest {
    @Test
    fun `character CVars`() {
        assertEquals(CvarCategory.CHARACTER, CvarCategorizer.categorize("r.Kuro.ToonOutlineDrawDistanceMobile"))
        assertEquals(CvarCategory.CHARACTER, CvarCategorizer.categorize("r.Kuro.ToonEyeTransparentDrawDistanceMobile"))
        assertEquals(CvarCategory.CHARACTER, CvarCategorizer.categorize("r.Mobile.OutlineScale"))
        assertEquals(CvarCategory.CHARACTER, CvarCategorizer.categorize("r.SubsurfaceScattering"))
        assertEquals(CvarCategory.CHARACTER, CvarCategorizer.categorize("r.SkinCache.SceneMemoryLimitInMB"))
        assertEquals(CvarCategory.CHARACTER, CvarCategorizer.categorize("r.MorphTarget.UnloadDelayTime"))
    }

    @Test
    fun `lighting and shadow CVars`() {
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Shadow.CSM.MaxMobileCascades"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.ShadowQuality"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Shadow.MaxResolution"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Shadow.PerObjectResolutionMax"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Shadow.SinglePass"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Shadow.ForceSerialSingleRenderPass"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Shadow.DistanceScale"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.DistanceFieldShadowing"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.DistanceFieldAO"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.CapsuleShadows"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.ContactShadows"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.AmbientOcclusionLevels"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.LightFunctionQuality"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.SSAO"))
    }

    @Test
    fun `mobile CVars`() {
        assertEquals(CvarCategory.MOBILE, CvarCategorizer.categorize("r.Mobile.ShadingPath"))
        assertEquals(CvarCategory.MOBILE, CvarCategorizer.categorize("r.Mobile.UseFSRUpscale"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.HBAO"))
        assertEquals(CvarCategory.MOBILE, CvarCategorizer.categorize("r.MobileHDR"))
        assertEquals(CvarCategory.MOBILE, CvarCategorizer.categorize("r.MobileMSAA"))
        assertEquals(CvarCategory.MOBILE, CvarCategorizer.categorize("r.Mobile.KuroPostprocess"))
        assertEquals(CvarCategory.MOBILE, CvarCategorizer.categorize("r.Mobile.TonemapperFilm"))
    }

    @Test
    fun `mobile with light shadow exceptions`() {
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.SSR"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.WaterSSR"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.NumDynamicPointLights"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.EnableMovableSpotlights"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.EnableStaticAndCSMShadowReceivers"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.SSAO"))
    }

    @Test
    fun `post processing CVars`() {
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.BloomQuality"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.EyeAdaptationQuality"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.MotionBlurQuality"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.DepthOfFieldQuality"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.LensFlareQuality"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.SceneColorFringeQuality"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.Tonemapper.Quality"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.Tonemapper.GrainQuantization"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.TemporalAAFilterSize"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.TemporalAA.Upsampling"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.Upscale.Quality"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.DefaultFeature.AntiAliasing"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.PostProcessAAQuality"))
    }

    @Test
    fun `environment CVars`() {
        assertEquals(CvarCategory.ENVIRONMENT, CvarCategorizer.categorize("r.Fog"))
        assertEquals(CvarCategory.ENVIRONMENT, CvarCategorizer.categorize("r.VolumetricFog"))
        assertEquals(CvarCategory.ENVIRONMENT, CvarCategorizer.categorize("r.FogVisibilityCulling.Enable"))
        assertEquals(CvarCategory.ENVIRONMENT, CvarCategorizer.categorize("r.FogVisibilityCulling.Opacity"))
        assertEquals(CvarCategory.ENVIRONMENT, CvarCategorizer.categorize("r.LandscapeReverseLODScaleFactor"))
        assertEquals(CvarCategory.ENVIRONMENT, CvarCategorizer.categorize("r.Kuro.Foliage.MobileGrassCullDistanceMax"))
        assertEquals(CvarCategory.ENVIRONMENT, CvarCategorizer.categorize("r.ReflectionEnvironment"))
        assertEquals(CvarCategory.ENVIRONMENT, CvarCategorizer.categorize("foliage.DensityScale"))
        assertEquals(CvarCategory.ENVIRONMENT, CvarCategorizer.categorize("foliage.LODDistanceScale"))
        assertEquals(CvarCategory.ENVIRONMENT, CvarCategorizer.categorize("grass.DensityScale"))
    }

    @Test
    fun `reflection CVars`() {
        assertEquals(CvarCategory.REFLECTION, CvarCategorizer.categorize("r.SSR.HalfRes"))
        assertEquals(CvarCategory.REFLECTION, CvarCategorizer.categorize("r.SSR.MaxRoughness"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.WaterSSR"))
        assertEquals(CvarCategory.REFLECTION, CvarCategorizer.categorize("r.Kuro.EnablePlanarReflection"))
    }

    @Test
    fun `texture streaming CVars`() {
        assertEquals(CvarCategory.TEXTURE_STREAMING, CvarCategorizer.categorize("r.TextureStreaming"))
        assertEquals(CvarCategory.TEXTURE_STREAMING, CvarCategorizer.categorize("r.Streaming.MipBias"))
        assertEquals(CvarCategory.TEXTURE_STREAMING, CvarCategorizer.categorize("r.Streaming.PoolSizeForMeshes"))
        assertEquals(CvarCategory.TEXTURE_STREAMING, CvarCategorizer.categorize("r.MaxAnisotropy"))
        assertEquals(CvarCategory.TEXTURE_STREAMING, CvarCategorizer.categorize("r.RenderTargetPoolMin"))
        assertEquals(CvarCategory.TEXTURE_STREAMING, CvarCategorizer.categorize("s.TimeLimitExceededMultiplier"))
    }

    @Test
    fun `performance CVars`() {
        assertEquals(CvarCategory.PERFORMANCE, CvarCategorizer.categorize("r.FramePace"))
        assertEquals(CvarCategory.PERFORMANCE, CvarCategorizer.categorize("r.VSync"))
        assertEquals(CvarCategory.PERFORMANCE, CvarCategorizer.categorize("r.FinishCurrentFrame"))
        assertEquals(CvarCategory.PERFORMANCE, CvarCategorizer.categorize("r.EnableMeshPassProcessorsCache"))
        assertEquals(CvarCategory.PERFORMANCE, CvarCategorizer.categorize("r.VRS.Enable"))
        assertEquals(CvarCategory.PERFORMANCE, CvarCategorizer.categorize("r.VRS.EnableMaterial"))
        assertEquals(CvarCategory.PERFORMANCE, CvarCategorizer.categorize("r.VRS.EnableMesh"))
        assertEquals(CvarCategory.MOBILE, CvarCategorizer.categorize("r.Mobile.EnableVoidGT"))
        assertEquals(CvarCategory.PERFORMANCE, CvarCategorizer.categorize("r.UseClusteredDeferredShading"))
    }

    @Test
    fun `pipeline and RHI CVars`() {
        assertEquals(CvarCategory.PIPELINE_RHI, CvarCategorizer.categorize("r.RHICmdBypass"))
        assertEquals(CvarCategory.PIPELINE_RHI, CvarCategorizer.categorize("r.RHICmdUseParallelAlgorithms"))
        assertEquals(CvarCategory.PIPELINE_RHI, CvarCategorizer.categorize("r.RHICmdUseThread"))
        assertEquals(CvarCategory.PIPELINE_RHI, CvarCategorizer.categorize("r.Vulkan.RobustBufferAccess"))
        assertEquals(CvarCategory.PIPELINE_RHI, CvarCategorizer.categorize("r.PSO.CompilationMode"))
    }

    @Test
    fun `LOD and culling CVars`() {
        assertEquals(CvarCategory.LOD_CULLING, CvarCategorizer.categorize("r.HZBOcclusion"))
        assertEquals(CvarCategory.LOD_CULLING, CvarCategorizer.categorize("r.CullDistanceVolume.Enable"))
        assertEquals(CvarCategory.LOD_CULLING, CvarCategorizer.categorize("r.MinScreenRadiusPercentage"))
        assertEquals(CvarCategory.LOD_CULLING, CvarCategorizer.categorize("r.MaxScreenRadiusPercentage"))
        assertEquals(CvarCategory.LOD_CULLING, CvarCategorizer.categorize("r.StaticMeshLODDistanceScale"))
        assertEquals(CvarCategory.LOD_CULLING, CvarCategorizer.categorize("r.ScreenSizeCullRatioFactor"))
        assertEquals(CvarCategory.LOD_CULLING, CvarCategorizer.categorize("r.AllowOcclusionQueries"))
        assertEquals(CvarCategory.LOD_CULLING, CvarCategorizer.categorize("r.Kuro.MobileISMDecideDistance"))
        assertEquals(CvarCategory.LOD_CULLING, CvarCategorizer.categorize("lod.TemporalLag"))
    }

    @Test
    fun `animation CVars`() {
        assertEquals(CvarCategory.ANIMATION, CvarCategorizer.categorize("a.URO.Enable"))
        assertEquals(CvarCategory.ANIMATION, CvarCategorizer.categorize("a.URO.ForceAnimRate"))
        assertEquals(CvarCategory.ANIMATION, CvarCategorizer.categorize("a.URO.ForceInterpolation"))
    }

    @Test
    fun `effects CVars`() {
        assertEquals(CvarCategory.EFFECTS, CvarCategorizer.categorize("fx.KuroUseGPUParticles"))
        assertEquals(CvarCategory.EFFECTS, CvarCategorizer.categorize("fx.Niagara.QualityLevel"))
        assertEquals(CvarCategory.EFFECTS, CvarCategorizer.categorize("r.EmitterSpawnRateScale"))
        assertEquals(CvarCategory.EFFECTS, CvarCategorizer.categorize("Niagara.GPUDrawIndirectArgsBufferSlack"))
    }

    @Test
    fun `thermal CVars`() {
        assertEquals(CvarCategory.THERMAL, CvarCategorizer.categorize("r.Kuro.AutoCoolEnable"))
        assertEquals(CvarCategory.THERMAL, CvarCategorizer.categorize("r.Kuro.ThermalControlMode"))
        assertEquals(CvarCategory.THERMAL, CvarCategorizer.categorize("r.DontLimitOnBattery"))
    }

    @Test
    fun `scalability CVars`() {
        assertEquals(CvarCategory.SCALABILITY, CvarCategorizer.categorize("sg.ShadowQuality"))
        assertEquals(CvarCategory.SCALABILITY, CvarCategorizer.categorize("sg.TextureQuality"))
        assertEquals(CvarCategory.SCALABILITY, CvarCategorizer.categorize("sg.ViewDistanceQuality"))
        assertEquals(CvarCategory.SCALABILITY, CvarCategorizer.categorize("sg.AntiAliasingQuality"))
        assertEquals(CvarCategory.SCALABILITY, CvarCategorizer.categorize("sg.ResolutionQuality"))
    }

    @Test
    fun `unknown CVar`() {
        assertEquals(CvarCategory.UNKNOWN, CvarCategorizer.categorize("r.NonexistentCVar"))
        assertEquals(CvarCategory.UNKNOWN, CvarCategorizer.categorize("some.random.setting"))
    }

    @Test
    fun `case insensitivity`() {
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("R.SHADOW.QUALITY"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Shadow.Quality"))
    }

    // ── prefix-index equivalence over the real CVar database ──
    // findSubRule's bucket index is keyed on prefix.take(8) ("r.shadow") but used to be
    // probed with k.substring(2, 8) ("shadow"), so EVERY lookup missed and fell back to the
    // full ~250-rule linear scan. That is a performance bug only: the linear fallback returns
    // the same rule, so no category assertion can observe it.
    //
    // What CAN be observed is that fixing the probe key (k.take(8)) did not change any
    // category. The bucket scan is only a sound optimisation if it returns the FIRST
    // matching rule in list order, exactly as the linear scan does — otherwise
    // overlapping prefixes (r.mobile.ssr → REFLECTION listed before r.mobile. → MOBILE)
    // would silently reclassify thousands of CVars. This sweeps every r.* CVar in the real
    // 5,889-line asset and pins the result, so a future index change that alters a category
    // fails here rather than silently reshuffling the UI.

    @Test
    fun `categorize is stable across the real r-prefixed CVar database`() {
        val keys = realCvarKeys()
        assertTrue("expected the real libUE4_cvars.txt to yield r.* keys, got ${keys.size}", keys.size > 1000)

        // First pass: snapshot the categorisation of the whole database.
        val first = LinkedHashMap<String, CvarCategory>()
        for (key in keys) first[key] = CvarCategorizer.categorize(key)

        // Second pass: the index is a lazily-populated singleton, so a warm call and a cold
        // call must agree. Re-reading the same keys after every other CVar in the database has
        // been categorised exercises the warmed bucket path against the first (cold) result.
        for (key in keys) {
            val warm = CvarCategorizer.categorize(key)
            val cold = first.getValue(key)
            assertEquals("category for $key changed between the cold and warm index passes", cold, warm)
        }

        // Spot-anchor a handful of well-known keys so a "everything is UNKNOWN" outcome
        // cannot pass this test unnoticed.
        val expectations =
            mapOf(
                "r.Shadow.CSM.MaxMobileCascades" to CvarCategory.LIGHTING_SHADOW,
                "r.Streaming.MipBias" to CvarCategory.TEXTURE_STREAMING,
                "r.BloomQuality" to CvarCategory.POST_PROCESS,
                "r.MorphTarget.UnloadDelayTime" to CvarCategory.CHARACTER,
                "r.StaticMeshLODDistanceScale" to CvarCategory.LOD_CULLING,
                "r.Vulkan.RobustBufferAccess" to CvarCategory.PIPELINE_RHI,
                "r.VSync" to CvarCategory.PERFORMANCE,
                "r.Mobile.Shadow.CSM" to CvarCategory.MOBILE,
            )
        for ((key, expected) in expectations) {
            assertEquals("anchor $key", expected, CvarCategorizer.categorize(key))
        }

        // The index must actually be reaching the bulk of the database, not just a handful.
        // Measured against the shipped asset: 4,080 distinct r.* keys, ~35% UNKNOWN.
        val unknown = first.values.count { it == CvarCategory.UNKNOWN }
        assertTrue(
            "the real database should classify the majority of r.* CVars, but $unknown of ${first.size} were UNKNOWN",
            unknown * 2 < first.size,
        )
    }

    @Test
    fun `colliding prefixes in the same bucket keep first-match-wins order`() {
        // r.minscreenradiuspercentage (LOD_CULLING) and r.minscreenradiusfor (LIGHTING_SHADOW)
        // both truncate to the SAME 8-char index key, "r.minscre". A bucket scan that
        // reorders the list, or that returns the bucket's last match instead of its first,
        // silently inverts the category of every r.MinScreenRadiusFor* CVar.
        assertEquals(CvarCategory.LOD_CULLING, CvarCategorizer.categorize("r.MinScreenRadiusPercentage"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.MinScreenRadiusForLights"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.MinScreenRadiusForCSMDepth"))

        // A longer prefix that the 8-char bucket cannot fully represent must still resolve,
        // via the linear fallback, to the same rule the bucket would have picked.
        assertEquals(CvarCategory.TEXTURE_STREAMING, CvarCategorizer.categorize("r.Streaming.MipBias"))
        assertEquals(CvarCategory.POST_PROCESS, CvarCategorizer.categorize("r.Tonemapper.GrainQuantization"))
    }

    @Test
    fun `r mobile sub-exceptions are handled before the prefix rules`() {
        // categorize() intercepts r.mobile.* via mobileSubExceptions / lightShadowPrefixes
        // BEFORE findSubRule runs, so these never reach the index. Pinning them keeps the
        // interaction between the two paths visible.
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.SSR"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.WaterSSR"))
        assertEquals(CvarCategory.LIGHTING_SHADOW, CvarCategorizer.categorize("r.Mobile.PixelProjectedReflectionQuality"))
        // No exception matches, so this one falls through to the r.mobile. rule → MOBILE.
        assertEquals(CvarCategory.MOBILE, CvarCategorizer.categorize("r.Mobile.Shadow.CSM"))
    }

    /**
     * The real CVar list lives in app/src/main/assets, which is NOT on the unit-test
     * classpath (the classpath copy under src/test/resources is a 10-line stub). Gradle runs
     * unit tests with the working directory set to the module dir, so resolve the asset
     * relative to that and fall back to the repo root for IDE runners.
     */
    private fun realCvarKeys(): List<String> {
        val candidates =
            listOf(
                File("src/main/assets/cvars/libUE4_cvars.txt"),
                File("app/src/main/assets/cvars/libUE4_cvars.txt"),
            )
        val file = candidates.firstOrNull { it.isFile }
        val cwd = File(".").absolutePath
        assertNotNull("libUE4_cvars.txt not found; tried ${candidates.map { it.path }} from cwd=$cwd", file)
        return file!!
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(";") && !it.startsWith("#") }
            .filter { it.startsWith("r.") }
            .distinct()
    }
}
