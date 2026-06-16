package com.wuwaconfig.app.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class AdbClient(private val crypto: AdbCrypto) {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var connected: Boolean = false
    private val localIdCounter = AtomicInteger(100)

    val isConnected: Boolean get() = connected

    suspend fun connect(port: Int, host: String = "127.0.0.1", readTimeoutMs: Int = 30000): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            socket = Socket().apply {
                connect(InetSocketAddress(host, port), 7000)
                soTimeout = readTimeoutMs
                keepAlive = true
            }
            input = socket!!.getInputStream()
            output = socket!!.getOutputStream()
            val result = authenticate()
            if (result.isSuccess) connected = true
            result
        } catch (e: Exception) {
            disconnect()
            Result.failure(e)
        }
    }

    private fun authenticate(): Result<Unit> {
        try {
            val cnxn = AdbProtocol.createConnectionMessage()
            AdbProtocol.writeMessage(output!!, cnxn)
            var triedSignature = false

            while (true) {
                val message = AdbProtocol.readMessage(input!!)
                    ?: return Result.failure(Exception("No response from ADB daemon"))

                when {
                    message.command.contentEquals(AdbProtocol.CNXN) -> {
                        return Result.success(Unit)
                    }
                    message.command.contentEquals(AdbProtocol.AUTH) -> {
                        if (!triedSignature) {
                            triedSignature = true
                            val signature = crypto.signToken(message.payload)
                            AdbProtocol.writeMessage(output!!, AdbProtocol.createAuthSignatureMessage(signature))
                        } else {
                            AdbProtocol.writeMessage(output!!, AdbProtocol.createAuthPublicKeyMessage(crypto.getAdbFormattedPublicKey()))
                        }
                    }
                    else -> return Result.failure(Exception("Unexpected message: ${String(message.command)}"))
                }
            }
        } catch (e: Exception) {
            disconnect()
            return Result.failure(Exception("ADB auth failed: ${e.message}"))
        }
    }

    suspend fun executeShellCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        if (!connected) return@withContext Result.failure(Exception("Not connected to ADB"))
        try {
            val localId = localIdCounter.getAndIncrement()
            AdbProtocol.writeMessage(output!!, AdbProtocol.createOpenMessage(localId, "shell:$command"))

            val response = StringBuilder()
            var remoteId = 0
            var stderr = StringBuilder()

            loop@ while (true) {
                val message = AdbProtocol.readMessage(input!!) ?: break
                when {
                    message.command.contentEquals(AdbProtocol.OKAY) -> {
                        if (remoteId == 0) remoteId = message.arg0
                    }
                    message.command.contentEquals(AdbProtocol.WRTE) -> {
                        if (remoteId == 0) remoteId = message.arg1
                        val text = String(message.payload, Charsets.UTF_8)
                        if (text.contains("Permission denied", ignoreCase = true) ||
                            text.contains("cannot open", ignoreCase = true)) {
                            stderr.append(text)
                        }
                        response.append(text)
                        AdbProtocol.writeMessage(output!!, AdbProtocol.createOkMessage(message.arg1, message.arg0))
                    }
                    message.command.contentEquals(AdbProtocol.CLSE) -> {
                        break@loop
                    }
                }
            }

            val result = response.toString()
            if (stderr.isNotEmpty()) {
                Result.failure(Exception(stderr.toString().trim()))
            } else {
                Result.success(result)
            }
        } catch (e: Exception) {
            connected = false
            Result.failure(e)
        }
    }

    suspend fun executeShellCommandWithRunAs(pkg: String, command: String): Result<String> {
        return executeShellCommand("run-as $pkg $command")
    }

    suspend fun pushFile(sourcePath: String, targetPath: String): Result<String> {
        return executeShellCommand("cp \"$sourcePath\" \"$targetPath\"")
    }

    suspend fun ensureDirectoryExists(dirPath: String): Result<String> {
        return executeShellCommand("mkdir -p ${shQuote(dirPath)}")
    }

    suspend fun fileExists(path: String): Result<Boolean> {
        val result = executeShellCommand("test -f ${shQuote(path)} && echo 1 || echo 0")
        return result.map { it.trim() == "1" }
    }

    suspend fun backupFile(path: String): Result<String> {
        val backupPath = "${path}.backup_${System.currentTimeMillis()}"
        return executeShellCommand("cp ${shQuote(path)} ${shQuote(backupPath)}")
    }

    suspend fun listDirectory(path: String): Result<List<String>> {
        val result = executeShellCommand("ls -1 ${shQuote(path)} 2>/dev/null")
        return result.map { output ->
            output.trim().lines().filter { it.isNotBlank() }
        }
    }

    fun disconnect() {
        connected = false
        try { socket?.close() } catch (_: Exception) {}
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        socket = null; input = null; output = null
    }

    private fun shQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}
