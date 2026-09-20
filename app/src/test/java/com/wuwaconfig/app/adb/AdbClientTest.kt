package com.wuwaconfig.app.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fake [Socket] backed by [PipedInputStream]/[PipedOutputStream] pairs.
 *
 * The server side writes to [serverOut] (which the client reads via [serverIn]);
 * the client writes to [clientOut] (which the server reads via [serverIn]).
 */
private class FakeSocket(
    val clientIn: PipedInputStream = PipedInputStream(),
    val clientOut: PipedOutputStream = PipedOutputStream(),
) : Socket() {
    private val serverOut = PipedOutputStream(clientIn)
    private val serverIn = PipedInputStream(clientOut)

    @Volatile
    private var closed = AtomicBoolean(false)

    /** Data the test (acting as the server) reads from the client. */
    val serverInput: PipedInputStream get() = serverIn

    /** Stream the test (acting as the server) writes to, which the client reads. */
    val serverOutput: PipedOutputStream get() = serverOut

    // The client reads exclusively through this stream. It honors SO_TIMEOUT
    // (raw pipes don't): AdbClient.drainTrailingWrite sets a 250ms timeout and
    // relies on SocketTimeoutException to end the drain — a raw pipe read
    // would block forever once the server is done writing.
    override fun getInputStream(): java.io.InputStream =
        object : java.io.FilterInputStream(clientIn) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val timeout = readTimeoutMs
                if (timeout == 0) return pipe.read(b, off, len)
                val deadline = System.currentTimeMillis() + timeout
                while (true) {
                    if (pipe.available() > 0) return pipe.read(b, off, len)
                    if (System.currentTimeMillis() >= deadline) {
                        throw java.net.SocketTimeoutException("Read timed out")
                    }
                    Thread.sleep(5)
                }
            }

            private val pipe: PipedInputStream get() = clientIn
        }

    // Real sockets honor SO_TIMEOUT; pipes don't. Store it locally so the
    // wrapper above (and drainTrailingWrite's save/restore) sees it.
    @Volatile
    private var readTimeoutMs = 0

    override fun getSoTimeout(): Int = readTimeoutMs

    override fun setSoTimeout(timeout: Int) {
        readTimeoutMs = timeout
    }

    override fun getOutputStream(): PipedOutputStream = clientOut

    override fun isConnected(): Boolean = !closed.get()

    override fun isClosed(): Boolean = closed.get()

    // A fake must not dial real TCP: AdbClient.connect() calls connect() plus
    // option setters on the factory socket. No-op the connect; the setters
    // below are safe on an unconnected socket.
    override fun connect(endpoint: java.net.SocketAddress) {
    }

    override fun connect(endpoint: java.net.SocketAddress, timeout: Int) {
    }

    override fun close() {
        closed.set(true)
        clientIn.close()
        clientOut.close()
        serverIn.close()
        serverOut.close()
    }
}

private class FakeCrypto : CryptoAdapter {
    override val isReady: Boolean = true

    override fun getAdbFormattedPublicKey(): ByteArray = "fake-key".toByteArray()

    override fun signToken(token: ByteArray): ByteArray = "fake-signature".toByteArray()

    override fun regenerateKeys(): Result<Unit> = Result.success(Unit)
}

class AdbClientTest {
    private fun createClientPair(): Pair<AdbClient, FakeSocket> {
        val socket = FakeSocket()
        val client =
            AdbClient(
                crypto = FakeCrypto(),
                socketFactory = { _, _ -> socket },
            )
        return Pair(client, socket)
    }

    @Test
    fun `connect succeeds when server sends CNXN`() =
        runBlocking {
            val (client, socket) = createClientPair()
            // Start the fake server: read the CNXN the client sends, then reply with CNXN.
            // Runs concurrently (launch): the server blocks on the pipe read until
            // the client below writes, so awaiting it here would deadlock.
            launch(Dispatchers.IO) {
                val msg = AdbProtocol.readMessage(socket.serverInput)
                assertTrue("client should send CNXN", msg!!.command.contentEquals(AdbProtocol.CNXN))
                AdbProtocol.writeMessage(socket.serverOutput, AdbProtocol.createConnectionMessage())
            }
            // Give the server coroutine time to process.
            kotlinx.coroutines.delay(100)
            val result = client.connect(5555, "127.0.0.1")
            assertTrue("connect should succeed, got: $result", result.isSuccess)
            assertTrue("client should be connected", client.isConnected)
            client.disconnect()
        }

    @Test
    fun `executeShellCommand returns the server response`() =
        runBlocking {
            val (client, socket) = createClientPair()
            launch(Dispatchers.IO) {
                // 1. Client sends CNXN; we reply CNXN (auth success).
                AdbProtocol.readMessage(socket.serverInput)
                AdbProtocol.writeMessage(socket.serverOutput, AdbProtocol.createConnectionMessage())
            }
            kotlinx.coroutines.delay(100)
            client.connect(5555).getOrThrow()

            val remoteId = 200
            launch(Dispatchers.IO) {
                // 2. Client sends OPEN for "shell:echo hello"
                val open = AdbProtocol.readMessage(socket.serverInput)!!
                assertTrue(open.command.contentEquals(AdbProtocol.OPEN))

                // 3. Server replies OKAY with remoteId matching the client's localId.
                AdbProtocol.writeMessage(
                    socket.serverOutput,
                    AdbProtocol.AdbMessage(AdbProtocol.OKAY, remoteId, open.arg0, ByteArray(0)),
                )

                // 4. Server sends WRTE with the response, then CLSE to close the stream.
                AdbProtocol.writeMessage(
                    socket.serverOutput,
                    AdbProtocol.AdbMessage(AdbProtocol.WRTE, remoteId, open.arg0, "hello world".toByteArray()),
                )
                AdbProtocol.writeMessage(
                    socket.serverOutput,
                    AdbProtocol.AdbMessage(AdbProtocol.CLSE, remoteId, open.arg0, ByteArray(0)),
                )
            }
            kotlinx.coroutines.delay(200)

            val result = client.executeShellCommand("echo hello")
            assertTrue("command should succeed, got: $result", result.isSuccess)
            assertEquals("hello world", result.getOrThrow())
            client.disconnect()
        }

    @Test
    fun `executeShellCommand fails when not connected`() =
        runBlocking {
            val (client, _) = createClientPair()
            val result = client.executeShellCommand("echo nope")
            assertTrue(result.isFailure)
            client.disconnect()
        }

    @Test
    fun `connect fails on authentication timeout`() =
        runBlocking {
            val (client, socket) = createClientPair()
            // Server sends nothing — the client waits for a CNXN/AUTH/OKAY response.
            // We don't write anything. The auth loop has a 30s deadline, but the
            // deadline is only checked between reads, so a blocked pipe read would
            // hang forever: close the SERVER WRITE end (feeds the client's read)
            // so the pending read throws and connect() fails fast. (Closing the
            // server read end does NOT unblock the client's read.)
            withContext(Dispatchers.IO) {
                socket.serverOutput.close()
            }
            kotlinx.coroutines.delay(50)
            val result = client.connect(5555)
            assertTrue(result.isFailure)
            assertFalse(client.isConnected)
        }

    @Test
    fun `connect rejects a foreign CLSE during command`() =
        runBlocking {
            val (client, socket) = createClientPair()
            launch(Dispatchers.IO) {
                AdbProtocol.readMessage(socket.serverInput)
                AdbProtocol.writeMessage(socket.serverOutput, AdbProtocol.createConnectionMessage())
            }
            kotlinx.coroutines.delay(100)
            client.connect(5555).getOrThrow()

            val remoteId = 1234
            launch(Dispatchers.IO) {
                val open = AdbProtocol.readMessage(socket.serverInput)!!
                // A foreign CLSE (wrong arg1) should be ignored, then our CLSE closes it.
                AdbProtocol.writeMessage(
                    socket.serverOutput,
                    AdbProtocol.AdbMessage(AdbProtocol.CLSE, remoteId, open.arg0 + 999, ByteArray(0)),
                )
                AdbProtocol.writeMessage(
                    socket.serverOutput,
                    AdbProtocol.AdbMessage(AdbProtocol.OKAY, remoteId, open.arg0, ByteArray(0)),
                )
                AdbProtocol.writeMessage(
                    socket.serverOutput,
                    AdbProtocol.AdbMessage(AdbProtocol.WRTE, remoteId, open.arg0, "ok".toByteArray()),
                )
                AdbProtocol.writeMessage(
                    socket.serverOutput,
                    AdbProtocol.AdbMessage(AdbProtocol.CLSE, remoteId, open.arg0, ByteArray(0)),
                )
            }
            kotlinx.coroutines.delay(200)

            val result = client.executeShellCommand("test")
            assertTrue(result.isSuccess)
            assertEquals("ok", result.getOrThrow())
            client.disconnect()
        }
}
