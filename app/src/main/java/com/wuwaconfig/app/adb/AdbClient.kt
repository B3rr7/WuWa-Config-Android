package com.wuwaconfig.app.adb

import android.util.Log
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

    @Volatile
    private var connected: Boolean = false

    private val localIdCounter = AtomicInteger(100)

    private val instanceId = System.identityHashCode(this)

    val isConnected: Boolean get() = connected

    suspend fun connect(
        port: Int,
        host: String = "127.0.0.1",
        readTimeoutMs: Int = 60000,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                Log.d("AdbClient", "connect[$instanceId]: opening socket to $host:$port")
                socket =
                    Socket().apply {
                        connect(InetSocketAddress(host, port), 7000)
                        soTimeout = readTimeoutMs
                        keepAlive = true
                    }
                input = socket!!.getInputStream()
                output = socket!!.getOutputStream()
                Log.d("AdbClient", "connect[$instanceId]: socket opened, authenticating")
                val result = authenticate()
                Log.d("AdbClient", "connect[$instanceId]: auth result = ${result.isSuccess}")
                if (result.isSuccess) {
                    connected = true
                    Log.d("AdbClient", "connect[$instanceId]: SUCCESS")
                    Result.success(Unit)
                } else {
                    Log.d("AdbClient", "connect[$instanceId]: auth failed: ${result.exceptionOrNull()?.message}")
                    disconnect()
                    result
                }
            } catch (e: Exception) {
                Log.d("AdbClient", "connect[$instanceId]: exception: $e")
                disconnect()
                Result.failure(e)
            }
        }

    private fun authenticate(): Result<Unit> {
        try {
            val out = output ?: return Result.failure(Exception("ADB output not initialized"))
            val inp = input ?: return Result.failure(Exception("ADB input not initialized"))
            val cnxn = AdbProtocol.createConnectionMessage()
            AdbProtocol.writeMessage(out, cnxn)
            var signatureAttempts = 0
            val MAX_SIGNATURE_ATTEMPTS = 2
            var publicKeySent = false
            var authAttempts = 0

            while (true) {
                val message =
                    AdbProtocol.readMessage(inp)
                        ?: return Result.failure(Exception("No response from ADB daemon"))

                when {
                    message.command.contentEquals(AdbProtocol.CNXN) -> {
                        Log.d("AdbClient", "auth[$instanceId]: received CNXN (authorized)")
                        return Result.success(Unit)
                    }
                    message.command.contentEquals(AdbProtocol.STLS) -> {
                        Log.w("AdbClient", "auth[$instanceId]: device requested TLS (STLS) — not supported, falling back")
                        return Result.failure(Exception("Device requires TLS connection (Android 14+). Try Shizuku or Root backend."))
                    }
                    message.command.contentEquals(AdbProtocol.AUTH) -> {
                        authAttempts++
                        if (signatureAttempts < MAX_SIGNATURE_ATTEMPTS) {
                            signatureAttempts++
                            Log.d("AdbClient", "auth[$instanceId]: AUTH challenge #$signatureAttempts, signing token")
                            val signature = crypto.signToken(message.payload)
                            AdbProtocol.writeMessage(out, AdbProtocol.createAuthSignatureMessage(signature))
                        } else if (!publicKeySent) {
                            Log.d("AdbClient", "auth[$instanceId]: signature rejected, sending public key")
                            publicKeySent = true
                            AdbProtocol.writeMessage(out, AdbProtocol.createAuthPublicKeyMessage(crypto.getAdbFormattedPublicKey()))
                        } else if (authAttempts < 8) {
                            Log.d("AdbClient", "auth[$instanceId]: re-sending public key (attempt $authAttempts)")
                            AdbProtocol.writeMessage(out, AdbProtocol.createAuthPublicKeyMessage(crypto.getAdbFormattedPublicKey()))
                        } else {
                            return Result.failure(
                                Exception("Authorization rejected. Check notification shade and accept the RSA fingerprint dialog."),
                            )
                        }
                    }
                    else -> {
                        Log.d("AdbClient", "auth[$instanceId]: unexpected cmd=${String(message.command)}")
                        return Result.failure(Exception("Unexpected message: ${String(message.command)}"))
                    }
                }
            }
        } catch (e: Exception) {
            disconnect()
            return Result.failure(Exception("ADB auth failed: ${e.message}"))
        }
    }

    suspend fun connectWithRegeneratedKeys(
        port: Int,
        host: String = "127.0.0.1",
        readTimeoutMs: Int = 60000,
    ): Result<Unit> {
        Log.d("AdbClient", "connectWithRegeneratedKeys[$instanceId]: regenerating RSA keys")
        crypto.regenerateKeys()
        return connect(port, host, readTimeoutMs)
    }

    suspend fun executeShellCommand(command: String): Result<String> =
        withContext(Dispatchers.IO) {
            Log.d("AdbClient", "shell[$instanceId]: connected=$connected cmd=$command")
            if (!connected) return@withContext Result.failure(Exception("Not connected to ADB"))
            val out = output ?: return@withContext Result.failure(Exception("ADB output not initialized"))
            val inp = input ?: return@withContext Result.failure(Exception("ADB input not initialized"))
            try {
                val localId = localIdCounter.getAndIncrement()
                AdbProtocol.writeMessage(out, AdbProtocol.createOpenMessage(localId, "shell:$command"))

                val response = StringBuilder()
                var remoteId = 0

                loop@ while (true) {
                    val message = AdbProtocol.readMessage(inp) ?: break
                    when {
                        message.command.contentEquals(AdbProtocol.OKAY) -> {
                            if (remoteId == 0) remoteId = message.arg0
                        }
                        message.command.contentEquals(AdbProtocol.WRTE) -> {
                            // Only process messages for our stream
                            if (remoteId > 0 && message.arg1 != localId) continue@loop
                            if (remoteId == 0) remoteId = message.arg1
                            response.append(String(message.payload, Charsets.UTF_8))
                            AdbProtocol.writeMessage(out, AdbProtocol.createOkMessage(message.arg1, message.arg0))
                        }
                        message.command.contentEquals(AdbProtocol.CLSE) -> {
                            // ADB daemon may send WRTE after CLSE (pipe buffer drain race).
                            // Only break for our stream, then drain trailing messages.
                            if (remoteId == 0 || message.arg1 == localId) {
                                drainTrailingWrite(localId, response)
                                break@loop
                            }
                        }
                    }
                }

                val result = response.toString()
                Log.d("AdbClient", "shell[$instanceId]: result='${result.take(200)}'")
                Result.success(result)
            } catch (e: Exception) {
                Log.d("AdbClient", "shell[$instanceId]: exception: $e")
                connected = false
                Result.failure(e)
            }
        }

    private fun drainTrailingWrite(
        localId: Int,
        response: StringBuilder,
    ) {
        val sock = socket ?: return
        val out = output ?: return
        val inp = input ?: return
        val originalTimeout = sock.soTimeout
        var remoteId = 0
        try {
            sock.soTimeout = 500
            var iterations = 0
            while (iterations < 100) {
                iterations++
                val msg = AdbProtocol.readMessage(inp) ?: break
                when {
                    msg.command.contentEquals(AdbProtocol.WRTE) && msg.arg1 == localId -> {
                        if (remoteId == 0) remoteId = msg.arg0
                        response.append(String(msg.payload, Charsets.UTF_8))
                        AdbProtocol.writeMessage(out, AdbProtocol.createOkMessage(msg.arg1, msg.arg0))
                    }
                    msg.command.contentEquals(AdbProtocol.CLSE) && msg.arg1 == localId -> {
                        if (remoteId == 0) remoteId = msg.arg0
                        break
                    }
                    else -> break
                }
            }
        } catch (_: java.net.SocketTimeoutException) {
        } finally {
            sock.soTimeout = originalTimeout
        }
    }

    suspend fun executeShellCommandWithRunAs(
        pkg: String,
        command: String,
    ): Result<String> {
        return executeShellCommand("run-as $pkg $command")
    }

    suspend fun pushFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> {
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
        val backupPath = "$path.backup_${System.currentTimeMillis()}"
        return executeShellCommand("cp ${shQuote(path)} ${shQuote(backupPath)}")
    }

    suspend fun listDirectory(path: String): Result<List<String>> {
        val result = executeShellCommand("ls -1 ${shQuote(path)} 2>/dev/null")
        return result.map { output ->
            output.trim().lines().filter { it.isNotBlank() }
        }
    }

    fun disconnect() {
        Log.d("AdbClient", "disconnect[$instanceId]")
        connected = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        try {
            input?.close()
        } catch (_: Exception) {
        }
        try {
            output?.close()
        } catch (_: Exception) {
        }
        socket = null
        input = null
        output = null
    }

    private fun shQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}
