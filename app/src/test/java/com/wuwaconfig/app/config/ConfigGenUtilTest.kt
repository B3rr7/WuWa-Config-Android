package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
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
}
