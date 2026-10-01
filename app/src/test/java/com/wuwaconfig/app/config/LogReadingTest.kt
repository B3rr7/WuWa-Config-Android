package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keyword list is the first gate a decrypted payload passes, so it has to
 * recognise the vocabulary this game actually emits.
 *
 * Measured on a real device log: the file opens with a long burst of repeated
 * launcher lines ("Sharphereal: Display: [...] calculate all size of lang res")
 * before any engine category appears, and the first `LogInit` landed at byte
 * 43,798 — far beyond the old 512-byte sample. Categories present included
 * LogKuroRendering, LogAndroid, LogConsoleManager, LogConfig, LogStreaming and
 * LogPakFile, while the classic desktop forms (LogRHI, Core.System,
 * GameUserSettings, PhysicalMemoryMB, LogMemory) were absent.
 */
class LogValidationWindowTest {
    private fun lut(b: Int): Int = if (b % 2 == 0) (b xor 0xEF) else (b xor 0xA5)

    /** The game's 3-byte WuWa magic, which decodeLogBytes dispatches on. */
    private val wuwaHeader = byteArrayOf(0x00, 0x54, 0x50)

    private fun encrypt(text: String): ByteArray =
        wuwaHeader +
            text.toByteArray(Charsets.UTF_8).map { lut((it.toInt() and 0xFF) xor 0x4A).toByte() }.toByteArray()

    /** A realistic head: launcher spam first, engine categories much later. */
    private fun kuroStyleLog(totalBytes: Int = 120_000): String =
        buildString {
            append("[2026.09.26-22.13.30:527][347][GameThread]Log file open, [2026.09.26-22.13.30]\n")
            var i = 0
            while (length < totalBytes) {
                append(
                    "[2026.09.26-22.13.30:527][347][GameThread]Sharphereal: Display: [$i][I][Launcher]" +
                        " calculate all size of lang res. [res: RoleVoice/role_lang_$i][preDownload: False]\n",
                )
                i++
            }
            append("[2026.09.26-22.13.31:001][347][GameThread]LogAndroid: Display: onSurfaceCreated\n")
            append("[2026.09.26-22.13.31:002][347][GameThread]LogKuroRendering: Initialising renderer\n")
        }

    @Test
    fun `a real kuro log with engine categories far past 512 bytes validates`() {
        val text = kuroStyleLog()
        val bytes = encrypt(text)
        // The first engine category is well past the old 512-byte window.
        assertTrue("fixture must exceed the old window", text.indexOf("LogAndroid") > 512)

        val decoded = LogParser.decodeLogBytes(bytes)
        assertTrue(
            "a valid Kuro log must not be pushed onto the best-effort fallback " +
                "(result was ${decoded.second})",
            decoded.second == LogParser.DecodeResult.DECRYPTED,
        )
        assertTrue(decoded.first.contains("LogKuroRendering"))
    }

    @Test
    fun `a short plain engine log still validates`() {
        val text = "[GameThread]LogInit: Display: engine initialized\n"
        val decoded = LogParser.decodeLogBytes(encrypt(text))
        assertTrue(decoded.first.contains("LogInit"))
    }

    @Test
    fun `pure noise is still rejected after the window grew 16x`() {
        // Deterministic non-log bytes: no printable-ASCII run long enough to contain a
        // marker. Growing the validation window must not let noise through.
        // Non-printable bytes — which is what XOR output of arbitrary data actually
        // looks like. Random PRINTABLE text is deliberately not asserted here: it is
        // textually indistinguishable from log output, and pretending otherwise would
        // be asserting a property the shape gate does not (and cannot) have.
        val rnd = java.util.Random(20260926L)
        val noise = ByteArray(8192) { rnd.nextInt(0x00, 0x20).toByte() }
        assertFalse(LogParser.looksLikeEngineLogText(String(noise, Charsets.ISO_8859_1)))
        // And it must not be reported as a decrypted UE log either.
        val decoded = LogParser.decodeLogBytes(wuwaHeader + noise)
        assertFalse("noise must not decode as a UE log", decoded.second == LogParser.DecodeResult.DECRYPTED)
    }

    @Test
    fun `the log-shape helper accepts real kuro content and rejects noise`() {
        assertTrue(LogParser.looksLikeEngineLogText(kuroStyleLog(2_000)))
        assertTrue(LogParser.looksLikeEngineLogText("[GameThread]LogTemp: hello"))
        assertFalse(LogParser.looksLikeEngineLogText(""))
        assertFalse(LogParser.looksLikeEngineLogText("   \n\t  "))
    }
}

/**
 * Backup filenames carry the session's start and end stamps, and mtime is not
 * reliable: on the device inspected, seven files shared `2026-09-23 17:34`
 * while their names spanned three weeks.
 *
 * These assert against the shipped [backupLogSortKey], not a local copy. An
 * earlier revision of this file re-implemented the stamp regex locally, which
 * meant the test would keep passing if the production ordering rule drifted.
 */
class BackupLogOrderingTest {
    private val realNames =
        listOf(
            "Client-backup-2026.09.05-21.13.08-2026.09.05-21.26.29.log",
            "Client-backup-2026.09.26-21.49.44-2026.09.26-22.13.30.log",
            "Client-backup-2026.09.10-20.51.55-2026.09.23-17.34.26.log",
            "Client-backup-2026.09.26-18.31.20-2026.09.26-18.54.55.log",
            "Client-backup-null-2026.09.06-21.33.30.log",
        )

    @Test
    fun `newest session sorts first`() {
        val sorted = realNames.sortedByDescending(::backupLogSortKey)
        assertTrue("newest first, got ${sorted.first()}", sorted.first().contains("2026.09.26-21.49.44"))
    }

    @Test
    fun `ordering follows the name stamp, not the mtime alias`() {
        // The 2026.09.10 session ran until 2026.09.23 17:34 — the same mtime as six
        // other files on the device — so any mtime-based sort would interleave it
        // arbitrarily. By session start it belongs between the 09-26 and 09-05 logs.
        val sorted = realNames.sortedByDescending(::backupLogSortKey)
        val iSep = sorted.indexOfFirst { it.contains("2026.09.10-20.51.55") }
        val iMay = sorted.indexOfFirst { it.contains("2026.09.05-21.13.08") }
        val iSep26 = sorted.indexOfFirst { it.contains("2026.09.26-21.49.44") }
        assertTrue("09-10 must sort after the newer 09-26 session", iSep > iSep26)
        assertTrue("09-10 must sort before the older 09-05 session", iSep < iMay)
    }

    @Test
    fun `a null-prefixed name still sorts by its embedded stamp`() {
        // "Client-backup-null-…" has no session-start stamp but does carry the END
        // stamp, so it must not throw and must not be treated as unparseable.
        val sorted = realNames.sortedByDescending(::backupLogSortKey)
        val idx = sorted.indexOfFirst { it.contains("null-") }
        assertTrue("must still be ordered, not dropped", idx >= 0)
        val iSep = sorted.indexOfFirst { it.contains("2026.09.10-20.51.55") }
        val iMay = sorted.indexOfFirst { it.contains("2026.09.05-21.13.08") }
        assertTrue("it falls between the 09-10 and 09-05 sessions by its stamp", idx > iSep && idx < iMay)
    }

    @Test
    fun `a name with no stamp at all sorts last without throwing`() {
        assertTrue("unparseable must sort last", backupLogSortKey("Client-backup-garbage.log") == 0L)
    }

    @Test
    fun `the two sessions on the same day order by time`() {
        val a = "Client-backup-2026.09.26-18.31.20-2026.09.26-18.54.55.log"
        val b = "Client-backup-2026.09.26-21.49.44-2026.09.26-22.13.30.log"
        assertTrue("later session first", backupLogSortKey(b) > backupLogSortKey(a))
    }

    @Test
    fun `a remote ls path sorts the same as its bare filename`() {
        // The name arrives from remote `ls`, so it is a full path. Stripping the
        // directory is part of the contract, not incidental.
        val bare = "Client-backup-2026.09.26-21.49.44-2026.09.26-22.13.30.log"
        val remote = "/sdcard/Android/data/com.kurogame.wutheringwaves/files/Logs/$bare"
        assertEquals(backupLogSortKey(bare), backupLogSortKey(remote))
    }
}
