package com.wuwaconfig.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineDiffTest {
    @Test
    fun `compute produces correct diff for simple input`() {
        val result = LineDiff.compute("a\nb\nc", "a\nx\nc")
        assertEquals(1, result.summary.added)
        assertEquals(1, result.summary.removed)
        assertEquals(2, result.summary.unchanged)
        assertEquals(0, result.sanitizedLines)
        // Lines: context, removed, added, context
        assertEquals(4, result.lines.size)
    }

    @Test
    fun `compute handles empty strings`() {
        val result = LineDiff.compute("", "")
        assertEquals(0, result.summary.added)
        assertEquals(0, result.summary.removed)
        // "".split('\n') yields a single empty-string line on each side,
        // which matches as one unchanged context line.
        assertEquals(1, result.summary.unchanged)
        assertEquals(0, result.sanitizedLines)
    }

    @Test
    fun `compute sanitizes lines containing null characters`() {
        val oldText = "line1\n${'\u0000'}corrupted\nline3"
        val newText = "line1\nline3"
        val result = LineDiff.compute(oldText, newText)

        // Every line in the result must be free of null characters.
        result.lines.forEach { line ->
            assertFalse("Line text must not contain null char: ${line.text}", line.text.contains('\u0000'))
        }
        // The corrupted line should have been replaced with a clean label.
        val corruptedLine = result.lines.find { it.text.contains("corrupted") || it.text.contains("[corrupted") }
        assertTrue(corruptedLine != null)
        assertTrue(corruptedLine!!.sanitized)
        // sanitizedLines should count the corrupted old line
        assertEquals(1, result.sanitizedLines)
    }

    @Test
    fun `compute sanitizes null characters in both old and new text`() {
        val oldText = "clean\n${'\u0000'}bad"
        val newText = "clean\n${'\u0000'}also_bad"
        val result = LineDiff.compute(oldText, newText)

        result.lines.forEach { line ->
            assertFalse(line.text.contains('\u0000'))
        }
        assertTrue(result.sanitizedLines >= 2)
    }

    @Test
    fun `compute sanitizes lines with non-printable control characters`() {
        val oldText = "line1\nline2${'\u0001'}${'\u0002'}\nline3"
        val newText = "line1\nline2\nline3"
        val result = LineDiff.compute(oldText, newText)

        result.lines.forEach { line ->
            assertFalse(line.text.contains('\u0001'))
            assertFalse(line.text.contains('\u0002'))
        }
        assertTrue(result.sanitizedLines >= 1)
    }

    @Test
    fun `compute preserves tab characters`() {
        val text = "col1\tcol2\tcol3"
        val result = LineDiff.compute(text, text)
        assertEquals(1, result.lines.size)
        assertTrue(result.lines[0].text.contains('\t'))
        assertEquals(0, result.sanitizedLines)
    }

    @Test
    fun `compute does not sanitize clean text`() {
        val oldText = "setting=value1\ngraphics=high\nfps=60"
        val newText = "setting=value2\ngraphics=high\nfps=60"
        val result = LineDiff.compute(oldText, newText)
        assertEquals(0, result.sanitizedLines)
        result.lines.forEach { line ->
            assertFalse(line.sanitized)
        }
    }

    @Test
    fun `compute with null chars in identical lines marks sanitized on context`() {
        val corrupt = "Engine${'\u0000'}\n"
        val result = LineDiff.compute(corrupt, corrupt)
        // When both old and new have the same corrupted line, the context line
        // is marked sanitized.
        val contextLine = result.lines.first { it.kind == DiffLine.Kind.CONTEXT }
        assertTrue(contextLine.sanitized)
        assertEquals(2, result.sanitizedLines) // one from old, one from new
    }

    @Test
    fun `sanitized lines use clean fallback label`() {
        val nullText = "hello\u0000world"
        val result = LineDiff.compute(nullText, "")
        val removedLine = result.lines.first { it.kind == DiffLine.Kind.REMOVED }
        assertTrue(removedLine.text.startsWith("[corrupted:"))
        assertFalse(removedLine.text.contains('\u0000'))
    }

    private fun bigText(
        count: Int,
        tag: String,
    ): String = (0 until count).joinToString("\n") { "$tag-line-$it" }

    // ── bounded LCS ──
    // lcsTable allocated an (n+1) x (m+1) IntArray. Two 5,000-line files is ~100MB and two
    // 20,000-line files is ~1.6GB — an OOM crash — and ReviewTuneScreen calls compute() on
    // DEVICE-SUPPLIED content, so the line count is not bounded by anything the app controls.
    // The fix strips the common prefix/suffix, hard-caps the table at 4M cells, and emits a
    // bounded fallback flagged with DiffResult.truncated.

    @Test
    fun `large wholly-differing input is bounded and flagged as truncated`() {
        // 6,000 x 6,000 = 36M cells, far past the 4M cap. The lines differ from the very
        // first one, so the prefix/suffix strip cannot shrink the problem: this is the case
        // the cap exists for. Pre-fix this allocated a 6,001 x 6,001 IntArray and OOM'd.
        val oldText = bigText(6_000, "old")
        val newText = bigText(6_000, "new")

        val result = LineDiff.compute(oldText, newText)

        assertTrue("an over-cap diff must be flagged truncated", result.truncated)
        // The fallback samples 200 lines per side plus an ellipsis marker per side = 402.
        assertTrue("fallback must not emit the whole 12,000-line diff, got ${result.lines.size}", result.lines.size <= 404)
        // The summary still reports the full residual, so the UI can say how much changed.
        assertEquals(6_000, result.summary.removed)
        assertEquals(6_000, result.summary.added)
        assertEquals(0, result.summary.unchanged)
    }

    @Test
    fun `very large wholly-differing input completes without OOM`() {
        // The 2 x 20,000-line case from the report: ~1.6GB for the table pre-fix.
        val oldText = bigText(20_000, "old")
        val newText = bigText(20_000, "new")

        val result = LineDiff.compute(oldText, newText)

        assertTrue(result.truncated)
        assertEquals(20_000, result.summary.removed)
        assertEquals(20_000, result.summary.added)
    }

    @Test
    fun `large input with a long common prefix is diffed exactly and cheaply`() {
        // The common real-world case: a one-line edit deep inside a 5,000-line file. The
        // prefix/suffix strip reduces the LCS to a 1x1 table, so this must NOT truncate and
        // must still locate the change.
        val shared = (0 until 5_000).joinToString("\n") { "shared-line-$it" }
        val oldText = shared + "\nOLD-TAIL"
        val newText = shared + "\nNEW-TAIL"

        val startedAt = System.nanoTime()
        val result = LineDiff.compute(oldText, newText)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertFalse("a small residual after prefix/suffix stripping must not truncate", result.truncated)
        assertEquals(1, result.summary.removed)
        assertEquals(1, result.summary.added)
        assertEquals(5_000, result.summary.unchanged)

        // The change must be surfaced at the end, with the right absolute line numbers.
        val removed = result.lines.single { it.kind == DiffLine.Kind.REMOVED }
        val added = result.lines.single { it.kind == DiffLine.Kind.ADDED }
        assertEquals("OLD-TAIL", removed.text)
        assertEquals("NEW-TAIL", added.text)
        assertEquals(5_001, removed.oldLineNumber!!)
        assertEquals(5_001, added.newLineNumber!!)

        // A 1-cell table is nothing; an O(n*m) path here would blow past this budget.
        assertTrue("prefix-stripped diff took ${elapsedMs}ms, expected well under 1s", elapsedMs < 1_000)
    }

    @Test
    fun `large input with a common prefix AND a common suffix is diffed exactly`() {
        val head = (0 until 3_000).joinToString("\n") { "head-line-$it" }
        val tail = (0 until 3_000).joinToString("\n") { "tail-line-$it" }
        val oldText = head + "\nOLD-MIDDLE\n" + tail
        val newText = head + "\nNEW-MIDDLE\n" + tail

        val result = LineDiff.compute(oldText, newText)

        assertFalse(result.truncated)
        assertEquals(1, result.summary.removed)
        assertEquals(1, result.summary.added)
        assertEquals(6_000, result.summary.unchanged)
    }

    @Test
    fun `small input is never flagged truncated and keeps its original counts`() {
        val result = LineDiff.compute("a\nb\nc", "a\nx\nc")

        assertFalse(result.truncated)
        assertEquals(DiffSummary(added = 1, removed = 1, unchanged = 2), result.summary)
        assertEquals(4, result.lines.size)
        assertEquals(0, result.sanitizedLines)
    }

    @Test
    fun `empty input is not flagged truncated`() {
        val result = LineDiff.compute("", "")
        assertFalse(result.truncated)
        assertEquals(DiffSummary(added = 0, removed = 0, unchanged = 1), result.summary)
    }

    // ── corrupt lines must not be reported as unchanged ──
    // The LCS used to run on the SANITISED lines. Two DIFFERENT corrupt lines of the same
    // length both collapsed to the identical label "[corrupted: N chars]", matched as a
    // common line, and were emitted as `unchanged` — hiding exactly the damage the
    // sanitisation exists to surface. The LCS now runs on the RAW lines and only the
    // emitted display text is sanitised.

    @Test
    fun `two different corrupt lines of equal length are reported as a change`() {
        // Both middle lines are 5 characters long and both contain a NUL, so pre-fix both
        // sanitised to "[corrupted: 5 chars]" and diffed as a single unchanged line.
        val oldText = "keep-1\nA\u0000XX\nkeep-3"
        val newText = "keep-1\nB\u0000YY\nkeep-3"

        val result = LineDiff.compute(oldText, newText)

        assertEquals("the differing corrupt line must show as removed", 1, result.summary.removed)
        assertEquals("the differing corrupt line must show as added", 1, result.summary.added)
        assertEquals("only the two surrounding lines are unchanged", 2, result.summary.unchanged)
        assertTrue("the diff must not be silently empty", result.lines.any { it.kind != DiffLine.Kind.CONTEXT })
    }

    @Test
    fun `an identical corrupt line on both sides is still reported as unchanged`() {
        // The counterpart: a corrupt line that is byte-identical on both sides has NOT
        // changed and must still match, or every corrupt file would diff as fully rewritten.
        val text = "keep-1\nA\u0000XX\nkeep-3"

        val result = LineDiff.compute(text, text)

        assertEquals(0, result.summary.removed)
        assertEquals(0, result.summary.added)
        assertEquals(3, result.summary.unchanged)
        assertEquals("both copies of the corrupt line still count as sanitised", 2, result.sanitizedLines)
    }

    @Test
    fun `differing corrupt lines of different lengths are reported as a change`() {
        val oldText = "keep-1\nA\u0000XX\nkeep-3"
        val newText = "keep-1\nB\u0000ZZZZZ\nkeep-3"

        val result = LineDiff.compute(oldText, newText)

        assertEquals(1, result.summary.removed)
        assertEquals(1, result.summary.added)
        assertEquals(2, result.summary.unchanged)
    }

    @Test
    fun `corrupt line change is still surfaced safely for the text layout engine`() {
        val oldText = "keep-1\nA\u0000XX\nkeep-3"
        val newText = "keep-1\nB\u0000YY\nkeep-3"

        val result = LineDiff.compute(oldText, newText)

        // The change is reported, but the emitted display text must stay layout-safe.
        for (line in result.lines) {
            assertFalse("emitted text must not contain a NUL: ${line.text}", line.text.contains('\u0000'))
        }
        assertTrue(result.lines.any { it.sanitized })
    }

    // ── CRLF normalisation ──
    // split('\n') left a trailing \r on every line, so a CRLF file read off the device diffed
    // as "every single line changed" against the LF-only generated file.

    @Test
    fun `CRLF and LF text with no other difference reports no changes`() {
        val oldText = "a\r\nb\r\nc\r\n"
        val newText = "a\nb\nc\n"

        val result = LineDiff.compute(oldText, newText)

        assertEquals(0, result.summary.added)
        assertEquals(0, result.summary.removed)
        // 3 content lines + the trailing "" that a final newline always produces.
        assertEquals(4, result.summary.unchanged)
        assertTrue("every line must be CONTEXT", result.lines.all { it.kind == DiffLine.Kind.CONTEXT })
    }

    @Test
    fun `CRLF text does not emit a stray carriage return in the display text`() {
        val result = LineDiff.compute("a\r\nb", "a\nb")
        assertEquals(0, result.summary.added)
        assertEquals(0, result.summary.removed)
        for (line in result.lines) {
            assertFalse("display text must not contain CR: '${line.text}'", line.text.contains('\r'))
        }
    }

    @Test
    fun `a real change in a CRLF file is still detected`() {
        val oldText = "a\r\nold-tail\r\nz\r\n"
        val newText = "a\r\nnew-tail\r\nz\r\n"

        val result = LineDiff.compute(oldText, newText)

        assertEquals(1, result.summary.added)
        assertEquals(1, result.summary.removed)
        // "a", "z" and the trailing "" that the terminating CRLF produces are all
        // unchanged context. The \r must not make any of them look modified.
        assertEquals(3, result.summary.unchanged)
        assertEquals("new-tail", result.lines.single { it.kind == DiffLine.Kind.ADDED }.text)
    }

    @Test
    fun `CRLF normalisation composes with the truncated cap`() {
        val oldText = bigText(3_000, "old").replace("\n", "\r\n")
        val newText = bigText(3_000, "new")

        val result = LineDiff.compute(oldText, newText)

        assertTrue(result.truncated)
        assertEquals(3_000, result.summary.removed)
        assertEquals(3_000, result.summary.added)
    }
}
