package com.wuwaconfig.app.adb

import android.util.Log
import com.wuwaconfig.app.backend.runAsCommand
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
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AdbClient(
    private val crypto: CryptoAdapter,
    private val socketFactory: (host: String, port: Int) -> Socket? = { _, _ -> Socket() },
) {
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

    private val keepaliveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var keepaliveJob: Job? = null

    @Volatile
    private var lastActivityMs = 0L

    private val txMutex = Mutex()

    /**
     * Guards the socket/input/output triple. [disconnect] is non-suspend, so it cannot take
     * [txMutex] without risking a lock-order inversion against [connect]; swapping the
     * fields out under this monitor instead guarantees a concurrent disconnect can never
     * null out (and leak) a socket that connect just installed.
     */
    private val connectionLock = Any()

    val isConnected: Boolean get() = connected

    private fun startKeepalive() {
        lastActivityMs = System.currentTimeMillis()
        keepaliveJob?.cancel()
        val connGen = generation.get()
        keepaliveJob =
            keepaliveScope.launch {
                while (isActive && connected) {
                    delay(KEEPALIVE_INTERVAL_MS)
                    if (!connected) break
                    if (System.currentTimeMillis() - lastActivityMs > KEEPALIVE_IDLE_MS) {
                        Log.d("AdbClient", "keepalive[$instanceId]: sending heartbeat")
                        if (txMutex.isLocked) {
                            Log.d("AdbClient", "keepalive[$instanceId]: skipping heartbeat — tx busy")
                            continue
                        }
                        val sock = socket
                        if (sock != null && sock.isConnected && !sock.isClosed) {
                            val originalTimeout = runCatching { sock.soTimeout }.getOrNull()
                            val probe =
                                try {
                                    // A half-dead device must not block all ADB work for the
                                    // connection-wide 60s read timeout on every heartbeat.
                                    runCatching { sock.soTimeout = HEARTBEAT_TIMEOUT_MS }
                                    executeShellCommand("echo $HEARTBEAT_PROBE")
                                } finally {
                                    if (originalTimeout != null) {
                                        runCatching { sock.soTimeout = originalTimeout }
                                    }
                                }
                            probe.onFailure {
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
                var sock: Socket? = null
                try {
                    Log.d("AdbClient", "connect[$instanceId]: opening socket to $host:$port")
                    // Create into a local first: if connect() or a socket setter throws,
                    // a `socket = ...` assignment would never happen and the fresh socket
                    // would leak an FD on every retry.
                    sock = socketFactory(host, port)
                        ?: return@withContext Result.failure(Exception("Connection refused: $host:$port"))
                    sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                    sock.soTimeout = readTimeoutMs
                    sock.keepAlive = true
                    sock.tcpNoDelay = true
                    val newInput = sock.getInputStream()
                    // Buffer the output: a frame is header + payload, and unbuffered
                    // writes cost two syscalls per frame.
                    val newOutput = AdbProtocol.wrapOutput(sock.getOutputStream())
                    synchronized(connectionLock) {
                        socket = sock
                        input = newInput
                        output = newOutput
                    }
                    Log.d("AdbClient", "connect[$instanceId]: socket opened, authenticating")
                    val result = authenticate()
                    Log.d("AdbClient", "connect[$instanceId]: auth result = ${result.isSuccess}")
                    if (result.isSuccess) {
                        // Bump the generation BEFORE publishing `connected`: a keepalive
                        // loop left over from the previous connection reads the generation
                        // and must already see the new value.
                        generation.incrementAndGet()
                        connected = true
                        // Ids only need to be unique within one connection; the counter
                        // would otherwise drift negative after enough reconnects.
                        localIdCounter.set(100)
                        startKeepalive()
                        markActivity()
                        Log.d("AdbClient", "connect[$instanceId]: SUCCESS")
                        Result.success(Unit)
                    } else {
                        Log.d("AdbClient", "connect[$instanceId]: auth failed: ${result.exceptionOrNull()?.message}")
                        disconnectLocked()
                        result
                    }
                } catch (e: Exception) {
                    Log.d("AdbClient", "connect[$instanceId]: exception: $e")
                    disconnectLocked()
                    runCatching { sock?.close() }
                    Result.failure(e)
                }
            }
        }

    /**
     * Android 11+ wireless *pairing*, addressed over loopback.
     *
     * This is what makes Local ADB possible at all on a stock phone, and it is the
     * mechanism LADB uses: with Wireless debugging on, adbd serves a short-lived
     * pairing port on every interface including 127.0.0.1, and completing the
     * handshake authorises this device's public key for good. After that the
     * transport port in 37000..44000 will complete a normal AUTH exchange with no
     * code, which is what [PortScanner] probes for.
     *
     * Without pairing, a stock phone rejects the connection outright and — unlike a
     * USB attach — shows no dialog, so the user is stuck with no clue why.
     *
     * Deliberately a separate socket rather than reusing the live one: pairing
     * addresses a different port, must not disturb an established session, and the
     * socket it uses is torn down by adbd as soon as pairing completes.
     */
    suspend fun pair(
        host: String,
        pairingPort: Int,
        pairingCode: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            var sock: Socket? = null
            try {
                val code = pairingCode.trim()
                if (code.isEmpty()) return@withContext Result.failure(Exception("Pairing code is empty"))
                sock = socketFactory(host, pairingPort)
                    ?: return@withContext Result.failure(Exception("Connection refused: $host:$pairingPort"))
                sock.connect(InetSocketAddress(host, pairingPort), CONNECT_TIMEOUT_MS)
                sock.soTimeout = PAIRING_TIMEOUT_MS

                // The pairing code travels in the CNXN banner, and the banner is
                // exactly the place `AdbCrypto.signToken` expects a peer token to be
                // hashed into — so a wrong code fails the signature below rather than
                // being silently accepted.
                val out = AdbProtocol.wrapOutput(sock.getOutputStream())
                val inp = sock.getInputStream()
                AdbProtocol.writeMessage(out, AdbProtocol.createConnectionMessage("host::pairing:$code"))

                val challenge = AdbProtocol.readMessage(inp)
                if (!PortScanner.isAuthChallenge(challenge)) {
                    return@withContext Result.failure(
                        Exception(
                            "Not a pairing challenge (got " +
                                (challenge?.let { AdbProtocol.hex(it.command) } ?: "no response") +
                                "). Check the port is the 'Pair device with pairing code' port.",
                        ),
                    )
                }
                AdbProtocol.writeMessage(
                    out,
                    AdbProtocol.createAuthSignatureMessage(crypto.signToken(challenge!!.payload)),
                )
                val response = AdbProtocol.readMessage(inp)
                if (response != null && response.command.contentEquals(AdbProtocol.CNXN)) {
                    Log.d("AdbClient", "pair: paired with $host:$pairingPort")
                    Result.success(Unit)
                } else {
                    Log.d("AdbClient", "pair: rejected by $host:$pairingPort")
                    Result.failure(Exception("Pairing rejected. The code is usually single-use and expires in seconds — reopen Wireless debugging and try again."))
                }
            } catch (e: Exception) {
                Log.d("AdbClient", "pair: exception: $e")
                Result.failure(e)
            } finally {
                runCatching { sock?.close() }
            }
        }

    private fun authenticate(): Result<Unit> {
        val sock = socket
        try {
            val out = output ?: return Result.failure(Exception("ADB output not initialized"))
            val inp = input ?: return Result.failure(Exception("ADB input not initialized"))
            val cnxn = AdbProtocol.createConnectionMessage()
            AdbProtocol.writeMessage(out, cnxn)
            var signatureAttempts = 0
            val MAX_SIGNATURE_ATTEMPTS = 2
            var publicKeySent = false
            var authAttempts = 0
            // Wall-clock bound on the whole handshake. The deadline below is clamped into
            // soTimeout before every read, because soTimeout bounds a SINGLE read — with the
            // connection-wide 60s value the "30s" budget could overshoot by minutes.
            val deadlineMs = System.currentTimeMillis() + AUTH_DEADLINE_MS

            while (true) {
                val remaining = deadlineMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    return Result.failure(Exception("ADB authorization timed out"))
                }
                if (sock != null) {
                    runCatching { sock.soTimeout = remaining.coerceIn(1, MAX_SO_TIMEOUT_MS).toInt() }
                }
                val message = AdbProtocol.readMessageOrThrow(inp)

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
                        val cmdHex = AdbProtocol.hex(message.command)
                        Log.d("AdbClient", "auth[$instanceId]: unexpected cmd=$cmdHex")
                        return Result.failure(Exception("Unexpected message from ADB daemon: $cmdHex"))
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: AdbProtocolException) {
            return Result.failure(Exception(e.message ?: "ADB auth failed"))
        } catch (e: Exception) {
            disconnectLocked()
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
                .onFailure { return@withContext Result.failure(it) }
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
                    // adbd's `shell:` service exits nonzero for EACCES but reports nothing
                    // on the wire, so without an exit-code sentinel "Permission denied"
                    // looked like success. Append one and map it back onto Result.
                    val wireCommand = withExitSentinel(command)
                    AdbProtocol.writeMessage(out, AdbProtocol.createOpenMessage(localId, "shell:$wireCommand"))

                    // Accumulate raw bytes and decode once at the end. adbd fragments
                    // the stream on arbitrary byte boundaries, so decoding each WRTE
                    // payload independently corrupts multi-byte (e.g. UTF-8) sequences.
                    val responseBytes = java.io.ByteArrayOutputStream()
                    var remoteId = 0
                    var totalBytes = 0
                    val startedMs = System.currentTimeMillis()

                    loop@ while (true) {
                        if (System.currentTimeMillis() - startedMs > SHELL_WALL_CLOCK_CAP_MS) {
                            // soTimeout bounds a single read, so a peer that trickles one
                            // byte per interval would otherwise hold txMutex forever.
                            return@withLock Result.failure(
                                Exception("ADB command exceeded ${SHELL_WALL_CLOCK_CAP_MS}ms (device is trickling data)"),
                            )
                        }
                        val message = AdbProtocol.readMessageOrThrow(inp)
                        when {
                            message.command.contentEquals(AdbProtocol.OKAY) -> {
                                // OKAY carries the daemon's id for our stream.
                                if (message.arg1 == localId) remoteId = message.arg0
                            }
                            message.command.contentEquals(AdbProtocol.WRTE) -> {
                                // Only process frames addressed to our stream. Frames for a
                                // different (leftover) stream must be ignored, not adopted.
                                if (message.arg1 != localId) continue@loop
                                remoteId = message.arg0
                                responseBytes.write(message.payload)
                                totalBytes += message.payload.size
                                if (totalBytes > MAX_RESPONSE_BYTES) {
                                    return@withLock Result.failure(
                                        Exception("ADB response exceeded $MAX_RESPONSE_BYTES bytes (${command.take(80)})"),
                                    )
                                }
                                AdbProtocol.writeMessage(out, AdbProtocol.createOkMessage(message.arg1, message.arg0))
                            }
                            message.command.contentEquals(AdbProtocol.CLSE) -> {
                                // Only end on a CLSE for our own stream. A foreign CLSE
                                // (e.g. from a previous, not-yet-drained stream) must be
                                // ignored or it would silently truncate our output.
                                if (message.arg1 == localId) {
                                    totalBytes = drainTrailingWrite(localId, responseBytes, totalBytes)
                                    break@loop
                                }
                            }
                            else -> {
                                Log.w("AdbClient", "shell[$instanceId]: ignoring ${AdbProtocol.hex(message.command)} frame")
                            }
                        }
                    }

                    // Close our side of the stream so the daemon can free it.
                    if (remoteId != 0) {
                        runCatching {
                            AdbProtocol.writeMessage(out, AdbProtocol.createCloseMessage(localId, remoteId))
                        }
                    }

                    val result = String(responseBytes.toByteArray(), Charsets.UTF_8)
                    val parsed = parseShellResult(result, command)
                    Log.d("AdbClient", "shell[$instanceId]: result='${result.take(200)}' success=${parsed.isSuccess}")
                    parsed
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: AdbProtocolException) {
                    Log.w("AdbClient", "shell[$instanceId]: protocol error: ${e.message}")
                    Result.failure(Exception(e.message ?: "ADB protocol error"))
                } catch (e: Exception) {
                    // A read/write timeout is a slow peer, not a dead link; every other
                    // IOException means the link is unusable and `connected` must not
                    // stay true on it.
                    val socketDead =
                        when (e) {
                            is SocketTimeoutException -> false
                            is IOException -> true
                            else -> false
                        }
                    Log.d("AdbClient", "shell[$instanceId]: exception: $e (socketDead=$socketDead)")
                    if (socketDead) {
                        disconnectLocked()
                        return@withLock Result.failure(Exception("ADB connection lost"))
                    }
                    Result.failure(e)
                }
            }
        }

    /**
     * Best-effort read of frames that raced the CLSE. adbd sends CLSE as the FINAL frame,
     * so this normally finds nothing and just waits out its (short) timeout. It must never
     * fail the command: it runs after a complete response, so any error here is logged and
     * dropped.
     */
    private fun drainTrailingWrite(
        localId: Int,
        response: java.io.ByteArrayOutputStream,
        alreadyRead: Int,
    ): Int {
        val sock = socket ?: return alreadyRead
        val out = output ?: return alreadyRead
        val inp = input ?: return alreadyRead
        val originalTimeout = runCatching { sock.soTimeout }.getOrNull() ?: return alreadyRead
        var total = alreadyRead
        try {
            sock.soTimeout = DRAIN_TIMEOUT_MS
            var iterations = 0
            while (iterations < 10 && total <= MAX_RESPONSE_BYTES) {
                iterations++
                val msg = AdbProtocol.readMessage(inp) ?: break
                when {
                    msg.command.contentEquals(AdbProtocol.WRTE) && msg.arg1 == localId -> {
                        response.write(msg.payload)
                        total += msg.payload.size
                        AdbProtocol.writeMessage(out, AdbProtocol.createOkMessage(msg.arg1, msg.arg0))
                    }
                    msg.command.contentEquals(AdbProtocol.CLSE) && msg.arg1 == localId -> {
                        break
                    }
                    else -> break
                }
            }
        } catch (e: IOException) {
            Log.w("AdbClient", "drainTrailingWrite: $e")
        } finally {
            runCatching { sock.soTimeout = originalTimeout }
        }
        return total
    }

    suspend fun executeShellCommandWithRunAs(
        pkg: String,
        command: String,
    ): Result<String> = executeShellCommand(runAsCommand(pkg, command))

    suspend fun ensureDirectoryExists(dirPath: String): Result<String> {
        return executeShellCommand("mkdir -p ${shQuote(dirPath)}")
    }

    suspend fun fileExists(path: String): Result<Boolean> {
        // `test -f ... && echo 1 || echo 0` always exits 0, so this stays a success even
        // on EACCES: the result is "absent", and callers that need the distinction use
        // the run-as variant.
        val result = executeShellCommand("test -f ${shQuote(path)} && echo 1 || echo 0")
        return result.map { it.trim() == "1" }
    }

    suspend fun backupFile(path: String): Result<String> {
        val backupPath = "$path.backup_${System.currentTimeMillis()}"
        val result = executeShellCommand("cp ${shQuote(path)} ${shQuote(backupPath)}")
        // cp prints nothing on success, so the command's stdout is useless here: report
        // where the backup actually went.
        return result.map { backupPath }
    }

    suspend fun listDirectory(path: String): Result<List<String>> {
        val result = executeShellCommand("ls -1 ${shQuote(path)} 2>/dev/null")
        return result.map { output ->
            output.trim().lines().filter { it.isNotBlank() }
        }
    }

    fun disconnect() {
        Log.d("AdbClient", "disconnect[$instanceId]")
        disconnectLocked()
    }

    private fun disconnectLocked() {
        connected = false
        keepaliveJob?.cancel()
        keepaliveJob = null
        // Swap the fields out under the monitor BEFORE closing: closing first would let a
        // concurrent connect() install a new socket that this method then nulls out,
        // leaking it forever.
        val (oldSocket, oldInput, oldOutput) =
            synchronized(connectionLock) {
                val s = socket
                val i = input
                val o = output
                socket = null
                input = null
                output = null
                Triple(s, i, o)
            }
        runCatching { oldSocket?.close() }
        runCatching { oldInput?.close() }
        runCatching { oldOutput?.close() }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 7000

        /**
         * A pairing handshake is user-timed: the code expires and the user has to
         * reopen the Wireless debugging dialog. 30s is generous enough for a
         * six-digit code and short enough that a wrong port does not hang the UI.
         */
        const val PAIRING_TIMEOUT_MS = 30_000
        const val MAX_SO_TIMEOUT_MS = 60_000L
        const val AUTH_DEADLINE_MS = 30_000L
        const val KEEPALIVE_INTERVAL_MS = 15_000L
        const val KEEPALIVE_IDLE_MS = 25_000L

        /** Short so a half-dead device cannot stall every other ADB operation. */
        const val HEARTBEAT_TIMEOUT_MS = 5000
        const val HEARTBEAT_PROBE = "ping"

        /** adbd sends CLSE last, so the drain is a race we expect to lose. */
        const val DRAIN_TIMEOUT_MS = 50

        /** Upper bound for a single shell transaction, independent of soTimeout. */
        const val SHELL_WALL_CLOCK_CAP_MS = 120_000L

        /** readFileBytes pipes arbitrary device files through this buffer. */
        const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024

        const val EXIT_PREFIX = "XWEXIT="
    }

    /** Appends an exit-code marker; `$?` reflects the whole compound command's status. */
    private fun withExitSentinel(command: String): String = "$command ; echo $EXIT_PREFIX\$?"

    /**
     * Anchored at the END of the output so a token appearing inside the command's own
     * output cannot spoof it. A missing marker (older daemon, or a peer that ignored the
     * command) is treated as success: we have no exit information at all.
     */
    private val exitMarkerRe = Regex("${Regex.escape(EXIT_PREFIX)}(\\d+)$")

    private fun parseShellResult(
        raw: String,
        command: String,
    ): Result<String> {
        val endTrimmed = raw.trimEnd('\n', '\r')
        val match = exitMarkerRe.find(endTrimmed) ?: return Result.success(raw)
        val exitCode = match.groupValues[1].toIntOrNull() ?: return Result.success(raw)
        val body = endTrimmed.removeRange(match.range).trimEnd('\n', '\r')
        if (exitCode == 0) return Result.success(body)
        // `body` is the command's stdout+stderr. For the `cat`/`base64` reads this app
        // issues against the game's Client.log, a partial success means `body` is
        // DECRYPTED LOG BYTES — and every caller logs this message to LogRepository,
        // which persists it to disk. Never put raw output in the exception.
        //
        // Keep only the first line, cap it hard, and fall back to the command's own
        // first token (the program name) when there is no output at all. The exit code
        // is the part that actually diagnoses the failure.
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val detail =
            if (firstLine.isNotEmpty()) {
                firstLine.take(120)
            } else {
                command.trim().substringBefore(' ').take(60).ifEmpty { "shell" }
            }
        return Result.failure(Exception("Command failed (exit $exitCode): $detail"))
    }
}
