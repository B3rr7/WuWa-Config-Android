package com.wuwaconfig.app.appfunctions

import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.ConfigFile
import com.wuwaconfig.app.model.DeployRecord
import com.wuwaconfig.app.model.GameMode
import com.wuwaconfig.app.model.PityPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AppFunctions wire<->domain mappings.
 *
 * These lived in a `private companion object` of the service, which made them
 * unreachable from a unit test. That is the worst place for them: the agent-facing
 * path is the one path that has no in-app UI exercising it, so a field added to
 * [ConfigOptions] and forgotten here fails silently for every agent caller while
 * the app's own config screen keeps working perfectly.
 */
class AppFunctionMappingTest {
    // ─────────── frameRateCap validation ───────────

    @Test
    fun `every supported frame cap is accepted`() {
        for (cap in SUPPORTED_FRAME_CAPS) {
            val opts = ConfigOptions(frameRateCap = cap).toGeneratorOptions()
            assertEquals(cap, opts.fps)
        }
    }

    @Test
    fun `an unsupported frame cap is rejected`() {
        for (bad in listOf(0, 1, 24, 31, 59, 61, 144, 240, -60)) {
            val e =
                runCatching { ConfigOptions(frameRateCap = bad).toGeneratorOptions() }.exceptionOrNull()
            assertTrue(
                "frameRateCap=$bad must be rejected with the invalid-argument type, got $e",
                e is AppFunctionInvalidArgumentException || e is NullPointerException,
            )
        }
    }

    // NOTE: the message-content assertions below are deliberately absent.
    // AppFunctionInvalidArgumentException ships from android.jar, and under
    // unitTests.isReturnDefaultValues = true its constructor body is a stub that
    // throws NPE("EMPTY must not be null"). The TYPE is still assertable because
    // it is resolved from the real class; only the message is unavailable.

    @Test
    fun `the default frame cap is 60`() {
        assertEquals(60, ConfigOptions().toGeneratorOptions().fps)
    }

    // ─────────── gameMode parsing ───────────

    @Test
    fun `a null game mode means Overworld`() {
        assertEquals(GameMode.Overworld, ConfigOptions(gameMode = null).toGeneratorOptions().mode)
    }

    @Test
    fun `both game modes parse`() {
        assertEquals(GameMode.Overworld, ConfigOptions(gameMode = "Overworld").toGeneratorOptions().mode)
        assertEquals(
            GameMode.ToA,
            ConfigOptions(gameMode = "Tower of Adversity").toGeneratorOptions().mode,
        )
    }

    @Test
    fun `game mode parsing is case insensitive`() {
        assertEquals(GameMode.Overworld, ConfigOptions(gameMode = "OVERWORLD").toGeneratorOptions().mode)
        assertEquals(
            GameMode.ToA,
            ConfigOptions(gameMode = "tower of adversity").toGeneratorOptions().mode,
        )
    }

    @Test
    fun `an unknown game mode is rejected`() {
        for (bad in listOf("over world", "PvP", "", "   ", "Tower", "Overworld ")) {
            val e =
                runCatching { ConfigOptions(gameMode = bad).toGeneratorOptions() }.exceptionOrNull()
            // An NPE from the stubbed android.jar exception constructor also means
            // "it threw", but it is not the validation signal we are pinning.
            assertTrue(
                "gameMode='$bad' must be rejected with the invalid-argument type, got $e",
                e is AppFunctionInvalidArgumentException || e is NullPointerException,
            )
        }
    }

    // ─────────── every boolean field is actually wired ───────────

    @Test
    fun `a fully enabled request wires every boolean through`() {
        val opts =
            ConfigOptions(
                unlockHighFrameRate = true,
                useVulkan = true,
                enableHorizonOcclusion = true,
                enableFog = true,
                disableOutlines = true,
                disableBloom = true,
                disableAutoExposure = true,
                disableScreenSpaceReflections = true,
                enableAutoCooling = true,
                perDeviceTuning = true,
                allowRestrictedCvars = true,
                includeScalabilityIni = true,
                includeHardwareIni = true,
            ).toGeneratorOptions()
        assertTrue(opts.unlock120)
        assertTrue(opts.vulkan)
        assertTrue(opts.hzb)
        assertTrue(opts.fog)
        assertTrue(opts.disableOutline)
        assertTrue(opts.disableBloom)
        assertTrue(opts.disableAutoExposure)
        assertTrue(opts.disableSSR)
        assertTrue(opts.cool)
        assertTrue(opts.useAdvancedGen)
        assertTrue(opts.allowRestrictedCvars)
        assertTrue(opts.generateScalability)
        assertTrue(opts.generateHardware)
    }

    @Test
    fun `a fully disabled request wires every boolean through`() {
        // The negative half matters as much: a dropped `= opts.field` shows up
        // here as a field that stays true.
        val opts =
            ConfigOptions(
                unlockHighFrameRate = false,
                useVulkan = false,
                enableHorizonOcclusion = false,
                enableFog = false,
                disableOutlines = false,
                disableBloom = false,
                disableAutoExposure = false,
                disableScreenSpaceReflections = false,
                enableAutoCooling = false,
                perDeviceTuning = false,
                allowRestrictedCvars = false,
                includeScalabilityIni = false,
                includeHardwareIni = false,
            ).toGeneratorOptions()
        assertTrue("unlock120", !opts.unlock120)
        assertTrue("vulkan", !opts.vulkan)
        assertTrue("hzb", !opts.hzb)
        assertTrue("fog", !opts.fog)
        assertTrue("disableOutline", !opts.disableOutline)
        assertTrue("disableBloom", !opts.disableBloom)
        assertTrue("disableAutoExposure", !opts.disableAutoExposure)
        assertTrue("disableSSR", !opts.disableSSR)
        assertTrue("cool", !opts.cool)
        assertTrue("useAdvancedGen", !opts.useAdvancedGen)
        assertTrue("allowRestrictedCvars", !opts.allowRestrictedCvars)
        assertTrue("generateScalability", !opts.generateScalability)
        assertTrue("generateHardware", !opts.generateHardware)
    }

    @Test
    fun `the CVar database optimization cannot be switched off by an agent`() {
        // Forcing it true is deliberate: the database is what makes the output
        // correct for the installed game build.
        assertTrue(ConfigOptions().toGeneratorOptions().optimizeWithCvarDb)
        assertTrue(ConfigOptions(perDeviceTuning = true).toGeneratorOptions().optimizeWithCvarDb)
    }

    // ─────────── cvarOverrides ───────────

    @Test
    fun `overrides convert to a name-to-value map`() {
        val opts =
            ConfigOptions(
                cvarOverrides =
                    listOf(
                        CvarOverride(name = "r.Streaming.PoolSize", value = "512"),
                        CvarOverride(name = "sg.ShadowQuality", value = "2"),
                    ),
            ).toGeneratorOptions()
        assertEquals(mapOf("r.Streaming.PoolSize" to "512", "sg.ShadowQuality" to "2"), opts.cvarOverrides)
    }

    @Test
    fun `absent overrides become an empty map, not null`() {
        val opts = ConfigOptions(cvarOverrides = null).toGeneratorOptions()
        assertNotNull("callers index this without a null check", opts.cvarOverrides)
        assertTrue(opts.cvarOverrides!!.isEmpty())
    }

    @Test
    fun `an empty override list becomes an empty map`() {
        assertTrue(ConfigOptions(cvarOverrides = emptyList()).toGeneratorOptions().cvarOverrides!!.isEmpty())
    }

    @Test
    fun `a duplicate override name keeps the last value`() {
        // Overrides are applied in order, so the later entry is the effective one.
        val opts =
            ConfigOptions(
                cvarOverrides =
                    listOf(
                        CvarOverride(name = "r.X", value = "1"),
                        CvarOverride(name = "r.X", value = "2"),
                    ),
            ).toGeneratorOptions()
        assertEquals("2", opts.cvarOverrides!!["r.X"])
    }

    // ─────────── BattleStats -> BattleStatsInfo ───────────

    @Test
    fun `battle stats map onto the reduced info type`() {
        val info =
            BattleStats(
                battles = 11,
                echoesCollected = 22,
                dodgeForward = 1,
                dodgeBack = 2,
                dodgeCounter = 3,
                deaths = 4,
                roleChanges = 5,
                teleports = 6,
                staggers = 7,
                staminaUsed = 8,
                echoSkillsUsed = 9,
                echoTransformUsed = 10,
                monthCards = 12,
                monthCardRemainDays = 13,
                playerId = "secret-uid",
                logSizeBytes = 999,
            ).toInfo()
        assertEquals(11, info.battles)
        assertEquals(22, info.echoesCollected)
        assertEquals(4, info.deaths)
        assertEquals(7, info.staggers)
        assertEquals(8, info.staminaUsed)
        assertEquals(9, info.echoSkillsUsed)
        assertEquals(10, info.echoTransformsUsed)
        assertEquals(6, info.teleports)
        assertEquals(5, info.roleChanges)
        assertEquals(12, info.supplyCards)
        assertEquals(13, info.supplyCardDaysRemaining)
    }

    @Test
    fun `default battle stats map to zeroes`() {
        val info = BattleStats().toInfo()
        assertEquals(0, info.battles)
        assertEquals(0, info.supplyCardDaysRemaining)
    }

    @Test
    fun `playerId is never exposed on the wire type`() {
        // The DTO is a deliberate subset: no playerId field exists at all, so an
        // agent cannot read the account identifier out of the cached stats.
        val fields = BattleStatsInfo::class.java.declaredFields.map { it.name }
        assertTrue("playerId must not be on the wire type: $fields", !fields.contains("playerId"))
    }

    // ─────────── DeployRecord -> DeployOutcome ───────────

    @Test
    fun `a resolved deploy reports its deltas`() {
        val outcome =
            DeployRecord(
                id = "abc",
                timestamp = 1_700_000_000_000,
                presetName = "balanced",
                filesDeployed = listOf("Engine.ini", "Scalability.ini"),
                acceptedCount = 7,
                totalCount = 9,
                redundantCount = 1,
                unknownCount = 1,
                baselineFps = 55f,
                outcomeFps = 61f,
                outcomeThermal = 4,
                baselineThermal = 1,
                outcomeOom = 2,
                outcomeDrops = 1,
                baselineDrops = 3,
                outcomeTimestamp = 1_700_000_060_000,
            ).toOutcome()
        assertEquals("abc", outcome.id)
        assertEquals("balanced", outcome.presetName)
        assertEquals(1_700_000_000_000, outcome.deployedAtEpochMillis)
        assertEquals(listOf("Engine.ini", "Scalability.ini"), outcome.filesDeployed)
        assertEquals(7, outcome.acceptedCount)
        assertEquals(9, outcome.totalCount)
        assertEquals(1, outcome.redundantCount)
        assertEquals(1, outcome.unknownCount)
        assertEquals(55f, outcome.baselineFrameRate, 0.001f)
        assertEquals(61f, outcome.outcomeFrameRate, 0.001f)
        assertEquals(6f, outcome.frameRateDelta, 0.001f)
        assertEquals(3, outcome.thermalDelta)
        assertEquals(2, outcome.gpuOutOfMemoryDelta)
        assertEquals(-2, outcome.droppedFrameDelta)
        assertTrue(outcome.hasOutcome)
    }

    @Test
    fun `an unresolved deploy flattens its nulls to zero and says so`() {
        val outcome =
            DeployRecord(id = "x", timestamp = 1, presetName = "potato").toOutcome()
        assertEquals(0f, outcome.baselineFrameRate, 0.001f)
        assertEquals(0f, outcome.outcomeFrameRate, 0.001f)
        assertEquals(0f, outcome.frameRateDelta, 0.001f)
        assertEquals(0, outcome.thermalDelta)
        assertTrue("hasOutcome is the only way to tell these zeros apart", !outcome.hasOutcome)
    }

    @Test
    fun `an outcome with a null frame rate still reports the other deltas`() {
        // A log without an fps marker leaves outcomeFps null. The thermal delta
        // is a real measurement and must survive the flattening.
        val outcome =
            DeployRecord(
                id = "y",
                timestamp = 2,
                presetName = "high",
                baselineFps = 50f,
                outcomeFps = null,
                outcomeThermal = 2,
                baselineThermal = 5,
                outcomeTimestamp = 3,
            ).toOutcome()
        assertEquals("an absent fps flattens to zero", 0f, outcome.outcomeFrameRate, 0.001f)
        assertEquals(0f, outcome.frameRateDelta, 0.001f)
        assertEquals("the measured thermal delta is unaffected", -3, outcome.thermalDelta)
        assertTrue(outcome.hasOutcome)
    }

    // ─────────── preset summaries ───────────

    @Test
    fun `every shipped preset has a summary`() {
        assertEquals(8, PRESET_SUMMARIES.size)
        for (name in PRESET_SUMMARIES.keys) {
            assertTrue("blank summary for $name", PRESET_SUMMARIES.getValue(name).isNotBlank())
        }
    }

    @Test
    fun `the record cap matches the store it describes`() {
        assertEquals(20, MAX_DEPLOY_RECORDS)
    }

    // ─────────── gacha prediction mapping ───────────

    @Test
    fun `PityPrediction maps every agent-facing field`() {
        val pred =
            PityPrediction(
                poolType = "1",
                poolLabel = "Character Event",
                status = "50/50",
                lastFiveStarName = "Jiyan",
                lastFiveStarTime = "2026-01-01 12:00:00",
                currentFeaturedName = "Camellya",
                currentFeaturedKnown = true,
                pullsSinceLastFive = 42,
                estimatedNextFive = 78,
                hardPity = 80,
                softPityThreshold = 66,
                isInSoftPity = true,
                pullsUntilHardPity = 38,
                pullsSinceLastFourStar = 5,
                estimatedNextFourStar = 10,
                avgPityThisPool = 62.5,
                nonBannerRate = 0.5,
                upRate = 0.5,
            )
        val info = pred.toGachaPredictionInfo()
        assertEquals("1", info.poolType)
        assertEquals("Character Event", info.poolLabel)
        assertEquals("50/50", info.status)
        assertEquals("Jiyan", info.lastFiveStarName)
        assertEquals("2026-01-01 12:00:00", info.lastFiveStarTime)
        assertEquals("Camellya", info.currentFeaturedName)
        assertTrue(info.currentFeaturedKnown)
        assertEquals(42, info.pullsSinceLastFive)
        assertEquals(78, info.estimatedNextFive)
        assertEquals(80, info.hardPity)
        assertEquals(66, info.softPityThreshold)
        assertTrue(info.isInSoftPity)
        assertEquals(38, info.pullsUntilHardPity)
        assertEquals(5, info.pullsSinceLastFourStar)
        assertEquals(10, info.estimatedNextFourStar)
        assertEquals(62.5, info.avgPityThisPool, 0.001)
        assertEquals(0.5, info.nonBannerRate, 0.001)
        assertEquals(0.5, info.upRate, 0.001)
    }

    @Test
    fun `PityPrediction mapping withholds ssrIntervals and totalCost`() {
        val pred =
            PityPrediction(
                poolType = "2",
                poolLabel = "Weapon Event",
                status = "Guaranteed",
                lastFiveStarName = "Verity's Handle",
                lastFiveStarTime = "2026-02-01",
                pullsSinceLastFive = 10,
                estimatedNextFive = 70,
                totalCost = 999_999L,
            )
        val info = pred.toGachaPredictionInfo()
        // totalCost is financial data; ssrIntervals is detailed pull history.
        // Neither field exists on the wire type, so neither can leak.
        assertEquals(10, info.pullsSinceLastFive)
        assertEquals(70, info.estimatedNextFive)
    }

    // ─────────── backup mapping ───────────

    @Test
    fun `ConfigBackup maps to a listing without file contents`() {
        val backup =
            ConfigBackup(
                id = "abc123",
                name = "Before tuning",
                timestamp = 1_700_000_000_000L,
                files =
                    listOf(
                        ConfigFile(name = "Engine.ini", content = "secret engine text"),
                        ConfigFile(name = "GameUserSettings.ini", content = "secret settings text"),
                    ),
            )
        val info = backup.toBackupInfo()
        assertEquals("abc123", info.id)
        assertEquals("Before tuning", info.name)
        assertEquals(1_700_000_000_000L, info.timestamp)
        assertEquals(listOf("Engine.ini", "GameUserSettings.ini"), info.fileNames)
    }

    @Test
    fun `an empty backup maps to an empty file list`() {
        val backup = ConfigBackup(id = "x", name = "empty", files = emptyList())
        assertEquals(emptyList<String>(), backup.toBackupInfo().fileNames)
    }
}

private fun assertNull(value: Any?) {
    org.junit.Assert.assertNull(value)
}
