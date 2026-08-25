package com.wuwaconfig.app.config

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import android.util.Log
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.shQuote
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.model.PlayerProfile
import com.wuwaconfig.app.model.VerificationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads and decodes the device Client.log (and backups), and extracts the
 * player profile and battle stats from it / the game databases.
 */
class ProfileExtractor(
    private val context: Context,
    private val backend: AccessBackend,
    private val backupDir: File,
    private val publicDir: File,
) {
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

    suspend fun readClientLogTextWithMetadata(onProgress: (Int) -> Unit = {}): Result<Pair<String, LogParser.DecodeResult>> =
        withContext(Dispatchers.IO) {
            try {
                val logFilePath = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
                readRemoteLogText(logFilePath, onProgress)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun readLatestBackupLogWithMetadata(onProgress: (Int) -> Unit = {}): Result<Pair<String, LogParser.DecodeResult>> =
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
                // Public copy is best-effort: private persistence is what matters.
                if (LogRepository.publicBaseDir() != null) {
                    try {
                        val publicFile = File(publicDir, "Client.log")
                        publicFile.writeText(content)
                        onProgress("Also saved to ${publicFile.absolutePath} (public)")
                    } catch (e: Exception) {
                        LogRepository.add("Public Client.log copy skipped: ${e.message}", LogLevel.WARNING)
                    }
                } else {
                    LogRepository.add("Public Client.log copy skipped: missing All-Files-Access", LogLevel.WARNING)
                }
                onProgress("Saved to ${savedFile.absolutePath}")
                Result.success(savedFile.absolutePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun readRemoteLogText(
        path: String,
        onProgress: (Int) -> Unit = {},
    ): Result<Pair<String, LogParser.DecodeResult>> {
        onProgress(5)
        val quoted = shQuote(path)
        val existsResult = backend.executeShellCommand("test -f $quoted 2>/dev/null && echo 1 || echo 0")
        val fileExists = existsResult.getOrNull()?.trim() == "1"
        if (!fileExists) return Result.failure(Exception("Client.log not found at: $path"))

        val sizeResult = backend.executeShellCommand("wc -c < $quoted 2>/dev/null")
        val fileSize = sizeResult.getOrNull()?.trim()?.toLongOrNull() ?: 0L
        if (fileSize <= 0L) return Result.failure(Exception("Client.log is empty"))

        return readRemoteLogToText(path, onProgress)
    }

    private suspend fun readRemoteLogToText(
        path: String,
        onProgress: (Int) -> Unit = {},
    ): Result<Pair<String, LogParser.DecodeResult>> {
        val cacheDir = context.cacheDir.absolutePath
        val localCopy = "$cacheDir/wuwa_log_copy_${System.currentTimeMillis()}"

        try {
            onProgress(10)
            backend.copyFile(path, localCopy).getOrThrow()

            onProgress(50)
            val localFile = File(localCopy)
            if (!localFile.exists() || localFile.length() == 0L) {
                throw Exception("Failed to copy log file")
            }

            val rawBytes = localFile.readBytes()
            onProgress(80)

            val (text, decodeResult) = LogParser.decodeLogBytes(rawBytes)
            onProgress(95)
            return Result.success(text to decodeResult)
        } catch (e: Exception) {
            Log.w("ProfileExtractor", "readRemoteLogToText failed: ${e.message}")
            return Result.failure(e)
        } finally {
            try {
                File(localCopy).delete()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun readFullClientLogWithMetadata(): Result<Pair<String, LogParser.DecodeResult>> = readRemoteLogToText("${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}")

    suspend fun readFullLatestBackupLog(): Result<Pair<String, LogParser.DecodeResult>> =
        runCatching {
            val listCmd = "ls -t ${shQuote(GamePaths.LOG_DIR)}/Client-backup-*.log 2>/dev/null | head -1"
            val result = backend.executeShellCommand(listCmd)
            val logPath =
                result.getOrNull()?.trim()
                    ?: throw Exception("No backup log found")
            // The filename comes from remote ls output — never trust it as shell input.
            if (!Regex("""Client-backup-[A-Za-z0-9._-]+\.log$""").containsMatchIn(logPath)) {
                throw Exception("Unexpected backup log name: ${logPath.substringAfterLast("/").take(80)}")
            }
            LogRepository.add("ConfigManager: reading full backup log: ${logPath.substringAfterLast("/")}")
            readRemoteLogToText(logPath).getOrThrow()
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

                val uidStr = uid ?: ""

                val baseProfile =
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

                val deviceInfo =
                    runCatching {
                        val decoded = readRemoteLogToText("${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}").getOrThrow()
                        LogParser.parseLog(decoded.first)
                    }.getOrNull()

                val profile =
                    if (deviceInfo != null) {
                        baseProfile.copy(
                            gpu = deviceInfo.gpu,
                            socName = deviceInfo.socName,
                            ramMb = deviceInfo.ramMb,
                            androidVersion = deviceInfo.androidVersion,
                            resolution = deviceInfo.resolution,
                            renderApi = deviceInfo.gameApi ?: deviceInfo.api,
                            vulkanStatus = deviceInfo.vulkanStatus,
                            fpsActual = deviceInfo.fpsActual,
                            fpsCap = deviceInfo.fpsCap,
                            screenPct = deviceInfo.screenPct,
                            shadowQ = deviceInfo.shadowQ,
                            qualityMode = deviceInfo.qualityMode,
                            thermalEvents = deviceInfo.thermalEvents,
                            gpuOom = deviceInfo.gpuOom,
                            dropFrames = deviceInfo.dropFrames,
                            textureErrors = deviceInfo.textureErrors,
                            forbiddenCvars = deviceInfo.forbiddenCvars,
                        )
                    } else {
                        baseProfile
                    }
                Result.success(profile)
            } catch (e: Exception) {
                Log.w("ProfileExtractor", "readProfile failed: ${e.message}")
                Result.failure(e)
            } finally {
                localDb?.close()
                devDb?.close()
                File(context.cacheDir, "profile_LocalStorage.db").delete()
                File(context.cacheDir, "profile_DeviceStorage.db").delete()
            }
        }

    suspend fun readBattleStats(): Result<BattleStats> =
        withContext(Dispatchers.IO) {
            val path = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
            try {
                val sizeRaw = backend.executeShellCommand("wc -c < \"$path\" 2>/dev/null").getOrDefault("0")
                val fileSize = sizeRaw.trim().toLongOrNull() ?: 0L
                if (fileSize <= 0L) return@withContext Result.failure(Exception("Client.log is empty"))

                val cacheDir = context.cacheDir.absolutePath
                val localCopy = "$cacheDir/wuwa_battlestats_${System.currentTimeMillis()}"

                backend.copyFile(path, localCopy).getOrThrow()

                val localFile = File(localCopy)
                if (!localFile.exists() || localFile.length() == 0L) {
                    throw Exception("Failed to copy log file")
                }

                val rawBytes = localFile.readBytes()
                try {
                    localFile.delete()
                } catch (_: Exception) {
                }

                val (text, _) = LogParser.decodeLogBytes(rawBytes)
                val lines = text.lines()

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
                return@withContext Result.success(stats.copy(logSizeBytes = fileSize))
            } catch (e: Exception) {
                Log.w("ProfileExtractor", "readBattleStats failed: ${e.message}")
                return@withContext Result.failure(e)
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
            // Accept either unix-seconds or unix-milliseconds; a value past ~year 2286
            // in seconds (1e10) is effectively always milliseconds.
            val millis = if (seconds >= 1e10) seconds else seconds * 1000
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            return sdf.format(java.util.Date(millis.toLong().coerceAtLeast(0L)))
        }
        return ts.take(19)
    }
}
