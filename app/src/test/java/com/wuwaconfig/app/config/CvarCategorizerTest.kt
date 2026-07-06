package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.CvarCategory
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
