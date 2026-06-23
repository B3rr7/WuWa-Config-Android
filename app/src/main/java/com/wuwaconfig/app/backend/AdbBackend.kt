package com.wuwaconfig.app.backend

import android.util.Base64
import com.wuwaconfig.app.adb.AdbClient
import com.wuwaconfig.app.adb.AdbCrypto
import com.wuwaconfig.app.adb.PortScanner
import java.io.File

class AdbBackend(private val crypto: AdbCrypto) : AccessBackend {
    private val client = AdbClient(crypto)
    override val isConnected: Boolean get() = client.isConnected

    companion object {
        private const val GAME_PKG = "com.kurogame.wutheringwaves.global"
    }

    override suspend fun connect(): Result<Unit> {
        val scan = PortScanner.scanForAdb()
        if (scan == null) {
            return Result.failure(Exception("ADB port not found. Enable Wireless Debugging and tap Connect, or enter IP:port manually."))
        }
        val first = client.connect(scan.port, scan.host)
        if (first.isSuccess) return first
        val msg = first.exceptionOrNull()?.message ?: ""
        if (msg.contains("Authorization") || msg.contains("auth")) {
            return client.connectWithRegeneratedKeys(scan.port, scan.host)
        }
        return first
    }

    suspend fun connectTo(host: String, port: Int): Result<Unit> {
        val first = client.connect(port, host)
        if (first.isSuccess) return first
        val msg = first.exceptionOrNull()?.message ?: ""
        if (msg.contains("Authorization") || msg.contains("auth")) {
            return client.connectWithRegeneratedKeys(port, host)
        }
        return first
    }

    override fun disconnect() = client.disconnect()

    override suspend fun executeShellCommand(command: String): Result<String> {
        val result = client.executeShellCommand(command)
        if (result.isFailure && result.exceptionOrNull()?.message?.contains("Permission denied") == true) {
            val alt = client.executeShellCommandWithRunAs(GAME_PKG, command)
            if (alt.isSuccess) return alt
        }
        return result
    }

    override suspend fun pushFile(sourcePath: String, targetPath: String): Result<String> {
        val encodedPath = "/data/local/tmp/wuwaconfig_${System.currentTimeMillis()}_${(0..9999).random()}.b64"
        return try {
            val bytes = File(sourcePath).readBytes()
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val parent = File(targetPath).parent ?: return Result.failure(Exception("Invalid target path"))
            val mkdirCmd = "mkdir -p ${shQuote(parent)}"
            val mkdirResult = client.executeShellCommand(mkdirCmd)
            if (mkdirResult.isFailure) {
                client.executeShellCommandWithRunAs(GAME_PKG, mkdirCmd)
            }
            encoded.chunked(4096).forEach { chunk ->
                val appendCmd = "printf '%s' ${shQuote(chunk)} >> ${shQuote(encodedPath)}"
                val r = client.executeShellCommand(appendCmd)
                if (r.isFailure) {
                    client.executeShellCommand("rm -f ${shQuote(encodedPath)}")
                    return r
                }
            }
            client.executeShellCommand(
                "base64 -d ${shQuote(encodedPath)} > ${shQuote(targetPath)}; rm -f ${shQuote(encodedPath)}"
            )
        } catch (e: Exception) {
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

    private fun shQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}
