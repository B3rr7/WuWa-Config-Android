package com.wuwaconfig.app.config

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

class ConfigManager(private val context: Context, private val backend: AccessBackend, backupDirPath: String? = null) {
    private val gson = Gson()
    private val hashMutex = Mutex()

    private val backupDir: File =
        File(backupDirPath ?: File(context.filesDir, "backups").absolutePath).also {
            if (!it.mkdirs() && !it.exists()) {
                LogRepository.add("ConfigManager: failed to create backup dir: ${it.absolutePath}", LogLevel.WARNING)
            }
        }
    private val publicDir: File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WuWaConfig").also {
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
        onProgress: (String) -> Unit,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                LogRepository.add("ConfigManager: applying custom configs")
                onProgress("Ensuring target directory exists...")
                backend.ensureDirectoryExists(GamePaths.TARGET_DIR).getOrThrow()

                val iniFiles =
                    listOfNotNull(
                        engineIni?.takeIf { it.isNotBlank() }?.let { "Engine.ini" to it },
                        deviceProfilesIni?.takeIf { it.isNotBlank() }?.let { "DeviceProfiles.ini" to it },
                        gameUserSettingsIni?.takeIf { it.isNotBlank() }?.let { "GameUserSettings.ini" to it },
                        scalabilityIni?.takeIf { it.isNotBlank() }?.let { "Scalability.ini" to it },
                        hardwareIni?.takeIf { it.isNotBlank() }?.let { "Hardware.ini" to it },
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
                            if (r.isSuccess) {
                                success = true
                                break
                            }
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

    suspend fun pushSingleFile(
        fileName: String,
        content: String,
        onProgress: (String) -> Unit,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                LogRepository.add("ConfigManager: pushing single file $fileName")
                onProgress("Ensuring target directory exists...")
                backend.ensureDirectoryExists(GamePaths.TARGET_DIR).getOrThrow()

                val tempDir = File(context.cacheDir, "staging")
                tempDir.mkdirs()
                try {
                    val tempFile = File(tempDir, fileName)
                    tempFile.writeText(content)
                    val targetPath = "${GamePaths.TARGET_DIR}/$fileName"
                    var lastError: Result<String>? = null
                    var success = false
                    for (attempt in 0..PUSH_RETRY_COUNT) {
                        val r = backend.pushFile(tempFile.absolutePath, targetPath)
                        if (r.isSuccess) {
                            success = true
                            break
                        }
                        lastError = r
                        onProgress("Retrying $fileName (attempt ${attempt + 2})...")
                    }
                    if (!success) {
                        throw lastError?.exceptionOrNull() ?: Exception("Failed to push $fileName")
                    }
                    LogRepository.add("ConfigManager: $fileName pushed successfully", LogLevel.SUCCESS)
                    Result.success("$fileName pushed successfully!")
                } finally {
                    tempDir.deleteRecursively()
                }
            } catch (e: Exception) {
                LogRepository.add("ConfigManager: pushSingleFile failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    suspend fun readClientLogContent(onProgress: (Int) -> Unit = {}): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val logFilePath = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
                val content = readRemoteLogText(logFilePath, onProgress).getOrThrow()
                Result.success(content.first)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun readClientLogTextWithMetadata(onProgress: (Int) -> Unit = {}): Result<Pair<String, Boolean>> =
        withContext(Dispatchers.IO) {
            try {
                val logFilePath = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
                readRemoteLogText(logFilePath, onProgress)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun readLatestBackupLogWithMetadata(onProgress: (Int) -> Unit = {}): Result<Pair<String, Boolean>> =
        withContext(Dispatchers.IO) {
            try {
                val listCmd = "ls -t ${shQuote(GamePaths.LOG_DIR)}/Client-backup-*.log 2>/dev/null | head -1"
                val result = backend.executeShellCommand(listCmd)
                val path = result.getOrNull()?.trim()
                if (path.isNullOrBlank()) return@withContext Result.failure(Exception("No backup log found"))
                LogRepository.add("ConfigManager: reading latest backup log: ${path.substringAfterLast("/")}")
                readRemoteLogText(path, onProgress)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun verifyDeployedCvars(generatedCvars: Set<String>): Result<VerificationReport> =
        withContext(Dispatchers.IO) {
            try {
                val logResult = readRemoteLogText("${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}")
                if (logResult.isFailure) return@withContext Result.failure(logResult.exceptionOrNull()!!)
                val (text, _) = logResult.getOrThrow()
                val info = LogParser.parseLog(text)
                val recognizedLower = info.activeCvars.keys.map { it.lowercase() }.toSet()
                val accepted = generatedCvars.filter { it.lowercase() in recognizedLower }.toSet()
                val rejected = generatedCvars - accepted
                Result.success(
                    VerificationReport(
                        accepted = accepted,
                        rejected = rejected,
                        recognizedCount = accepted.size,
                        totalCount = generatedCvars.size,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun cleanupOldClientLogs() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        for (dir in listOf(backupDir, publicDir)) {
            val file = File(dir, "Client.log")
            if (file.exists() && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    suspend fun collectClientLog(onProgress: (String) -> Unit): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                cleanupOldClientLogs()
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

    suspend fun createBackup(
        name: String,
        type: String = "manual",
        selectedFiles: Set<String>? = null,
    ): Result<ConfigBackup> =
        withContext(Dispatchers.IO) {
            try {
                LogRepository.add("ConfigManager: creating backup '$name'")
                Log.d("ConfigManager", "createBackup: listing ${GamePaths.TARGET_DIR}")
                val files = backend.listDirectory(GamePaths.TARGET_DIR).getOrThrow()
                Log.d("ConfigManager", "createBackup: listed ${files.size} files: $files")
                val allIniNames = setOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini", "Scalability.ini", "Hardware.ini")
                val targetNames = selectedFiles ?: allIniNames
                val configFiles =
                    files.filter { it in targetNames && it in allIniNames }.map { fileName ->
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
        val privateBackups =
            if (backupDir.exists()) {
                backupDir.listFiles()
                    ?.filter { it.extension == "json" }
                    ?.mapNotNull { file ->
                        try {
                            gson.fromJson(file.readText(), ConfigBackup::class.java)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    ?: emptyList()
            } else emptyList()

        val privateNames = privateBackups.map { it.name }.toSet()
        val publicBackupsDir = File(publicDir, "Backups")
        val publicBackups =
            if (publicBackupsDir.exists()) {
                publicBackupsDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.filter { dir -> dir.listFiles()?.any { f -> f.extension == "ini" } == true }
                    ?.filter { dir -> dir.name !in privateNames }
                    ?.mapNotNull { dir ->
                        try {
                            val iniFiles =
                                dir.listFiles()
                                    ?.filter { it.extension == "ini" }
                                    ?.sortedBy { it.name }
                                    ?.map { f ->
                                        ConfigFile(name = f.name, content = f.readText())
                                    }
                                    ?: emptyList()
                            if (iniFiles.isEmpty()) return@mapNotNull null
                            ConfigBackup(
                                id = java.util.UUID.nameUUIDFromBytes(dir.absolutePath.toByteArray()).toString(),
                                name = dir.name,
                                timestamp = dir.lastModified().coerceAtLeast(1L),
                                files = iniFiles,
                                type = "legacy",
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    ?: emptyList()
            } else emptyList()

        return (privateBackups + publicBackups).sortedByDescending { it.timestamp }
    }

    private fun sanitizeDirName(name: String): String = name.replace(Regex("""[<>:"/\\|?*]"""), "_").take(100)

    fun deleteLocalBackup(backup: ConfigBackup) {
        val file = File(backupDir, "${backup.id}.json")
        if (file.exists()) file.delete()
        val pubDir = File(File(publicDir, "Backups"), sanitizeDirName(backup.name))
        if (pubDir.exists()) pubDir.deleteRecursively()
    }

    suspend fun restoreBackup(
        backup: ConfigBackup,
        onProgress: (String) -> Unit,
        selectedFiles: Set<String>? = null,
    ): Result<String> {
        val files = if (selectedFiles != null) backup.files.filter { it.name in selectedFiles } else backup.files
        if (files.isEmpty()) return Result.failure(Exception("No files selected for restore"))
        return applyFiles(backup.name, files, onProgress)
    }

    private suspend fun applyFiles(
        label: String,
        files: List<ConfigFile>,
        onProgress: (String) -> Unit,
    ): Result<String> =
        withContext(Dispatchers.IO) {
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
                            if (r.isSuccess) {
                                success = true
                                break
                            }
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

    private suspend fun readRemoteLogText(
        path: String,
        onProgress: (Int) -> Unit = {},
    ): Result<Pair<String, Boolean>> {
        onProgress(5)
        val existsResult = backend.executeShellCommand("test -f \"$path\" 2>/dev/null && echo 1 || echo 0")
        val fileExists = existsResult.getOrNull()?.trim() == "1"
        if (!fileExists) return Result.failure(Exception("Client.log not found at: $path"))

        val sizeResult = backend.executeShellCommand("wc -c < \"$path\" 2>/dev/null")
        val fileSize = sizeResult.getOrNull()?.trim()?.toLongOrNull() ?: 0L
        if (fileSize <= 0L) return Result.failure(Exception("Client.log is empty"))

        val CHUNK_SIZE = 1_000_000L
        val totalChunks =
            when {
                fileSize <= CHUNK_SIZE -> 1
                else -> (fileSize / 5_000_000L).toInt().coerceIn(5, 30)
            }

        fun decodeB64(output: String): ByteArray? {
            val clean =
                output.lines()
                    .filterNot { it.startsWith("base64:", ignoreCase = true) }
                    .joinToString("").trim()
            if (clean.isBlank()) return null
            return try {
                Base64.decode(clean, Base64.DEFAULT)
            } catch (_: Exception) {
                null
            }
        }

        suspend fun pullChunk(offset: Long): Pair<String, Boolean>? {
            val cmd = "dd if=\"$path\" bs=1 skip=$offset count=$CHUNK_SIZE 2>/dev/null | base64"
            val out = backend.executeShellCommand(cmd).getOrNull()
            val raw = out?.let { decodeB64(it) } ?: return null
            return try {
                LogParser.decodeLogBytes(raw)
            } catch (_: Exception) {
                null
            }
        }

        suspend fun pullChunkText(
            offset: Long,
            wasEncrypted: Boolean,
        ): String? {
            val cmd = "dd if=\"$path\" bs=1 skip=$offset count=$CHUNK_SIZE 2>/dev/null | base64"
            val out = backend.executeShellCommand(cmd).getOrNull()
            val raw = out?.let { decodeB64(it) } ?: return null
            return try {
                val pair =
                    if (wasEncrypted) {
                        LogParser.decodeXorBytes(raw)
                    } else {
                        LogParser.decodeLogBytes(raw)
                    }
                pair.first
            } catch (_: Exception) {
                null
            }
        }

        suspend fun readFullBase64(): Result<Pair<String, Boolean>>? {
            onProgress(50)
            val full = backend.executeShellCommand("base64 \"$path\" 2>/dev/null")
            onProgress(85)
            val raw = full.getOrNull()?.let { decodeB64(it) } ?: return null
            val result =
                try {
                    LogParser.decodeLogBytes(raw)
                } catch (_: Exception) {
                    null
                }
            onProgress(95)
            return if (result != null) Result.success(result) else null
        }

        if (totalChunks == 1) {
            val singleResult = readFullBase64()
            if (singleResult != null) return singleResult
        } else {
            val offsets =
                (0 until totalChunks).map { i ->
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

            // Multi-chunk failed — fall back to full-file base64 read
            val fallback = readFullBase64()
            if (fallback != null) return fallback
        }

        onProgress(95)
        return Result.failure(Exception("Failed to read Client.log ($fileSize bytes)"))
    }

    fun cleanIniContent(
        original: String,
        fileName: String,
    ): String {
        if (original.isBlank()) return original
        if (fileName == "Engine.ini") {
            val result = StringBuilder()
            var inCoreSystem = false
            for (line in original.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    val section = trimmed.removePrefix("[").removeSuffix("]")
                    inCoreSystem = section == "Core.System"
                    if (inCoreSystem) {
                        result.appendLine(line)
                    }
                    continue
                }
                if (inCoreSystem) {
                    result.appendLine(line)
                }
            }
            return result.toString().trimEnd() + "\n"
        }
        val result = StringBuilder()
        var inSection = false
        for (line in original.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inSection = true
                result.appendLine(line)
                continue
            }
            if (inSection && trimmed.contains('=')) continue
            if (inSection && trimmed.isNotBlank()) continue
            result.appendLine(line)
        }
        return result.toString().trimEnd() + "\n"
    }

    suspend fun cleanConfigFiles(onProgress: (String) -> Unit): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                LogRepository.add("ConfigManager: cleaning config files")
                var cleaned = 0
                for (name in GamePaths.MONITORED_FILES) {
                    val path = "${GamePaths.TARGET_DIR}/$name"
                    if (!backend.fileExists(path).getOrElse { false }) continue
                    onProgress("Reading $name...")
                    val contentResult = backend.readFile(path)
                    if (contentResult.isFailure) continue
                    val content = contentResult.getOrThrow()
                    val cleanedContent = cleanIniContent(content, name)
                    if (cleanedContent == content) {
                        onProgress("$name unchanged, skipping")
                        continue
                    }
                    onProgress("Cleaning $name...")
                    val tempFile = File(context.cacheDir, "staging_$name")
                    tempFile.parentFile?.mkdirs()
                    try {
                        tempFile.writeText(cleanedContent)
                        backend.pushFile(tempFile.absolutePath, path).getOrThrow()
                        LogRepository.add("ConfigManager: cleaned $name")
                        cleaned++
                    } finally {
                        tempFile.delete()
                    }
                }
                if (cleaned > 0) {
                    LogRepository.add("ConfigManager: cleaned $cleaned config file(s)", LogLevel.SUCCESS)
                    Result.success("Cleaned $cleaned config file(s)")
                } else {
                    LogRepository.add("ConfigManager: no config files needed cleaning", LogLevel.WARNING)
                    Result.failure(Exception("All config files are already clean"))
                }
            } catch (e: Exception) {
                LogRepository.add("ConfigManager: cleanConfigFiles failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    suspend fun deleteConfigFiles(fileNames: Set<String>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (fileNames.isEmpty()) return@withContext Result.failure(Exception("No files selected for deletion"))
                LogRepository.add("ConfigManager: deleting config files: ${fileNames.joinToString(", ")}")
                var deleted = 0
                var errors = 0
                for (name in fileNames) {
                    val path = "${GamePaths.TARGET_DIR}/$name"
                    val exists = backend.fileExists(path).getOrElse { false }
                    if (!exists) {
                        LogRepository.add("ConfigManager: $name not found on device, skipping")
                        continue
                    }
                    val cmd = "rm -f ${shQuote(path)}"
                    val result = backend.executeShellCommand(cmd)
                    if (result.isSuccess) {
                        LogRepository.add("ConfigManager: deleted $name")
                        deleted++
                    } else {
                        LogRepository.add("ConfigManager: failed to delete $name: ${result.exceptionOrNull()?.message}", LogLevel.ERROR)
                        errors++
                    }
                }
                if (deleted > 0) {
                    LogRepository.add("ConfigManager: deleted $deleted config file(s)", LogLevel.SUCCESS)
                    Result.success("Deleted $deleted config file(s)")
                } else {
                    LogRepository.add("ConfigManager: no config files were deleted", LogLevel.WARNING)
                    Result.failure(Exception("No config files were deleted"))
                }
            } catch (e: Exception) {
                LogRepository.add("ConfigManager: deleteConfigFiles failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    private suspend fun computeIniHash(name: String): Result<String> {
        val path = "${GamePaths.TARGET_DIR}/$name"
        val bytesResult = backend.readFileBytes(path)
        if (bytesResult.isFailure) {
            LogRepository.add("ConfigManager: readFileBytes FAILED for $name: ${bytesResult.exceptionOrNull()?.message}", LogLevel.ERROR)
            return Result.failure(bytesResult.exceptionOrNull()!!)
        }
        val bytes = bytesResult.getOrThrow()
        val md5 = MessageDigest.getInstance("MD5")
        val hash = md5.digest(bytes).joinToString("") { "%02x".format(it) }
        LogRepository.add("ConfigManager: computed hash for $name = $hash (${bytes.size} bytes)")
        return Result.success(hash)
    }

    suspend fun refreshConfigHashes(): Result<String> =
        hashMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    LogRepository.add("ConfigManager: refreshing config hashes")
                    val md5 = MessageDigest.getInstance("MD5")
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

                    val existingHashContent = backend.readFile(GamePaths.HASH_MONITOR_PATH).getOrDefault("")
                    val existingLines = existingHashContent.lines().toMutableList()
                    val hasExistingContent = existingLines.any { it.trim().startsWith("[") }

                    val updates = mutableMapOf<String, Map<String, String>>()
                    for (name in GamePaths.MONITORED_FILES) {
                        val hashResult = computeIniHash(name)
                        if (hashResult.isFailure) {
                            LogRepository.add("ConfigManager: hash computation FAILED for $name, using empty hash", LogLevel.ERROR)
                        }
                        val hash = hashResult.getOrDefault("")

                        var prevCount: Int? = null
                        var prevTime = ""
                        var inSection = false
                        val iniSectionRegex = Regex("^\\[[A-Za-z0-9_\\-]+\\.ini\\]$", RegexOption.IGNORE_CASE)
                        for (line in existingLines) {
                            val t = line.trim()
                            if (t.equals("[$name]", ignoreCase = true)) {
                                inSection = true
                                continue
                            }
                            if (inSection && t.matches(iniSectionRegex)) break
                            if (inSection && t.startsWith("ModifyCount=")) {
                                prevCount = t.removePrefix("ModifyCount=").toIntOrNull()
                            }
                            if (inSection && t.startsWith("LastModifiedTime=")) {
                                prevTime = t.removePrefix("LastModifiedTime=").trim()
                            }
                        }
                        val displayCount = (prevCount?.coerceIn(0, 8)) ?: 0
                        updates[name] =
                            mapOf(
                                "Hash" to hash,
                                "ModifyCount" to displayCount.toString(),
                                "LastModifiedTime" to (prevTime.ifBlank { now }),
                            )
                    }

                    val patchedLines = mutableListOf<String>()
                    var currentSection = ""
                    val seenKeys = mutableSetOf<String>()

                    fun flushPendingSection(name: String) {
                        val patch = updates.remove(name) ?: return
                        for (lineKey in listOf("Hash", "ModifyCount", "LastModifiedTime")) {
                            val value = patch[lineKey] ?: continue
                            val newLine = "$lineKey=$value"
                            if (seenKeys.add(newLine)) patchedLines.add(newLine)
                        }
                    }

                    fun dedupLine(trimmed: String): Boolean {
                        if (trimmed.startsWith("[") && trimmed.endsWith("]")) return false
                        val eq = trimmed.indexOf('=')
                        if (eq <= 0) return false
                        val key = trimmed.substring(0, eq).trim()
                        val isDuplicate = key in listOf("Hash", "ModifyCount", "LastModifiedTime") && !seenKeys.add(trimmed)
                        if (isDuplicate) {
                            LogRepository.add("ConfigManager: dropped duplicate $key in section [$currentSection]", LogLevel.WARNING)
                        }
                        return isDuplicate
                    }

                    if (hasExistingContent) {
                        for (line in existingLines) {
                            val trimmed = line.trim()
                            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                                val sectionName = trimmed.removePrefix("[").removeSuffix("]")
                                if (updates.containsKey(currentSection)) {
                                    flushPendingSection(currentSection)
                                }
                                currentSection = sectionName
                                seenKeys.clear()
                                patchedLines.add(line)
                            } else if (updates.containsKey(currentSection)) {
                                val eq = trimmed.indexOf('=')
                                if (eq > 0) {
                                    val key = trimmed.substring(0, eq).trim()
                                    val replacement = updates[currentSection]?.get(key)
                                    if (replacement != null) {
                                        val indent = line.takeWhile { it == ' ' || it == '\t' }
                                        val newLine = "$indent$key=$replacement"
                                        if (seenKeys.add(newLine)) patchedLines.add(newLine)
                                    } else if (!dedupLine(trimmed)) {
                                        patchedLines.add(line)
                                    }
                                } else {
                                    patchedLines.add(line)
                                }
                            } else if (!dedupLine(trimmed)) {
                                patchedLines.add(line)
                            }
                        }
                        if (updates.containsKey(currentSection)) {
                            flushPendingSection(currentSection)
                        }
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
                            val hashResult = computeIniHash(name)
                            val hash =
                                if (hashResult.isSuccess) {
                                    hashResult.getOrThrow()
                                } else {
                                    LogRepository.add("ConfigManager: first-time hash FAILED for $name, using fallback", LogLevel.ERROR)
                                    val content = backend.readFile("${GamePaths.TARGET_DIR}/$name").getOrDefault("")
                                    md5.digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
                                }
                            patchedLines.add("[$name]")
                            patchedLines.add("Hash=$hash")
                            patchedLines.add("ModifyCount=0")
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
                        if (r.isSuccess) {
                            hashPushOk = true
                            break
                        }
                        hashPushError = r.exceptionOrNull()
                    }
                    if (!hashPushOk) {
                        backend.executeShellCommand("rm -f ${shQuote(hashTempPath)}")
                        throw hashPushError ?: Exception("Failed to push hash file")
                    }
                    val mvResult = backend.executeShellCommand("mv ${shQuote(hashTempPath)} ${shQuote(GamePaths.HASH_MONITOR_PATH)}")
                    if (mvResult.isFailure) {
                        backend.executeShellCommand("rm -f ${shQuote(hashTempPath)}")
                        LogRepository.add("ConfigManager: atomic rename failed, .new temp cleaned up", LogLevel.ERROR)
                        throw mvResult.exceptionOrNull() ?: Exception("Failed to atomically rename hash file")
                    }
                    tempFile.delete()

                    val verifyResult = backend.readFile(GamePaths.HASH_MONITOR_PATH)
                    if (verifyResult.isSuccess) {
                        val stored = verifyResult.getOrThrow().trim()
                        if (stored == newContent.trim()) {
                            Log.d("ConfigManager", "Config hashes refreshed and verified successfully")
                            LogRepository.add("ConfigManager: hashes refreshed and verified", LogLevel.SUCCESS)
                            Result.success("Config hashes synced & verified")
                        } else {
                            Log.e("ConfigManager", "Hash file read-back MISMATCH — hash may be corrupt")
                            LogRepository.add("ConfigManager: hash verify MISMATCH", LogLevel.ERROR)
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
        }

    data class HashFileSnapshot(
        val content: String,
        val timestamp: Long,
    )

    suspend fun snapshotHashFile(): Result<HashFileSnapshot> =
        withContext(Dispatchers.IO) {
            val result = backend.readFile(GamePaths.HASH_MONITOR_PATH)
            if (result.isFailure) {
                LogRepository.add("ConfigManager: hash snapshot FAILED: ${result.exceptionOrNull()?.message}", LogLevel.ERROR)
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }
            val content = result.getOrThrow()
            LogRepository.add("ConfigManager: hash snapshot taken (${content.length} chars)")
            Result.success(HashFileSnapshot(content, System.currentTimeMillis()))
        }

    suspend fun reconcileAfterModify(snapshot: HashFileSnapshot?): Result<String> {
        if (snapshot == null) {
            LogRepository.add("ConfigManager: no snapshot — full refresh", LogLevel.WARNING)
            return refreshConfigHashes()
        }
        return withContext(Dispatchers.IO) {
            val currentContent = backend.readFile(GamePaths.HASH_MONITOR_PATH).getOrDefault("")
            val gameTouched = currentContent != snapshot.content

            if (gameTouched) {
                LogRepository.add(
                    "ConfigManager: hash file CHANGED during operation — concurrent game access detected, reconciling",
                    LogLevel.WARNING,
                )
            } else {
                LogRepository.add("ConfigManager: hash file unchanged — safe update")
            }

            refreshConfigHashes()
        }
    }

    suspend fun readConfigModifyCounts(): Result<List<com.wuwaconfig.app.model.ConfigHashInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val content = backend.readFile(GamePaths.HASH_MONITOR_PATH).getOrDefault("")
                if (content.isBlank()) return@withContext Result.failure(Exception("No hash file on device"))
                val monitoredNames = GamePaths.MONITORED_FILES.toSet()
                val results = mutableListOf<com.wuwaconfig.app.model.ConfigHashInfo>()
                var currentFile = ""
                for (line in content.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        currentFile = trimmed.removePrefix("[").removeSuffix("]")
                    } else if (trimmed.startsWith("ModifyCount=") && currentFile.isNotEmpty() && currentFile in monitoredNames) {
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

    suspend fun readProfile(): Result<PlayerProfile> =
        withContext(Dispatchers.IO) {
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

                val profile =
                    PlayerProfile(
                        engineSettingCount = countIniSettings("Engine.ini"),
                        deviceProfileCount = countIniSettings("DeviceProfiles.ini"),
                        gameUserSettingCount = countIniSettings("GameUserSettings.ini"),
                        scalabilitySettingCount = countIniSettings("Scalability.ini"),
                        hardwareSettingCount = countIniSettings("Hardware.ini"),
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
                        language =
                            when (cleanString(langRaw)) {
                                "1" -> "en"
                                "2" -> "zh"
                                "3" -> "ja"
                                "4" -> "ko"
                                else -> cleanString(langRaw) ?: "—"
                            },
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

    private suspend fun readContiguousPartitionBytes(
        path: String,
        skip: Long,
        count: Long,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val cmd = "dd if=${shQuote(path)} bs=1 skip=$skip count=$count 2>/dev/null | gzip -cf | base64 -w0"
            val b64 = backend.executeShellCommand(cmd).getOrThrow().trim()
            if (b64.isBlank()) throw Exception("Empty partition at skip=$skip count=$count")
            val compressed = Base64.decode(b64, Base64.DEFAULT)
            GZIPInputStream(ByteArrayInputStream(compressed)).readBytes()
        }
    }

    suspend fun readBattleStats(): Result<BattleStats> {
        val path = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
        try {
            val sizeRaw = backend.executeShellCommand("wc -c < \"$path\" 2>/dev/null").getOrDefault("0")
            val fileSize = sizeRaw.trim().toLongOrNull() ?: 0L
            if (fileSize <= 0L) return Result.failure(Exception("Client.log is empty"))

            val numPartitions = when {
                fileSize <= 5_000_000L -> 1
                fileSize <= 30_000_000L -> 2
                fileSize <= 60_000_000L -> 4
                fileSize <= 100_000_000L -> 6
                else -> 8
            }
            val partitionSize = fileSize / numPartitions
            val offsets = (0 until numPartitions).map { i ->
                val skip = i * partitionSize
                val count = if (i == numPartitions - 1) fileSize - skip else partitionSize
                skip to count
            }

            val rawChunks = mutableListOf<ByteArray>()
            for ((skip, count) in offsets) {
                val result = readContiguousPartitionBytes(path, skip, count)
                if (result.isFailure) {
                    Log.w("ConfigManager", "partition skip=$skip failed: ${result.exceptionOrNull()?.message}")
                    continue
                }
                rawChunks.add(result.getOrThrow())
            }

            if (rawChunks.isEmpty()) return Result.failure(Exception("No data could be read from log"))

            var wasEncrypted = false
            val texts = mutableListOf<String>()
            for ((i, raw) in rawChunks.withIndex()) {
                val (text, enc) =
                    if (i == 0) {
                        LogParser.decodeLogBytes(raw)
                    } else {
                        if (wasEncrypted) {
                            LogParser.decodeXorBytes(raw)
                        } else {
                            LogParser.decodeLogBytes(raw)
                        }
                    }
                if (i == 0) wasEncrypted = enc
                texts.add(text)
            }

            val fullText = texts.joinToString("\n")
            val lines = fullText.lines()

            val numCores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

            val stats =
                if (numCores <= 1 || lines.size < 5000) {
                    LogParser.parseBattleStatsLines(lines)
                } else {
                    val chunkSize = (lines.size + numCores - 1) / numCores
                    val partials =
                        coroutineScope {
                            lines.chunked(chunkSize)
                                .map { chunk ->
                                    async(Dispatchers.Default) { LogParser.parseBattleStatsLines(chunk) }
                                }
                                .awaitAll()
                        }
                    partials.reduce { a, b -> a + b }
                }
            return Result.success(stats.copy(logSizeBytes = fileSize))
        } catch (e: Exception) {
            Log.w("ConfigManager", "readBattleStats failed: ${e.message}")
            return Result.failure(e)
        }
    }

    private suspend fun readFullFileText(path: String): Result<Pair<String, Boolean>> = runCatching {
        val sizeRaw = backend.executeShellCommand("wc -c < ${shQuote(path)} 2>/dev/null").getOrDefault("0")
        val fileSize = sizeRaw.trim().toLongOrNull() ?: 0L
        if (fileSize <= 0L) throw Exception("File is empty")

        val numPartitions = when {
            fileSize <= 5_000_000L -> 1
            fileSize <= 30_000_000L -> 2
            fileSize <= 60_000_000L -> 4
            fileSize <= 100_000_000L -> 6
            else -> 8
        }
        val partitionSize = fileSize / numPartitions
        val offsets = (0 until numPartitions).map { i ->
            val skip = i * partitionSize
            val count = if (i == numPartitions - 1) fileSize - skip else partitionSize
            skip to count
        }

        val rawChunks = mutableListOf<ByteArray>()
        for ((skip, count) in offsets) {
            val result = readContiguousPartitionBytes(path, skip, count)
            if (result.isFailure) {
                Log.w("ConfigManager", "partition skip=$skip failed: ${result.exceptionOrNull()?.message}")
                continue
            }
            rawChunks.add(result.getOrThrow())
        }

        if (rawChunks.isEmpty()) throw Exception("No data could be read from file")

        var wasEncrypted = false
        val texts = mutableListOf<String>()
        for ((i, raw) in rawChunks.withIndex()) {
            val (text, enc) =
                if (i == 0) {
                    LogParser.decodeLogBytes(raw)
                } else {
                    if (wasEncrypted) {
                        LogParser.decodeXorBytes(raw)
                    } else {
                        LogParser.decodeLogBytes(raw)
                    }
                }
            if (i == 0) wasEncrypted = enc
            texts.add(text)
        }

        texts.joinToString("\n") to wasEncrypted
    }

    suspend fun readFullClientLogWithMetadata(): Result<Pair<String, Boolean>> =
        readFullFileText("${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}")

    suspend fun readFullLatestBackupLog(): Result<Pair<String, Boolean>> = runCatching {
        val listCmd = "ls -t ${shQuote(GamePaths.LOG_DIR)}/Client-backup-*.log 2>/dev/null | head -1"
        val result = backend.executeShellCommand(listCmd)
        val logPath =
            result.getOrNull()?.trim()
                ?: throw Exception("No backup log found")
        LogRepository.add("ConfigManager: reading full backup log: ${logPath.substringAfterLast("/")}")
        readFullFileText(logPath).getOrThrow()
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
        val remotePath =
            when (dbName) {
                "LocalStorage.db" -> "${GamePaths.LOG_DIR.substringBeforeLast("/")}/LocalStorage/$dbName"
                "DeviceStorage.db" -> "${GamePaths.LOG_DIR.substringBeforeLast("/")}/DeviceSaved/$dbName"
                else -> return null
            }
        val localFile = File(context.cacheDir, "profile_$dbName")
        return try {
            val raw = backend.executeShellCommand("base64 ${shQuote(remotePath)} 2>/dev/null").getOrNull() ?: return null
            val bytes = Base64.decode(raw.trim(), Base64.DEFAULT)
            localFile.writeBytes(bytes)
            SQLiteDatabase.openDatabase(localFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDb(
        db: SQLiteDatabase?,
        key: String,
    ): String? {
        if (db == null) return null
        return try {
            val cursor = db.rawQuery("SELECT value FROM LocalStorage WHERE key=?", arrayOf(key))
            val result = if (cursor.moveToFirst()) cursor.getString(0) else null
            cursor.close()
            result
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun extractSavValue(
        savName: String,
        key: String,
    ): String? {
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
        } catch (_: Exception) {
        }
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
