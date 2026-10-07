/**
 * Wire types for the AppFunctions surface. Deliberately separate from the app's own
 * models: [com.wuwaconfig.app.config.PresetProfile] exposes every tuning knob as a
 * constructor parameter, which the schema cannot express, and
 * [com.wuwaconfig.app.model.GeneratorOptions] has 29 fields of which an agent needs
 * a handful. These are the subset worth paying tokens for.
 *
 * Every property carries its own KDoc because the appfunctions KSP processor only
 * lifts property-level documentation into the generated schema — class-level
 * `@param` tags are silently dropped, leaving the agent with an undescribed surface.
 *
 * The app's own style is prose KDoc with `[Symbol]` links and no `@param` tags
 * (see [com.wuwaconfig.app.model.LogRepository]); the `@param` tags below are the
 * documented exception, required here for the agent-facing contract.
 */
package com.wuwaconfig.app.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/** One entry from the generator's preset table, with the trade-off spelled out. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class PresetInfo(
    /** Preset identifier, for example "balanced". Pass this to "generateConfig". */
    val name: String,
    /** Quality rank from 0 (lowest) to 7 (highest). Each preset holds a distinct rank. */
    val detailRank: Int,
    /** What this preset trades away, in one line, for example "Daily default". */
    val summary: String,
    /** Internal render resolution as a percentage of native. Below 100 renders fewer pixels. */
    val renderScalePercent: Int,
    /** Shadow quality tier, 0 (off) to 5 (highest). */
    val shadowQuality: Int,
    /** Shadow map resolution. 128 is the cheapest tier, 4096 the most expensive. */
    val shadowResolution: Int,
    /** Screen-space reflections tier, 0 (off) to 4 (highest). */
    val screenSpaceReflections: Int,
    /** Texture streaming multiplier relative to default. 0.3 loads far less, 6 loads the most. */
    val textureStreamingMultiplier: Double,
    /** View distance scale, where 1.0 is the game's default. */
    val viewDistanceScale: Double,
    /** Foliage draw distance scale, where 1.0 is the game's default. */
    val foliageDistanceScale: Double,
    /** Grass cull distance in centimetres. Higher values cull more aggressively. */
    val grassCullDistance: Int,
    /** Level-of-detail bias. Negative values push detail further away, positive values closer. */
    val levelOfDetailBias: Int,
    /** Texture mip bias. Negative values sharpen distant textures at a cost in bandwidth. */
    val mipBias: Int,
    /** Character model detail tier, 0 (simplified) to 3 (highest). */
    val characterDetail: Int,
    /** Post-processing effect tier, 0 (off) to 3 (highest). */
    val postProcessQuality: Int,
    /** Whether baked static lighting is enabled. Disabling it removes precomputed light. */
    val staticLightingEnabled: Boolean,
    /** Cutscene render quality tier, 0 (lowest) to 3 (highest). */
    val cutsceneQuality: Int,
)

/** A single CVar the caller wants forced to an exact value after generation. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CvarOverride(
    /** CVar name as the engine spells it, for example "r.Shadow.MaxResolution". Case-insensitive. */
    val name: String,
    /** Value to force. Wins over anything the preset or the log analysis would have set. */
    val value: String,
)

/**
 * The subset of generator settings worth exposing. Everything omitted uses
 * [com.wuwaconfig.app.model.GeneratorOptions] defaults, which are the values the
 * app's own UI ships with.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ConfigOptions(
    /** Requested frame cap. Supported values are 30, 45, 60, 90 and 120. */
    val frameRateCap: Int = 60,
    /** Game activity to tune for. Either "Overworld" or "Tower of Adversity". Null means "Overworld". */
    val gameMode: String? = null,
    /** Raise the frame cap above 60 where the device allows it. Costs battery and heat. */
    val unlockHighFrameRate: Boolean = false,
    /** Render with Vulkan instead of OpenGL ES. A global switch, not per-preset; the game may fall back. */
    val useVulkan: Boolean = false,
    /** Enable horizon-based occlusion culling. Cheap frame time on open areas, a cost in dense ones. */
    val enableHorizonOcclusion: Boolean = false,
    /** Keep distance fog. Turning it off raises visibility at the cost of draw distance. */
    val enableFog: Boolean = false,
    /** Turn off character outlines. */
    val disableOutlines: Boolean = false,
    /** Turn off the bloom post-process. */
    val disableBloom: Boolean = false,
    /** Turn off automatic exposure adaptation, fixing brightness at the scene's default. */
    val disableAutoExposure: Boolean = false,
    /** Turn off screen-space reflections, independent of the preset's own setting. */
    val disableScreenSpaceReflections: Boolean = false,
    /** Let the generator reduce the load when the device reports thermal pressure. */
    val enableAutoCooling: Boolean = true,
    /**
     * Tune each setting from the analysed device rather than applying the preset
     * unchanged. Has no effect unless device information is available, which is
     * what "analyzeGameLog" or "getCachedLogAnalysis" produce.
     */
    val perDeviceTuning: Boolean = false,
    /** Keep the 31 CVar entries the game treats as restricted. Disabling strips them. */
    val allowRestrictedCvars: Boolean = true,
    /** Also produce the Scalability.ini, which tunes distance-based quality scaling. */
    val includeScalabilityIni: Boolean = false,
    /** Also produce the Hardware.ini, which caps hardware-specific features. */
    val includeHardwareIni: Boolean = false,
    /** Individual CVar values to force after the preset is applied, highest priority of all. Null means none. */
    val cvarOverrides: List<CvarOverride>? = null,
)

/**
 * A generated configuration, returned as text. Nothing is written to the game;
 * the caller is expected to show it to the user.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GeneratedConfig(
    /** Preset that was actually applied. Differs from the requested name when an unknown name fell back to "balanced". */
    val presetName: String,
    /** Number of distinct CVar settings in the generated engine configuration. */
    val cvarCount: Int,
    /** CVar names present in the engine configuration. Empty unless explicitly requested. */
    val cvarNames: List<String>,
    /** Contents of Engine.ini, the main graphics configuration. */
    val engineIni: String,
    /** Contents of DeviceProfiles.ini, which selects the per-device quality profile. */
    val deviceProfilesIni: String,
    /** Contents of GameUserSettings.ini, which holds the user's own saved settings. */
    val gameUserSettingsIni: String,
    /** Contents of Scalability.ini, or empty when not requested. */
    val scalabilityIni: String,
    /** Contents of Hardware.ini, or empty when not requested. */
    val hardwareIni: String,
)

/** The last profile the app read from the device, or the parts it has. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DeviceProfileInfo(
    /** In-game player identifier, absent when the app has not read the device. */
    val playerUid: String,
    /** Game server the account plays on. */
    val server: String,
    /** Account level, absent when unknown. */
    val playerLevel: Int,
    /** Version of the game the profile was read from. */
    val gameVersion: String,
    /** Android version of the device. */
    val androidVersion: String,
    /** GPU model string exactly as the game reports it. */
    val gpu: String,
    /** SoC name. */
    val socName: String,
    /** Device RAM in megabytes. */
    val ramMb: Int,
    /** Screen resolution as reported, for example "1080x2400". */
    val resolution: String,
    /** Graphics API in use, for example "Vulkan" or "OpenGLES3". */
    val renderApi: String,
    /** Vulkan support status as the game reports it. */
    val vulkanStatus: String,
    /** Frame cap the game is configured for. */
    val frameRateCap: Int,
    /** Frame rate the game actually achieved, as a mean. */
    val measuredFrameRate: Float,
    /** Render scale percentage currently in use. */
    val renderScalePercent: Float,
    /** Shadow quality currently selected. */
    val shadowQuality: Int,
    /** Quality preset name currently selected in the game. */
    val qualityMode: String,
    /** Count of thermal throttling events seen in the log. */
    val thermalEvents: Int,
    /** Count of GPU out-of-memory events seen in the log. */
    val gpuOutOfMemoryEvents: Int,
    /** Count of dropped-frame events seen in the log. */
    val droppedFrames: Int,
    /** Count of texture load failures seen in the log. */
    val textureErrors: Int,
)

/** A scored preset suggestion derived from analysed device information. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class PresetRecommendation(
    /** Recommended preset identifier. Pass this to "generateConfig". */
    val presetName: String,
    /** Confidence from 0 to 100. Below 50 means the device information was thin, not that the preset is bad. */
    val score: Int,
    /** Detected GPU class, one of "flagship", "high", "mid_high", "mid", "mid_low", "low" or "unknown". */
    val gpuTier: String,
    /** What raised the score, in plain terms. */
    val signals: List<String>,
    /** What would make a better result possible, such as a missing log field. */
    val warnings: List<String>,
)

/** Combat and exploration counters parsed out of a game log. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class BattleStatsInfo(
    /** Number of completed battles. */
    val battles: Int,
    /** Echoes collected. */
    val echoesCollected: Int,
    /** Times the player died. */
    val deaths: Int,
    /** Enemy stagger events triggered. */
    val staggers: Int,
    /** Stamina consumed, in game units. */
    val staminaUsed: Int,
    /** Echo skills activated. */
    val echoSkillsUsed: Int,
    /** Echo transformations performed. */
    val echoTransformsUsed: Int,
    /** Teleports performed. */
    val teleports: Int,
    /** Character role changes. */
    val roleChanges: Int,
    /** Supply cards currently held. */
    val supplyCards: Int,
    /** Days remaining on the supply card benefit. */
    val supplyCardDaysRemaining: Int,
    /** Total playtime across all parsed log sessions, in seconds. */
    val playtimeSeconds: Long = 0L,
    /** Number of distinct play sessions estimated from log timestamps. */
    val sessions: Int = 0,
)

/** The outcome of analysing a game log. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GameLogAnalysis(
    /** Device model as the game reports it. */
    val deviceModel: String,
    /** GPU model string exactly as the game reports it. */
    val gpu: String,
    /** SoC name. */
    val socName: String,
    /** Device RAM in megabytes. */
    val ramMb: Int,
    /** Android version of the device. */
    val androidVersion: String,
    /** Screen resolution as reported, for example "1080x2400". */
    val resolution: String,
    /** Graphics API in use, for example "Vulkan" or "OpenGLES3". */
    val renderApi: String,
    /** Vulkan support status as the game reports it. */
    val vulkanStatus: String,
    /** Frame cap the game is configured for. */
    val frameRateCap: Int,
    /** Frame rate the game actually achieved, as a mean. */
    val measuredFrameRate: Float,
    /** Render scale percentage in use. */
    val renderScalePercent: Float,
    /** Shadow quality currently selected. */
    val shadowQuality: Int,
    /** Quality preset name currently selected in the game. */
    val qualityMode: String,
    /** True when the device is flagged low-memory by the game. */
    val lowMemoryDevice: Boolean,
    /** Count of dropped-frame events. */
    val droppedFrames: Int,
    /** Count of thermal throttling events. */
    val thermalEvents: Int,
    /** Count of GPU out-of-memory events. */
    val gpuOutOfMemoryEvents: Int,
    /** Count of texture load failures. */
    val textureErrors: Int,
    /** Count of restricted CVar entries found active. */
    val restrictedCvarCount: Int,
    /** Number of CVar settings read out of the log. */
    val activeCvarCount: Int,
    /** Whether the log was XOR-obfuscated and had to be decrypted first. */
    val wasEncrypted: Boolean,
    /** Size of the analysed log in bytes. */
    val logSizeBytes: Long,
    /** Combat and exploration counters, absent when the log contains none. */
    val battleStats: BattleStatsInfo,
    /** Preset the app would suggest for this log, so the caller can generate immediately. */
    val recommendation: PresetRecommendation,
)

/** One completed configuration deployment and, when measured, what it achieved. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DeployOutcome(
    /** Opaque identifier for this deployment. */
    val id: String,
    /** Preset that was deployed. */
    val presetName: String,
    /** When the deployment happened. */
    val deployedAtEpochMillis: Long,
    /** Configuration files that were written, for example "Engine.ini". */
    val filesDeployed: List<String>,
    /** CVar settings the game confirmed it accepted. */
    val acceptedCount: Int,
    /** CVar settings the deployment attempted in total. */
    val totalCount: Int,
    /** CVar settings commented out as redundant with a game default. */
    val redundantCount: Int,
    /** CVar settings commented out as unrecognised by the game. */
    val unknownCount: Int,
    /** Frame rate measured before the deployment, absent when never measured. */
    val baselineFrameRate: Float,
    /** Frame rate measured after the deployment, absent when never measured. */
    val outcomeFrameRate: Float,
    /** Change in frame rate. Positive means the deployment helped. */
    val frameRateDelta: Float,
    /** Change in thermal events. Positive means the deployment made the device hotter. */
    val thermalDelta: Int,
    /** Change in GPU out-of-memory events. Negative means the deployment helped. */
    val gpuOutOfMemoryDelta: Int,
    /** Change in dropped frames. Negative means the deployment helped. */
    val droppedFrameDelta: Int,
    /** False when the deployment was never measured, so every delta is absent. */
    val hasOutcome: Boolean,
)

/** A previously cached log analysis, used when the caller has no log to supply. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CachedLogAnalysisInfo(
    /** When the analysis ran, as epoch milliseconds. */
    val analysedAtEpochMillis: Long,
    /** Frame rate the game achieved in the analysed log. */
    val measuredFrameRate: Float,
    /** Count of dropped-frame events. */
    val droppedFrames: Int,
    /** Count of thermal throttling events. */
    val thermalEvents: Int,
    /** Count of GPU out-of-memory events. */
    val gpuOutOfMemoryEvents: Int,
    /** Count of texture load failures. */
    val textureErrors: Int,
    /** Count of restricted CVar entries found active. */
    val restrictedCvarCount: Int,
    /** Preset the app recommended at the time of analysis, empty when none was stored. */
    val recommendedPreset: String,
    /** Confidence of that recommendation, 0 to 100. */
    val recommendationScore: Int,
)

/** Per-pool gacha pity status from the app's cached gacha analysis. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GachaPredictionInfo(
    /** Pool identifier, for example "1" (Character Event). */
    val poolType: String,
    /** Human-readable pool name, for example "Character Event". */
    val poolLabel: String,
    /** Whether the next 5-star is "Guaranteed" to be the featured one, "50/50", or "Unknown". */
    val status: String,
    /** Name of the last 5-star pulled in this pool, empty when none. */
    val lastFiveStarName: String,
    /** When the last 5-star was pulled, as a game timestamp, empty when none. */
    val lastFiveStarTime: String,
    /** The featured character of the current banner, empty when unknown. */
    val currentFeaturedName: String,
    /** True when the featured character is known rather than guessed. */
    val currentFeaturedKnown: Boolean,
    /** Pulls since the last 5-star in this pool. */
    val pullsSinceLastFive: Int,
    /** Estimated pulls until the next 5-star, based on this pool's average. */
    val estimatedNextFive: Int,
    /** Pulls at which a 5-star is guaranteed (hard pity). */
    val hardPity: Int,
    /** Pulls at which the 5-star rate begins to increase (soft pity). */
    val softPityThreshold: Int,
    /** True when pullsSinceLastFive has reached the soft-pity threshold. */
    val isInSoftPity: Boolean,
    /** Pulls remaining until the hard-pity guarantee. */
    val pullsUntilHardPity: Int,
    /** Pulls since the last 4-star or 5-star in this pool. */
    val pullsSinceLastFourStar: Int,
    /** Estimated pulls until the next 4-star. */
    val estimatedNextFourStar: Int,
    /** Average 5-star pity observed in this pool. */
    val avgPityThisPool: Double,
    /** Chance the next 5-star is not the featured one, 0.0 to 1.0. */
    val nonBannerRate: Double,
    /** Chance the next 5-star is the featured one, 0.0 to 1.0. */
    val upRate: Double,
)

/** One item's lifetime pull count, from the retained gacha history. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GachaItemCountInfo(
    /** Character or weapon name as the game reports it. */
    val name: String,
    /** In-game resource id, used to resolve an avatar. Zero when the endpoint did not supply one. */
    val resourceId: Int,
    /** Lifetime number of pulls of this item. */
    val count: Int,
)

/** Lifetime totals across the whole retained gacha history. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class GachaStatsInfo(
    /**
     * How long ago these records were fetched, in hours.
     *
     * Null when the age cannot be known — a cache written before `fetchedAt`
     * existed reports no timestamp, and that is not the same as "fetched just now".
     * Collapsing it to zero would tell an agent the numbers are current when they
     * may be a year old, which is the one claim here that must not be wrong.
     */
    val ageHours: Long? = null,
    /**
     * True when the records are past the 12-hour freshness window, so they may no
     * longer describe the account. Still valid history — re-fetch for current numbers.
     */
    val isStale: Boolean,
    /** Lifetime number of pulls. */
    val totalPulls: Int,
    /** Lifetime currency equivalent of those pulls, using the game's cost per pull. */
    val totalCurrency: Long,
    /** Lifetime 5-star count. */
    val fiveStarCount: Int,
    /** Lifetime 4-star count. */
    val fourStarCount: Int,
    /** Lifetime 3-star count. */
    val threeStarCount: Int,
    /** Average pulls per 5-star over the history. Zero when the history holds no 5-star. */
    val averageFiveStarPity: Double,
    /** Average pulls per 4-star over the history. Zero when the history holds no 4-star. */
    val averageFourStarPity: Double,
    /**
     * Share of character-banner 50/50s won, from 0.0 to 1.0. Draws that the loss
     * guarantee covered are excluded from both sides. Zero when there was no 50/50.
     */
    val fiftyFiftyWinRate: Double,
    /** Timestamp of the earliest pull, empty when unknown. */
    val firstPullTime: String,
    /** Timestamp of the most recent pull, empty when unknown. */
    val lastPullTime: String,
    /** Lifetime pulls made in character pools. */
    val characterPulls: Int,
    /** Distinct 5-star character items obtained. */
    val characterFiveStarItems: Int,
    /** Lifetime pulls made in weapon pools. */
    val weaponPulls: Int,
    /** Distinct 5-star weapon items obtained. */
    val weaponFiveStarItems: Int,
    /** Most-pulled 5-star items, highest first. */
    val topFiveStars: List<GachaItemCountInfo>,
    /** Most-pulled 4-star items, highest first. */
    val topFourStars: List<GachaItemCountInfo>,
)

/** The result of looking up a CVar name in the game's CVar database. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CvarInfo(
    /** The CVar name as queried. */
    val name: String,
    /** True when the name exists in the game's CVar database. */
    val isKnown: Boolean,
    /** True when the name is in the monitored set the app tracks for drift. */
    val isMonitored: Boolean,
    /** The game's default value for this CVar, or null when unknown. */
    val gameDefault: String?,
    /** Functional category, for example "Lighting & Shadow". */
    val category: String,
    /** True when the name is one of the restricted CVars the game mishandles. */
    val isForbidden: Boolean,
)

/** One configuration backup stored by the app. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class BackupInfo(
    /** Opaque identifier for this backup. */
    val id: String,
    /** User-assigned backup name. */
    val name: String,
    /** When the backup was created, as epoch milliseconds. */
    val timestamp: Long,
    /** Configuration files stored in this backup. */
    val fileNames: List<String>,
)
