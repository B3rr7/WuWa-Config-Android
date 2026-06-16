package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.GeneratedIni
import com.wuwaconfig.app.model.GeneratorOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PresetProfile(
    val screen: Int, val shadow: Int, val shadowRes: Int, val ssr: Int,
    val mipbias: Int, val streaming: Double, val vd: Double, val flod: Double,
    val detail: Int, val lod_bias: Int, val grasscull: Int
)

val PRESETS = mapOf(
    "performance" to PresetProfile(75, 1, 512, 0, 1, 1.0, 0.7, 1.0, 0, 2, 8000),
    "balanced"    to PresetProfile(100, 2, 1024, 1, 0, 2.0, 1.5, 2.0, 1, 0, 15000),
    "high"        to PresetProfile(100, 4, 2048, 2, 0, 3.0, 2.0, 2.5, 2, 0, 20000),
    "ultra"       to PresetProfile(100, 5, 2048, 4, -1, 4.0, 3.0, 3.0, 2, -1, 30000)
)

object ConfigGenerator {
    var activePreset = "balanced"
    var logInfo = LogInfo()

    fun configHeader(platform: String, preset: String): String {
        val now = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return listOf(
            "; ════════════════════════════════════════════════",
            "; ██████╗ 42╚████╗     TOOLKIT",
            "; ██╔══██╗██║  ██║╚════██╗    Wuthering Waves Config",
            "; ██████╔╝███████║ █████╔╝    P42 Toolkit",
            "; ██╔═══╝ ╚════██║██╔═══╝     Generated: $now",
            "; ██║           ██║███████╗   Preset : ${preset.uppercase()}",
            "; ╚═╝           ╚═╝╚══════╝   Device : ${logInfo.deviceModel ?: "unknown"}",
            "; Platform : $platform",
            "; GPU: ${logInfo.gpu ?: "unknown"}",
            "; ════════════════════════════════════════════════",
            ""
        ).joinToString("\n")
    }

    fun generate(preset: String, opts: GeneratorOptions): GeneratedIni {
        val p = PRESETS[preset]!!
        activePreset = preset
        return GeneratedIni(
            engine = buildAndroidEngineIni(p, opts),
            deviceProfiles = buildAndroidDeviceProfilesIni(p, opts),
            gameUserSettings = buildAndroidGameUserSettingsIni(p, opts)
        )
    }

    private fun buildAndroidEngineIni(p: PresetProfile, opts: GeneratorOptions): String {
        val gpu = (logInfo.gpu ?: "").lowercase()
        val hasVulkan = logInfo.vulkanStatus == "available"
        val hasThermalIssues = logInfo.thermalEvents >= 5
        val isHighEnd = Regex("""adreno.*7\d{2}""").containsMatchIn(gpu) ||
                Regex("""adreno.*8\d{2}""").containsMatchIn(gpu) ||
                gpu.contains("mali-g7") || gpu.contains("mali-g6") || gpu.contains("mali-g615") ||
                ((logInfo.fpsCap ?: 0) >= 60 && !Regex("""adreno.*6\d{2}""").containsMatchIn(gpu))
        val isMid = Regex("""adreno.*6\d{2}""").containsMatchIn(gpu) ||
                gpu.contains("mali-g5") || gpu.contains("mali-g57") || gpu.contains("mali-g68")

        val charOutline = if (p.detail > 1) 1200 else if (p.detail > 0) 950 else 850
        val charEyeDist = if (p.detail > 1) 700 else if (p.detail > 0) 550 else 450
        val charLODScale = if (p.detail > 1) 7.0 else if (p.detail > 0) 6.0 else 5.0
        val streamPool = if (isHighEnd) 800 else if (isMid) 500 else 380
        val niagQ = if (p.detail > 1) 2 else 1
        val shadowCascade = if (p.shadow >= 4) 3 else 2
        val shadowSkLOD = if (p.shadow >= 4) 1 else 2
        val maxAniso = if (isHighEnd) 16 else if (isMid) 8 else 4
        val ismDist = if (isHighEnd) 14000 else if (isMid) 10000 else 7000
        val ismRad = if (isHighEnd) 18000 else if (isMid) 13000 else 9000
        val grassCull = if (isHighEnd) 2000 else if (isMid && hasThermalIssues) 600 else if (isMid) 1200 else 800
        val npcDist = if (isHighEnd) 15000 else if (isMid) 10000 else 7000

        val lines = mutableListOf<String>().apply {
            add(configHeader("Android", activePreset))
            add("[SystemSettings]"); add("")
            add("; ── CHARACTER QUALITY ─────────────────────────────────")
            add("r.Shadow.SkeletalMeshLODBias=$shadowSkLOD")
            add("r.Kuro.SkeletalMesh.LODScreenSizeScale=$charLODScale")
            add("r.Mobile.KuroPostprocess=1")
            add("r.Mobile.TonemapperFilm=1")
            add("r.Kuro.ToonOutlineDrawDistanceMobile=$charOutline")
            add("r.Kuro.ToonEyeTransparentDrawDistanceMobile=$charEyeDist")
            add("r.Kuro.ToonFaceShadowMeshDrawDistanceMobile=$charEyeDist")
            add("r.Mobile.OutlineScale=${if (p.detail > 1) "1.3" else if (p.detail > 0) "1.2" else "1.1"}")
            add("r.Kuro.AutoExposure=1")
            add("r.Kuro.RadialBlur.MobileIntensityScalar=${if (p.detail > 1) "0.9" else if (p.detail > 0) "0.75" else "0.6"}")
            add("r.Mobile.TreeRimLight=1")
            add("r.Kuro.LandscapeCapture=1")
            add("r.Kuro.LandscapeCaptureDistance=${if (isHighEnd) 8000 else if (isMid) 6000 else 4000}")
            add("r.Mobile.Kuro.LandscapeCaptureSize=${if (p.detail > 0) 2 else 1}")
            add("")
            add("; ── SCALABILITY ──────────────────────────────────────")
            add("sg.ShadowQuality=${if (p.shadow >= 4) 3 else if (p.shadow >= 2) 2 else 1}")
            add("sg.TextureQuality=${if (p.detail > 1) 3 else if (p.detail > 0) 2 else 1}")
            add("sg.PostProcessQuality=${if (p.detail > 1) 3 else if (p.detail > 0) 2 else 1}")
            add("sg.EffectsQuality=${if (p.detail > 1) 2 else 1}")
            add("sg.AntiAliasingQuality=${if (p.detail > 0) 2 else 1}")
            add("sg.ViewDistanceQuality=${if (p.detail > 1) 3 else if (p.detail > 0) 2 else 1}")
            add("sg.FoliageQuality=${if (p.detail > 1) 2 else if (p.detail > 0) 1 else 0}")
            add("")
            add("; ── ANTI-ALIASING ────────────────────────────────────")
            add("r.PostProcessAAQuality=6")
            add("r.TemporalAA.Upsampling=1")
            add("r.DefaultFeature.AntiAliasing=2")
            add("")
            add("; ── POST PROCESSING ──────────────────────────────────")
            add("r.BloomQuality=${if (p.detail > 1) 4 else if (p.detail > 0) 3 else 1}")
            add("r.EyeAdaptationQuality=2")
            add("r.MotionBlurQuality=0")
            add("r.DepthOfFieldQuality=${if (p.detail > 1) 2 else if (p.detail > 0) 1 else 0}")
            add("r.LightShaftQuality=${if (p.detail > 0) 1 else 0}")
            add("r.LensFlareQuality=0")
            add("r.SceneColorFringeQuality=${if (opts.ca) 0 else 1}")
            add("r.Tonemapper.GrainQuantization=0")
            add("r.DisableDistortion=${if (p.detail > 1) 0 else 1}")
            add("r.AmbientOcclusionLevels=${if (p.detail > 1) 1 else 0}")
            add("r.KuroTonemapping=3")
            add("r.Kuro.KuroBloomEnable=1")
            add("")
            add("; ── SHADOW ───────────────────────────────────────────")
            add("r.Shadow.KuroEnablePointLightShadow=${if (p.shadow >= 3) 1 else 0}")
            add("r.Shadow.CSM.MaxMobileCascades=$shadowCascade")
            add("r.Shadow.RadiusThresholdFar=${if (p.shadow >= 3) "0.06" else "0.12"}")
            add("r.Shadow.UnbuiltPreviewInGame=1")
            add("r.Kuro.GlobalLightQuality_PC=${if (p.shadow >= 4) 4 else if (p.shadow >= 2) 3 else 2}")
            add("r.Kuro.GlobalLightShadowQuality_PC=${if (p.shadow >= 4) 4 else if (p.shadow >= 2) 3 else 2}")
            add("")
            add("; ── TEXTURE STREAMING ────────────────────────────────")
            add("r.TextureStreaming=1")
            add("r.Streaming.PoolSize=$streamPool")
            add("r.Streaming.Boost=${if (p.detail > 1) "1.2" else if (p.detail > 0) "1.0" else "0.85"}")
            add("r.Streaming.MipBias=${if (p.detail > 1) "0" else "1"}")
            add("r.Streaming.LODBias=0")
            add("r.MaxAnisotropy=$maxAniso")
            add("r.streaming.TexturePoolSizeMode=1")
            add("r.Streaming.KuroMinFOVFactorForStreaming=0.2")
            add("r.Streaming.GroupBoost.MediumNpcTextureFactor=${if (p.detail > 0) "1.5" else "1.2"}")
            add("")
            add("; ── EFFECTS / PARTICLES ──────────────────────────────")
            add("; ⚠ CRASH FIX March 2026 — MANDATORY")
            add("fx.KuroUseGPUParticles=0")
            add("Niagara.GPUDrawIndirectArgsBufferSlack=4096")
            add("fx.Niagara.QualityLevel=$niagQ")
            add("r.EmitterSpawnRateScale=${if (p.detail > 1) "1.0" else if (p.detail > 0) "0.8" else "0.6"}")
            add("")
            add("; ── WATER / REFLECTION ───────────────────────────────")
            add("r.Mobile.WaterSSR=${if (isHighEnd && p.detail > 0) 1 else 0}")
            add("r.Mobile.WaterSSRStep=${if (p.detail > 1) 12 else 8}")
            add("r.Mobile.SSR=${if (isHighEnd && p.detail > 0) 1 else 0}")
            add("r.Mobile.SceneObjMobileSSR=${if (isHighEnd && p.detail > 1) 1 else 0}")
            add("r.Kuro.EnablePlanarReflection=${if (isHighEnd && p.detail > 1) 1 else 0}")
            add("r.DistanceFieldAO=0")
            add("")
            add("; ── ENVIRONMENT ──────────────────────────────────────")
            if (opts.fog) { add("r.Fog=0"); add("r.KuroVolumeCloudEnable=0") } else add("r.Fog=1")
            add("r.Kuro.SuperFarFogGlobalDistanceScale=${if (p.detail > 1) 1 else 0}")
            add("r.Kuro.LightFunction=1")
            add("foliage.LODOptimize=1")
            add("r.EnableAggressivePVS=1")
            add("r.Kuro.MobileISMDecideDistance=$ismDist.0")
            add("r.Kuro.MobileISMMeshRadiusMax=$ismRad.0")
            add("r.Kuro.Foliage.MobileGrassCullDistanceMax=$grassCull")
            add("r.Kuro.Foliage.MobileGrass3_0CullDistanceMax=$grassCull")
            add("r.Kuro.Foliage.MobileMiddleCullDistanceMin=${(grassCull * 1.8).toInt()}")
            add("r.Kuro.Foliage.MobileMiddleCullDistanceMax=${(grassCull * 2.2).toInt()}")
            add("r.Kuro.Foliage.MobileFarCullDistanceMin=${(grassCull * 2.8).toInt()}")
            add("r.Kuro.Foliage.MobileFarCullDistanceMax=${(grassCull * 3.2).toInt()}")
            add("")
            add("; ── NPC & WORLD ──────────────────────────────────────")
            add("r.Kuro.NpcDisappearDistance=$npcDist")
            add("r.Kuro.LandscapeReverseLODScaleFactor=${if (p.detail > 1) 2 else 3}")
            add("r.LandscapeLOD0ScreenSizeScale=2")
            add("r.KuroMaxFOVForLOD=${if (p.detail > 1) 85 else 80}")
            add("r.MDCFallback.EnabledLOD=1")
            add("r.BBM.LODBias=${if (p.detail > 1) 0 else 1}")
            add("lod.TemporalLag=1")
            add("r.RenderTargetPoolMin=${if (p.detail > 0) 100 else 50}")
            add("r.AllowPrecomputedVisibility=1")
            add("r.HZBOcclusion=${if (opts.hzb) 1 else 0}")
            add("")
            add("; ── FRAME & DISPLAY ──────────────────────────────────")
            add("r.MobileHDR=1")
            add("r.VSync=${if (opts.vsync) 1 else 0}")
            add("r.FramePace=${opts.fps}")
            add("r.SkinCache.SceneMemoryLimitInMB=${if (isHighEnd) 384 else if (isMid) 256 else 192}")
            add("r.ShaderPipelineCache.Enabled=1")
            add("r.ShaderPipelineCache.PrecompileCheckCacheHash=1")
            add("r.ShaderPipelineCache.BatchSize=128")
            add("r.PSO.CompilationMode=0")
            add("r.kuro.LGUIBlurTexture.save=0")
            add("")
            add("; ── THERMAL & STABILITY ──────────────────────────────")
            add("r.Kuro.AutoCoolEnable=${if (opts.cool) 1 else 0}")
            if (hasThermalIssues) {
                add("; Thermal throttle detected in log — applying safeguards")
                add("r.Kuro.ThermalControlMode=1")
            }
            if (hasVulkan || opts.vulkan) {
                add("r.Vulkan.RobustBufferAccess=1")
                add("r.Vulkan.DescriptorSetLayoutMode=2")
            } else add("; Vulkan not detected")
            add("")
            add("; ── FORBIDDEN CVAR OVERRIDES ──────────────────────────")
            add("; Disabling known problematic CVars detected in log")
            add("r.FidelityFX.FSR.RCAS.Enabled=0")
            add("r.TemporalAA.Sharpness=0")
            add("r.Mobile.SSAO=0")
            add("r.Mobile.EnableVoidGT=0")
            add("r.DefaultFeature.LensFlare=0")
            add("")
            add("[/Script/Engine.StreamingSettings]")
            add("s.TimeLimitExceededMultiplier=1.5")
            add("s.AsyncLoadingThreadEnabled=1")
            add("s.EventDrivenLoaderEnabled=1")
            add("")
            add("[/Script/Engine.GarbageCollectionSettings]")
            add("gc.LowMemory.TimeBetweenPurgingPendingLevels=20")
        }
        return lines.joinToString("\n")
    }

    private fun buildAndroidDeviceProfilesIni(p: PresetProfile, opts: GeneratorOptions): String {
        val gpu = (logInfo.gpu ?: "").lowercase()
        val socText = listOfNotNull(logInfo.socName, logInfo.socCode, logInfo.cpuName, logInfo.deviceModel)
            .joinToString(" ").lowercase()
        val hasThermalIssues = logInfo.thermalEvents >= 5
        val isHighEnd = Regex("""adreno.*7\d{2}""").containsMatchIn(gpu) ||
                Regex("""adreno.*8\d{2}""").containsMatchIn(gpu) ||
                gpu.contains("mali-g7") || gpu.contains("mali-g6") || gpu.contains("mali-g615") ||
                ((logInfo.fpsCap ?: 0) >= 60 && !Regex("""adreno.*6\d{2}""").containsMatchIn(gpu))
        val isMid = Regex("""adreno.*6\d{2}""").containsMatchIn(gpu) ||
                gpu.contains("mali-g5") || gpu.contains("mali-g57") || gpu.contains("mali-g68")
        val texBias = if (p.detail > 1) 80 else if (p.detail > 0) 200 else 400
        val charOutline = if (p.detail > 1) 1200 else if (p.detail > 0) 950 else 850
        val charEyeDist = if (p.detail > 1) 700 else if (p.detail > 0) 550 else 450
        val charLODScale = if (p.detail > 1) 7.0 else if (p.detail > 0) 6.0 else 5.0
        val ismDist = if (isHighEnd) 14000 else 10000
        val ismRad = if (isHighEnd) 18000 else 13000
        val grassCull = if (isHighEnd) 2000 else if (isMid && hasThermalIssues) 600 else if (p.detail > 0) 1200 else 800

        fun profileFromChipset(): String? {
            val t = socText
            return when {
                Regex("""snapdragon\s*8\s*elite|sm8750|adreno\s*830""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("adreno 830") -> "Android_Adreno830"
                Regex("""snapdragon\s*8\s*gen\s*3|sm8650|adreno\s*750""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("adreno 750") -> "Android_Adreno750"
                Regex("""snapdragon\s*8\s*gen\s*2|sm8550|adreno\s*740""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("adreno 740") -> "Android_Adreno740"
                Regex("""snapdragon\s*8\s*\+?\s*gen\s*1|sm8475|sm8450|adreno\s*730""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("adreno 730") -> "Android_Adreno7xx"
                Regex("""snapdragon\s*7|sm7\d{3}|adreno\s*7""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""adreno\s*7""", RegexOption.IGNORE_CASE).containsMatchIn(gpu) -> "Android_Adreno7xx"
                Regex("""snapdragon\s*6|snapdragon\s*695|snapdragon\s*680|sm6\d{3}|adreno\s*6""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""adreno\s*6""", RegexOption.IGNORE_CASE).containsMatchIn(gpu) -> "Android_Adreno6xx"
                Regex("""adreno\s*5""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""adreno\s*5""", RegexOption.IGNORE_CASE).containsMatchIn(gpu) -> "Android_Adreno5xx"
                Regex("""adreno\s*4""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""adreno\s*4""", RegexOption.IGNORE_CASE).containsMatchIn(gpu) -> "Android_Adreno4xx"
                Regex("""dimensity\s*94|mali-g925""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("mali-g925") -> "Android_Mali_G925"
                Regex("""dimensity\s*93|mali-g720""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("mali-g720") -> "Android_Mali_G720"
                Regex("""dimensity\s*92|mali-g715""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("mali-g715") -> "Android_Mali_G715"
                Regex("""dimensity\s*90|mali-g710""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("mali-g710") -> "Android_Mali_G710"
                Regex("""dimensity\s*8|mali-g61[0-9]|mali-g615""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("mali-g615") -> "Android_Mali_G615"
                Regex("""dimensity\s*7|mali-g6""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""mali-g6""", RegexOption.IGNORE_CASE).containsMatchIn(gpu) -> "Android_Mali_G61x"
                Regex("""dimensity\s*6|mali-g57""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("mali-g57") -> "Android_Mali_G57"
                Regex("""exynos\s*24|xclipse\s*9""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""xclipse\s*9""", RegexOption.IGNORE_CASE).containsMatchIn(gpu) -> "Android_Xclipse9xx"
                Regex("""exynos\s*13|xclipse\s*5""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""xclipse\s*5""", RegexOption.IGNORE_CASE).containsMatchIn(gpu) -> "Android_Xclipse5xx"
                Regex("""kirin|maleoon""", RegexOption.IGNORE_CASE).containsMatchIn(t) || gpu.contains("maleoon") -> "Android_Maleoon"
                else -> null
            }
        }

        fun sanitizeProfileName(name: String?): String? {
            if (name == null) return null
            val clean = name.trim().replace(Regex("""[^A-Za-z0-9_]"""), "_")
            return if (clean.startsWith("Android_")) clean else null
        }

        val detectedProfile = sanitizeProfileName(logInfo.deviceProfile)
        val chipsetProfile = profileFromChipset()
        val presetBaseProfile = when (activePreset) {
            "performance" -> "Android_Low"
            "balanced" -> "Android_Mid"
            "high" -> "Android_VeryHigh"
            "ultra" -> "Android_ultra"
            else -> "Android_Mid"
        }

        fun universalProfilesForPreset(): List<String> = when (activePreset) {
            "performance" -> listOf("Android_Low")
            "high" -> listOf("Android_VeryHigh")
            "ultra" -> listOf("Android_ultra")
            else -> listOf("Android_Mid")
        }

        fun profileCVarLines(): List<String> {
            val lines = mutableListOf(
                "; Device tier — follows selected preset, not forced high",
                "+CVars=r.Mobile.DeviceEvaluation=${if (activePreset == "performance") 1 else if (activePreset == "balanced") 2 else 3}",
                "+CVars=r.MobileContentScaleFactor=0.0",
                "+CVars=r.SecondaryScreenPercentage.GameViewport=100",
                "",
                "; Texture LOD",
                "+CVars=r.streaming.QualityExtraLODBiasSetting=$texBias",
                "",
                "; Character quality",
                "+CVars=r.Kuro.ToonOutlineDrawDistanceMobile=$charOutline",
                "+CVars=r.Kuro.ToonEyeTransparentDrawDistanceMobile=$charEyeDist",
                "+CVars=r.Kuro.ToonFaceShadowMeshDrawDistanceMobile=$charEyeDist",
                "+CVars=r.Kuro.SkeletalMesh.LODScreenSizeScale=$charLODScale",
                "",
                "; Imposter",
                "+CVars=r.imp.SSMbScaleLod0=0.0",
                "+CVars=r.imp.SSMbScaleLod1=0.0",
                "",
                "; ISM draw distances",
                "+CVars=r.Kuro.MobileISMDecideDistance=$ismDist.0",
                "+CVars=r.Kuro.MobileISMMeshRadiusMax=$ismRad.0",
                "",
                "; Foliage cull",
                "+CVars=r.Kuro.Foliage.MobileGrassCullDistanceMax=$grassCull",
                "+CVars=r.Kuro.Foliage.MobileGrass3_0CullDistanceMax=$grassCull",
                "+CVars=r.Kuro.Foliage.MobileMiddleCullDistanceMin=${(grassCull * 1.8).toInt()}",
                "+CVars=r.Kuro.Foliage.MobileMiddleCullDistanceMax=${(grassCull * 2.2).toInt()}",
                "+CVars=r.Kuro.Foliage.MobileFarCullDistanceMin=${(grassCull * 2.8).toInt()}",
                "+CVars=r.Kuro.Foliage.MobileFarCullDistanceMax=${(grassCull * 3.2).toInt()}",
                "",
                "; FPS unlock",
                "+CVars=r.Kuro.MaxFPS.ThirdParty60=1"
            )
            if (opts.unlock120) lines.add("+CVars=r.Kuro.MaxFPS.ThirdParty120=1")
            if (opts.unlockUltra) lines.add("+CVars=r.Kuro.GraphicsQuality.ThirdPartyUltraEnable=1")
            return lines
        }

        val hasUploadedLog = logInfo.gpu != null || logInfo.deviceModel != null
        if (!hasUploadedLog) {
            val profiles = universalProfilesForPreset()
            val rootProfile = profiles[0]
            val rootBaseProfile = if (presetBaseProfile == "Android_ultra") "Android_VeryHigh" else "Android"
            val lines = mutableListOf<String>().apply {
                add(configHeader("Android", activePreset))
                add("[DeviceProfiles]")
                profiles.forEach { add("+DeviceProfileNameAndTypes=$it,Android") }
                add("")
                add("; Universal Android preset — no Client.log uploaded")
                add("; Preset base profile: $presetBaseProfile")
                add("[$rootProfile DeviceProfile]")
                add("DeviceType=Android")
                add("BaseProfileName=$rootBaseProfile")
                add("")
                addAll(profileCVarLines())
                add("")
            }
            return lines.joinToString("\n")
        }

        val profile = chipsetProfile ?: detectedProfile ?: presetBaseProfile
        val baseProfile = if (chipsetProfile != null || detectedProfile != null) presetBaseProfile else "Android"

        val lines = mutableListOf<String>().apply {
            add(configHeader("Android", activePreset))
            add("[DeviceProfiles]")
            add("+DeviceProfileNameAndTypes=$profile,Android")
            add("")
            add("; Targeted Android profile — generated from detected SoC/chipset")
            add("; GPU: ${logInfo.gpu ?: "unknown"}")
            add("; SoC: ${logInfo.socName ?: logInfo.cpuName ?: logInfo.socCode ?: "unknown"}")
            add("; Selected game profile: ${logInfo.deviceProfile ?: "unknown"}")
            add("; Preset base profile: $presetBaseProfile")
            add("[$profile DeviceProfile]")
            add("DeviceType=Android")
            add("BaseProfileName=$baseProfile")
            add("")
            addAll(profileCVarLines())
            add("")
        }
        return lines.joinToString("\n")
    }

    private fun buildAndroidGameUserSettingsIni(p: PresetProfile, opts: GeneratorOptions): String {
        val resQ = if (p.detail > 1) 100 else if (p.detail > 0) 85 else 70
        val viewQ = if (p.detail > 1) 3 else if (p.detail > 0) 2 else 1
        val shadowQ = if (p.shadow >= 4) 3 else if (p.shadow >= 2) 2 else 1
        val postQ = if (p.detail > 1) 3 else if (p.detail > 0) 2 else 1
        val texQ = if (p.detail > 1) 3 else if (p.detail > 0) 2 else 1
        val fxQ = if (p.detail > 1) 2 else if (p.detail > 0) 1 else 0
        val kuroQ = if (p.detail > 1) 3 else 2
        val aaQ = if (p.detail > 0) 2 else 1

        return listOf(
            "; WuWa GameUserSettings.ini — P42 Toolkit", "",
            "[ScalabilityGroups]",
            "sg.ResolutionQuality=$resQ",
            "sg.ViewDistanceQuality=$viewQ",
            "sg.AntiAliasingQuality=$aaQ",
            "sg.ShadowQuality=$shadowQ",
            "sg.PostProcessQuality=$postQ",
            "sg.TextureQuality=$texQ",
            "sg.EffectsQuality=$fxQ",
            "sg.FoliageQuality=${if (p.detail > 1) 2 else if (p.detail > 0) 1 else 0}",
            "sg.ShadingQuality=${if (p.detail > 1) 3 else 2}",
            "sg.KuroRenderQuality=$kuroQ",
            "sg.KuroLocalRenderQuality=0",
            "sg.RayTracingQuality=0",
            "",
            "[/Script/Engine.GameUserSettings]",
            "bUseVSync=${if (opts.vsync) "True" else "False"}",
            "bUseDynamicResolution=False",
            "ResolutionSizeX=1280",
            "ResolutionSizeY=720",
            "LastUserConfirmedResolutionSizeX=1280",
            "LastUserConfirmedResolutionSizeY=720",
            "WindowPosX=-1",
            "WindowPosY=-1",
            "FullscreenMode=1",
            "GameQualitySettingLevel=$kuroQ",
            "LastConfirmedFullscreenMode=1",
            "PreferredFullscreenMode=0",
            "Version=5",
            "AudioQualityLevel=0",
            "LastConfirmedAudioQualityLevel=0",
            "FrameRateLimit=${opts.fps}.000000",
            "FramePace=${opts.fps}",
            "DesiredScreenWidth=1280",
            "bUseDesiredScreenHeight=False",
            "DesiredScreenHeight=720",
            "LastUserConfirmedDesiredScreenWidth=1280",
            "LastUserConfirmedDesiredScreenHeight=720",
            "LastRecommendedScreenWidth=-1.000000",
            "LastRecommendedScreenHeight=-1.000000",
            "LastCPUBenchmarkResult=-1.000000",
            "LastGPUBenchmarkResult=-1.000000",
            "LastGPUBenchmarkMultiplier=1.000000",
            "bUseHDRDisplayOutput=False",
            "HDRDisplayOutputNits=1000",
            "",
            "[Internationalization]",
            "Culture=en",
            "",
            "[ShaderPipelineCache.CacheFile]",
            "LastOpened=Client"
        ).joinToString("\n")
    }
}

data class LogInfo(
    val gpu: String? = null,
    val gpuFamily: String? = null,
    val gpuTier: String? = null,
    val glVersion: String? = null,
    val driverVersion: String? = null,
    val deviceModel: String? = null,
    val socName: String? = null,
    val socCode: String? = null,
    val cpuName: String? = null,
    val ramMb: Int? = null,
    val androidVersion: String? = null,
    val os: String? = null,
    val resolution: String? = null,
    val api: String? = null,
    val vulkanStatus: String? = null,
    val deviceProfile: String? = null,
    val fpsCap: Int? = null,
    val fpsActual: Float? = null,
    val screenPct: Float? = null,
    val shadowQ: Int? = null,
    val qualityMode: String? = null,
    val kuroPostprocess: Int? = null,
    val isLowMem: Boolean? = null,
    val textureErrors: Int = 0,
    val gpuOom: Int = 0,
    val dropFrames: Int = 0,
    val forbiddenCvars: Int = 0,
    val thermalEvents: Int = 0,
    val networkErrors: Int = 0,
    val activeCvars: Map<String, String> = emptyMap()
)
