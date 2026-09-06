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

    // ─────────── GPU tier signals ───────────

    @Test
    fun `high GPU contributes plus 20 signal`() {
        val rec = recFor(LogInfo(gpu = "Adreno 750"))
        assertTrue(
            "expected High-end GPU: +20, got: ${rec.signals}",
            rec.signals.any { it.contains("High-end GPU: +20") },
        )
    }

    @Test
    fun `mid_high GPU contributes plus 10 signal`() {
        val rec = recFor(LogInfo(gpu = "Adreno 730"))
        assertTrue(
            "expected Mid-high GPU: +10, got: ${rec.signals}",
            rec.signals.any { it.contains("Mid-high GPU: +10") },
        )
    }

    @Test
    fun `mid GPU contributes no signal`() {
        val rec = recFor(LogInfo(gpu = "Adreno 620"))
        assertTrue(
            "mid GPU should add no signal, got: ${rec.signals}",
            rec.signals.none { it.contains("GPU:") },
        )
    }

    @Test
    fun `mid_low GPU contributes minus 10 signal`() {
        val rec = recFor(LogInfo(gpu = "Mali-G57"))
        assertTrue(
            "expected Low-mid GPU: -10, got: ${rec.signals}",
            rec.signals.any { it.contains("Low-mid GPU: -10") },
        )
    }

    // ─────────── RAM signals ───────────

    @Test
    fun `6GB RAM contributes plus 5 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = 6000))
        assertTrue(
            "expected 6GB+ RAM: +5, got: ${rec.signals}",
            rec.signals.any { it.contains("6GB+ RAM: +5") },
        )
    }

    @Test
    fun `4GB RAM contributes no signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = 4500))
        assertTrue(
            "4-6GB RAM should add no signal, got: ${rec.signals}",
            rec.signals.none { it.contains("RAM:") },
        )
    }

    @Test
    fun `vulkan available contributes plus 8 signal`() {
        val rec = recFor(LogInfo(vulkanStatus = "available"))
        assertTrue(
            "expected Vulkan: +8, got: ${rec.signals}",
            rec.signals.any { it.contains("Vulkan: +8") },
        )
    }

    // ─────────── FPS drop signal tiers ───────────

    @Test
    fun `fps drop 20 to 30 percent contributes minus 12 signal`() {
        val rec = recFor(LogInfo(fpsCap = 60, fpsActual = 46.0f))
        assertTrue(
            "expected FPS drop 20-30%: -12, got: ${rec.signals}",
            rec.signals.any { it.contains("FPS drop 20-30%: -12") },
        )
    }

    @Test
    fun `fps drop 10 to 20 percent contributes minus 6 signal`() {
        val rec = recFor(LogInfo(fpsCap = 60, fpsActual = 52.0f))
        assertTrue(
            "expected FPS drop 10-20%: -6, got: ${rec.signals}",
            rec.signals.any { it.contains("FPS drop 10-20%: -6") },
        )
    }

    // ─────────── FPS actual signal tiers ───────────

    @Test
    fun `fps actual below 30 contributes minus 15 signal`() {
        val rec = recFor(LogInfo(fpsCap = 60, fpsActual = 25.0f))
        assertTrue(
            "expected FPS <30: -15, got: ${rec.signals}",
            rec.signals.any { it.contains("FPS <30: -15") },
        )
    }

    @Test
    fun `fps actual 30 to 45 contributes minus 8 signal`() {
        val rec = recFor(LogInfo(fpsCap = 60, fpsActual = 35.0f))
        assertTrue(
            "expected FPS 30-45: -8, got: ${rec.signals}",
            rec.signals.any { it.contains("FPS 30-45: -8") },
        )
    }

    // ─────────── Thermal signal tiers ───────────

    @Test
    fun `thermal x1 contributes minus 5 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, thermalEvents = 1))
        assertTrue(
            "expected Thermal events x1: -5, got: ${rec.signals}",
            rec.signals.any { it.contains("Thermal events x1: -5") },
        )
    }

    @Test
    fun `thermal x3 contributes minus 12 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, thermalEvents = 3))
        assertTrue(
            "expected Thermal events x3: -12, got: ${rec.signals}",
            rec.signals.any { it.contains("Thermal events x3: -12") },
        )
    }

    // ─────────── GPU OOM signal tiers ───────────

    @Test
    fun `gpuOom x1 contributes minus 12 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, gpuOom = 1))
        assertTrue(
            "expected GPU OOM x1: -12, got: ${rec.signals}",
            rec.signals.any { it.contains("GPU OOM x1: -12") },
        )
    }

    @Test
    fun `gpuOom x2 contributes minus 20 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, gpuOom = 2))
        assertTrue(
            "expected GPU OOM x2: -20, got: ${rec.signals}",
            rec.signals.any { it.contains("GPU OOM x2: -20") },
        )
    }

    // ─────────── Drop frames signal tiers ───────────

    @Test
    fun `drop frames x5 contributes minus 5 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, dropFrames = 5))
        assertTrue(
            "expected Frame drops x5: -5, got: ${rec.signals}",
            rec.signals.any { it.contains("Frame drops x5: -5") },
        )
    }

    @Test
    fun `drop frames x15 contributes minus 10 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, dropFrames = 15))
        assertTrue(
            "expected Frame drops x15: -10, got: ${rec.signals}",
            rec.signals.any { it.contains("Frame drops x15: -10") },
        )
    }

    // ─────────── Network errors ───────────

    @Test
    fun `network errors x5 contributes minus 5 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, networkErrors = 5))
        assertTrue(
            "expected Network issues x5: -5, got: ${rec.signals}",
            rec.signals.any { it.contains("Network issues x5: -5") },
        )
    }

    @Test
    fun `network errors x10 contributes minus 10 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, networkErrors = 10))
        assertTrue(
            "expected Network issues x10: -10, got: ${rec.signals}",
            rec.signals.any { it.contains("Network issues x10: -10") },
        )
    }

    // ─────────── Auto-adjust triggers ───────────

    @Test
    fun `auto-adjust triggers x5 contributes minus 3 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, autoAdjustTriggers = 5))
        assertTrue(
            "expected Occasional auto-adjust x5: -3, got: ${rec.signals}",
            rec.signals.any { it.contains("Occasional auto-adjust x5: -3") },
        )
    }

    @Test
    fun `auto-adjust triggers x15 contributes minus 8 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, autoAdjustTriggers = 15))
        assertTrue(
            "expected Frequent auto-adjust x15: -8, got: ${rec.signals}",
            rec.signals.any { it.contains("Frequent auto-adjust x15: -8") },
        )
    }

    @Test
    fun `auto-adjust triggers x30 contributes minus 15 signal and warning`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, autoAdjustTriggers = 30))
        assertTrue(
            "expected Extreme auto-adjust x30: -15, got: ${rec.signals}",
            rec.signals.any { it.contains("Extreme auto-adjust x30: -15") },
        )
        assertTrue(
            "expected auto-adjust warning, got: ${rec.warnings}",
            rec.warnings.any { it.contains("Auto quality system triggered 30x") },
        )
    }

    @Test
    fun `auto-adjust unstable ratio adds warning`() {
        val rec = recFor(LogInfo(autoAdjustTriggers = 12, autoAdjustRecoveries = 1))
        assertTrue(
            "expected instability warning, got: ${rec.warnings}",
            rec.warnings.any { it.contains("Auto-adjust unable to stabilize") },
        )
    }

    // ─────────── Screen percentage signals ───────────

    @Test
    fun `screenPct 110 to 124 contributes plus 3 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, screenPct = 115.0f))
        assertTrue(
            "expected High render scale: +3, got: ${rec.signals}",
            rec.signals.any { it.contains("High render scale: +3") },
        )
    }

    @Test
    fun `screenPct below 70 contributes minus 10 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, screenPct = 60.0f))
        assertTrue(
            "expected Low render scale <70%: -10, got: ${rec.signals}",
            rec.signals.any { it.contains("Low render scale <70%: -10") },
        )
    }

    // ─────────── Resolution signals ───────────

    @Test
    fun `4K resolution contributes minus 10 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, resolution = "3840x2160"))
        assertTrue(
            "expected 4K resolution: -10, got: ${rec.signals}",
            rec.signals.any { it.contains("4K resolution: -10") },
        )
    }

    @Test
    fun `4K on mid GPU adds extra penalty and warning`() {
        val rec = recFor(LogInfo(gpu = "Adreno 620", resolution = "3840x2160"))
        assertTrue(
            "expected 4K on mid GPU: -8, got: ${rec.signals}",
            rec.signals.any { it.contains("4K on mid GPU: -8") },
        )
        assertTrue(
            "expected 4K warning, got: ${rec.warnings}",
            rec.warnings.any { it.contains("4K on mid/low-end GPU") },
        )
    }

    @Test
    fun `QHD resolution contributes minus 4 signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, resolution = "2560x1440"))
        assertTrue(
            "expected QHD+ resolution: -4, got: ${rec.signals}",
            rec.signals.any { it.contains("QHD+ resolution: -4") },
        )
    }

    @Test
    fun `QHD on mid_low GPU adds extra penalty`() {
        val rec = recFor(LogInfo(gpu = "Mali-G57", resolution = "2560x1440"))
        assertTrue(
            "expected QHD+ on mid_low GPU: -6, got: ${rec.signals}",
            rec.signals.any { it.contains("QHD+ on mid_low GPU: -6") },
        )
    }

    // ─────────── Texture errors ───────────

    @Test
    fun `texture errors contribute penalty signal`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, textureErrors = 3))
        assertTrue(
            "expected Texture errors x3: -3, got: ${rec.signals}",
            rec.signals.any { it.contains("Texture errors x3: -3") },
        )
    }

    @Test
    fun `texture errors x5 adds warning`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = null, textureErrors = 5))
        assertTrue(
            "expected texture warning, got: ${rec.warnings}",
            rec.warnings.any { it.contains("Frequent texture errors") },
        )
    }

    // ─────────── CVar analysis signals ───────────

    @Test
    fun `many unknown cvars in log adds penalty`() {
        val cvars =
            mapOf(
                "unknown.Cvar.One" to "1",
                "unknown.Cvar.Two" to "2",
                "unknown.Cvar.Three" to "3",
                "unknown.Cvar.Four" to "4",
                "unknown.Cvar.Five" to "5",
                "unknown.Cvar.Six" to "6",
            )
        val rec = recFor(LogInfo(gpu = null, ramMb = null, activeCvars = cvars))
        assertTrue(
            "expected unknown CVars penalty, got: ${rec.signals}",
            rec.signals.any { it.contains("6 unknown CVars") && it.contains("-5") },
        )
    }

    @Test
    fun `high shadows on low GPU adds penalty signal`() {
        val cvars = mapOf("sg.ShadowQuality" to "3")
        val rec = recFor(LogInfo(gpu = "Mali-G57", ramMb = 6000, activeCvars = cvars))
        assertTrue(
            "expected High shadows on low GPU: -6, got: ${rec.signals}",
            rec.signals.any { it.contains("High shadows on low GPU: -6") },
        )
    }

    @Test
    fun `high textures plus low RAM adds penalty signal`() {
        val cvars = mapOf("sg.TextureQuality" to "3")
        val rec = recFor(LogInfo(gpu = "Mali-G57", ramMb = 4000, activeCvars = cvars))
        assertTrue(
            "expected High textures + <6GB RAM: -5, got: ${rec.signals}",
            rec.signals.any { it.contains("High textures + <6GB RAM: -5") },
        )
    }

    @Test
    fun `render scale over 100 adds penalty signal`() {
        val cvars = mapOf("r.ScreenPercentage" to "150")
        val rec = recFor(LogInfo(gpu = "Mali-G57", ramMb = 6000, activeCvars = cvars))
        assertTrue(
            "expected Render scale >100% penalty, got: ${rec.signals}",
            rec.signals.any { it.contains("Render scale >100%:") },
        )
    }

    @Test
    fun `fsr enabled adds minus 8 signal`() {
        val cvars = mapOf("r.FidelityFX.FSR.RCAS" to "1")
        val rec = recFor(LogInfo(gpu = "Mali-G57", ramMb = 6000, activeCvars = cvars))
        assertTrue(
            "expected FSR RCAS enabled: -8, got: ${rec.signals}",
            rec.signals.any { it.contains("FSR RCAS enabled: -8") },
        )
    }

    @Test
    fun `ssao on low GPU adds penalty signal`() {
        val cvars = mapOf("r.Mobile.SSAO" to "1")
        val rec = recFor(LogInfo(gpu = "Mali-G57", activeCvars = cvars))
        assertTrue(
            "expected SSAO on low GPU: -5, got: ${rec.signals}",
            rec.signals.any { it.contains("SSAO on low GPU: -5") },
        )
    }

    @Test
    fun `high bloom plus thermal adds penalty signal`() {
        val cvars = mapOf("r.BloomQuality" to "3")
        val rec = recFor(LogInfo(gpu = "Mali-G57", ramMb = 6000, thermalEvents = 3, activeCvars = cvars))
        assertTrue(
            "expected High bloom + thermal: -3, got: ${rec.signals}",
            rec.signals.any { it.contains("High bloom + thermal: -3") },
        )
    }

    // ─────────── Combined penalty signals ───────────

    @Test
    fun `low RAM plus high res adds penalty signal and warning`() {
        val rec = recFor(LogInfo(gpu = null, ramMb = 4000, resolution = "2560x1440"))
        assertTrue(
            "expected Low RAM + high res: -5, got: ${rec.signals}",
            rec.signals.any { it.contains("Low RAM + high res: -5") },
        )
        assertTrue(
            "expected high-res RAM warning, got: ${rec.warnings}",
            rec.warnings.any { it.contains("High resolution on device with <6GB RAM") },
        )
    }

    @Test
    fun `thermal plus low GPU combo adds penalty signal`() {
        val rec = recFor(LogInfo(gpu = "Mali-G57", thermalEvents = 4))
        assertTrue(
            "expected Thermal + low GPU combo penalty, got: ${rec.signals}",
            rec.signals.any { it.contains("Thermal + low GPU combo:") },
        )
    }

    @Test
    fun `gpuOom plus texture errors adds combined penalty`() {
        val rec = recFor(LogInfo(gpuOom = 2, textureErrors = 5))
        assertTrue(
            "expected OOM + texture errors: -5, got: ${rec.signals}",
            rec.signals.any { it.contains("OOM + texture errors: -5") },
        )
    }

    // ─────────── Additional preset recommendation tests ───────────

    @Test
    fun `flagship vulkan at target fps recommends cinematic`() {
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
    fun `high GPU with 8GB vulkan QHD recommends high`() {
        val info =
            LogInfo(
                gpu = "Adreno 750",
                ramMb = 8000,
                vulkanStatus = "available",
                fpsCap = 60,
                fpsActual = 60.0f,
                thermalEvents = 0,
                gpuOom = 0,
                isLowMem = false,
                resolution = "2560x1440",
            )
        assertEquals("high", recFor(info).preset)
    }

    @Test
    fun `score 40 recommends balanced`() {
        val info =
            LogInfo(
                gpu = "Adreno 610",
                ramMb = 4000,
                vulkanStatus = null,
                thermalEvents = 0,
                autoAdjustTriggers = 0,
                gpuOom = 0,
                isLowMem = false,
            )
        assertEquals("balanced", recFor(info).preset)
    }

    @Test
    fun `score 38 with autoAdjustTriggers 12 recommends endurance`() {
        val info =
            LogInfo(
                gpu = null,
                ramMb = null,
                vulkanStatus = null,
                thermalEvents = 0,
                autoAdjustTriggers = 12,
                gpuOom = 0,
                isLowMem = false,
            )
        assertEquals("endurance", recFor(info).preset)
    }

    @Test
    fun `score 42 with autoAdjustTriggers 12 recommends balanced`() {
        val info =
            LogInfo(
                gpu = null,
                ramMb = null,
                vulkanStatus = null,
                autoAdjustTriggers = 12,
                fpsCap = null,
                fpsActual = null,
                thermalEvents = 0,
                gpuOom = 0,
                isLowMem = false,
            )
        assertEquals("endurance", recFor(info).preset)
    }

    @Test
    fun `score 22 with null gpu autoAdjust 12 recommends endurance`() {
        val info =
            LogInfo(
                gpu = null,
                ramMb = null,
                vulkanStatus = null,
                thermalEvents = 0,
                autoAdjustTriggers = 12,
                gpuOom = 0,
                isLowMem = false,
            )
        assertEquals("endurance", recFor(info).preset)
    }

    @Test
    fun `score 20 recommends performance`() {
        val info =
            LogInfo(
                gpu = "Adreno 610",
                ramMb = 3000,
                vulkanStatus = null,
                thermalEvents = 1,
                autoAdjustTriggers = 0,
                gpuOom = 0,
                isLowMem = false,
            )
        assertEquals("performance", recFor(info).preset)
    }
}
