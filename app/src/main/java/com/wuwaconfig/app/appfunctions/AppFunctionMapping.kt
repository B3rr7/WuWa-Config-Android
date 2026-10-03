package com.wuwaconfig.app.appfunctions

import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.wuwaconfig.app.config.GachaItemCount
import com.wuwaconfig.app.config.GachaStatsResult
import com.wuwaconfig.app.config.gameProfile
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.DeployRecord
import com.wuwaconfig.app.model.GameMode
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.model.PityPrediction

/** Mirrors DeployHistoryStore.MAX_RECORDS; the store evicts silently beyond it. */
internal const val MAX_DEPLOY_RECORDS = 20

internal val SUPPORTED_FRAME_CAPS: Set<Int> = gameProfile().supportedFrameCaps.toSet()

/**
 * The one-line blurbs the config screen shows beside each preset. Lived in that
 * screen's own hardcoded list until the AppFunctions surface needed them; kept here so
 * an agent reads the same wording a user sees.
 */
internal val PRESET_SUMMARIES =
    mapOf(
        "potato" to "Minimum settings, for devices that cannot hold a stable frame rate",
        "endurance" to "Long sessions on mid-tier hardware, favouring low heat over fidelity",
        "performance" to "Stability first, when frame pacing matters more than looks",
        "competitive" to "Maximum clarity without post-processing clutter, for PvP",
        "balanced" to "The daily default",
        "high" to "Sharper visuals, for hardware that can hold them",
        "ultra" to "Flagship devices only",
        "cinematic" to "Above ultra, flagship hardware only",
    )

/**
 * Wire type -> domain type for the config generator.
 *
 * Deliberately a subset of [GeneratorOptions]: the domain type has 29 fields and an
 * agent should not have to supply all of them. Every field that IS surfaced has to be
 * wired, which is the whole reason this mapping is pinned by tests — a field added to
 * [ConfigOptions] and silently dropped here would fail silently for every agent caller
 * while the app's own UI path kept working.
 *
 * `optimizeWithCvarDb` is the one field with no wire counterpart: it is forced true
 * because the CVar database is what makes the generated INIs correct on a given game
 * build, and an agent opting out would get configs the game silently ignores.
 *
 * gameMode is nullable in [ConfigOptions] because the appfunctions schema has no
 * non-null string with a default; null carries the app's own default.
 */
internal fun ConfigOptions.toGeneratorOptions(): GeneratorOptions {
    if (frameRateCap !in SUPPORTED_FRAME_CAPS) {
        throw AppFunctionInvalidArgumentException(
            "frameRateCap must be one of ${SUPPORTED_FRAME_CAPS.sorted().joinToString(", ")}, but was $frameRateCap.",
        )
    }
    val mode =
        GameMode.entries.firstOrNull { it.label.equals(gameMode ?: "Overworld", ignoreCase = true) }
            ?: throw AppFunctionInvalidArgumentException(
                "gameMode must be \"Overworld\" or \"Tower of Adversity\", but was \"$gameMode\".",
            )
    return GeneratorOptions(
        fps = frameRateCap,
        unlock120 = unlockHighFrameRate,
        cool = enableAutoCooling,
        vulkan = useVulkan,
        hzb = enableHorizonOcclusion,
        fog = enableFog,
        disableOutline = disableOutlines,
        disableBloom = disableBloom,
        disableAutoExposure = disableAutoExposure,
        disableSSR = disableScreenSpaceReflections,
        mode = mode,
        cvarOverrides = cvarOverrides.orEmpty().associate { it.name to it.value },
        generateScalability = includeScalabilityIni,
        generateHardware = includeHardwareIni,
        allowRestrictedCvars = allowRestrictedCvars,
        optimizeWithCvarDb = true,
        useAdvancedGen = perDeviceTuning,
    )
}

/**
 * The deliberately reduced [BattleStatsInfo]: 11 of the 16 fields. `playerId` is
 * withheld (it is the account identifier, and an agent has no use for it), and
 * `logSizeBytes` is an artefact of how the stats were read rather than a gameplay
 * figure.
 */
internal fun BattleStats.toInfo(): BattleStatsInfo =
    BattleStatsInfo(
        battles = battles,
        echoesCollected = echoesCollected,
        deaths = deaths,
        staggers = staggers,
        staminaUsed = staminaUsed,
        echoSkillsUsed = echoSkillsUsed,
        echoTransformsUsed = echoTransformUsed,
        teleports = teleports,
        roleChanges = roleChanges,
        supplyCards = monthCards,
        supplyCardDaysRemaining = monthCardRemainDays,
    )

/**
 * Nullable deltas are flattened to their zero defaults because the wire type has no
 * null for them; [hasOutcome] is the field that tells the caller whether those zeros
 * are measurements or just "not measured yet".
 */
internal fun DeployRecord.toOutcome(): DeployOutcome {
    val delta = comparison()
    return DeployOutcome(
        id = id,
        presetName = presetName,
        deployedAtEpochMillis = timestamp,
        filesDeployed = filesDeployed,
        acceptedCount = acceptedCount,
        totalCount = totalCount,
        redundantCount = redundantCount,
        unknownCount = unknownCount,
        baselineFrameRate = baselineFps ?: 0f,
        outcomeFrameRate = outcomeFps ?: 0f,
        frameRateDelta = delta.fpsDelta ?: 0f,
        thermalDelta = delta.thermalDelta ?: 0,
        gpuOutOfMemoryDelta = delta.oomDelta ?: 0,
        droppedFrameDelta = delta.dropFramesDelta ?: 0,
        hasOutcome = hasOutcome,
    )
}

/**
 * The deliberately reduced [GachaPredictionInfo]: every field of [PityPrediction]
 * except `ssrIntervals` (detailed pull history, not needed for "when's my next
 * 5-star?") and `totalCost` (a spending total, which is financial data the agent
 * has no use for). The account identifier and raw gacha URL are never part of
 * [PityPrediction] in the first place.
 */
internal fun PityPrediction.toGachaPredictionInfo(): GachaPredictionInfo =
    GachaPredictionInfo(
        poolType = poolType,
        poolLabel = poolLabel,
        status = status,
        lastFiveStarName = lastFiveStarName,
        lastFiveStarTime = lastFiveStarTime,
        currentFeaturedName = currentFeaturedName,
        currentFeaturedKnown = currentFeaturedKnown,
        pullsSinceLastFive = pullsSinceLastFive,
        estimatedNextFive = estimatedNextFive,
        hardPity = hardPity,
        softPityThreshold = softPityThreshold,
        isInSoftPity = isInSoftPity,
        pullsUntilHardPity = pullsUntilHardPity,
        pullsSinceLastFourStar = pullsSinceLastFourStar,
        estimatedNextFourStar = estimatedNextFourStar,
        avgPityThisPool = avgPityThisPool,
        nonBannerRate = nonBannerRate,
        upRate = upRate,
    )

/** [ConfigBackup] reduced to its listing fields; file contents are not exposed. */
internal fun ConfigBackup.toBackupInfo(): BackupInfo =
    BackupInfo(
        id = id,
        name = name,
        timestamp = timestamp,
        fileNames = files.map { it.name },
    )

internal fun GachaItemCount.toInfo(): GachaItemCountInfo = GachaItemCountInfo(name = name, resourceId = resourceId, count = count)

/**
 * Lifetime totals plus the freshness of the record set they came from.
 *
 * The age and staleness are carried on the wire rather than left for the caller to
 * infer, because "average pity 55" and "average pity 55, measured three weeks ago"
 * are different claims and an agent quoting the first as current would be wrong.
 */
internal fun GachaStatsResult.toInfo(
    ageHours: Long?,
    isStale: Boolean,
): GachaStatsInfo =
    GachaStatsInfo(
        // Passed through as null rather than flattened to zero: an unknown age and a
        // just-now fetch are different claims, and only one of them is a lie.
        ageHours = ageHours,
        isStale = isStale,
        totalPulls = totalPulls,
        totalCurrency = totalCurrency,
        fiveStarCount = fiveStarCount,
        fourStarCount = fourStarCount,
        threeStarCount = threeStarCount,
        averageFiveStarPity = averageFiveStarPity,
        averageFourStarPity = averageFourStarPity,
        fiftyFiftyWinRate = fiftyFiftyWinRate,
        firstPullTime = firstPullTime,
        lastPullTime = lastPullTime,
        characterPulls = characterPulls,
        characterFiveStarItems = characterFiveStarItems,
        weaponPulls = weaponPulls,
        weaponFiveStarItems = weaponFiveStarItems,
        topFiveStars = topFiveStars.map { it.toInfo() },
        topFourStars = topFourStars.map { it.toInfo() },
    )
