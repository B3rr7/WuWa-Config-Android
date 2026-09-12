package com.wuwaconfig.app.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbProtocolTest {
    // ── round-trip ──

    @Test
    fun `write then read round-trips a message`() {
        val out = ByteArrayOutputStream()
        val payload = "hello adb".toByteArray()
        AdbProtocol.writeMessage(out, AdbProtocol.AdbMessage(AdbProtocol.WRTE, 1, 2, payload))
        val msg = AdbProtocol.readMessage(ByteArrayInputStream(out.toByteArray()))
        assertEquals(AdbProtocol.WRTE.contentToString(), msg!!.command.contentToString())
        assertEquals(1, msg.arg0)
        assertEquals(2, msg.arg1)
        assertEquals(payload.contentToString(), msg.payload.contentToString())
    }

    @Test
    fun `round-trip preserves empty payload`() {
        val out = ByteArrayOutputStream()
        AdbProtocol.writeMessage(out, AdbProtocol.AdbMessage(AdbProtocol.OKAY, 7, 0, ByteArray(0)))
        val msg = AdbProtocol.readMessage(ByteArrayInputStream(out.toByteArray()))
        assertEquals(0, msg!!.payload.size)
        assertEquals(7, msg.arg0)
    }

    @Test
    fun `round-trip preserves max-size payload`() {
        val payload = ByteArray(AdbProtocol.MAX_DATA)
        payload[0] = 1
        payload[AdbProtocol.MAX_DATA - 1] = 2
        val out = ByteArrayOutputStream()
        AdbProtocol.writeMessage(out, AdbProtocol.AdbMessage(AdbProtocol.WRTE, 0, 0, payload))
        val msg = AdbProtocol.readMessage(ByteArrayInputStream(out.toByteArray()))
        assertEquals(AdbProtocol.MAX_DATA, msg!!.payload.size)
        assertEquals(1.toByte(), msg.payload[0])
        assertEquals(2.toByte(), msg.payload[AdbProtocol.MAX_DATA - 1])
    }

    // ── message factory helpers ──

    @Test
    fun `createConnectionMessage uses CNXN command`() {
        val msg = AdbProtocol.createConnectionMessage()
        assertSame(AdbProtocol.CNXN, msg.command)
    }

    @Test
    fun `createOpenMessage emits OPEN with shell command`() {
        val msg = AdbProtocol.createOpenMessage(42, "shell:ls")
        assertSame(AdbProtocol.OPEN, msg.command)
        assertEquals(42, msg.arg0)
        // Payload is the destination null-terminated: "shell:ls\0".
        val payloadStr = String(msg.payload)
        assertEquals("shell:ls", payloadStr.trim { it <= ' ' })
    }

    @Test
    fun `createOkMessage echoes the remote id`() {
        val msg = AdbProtocol.createOkMessage(99, 5)
        assertSame(AdbProtocol.OKAY, msg.command)
        assertEquals(99, msg.arg0)
        assertEquals(5, msg.arg1)
    }

    @Test
    fun `createCloseMessage closes both directions`() {
        val msg = AdbProtocol.createCloseMessage(1, 2)
        assertSame(AdbProtocol.CLSE, msg.command)
        assertEquals(1, msg.arg0)
        assertEquals(2, msg.arg1)
    }

    // ── hostile-input guards ──

    @Test
    fun `readMessage returns null on truncated header`() {
        // A 10-byte blob is not a valid 24-byte header.
        assertNull(AdbProtocol.readMessage(ByteArrayInputStream(ByteArray(10))))
    }

    @Test
    fun `readMessage returns null on truncated payload`() {
        val out = ByteArrayOutputStream()
        AdbProtocol.writeMessage(out, AdbProtocol.AdbMessage(AdbProtocol.WRTE, 0, 0, ByteArray(100)))
        val bytes = out.toByteArray()
        // Cut 50 bytes off the payload so the reader runs out of data mid-frame.
        assertNull(AdbProtocol.readMessage(ByteArrayInputStream(bytes, 0, bytes.size - 50)))
    }

    @Test
    fun `readMessage rejects corrupted magic`() {
        val out = ByteArrayOutputStream()
        AdbProtocol.writeMessage(out, AdbProtocol.AdbMessage(AdbProtocol.OKAY, 0, 0, ByteArray(0)))
        val bytes = out.toByteArray()
        // Flip a bit in the magic field (last 4 bytes of the 24-byte header).
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        assertNull(AdbProtocol.readMessage(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `dataLength field is little-endian`() {
        val out = ByteArrayOutputStream()
        AdbProtocol.writeMessage(out, AdbProtocol.AdbMessage(AdbProtocol.WRTE, 0, 0, ByteArray(0)))
        val header = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        header.position(12)
        // The dataLength field sits at offset 12 in the 24-byte header.
        assertEquals(0, header.int)
    }
}
