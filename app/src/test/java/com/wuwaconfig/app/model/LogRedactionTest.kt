package com.wuwaconfig.app.model

import com.wuwaconfig.app.backend.maskHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exported log is the one artifact that deliberately leaves app-private storage
 * and lands in Downloads, where on API 26-29 any installed app can read it (and where
 * the user is likely to share it). These tests pin the redaction applied at that
 * boundary and the LAN-IP masking applied to the ADB connect log lines.
 */
class LogRedactionTest {
    @Test
    fun `record_id in a Convene URL fragment is redacted`() {
        val line =
            "Found Convene URL: https://aki-gm-resources-oversea.aki-game.net/aki/gacha/index.html" +
                "#/record?player_id=123456789&record_id=SECRET123&resources_id=1&gacha_type=1&svr_id=1&lang=en"
        val out = redact(line)
        assertFalse("record_id must not survive export: $out", out.contains("SECRET123"))
        assertTrue("the key should remain so the log stays diagnosable", out.contains("record_id=<redacted>"))
        // The rest of the URL is useful and must not be mangled.
        assertTrue(out.contains("player_id="))
        assertTrue(out.contains("resources_id=1"))
    }

    @Test
    fun `player_id in a Convene URL fragment is redacted`() {
        val out = redact("url ...?player_id=987654321&record_id=abc")
        assertFalse(out.contains("987654321"))
        assertTrue(out.contains("player_id=<redacted>"))
    }

    @Test
    fun `recordId in a JSON body is redacted`() {
        val out = redact("""{"playerId":"111","recordId":"LEAKME","cardPoolId":"5"}""")
        assertFalse("the recordId value must not survive: $out", out.contains("LEAKME"))
        assertFalse("the playerId value must not survive: $out", out.contains("111"))
        assertTrue(out.contains("\"recordId\":\"<redacted>\""))
    }

    @Test
    fun `SetUserId in the game log is redacted`() {
        val out = redact("LogRHI: SetUserId [playerId:1122334455] some other content")
        assertFalse(out.contains("1122334455"))
        assertTrue(out.contains("SetUserId [playerId:<redacted>]"))
    }

    @Test
    fun `ordinary diagnostic lines are untouched`() {
        val line = "ADB push: /data/user/0/app/cache/staging-1234 -> /storage/emulated/0/Android/data/x/Engine.ini"
        assertEquals(line, redact(line))
    }

    @Test
    fun `redaction is case insensitive on the key`() {
        assertFalse(redact("RECORD_ID=LEAK").contains("LEAK"))
        assertFalse(redact("Player_Id=LEAK").contains("LEAK"))
    }

    @Test
    fun `the LAN IP is masked but the diagnostic port is kept`() {
        // Host+port together are what a third party on the same Wi-Fi needs to open a
        // wireless-ADB session to this phone, so the address must not be published.
        // Only the final octet is masked: the /24 is still useful for diagnosing a
        // scan miss ("is this a home LAN?") but no longer identifies the phone.
        assertEquals("192.168.1.x", maskHost("192.168.1.23"))
        assertFalse(maskHost("192.168.1.23").contains("1.23"))
    }

    @Test
    fun `loopback and IPv6 are classified without leaking an address`() {
        assertEquals("127.0.0.1", maskHost("127.0.0.1"))
        assertEquals("[ipv6]", maskHost("fe80::1c2b:3d4e:5f60:7a8b"))
    }

    private fun redact(s: String): String = redactCredentialsForTest(s)
}
