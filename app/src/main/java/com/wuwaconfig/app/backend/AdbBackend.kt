package com.wuwaconfig.app.backend

import android.util.Base64
import com.wuwaconfig.app.adb.AdbClient
import com.wuwaconfig.app.adb.AdbCrypto
import com.wuwaconfig.app.adb.PortScanner
import java.io.File

class AdbBackend(private val crypto: AdbCrypto) : AccessBackend {
    private val client = AdbClient(crypto)
    override val isConnected: Boolean get() = client.isConnected

    override suspend fun connect(): Result<Unit> {
        val port = PortScanner.scanForAdb()
        if (port == 0) {
            return Result.failure(Exception("ADB daemon not found. Enable Wireless Debugging."))
        }
        return client.connect(port)
    }

    override fun disconnect() = client.disconnect()
    override suspend fun executeShellCommand(command: String) = client.executeShellCommand(command)
    override suspend fun pushFile(sourcePath: String, targetPath: String): Result<String> {
        return try {
            val encoded = Base64.encodeToString(File(sourcePath).readBytes(), Base64.NO_WRAP)
            val encodedPath = "$targetPath.wuwap42.b64"
            client.executeShellCommand(": > ${shellQuote(encodedPath)}").getOrThrow()
            encoded.chunked(4096).forEach { chunk ->
                client.executeShellCommand("printf %s ${shellQuote(chunk)} >> ${shellQuote(encodedPath)}").getOrThrow()
            }
            client.executeShellCommand(
                "base64 -d ${shellQuote(encodedPath)} > ${shellQuote(targetPath)} && rm -f ${shellQuote(encodedPath)}"
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    override suspend fun ensureDirectoryExists(dirPath: String) = client.ensureDirectoryExists(dirPath)
    override suspend fun fileExists(path: String) = client.fileExists(path)
    override suspend fun listDirectory(path: String) = client.listDirectory(path)
    override suspend fun backupFile(path: String) = client.backupFile(path)
    override suspend fun readFile(path: String): Result<String> = executeShellCommand("cat \"$path\"")

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}
