package com.wuwaconfig.app.model

import java.io.File

/**
 * A JSON payload plus the wall-clock millis it was written at. Both
 * `cached_*.json` stores already had this exact shape inline.
 */
internal data class TimedCache<T>(
    val value: T,
    val timestamp: Long,
)

/**
 * Shared 24h TTL gate for [BattleStatsStore] and [LogAnalysisStore].
 *
 * Both stores previously carried an identical
 * `now - cached.timestamp > CACHE_TTL_MS -> delete; return null` branch. Two
 * copies means a fix to the expiry rule has to be made twice, and only one of
 * them gets made.
 *
 * The age comes from the **timestamp embedded in the JSON**, never from the
 * file's mtime. That distinction is load-bearing: the file is written by
 * [com.wuwaconfig.app.util.writeAtomic] via a temp sibling plus a rename, and a
 * restore-from-backup or an `adb pull` round-trip can rewrite mtime without the
 * payload changing. Trusting mtime would silently expire a cache the user never
 * invalidated.
 *
 * Deleting on expiry is what makes this a cache rather than a store: the space
 * is reclaimed on read, so a stale payload can never be revived by a later load.
 * Corrupt-but-recent data is deliberately NOT deleted — a kill mid-write can
 * leave truncated JSON that the next [writeAtomic] will overwrite anyway.
 */
internal fun <T> readFreshCache(
    file: File,
    ttlMs: Long,
    now: Long,
    decode: (String) -> TimedCache<T>?,
): T? {
    if (!file.exists()) return null
    val cached =
        try {
            decode(file.readText())
        } catch (_: Exception) {
            null
        } ?: return null
    return if (now - cached.timestamp > ttlMs) {
        file.delete()
        null
    } else {
        cached.value
    }
}
