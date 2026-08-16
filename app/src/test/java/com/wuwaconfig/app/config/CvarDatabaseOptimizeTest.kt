package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CvarDatabaseOptimizeTest {
    private val allCvars = setOf("r.screenpercentage", "foliage.densityscale", "r.kuro.autoexposure")
    private val monitored = setOf("foliage.densityscale", "r.kuro.autoexposure")
    private val defaults = mapOf("r.kuro.autoexposure" to "1", "foliage.densityscale" to "1.0")

    @Test
    fun `keeps known cvar that differs from default`() {
        val ini = "[/Script/Engine.RendererSettings]\nr.Kuro.AutoExposure=0\n"
        val out = optimizeIniTextImpl(ini, allCvars, monitored, defaults)
        assertTrue(out.contains("r.Kuro.AutoExposure=0"))
        assertFalse(out.contains("[CvarDB]"))
    }

    @Test
    fun `comments out redundant monitored cvar matching default`() {
        val ini = "r.Kuro.AutoExposure=1\n"
        val out = optimizeIniTextImpl(ini, allCvars, monitored, defaults)
        assertTrue(out.contains("[CvarDB]") && out.contains("redundant"))
        assertTrue(out.contains("r.Kuro.AutoExposure=1"))
    }

    @Test
    fun `comments out unknown cvar`() {
        val ini = "Some.Unknown.CVar=5\n"
        val out = optimizeIniTextImpl(ini, allCvars, monitored, defaults)
        assertTrue(out.contains("[CvarDB]") && out.contains("unknown"))
    }

    @Test
    fun `preserves section headers comments and non monitored known cvars`() {
        val ini = "[Section]\n; a comment\nr.ScreenPercentage=100\n"
        val out = optimizeIniTextImpl(ini, allCvars, monitored, defaults)
        assertTrue(out.contains("[Section]"))
        assertTrue(out.contains("; a comment"))
        assertTrue(out.contains("r.ScreenPercentage=100"))
    }

    @Test
    fun `handles plus cvar prefix lines`() {
        val ini = "+CVars=r.Kuro.AutoExposure=1\n"
        val out = optimizeIniTextImpl(ini, allCvars, monitored, defaults)
        assertTrue(out.contains("[CvarDB]") && out.contains("redundant"))
    }

    @Test
    fun `empty input stays empty`() {
        assertEquals("", optimizeIniTextImpl("", allCvars, monitored, defaults))
    }
}
