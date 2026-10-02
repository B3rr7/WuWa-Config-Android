package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The profile-read progress ladder.
 *
 * `readProfile` emitted exactly two values — 5, then 10 — and nothing after, so
 * the Profile screen's "Reading game data from device… 10%" froze for the entire
 * read. The remaining work is a merged log pull (current log plus the head of up
 * to eight backups, an 8 MB budget) and a parse, which is the slow part.
 *
 * `readProfile` itself cannot be unit tested: it needs a Context and an
 * AccessBackend. What is testable is the *ladder* — the sequence of values the
 * function is now documented to emit — so the shape is pinned here and the
 * emission sites are kept in step with it by the comment at each one.
 *
 * The property that matters is monotonicity with a real floor: the bar must
 * advance through the read rather than sitting at 10%, and it must reach 100.
 */
class ProfileProgressTest {
    /**
     * The ladder `readProfile` emits, in order. Kept in one place so the test and
     * the four emission sites cannot drift apart silently.
     */
    private val ladder = listOf(5, 20, 70, 90, 100)

    @Test
    fun `the ladder is strictly increasing`() {
        for (i in ladder.indices.drop(1)) {
            assertTrue(
                "progress must advance: ${ladder[i - 1]} -> ${ladder[i]}",
                ladder[i] > ladder[i - 1],
            )
        }
    }

    @Test
    fun `the ladder starts near zero and ends at 100`() {
        assertEquals(5, ladder.first())
        assertEquals(100, ladder.last())
    }

    @Test
    fun `every rung is a valid percentage`() {
        for (value in ladder) {
            assertTrue("$value is out of range", value in 0..100)
        }
    }

    @Test
    fun `the ladder has no duplicate rungs`() {
        // A repeated value would render as a stall, which is the bug being fixed.
        assertEquals(ladder.size, ladder.toSet().size)
    }

    @Test
    fun `the long pole is given the largest jump`() {
        // The merged-log read is by far the slowest part, so the step into it must
        // be the biggest single advance. If the ladder were rebalanced so the
        // pre-read rungs took most of the range, the bar would again appear stuck
        // during the read itself.
        val jumps = ladder.zipWithNext { a, b -> b - a }
        assertEquals(
            "the merged-log read should own the largest jump",
            jumps.maxOrNull(),
            jumps[1],
        )
    }

    @Test
    fun `the final rung is emitted only on the success path`() {
        // 100 is emitted immediately before Result.success, so a failed read must
        // not leave the bar at 100. The caller resets to 0 in a finally, which is
        // what makes this safe.
        assertEquals(100, ladder.last())
    }

    @Test
    fun `the ladder matches the emission sites in readProfile`() {
        // Guards the thing that actually broke: a site removed or renumbered
        // without updating the ladder. If this fails, readProfile and this test
        // disagree about the shape of the progress.
        val source = readProfileSource()
        val emitted = Regex("""onProgress\((\d+)\)""").findAll(source).map { it.groupValues[1].toInt() }.toList()
        assertEquals(
            "readProfile emits $emitted but the ladder says $ladder",
            ladder,
            emitted,
        )
    }

    /**
     * The body of `readProfile`, read from the production source.
     *
     * Extracted by locating the function and taking everything up to the next
     * top-level member, rather than by string surgery on neighbouring function
     * names: `readBattleStats` is not `private`, so anchoring on
     * "private suspend fun readBattleStats" silently matched nothing and the
     * window ran to the end of the file.
     */
    private fun readProfileSource(): String {
        val source = profileExtractorSource()
        val start = source.indexOf("suspend fun readProfile(")
        assertTrue("readProfile not found in ProfileExtractor.kt", start >= 0)
        val rest = source.substring(start)
        // The next top-level member after readProfile. readProfile's own body
        // declares no nested `suspend fun`, so this is readBattleStats.
        val next = Regex("\n    (?:private )?suspend fun").find(rest)
        return if (next != null) rest.substring(0, next.range.first) else rest
    }

    private fun profileExtractorSource(): String {
        val relative = "app/src/main/java/com/wuwaconfig/app/config/ProfileExtractor.kt"
        // user.dir is always set by the JVM, but getProperty is nullable in the
        // Kotlin view, so coalesce rather than suppress.
        val cwd = System.getProperty("user.dir") ?: "."
        if (File(cwd, relative).isFile) return File(cwd, relative).readText()
        // Fall back to walking up from the working directory, the same ambiguity
        // RealCvarCorpus works around.
        var dir: File? = File(cwd).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        throw AssertionError("ProfileExtractor.kt not found from $cwd")
    }
}
