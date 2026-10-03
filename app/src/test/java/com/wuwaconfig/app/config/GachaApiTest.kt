package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.GachaApiResponse
import com.wuwaconfig.app.model.GachaPoolType
import com.wuwaconfig.app.model.GachaRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GachaApiTest {
    @Test
    fun `parseUrl extracts all parameters from standard URL`() {
        val url =
            "https://aki-gm-resources-oversea.aki-game.net/aki/gacha/index.html" +
                "#/record?player_id=1234567890&record_id=abcdef1234567890&resources_id=1&gacha_type=1&svr_id=1&lang=en"
        val params = GachaApi.parseUrl(url)
        assertEquals("1234567890", params?.playerId)
        assertEquals("abcdef1234567890", params?.recordId)
        assertEquals("1", params?.cardPoolId)
        assertEquals("1", params?.cardPoolType)
        assertEquals("1", params?.serverId)
        assertEquals("en", params?.languageCode)
    }

    @Test
    fun `parseUrl extracts parameters from non-oversea URL`() {
        val url =
            "https://aki-gm-resources.aki-game.com/aki/gacha/index.html" +
                "#/record?player_id=9876543210&record_id=fedcba0987654321&resources_id=7&gacha_type=2&svr_id=2&lang=zh"
        val params = GachaApi.parseUrl(url)
        assertEquals("9876543210", params?.playerId)
        assertEquals("fedcba0987654321", params?.recordId)
        assertEquals("7", params?.cardPoolId)
        assertEquals("2", params?.cardPoolType)
        assertEquals("2", params?.serverId)
        assertEquals("zh", params?.languageCode)
    }

    @Test
    fun `parseUrl defaults language to en when missing`() {
        val url =
            "https://aki-gm-resources.aki-game.com/aki/gacha/index.html" +
                "#/record?player_id=123&record_id=abc&resources_id=1&gacha_type=1&svr_id=1"
        val params = GachaApi.parseUrl(url)
        assertEquals("en", params?.languageCode)
    }

    @Test
    fun `parseUrl returns null for missing player_id`() {
        val url =
            "https://aki-gm-resources.aki-game.com/aki/gacha/index.html" +
                "#/record?record_id=abc&resources_id=1&gacha_type=1&svr_id=1"
        assertNull(GachaApi.parseUrl(url))
    }

    @Test
    fun `parseUrl returns null for missing record_id`() {
        val url =
            "https://aki-gm-resources.aki-game.com/aki/gacha/index.html" +
                "#/record?player_id=123&resources_id=1&gacha_type=1&svr_id=1"
        assertNull(GachaApi.parseUrl(url))
    }

    @Test
    fun `parseUrl returns null for missing resources_id`() {
        val url =
            "https://aki-gm-resources.aki-game.com/aki/gacha/index.html" +
                "#/record?player_id=123&record_id=abc&gacha_type=1&svr_id=1"
        assertNull(GachaApi.parseUrl(url))
    }

    @Test
    fun `parseUrl returns null for missing gacha_type`() {
        val url =
            "https://aki-gm-resources.aki-game.com/aki/gacha/index.html" +
                "#/record?player_id=123&record_id=abc&resources_id=1&svr_id=1"
        assertNull(GachaApi.parseUrl(url))
    }

    @Test
    fun `parseUrl returns null for missing svr_id`() {
        val url =
            "https://aki-gm-resources.aki-game.com/aki/gacha/index.html" +
                "#/record?player_id=123&record_id=abc&resources_id=1&gacha_type=1"
        assertNull(GachaApi.parseUrl(url))
    }

    @Test
    fun `parseUrl handles URL with extra parameters`() {
        val url =
            "https://aki-gm-resources-oversea.aki-game.net/aki/gacha/index.html" +
                "#/record?player_id=123&record_id=abc&resources_id=1&gacha_type=1&svr_id=1&lang=en&extra=ignored&foo=bar"
        val params = GachaApi.parseUrl(url)
        assertEquals("123", params?.playerId)
        assertEquals("abc", params?.recordId)
        assertEquals("1", params?.cardPoolId)
        assertEquals("1", params?.cardPoolType)
        assertEquals("1", params?.serverId)
        assertEquals("en", params?.languageCode)
    }

    @Test
    fun `parseUrl handles URL without fragment`() {
        val url = "https://aki-gm-resources.aki-game.com/aki/gacha/index.html"
        assertNull(GachaApi.parseUrl(url))
    }

    // ── fetchAllRecords partial-failure contract (P0-3) ──
    // These tests exercise the HTTP layer through the test seam rather than the
    // network, so they assert the failure mode that matters: a single failed pool
    // must surface as a failure instead of silently caching a partial history.

    private fun okResponse(
        code: Int = 0,
        records: List<GachaRecord> = emptyList(),
    ) = GachaApiResponse(code = code, message = "ok", data = records)

    private fun record(
        type: String,
        name: String,
        count: Int = 1,
        quality: Int = 5,
    ) = GachaRecord(cardPoolType = type, qualityLevel = quality, name = name, count = count, time = "2024-01-01")

    @Test
    fun `fetchAllRecords fails when any pool fails`() {
        val params =
            GachaApi.GachaUrlParams(
                playerId = "1",
                recordId = "abc",
                cardPoolId = "1",
                cardPoolType = "1",
                serverId = "1",
                languageCode = "en",
            )
        var callCount = 0
        GachaApi.setPostRequestForTest { _, _ ->
            callCount++
            if (callCount == 1) return@setPostRequestForTest Result.failure(Exception("boom"))
            Result.success(okResponse(records = listOf(record("1", "Standard"))))
        }
        try {
            val result = GachaApi.fetchAllRecords(params)
            assertFalse("expected failure when one pool fails", result.isSuccess)
            val msg = result.exceptionOrNull()?.message ?: ""
            assertTrue(msg.contains("incomplete"))
            assertTrue(msg.contains("failed pools"))
        } finally {
            GachaApi.setPostRequestForTest(null)
        }
    }

    @Test
    fun `fetchAllRecords fails when all pools fail`() {
        val params =
            GachaApi.GachaUrlParams(
                playerId = "1",
                recordId = "abc",
                cardPoolId = "1",
                cardPoolType = "1",
                serverId = "1",
                languageCode = "en",
            )
        GachaApi.setPostRequestForTest { _, _ -> Result.failure(Exception("net")) }
        try {
            val result = GachaApi.fetchAllRecords(params)
            assertFalse("expected failure when every pool fails", result.isSuccess)
        } finally {
            GachaApi.setPostRequestForTest(null)
        }
    }

    @Test
    fun `fetchAllRecords succeeds when every pool returns empty`() {
        val params =
            GachaApi.GachaUrlParams(
                playerId = "1",
                recordId = "abc",
                cardPoolId = "1",
                cardPoolType = "1",
                serverId = "1",
                languageCode = "en",
            )
        GachaApi.setPostRequestForTest { _, _ -> Result.success(okResponse()) }
        try {
            val result = GachaApi.fetchAllRecords(params)
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().records.isEmpty())
        } finally {
            GachaApi.setPostRequestForTest(null)
        }
    }

    @Test
    fun `fetchAllRecords fails when every pool returns code non-zero`() {
        val params =
            GachaApi.GachaUrlParams(
                playerId = "1",
                recordId = "abc",
                cardPoolId = "1",
                cardPoolType = "1",
                serverId = "1",
                languageCode = "en",
            )
        GachaApi.setPostRequestForTest { _, _ ->
            Result.success(GachaApiResponse(code = 1001, message = "bad record", data = emptyList()))
        }
        try {
            val result = GachaApi.fetchAllRecords(params)
            assertFalse("expected failure when server rejects every pool", result.isSuccess)
            assertTrue(result.exceptionOrNull()?.message!!.contains("bad record"))
        } finally {
            GachaApi.setPostRequestForTest(null)
        }
    }

    // ── the endpoint returns a banner NAME as cardPoolType, not a pool id ──
    //
    // Regression cover for the bug found on a real account against 3.7.0, where the
    // current banner rendered nothing and a long-expired banner rendered instead.
    // Every grouping downstream keys off cardPoolType, so a name there silently
    // emptied every bucket rather than raising anything.

    /** Banner names exactly as the 3.7.0 endpoint returned them, typos included. */
    private val BANNER_NAME_CHARACTER = "Resonators Accurate Modulation"
    private val BANNER_NAME_WEAPON = "Full-Range Modualtion"

    private fun params() =
        GachaApi.GachaUrlParams(
            playerId = "1",
            recordId = "abc",
            cardPoolId = "4e72d71659676be7c39c",
            cardPoolType = "1",
            serverId = "1",
            languageCode = "en",
        )

    @Test
    fun `records are bucketed by the queried pool, not the returned banner name`() {
        // Answer only the CHARACTER_EVENT query (pool "1") with rows labelled by
        // banner name; every other pool reports a valid empty history.
        GachaApi.setPostRequestForTest { _, body ->
            if (body["cardPoolType"] == GachaPoolType.CHARACTER_EVENT.type) {
                Result.success(
                    okResponse(
                        records =
                            listOf(
                                record(BANNER_NAME_CHARACTER, "UpA"),
                                record(BANNER_NAME_CHARACTER, "Filler", quality = 3),
                            ),
                    ),
                )
            } else {
                Result.success(okResponse())
            }
        }
        try {
            val data = GachaApi.fetchAllRecords(params()).getOrThrow()

            assertEquals("every row must survive", 2, data.records.size)
            assertTrue(
                "rows must be relabelled to the queried pool, got ${data.records.map { it.cardPoolType }}",
                data.records.all { it.cardPoolType == GachaPoolType.CHARACTER_EVENT.type },
            )
            assertEquals(
                "the current banner must produce a prediction",
                1,
                data.predictions.size,
            )
            assertEquals(GachaPoolType.CHARACTER_EVENT.type, data.predictions.single().poolType)
        } finally {
            GachaApi.setPostRequestForTest(null)
        }
    }

    @Test
    fun `a weapon banner name also lands in the right prediction`() {
        GachaApi.setPostRequestForTest { _, body ->
            if (body["cardPoolType"] == GachaPoolType.WEAPON_EVENT.type) {
                Result.success(okResponse(records = listOf(record(BANNER_NAME_WEAPON, "Sword"))))
            } else {
                Result.success(okResponse())
            }
        }
        try {
            val data = GachaApi.fetchAllRecords(params()).getOrThrow()
            assertEquals(1, data.predictions.size)
            val pred = data.predictions.single()
            assertEquals(GachaPoolType.WEAPON_EVENT.type, pred.poolType)
            assertEquals("a weapon banner is 100% UP", 1.0, pred.upRate, 0.0001)
        } finally {
            GachaApi.setPostRequestForTest(null)
        }
    }

    @Test
    fun `a numeric cardPoolType in the response is left as it was`() {
        // The STANDARD_3-style pools really do answer with a literal number, and it
        // already agrees with the query. Relabelling must be a no-op there, not a
        // rewrite that could paper over a genuine mismatch.
        GachaApi.setPostRequestForTest { _, body ->
            if (body["cardPoolType"] == GachaPoolType.STANDARD_3.type) {
                Result.success(okResponse(records = listOf(record(GachaPoolType.STANDARD_3.type, "OldHero"))))
            } else {
                Result.success(okResponse())
            }
        }
        try {
            val data = GachaApi.fetchAllRecords(params()).getOrThrow()
            assertEquals(1, data.records.size)
            assertEquals(GachaPoolType.STANDARD_3.type, data.records.single().cardPoolType)
            // Standard pools are not banners, so they yield no prediction.
            assertTrue(data.predictions.isEmpty())
        } finally {
            GachaApi.setPostRequestForTest(null)
        }
    }

    @Test
    fun `standard pool 5 stars are recognised so the fifty fifty logic works`() {
        // The 50/50 verdict depends on knowing which 5-stars are standard. Deriving
        // that set needs the relabelling too, or a standard 5-star is invisible and
        // every banner 5-star looks like a featured one.
        GachaApi.setPostRequestForTest { _, body ->
            when (body["cardPoolType"]) {
                GachaPoolType.STANDARD_3.type ->
                    Result.success(okResponse(records = listOf(record("some banner name", "StandardHero"))))
                GachaPoolType.CHARACTER_EVENT.type ->
                    Result.success(
                        okResponse(
                            records =
                                listOf(
                                    record("another banner name", "StandardHero"),
                                    record("yet another name", "UpA"),
                                ),
                        ),
                    )
                else -> Result.success(okResponse())
            }
        }
        try {
            val data = GachaApi.fetchAllRecords(params()).getOrThrow()
            val charPred = data.predictions.firstOrNull { it.poolType == GachaPoolType.CHARACTER_EVENT.type }
            assertNotNull("a character banner prediction is required", charPred)
            // First 5-star was a standard one, so the banner is on a 50/50.
            assertEquals("50/50", charPred!!.status)
        } finally {
            GachaApi.setPostRequestForTest(null)
        }
    }
}
