package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.PlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Why `PlayerProfile.deviceLogRead` exists.
 *
 * Every performance/diagnostic field on [PlayerProfile] is nullable-or-zero, so
 * the profile carried no way to distinguish two states the UI was rendering
 * identically:
 *
 *  - the log read succeeded and the session was simply clean, versus
 *  - the log could not be read at all.
 *
 * On the moto g(60) this was measured, not theorised: a full 10 MB read produced
 * `fpsCap = 60`, `screenPct = 60`, `shadowQ = 0`, `qualityMode = "0"` and all
 * five diagnostic counters at 0. `fpsActual` is null in *both* states because the
 * game emits no `AverageFPS` line at all (a desktop-UE stat), so it can never
 * distinguish them either.
 *
 * Consequence before this flag: `PerformanceSection` collapsed to its "no
 * performance data in the game log" empty state whenever the counters were all
 * zero, i.e. on every healthy session. A user could not tell a working read from
 * a broken one.
 */
class ProfileLogReadFlagTest {
    /**
     * A profile shaped exactly like a real successful read of a clean session:
     * populated where the game logs a value, zero/empty where it does not.
     * Taken from the on-device `player_profile.json` plus the known-absent FPS.
     */
    private val cleanSession =
        PlayerProfile(
            gpu = "Adreno (TM) 618",
            socName = "SM7150",
            ramMb = 5120,
            androidVersion = "16",
            resolution = "2456x1080",
            renderApi = "OpenGL ES",
            vulkanStatus = "not_available",
            fpsActual = null,
            fpsCap = 60,
            screenPct = 60f,
            shadowQ = 0,
            qualityMode = "0",
            thermalEvents = 0,
            gpuOom = 0,
            dropFrames = 0,
            textureErrors = 0,
            forbiddenCvars = 0,
            deviceLogRead = true,
        )

    @Test
    fun `a clean session is distinguishable from an unread log`() {
        val unread = cleanSession.copy(gpu = null, deviceLogRead = false)
        assertFalse("the unread profile must not claim the log was read", unread.deviceLogRead)
        assertTrue(cleanSession.deviceLogRead)
    }

    @Test
    fun `all-zero diagnostics no longer imply the log was unread`() {
        // The specific bug: these five were all zero on a real, successful read.
        assertEquals(0, cleanSession.thermalEvents)
        assertEquals(0, cleanSession.gpuOom)
        assertEquals(0, cleanSession.dropFrames)
        assertEquals(0, cleanSession.textureErrors)
        assertEquals(0, cleanSession.forbiddenCvars)
        assertTrue(
            "zero diagnostics must not read as a failed read",
            cleanSession.deviceLogRead,
        )
    }

    @Test
    fun `fpsActual is null even on a successful read so it cannot signal failure`() {
        // Worth pinning separately because it is the one field the old
        // `hasData` gate leaned on. The game logs no AverageFPS line, so this is
        // null forever regardless of read success.
        assertEquals(null, cleanSession.fpsActual)
        assertTrue(cleanSession.deviceLogRead)
    }

    @Test
    fun `the flag defaults to false so a stale cache is not read as healthy`() {
        // A hand-built profile and any pre-existing cached JSON both land here.
        // Conservative direction: show the "could not read" explanation rather
        // than assert a clean bill of health nobody measured.
        assertFalse(PlayerProfile().deviceLogRead)
    }

    @Test
    fun `the flag round-trips through the persisted JSON store`() {
        // ProfileStore persists to filesDir/player_profile.json. If this flag
        // were not serialised, every cached profile would come back as
        // deviceLogRead=false and re-show the unread-log warning on next launch
        // even though the read had succeeded.
        val json = com.google.gson.Gson().toJson(cleanSession)
        assertTrue("deviceLogRead must survive serialisation", json.contains("deviceLogRead"))
        val back = com.google.gson.Gson().fromJson(json, PlayerProfile::class.java)
        assertTrue(back.deviceLogRead)
        assertEquals(cleanSession, back)
    }
}
