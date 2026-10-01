package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

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

    // ── decryptWithFallback plausibility gate ──
    // When no strategy produced output containing a UE4 keyword, the fallback used to
    // `return XorLutStrategy().decrypt(body)` — the SAME transform that had just been
    // rejected. That could never "succeed" and could only hand back unvalidated noise,
    // which was then tagged DecodeResult.DECRYPTED. Downstream, verifyDeployedCvars ran on
    // the garbage and reported EVERY generated CVar as "rejected" after a deploy that had
    // actually succeeded. The fallback now requires the transform OUTPUT to be plausible
    // decoded text, and returns null when nothing validates.

    @Test
    fun `decryptBackupLog rejects a plain text file that only passes the BOM magic gate`() {
        // EF BB BF is the backup-log magic, but a file that merely STARTS with those three
        // bytes is not encrypted. It is a plain, unencrypted UTF-8-BOM text file.
        val plainText = "This is a plain text backup file, never encrypted by the game.\n"
        val data = backupHeader + plainText.toByteArray(Charsets.UTF_8)

        assertNull("an unencrypted plain-text file must not be reported as decrypted", LogParser.decryptBackupLog(data))
    }

    @Test
    fun `decryptBackupLog rejects a plain text INI behind the BOM magic gate`() {
        val plainText = "[SystemSettings]\nr.ShadowQuality=3\nr.FramePace=60\nplain text here\n"
        val data = backupHeader + plainText.toByteArray(Charsets.UTF_8)

        assertNull(LogParser.decryptBackupLog(data))
    }

    @Test
    fun `decodeLogBytes does not claim DECRYPTED for an unencrypted BOM-prefixed text file`() {
        val plainText = "just some ordinary ascii words in a row, nothing special at all\n"
        val data = backupHeader + plainText.toByteArray(Charsets.UTF_8)

        val (_, result) = LogParser.decodeLogBytes(data)
        assertEquals(
            "an undecodable payload must fall through to PLAINTEXT, not be tagged DECRYPTED",
            LogParser.DecodeResult.PLAINTEXT,
            result,
        )
    }

    @Test
    fun `decryptWithFallback returns null for a high-entropy random byte blob`() {
        // Deterministic (fixed-seed java.util.Random) so the test can never flake: a
        // uniform random byte string is exactly the shape that used to be handed back as
        // "decrypted" noise. Measured: the XOR-LUT output is ~64% printable, below the 92%
        // plausibility floor, and contains no UE4 keyword under any of the five strategies.
        val random = Random(20260925L)
        val blob = ByteArray(512) { random.nextInt(256).toByte() }

        assertNull(LogParser.decryptWithFallback(blob))
    }

    @Test
    fun `decodeLogBytes does not claim DECRYPTED for a random byte blob`() {
        val random = Random(1234567L)
        val blob = ByteArray(512) { random.nextInt(256).toByte() }

        val (_, result) = LogParser.decodeLogBytes(blob)
        assertEquals(LogParser.DecodeResult.PLAINTEXT, result)
    }

    @Test
    fun `short correctly-encrypted content with no UE4 keyword is still accepted`() {
        // The other half of the gate: the plausibility fallback must not become a blanket
        // rejection. "Backup log content" is too short to contain any UE4_KEYWORDS, so it
        // only survives via looksLikeDecodedText — exactly the path the fix added.
        val plaintext = "Backup log content\n".toByteArray(Charsets.UTF_8)
        val encrypted = encryptPlaintext(plaintext, backupHeader)

        val decrypted = LogParser.decryptBackupLog(encrypted)
        assertEquals(plaintext.toList(), decrypted!!.toList())

        val (text, result) = LogParser.decodeLogBytes(encrypted)
        assertEquals("Backup log content\n", text)
        assertEquals(LogParser.DecodeResult.DECRYPTED, result)
    }

    @Test
    fun `decryptWithFallback accepts genuinely encrypted UE4 log content`() {
        val plaintext =
            "LogInit: Display: Loaded DefaultEngine.ini\nr.ScreenPercentage=100\n"
                .toByteArray(Charsets.UTF_8)
        val encrypted = encryptPlaintext(plaintext, wuwaHeader)

        val decrypted = LogParser.decryptWuwaLog(encrypted)
        assertEquals(plaintext.toList(), decrypted!!.toList())
    }

    /**
     * Regression from a real device run: with an empty main Client.log the app fell
     * back to a backup log that decoded to printable-but-meaningless text. The
     * plausibility gate accepted it, the merge poisoned the analysis, and the app
     * reported a fabricated "VERIFY: 0/93 CVars accepted by engine".
     *
     * Printability is not evidence — XOR-LUT output of arbitrary bytes is often
     * printable. A payload long enough to judge must look like a LOG.
     */
    @Test
    fun `a long printable payload that is not log-shaped is rejected`() {
        val noise = ByteArray(512) { (it * 37 % 95 + 32).toByte() }
        assertNull(
            "printable noise must not be accepted as a decoded log",
            LogParser.decryptWithFallback(noise),
        )
    }

    @Test
    fun `a long log-shaped payload with no UE4 keyword is accepted by the gate`() {
        // Deliberately avoids every UE4_KEYWORDS entry so verifyDecryption() cannot
        // pass this — it is accepted only by the new log-shape gate, which is exactly
        // the path under test.
        val text =
            buildString {
                repeat(12) { i ->
                    append("[2026.09.26-22.19.44:Warning] verbose: texture streaming budget exceeded ($i)\n")
                }
            }
        val encrypted = encryptPlaintext(text.toByteArray(Charsets.UTF_8), wuwaHeader)
        assertEquals(
            text,
            String(LogParser.decryptWuwaLog(encrypted)!!, Charsets.UTF_8),
        )
    }

    @Test
    fun `a short correctly encrypted payload is still accepted`() {
        // Below the keyword-scan threshold there is nothing to judge, so a
        // header-validated, correctly-encrypted short log is still read.
        val plaintext = "Log line"
        val encrypted = encryptPlaintext(plaintext.toByteArray(Charsets.UTF_8), wuwaHeader)
        assertEquals(plaintext, String(LogParser.decryptWuwaLog(encrypted)!!, Charsets.UTF_8))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Real captured log lines.
    //
    // The fixtures below are verbatim lines from the device's own 3.7.0
    // Client.log, not synthetic text. That distinction is the whole point: this
    // file previously fed parseLog INI fragments such as "r.ShadowQuality=3",
    // which the parser never sees in the field, so three regexes could match
    // nothing at all across every real session and the suite stayed green.
    //
    // 670/531/241 of these LogConsoleManager lines per session were being
    // discarded, which is why sg.ShadowQuality never reached activeCvars and
    // why screenPct/shadowQ were permanently null.
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseLines(vararg lines: String) = LogParser.parseLog(lines.joinToString("\n"))

    /**
     * The engine's console-variable line, verbatim. Only the variable name and the
     * surviving value change between occurrences in a real session; the sentence
     * around them never does.
     */
    private fun consoleManagerLine(
        stamp: String,
        name: String,
        value: String,
        losing: String = "SetByScalability",
        winning: String = "SetByProjectSetting",
    ) = "[$stamp][  0][GameThread]LogConsoleManager: Warning: Setting the console variable " +
        "'$name' with '$losing' was ignored as it is lower priority than the previous " +
        "'$winning'. Value remains '$value'"

    @Test
    fun `effective CVar value is captured from the console-variable line`() {
        // Verbatim, log/decrypted/Client.log.txt :875 and :888.
        val info =
            parseLines(
                "[2026.09.30-23.09.03:875][  0][GameThread]LogConfig: Setting CVar [[r.RenderTargetPoolMin:50]]",
                consoleManagerLine("2026.09.30-23.09.03:888", "r.RenderTargetPoolMin", "50"),
            )
        assertEquals("50", info.activeCvars["r.RenderTargetPoolMin"])
    }

    @Test
    fun `the effective value wins when it differs from the requested one`() {
        // The engine writes the requested value first, then reports which value
        // actually survived priority resolution. Real 3.7.0 sessions disagreed on
        // r.ScreenPercentage (85 requested, 80 in force), and the requested value
        // is the one that is actively misleading.
        val info =
            parseLines(
                "[2026.09.30-23.09.03:874][  0][GameThread]LogConfig: Setting CVar [[r.ScreenPercentage:85]]",
                consoleManagerLine("2026.09.30-23.09.03:888", "r.ScreenPercentage", "80"),
            )
        assertEquals("80", info.activeCvars["r.ScreenPercentage"])
        assertEquals(80f, info.screenPct!!, 0.001f)
    }

    @Test
    fun `sg ShadowQuality is captured although it is only ever reported this way`() {
        // Verbatim, Client.log.txt :443. No `Setting CVar [[sg.ShadowQuality:…]]`
        // line exists for this CVar, so before CVar_EFFECTIVE_RE it was absent
        // from activeCvars entirely and SmartBrain's shadow-quality scoring never ran.
        val info =
            parseLines(
                consoleManagerLine("2026.09.30-23.10.51:443", "sg.ShadowQuality", "0", winning = "SetByConsole"),
            )
        assertEquals("0", info.activeCvars["sg.ShadowQuality"])
        assertEquals(0, info.shadowQ)
    }

    @Test
    fun `the engine's unknown-name placeholder is not recorded as a CVar`() {
        // Verbatim, Client.log.txt :890. The engine prints the literal `unknown?`
        // when it cannot resolve a console variable's name; it is not a CVar and
        // must not reach activeCvars, ForbiddenCvars, or the CVar-database gate.
        val info =
            parseLines(
                consoleManagerLine("2026.09.30-23.09.03:890", "unknown?", "0", losing = "SetByProjectSetting", winning = "SetByDeviceProfile"),
            )
        assertTrue("placeholder leaked into activeCvars: ${info.activeCvars.keys}", info.activeCvars.isEmpty())
        assertEquals(0, info.forbiddenCvars)
    }

    @Test
    fun `a real session's device and API fields all resolve`() {
        // The regression guard for this whole class of bug: these are the lines a
        // real 3.7.0 session emits, and each must yield its field. If the game
        // changes a format, this fails instead of silently nulling a field.
        val info =
            parseLines(
                "LogInit: OS: Android (16), CPU: moto g(60), GPU: Adreno (TM) 618",
                "LogInit: Build: ++UE4+Release-4.26-CL-0",
                "K#GPUFamily : Adreno (TM) 618",
                "K#DeviceModel : motorola(moto g(60))",
                "LogInit: Memory total: Physical=5642.29MB (6GB approx) Available=2100.00MB",
                "Setting Android Resolution, logic resolution Width=2456 and Height=1080, final Width=1632 and Height=720",
                "Selected Device Profile: [Android_Adreno6xx_Lowest]",
                "[2026.09.30-23.09.03][  0][GameThread]sg.KuroRenderQuality = \"4\"",
                "LogRHI: Initializing OpenGL RHI",
                "LogAndroid: VulkanAvailable: false",
                "LogAndroid: OpenGL ES will be used.",
                "LogFramePacer: Display: r.FramePace : requesting 30, set as 30",
            )
        assertEquals("Adreno (TM) 618", info.gpu)
        assertEquals("motorola(moto g(60))", info.deviceModel)
        assertEquals("moto g(60)", info.cpuName)
        assertEquals(5642, info.ramMb)
        assertEquals("16", info.androidVersion)
        assertEquals("2456x1080", info.resolution)
        assertEquals("Android_Adreno6xx_Lowest", info.deviceProfile)
        assertEquals("UE4+Release-4.26-CL-0", info.engineVersion)
        assertEquals("OpenGL ES", info.gameApi)
        assertEquals("not_available", info.vulkanStatus)
        assertEquals(30, info.fpsCap)
        assertEquals("4", info.qualityMode)
    }

    @Test
    fun `fpsActual stays null because this game logs no FPS line at all`() {
        // Documented as unreachable rather than silently "fixed": there is no
        // AverageFPS line anywhere in a 3.7.0 session, so no pattern can recover
        // it. SmartBrain gates "competitive" on this field — see SmartBrain:457.
        assertNull(parseLines("[2026.09.30-23.09.03] GameThread: nothing relevant here").fpsActual)
    }
}
