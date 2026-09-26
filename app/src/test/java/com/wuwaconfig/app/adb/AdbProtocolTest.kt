package com.wuwaconfig.app.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    // ── readMessageOrThrow carries a real reason ──
    // readMessage collapsed a clean EOF, a bad magic, an out-of-range dataLength and a
    // truncated payload all into `null`, so a garbage (or spoofing) responder surfaced to
    // the user as a misleading "No response from ADB daemon". readMessageOrThrow throws
    // AdbProtocolException carrying the concrete reason.

    private fun rawHeader(
        cmd: ByteArray,
        arg0: Int,
        arg1: Int,
        dataLength: Int,
        corruptMagic: Boolean = false,
    ): ByteArray =
        ByteBuffer
            .allocate(24)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put(cmd)
                putInt(arg0)
                putInt(arg1)
                putInt(dataLength)
                putInt(0)
                val cmdInt = ByteBuffer.wrap(cmd).order(ByteOrder.LITTLE_ENDIAN).int
                val magic = cmdInt xor 0xFFFFFFFF.toInt()
                putInt(if (corruptMagic) magic xor 0xFF else magic)
            }.array()

    private fun reasonOf(
        bytes: ByteArray,
        limit: Int = bytes.size,
    ): String {
        try {
            AdbProtocol.readMessageOrThrow(ByteArrayInputStream(bytes, 0, limit))
        } catch (e: AdbProtocolException) {
            return e.reason
        }
        throw AssertionError("expected readMessageOrThrow to throw for ${bytes.size}/$limit bytes")
    }

    @Test
    fun `readMessageOrThrow reports a truncated header distinctly`() {
        val reason = reasonOf(ByteArray(10))
        assertTrue("truncated-header reason should mention the header, got: $reason", reason.contains("header"))
        assertTrue("truncated-header reason should count the bytes, got: $reason", reason.contains("10 of 24"))
    }

    @Test
    fun `readMessageOrThrow reports a corrupted magic distinctly`() {
        val bytes = rawHeader(AdbProtocol.OKAY, 0, 0, 0, corruptMagic = true)
        val reason = reasonOf(bytes)
        assertTrue("bad-magic reason should say so, got: $reason", reason.contains("bad magic"))
        assertTrue("bad-magic reason should name the frame, got: $reason", reason.contains("4f4b4159"))
    }

    @Test
    fun `readMessageOrThrow reports an out-of-range dataLength distinctly`() {
        // A hostile peer asking for Int.MAX_VALUE bytes must be rejected before allocating.
        val reason = reasonOf(rawHeader(AdbProtocol.WRTE, 0, 0, Int.MAX_VALUE))
        assertTrue("range reason should say out-of-range, got: $reason", reason.contains("out-of-range"))
        assertTrue("range reason should echo the value, got: $reason", reason.contains("2147483647"))

        // Negative is equally untrustworthy.
        val negative = reasonOf(rawHeader(AdbProtocol.WRTE, 0, 0, -1))
        assertTrue("range reason should cover a negative length, got: $negative", negative.contains("out-of-range"))
    }

    @Test
    fun `readMessageOrThrow reports a dataLength above MAX_DATA distinctly`() {
        val reason = reasonOf(rawHeader(AdbProtocol.WRTE, 0, 0, AdbProtocol.MAX_DATA + 1))
        assertTrue("range reason should say out-of-range, got: $reason", reason.contains("out-of-range"))
        assertTrue("range reason should echo the value, got: $reason", reason.contains("${AdbProtocol.MAX_DATA + 1}"))
    }

    @Test
    fun `readMessageOrThrow reports a truncated payload distinctly`() {
        val out = ByteArrayOutputStream()
        AdbProtocol.writeMessage(out, AdbProtocol.AdbMessage(AdbProtocol.WRTE, 0, 0, ByteArray(100)))
        val bytes = out.toByteArray()
        // Cut 50 bytes off the payload so the reader runs out mid-frame.
        val reason = reasonOf(bytes, bytes.size - 50)
        assertTrue("truncated-payload reason should say truncated, got: $reason", reason.contains("truncated"))
        assertTrue("truncated-payload reason should count bytes, got: $reason", reason.contains("of 100 bytes"))
    }

    @Test
    fun `the four failure reasons are pairwise distinguishable`() {
        // The whole point of the fix: a user (and a bug report) must be able to tell a
        // dead socket from a non-ADB listener from a hostile length from a short read.
        val out = ByteArrayOutputStream()
        AdbProtocol.writeMessage(out, AdbProtocol.AdbMessage(AdbProtocol.WRTE, 0, 0, ByteArray(100)))
        val full = out.toByteArray()

        val reasons =
            listOf(
                reasonOf(ByteArray(10)),
                reasonOf(rawHeader(AdbProtocol.OKAY, 0, 0, 0, corruptMagic = true)),
                reasonOf(rawHeader(AdbProtocol.WRTE, 0, 0, Int.MAX_VALUE)),
                reasonOf(full, full.size - 50),
            )
        assertEquals("all four failure modes must produce distinct reasons", 4, reasons.toSet().size)
    }

    @Test
    fun `AdbProtocolException message includes the reason`() {
        val e = AdbProtocolException("bad magic in CNXN frame header")
        assertTrue(e.message!!.contains("bad magic in CNXN frame header"))
        assertEquals("bad magic in CNXN frame header", e.reason)
    }

    @Test
    fun `readMessage still returns null for every failure mode`() {
        // The lenient contract the existing callers depend on must be preserved: readMessage
        // is readMessageOrThrow with the reason discarded.
        val out = ByteArrayOutputStream()
        AdbProtocol.writeMessage(out, AdbProtocol.AdbMessage(AdbProtocol.WRTE, 0, 0, ByteArray(100)))
        val full = out.toByteArray()

        assertNull(AdbProtocol.readMessage(ByteArrayInputStream(ByteArray(0))))
        assertNull(AdbProtocol.readMessage(ByteArrayInputStream(ByteArray(10))))
        assertNull(AdbProtocol.readMessage(ByteArrayInputStream(rawHeader(AdbProtocol.OKAY, 0, 0, 0, corruptMagic = true))))
        assertNull(AdbProtocol.readMessage(ByteArrayInputStream(rawHeader(AdbProtocol.WRTE, 0, 0, Int.MAX_VALUE))))
        assertNull(AdbProtocol.readMessage(ByteArrayInputStream(full, 0, full.size - 50)))
    }

    // ── AdbMessage value equality ──
    // command and payload are ByteArray, so the generated equals compared them by IDENTITY.
    // The old tests worked around this with assertSame(AdbProtocol.CNXN, msg.command), which
    // passes even if createOkMessage emitted the WRONG id bytes — the assertion was about the
    // array instance, not the value on the wire.

    @Test
    fun `AdbMessage compares by content not by array identity`() {
        val a = AdbProtocol.AdbMessage(byteArrayOf(0x57, 0x52, 0x54, 0x45), 1, 2, "payload".toByteArray())
        val b = AdbProtocol.AdbMessage(byteArrayOf(0x57, 0x52, 0x54, 0x45), 1, 2, "payload".toByteArray())

        // Distinct array instances...
        assertFalse("the fixtures must not share array instances", a.command === b.command)
        assertFalse("the fixtures must not share array instances", a.payload === b.payload)
        // …but equal content.
        assertTrue("equal content must compare equal", a == b)
        assertEquals("equal content must hash equally", a.hashCode(), b.hashCode())
    }

    @Test
    fun `AdbMessage is not equal when any field differs`() {
        val base = AdbProtocol.AdbMessage(AdbProtocol.WRTE, 1, 2, "payload".toByteArray())
        assertNotEquals(AdbProtocol.AdbMessage(AdbProtocol.OKAY, 1, 2, "payload".toByteArray()), base)
        assertNotEquals(AdbProtocol.AdbMessage(AdbProtocol.WRTE, 9, 2, "payload".toByteArray()), base)
        assertNotEquals(AdbProtocol.AdbMessage(AdbProtocol.WRTE, 1, 9, "payload".toByteArray()), base)
        assertNotEquals(AdbProtocol.AdbMessage(AdbProtocol.WRTE, 1, 2, "different".toByteArray()), base)
        assertNotEquals(AdbProtocol.AdbMessage(AdbProtocol.WRTE, 1, 2, ByteArray(0)), base)
    }

    @Test
    fun `AdbMessage payload length difference is a real inequality`() {
        // The classic identity-equals hole: two zero-length payloads created independently.
        val a = AdbProtocol.AdbMessage(AdbProtocol.OKAY, 7, 5, ByteArray(0))
        val b = AdbProtocol.AdbMessage(AdbProtocol.OKAY, 7, 5, ByteArray(0))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(AdbProtocol.AdbMessage(AdbProtocol.OKAY, 7, 6, ByteArray(0)), a)
    }

    @Test
    fun `createOkMessage emits the OKAY id bytes`() {
        // Verifies the BYTES, not the array instance — this is what assertSame(OKAY, ...)
        // could never catch.
        val msg = AdbProtocol.createOkMessage(99, 5)
        assertArrayEquals(AdbProtocol.OKAY, msg.command)
        assertEquals(99, msg.arg0)
        assertEquals(5, msg.arg1)
        assertEquals(0, msg.dataLength)
    }

    @Test
    fun `createConnectionMessage emits the CNXN id bytes`() {
        val msg = AdbProtocol.createConnectionMessage()
        assertArrayEquals(AdbProtocol.CNXN, msg.command)
        assertEquals(4, msg.command.size)
        assertEquals(AdbProtocol.VERSION, msg.arg0)
        assertEquals(AdbProtocol.MAX_DATA, msg.arg1)
    }

    @Test
    fun `createOpenMessage emits the OPEN id bytes`() {
        val msg = AdbProtocol.createOpenMessage(42, "shell:ls")
        assertArrayEquals(AdbProtocol.OPEN, msg.command)
        assertEquals(42, msg.arg0)
    }

    @Test
    fun `createCloseMessage emits the CLSE id bytes`() {
        val msg = AdbProtocol.createCloseMessage(1, 2)
        assertArrayEquals(AdbProtocol.CLSE, msg.command)
        assertEquals(1, msg.arg0)
        assertEquals(2, msg.arg1)
    }

    @Test
    fun `a round-tripped message equals the message that was written`() {
        val original = AdbProtocol.AdbMessage(AdbProtocol.WRTE, 11, 22, "round trip me".toByteArray())
        val out = ByteArrayOutputStream()
        AdbProtocol.writeMessage(out, original)

        val decoded = AdbProtocol.readMessageOrThrow(ByteArrayInputStream(out.toByteArray()))

        // Crosses a serialization boundary, so this can only hold with real value equality.
        assertEquals(original, decoded)
        assertEquals(original.hashCode(), decoded.hashCode())
    }

    @Test
    fun `the command constants are the ADB wire ids`() {
        // String(bytes, US_ASCII), NOT contentToString() — the latter is
        // List<Byte>.contentToString() and renders the byte VALUES ("[67, 78, 88, 78]"),
        // so the assertion silently passed on garbage instead of checking the wire id.
        fun id(bytes: ByteArray) = String(bytes, Charsets.US_ASCII)
        assertEquals("CNXN", id(AdbProtocol.CNXN))
        assertEquals("OPEN", id(AdbProtocol.OPEN))
        assertEquals("OKAY", id(AdbProtocol.OKAY))
        assertEquals("CLSE", id(AdbProtocol.CLSE))
        assertEquals("WRTE", id(AdbProtocol.WRTE))
        assertEquals("AUTH", id(AdbProtocol.AUTH))
        assertEquals("STLS", id(AdbProtocol.STLS))
    }
}
