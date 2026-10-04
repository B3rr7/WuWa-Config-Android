package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing for Kuro's official guide.
 *
 * Pure and offline: the payload is the one shape observed in production, plus the
 * deformations a future revision could plausibly introduce. The point of pinning
 * them is that a silent mis-parse here would show the wrong character under a
 * featured banner, which is worse than showing nothing.
 */
class KuroGuideTest {
    private fun row(
        id: String = "1311",
        name: String = "Hsin",
        language: String = "en",
        star: Int = 5,
        status: Int = 2,
        card: String = "https://guide-res.aki-game.net/card.png",
    ) = """
        {
          "roleGbId": "$id",
          "cardPictureUrl": "$card",
          "illustrationPictureUrl": "https://guide-res.aki-game.net/illust.png",
          "star": $star,
          "roleStatus": $status,
          "texts": [ { "language": "$language", "name": "$name" } ]
        }
        """.trimIndent()

    private fun envelope(vararg rows: String) = """{"code":200,"message":"ok","data":[${rows.joinToString(",")}]}"""

    @Test
    fun `parses a character with its status and art`() {
        val parsed = KuroGuide.parseAvatarList(envelope(row()))
        assertEquals(1, parsed.size)
        val c = parsed.single()
        assertEquals("1311", c.roleGbId)
        assertEquals("Hsin", c.name)
        assertEquals(5, c.star)
        assertEquals(OfficialStatus.NEWLY_LAUNCHED, c.status)
        assertEquals("https://guide-res.aki-game.net/card.png", c.cardPictureUrl)
    }

    @Test
    fun `decodes every documented status code`() {
        val parsed =
            KuroGuide.parseAvatarList(
                envelope(
                    row(id = "1", name = "A", status = 1),
                    row(id = "2", name = "B", status = 2),
                    row(id = "3", name = "C", status = 3),
                    row(id = "4", name = "D", status = 4),
                ),
            )
        assertEquals(
            listOf(
                OfficialStatus.ALREADY_ONLINE,
                OfficialStatus.NEWLY_LAUNCHED,
                OfficialStatus.UP,
                OfficialStatus.PROSPECT,
            ),
            parsed.map { it.status },
        )
    }

    @Test
    fun `an unrecognised status is not treated as featured`() {
        // A code the site adds later must not accidentally read as "UP" and put a
        // character on the featured strip.
        val parsed = KuroGuide.parseAvatarList(envelope(row(status = 99)))
        assertEquals(OfficialStatus.UNKNOWN, parsed.single().status)
        assertTrue(KuroGuide.featuredCharacters(parsed).isEmpty())
    }

    @Test
    fun `featured keeps NEW and UP and nothing else`() {
        val parsed =
            KuroGuide.parseAvatarList(
                envelope(
                    row(id = "1", name = "Online", status = 1),
                    row(id = "2", name = "New", status = 2),
                    row(id = "3", name = "Up", status = 3),
                    row(id = "4", name = "Prospect", status = 4),
                ),
            )
        assertEquals(listOf("New", "Up"), KuroGuide.featuredCharacters(parsed).map { it.name })
    }

    @Test
    fun `prefers the requested language for the name`() {
        val json =
            """{"data":[{
              "roleGbId":"9","star":5,"roleStatus":3,
              "cardPictureUrl":"c.png","illustrationPictureUrl":"i.png",
              "texts":[
                {"language":"en","name":"Cardboard"},
                {"language":"ja","name":"CardboardJA"}
              ]}]}"""
        assertEquals("Cardboard", KuroGuide.parseAvatarList(json).single().name)
    }

    @Test
    fun `falls back to any language when the requested one is absent`() {
        // A newly added character can ship with only some languages. A name in the
        // wrong language still identifies it; no name at all does not.
        val json =
            """{"data":[{
              "roleGbId":"9","star":4,"roleStatus":1,
              "cardPictureUrl":"","illustrationPictureUrl":"",
              "texts":[{"language":"zh-Hans","name":"OnlyChinese"}]}]}"""
        val c = KuroGuide.parseAvatarList(json).single()
        assertEquals("OnlyChinese", c.name)
    }

    @Test
    fun `a row with no name at all is skipped`() {
        val parsed = KuroGuide.parseAvatarList(envelope(row(id = "5"), """{"roleGbId":"6","star":5}"""))
        assertEquals(listOf("5"), parsed.map { it.roleGbId })
    }

    @Test
    fun `a row with no id is skipped`() {
        val parsed = KuroGuide.parseAvatarList(envelope("""{"name":"NoId","star":5,"roleStatus":3}"""))
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `a bare array is accepted as well as the envelope`() {
        // The endpoint returns an envelope today; dropping it is a plausible future
        // revision and should not read as "no data".
        val bare = "[${row()}]"
        assertEquals(1, KuroGuide.parseAvatarList(bare).size)
    }

    @Test
    fun `an unrecognised shape throws rather than returning nothing quietly`() {
        val failed =
            runCatching { KuroGuide.parseAvatarList("""{"unexpected":true}""") }.exceptionOrNull()
        assertTrue("must not silently parse to an empty list", failed is IllegalArgumentException)
    }

    @Test
    fun `missing optional art degrades to empty rather than failing`() {
        val json =
            """{"data":[{"roleGbId":"9","star":5,"roleStatus":2,
              "texts":[{"language":"en","name":"NoArt"}]}]}"""
        val c = KuroGuide.parseAvatarList(json).single()
        assertEquals("", c.cardPictureUrl)
        assertEquals("", c.illustrationPictureUrl)
    }
}
