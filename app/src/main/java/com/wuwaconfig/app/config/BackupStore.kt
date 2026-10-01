package com.wuwaconfig.app.config

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.ConfigFile
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.util.writeAtomic
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
                val allIniNames = GamePaths.MONITORED_FILE_SET
                val targetNames = selectedFiles ?: allIniNames
                val configFiles =
                    files.filter { it in targetNames && it in allIniNames }.mapNotNull { fileName ->
                        Log.d("BackupStore", "createBackup: reading $fileName")
                        val content = backend.readFile("${GamePaths.TARGET_DIR}/$fileName").getOrNull()
                        if (content == null) {
                            // A single unreadable file must not abort the whole backup.
                            LogRepository.add("ConfigManager: skipped unreadable $fileName", LogLevel.WARNING)
                            null
                        } else {
                            Log.d("BackupStore", "createBackup: read $fileName (${content.length} chars)")
                            ConfigFile(name = fileName, content = content)
                        }
                    }
                LogRepository.add("ConfigManager: backup read ${configFiles.size} config files")
                Log.d("BackupStore", "createBackup: saving backup to $backupDir")
                val backup = ConfigBackup(name = name, files = configFiles, type = type)
                File(backupDir, "${backup.id}.json").writeAtomic(gson.toJson(backup))
                exportPublicCopy(backup, configFiles)
                Log.d("BackupStore", "createBackup: SUCCESS")
                LogRepository.add("ConfigManager: backup '$name' created", LogLevel.SUCCESS)
                Result.success(backup)
            } catch (e: Exception) {
                Log.e("BackupStore", "createBackup FAILED: ${e.message}", e)
                LogRepository.add("ConfigManager: createBackup failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    fun getLocalBackups(): List<ConfigBackup> = listBackups(backupDir, publicDir, gson)

    /**
     * Best-effort export of the backup into Downloads. The private copy is the
     * source of truth and is already persisted when this runs, so a failure here
     * (missing All-Files-Access, full disk) must not fail the whole operation.
     */
    private fun exportPublicCopy(
        backup: ConfigBackup,
        configFiles: List<ConfigFile>,
    ) {
        if (!canWritePublicStorage()) {
            LogRepository.add("Public backup skipped: missing All-Files-Access permission", LogLevel.WARNING)
            return
        }
        try {
            val publicBackupDir = File(File(publicDir, "Backups"), publicDirName(backup)).also { it.mkdirs() }
            configFiles.forEach { f -> File(publicBackupDir, f.name).writeText(f.content) }
        } catch (e: Exception) {
            LogRepository.add("Public backup copy failed (private backup kept): ${e.message}", LogLevel.WARNING)
        }
    }

    private fun canWritePublicStorage(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun deleteLocalBackup(backup: ConfigBackup) {
        val file = File(backupDir, "${backup.id}.json")
        if (file.exists()) file.delete()
        if (!canWritePublicStorage()) return
        val publicBackupsDir = File(publicDir, "Backups")
        // Exact id-suffixed dir, plus the legacy unsuffixed dir from versions
        // before ids were appended to disambiguate sanitized-name collisions.
        File(publicBackupsDir, publicDirName(backup)).deleteRecursively()
        File(publicBackupsDir, sanitizeDirName(backup.name)).deleteRecursively()
    }

    /** Id-suffixed so distinct names sanitizing to the same string stay distinct. */
    private fun publicDirName(backup: ConfigBackup): String = Companion.sanitizeDirName(backup.name) + "_" + backup.id.take(8)

    companion object {
        /**
         * Lists backups from the private store and the public mirror, newest first.
         *
         * Extracted from [getLocalBackups] so the ordering and the de-duplication
         * can be tested. That logic was previously only reachable through a
         * constructed BackupStore, which needs a Context and an AccessBackend — and
         * the public half additionally needs `Environment`, which is not stubbed.
         * The listing itself is pure `File` work, so it was the one part of this
         * class that could be pinned without a device.
         *
         * Two rules matter and are easy to get wrong:
         *
         *  - **Ordering is by `timestamp`, descending.** The private JSONs carry a
         *    real timestamp; the public "legacy" dirs fall back to the directory's
         *    mtime, which is why a bulk restore can interleave them.
         *  - **A public copy of a private backup is not listed again.** They share
         *    the backup's data, so listing both shows one backup twice. Both the
         *    id-suffixed directory `exportPublicCopy` creates and the unsuffixed
         *    legacy layout are matched, because older versions wrote the latter.
         *
         * Corrupt JSON is skipped rather than failing the listing: one truncated
         * file must not hide every other backup.
         */
        internal fun listBackups(
            backupDir: File,
            publicDir: File,
            gson: Gson = Gson(),
        ): List<ConfigBackup> {
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
            val privateDirNames =
                privateBackups
                    .flatMap { listOf(sanitizeDirName(it.name), sanitizeDirName(it.name) + "_" + it.id.take(8)) }
                    .toSet()
            val publicBackupsDir = File(publicDir, "Backups")
            val publicBackups =
                if (publicBackupsDir.exists()) {
                    publicBackupsDir.listFiles()
                        ?.filter { it.isDirectory }
                        ?.filter { dir -> dir.listFiles()?.any { f -> f.extension == "ini" } == true }
                        ?.filter { dir -> dir.name !in privateNames && dir.name !in privateDirNames }
                        ?.mapNotNull { dir ->
                            try {
                                val iniFiles =
                                    dir.listFiles()
                                        ?.filter { it.extension == "ini" }
                                        ?.sortedBy { it.name }
                                        // Per file, not per directory: the private half
                                        // above already tolerates one unreadable JSON,
                                        // and a single bad INI (a directory named
                                        // "x.ini", a permission denial) must not drop
                                        // the whole backup from the listing.
                                        ?.mapNotNull { f ->
                                            try {
                                                ConfigFile(name = f.name, content = f.readText())
                                            } catch (_: Exception) {
                                                null
                                            }
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

        /**
         * The de-duplication key for a backup's public directory.
         *
         * Lives in the companion rather than as a member so it is callable without
         * constructing a BackupStore — which needs a Context and an AccessBackend.
         * That matters because it is half of the rule that keeps a backup from
         * being listed twice: [listBackups] matches a public directory against
         * both this and the unsuffixed form, so the sanitisation has to be pinned
         * directly rather than only observed through the listing.
         */
        internal fun sanitizeDirName(name: String): String = name.replace(Regex("""[<>:"/\\|?*]"""), "_").take(100)
    }
}
