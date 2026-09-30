package com.wuwaconfig.app.util

data class DiffLine(
    val kind: Kind,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String,
    /** True when the original line contained null characters or non-printable
     * control characters and was replaced with a clean fallback label inside
     * [LineDiff.compute]. The UI can use this to show a warning indicator. */
    val sanitized: Boolean = false,
) {
    enum class Kind {
        CONTEXT,
        ADDED,
        REMOVED,
    }
}

data class DiffSummary(
    val added: Int,
    val removed: Int,
    val unchanged: Int,
)

data class DiffResult(
    val lines: List<DiffLine>,
    val summary: DiffSummary,
    /** Number of lines that were sanitized (null/control-char replacement)
     * during [LineDiff.compute]. Exposed as a verification metric so callers
     * can log or surface corruption warnings. */
    val sanitizedLines: Int = 0,
    /** True when the differing middle section was too large to run an LCS over,
     * so [lines] holds a bounded sample and [summary] was derived without an LCS
     * (one removed block + one added block). Callers should show a
     * "diff too large — showing summary" hint instead of a full diff. */
    val truncated: Boolean = false,
)

object LineDiff {
    /** Char code for DEL (0x7F) — a non-printable control character that can
     * confuse text layout engines alongside null bytes. */
    private const val DEL = 0x7F

    /**
     * Returns `true` if the line contains any null character (`U+0000`) or other
     * non-printable control character (excluding `\t`, which is safe). These
     * characters can originate from a partially-written or corrupted device INI
     * file (e.g. the game writing `Client.log` concurrently, or a truncated
     * push) and cause an `IndexOutOfBoundsException` inside Compose's text
     * layout engine when passed to `Text` / `BasicTextField`.
     */
    private fun isCorrupted(line: String): Boolean =
        line.indexOf('\u0000') >= 0 ||
            line.any { c -> (c.code < 0x20 && c != '\t') || c.code == DEL }

    /**
     * Strict input verification: if the line is corrupted (contains null chars
     * or control chars), fall back to a clean text label instead of the raw
     * content. This prevents out-of-bounds crashes in the text layout engine.
     *
     * @return the original line, or a safe `[corrupted: N chars]` label.
     */
    private fun sanitizeLine(line: String): Pair<String, Boolean> {
        return if (isCorrupted(line)) {
            Pair("[corrupted: ${line.length} chars]", true)
        } else {
            Pair(line, false)
        }
    }

    /**
     * Hard cap on the LCS table. The table is `4 * (n + 1) * (m + 1)` bytes, and
     * both line counts come from DEVICE-SUPPLIED file contents, so two 20k-line
     * files would otherwise allocate ~1.6GB and OOM. Above this cell count the
     * LCS is skipped entirely and a bounded summary is emitted instead.
     */
    private const val MAX_LCS_CELLS = 4_000_000L

    /** Upper bound on how many lines the non-LCS fallback emits per side. */
    private const val FALLBACK_MAX_LINES = 200

    /**
     * Splits on `\n` and normalises CRLF / CR line endings. Device INI files are
     * frequently written with CRLF, and a generated file never is — without this
     * a whole-file rewrite would diff as "every single line changed".
     */
    private fun splitLines(text: String): List<String> {
        val raw = text.split('\n')
        return raw.map { if (it.isNotEmpty() && it[it.length - 1] == '\r') it.dropLast(1) else it }
    }

    fun compute(
        oldText: String,
        newText: String,
    ): DiffResult {
        // The LCS runs on the RAW lines: sanitising first would collapse two
        // different corrupt lines of equal length into the same placeholder and
        // report them as unchanged, hiding exactly the corruption we want to
        // surface. Sanitisation happens only on the `text` of each emitted line.
        val oldLines = splitLines(oldText)
        val newLines = splitLines(newText)

        val sanitizedCount =
            oldLines.count { isCorrupted(it) } + newLines.count { isCorrupted(it) }

        // (a) strip the common prefix / suffix so the table only covers the
        // genuinely different middle. A one-line edit in a 5000-line file drops
        // the cell count from 25M to ~1.
        val shorter = minOf(oldLines.size, newLines.size)
        var prefix = 0
        while (prefix < shorter && oldLines[prefix] == newLines[prefix]) prefix++
        var suffix = 0
        while (suffix < shorter - prefix &&
            oldLines[oldLines.size - 1 - suffix] == newLines[newLines.size - 1 - suffix]
        ) {
            suffix++
        }
        val oldMidEnd = oldLines.size - suffix
        val newMidEnd = newLines.size - suffix
        val oldMid = oldLines.subList(prefix, oldMidEnd)
        val newMid = newLines.subList(prefix, newMidEnd)

        val out = ArrayList<DiffLine>(oldLines.size + newLines.size)
        var added = 0
        var removed = 0
        var unchanged = 0

        for (i in 0 until prefix) {
            val (text, san) = sanitizeLine(oldLines[i])
            out.add(DiffLine(DiffLine.Kind.CONTEXT, i + 1, i + 1, text, sanitized = san))
            unchanged++
        }

        // (c) hard cap: never allocate the table for a device-controlled diff.
        val cells = oldMid.size.toLong() * newMid.size.toLong()
        if (cells > MAX_LCS_CELLS) {
            // Cheap non-LCS fallback: the residual is reported as one removed
            // block plus one added block, capped so the list stays bounded.
            removed += oldMid.size
            added += newMid.size
            for (k in 0 until minOf(oldMid.size, FALLBACK_MAX_LINES)) {
                val (text, san) = sanitizeLine(oldMid[k])
                out.add(DiffLine(DiffLine.Kind.REMOVED, prefix + k + 1, null, text, sanitized = san))
            }
            if (oldMid.size > FALLBACK_MAX_LINES) {
                out.add(
                    DiffLine(
                        DiffLine.Kind.CONTEXT,
                        null,
                        null,
                        "… ${oldMid.size - FALLBACK_MAX_LINES} more removed line(s) not shown",
                    ),
                )
            }
            for (k in 0 until minOf(newMid.size, FALLBACK_MAX_LINES)) {
                val (text, san) = sanitizeLine(newMid[k])
                out.add(DiffLine(DiffLine.Kind.ADDED, null, prefix + k + 1, text, sanitized = san))
            }
            if (newMid.size > FALLBACK_MAX_LINES) {
                out.add(
                    DiffLine(
                        DiffLine.Kind.CONTEXT,
                        null,
                        null,
                        "… ${newMid.size - FALLBACK_MAX_LINES} more added line(s) not shown",
                    ),
                )
            }
        } else if (oldMid.isNotEmpty() || newMid.isNotEmpty()) {
            val mid = lcsDiff(oldMid, newMid, prefix)
            out.addAll(mid.lines)
            added += mid.added
            removed += mid.removed
            unchanged += mid.unchanged
        }

        for (k in 0 until suffix) {
            val (text, san) = sanitizeLine(oldLines[oldMidEnd + k])
            out.add(DiffLine(DiffLine.Kind.CONTEXT, oldMidEnd + k + 1, newMidEnd + k + 1, text, sanitized = san))
            unchanged++
        }

        return DiffResult(
            lines = out,
            summary =
                DiffSummary(
                    added = added,
                    removed = removed,
                    unchanged = unchanged,
                ),
            sanitizedLines = sanitizedCount,
            truncated = cells > MAX_LCS_CELLS,
        )
    }

    private class MidDiff(
        val lines: List<DiffLine>,
        val added: Int,
        val removed: Int,
        val unchanged: Int,
    )

    /**
     * Runs the LCS over the residual middle only and returns the diff lines in
     * source order, with line numbers offset by [lineOffset] so they stay
     * absolute within the original documents. [oldMid] / [newMid] are RAW lines.
     */
    private fun lcsDiff(
        oldMid: List<String>,
        newMid: List<String>,
        lineOffset: Int,
    ): MidDiff {
        val a = oldMid.toTypedArray()
        val b = newMid.toTypedArray()
        val dp = lcsTable(a, b)
        val out = ArrayList<DiffLine>(a.size + b.size)
        var added = 0
        var removed = 0
        var unchanged = 0
        var i = a.size
        var j = b.size
        while (i > 0 && j > 0) {
            if (a[i - 1] == b[j - 1]) {
                val (text, san) = sanitizeLine(a[i - 1])
                out.add(DiffLine(DiffLine.Kind.CONTEXT, lineOffset + i, lineOffset + j, text, sanitized = san))
                unchanged++
                i--
                j--
            } else if (dp[i][j - 1] >= dp[i - 1][j]) {
                val (text, san) = sanitizeLine(b[j - 1])
                out.add(DiffLine(DiffLine.Kind.ADDED, null, lineOffset + j, text, sanitized = san))
                added++
                j--
            } else {
                val (text, san) = sanitizeLine(a[i - 1])
                out.add(DiffLine(DiffLine.Kind.REMOVED, lineOffset + i, null, text, sanitized = san))
                removed++
                i--
            }
        }
        while (i > 0) {
            val (text, san) = sanitizeLine(a[i - 1])
            out.add(DiffLine(DiffLine.Kind.REMOVED, lineOffset + i, null, text, sanitized = san))
            removed++
            i--
        }
        while (j > 0) {
            val (text, san) = sanitizeLine(b[j - 1])
            out.add(DiffLine(DiffLine.Kind.ADDED, null, lineOffset + j, text, sanitized = san))
            added++
            j--
        }
        out.reverse()
        return MidDiff(out, added, removed, unchanged)
    }

    private fun lcsTable(
        a: Array<String>,
        b: Array<String>,
    ): Array<IntArray> {
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) {
            for (j in 1..m) {
                dp[i][j] =
                    if (a[i - 1] == b[j - 1]) {
                        dp[i - 1][j - 1] + 1
                    } else {
                        maxOf(dp[i - 1][j], dp[i][j - 1])
                    }
            }
        }
        return dp
    }
}

private fun ByteArray.toHexLowercase(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append("0123456789abcdef"[v ushr 4])
        sb.append("0123456789abcdef"[v and 0x0F])
    }
    return sb.toString()
}

object Hashing {
    fun md5Of(text: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return digest.toHexLowercase()
    }
}
