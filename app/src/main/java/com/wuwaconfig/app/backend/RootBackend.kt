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
    override var isConnected: Boolean = false
        private set

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
                        val reader = async(Dispatchers.IO) { process.inputStream.bufferedReader().readText().trim() }
                        val exited = process.waitFor(10, TimeUnit.SECONDS)
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
                    process.destroyForcibly()
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
                val (output, timedOut) =
                    coroutineScope {
                        val reader = async(Dispatchers.IO) { process.inputStream.bufferedReader().readText() }
                        val exited = process.waitFor(10, TimeUnit.SECONDS)
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
                    Result.failure(Exception(output.trim().ifEmpty { "Command failed with exit code $exitCode" }))
                } else {
                    Result.success(output.trim())
                }
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
            val b64 = executeShellCommand("base64 -w0 ${shQuote(path)}")
            if (b64.isFailure) return@withContext Result.failure(b64.exceptionOrNull()!!)
            try {
                val bytes = Base64.decode(b64.getOrThrow(), Base64.DEFAULT)
                Result.success(bytes)
            } catch (e: Exception) {
                LogRepository.add("Root: readFileBytes base64 decode failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> {
        val parent = java.io.File(targetPath).parent
        executeShellCommand("mkdir -p ${shQuote(parent)}")
        return executeShellCommand("cp ${shQuote(sourcePath)} ${shQuote(targetPath)}").map { targetPath }
    }

    override suspend fun deleteFile(path: String): Result<Unit> {
        return executeShellCommand("rm -f ${shQuote(path)}").map { Unit }
    }
}
