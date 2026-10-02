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
