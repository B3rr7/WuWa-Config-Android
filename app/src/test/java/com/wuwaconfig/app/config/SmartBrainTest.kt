package com.wuwaconfig.app.config

import android.content.res.AssetManager
import com.wuwaconfig.app.model.LogInfo
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock

class SmartBrainTest {
    private lateinit var cvarDb: CvarDatabase

    private fun fixtureBytes(name: String): ByteArray {
        val s = javaClass.getResourceAsStream("/cvars/$name")
        requireNotNull(s) { "missing test resource /cvars/$name" }
        return s.use { it.readBytes() }
    }

    private fun newCvarDb(): CvarDatabase {
        val m = mock(AssetManager::class.java)
        doReturn(fixtureBytes("libUE4_cvars.txt").inputStream())
            .`when`(m).open("cvars/libUE4_cvars.txt")
        doReturn(fixtureBytes("config_monitor_cvars.txt").inputStream())
            .`when`(m).open("cvars/config_monitor_cvars.txt")
        doReturn(fixtureBytes("config_monitor_values.txt").inputStream())
            .`when`(m).open("cvars/config_monitor_values.txt")
        return CvarDatabase(m)
    }

    @Before
    fun setUp() {
        LogRepository.clear()
        val db = newCvarDb()
        runBlocking { db.load() }
        cvarDb = db
    }

    @After
    fun tearDown() {
        LogRepository.clear()
    }

    private fun recFor(
        info: LogInfo,
        allowRestrictedCvars: Boolean = true,
    ) = SmartBrain.scoreRecommendation(info, cvarDb, allowRestrictedCvars)

    // ─────────── score clamp ───────────

    @Test
    fun `score is clamped to 0 for all-negative LogInfo`() {
        val info =
            LogInfo(
                // "unknown" -> -20
                gpu = null,
                // 1..3999 -> -15
                ramMb = 2000,
                vulkanStatus = null,
                fpsCap = 60,
                // dropPct>30 -> -18, actual<30 -> -15
                fpsActual = 20.0f,
                // >=5 -> -20, +>=3 & unknown -> -5
                thermalEvents = 5,
                // >=1 -> -12
                gpuOom = 1,
                // >=15 -> -10
                dropFrames = 20,
                isLowMem = false,
            )
        val rec = recFor(info)
        assertEquals(0, rec.score)
    }

    @Test
    fun `score is clamped to 100 for all-positive LogInfo`() {
        val info =
            LogInfo(
                // flagship -> +30
                gpu = "Adreno 830",
                // >=8000 -> +8
                ramMb = 8000,
                // +8
                vulkanStatus = "available",
                fpsCap = 60,
                // at target -> +5
                fpsActual = 60.0f,
                thermalEvents = 0,
                gpuOom = 0,
                dropFrames = 0,
                isLowMem = false,
                // >=125 -> +5
                screenPct = 130.0f,
            )
        val rec = recFor(info)
        assertEquals(100, rec.score)
    }

    // ─────────── recommendPreset gates ───────────

    @Test
    fun `gpuOom greater equal 2 forces potato regardless of score`() {
        // Maximally positive device profile, but gpuOom=2 still forces potato.
        val info =
            LogInfo(
                gpu = "Adreno 830",
                ramMb = 8000,
                vulkanStatus = "available",
                fpsCap = 60,
                fpsActual = 60.0f,
                thermalEvents = 0,
                gpuOom = 2,
                isLowMem = false,
            )
        assertEquals("potato", recFor(info).preset)
    }

    @Test
    fun `isLowMem forces potato`() {
        val info =
            LogInfo(
                gpu = "Adreno 830",
                ramMb = 8000,
                isLowMem = true,
            )
        assertEquals("potato", recFor(info).preset)
    }

    @Test
    fun `flagship vulkan no thermal 1080p recommends cinematic`() {
        val info =
            LogInfo(
                gpu = "Adreno 830",
                ramMb = 8000,
                vulkanStatus = "available",
                fpsCap = 60,
                fpsActual = 60.0f,
                thermalEvents = 0,
                gpuOom = 0,
                isLowMem = false,
            )
        assertEquals("cinematic", recFor(info).preset)
    }

    @Test
    fun `flagship vulkan with thermal recommends ultra (breaks cinematic)`() {
        val info =
            LogInfo(
                gpu = "Adreno 830",
                ramMb = 8000,
                vulkanStatus = "available",
                fpsCap = 60,
                fpsActual = 60.0f,
                // breaks cinematic's thermal==0 requirement
                thermalEvents = 1,
                gpuOom = 0,
                isLowMem = false,
            )
        assertEquals("ultra", recFor(info).preset)
    }

    @Test
    fun `flagship without vulkan recommends high`() {
        val info =
            LogInfo(
                gpu = "Adreno 830",
                ramMb = 8000,
                // breaks the vulkan-gated cinematic/ultra/high(vulkan)
                vulkanStatus = null,
                fpsCap = 60,
                fpsActual = 60.0f,
                thermalEvents = 0,
                isLowMem = false,
            )
        assertEquals("high", recFor(info).preset)
    }

    @Test
    fun `mid tier with vulkan no fps recommends balanced`() {
        val info =
            LogInfo(
                // mid
                gpu = "Adreno 640",
                ramMb = 6000,
                vulkanStatus = "available",
                fpsCap = null,
                fpsActual = null,
                thermalEvents = 0,
                isLowMem = false,
            )
        assertEquals("balanced", recFor(info).preset)
    }

    @Test
    fun `fps gap greater 15 recommends competitive`() {
        val info =
            LogInfo(
                // mid
                gpu = "Adreno 640",
                ramMb = 6000,
                vulkanStatus = "available",
                fpsCap = 90,
                // gap 16 > 15, dropPct~17.7 -> -6
                fpsActual = 74.0f,
                thermalEvents = 0,
                isLowMem = false,
            )
        assertEquals("competitive", recFor(info).preset)
    }

    @Test
    fun `mid_low with thermal events recommends endurance`() {
        val info =
            LogInfo(
                // mid_low
                gpu = "Mali-G57",
                ramMb = 6000,
                vulkanStatus = null,
                thermalEvents = 3,
                gpuOom = 0,
                isLowMem = false,
            )
        assertEquals("endurance", recFor(info).preset)
    }

    @Test
    fun `frequent auto-adjust triggers recommend endurance`() {
        val info =
            LogInfo(
                // mid_low
                gpu = "Mali-G57",
                // neutral
                ramMb = 4000,
                vulkanStatus = null,
                thermalEvents = 0,
                autoAdjustTriggers = 12,
                gpuOom = 0,
                isLowMem = false,
            )
        assertEquals("endurance", recFor(info).preset)
    }

    @Test
    fun `mid_low with 4GB and no headroom recommends performance`() {
        val info =
            LogInfo(
                // mid_low
                gpu = "Mali-G57",
                // 1..3999 -> -15
                ramMb = 3500,
                vulkanStatus = null,
                thermalEvents = 0,
                autoAdjustTriggers = 0,
                gpuOom = 0,
                isLowMem = false,
            )
        assertEquals("performance", recFor(info).preset)
    }

    // ─────────── signal spot-checks ───────────

    @Test
    fun `flagship GPU contributes plus 30 signal`() {
        val rec = recFor(LogInfo(gpu = "Adreno 830"))
        assertTrue(
            "expected flagship signal, got: ${rec.signals}",
            rec.signals.any { it.contains("Flagship GPU: +30") },
        )
    }

    @Test
    fun `unknown GPU contributes minus 20 signal`() {
        val rec = recFor(LogInfo(gpu = "SomeRandomChip 999"))
        assertTrue(
            "expected low-end signal, got: ${rec.signals}",
            rec.signals.any { it.contains("Low-end GPU: -20") },
        )
    }

    @Test
    fun `8GB RAM contributes plus 8 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = 8000))
        assertTrue(
            "expected 8GB signal, got: ${rec.signals}",
            rec.signals.any { it.contains("8GB+ RAM: +8") },
        )
    }

    @Test
    fun `under 4GB RAM contributes minus 15 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = 3000))
        assertTrue(
            "expected <4GB signal, got: ${rec.signals}",
            rec.signals.any { it.contains("<4GB RAM: -15") },
        )
    }

    @Test
    fun `thermal events x5 contributes minus 20 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, thermalEvents = 5))
        assertTrue(
            "expected thermal x5 signal, got: ${rec.signals}",
            rec.signals.any { it.contains("Thermal throttling x5: -20") },
        )
    }

    @Test
    fun `gpuOom x3 contributes minus 30 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, gpuOom = 3))
        assertTrue(
            "expected gpuOom x3 signal, got: ${rec.signals}",
            rec.signals.any { it.contains("GPU OOM x3: -30") },
        )
    }

    @Test
    fun `fps drop greater 30 percent contributes minus 18 signal`() {
        val rec =
            recFor(
                LogInfo(
                    gpu = null,
                    ramMb = null,
                    fpsCap = 60,
                    // dropPct = ((60-20)/60)*100 = 66.7
                    fpsActual = 20.0f,
                ),
            )
        assertTrue(
            "expected FPS drop >30% signal, got: ${rec.signals}",
            rec.signals.any { it.contains("FPS drop >30%: -18") },
        )
    }

    // ─────────── warnings ───────────

    @Test
    fun `gpuOom greater 0 adds GPU OOM warning`() {
        val rec = recFor(LogInfo(gpuOom = 1))
        assertTrue(
            "expected GPU OOM warning, got: ${rec.warnings}",
            rec.warnings.any { it.contains("GPU OOM detected") },
        )
    }

    @Test
    fun `thermal events greater equal 3 adds throttling warning`() {
        val rec = recFor(LogInfo(thermalEvents = 3))
        assertTrue(
            "expected throttling warning, got: ${rec.warnings}",
            rec.warnings.any { it.contains("Heavy thermal throttling") },
        )
    }

    @Test
    fun `forbidden cvars with allow false add warning and per-cvar penalty`() {
        val rec = recFor(LogInfo(forbiddenCvars = 3), allowRestrictedCvars = false)
        assertTrue(
            "expected forbidden warning, got: ${rec.warnings}",
            rec.warnings.any { it.contains("3 forbidden CVars found") },
        )
        assertTrue(
            "expected per-cvar penalty signal, got: ${rec.signals}",
            rec.signals.any { it.contains("Forbidden CVars x3: -5 each") },
        )
    }

    @Test
    fun `forbidden cvars with allow true do not add warning`() {
        val rec = recFor(LogInfo(forbiddenCvars = 3), allowRestrictedCvars = true)
        assertTrue(
            "must not warn when restricted CVars are allowed, got: ${rec.warnings}",
            rec.warnings.none { it.contains("forbidden CVars found") },
        )
        assertTrue(
            "must not penalize when allowed, got: ${rec.signals}",
            rec.signals.none { it.contains("Forbidden CVars x3") },
        )
    }
}
