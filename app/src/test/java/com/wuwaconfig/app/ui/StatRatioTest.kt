package com.wuwaconfig.app.ui

import com.wuwaconfig.app.ui.screens.statRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The current-vs-target ratio behind each stat's progress bar on the character screen.
 *
 * Kuro formats both values as percentage strings, and not every stat is numeric — the ratio
 * drives a filled bar, so an unparseable pair must return `null` (draw nothing) rather than
 * silently render a full or empty bar that misreports the player's build.
 */
class StatRatioTest {
    @Test
    fun `percentage strings divide into a ratio`() {
        assertEquals(291.6f / 240f, statRatio("291.6%", "240.0%")!!, 1e-4f)
        assertEquals(48.8f / 70f, statRatio("48.8%", "70.0%")!!, 1e-4f)
        assertEquals(119.2f / 140f, statRatio("119.2%", "140.0%")!!, 1e-4f)
    }

    @Test
    fun `bare numbers without a percent sign still parse`() {
        assertEquals(0.5f, statRatio("50", "100")!!, 1e-4f)
    }

    @Test
    fun `whitespace is tolerated`() {
        assertEquals(0.5f, statRatio(" 50.0% ", " 100.0% ")!!, 1e-4f)
    }

    @Test
    fun `an absent or non-numeric value yields no bar rather than a misleading one`() {
        assertNull(statRatio(null, "70.0%"))
        assertNull(statRatio("48.8%", null))
        assertNull(statRatio("", "70.0%"))
        assertNull(statRatio("—", "70.0%"))
        assertNull(statRatio("Max", "100"))
    }

    @Test
    fun `a zero or negative target cannot divide`() {
        assertNull(statRatio("48.8%", "0.0%"))
        assertNull(statRatio("48.8%", "-10%"))
    }
}
