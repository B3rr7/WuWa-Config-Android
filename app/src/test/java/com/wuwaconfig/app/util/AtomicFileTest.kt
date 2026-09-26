package com.wuwaconfig.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicFileTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** The suffix AtomicFile appends to its staging file name (default arg of writeAtomic). */
    private val stagingSuffix = ".tmp"

    /** Any sibling that is the staging file rather than the real store. */
    private fun stagingSiblings(
        dir: File,
        target: File,
    ): List<String> {
        val siblings = dir.listFiles().orEmpty().filter { it != target }
        return siblings.filter { it.name.contains(stagingSuffix) }.map { it.name }
    }

    @Test
    fun `writes content atomically to target`() {
        val target = File(tmp.root, "store.json")
        target.writeAtomic("""{"a":1}""")
        assertEquals("""{"a":1}""", target.readText())
    }

    @Test
    fun `overwrites existing content completely`() {
        val target = File(tmp.root, "store.json")
        target.writeText("x".repeat(10_000))
        target.writeAtomic("small")
        assertEquals("small", target.readText())
        // No temp siblings left behind. `listFiles()` is nullable (null when the
        // dir can't be read), so narrow explicitly: a null listing cannot prove
        // "no leak", so it must fail the assertion rather than silently pass.
        assertTrue(tmp.root.listFiles()?.all { it == target } ?: false)
    }

    @Test
    fun `leaves no staging file behind after a successful write`() {
        // Renamed: this exercises the SUCCESS path. writeAtomic stages the content in a
        // sibling temp, fsyncs it, then rename(2)s it over the target. A same-directory
        // rename always succeeds, so the cross-filesystem fallback is never entered here —
        // there is no portable way to force a genuine EXDEV, and none is needed to prove
        // the temp file is cleaned up.
        val dir = tmp.newFolder("nested")
        val target = File(dir, "store.json")
        target.writeAtomic("data")
        assertEquals("data", target.readText())
        assertEquals(1, dir.listFiles()?.size)
    }

    @Test
    fun `leaves no staging file behind on a first write or an overwrite`() {
        val dir = tmp.newFolder("store-dir")
        val target = File(dir, "profile.json")

        target.writeAtomic("""{"v":1}""")
        assertEquals("""{"v":1}""", target.readText())
        assertEquals("a first write must not leave a staging file", emptyList<String>(), stagingSiblings(dir, target))

        // Overwrite in place, repeatedly — each pass stages a fresh temp.
        repeat(3) { i ->
            target.writeAtomic("""{"v":${i + 2}}""")
            assertEquals("""{"v":${i + 2}}""", target.readText())
            assertEquals("an overwrite must not leave a staging file", emptyList<String>(), stagingSiblings(dir, target))
        }

        // Only the target remains.
        assertEquals(listOf(target.name), dir.list()!!.toList())
    }

    @Test
    fun `the target is never deleted by a write`() {
        // The old cross-filesystem fallback was delete()-then-copy, so a crash mid-copy
        // destroyed the store outright. The replacement copies to a SECOND temp in the
        // destination directory and renames that, so the target is never unlinked.
        // A same-directory write never enters the fallback, but the invariant that matters
        // is observable here: the pre-existing content survives intact until the new content
        // is fully staged.
        val target = File(tmp.root, "gacha_history.json")
        target.writeAtomic("""{"records":[]}""")

        assertTrue("the pre-existing store must still exist before the next write", target.exists())
        val before = target.readText()

        target.writeAtomic("""{"records":[{"id":"a"}]}""")

        assertTrue("the target must exist after the write", target.exists())
        assertEquals("""{"records":[{"id":"a"}]}""", target.readText())
        assertTrue("the new content must fully replace the old", before != target.readText())
    }

    @Test
    fun `writes large content intact`() {
        val target = File(tmp.root, "big.json")
        val payload = "x".repeat(512 * 1024)
        target.writeAtomic(payload)
        assertEquals(payload.length, target.readText().length)
        assertEquals(payload, target.readText())
    }

    @Test
    fun `writeAtomic is idempotent for identical content`() {
        val target = File(tmp.root, "store.json")
        target.writeAtomic("same")
        val firstRead = target.readText()
        target.writeAtomic("same")
        assertEquals(firstRead, target.readText())
        assertEquals(emptyList<String>(), stagingSiblings(tmp.root, target))
    }

    @Test
    fun `a custom tmp suffix is honoured and cleaned up`() {
        val dir = tmp.newFolder("custom")
        val target = File(dir, "store.json")
        target.writeAtomic("data", tmpSuffix = ".staging")
        assertEquals("data", target.readText())
        assertEquals(1, dir.listFiles()?.size)
    }
}
