package com.wuwaconfig.app.model

import android.content.Context
import com.google.gson.Gson
import java.io.File

object BattleStatsStore {
    private const val FILE_NAME = "cached_battle_stats.json"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    private val gson = Gson()

    data class CachedBattleStats(
        val stats: BattleStats,
        val timestamp: Long,
    )

    fun save(
        context: Context,
        stats: BattleStats,
    ) {
        val cached = CachedBattleStats(stats, System.currentTimeMillis())
        File(context.filesDir, FILE_NAME).writeText(gson.toJson(cached))
    }

    fun load(context: Context): BattleStats? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val cached =
            try {
                gson.fromJson(file.readText(), CachedBattleStats::class.java)
            } catch (_: Exception) {
                null
            }
        if (cached == null) return null
        if (System.currentTimeMillis() - cached.timestamp > CACHE_TTL_MS) {
            file.delete()
            return null
        }
        return cached.stats
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
