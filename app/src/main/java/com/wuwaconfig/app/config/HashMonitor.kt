package com.wuwaconfig.app.config

import android.content.Context
import android.util.Log
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.PUSH_RETRY_COUNT
import com.wuwaconfig.app.backend.shQuote
import com.wuwaconfig.app.model.ConfigHashInfo
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns the KuroConfigMonitor hash file: computing per-file MD5s, atomically
 * patching/verifying the hash file, and detecting concurrent game writes.
 */
class HashMonitor(
    private val context: Context,
    private val backend: AccessBackend,
) {
    private val hashMutex = Mutex()

    data class HashFileSnapshot(
        val content: String,
        val timestamp: Long,
    )

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

    suspend fun refreshConfigHashes(incrementModifyCount: Boolean = false): Result<String> {
        if (!WuWaConfigApp.instance.hashMonitorEnabled.value) {
            LogRepository.add("ConfigManager: HashMonitor disabled — skipping hash sync", LogLevel.WARNING)
            return Result.success("HashMonitor disabled — skipped")
        }
        return hashMutex.withLock {
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
                        val baseCount = (prevCount?.coerceIn(0, 8)) ?: 0
                        val displayCount = if (incrementModifyCount) minOf(baseCount + 1, 8) else baseCount
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
    }

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

            refreshConfigHashes(incrementModifyCount = !gameTouched)
        }
    }

    suspend fun readConfigModifyCounts(): Result<List<ConfigHashInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val content = backend.readFile(GamePaths.HASH_MONITOR_PATH).getOrDefault("")
                if (content.isBlank()) return@withContext Result.failure(Exception("No hash file on device"))
                val monitoredNames = GamePaths.MONITORED_FILES.toSet()
                val results = mutableListOf<ConfigHashInfo>()
                var currentFile = ""
                for (line in content.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        currentFile = trimmed.removePrefix("[").removeSuffix("]")
                    } else if (trimmed.startsWith("ModifyCount=") && currentFile.isNotEmpty() && currentFile in monitoredNames) {
                        val count = trimmed.removePrefix("ModifyCount=").toIntOrNull() ?: 0
                        results.add(ConfigHashInfo(currentFile, count))
                    }
                }
                if (results.isEmpty()) return@withContext Result.failure(Exception("No modify counts found"))
                Result.success(results)
            } catch (e: Exception) {
                Log.w("ConfigManager", "Failed to read modify counts: ${e.message}")
                Result.failure(e)
            }
        }
}
