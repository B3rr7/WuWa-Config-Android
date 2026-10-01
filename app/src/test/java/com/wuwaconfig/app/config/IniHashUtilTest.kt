package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `extractHash` is a hand-rolled section scanner over the device hash file. Its
 * two failure modes are both silent — it returns null and the caller treats the
 * file as unhashed, which then forces a hash refresh on every single sync.
 */
class IniHashUtilTest {
    private val hashFile =
        """
        [Engine.ini]
        Hash=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        ModifyCount=0
        LastModifiedTime=0

        [Scalability.ini]
        Hash=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
        ModifyCount=3
        """.trimIndent()

    @Test
    fun `reads the hash out of a named section`() {
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", extractHash(hashFile, "Engine.ini"))
    }

    @Test
    fun `a later section is found independently of position`() {
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", extractHash(hashFile, "Scalability.ini"))
    }

    @Test
    fun `the section name match is case insensitive`() {
        // The hash file is written by the app but read back across versions and
        // shells, so casing is not something to rely on.
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", extractHash(hashFile, "ENGINE.INI"))
        assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", extractHash(hashFile, "scalability.ini"))
    }

    @Test
    fun `an absent section returns null`() {
        assertNull(extractHash(hashFile, "Hardware.ini"))
    }

    @Test
    fun `an empty file returns null`() {
        assertNull(extractHash("", "Engine.ini"))
    }

    @Test
    fun `a file with no hashes returns null`() {
        assertNull(extractHash("[Engine.ini]\nModifyCount=0", "Engine.ini"))
    }

    @Test
    fun `a section header with no Hash line returns null`() {
        assertNull(extractHash("[Engine.ini]\nModifyCount=0\n\n[Scalability.ini]\nHash=bb", "Engine.ini"))
    }

    @Test
    fun `the scan stops at the next section header`() {
        // Without the HASH_SECTION_REGEX break, the Scalability Hash would be
        // returned for an Engine.ini lookup that has no hash of its own.
        val text =
            """
            [Engine.ini]
            ModifyCount=0
            [Scalability.ini]
            Hash=cccccccccccccccccccccccccccccccc
            """.trimIndent()
        assertNull("must not bleed across the section boundary", extractHash(text, "Engine.ini"))
    }

    @Test
    fun `the first Hash line in a section wins`() {
        val text =
            """
            [Engine.ini]
            Hash=first
            Hash=second
            """.trimIndent()
        assertEquals("first", extractHash(text, "Engine.ini"))
    }

    @Test
    fun `indented lines are tolerated`() {
        val text =
            """
            [Engine.ini]
            Hash=dddddddddddddddddddddddddddddddd
            """.trimIndent()
        assertEquals("dddddddddddddddddddddddddddddddd", extractHash(text, "Engine.ini"))
    }

    @Test
    fun `the hash value is trimmed`() {
        val text = "[Engine.ini]\nHash=   eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee   \n"
        assertEquals("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", extractHash(text, "Engine.ini"))
    }

    @Test
    fun `a header line without a trailing Hash does not match a prefix section`() {
        // "[Engine.ini.bak]" must not satisfy a lookup for "Engine.ini".
        val text = "[Engine.ini.bak]\nHash=ffffffffffffffffffffffffffffffff\n"
        assertNull(extractHash(text, "Engine.ini"))
    }

    @Test
    fun `a section name appearing as a Hash value is not mistaken for a header`() {
        val text = "[Scalability.ini]\nHash=Engine.ini\n"
        assertEquals("Engine.ini", extractHash(text, "Scalability.ini"))
        assertNull(extractHash(text, "Engine.ini"))
    }

    @Test
    fun `an empty Hash value is returned as an empty string, not null`() {
        // Callers distinguish "no stored hash" from "stored an empty hash", so
        // this must not be conflated with a missing section.
        assertEquals("", extractHash("[Engine.ini]\nHash=\n", "Engine.ini"))
    }
}
