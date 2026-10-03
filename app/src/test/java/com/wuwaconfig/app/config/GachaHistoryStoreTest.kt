package com.wuwaconfig.app.config

import android.content.Context
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaRecord
import com.wuwaconfig.app.model.PityPrediction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.io.File

class GachaHistoryStoreTest {
    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir =
            File.createTempFile("gacha", "test").also {
                it.delete()
                it.mkdirs()
            }
        context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.filesDir).thenReturn(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun sampleData(
        totalPulls: Int = 100,
        fiveStars: Int = 5,
    ) = GachaData(
        records =
            (0 until totalPulls).map {
                GachaRecord(
                    cardPoolType = "1",
                    qualityLevel = if (it % 20 == 0) 5 else 3,
                    name = "Item$it",
                    count = 1,
                    time = "2024-01-${(it % 28) + 1} 10:00:00",
                )
            },
        poolsWithData = listOf("1"),
        totalPulls = totalPulls,
        fiveStars = fiveStars,
        fourStars = totalPulls - fiveStars,
        avgPity5 = 20.0,
        avgPity4 = 10.0,
        predictions = emptyList(),
    )

    // ─────────── save / load round-trip ───────────

    @Test
    fun `save returns entry with correct fields`() {
        val data = sampleData(totalPulls = 200, fiveStars = 8)
        val entry = GachaHistoryStore.save(context, data)
        assertEquals(200, entry.totalPulls)
        assertEquals(8, entry.fiveStars)
        assertTrue("expiresAt must be in the future", entry.expiresAt > System.currentTimeMillis())
        // expiresAt is the RETENTION deadline, not the freshness window. This used
        // to assert ~12h, which is now the freshness window and is carried by
        // isStale/ageHours instead — see the staleness tests below.
        val retentionHours = gameProfile().gachaHistoryRetentionHours.toLong()
        assertTrue(
            "expiresAt must be ~${retentionHours}h from now (±10s tolerance)",
            entry.expiresAt - System.currentTimeMillis() in (retentionHours * 3600 * 1000 - 10_000)..(retentionHours * 3600 * 1000 + 10_000),
        )
        assertTrue("id should be 8 chars", entry.id.length == 8)
        assertTrue("fullDataJson should contain records", entry.fullDataJson.contains("Item0"))
    }

    @Test
    fun `load returns saved entry`() {
        val data = sampleData()
        GachaHistoryStore.save(context, data)
        val loaded = GachaHistoryStore.load(context)
        assertNotNull(loaded)
        assertEquals(data.totalPulls, loaded!!.totalPulls)
        assertEquals(data.fiveStars, loaded.fiveStars)
        assertTrue(loaded.fullDataJson.contains("Item0"))
    }

    // ─────────── retention vs freshness ───────────
    //
    // The distinction these pin is the whole point of the change: the file is
    // history and must survive being stale. The old suite asserted the opposite
    // (`load returns null and deletes expired entry`) and was passing for the
    // wrong reason — it described a cache, not a history.

    @Test
    fun `load returns null when file does not exist`() {
        assertNull(GachaHistoryStore.load(context))
    }

    @Test
    fun `load returns null and deletes an entry past RETENTION`() {
        val data = sampleData()
        val expiredEntry =
            com.wuwaconfig.app.model.GachaHistoryEntry(
                id = "expired1",
                fetchedAt = System.currentTimeMillis() - 1000,
                // already past the retention window
                expiresAt = System.currentTimeMillis() - 1000,
                totalPulls = data.totalPulls,
                fiveStars = data.fiveStars,
                fullDataJson = com.google.gson.Gson().toJson(data),
            )
        File(tempDir, "gacha_history.json").writeText(com.google.gson.Gson().toJson(expiredEntry))
        assertNull(GachaHistoryStore.load(context))
        assertTrue(!File(tempDir, "gacha_history.json").exists())
    }

    @Test
    fun `a stale entry is still loaded, not deleted`() {
        // The regression this guards: being past the 12h freshness window used to
        // delete the file, so lifetime statistics were impossible to compute.
        val data = sampleData()
        val staleEntry =
            com.wuwaconfig.app.model.GachaHistoryEntry(
                id = "stale1",
                fetchedAt = System.currentTimeMillis() - 48 * 3600 * 1000L,
                expiresAt = System.currentTimeMillis() + 300 * 24 * 3600 * 1000L,
                totalPulls = data.totalPulls,
                fiveStars = data.fiveStars,
                fullDataJson = com.google.gson.Gson().toJson(data),
            )
        File(tempDir, "gacha_history.json").writeText(com.google.gson.Gson().toJson(staleEntry))

        val loaded = GachaHistoryStore.load(context)
        assertNotNull("a stale entry is still valid history", loaded)
        assertEquals("stale1", loaded!!.id)
        assertTrue("the file must survive being stale", File(tempDir, "gacha_history.json").exists())
        assertTrue("and it must report itself stale", GachaHistoryStore.isStale(loaded))
    }

    @Test
    fun `an entry inside the freshness window is not stale`() {
        val entry = GachaHistoryStore.save(context, sampleData())
        assertTrue("a just-saved entry is fresh", !GachaHistoryStore.isStale(entry))
        assertEquals(0L, GachaHistoryStore.ageHours(entry))
    }

    @Test
    fun `a legacy entry with no fetchedAt is not reported stale`() {
        // Gson leaves the new field at 0 for JSON written by an older build.
        // Treating that as "infinitely old" would tell the player to re-fetch
        // history that is still perfectly good.
        val legacy =
            com.wuwaconfig.app.model.GachaHistoryEntry(
                id = "legacy",
                expiresAt = System.currentTimeMillis() + 3600_000L,
                totalPulls = 10,
                fiveStars = 1,
                fullDataJson = "{}",
            )
        assertTrue(!GachaHistoryStore.isStale(legacy))
        assertNull("age is unknowable, not zero", GachaHistoryStore.ageHours(legacy))
    }

    // ─────────── delete ───────────

    @Test
    fun `delete removes the file`() {
        GachaHistoryStore.save(context, sampleData())
        assertNotNull(GachaHistoryStore.load(context))
        GachaHistoryStore.delete(context)
        assertNull(GachaHistoryStore.load(context))
    }

    @Test
    fun `delete is a no-op when file does not exist`() {
        GachaHistoryStore.delete(context) // should not throw
    }

    // ─────────── overwrite ───────────

    @Test
    fun `save overwrites previous entry`() {
        GachaHistoryStore.save(context, sampleData(totalPulls = 100, fiveStars = 5))
        GachaHistoryStore.save(context, sampleData(totalPulls = 200, fiveStars = 10))
        val loaded = GachaHistoryStore.load(context)!!
        assertEquals(200, loaded.totalPulls)
        assertEquals(10, loaded.fiveStars)
    }

    // ─────────── corruption recovery ───────────

    @Test
    fun `load recovers from corrupted JSON by deleting the file`() {
        File(tempDir, "gacha_history.json").writeText("not valid json {{{")
        assertNull(GachaHistoryStore.load(context))
        assertTrue("corrupted file should be deleted", !File(tempDir, "gacha_history.json").exists())
    }

    // ─────────── legacy cache backward compatibility ───────────

    @Test
    fun `gson restores legacy cache missing new fields using kotlin defaults`() {
        // Simulates JSON written by an older app version: only the pre-existing
        // required fields, no later-added optional fields (currentFeaturedName,
        // ssrIntervals, totalCost, firstPullDate, minPity5, etc.).
        val legacyJson =
            """
            {
              "predictions": [
                {
                  "poolType": "1",
                  "poolLabel": "Character Event",
                  "status": "soft",
                  "lastFiveStarName": "Verina",
                  "lastFiveStarTime": "2024-03-01 10:00:00",
                  "pullsSinceLastFive": 20,
                  "estimatedNextFive": 46
                }
              ],
              "totalPulls": 160
            }
            """.trimIndent()
        val data: GachaData = GachaHistoryStore.gson.fromJson(legacyJson, GachaData::class.java)
        assertNotNull(data.predictions)
        assertEquals(1, data.predictions.size)
        val pred: PityPrediction = data.predictions[0]
        // Defaults applied instead of null → no NPE risk in UI.
        assertEquals(80, pred.hardPity)
        assertEquals("", pred.currentFeaturedName)
        assertEquals(false, pred.currentFeaturedKnown)
        assertNotNull(pred.ssrIntervals)
        assertEquals(0, pred.ssrIntervals.size)
        assertEquals(0L, pred.totalCost)
        assertEquals("", pred.firstPullDate)
        assertEquals(0, pred.minPity5)
        assertEquals(0, pred.maxPity5)
    }

    @Test
    fun `restore handles cache where predictions list is null`() {
        // Old schema had no predictions array at all.
        val legacyJson = """{"totalPulls": 90, "fiveStars": 3}"""
        val data: GachaData = GachaHistoryStore.gson.fromJson(legacyJson, GachaData::class.java)
        assertNotNull(data)
        assertNotNull(data.predictions)
        assertEquals(0, data.predictions.size)
        assertEquals(90, data.totalPulls)
    }
}
