package com.wuwaconfig.app.backend

import android.content.pm.PackageManager
import android.util.Base64
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
        get() = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val version = Shizuku.getVersion()
            if (version < 0) {
                return@withContext Result.failure(Exception("Shizuku is not running. Start Shizuku first."))
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return@withContext Result.failure(Exception("Shizuku permission not granted."))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Shizuku is not running. Start Shizuku first."))
        }
    }

    override fun disconnect() {}

    override suspend fun executeShellCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val result = shizukuExecWithExit("sh", "-c", command)
            if (result.exitCode != 0) {
                val stderr = result.stderr.trim()
                return@withContext Result.failure(Exception(stderr.ifEmpty { "Command failed (exit ${result.exitCode})" }))
            }
            val filtered = filterPermissionDenied(result.stdout)
            if (filtered != result.stdout) {
                return@withContext Result.failure(Exception(filtered.trim().ifEmpty { "Permission denied" }))
            }
            Result.success(result.stdout.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pushFile(sourcePath: String, targetPath: String): Result<String> = withContext(Dispatchers.IO) {
        val encodedPath = "/data/local/tmp/wuwaconfig_${System.currentTimeMillis()}_${(0..9999).random()}.b64"
        try {
            val bytes = File(sourcePath).readBytes()
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val parent = File(targetPath).parent ?: return@withContext Result.failure(Exception("Invalid target path"))
            shizukuExec("mkdir", "-p", parent)
            val escapedPath = encodedPath.replace("'", "'\\''")
            for (chunk in encoded.chunked(4096)) {
                try {
                    shizukuExec("sh", "-c", "printf '%s' '${chunk.replace("'", "'\\''")}' >> '$escapedPath'")
                } catch (e: Exception) {
                    shizukuExec("sh", "-c", "rm -f '$escapedPath'")
                    return@withContext Result.failure(e)
                }
            }
            shizukuExec("sh", "-c", "base64 -d '$escapedPath' > '${targetPath.replace("'", "'\\''")}'; rm -f '$escapedPath'")
            Result.success("Pushed to $targetPath")
        } catch (e: Exception) {
            shizukuExec("sh", "-c", "rm -f '$encodedPath'")
            Result.failure(e)
        }
    }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val out = shizukuExec("mkdir", "-p", dirPath)
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fileExists(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val out = shizukuExec("sh", "-c", "test -f '$path' && echo 1 || echo 0")
            Result.success(out.trim() == "1")
        } catch (_: Exception) {
            Result.success(false)
        }
    }

    override suspend fun listDirectory(path: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val out = shizukuExec("ls", "-1", path)
            Result.success(out.trim().lines().filter { it.isNotBlank() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun backupFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val backupPath = "${path}.backup_${System.currentTimeMillis()}"
            shizukuExec("cp", path, backupPath)
            Result.success(backupPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun readFile(path: String): Result<String> = withContext(Dispatchers.IO) {
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
        val clazz = Class.forName("rikka.shizuku.Shizuku")
        val method = clazz.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        try { method.isAccessible = true } catch (_: Exception) {}
        val process = method.invoke(null, cmd, null, null) as java.lang.Process

        val stdout = readStream(process.inputStream)
        val stderr = readStream(process.errorStream)

        val exited = process.waitFor(15, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            throw Exception("Command timed out after 15s")
        }

        return ExecResult(stdout, stderr, process.exitValue())
    }

    private fun readStream(stream: InputStream): String {
        return BufferedReader(InputStreamReader(stream)).readText()
    }

    private fun filterPermissionDenied(output: String): String {
        val deniedLines = output.lines().filter { it.contains("Permission denied", ignoreCase = true) }
        return if (deniedLines.isNotEmpty()) deniedLines.joinToString("\n") else output
    }
}
