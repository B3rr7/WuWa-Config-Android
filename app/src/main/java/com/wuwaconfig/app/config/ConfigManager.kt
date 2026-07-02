package com.wuwaconfig.app.config

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.PUSH_RETRY_COUNT
import com.wuwaconfig.app.backend.shQuote
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.ConfigFile
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.model.PlayerProfile
import com.wuwaconfig.app.model.VerificationReport
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfigManager(private val context: Context, private val backend: AccessBackend, backupDirPath: String? = null) {
    private val gson = Gson()

    private val backupDir: File = File(backupDirPath ?: File(context.filesDir, "backups").absolutePath).also {
        if (!it.mkdirs() && !it.exists()) {
            LogRepository.add("ConfigManager: failed to create backup dir: ${it.absolutePath}", LogLevel.WARNING)
        }
    }
    private val publicDir: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WuWaConfig").also {
        if (!it.mkdirs() && !it.exists()) {
            LogRepository.add("ConfigManager: failed to create public dir: ${it.absolutePath}", LogLevel.WARNING)
        }
    }

    suspend fun applyCustomConfigs(
        engineIni: String?,
        deviceProfilesIni: String?,
        gameUserSettingsIni: String?,
        scalabilityIni: String? = null,
        hardwareIni: String? = null,
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            LogRepository.add("ConfigManager: applying custom configs")
            onProgress("Ensuring target directory exists...")
            backend.ensureDirectoryExists(GamePaths.TARGET_DIR).getOrThrow()

            val iniFiles = listOfNotNull(
                engineIni?.takeIf { it.isNotBlank() }?.let { "Engine.ini" to it },
                deviceProfilesIni?.takeIf { it.isNotBlank() }?.let { "DeviceProfiles.ini" to it },
                gameUserSettingsIni?.takeIf { it.isNotBlank() }?.let { "GameUserSettings.ini" to it },
                scalabilityIni?.takeIf { it.isNotBlank() }?.let { "Scalability.ini" to it },
                hardwareIni?.takeIf { it.isNotBlank() }?.let { "Hardware.ini" to it }
            )

            if (iniFiles.isEmpty()) {
                LogRepository.add("ConfigManager: no config files selected", LogLevel.WARNING)
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
                    LogRepository.add("ConfigManager: pushing $name")
                    val tempFile = File(tempDir, name)
                    tempFile.writeText(content)
                    val targetPath = "${GamePaths.TARGET_DIR}/$name"
                    var lastError: Result<String>? = null
                    var success = false
                    for (attempt in 0..PUSH_RETRY_COUNT) {
                        val r = backend.pushFile(tempFile.absolutePath, targetPath)
                        if (r.isSuccess) { success = true; break }
                        lastError = r
                        onProgress("Retrying $name (attempt ${attempt + 2})...")
                    }
                    if (!success) {
                        throw lastError?.exceptionOrNull() ?: Exception("Failed to push $name")
                    }
                }
                LogRepository.add("ConfigManager: custom configs applied successfully", LogLevel.SUCCESS)
                Result.success("Custom configs applied successfully!")
            } finally {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            LogRepository.add("ConfigManager: applyCustomConfigs failed: ${e.message}", LogLevel.ERROR)
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

    suspend fun verifyDeployedCvars(generatedCvars: Set<String>): Result<VerificationReport> = withContext(Dispatchers.IO) {
        try {
            val logResult = readRemoteLogText("${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}")
            if (logResult.isFailure) return@withContext Result.failure(logResult.exceptionOrNull()!!)
            val (text, _) = logResult.getOrThrow()
            val info = LogParser.parseLog(text)
            val recognizedLower = info.activeCvars.keys.map { it.lowercase() }.toSet()
            val accepted = generatedCvars.filter { it.lowercase() in recognizedLower }.toSet()
            val rejected = generatedCvars - accepted
            Result.success(VerificationReport(
                accepted = accepted,
                rejected = rejected,
                recognizedCount = accepted.size,
                totalCount = generatedCvars.size
            ))
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
            val publicFile = File(publicDir, "Client.log")
            publicFile.writeText(content)
            onProgress("Saved to ${savedFile.absolutePath}")
            onProgress("Also saved to ${publicFile.absolutePath} (public)")
            Result.success(savedFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBackup(name: String, type: String = "manual", selectedFiles: Set<String>? = null): Result<ConfigBackup> = withContext(Dispatchers.IO) {
        try {
            LogRepository.add("ConfigManager: creating backup '$name'")
            Log.d("ConfigManager", "createBackup: listing ${GamePaths.TARGET_DIR}")
            val files = backend.listDirectory(GamePaths.TARGET_DIR).getOrThrow()
            Log.d("ConfigManager", "createBackup: listed ${files.size} files: $files")
            val allIniNames = setOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini", "Scalability.ini", "Hardware.ini")
            val targetNames = selectedFiles ?: allIniNames
            val configFiles = files.filter { it in targetNames && it in allIniNames }.map { fileName ->
                Log.d("ConfigManager", "createBackup: reading $fileName")
                val content = backend.readFile("${GamePaths.TARGET_DIR}/$fileName").getOrDefault("")
                Log.d("ConfigManager", "createBackup: read $fileName (${content.length} chars)")
                ConfigFile(name = fileName, content = content)
            }
            LogRepository.add("ConfigManager: backup read ${configFiles.size} config files")
            Log.d("ConfigManager", "createBackup: saving backup to $backupDir")
            val backup = ConfigBackup(name = name, files = configFiles, type = type)
            File(backupDir, "${backup.id}.json").writeText(gson.toJson(backup))
            val publicBackupDir = File(File(publicDir, "Backups"), sanitizeDirName(name)).also { it.mkdirs() }
            configFiles.forEach { f -> File(publicBackupDir, f.name).writeText(f.content) }
            Log.d("ConfigManager", "createBackup: SUCCESS")
            LogRepository.add("ConfigManager: backup '$name' created", LogLevel.SUCCESS)
            Result.success(backup)
        } catch (e: Exception) {
            Log.e("ConfigManager", "createBackup FAILED: ${e.message}", e)
            LogRepository.add("ConfigManager: createBackup failed: ${e.message}", LogLevel.ERROR)
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

    private fun sanitizeDirName(name: String): String =
        name.replace(Regex("""[<>:"/\\|?*]"""), "_").take(100)

    fun deleteLocalBackup(backup: ConfigBackup) {
        val file = File(backupDir, "${backup.id}.json")
        if (file.exists()) file.delete()
        val pubDir = File(File(publicDir, "Backups"), sanitizeDirName(backup.name))
        if (pubDir.exists()) pubDir.deleteRecursively()
    }

    suspend fun restoreBackup(backup: ConfigBackup, onProgress: (String) -> Unit, selectedFiles: Set<String>? = null): Result<String> {
        val files = if (selectedFiles != null) backup.files.filter { it.name in selectedFiles } else backup.files
        if (files.isEmpty()) return Result.failure(Exception("No files selected for restore"))
        return applyFiles(backup.name, files, onProgress)
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
                    val targetPath = "${GamePaths.TARGET_DIR}/${file.name}"
                    var lastError: Result<String>? = null
                    var success = false
                    for (attempt in 0..PUSH_RETRY_COUNT) {
                        val r = backend.pushFile(tempFile.absolutePath, targetPath)
                        if (r.isSuccess) { success = true; break }
                        lastError = r
                        onProgress("Retrying ${file.name} (attempt ${attempt + 2})...")
                    }
                    if (!success) {
                        throw lastError?.exceptionOrNull() ?: Exception("Failed to restore ${file.name}")
                    }
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
        if (fileSize <= 0L) return Result.failure(Exception("Cannot determine log file size"))

        val CHUNK_SIZE = 1_000_000L
        val totalChunks = when {
            fileSize <= CHUNK_SIZE -> 1
            else -> (fileSize / 5_000_000L).toInt().coerceIn(5, 30)
        }

        fun decodeB64(output: String): ByteArray? {
            val clean = output.lines()
                .filterNot { it.startsWith("base64:", ignoreCase = true) }
                .joinToString("").trim()
            if (clean.isBlank()) return null
            return try { Base64.decode(clean, Base64.DEFAULT) } catch (_: Exception) { null }
        }

        fun decompress(data: ByteArray): ByteArray {
            return try {
                ByteArrayOutputStream().use { out ->
                    GZIPInputStream(data.inputStream()).use { it.copyTo(out) }
                    out.toByteArray()
                }
            } catch (_: Exception) { data }
        }

        suspend fun pullChunk(offset: Long): Pair<String, Boolean>? {
            val cmd = "dd if=\"$path\" bs=1 skip=$offset count=$CHUNK_SIZE 2>/dev/null | gzip -c | base64"
            val out = backend.executeShellCommand(cmd).getOrNull()
            val raw = out?.let { decodeB64(it) } ?: return null
            val decompressed = decompress(raw)
            return try { LogParser.decodeLogBytes(decompressed) } catch (_: Exception) { null }
        }

        suspend fun pullChunkText(offset: Long, wasEncrypted: Boolean): String? {
            val cmd = "dd if=\"$path\" bs=1 skip=$offset count=$CHUNK_SIZE 2>/dev/null | gzip -c | base64"
            val out = backend.executeShellCommand(cmd).getOrNull()
            val raw = out?.let { decodeB64(it) } ?: return null
            val decompressed = decompress(raw)
            return try {
                val pair = if (wasEncrypted) LogParser.decodeXorBytes(decompressed)
                           else LogParser.decodeLogBytes(decompressed)
                pair.first
            } catch (_: Exception) { null }
        }

        if (totalChunks == 1) {
            onProgress(50)
            val full = backend.executeShellCommand("base64 \"$path\" 2>/dev/null")
            onProgress(85)
            val raw = full.getOrNull()?.let { decodeB64(it) }
            val result = raw?.let { try { LogParser.decodeLogBytes(it) } catch (_: Exception) { null } }
            onProgress(95)
            if (result != null) return Result.success(result)
        } else {
            val offsets = (0 until totalChunks).map { i ->
                (fileSize * i / totalChunks).coerceAtMost(fileSize - CHUNK_SIZE)
            }.distinct()

            fun progressForChunk(done: Int) = 10 + (done * 80 / offsets.size)

            var wasEncrypted = false
            val chunks = mutableListOf<String>()

            for ((i, offset) in offsets.withIndex()) {
                onProgress(progressForChunk(i))
                if (i == 0) {
                    pullChunk(offset)?.let { (text, enc) ->
                        wasEncrypted = enc
                        chunks.add(text)
                    }
                } else {
                    pullChunkText(offset, wasEncrypted)?.let { chunks.add(it) }
                }
            }

            onProgress(92)
            val combined = chunks.joinToString("\n")
            onProgress(95)
            if (combined.isNotBlank()) return Result.success(combined to wasEncrypted)
        }

        onProgress(95)
        return Result.failure(Exception("Failed to read Client.log ($fileSize bytes)"))
    }

    suspend fun getCurrentConfigFiles(): Result<List<String>> {
        return backend.listDirectory(GamePaths.TARGET_DIR)
    }

    suspend fun deleteConfigFiles(onProgress: (String) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        try {
            LogRepository.add("ConfigManager: deleting config files")
            val targets = listOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini", "Scalability.ini", "Hardware.ini")
            var deleted = 0
            for (name in targets) {
                val path = "${GamePaths.TARGET_DIR}/$name"
                if (backend.fileExists(path).getOrElse { false }) {
                    backend.executeShellCommand("rm -f \"$path\"").getOrThrow()
                    onProgress("Deleted $name")
                    LogRepository.add("ConfigManager: deleted $name")
                    deleted++
                }
            }
            if (deleted > 0) {
                LogRepository.add("ConfigManager: deleted $deleted config file(s)", LogLevel.SUCCESS)
                Result.success("Deleted $deleted config file(s)")
            } else {
                LogRepository.add("ConfigManager: no config files found to delete", LogLevel.WARNING)
                Result.failure(Exception("No config files found to delete"))
            }
        } catch (e: Exception) {
            LogRepository.add("ConfigManager: deleteConfigFiles failed: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    suspend fun refreshConfigHashes(): Result<String> = withContext(Dispatchers.IO) {
        try {
            LogRepository.add("ConfigManager: refreshing config hashes")
            val md5 = MessageDigest.getInstance("MD5")
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

            val existingHashContent = backend.readFile(GamePaths.HASH_MONITOR_PATH).getOrDefault("")
            val existingLines = existingHashContent.lines().toMutableList()
            val hasExistingContent = existingLines.any { it.trim().startsWith("[") }

            // Build map of monitored file -> new values to patch
            val updates = mutableMapOf<String, Map<String, String>>()
            for (name in GamePaths.MONITORED_FILES) {
                val path = "${GamePaths.TARGET_DIR}/$name"
                val content = backend.readFile(path).getOrDefault("")
                val hash = md5.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }

                var prevCount = 0
                var inSection = false
                for (line in existingLines) {
                    val t = line.trim()
                    if (t.equals("[$name]", ignoreCase = false)) { inSection = true; continue }
                    if (inSection && t.startsWith("[") && t.endsWith("]")) break
                    if (inSection && t.startsWith("ModifyCount=")) {
                        prevCount = t.removePrefix("ModifyCount=").toIntOrNull() ?: 0
                    }
                }
                updates[name] = mapOf(
                    "Hash" to hash,
                    "ModifyCount" to (prevCount + 1).toString(),
                    "LastModifiedTime" to now
                )
            }

            val patchedLines = mutableListOf<String>()
            var currentSection = ""

            fun flushPendingSection(name: String) {
                val patch = updates.remove(name) ?: return
                for (lineKey in listOf("Hash", "ModifyCount", "LastModifiedTime")) {
                    val value = patch[lineKey] ?: continue
                    patchedLines.add("$lineKey=$value")
                }
            }

            if (hasExistingContent) {
                for (line in existingLines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        val sectionName = trimmed.removePrefix("[").removeSuffix("]")
                        // If we just finished a monitored section and not all patch keys were found,
                        // append any missing ones before the next section
                        if (updates.containsKey(currentSection)) {
                            flushPendingSection(currentSection)
                        }
                        currentSection = sectionName
                        patchedLines.add(line)
                    } else if (updates.containsKey(currentSection)) {
                        val eq = trimmed.indexOf('=')
                        if (eq > 0) {
                            val key = trimmed.substring(0, eq).trim()
                            val replacement = updates[currentSection]?.get(key)
                            if (replacement != null) {
                                val indent = line.takeWhile { it == ' ' || it == '\t' }
                                patchedLines.add("$indent$key=$replacement")
                            } else {
                                patchedLines.add(line)
                            }
                        } else {
                            patchedLines.add(line)
                        }
                    } else {
                        patchedLines.add(line)
                    }
                }
                // If last section was monitored, append any remaining patch keys
                if (updates.containsKey(currentSection)) {
                    flushPendingSection(currentSection)
                }
                // If any monitored files had no section in the existing file, append new sections
                for ((name, patch) in updates) {
                    patchedLines.add("")
                    patchedLines.add("[$name]")
                    patchedLines.add("Hash=${patch["Hash"] ?: ""}")
                    patchedLines.add("ModifyCount=${patch["ModifyCount"] ?: "0"}")
                    patchedLines.add("LastModifiedTime=${patch["LastModifiedTime"] ?: now}")
                }
            } else {
                // No existing content — build from scratch (first-time)
                for (name in GamePaths.MONITORED_FILES) {
                    val content = backend.readFile("${GamePaths.TARGET_DIR}/$name").getOrDefault("")
                    val hash = md5.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
                    patchedLines.add("[$name]")
                    patchedLines.add("Hash=$hash")
                    patchedLines.add("ModifyCount=1")
                    patchedLines.add("LastModifiedTime=$now")
                    patchedLines.add("")
                }
            }

            val newContent = patchedLines.joinToString("\n").trimEnd() + "\n"
            val tempFile = File(context.cacheDir, "KuroConfigMonitor.hash")
            tempFile.writeText(newContent)
            val hashTempPath = GamePaths.HASH_MONITOR_PATH + ".new"
            var hashPushOk = false
            var hashPushError: Throwable? = null
            for (attempt in 0..PUSH_RETRY_COUNT) {
                val r = backend.pushFile(tempFile.absolutePath, hashTempPath)
                if (r.isSuccess) { hashPushOk = true; break }
                hashPushError = r.exceptionOrNull()
            }
            if (!hashPushOk) {
                backend.executeShellCommand("rm -f ${shQuote(hashTempPath)}")
                throw hashPushError ?: Exception("Failed to push hash file")
            }
            backend.executeShellCommand("mv ${shQuote(hashTempPath)} ${shQuote(GamePaths.HASH_MONITOR_PATH)}")
            tempFile.delete()

            val verifyResult = backend.readFile(GamePaths.HASH_MONITOR_PATH)
            if (verifyResult.isSuccess) {
                val stored = verifyResult.getOrThrow().trim()
                if (stored == newContent.trim()) {
                    Log.d("ConfigManager", "Config hashes refreshed and verified successfully")
                    LogRepository.add("ConfigManager: hashes refreshed and verified", LogLevel.SUCCESS)
                    Result.success("Config hashes synced & verified")
                } else {
                    Log.w("ConfigManager", "Hash file read-back mismatch")
                    LogRepository.add("ConfigManager: hash verify mismatch", LogLevel.WARNING)
                    Result.success("Config hashes synced (verify mismatch)")
                }
            } else {
                Log.w("ConfigManager", "Could not verify hash file: ${verifyResult.exceptionOrNull()?.message}")
                LogRepository.add("ConfigManager: hash verify skipped", LogLevel.WARNING)
                Result.success("Config hashes synced (verify skipped)")
            }
        } catch (e: Exception) {
            Log.w("ConfigManager", "Failed to refresh hashes: ${e.message}")
            LogRepository.add("ConfigManager: refreshConfigHashes failed: ${e.message}", LogLevel.ERROR)
            Result.failure(e)
        }
    }

    suspend fun readConfigModifyCounts(): Result<List<com.wuwaconfig.app.model.ConfigHashInfo>> = withContext(Dispatchers.IO) {
        try {
            val content = backend.readFile(GamePaths.HASH_MONITOR_PATH).getOrDefault("")
            if (content.isBlank()) return@withContext Result.failure(Exception("No hash file on device"))
            val results = mutableListOf<com.wuwaconfig.app.model.ConfigHashInfo>()
            var currentFile = ""
            for (line in content.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    currentFile = trimmed.removePrefix("[").removeSuffix("]")
                } else if (trimmed.startsWith("ModifyCount=") && currentFile.isNotEmpty()) {
                    val count = trimmed.removePrefix("ModifyCount=").toIntOrNull() ?: 0
                    results.add(com.wuwaconfig.app.model.ConfigHashInfo(currentFile, count))
                }
            }
            if (results.isEmpty()) return@withContext Result.failure(Exception("No modify counts found"))
            Result.success(results)
        } catch (e: Exception) {
            Log.w("ConfigManager", "Failed to read modify counts: ${e.message}")
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

    private var cachedBattleStats: BattleStats? = null
    private var cachedFileSize: Long = -1L

    suspend fun readBattleStats(): Result<BattleStats> {
        val path = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
        try {
            val sizeRaw = backend.executeShellCommand("wc -c < \"$path\" 2>/dev/null").getOrDefault("0")
            val fileSize = sizeRaw.trim().toLongOrNull() ?: 0L

            if (fileSize == cachedFileSize && cachedBattleStats != null) {
                return Result.success(cachedBattleStats!!)
            }

            val CHUNK = 2_000_000L
            val NUM_SAMPLES = 8
            val rawChunks = mutableListOf<ByteArray>()

            fun decompress(data: ByteArray): ByteArray {
                return try {
                    java.io.ByteArrayOutputStream().use { out ->
                        java.util.zip.GZIPInputStream(data.inputStream()).use { it.copyTo(out) }
                        out.toByteArray()
                    }
                } catch (_: Exception) { data }
            }

            suspend fun pullChunk(offset: Long, count: Long): ByteArray? {
                val cmd = "dd if=\"$path\" bs=1 skip=$offset count=$count 2>/dev/null | gzip -c | base64"
                val out = backend.executeShellCommand(cmd).getOrNull() ?: return null
                val clean = out.lines()
                    .filterNot { it.startsWith("base64:", ignoreCase = true) }
                    .joinToString("").trim()
                if (clean.isBlank()) return null
                val b64decoded = try { Base64.decode(clean, Base64.DEFAULT) } catch (_: Exception) { return null }
                return decompress(b64decoded)
            }

            if (fileSize <= CHUNK) {
                pullChunk(0, fileSize)?.let { rawChunks.add(it) }
            } else {
                pullChunk(0, CHUNK)?.let { rawChunks.add(it) }
                val step = maxOf(1, fileSize / NUM_SAMPLES)
                for (i in 1 until NUM_SAMPLES) {
                    val offset = (step * i).coerceAtMost(fileSize - CHUNK)
                    pullChunk(offset, CHUNK)?.let { rawChunks.add(it) }
                }
            }

            if (rawChunks.isEmpty()) return Result.failure(Exception("No data read from log"))

            var wasEncrypted = false
            val texts = mutableListOf<String>()
            for ((i, raw) in rawChunks.withIndex()) {
                val (text, enc) = if (i == 0) LogParser.decodeLogBytes(raw) else {
                    if (wasEncrypted) LogParser.decodeXorBytes(raw)
                    else LogParser.decodeLogBytes(raw)
                }
                if (i == 0) wasEncrypted = enc
                texts.add(text)
            }

            val combined = texts.joinToString("\n")
            val stats = LogParser.parseBattleStats(combined).copy(logSizeBytes = fileSize)
            cachedBattleStats = stats
            cachedFileSize = fileSize
            return Result.success(stats)
        } catch (e: Exception) {
            Log.w("ConfigManager", "readBattleStats failed: ${e.message}")
            return Result.failure(e)
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
