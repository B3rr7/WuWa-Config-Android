package com.wuwaconfig.app.util

import java.io.File
import java.io.FileOutputStream

/**
 * Atomic file write: content lands in a temp sibling, is flushed all the way to
 * stable storage, then rename(2)'d over the target. A crash mid-write can never
 * leave the store truncated — readers see either the old file or the new one.
 *
 * The fsync is not optional. `writeText()` only lands the bytes in the PAGE
 * CACHE, so a power loss immediately after the rename can still leave a
 * zero-length or half-written target; `flush()` pushes the Java-side buffer out
 * and `fd.sync()` forces it to the device.
 *
 * Known limit: `fd.sync()` covers the temp file's DATA, not the parent
 * directory entry created by the subsequent `renameTo`. A crash in the window
 * between the rename and a directory fsync can still lose the rename itself on
 * filesystems that journal metadata separately. Java cannot portably open a
 * directory as a FileChannel here, so the guarantee is "readers never see a
 * truncated or half-written file" — not "the rename is guaranteed durable".
 * That is the property callers actually depend on.
 *
 * Mirrors the pattern HashMonitor already uses for the hash file.
 */
fun File.writeAtomic(
    text: String,
    tmpSuffix: String = ".tmp",
) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    val tmp = File(parentFile, "$name$tmpSuffix-${System.nanoTime()}")
    try {
        FileOutputStream(tmp).use { fos ->
            fos.write(bytes)
            fos.flush()
            fos.fd.sync()
        }
        if (!tmp.renameTo(this)) {
            replaceViaDestinationTemp(tmp, this, tmpSuffix)
        }
    } finally {
        if (tmp.exists()) tmp.delete()
    }
}

/**
 * Cross-filesystem fallback for [writeAtomic]: rename(2) fails with EXDEV when
 * the temp lands on a different mount than the target (filesDir ->
 * Downloads-style paths on exotic mounts).
 *
 * Deliberately NEVER `delete()`s the target first. delete-then-copy opens a
 * window in which the store does not exist at all, so the next launch finds
 * nothing and the profile / gacha / deploy history is simply gone. Instead the
 * bytes are copied to a second temp in the DESTINATION directory and that temp
 * is renamed over the target — still atomic with respect to readers, and
 * non-destructive when it fails.
 */
private fun replaceViaDestinationTemp(
    tmp: File,
    target: File,
    tmpSuffix: String,
) {
    val destDir = target.parentFile ?: throw IllegalStateException("Cannot replace ${target.path}: no destination directory")
    val destTmp = File(destDir, "${target.name}$tmpSuffix-xdev-${System.nanoTime()}")
    var committed = false
    try {
        tmp.copyTo(destTmp, overwrite = true)
        committed = destTmp.renameTo(target)
        if (!committed) throw IllegalStateException("Cannot atomically replace ${target.path} (cross-filesystem rename failed)")
    } finally {
        if (!committed) destTmp.delete()
    }
}
