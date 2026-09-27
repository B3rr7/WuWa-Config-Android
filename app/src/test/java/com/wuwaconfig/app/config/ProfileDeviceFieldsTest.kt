package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every line below is copied verbatim from this device's real, decrypted
 * `Client-backup-2026.09.26-21.49.44-2026.09.26-22.13.30.log`
 * (moto g(60), Adreno (TM) 618, Android 16, OpenGL ES).
 *
 * Why this test exists: Player Profile rendered DEVICE and PERFORMANCE as
 * "no data" even though the game had plainly logged all of it. Two independent
 * causes, both covered here.
 *
 * 1. The wrong log file. The game rotates `Client.log` at ~20 MB. The live
 *    `Client.log` was a 92 KB post-rotation tail with ZERO hits for
 *    `K#GPUFamily`, `LogInit: OS:`, `PhysicalMemoryMB` or `AndroidVersion` —
 *    all device data lives in the startup portion of the newest backup.
 *    `readProfile` read only the live file, so it parsed cleanly and returned
 *    an all-null device block. `verifyDeployedCvars` had already been fixed for
 *    exactly this; `readProfile` had not. See ProfileExtractor.readProfile.
 *
 * 2. Regexes that never matched this game's actual wording, so even a correct
 *    file would have yielded nulls for RAM and resolution.
 */
class ProfileDeviceFieldsTest {
    /** The real startup lines, in the order the game emits them. */
    private val realStartupLog =
        listOf(
            "[GameThread]LogRHI: EGL Version: 1.5 Android META-EGL",
            "[GameThread]LogAndroid: Vulkan library detected, checking for available driver",
            "[GameThread]LogAndroid: Error: Failed to init Vulkan because of current driver version 0x801f6000 less than the minimum tolerance version 0x80212000.",
            "[GameThread]LogAndroid: Vulkan driver NOT available.",
            "[GameThread]LogCsMobile: Display: AddSceneData K#GPUFamily : Adreno (TM) 618",
            "[GameThread]LogInit: OS: Android (16), CPU: moto g(60), GPU: Adreno (TM) 618",
            "[2026.09.26-21.49.44:925][  0][GameThread]LogAndroidWindowUtils: Setting Android Resolution, logic resolution Width=2456 and Height=1080, final Width=1632 and Height=720 (requested scale = 1.000000, Scale ratio = 1.300000)",
            "[GameThread]LogAndroid:   VulkanAvailable: false",
            "[GameThread]LogAndroid:   VulkanVersion: 0.0.0",
            "[GameThread]LogAndroid:   AndroidVersion: 16",
            "[GameThread]LogInit: Memory total: Physical=5642.29MB (6GB approx) Available=2471.97MB PageSize=4.0KB",
            "[GameThread]LogAndroid: OpenGL ES will be used.",
            "[GameThread]LogRHI: App is packaged for OpenGL ES 3.1 and an ES 3.2-capable device was detected.",
            "[GameThread]LogRHI: Initializing OpenGL RHI",
        ).joinToString("\n")

    @Test
    fun `the real startup log yields the GPU`() {
        assertEquals("Adreno (TM) 618", LogParser.parseLog(realStartupLog).gpu)
    }

    @Test
    fun `the real startup log yields the android version`() {
        assertEquals("16", LogParser.parseLog(realStartupLog).androidVersion)
    }

    /**
     * `RAM_RE` looked for `PhysicalMemoryMB: <int>` and `RAM_GB_RE` for
     * `Platform has ~<n> GB`. This game writes neither — UE4 emits
     * `Memory total: Physical=5642.29MB (6GB approx)`. Both regexes missed, so
     * RAM showed as a dash even with a correctly-read log.
     */
    @Test
    fun `the real startup log yields total physical RAM in MB`() {
        val ram = LogParser.parseLog(realStartupLog).ramMb
        assertNotNull("RAM must parse from 'Physical=5642.29MB'", ram)
        assertEquals(5642, ram)
    }

    /**
     * The game reports the panel as `Setting Android Resolution, logic
     * resolution Width=2456 and Height=1080`. `RES_RE` required the digits to
     * sit directly after the word "Resolution" and `VIEWPORT_RE` required a
     * literal "ViewportSize" — neither exists here, so resolution stayed null.
     */
    @Test
    fun `the real startup log yields the logical resolution`() {
        // The panel is 2456x1080 (wm size reports 1080x2460). The log's
        // "final Width=" is 1632x720 on the engine's first line and 2456x1080 on
        // its last, so only the "logic resolution" values are stable.
        assertEquals("2456x1080", LogParser.parseLog(realStartupLog).resolution)
    }

    @Test
    fun `the real startup log reports vulkan unavailable and an opengl api`() {
        val info = LogParser.parseLog(realStartupLog)
        // The log names Vulkan only to report that it FAILED to initialise, and
        // then states VulkanAvailable: false and "OpenGL ES will be used." A bare
        // "vulkan" substring scan reported Vulkan here, which is wrong — and it
        // feeds the Vulkan / force-OpenGL toggle in the config generator.
        assertEquals("OpenGL ES", info.api)
        assertEquals("not_available", info.vulkanStatus)
    }

    /**
     * Regression guard for the sticky negative. Every one of these lines contains
     * the substring "vulkan" or "opengl"; only the availability flags decide.
     * Order matters: the failure is stated first, then Vulkan is mentioned again
     * in unrelated CVar lines, which must not resurrect it.
     */
    @Test
    fun `a later bare mention of vulkan does not undo an explicit unavailability`() {
        val info =
            LogParser.parseLog(
                listOf(
                    "[GameThread]LogAndroid: Vulkan driver NOT available.",
                    "[GameThread]LogAndroid:   VulkanAvailable: false",
                    "[GameThread]LogConfig: Setting CVar [[r.Vulkan.DisablePacing:1]]",
                    "[GameThread]LogConfig: Setting CVar [[r.Vulkan.DisableSubpassDeferred:1]]",
                ).joinToString("\n"),
            )
        assertEquals("not_available", info.vulkanStatus)
        assertTrue("api must not be Vulkan, got '${info.api}'", info.api != "Vulkan")
    }

    /** The inverse must still work: a device that really does run Vulkan. */
    @Test
    fun `an explicitly available vulkan is still detected`() {
        val info =
            LogParser.parseLog(
                listOf(
                    "[GameThread]LogAndroid:   VulkanAvailable: true",
                    "[GameThread]LogRHI: Initializing Vulkan RHI",
                ).joinToString("\n"),
            )
        assertEquals("Vulkan", info.api)
        assertEquals("available", info.vulkanStatus)
    }
}
