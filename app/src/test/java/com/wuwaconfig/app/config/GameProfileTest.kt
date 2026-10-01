package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.GamePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The asset is only useful if it agrees with the code.
 *
 * `GameProfile` exists so a game update can be tracked by editing
 * `assets/config/game_profile.properties` instead of recompiling. That only holds
 * if two things stay true, and both are silently violated by ordinary editing:
 *
 *  1. **The asset and the compiled-in defaults carry the same values.** They are
 *     duplicated by design — the defaults are the fallback used when the asset is
 *     missing from a split APK — so editing the asset without editing the default
 *     would make behaviour depend on whether the asset loaded. A user on a broken
 *     split would silently get the game's old path and a deploy into a directory
 *     that does not exist.
 *  2. **The asset actually parses and every list is non-empty.** An empty
 *     `forbiddenCvars` or `cvarPrefixes` disables an entire subsystem rather than
 *     erroring.
 *
 * This test reads the real asset off disk, exactly like
 * [RealCvarCorpus] does for the CVar corpus.
 */
class GameProfileTest {
    private fun loadRealAsset(): GameProfile {
        // Read the file directly so these assertions are about the file's
        // contents, not about how the loader happens to wrap them.
        val props = java.util.Properties()
        props.load(realProfileFile().inputStream())
        return GameProfile(props)
    }

    @Test
    fun `the asset file exists and is not empty`() {
        val text = realProfileFile().readText()
        assertTrue("game_profile.properties is empty", text.isNotBlank())
        assertTrue("no keys parsed", text.lines().any { it.contains('=') })
    }

    @Test
    fun `no list-valued key is empty in the asset`() {
        val profile = loadRealAsset()
        assertFalse("cvarPrefixes", profile.cvarPrefixes.isEmpty())
        assertFalse("forbiddenCvars", profile.forbiddenCvars.isEmpty())
        assertFalse("ue5OnlyCvars", profile.ue5OnlyCvars.isEmpty())
        assertFalse("engineKeywords", profile.engineKeywords.isEmpty())
        assertFalse("supportedFrameCaps", profile.supportedFrameCaps.isEmpty())
    }

    // ── asset vs. compiled-in default parity ──

    @Test
    fun `build identity matches the compiled-in defaults`() {
        val p = loadRealAsset()
        assertEquals(GameProfile.DEFAULT_GAME_VERSION, p.gameVersion)
        assertEquals(GameProfile.DEFAULT_VERSION_CODE, p.versionCode)
        assertEquals(GameProfile.DEFAULT_ENGINE_GENERATION, p.engineGeneration)
    }

    @Test
    fun `paths match the compiled-in defaults`() {
        val p = loadRealAsset()
        assertEquals(GameProfile.DEFAULT_PACKAGE, p.targetPackage)
        assertEquals(GameProfile.DEFAULT_CONFIG_PATH, p.configPath)
        assertEquals(GameProfile.DEFAULT_LOG_PATH, p.logPath)
        assertEquals(GameProfile.DEFAULT_LOG_FILE_NAME, p.logFileName)
        assertEquals(GameProfile.DEFAULT_HASH_MONITOR_REL_PATH, p.hashMonitorRelPath)
        assertEquals(GameProfile.DEFAULT_UE4_COMMAND_LINE_REL_PATH, p.ue4CommandLineRelPath)
        assertEquals(GameProfile.DEFAULT_BACKUP_LOG_NAME_PATTERN, p.backupLogNamePattern)
        assertEquals(GameProfile.DEFAULT_BACKUP_STAMP_PATTERN, p.backupStampPattern)
        assertEquals(GameProfile.DEFAULT_LOCAL_STORAGE_DB_REL, p.localStorageDbRel)
        assertEquals(GameProfile.DEFAULT_DEVICE_STORAGE_DB_REL, p.deviceStorageDbRel)
        assertEquals(GameProfile.DEFAULT_LOCAL_STORAGE_TABLE, p.localStorageTable)
        assertEquals(GameProfile.DEFAULT_DEVICE_STORAGE_TABLE, p.deviceStorageTable)
    }

    @Test
    fun `flags and caps match the compiled-in defaults`() {
        val p = loadRealAsset()
        assertEquals(GameProfile.DEFAULT_FORCE_CSHARP_FLAG, p.forceCSharpEnvFlag)
        assertEquals(GameProfile.DEFAULT_HASH_MODIFY_COUNT_CAP, p.hashModifyCountCap)
        assertEquals(GameProfile.DEFAULT_SUPPORTED_FRAME_CAPS, p.supportedFrameCaps)
    }

    @Test
    fun `gacha economy matches the compiled-in defaults`() {
        val p = loadRealAsset()
        assertEquals(GameProfile.DEFAULT_HARD_PITY, p.hardPity)
        assertEquals(GameProfile.DEFAULT_SOFT_PITY_START, p.softPityStart)
        assertEquals(GameProfile.DEFAULT_FOUR_STAR_GUARANTEE, p.fourStarGuarantee)
        assertEquals(GameProfile.DEFAULT_CURRENCY_PER_PULL, p.currencyPerPull)
        assertEquals(GameProfile.DEFAULT_AVG_WEAPON_PITY, p.avgWeaponPityFallback)
        assertEquals(GameProfile.DEFAULT_GACHA_HOST_ID_PREFIX1, p.gachaHostIdPrefix1)
        assertEquals(GameProfile.DEFAULT_GACHA_HOST_OTHER, p.gachaHostOther)
        assertEquals(GameProfile.DEFAULT_GACHA_QUERY_PATH, p.gachaQueryPath)
        assertEquals(GameProfile.DEFAULT_CONVENE_URL_PATTERN, p.conveneUrlPattern)
        assertEquals(GameProfile.DEFAULT_SOFT_PITY_RATE, p.softPityRateAtThreshold, 1e-9)
    }

    @Test
    fun `CVar lists match the compiled-in defaults exactly`() {
        val p = loadRealAsset()
        assertEquals(GameProfile.DEFAULT_CVAR_PREFIXES, p.cvarPrefixes)
        assertEquals(GameProfile.DEFAULT_FORBIDDEN_CVARS, p.forbiddenCvars)
        assertEquals(GameProfile.DEFAULT_UE5_ONLY_CVARS, p.ue5OnlyCvars)
        assertEquals(GameProfile.DEFAULT_ENGINE_KEYWORDS, p.engineKeywords)
    }

    // ── degradation when the asset is unusable ──

    @Test
    fun `an empty profile falls back to every default`() {
        val empty = GameProfile(java.util.Properties())
        assertEquals(GameProfile.DEFAULT_GAME_VERSION, empty.gameVersion)
        assertEquals(GameProfile.DEFAULT_VERSION_CODE, empty.versionCode)
        assertEquals(GameProfile.DEFAULT_PACKAGE, empty.targetPackage)
        assertEquals(GameProfile.DEFAULT_CONFIG_PATH, empty.configPath)
        assertEquals(GameProfile.DEFAULT_CVAR_PREFIXES, empty.cvarPrefixes)
        assertEquals(GameProfile.DEFAULT_FORBIDDEN_CVARS, empty.forbiddenCvars)
        assertEquals(GameProfile.DEFAULT_ENGINE_KEYWORDS, empty.engineKeywords)
    }

    @Test
    fun `a missing key falls back rather than blanking the field`() {
        val props = java.util.Properties()
        props.setProperty("gameVersion", "9.9.9")
        val partial = GameProfile(props)
        assertEquals("the one provided key wins", "9.9.9", partial.gameVersion)
        assertEquals("the absent key falls back", GameProfile.DEFAULT_PACKAGE, partial.targetPackage)
        assertEquals(GameProfile.DEFAULT_CONFIG_PATH, partial.configPath)
    }

    @Test
    fun `a blank value is treated as absent`() {
        val props = java.util.Properties()
        props.setProperty("configPath", "   ")
        val blank = GameProfile(props)
        assertEquals(GameProfile.DEFAULT_CONFIG_PATH, blank.configPath)
    }

    @Test
    fun `a non-numeric int falls back instead of throwing`() {
        val props = java.util.Properties()
        props.setProperty("versionCode", "not-a-number")
        props.setProperty("hardPity", "")
        val bad = GameProfile(props)
        assertEquals(GameProfile.DEFAULT_VERSION_CODE, bad.versionCode)
        assertEquals(GameProfile.DEFAULT_HARD_PITY, bad.hardPity)
    }

    // ── staleness signal ──

    @Test
    fun `an older installed game is not reported as a stale database`() {
        val p = loadRealAsset()
        assertFalse(p.isCvarDbStaleFor(GameProfile.DEFAULT_VERSION_CODE - 1))
    }

    @Test
    fun `the exact database version is not stale`() {
        val p = loadRealAsset()
        assertFalse(p.isCvarDbStaleFor(GameProfile.DEFAULT_VERSION_CODE))
    }

    @Test
    fun `a newer installed game is reported as a stale database`() {
        val p = loadRealAsset()
        assertTrue("a game newer than the DB must be flagged", p.isCvarDbStaleFor(GameProfile.DEFAULT_VERSION_CODE + 1))
    }

    @Test
    fun `the minimum database version defaults to the profile version`() {
        val p = GameProfile(java.util.Properties())
        assertEquals(p.versionCode, p.minCvarDbVersionCode)
    }

    // ── regex-valued fields must actually compile ──

    @Test
    fun `the backup log name pattern compiles and matches a real device name`() {
        val p = loadRealAsset()
        val re = Regex(p.backupLogNamePattern)
        assertTrue(
            re.matches("Client-backup-2026.09.30-12.26.46-2026.09.30-13.14.07.log"),
        )
        assertTrue(re.matches("Client-backup-null-2026.09.06-21.33.30.log"))
        assertFalse("a shell-injected name must not pass", re.matches("; rm -rf /"))
        assertFalse(re.matches("Client.log"))
    }

    @Test
    fun `the backup stamp pattern compiles and extracts a stamp`() {
        val p = loadRealAsset()
        val m = Regex(p.backupStampPattern).find("Client-backup-2026.09.05-21.13.08-2026.09.05-21.26.29.log")
        assertNotNull(m)
        assertEquals("2026", m!!.groupValues[1])
        assertEquals("09", m.groupValues[2])
        assertEquals("05", m.groupValues[3])
    }

    @Test
    fun `the convene URL pattern compiles and matches the real page shape`() {
        val p = loadRealAsset()
        val re = Regex(p.conveneUrlPattern)
        assertTrue(
            re.containsMatchIn(
                "https://aki-gm-resources.aki-game.com/aki/gacha/index.html#/record?player_id=1&record_id=2&gacha_type=1",
            ),
        )
    }

    // ── GamePaths derives from the profile ──

    @Test
    fun `GamePaths builds its absolute paths from the profile's relative ones`() {
        val p = GameProfile.get()
        val root = "/storage/emulated/0/Android/data/${p.targetPackage}"
        assertTrue(GamePaths.TARGET_DIR.startsWith(root))
        assertTrue(GamePaths.TARGET_DIR.endsWith(p.configPath))
        assertTrue(GamePaths.LOG_DIR.endsWith(p.logPath))
        assertTrue(GamePaths.HASH_MONITOR_PATH.endsWith(p.hashMonitorRelPath))
        assertTrue(GamePaths.UE4_COMMAND_LINE_PATH.endsWith(p.ue4CommandLineRelPath))
    }

    @Test
    fun `every derived path shares the same root`() {
        val root = "/storage/emulated/0/Android/data/${GameProfile.get().targetPackage}"
        for (path in listOf(GamePaths.TARGET_DIR, GamePaths.LOG_DIR, GamePaths.HASH_MONITOR_PATH, GamePaths.UE4_COMMAND_LINE_PATH)) {
            assertTrue("$path does not start with $root", path.startsWith(root))
        }
    }

    @Test
    fun `the monitored file list is the five generated INIs`() {
        assertEquals(5, GamePaths.MONITORED_FILES.size)
        assertTrue(GamePaths.MONITORED_FILES.containsAll(listOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini", "Scalability.ini", "Hardware.ini")))
        assertEquals(GamePaths.MONITORED_FILES.toSet(), GamePaths.MONITORED_FILE_SET)
    }
}

/**
 * Locates `assets/config/game_profile.properties` by walking up from the working
 * directory, for the same reason [RealCvarCorpus] does: the runner's cwd differs
 * between Gradle, the IDE and a bare `gradle test`.
 */
internal fun realProfileFile(): File {
    val rel = "app/src/main/assets/config/game_profile.properties"
    var dir: File? = File(".").absoluteFile
    val tried = mutableListOf<String>()
    while (dir != null) {
        val direct = File(dir, rel)
        tried += direct.path
        if (direct.isFile) return direct
        val fromModule = File(dir, "src/main/assets/config/game_profile.properties")
        tried += fromModule.path
        if (fromModule.isFile) return fromModule
        dir = dir.parentFile
    }
    throw AssertionError("game_profile.properties not found; tried:\n  " + tried.joinToString("\n  "))
}
