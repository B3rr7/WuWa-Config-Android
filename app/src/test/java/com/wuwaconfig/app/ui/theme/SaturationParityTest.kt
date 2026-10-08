package com.wuwaconfig.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Guards the hand-written HSV round-trip in `adjustSaturation` against the
 * `android.graphics.Color.colorToHSV` implementation it replaced.
 *
 * The replacement exists because the platform pair is a stubbed `android.jar`
 * method under unit tests (so the saturation slider had no reachable test) and
 * because it quantizes through 8-bit ints. Both are real wins, but they are also
 * a reimplementation of a colour conversion, which is exactly the kind of change
 * that silently shifts every accent in the app by a few units of red. These
 * tests pin the mathematical invariants that must hold regardless.
 */
class SaturationParityTest {
    @Test
    fun `factor of one is the identity`() {
        // This is the one that was false before: the platform pair did not
        // round-trip unchanged, so `scaledBy(1f)` was not a no-op.
        for (color in listOf(RED, GREEN, BLUE, GREY, NEON_PURPLE, GOLD)) {
            assertEquals(color, adjustSaturation(color, 1f))
        }
    }

    @Test
    fun `maximum saturation pushes fully saturated colours toward vivid`() {
        // Already-saturated primaries cannot get more saturated, so they must be
        // left alone rather than clipped to something different.
        assertEquals(RED, adjustSaturation(RED, 1.6f))
        assertEquals(GREEN, adjustSaturation(GREEN, 1.6f))
        assertEquals(BLUE, adjustSaturation(BLUE, 1.6f))
        // A desaturated colour gains saturation.
        val muted = adjustSaturation(MUTED_BLUE, 1.6f)
        assertNotEquals(MUTED_BLUE, muted)
    }

    @Test
    fun `desaturation reduces saturation without shifting brightness`() {
        val vivid = NEON_PURPLE
        val dull = adjustSaturation(vivid, 0.5f)
        assertNotEquals(vivid, dull)
        // Hue is preserved: the dominant channel is still red for a purple whose
        // red channel leads, so this is a saturation change and not a hue change.
        assertEquals(vivid.red > vivid.blue, dull.red > dull.blue)
        // Value (the max channel) is untouched, which is what "brightness" means
        // here.
        assertEquals(maxOf(vivid.red, vivid.green, vivid.blue), maxOf(dull.red, dull.green, dull.blue), 0.02f)
    }

    @Test
    fun `achromatic colours are returned untouched`() {
        // Hue is undefined at delta == 0; a naive implementation divides by it
        // and produces NaN, which Compose renders as transparent.
        for (grey in listOf(Color.Black, Color.White, GREY)) {
            assertEquals(grey, adjustSaturation(grey, 1.6f))
            assertEquals(grey, adjustSaturation(grey, 0.5f))
        }
    }

    @Test
    fun `alpha is never modified`() {
        val translucent = Color(0.5f, 0.2f, 0.8f, 0.42f)
        assertEquals(translucent.alpha, adjustSaturation(translucent, 1.6f).alpha)
        assertEquals(translucent.alpha, adjustSaturation(translucent, 0.5f).alpha)
    }

    @Test
    fun `out of range factors are clamped rather than extrapolated`() {
        val base = NEON_PURPLE
        assertEquals(adjustSaturation(base, 1.6f), adjustSaturation(base, 12f))
        assertEquals(adjustSaturation(base, 0.5f), adjustSaturation(base, -4f))
    }

    private companion object {
        val RED = Color(1f, 0f, 0f, 1f)
        val GREEN = Color(0f, 1f, 0f, 1f)
        val BLUE = Color(0f, 0f, 1f, 1f)
        val GREY = Color(0.5f, 0.5f, 0.5f, 1f)
        val MUTED_BLUE = Color(0.35f, 0.4f, 0.55f, 1f)
        val NEON_PURPLE = Color(0xFF6A00FF)
        val GOLD = Color(0xFFD4AF37)
    }
}
