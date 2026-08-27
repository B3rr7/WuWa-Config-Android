package com.wuwaconfig.app.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaHistoryEntry
import com.wuwaconfig.app.util.writeAtomic
import java.io.File
import java.util.UUID

object GachaHistoryStore {
    private const val FILE_NAME = "gacha_history.json"
    private const val TTL_HOURS = 12L
    private val gson = Gson()
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
    ) {
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
    }

    fun delete(ctx: Context) {
        synchronized(lock) { getFile(ctx).delete() }
    }
}
