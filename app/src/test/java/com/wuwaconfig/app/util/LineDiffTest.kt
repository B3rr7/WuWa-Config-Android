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
}
