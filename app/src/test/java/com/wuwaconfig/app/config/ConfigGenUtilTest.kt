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
    fun `overrides first occurrence when key repeats`() {
        val ini = "r.Foo=1\nr.Foo=2"
        val out = applyCvarOverrides(ini, mapOf("r.Foo" to "9"))
        assertEquals("r.Foo=9\nr.Foo=2", out)
    }
}
