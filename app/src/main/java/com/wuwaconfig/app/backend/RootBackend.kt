package com.wuwaconfig.app.backend

import android.util.Base64
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
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
    }

    override suspend fun connect(): Result<Unit> =
        withContext(Dispatchers.IO) {
            LogRepository.add("Root: checking su access...")
            try {
                val process =
                    ProcessBuilder("su", "-c", "echo ROOT_OK")
                        .redirectErrorStream(true)
                        .start()
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
        withContext(Dispatchers.IO) {
            LogRepository.add("Root shell: ${command.take(120)}")
            try {
                val process =
                    ProcessBuilder("su", "-c", command)
                        .redirectErrorStream(true)
                        .start()
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
                    return@withContext Result.failure(Exception("Command timed out: $command"))
                }
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    LogRepository.add("Root shell failed (exit $exitCode): ${output.take(100)}", LogLevel.ERROR)
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
        val result = executeShellCommand("cp ${shQuote(sourcePath)} ${shQuote(targetPath)}")
        if (result.isSuccess) LogRepository.add("Root push completed: $targetPath", LogLevel.SUCCESS)
        return result
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
        return executeShellCommand("cp ${shQuote(path)} ${shQuote(backupPath)}")
    }

    override suspend fun readFile(path: String): Result<String> {
        return executeShellCommand("cat ${shQuote(path)}")
    }

    override suspend fun readFileBytes(path: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
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
        if (mkdir.isFailure) return mkdir.map { targetPath }
        return executeShellCommand("cp ${shQuote(sourcePath)} ${shQuote(targetPath)}").map { targetPath }
    }

    override suspend fun deleteFile(path: String): Result<Unit> {
        return executeShellCommand("rm -f ${shQuote(path)}").map { Unit }
    }
}
