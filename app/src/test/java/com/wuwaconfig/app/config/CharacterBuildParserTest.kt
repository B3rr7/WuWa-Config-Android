package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing for a Kuro guide character build ([CharacterBuildParser]).
 *
 * The primary case reads a real, unmodified capture of the `/introduction/info` payload
 * (a 5★ Hsin build). It is deliberately the single-language `zh-Hans` capture, so a
 * parse requested in `en` must fall back to `zh-Hans` rather than come out blank —
 * the exact way a player's own authenticated build is likely to arrive.
 */
class CharacterBuildParserTest {
    private fun loadFixture(name: String = "sample_10228.json"): String {
        val stream =
            javaClass.getResourceAsStream("/character_build/$name")
                ?: error("fixture $name missing from test resources")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `parses the real captured build into the model`() {
        val build = CharacterBuildParser.parse(loadFixture())
        assertNotNull(build)
        val b = build!!
        assertEquals(10228, b.id)
        assertEquals("1311", b.role.roleGbId)
        assertEquals(5, b.role.star)
        // Requested "en"; the capture only has zh-Hans, so the fallback must resolve it.
        assertEquals("心", b.role.name)
        assertTrue(b.role.elementImageUrl.isNotEmpty())
        assertEquals("巡游天国FM", b.source)
        assertEquals(35, b.likeCount)
        // A curated build has no in-game level.
        assertNull(b.level)
        assertNull(b.equippedWeapon)
    }

    @Test
    fun `resonance chain is all six links in sequence order`() {
        val b = CharacterBuildParser.parse(loadFixture())!!
        assertEquals(6, b.resonance.size)
        assertEquals((1..6).toList(), b.resonance.map { it.sequence })
        assertTrue(b.resonance.all { it.name.isNotEmpty() })
    }

    @Test
    fun `skills and keystone resolve from the capture`() {
        val b = CharacterBuildParser.parse(loadFixture())!!
        assertEquals(5, b.skills.size)
        assertTrue(b.skills.all { it.name.isNotEmpty() })
        assertNotNull(b.keynoteSkill)
        assertTrue(b.keynoteSkill!!.name.isNotEmpty())
    }

    @Test
    fun `main echo carries its set effects`() {
        val b = CharacterBuildParser.parse(loadFixture())!!
        val echo = b.mainEcho
        assertNotNull(echo)
        assertEquals("共鸣回响·天演溯心", echo!!.name)
        assertEquals(2, echo.setEffects.size)
        assertTrue(echo.setEffects.all { it.name.isNotEmpty() })
    }

    @Test
    fun `weapon options parse in order`() {
        val b = CharacterBuildParser.parse(loadFixture())!!
        assertEquals(listOf("玉阙玄华", "幽冥的忘忧章", "掣傀之手", "清音"), b.weaponOptions.map { it.name })
        assertEquals("百巧琢锋", b.weaponOptions.first().effectName)
    }

    @Test
    fun `recommended stats carry their target values`() {
        val b = CharacterBuildParser.parse(loadFixture())!!
        val stats = b.recommendedStats.associateBy { it.name }
        assertEquals("70.0%", stats.getValue("暴击").target)
        assertEquals("260.0%", stats.getValue("暴击伤害").target)
        assertEquals("120.0%", stats.getValue("共鸣效率").target)
    }

    @Test
    fun `an authenticated build surfaces the user's actually-equipped gear`() {
        // The `.current` fields are filled only when the request carried the player's
        // x-token. A curated capture leaves them null; this one does not, and the
        // equipped weapon/echo differ from the build's recommendations.
        val b = CharacterBuildParser.parse(loadFixture("sample_14007_authed.json"))
        assertNotNull(b)
        val build = b!!
        assertEquals("Cantarella", build.role.name)
        assertEquals(5, build.role.star)
        assertEquals("Rime-Draped Sprouts", build.equippedWeapon?.name)
        assertEquals("Lorelei", build.equippedEcho?.name)
        // Recommendations must not be confused with what is actually equipped.
        assertEquals("Nightmare: Crownless", build.mainEcho?.name)
        assertNotNull(build.equippedWeapon)
        // Level is not part of this payload; it arrives via /user/player/list.
        assertNull(build.level)
    }

    @Test
    fun `skill priority carries the recommended order with the player's own levels`() {
        val b = CharacterBuildParser.parse(loadFixture("sample_14007_authed.json"))!!
        assertEquals(5, b.skillPriority.size)
        val first = b.skillPriority.first()
        assertEquals("Illusion Collapse", first.name)
        assertEquals(6, first.recommendLevel)
        assertEquals(8, first.currentLevel)
        assertTrue(first.pictureUrl.isNotEmpty())
        assertTrue(b.skillPriority.all { (it.currentLevel ?: 0) > 0 })
    }

    @Test
    fun `a curated build has zeroed current skill levels, never the player's upgrades`() {
        val b = CharacterBuildParser.parse(loadFixture())!!
        assertEquals(5, b.skillPriority.size)
        assertTrue(b.skillPriority.all { (it.currentLevel ?: -1) == 0 })
        assertTrue(b.skillPriority.all { (it.recommendLevel ?: 0) > 0 })
    }

    @Test
    fun `stats carry the player's current value, icon and finished flag`() {
        val b = CharacterBuildParser.parse(loadFixture("sample_14007_authed.json"))!!
        val stats = b.recommendedStats.associateBy { it.name }
        val crit = stats.getValue("Crit. DMG")
        assertEquals("291.6%", crit.current)
        assertEquals("240.0%", crit.target)
        assertTrue(crit.pictureUrl.isNotEmpty())
        assertTrue(crit.isFinished)
        assertEquals("48.8%", stats.getValue("Crit. Rate").current)
    }

    @Test
    fun `a null data envelope yields null rather than a broken build`() {
        assertNull(CharacterBuildParser.parse("""{"code":200,"message":"ok","data":null}"""))
        assertNull(CharacterBuildParser.parse("""{"code":500,"message":"server error"}"""))
        assertNull(CharacterBuildParser.parse("null"))
    }

    @Test
    fun `an explicit level on an authenticated build is kept`() {
        // The player's own build carries a level; a curated one does not. Confirm the
        // field round-trips when present and is absent otherwise.
        val json =
            """{"code":200,"data":{"level":90,"id":7,"role":
            {"roleGbId":"1607","star":5,"cardPictureUrl":"c.png","illustrationPictureUrl":"i.png",
             "texts":[{"language":"en","name":"Cantarella"}]}}}"""
        val b = CharacterBuildParser.parse(json)!!
        assertEquals(90, b.level)
        assertEquals("Cantarella", b.role.name)
        assertTrue(b.resonance.isEmpty())
        assertTrue(b.skills.isEmpty())
    }

    @Test
    fun `a bare build object without an envelope is rejected`() {
        // Only the `{code,data}` envelope is accepted at the top level; a stray object is
        // not a build and must not masquerade as one.
        assertNull(CharacterBuildParser.parse("""{"roleGbId":"1311","star":5}"""))
    }
}
