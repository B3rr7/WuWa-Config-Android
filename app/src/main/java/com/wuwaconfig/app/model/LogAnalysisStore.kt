package com.wuwaconfig.app.model

import android.content.Context
import com.google.gson.Gson
import com.wuwaconfig.app.config.BrainRecommendation
import java.io.File

object LogAnalysisStore {
    private const val FILE_NAME = "cached_log_analysis.json"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    private val gson = Gson()

    data class CachedAnalysis(
        val logInfo: LogInfo,
        val brainRecommendation: BrainRecommendation?,
        val allowRestrictedCvars: Boolean,
        val timestamp: Long,
    )

    fun save(
        context: Context,
        logInfo: LogInfo,
        brainRecommendation: BrainRecommendation?,
        allowRestrictedCvars: Boolean,
    ) {
        val cached = CachedAnalysis(logInfo, brainRecommendation, allowRestrictedCvars, System.currentTimeMillis())
        File(context.filesDir, FILE_NAME).writeText(gson.toJson(cached))
    }

    fun load(context: Context): CachedAnalysis? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val cached =
            try {
                gson.fromJson(file.readText(), CachedAnalysis::class.java)
            } catch (_: Exception) {
                null
            }
        if (cached == null) return null
        if (System.currentTimeMillis() - cached.timestamp > CACHE_TTL_MS) {
            file.delete()
            return null
        }
        return cached
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
