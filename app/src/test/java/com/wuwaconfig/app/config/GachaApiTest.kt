package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.GachaApiResponse
import com.wuwaconfig.app.model.GachaRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    ) = GachaRecord(cardPoolType = type, qualityLevel = 5, name = name, count = count, time = "2024-01-01")

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
}
