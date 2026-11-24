package com.wuwaconfig.app.ui

import androidx.compose.ui.graphics.Color
import com.wuwaconfig.app.model.LogInfo
import com.wuwaconfig.app.ui.anim.BrainLine
import com.wuwaconfig.app.ui.anim.LogCategory
import com.wuwaconfig.app.ui.anim.hueOf
import com.wuwaconfig.app.ui.anim.toBrainLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure seams of the log-read/load animations: LogInfo -> brain lines and
 * theme Color -> HSL hue. The Canvas sims themselves are untestable on a JVM
 * (Paint/Typeface are stubbed android.jar surfaces), so what is pinned here is
 * exactly what they consume. `hueOf` is hand-written for the same reason
 * adjustSaturation is — android.graphics.Color.colorToHSV returns garbage
 * under isReturnDefaultValues.
 */
class BrainMappingTest {
    private val full =
        LogInfo(
            deviceModel = "moto g60",
            socName = "SD732G",
            cpuName = "Kryo 570",
            gpu = "Mali-G710",
            gameApi = "Vulkan 1.3",
            ramMb = 12288,
            androidVersion = "16",
            activeCvars = mapOf("r.ScreenPercentage" to "100", "r.Streaming.PoolSize" to "1200"),
        )

    @Test
    fun `maps all signals in category order with cvars last`() {
        val lines = full.toBrainLines()
        assertEquals(7, lines.size)
        assertEquals(LogCategory.DEVICE, lines[0].category)
        assertEquals("DEVICE=moto g60", lines[0].text)
        assertEquals("SoC=SD732G", lines[1].text)
        assertEquals("GPU=Mali-G710", lines[2].text)
        assertEquals("API=Vulkan 1.3", lines[3].text)
        assertEquals("RAM=12.0 GB", lines[4].text)
        assertEquals("OS=Android 16", lines[5].text)
        assertEquals("CVARS=2 active", lines[6].text)
        assertEquals(LogCategory.CVARS, lines[6].category)
    }

    @Test
    fun `falls back to cpuName when deviceModel missing`() {
        val lines = full.copy(deviceModel = null).toBrainLines()
        assertEquals("DEVICE=Kryo 570", lines[0].text)
    }

    @Test
    fun `api falls back to the raw api field`() {
        val lines = full.copy(gameApi = null, api = "OpenGLES").toBrainLines()
        assertEquals("API=OpenGLES", lines[3].text)
    }

    @Test
    fun `missing fields shrink the read instead of printing unknown`() {
        val lines = LogInfo(gpu = "Adreno 730").toBrainLines()
        assertEquals(listOf("GPU=Adreno 730", "CVARS=0 active"), lines.map { it.text })
    }

    @Test
    fun `cap holds at ten lines with cvars still last`() {
        val saturated =
            LogInfo(
                deviceModel = "D1", socName = "S1", cpuName = "C1", gpu = "G1",
                gameApi = "A1", api = "A2", ramMb = 1, androidVersion = "16",
                activeCvars = mapOf("r" to "1"),
            )
        val lines = saturated.toBrainLines()
        assertTrue(lines.size <= 10)
        assertEquals(LogCategory.CVARS, lines.last().category)
        assertEquals("CVARS=1 active", lines.last().text)
    }

    @Test
    fun `hueOf matches the derivative hues for primaries`() {
        assertEquals(0f, hueOf(Color(1f, 0f, 0f)), 0.01f)
        assertEquals(120f, hueOf(Color(0f, 1f, 0f)), 0.01f)
        assertEquals(240f, hueOf(Color(0f, 0f, 1f)), 0.01f)
        assertEquals(180f, hueOf(Color(0f, 1f, 1f)), 0.01f)
    }

    @Test
    fun `greys degenerate to hue 0 not garbage`() {
        assertEquals(0f, hueOf(Color(0.5f, 0.5f, 0.5f)), 0.5f)
        assertEquals(0f, hueOf(Color.White), 0.5f)
    }

    @Test
    fun `the default cyan accent stays teal`() {
        // BaseNeonCyan = 0xFF00B0C7 → hue ~187f; the idle sim hue ran at 205f,
        // so a theme-driven idle is measurably different and still cyan.
        val h = hueOf(Color(0xFF00B0C7))
        assertEquals(187f, h, 1.5f)
    }

    @Test
    fun `brain line is a plain text-category pair`() {
        assertEquals(BrainLine("GPU=Mali-G710", LogCategory.GPU), BrainLine("GPU=Mali-G710", LogCategory.GPU))
    }
}
