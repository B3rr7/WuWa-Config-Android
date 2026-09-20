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

    fun compute(
        oldText: String,
        newText: String,
    ): DiffResult {
        val oldLines = oldText.split('\n').map { sanitizeLine(it) }
        val newLines = newText.split('\n').map { sanitizeLine(it) }
        val sanitizedCount = oldLines.count { it.second } + newLines.count { it.second }

        val lcs = lcsTable(oldLines.map { it.first }.toTypedArray(), newLines.map { it.first }.toTypedArray())
        val out = ArrayList<DiffLine>(oldLines.size + newLines.size)
        var i = oldLines.size
        var j = newLines.size
        var added = 0
        var removed = 0
        var unchanged = 0
        while (i > 0 && j > 0) {
            val oldLine = oldLines[i - 1]
            val newLine = newLines[j - 1]
            if (oldLine.first == newLine.first) {
                out.add(DiffLine(DiffLine.Kind.CONTEXT, i, j, oldLine.first, sanitized = oldLine.second || newLine.second))
                unchanged++
                i--
                j--
            } else if (lcs[i][j - 1] >= lcs[i - 1][j]) {
                out.add(DiffLine(DiffLine.Kind.ADDED, null, j, newLine.first, sanitized = newLine.second))
                added++
                j--
            } else {
                out.add(DiffLine(DiffLine.Kind.REMOVED, i, null, oldLine.first, sanitized = oldLine.second))
                removed++
                i--
            }
        }
        while (i > 0) {
            val oldLine = oldLines[i - 1]
            out.add(DiffLine(DiffLine.Kind.REMOVED, i, null, oldLine.first, sanitized = oldLine.second))
            removed++
            i--
        }
        while (j > 0) {
            val newLine = newLines[j - 1]
            out.add(DiffLine(DiffLine.Kind.ADDED, null, j, newLine.first, sanitized = newLine.second))
            added++
            j--
        }
        out.reverse()
        return DiffResult(
            lines = out,
            summary =
                DiffSummary(
                    added = added,
                    removed = removed,
                    unchanged = unchanged,
                ),
            sanitizedLines = sanitizedCount,
        )
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
