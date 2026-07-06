package com.wuwaconfig.app.backend

import android.content.pm.PackageManager
import android.util.Base64
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

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
            try {
                val result = shizukuExecWithExit("sh", "-c", command)
                if (result.exitCode != 0) {
                    val stderr = result.stderr.trim()
                    LogRepository.add("Shizuku shell failed (exit ${result.exitCode}): ${stderr.take(100)}", LogLevel.ERROR)
                    return@withContext Result.failure(Exception(stderr.ifEmpty { "Command failed (exit ${result.exitCode})" }))
                }
                val filtered = filterPermissionDenied(result.stdout)
                if (filtered != result.stdout) {
                    LogRepository.add("Shizuku shell permission denied", LogLevel.ERROR)
                    return@withContext Result.failure(Exception(filtered.trim().ifEmpty { "Permission denied" }))
                }
                Result.success(result.stdout.trim())
            } catch (e: Exception) {
                LogRepository.add("Shizuku shell exception: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
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
            val encodedPath = "/data/local/tmp/wuwaconfig_${System.currentTimeMillis()}_${(0..9999).random()}.b64"
            val parent = File(targetPath).parent ?: return@withContext Result.failure(Exception("Invalid target path"))

            suspend fun doPush(): Result<String> {
                shizukuExec("sh", "-c", "rm -f /data/local/tmp/wuwaconfig_*.b64")
                shizukuExec("mkdir", "-p", parent)
                val eq = shQuote(encodedPath)
                shizukuExec("sh", "-c", "rm -f $eq")
                val chunkSize = maxPushChunkSize(encodedPath)
                val chunks = encoded.chunked(chunkSize)
                for ((i, chunk) in chunks.withIndex()) {
                    val redirect = if (i == 0) ">" else ">>"
                    var lastErr: Exception? = null
                    for (attempt in 0..PUSH_RETRY_COUNT) {
                        try {
                            shizukuExec("sh", "-c", "printf '%s' ${shQuote(chunk)} $redirect $eq")
                            lastErr = null
                            break
                        } catch (e: Exception) {
                            lastErr = e
                        }
                    }
                    if (lastErr != null) {
                        shizukuExec("sh", "-c", "rm -f $eq")
                        return@doPush Result.failure(lastErr)
                    }
                }
                val tq = shQuote(targetPath)
                try {
                    shizukuExec("sh", "-c", "base64 -d $eq > $tq; rm -f $eq")
                } catch (e: Exception) {
                    shizukuExec("sh", "-c", "rm -f $eq")
                    return@doPush Result.failure(e)
                }
                val verifyCmd = "md5sum $tq 2>/dev/null | cut -d' ' -f1"
                val remoteMd5 =
                    try {
                        shizukuExec("sh", "-c", verifyCmd).trim()
                    } catch (_: Exception) {
                        ""
                    }
                if (remoteMd5.length == 32) {
                    if (remoteMd5 != localMd5) {
                        shizukuExec("sh", "-c", "rm -f $tq")
                        return@doPush Result.failure(Exception("MD5 mismatch after push: local=$localMd5 remote=$remoteMd5"))
                    }
                } else {
                    val sizeCmd = "wc -c < $tq 2>/dev/null"
                    val remoteSize =
                        try {
                            shizukuExec("sh", "-c", sizeCmd).trim().toLong()
                        } catch (_: Exception) {
                            0L
                        }
                    if (remoteSize != bytes.size.toLong()) {
                        shizukuExec("sh", "-c", "rm -f $tq")
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
                    shizukuExec("sh", "-c", "rm -f ${shQuote(encodedPath)}; rm -f ${shQuote(targetPath)}")
                }
                LogRepository.add("Shizuku push failed after retries: ${lastError?.exceptionOrNull()?.message}", LogLevel.ERROR)
                return@withContext lastError ?: Result.failure(Exception("Push failed"))
            } catch (e: Exception) {
                LogRepository.add("Shizuku push exception: ${e.message}", LogLevel.ERROR)
                shizukuExec("sh", "-c", "rm -f ${shQuote(encodedPath)}")
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
            try {
                val out = shizukuExec("cat", path)
                Result.success(out)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private data class ExecResult(val stdout: String, val stderr: String, val exitCode: Int)

    @Throws(Exception::class)
    private fun shizukuExec(vararg cmd: String): String {
        val result = shizukuExecWithExit(*cmd)
        if (result.stderr.isNotBlank()) throw Exception(result.stderr.trim())
        return result.stdout
    }

    @Throws(Exception::class)
    private fun shizukuExecWithExit(vararg cmd: String): ExecResult {
        val clazz =
            try {
                Class.forName("rikka.shizuku.Shizuku")
            } catch (e: ClassNotFoundException) {
                throw Exception("Shizuku not available: ${e.message}")
            }
        val method =
            try {
                clazz.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            } catch (e: NoSuchMethodException) {
                throw Exception("Shizuku API mismatch: ${e.message}")
            }
        try {
            method.isAccessible = true
        } catch (_: Exception) {
        }
        val process =
            try {
                method.invoke(null, cmd, null, null) as? java.lang.Process
            } catch (e: Exception) {
                throw Exception("Shizuku invoke failed: ${e.message}")
            } ?: throw Exception("Shizuku returned null process")

        val stdout = readStream(process.inputStream)
        val stderr = readStream(process.errorStream)

        val exited = process.waitFor(15, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            throw Exception("Command timed out after 15s")
        }

        val exitCode =
            try {
                process.exitValue()
            } catch (e: IllegalThreadStateException) {
                process.waitFor(5, TimeUnit.SECONDS)
                process.exitValue()
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
