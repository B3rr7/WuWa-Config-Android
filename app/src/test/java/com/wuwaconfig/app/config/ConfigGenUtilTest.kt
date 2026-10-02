package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigGenUtilTest {
    @Test
    fun `applies override when value differs`() {
        val ini = "r.ScreenPercentage=100"
        val out = applyCvarOverrides(ini, mapOf("r.ScreenPercentage" to "75"))
        assertEquals("r.ScreenPercentage=75", out)
    }

    @Test
    fun `keeps existing value when override equals current`() {
        val ini = "r.ScreenPercentage=75"
        val out = applyCvarOverrides(ini, mapOf("r.ScreenPercentage" to "75"))
        assertEquals("r.ScreenPercentage=75", out)
    }

    @Test
    fun `applies multiple overrides`() {
        val ini = "r.ScreenPercentage=100\nr.FrameRate=30"
        val out =
            applyCvarOverrides(
                ini,
                mapOf("r.ScreenPercentage" to "75", "r.FrameRate" to "60"),
            )
        assertEquals("r.ScreenPercentage=75\nr.FrameRate=60", out)
    }

    @Test
    fun `ignores override for key not present`() {
        val ini = "r.ScreenPercentage=100"
        val out = applyCvarOverrides(ini, mapOf("r.DoesNotExist" to "1"))
        assertEquals("r.ScreenPercentage=100", out)
    }

    @Test
    fun `preserves leading whitespace of the original line`() {
        val ini = "    r.ScreenPercentage=100"
        val out = applyCvarOverrides(ini, mapOf("r.ScreenPercentage" to "75"))
        assertEquals("    r.ScreenPercentage=75", out)
    }

    @Test
    fun `overrides every occurrence when key repeats`() {
        val ini = "r.Foo=1\nr.Foo=2"
        val out = applyCvarOverrides(ini, mapOf("r.Foo" to "9"))
        assertEquals("r.Foo=9\nr.Foo=9", out)
    }

    @Test
    fun `preserves dedup-last-wins semantics after override`() {
        // deduplicateIniText keeps the LAST occurrence; applyCvarOverrides must touch all
        // of them so the surviving (last) occurrence is actually updated.
        val ini = "r.Bar=1\n; comment\nr.Bar=2"
        val out = applyCvarOverrides(ini, mapOf("r.Bar" to "7"))
        assertEquals("r.Bar=7\n; comment\nr.Bar=7", out)
    }

    // ── case-insensitive key matching ──
    // applyCvarOverrides used to be the ONLY case-SENSITIVE CVar lookup in the codebase:
    // extractCvarNames, deduplicateIniText, mergeWithLogCvars and ForbiddenCvars all
    // lowercase both sides. An override keyed "r.screenpercentage" therefore never matched
    // the generator's mixed-case "r.ScreenPercentage" line and vanished silently — the
    // user set an override and got the default value with no error.

    @Test
    fun `override key case does not matter - lowercase key matches mixed-case cvar`() {
        val ini = "r.ScreenPercentage=100"
        val out = applyCvarOverrides(ini, mapOf("r.screenpercentage" to "75"))
        assertEquals("r.ScreenPercentage=75", out)
    }

    @Test
    fun `override key case does not matter - uppercase key matches mixed-case cvar`() {
        val ini = "r.ScreenPercentage=100"
        val out = applyCvarOverrides(ini, mapOf("R.ScreenPercentage" to "75"))
        assertEquals("r.ScreenPercentage=75", out)
    }

    @Test
    fun `override key case does not matter - mixed-case key matches lowercase cvar`() {
        val ini = "r.screenpercentage=100"
        val out = applyCvarOverrides(ini, mapOf("r.ScreenPercentage" to "75"))
        assertEquals("r.screenpercentage=75", out)
    }

    @Test
    fun `override key case does not matter for the kuro-prefixed cvars the generator emits`() {
        // The generator emits "r.Kuro.AutoExposure" but the override map is keyed lowercase.
        val ini = "r.Kuro.AutoExposure=1\nr.AllowStaticLighting=1"
        val out = applyCvarOverrides(ini, mapOf("r.kuro.autoexposure" to "0"))
        assertEquals("r.Kuro.AutoExposure=0\nr.AllowStaticLighting=1", out)
    }

    @Test
    fun `case-insensitive override still applies to every repeated occurrence`() {
        val ini = "r.ScreenPercentage=100\n; comment\nr.ScreenPercentage=90"
        val out = applyCvarOverrides(ini, mapOf("r.screenpercentage" to "75"))
        assertEquals("r.ScreenPercentage=75\n; comment\nr.ScreenPercentage=75", out)
    }

    @Test
    fun `drift is null when the device block matches the default`() {
        assertNull(coreSystemDrift(DEFAULT_CORE, DEFAULT_CORE))
    }

    @Test
    fun `a path the device mounts but the default omits is reported`() {
        // This is the case that motivates the externalisation: the bundled
        // inventory is stale and the new plugin's content would not mount.
        val device = DEFAULT_CORE + "Paths=../../../Engine/Plugins/NewPlugin/Content"
        val drift = coreSystemDrift(device, DEFAULT_CORE)
        assertNotNull(drift)
        assertTrue(drift!!.contains("NewPlugin"))
        assertTrue(drift.contains("1 path(s) on device"))
    }

    @Test
    fun `a default path the device does not mount is reported separately`() {
        // Normal after a plugin is removed upstream; reported, but not as the
        // failure direction, because the default is a deliberate superset.
        val device = DEFAULT_CORE.filterNot { it.contains("NRD") }
        val drift = coreSystemDrift(device, DEFAULT_CORE)
        assertNotNull(drift)
        assertTrue(drift!!.contains("harmless"))
    }

    @Test
    fun `both directions are reported together`() {
        val device = DEFAULT_CORE.filterNot { it.contains("NRD") } + "Paths=../../../Engine/Plugins/NewPlugin/Content"
        val drift = coreSystemDrift(device, DEFAULT_CORE)
        assertNotNull(drift)
        assertTrue(drift!!.contains("NewPlugin"))
        assertTrue(drift.contains("harmless"))
    }

    @Test
    fun `drift detection ignores whitespace and ordering`() {
        // Index 0 is the header by construction, both from the asset and from
        // extractCoreSystemPaths, so only the Paths= entries may be reordered.
        val shuffled = listOf(DEFAULT_CORE.first()) + DEFAULT_CORE.drop(1).reversed().map { "  ${it.trim()}  " }
        assertNull(coreSystemDrift(shuffled, DEFAULT_CORE))
    }

    @Test
    fun `an unreadable or blockless engine ini falls back to the default`() {
        assertEquals(DEFAULT_CORE, extractCoreSystemPaths(null, DEFAULT_CORE))
        assertEquals(DEFAULT_CORE, extractCoreSystemPaths("", DEFAULT_CORE))
        assertEquals(DEFAULT_CORE, extractCoreSystemPaths("[SystemSettings]\nx=1", DEFAULT_CORE))
        // A [Core.System] header with no Paths= lines is not a usable block.
        assertEquals(DEFAULT_CORE, extractCoreSystemPaths("[Core.System]\n", DEFAULT_CORE))
    }

    @Test
    fun `a device block is returned verbatim including unknown paths`() {
        val ini = "[Core.System]\nPaths=../../../Engine/Content\nPaths=../../../Engine/Plugins/NewPlugin/Content\n[SystemSettings]\nx=1"
        val extracted = extractCoreSystemPaths(ini, DEFAULT_CORE)
        assertEquals(listOf("[Core.System]", "Paths=../../../Engine/Content", "Paths=../../../Engine/Plugins/NewPlugin/Content"), extracted)
    }

    private companion object {
        val DEFAULT_CORE =
            listOf(
                "[Core.System]",
                "Paths=../../../Engine/Content",
                "Paths=../../../Engine/Plugins/Runtime/Nvidia/NRD/Content",
            )
    }
}
