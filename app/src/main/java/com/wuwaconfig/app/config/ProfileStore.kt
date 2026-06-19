package com.wuwaconfig.app.config

import android.content.Context
import com.google.gson.Gson
import com.wuwaconfig.app.model.PlayerProfile
import java.io.File

object ProfileStore {
    private const val FILE_NAME = "player_profile.json"
    private val gson = Gson()

    private fun getFile(ctx: Context): File =
        File(ctx.filesDir, FILE_NAME)

    fun load(ctx: Context): PlayerProfile? {
        val file = getFile(ctx)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), PlayerProfile::class.java)
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    fun save(ctx: Context, profile: PlayerProfile) {
        getFile(ctx).writeText(gson.toJson(profile))
    }

    fun delete(ctx: Context) {
        getFile(ctx).delete()
    }
}
