package com.wuwaconfig.app.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import com.google.gson.reflect.TypeToken
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaHistoryEntry
import com.wuwaconfig.app.model.GachaRecord
import com.wuwaconfig.app.model.PityPrediction
import com.wuwaconfig.app.model.SsrInterval
import com.wuwaconfig.app.util.writeAtomic
import java.io.File
import java.util.UUID

object GachaHistoryStore {
    private const val FILE_NAME = "gacha_history.json"
    private const val TTL_HOURS = 12L

    /** Shared Gson configured with [InstanceCreator]s so that legacy cache JSON written
     *  by prior app versions (missing fields added later) is hydrated with Kotlin default
     *  values instead of `null`. Plain Gson ignores Kotlin defaults and would NPE the UI. */
    val gson: Gson =
        GsonBuilder()
            .registerTypeAdapter(GachaData::class.java, InstanceCreator { GachaData() })
            .registerTypeAdapter(
                PityPrediction::class.java,
                InstanceCreator {
                    PityPrediction(
                        poolType = "",
                        poolLabel = "",
                        status = "",
                        lastFiveStarName = "",
                        lastFiveStarTime = "",
                        pullsSinceLastFive = 0,
                        estimatedNextFive = 0,
                    )
                },
            )
            .registerTypeAdapter(
                GachaRecord::class.java,
                InstanceCreator {
                    GachaRecord(cardPoolType = "", qualityLevel = 0, name = "", count = 0, time = "")
                },
            )
            .registerTypeAdapter(
                SsrInterval::class.java,
                InstanceCreator {
                    SsrInterval(name = "", count = 0, time = "", pity = 0)
                },
            )
            .create()
    private val lock = Any()

    private fun getFile(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun load(ctx: Context): GachaHistoryEntry? {
        val file = getFile(ctx)
        if (!file.exists()) return null
        return synchronized(lock) {
            try {
                val text = file.readText()
                val type = object : TypeToken<GachaHistoryEntry>() {}.type
                val entry = gson.fromJson<GachaHistoryEntry>(text, type)
                if (entry != null && System.currentTimeMillis() >= entry.expiresAt) {
                    file.delete()
                    null
                } else {
                    entry
                }
            } catch (_: Exception) {
                file.delete()
                null
            }
        }
    }

    fun save(
        ctx: Context,
        data: GachaData,
    ): GachaHistoryEntry {
        val now = System.currentTimeMillis()
        val entry =
            GachaHistoryEntry(
                id = UUID.randomUUID().toString().take(8),
                expiresAt = now + TTL_HOURS * 60 * 60 * 1000,
                totalPulls = data.totalPulls,
                fiveStars = data.fiveStars,
                fullDataJson = gson.toJson(data),
            )
        synchronized(lock) { getFile(ctx).writeAtomic(gson.toJson(entry)) }
        return entry
    }

    fun delete(ctx: Context) {
        synchronized(lock) { getFile(ctx).delete() }
    }
}
