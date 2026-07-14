package com.wuwaconfig.app.util

data class DiffLine(
    val kind: Kind,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String,
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
)

object LineDiff {
    fun compute(
        oldText: String,
        newText: String,
    ): DiffResult {
        val oldLines = oldText.split('\n')
        val newLines = newText.split('\n')

        val lcs = lcsTable(oldLines.toTypedArray(), newLines.toTypedArray())
        val out = ArrayList<DiffLine>(oldLines.size + newLines.size)
        var i = oldLines.size
        var j = newLines.size
        var added = 0
        var removed = 0
        var unchanged = 0
        while (i > 0 && j > 0) {
            if (oldLines[i - 1] == newLines[j - 1]) {
                out.add(DiffLine(DiffLine.Kind.CONTEXT, i, j, oldLines[i - 1]))
                unchanged++
                i--
                j--
            } else if (lcs[i][j - 1] >= lcs[i - 1][j]) {
                out.add(DiffLine(DiffLine.Kind.ADDED, null, j, newLines[j - 1]))
                added++
                j--
            } else {
                out.add(DiffLine(DiffLine.Kind.REMOVED, i, null, oldLines[i - 1]))
                removed++
                i--
            }
        }
        while (i > 0) {
            out.add(DiffLine(DiffLine.Kind.REMOVED, i, null, oldLines[i - 1]))
            removed++
            i--
        }
        while (j > 0) {
            out.add(DiffLine(DiffLine.Kind.ADDED, null, j, newLines[j - 1]))
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
