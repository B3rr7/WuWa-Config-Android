package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [BuildListParser] against the real `/introduction/list` payload, confirming the top
 * entry's id (the recommendation context this app keys the character's own equipment off) and
 * that the localized name resolves in the preferred language.
 */
class BuildListParserTest {
    private fun loadFixture(name: String): String =
        javaClass.getResourceAsStream("/character_build/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("fixture $name missing from test resources")

    @Test
    fun `the list is ordered with the top curated build first`() {
        val builds = BuildListParser.parse(loadFixture("build_list.json"), "en")
        assertTrue(builds.isNotEmpty())
        assertEquals(14007, builds.first().id)
        assertEquals(10257, builds.first().likeCount)
        assertEquals(2881, builds.first().collectCount)
    }

    @Test
    fun `the build name resolves in the preferred language`() {
        val top = BuildListParser.parse(loadFixture("build_list.json"), "en").first()
        assertEquals("Cantarella", top.name)
    }

    @Test
    fun `every build has a usable id`() {
        val builds = BuildListParser.parse(loadFixture("build_list.json"), "en")
        assertTrue(builds.all { it.id > 0 })
        assertNotNull(builds.map { it.id }.toSet().distinct())
    }

    @Test
    fun `a missing data array yields an empty list`() {
        assertTrue(BuildListParser.parse("""{"code":200,"message":"ok"}""").isEmpty())
    }
}
