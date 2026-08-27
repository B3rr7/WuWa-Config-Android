package com.wuwaconfig.app.config

import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.computeMd5
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository

/**
 * Single implementation of the device hash-sync check shared by the deploy
 * pipeline and the INI editor (previously two divergent copies whose result
 * callbacks had inverted semantics).
 *
 * Contract: [syncIfNeeded] returns `true` when the device hashes were out of
 * sync and a refresh was performed (or attempted); `false` when everything
 * already matched.
 */
class HashSync(
    private val backendProvider: () -> AccessBackend,
    private val configManager: ConfigManager,
) {
    private fun log(
        message: String,
        level: LogLevel = LogLevel.INFO,
    ) {
        LogRepository.add(message, level)
    }

    private val backend: AccessBackend get() = backendProvider()

    suspend fun syncIfNeeded(): Boolean {
        log("Hash sync: checking device config hashes...")
        val hashContent = backend.readFile(GamePaths.HASH_MONITOR_PATH).getOrDefault("")
        if (hashContent.isBlank()) {
            log("Hash sync: no hash file found, creating fresh...")
            configManager.refreshConfigHashes().onSuccess { log(it) }
            return true
        }
        var needsRefresh = false
        for (name in GamePaths.MONITORED_FILES) {
            val path = "${GamePaths.TARGET_DIR}/$name"
            // Files the user intentionally never deployed can't have a matching
            // hash; counting them as a mismatch forces a permanent refresh loop.
            if (!backend.fileExists(path).getOrDefault(false)) {
                continue
            }
            val actualHashResult = computeIniHash(name)
            if (actualHashResult.isFailure) {
                log("Hash sync: SKIPPING $name — cannot compute hash", LogLevel.ERROR)
                needsRefresh = true
                continue
            }
            val actualHash = actualHashResult.getOrThrow()
            val storedHash = extractHash(hashContent, name)
            when {
                storedHash != null && storedHash != actualHash -> {
                    log("Hash sync: $name hash mismatch (stored=$storedHash, actual=$actualHash)", LogLevel.WARNING)
                    needsRefresh = true
                }
                storedHash == null -> {
                    log("Hash sync: $name has no stored hash", LogLevel.WARNING)
                    needsRefresh = true
                }
                else -> log("Hash sync: $name hash OK ($actualHash)")
            }
        }
        if (needsRefresh) {
            log("Hash sync: refreshing to match current files...")
            configManager.refreshConfigHashes().onSuccess { log(it) }
        } else {
            log("Hash sync: all hashes match", LogLevel.SUCCESS)
        }
        return needsRefresh
    }

    suspend fun computeIniHash(name: String): Result<String> {
        val path = "${GamePaths.TARGET_DIR}/$name"
        val bytesResult = backend.readFileBytes(path)
        if (bytesResult.isFailure) {
            log("Hash sync: readFileBytes FAILED for $name: ${bytesResult.exceptionOrNull()?.message}", LogLevel.ERROR)
            return Result.failure(bytesResult.exceptionOrNull()!!)
        }
        val bytes = bytesResult.getOrThrow()
        val hash = computeMd5(bytes)
        log("Hash sync: computed hash for $name = $hash (${bytes.size} bytes)")
        return Result.success(hash)
    }
}
