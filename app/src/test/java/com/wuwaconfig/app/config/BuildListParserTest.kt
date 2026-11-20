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

    @Test
    fun `an unsorted server response is reordered by popularity`() {
        // Captured live on 2026-11-19 for Camellya: the server returned ids ascending and put
        // the 8,689-like current build last. Trusting that order made `first()` an archived id,
        // and `/introduction/info` answers those with `data: null` — the screen then reported
        // "no build details" for a character that has one.
        val json =
            """
            {"code":200,"message":"ok","data":[
              {"id":10008,"likeCount":155,"collectCount":0,"role":{"texts":[{"language":"en","name":"Old"}]}},
              {"id":12089,"likeCount":759,"collectCount":0,"role":{"texts":[{"language":"en","name":"Mid"}]}},
              {"id":14050,"likeCount":8689,"collectCount":3483,"role":{"texts":[{"language":"en","name":"Current"}]}}
            ]}
            """.trimIndent()
        val builds = BuildListParser.parse(json, "en")
        assertEquals(listOf(14050, 12089, 10008), builds.map { it.id })
        assertEquals("Current", builds.first().name)
    }

    @Test
    fun `equal popularity falls back to the newest id`() {
        val json =
            """
            {"data":[
              {"id":10001,"likeCount":0},
              {"id":14002,"likeCount":0},
              {"id":12003,"likeCount":7}
            ]}
            """.trimIndent()
        assertEquals(listOf(12003, 14002, 10001), BuildListParser.parse(json, "en").map { it.id })
    }

    @Test
    fun `a build without a like count sorts below liked builds`() {
        val json =
            """
            {"data":[
              {"id":14000},
              {"id":10000,"likeCount":3}
            ]}
            """.trimIndent()
        assertEquals(listOf(10000, 14000), BuildListParser.parse(json, "en").map { it.id })
    }
}
