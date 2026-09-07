package com.wuwaconfig.app.config

import android.content.Context
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaRecord
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
        assertTrue(
            "expiresAt must be ~12h from now (±10s tolerance)",
            entry.expiresAt - System.currentTimeMillis() in (12L * 3600 * 1000 - 10_000)..(12L * 3600 * 1000 + 10_000),
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

    // ─────────── TTL behavior ───────────

    @Test
    fun `load returns null when file does not exist`() {
        assertNull(GachaHistoryStore.load(context))
    }

    @Test
    fun `load returns null and deletes expired entry`() {
        val data = sampleData()
        val expiredEntry =
            com.wuwaconfig.app.model.GachaHistoryEntry(
                id = "expired1",
                // already expired
                expiresAt = System.currentTimeMillis() - 1000,
                totalPulls = data.totalPulls,
                fiveStars = data.fiveStars,
                fullDataJson = com.google.gson.Gson().toJson(data),
            )
        File(tempDir, "gacha_history.json").writeText(com.google.gson.Gson().toJson(expiredEntry))
        assertNull(GachaHistoryStore.load(context))
        // File should be deleted
        assertTrue(!File(tempDir, "gacha_history.json").exists())
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
}
