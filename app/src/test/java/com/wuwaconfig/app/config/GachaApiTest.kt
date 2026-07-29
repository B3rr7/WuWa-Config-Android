package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
