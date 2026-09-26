package com.wuwaconfig.app.backend

import android.util.Base64
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RootBackend : AccessBackend {
    @Volatile
    override var isConnected: Boolean = false
        private set

    companion object {
        private const val ROOT_CMD_TIMEOUT_SEC = 15L
        private const val ROOT_FILE_OP_TIMEOUT_SEC = 60L

        /**
         * `Process.waitFor` blocks a thread, and ProfileExtractor fans several `su` calls out
         * concurrently. Plain [Dispatchers.IO] is an elastic pool, not a blocking-work pool,
         * so a burst of long waits can starve unrelated IO work — cap it. Stream readers stay
         * on the elastic pool so they can never queue behind the cap and deadlock a
         * waitFor/reader pair.
         */
        private val rootIo: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)
    }

    override suspend fun connect(): Result<Unit> =
        withContext(rootIo) {
            LogRepository.add("Root: checking su access...")
            try {
                val process =
                    ProcessBuilder("su", "-c", "echo ROOT_OK")
                        .redirectErrorStream(true)
                        .start()
                closeChildStdin(process)
                val (output, timedOut) =
                    coroutineScope {
                        val reader = async(Dispatchers.IO) { process.inputStream.bufferedReader().use { it.readText().trim() } }
                        val exited = process.waitFor(ROOT_CMD_TIMEOUT_SEC, TimeUnit.SECONDS)
                        if (!exited) {
                            try {
                                process.destroyForcibly()
                            } catch (_: Exception) {
                            }
                            reader.cancel()
                            Pair("", true)
                        } else {
                            Pair(reader.await(), false)
                        }
                    }
                if (timedOut) {
                    LogRepository.add("Root check timed out", LogLevel.ERROR)
                    return@withContext Result.failure(Exception("Root check timed out"))
                }
                val exitCode = process.exitValue()
                if (exitCode == 0 && output == "ROOT_OK") {
                    isConnected = true
                    LogRepository.add("Root access granted", LogLevel.SUCCESS)
                    Result.success(Unit)
                } else {
                    LogRepository.add("Root access denied: $output", LogLevel.ERROR)
                    Result.failure(Exception(output.ifBlank { "Root access denied" }))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("Root not available: ${e.message}", LogLevel.ERROR)
                Result.failure(Exception("Root not available: ${e.message}"))
            }
        }

    override fun disconnect() {
        LogRepository.add("Root: disconnect")
        isConnected = false
    }

    override suspend fun executeShellCommand(command: String): Result<String> =
        withContext(rootIo) {
            LogRepository.add("Root shell: ${command.take(120)}")
            try {
                val process =
                    ProcessBuilder("su", "-c", command)
                        .redirectErrorStream(true)
                        .start()
                // The child's stdin pipe is inherited but never written or closed, so a
                // command that reads stdin (`cat` with no args) would block until the
                // timeout. Close it right away so the child sees EOF.
                closeChildStdin(process)
                // Use longer timeout for file ops that may touch large outputs.
                val timeoutSec =
                    if (command.startsWith("cat ") || command.contains("base64") || command.startsWith("cp ")) {
                        ROOT_FILE_OP_TIMEOUT_SEC
                    } else {
                        ROOT_CMD_TIMEOUT_SEC
                    }
                val (output, timedOut) =
                    coroutineScope {
                        val reader = async(Dispatchers.IO) { process.inputStream.bufferedReader().use { it.readText() } }
                        val exited = process.waitFor(timeoutSec, TimeUnit.SECONDS)
                        if (!exited) {
                            try {
                                process.destroyForcibly()
                            } catch (_: Exception) {
                            }
                            reader.cancel()
                            Pair("", true)
                        } else {
                            Pair(reader.await(), false)
                        }
                    }
                if (timedOut) {
                    LogRepository.add("Root shell timed out", LogLevel.ERROR)
                    // Truncated: the full command is logged and surfaced to the user and can
                    // contain a whole base64 chunk.
                    return@withContext Result.failure(Exception("Command timed out: ${command.take(120)}"))
                }
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    // First line only, and never more than 120 chars: `output` is the
                    // command's stdout, which for the game's Client.log reads is
                    // DECRYPTED LOG CONTENT that must not reach disk.
                    LogRepository.add(
                        "Root shell failed (exit $exitCode): " +
                            output.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(120),
                        LogLevel.ERROR,
                    )
                    // Su revoked or denied — mark disconnected so UI reflects it.
                    if (output.contains("Permission denied", ignoreCase = true) ||
                        output.contains("not allowed", ignoreCase = true)
                    ) {
                        isConnected = false
                    }
                    Result.failure(Exception(output.trim().ifEmpty { "Command failed with exit code $exitCode" }))
                } else {
                    Result.success(output.trim())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("Root shell exception: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun pushFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> {
        LogRepository.add("Root push: $sourcePath -> $targetPath")
        val bytes =
            try {
                java.io.File(sourcePath).readBytes()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return Result.failure(Exception("Cannot read $sourcePath: ${e.message}"))
            }
        val localMd5 = computeMd5(bytes)
        // Parity with the other backends: `cp` to a missing directory fails, and without a
        // verify a failed/partial cp was previously reported as a successful push.
        val parent = targetPath.substringBeforeLast('/', "")
        if (parent.isEmpty()) return Result.failure(Exception("Invalid target path: $targetPath"))
        val mkdir = executeShellCommand("mkdir -p ${shQuote(parent)}")
        if (mkdir.isFailure) {
            return Result.failure(Exception("Cannot create directory $parent: ${mkdir.exceptionOrNull()?.message}"))
        }
        val result = executeShellCommand("cp ${shQuote(sourcePath)} ${shQuote(targetPath)}")
        if (result.isFailure) {
            return Result.failure(result.exceptionOrNull() ?: Exception("Push failed"))
        }
        val verify = executeShellCommand("md5sum ${shQuote(targetPath)} 2>/dev/null | cut -d' ' -f1")
        val remoteMd5 = verify.getOrNull()?.trim()
        if (remoteMd5 != null && remoteMd5.length == 32 && remoteMd5 != localMd5) {
            return Result.failure(Exception("MD5 mismatch after push: local=$localMd5 remote=$remoteMd5"))
        }
        LogRepository.add("Root push completed: $targetPath", LogLevel.SUCCESS)
        return Result.success("Pushed to $targetPath")
    }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> {
        return executeShellCommand("mkdir -p ${shQuote(dirPath)}")
    }

    override suspend fun fileExists(path: String): Result<Boolean> {
        val result = executeShellCommand("test -f ${shQuote(path)} && echo 1 || echo 0")
        return result.map { it.trim() == "1" }
    }

    override suspend fun listDirectory(path: String): Result<List<String>> {
        val result = executeShellCommand("ls -1 ${shQuote(path)} 2>/dev/null")
        return result.map { output ->
            output.trim().lines().filter { it.isNotBlank() }
        }
    }

    override suspend fun backupFile(path: String): Result<String> {
        val backupPath = "$path.backup_${System.currentTimeMillis()}"
        return executeShellCommand("cp ${shQuote(path)} ${shQuote(backupPath)}").map { backupPath }
    }

    override suspend fun readFile(path: String): Result<String> {
        return executeShellCommand("cat ${shQuote(path)}")
    }

    override suspend fun readFileBytes(path: String): Result<ByteArray> =
        withContext(rootIo) {
            try {
                val b64 = executeShellCommand("base64 -w0 ${shQuote(path)}")
                if (b64.isFailure) return@withContext Result.failure(b64.exceptionOrNull()!!)
                val bytes = Base64.decode(b64.getOrThrow(), Base64.DEFAULT)
                Result.success(bytes)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("Root: readFileBytes base64 decode failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> {
        val parent = targetPath.substringBeforeLast('/', "")
        if (parent.isEmpty()) return Result.failure(Exception("Invalid target path"))
        val mkdir = executeShellCommand("mkdir -p ${shQuote(parent)}")
        if (mkdir.isFailure) {
            // Explicit: `Result.map` on a failure is the identity, which read as if a
            // mkdir failure were being reported as success.
            return Result.failure(
                Exception("Cannot create directory $parent: ${mkdir.exceptionOrNull()?.message}"),
            )
        }
        return executeShellCommand("cp ${shQuote(sourcePath)} ${shQuote(targetPath)}").map { targetPath }
    }

    override suspend fun deleteFile(path: String): Result<Unit> {
        return executeShellCommand("rm -f ${shQuote(path)}").map { Unit }
    }

    private fun closeChildStdin(process: Process) {
        // Nonzero exit tolerated: a child that already exited has nothing to read.
        runCatching { process.outputStream.close() }
    }
}
