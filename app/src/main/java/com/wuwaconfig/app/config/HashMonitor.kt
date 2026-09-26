package com.wuwaconfig.app.config

import android.content.Context
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.PUSH_RETRY_COUNT
import com.wuwaconfig.app.backend.SafBackend
import com.wuwaconfig.app.backend.computeMd5
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
    // Injected so the toggle can be stubbed in unit tests without standing up
    // WuWaConfigApp.instance (a lateinit that is null in a headless process).
    private val hashMonitorEnabled: () -> Boolean = { WuWaConfigApp.instance.hashMonitorEnabled.value },
) {
    // A single app-wide mutex serializes all hash-file writes. ConfigManager
    // instances are created per-ViewModel, but every instance stages to the
    // same device path, so the lock must be shared or concurrent deploys
    // (deploy + INI-edit save) can push the wrong content.
    companion object {
        private val hashMutex = Mutex()

        // Single owner of the "[*.ini]" section-header pattern. IniHashUtil.extractHash
        // used to keep a byte-identical private copy, so a fix to one silently
        // diverged from the other.
        internal val HASH_SECTION_REGEX = Regex("^\\[[A-Za-z0-9_\\-]+\\.ini\\]$", RegexOption.IGNORE_CASE)
    }

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
        val hash = computeMd5(bytes)
        LogRepository.add("ConfigManager: computed hash for $name = $hash (${bytes.size} bytes)")
        return Result.success(hash)
    }

    suspend fun refreshConfigHashes(incrementModifyCount: Boolean = false): Result<String> {
        if (!hashMonitorEnabled()) {
            LogRepository.add("ConfigManager: HashMonitor disabled — skipping hash sync", LogLevel.WARNING)
            // Success, but unambiguous: nothing was written and the drift detector is
            // OFF, so callers must not read this as "hashes verified in sync".
            return Result.success("Hash sync NOT performed: HashMonitor is disabled (drift detection is off)")
        }
        if (backend is SafBackend) {
            // SAF cannot run `mv` (no shell), so the hash file is never written here.
            // Report it as a FAILURE, not a success: every caller treats isSuccess as
            // "hashes are in sync", so returning success here told the deploy pipeline
            // the drift detector is live when nothing was ever written.
            val message = "Hash sync unavailable on the SAF backend (no shell `mv` to replace the hash file atomically)"
            LogRepository.add("ConfigManager: $message", LogLevel.WARNING)
            return Result.failure(Exception(message))
        }
        return hashMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    LogRepository.add("ConfigManager: refreshing config hashes")

                    // HASH_MONITOR_PATH lives under .../Client/Client/Config/Kuro/, which
                    // is a DIFFERENT directory from GamePaths.TARGET_DIR (.../Saved/Config/
                    // Android) — the only directory ConfigManager ever mkdir -p's. On a
                    // fresh install Config/Kuro/ does not exist, every push fails all
                    // retries, and the whole hash-monitor feature is silently dead.
                    val hashDir = GamePaths.HASH_MONITOR_PATH.substringBeforeLast("/", "")
                    backend.ensureDirectoryExists(hashDir).getOrThrow()

                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

                    val existingHashContent = backend.readFile(GamePaths.HASH_MONITOR_PATH).getOrDefault("")
                    val existingLines = existingHashContent.lines().toMutableList()
                    val hasExistingContent = existingLines.any { it.trim().startsWith("[") }

                    val updates = mutableMapOf<String, Map<String, String>>()
                    for (name in GamePaths.MONITORED_FILES) {
                        val hashResult = computeIniHash(name)
                        // An empty `Hash=` sentinel would be persisted as a real value
                        // and permanently mark the file as drifted (every later sync
                        // compares "" != actualHash). Abort the whole refresh instead.
                        if (hashResult.isFailure) {
                            LogRepository.add(
                                "ConfigManager: hash computation FAILED for $name — aborting refresh, hash file left untouched",
                                LogLevel.ERROR,
                            )
                            val cause = hashResult.exceptionOrNull() ?: Exception("hash computation failed for $name")
                            return@withContext Result.failure(Exception("Config hash refresh aborted: cannot read $name: ${cause.message}"))
                        }
                        val hash = hashResult.getOrThrow()

                        var prevCount: Int? = null
                        var prevTime = ""
                        var inSection = false
                        for (line in existingLines) {
                            val t = line.trim()
                            if (t.equals("[$name]", ignoreCase = true)) {
                                inSection = true
                                continue
                            }
                            if (inSection && t.matches(HASH_SECTION_REGEX)) break
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
                    // Keyed by BARE KEY, not by the full "key=value" line: the hash
                    // file is rewritten by the game and can contain two differing
                    // `Hash=` lines under the same section, which line-dedup keeps.
                    val seenKeys = mutableSetOf<String>()

                    fun flushPendingSection(name: String) {
                        val patch = updates.remove(name) ?: return
                        for (lineKey in listOf("Hash", "ModifyCount", "LastModifiedTime")) {
                            val value = patch[lineKey] ?: continue
                            val newLine = "$lineKey=$value"
                            // Bare key, so flushPendingSection and dedupLine share one
                            // namespace (cleared per section by the header branch).
                            if (seenKeys.add(lineKey)) patchedLines.add(newLine)
                        }
                    }

                    fun dedupLine(trimmed: String): Boolean {
                        if (trimmed.startsWith("[") && trimmed.endsWith("]")) return false
                        val eq = trimmed.indexOf('=')
                        if (eq <= 0) return false
                        val key = trimmed.substring(0, eq).trim()
                        val isDuplicate = key in listOf("Hash", "ModifyCount", "LastModifiedTime") && !seenKeys.add(key)
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
                                        // Same bare-key namespace as dedupLine /
                                        // flushPendingSection: a rewritten `Hash=` must
                                        // claim the "Hash" key so a second one in the
                                        // same section is recognised as a duplicate.
                                        if (seenKeys.add(key)) patchedLines.add("$indent$key=$replacement")
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
                        // No existing content — build from scratch (first-time).
                        // Reuse the hashes already computed by the scan loop above
                        // (updates map) instead of re-reading every file from the
                        // device a second time. Each computeIniHash issues a
                        // readFileBytes over the active backend (ADB/Shizuku/Root),
                        // so the redundant pass was a double device read per file —
                        // the "double backup" bug.
                        for (name in GamePaths.MONITORED_FILES) {
                            val patch = updates[name] ?: continue
                            patchedLines.add("[$name]")
                            patchedLines.add("Hash=${patch["Hash"] ?: ""}")
                            patchedLines.add("ModifyCount=${patch["ModifyCount"] ?: "0"}")
                            patchedLines.add("LastModifiedTime=${patch["LastModifiedTime"] ?: now}")
                            patchedLines.add("")
                        }
                    }

                    val newContent = patchedLines.joinToString("\n").trimEnd() + "\n"
                    // Unique temp name so a retry or a concurrent (mutex-serialized)
                    // refresh can never clobber another's staging file.
                    val tempFile = File(context.cacheDir, "KuroConfigMonitor.hash.${System.nanoTime()}")
                    val hashTempPath = GamePaths.HASH_MONITOR_PATH + ".new"
                    try {
                        tempFile.writeText(newContent)
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

                        val verifyResult = backend.readFile(GamePaths.HASH_MONITOR_PATH)
                        if (verifyResult.isSuccess) {
                            val stored = verifyResult.getOrThrow().trim()
                            if (stored == newContent.trim()) {
                                LogRepository.add("ConfigManager: hashes refreshed and verified", LogLevel.SUCCESS)
                                Result.success("Config hashes synced & verified")
                            } else {
                                LogRepository.add("ConfigManager: hash verify MISMATCH", LogLevel.ERROR)
                                Result.failure(Exception("Hash verify MISMATCH — config hashes may be corrupt"))
                            }
                        } else {
                            LogRepository.add("ConfigManager: hash verify skipped - ${verifyResult.exceptionOrNull()?.message}", LogLevel.WARNING)
                            Result.success("Config hashes synced (verify skipped)")
                        }
                    } finally {
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    LogRepository.add("ConfigManager: refreshConfigHashes failed: ${e.message}", LogLevel.ERROR)
                    Result.failure(e)
                }
            }
        }
    }

    // Snapshot / count reads take the SAME app-wide mutex that refreshConfigHashes
    // holds, so they can never observe the hash file mid-replacement. The mutex is
    // never held across refreshConfigHashes() itself (reconcileAfterModify does
    // exactly that) — the unlocked read below always completes before the locked
    // refresh starts.
    suspend fun snapshotHashFile(): Result<HashFileSnapshot> =
        hashMutex.withLock {
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
        hashMutex.withLock {
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
                    LogRepository.add("ConfigManager: failed to read modify counts: ${e.message}", LogLevel.ERROR)
                    Result.failure(e)
                }
            }
        }
}
