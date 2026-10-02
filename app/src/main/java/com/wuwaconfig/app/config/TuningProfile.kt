package com.wuwaconfig.app.config

import android.content.res.AssetManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository

/**
 * Nested tuning data, loaded from `assets/config/tuning.json`.
 *
 * ## Why a second asset
 *
 * [GameProfile] holds flat scalars and comma-separated lists in a
 * `.properties` file. The tuning data is neither: presets are 8 records of 15
 * fields, the chipset tables are regex→value maps, and the plugin paths are an
 * ordered list. `.properties` cannot express those shapes, and its
 * backslash-escaping has already caused one silent bug in [GameProfile].
 * JSON is the right format here.
 *
 * ## Failure contract
 *
 * Identical to [GameProfile]: every table has a compiled-in default equal to
 * the asset's value, so a missing, empty or malformed asset degrades to
 * exactly today's behaviour. `TuningProfileTest` pins asset↔default parity for
 * every table, which is what makes the duplication safe — editing the asset
 * without editing the default would otherwise make behaviour depend on whether
 * the asset loaded.
 *
 * The load is synchronous and happens once, from `WuWaConfigApp.onCreate`,
 * because the values are needed during `ConfigGenerator` construction.
 */
class TuningProfile internal constructor(
    private val root: JsonObject,
    /** Non-fatal problems found while loading, surfaced in the app log. */
    val loadWarnings: List<String> = emptyList(),
) {
    /**
     * The eight presets and their per-field tuning.
     *
     * Keyed by preset name, in declaration order. A name present in the defaults
     * but absent from the asset is dropped rather than defaulted, so a typo in
     * the asset cannot silently resurrect a removed preset.
     */
    val presets: Map<String, PresetProfile> = parsePresets(root)

    /**
     * The default `[Core.System]` plugin block, header included.
     *
     * Order is significant — it is emitted into the generated Engine.ini
     * verbatim — so this stays a `List`, not a `Set`.
     */
    val coreSystemPaths: List<String> =
        root.getAsJsonArray("coreSystemPaths")?.mapNotNull { it.asString }?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_CORE_SYSTEM_PATHS

    /**
     * GPU string → tier name (`flagship`/`high`/`mid_high`/`mid`/`mid_low`/`low`),
     * in priority order — the first match wins, so order is significant.
     */
    val gpuTierPatterns: List<RegexTier> = root.patternTable("gpuTierPatterns", DEFAULT_GPU_TIER_PATTERNS)

    /** Chipset or GPU string → UE `DeviceProfile` name, first match wins. */
    val chipsetProfiles: List<RegexTier> = root.patternTable("chipsetProfiles", DEFAULT_CHIPSET_PROFILES)

    /** As [chipsetProfiles], for when the SoC is unknown and only the GPU is. */
    val gpuOnlyProfiles: List<RegexTier> = root.patternTable("gpuOnlyProfiles", DEFAULT_GPU_ONLY_PROFILES)

    /** Device-tier boundaries: "is this GPU high-end", then "is it mid". */
    val highEndGpuPatterns: List<Regex> = root.regexList("highEndGpuPatterns", DEFAULT_HIGH_END_GPU_PATTERNS)

    val midGpuPatterns: List<Regex> = root.regexList("midGpuPatterns", DEFAULT_MID_GPU_PATTERNS)

    /**
     * A regex plus its mapped value, compiled once.
     *
     * These tables exist so detection does not recompile ~60 patterns per
     * generate call, and compiling eagerly at load keeps that property when the
     * source becomes an asset.
     */
    data class RegexTier(
        val regex: Regex,
        val value: String,
    )

    private fun JsonObject.patternTable(
        key: String,
        default: List<Pair<Regex, String>>,
    ): List<RegexTier> {
        val rows =
            root.getAsJsonArray(key)?.mapNotNull { row ->
                val o = row.asJsonObject
                val pattern = o.get("pattern")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                val value = o.get("value")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                val ignoreCase = o.get("ignoreCase")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                // A pattern that will not compile must not take down generation.
                runCatching {
                    if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
                }.getOrNull()
                    ?.let { RegexTier(it, value) }
            }.orEmpty()
        if (rows.isEmpty()) return default.map { RegexTier(it.first, it.second) }
        // Order is the matching rule, so the asset's order is authoritative; only
        // its length is checked against the default, by the parity test.
        return rows
    }

    private fun JsonObject.regexList(
        key: String,
        default: List<Regex>,
    ): List<Regex> {
        val rows =
            root.getAsJsonArray(key)?.mapNotNull { entry ->
                val pattern = entry.asString
                runCatching { Regex(pattern) }.getOrNull()
            }.orEmpty()
        return rows.ifEmpty { default }
    }

    private fun parsePresets(json: JsonObject): Map<String, PresetProfile> {
        val obj = json.getAsJsonObject("presets") ?: return DEFAULT_PRESETS
        val result = LinkedHashMap<String, PresetProfile>()
        for ((name, default) in DEFAULT_PRESETS) {
            val o = obj.getAsJsonObject(name) ?: continue
            result[name] =
                PresetProfile(
                    screen = o.int("screen", default.screen),
                    shadow = o.int("shadow", default.shadow),
                    shadowRes = o.int("shadowRes", default.shadowRes),
                    ssr = o.int("ssr", default.ssr),
                    mipbias = o.int("mipbias", default.mipbias),
                    streaming = o.double("streaming", default.streaming),
                    vd = o.double("vd", default.vd),
                    flod = o.double("flod", default.flod),
                    detail = o.int("detail", default.detail),
                    lod_bias = o.int("lod_bias", default.lod_bias),
                    grasscull = o.int("grasscull", default.grasscull),
                    characterDetail = o.int("characterDetail", default.characterDetail),
                    postProcess = o.int("postProcess", default.postProcess),
                    staticLighting = o.bool("staticLighting", default.staticLighting),
                    cutsceneQuality = o.int("cutsceneQuality", default.cutsceneQuality),
                )
        }
        return result
    }

    private fun JsonObject.int(
        key: String,
        default: Int,
    ): Int = get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: default

    private fun JsonObject.double(
        key: String,
        default: Double,
    ): Double = get(key)?.takeIf { it.isJsonPrimitive }?.asDouble ?: default

    private fun JsonObject.bool(
        key: String,
        default: Boolean,
    ): Boolean = get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default

    companion object {
        private const val ASSET_PATH = "config/tuning.json"

        // ── compiled-in defaults: the values this build shipped with. Each must
        // equal the corresponding asset key, which TuningProfileTest enforces. ──

        val DEFAULT_PRESETS: Map<String, PresetProfile> =
            linkedMapOf(
                "potato" to
                    PresetProfile(
                        screen = 60, shadow = 0, shadowRes = 128, ssr = 0, mipbias = 3,
                        streaming = 0.3, vd = 0.3, flod = 0.4, detail = 0, lod_bias = 5, grasscull = 1500,
                        characterDetail = 0, postProcess = 0, staticLighting = false, cutsceneQuality = 0,
                    ),
                "endurance" to
                    PresetProfile(
                        screen = 70, shadow = 0, shadowRes = 128, ssr = 0, mipbias = 3,
                        streaming = 0.4, vd = 0.4, flod = 0.5, detail = 1, lod_bias = 4, grasscull = 2500,
                        characterDetail = 0, postProcess = 0, staticLighting = false, cutsceneQuality = 0,
                    ),
                "performance" to
                    PresetProfile(
                        screen = 60, shadow = 0, shadowRes = 256, ssr = 0, mipbias = 3,
                        streaming = 0.5, vd = 0.5, flod = 0.6, detail = 2, lod_bias = 3, grasscull = 4500,
                        characterDetail = 1, postProcess = 1, staticLighting = false, cutsceneQuality = 1,
                    ),
                "competitive" to
                    PresetProfile(
                        screen = 100, shadow = 2, shadowRes = 256, ssr = 0, mipbias = 1,
                        streaming = 1.0, vd = 2.0, flod = 1.0, detail = 3, lod_bias = 1, grasscull = 2000,
                        characterDetail = 1, postProcess = 1, staticLighting = false, cutsceneQuality = 1,
                    ),
                "balanced" to
                    PresetProfile(
                        screen = 80, shadow = 2, shadowRes = 1024, ssr = 1, mipbias = 0,
                        streaming = 2.0, vd = 1.5, flod = 2.0, detail = 4, lod_bias = 0, grasscull = 15000,
                        characterDetail = 2, postProcess = 2, staticLighting = true, cutsceneQuality = 2,
                    ),
                "high" to
                    PresetProfile(
                        screen = 100, shadow = 4, shadowRes = 2048, ssr = 2, mipbias = 0,
                        streaming = 3.0, vd = 2.0, flod = 2.5, detail = 5, lod_bias = 0, grasscull = 20000,
                        characterDetail = 2, postProcess = 2, staticLighting = true, cutsceneQuality = 2,
                    ),
                "ultra" to
                    PresetProfile(
                        screen = 100, shadow = 5, shadowRes = 2048, ssr = 4, mipbias = -1,
                        streaming = 4.0, vd = 3.0, flod = 3.0, detail = 6, lod_bias = -1, grasscull = 30000,
                        characterDetail = 3, postProcess = 3, staticLighting = true, cutsceneQuality = 3,
                    ),
                "cinematic" to
                    PresetProfile(
                        screen = 100, shadow = 5, shadowRes = 4096, ssr = 4, mipbias = -2,
                        streaming = 6.0, vd = 4.0, flod = 4.0, detail = 7, lod_bias = -2, grasscull = 40000,
                        characterDetail = 3, postProcess = 3, staticLighting = true, cutsceneQuality = 3,
                    ),
            )

        val DEFAULT_CORE_SYSTEM_PATHS: List<String> =
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

        val DEFAULT_GPU_TIER_PATTERNS: List<Pair<Regex, String>> =
            listOf(
                Regex("""adreno.*8[3-9]\d|adreno.*8[12]\d""") to "flagship",
                Regex("""tensor\s*g[345]""") to "flagship",
                Regex("""dimensity\s*9[3-9]\d\d?""") to "flagship",
                Regex("""apple\s*(m[34]|a18)""") to "flagship",
                Regex("""adreno.*7[5-9]\d|adreno.*8[0]\d""") to "high",
                Regex("""tensor\s*g[12]""") to "high",
                Regex("""dimensity\s*(9[0-2]\d|8[5-9]\d)""") to "high",
                Regex("""exynos\s*2200""") to "high",
                Regex("""kirin\s*9000""") to "high",
                Regex("""mali-g(7[6-9]|8\d|9\d)\d?""") to "high",
                Regex("""apple\s*(m[12]|a1[67])""") to "high",
                Regex("""adreno.*7[0-4]\d|adreno.*6[5-9]\d""") to "mid_high",
                Regex("""dimensity\s*(8[0-4]\d|7[3-9]\d)""") to "mid_high",
                Regex("""tensor""") to "mid_high",
                Regex("""exynos\s*2[1-3]00""") to "mid_high",
                Regex("""kirin\s*9[1-9]\d\d?""") to "mid_high",
                Regex("""xclipse""") to "mid_high",
                Regex("""apple\s*a1[45]""") to "mid_high",
                Regex("""adreno.*6[0-4]\d|mali-g(6\d|7[0-5])\d?|mali-g615""") to "mid",
                Regex("""dimensity\s*[0-9]{3}""") to "mid",
                Regex("""exynos\s*[0-9]{4}""") to "mid",
                Regex("""kirin\s*[0-9]{4}""") to "mid",
                Regex("""apple\s*a1[23]""") to "mid",
                Regex("""adreno.*5\d\d|mali-g5\d?""") to "mid_low",
                Regex("""adreno.*[34]\d\d|mali-g[34]""") to "low",
            )

        val DEFAULT_CHIPSET_PROFILES: List<Pair<Regex, String>> =
            listOf(
                Regex("""snapdragon\s*8\s*elite|sm8750|adreno\s*830""", RegexOption.IGNORE_CASE) to "Android_Adreno830",
                Regex("""snapdragon\s*8\s*gen\s*3|sm8650|adreno\s*750""", RegexOption.IGNORE_CASE) to "Android_Adreno750",
                Regex("""snapdragon\s*8\s*gen\s*2|sm8550|adreno\s*740""", RegexOption.IGNORE_CASE) to "Android_Adreno740",
                Regex("""snapdragon\s*8\s*\+?\s*gen\s*1|sm8475|sm8450|adreno\s*730""", RegexOption.IGNORE_CASE) to "Android_Adreno7xx",
                Regex("""snapdragon\s*7|sm7\d{3}|adreno\s*7""", RegexOption.IGNORE_CASE) to "Android_Adreno7xx",
                Regex("""snapdragon\s*6|snapdragon\s*695|snapdragon\s*680|sm6\d{3}|adreno\s*6""", RegexOption.IGNORE_CASE) to "Android_Adreno6xx",
                Regex("""adreno\s*5""", RegexOption.IGNORE_CASE) to "Android_Adreno5xx",
                Regex("""adreno\s*4""", RegexOption.IGNORE_CASE) to "Android_Adreno4xx",
                Regex("""dimensity\s*94|mali-g925""", RegexOption.IGNORE_CASE) to "Android_Mali_G925",
                Regex("""dimensity\s*93|mali-g720""", RegexOption.IGNORE_CASE) to "Android_Mali_G720",
                Regex("""dimensity\s*92|mali-g715""", RegexOption.IGNORE_CASE) to "Android_Mali_G715",
                Regex("""dimensity\s*90|mali-g710""", RegexOption.IGNORE_CASE) to "Android_Mali_G710",
                Regex("""dimensity\s*8|mali-g61[0-9]|mali-g615""", RegexOption.IGNORE_CASE) to "Android_Mali_G615",
                Regex("""dimensity\s*7|mali-g6""", RegexOption.IGNORE_CASE) to "Android_Mali_G61x",
                Regex("""dimensity\s*6|mali-g57""", RegexOption.IGNORE_CASE) to "Android_Mali_G57",
                Regex("""exynos\s*24|xclipse\s*9""", RegexOption.IGNORE_CASE) to "Android_Xclipse9xx",
                Regex("""exynos\s*13|xclipse\s*5""", RegexOption.IGNORE_CASE) to "Android_Xclipse5xx",
                Regex("""kirin|maleoon""", RegexOption.IGNORE_CASE) to "Android_Maleoon",
            )

        val DEFAULT_GPU_ONLY_PROFILES: List<Pair<Regex, String>> =
            listOf(
                Regex("""adreno\s*830""", RegexOption.IGNORE_CASE) to "Android_Adreno830",
                Regex("""adreno\s*750""", RegexOption.IGNORE_CASE) to "Android_Adreno750",
                Regex("""adreno\s*740""", RegexOption.IGNORE_CASE) to "Android_Adreno740",
                Regex("""adreno\s*730""", RegexOption.IGNORE_CASE) to "Android_Adreno7xx",
                Regex("""adreno\s*7""", RegexOption.IGNORE_CASE) to "Android_Adreno7xx",
                Regex("""adreno\s*6""", RegexOption.IGNORE_CASE) to "Android_Adreno6xx",
                Regex("""adreno\s*5""", RegexOption.IGNORE_CASE) to "Android_Adreno5xx",
                Regex("""adreno\s*4""", RegexOption.IGNORE_CASE) to "Android_Adreno4xx",
                Regex("""mali-g925""", RegexOption.IGNORE_CASE) to "Android_Mali_G925",
                Regex("""mali-g720""", RegexOption.IGNORE_CASE) to "Android_Mali_G720",
                Regex("""mali-g715""", RegexOption.IGNORE_CASE) to "Android_Mali_G715",
                Regex("""mali-g710""", RegexOption.IGNORE_CASE) to "Android_Mali_G710",
                Regex("""mali-g615""", RegexOption.IGNORE_CASE) to "Android_Mali_G615",
                Regex("""mali-g6""", RegexOption.IGNORE_CASE) to "Android_Mali_G61x",
                Regex("""mali-g57""", RegexOption.IGNORE_CASE) to "Android_Mali_G57",
                Regex("""xclipse\s*9""", RegexOption.IGNORE_CASE) to "Android_Xclipse9xx",
                Regex("""xclipse\s*5""", RegexOption.IGNORE_CASE) to "Android_Xclipse5xx",
                Regex("""maleoon""", RegexOption.IGNORE_CASE) to "Android_Maleoon",
            )

        val DEFAULT_HIGH_END_GPU_PATTERNS: List<Regex> =
            listOf(
                Regex("""adreno.*7[4-9]\d"""),
                Regex("""adreno.*8\d{2}"""),
                Regex("""mali-g(7[2-9]\d|8\d{1,2}|9\d{1,2})"""),
            )

        val DEFAULT_MID_GPU_PATTERNS: List<Regex> =
            listOf(
                Regex("""adreno.*6\d{2}"""),
                Regex("""adreno.*7[1-3]\d"""),
                Regex("""mali-g(5\d{1,2}|6\d{1,2})"""),
            )

        @Volatile
        private var instance: TuningProfile? = null

        /**
         * The pre-load fallback, deliberately *not* cached into [instance].
         *
         * Caching it there would make [load] a no-op via its own idempotence
         * guard, so any read that happened before `onCreate` — a static
         * initialiser, or the Shizuku UserService process where `onCreate`
         * returns early — would permanently pin the built-in values and the asset
         * would never be read. Sharing one lazily-built instance instead keeps
         * the fallback cheap without making it sticky.
         */
        private val defaults: TuningProfile by lazy { TuningProfile(JsonObject()) }

        fun load(assets: AssetManager): TuningProfile {
            instance?.let { return it }
            val loaded = read(assets)
            instance = loaded
            if (loaded.loadWarnings.isEmpty()) {
                LogRepository.add("TuningProfile: loaded $ASSET_PATH (${loaded.presets.size} presets)", LogLevel.SUCCESS)
            } else {
                LogRepository.add(
                    "TuningProfile: $ASSET_PATH: ${loaded.loadWarnings.joinToString("; ")} — using built-in values where empty",
                    LogLevel.WARNING,
                )
            }
            return loaded
        }

        /**
         * The loaded profile, or an all-defaults profile if [load] never ran.
         *
         * Safe before load: [TuningProfile.DEFAULT_PRESETS] is byte-for-byte the
         * asset, so a caller that races the load sees today's behaviour.
         */
        fun get(): TuningProfile = instance ?: defaults

        private fun read(assets: AssetManager): TuningProfile {
            val warnings = mutableListOf<String>()
            val root =
                try {
                    assets.open(ASSET_PATH).bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
                } catch (e: Exception) {
                    // Built-in defaults: the generator must keep working when the
                    // asset is missing from a split APK, unreadable, or malformed.
                    warnings += "load failed (${e.javaClass.simpleName}: ${e.message})"
                    return TuningProfile(JsonObject(), warnings)
                }
            if (root.keySet().isEmpty()) warnings += "asset parsed to zero keys"
            return TuningProfile(root, warnings)
        }
    }
}
