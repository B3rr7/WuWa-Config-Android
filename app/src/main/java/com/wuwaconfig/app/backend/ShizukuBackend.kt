package com.wuwaconfig.app.backend

import android.content.pm.PackageManager
import android.util.Base64
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

// NOTE: Shizuku.newProcess() (used below) is deprecated as of API 13 in favor of UserService.
// UserService runs Java/native code in a privileged process — more powerful than shell commands.
// Migration path: create a UserService that exposes shell exec, then bind via ShizukuUserServiceManager.
// See https://github.com/RikkaApps/Shizuku-API#user-service
class ShizukuBackend : AccessBackend {
    override val isConnected: Boolean
        get() =
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }

    override suspend fun connect(): Result<Unit> =
        withContext(Dispatchers.IO) {
            LogRepository.add("Shizuku connect: checking...")
            try {
                val version = Shizuku.getVersion()
                if (version < 0) {
                    LogRepository.add("Shizuku not running", LogLevel.ERROR)
                    return@withContext Result.failure(Exception("Shizuku is not running. Start Shizuku first."))
                }
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    LogRepository.add("Shizuku permission not granted", LogLevel.ERROR)
                    return@withContext Result.failure(Exception("Shizuku permission not granted."))
                }
                LogRepository.add("Shizuku connected successfully", LogLevel.SUCCESS)
                Result.success(Unit)
            } catch (e: Exception) {
                LogRepository.add("Shizuku connect failed: ${e.message}", LogLevel.ERROR)
                Result.failure(Exception("Shizuku is not running. Start Shizuku first."))
            }
        }

    override fun disconnect() {
        LogRepository.add("Shizuku disconnect")
    }

    override suspend fun executeShellCommand(command: String): Result<String> =
        withContext(Dispatchers.IO) {
            LogRepository.add("Shizuku shell: ${command.take(120)}")
            var lastError: Exception? = null
            for (attempt in 0..2) {
                if (attempt > 0) {
                    LogRepository.add("Shizuku shell retry $attempt: ${command.take(80)}")
                    delay(500L * attempt)
                }
                try {
                    val result = shizukuExecWithExit("sh", "-c", command)
                    if (result.exitCode != 0) {
                        val stderr = result.stderr.trim()
                        if (result.exitCode == 143 || stderr.contains("timed out", ignoreCase = true)) {
                            lastError = Exception(stderr.ifEmpty { "Command timed out (exit ${result.exitCode})" })
                            continue
                        }
                        LogRepository.add("Shizuku shell failed (exit ${result.exitCode}): ${stderr.take(100)}", LogLevel.ERROR)
                        return@withContext Result.failure(Exception(stderr.ifEmpty { "Command failed (exit ${result.exitCode})" }))
                    }
                    val filtered = filterPermissionDenied(result.stdout)
                    if (filtered != result.stdout) {
                        LogRepository.add("Shizuku shell permission denied", LogLevel.ERROR)
                        return@withContext Result.failure(Exception(filtered.trim().ifEmpty { "Permission denied" }))
                    }
                    return@withContext Result.success(result.stdout.trim())
                } catch (e: Exception) {
                    lastError = e
                    LogRepository.add("Shizuku shell attempt $attempt failed: ${e.message}", LogLevel.WARNING)
                }
            }
            LogRepository.add("Shizuku shell exhausted: ${lastError?.message}", LogLevel.ERROR)
            Result.failure(lastError ?: Exception("Shell command failed after retries"))
        }

    override suspend fun pushFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            LogRepository.add("Shizuku push: $sourcePath -> $targetPath")
            val sourceFile = File(sourcePath)
            val bytes = sourceFile.readBytes()
            val localMd5 = computeMd5(sourceFile)
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val target = shQuote(targetPath)
            val parent = File(targetPath).parent ?: return@withContext Result.failure(Exception("Invalid target path"))

            suspend fun doPush(): Result<String> {
                val tmpB64 = "/data/local/tmp/wb64_${System.currentTimeMillis()}_${(0..9999).random()}"
                val tq = shQuote(tmpB64)
                val setup = "rm -f /data/local/tmp/wb64_* && mkdir -p ${shQuote(parent)}"
                val chunkSize = maxPushChunkSize(tq)
                val chunks = encoded.chunked(chunkSize)
                val writes =
                    chunks.mapIndexed { i, chunk ->
                        val redir = if (i == 0) ">" else ">>"
                        "printf '%s' ${shQuote(chunk)} $redir $tq"
                    }
                val decode = "base64 -d $tq > $target && rm -f $tq"
                val verify = "md5sum $target 2>/dev/null | cut -d' ' -f1"
                val fullCmd = (listOf(setup) + writes + listOf(decode, verify)).joinToString(" && ")

                // If script exceeds arg limit, write it to temp and execute
                val result: String
                if (fullCmd.length <= MAX_ARG_STRLEN) {
                    result = shizukuExec("sh", "-c", fullCmd)
                } else {
                    val scriptPath = "/data/local/tmp/wuwa_push_${System.currentTimeMillis()}.sh"
                    val sq = shQuote(scriptPath)
                    val scriptChunks = fullCmd.chunked(4096)
                    val writeScript =
                        scriptChunks.mapIndexed { i, chunk ->
                            val redir = if (i == 0) ">" else ">>"
                            "printf '%s' ${shQuote(chunk)} $redir $sq"
                        }.joinToString(" && ")
                    shizukuExec("sh", "-c", writeScript)
                    result = shizukuExec("sh", "-c", "chmod +x $sq && sh $sq && rm -f $sq")
                }

                val remoteMd5 = result.trim()
                if (remoteMd5.length == 32) {
                    if (remoteMd5 != localMd5) {
                        shizukuExec("sh", "-c", "rm -f $target")
                        return@doPush Result.failure(Exception("MD5 mismatch after push: local=$localMd5 remote=$remoteMd5"))
                    }
                } else {
                    val sizeCmd = "wc -c < $target 2>/dev/null"
                    val remoteSize =
                        try {
                            shizukuExec("sh", "-c", sizeCmd).trim().toLong()
                        } catch (_: Exception) {
                            0L
                        }
                    if (remoteSize != bytes.size.toLong()) {
                        shizukuExec("sh", "-c", "rm -f $target")
                        return@doPush Result.failure(Exception("Size mismatch after push: local=${bytes.size} remote=$remoteSize"))
                    }
                }
                LogRepository.add("Shizuku push completed: $targetPath", LogLevel.SUCCESS)
                return@doPush Result.success("Pushed to $targetPath")
            }

            try {
                var lastError: Result<String>? = null
                for (attempt in 0..PUSH_RETRY_COUNT) {
                    val result = doPush()
                    if (result.isSuccess) return@withContext result
                    lastError = result
                    shizukuExec("sh", "-c", "rm -f /data/local/tmp/wb64_* /data/local/tmp/wuwa_push_*.sh")
                }
                LogRepository.add("Shizuku push failed after retries: ${lastError?.exceptionOrNull()?.message}", LogLevel.ERROR)
                return@withContext lastError ?: Result.failure(Exception("Push failed"))
            } catch (e: Exception) {
                LogRepository.add("Shizuku push exception: ${e.message}", LogLevel.ERROR)
                shizukuExec("sh", "-c", "rm -f /data/local/tmp/wb64_* /data/local/tmp/wuwa_push_*.sh")
                Result.failure(e)
            }
        }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val out = shizukuExec("mkdir", "-p", dirPath)
                Result.success(out)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun fileExists(path: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val out = shizukuExec("sh", "-c", "test -f '$path' && echo 1 || echo 0")
                Result.success(out.trim() == "1")
            } catch (_: Exception) {
                Result.success(false)
            }
        }

    override suspend fun listDirectory(path: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val out = shizukuExec("ls", "-1", path)
                Result.success(out.trim().lines().filter { it.isNotBlank() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun backupFile(path: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val backupPath = "$path.backup_${System.currentTimeMillis()}"
                shizukuExec("cp", path, backupPath)
                Result.success(backupPath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun readFile(path: String): Result<String> =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            for (attempt in 0..2) {
                if (attempt > 0) delay(500L * attempt)
                try {
                    val out = shizukuExec("cat", path)
                    return@withContext Result.success(out)
                } catch (e: Exception) {
                    lastError = e
                    LogRepository.add("Shizuku readFile attempt $attempt failed: ${e.message}", LogLevel.WARNING)
                }
            }
            LogRepository.add("Shizuku readFile failed: ${lastError?.message}", LogLevel.ERROR)
            Result.failure(lastError ?: Exception("readFile failed"))
        }

    override suspend fun readFileBytes(path: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            for (attempt in 0..2) {
                if (attempt > 0) delay(500L * attempt)
                try {
                    val b64 = shizukuExec("base64", "-w0", path)
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    return@withContext Result.success(bytes)
                } catch (e: Exception) {
                    lastError = e
                    LogRepository.add("Shizuku readFileBytes attempt $attempt failed: ${e.message}", LogLevel.WARNING)
                }
            }
            LogRepository.add("Shizuku readFileBytes failed: ${lastError?.message}", LogLevel.ERROR)
            Result.failure(lastError ?: Exception("readFileBytes failed"))
        }

    private data class ExecResult(val stdout: String, val stderr: String, val exitCode: Int)

    @Throws(Exception::class)
    private fun shizukuExec(vararg cmd: String): String {
        val result = shizukuExecWithExit(*cmd)
        if (result.exitCode != 0 && result.stderr.isNotBlank()) {
            throw Exception(result.stderr.trim())
        }
        return result.stdout
    }

    @Throws(Exception::class)
    private fun shizukuExecWithExit(vararg cmd: String): ExecResult {
        val process: java.lang.Process =
            try {
                val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                method.isAccessible = true
                method.invoke(null, cmd, null, null) as? java.lang.Process
            } catch (e: Exception) {
                throw Exception("Shizuku exec failed: ${e.message}")
            } ?: throw Exception("Shizuku returned null process")

        val stdout = readStream(process.inputStream)
        val stderr = readStream(process.errorStream)

        val exited = process.waitFor(60, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            throw Exception("Command timed out after 60s")
        }

        val exitCode =
            try {
                process.exitValue()
            } catch (_: IllegalThreadStateException) {
                // waitFor already returned true, output already captured above
                0
            }

        return ExecResult(stdout, stderr, exitCode)
    }

    private fun readStream(stream: InputStream): String {
        return BufferedReader(InputStreamReader(stream)).readText()
    }

    private fun filterPermissionDenied(output: String): String {
        val deniedLines = output.lines().filter { it.contains("Permission denied", ignoreCase = true) }
        return if (deniedLines.isNotEmpty()) deniedLines.joinToString("\n") else output
    }
}
