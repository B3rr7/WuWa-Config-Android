package com.wuwaconfig.app.model

import android.content.Context
import com.google.gson.Gson
import com.wuwaconfig.app.config.BrainRecommendation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.io.File

/**
 * `readFreshCache` is the shared 24h expiry gate behind both `cached_*.json`
 * stores. It previously existed as two identical copies inside
 * [BattleStatsStore] and [LogAnalysisStore]; these pin the rule once.
 */
class CacheTtlTest {
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir =
            File.createTempFile("cachettl", "test").also {
                it.delete()
                it.mkdirs()
            }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun write(
        name: String,
        timestamp: Long,
    ): File = File(tempDir, name).also { it.writeText("""{"timestamp":$timestamp,"value":"payload"}""") }

    /** Reads the tiny `{"timestamp":N,"value":"payload"}` shape these tests write. */
    private fun read(
        file: File,
        now: Long,
        ttl: Long = TTL,
    ): String? =
        readFreshCache(file, ttlMs = ttl, now = now) { text ->
            val ts = TIMESTAMP.find(text)?.groupValues?.get(1)?.toLongOrNull()
            if (ts == null) null else TimedCache("payload", ts)
        }

    private companion object {
        const val TTL = 24L * 60 * 60 * 1000
        val TIMESTAMP = Regex(""""timestamp"\s*:\s*(\d+)""")
    }

    @Test
    fun `a missing file reads as null`() {
        assertNull(read(File(tempDir, "absent.json"), now = 0))
    }

    @Test
    fun `a fresh payload is returned`() {
        val file = write("fresh.json", timestamp = 1_000)
        assertEquals("payload", read(file, now = 1_000))
        assertTrue("a fresh file must survive the read", file.exists())
    }

    @Test
    fun `the boundary millisecond is still fresh`() {
        // The production check is `age > ttl`, so exactly ttl is NOT expired.
        val file = write("boundary.json", timestamp = 0)
        assertEquals("payload", read(file, now = TTL))
        assertTrue(file.exists())
    }

    @Test
    fun `one millisecond past the ttl is expired`() {
        val file = write("stale.json", timestamp = 0)
        assertNull(read(file, now = TTL + 1))
    }

    @Test
    fun `an expired payload is deleted from disk`() {
        // This is what makes it a cache rather than a store: the space is
        // reclaimed on read, so a stale file can never be revived later.
        val file = write("deleted.json", timestamp = 0)
        read(file, now = TTL + 1)
        assertTrue("the expired file must be removed", !file.exists())
    }

    @Test
    fun `a corrupt payload reads as null and is left alone`() {
        // Corrupt-but-recent is not an expiry case. Deleting here would throw
        // away a payload that writeAtomic may still be in the middle of.
        val file = File(tempDir, "corrupt.json").also { it.writeText("{not json") }
        assertNull(read(file, now = 0))
        assertTrue("corrupt data must not be deleted by a read", file.exists())
    }

    @Test
    fun `a null decode reads as null`() {
        val file = File(tempDir, "null.json").also { it.writeText("null") }
        assertNull(read(file, now = 0))
    }

    @Test
    fun `expiry uses the embedded timestamp, not the file mtime`() {
        // The file is written via a temp sibling + rename; a restore-from-backup
        // or adb pull can rewrite mtime without the payload changing. Gating on
        // mtime would silently expire a cache the user never invalidated.
        val file = write("mtime.json", timestamp = 0)
        file.setLastModified(System.currentTimeMillis())
        assertNull("the embedded timestamp is what counts", read(file, now = TTL + 1))
    }

    @Test
    fun `a very old timestamp is expired regardless of mtime`() {
        val file = write("ancient.json", timestamp = 0)
        file.setLastModified(Long.MAX_VALUE / 2)
        assertNull(read(file, now = TTL + 1))
    }

    @Test
    fun `a future timestamp is not expired`() {
        // Clock skew must not expire a cache that was just written.
        val file = write("future.json", timestamp = TTL * 10)
        assertEquals("payload", read(file, now = 0))
    }

    @Test
    fun `expiry is measured at exactly ttl plus one`() {
        val file = write("precise.json", timestamp = 5_000)
        assertNotNull(read(file, now = 5_000 + TTL))
        assertNull(read(file, now = 5_000 + TTL + 1))
    }
}

/** The concrete store, pinned to the same TTL contract. */
class CacheStoresTest {
    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir =
            File.createTempFile("stores", "test").also {
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

    private fun cacheFile(name: String) = File(tempDir, name)

    private fun stats(battles: Int = 3) = BattleStats(battles = battles, playerId = "uid-1")

    @Test
    fun `battle stats round-trip through the cache`() {
        BattleStatsStore.save(context, stats(7))
        val loaded = BattleStatsStore.load(context)
        assertNotNull(loaded)
        assertEquals(7, loaded!!.battles)
        assertEquals("uid-1", loaded.playerId)
    }

    @Test
    fun `battle stats load is null before anything is saved`() {
        assertNull(BattleStatsStore.load(context))
    }

    @Test
    fun `battle stats save overwrites the previous entry`() {
        BattleStatsStore.save(context, stats(1))
        BattleStatsStore.save(context, stats(2))
        assertEquals(2, BattleStatsStore.load(context)!!.battles)
    }

    @Test
    fun `clear removes the battle stats cache`() {
        BattleStatsStore.save(context, stats(5))
        BattleStatsStore.clear(context)
        assertNull(BattleStatsStore.load(context))
    }

    @Test
    fun `clear on a missing cache is a no-op`() {
        BattleStatsStore.clear(context)
        assertNull(BattleStatsStore.load(context))
    }

    @Test
    fun `an expired battle stats cache is dropped`() {
        BattleStatsStore.save(context, stats(9))
        val file = cacheFile("cached_battle_stats.json")
        assertTrue(file.exists())
        // Rewrite the payload with an ancient embedded timestamp; the TTL gate
        // reads that, not the mtime.
        File(tempDir, "cached_battle_stats.json").writeText(
            Gson().toJson(
                mapOf(
                    "stats" to mapOf("battles" to 9),
                    "timestamp" to 0L,
                ),
            ),
        )
        assertNull("a 1970 timestamp must be long expired", BattleStatsStore.load(context))
    }

    @Test
    fun `a corrupt battle stats cache loads as null`() {
        BattleStatsStore.save(context, stats(1))
        cacheFile("cached_battle_stats.json").writeText("{ not json")
        assertNull(BattleStatsStore.load(context))
    }

    @Test
    fun `log analysis round-trips through the cache`() {
        val rec = BrainRecommendation(preset = "balanced", score = 80, signals = listOf("s"), warnings = emptyList())
        LogAnalysisStore.save(context, LogInfo(fpsActual = 60f), rec)
        val loaded = LogAnalysisStore.load(context)
        assertNotNull(loaded)
        assertEquals(60f, loaded!!.logInfo.fpsActual!!, 0.001f)
        assertEquals("balanced", loaded.brainRecommendation?.preset)
        assertEquals(80, loaded.brainRecommendation?.score)
    }

    @Test
    fun `log analysis tolerates a null recommendation`() {
        LogAnalysisStore.save(context, LogInfo(fpsActual = 45f), null)
        val loaded = LogAnalysisStore.load(context)
        assertNotNull(loaded)
        assertNull(loaded!!.brainRecommendation)
    }

    @Test
    fun `log analysis load is null before anything is saved`() {
        assertNull(LogAnalysisStore.load(context))
    }

    @Test
    fun `an expired log analysis cache is dropped`() {
        LogAnalysisStore.save(context, LogInfo(fpsActual = 60f), null)
        cacheFile("cached_log_analysis.json").writeText(
            Gson().toJson(
                mapOf(
                    "logInfo" to mapOf("fpsActual" to 60),
                    "timestamp" to 0L,
                ),
            ),
        )
        assertNull(LogAnalysisStore.load(context))
    }

    @Test
    fun `a corrupt log analysis cache loads as null`() {
        LogAnalysisStore.save(context, LogInfo(), null)
        cacheFile("cached_log_analysis.json").writeText("]]] not json")
        assertNull(LogAnalysisStore.load(context))
    }

    @Test
    fun `the two stores do not collide on their cache files`() {
        BattleStatsStore.save(context, stats(4))
        LogAnalysisStore.save(context, LogInfo(fpsActual = 30f), null)
        assertEquals(4, BattleStatsStore.load(context)!!.battles)
        assertEquals(30f, LogAnalysisStore.load(context)!!.logInfo.fpsActual!!, 0.001f)
    }
}
