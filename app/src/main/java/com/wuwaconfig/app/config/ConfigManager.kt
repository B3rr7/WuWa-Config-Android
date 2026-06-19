package com.wuwaconfig.app.config

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import android.util.Log
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.ConfigFile
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.PlayerProfile
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

class ConfigManager(private val context: Context, private val backend: AccessBackend, backupDirPath: String? = null) {
    private val gson = Gson()

    private val backupDir: File = File(backupDirPath ?: File(context.filesDir, "backups").absolutePath).also { it.mkdirs() }

    suspend fun applyCustomConfigs(
        engineIni: String?,
        deviceProfilesIni: String?,
        gameUserSettingsIni: String?,
        scalabilityIni: String? = null,
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            onProgress("Ensuring target directory exists...")
            backend.ensureDirectoryExists(GamePaths.TARGET_DIR).getOrThrow()

            val iniFiles = listOfNotNull(
                engineIni?.takeIf { it.isNotBlank() }?.let { "Engine.ini" to it },
                deviceProfilesIni?.takeIf { it.isNotBlank() }?.let { "DeviceProfiles.ini" to it },
                gameUserSettingsIni?.takeIf { it.isNotBlank() }?.let { "GameUserSettings.ini" to it },
                scalabilityIni?.takeIf { it.isNotBlank() }?.let { "Scalability.ini" to it }
            )

            if (iniFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No config file content selected"))
            }

            for ((name, _) in iniFiles) {
                val targetPath = "${GamePaths.TARGET_DIR}/$name"
                val exists = backend.fileExists(targetPath).getOrElse { false }
                if (exists) {
                    onProgress("$name exists on device, will overwrite")
                }
            }

            val tempDir = File(context.cacheDir, "staging")
            tempDir.mkdirs()
            try {
                for ((name, content) in iniFiles) {
                    onProgress("Applying $name...")
                    val tempFile = File(tempDir, name)
                    tempFile.writeText(content)
                    backend.pushFile(tempFile.absolutePath, "${GamePaths.TARGET_DIR}/$name").getOrThrow()
                }
                Result.success("Custom configs applied successfully!")
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readClientLogContent(onProgress: (Int) -> Unit = {}): Result<String> = withContext(Dispatchers.IO) {
        try {
            val logFilePath = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
            val content = readRemoteLogText(logFilePath, onProgress).getOrThrow()
            Result.success(content.first)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readClientLogTextWithMetadata(onProgress: (Int) -> Unit = {}): Result<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        try {
            val logFilePath = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
            readRemoteLogText(logFilePath, onProgress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun collectClientLog(onProgress: (String) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        try {
            val logFilePath = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
            onProgress("Reading ${GamePaths.LOG_FILE_NAME}...")
            val content = readRemoteLogText(logFilePath).getOrThrow().first
            backupDir.mkdirs()
            val savedFile = File(backupDir, "Client.log")
            savedFile.writeText(content)
            onProgress("Saved to ${savedFile.absolutePath}")
            Result.success(savedFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBackup(name: String, type: String = "manual"): Result<ConfigBackup> = withContext(Dispatchers.IO) {
        try {
            Log.d("ConfigManager", "createBackup: listing ${GamePaths.TARGET_DIR}")
            val files = backend.listDirectory(GamePaths.TARGET_DIR).getOrThrow()
            Log.d("ConfigManager", "createBackup: listed ${files.size} files: $files")
            val iniNames = setOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini")
            val configFiles = files.filter { it in iniNames }.map { fileName ->
                Log.d("ConfigManager", "createBackup: reading $fileName")
                val content = backend.readFile("${GamePaths.TARGET_DIR}/$fileName").getOrDefault("")
                Log.d("ConfigManager", "createBackup: read $fileName (${content.length} chars)")
                ConfigFile(name = fileName, content = content)
            }
            Log.d("ConfigManager", "createBackup: saving backup to $backupDir")
            val backup = ConfigBackup(name = name, files = configFiles, type = type)
            File(backupDir, "${backup.id}.json").writeText(gson.toJson(backup))
            Log.d("ConfigManager", "createBackup: SUCCESS")
            Result.success(backup)
        } catch (e: Exception) {
            Log.e("ConfigManager", "createBackup FAILED: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getLocalBackups(): List<ConfigBackup> {
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try { gson.fromJson(file.readText(), ConfigBackup::class.java) } catch (_: Exception) { null }
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    fun deleteLocalBackup(backup: ConfigBackup) {
        val file = File(backupDir, "${backup.id}.json")
        if (file.exists()) file.delete()
    }

    suspend fun restoreBackup(backup: ConfigBackup, onProgress: (String) -> Unit): Result<String> {
        return applyFiles(backup.name, backup.files, onProgress)
    }

    private suspend fun applyFiles(
        label: String,
        files: List<ConfigFile>,
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            onProgress("Ensuring target directory exists...")
            backend.ensureDirectoryExists(GamePaths.TARGET_DIR).getOrThrow()

            val tempDir = File(context.cacheDir, "staging")
            tempDir.mkdirs()
            try {
                for (file in files) {
                    onProgress("Restoring ${file.name}...")
                    val tempFile = File(tempDir, file.name)
                    tempFile.writeText(file.content)
                    backend.pushFile(tempFile.absolutePath, "${GamePaths.TARGET_DIR}/${file.name}").getOrThrow()
                }
                Result.success("$label restored successfully!")
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readCurrentConfig(fileName: String): Result<String> {
        return backend.readFile("${GamePaths.TARGET_DIR}/$fileName")
    }

    private suspend fun readRemoteLogText(path: String, onProgress: (Int) -> Unit = {}): Result<Pair<String, Boolean>> {
        onProgress(5)
        val sizeResult = backend.executeShellCommand("wc -c < \"$path\" 2>/dev/null")
        val fileSize = sizeResult.getOrNull()?.trim()?.toLongOrNull() ?: 0L
        onProgress(10)
        val CHUNK = 500_000L
        val LARGE_THRESHOLD = 500_000L

        fun decodeB64(output: String): ByteArray? {
            val clean = output.lines()
                .filterNot { it.startsWith("base64:", ignoreCase = true) }
                .joinToString("").trim()
            if (clean.isBlank()) return null
            return try { Base64.decode(clean, Base64.DEFAULT) } catch (_: Exception) { null }
        }

        fun decodeChunk(b64: String?): Pair<String, Boolean>? {
            val raw = b64?.let { decodeB64(it) } ?: return null
            return try { LogParser.decodeLogBytes(raw) } catch (_: Exception) { null }
        }

        if (fileSize > LARGE_THRESHOLD) {
            onProgress(25)
            val headB64 = backend.executeShellCommand("head -c $CHUNK \"$path\" | base64 2>/dev/null")
            onProgress(45)
            val tailB64 = backend.executeShellCommand("tail -c $CHUNK \"$path\" | base64 2>/dev/null")
            onProgress(60)
            val head = decodeChunk(headB64.getOrNull())
            val wasEncrypted = head?.second == true

            fun decodeChunkX(b64: String?): Pair<String, Boolean>? {
                val raw = b64?.let { decodeB64(it) } ?: return null
                return try {
                    if (wasEncrypted) com.wuwaconfig.app.config.LogParser.decodeXorBytes(raw)
                    else com.wuwaconfig.app.config.LogParser.decodeLogBytes(raw)
                } catch (_: Exception) { null }
            }

            val chunks = mutableListOf<String>()
            head?.first?.let { chunks.add(it) }
            if (fileSize > CHUNK * 3) {
                onProgress(65)
                val midOff = maxOf(0, fileSize / 2 - CHUNK / 2)
                val midB64 = backend.executeShellCommand("dd if=\"$path\" bs=1 skip=$midOff count=$CHUNK 2>/dev/null | base64")
                midB64.getOrNull()?.let { decodeChunkX(it) }?.first?.let { chunks.add(it) }
            }
            onProgress(80)
            decodeChunkX(tailB64.getOrNull())?.first?.let { chunks.add(it) }
            val text = chunks.joinToString("\n")
            onProgress(90)
            if (text.isNotBlank()) return Result.success(text to wasEncrypted)
        } else {
            onProgress(25)
            val full = backend.executeShellCommand("base64 \"$path\" 2>/dev/null")
            onProgress(70)
            val result = full.getOrNull()?.let { decodeChunk(it) }
            onProgress(90)
            if (result != null) return Result.success(result)
        }
        onProgress(85)
        return backend.readFile(path).map { text ->
            LogParser.decodeLogBytes(text.toByteArray(Charsets.ISO_8859_1))
        }
    }

    suspend fun getCurrentConfigFiles(): Result<List<String>> {
        return backend.listDirectory(GamePaths.TARGET_DIR)
    }

    suspend fun deleteConfigFiles(onProgress: (String) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targets = listOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini")
            var deleted = 0
            for (name in targets) {
                val path = "${GamePaths.TARGET_DIR}/$name"
                if (backend.fileExists(path).getOrElse { false }) {
                    backend.executeShellCommand("rm -f \"$path\"").getOrThrow()
                    onProgress("Deleted $name")
                    deleted++
                }
            }
            if (deleted > 0) {
                Result.success("Deleted $deleted config file(s)")
            } else {
                Result.failure(Exception("No config files found to delete"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshConfigHashes(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val md5 = MessageDigest.getInstance("MD5")
            val lines = mutableListOf<String>()

            for (name in GamePaths.MONITORED_FILES) {
                val path = "${GamePaths.TARGET_DIR}/$name"
                val content = backend.readFile(path).getOrDefault("")
                val hash = md5.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
                lines.add("[$name]")
                lines.add("Hash=$hash")
                lines.add("ModifyCount=1")
                lines.add("LastModifiedTime=2026-06-19 12:00:00")
                lines.add("")
            }

            val newContent = lines.joinToString("\n").trimEnd() + "\n"
            val tempFile = File(context.cacheDir, "KuroConfigMonitor.hash")
            tempFile.writeText(newContent)
            backend.pushFile(tempFile.absolutePath, GamePaths.HASH_MONITOR_PATH).getOrThrow()
            tempFile.delete()
            Log.d("ConfigManager", "Config hashes refreshed successfully")
            Result.success("Config hashes synced")
        } catch (e: Exception) {
            Log.w("ConfigManager", "Failed to refresh hashes: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun readProfile(): Result<PlayerProfile> = withContext(Dispatchers.IO) {
        val localDb = pullDb("LocalStorage.db")
        val devDb = pullDb("DeviceStorage.db")
        try {
            val uid = queryDb(localDb, "RecentlyLoginUID")?.filter { it.isDigit() }
            val langRaw = queryDb(devDb, "UseLanguage_en")

            val serverLevels = parseServerLevels(queryDb(localDb, "SdkLevelData"))
            val primaryServer = serverLevels.firstOrNull()
            val secondaryServer = serverLevels.getOrNull(1)

            val rawLogin = queryDb(localDb, "RecentlyLoginUID")
            val otherUid = if (rawLogin != null && uid != null && rawLogin != uid) rawLogin else null

            val uidStr = uid ?: ""

            val profile = PlayerProfile(
                engineSettingCount = countIniSettings("Engine.ini"),
                deviceProfileCount = countIniSettings("DeviceProfiles.ini"),
                gameUserSettingCount = countIniSettings("GameUserSettings.ini"),
                uid = uid,
                server = primaryServer?.first,
                playerLevel = primaryServer?.second,
                serverLevels = serverLevels,
                secondaryUid = otherUid,
                secondaryServer = secondaryServer?.first,
                secondaryLevel = secondaryServer?.second,
                lastLoginTime = formatTimestamp(cleanString(queryDb(localDb, "LoginTime_$uidStr"))),
                towerFloor = queryDb(localDb, "AdventrueTower_$uidStr")?.toIntOrNull(),
                weeklyRogueScore = queryDb(localDb, "AdventrueWeeklyRogue_$uidStr")?.toIntOrNull(),
                battlePassPurchased = queryDb(localDb, "BattlePassPayButton_$uidStr")?.contains("1B") == true,
                loopTowerSeason = queryDb(localDb, "LoopTowerSeason_$uidStr")?.toIntOrNull(),
                gameVersion = cleanString(queryDb(devDb, "Version_Resource")),
                patchVersion = cleanString(queryDb(devDb, "PatchVersion")),
                launcherVersion = cleanString(queryDb(devDb, "Version_Launcher")),
                language = when (cleanString(langRaw)) {
                    "1" -> "en"
                    "2" -> "zh"
                    "3" -> "ja"
                    "4" -> "ko"
                    else -> cleanString(langRaw) ?: "—"
                },
                loginDeviceId = extractSavValue("KURO_PLAYER_PREFS.sav", "LoginDeviceId"),
            )
            Result.success(profile)
        } catch (e: Exception) {
            Log.w("ConfigManager", "readProfile failed: ${e.message}")
            Result.failure(e)
        } finally {
            localDb?.close()
            devDb?.close()
            File(context.cacheDir, "profile_LocalStorage.db").delete()
            File(context.cacheDir, "profile_DeviceStorage.db").delete()
        }
    }

    suspend fun readBattleStats(): Result<BattleStats> {
        return try {
            val result = readRemoteLogText("${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}")
            if (result.isFailure) return Result.failure(result.exceptionOrNull() ?: Exception("Failed to read log"))
            val (text, _) = result.getOrThrow()
            val stats = LogParser.parseBattleStats(text)
            Result.success(stats)
        } catch (e: Exception) {
            Log.w("ConfigManager", "readBattleStats failed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun countIniSettings(name: String): Int {
        val path = "${GamePaths.TARGET_DIR}/$name"
        val content = backend.readFile(path).getOrDefault("")
        return content.lines().count { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith(";") || trimmed.startsWith("[")) return@count false
            val eq = trimmed.indexOf('=')
            if (eq < 0) return@count false
            val afterEq = trimmed.substring(eq + 1).trim()
            afterEq.isNotEmpty() && !afterEq.startsWith(";")
        }
    }

    private fun cleanString(raw: String?): String? {
        return raw?.trim()?.trim('"')?.trim('\'')?.trimEnd(')')?.takeIf { it.isNotBlank() }
    }

    private suspend fun pullDb(dbName: String): SQLiteDatabase? {
        val remotePath = when (dbName) {
            "LocalStorage.db" -> "${GamePaths.LOG_DIR.substringBeforeLast("/")}/LocalStorage/$dbName"
            "DeviceStorage.db" -> "${GamePaths.LOG_DIR.substringBeforeLast("/")}/DeviceSaved/$dbName"
            else -> return null
        }
        val localFile = File(context.cacheDir, "profile_$dbName")
        return try {
            val raw = backend.executeShellCommand("base64 \"$remotePath\" 2>/dev/null").getOrNull() ?: return null
            val bytes = Base64.decode(raw.trim(), Base64.DEFAULT)
            localFile.writeBytes(bytes)
            SQLiteDatabase.openDatabase(localFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: Exception) { null }
    }

    private fun queryDb(db: SQLiteDatabase?, key: String): String? {
        if (db == null) return null
        return try {
            val cursor = db.rawQuery("SELECT value FROM LocalStorage WHERE key=?", arrayOf(key))
            val result = if (cursor.moveToFirst()) cursor.getString(0) else null
            cursor.close()
            result
        } catch (_: Exception) { null }
    }

    private suspend fun extractSavValue(savName: String, key: String): String? {
        val savPath = "${GamePaths.LOG_DIR.substringBeforeLast("/")}/SaveGames/$savName"
        val raw = backend.executeShellCommand("strings \"$savPath\" 2>/dev/null | grep -A1 \"^$key\$\" | tail -1").getOrNull()?.trim()
        return raw?.takeIf { it.isNotBlank() && !it.contains("StrProperty") }
    }

    private fun parseServerLevels(json: String?): List<Pair<String, Int>> {
        if (json == null) return emptyList()
        val results = mutableListOf<Pair<String, Int>>()
        try {
            val regionRegex = """"Region"\s*:\s*"([^"]+)"""".toRegex()
            val levelRegex = """"Level"\s*:\s*(\d+)""".toRegex()
            val regions = regionRegex.findAll(json).toList()
            val levels = levelRegex.findAll(json).toList()
            for (i in 0 until minOf(regions.size, levels.size)) {
                val region = regions[i].groupValues[1]
                val level = levels[i].groupValues[1].toIntOrNull() ?: continue
                results.add(region to level)
            }
        } catch (_: Exception) {}
        return results
    }

    private fun formatTimestamp(ts: String?): String? {
        if (ts == null) return null
        val cleaned = ts.takeWhile { it.isDigit() || it == '.' }
        val seconds = cleaned.toDoubleOrNull()
        if (seconds != null && seconds > 0) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            return sdf.format(java.util.Date((seconds * 1000).toLong()))
        }
        return ts.take(19)
    }
}
