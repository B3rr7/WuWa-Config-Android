package com.wuwaconfig.app.config

import com.google.gson.Gson
import com.wuwaconfig.app.model.PlayerProfile
import com.wuwaconfig.app.util.writeAtomic
import java.io.File

class ProfileStore(private val storeFile: File) {
    private val gson = Gson()

    fun load(): PlayerProfile? {
        if (!storeFile.exists()) return null
        return try {
            gson.fromJson(storeFile.readText(), PlayerProfile::class.java)
        } catch (_: Exception) {
            storeFile.delete()
            null
        }
    }

    fun save(profile: PlayerProfile) {
        try {
            storeFile.writeAtomic(gson.toJson(profile))
        } catch (_: Exception) {
        }
    }

    fun delete() {
        storeFile.delete()
    }
}
