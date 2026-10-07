package com.wuwaconfig.app.config

import com.google.gson.JsonParser
import com.wuwaconfig.app.model.AscensionPhaseCost
import com.wuwaconfig.app.model.CalculatorCharacter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pins the runtime refresh contract in [MaterialData.refresh].
 *
 * The whole point of the feature is that a new character reaches the app without
 * an app update, and that a broken network never makes the data worse. These
 * tests are the two halves of that: a newer payload swaps in, and every failure
 * mode (offline, malformed, empty) leaves the current data untouched.
 *
 * The HTTP layer is replaced by [FakeFetcher]; the cache is a real file in a
 * [TemporaryFolder] so the atomic write and the ETag round-trip are exercised
 * for real rather than mocked.
 */
class MaterialDataRefreshTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val url = "https://example.com/calculator_materials.json"

    /** A fixed clock so the TTL arithmetic is deterministic. */
    private val now = 1_000_000L

    /** The 6h TTL, mirrored from MaterialData so the gate is tested, not the constant. */
    private val ttl = 6L * 60 * 60 * 1000

    /** A minimal but valid payload: one character and one ascension phase. */
    private val validPayload =
        """
        {
          "characterAscensionPhases": [{"phase": 1, "shellCredit": 1000}],
          "characters": {"Hero": {"rarity": 5}},
          "weapons": {},
          "skillMainCosts": [],
          "skillInherentCosts": [],
          "skillStatBonusCosts": [],
          "weaponAscensionByRarity": {}
        }
        """.trimIndent()

    /** A payload whose tables are all empty — parseable, but useless. */
    private val emptyPayload =
        """
        {
          "characterAscensionPhases": [],
          "characters": {},
          "weapons": {},
          "skillMainCosts": [],
          "skillInherentCosts": [],
          "skillStatBonusCosts": [],
          "weaponAscensionByRarity": {}
        }
        """.trimIndent()

    private class FakeFetcher(var result: FetchResult) : RemoteFetcher {
        var calls = 0
        var lastEtag: String? = null

        override fun fetch(
            url: String,
            etag: String?,
        ): FetchResult {
            calls++
            lastEtag = etag
            return result
        }
    }

    private lateinit var fetcher: FakeFetcher
    private lateinit var cacheFile: File

    /** The "current" data a failed refresh must leave untouched. */
    private fun currentData(): MaterialData =
        MaterialData(
            characters = mapOf("Old" to CalculatorCharacter(name = "Old", rarity = 5, boss = null, local = null, common = emptyList(), wsm = emptyList(), dwsm = emptyList(), skillBoss = null)),
            weapons = emptyMap(),
            characterAscensionPhases = listOf(AscensionPhaseCost(phase = 1, shellCredit = 1, local = 0, common1 = 0, common2 = 0, common3 = 0, common4 = 0, boss = 0)),
            skillMainCosts = emptyList(),
            skillInherentCosts = emptyList(),
            skillStatBonusCosts = emptyList(),
            weaponAscensionByRarity = emptyMap(),
        )

    @Before
    fun setUp() {
        cacheFile = File(tmp.root, "cache.json")
        fetcher = FakeFetcher(FetchResult.Failed("default"))
        MaterialData.setFetcherForTest(fetcher)
    }

    @After
    fun tearDown() {
        MaterialData.setFetcherForTest(null)
        MaterialData.installForTest(null)
    }

    private fun writeCache(
        body: String,
        etag: String?,
        timestamp: Long,
    ) {
        val root =
            com.google.gson.JsonObject().apply {
                addProperty("body", body)
                add("etag", etag?.let { com.google.gson.JsonPrimitive(it) } ?: com.google.gson.JsonNull.INSTANCE)
                addProperty("timestamp", timestamp)
            }
        cacheFile.writeText(root.toString())
    }

    private fun readCacheEtag(): String? =
        try {
            val root = JsonParser.parseReader(cacheFile.bufferedReader()).asJsonObject
            root.get("etag")?.takeIf { !it.isJsonNull }?.asString
        } catch (_: Exception) {
            null
        }

    private fun readCacheTimestamp(): Long =
        try {
            val root = JsonParser.parseReader(cacheFile.bufferedReader()).asJsonObject
            root.get("timestamp")?.asLong ?: -1
        } catch (_: Exception) {
            -1
        }

    @Test
    fun `modified payload swaps in and is cached with its etag`() {
        fetcher.result = FetchResult.Modified(validPayload, "etag-1")
        MaterialData.refresh(cacheFile, url, now)

        assertEquals(1, fetcher.calls)
        // First fetch has no cache, so there is no ETag to revalidate with.
        assertNull(fetcher.lastEtag)
        assertTrue(cacheFile.exists())
        assertEquals("etag-1", readCacheEtag())
        // The swap is visible through get().
        assertTrue(MaterialData.get().characters.containsKey("Hero"))
        assertFalse(MaterialData.get().characters.containsKey("Old"))
    }

    @Test
    fun `failed fetch keeps current data and writes no cache`() {
        val before = currentData()
        MaterialData.installForTest(before)
        fetcher.result = FetchResult.Failed("offline")

        MaterialData.refresh(cacheFile, url, now)

        assertEquals(1, fetcher.calls)
        assertFalse("a failed fetch must not write a cache", cacheFile.exists())
        assertEquals(before, MaterialData.get())
    }

    @Test
    fun `corrupt json keeps current data and writes no cache`() {
        val before = currentData()
        MaterialData.installForTest(before)
        fetcher.result = FetchResult.Modified("this is not json", "etag-1")

        MaterialData.refresh(cacheFile, url, now)

        assertEquals(1, fetcher.calls)
        assertFalse("a malformed payload must not be cached", cacheFile.exists())
        assertEquals(before, MaterialData.get())
    }

    @Test
    fun `empty payload is rejected and keeps current data`() {
        val before = currentData()
        MaterialData.installForTest(before)
        fetcher.result = FetchResult.Modified(emptyPayload, "etag-1")

        MaterialData.refresh(cacheFile, url, now)

        assertEquals(1, fetcher.calls)
        assertFalse("an empty payload must not replace good data", cacheFile.exists())
        assertEquals(before, MaterialData.get())
    }

    @Test
    fun `fresh cache skips the network entirely`() {
        val before = currentData()
        MaterialData.installForTest(before)
        // A cache written "now" is fresh: age 0 < TTL.
        writeCache(validPayload, "etag-1", now)
        fetcher.result = FetchResult.Modified(validPayload, "etag-2")

        MaterialData.refresh(cacheFile, url, now)

        assertEquals("a fresh cache must not hit the network", 0, fetcher.calls)
        assertEquals(before, MaterialData.get())
    }

    @Test
    fun `stale cache revalidates with the stored etag`() {
        val before = currentData()
        MaterialData.installForTest(before)
        // One millisecond past the TTL.
        writeCache(validPayload, "old-etag", now - ttl - 1)
        fetcher.result = FetchResult.NotModified("new-etag")

        MaterialData.refresh(cacheFile, url, now)

        assertEquals(1, fetcher.calls)
        assertEquals("old-etag", fetcher.lastEtag)
        // NotModified slides the TTL window forward rather than swapping data.
        assertEquals(now, readCacheTimestamp())
        assertEquals("new-etag", readCacheEtag())
        assertEquals(before, MaterialData.get())
    }

    @Test
    fun `stale cache with modified payload swaps in new data`() {
        val before = currentData()
        MaterialData.installForTest(before)
        writeCache(validPayload, "old-etag", now - ttl - 1)
        fetcher.result = FetchResult.Modified(validPayload, "new-etag")

        MaterialData.refresh(cacheFile, url, now)

        assertEquals(1, fetcher.calls)
        assertEquals("new-etag", readCacheEtag())
        assertTrue(MaterialData.get().characters.containsKey("Hero"))
    }
}
