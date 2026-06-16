package com.wuwaconfig.app.adb

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

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

    const val VERSION = 0x01000001
    const val MAX_DATA = 256 * 1024

    data class AdbMessage(
        val command: ByteArray,
        val arg0: Int,
        val arg1: Int,
        val payload: ByteArray
    ) {
        val dataLength: Int get() = payload.size
    }

    fun readMessage(input: InputStream): AdbMessage? {
        val header = ByteArray(24)
        var offset = 0
        while (offset < 24) {
            val read = input.read(header, offset, 24 - offset)
            if (read < 0) return null
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
        if (magic != (cmdInt xor 0xFFFFFFFF.toInt())) return null

        val payload = if (dataLength > 0) {
            val data = ByteArray(dataLength)
            var dataOffset = 0
            while (dataOffset < dataLength) {
                val read = input.read(data, dataOffset, dataLength - dataOffset)
                if (read < 0) return null
                dataOffset += read
            }
            data
        } else ByteArray(0)

        // CRC32 is not enforced on Android 11+ (daemon may send 0)
        // Skip the check for compatibility

        return AdbMessage(cmd, arg0, arg1, payload)
    }

    private fun calculateCrc32(data: ByteArray): Int {
        val crc = CRC32()
        crc.update(data)
        return crc.value.toInt()
    }

    fun writeMessage(output: OutputStream, message: AdbMessage) {
        val cmdInt = ByteBuffer.wrap(message.command).order(ByteOrder.LITTLE_ENDIAN).int
        val magic = cmdInt xor 0xFFFFFFFF.toInt()
        val crc32 = calculateCrc32(message.payload)

        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
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

    fun createConnectionMessage(banner: String = "device::"): AdbMessage {
        val payload = "${banner}\u0000".encodeToByteArray()
        return AdbMessage(CNXN, VERSION, MAX_DATA, payload)
    }

    fun createAuthSignatureMessage(signature: ByteArray): AdbMessage {
        return AdbMessage(AUTH, AUTH_SIGNATURE, 0, signature)
    }

    fun createAuthPublicKeyMessage(publicKey: ByteArray): AdbMessage {
        return AdbMessage(AUTH, AUTH_RSA_PUBLIC, 0, publicKey)
    }

    fun createOpenMessage(localId: Int, destination: String): AdbMessage {
        return AdbMessage(OPEN, localId, 0, "$destination\u0000".encodeToByteArray())
    }

    fun createWriteMessage(localId: Int, remoteId: Int, data: ByteArray): AdbMessage {
        return AdbMessage(WRTE, localId, remoteId, data)
    }

    fun createCloseMessage(localId: Int, remoteId: Int): AdbMessage {
        return AdbMessage(CLSE, localId, remoteId, ByteArray(0))
    }

    fun createOkMessage(localId: Int, remoteId: Int): AdbMessage {
        return AdbMessage(OKAY, localId, remoteId, ByteArray(0))
    }


}
