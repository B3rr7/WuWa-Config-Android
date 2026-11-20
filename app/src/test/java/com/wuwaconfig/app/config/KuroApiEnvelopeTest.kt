package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guide's error envelope.
 *
 * Every body in this file was captured from the live guide server on 2026-11-19. Kuro answers
 * failures with **HTTP 200** and puts the verdict in the body, so a status check alone reads a
 * rejected `x-token` as "no data": the player list comes back empty and `/introduction/info`
 * comes back `data: null`, with no error anywhere. That is the exact shape of the Characters
 * screen never loading a build.
 */
class KuroApiEnvelopeTest {
    @Test
    fun `a rejected token is an error, not an empty answer`() {
        // Sent with an x-token the guide no longer honours.
        assertEquals(500 to "server error", KuroClient.apiErrorCode("""{"message":"server error","code":500}"""))
    }

    @Test
    fun `the sign-in-required code is an error`() {
        assertEquals(1000 to "请登录", KuroClient.apiErrorCode("""{"message":"请登录","code":1000}"""))
    }

    @Test
    fun `a successful answer is not an error`() {
        // `data: null` with code 200 is what /introduction/info answers anonymously.
        assertNull(KuroClient.apiErrorCode("""{"code":200,"message":"ok","data":null}"""))
        assertNull(KuroClient.apiErrorCode("""{"code":200,"message":"ok","data":[]}"""))
    }

    @Test
    fun `a body without a top-level code is not an error`() {
        // The public avatar list answers with `data` alone on some builds.
        assertNull(KuroClient.apiErrorCode("""{"data":[{"roleGbId":"1512"}]}"""))
    }

    @Test
    fun `the passport envelope is not mistaken for a guide error`() {
        // The SDK uses `codes` (plural); inspecting it here would abort every failed login with
        // a guide error instead of the passport's own message.
        assertNull(KuroClient.apiErrorCode("""{"codes":10,"error_description":"Server Unavailable."}"""))
    }

    @Test
    fun `non-json bodies are not errors`() {
        assertNull(KuroClient.apiErrorCode(""))
        assertNull(KuroClient.apiErrorCode("<html>502 Bad Gateway</html>"))
    }

    @Test
    fun `credential rejections are recognised`() {
        for (code in listOf(401, 403, 500, 1000)) {
            assertTrue(
                "code $code should mean the credential was rejected",
                KuroClient.isSessionRejection(KuroClient.KuroApiException(code, "")),
            )
        }
        assertFalse(KuroClient.isSessionRejection(KuroClient.KuroApiException(400, "bad request")))
        assertFalse(KuroClient.isSessionRejection(java.net.SocketTimeoutException("timeout")))
    }

    @Test
    fun `a rejection reads as a session problem, not as missing data`() {
        val message = KuroClient.describeGuideError(KuroClient.KuroApiException(500, "server error"))
        assertTrue(message, message.contains("session", ignoreCase = true))
        assertFalse(message, message.contains("null"))
    }

    @Test
    fun `a non-auth guide error names the code`() {
        val message = KuroClient.describeGuideError(KuroClient.KuroApiException(404, "not found"))
        assertTrue(message, message.contains("404"))
        assertTrue(message, message.contains("not found"))
    }

    @Test
    fun `a plain exception keeps its own message`() {
        assertEquals("boom", KuroClient.describeGuideError(IllegalStateException("boom")))
    }
}
