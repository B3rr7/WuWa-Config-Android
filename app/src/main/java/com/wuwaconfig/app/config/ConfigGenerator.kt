package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.CvarEntry
import com.wuwaconfig.app.model.GameMode
import com.wuwaconfig.app.model.GeneratedIni
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.model.LogInfo
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PresetProfile(
    val screen: Int,
    val shadow: Int,
    val shadowRes: Int,
    val ssr: Int,
    val mipbias: Int,
    val streaming: Double,
    val vd: Double,
    val flod: Double,
    val detail: Int,
    val lod_bias: Int,
    val grasscull: Int,
) {
    /**
     * Maps the preset's fine-grained [detail] rank onto the three boolean gates the
     * generator historically branched on (`>0` / `>1` / `>2`). Preserving this mapping
     * keeps every existing branch's meaning intact while letting the 8 presets occupy
     * distinct ranks (0..7) so high/ultra/cinematic no longer collapse to identical output.
     */
    val q0: Boolean get() = detail > 0
    val q1: Boolean get() = detail > 1
    val q2: Boolean get() = detail > 2
}

val PRESETS =
    mapOf(
        "potato" to PresetProfile(60, 0, 128, 0, 3, 0.3, 0.3, 0.4, 1, 5, 1500),
        "endurance" to PresetProfile(70, 0, 128, 0, 3, 0.4, 0.4, 0.5, 1, 4, 2500),
        "performance" to PresetProfile(60, 0, 256, 0, 3, 0.5, 0.5, 0.6, 0, 3, 4500),
        "competitive" to PresetProfile(100, 2, 256, 0, 1, 1.0, 2.0, 1.0, 1, 1, 2000),
        "balanced" to PresetProfile(80, 2, 1024, 1, 0, 2.0, 1.5, 2.0, 1, 0, 15000),
        "high" to PresetProfile(100, 4, 2048, 2, 0, 3.0, 2.0, 2.5, 2, 0, 20000),
        "ultra" to PresetProfile(100, 5, 2048, 4, -1, 4.0, 3.0, 3.0, 3, -1, 30000),
        "cinematic" to PresetProfile(100, 5, 4096, 4, -2, 6.0, 4.0, 4.0, 4, -2, 40000),
    )

class ConfigGenerator(private val cvarDatabase: CvarDatabase) {
    private val cvarPrefixes =
        listOf(
            "a.", "fx.", "foliage.", "gc.", "grass.", "kuro.", "lod.", "niagara.",
            "r.", "s.", "sg.", "wp.",
        )

    fun extractCvarNames(iniText: String): Set<String> {
        val names = linkedSetOf<String>()
        for (line in iniText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#") ||
                trimmed.startsWith("//") || trimmed.startsWith("[")
            ) {
                continue
            }

            val kv = trimmed.removePrefix("+CVars=").removePrefix("-CVars=").trim()
            val eq = kv.indexOf('=')
            if (eq <= 0) continue
            val key = kv.substring(0, eq).trim()
            val keyLower = key.lowercase()
            if (cvarPrefixes.any { keyLower.startsWith(it) }) names.add(key)
        }
        return names
    }

    fun replaceCoreSystemPaths(
        engineIni: String,
        corePaths: List<String>,
    ): String {
        val lines = engineIni.lines()
        val result = mutableListOf<String>()
        var i = 0
        var replaced = false

        while (i < lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.equals("[Core.System]", ignoreCase = true)) {
                result.addAll(corePaths)
                replaced = true
                i++
                while (i < lines.size && !lines[i].trim().startsWith("[")) i++
                continue
            }
            result.add(lines[i])
            i++
        }

        if (!replaced) {
            val insertAt =
                result.indexOfFirst { it.trim().startsWith("[SystemSettings]", ignoreCase = true) }
                    .let { if (it >= 0) it else 0 }
            result.addAll(insertAt, corePaths + "")
        }
        return result.joinToString("\n")
    }

    val DEFAULT_CORE_SYSTEM =
        listOf(
            "[Core.System]",
            "Paths=../../../Engine/Content",
            "Paths=%GAMEDIR%Content",
            "Paths=../../../Engine/Plugins/ThirdParty/ImpostorBaker/Content",
            "Paths=../../../Engine/Plugins/json2struct/Content",
            "Paths=../../../Engine/Plugins/Experimental/FieldSystemPlugin/Content",
            "Paths=../../../Client/Plugins/LGUI/LGUI/Content",
            "Paths=../../../Engine/Plugins/PrefabSystem/Content",
            "Paths=../../../Engine/Plugins/FX/Niagara/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroGameplay/Content",
            "Paths=../../../Client/Plugins/Puerts/Puerts/Content",
            "Paths=../../../Client/Plugins/Wwise/Content",
            "Paths=../../../Engine/Plugins/Editor/GeometryMode/Content",
            "Paths=../../../Engine/Plugins/MovieScene/SequencerScripting/Content",
            "Paths=../../../Engine/Plugins/Experimental/PythonScriptPlugin/Content",
            "Paths=../../../Client/Plugins/CrashSight/Content",
            "Paths=../../../Engine/Plugins/ThirdParty/QuickEditor/Content",
            "Paths=../../../Client/Plugins/Sharphereal/Content",
            "Paths=../../../Engine/Plugins/Experimental/GeometryProcessing/Content",
            "Paths=../../../Client/Plugins/Kuro/TASdkPlugin/Content",
            "Paths=../../../Client/Plugins/Kuro/KRDataAnalyticsPlugin/Content",
            "Paths=../../../Engine/Plugins/rdLODtools/Content",
            "Paths=../../../Client/Plugins/AudioMaterialPlugin/Content",
            "Paths=../../../Engine/Plugins/Runtime/Nvidia/DLSS/Content",
            "Paths=../../../Engine/Plugins/Runtime/HoudiniEngine/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroHotPatch/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroImposter/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroAutomationTool/Content",
            "Paths=../../../Engine/Plugins/FX/HoudiniNiagara/Content",
            "Paths=../../../Client/Plugins/LogicDriverLite/Content",
            "Paths=../../../Engine/Plugins/Runtime/AudioSynesthesia/Content",
            "Paths=../../../Engine/Plugins/Experimental/ControlRig/Content",
            "Paths=../../../Engine/Plugins/Media/MediaCompositing/Content",
            "Paths=../../../Engine/Plugins/Runtime/Synthesis/Content",
            "Paths=../../../Engine/Plugins/SequenceDialogue/Content",
            "Paths=../../../Client/Plugins/Puerts/ReactUMG/Content",
            "Paths=../../../Client/Plugins/genesis-ue-plugin/RenderExporter/Content",
            "Paths=../../../Engine/Plugins/KuroiOSDelegate/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroGameplayUI/Content",
            "Paths=../../../Engine/Plugins/Runtime/Nvidia/OpacityMicroMap/Content",
            "Paths=../../../Engine/Plugins/Experimental/ColorCorrectRegions/Content",
            "Paths=../../../Engine/Plugins/Compositing/OpenCVLensDistortion/Content",
            "Paths=../../../Engine/Plugins/Experimental/FastGeoStreaming/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroWorldPartition/Content",
            "Paths=../../../Client/Plugins/BlockoutToolsPlugin/Content",
            "Paths=../../../Client/Plugins/ComfyTextures/Content",
            "Paths=../../../Client/Plugins/KuroComputeShader/Content",
            "Paths=../../../Client/Plugins/KuroTDM/Content",
            "Paths=../../../Client/Plugins/Kuro/ImposterBaker/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroDynamicMeshBatch/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroGachaTools/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroPerfCat/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroPSOTools/Content",
            "Paths=../../../Client/Plugins/Kuro/KuroPushSdk/Content",
            "Paths=../../../Client/Plugins/MeshBlend/Content",
            "Paths=../../../Client/Plugins/SdkParamExtend/Content",
            "Paths=../../../Client/Plugins/SpinePlugin/Content",
            "Paths=../../../Client/Plugins/TFlow/Content",
            "Paths=../../../Client/Plugins/TpSafe/Content",
            "Paths=../../../Engine/Plugins/AFME/Content",
            "Paths=../../../Engine/Plugins/Animation/ACLPlugin/Content",
            "Paths=../../../Engine/Plugins/AssetChecker/Content",
            "Paths=../../../Engine/Plugins/AssetMemoryAnalyzer/Content",
            "Paths=../../../Engine/Plugins/DawnSDK/DawnSDK/Content",
            "Paths=../../../Engine/Plugins/Editor/SpeedTreeImporter/Content",
            "Paths=../../../Engine/Plugins/Experimental/ChaosClothEditor/Content",
            "Paths=../../../Engine/Plugins/Experimental/ChaosNiagara/Content",
            "Paths=../../../Engine/Plugins/Experimental/ChaosSolverPlugin/Content",
            "Paths=../../../Engine/Plugins/GSR/Content",
            "Paths=../../../Engine/Plugins/KuroFI/Content",
            "Paths=../../../Engine/Plugins/MagicDawn/Content",
            "Paths=../../../Engine/Plugins/MFRCModule/Content",
            "Paths=../../../Engine/Plugins/MTKCompensatedTimeStep/Content",
            "Paths=../../../Engine/Plugins/MagtModule/Content",
            "Paths=../../../Engine/Plugins/Runtime/Intel/XeSS/Content",
            "Paths=../../../Engine/Plugins/Runtime/Nvidia/NRD/Content",
        )

    fun extractCoreSystemPaths(engineIni: String?): List<String> {
        if (engineIni == null) return DEFAULT_CORE_SYSTEM
        val lines = engineIni.lines()
        val inCore = lines.indexOfFirst { it.trim().equals("[Core.System]", ignoreCase = true) }
        if (inCore == -1) return DEFAULT_CORE_SYSTEM
        val paths = mutableListOf("[Core.System]")
        for (i in (inCore + 1) until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            if (line.trim().startsWith("[")) break
            if (line.trim().startsWith("Paths=", ignoreCase = true)) paths.add(line.trimEnd())
        }
        return if (paths.size > 1) paths else DEFAULT_CORE_SYSTEM
    }

    fun configHeader(
        platform: String,
        preset: String,
        logInfo: LogInfo,
    ): String {
        val timestamp = SimpleDateFormat("yyyy.MM.dd @ HH:mm", Locale.US).format(Date())
        val device = logInfo.deviceModel ?: "Generic"
        val gpu = logInfo.gpu ?: "Generic GPU"
        return listOf(
            "; ┌───[ P42 TOOLKIT :: PERFORMANCE CONFIG ]──────────────────────────────────┐",
            "; │                                                                          │",
            "; │   ██████╗  ██╗  ██╗██████╗    [ ENGINE ] : Unreal Engine 4 / WutheringWaves│",
            "; │   ██╔══██╗ ██║  ██║╚════██╗   [ PRESET ] : ${preset.uppercase().padEnd(30)}│",
            "; │   ██████╔╝ ███████║ █████╔╝   [ DEVICE ] : ${device.padEnd(30)}│",
            "; │   ██╔═══╝  ╚════██║██╔═══╝    [ GPU    ] : ${gpu.padEnd(30)}│",
            "; │   ██║           ██║███████╗   [ TIME   ] : ${timestamp.padEnd(30)}│",
            "; │   ╚═╝           ╚═╝╚══════╝                                              │",
            "; └──────────────────────────────────────────────────────────────────────────┘",
            "",
        ).joinToString("\n")
    }

    fun generate(
        preset: String,
        opts: GeneratorOptions,
        existingEngineContent: String? = null,
        logInfo: LogInfo = LogInfo(),
        profileOverride: PresetProfile? = null,
    ): GeneratedIni {
        LogRepository.add("ConfigGenerator: generating config with preset '$preset'")
        val corePaths = if (existingEngineContent != null) extractCoreSystemPaths(existingEngineContent) else null
        LogRepository.add(
            "ConfigGenerator: preset=$preset, hasExistingEngine=${existingEngineContent != null}, optimize=${opts.optimizeWithCvarDb}",
        )
        return if (corePaths != null) {
            generateWithCorePaths(preset, opts, corePaths, logInfo, profileOverride).ini
        } else {
            generateWithCorePaths(preset, opts, DEFAULT_CORE_SYSTEM, logInfo, profileOverride).ini
        }
    }

    data class GenerateResult(
        val ini: GeneratedIni,
        val cvarNames: Set<String>,
        val activePreset: String,
    )

    fun generateWithCorePaths(
        preset: String,
        opts: GeneratorOptions,
        corePaths: List<String>,
        logInfo: LogInfo = LogInfo(),
        profileOverride: PresetProfile? = null,
    ): GenerateResult {
        val p =
            if (profileOverride != null) {
                LogRepository.add("ConfigGenerator: using profileOverride (retune)")
                profileOverride
            } else if (opts.useAdvancedGen) {
                LogRepository.add("ConfigGenerator: using CvarOptimizer per-device tuning")
                CvarOptimizer.toPresetProfile(CvarOptimizer.optimizeProfile(logInfo))
            } else {
                PRESETS[preset] ?: error("Unknown preset: $preset")
            }
        LogRepository.add("ConfigGenerator: building Engine.ini")
        val rawEngine = buildAndroidEngineIni(p, opts, corePaths, logInfo, preset)
        val engine =
            if (opts.importFromLog && logInfo.activeCvars.isNotEmpty()) {
                LogRepository.add("ConfigGenerator: merging with ${logInfo.activeCvars.size} log CVars")
                mergeWithLogCvars(rawEngine, logInfo.activeCvars, opts)
            } else {
                rawEngine
            }
        val overriddenEngine = applyCvarOverrides(engine, opts.cvarOverrides)
        val optimizedEngine = if (opts.optimizeWithCvarDb) cvarDatabase.optimizeIniText(overriddenEngine) else overriddenEngine
        LogRepository.add("ConfigGenerator: building DeviceProfiles.ini")
        val dp = buildAndroidDeviceProfilesIni(p, opts, logInfo, preset)
        val gus = buildAndroidGameUserSettingsIni(p, opts, logInfo)
        val sc = if (opts.generateScalability) buildAndroidScalabilityIni(p, opts) else ""
        val hw = if (opts.generateHardware) buildAndroidHardwareIni(p, opts, logInfo, preset) else ""
        val deduplicatedEngine = deduplicateIniText(optimizedEngine)
        var finalEngine = deduplicatedEngine
        var finalDp = dp
        var finalGus = gus
        var finalSc = sc
        var finalHw = hw
        if (!opts.allowRestrictedCvars) {
            finalEngine = ForbiddenCvars.stripForbiddenCvars(deduplicatedEngine)
            finalDp = ForbiddenCvars.stripForbiddenCvars(dp)
            finalGus = ForbiddenCvars.stripForbiddenCvars(gus)
            finalSc = if (sc.isNotBlank()) ForbiddenCvars.stripForbiddenCvars(sc) else sc
            finalHw = if (hw.isNotBlank()) ForbiddenCvars.stripForbiddenCvars(hw) else hw
            val strippedCount =
                deduplicatedEngine.lines().size - finalEngine.lines().size +
                    dp.lines().size - finalDp.lines().size +
                    gus.lines().size - finalGus.lines().size
            if (strippedCount > 0) {
                LogRepository.add("ConfigGenerator: stripped $strippedCount forbidden CVar(s) (restricted CVars OFF)", LogLevel.WARNING)
            }
        }
        val cvarNames = extractCvarNames(finalEngine)
        LogRepository.add("ConfigGenerator: generation complete", LogLevel.SUCCESS)
        return GenerateResult(
            ini = GeneratedIni(engine = finalEngine, deviceProfiles = finalDp, gameUserSettings = finalGus, scalability = finalSc, hardware = finalHw),
            cvarNames = cvarNames,
            activePreset = preset,
        )
    }

    private fun mergeWithLogCvars(
        generatedIni: String,
        logCvars: Map<String, String>,
        opts: GeneratorOptions,
    ): String {
        val generatedKeys = extractCvarNames(generatedIni).map { it.lowercase() }.toSet()
        val logLines = mutableListOf<String>()
        for ((key, value) in logCvars) {
            val kl = key.lowercase()
            if (kl.startsWith("sg.") || kl.startsWith("r.") || kl.startsWith("fx.") || kl.startsWith("foliage.") || kl.startsWith("grass.") || kl.startsWith("a.") || kl.startsWith("niagara.")) {
                if (kl !in generatedKeys) {
                    logLines.add("$key=$value")
                }
            }
        }
        if (logLines.isEmpty()) return generatedIni
        val lines = generatedIni.lines().toMutableList()
        val ssIdx = lines.indexOfLast { it.trim().equals("[SystemSettings]", ignoreCase = true) }
        val insertIdx =
            if (ssIdx >= 0) {
                var after = ssIdx + 1
                while (after < lines.size && lines[after].isBlank()) after++
                after
            } else {
                lines.size
            }
        lines.addAll(insertIdx, listOf("", "; ── IMPORTED FROM Client.log (not in preset) ─────") + logLines + listOf(""))
        return deduplicateIniText(lines.joinToString("\n"))
    }

    private fun deduplicateIniText(text: String): String {
        val lines = text.lines()
        val seen = mutableMapOf<String, Int>()
        val toRemove = mutableSetOf<Int>()
        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("[") || trimmed.startsWith("+")) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val key = trimmed.substring(0, eq).trim().lowercase()
            if (!cvarPrefixes.any { key.startsWith(it) }) continue
            val prev = seen[key]
            if (prev != null) toRemove.add(prev)
            seen[key] = i
        }
        if (toRemove.isEmpty()) return text
        return lines.filterIndexed { i, _ -> i !in toRemove }.joinToString("\n")
    }

    private data class DeviceTier(
        val isHighEnd: Boolean,
        val isMid: Boolean,
        val hasThermalIssues: Boolean,
        val streamPool: Int,
        val maxAniso: Int,
        val landscapeCaptureDist: Int,
        val skinCacheMem: Int,
        val ismDist: Int,
        val ismRad: Int,
        val grassCull: Int,
        val npcDist: Int,
    ) {
        companion object {
            private val HIGH_END = TierValues(800, 16, 8000, 384, 14000, 18000, 15000)
            private val MID = TierValues(500, 8, 6000, 256, 10000, 13000, 10000)
            private val LOW = TierValues(380, 4, 4000, 192, 7000, 9000, 7000)

            private data class TierValues(
                val streamPool: Int,
                val maxAniso: Int,
                val landscapeCaptureDist: Int,
                val skinCacheMem: Int,
                val ismDist: Int,
                val ismRad: Int,
                val npcDist: Int,
            )

            fun fromTier(
                isHighEnd: Boolean,
                isMid: Boolean,
                hasThermalIssues: Boolean,
            ): DeviceTier {
                val values =
                    if (isHighEnd) {
                        HIGH_END
                    } else if (isMid) {
                        MID
                    } else {
                        LOW
                    }
                val grassCull =
                    when {
                        isHighEnd -> 2000
                        isMid && hasThermalIssues -> 600
                        isMid -> 1200
                        else -> 800
                    }
                return DeviceTier(
                    isHighEnd = isHighEnd,
                    isMid = isMid,
                    hasThermalIssues = hasThermalIssues,
                    streamPool = values.streamPool,
                    maxAniso = values.maxAniso,
                    landscapeCaptureDist = values.landscapeCaptureDist,
                    skinCacheMem = values.skinCacheMem,
                    ismDist = values.ismDist,
                    ismRad = values.ismRad,
                    grassCull = grassCull,
                    npcDist = values.npcDist,
                )
            }
        }
    }

    private fun computeDeviceTier(logInfo: LogInfo): DeviceTier {
        val gpu = (logInfo.gpu ?: "").lowercase()
        val hasThermalIssues = logInfo.thermalEvents >= 5
        val isHighEnd =
            Regex("""adreno.*7\d{2}""").containsMatchIn(gpu) ||
                Regex("""adreno.*8\d{2}""").containsMatchIn(gpu) ||
                Regex("""mali-g(7\d{1,2}|8\d{1,2}|9\d{1,2})""").containsMatchIn(gpu)
        val isMid =
            Regex("""adreno.*6\d{2}""").containsMatchIn(gpu) ||
                Regex("""mali-g(5\d{1,2}|6\d{1,2})""").containsMatchIn(gpu)
        return DeviceTier.fromTier(isHighEnd, isMid, hasThermalIssues)
    }

    private fun buildAndroidEngineIni(
        p: PresetProfile,
        opts: GeneratorOptions,
        coreSystemPaths: List<String>? = null,
        logInfo: LogInfo = LogInfo(),
        activePreset: String = "balanced",
    ): String {
        val dt = computeDeviceTier(logInfo)
        val hasVulkan = logInfo.vulkanStatus == "available"
        val corePaths = coreSystemPaths ?: DEFAULT_CORE_SYSTEM
        val ctx = EngineIniContext(p, opts, corePaths, dt, hasVulkan, activePreset)
        val lines = mutableListOf<String>()
        lines.add(configHeader("Android", activePreset, logInfo))
        lines.add("")
        ctx.corePaths.forEach { lines.add(it) }
        lines.add("")
        lines.add("[SystemSettings]")
        lines.add("")
        lines.addAll(buildCharacterQualitySection(ctx))
        lines.addAll(buildAntiAliasingSection(ctx))
        lines.addAll(buildPostProcessingSection(ctx))
        lines.addAll(buildShadowSection(ctx))
        lines.addAll(buildTextureStreamingSection(ctx))
        lines.addAll(buildMobileRenderingSection(ctx))
        lines.addAll(buildVrsSection(ctx))
        lines.addAll(buildEffectsParticlesSection(ctx))
        lines.addAll(buildWaterReflectionSection(ctx))
        lines.addAll(buildScreenSpaceEffectsSection(ctx))
        lines.addAll(buildEnvironmentSection(ctx))
        lines.addAll(buildNpcWorldSection(ctx))
        lines.addAll(buildAdvancedLodCullingSection(ctx))
        lines.addAll(buildAnimationBlueprintSection(ctx))
        lines.addAll(buildFrameDisplaySection(ctx))
        lines.addAll(buildPipelineRhiSection(ctx))
        lines.addAll(buildThermalStabilitySection(ctx))
        lines.addAll(buildForbiddenCvarOverridesSection(ctx))
        lines.addAll(buildPerformanceTweaksSection(ctx))
        lines.addAll(buildExperimentalCvarsSection(ctx))
        lines.addAll(buildEnrichmentCvars(p, opts))
        lines.addAll(buildGameModeToaSection(ctx))
        lines.add("[/Script/Engine.StreamingSettings]")
        lines.add("s.TimeLimitExceededMultiplier=1.5")
        lines.add("s.AsyncLoadingThreadEnabled=1")
        lines.add("s.EventDrivenLoaderEnabled=1")
        lines.add("")
        lines.add("[/Script/Engine.GarbageCollectionSettings]")
        lines.add("gc.LowMemory.TimeBetweenPurgingPendingLevels=20")
        lines.add("")
        lines.addAll(buildGsrSection(ctx))
        return lines.joinToString("\n")
    }

    private data class EngineIniContext(
        val p: PresetProfile,
        val opts: GeneratorOptions,
        val corePaths: List<String>,
        val dt: DeviceTier,
        val hasVulkan: Boolean,
        val activePreset: String = "balanced",
    ) {
        val charOutline: Int = if (p.q1) 1200 else if (p.q0) 950 else 850
        val charEyeDist: Int = if (p.q1) 700 else if (p.q0) 550 else 450
        val charLODScale: Double = if (p.q1) 7.0 else if (p.q0) 6.0 else 5.0
        val niagQ: Int = if (p.q1) 2 else 1
        val shadowCascade: Int = if (p.shadow >= 4) 3 else 2
        val shadowSkLOD: Int = if (p.shadow >= 4) 1 else 2
    }

    private fun buildCharacterQualitySection(ctx: EngineIniContext): List<String> {
        val c = ctx
        val outlineScale = if (c.opts.disableOutline) "0" else if (c.p.q1) "1.3" else if (c.p.q0) "1.2" else "1.1"
        val autoExposure = if (c.opts.disableAutoExposure) "0" else "1"
        val radialBlur = if (c.opts.disableRadialBlur) "0" else if (c.p.q1) "0.9" else if (c.p.q0) "0.75" else "0.6"
        val landscapeCaptureSize = if (c.p.q0) 2 else 1
        return listOf(
            "; ── CHARACTER QUALITY ─────────────────────────────────",
            "r.Shadow.SkeletalMeshLODBias=${c.shadowSkLOD}",
            "r.Kuro.SkeletalMesh.LODScreenSizeScale=${c.charLODScale}",
            "r.Mobile.KuroPostprocess=1",
            "r.Mobile.TonemapperFilm=1",
            "r.Kuro.ToonOutlineDrawDistanceMobile=${c.charOutline}",
            "r.Kuro.ToonEyeTransparentDrawDistanceMobile=${c.charEyeDist}",
            "r.Kuro.ToonFaceShadowMeshDrawDistanceMobile=${c.charEyeDist}",
            "r.Mobile.OutlineScale=$outlineScale",
            "r.Kuro.AutoExposure=$autoExposure",
            "r.Kuro.RadialBlur.MobileIntensityScalar=$radialBlur",
            "Kuro.Blueprint.EnableGameBudget=0",
            "r.Mobile.TreeRimLight=1",
            "r.Kuro.LandscapeCapture=1",
            "r.Kuro.LandscapeCaptureDistance=${c.dt.landscapeCaptureDist}",
            "r.Mobile.Kuro.LandscapeCaptureSize=$landscapeCaptureSize",
            "",
        )
    }

    private fun buildAntiAliasingSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        return listOf(
            "; ── ANTI-ALIASING ────────────────────────────────────",
            "r.PostProcessAAQuality=6",
            "r.TemporalAA.Upsampling=1",
            "r.TemporalAA.Algorithm=1",
            "r.TemporalAACatmullRom=1",
            "r.TemporalAACurrentFrameWeight=0.25",
            "r.TemporalAAFilterSize=0.5",
            "r.TemporalAAPauseCorrect=1",
            "r.TemporalAA.MobileFrameWeight=${if (p.q1) 0.08 else 0.12}",
            "r.TemporalAA.MobileStaticFrameWeight=${if (p.q1) 0.3 else 0.5}",
            "r.DefaultFeature.AntiAliasing=2",
            "",
        )
    }

    private fun buildPostProcessingSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        val opts = ctx.opts
        return listOf(
            "; ── POST PROCESSING ──────────────────────────────────",
            "r.BloomQuality=${if (opts.disableBloom) 0 else if (p.q1) 4 else if (p.q0) 3 else 1}",
            "r.EyeAdaptationQuality=2",
            "r.MotionBlurQuality=0",
            "r.DepthOfFieldQuality=${if (p.q1) 2 else if (p.q0) 1 else 0}",
            "r.LightShaftQuality=${if (p.q0) 1 else 0}",
            "r.LensFlareQuality=0",
            "r.SceneColorFringeQuality=${if (opts.ca) 1 else 0}",
            "r.Tonemapper.GrainQuantization=0",
            "r.DisableDistortion=${if (p.q1) 0 else 1}",
            "r.AmbientOcclusionLevels=${if (p.q1) 1 else 0}",
            "r.KuroTonemapping=3",
            "r.Kuro.KuroBloomEnable=${if (opts.disableBloom) 0 else 1}",
            "r.Kuro.KuroEnableFFTBloom=${if (opts.disableBloom) 0 else if (p.q1) 1 else 0}",
            "r.Kuro.KuroEnableToonFFTBloom=0",
            "r.Kuro.KuroBloomStreak=${if (p.q1) 1 else 0}",
            "r.LightShaftDownSampleFactor=${if (p.q1) 2 else 4}",
            "r.Tonemapper.Quality=4",
            "r.Upscale.Quality=3",
            "",
        )
    }

    private fun buildShadowSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        val sc = ctx.shadowCascade
        return listOf(
            "; ── SHADOW ───────────────────────────────────────────",
            "r.Shadow.KuroEnablePointLightShadow=${if (p.shadow >= 3) 1 else 0}",
            "r.Shadow.CSM.MaxMobileCascades=$sc",
            "r.Shadow.RadiusThresholdFar=${if (p.shadow >= 3) "0.06" else "0.12"}",
            "r.Shadow.UnbuiltPreviewInGame=1",
            "r.Kuro.GlobalLightQuality_PC=${if (p.shadow >= 4) 4 else if (p.shadow >= 2) 3 else 2}",
            "r.Kuro.GlobalLightShadowQuality_PC=${if (p.shadow >= 4) 4 else if (p.shadow >= 2) 3 else 2}",
            "r.Shadow.RadiusThreshold=${if (p.shadow >= 3) 0.06 else 0.12}",
            "r.Shadow.PerObjectResolutionMax=${if (p.shadow >= 3) 2048 else if (p.shadow >= 2) 1024 else 512}",
            "r.Shadow.MaxResolution=${if (p.shadow >= 3) 2048 else if (p.shadow >= 2) 1024 else 512}",
            "r.Shadow.RadiusThresholdOverrideEnable=0",
            "r.Shadow.PerObjectResolutionMin=64",
            "r.MobileNumDynamicPointLights=2",
            "r.Shadow.SinglePass=1",
            "r.Shadow.DirectLightCacheMaxKeepFrameInterval=1",
            "r.Shadow.ForceSerialSingleRenderPass=0",
            "",
        )
    }

    private fun buildTextureStreamingSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        val dt = ctx.dt
        return listOf(
            "; ── TEXTURE STREAMING ────────────────────────────────",
            "r.TextureStreaming=1",
            "r.Streaming.MipBias=${if (p.mipbias < 0) 0 else p.mipbias}",
            "r.MaxAnisotropy=${dt.maxAniso}",
            "r.streaming.TexturePoolSizeMode=1",
            "r.Streaming.KuroMinFOVFactorForStreaming=0.2",
            "r.Streaming.GroupBoost.MediumNpcTextureFactor=${if (p.q0) "1.5" else "1.2"}",
            "r.Streaming.PoolSizeForMeshes=${(dt.streamPool * 0.3).toInt()}",
            "r.Streaming.UsingKuroStreamingPriority=2",
            "r.Streaming.AmortizeCPUToGPUCopy=1",
            "r.Streaming.DefragDynamicBounds=1",
            "r.Streaming.CheckBuildStatus=0",
            "r.Streaming.UseAllMips=${if (p.mipbias > 1) 0 else 1}",
            "",
        )
    }

    private fun buildMobileRenderingSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        return listOf(
            "; ── MOBILE RENDERING ─────────────────────────────────",
            "r.Mobile.ShadingPath=1",
            "r.Mobile.UseFSRUpscale=${if (p.q1) 0 else 1}",
            "r.MobileMSAA=0",
            "r.Mobile.HBAO=${if (p.q0) 1 else 0}",
            "r.Mobile.HBAO.BlurType=1",
            "r.Mobile.HBAO.LargeAOFactor=0.5",
            "r.Mobile.HBAO.SmallAOFactor=1.0",
            "r.Mobile.PixelProjectedReflectionQuality=${if (p.q1) 1 else 0}",
            "r.Mobile.EnableStaticAndCSMShadowReceivers=1",
            "",
        )
    }

    private fun buildVrsSection(ctx: EngineIniContext): List<String> {
        return listOf(
            "; ── VRS (Variable Rate Shading) ───────────────────────",
            "r.VRS.EnableMaterial=1",
            "r.VRS.EnableMesh=1",
            "",
        )
    }

    private fun buildEffectsParticlesSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        return listOf(
            "; ── EFFECTS / PARTICLES (GPU particle offload for thermal/perf) ──",
            "fx.KuroUseGPUParticles=0",
            "Niagara.GPUDrawIndirectArgsBufferSlack=4096",
            "fx.Niagara.QualityLevel=${ctx.niagQ}",
            "r.EmitterSpawnRateScale=${if (p.q1) "1.0" else if (p.q0) "0.8" else "0.6"}",
            "FX.MaxCPUParticlesPerEmitter=${if (p.q1) 100 else 50}",
            "FX.MaxGPUParticlesSpawnedPerFrame=${if (p.q1) 4096 else 2048}",
            "",
        )
    }

    private fun buildWaterReflectionSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        val dt = ctx.dt
        val lines = mutableListOf<String>()
        lines.add("; ── WATER / REFLECTION ───────────────────────────────")
        if (ctx.opts.disableSSR) {
            lines.add("; SSR disabled by user toggle")
            lines.add("r.Mobile.WaterSSR=0")
            lines.add("r.Mobile.WaterSSRStep=0")
            lines.add("r.Mobile.SSR=0")
            lines.add("r.Mobile.SceneObjMobileSSR=0")
            lines.add("r.Kuro.EnablePlanarReflection=0")
        } else {
            lines.add("r.Mobile.WaterSSR=${if (dt.isHighEnd && p.q0) 1 else 0}")
            lines.add("r.Mobile.WaterSSRStep=${if (p.q1) 12 else 8}")
            lines.add("r.Mobile.SSR=${if (dt.isHighEnd && p.q0) 1 else 0}")
            lines.add("r.Mobile.SceneObjMobileSSR=${if (dt.isHighEnd && p.q1) 1 else 0}")
            lines.add("r.Kuro.EnablePlanarReflection=${if (dt.isHighEnd && p.q1) 1 else 0}")
            lines.add("r.SSR.MaxRoughness=${if (p.q1) 1.0 else 0.6}")
            lines.add("r.SSR.HalfResSceneColor=1")
        }
        lines.add("r.DistanceFieldAO=0")
        lines.add("")
        return lines
    }

    private fun buildScreenSpaceEffectsSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        val dt = ctx.dt
        val lines = mutableListOf<String>()
        lines.add("; ── SCREEN-SPACE EFFECTS ────────────────────────────")
        lines.add("r.SSGI.Enable=${if (p.q1) 1 else 0}")
        lines.add("r.SubsurfaceScattering=${if (p.q1) 1 else 0}")
        lines.add("r.SSFS.HighQuality=${if (p.q1) 1 else 0}")
        lines.add("r.SSFS.FullPrecision=${if (p.q1) 1 else 0}")
        lines.add("r.SSS.HalfRes=${if (p.q1) 0 else 1}")
        lines.add("r.SSS.Quality=${if (p.q1) 2 else 1}")
        if (p.detail >= 4) {
            lines.add("; Cinematic premium — flagship only")
            lines.add("r.Kuro.EnablePlanarReflection=1")
            lines.add("r.ContactShadows=1")
            lines.add("r.SSGI.Enable=${if (dt.isHighEnd) 1 else 0}")
        }
        lines.add("foliage.DitheredLOD=1")
        lines.add("r.Shadow.MinResolution=64")
        lines.add("r.Shadow.FadeResolution=128")
        lines.add("r.Shadow.TexelsPerPixel=${if (p.q2) 2.0 else if (p.q0) 1.5 else 1.0}")
        lines.add("")
        return lines
    }

    private fun buildEnvironmentSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        val dt = ctx.dt
        val lines = mutableListOf<String>()
        lines.add("; ── ENVIRONMENT ──────────────────────────────────────")
        if (ctx.opts.fog) {
            lines.add("r.Fog=0")
            lines.add("r.KuroVolumeCloudEnable=0")
        } else {
            lines.add("r.Fog=1")
            lines.add("r.KuroVolumeCloudEnable=1")
        }
        lines.add("r.Kuro.SuperFarFogGlobalDistanceScale=${if (p.q1) 1 else 0}")
        lines.add("r.LightFunctionQuality=1")
        lines.add("r.Kuro.LightFunction=1")
        lines.add("r.FogVisibilityCulling.Enable=1")
        lines.add("r.FogVisibilityCulling.Opacity=${if (p.q1) "0.8" else "0.5"}")
        lines.add("foliage.LODOptimize=1")
        lines.add("r.EnableAggressivePVS=1")
        lines.add("r.Kuro.MobileISMDecideDistance=${dt.ismDist}.0")
        lines.add("r.Kuro.MobileISMMeshRadiusMax=${dt.ismRad}.0")
        lines.add("r.Kuro.Foliage.MobileGrassCullDistanceMax=${dt.grassCull}")
        lines.add("r.Kuro.Foliage.MobileGrass3_0CullDistanceMax=${dt.grassCull}")
        lines.add("r.Kuro.Foliage.MobileMiddleCullDistanceMin=${(dt.grassCull * 1.8).toInt()}")
        lines.add("r.Kuro.Foliage.MobileMiddleCullDistanceMax=${(dt.grassCull * 2.2).toInt()}")
        lines.add("r.Kuro.Foliage.MobileFarCullDistanceMin=${(dt.grassCull * 2.8).toInt()}")
        lines.add("r.Kuro.Foliage.MobileFarCullDistanceMax=${(dt.grassCull * 3.2).toInt()}")
        lines.add("foliage.DensityScale=${if (dt.isHighEnd && p.q1) 1.5 else if (p.q0) 1.0 else 0.6}")
        lines.add("grass.DensityScale=${if (dt.isHighEnd && p.q1) 1.5 else if (p.q0) 1.0 else 0.6}")
        lines.add("foliage.LODDistanceScale=${if (p.q1) 1.2 else if (p.q0) 1.0 else 0.7}")
        lines.add("")
        return lines
    }

    private fun buildNpcWorldSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        val dt = ctx.dt
        return listOf(
            "; ── NPC & WORLD ──────────────────────────────────────",
            "r.Kuro.NpcDisappearDistance=${dt.npcDist}",
            "r.LandscapeReverseLODScaleFactor=${if (p.q1) 2 else 3}",
            "r.LandscapeLOD0ScreenSizeScale=2",
            "r.KuroMaxFOVForLOD=${if (p.q1) 85 else 80}",
            "r.MDCFallback.EnabledLOD=1",
            "r.BBM.LODBias=${if (p.q1) 0 else 1}",
            "lod.TemporalLag=1",
            "r.RenderTargetPoolMin=${if (p.q1) 150 else if (p.q0) 80 else 64}",
            "r.Streaming.FullyLoadUsedTextures=${if (p.q0) 1 else 0}",
            "r.AllowPrecomputedVisibility=1",
            "r.HZBOcclusion=${if (ctx.opts.hzb) 1 else 0}",
            "r.EnableMeshPassProcessorsCache=1",
            "r.EnableGetDynElemsCache=1",
            "r.MorphTarget.EnableSplit=1",
            "r.MorphTarget.UnloadDelayTime=${if (p.q1) 30 else if (p.q0) 10 else 3}",
            "",
        )
    }

    private fun buildAdvancedLodCullingSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        return listOf(
            "; ── ADVANCED LOD / CULLING ──────────────────────────",
            "r.CullDistanceVolume.Enable=1",
            "r.UseClusteredDeferredShading=1",
            "r.AllowOcclusionQueries=1",
            "r.MinScreenRadiusForLights=${if (p.q1) 0.02 else 0.04}",
            "r.MinScreenRadiusForCSMDepth=${if (p.q1) 0.01 else 0.02}",
            "r.StaticMeshLODDistanceScale=${if (p.q1) 1.0 else if (p.q0) 0.85 else 0.7}",
            "r.ScreenSizeCullRatioFactor=${if (p.q1) 0.5 else 3.0}",
            "r.ParallelFrustumCull=1",
            "wp.Runtime.PlannedLoadingRangeScale=${if (p.q1) 5 else if (p.q0) 3 else 2}",
            "",
        )
    }

    private fun buildAnimationBlueprintSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        return listOf(
            "; ── ANIMATION & BLUEPRINT ───────────────────────────",
            "a.URO.Enable=1",
            "a.URO.ForceAnimRate=${if (p.q1) 1 else 0}",
            "a.URO.ForceInterpolation=1",
            "",
        )
    }

    private fun buildFrameDisplaySection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        val dt = ctx.dt
        return listOf(
            "; ── FRAME & DISPLAY ──────────────────────────────────",
            "r.MobileHDR=1",
            "r.VSync=${if (ctx.opts.vsync) 1 else 0}",
            "r.SkinCache.SceneMemoryLimitInMB=${dt.skinCacheMem}",
            "r.ShaderPipelineCache.Enabled=1",
            "r.ShaderPipelineCache.PrecompileCheckCacheHash=1",
            "r.ShaderPipelineCache.BatchSize=128",
            "r.PSO.CompilationMode=0",
            "r.kuro.LGUIBlurTexture.save=0",
            "r.KuroFI.Enable=${if (p.q1) 1 else 0}",
            "r.FinishCurrentFrame=0",
            "r.DontLimitOnBattery=1",
            "",
        )
    }

    private fun buildPipelineRhiSection(ctx: EngineIniContext): List<String> {
        return listOf(
            "; ── PIPELINE / RHI ───────────────────────────────────",
            "r.PSO.CacheEvictScheme=1",
            "r.pso.evictiontime=20",
            "r.RHICmdBypass=1",
            "r.RHICmdUseParallelAlgorithms=1",
            "r.RHICmdUseThread=1",
            "r.RHICmdAsyncRHIThreadDispatch=1",
            "",
        )
    }

    private fun buildThermalStabilitySection(ctx: EngineIniContext): List<String> {
        val opts = ctx.opts
        val dt = ctx.dt
        val lines = mutableListOf<String>()
        lines.add("; ── THERMAL & STABILITY ──────────────────────────────")
        if (opts.disableAutoAdjust) {
            lines.add("; Auto quality adjustment disabled by user")
            lines.add("r.Kuro.AutoCoolEnable=0")
            lines.add("r.Kuro.AutoCoolUIEnable=0")
            lines.add("r.Kuro.AutoExposure=0")
            lines.add("t.MaxFPS=${opts.fps}")
        } else {
            lines.add("r.Kuro.AutoCoolEnable=${if (opts.cool) 1 else 0}")
            lines.add("r.Kuro.AutoCoolUIEnable=${if (opts.cool) 1 else 0}")
        }
        if (dt.hasThermalIssues) {
            lines.add("; Thermal throttle detected in log — applying safeguards")
            lines.add("r.Kuro.AutoCoolEnable=1")
            lines.add("r.Kuro.AutoCoolUIEnable=1")
            lines.add("r.Kuro.AutoCoolCpuTempThreshold=48")
        }
        if (ctx.hasVulkan || opts.vulkan) {
            lines.add("r.Vulkan.RobustBufferAccess=1")
            lines.add("r.Vulkan.DescriptorSetLayoutMode=2")
            lines.add("r.Vulkan.PipelineLRUCapactiy=128")
        } else {
            lines.add("; Vulkan not detected")
        }
        lines.add("")
        return lines
    }

    private fun buildForbiddenCvarOverridesSection(ctx: EngineIniContext): List<String> {
        return listOf(
            "; ── FORBIDDEN CVAR OVERRIDES ──────────────────────────",
            "; Disabling known problematic CVars detected in log",
            "r.FidelityFX.FSR.RCAS.Enabled=0",
            "r.TemporalAA.Sharpness=0",
            "r.Mobile.SSAO=0",
            "r.DefaultFeature.LensFlare=0",
            "",
        )
    }

    private fun buildPerformanceTweaksSection(ctx: EngineIniContext): List<String> {
        val p = ctx.p
        val preset = ctx.activePreset
        val lines = mutableListOf<String>()
        if (preset == "potato" || preset == "endurance" || preset == "performance") {
            lines.add("; ── PERFORMANCE TWEAKS ───────────────────────────")
            lines.add("; HZB occlusion — skip rendering hidden objects (saves GPU)")
            lines.add("r.HZBOcclusion=1")
            lines.add("")
            lines.add("; Kill reflection environments, light functions, local light specular")
            lines.add("r.ReflectionEnvironment=0")
            lines.add("r.LightFunctionQuality=0")
            lines.add("r.Mobile.DisableLocalLightSpecularDistance=0")
            if (preset != "endurance") {
                lines.add("r.Mobile.EnableStaticAndCSMShadowReceivers=0")
            } else {
                lines.add("; Endurance keeps static shadow receivers (cheap static lighting)")
                lines.add("r.Mobile.EnableStaticAndCSMShadowReceivers=1")
            }
            lines.add("")
            lines.add("; Dynamic / movable light reduction")
            lines.add("r.MobileNumDynamicPointLights=0")
            lines.add("r.Mobile.AllowMovableDirectionalLights=1")
            lines.add("r.Mobile.EnableMovableSpotlights=0")
            lines.add("r.Mobile.EnableMovableSpotLights=0")
            lines.add("r.Mobile.EnableMovableSpotlightsShadow=0")
            lines.add("r.Mobile.EnableKuroSpotlightsShadow=0")
            lines.add("r.Mobile.EnableMovableLightCSMShaderCulling=1")
            lines.add("")
            lines.add("; Shadow quality — absolute minimum")
            lines.add("r.ShadowQuality=1")
            lines.add("r.Shadow.CSM.MaxCascades=1")
            lines.add("r.Shadow.CSM.MaxMobileCascades=1")
            lines.add("r.Shadow.MaxResolution=512")
            lines.add("r.Shadow.PerObjectResolutionMax=256")
            lines.add("r.Shadow.MinResolution=32")
            lines.add("r.Shadow.TexelsPerPixel=0.5")
            lines.add("r.Shadow.RadiusThreshold=0.08")
            lines.add("r.Shadow.DistanceScale=0.4")
            lines.add("r.Shadow.CSM.TransitionScale=0.3")
            lines.add("")
            lines.add("; Heavy lighting systems off")
            lines.add("r.DistanceFieldShadowing=0")
            lines.add("r.CapsuleShadows=0")
            lines.add("r.ContactShadows=0")
            lines.add("r.VolumetricFog=0")
            lines.add("r.LightShaftDownSampleFactor=8")
            lines.add("")
            lines.add("; Screen-space effects — minimum")
            lines.add("r.SSGI.Enable=0")
            lines.add("r.SubsurfaceScattering=0")
            lines.add("r.SSR.HalfResSceneColor=1")
            lines.add("r.SSR.MaxRoughness=0.4")
            lines.add("r.EyeAdaptationQuality=0")
            lines.add("")
            lines.add("; LOD & culling — aggressive")
            lines.add("r.LandscapeLOD0ScreenSizeScale=3")
            lines.add("r.MinScreenRadiusForLights=0.06")
            lines.add("r.MinScreenRadiusForCSMDepth=0.03")
            lines.add("r.StaticMeshLODDistanceScale=${"%.2f".format(1.0 + p.lod_bias * 0.1)}")
            lines.add("r.ScreenSizeCullRatioFactor=5.0")
            lines.add("foliage.DensityScale=0.5")
            lines.add("grass.DensityScale=0.4")
            lines.add("foliage.LODDistanceScale=${"%.2f".format(0.6 + p.lod_bias * 0.1)}")
            lines.add("")
            lines.add("; Thermal, bloom, volumetric clouds & misc")
            lines.add("r.Kuro.KuroEnableFFTBloom=0")
            lines.add("r.Kuro.KuroBloomStreak=0")
            lines.add("r.KuroVolumeCloudEnable=0")
            lines.add("r.Kuro.AutoCoolEnable=1")
            lines.add("a.URO.ForceAnimRate=0")
            lines.add("")
        }
        return lines
    }

    private fun buildExperimentalCvarsSection(ctx: EngineIniContext): List<String> {
        val lines = mutableListOf<String>()
        if (ctx.opts.experimentalCvars) {
            lines.add("; ── EXPERIMENTAL CVars (verified on Adreno 618) ─────")
            lines.add("r.renderswitch.water=0")
            lines.add("r.renderswitch.hlod=0")
            lines.add("r.renderswitch.character=0")
            lines.add("r.renderswitch.gridhlod=0")
            lines.add("r.kuro.hidehlod=1")
            lines.add("r.kuro.waterraindrop=0")
            lines.add("r.kuro.enablekurovolumegodray=0")
            lines.add("r.kuro.temporaryenablefsr=0")
            lines.add("r.kuro.lensflarecolorthresholdrange=999")
            lines.add("r.kuro.grassinteractionrange=0")
            lines.add("r.kuro.basepassvelocity=1")
            lines.add("r.mobile.enablewater=0")
            lines.add("r.mobile.usescreenpassssr=0")
            lines.add("r.mobile.hzb=1")
            lines.add("r.mobile.enablemobiledeferredlighting=0")
            lines.add("r.mobile.enablelandscapessr=0")
            lines.add("r.imp.kuroimposterskipifiobusy=1")
            lines.add("r.kuro.landscapeusemodifiedlod=2")
            lines.add("r.mobile.enableoutlinevelocity=0")
            lines.add("r.kuro.kurodisabletoonvelocity=1")
            lines.add("r.mobile.basepassvelocity=0")
            lines.add("r.mobile.rendervelocity=0")
            lines.add("r.mobile.enablestaticmeshvelocity=0")
            lines.add("")
        }
        return lines
    }

    private fun buildGameModeToaSection(ctx: EngineIniContext): List<String> {
        val lines = mutableListOf<String>()
        if (ctx.opts.mode == GameMode.ToA) {
            lines.add("")
            lines.add("; ── GAME MODE: TOWER OF ADVERSITY ─────────────────")
            lines.add("; Closed boss/echo arena — fewer open-world objects to render,")
            lines.add("; so environment is lightened while character/boss quality is kept.")
            lines.add("r.Fog=0")
            lines.add("r.KuroVolumeCloudEnable=0")
            lines.add("r.VolumetricFog=0")
            lines.add("foliage.DensityScale=0.35")
            lines.add("grass.DensityScale=0.35")
            lines.add("foliage.LODDistanceScale=0.6")
            lines.add("r.Kuro.Foliage.MobileGrassCullDistanceMax=2500")
            lines.add("r.Kuro.Foliage.MobileGrass3_0CullDistanceMax=2500")
            lines.add("r.Kuro.Foliage.MobileMiddleCullDistanceMin=3500")
            lines.add("r.Kuro.Foliage.MobileMiddleCullDistanceMax=4500")
            lines.add("r.Kuro.Foliage.MobileFarCullDistanceMin=5500")
            lines.add("r.Kuro.Foliage.MobileFarCullDistanceMax=6500")
            lines.add("r.Kuro.NpcDisappearDistance=8000")
            lines.add("r.Kuro.MobileISMDecideDistance=12000.0")
            lines.add("r.Kuro.MobileISMMeshRadiusMax=200.0")
            lines.add("r.Mobile.WaterSSR=0")
            lines.add("r.Mobile.SSR=0")
            lines.add("r.Mobile.SceneObjMobileSSR=0")
            lines.add("r.Kuro.EnablePlanarReflection=0")
            lines.add("r.MobileNumDynamicPointLights=1")
        }
        return lines
    }

    private fun buildGsrSection(ctx: EngineIniContext): List<String> {
        val lines = mutableListOf<String>()
        if (ctx.opts.enableGSR) {
            lines.add("; ── GAME SUPER RESOLUTION (GSR Upscaling) ───────")
            lines.add("[/Script/GSRTUModule.GSRSettings]")
            lines.add("r.sgsr2.enabled=1")
            lines.add("r.sgsr2.history=1")
            lines.add("r.sgsr2.tunemipbias=0")
            lines.add("")
        }
        return lines
    }

    /**
     * DB-verified enrichment: emits additional, additive CVars drawn from the game's CVar
     * dump. Every key is confirmed to exist via [CvarDatabase.isKnown] before emission, so
     * no fabricated CVars leak into the config. Values are scaled by preset tier using the
     * q0/q1/q2 gates already used everywhere else in the builder.
     */
    private fun buildEnrichmentCvars(
        p: PresetProfile,
        opts: GeneratorOptions,
    ): List<String> {
        val out = mutableListOf<String>()
        out.add("")
        out.add("; ── CURATED ENRICHMENT (DB-verified, scaled by preset) ────")

        fun emit(
            key: String,
            value: String,
        ) {
            if (cvarDatabase.isKnown(key)) out.add("$key=$value")
        }

        val shadowFade =
            when {
                p.q1 -> "1.5"
                p.q0 -> "2.0"
                else -> "2.5"
            }
        val shadowDensity =
            when {
                p.q1 -> "0.65"
                p.q0 -> "0.5"
                else -> "0.35"
            }
        val boostThreads = if (p.q1) "75" else "50"
        val softwareOcclusion = if (p.q0) "1" else "0"
        val permPool = if (p.q1) "4000000" else "2000000"

        // Lighting & shadow (complement the per-preset shadow scalars)
        emit("r.Shadow.CSM.Enable", "1")
        emit("r.Shadow.FadeExponent", shadowFade)
        emit("r.Shadow.WholeSceneShadowDensity", shadowDensity)
        // Texture streaming throughput
        emit("r.Streaming.AsyncLoadingTimeLimit", "0")
        emit("r.Streaming.BoostWorkerThreadsPercentage", boostThreads)
        // Mobile occlusion
        emit("r.Mobile.AllowSoftwareOcclusion", softwareOcclusion)
        // Garbage collection / memory headroom
        emit("gc.SizeOfPermanentObjectPool", permPool)
        return out
    }

    private fun buildAndroidDeviceProfilesIni(
        p: PresetProfile,
        opts: GeneratorOptions,
        logInfo: LogInfo = LogInfo(),
        activePreset: String = "balanced",
    ): String {
        val dt = computeDeviceTier(logInfo)
        val gpu = (logInfo.gpu ?: "").lowercase()
        val socText =
            listOfNotNull(logInfo.socName, logInfo.socCode, logInfo.cpuName, logInfo.deviceModel)
                .joinToString(" ").lowercase()
        val texBias =
            if (p.q1) {
                80
            } else if (p.q0) {
                200
            } else {
                400
            }
        val charOutline =
            if (p.q1) {
                1200
            } else if (p.q0) {
                950
            } else {
                850
            }
        val charEyeDist =
            if (p.q1) {
                700
            } else if (p.q0) {
                550
            } else {
                450
            }
        val charLODScale =
            if (p.q1) {
                7.0
            } else if (p.q0) {
                6.0
            } else {
                5.0
            }

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
        val presetBaseProfile =
            when (activePreset) {
                "potato" -> "Android_Low"
                "endurance" -> "Android_Low"
                "performance" -> "Android_Low"
                "competitive" -> "Android_Low"
                "balanced" -> "Android_Mid"
                "high" -> "Android_VeryHigh"
                "ultra" -> "Android_Ultra"
                "cinematic" -> "Android_Ultra"
                else -> "Android_Mid"
            }

        fun universalProfilesForPreset(): List<String> =
            when (activePreset) {
                "potato" -> listOf("Android_Low")
                "endurance" -> listOf("Android_Low")
                "performance" -> listOf("Android_Low")
                "competitive" -> listOf("Android_Low")
                "high" -> listOf("Android_VeryHigh")
                "ultra" -> listOf("Android_Ultra")
                "cinematic" -> listOf("Android_Ultra")
                else -> listOf("Android_Mid")
            }

        fun profileCVarLines(): List<String> {
            val lines =
                mutableListOf(
                    "; Device tier — follows selected preset, not forced high",
                    "+CVars=r.Mobile.DeviceEvaluation=${if (activePreset == "potato" || activePreset == "endurance") {
                        0
                    } else if (activePreset == "performance" || activePreset == "competitive") {
                        1
                    } else if (activePreset == "balanced") {
                        2
                    } else {
                        3
                    }}",
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
                    "+CVars=r.Kuro.MobileISMDecideDistance=${dt.ismDist}.0",
                    "+CVars=r.Kuro.MobileISMMeshRadiusMax=${dt.ismRad}.0",
                    "",
                    "; Foliage cull",
                    "+CVars=r.Kuro.Foliage.MobileGrassCullDistanceMax=${dt.grassCull}",
                    "+CVars=r.Kuro.Foliage.MobileGrass3_0CullDistanceMax=${dt.grassCull}",
                    "+CVars=r.Kuro.Foliage.MobileMiddleCullDistanceMin=${(dt.grassCull * 1.8).toInt()}",
                    "+CVars=r.Kuro.Foliage.MobileMiddleCullDistanceMax=${(dt.grassCull * 2.2).toInt()}",
                    "+CVars=r.Kuro.Foliage.MobileFarCullDistanceMin=${(dt.grassCull * 2.8).toInt()}",
                    "+CVars=r.Kuro.Foliage.MobileFarCullDistanceMax=${(dt.grassCull * 3.2).toInt()}",
                    "",
                    "; FPS unlock",
                    "+CVars=r.Kuro.MaxFPS.ThirdParty60=1",
                )
            if (opts.unlock120) lines.add("+CVars=r.Kuro.MaxFPS.ThirdParty120=1")
            if (opts.unlockUltra) lines.add("+CVars=r.Kuro.GraphicsQuality.ThirdPartyUltraEnable=1")
            return lines
        }

        val hasUploadedLog = logInfo.gpu != null || logInfo.deviceModel != null
        if (!hasUploadedLog) {
            val profiles = universalProfilesForPreset()
            val rootProfile = profiles[0]
            val rootBaseProfile = if (presetBaseProfile == "Android_Ultra") "Android_VeryHigh" else "Android"
            val lines =
                mutableListOf<String>().apply {
                    add(configHeader("Android", activePreset, logInfo))
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

        val lines =
            mutableListOf<String>().apply {
                add(configHeader("Android", activePreset, logInfo))
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

    private fun parseResolution(res: String?): Pair<Int, Int>? {
        if (res == null) return null
        val parts = res.trim().split(Regex("\\s*[xX*]\\s*"))
        val w = parts.firstOrNull()?.toIntOrNull() ?: return null
        val h = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return w to h
    }

    private fun buildAndroidGameUserSettingsIni(
        p: PresetProfile,
        opts: GeneratorOptions,
        logInfo: LogInfo = LogInfo(),
    ): String {
        val deviceRes = parseResolution(logInfo.resolution)
        val (resW, resH) = if (deviceRes != null && deviceRes.first >= 720) deviceRes else (1280 to 720)
        val viewQ =
            if (p.q1) {
                3
            } else if (p.q0) {
                2
            } else {
                1
            }
        val shadowQ =
            if (p.shadow >= 4) {
                3
            } else if (p.shadow >= 2) {
                2
            } else {
                1
            }
        val postQ =
            if (p.q1) {
                3
            } else if (p.q0) {
                2
            } else {
                1
            }
        val texQ =
            if (p.q1) {
                3
            } else if (p.q0) {
                2
            } else {
                1
            }
        val fxQ =
            if (p.q1) {
                2
            } else if (p.q0) {
                1
            } else {
                0
            }
        val kuroQ = if (p.q1) 3 else 2
        val aaQ = if (p.q0) 2 else 1

        return listOf(
            "; WuWa GameUserSettings.ini — WuWaConfig", "",
            "[ScalabilityGroups]",
            "sg.ResolutionQuality=${p.screen}",
            "sg.ViewDistanceQuality=$viewQ",
            "sg.AntiAliasingQuality=$aaQ",
            "sg.ShadowQuality=$shadowQ",
            "sg.PostProcessQuality=$postQ",
            "sg.TextureQuality=$texQ",
            "sg.EffectsQuality=$fxQ",
            "sg.FoliageQuality=${if (p.q1) {
                2
            } else if (p.q0) {
                1
            } else {
                0
            }}",
            "sg.ShadingQuality=${if (p.q1) 3 else 2}",
            "sg.KuroRenderQuality=$kuroQ",
            "sg.KuroLocalRenderQuality=0",
            "sg.RayTracingQuality=0",
            "",
            "[/Script/Engine.GameUserSettings]",
            "bUseVSync=${if (opts.vsync) "True" else "False"}",
            "bUseDynamicResolution=False",
            "ResolutionSizeX=$resW",
            "ResolutionSizeY=$resH",
            "LastUserConfirmedResolutionSizeX=$resW",
            "LastUserConfirmedResolutionSizeY=$resH",
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
            "DesiredScreenWidth=$resW",
            "bUseDesiredScreenHeight=False",
            "DesiredScreenHeight=$resH",
            "LastUserConfirmedDesiredScreenWidth=$resW",
            "LastUserConfirmedDesiredScreenHeight=$resH",
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
            "LastOpened=Client",
        ).joinToString("\n")
    }

    private fun buildAndroidScalabilityIni(
        p: PresetProfile,
        opts: GeneratorOptions,
    ): String {
        val viewQ =
            if (p.q1) {
                3
            } else if (p.q0) {
                2
            } else {
                1
            }
        val shadowQ =
            if (p.shadow >= 4) {
                3
            } else if (p.shadow >= 2) {
                2
            } else {
                1
            }
        val postQ =
            if (p.q1) {
                3
            } else if (p.q0) {
                2
            } else {
                1
            }
        val texQ =
            if (p.q1) {
                3
            } else if (p.q0) {
                2
            } else {
                1
            }
        val fxQ =
            if (p.q1) {
                2
            } else if (p.q0) {
                1
            } else {
                0
            }
        val folQ =
            if (p.q1) {
                2
            } else if (p.q0) {
                1
            } else {
                0
            }
        val kuroQ = if (p.q1) 3 else 2
        val aaQ = if (p.q0) 2 else 1
        val shaQ = if (p.q1) 3 else 2

        val header =
            listOf(
                "; WuWa Scalability.ini — WuWaConfig",
                "",
                "[ScalabilitySettings]",
                "ResolutionQuality=${p.screen}.0",
                "ViewDistanceQuality=$viewQ",
                "AntiAliasingQuality=$aaQ",
                "ShadowQuality=$shadowQ",
                "PostProcessQuality=$postQ",
                "TextureQuality=$texQ",
                "EffectsQuality=$fxQ",
                "FoliageQuality=$folQ",
                "ShadingQuality=$shaQ",
                "KuroRenderQuality=$kuroQ",
                "KuroLocalRenderQuality=0",
            )

        val sections =
            listOf(
                listOf("", "[ViewDistanceQuality@0]", "r.ViewDistanceScale=0.70", "r.SkeletalMeshLODBias=0", "r.NeverOcclusionTestDistance=0"),
                listOf("", "[ViewDistanceQuality@1]", "r.ViewDistanceScale=0.85", "r.SkeletalMeshLODBias=0"),
                listOf("", "[ViewDistanceQuality@2]", "r.ViewDistanceScale=1.0", "r.SkeletalMeshLODBias=0"),
                listOf("", "[ViewDistanceQuality@3]", "r.ViewDistanceScale=1.0", "r.SkeletalMeshLODBias=0"),
                listOf("", "[AntiAliasingQuality@0]", "r.PostProcessAAQuality=0", "r.MotionBlurQuality=0", "r.AmbientOcclusionLevels=-1", "r.AmbientOcclusionMaxQuality=0"),
                listOf("", "[AntiAliasingQuality@1]", "r.PostProcessAAQuality=2", "r.MotionBlurQuality=1"),
                listOf("", "[AntiAliasingQuality@2]", "r.PostProcessAAQuality=3", "r.MotionBlurQuality=2"),
                listOf("", "[AntiAliasingQuality@3]", "r.PostProcessAAQuality=3", "r.MotionBlurQuality=3", "r.AmbientOcclusionLevels=0"),
                listOf("", "[ShadowQuality@0]", "r.ShadowQuality=1", "r.Shadow.CSM.MaxCascades=1", "r.Shadow.CSM.MaxMobileCascades=1", "r.Shadow.MaxResolution=128", "r.LightFunctionQuality=0"),
                listOf("", "[ShadowQuality@1]", "r.ShadowQuality=2", "r.Shadow.CSM.MaxCascades=3", "r.Shadow.MaxResolution=256", "r.LightFunctionQuality=1", "r.Shadow.MobileDistributionOverride=3.0"),
                listOf("", "[ShadowQuality@2]", "r.ShadowQuality=2", "r.Shadow.CSM.MaxCascades=3", "r.Shadow.MaxResolution=512", "r.LightFunctionQuality=1", "r.Shadow.CacheDirectLightShadow=3", "r.Shadow.MobileDistributionOverride=2.5"),
                listOf("", "[ShadowQuality@3]", "r.ShadowQuality=2", "r.Shadow.CSM.MaxCascades=3", "r.Shadow.MaxResolution=512", "r.LightFunctionQuality=1", "r.Shadow.CacheDirectLightShadow=3", "r.Shadow.MobileDistributionOverride=2.0"),
                listOf("", "[PostProcessQuality@0]", "r.MotionBlurQuality=0", "r.RenderTargetPoolMin=300", "r.AmbientOcclusionRadiusScale=1.2"),
                listOf("", "[PostProcessQuality@1]", "r.MotionBlurQuality=1", "r.RenderTargetPoolMin=400"),
                listOf("", "[PostProcessQuality@2]", "r.MotionBlurQuality=2", "r.RenderTargetPoolMin=500"),
                listOf("", "[PostProcessQuality@3]", "r.MotionBlurQuality=3", "r.RenderTargetPoolMin=600"),
                listOf(
                    "", "[TextureQuality@0]",
                    "r.Streaming.MipBias=16", "r.Streaming.PoolSize=300",
                    "r.Streaming.PoolSizeForMeshes=300", "r.Streaming.Boost=0.3",
                    "r.Streaming.MaxNumTexturesToStreamPerFrame=1",
                    "r.TranslucencyLightingVolumeDim=24", "r.VT.MaxAnisotropy=4",
                ),
                listOf("", "[TextureQuality@1]", "r.Streaming.MipBias=8", "r.Streaming.PoolSize=400", "r.Streaming.PoolSizeForMeshes=400", "r.Streaming.Boost=0.5"),
                listOf("", "[TextureQuality@2]", "r.Streaming.MipBias=4", "r.Streaming.PoolSize=600", "r.Streaming.PoolSizeForMeshes=600", "r.Streaming.Boost=0.8"),
                listOf("", "[TextureQuality@3]", "r.Streaming.MipBias=0", "r.Streaming.PoolSize=800", "r.Streaming.PoolSizeForMeshes=800", "r.Streaming.Boost=1.0"),
                listOf("", "[EffectsQuality@0]", "r.DetailMode=0", "r.SSR.Quality=0", "r.SSR.HalfResSceneColor=1", "r.RefractionQuality=0", "r.SceneColorFormat=2", "r.TranslucencyVolumeBlur=0"),
                listOf("", "[EffectsQuality@1]", "r.DetailMode=1", "r.SSR.Quality=1", "r.SSR.HalfResSceneColor=1", "r.RefractionQuality=1"),
                listOf("", "[EffectsQuality@2]", "r.DetailMode=2", "r.SSR.Quality=2", "r.SSR.HalfResSceneColor=0", "r.RefractionQuality=2"),
                listOf("", "[FoliageQuality@0]", "foliage.DensityScale=1.0", "foliage.DensityType=0", "foliage.DensityScaleLOD.DensityType=0", "foliage.DensityScaleLOD.DistanceType=0", "grass.DensityScale=0.8", "grass.CullDistanceScale=0.8"),
                listOf("", "[FoliageQuality@1]", "foliage.DensityScale=1.0", "foliage.DensityType=1", "foliage.DensityScaleLOD.DensityType=1", "foliage.DensityScaleLOD.DistanceType=1", "grass.DensityScale=1.0", "grass.CullDistanceScale=0.9"),
                listOf("", "[FoliageQuality@2]", "foliage.DensityScale=1.0", "foliage.DensityType=2", "foliage.DensityScaleLOD.DensityType=2", "foliage.DensityScaleLOD.DistanceType=2", "grass.DensityScale=1.0", "grass.CullDistanceScale=1.0"),
                listOf("", "[ShadingQuality@2]", "r.HairStrands.SkyAO.SampleCount=4", "r.HairStrands.SkyLighting.IntegrationType=2", "r.HairStrands.Visibility.MSAA.SamplePerPixel=4"),
                listOf("", "[ShadingQuality@3]", "r.HairStrands.SkyAO.SampleCount=4", "r.HairStrands.SkyLighting.IntegrationType=2", "r.HairStrands.Visibility.MSAA.SamplePerPixel=4"),
                listOf(
                    "", "[KuroRenderQuality@0]",
                    "KuroRenderQuality.LevelName=极致性能",
                    "r.StaticMeshLODDistanceScale=3",
                    "r.ScreenSizeCullRatioFactor=150",
                    "r.DrawKuroPPLensflare=0",
                    "r.Kuro.NpcDisappearDistance=1000",
                    "r.Kuro.SkeletalMesh.LODDistanceScale=0.2",
                    "r.Kuro.FloatingStaticMeshTickFactor=2.4",
                    "r.Kuro.FlickerLightActorTickFactor=12.0",
                    "r.Kuro.MaterialDesktopQualityShoulderRender=0",
                    "r.Kuro.GlobalPointCloudStreamEnabled=0",
                    "foliage.DensityType=0",
                    "foliage.DensityScaleLOD.Switch=0",
                ),
                listOf(
                    "", "[KuroRenderQuality@3]",
                    "KuroRenderQuality.LevelName=画质优先",
                    "r.StaticMeshLODDistanceScale=1",
                    "r.ScreenSizeCullRatioFactor=40",
                    "r.DrawKuroPPLensflare=1",
                    "r.Kuro.NpcDisappearDistance=1800",
                    "r.Kuro.SkeletalMesh.LODDistanceScale=0.6",
                    "r.Kuro.FloatingStaticMeshTickFactor=1.2",
                    "r.Kuro.FlickerLightActorTickFactor=2.4",
                    "r.Kuro.MaterialDesktopQualityShoulderRender=3",
                    "r.Kuro.GlobalPointCloudStreamEnabled=0",
                    "foliage.DensityType=1",
                    "foliage.DensityScaleLOD.Switch=0",
                ),
                listOf(
                    "", "[KuroLocalRenderQuality@0]",
                    "KuroRenderQuality.LevelName=极致性能",
                    "r.StaticMeshLODDistanceScale=3",
                    "r.ScreenSizeCullRatioFactor=150",
                    "r.DrawKuroPPLensflare=0",
                    "r.Kuro.NpcDisappearDistance=1000",
                    "r.Kuro.SkeletalMesh.LODDistanceScale=0.2",
                    "r.Kuro.FloatingStaticMeshTickFactor=2.4",
                    "r.Kuro.FlickerLightActorTickFactor=12.0",
                    "r.Kuro.MaterialDesktopQualityShoulderRender=0",
                    "r.Kuro.GlobalPointCloudStreamEnabled=0",
                ),
            )

        return (header + sections.flatten()).joinToString("\n")
    }

    private fun buildAndroidHardwareIni(
        p: PresetProfile,
        opts: GeneratorOptions,
        logInfo: LogInfo = LogInfo(),
        activePreset: String = "balanced",
    ): String {
        val dt = computeDeviceTier(logInfo)
        val presetLabel =
            when (activePreset) {
                "potato" -> "Low"
                "endurance" -> "Low"
                "performance" -> "Low"
                "competitive" -> "Low"
                "balanced" -> "Mid"
                "high" -> "High"
                "ultra" -> "Ultra"
                "cinematic" -> "Ultra"
                else -> "Mid"
            }
        return listOf(
            "; WuWa Hardware.ini — WuWaConfig",
            "; Generated for ${logInfo.deviceModel ?: "Android device"}",
            "",
            "[DeviceProfile]",
            "DeviceProfileName=Android_$presetLabel",
            "DeviceType=Android",
            "",
            "; FPS cap based on preset",
            "FramePace=${opts.fps}",
            "",
            "; Anisotropic filtering",
            "+CVars=r.MaxAnisotropy=${dt.maxAniso}",
            "",
            "; LOD bias",
            "+CVars=r.Streaming.MipBias=${if (p.q1) 0 else 1}",
            "",
            "; Foliage LOD",
            "+CVars=foliage.LODDistanceScale=${"%.1f".format(p.flod)}",
        ).joinToString("\n")
    }

    fun parseCvarEntries(
        engineIni: String,
        logInfo: LogInfo = LogInfo(),
    ): List<CvarEntry> {
        val entries = mutableListOf<CvarEntry>()
        var currentCategory = ""
        for (line in engineIni.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) continue
            if (trimmed.startsWith(";")) {
                val cat = trimmed.removePrefix(";").trim().removePrefix("──").trim().removeSuffix("──").trim()
                if (cat.isNotEmpty() && !cat.startsWith("═")) currentCategory = cat
                continue
            }
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                val key = trimmed.substring(0, eq).trim()
                val value = trimmed.substring(eq + 1).trim()
                if (key.isNotEmpty() && !key.startsWith("+")) {
                    entries.add(CvarEntry(key = key, value = value, category = currentCategory))
                }
            }
        }
        return entries
    }

    fun applyCvarOverrides(
        text: String,
        overrides: Map<String, String>,
    ): String {
        if (overrides.isEmpty()) return text
        val lines = text.lines().toMutableList()
        for ((key, newValue) in overrides) {
            val idx =
                lines.indexOfFirst { line ->
                    val trimmed = line.trim()
                    val eq = trimmed.indexOf('=')
                    eq > 0 && trimmed.substring(0, eq).trim() == key
                }
            if (idx >= 0) {
                val raw = lines[idx]
                val trimmed = raw.trim()
                val eq = trimmed.indexOf('=')
                val existingVal = trimmed.substring(eq + 1).trim()
                if (existingVal != newValue) {
                    val rawEq = raw.indexOf('=')
                    lines[idx] = raw.substring(0, rawEq + 1) + " " + newValue
                }
            }
        }
        return lines.joinToString("\n")
    }
}
