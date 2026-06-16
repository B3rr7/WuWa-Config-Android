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
            val output = shizukuExec("sh", "-c", command)
            val filtered = filterPermissionDenied(output)
            if (filtered != output) {
                return@withContext Result.failure(Exception(filtered.trim().ifEmpty { "Command failed" }))
            }
            Result.success(output.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pushFile(sourcePath: String, targetPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = File(sourcePath).readBytes()
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val encodedPath = "$targetPath.wuwap42.b64"
            val parent = File(targetPath).parent ?: return@withContext Result.failure(Exception("Invalid target path"))
            shizukuExec("mkdir", "-p", parent)
            encoded.chunked(4096).forEach { chunk ->
                shizukuExec("sh", "-c", "printf '%s' '${chunk.replace("'", "'\\''")}' >> '${encodedPath.replace("'", "'\\''")}'")
            }
            shizukuExec("sh", "-c", "base64 -d '${encodedPath.replace("'", "'\\''")}' > '${targetPath.replace("'", "'\\''")}' && rm -f '${encodedPath.replace("'", "'\\''")}'")
            Result.success("Pushed to $targetPath")
        } catch (e: Exception) {
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

    private fun shizukuExec(vararg cmd: String): String {
        val clazz = Class.forName("rikka.shizuku.Shizuku")
        val method = clazz.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        method.isAccessible = true
        val remoteProcess = method.invoke(null, cmd, null, null)

        val stdout = readStream(remoteProcess.javaClass.getMethod("getInputStream").invoke(remoteProcess) as InputStream)
        val stderr = readStream(remoteProcess.javaClass.getMethod("getErrorStream").invoke(remoteProcess) as InputStream)

        remoteProcess.javaClass.getMethod("waitFor").invoke(remoteProcess)

        if (stderr.isNotBlank()) throw Exception(stderr.trim())
        return stdout
    }

    private fun readStream(stream: InputStream): String {
        return BufferedReader(InputStreamReader(stream)).readText()
    }

    private fun filterPermissionDenied(output: String): String {
        val deniedLines = output.lines().filter { it.contains("Permission denied", ignoreCase = true) }
        return if (deniedLines.isNotEmpty()) deniedLines.joinToString("\n") else output
    }
}
