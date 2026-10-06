package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [KuroLoginParser] against the three captured login-hop responses. The login flow is a
 * plain form-encoded OAuth exchange (no signature), so these fixtures are the source of truth
 * for the exact keys each hop returns; a Kuro API revision that renames a key would show up
 * here before it reaches the [KuroClient].
 *
 * The fixtures are the captured responses with every credential and identifier replaced. The
 * real capture carried a live OAuth `code`, `access_token` and guide bearer `x-token` — the
 * last of which is exactly what the app persists to `kuro_session.bin` — alongside the
 * account's real email and display name. Only the key shape and types matter here, so the
 * values are fabricated; the `x-token` stays valid base64 of a `{cUid, channelId,
 * innerToken}` object because that structure is what the decoder documents.
 */
class KuroLoginParserTest {
    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/character_build/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("fixture $name missing from test resources")

    @Test
    fun `the email+password hop returns an oauth code and the identity`() {
        val result = KuroLoginParser.parseEmailPwd(loadFixture("emailpwd_resp.json"))
        assertNotNull(result)
        assertEquals("100000001", result!!.cuid)
        assertEquals("U100000001A", result.username)
        assertEquals("test@example.com", result.email)
        assertTrue(result.code.isNotBlank())
    }

    @Test
    fun `the token exchange returns an access token and its lifetime`() {
        val result = KuroLoginParser.parseGetToken(loadFixture("gettoken_resp.json"))
        assertNotNull(result)
        assertEquals(259200, result!!.expiresInSec)
        assertTrue(result.accessToken.startsWith("11111111"))
    }

    @Test
    fun `the guide login hop returns the bearer x-token`() {
        val token = KuroLoginParser.parseGuideToken(loadFixture("login_sdk_resp.json"))
        assertNotNull(token)
        // The JWT decodes to a {cUid, channelId, innerToken} object; the header is base64.
        assertTrue(token!!.startsWith("eyJjVWlkIjoiMTAwMDAwMDAx"))
    }

    @Test
    fun `a response without a data object parses to null`() {
        assertNull(KuroLoginParser.parseEmailPwd("""{"code":500,"message":"bad"}"""))
        assertNull(KuroLoginParser.parseGetToken("""{"data":{}}"""))
        assertNull(KuroLoginParser.parseGuideToken("not json"))
    }
}
