package com.wuwaconfig.app.model

import android.content.Context
import com.google.gson.Gson
import com.wuwaconfig.app.util.writeAtomic
import java.io.File

object BattleStatsStore {
    private const val FILE_NAME = "cached_battle_stats.json"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    private val gson = Gson()

    data class CachedBattleStats(
        val stats: BattleStats,
        val timestamp: Long,
        val summary: BattleStatsSummary? = null,
    )

    private fun CachedBattleStats.toTimedCache() = TimedCache(stats, timestamp)

    /**
     * Callers must already be on a background dispatcher — this performs real
     * file I/O (via writeAtomic's fsync) and is not main-thread safe.
     */
    fun save(
        context: Context,
        stats: BattleStats,
        summary: BattleStatsSummary? = null,
    ) {
        val cached = CachedBattleStats(stats, System.currentTimeMillis(), summary)
        // writeAtomic, not writeText: a kill mid-writeText leaves truncated JSON
        // and the next load() falls into the catch -> null branch, losing the
        // whole 24h cache.
        File(context.filesDir, FILE_NAME).writeAtomic(gson.toJson(cached))
    }

    fun load(context: Context): BattleStats? =
        readFreshCache(
            File(context.filesDir, FILE_NAME),
            ttlMs = CACHE_TTL_MS,
            now = System.currentTimeMillis(),
        ) { text -> gson.fromJson(text, CachedBattleStats::class.java)?.toTimedCache() }

    fun loadSummary(context: Context): BattleStatsSummary? =
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return null
            val now = System.currentTimeMillis()
            gson.fromJson(file.readText(), CachedBattleStats::class.java)
                ?.takeIf { now - it.timestamp < CACHE_TTL_MS }
                ?.summary
        }.getOrNull()

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
