package com.wuwaconfig.app.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RootBackend : AccessBackend {
    override var isConnected: Boolean = false
        private set

    override suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "echo ROOT_OK")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exited = process.waitFor(10, TimeUnit.SECONDS)
            if (!exited) { process.destroyForcibly(); return@withContext Result.failure(Exception("Root check timed out")) }
            val exitCode = process.exitValue()
            if (exitCode == 0 && output == "ROOT_OK") {
                isConnected = true
                Result.success(Unit)
            } else {
                Result.failure(Exception(output.ifBlank { "Root access denied" }))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Root not available: ${e.message}"))
        }
    }

    override fun disconnect() {
        isConnected = false
    }


    override suspend fun executeShellCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exited = process.waitFor(10, TimeUnit.SECONDS)
            if (!exited) { process.destroyForcibly(); return@withContext Result.failure(Exception("Command timed out: $command")) }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                Result.failure(Exception(output.trim().ifEmpty { "Command failed with exit code $exitCode" }))
            } else {
                Result.success(output.trim())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun pushFile(sourcePath: String, targetPath: String): Result<String> {
        return executeShellCommand("cp \"$sourcePath\" \"$targetPath\"")
    }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> {
        return executeShellCommand("mkdir -p \"$dirPath\"")
    }

    override suspend fun fileExists(path: String): Result<Boolean> {
        val result = executeShellCommand("test -f \"$path\" && echo 1 || echo 0")
        return result.map { it.trim() == "1" }
    }

    override suspend fun listDirectory(path: String): Result<List<String>> {
        val result = executeShellCommand("ls -1 \"$path\" 2>/dev/null")
        return result.map { output ->
            output.trim().lines().filter { it.isNotBlank() }
        }
    }

    override suspend fun backupFile(path: String): Result<String> {
        val backupPath = "${path}.backup_${System.currentTimeMillis()}"
        return executeShellCommand("cp \"$path\" \"$backupPath\"")
    }

    override suspend fun readFile(path: String): Result<String> {
        return executeShellCommand("cat \"$path\"")
    }
}
