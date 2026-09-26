package com.wuwaconfig.app.backend

import android.util.Base64
import com.wuwaconfig.app.adb.AdbClient
import com.wuwaconfig.app.adb.AdbCrypto
import com.wuwaconfig.app.adb.PortScanner
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import java.io.File

class AdbBackend(private val crypto: AdbCrypto) : AccessBackend {
    private val client = AdbClient(crypto)
    override val isConnected: Boolean get() = client.isConnected

    companion object {
        private const val GAME_PKG = GamePaths.TARGET_PACKAGE
        private const val STAGING_SUFFIX = ".wuwa_new"
    }

    override suspend fun connect(): Result<Unit> {
        LogRepository.add("ADB connect: scanning for ADB port...")
        val scan = PortScanner.scanForAdb()
        if (scan == null) {
            LogRepository.add("ADB connect failed: port not found", LogLevel.ERROR)
            return Result.failure(Exception("ADB port not found. Enable Wireless Debugging and tap Connect, or enter IP:port manually."))
        }
        // The LAN IP is masked. Host+port together are exactly what a third party on
        // the same Wi-Fi needs to open a wireless-ADB session to this phone, and the
        // port alone is what makes this line useful for diagnosing a scan miss.
        LogRepository.add("ADB connect: found port ${scan.port} on ${maskHost(scan.host)}")
        val first = client.connect(scan.port, scan.host)
        if (first.isSuccess) {
            LogRepository.add("ADB connected successfully", LogLevel.SUCCESS)
            return first
        }
        val msg = first.exceptionOrNull()?.message ?: ""
        if (msg.contains("rejected", ignoreCase = true) || msg.contains("denied", ignoreCase = true)) {
            LogRepository.add("ADB connection rejected, regenerating keys...", LogLevel.WARNING)
            return client.connectWithRegeneratedKeys(scan.port, scan.host)
        }
        LogRepository.add("ADB connect failed: ${first.exceptionOrNull()?.message}", LogLevel.ERROR)
        return first
    }

    suspend fun connectTo(
        host: String,
        port: Int,
    ): Result<Unit> {
        LogRepository.add("ADB connectTo: ${maskHost(host)}:$port")
        val first = client.connect(port, host)
        if (first.isSuccess) {
            LogRepository.add("ADB connected to ${maskHost(host)}:$port", LogLevel.SUCCESS)
            return first
        }
        val msg = first.exceptionOrNull()?.message ?: ""
        if (msg.contains("rejected", ignoreCase = true) || msg.contains("denied", ignoreCase = true)) {
            LogRepository.add("ADB connection to $host:$port rejected, regenerating keys...", LogLevel.WARNING)
            return client.connectWithRegeneratedKeys(port, host)
        }
        LogRepository.add("ADB connectTo $host:$port failed: ${first.exceptionOrNull()?.message}", LogLevel.ERROR)
        return first
    }

    override fun disconnect() {
        LogRepository.add("ADB disconnect")
        client.disconnect()
    }

    override suspend fun executeShellCommand(command: String): Result<String> {
        LogRepository.add("ADB shell: ${command.take(120)}")
        // Shared with ShizukuBackend/AdbClient: retry the WHOLE command (grouped through
        // `run-as ... sh -c`) when the shell reports a permission failure, which is what
        // scoped storage produces on the ROMs this fallback exists for.
        val result =
            withRunAsFallback(command, GAME_PKG) { cmd ->
                client.executeShellCommand(cmd)
            }
        if (result.isSuccess) {
            return result
        }
        val msg = result.exceptionOrNull()?.message ?: ""
        if (isPermissionDenied(msg)) {
            LogRepository.add("ADB shell permission denied — paths under /storage/emulated/0/ should be accessible via ADB shell on Android 11+. Some OEMs block this.", LogLevel.WARNING)
        }
        if (isNotDebuggable(msg)) {
            LogRepository.add("ADB: run-as unavailable — game package is not debuggable. Use Shizuku or Root backend.", LogLevel.ERROR)
        }
        // Only the first line of the message reaches disk; AdbClient already caps it
        // and strips multi-line command output so decrypted log bytes cannot be
        // persisted here.
        LogRepository.add("ADB shell failed: ${msg.lineSequence().firstOrNull().orEmpty().take(160)}", LogLevel.ERROR)
        return result
    }

    override suspend fun pushFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> {
        LogRepository.add("ADB push: $sourcePath -> $targetPath")
        val sourceFile = File(sourcePath)
        val bytes = sourceFile.readBytes()
        val localMd5 = computeMd5(bytes)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val nonce = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        val encodedPath = "/data/local/tmp/wuwaconfig_${System.currentTimeMillis()}_$nonce.b64"
        val parent = File(targetPath).parent ?: return Result.failure(Exception("Invalid target path"))
        // Never write the target directly: the shell can read an existing file it cannot
        // write, so an md5 mismatch after a direct write would tempt us to delete a
        // perfectly good deployed config. Decode into a sibling and mv only once verified.
        val stagingTarget = targetPath + STAGING_SUFFIX

        suspend fun doPush(attempt: Int): Result<String> {
            // Per-attempt staging file: a retried chunk must never append to a
            // partially-written file from the previous attempt.
            val attemptPath = "$encodedPath.$attempt"
            val mkdirCmd = "mkdir -p ${shQuote(parent)}"
            val mkdirResult =
                withRunAsFallback(mkdirCmd, GAME_PKG) { cmd ->
                    client.executeShellCommand(cmd)
                }
            if (mkdirResult.isFailure) {
                return Result.failure(
                    Exception("Cannot create directory ${parent}: ${mkdirResult.exceptionOrNull()?.message}"),
                )
            }
            // Nonzero exit tolerated: `rm -f` fails only when the staging path is
            // unwritable, which the first write reports anyway.
            client.executeShellCommand("rm -f ${shQuote(attemptPath)}")
            val plan = buildPushFilePlan(encoded, stagingTarget, attemptPath)
            for (w in plan.writes) {
                var lastErr: Result<String>? = null
                for (i in 0..PUSH_RETRY_COUNT) {
                    val r = client.executeShellCommand(w)
                    if (r.isSuccess) {
                        lastErr = null
                        break
                    }
                    lastErr = r
                }
                if (lastErr != null) {
                    client.executeShellCommand("rm -f ${shQuote(attemptPath)}")
                    return lastErr
                }
            }
            val decodeCmd = plan.decode
            var decodeResult = client.executeShellCommand(decodeCmd)
            if (decodeResult.isFailure) {
                val altDecode = client.executeShellCommandWithRunAs(GAME_PKG, decodeCmd)
                if (altDecode.isSuccess) decodeResult = altDecode
            }
            if (decodeResult.isFailure) {
                client.executeShellCommand("rm -f ${shQuote(attemptPath)} ${shQuote(stagingTarget)}")
                return decodeResult
            }

            val verifyCmd = plan.verify
            var remoteMd5 = client.executeShellCommand(verifyCmd).getOrNull()?.trim()
            if (remoteMd5 == null || remoteMd5.length != 32) {
                val altMd5 = client.executeShellCommandWithRunAs(GAME_PKG, verifyCmd).getOrNull()?.trim()
                if (altMd5 != null && altMd5.length == 32) remoteMd5 = altMd5
            }
            if (remoteMd5 != null && remoteMd5.length == 32) {
                if (remoteMd5 != localMd5) {
                    // Only our own staging files are removed; the target may still be a
                    // perfectly good deploy, so it is never deleted.
                    client.executeShellCommand("rm -f ${shQuote(attemptPath)} ${shQuote(stagingTarget)}")
                    return Result.failure(Exception("MD5 mismatch after push: local=$localMd5 remote=$remoteMd5"))
                }
            } else {
                val sizeCmd = "wc -c < ${shQuote(stagingTarget)} 2>/dev/null"
                var remoteSize = client.executeShellCommand(sizeCmd).getOrNull()?.trim()?.toLongOrNull()
                if (remoteSize == null) {
                    val altSize = client.executeShellCommandWithRunAs(GAME_PKG, sizeCmd).getOrNull()?.trim()?.toLongOrNull()
                    if (altSize != null) remoteSize = altSize
                }
                if (remoteSize == null) {
                    client.executeShellCommand("rm -f ${shQuote(attemptPath)} ${shQuote(stagingTarget)}")
                    return Result.failure(Exception("Cannot verify file after push — file may not exist"))
                }
                if (remoteSize != bytes.size.toLong()) {
                    client.executeShellCommand("rm -f ${shQuote(attemptPath)} ${shQuote(stagingTarget)}")
                    return Result.failure(Exception("Size mismatch after push: local=${bytes.size} remote=$remoteSize"))
                }
            }
            val moveCmd =
                "mv ${shQuote(stagingTarget)} ${shQuote(targetPath)}"
            val moveResult =
                withRunAsFallback(moveCmd, GAME_PKG) { cmd ->
                    client.executeShellCommand(cmd)
                }
            if (moveResult.isFailure) {
                return Result.failure(
                    Exception("Cannot move staged file into place: ${moveResult.exceptionOrNull()?.message}"),
                )
            }
            LogRepository.add("ADB push completed: $targetPath", LogLevel.SUCCESS)
            return Result.success("Pushed to $targetPath")
        }

        return try {
            var lastError: Result<String>? = null
            for (attempt in 0..PUSH_RETRY_COUNT) {
                val result = doPush(attempt)
                if (result.isSuccess) return result
                lastError = result
            }
            LogRepository.add("ADB push failed after retries: ${lastError?.exceptionOrNull()?.message}", LogLevel.ERROR)
            lastError ?: Result.failure(Exception("Push failed"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LogRepository.add("ADB push exception: ${e.message}", LogLevel.ERROR)
            // Nonzero exit tolerated: best-effort staging cleanup, the real error wins.
            client.executeShellCommand("rm -f ${shQuote(encodedPath)}.*")
            Result.failure(e)
        }
    }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> {
        val cmd = "mkdir -p ${shQuote(dirPath)}"
        return withRunAsFallback(cmd, GAME_PKG) { client.executeShellCommand(it) }
    }

    override suspend fun fileExists(path: String): Result<Boolean> {
        // Intentionally tolerant: `&& ... || ...` always exits 0, so a failure here is
        // transport-level, not a permission verdict.
        val result = client.fileExists(path)
        if (result.isFailure) {
            val alt = client.executeShellCommandWithRunAs(GAME_PKG, "test -f ${shQuote(path)} && echo 1 || echo 0")
            return alt.map { it.trim() == "1" }
        }
        return result
    }

    override suspend fun listDirectory(path: String): Result<List<String>> {
        // `ls` on a denied directory now fails (exit code aware) instead of returning an
        // empty list, so a denied listing can no longer be reported as "0 files".
        val result = client.listDirectory(path)
        if (result.isFailure) {
            val alt = client.executeShellCommandWithRunAs(GAME_PKG, "ls -1 ${shQuote(path)} 2>/dev/null")
            return alt.map { output -> output.trim().lines().filter { it.isNotBlank() } }
        }
        return result
    }

    override suspend fun backupFile(path: String): Result<String> {
        val result = client.backupFile(path)
        if (result.isSuccess) return result
        val backupPath = "$path.backup_${System.currentTimeMillis()}"
        // Report where the backup went; the run-as `cp` prints nothing on success.
        return client.executeShellCommandWithRunAs(GAME_PKG, "cp ${shQuote(path)} ${shQuote(backupPath)}")
            .map { backupPath }
    }

    override suspend fun readFile(path: String): Result<String> {
        val cmd = "cat ${shQuote(path)}"
        return withRunAsFallback(cmd, GAME_PKG) { client.executeShellCommand(it) }
    }

    override suspend fun readFileBytes(path: String): Result<ByteArray> {
        val b64Cmd = "base64 -w0 ${shQuote(path)}"
        val b64 = withRunAsFallback(b64Cmd, GAME_PKG) { client.executeShellCommand(it) }
        return b64.mapCatching { Base64.decode(it.trim(), Base64.DEFAULT) }
    }

    override suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> {
        val parent = File(targetPath).parent
            ?: return Result.failure(Exception("Invalid target path: $targetPath"))
        val mkdirCmd = "mkdir -p ${shQuote(parent)}"
        val mkdirResult = withRunAsFallback(mkdirCmd, GAME_PKG) { client.executeShellCommand(it) }
        if (mkdirResult.isFailure) return mkdirResult
        val cpCmd = "cp ${shQuote(sourcePath)} ${shQuote(targetPath)}"
        val result = withRunAsFallback(cpCmd, GAME_PKG) { client.executeShellCommand(it) }
        // A failed cp (e.g. the shell cannot write into the app's private data dir) now
        // surfaces as Result.failure, but the deployment may still be recoverable: stage
        // it through world-writable /data/local/tmp (which shell can write and the app
        // process can read) and move it into place with an in-process file op the app UID
        // is allowed to perform.
        val landedOut = client.executeShellCommand("test -f ${shQuote(targetPath)} && echo 1 || echo 0")
        val landed = landedOut.getOrNull()?.trim() == "1"
        if (!landed) {
            val nonce = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val stage = "/data/local/tmp/wuwa_cp_${System.currentTimeMillis()}_$nonce"
            val stageResult = client.executeShellCommand("cp ${shQuote(sourcePath)} ${shQuote(stage)}")
            if (stageResult.isSuccess) {
                // The staged file is owned by shell with mode 660, which the app UID
                // cannot read. Make it world-readable so the in-process copy below
                // (run as the app UID) can read it.
                // Nonzero exit tolerated: the following copy reports an unreadable stage.
                client.executeShellCommand("chmod 644 ${shQuote(stage)}")
                return try {
                    val dest = File(targetPath)
                    dest.parentFile?.mkdirs()
                    // Copy to a local temp first: copying straight onto the target with
                    // overwrite=true would truncate it before the staged file is known
                    // readable. rename(2) atomically replaces an existing destination.
                    val tmp = File.createTempFile("wuwa_dl_", null, dest.parentFile)
                    try {
                        File(stage).copyTo(tmp, overwrite = true)
                        if (!tmp.renameTo(dest)) throw Exception("Failed to move file into place: $targetPath")
                    } finally {
                        tmp.delete()
                    }
                    Result.success(targetPath)
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    // The app UID cannot usually delete shell-owned files from
                    // /data/local/tmp (no o+w on the directory) — ask the shell instead;
                    // a local File.delete() here would silently fail and leak stages.
                    client.executeShellCommand("rm -f ${shQuote(stage)}")
                }
            }
        }
        return if (landed) {
            Result.success(targetPath)
        } else {
            Result.failure(Exception("copyFile failed: $targetPath was not written (shell cp and staging both failed)"))
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> {
        val cmd = "rm -f ${shQuote(path)}"
        val result = client.executeShellCommand(cmd)
        return if (result.isSuccess) {
            Result.success(Unit)
        } else {
            client.executeShellCommandWithRunAs(GAME_PKG, cmd).map { Unit }
        }
    }
}

/**
 * Masks a host for logging: keeps only enough to tell "loopback vs LAN vs IPv6" and
 * which /24, without publishing the exact address of the user's phone.
 */
internal fun maskHost(host: String): String =
    when {
        host == "127.0.0.1" || host == "localhost" -> host
        host.contains(':') -> "[ipv6]"
        else -> host.substringBeforeLast('.', missingDelimiterValue = "x") + ".x"
    }
