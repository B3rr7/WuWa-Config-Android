package com.wuwaconfig.app.config

import com.wuwaconfig.app.ui.theme.elementNameForGbId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The guide never sends an element *name*: both the roster and the build endpoints carry only
 * `element.gbId`. The UI used to read `element.texts[].name`, got an empty string for every
 * character, and fell back to one colour for everyone. These pin the id mapping and the two
 * places it has to be parsed from.
 */
class ElementIdMappingTest {
    @Test
    fun `maps every guide element id`() {
        assertEquals("Glacio", elementNameForGbId("1"))
        assertEquals("Fusion", elementNameForGbId("2"))
        assertEquals("Electro", elementNameForGbId("3"))
        assertEquals("Aero", elementNameForGbId("4"))
        assertEquals("Spectro", elementNameForGbId("5"))
        assertEquals("Havoc", elementNameForGbId("6"))
    }

    @Test
    fun `trims and rejects unknown ids`() {
        assertEquals("Spectro", elementNameForGbId(" 5 "))
        assertNull(elementNameForGbId(""))
        assertNull(elementNameForGbId("7"))
        assertNull(elementNameForGbId("spectro"))
    }

    @Test
    fun `roster rows carry the element id`() {
        val json =
            """
            {"code":200,"data":[{"roleGbId":"14135","star":5,
             "texts":[{"language":"en","name":"Lily"}],
             "element":{"gbId":"5","pictureUrl":"https://example.test/spectro.png"}}]}
            """.trimIndent()
        assertEquals("5", KuroGuide.parseAvatarList(json).single().elementGbId)
    }

    @Test
    fun `build rows carry the element id without an element name`() {
        val json =
            """
            {"code":200,"data":{"role":{"roleGbId":"1607","star":5,
             "texts":[{"language":"en","name":"Cantarella"}],
             "element":{"gbId":"6","pictureUrl":"https://example.test/havoc.png"}}}}
            """.trimIndent()
        val role = CharacterBuildParser.parse(json)!!.role
        assertEquals("6", role.elementGbId)
        // Live data has no element.texts at all — the old parser produced "" here.
        assertEquals("", role.element)
    }
}
