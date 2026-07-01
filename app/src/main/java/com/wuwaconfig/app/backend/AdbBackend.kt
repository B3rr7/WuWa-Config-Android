package com.wuwaconfig.app.backend

import android.util.Base64
import com.wuwaconfig.app.adb.AdbClient
import com.wuwaconfig.app.adb.AdbCrypto
import com.wuwaconfig.app.adb.PortScanner
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import java.io.File
import java.security.MessageDigest

class AdbBackend(private val crypto: AdbCrypto) : AccessBackend {
    private val client = AdbClient(crypto)
    override val isConnected: Boolean get() = client.isConnected

    companion object {
        private const val GAME_PKG = "com.kurogame.wutheringwaves.global"
    }

    override suspend fun connect(): Result<Unit> {
        LogRepository.add("ADB connect: scanning for ADB port...")
        val scan = PortScanner.scanForAdb()
        if (scan == null) {
            LogRepository.add("ADB connect failed: port not found", LogLevel.ERROR)
            return Result.failure(Exception("ADB port not found. Enable Wireless Debugging and tap Connect, or enter IP:port manually."))
        }
        LogRepository.add("ADB connect: found port ${scan.port} on ${scan.host}")
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

    suspend fun connectTo(host: String, port: Int): Result<Unit> {
        LogRepository.add("ADB connectTo: $host:$port")
        val first = client.connect(port, host)
        if (first.isSuccess) {
            LogRepository.add("ADB connected to $host:$port", LogLevel.SUCCESS)
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
        val result = client.executeShellCommand(command)
        if (result.isFailure && result.exceptionOrNull()?.message?.contains("Permission denied") == true) {
            LogRepository.add("ADB shell permission denied, retrying with run-as", LogLevel.WARNING)
            val alt = client.executeShellCommandWithRunAs(GAME_PKG, command)
            if (alt.isSuccess) {
                LogRepository.add("ADB shell succeeded via run-as", LogLevel.SUCCESS)
                return alt
            }
        }
        if (result.isFailure) {
            LogRepository.add("ADB shell failed: ${result.exceptionOrNull()?.message}", LogLevel.ERROR)
        }
        return result
    }

    override suspend fun pushFile(sourcePath: String, targetPath: String): Result<String> {
        LogRepository.add("ADB push: $sourcePath -> $targetPath")
        val sourceFile = File(sourcePath)
        val bytes = sourceFile.readBytes()
        val localMd5 = computeMd5(sourceFile)
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val encodedPath = "/data/local/tmp/wuwaconfig_${System.currentTimeMillis()}_${(0..9999).random()}.b64"
        val parent = File(targetPath).parent ?: return Result.failure(Exception("Invalid target path"))

        suspend fun doPush(): Result<String> {
            val mkdirCmd = "mkdir -p ${shQuote(parent)}"
            val mkdirResult = client.executeShellCommand(mkdirCmd)
            if (mkdirResult.isFailure) {
                client.executeShellCommandWithRunAs(GAME_PKG, mkdirCmd)
            }
            client.executeShellCommand("rm -f ${shQuote(encodedPath)}")
            val chunkSize = maxPushChunkSize(encodedPath)
            val chunks = encoded.chunked(chunkSize)
            for ((i, chunk) in chunks.withIndex()) {
                val redirect = if (i == 0) ">" else ">>"
                var lastErr: Result<String>? = null
                for (attempt in 0..PUSH_RETRY_COUNT) {
                    val appendCmd = "printf '%s' ${shQuote(chunk)} $redirect ${shQuote(encodedPath)}"
                    val r = client.executeShellCommand(appendCmd)
                    if (r.isSuccess) {
                        lastErr = null
                        break
                    }
                    lastErr = r
                }
                if (lastErr != null) {
                    client.executeShellCommand("rm -f ${shQuote(encodedPath)}")
                    return lastErr
                }
            }
            val decodeResult = client.executeShellCommand(
                "base64 -d ${shQuote(encodedPath)} > ${shQuote(targetPath)}; rm -f ${shQuote(encodedPath)}"
            )
            if (decodeResult.isFailure) return decodeResult
            val verifyCmd = "md5sum ${shQuote(targetPath)} 2>/dev/null | cut -d' ' -f1"
            val remoteMd5 = client.executeShellCommand(verifyCmd).getOrNull()?.trim()
            if (remoteMd5 != null && remoteMd5.length == 32) {
                if (remoteMd5 != localMd5) {
                    client.executeShellCommand("rm -f ${shQuote(targetPath)}")
                    return Result.failure(Exception("MD5 mismatch after push: local=$localMd5 remote=$remoteMd5"))
                }
            } else {
                val sizeCmd = "wc -c < ${shQuote(targetPath)} 2>/dev/null"
                val remoteSize = client.executeShellCommand(sizeCmd).getOrNull()?.trim()?.toLongOrNull()
                if (remoteSize != null && remoteSize != bytes.size.toLong()) {
                    client.executeShellCommand("rm -f ${shQuote(targetPath)}")
                    return Result.failure(Exception("Size mismatch after push: local=${bytes.size} remote=$remoteSize"))
                }
            }
            LogRepository.add("ADB push completed: $targetPath", LogLevel.SUCCESS)
            return Result.success("Pushed to $targetPath")
        }

        return try {
            var lastError: Result<String>? = null
            for (attempt in 0..PUSH_RETRY_COUNT) {
                val result = doPush()
                if (result.isSuccess) return result
                lastError = result
                client.executeShellCommand("rm -f ${shQuote(encodedPath)}; rm -f ${shQuote(targetPath)}")
            }
            LogRepository.add("ADB push failed after retries: ${lastError?.exceptionOrNull()?.message}", LogLevel.ERROR)
            lastError ?: Result.failure(Exception("Push failed"))
        } catch (e: Exception) {
            LogRepository.add("ADB push exception: ${e.message}", LogLevel.ERROR)
            client.executeShellCommand("rm -f ${shQuote(encodedPath)}")
            Result.failure(e)
        }
    }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> {
        val result = client.ensureDirectoryExists(dirPath)
        if (result.isFailure) {
            return client.executeShellCommandWithRunAs(GAME_PKG, "mkdir -p ${shQuote(dirPath)}")
        }
        return result
    }

    override suspend fun fileExists(path: String): Result<Boolean> {
        val result = client.fileExists(path)
        if (result.isFailure) {
            val alt = client.executeShellCommandWithRunAs(GAME_PKG, "test -f ${shQuote(path)} && echo 1 || echo 0")
            return alt.map { it.trim() == "1" }
        }
        return result
    }

    override suspend fun listDirectory(path: String): Result<List<String>> {
        val result = client.listDirectory(path)
        if (result.isFailure) {
            val alt = client.executeShellCommandWithRunAs(GAME_PKG, "ls -1 ${shQuote(path)} 2>/dev/null")
            return alt.map { output -> output.trim().lines().filter { it.isNotBlank() } }
        }
        return result
    }

    override suspend fun backupFile(path: String): Result<String> {
        val result = client.backupFile(path)
        if (result.isFailure) {
            val backupPath = "${path}.backup_${System.currentTimeMillis()}"
            return client.executeShellCommandWithRunAs(GAME_PKG, "cp ${shQuote(path)} ${shQuote(backupPath)}")
        }
        return result
    }

    override suspend fun readFile(path: String): Result<String> {
        val result = client.executeShellCommand("cat ${shQuote(path)}")
        if (result.isFailure) {
            return client.executeShellCommandWithRunAs(GAME_PKG, "cat ${shQuote(path)}")
        }
        return result
    }

}
