package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Both databases on this device name their table "LocalStorage" — the FILE name and
 * the TABLE name are unrelated. Verified on-device:
 *
 *   Saved/LocalStorage/LocalStorage.db  -> table "LocalStorage", 49152 B
 *   Saved/DeviceSaved/DeviceStorage.db  -> table "LocalStorage", 12288 B
 *
 * Parameterising the table per database and naming the second one "DeviceStorage"
 * (after the file) made every DeviceStorage query throw "no such table", the bare
 * catch swallowed it, and language + all three version fields silently came back
 * null. That is what "some data not loading" on the Player Profile screen was.
 */
class ProfileExtractionSchemaTest {
    /** The exact SdkLevelData value read off the device. */
    private val realSdkLevelData =
        """{"___MetaType___":"___Map___","Content":[["504796016",[{"Region":"Asia","Level":80}]]]}"""

    @Test
    fun `the DeviceStorage db uses the LocalStorage table name`() {
        // The constant the app passes as the table for DeviceSaved/DeviceStorage.db.
        assertEquals("LocalStorage", ProfileExtractor.DEVICE_STORAGE_TABLE)
        assertEquals("LocalStorage", ProfileExtractor.LOCAL_STORAGE_TABLE)
    }

    /**
     * A previous implementation blanked every character nested deeper than one brace
     * level, ignoring `[` / `]`. The server object inside the Content ARRAY therefore
     * reached brace-depth 2 and its Region/Level were blanked out, so this returned an
     * empty list for every real profile — taking PlayerProfile.server and
     * playerLevel with it, since both come from the first pair.
     */
    @Test
    fun `the real SdkLevelData payload yields its region and level`() {
        val parsed = parseServerLevels(realSdkLevelData)
        assertEquals("must not lose the only server in the payload", 1, parsed.size)
        assertEquals("Asia" to 80, parsed.first())
    }

    @Test
    fun `an array of server objects all parse`() {
        val json =
            """{"___MetaType___":"___Map___","Content":[["1",[{"Region":"Asia","Level":80},{"Region":"Europe","Level":42}]]]}"""
        val parsed = parseServerLevels(json)
        assertEquals(2, parsed.size)
        assertEquals("Asia" to 80, parsed[0])
        assertEquals("Europe" to 42, parsed[1])
    }

    @Test
    fun `a stray Level in another object does not desynchronise the pairs`() {
        // The original bug this replaced: two independent findAll lists zipped by
        // ordinal index, so one extra "Level" shifted every later pair.
        val json =
            """{"___MetaType___":"___Map___","Content":[["1",[{"Region":"Asia","Level":80,"Extra":{"Level":999}},{"Region":"Europe","Level":42}]]]}"""
        val parsed = parseServerLevels(json)
        assertEquals(2, parsed.size)
        assertTrue("no pair may carry the stray nested level", parsed.none { it.second == 999 })
        assertEquals("Asia" to 80, parsed[0])
        assertEquals("Europe" to 42, parsed[1])
    }

    @Test
    fun `a region with no level is skipped without shifting the next pair`() {
        val json = """{"Content":[["1",[{"Region":"Asia"},{"Region":"Europe","Level":42}]]]}"""
        val parsed = parseServerLevels(json)
        assertEquals(listOf("Europe" to 42), parsed)
    }

    @Test
    fun `level before region in the same object still pairs`() {
        val json = """{"Content":[["1",[{"Level":7,"Region":"Asia"}]]]}"""
        assertEquals(listOf("Asia" to 7), parseServerLevels(json))
    }

    @Test
    fun `a flat list of pairs still works for an older payload shape`() {
        // Fallback path: no adjacency, so the documented findAll+zip fallback runs.
        val json = """[{"Region":"Asia","Level":80}]"""
        assertEquals(listOf("Asia" to 80), parseServerLevels(json))
    }

    @Test
    fun `blank input returns empty rather than throwing`() {
        assertTrue(parseServerLevels(null).isEmpty())
        assertTrue(parseServerLevels("").isEmpty())
        assertTrue(parseServerLevels("   ").isEmpty())
    }
}
