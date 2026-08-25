package com.wuwaconfig.app.adb

import android.util.Log
import com.wuwaconfig.app.backend.shQuote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AdbClient(private val crypto: AdbCrypto) {
    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var input: InputStream? = null

    @Volatile
    private var output: OutputStream? = null

    @Volatile
    private var connected: Boolean = false

    private val localIdCounter = AtomicInteger(100)

    /**
     * Bumped on every successful connect so a stale keepalive failure (from the
     * previous connection) cannot mark a fresh connection as disconnected.
     */
    private val generation = AtomicLong(0)

    private val instanceId = System.identityHashCode(this)

    private var keepaliveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepaliveJob: Job? = null

    @Volatile
    private var lastActivityMs = 0L

    private val txMutex = Mutex()

    val isConnected: Boolean get() = connected

    private fun startKeepalive() {
        lastActivityMs = System.currentTimeMillis()
        keepaliveJob?.cancel()
        keepaliveScope.cancel()
        keepaliveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val connGen = generation.get()
        keepaliveJob =
            keepaliveScope.launch {
                while (isActive && connected) {
                    delay(15_000L)
                    if (!connected) break
                    if (System.currentTimeMillis() - lastActivityMs > 25_000L) {
                        Log.d("AdbClient", "keepalive[$instanceId]: sending heartbeat")
                        val sock = socket
                        if (sock != null && sock.isConnected && !sock.isClosed) {
                            executeShellCommand("echo ping").onFailure {
                                // Only mark disconnected if this heartbeat still belongs
                                // to the current connection.
                                if (generation.get() == connGen) {
                                    Log.w("AdbClient", "keepalive[$instanceId]: heartbeat failed, marking disconnected")
                                    connected = false
                                }
                            }
                        } else if (generation.get() == connGen) {
                            connected = false
                        }
                    }
                }
            }
    }

    private fun markActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    suspend fun connect(
        port: Int,
        host: String = "127.0.0.1",
        readTimeoutMs: Int = 60000,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            // Hold the transaction mutex for the whole handshake: without this a
            // concurrent executeShellCommand could observe half-swapped
            // socket/input/output fields mid-reconnect.
            txMutex.withLock {
                try {
                    Log.d("AdbClient", "connect[$instanceId]: opening socket to $host:$port")
                    socket =
                        Socket().apply {
                            connect(InetSocketAddress(host, port), 7000)
                            soTimeout = readTimeoutMs
                            keepAlive = true
                            tcpNoDelay = true
                        }
                    input = socket!!.getInputStream()
                    output = socket!!.getOutputStream()
                    Log.d("AdbClient", "connect[$instanceId]: socket opened, authenticating")
                    val result = authenticate()
                    Log.d("AdbClient", "connect[$instanceId]: auth result = ${result.isSuccess}")
                    if (result.isSuccess) {
                        connected = true
                        generation.incrementAndGet()
                        startKeepalive()
                        markActivity()
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
            // Wall-clock bound on the whole handshake: each round-trip is bounded by
            // the socket timeout, so an unbounded challenge loop could hang ~8 min.
            val deadlineMs = System.currentTimeMillis() + 30_000

            while (true) {
                if (System.currentTimeMillis() > deadlineMs) {
                    return Result.failure(Exception("ADB authorization timed out"))
                }
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
    ): Result<Unit> =
        // RSA key generation plus EncryptedFile writes are too heavy for the caller
        // (often Main) — keep them on IO.
        withContext(Dispatchers.IO) {
            Log.d("AdbClient", "connectWithRegeneratedKeys[$instanceId]: regenerating RSA keys")
            crypto.regenerateKeys()
            connect(port, host, readTimeoutMs)
        }

    suspend fun executeShellCommand(command: String): Result<String> =
        withContext(Dispatchers.IO) {
            Log.d("AdbClient", "shell[$instanceId]: connected=$connected cmd=$command")
            if (!connected) return@withContext Result.failure(Exception("Not connected to ADB"))
            txMutex.withLock {
                markActivity()
                val out = output ?: return@withLock Result.failure(Exception("ADB output not initialized"))
                val inp = input ?: return@withLock Result.failure(Exception("ADB input not initialized"))
                try {
                    val localId = localIdCounter.getAndIncrement()
                    AdbProtocol.writeMessage(out, AdbProtocol.createOpenMessage(localId, "shell:$command"))

                    val response = StringBuilder()
                    var remoteId = 0

                    loop@ while (true) {
                        val message = AdbProtocol.readMessage(inp)
                        if (message == null) {
                            Log.w("AdbClient", "shell[$instanceId]: stream ended prematurely before CLSE")
                            return@withLock Result.failure(Exception("ADB stream ended prematurely"))
                        }
                        when {
                            message.command.contentEquals(AdbProtocol.OKAY) -> {
                                if (remoteId == 0) remoteId = message.arg0
                            }
                            message.command.contentEquals(AdbProtocol.WRTE) -> {
                                // Only process messages for our stream
                                if (remoteId > 0 && message.arg1 != localId) continue@loop
                                // WRTE arg0 is the daemon's id for our stream (our remoteId);
                                // arg1 is our own localId.
                                if (remoteId == 0) remoteId = message.arg0
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
                    val socketDead =
                        when (e) {
                            is SocketException -> {
                                val msg = e.message?.lowercase() ?: ""
                                listOf("closed", "reset", "broken pipe", "connection").any { msg.contains(it) }
                            }
                            is SocketTimeoutException -> false
                            else -> false
                        }
                    Log.d("AdbClient", "shell[$instanceId]: exception: $e (socketDead=$socketDead)")
                    if (socketDead) {
                        disconnect()
                        return@withLock Result.failure(Exception("ADB connection lost"))
                    }
                    Result.failure(e)
                }
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
        try {
            sock.soTimeout = 500
            var iterations = 0
            while (iterations < 100) {
                iterations++
                val msg = AdbProtocol.readMessage(inp) ?: break
                when {
                    msg.command.contentEquals(AdbProtocol.WRTE) && msg.arg1 == localId -> {
                        response.append(String(msg.payload, Charsets.UTF_8))
                        AdbProtocol.writeMessage(out, AdbProtocol.createOkMessage(msg.arg1, msg.arg0))
                    }
                    msg.command.contentEquals(AdbProtocol.CLSE) && msg.arg1 == localId -> {
                        break
                    }
                    else -> break
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w("AdbClient", "drainTrailingWrite socket timeout: ${e.message}")
        } finally {
            sock.soTimeout = originalTimeout
        }
    }

    suspend fun executeShellCommandWithRunAs(
        pkg: String,
        command: String,
    ): Result<String> {
        // Nonce sentinel so command output that legitimately starts with a
        // fixed "EXIT:" token is not mangled by the filter below.
        val sentinel = "EXIT_${java.lang.Long.toString(System.nanoTime(), 36)}:"
        val result = executeShellCommand("run-as ${shQuote(pkg)} $command 2>/dev/null; echo ${sentinel}\$?")
        if (result.isSuccess) {
            val output = result.getOrThrow()
            val lines = output.lines()
            val exitLine = lines.lastOrNull { it.startsWith(sentinel) }
            if (exitLine != null) {
                val exitCode = exitLine.removePrefix(sentinel).trim().toIntOrNull() ?: -1
                if (exitCode != 0) {
                    val errorHint =
                        if (exitCode == 1) {
                            "Package not debuggable — run-as cannot be used for production apps. Use Shizuku or Root."
                        } else {
                            "run-as failed with exit code $exitCode"
                        }
                    return Result.failure(Exception("$errorHint (pkg=$pkg)"))
                }
                val cleanOut = lines.filterNot { it.startsWith(sentinel) }.joinToString("\n")
                return Result.success(cleanOut)
            }
        }
        return result
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
        keepaliveJob?.cancel()
        keepaliveScope.cancel()
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
}
