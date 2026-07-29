package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogParserTest {
    private fun lut(b: Int): Int = if (b % 2 == 0) (b xor 0xEF) else (b xor 0xA5)

    private fun encryptPlaintext(
        plaintext: ByteArray,
        header: ByteArray,
    ): ByteArray {
        val encrypted =
            plaintext.map { b ->
                val xored = (b.toInt() and 0xFF) xor 0x4A
                lut(xored).toByte()
            }.toByteArray()
        return header + encrypted
    }

    private val wuwaHeader = byteArrayOf(0x00, 0x54, 0x50)
    private val backupHeader = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    @Test
    fun `LUT LUT property holds for all byte values`() {
        for (b in 0..255) {
            val lut1 = lut(b)
            val lut2 = lut(lut1)
            assertEquals("LUT(LUT($b)) should equal $b xor 0x4A", b xor 0x4A, lut2)
        }
    }

    @Test
    fun `applyXorLut is inverse of game encryption`() {
        val plaintext = "Test data for LUT verification".toByteArray(Charsets.UTF_8)
        val encrypted =
            plaintext.map { b ->
                val xored = (b.toInt() and 0xFF) xor 0x4A
                lut(xored).toByte()
            }.toByteArray()
        val decrypted = LogParser.applyXorLut(encrypted)
        assertEquals(plaintext.toList(), decrypted.toList())
    }

    @Test
    fun `decryptWuwaLog restores plaintext from encrypted data`() {
        val plaintext = "Hello, World!\nLog line 2\n".toByteArray(Charsets.UTF_8)
        val encrypted = encryptPlaintext(plaintext, wuwaHeader)
        val decrypted = LogParser.decryptWuwaLog(encrypted)
        assertEquals(plaintext.toList(), decrypted!!.toList())
    }

    @Test
    fun `decryptWuwaLog returns null for wrong header`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertNull(LogParser.decryptWuwaLog(data))
    }

    @Test
    fun `decryptWuwaLog returns null for too-short data`() {
        assertNull(LogParser.decryptWuwaLog(byteArrayOf(0x00, 0x54)))
        assertNull(LogParser.decryptWuwaLog(byteArrayOf()))
    }

    @Test
    fun `decryptBackupLog restores plaintext from encrypted data`() {
        val plaintext = "Backup log content\n".toByteArray(Charsets.UTF_8)
        val encrypted = encryptPlaintext(plaintext, backupHeader)
        val decrypted = LogParser.decryptBackupLog(encrypted)
        assertEquals(plaintext.toList(), decrypted!!.toList())
    }

    @Test
    fun `decryptBackupLog returns null for wrong header`() {
        val data = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertNull(LogParser.decryptBackupLog(data))
    }

    @Test
    fun `decodeLogBytes decrypts WuWa format and reports success`() {
        val plaintext = "Log line 1\nLog line 2\n".toByteArray(Charsets.UTF_8)
        val encrypted = encryptPlaintext(plaintext, wuwaHeader)
        val (text, result) = LogParser.decodeLogBytes(encrypted)
        assertEquals(plaintext.toString(Charsets.UTF_8), text)
        assertEquals(LogParser.DecodeResult.DECRYPTED, result)
    }

    @Test
    fun `decodeLogBytes decrypts backup format and reports success`() {
        val plaintext = "Backup log content\n".toByteArray(Charsets.UTF_8)
        val encrypted = encryptPlaintext(plaintext, backupHeader)
        val (text, result) = LogParser.decodeLogBytes(encrypted)
        assertEquals(plaintext.toString(Charsets.UTF_8), text)
        assertEquals(LogParser.DecodeResult.DECRYPTED, result)
    }

    @Test
    fun `decodeLogBytes returns raw text for non-encrypted data`() {
        val plaintext = "Plain text log".toByteArray(Charsets.UTF_8)
        val (text, result) = LogParser.decodeLogBytes(plaintext)
        assertEquals("Plain text log", text)
        assertEquals(LogParser.DecodeResult.PLAINTEXT, result)
    }

    @Test
    fun `decryptWuwaLog handles UTF-16BE content with BOM`() {
        val plaintext = "Hello".toByteArray(Charsets.UTF_16BE)
        val withBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + plaintext
        val encrypted = encryptPlaintext(withBom, wuwaHeader)
        val decrypted = LogParser.decryptWuwaLog(encrypted)
        assertEquals(plaintext.toList(), decrypted!!.toList())
    }

    @Test
    fun `extractConveneUrl finds gacha URL in text`() {
        val text =
            """
            Some log text
            https://aki-gm-resources-oversea.aki-game.net/aki/gacha/index.html#/record?player_id=123&record_id=abc&resources_id=1&gacha_type=1&svr_id=1&lang=en
            More log text
            """.trimIndent()
        val url = LogParser.extractConveneUrl(text)
        assertEquals(
            "https://aki-gm-resources-oversea.aki-game.net/aki/gacha/index.html#/record?player_id=123&record_id=abc&resources_id=1&gacha_type=1&svr_id=1&lang=en",
            url,
        )
    }

    @Test
    fun `extractConveneUrl returns null when no URL present`() {
        assertNull(LogParser.extractConveneUrl("No URL here"))
    }
}
