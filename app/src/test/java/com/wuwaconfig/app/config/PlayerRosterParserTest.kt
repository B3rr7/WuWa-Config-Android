package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [PlayerRosterParser] against the real `/user/player/list` and
 * `/user/player/choose` payloads, so the exact `data`/`profile` nesting and the
 * `playerId`-null-for-empty-region behaviour cannot drift silently.
 *
 * The fixtures are the captured responses with `playerId`, `serverId` and the character name
 * replaced: those identify a real in-game account across regions, and the roster is personal
 * data rather than game content.
 */
class PlayerRosterParserTest {
    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/character_build/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("fixture $name missing from test resources")

    @Test
    fun `parses every region row, selectable and not`() {
        val players = PlayerRosterParser.parsePlayers(loadFixture("player_list.json"))
        assertEquals(5, players.size)
        assertEquals(2, players.count { it.isSelectable })
    }

    @Test
    fun `a character region carries the id, name, server and level`() {
        val asia =
            PlayerRosterParser.parsePlayers(loadFixture("player_list.json"))
                .first { it.playerId == 100000001L }
        assertEquals("Asia", asia.serverName)
        assertEquals(80, asia.level)
        assertEquals(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            asia.serverId,
        )
        assertTrue(asia.isSelectable)
        assertNotNull(asia.playerName)
    }

    @Test
    fun `a region with no character is not selectable`() {
        val players = PlayerRosterParser.parsePlayers(loadFixture("player_list.json"))
        val empty = players.first { it.playerId == null }
        assertFalse(empty.isSelectable)
        assertEquals("America", empty.serverName)
        assertNull(empty.level)
    }

    @Test
    fun `the second owned region parses too`() {
        val sea =
            PlayerRosterParser.parsePlayers(loadFixture("player_list.json"))
                .first { it.playerId == 200000002L }
        assertEquals("SEA", sea.serverName)
        assertEquals(34, sea.level)
    }

    @Test
    fun `a missing data array yields an empty roster rather than a throw`() {
        assertTrue(PlayerRosterParser.parsePlayers("""{"code":200,"message":"ok"}""").isEmpty())
        assertTrue(PlayerRosterParser.parsePlayers("not json").isEmpty())
    }

    @Test
    fun `parses the chosen player from the profile nesting`() {
        val chosen = PlayerRosterParser.parseChosen(loadFixture("player_choose_resp.json"))
        assertNotNull(chosen)
        assertEquals(100000001L, chosen!!.playerId)
        assertEquals("Asia", chosen.serverName)
        assertEquals(80, chosen.level)
        assertEquals(
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            chosen.serverId,
        )
        assertEquals("TestChar", chosen.playerName)
    }

    @Test
    fun `an empty profile yields a null chosen player`() {
        assertNull(PlayerRosterParser.parseChosen("""{"code":200,"data":{}}"""))
    }
}
