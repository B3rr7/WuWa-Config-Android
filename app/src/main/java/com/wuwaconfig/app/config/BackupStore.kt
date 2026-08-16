package com.wuwaconfig.app.config

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.ConfigFile
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Owns backup persistence: the private/public backup directories, creating,
 * listing and deleting [ConfigBackup]s, and persisting the current Client.log.
 */
class BackupStore(
    context: Context,
    private val backend: AccessBackend,
    backupDirPath: String? = null,
) {
    private val gson = Gson()

    val backupDir: File =
        File(backupDirPath ?: File(context.filesDir, "backups").absolutePath).also {
            if (!it.mkdirs() && !it.exists()) {
                LogRepository.add("ConfigManager: failed to create backup dir: ${it.absolutePath}", LogLevel.WARNING)
            }
        }
    val publicDir: File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WuWaConfig").also {
            if (!it.mkdirs() && !it.exists()) {
                LogRepository.add("ConfigManager: failed to create public dir: ${it.absolutePath}", LogLevel.WARNING)
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
                Log.d("BackupStore", "createBackup: listing ${GamePaths.TARGET_DIR}")
                val files = backend.listDirectory(GamePaths.TARGET_DIR).getOrThrow()
                Log.d("BackupStore", "createBackup: listed ${files.size} files: $files")
                val allIniNames = setOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini", "Scalability.ini", "Hardware.ini")
                val targetNames = selectedFiles ?: allIniNames
                val configFiles =
                    files.filter { it in targetNames && it in allIniNames }.map { fileName ->
                        Log.d("BackupStore", "createBackup: reading $fileName")
                        val content = backend.readFile("${GamePaths.TARGET_DIR}/$fileName").getOrDefault("")
                        Log.d("BackupStore", "createBackup: read $fileName (${content.length} chars)")
                        ConfigFile(name = fileName, content = content)
                    }
                LogRepository.add("ConfigManager: backup read ${configFiles.size} config files")
                Log.d("BackupStore", "createBackup: saving backup to $backupDir")
                val backup = ConfigBackup(name = name, files = configFiles, type = type)
                File(backupDir, "${backup.id}.json").writeText(gson.toJson(backup))
                val publicBackupDir = File(File(publicDir, "Backups"), sanitizeDirName(name)).also { it.mkdirs() }
                configFiles.forEach { f -> File(publicBackupDir, f.name).writeText(f.content) }
                Log.d("BackupStore", "createBackup: SUCCESS")
                LogRepository.add("ConfigManager: backup '$name' created", LogLevel.SUCCESS)
                Result.success(backup)
            } catch (e: Exception) {
                Log.e("BackupStore", "createBackup FAILED: ${e.message}", e)
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
            } else {
                emptyList()
            }

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
                                id = UUID.nameUUIDFromBytes(dir.absolutePath.toByteArray()).toString(),
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
            } else {
                emptyList()
            }

        return (privateBackups + publicBackups).sortedByDescending { it.timestamp }
    }

    fun deleteLocalBackup(backup: ConfigBackup) {
        val file = File(backupDir, "${backup.id}.json")
        if (file.exists()) file.delete()
        val pubDir = File(File(publicDir, "Backups"), sanitizeDirName(backup.name))
        if (pubDir.exists()) pubDir.deleteRecursively()
    }

    private fun sanitizeDirName(name: String): String = name.replace(Regex("""[<>:"/\\|?*]"""), "_").take(100)
}
