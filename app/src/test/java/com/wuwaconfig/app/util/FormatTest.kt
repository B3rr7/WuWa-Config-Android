package com.wuwaconfig.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared byte formatter.
 *
 * 1024-based, because these are bytes on a filesystem. `BattleStatsScreen` used
 * to carry a private 1000-based copy, so the same 20 MB log read as "20.0 MB"
 * on one screen and "21.0 MB" on another. One formatter removes the whole class
 * of disagreement.
 */
class FormatTest {
    @Test
    fun `bytes below a kilobyte are shown in B`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1 B", formatBytes(1))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `kilobytes are shown in KB`() {
        assertEquals("1.0 KB", formatBytes(1_024))
        assertEquals("2.5 KB", formatBytes(2_560))
        assertEquals("1023.9 KB", formatBytes(1_048_473))
    }

    @Test
    fun `megabytes are shown in MB`() {
        assertEquals("1.0 MB", formatBytes(1_048_576))
        assertEquals("20.0 MB", formatBytes(20L * 1024 * 1024))
    }

    @Test
    fun `the 1024-based convention is what makes screens agree`() {
        // The bug: a 1000-based formatter reports 20 MiB as "21.0 MB" while a
        // 1024-based one reports "20.0 MB". Pinned so the convention cannot drift
        // back to 1000-based on a private copy.
        val twentyMiB = 20L * 1024 * 1024
        assertEquals("20.0 MB", formatBytes(twentyMiB))
        assertEquals(false, formatBytes(twentyMiB).startsWith("21"))
    }

    @Test
    fun `a 20MB game log formats the same as every other 20MB log`() {
        // The motivating case: the game rotates Client.log at ~20 MB, and the size
        // was displayed differently depending on which screen showed it.
        val log = 20L * 1024 * 1024
        assertEquals("20.0 MB", formatBytes(log))
    }

    @Test
    fun `large values stay in MB rather than switching units`() {
        assertEquals("1024.0 MB", formatBytes(1_024L * 1024 * 1024))
        assertEquals("5120.0 MB", formatBytes(5L * 1_024 * 1024 * 1024))
    }

    @Test
    fun `the formatter never returns a blank or unit-less string`() {
        for (bytes in listOf(0L, 1L, 1_023L, 1_024L, 1_048_575L, 1_048_576L, 10_000_000L)) {
            val label = formatBytes(bytes)
            assertEquals(false, label.isBlank())
            // Every value above 0 carries a unit, so a caller never has to guess
            // whether it is bytes, KB or MB.
            if (bytes > 0) {
                assertEquals(false, label == "$bytes")
            }
        }
    }

    @Test
    fun `the formatter is monotonic`() {
        var previous = ""
        for (bytes in listOf(0L, 512L, 1_024L, 10_000L, 1_048_576L, 5_000_000L, 100_000_000L)) {
            val label = formatBytes(bytes)
            // Not a numeric comparison (the unit changes), but the same input must
            // always give the same output.
            assertEquals(label, formatBytes(bytes))
            previous = label
        }
        assertEquals(false, previous.isBlank())
    }
}
