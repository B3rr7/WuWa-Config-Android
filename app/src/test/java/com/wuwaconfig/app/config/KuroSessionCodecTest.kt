package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trips [KuroSession] through [KuroSessionCodec] so the on-disk field set cannot drift
 * from the in-memory model. The encryption layer ([KuroSessionStore]) is Android-only and is
 * exercised on device; this pins the JSON shape that layer protects.
 *
 * The values are fabricated rather than taken from a captured session: a real `xToken` is a
 * live bearer credential, and the `cuid`/`playerId`/`serverId` identify a specific account.
 */
class KuroSessionCodecTest {
    private val chosen =
        ChosenPlayer(
            playerId = 100000001L,
            playerName = "TestChar",
            serverId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            serverName = "Asia",
            level = 80,
        )

    @Test
    fun `a full session round-trips losslessly`() {
        val session =
            KuroSession(
                xToken = "eyJjVWlkIjoiMTAwMDAwMDAx",
                cuid = "100000001",
                username = "U100000001A",
                chosenPlayer = chosen,
                email = "test@example.com",
                expiresAtEpochSec = 1_791_206_823L,
            )
        val decoded = KuroSessionCodec.decode(KuroSessionCodec.encode(session))
        assertEquals(session, decoded)
    }

    @Test
    fun `a session with no chosen player round-trips with a null player`() {
        val session =
            KuroSession(
                xToken = "tok",
                cuid = "1",
                username = "U1",
                chosenPlayer = null,
                email = null,
                expiresAtEpochSec = null,
            )
        val decoded = KuroSessionCodec.decode(KuroSessionCodec.encode(session))
        assertEquals(session, decoded)
        assertNull(decoded!!.chosenPlayer)
        assertNull(decoded.email)
        assertNull(decoded.expiresAtEpochSec)
    }

    @Test
    fun `missing optional fields fall back to their defaults`() {
        // cuid/username present, the rest absent: they must read as null, not as 0/false.
        val json = """{"xToken":"t","cuid":"9","username":"U9"}"""
        val decoded = KuroSessionCodec.decode(json)
        assertEquals("t", decoded!!.xToken)
        assertEquals("9", decoded.cuid)
        assertNull(decoded.chosenPlayer)
        assertNull(decoded.expiresAtEpochSec)
    }

    @Test
    fun `a payload with no xToken decodes to null`() {
        assertNull(KuroSessionCodec.decode("""{"cuid":"9","username":"U9"}"""))
        assertNull(KuroSessionCodec.decode("not json"))
    }
}
