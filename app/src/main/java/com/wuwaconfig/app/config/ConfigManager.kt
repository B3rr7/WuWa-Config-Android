package com.wuwaconfig.app.config

import android.content.Context
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.PUSH_RETRY_COUNT
import com.wuwaconfig.app.backend.retryIO
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.ConfigFile
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.model.PlayerProfile
import com.wuwaconfig.app.model.VerificationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * Facade that wires together the config subsystems and exposes the full
 * [ConfigManager] API used across the app:
 *  - [BackupStore]     — backup create/list/delete + Client.log persistence
 *  - [ProfileExtractor] — log read/decode, profile and battle-stats extraction
 *  - [HashMonitor]     — KuroConfigMonitor hash file management
 *  - deploy / restore / clean (kept here as the core config-apply responsibility)
 */
class ConfigManager(
    private val context: Context,
    private val backendProvider: () -> AccessBackend,
    private val backupDirPath: String? = null,
) {
    private var _backend: AccessBackend = backendProvider()
    private lateinit var _backupStore: BackupStore
    private lateinit var _profileExtractor: ProfileExtractor
    private lateinit var _hashMonitor: HashMonitor

    init {
        rebuild()
    }

    private fun rebuild() {
        _backupStore = BackupStore(context, _backend, backupDirPath)
        _profileExtractor = ProfileExtractor(context, _backend, _backupStore.backupDir, _backupStore.publicDir)
        _hashMonitor = HashMonitor(context, _backend)
    }

    private fun rebuildIfNeeded() {
        val b = backendProvider()
        if (b !== _backend) {
            _backend = b
            rebuild()
        }
    }

    private val backend: AccessBackend
        get() {
            rebuildIfNeeded()
            return _backend
        }
    private val backupStore: BackupStore
        get() {
            rebuildIfNeeded()
            return _backupStore
        }
    private val profileExtractor: ProfileExtractor
        get() {
            rebuildIfNeeded()
            return _profileExtractor
        }
    private val hashMonitor: HashMonitor
        get() {
            rebuildIfNeeded()
            return _hashMonitor
        }

    // ===== Deploy / restore (core config-apply responsibility) =====

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
                        pushWithRetry(name, tempFile.absolutePath, targetPath, onProgress)
                            .onFailure { throw it }
                        delay(50 + Random.nextLong(100))
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
                    pushWithRetry(fileName, tempFile.absolutePath, targetPath, onProgress)
                        .onFailure { throw it }
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
                        pushWithRetry(file.name, tempFile.absolutePath, targetPath, onProgress)
                            .onFailure { throw it }
                        delay(50 + Random.nextLong(100))
                    }
                    Result.success("$label restored successfully!")
                } finally {
                    tempDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun pushWithRetry(
        displayName: String,
        sourcePath: String,
        targetPath: String,
        onProgress: (String) -> Unit,
    ): Result<String> =
        retryIO(times = PUSH_RETRY_COUNT + 1, backoffMs = 0) {
            onProgress("Retrying $displayName...")
            backend.pushFile(sourcePath, targetPath).getOrThrow()
        }

    suspend fun readCurrentConfig(fileName: String): Result<String> {
        return backend.readFile("${GamePaths.TARGET_DIR}/$fileName")
    }

    fun cleanIniContent(
        original: String,
        fileName: String,
    ): String {
        if (original.isBlank()) return original
        if (fileName == "Engine.ini") {
            val result = StringBuilder()
            var inCoreSystem = false
            var foundCoreSystem = false
            for (line in original.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    val section = trimmed.removePrefix("[").removeSuffix("]")
                    inCoreSystem = section == "Core.System"
                    if (inCoreSystem) {
                        foundCoreSystem = true
                        result.appendLine(line)
                    }
                    continue
                }
                if (inCoreSystem) {
                    result.appendLine(line)
                }
            }
            if (!foundCoreSystem) return original
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
                    Result.success("All config files are already clean")
                }
            } catch (e: Exception) {
                LogRepository.add("ConfigManager: cleanConfigFiles failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    suspend fun deleteConfigFiles(fileNames: Set<String>): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (fileNames.isEmpty()) {
                    LogRepository.add("ConfigManager: no files selected for deletion")
                    return@withContext Result.success("No files selected for deletion")
                }
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
                    val result = backend.deleteFile(path)
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
                } else if (errors > 0) {
                    LogRepository.add("ConfigManager: $errors delete error(s)", LogLevel.ERROR)
                    Result.failure(Exception("$errors config file(s) failed to delete"))
                } else {
                    LogRepository.add("ConfigManager: no config files needed deletion", LogLevel.WARNING)
                    Result.success("No config files needed deletion")
                }
            } catch (e: Exception) {
                LogRepository.add("ConfigManager: deleteConfigFiles failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    // ===== Backup subsystem (delegated to BackupStore) =====

    suspend fun createBackup(
        name: String,
        type: String = "manual",
        selectedFiles: Set<String>? = null,
    ): Result<ConfigBackup> = backupStore.createBackup(name, type, selectedFiles)

    fun getLocalBackups(): List<ConfigBackup> = backupStore.getLocalBackups()

    fun deleteLocalBackup(backup: ConfigBackup) = backupStore.deleteLocalBackup(backup)

    suspend fun collectClientLog(onProgress: (String) -> Unit): Result<String> = profileExtractor.collectClientLog(onProgress)

    // ===== Profile / log subsystem (delegated to ProfileExtractor) =====

    suspend fun readClientLogContent(onProgress: (Int) -> Unit = {}): Result<String> = profileExtractor.readClientLogContent(onProgress)

    suspend fun readClientLogTextWithMetadata(onProgress: (Int) -> Unit = {}): Result<Pair<String, LogParser.DecodeResult>> = profileExtractor.readClientLogTextWithMetadata(onProgress)

    suspend fun readLatestBackupLogWithMetadata(onProgress: (Int) -> Unit = {}): Result<Pair<String, LogParser.DecodeResult>> = profileExtractor.readLatestBackupLogWithMetadata(onProgress)

    suspend fun verifyDeployedCvars(generatedCvars: Set<String>): Result<VerificationReport> = profileExtractor.verifyDeployedCvars(generatedCvars)

    suspend fun readProfile(): Result<PlayerProfile> = profileExtractor.readProfile()

    suspend fun readBattleStats(): Result<BattleStats> = profileExtractor.readBattleStats()

    suspend fun readFullClientLogWithMetadata(): Result<Pair<String, LogParser.DecodeResult>> = profileExtractor.readFullClientLogWithMetadata()

    suspend fun readFullLatestBackupLog(): Result<Pair<String, LogParser.DecodeResult>> = profileExtractor.readFullLatestBackupLog()

    // ===== Hash subsystem (delegated to HashMonitor) =====

    suspend fun refreshConfigHashes(incrementModifyCount: Boolean = false): Result<String> = hashMonitor.refreshConfigHashes(incrementModifyCount)

    suspend fun snapshotHashFile(): Result<HashMonitor.HashFileSnapshot> = hashMonitor.snapshotHashFile()

    suspend fun reconcileAfterModify(snapshot: HashMonitor.HashFileSnapshot?): Result<String> = hashMonitor.reconcileAfterModify(snapshot)

    suspend fun readConfigModifyCounts(): Result<List<com.wuwaconfig.app.model.ConfigHashInfo>> = hashMonitor.readConfigModifyCounts()
}
