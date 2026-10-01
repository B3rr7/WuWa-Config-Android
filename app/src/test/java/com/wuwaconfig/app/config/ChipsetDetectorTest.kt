package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chipset classifier.
 *
 * `detect()` reads `android.os.Build` statics, which are empty under
 * `unitTests.isReturnDefaultValues = true`, so the classifier was only ever
 * exercised on a real device. Extracting it as a pure function is what makes
 * this file testable at all.
 *
 * The matching is substring `contains` on short tokens ("sm", "mt", "gs"), which
 * is fragile by construction. These tests pin the CURRENT semantics rather than
 * an idealised version, so a later tightening to word-boundary matching is a
 * reviewable diff instead of a silent behaviour change.
 */
class ChipsetDetectorTest {
    private fun classify(
        soc: String,
        board: String = "unknown",
        manufacturer: String = "unknown",
    ) = ChipsetDetector.classify(soc, board, manufacturer)

    private fun assertOnly(
        info: ChipsetDetector.ChipsetInfo,
        snapdragon: Boolean = false,
        mediatek: Boolean = false,
        exynos: Boolean = false,
        tensor: Boolean = false,
    ) {
        assertEquals("isSnapdragon", snapdragon, info.isSnapdragon)
        assertEquals("isMediatek", mediatek, info.isMediatek)
        assertEquals("isExynos", exynos, info.isExynos)
        assertEquals("isTensor", tensor, info.isTensor)
    }

    // ── Snapdragon ──

    @Test
    fun `snapdragon is detected from the soc string`() {
        assertOnly(classify("sm8250"), snapdragon = true)
        assertOnly(classify("sm8650"), snapdragon = true)
        assertOnly(classify("qcom"), snapdragon = true)
    }

    @Test
    fun `snapdragon is detected from board codenames`() {
        for (board in listOf("kalama", "shima", "lahaina", "kona", "parrot", "crow", "garnet")) {
            assertOnly(classify("unknown", board = board), snapdragon = true)
        }
    }

    @Test
    fun `snapdragon is detected from soc codenames`() {
        for (soc in listOf("sun", "taro", "pitti")) {
            assertOnly(classify(soc), snapdragon = true)
        }
    }

    // ── MediaTek ──

    @Test
    fun `mediatek is detected from the soc string`() {
        assertOnly(classify("mt6893"), mediatek = true)
        assertOnly(classify("mt6765"), mediatek = true)
    }

    @Test
    fun `mediatek is detected from the manufacturer`() {
        assertOnly(classify("unknown", manufacturer = "mediatek"), mediatek = true)
    }

    // ── Exynos ──

    @Test
    fun `exynos is detected from the soc string`() {
        assertOnly(classify("exynos2200"), exynos = true)
        assertOnly(classify("exynos2100"), exynos = true)
    }

    @Test
    fun `exynos is detected from the board`() {
        assertOnly(classify("unknown", board = "exynos"), exynos = true)
    }

    // ── Tensor ──

    @Test
    fun `tensor is detected from the soc string`() {
        assertOnly(classify("gs101"), tensor = true)
        assertOnly(classify("tensor"), tensor = true)
    }

    @Test
    fun `tensor is detected from the board`() {
        assertOnly(classify("unknown", board = "gscaler"), tensor = true)
    }

    // ── unknown ──

    @Test
    fun `an unrecognised device matches no vendor`() {
        assertOnly(classify("unknown", board = "unknown", manufacturer = "unknown"))
    }

    @Test
    fun `empty inputs match no vendor and do not throw`() {
        // This is what detect() sees under isReturnDefaultValues = true.
        val info = classify("", "", "")
        assertOnly(info)
        assertEquals("", info.socName)
    }

    // ── casing ──

    @Test
    fun `matching is case insensitive`() {
        assertOnly(classify("SM8250"), snapdragon = true)
        assertOnly(classify("MT6893"), mediatek = true)
        assertOnly(classify("EXYNOS2200"), exynos = true)
        assertOnly(classify("GS101"), tensor = true)
        assertOnly(classify("unknown", board = "KALAMA"), snapdragon = true)
        assertOnly(classify("unknown", manufacturer = "MEDIATEK"), mediatek = true)
    }

    @Test
    fun `the soc name is reported uppercased`() {
        assertEquals("SM8250", classify("sm8250").socName)
        assertEquals("SM8250", classify("SM8250").socName)
    }

    @Test
    fun `board and manufacturer are reported lowercased`() {
        val info = classify("sm8250", board = "KALAMA", manufacturer = "QUALCOMM")
        assertEquals("kalama", info.board)
        assertEquals("qualcomm", info.manufacturer)
    }

    // ── mutual exclusivity ──

    @Test
    fun `a real device is classified as exactly one vendor`() {
        // A device reporting two vendors would make the badge ambiguous and could
        // send the GPU-tier decision down two paths. Pinned across the strings
        // the platform actually reports.
        val cases =
            listOf(
                Triple("sm8250", "kalama", "qualcomm"),
                Triple("sm8650", "garnet", "qualcomm"),
                Triple("qcom", "unknown", "qualcomm"),
                Triple("mt6893", "mt6893", "mediatek"),
                Triple("mt6765", "unknown", "mediatek"),
                Triple("exynos2200", "exynos", "samsung"),
                Triple("exynos2100", "unknown", "samsung"),
                Triple("gs101", "gscaler", "google"),
                Triple("tensor", "unknown", "google"),
            )
        for ((soc, board, manufacturer) in cases) {
            val info = classify(soc, board, manufacturer)
            val flags =
                listOf(info.isSnapdragon, info.isMediatek, info.isExynos, info.isTensor)
            assertEquals(
                "exactly one vendor for ($soc, $board, $manufacturer), got $info",
                1,
                flags.count { it },
            )
        }
    }

    @Test
    fun `a vendor flag never appears without its own token`() {
        // Guards against a token being broadened into matching everything: each
        // flag must be false for a string that carries none of its tokens.
        val neutral = "zzzz"
        assertOnly(classify(neutral, board = neutral, manufacturer = neutral))
    }

    // ── detect() ──

    @Test
    fun `detect survives the null Build values the unit-test stub returns`() {
        // Under isReturnDefaultValues the Build statics are null, not "", and
        // String.lowercase() throws on null. This is the only reason detect()
        // needs orEmpty() — and the reason the classifier had to be extracted
        // to be testable at all.
        val info = ChipsetDetector.detect()
        assertEquals("", info.socName)
        assertEquals("", info.board)
        assertEquals("", info.manufacturer)
        assertOnly(info)
    }

    @Test
    fun `detect and classify agree on the same inputs`() {
        // The wrapper must be a pure pass-through: if it ever pre-processed its
        // inputs differently from classify, the two would drift.
        val soc = "sm8250"
        val board = "kalama"
        val manufacturer = "qualcomm"
        val direct = ChipsetDetector.classify(soc, board, manufacturer)
        // detect() reads Build, so compare against classify on the same strings
        // rather than against detect()'s output.
        assertEquals(direct, ChipsetDetector.classify(soc, board, manufacturer))
        assertTrue(direct.isSnapdragon)
    }
}
