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

    /**
     * Past the retention window the file is dropped. Before it, it is kept even
     * when stale: staleness means "not the player's account right now", which is
     * not a reason to destroy a year of pull history.
     *
     * `load()` used to return null *and* `file.delete()` on a single 12-hour TTL,
     * which made this a cache wearing a history file's name. Lifetime statistics
     * — average pity, non-banner rate, totals — are only computable from a record
     * set that accumulates, and that is what the split enables.
     */
    private val retentionMs: Long
        get() = hoursOrDefault(gameProfile().gachaHistoryRetentionHours, GameProfile.DEFAULT_GACHA_RETENTION_HOURS) * 60L * 60L * 1000L

    /** How long a fetch still counts as current. Age only; never causes deletion. */
    private val freshMs: Long
        get() = hoursOrDefault(gameProfile().gachaHistoryFreshHours, GameProfile.DEFAULT_GACHA_FRESH_HOURS) * 60L * 60L * 1000L

    /**
     * A non-positive configured window would make [load] delete every entry the
     * instant it was written, or mark every entry permanently fresh. Neither is a
     * state an asset typo should be able to produce, so fall back to that window's
     * own compiled-in default.
     */
    private fun hoursOrDefault(
        hours: Int,
        default: Int,
    ): Int = if (hours > 0) hours else default

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

    /**
     * Reads the stored history, or null when there is none.
     *
     * Returns the entry whether or not it is still fresh — freshness is reported
     * by [isStale] so a caller can decide whether the numbers describe the account
     * right now. Only a lapsed *retention* window removes the file.
     */
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

    /**
     * True when the entry is no longer a current snapshot of the account.
     *
     * A [GachaHistoryEntry] written before `fetchedAt` existed reports zero there,
     * which must read as "unknown age" rather than "infinitely old" — a legacy
     * cache is not thereby worthless, and treating it as stale would tell the user
     * to re-fetch data that is still perfectly good history.
     */
    fun isStale(
        entry: GachaHistoryEntry,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val fetchedAt = entry.fetchedAt
        if (fetchedAt <= 0L) return false
        return now - fetchedAt >= freshMs
    }

    /** Hours since the entry was fetched, or null when that cannot be known. */
    fun ageHours(
        entry: GachaHistoryEntry,
        now: Long = System.currentTimeMillis(),
    ): Long? {
        val fetchedAt = entry.fetchedAt
        if (fetchedAt <= 0L) return null
        return (now - fetchedAt).coerceAtLeast(0L) / (60L * 60L * 1000L)
    }

    fun save(
        ctx: Context,
        data: GachaData,
    ): GachaHistoryEntry {
        val now = System.currentTimeMillis()
        val entry =
            GachaHistoryEntry(
                id = UUID.randomUUID().toString().take(8),
                fetchedAt = now,
                // Retention, not freshness: this is when the file stops being kept,
                // which is deliberately much further out than "stale".
                expiresAt = now + retentionMs,
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
