package com.wuwaconfig.app.util

/**
 * Formats a byte count for display.
 *
 * 1024-based, because these are bytes on a filesystem and the rest of the
 * codebase already reports them that way. `BattleStatsScreen` used to carry a
 * private 1000-based copy, so the same 20 MB log read as "20.0 MB" in one place
 * and "21.0 MB" in another — a small difference, but one that makes two screens
 * disagree about the same file.
 */
fun formatBytes(bytes: Long): String =
    when {
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
