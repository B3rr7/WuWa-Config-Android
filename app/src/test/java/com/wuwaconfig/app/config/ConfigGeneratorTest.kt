package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.model.LogInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigGeneratorTest {
    private fun createTestCvarDatabase(): CvarDatabase {
        val assetManagerClass = Class.forName("android.content.res.AssetManager")
        val amConstructor = assetManagerClass.getDeclaredConstructor()
        amConstructor.isAccessible = true
        val assetManager = amConstructor.newInstance()
        val constructor = CvarDatabase::class.java.getDeclaredConstructor(assetManagerClass)
        constructor.isAccessible = true
        return constructor.newInstance(assetManager)
    }

    private val generator = ConfigGenerator(createTestCvarDatabase())

    private val defaultOpts = GeneratorOptions(optimizeWithCvarDb = false, useAdvancedGen = false)

    @Test
    fun `all 8 presets are present`() {
        assertEquals(8, PRESETS.size)
        assertTrue(PRESETS.containsKey("potato"))
        assertTrue(PRESETS.containsKey("endurance"))
        assertTrue(PRESETS.containsKey("performance"))
        assertTrue(PRESETS.containsKey("competitive"))
        assertTrue(PRESETS.containsKey("balanced"))
        assertTrue(PRESETS.containsKey("high"))
        assertTrue(PRESETS.containsKey("ultra"))
        assertTrue(PRESETS.containsKey("cinematic"))
    }

    @Test
    fun `preset detail values match expected mapping`() {
        // detail ranks must increase monotonically with quality so the q0/q1/q2
        // gates never invert (e.g. potato must be lighter than performance).
        assertEquals(0, PRESETS["potato"]!!.detail)
        assertEquals(1, PRESETS["endurance"]!!.detail)
        assertEquals(2, PRESETS["performance"]!!.detail)
        assertEquals(3, PRESETS["competitive"]!!.detail)
        assertEquals(4, PRESETS["balanced"]!!.detail)
        assertEquals(5, PRESETS["high"]!!.detail)
        assertEquals(6, PRESETS["ultra"]!!.detail)
        assertEquals(7, PRESETS["cinematic"]!!.detail)
    }

    @Test
    fun `PresetProfile detail gates map correctly`() {
        val p0 = PresetProfile(100, 5, 2048, 4, 0, 4.0, 3.0, 3.0, 0, -1, 30000, 0, 0, false, 0)
        assertFalse(p0.q0)
        assertFalse(p0.q1)
        assertFalse(p0.q2)

        val p1 = PresetProfile(100, 5, 2048, 4, 0, 4.0, 3.0, 3.0, 1, -1, 30000, 0, 0, false, 0)
        assertTrue(p1.q0)
        assertFalse(p1.q1)
        assertFalse(p1.q2)

        val p2 = PresetProfile(100, 5, 2048, 4, 0, 4.0, 3.0, 3.0, 2, -1, 30000, 1, 1, false, 1)
        assertTrue(p2.q0)
        assertTrue(p2.q1)
        assertFalse(p2.q2)

        val p3 = PresetProfile(100, 5, 2048, 4, 0, 4.0, 3.0, 3.0, 3, -1, 30000, 1, 1, false, 1)
        assertTrue(p3.q0)
        assertTrue(p3.q1)
        assertTrue(p3.q2)
    }

    @Test
    fun `generateWithCorePaths produces non-empty output for high preset`() {
        val result =
            generator.generateWithCorePaths(
                preset = "high",
                opts = defaultOpts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
            )
        assertTrue(result.ini.engine.isNotBlank())
        assertTrue(result.ini.deviceProfiles.isNotBlank())
        assertTrue(result.ini.gameUserSettings.isNotBlank())
        assertEquals("high", result.activePreset)
    }

    @Test
    fun `generateWithCorePaths produces non-empty output for all presets`() {
        for ((name, _) in PRESETS) {
            val result =
                generator.generateWithCorePaths(
                    preset = name,
                    opts = defaultOpts,
                    corePaths = emptyList(),
                    logInfo = LogInfo(),
                )
            assertTrue("$name: Engine.ini should not be empty", result.ini.engine.isNotBlank())
            assertTrue("$name: DeviceProfiles.ini should not be empty", result.ini.deviceProfiles.isNotBlank())
            assertTrue("$name: GameUserSettings.ini should not be empty", result.ini.gameUserSettings.isNotBlank())
        }
    }

    @Test
    fun `generateWithCorePaths strips forbidden CVars when restricted are off`() {
        val opts = defaultOpts.copy(allowRestrictedCvars = false)
        val result =
            generator.generateWithCorePaths(
                preset = "high",
                opts = opts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
            )
        for (cvar in result.cvarNames) {
            assertFalse("$cvar should be stripped", ForbiddenCvars.isForbidden(cvar))
        }
    }

    @Test
    fun `generateWithCorePaths keeps forbidden CVars when restricted are on`() {
        val opts = defaultOpts.copy(allowRestrictedCvars = true)
        val result =
            generator.generateWithCorePaths(
                preset = "high",
                opts = opts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
            )
        val forbiddenInOutput = result.cvarNames.count { ForbiddenCvars.isForbidden(it) }
        assertTrue("Should have forbidden CVars when allowRestrictedCvars=true", forbiddenInOutput > 0)
    }

    @Test
    fun `generateWithCorePaths round-trip extracts generated CVars`() {
        val result =
            generator.generateWithCorePaths(
                preset = "high",
                opts = defaultOpts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
            )
        val extracted = generator.extractCvarNames(result.ini.engine)
        assertTrue(extracted.isNotEmpty())
        assertTrue(result.cvarNames.containsAll(extracted))
    }

    @Test
    fun `generateWithCorePaths with profileOverride uses override`() {
        val override = PresetProfile(100, 5, 2048, 4, 0, 4.0, 3.0, 3.0, 3, -1, 30000, 1, 1, false, 1)
        val result =
            generator.generateWithCorePaths(
                preset = "potato",
                opts = defaultOpts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
                profileOverride = override,
            )
        assertEquals("potato", result.activePreset)
        assertTrue(result.ini.engine.isNotBlank())
    }

    @Test
    fun `generateWithCorePaths with cvarOverrides applies them`() {
        val opts = defaultOpts.copy(cvarOverrides = mapOf("r.Kuro.AutoExposure" to "0"))
        val result =
            generator.generateWithCorePaths(
                preset = "high",
                opts = opts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
            )
        assertTrue(result.ini.engine.contains("r.Kuro.AutoExposure=0"))
    }

    @Test
    fun `generateWithCorePaths with high-end GPU sets high-end tier values`() {
        val logInfo = LogInfo(gpu = "Adreno 750")
        val result =
            generator.generateWithCorePaths(
                preset = "high",
                opts = defaultOpts,
                corePaths = emptyList(),
                logInfo = logInfo,
            )
        assertTrue(result.ini.engine.isNotBlank())
        val engineText = result.ini.engine
        assertTrue(
            "High-end GPU with 'high' preset should use the preset-tuned grassCull (20000)",
            engineText.contains("r.Kuro.Foliage.MobileGrassCullDistanceMax=20000"),
        )
    }

    @Test
    fun `generateWithCorePaths with low-end GPU sets low-end tier values`() {
        val logInfo = LogInfo(gpu = "Adreno 500")
        val result =
            generator.generateWithCorePaths(
                preset = "high",
                opts = defaultOpts,
                corePaths = emptyList(),
                logInfo = logInfo,
            )
        assertTrue(result.ini.engine.isNotBlank())
        val engineText = result.ini.engine
        assertTrue(
            "Low-end GPU with 'high' preset should clamp grassCull to the tier ceiling (16000)",
            engineText.contains("r.Kuro.Foliage.MobileGrassCullDistanceMax=16000"),
        )
    }

    @Test
    fun `generateWithCorePaths GameUserSettings has fullscreen mode 0`() {
        val result =
            generator.generateWithCorePaths(
                preset = "high",
                opts = defaultOpts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
            )
        assertTrue(
            "GameUserSettings should have FullscreenMode=0",
            result.ini.gameUserSettings.contains("FullscreenMode=0"),
        )
    }

    @Test
    fun `generateWithCorePaths with scalability and hardware generation`() {
        val opts = defaultOpts.copy(generateScalability = true, generateHardware = true)
        val result =
            generator.generateWithCorePaths(
                preset = "high",
                opts = opts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
            )
        assertTrue(result.ini.scalability.isNotBlank())
        assertTrue(result.ini.hardware.isNotBlank())
    }

    @Test
    fun `generateWithCorePaths unknown preset falls back to balanced`() {
        val result =
            generator.generateWithCorePaths(
                preset = "nonexistent",
                opts = defaultOpts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
            )
        assertTrue(result.ini.engine.isNotBlank())
        assertEquals("nonexistent", result.activePreset)
    }

    @Test
    fun `new preset fields are wired into generated INIs`() {
        val cinematic =
            generator.generateWithCorePaths(
                preset = "cinematic",
                opts = defaultOpts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
            )
        assertTrue(cinematic.ini.engine.contains("r.AllowStaticLighting=1"))
        assertTrue(cinematic.ini.engine.contains("r.KuroMaterialQualityLevel=3"))
        assertTrue(cinematic.ini.engine.contains("r.Kuro.Movie.EnableCGMovieRendering=1"))
        assertTrue(cinematic.ini.gameUserSettings.contains("sg.PostProcessQuality=3"))

        val potato =
            generator.generateWithCorePaths(
                preset = "potato",
                opts = defaultOpts,
                corePaths = emptyList(),
                logInfo = LogInfo(),
            )
        assertTrue(potato.ini.engine.contains("r.AllowStaticLighting=0"))
        assertTrue(potato.ini.engine.contains("r.KuroMaterialQualityLevel=0"))
        assertTrue(potato.ini.engine.contains("r.Kuro.Movie.EnableCGMovieRendering=0"))
        assertTrue(potato.ini.gameUserSettings.contains("sg.PostProcessQuality=0"))
    }
}
