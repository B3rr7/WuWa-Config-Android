package com.wuwaconfig.app.adb

import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** Why a frame could not be read — carried to the user instead of a bare "no response". */
class AdbProtocolException(
    val reason: String,
    cause: Throwable? = null,
) : Exception("ADB protocol error: $reason", cause)

object AdbProtocol {
    const val AUTH_TOKEN = 1
    const val AUTH_SIGNATURE = 2
    const val AUTH_RSA_PUBLIC = 3

    val CNXN = "CNXN".encodeToByteArray()
    val OPEN = "OPEN".encodeToByteArray()
    val OKAY = "OKAY".encodeToByteArray()
    val CLSE = "CLSE".encodeToByteArray()
    val WRTE = "WRTE".encodeToByteArray()
    val AUTH = "AUTH".encodeToByteArray()
    val STLS = "STLS".encodeToByteArray()

    const val VERSION = 0x01000001
    const val MAX_DATA = 256 * 1024

    data class AdbMessage(
        val command: ByteArray,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray,
    ) {
        val dataLength: Int get() = payload.size

        // The generated equals/hashCode would compare ByteArray fields by IDENTITY, so
        // two messages with identical wire content would not be equal.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AdbMessage) return false
            return arg0 == other.arg0 &&
                arg1 == other.arg1 &&
                command.contentEquals(other.command) &&
                payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = command.contentHashCode()
            result = 31 * result + arg0
            result = 31 * result + arg1
            result = 31 * result + payload.contentHashCode()
            return result
        }

        override fun toString(): String =
            "AdbMessage(cmd=${hex(command)}, arg0=$arg0, arg1=$arg1, dataLength=$dataLength)"
    }

    /** Renders a 4-byte wire command as hex — `String(bytes)` is mostly replacement chars. */
    fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    /**
     * Reads one frame, returning null on any protocol/EOF error. Kept for callers that
     * treat "no answer" as an ordinary outcome; use [readMessageOrThrow] to report why.
     */
    fun readMessage(input: InputStream): AdbMessage? =
        try {
            readMessageOrThrow(input)
        } catch (_: AdbProtocolException) {
            null
        }

    /**
     * Reads one frame, throwing [AdbProtocolException] with the concrete reason for a clean
     * EOF, a bad magic, an out-of-range length or a truncated payload. Collapsing all four
     * into `null` produced a misleading "No response from ADB daemon" for a garbage (or
     * spoofing) responder.
     */
    fun readMessageOrThrow(input: InputStream): AdbMessage {
        val header = ByteArray(24)
        var offset = 0
        while (offset < 24) {
            val read = input.read(header, offset, 24 - offset)
            // InputStream.read may legally return 0 for len > 0; looping on that spins at
            // 100% CPU forever, so treat it as an unrecoverable stall.
            if (read <= 0) {
                throw AdbProtocolException(
                    if (read < 0) {
                        "connection closed after $offset of 24 header bytes"
                    } else {
                        "peer sent no data (0-byte read) with $offset of 24 header bytes received"
                    },
                )
            }
            offset += read
        }

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = ByteArray(4)
        buffer.get(cmd)
        val arg0 = buffer.getInt()
        val arg1 = buffer.getInt()
        val dataLength = buffer.getInt()
        buffer.getInt() // crc32 (not checked on Android 11+)
        val magic = buffer.getInt()

        val cmdInt = ByteBuffer.wrap(cmd).order(ByteOrder.LITTLE_ENDIAN).int
        if (magic != (cmdInt xor 0xFFFFFFFF.toInt())) {
            throw AdbProtocolException("bad magic in ${hex(cmd)} frame header (peer is not an ADB daemon, or the stream is corrupt)")
        }

        // Never trust the wire: a hostile peer answering a scanned port could
        // otherwise request an Int.MAX_VALUE-sized allocation.
        if (dataLength < 0 || dataLength > MAX_DATA) {
            throw AdbProtocolException("out-of-range payload length $dataLength in ${hex(cmd)} frame")
        }

        val payload =
            if (dataLength > 0) {
                val data = ByteArray(dataLength)
                var dataOffset = 0
                while (dataOffset < dataLength) {
                    val read = input.read(data, dataOffset, dataLength - dataOffset)
                    if (read <= 0) {
                        throw AdbProtocolException(
                            "truncated ${hex(cmd)} payload: got $dataOffset of $dataLength bytes",
                        )
                    }
                    dataOffset += read
                }
                data
            } else {
                ByteArray(0)
            }

        // CRC32 is not enforced on Android 11+ (daemon may send 0)
        // Skip the check for compatibility

        return AdbMessage(cmd, arg0, arg1, payload)
    }

    private fun calculateCrc32(data: ByteArray): Int {
        val crc = CRC32()
        crc.update(data)
        return crc.value.toInt()
    }

    /**
     * Header + payload are written as one buffered call. Callers should obtain this once
     * per connection (see [wrapOutput]) so a frame costs one syscall instead of two.
     */
    fun wrapOutput(output: OutputStream): OutputStream = BufferedOutputStream(output, 32 * 1024)

    fun writeMessage(
        output: OutputStream,
        message: AdbMessage,
    ) {
        val cmdInt = ByteBuffer.wrap(message.command).order(ByteOrder.LITTLE_ENDIAN).int
        val magic = cmdInt xor 0xFFFFFFFF.toInt()
        val crc32 = calculateCrc32(message.payload)

        val header =
            ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(message.command)
                putInt(message.arg0)
                putInt(message.arg1)
                putInt(message.dataLength)
                putInt(crc32)
                putInt(magic)
            }.array()

        output.write(header)
        if (message.payload.isNotEmpty()) {
            output.write(message.payload)
        }
        output.flush()
    }

    fun createConnectionMessage(banner: String = "host::"): AdbMessage {
        val payload = "${banner}\u0000".encodeToByteArray()
        return AdbMessage(CNXN, VERSION, MAX_DATA, payload)
    }

    fun createAuthSignatureMessage(signature: ByteArray): AdbMessage {
        return AdbMessage(AUTH, AUTH_SIGNATURE, 0, signature)
    }

    fun createAuthPublicKeyMessage(publicKey: ByteArray): AdbMessage {
        return AdbMessage(AUTH, AUTH_RSA_PUBLIC, 0, publicKey)
    }

    fun createOpenMessage(
        localId: Int,
        destination: String,
    ): AdbMessage {
        return AdbMessage(OPEN, localId, 0, "${destination}\u0000".encodeToByteArray())
    }

    fun createOkMessage(
        localId: Int,
        remoteId: Int,
    ): AdbMessage {
        return AdbMessage(OKAY, localId, remoteId, ByteArray(0))
    }

    fun createCloseMessage(
        localId: Int,
        remoteId: Int,
    ): AdbMessage {
        return AdbMessage(CLSE, localId, remoteId, ByteArray(0))
    }
}
