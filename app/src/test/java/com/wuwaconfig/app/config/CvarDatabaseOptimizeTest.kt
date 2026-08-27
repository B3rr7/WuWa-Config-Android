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
        val ini = "r.TotallyUnknownCvar=5\n"
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

    @Test
    fun `disables unknown cvar with leading semicolon so the value is no longer applied`() {
        val ini = "r.TotallyUnknownCvar=5\n"
        val out = optimizeIniTextImpl(ini, allCvars, monitored, defaults)
        val line = out.lines().first()
        assertTrue(line.startsWith(";"))
        assertTrue(line.contains("[CvarDB]"))
        assertTrue(line.contains("unknown"))
    }

    @Test
    fun `inline trailing comments do not break redundant default detection`() {
        val ini = "r.Kuro.AutoExposure=1 ; keep me lit\n"
        val out = optimizeIniTextImpl(ini, allCvars, monitored, defaults)
        assertTrue("inline comment must still flag the redundant default", out.contains("[CvarDB]") && out.contains("redundant"))
        assertTrue(out.contains("r.Kuro.AutoExposure=1"))
    }

    @Test
    fun `removal directive lines are never flagged as unknown`() {
        val ini = "-CVars=r.Kuro.AutoExposure\n-CVARS=foliage.DensityScale\n"
        val out = optimizeIniTextImpl(ini, allCvars, monitored, defaults)
        assertFalse("removal directives must be preserved verbatim", out.contains("[CvarDB]"))
        assertTrue(out.contains("-CVars=r.Kuro.AutoExposure"))
        assertTrue(out.contains("-CVARS=foliage.DensityScale"))
    }

    @Test
    fun `inline comment on a differing known cvar keeps it active`() {
        val ini = "r.Kuro.AutoExposure=0 ; dimmed for battery\n"
        val out = optimizeIniTextImpl(ini, allCvars, monitored, defaults)
        assertFalse("value differs from default, must stay active", out.contains("[CvarDB]"))
        assertTrue(out.contains("r.Kuro.AutoExposure=0"))
    }

    @Test
    fun `preserves non-cvar keys like Paths under Core System`() {
        val ini =
            "[Core.System]\n" +
                "Paths=../../../Engine/Content\n" +
                "Paths=%GAMEDIR%Content\n" +
                "Paths=../../../Engine/Plugins/FX/Niagara/Content\n"
        val out = optimizeIniTextImpl(ini, allCvars, monitored, defaults)
        assertTrue("Paths= mount lines must survive optimization", out.contains("Paths=../../../Engine/Content"))
        assertTrue("Paths= mount lines must survive optimization", out.contains("Paths=%GAMEDIR%Content"))
        assertTrue(
            "Paths= mount lines must survive optimization",
            out.contains("Paths=../../../Engine/Plugins/FX/Niagara/Content"),
        )
        assertFalse("non-CVar keys must not be flagged as unknown", out.contains("[CvarDB]"))
    }

    @Test
    fun `keeps known kuro and mobile cvars that are in the database`() {
        // These mirror the entries that back the 120FPS / Ultra / mobile-HBAO toggles
        // and were missing from libUE4_cvars.txt. Once present in the known set they
        // must NOT be commented out.
        val known =
            setOf(
                "r.kuro.maxfps.thirdparty60",
                "r.kuro.maxfps.thirdparty120",
                "r.kuro.graphicsquality.thirdpartyultraenable",
                "r.mobile.hbao",
                "r.screenpercentage",
                "foliage.densityscale",
                "r.kuro.autoexposure",
            )
        val ini =
            "+CVars=r.Kuro.MaxFPS.ThirdParty120=1\n" +
                "+CVars=r.Kuro.GraphicsQuality.ThirdPartyUltraEnable=1\n" +
                "r.Mobile.HBAO=1\n"
        val out = optimizeIniTextImpl(ini, known, monitored, defaults)
        assertTrue("ThirdParty120 must be kept (known CVar)", out.contains("r.Kuro.MaxFPS.ThirdParty120=1"))
        assertTrue(
            "ThirdPartyUltraEnable must be kept (known CVar)",
            out.contains("r.Kuro.GraphicsQuality.ThirdPartyUltraEnable=1"),
        )
        assertTrue("r.Mobile.HBAO must be kept (known CVar)", out.contains("r.Mobile.HBAO=1"))
        assertFalse("known CVars must not be flagged", out.contains("[CvarDB]"))
    }
}
