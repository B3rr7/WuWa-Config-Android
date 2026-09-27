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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
    companion object {
        private val BACKUP_LOG_NAME_REGEX = Regex("""Client-backup-[A-Za-z0-9._-]+\.log""")

        /** Matches the `YYYY.MM.DD-HH.MM.SS` stamps the game embeds in backup names. */
        private val BACKUP_STAMP_REGEX = Regex("""(\d{4})\.(\d{2})\.(\d{2})-(\d{2})\.(\d{2})\.(\d{2})""")

        // BOTH databases name their table "LocalStorage". Verified on-device:
        //   Saved/LocalStorage/LocalStorage.db -> table "LocalStorage"
        //   Saved/DeviceSaved/DeviceStorage.db -> table "LocalStorage"  (12 KB, 1 table)
        // Parameterising this per database and naming the second one "DeviceStorage"
        // (after the FILE) made every DeviceStorage query throw "no such table", the
        // bare catch swallowed it, and language + all three version fields silently
        // came back null. The file name and the table name are unrelated here.
        internal const val LOCAL_STORAGE_TABLE = "LocalStorage"
        internal const val DEVICE_STORAGE_TABLE = "LocalStorage"

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

    suspend fun readClientLogTextWithMetadata(onProgress: (Int) -> Unit = {}): Result<Pair<String, LogParser.DecodeResult>> =
        withContext(Dispatchers.IO) {
            try {
                val logFilePath = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
                readRemoteLogText(logFilePath, onProgress)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Resolves the most recent backup log path via `ls -t` and validates the
     * filename before it is ever used as shell input. Shared by both backup-log
     * readers so the trust boundary can't drift between them.
     *
     * The name comes from remote `ls` output, so it is never trusted as shell input.
     * The check is anchored (`matches` on the basename, not `containsMatchIn` on the
     * full path) — an unanchored search would accept
     * "/tmp/evil/Client-backup-x.log;rm -rf /".
     */
    private suspend fun latestBackupLogPath(): Result<String> =
        withContext(Dispatchers.IO) {
            val listCmd = "ls -t ${shQuote(GamePaths.LOG_DIR)}/Client-backup-*.log 2>/dev/null | head -1"
            val result = backend.executeShellCommand(listCmd)
            val logPath =
                result.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                    ?: return@withContext Result.failure(Exception("No backup log found"))
            if (!BACKUP_LOG_NAME_REGEX.matches(logPath.substringAfterLast("/"))) {
                return@withContext Result.failure(
                    Exception("Unexpected backup log name: ${logPath.substringAfterLast("/").take(80)}"),
                )
            }
            Result.success(logPath)
        }

    suspend fun verifyDeployedCvars(generatedCvars: Set<String>): Result<VerificationReport> =
        withContext(Dispatchers.IO) {
            try {
                // The current Client.log alone is not enough: the game rotates it at
                // ~20 MB, so the newest session's CVar/battle/perf history usually lives
                // in the backups. Reading only the current log is what made verification
                // report 0 accepted CVars when the live log had just been truncated.
                val merged = readMergedClientLog()
                if (merged.isFailure) return@withContext Result.failure(merged.exceptionOrNull()!!)
                val (text, report) = merged.getOrThrow()
                LogRepository.add("verifyDeployedCvars: ${report.summary()}", LogLevel.INFO)
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
                // NO public/shared-storage copy.
                //
                // The DECRYPTED Client.log contains the player's UID, device
                // fingerprint, and — decisively — the Convene/gacha URL whose
                // fragment carries `record_id`, a bearer-style credential for the
                // gacha-history endpoint. Writing it to Downloads/WuWaConfig made
                // it readable by ANY installed app on API 26-29 (READ_EXTERNAL_STORAGE
                // is a normal, install-time permission there) and by every
                // All-Files-Access holder on API 30+. The app-private copy in
                // backupDir serves every in-app consumer, so the public copy bought
                // nothing except exposure.
                //
                // To share a log, use the explicit, user-initiated export in the Logs
                // screen (LogRepository.saveSnapshot) — and redact before doing so.
                LogRepository.add(
                    "ProfileExtractor: Client.log saved to app-private storage (not exported to shared storage)",
                    LogLevel.INFO,
                )
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
        // UUID, not currentTimeMillis(): two concurrent reads landing in the same
        // millisecond shared a path, and one `finally { delete() }` yanked the file
        // out from under the other mid-read.
        val localCopy = "$cacheDir/wuwa_log_copy_${UUID.randomUUID()}"

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
        latestBackupLogPath().fold(
            onSuccess = { path ->
                LogRepository.add("ConfigManager: reading full backup log: ${path.substringAfterLast("/")}")
                readRemoteLogToText(path)
            },
            onFailure = { Result.failure(it) },
        )

    // ── Multi-log reading ────────────────────────────────────────────────
    //
    // The game caps each Client.log at ~20 MB and rotates the old one to
    // Client-backup-<start>-<end>.log. A device inspected during this work held
    // 20 backups totalling ~194 MB, but readFullLatestBackupLog() takes
    // `ls -t … | head -1`, i.e. exactly ONE of them. Everything the game had
    // written in earlier sessions was silently discarded.

    /**
     * Every backup log, newest session first.
     *
     * Sorted by the timestamp embedded in the FILENAME, not by mtime: on the device
     * inspected, seven files shared an mtime of `2026-09-23 17:34` (a bulk restore
     * artifact) while their names spanned three weeks, so `ls -t` ordering is not
     * trustworthy. Names whose stamp cannot be parsed sort last.
     */
    suspend fun listBackupLogsNewestFirst(): List<String> =
        withContext(Dispatchers.IO) {
            val listed =
                backend.executeShellCommand(
                    "ls -1 ${shQuote(GamePaths.LOG_DIR)}/Client-backup-*.log 2>/dev/null",
                )
            val paths = listed.getOrNull().orEmpty().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            // The name came from remote `ls`, so it is never trusted as shell input.
            val safe = paths.filter { BACKUP_LOG_NAME_REGEX.matches(it.substringAfterLast("/")) }
            if (safe.size != paths.size) {
                LogRepository.add(
                    "ProfileExtractor: ignored ${paths.size - safe.size} backup log name(s) that did not match the expected pattern",
                    LogLevel.WARNING,
                )
            }
            safe.sortedByDescending { backupLogSortKey(it) }
        }

    /** Epoch millis of the session-start stamp in a backup filename; 0 when absent. */
    private fun backupLogSortKey(path: String): Long {
        val m = BACKUP_STAMP_REGEX.find(path.substringAfterLast("/")) ?: return 0L
        val g = m.groupValues
        val nums = IntArray(6) { g[it + 1].toIntOrNull() ?: return 0L }
        return runCatching {
            java.util.Calendar.getInstance().apply {
                clear()
                set(nums[0], nums[1] - 1, nums[2], nums[3], nums[4], nums[5])
            }.timeInMillis
        }.getOrDefault(0L)
    }

    /**
     * Reads at most [maxBytes] from the START of a remote log and decrypts it.
     *
     * The transform is a byte-wise XOR-LUT with no chaining, so a prefix decrypts
     * correctly on its own. That makes a head read meaningful and, unlike a full
     * read, it does not pull 20 MB through the shell for a file whose interesting
     * content is all in the first few hundred KB.
     *
     * The head size is deliberately kept under the binder budget: base64 inflates by
     * ~4/3, so [maxBytes] must stay below ShellUserService's MAX_BINDER_OUTPUT.
     */
    private suspend fun readRemoteLogHeadToText(
        path: String,
        maxBytes: Long,
    ): Result<Pair<String, LogParser.DecodeResult>> =
        withContext(Dispatchers.IO) {
            val quoted = shQuote(path)
            val cmd = "head -c $maxBytes $quoted 2>/dev/null | base64 -w0 2>/dev/null"
            val out = backend.executeShellCommand(cmd).getOrNull()?.trim().orEmpty()
            if (out.isEmpty()) return@withContext Result.failure(Exception("empty head read: $path"))
            val bytes =
                runCatching { android.util.Base64.decode(out, android.util.Base64.DEFAULT) }
                    .getOrElse { return@withContext Result.failure(Exception("base64 decode failed: $path")) }
            if (bytes.isEmpty()) return@withContext Result.failure(Exception("empty head read: $path"))
            val decoded = LogParser.decodeLogBytes(bytes)
            if (decoded.second != LogParser.DecodeResult.DECRYPTED) {
                LogRepository.add(
                    "ProfileExtractor: ${path.substringAfterLast("/")} head did not decrypt (${decoded.second}); skipped",
                    LogLevel.WARNING,
                )
            }
            Result.success(decoded)
        }

    /** Which logs went into a merged read, and what was skipped. */
    data class MergedLogReport(
        val used: List<String>,
        val skipped: List<String>,
        val bytesRead: Long,
    ) {
        fun summary(): String =
            "Merged ${used.size} log(s), ${
                "%.1f".format(bytesRead / 1024.0 / 1024.0)
            } MB" + if (skipped.isEmpty()) "" else " (skipped ${skipped.size})"
    }

    /**
     * Reads the current log plus the head of each backup, concatenated NEWEST FIRST.
     *
     * Order matters: LogParser.parseLog is first-match-wins, so putting the newest
     * session first makes each field resolve to the current value rather than the
     * oldest. The previous `readFullLatestBackupLog` merge appended the backup BEFORE
     * the current log ("$backupText\n$text"), which inverted exactly that.
     *
     * Each log is validated independently, so one undecryptable file degrades to a
     * skip instead of poisoning the whole analysis.
     */
    suspend fun readMergedClientLog(
        headBytesPerLog: Long = 256L * 1024,
        maxBackupLogs: Int = 8,
        totalBudgetBytes: Long = 8L * 1024 * 1024,
    ): Result<Pair<String, MergedLogReport>> =
        withContext(Dispatchers.IO) {
            val used = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            val parts = mutableListOf<String>()
            var bytes = 0L

            val currentPath = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
            val current = readRemoteLogToText(currentPath)
            if (current.isSuccess) {
                val text = current.getOrThrow().first
                if (text.isNotBlank()) {
                    parts += text
                    bytes += text.length
                    used += GamePaths.LOG_FILE_NAME
                }
            } else {
                skipped += "${GamePaths.LOG_FILE_NAME} (${current.exceptionOrNull()?.message})"
            }

            for (path in listBackupLogsNewestFirst()) {
                if (used.size - 1 >= maxBackupLogs) {
                    skipped += "${path.substringAfterLast("/")} (over the $maxBackupLogs backup limit)"
                    continue
                }
                if (bytes >= totalBudgetBytes) {
                    skipped += "${path.substringAfterLast("/")} (over the byte budget)"
                    continue
                }
                val head = readRemoteLogHeadToText(path, headBytesPerLog)
                if (head.isFailure) {
                    skipped += "${path.substringAfterLast("/")} (${head.exceptionOrNull()?.message})"
                    continue
                }
                val (text, _decode) = head.getOrThrow()
                if (!LogParser.looksLikeEngineLogText(text)) {
                    skipped += "${path.substringAfterLast("/")} (undecryptable)"
                    continue
                }
                parts += text
                bytes += text.length
                used += path.substringAfterLast("/")
            }

            if (parts.isEmpty()) {
                return@withContext Result.failure(Exception("No readable Client.log found"))
            }
            val report = MergedLogReport(used, skipped, bytes)
            LogRepository.add("ProfileExtractor: ${report.summary()} — newest first", LogLevel.INFO)
            Result.success(parts.joinToString("\n") to report)
        }

    suspend fun readProfile(onProgress: (Int) -> Unit = {}): Result<PlayerProfile> =
        withContext(Dispatchers.IO) {
            onProgress(5)
            val localDb = pullDb("LocalStorage.db")
            val devDb = pullDb("DeviceStorage.db")
            try {
                val uid = queryDb(localDb, LOCAL_STORAGE_TABLE, "RecentlyLoginUID")?.filter { it.isDigit() }
                val langRaw = queryDb(devDb, DEVICE_STORAGE_TABLE, "UseLanguage_en")

                val serverLevels = parseServerLevels(queryDb(localDb, LOCAL_STORAGE_TABLE, "SdkLevelData"))
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
                        lastLoginTime = formatTimestamp(cleanString(queryDb(localDb, LOCAL_STORAGE_TABLE, "LoginTime_$uidStr"))),
                        towerFloor = queryDb(localDb, LOCAL_STORAGE_TABLE, "AdventrueTower_$uidStr")?.toIntOrNull(),
                        weeklyRogueScore = queryDb(localDb, LOCAL_STORAGE_TABLE, "AdventrueWeeklyRogue_$uidStr")?.toIntOrNull(),
                        battlePassPurchased = queryDb(localDb, LOCAL_STORAGE_TABLE, "BattlePassPayButton_$uidStr")?.contains("1B") == true,
                        loopTowerSeason = queryDb(localDb, LOCAL_STORAGE_TABLE, "LoopTowerSeason_$uidStr")?.toIntOrNull(),
                        gameVersion = cleanString(queryDb(devDb, DEVICE_STORAGE_TABLE, "Version_Resource")),
                        patchVersion = cleanString(queryDb(devDb, DEVICE_STORAGE_TABLE, "PatchVersion")),
                        launcherVersion = cleanString(queryDb(devDb, DEVICE_STORAGE_TABLE, "Version_Launcher")),
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
                        onProgress(10)
                        val decoded = readRemoteLogToText("${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}", onProgress).getOrThrow()
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

    suspend fun readBattleStats(onProgress: (Int) -> Unit = {}): Result<BattleStats> =
        withContext(Dispatchers.IO) {
            val path = "${GamePaths.LOG_DIR}/${GamePaths.LOG_FILE_NAME}"
            try {
                val sizeRaw = backend.executeShellCommand("wc -c < ${shQuote(path)} 2>/dev/null").getOrDefault("0")
                val fileSize = sizeRaw.trim().toLongOrNull() ?: 0L
                if (fileSize <= 0L) return@withContext Result.failure(Exception("Client.log is empty"))

                val cacheDir = context.cacheDir.absolutePath
                // UUID, not currentTimeMillis(): two concurrent reads landing in the
                // same millisecond shared a path and one deleted the other's file.
                val localCopy = "$cacheDir/wuwa_battlestats_${UUID.randomUUID()}"

                onProgress(10)
                // The copy holds the game's DECRYPTED log, so delete it in a finally on
                // EVERY path. Deleting eagerly after readBytes() (the old shape) left the
                // plaintext copy behind in cacheDir whenever readBytes() itself threw or
                // the coroutine was cancelled. The sibling readRemoteLogToText() already
                // used the finally shape.
                val rawBytes =
                    try {
                        backend.copyFile(path, localCopy).getOrThrow()
                        val localFile = File(localCopy)
                        if (!localFile.exists() || localFile.length() == 0L) {
                            throw Exception("Failed to copy log file")
                        }
                        localFile.readBytes()
                    } finally {
                        runCatching { File(localCopy).delete() }
                    }

                onProgress(50)
                val (text, _) = LogParser.decodeLogBytes(rawBytes)
                onProgress(80)
                val lines = text.lines()

                // parseBattleStatsLines is stateful (the running stamina counter
                // depends on the order of lines), so it must run single-threaded.
                // Parallel chunking restarted the counter per chunk and produced a
                // result that varied by core count.
                val stats = LogParser.parseBattleStatsLines(lines)
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
            // Same skip-set as extractCvarNames / deduplicateIniText / parseCvarEntries:
            // ";" comments, "#" comments, "//" comments and section headers.
            if (trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("[")) return@count false
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
        } catch (e: CancellationException) {
            // Structured-concurrency: CancellationException extends IllegalStateException
            // extends Exception, so the bare catch below would swallow it.
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * SQLite schema notes (the two databases are NOT interchangeable):
     *  - `LocalStorage.db` (…/Client/LocalStorage/) holds a `LocalStorage` table
     *    with a (key, value) key/value store: RecentlyLoginUID, SdkLevelData,
     *    LoginTime_<uid>, AdventrueTower_<uid>, … — i.e. every game-side key.
     *  - `DeviceStorage.db` (…/Client/DeviceSaved/) holds a `DeviceStorage` table
     *    with a (key, value) key/value store for launcher/device bookkeeping:
     *    UseLanguage_en, Version_Resource, PatchVersion, Version_Launcher.
     * Querying the wrong table name makes SQLite throw, which the old
     * hard-coded "SELECT … FROM LocalStorage" silently swallowed — language came
     * back as "—" and all three version fields as null.
     */
    private fun queryDb(
        db: SQLiteDatabase?,
        table: String,
        key: String,
    ): String? {
        if (db == null) return null
        return try {
            db.rawQuery("SELECT value FROM $table WHERE key=?", arrayOf(key)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: CancellationException) {
            // Structured-concurrency: CancellationException extends IllegalStateException
            // extends Exception, so the bare catch below would swallow it.
            throw e
        } catch (_: Exception) {
            LogRepository.add("ProfileExtractor: queryDb($table, $key) failed", LogLevel.WARNING)
            null
        }
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

/**
 * Extracts (region, level) pairs from the game's `SdkLevelData` blob.
 *
 * The real payload is a UE Map serialisation, verified on-device:
 *
 *   {"___MetaType___":"___Map___","Content":[["504796016",[{"Region":"Asia","Level":80}]]]}
 *
 * A previous version blanked out every character nested deeper than one brace
 * level to defend against a stray "Level" key desynchronising an ordinal zip. It
 * ignored `[` / `]` entirely, so the server object inside the Content ARRAY went
 * to brace-depth 2 and its "Region" and "Level" were blanked out — the function
 * returned an empty list for every real profile, taking PlayerProfile.server and
 * playerLevel with it (both come from the first pair).
 *
 * Pairing on adjacency instead of depth fixes that and is strictly safer than the
 * ordinal zip it replaced: a "Level" that belongs to some other object simply has
 * no adjacent "Region" and is ignored, rather than shifting every later pair.
 */
internal fun parseServerLevels(json: String?): List<Pair<String, Int>> {
    if (json.isNullOrBlank()) return emptyList()
    val results = mutableListOf<Pair<String, Int>>()
    try {
        // Both key orders, whitespace-tolerant. The separators are restricted to
        // "structural" characters (brace, bracket, comma, whitespace) so a Region
        // and a Level from DIFFERENT objects cannot be paired just because some
        // other text happened to sit between them.
        val region = """"Region"\s*:\s*"([^"]*)"""".toRegex()
        val level = """"Level"\s*:\s*(\d+)""".toRegex()
        val regionFirst =
            Regex(""""Region"\s*:\s*"([^"]*)"[\s,\]]{0,8}"Level"\s*:\s*(\d+)""")
        val levelFirst =
            Regex(""""Level"\s*:\s*(\d+)[\s,\]]{0,8}"Region"\s*:\s*"([^"]*)"""")

        var consumedTo = -1
        for (m in regionFirst.findAll(json)) {
            val r = m.groupValues[1]
            val l = m.groupValues[2].toIntOrNull() ?: continue
            if (r.isBlank()) continue
            results.add(r to l)
            consumedTo = maxOf(consumedTo, m.range.last)
        }
        for (m in levelFirst.findAll(json)) {
            if (m.range.first <= consumedTo) continue
            val l = m.groupValues[1].toIntOrNull() ?: continue
            val r = m.groupValues[2]
            if (r.isBlank()) continue
            results.add(r to l)
        }
        // Documented fallbacks: kept so an unexpected shape still yields something
        // rather than silently nothing.
        if (results.isEmpty()) {
            val regions = region.findAll(json).map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()
            val levels = level.findAll(json).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
            val n = minOf(regions.size, levels.size)
            for (i in 0 until n) results.add(regions[i] to levels[i])
        }
    } catch (_: Exception) {
        return emptyList()
    }
    return results
}
