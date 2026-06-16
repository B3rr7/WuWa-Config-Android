package com.wuwaconfig.app.config

import android.content.Context
import android.util.Base64
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.ConfigFile
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ConfigManager(private val context: Context, private val backend: AccessBackend, backupDirPath: String? = null) {
    private val gson = Gson()

    companion object {
        const val TARGET_PACKAGE = "com.kurogame.wutheringwaves.global"
        const val CONFIG_PATH = "files/UE4Game/Client/Client/Saved/Config/Android"
        const val LOG_PATH = "files/UE4Game/Client/Client/Saved/Logs"
        val TARGET_DIR = "/storage/emulated/0/Android/data/$TARGET_PACKAGE/$CONFIG_PATH"
        val LOG_DIR = "/storage/emulated/0/Android/data/$TARGET_PACKAGE/$LOG_PATH"
        const val LOG_FILE_NAME = "Client.log"
    }

    private val backupDir: File = File(backupDirPath ?: File(context.filesDir, "backups").absolutePath).also { it.mkdirs() }

    suspend fun applyCustomConfigs(
        engineIni: String?,
        deviceProfilesIni: String?,
        gameUserSettingsIni: String?,
        onProgress: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            onProgress("Ensuring target directory exists...")
            backend.ensureDirectoryExists(TARGET_DIR).getOrThrow()

            val iniFiles = listOfNotNull(
                engineIni?.takeIf { it.isNotBlank() }?.let { "Engine.ini" to it },
                deviceProfilesIni?.takeIf { it.isNotBlank() }?.let { "DeviceProfiles.ini" to it },
                gameUserSettingsIni?.takeIf { it.isNotBlank() }?.let { "GameUserSettings.ini" to it }
            )

            if (iniFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No config file content selected"))
            }

            for ((name, _) in iniFiles) {
                val targetPath = "$TARGET_DIR/$name"
                val exists = backend.fileExists(targetPath).getOrElse { false }
                if (exists) {
                    onProgress("Backing up $name...")
                    backend.backupFile(targetPath)
                }
            }

            val tempDir = File(context.cacheDir, "staging")
            tempDir.mkdirs()

            for ((name, content) in iniFiles) {
                onProgress("Applying $name...")
                val tempFile = File(tempDir, name)
                tempFile.writeText(content)
                backend.pushFile(tempFile.absolutePath, "$TARGET_DIR/$name").getOrThrow()
            }

            tempDir.deleteRecursively()
            Result.success("Custom configs applied successfully!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readClientLogContent(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val logFilePath = "$LOG_DIR/$LOG_FILE_NAME"
            val content = readRemoteLogText(logFilePath).getOrThrow()
            Result.success(content.first)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readClientLogTextWithMetadata(): Result<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        try {
            val logFilePath = "$LOG_DIR/$LOG_FILE_NAME"
            readRemoteLogText(logFilePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun collectClientLog(onProgress: (String) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        try {
            val logFilePath = "$LOG_DIR/$LOG_FILE_NAME"
            onProgress("Reading $LOG_FILE_NAME...")
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
            val files = backend.listDirectory(TARGET_DIR).getOrThrow()
            val iniNames = setOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini")
            val configFiles = files.filter { it in iniNames }.map { fileName ->
                val content = backend.readFile("$TARGET_DIR/$fileName").getOrDefault("")
                ConfigFile(name = fileName, content = content)
            }

            val backup = ConfigBackup(name = name, files = configFiles, type = type)
            File(backupDir, "${backup.id}.json").writeText(gson.toJson(backup))
            Result.success(backup)
        } catch (e: Exception) {
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
            backend.ensureDirectoryExists(TARGET_DIR).getOrThrow()

            val tempDir = File(context.cacheDir, "staging")
            tempDir.mkdirs()

            for (file in files) {
                onProgress("Restoring ${file.name}...")
                val tempFile = File(tempDir, file.name)
                tempFile.writeText(file.content)
                backend.pushFile(tempFile.absolutePath, "$TARGET_DIR/${file.name}").getOrThrow()
            }

            tempDir.deleteRecursively()
            Result.success("$label restored successfully!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readCurrentConfig(fileName: String): Result<String> {
        return backend.readFile("$TARGET_DIR/$fileName")
    }

    private suspend fun readRemoteLogText(path: String): Result<Pair<String, Boolean>> {
        val sizeResult = backend.executeShellCommand("wc -c < \"$path\" 2>/dev/null")
        val fileSize = sizeResult.getOrNull()?.trim()?.toLongOrNull() ?: 0L
        val isLarge = fileSize > 2_000_000

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

        if (isLarge) {
            val headB64 = backend.executeShellCommand("head -c 1000000 \"$path\" | base64 2>/dev/null")
            val tailB64 = backend.executeShellCommand("tail -c 1000000 \"$path\" | base64 2>/dev/null")
            val head = decodeChunk(headB64.getOrNull())
            val tail = decodeChunk(tailB64.getOrNull())
            val text = listOfNotNull(head?.first, tail?.first).joinToString("\n")
            val wasEncrypted = head?.second == true || tail?.second == true
            if (text.isNotBlank()) return Result.success(text to wasEncrypted)
        } else {
            val full = backend.executeShellCommand("base64 \"$path\" 2>/dev/null")
            val result = full.getOrNull()?.let { decodeChunk(it) }
            if (result != null) return Result.success(result)
        }

        return backend.readFile(path).map { text ->
            LogParser.decodeLogBytes(text.toByteArray(Charsets.ISO_8859_1))
        }
    }

    suspend fun getCurrentConfigFiles(): Result<List<String>> {
        return backend.listDirectory(TARGET_DIR)
    }

    suspend fun deleteConfigFiles(onProgress: (String) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        try {
            val targets = listOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini")
            var deleted = 0
            for (name in targets) {
                val path = "$TARGET_DIR/$name"
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
}
