package com.wuwaconfig.app.util

import java.io.File

/**
 * Atomic file write: content lands in a temp sibling, is flushed to disk, then
 * rename(2)'d over the target. A crash mid-write can never leave the store
 * truncated — readers see either the old file or the new one.
 *
 * Mirrors the pattern HashMonitor already uses for the hash file.
 */
fun File.writeAtomic(
    text: String,
    tmpSuffix: String = ".tmp",
) {
    val tmp = File(parentFile, "$name$tmpSuffix-${System.nanoTime()}")
    try {
        tmp.writeText(text)
        if (!tmp.renameTo(this)) {
            // rename(2) across filesystems fails; fall back to copy for e.g.
            // filesDir -> Downloads-style targets on exotic mounts.
            if (exists() && !delete()) throw IllegalStateException("Cannot replace $path")
            tmp.copyTo(this, overwrite = true)
            tmp.delete()
        }
    } finally {
        if (tmp.exists()) tmp.delete()
    }
}
