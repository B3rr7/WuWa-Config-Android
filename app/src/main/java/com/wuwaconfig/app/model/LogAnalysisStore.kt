package com.wuwaconfig.app.model

import android.content.Context
import com.google.gson.Gson
import com.wuwaconfig.app.config.BrainRecommendation
import com.wuwaconfig.app.util.writeAtomic
import java.io.File

object LogAnalysisStore {
    private const val FILE_NAME = "cached_log_analysis.json"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    private val gson = Gson()

    data class CachedAnalysis(
        val logInfo: LogInfo,
        val brainRecommendation: BrainRecommendation?,
        val timestamp: Long,
    )

    private fun CachedAnalysis.toTimedCache() = TimedCache(this, timestamp)

    /**
     * Callers must already be on a background dispatcher — this performs real
     * file I/O (via writeAtomic's fsync) and is not main-thread safe.
     */
    fun save(
        context: Context,
        logInfo: LogInfo,
        brainRecommendation: BrainRecommendation?,
    ) {
        val cached = CachedAnalysis(logInfo, brainRecommendation, System.currentTimeMillis())
        // writeAtomic, not writeText: a kill mid-writeText leaves truncated JSON
        // and the next load() falls into the catch -> null branch, losing the
        // whole 24h cache.
        File(context.filesDir, FILE_NAME).writeAtomic(gson.toJson(cached))
    }

    fun load(context: Context): CachedAnalysis? =
        readFreshCache(
            File(context.filesDir, FILE_NAME),
            ttlMs = CACHE_TTL_MS,
            now = System.currentTimeMillis(),
        ) { text -> gson.fromJson(text, CachedAnalysis::class.java)?.toTimedCache() }
}
