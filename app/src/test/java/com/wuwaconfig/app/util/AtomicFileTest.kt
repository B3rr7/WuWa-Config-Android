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
        // No temp siblings left behind
        assertTrue(tmp.root.listFiles().all { it == target })
    }

    @Test
    fun `leaves no temp files after failed rename fallback`() {
        val dir = tmp.newFolder("nested")
        val target = File(dir, "store.json")
        target.writeAtomic("data")
        assertEquals("data", target.readText())
        assertEquals(1, dir.listFiles()?.size)
    }
}
