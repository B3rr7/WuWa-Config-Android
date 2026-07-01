# Config Generator — Preset Demos (Engine.ini)

Common header omitted. All show the actual CVar values each preset produces for a Snapdragon 8 Gen 2 device (Adreno 740, 8GB RAM, Vulkan).

---

## Potato

Target: **50% render scale, minimum shadows, 0 SSR, aggressive culling**

```ini
; ── CHARACTER QUALITY ─────────────────────────────────
r.Shadow.SkeletalMeshLODBias=2
r.Kuro.SkeletalMesh.LODScreenSizeScale=5.0
r.Mobile.OutlineScale=1.1
r.Kuro.RadialBlur.MobileIntensityScalar=0.6
Kuro.Blueprint.EnableGameBudget=0

; ── SCALABILITY ──────────────────────────────────────
sg.ShadowQuality=1
sg.TextureQuality=1
sg.PostProcessQuality=1
sg.EffectsQuality=1
sg.AntiAliasingQuality=1
sg.ViewDistanceQuality=1
sg.FoliageQuality=0

; ── POST PROCESSING ──────────────────────────────────
r.BloomQuality=1
r.MotionBlurQuality=0
r.DepthOfFieldQuality=0
r.SceneColorFringeQuality=1
r.Tonemapper.Quality=4
r.Upscale.Quality=3
r.AmbientOcclusionLevels=0

; ── SHADOW ───────────────────────────────────────────
r.Shadow.CSM.MaxMobileCascades=2
r.Shadow.MaxResolution=512
r.Shadow.RadiusThreshold=0.12
r.MobileNumDynamicPointLights=2

; ── TEXTURE STREAMING ────────────────────────────────
r.Streaming.MipBias=3
r.MaxAnisotropy=4
r.Streaming.PoolSizeForMeshes=114

; ── MOBILE RENDERING ─────────────────────────────────
r.Mobile.UseFSRUpscale=1
r.MobileMSAA=0

; ── EFFECTS / PARTICLES ──────────────────────────────
fx.KuroUseGPUParticles=0
fx.Niagara.QualityLevel=1
r.EmitterSpawnRateScale=0.6
FX.MaxCPUParticlesPerEmitter=50
FX.MaxGPUParticlesSpawnedPerFrame=2048

; ── WATER / REFLECTION ───────────────────────────────
r.Mobile.WaterSSR=0
r.DistanceFieldAO=0

; ── FRAME & DISPLAY ──────────────────────────────────
r.VSync=1
r.FramePace=60
r.KuroFI.Enable=0
```

### Extra performance tweaks (potato/perf only):
```ini
r.HZBOcclusion=1
r.ReflectionEnvironment=0
r.MobileNumDynamicPointLights=0
r.Mobile.EnableMovableSpotlights=0
r.ShadowQuality=1
r.Shadow.CSM.MaxCascades=1
r.Shadow.CSM.MaxMobileCascades=1
r.Shadow.MaxResolution=512
r.Shadow.DistanceScale=0.4
r.VolumetricFog=0
r.DistanceFieldShadowing=0
r.CapsuleShadows=0
r.ContactShadows=0
r.SSGI.Enable=0
r.SubsurfaceScattering=0
r.MinScreenRadiusPercentage=0.015
foliage.MinScreenRadiusPercentage=0.008
r.StaticMeshLODDistanceScale=0.6
foliage.DensityScale=0.5
grass.DensityScale=0.4
r.KuroVolumeCloudEnable=0
```

---

## Balanced

Target: **80% render scale, medium shadows, SSR on, moderate culling**

```ini
; ── CHARACTER QUALITY ─────────────────────────────────
r.Shadow.SkeletalMeshLODBias=2
r.Kuro.SkeletalMesh.LODScreenSizeScale=6.0
r.Mobile.OutlineScale=1.2
r.Kuro.RadialBlur.MobileIntensityScalar=0.75
Kuro.Blueprint.EnableGameBudget=0

; ── SCALABILITY ──────────────────────────────────────
sg.ResolutionQuality=80
sg.ShadowQuality=2
sg.TextureQuality=2
sg.PostProcessQuality=2
sg.EffectsQuality=1
sg.AntiAliasingQuality=2
sg.ViewDistanceQuality=2
sg.FoliageQuality=1

; ── ANTI-ALIASING ────────────────────────────────────
r.PostProcessAAQuality=6
r.TemporalAA.Upsampling=1
r.TemporalAA.Algorithm=1
r.TemporalAAFilterSize=0.5
r.TemporalAA.MobileStaticFrameWeight=0.5

; ── POST PROCESSING ──────────────────────────────────
r.BloomQuality=3
r.EyeAdaptationQuality=2
r.MotionBlurQuality=0
r.DepthOfFieldQuality=1
r.LightShaftQuality=1
r.SceneColorFringeQuality=1
r.Tonemapper.Quality=4
r.Upscale.Quality=3
r.AmbientOcclusionLevels=0

; ── SHADOW ───────────────────────────────────────────
r.Shadow.CSM.MaxMobileCascades=2
r.Shadow.PerObjectResolutionMax=1024
r.Shadow.MaxResolution=1024
r.MobileNumDynamicPointLights=2

; ── TEXTURE STREAMING ────────────────────────────────
r.Streaming.MipBias=0
r.MaxAnisotropy=8
r.Streaming.PoolSizeForMeshes=150

; ── MOBILE RENDERING ─────────────────────────────────
r.Mobile.ShadingPath=1
r.Mobile.UseFSRUpscale=1
r.MobileMSAA=0
r.Mobile.HBAO=1

; ── VRS ──────────────────────────────────────────────
r.VRS.EnableMaterial=1
r.VRS.EnableMesh=1

; ── EFFECTS / PARTICLES ──────────────────────────────
fx.KuroUseGPUParticles=0
fx.Niagara.QualityLevel=1
r.EmitterSpawnRateScale=0.8
FX.MaxCPUParticlesPerEmitter=50
FX.MaxGPUParticlesSpawnedPerFrame=2048

; ── WATER / REFLECTION ───────────────────────────────
r.Mobile.WaterSSR=0
r.Mobile.SSR=0
r.DistanceFieldAO=0

; ── ENVIRONMENT ──────────────────────────────────────
r.Fog=1
r.KuroVolumeCloudEnable=1
r.FogVisibilityCulling.Opacity=0.5
foliage.DensityScale=1.0
grass.DensityScale=1.0

; ── NPC & WORLD ──────────────────────────────────────
r.Kuro.NpcDisappearDistance=10000
r.LandscapeReverseLODScaleFactor=3
r.RenderTargetPoolMin=80
r.HZBOcclusion=0
r.MorphTarget.UnloadDelayTime=10

; ── FRAME & DISPLAY ──────────────────────────────────
r.VSync=1
r.FramePace=60
r.KuroFI.Enable=0
```

---

## Ultra

Target: **100% render scale, maximum shadows, SSR on, high-quality effects**

```ini
; ── CHARACTER QUALITY ─────────────────────────────────
r.Shadow.SkeletalMeshLODBias=1
r.Kuro.SkeletalMesh.LODScreenSizeScale=7.0
r.Mobile.OutlineScale=1.3
r.Kuro.RadialBlur.MobileIntensityScalar=0.9
Kuro.Blueprint.EnableGameBudget=0

; ── SCALABILITY ──────────────────────────────────────
sg.ShadowQuality=3
sg.TextureQuality=3
sg.PostProcessQuality=3
sg.EffectsQuality=2
sg.AntiAliasingQuality=2
sg.ViewDistanceQuality=3
sg.FoliageQuality=2

; ── SHADOW ───────────────────────────────────────────
r.Shadow.CSM.MaxMobileCascades=2 → 3 (high/ultra)
r.Shadow.PerObjectResolutionMax=2048
r.Shadow.MaxResolution=2048
r.MobileNumDynamicPointLights=2

; ── TEXTURE STREAMING ────────────────────────────────
r.Streaming.MipBias=0
r.MaxAnisotropy=16
r.Streaming.PoolSizeForMeshes=240

; ── MOBILE RENDERING ─────────────────────────────────
r.Mobile.UseFSRUpscale=0
r.MobileMSAA=0
r.Mobile.HBAO=1
r.Mobile.PixelProjectedReflectionQuality=1
r.Mobile.EnableStaticAndCSMShadowReceivers=1

; ── EFFECTS / PARTICLES ──────────────────────────────
fx.Niagara.QualityLevel=2
r.EmitterSpawnRateScale=1.0
FX.MaxCPUParticlesPerEmitter=100
FX.MaxGPUParticlesSpawnedPerFrame=4096

; ── WATER / REFLECTION ───────────────────────────────
r.Mobile.WaterSSR=1
r.Mobile.WaterSSRStep=12
r.Mobile.SSR=1
r.Mobile.SceneObjMobileSSR=1
r.Kuro.EnablePlanarReflection=1
r.SSR.HalfRes=0
r.SSR.MaxRoughness=1.0

; ── SCREEN-SPACE EFFECTS ────────────────────────────
r.SSGI.Enable=1
r.SubsurfaceScattering=1
r.SSFS.HighQuality=1
r.SSS.Quality=2
r.DistanceFieldAO=0

; ── ENVIRONMENT ──────────────────────────────────────
r.Kuro.SuperFarFogGlobalDistanceScale=1
r.FogVisibilityCulling.Opacity=0.8
foliage.DensityScale=1.5
grass.DensityScale=1.5

; ── NPC & WORLD ──────────────────────────────────────
r.Kuro.NpcDisappearDistance=15000
r.LandscapeReverseLODScaleFactor=2
r.RenderTargetPoolMin=150
r.HZBOcclusion=0
r.MorphTarget.UnloadDelayTime=30

; ── FRAME & DISPLAY ──────────────────────────────────
r.VSync=1
r.FramePace=60
r.KuroFI.Enable=1

; ── PIPELINE / RHI ───────────────────────────────────
r.RHICmdBypass=1
r.RHICmdUseParallelAlgorithms=1
r.RHICmdUseThread=1
; Vulkan detected
r.Vulkan.RobustBufferAccess=1
r.Vulkan.DescriptorSetLayoutMode=2
r.Vulkan.PipelineLRUCapactiy=128
```

---

## Diff Summary

| CVar | Potato | Balanced | Ultra |
|------|--------|----------|-------|
| sg.ResolutionQuality | 50 | 80 | 100 |
| sg.ShadowQuality | 1 | 2 | 3 |
| sg.TextureQuality | 1 | 2 | 3 |
| sg.ViewDistanceQuality | 1 | 2 | 3 |
| sg.FoliageQuality | 0 | 1 | 2 |
| r.BloomQuality | 1 | 3 | 4 |
| r.DepthOfFieldQuality | 0 | 1 | 2 |
| r.DepthOfFieldQuality | 0 | 1 | 2 |
| r.Shadow.MaxResolution | 512 | 1024 | 2048 |
| r.Streaming.MipBias | 3 | 0 | 0 |
| r.MaxAnisotropy | 4 | 8 | 16 |
| r.Mobile.UseFSRUpscale | 1 | 1 | 0 *(disabled)* |
| r.Mobile.WaterSSR | 0 | 0 | 1 |
| r.Mobile.SSR | 0 | 0 | 1 |
| r.SSGI.Enable | 0 | 0 | 1 |
| r.SubsurfaceScattering | 0 | 0 | 1 |
| r.KuroFI.Enable | 0 | 0 | 1 |
| foliage.DensityScale | 0.6 | 1.0 | 1.5 |
| r.RenderTargetPoolMin | 64 | 80 | 150 |
| r.MorphTarget.UnloadDelayTime | 3 | 10 | 30 |
| r.Mobile.HBAO | 0 | 1 | 1 |
| r.HZBOcclusion | 1 *(forced)* | 0 | 0 |
| r.ReflectionEnvironment | 0 *(forced)* | *(default)* | *(default)* |
| r.DistanceFieldShadowing | 0 *(forced)* | *(default)* | *(default)* |
| r.VolumetricFog | 0 *(forced)* | *(default)* | *(default)* |
| Extra perf CVars | ~30 lines | none | none |
