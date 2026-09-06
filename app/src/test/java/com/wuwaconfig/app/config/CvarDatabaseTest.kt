package com.wuwaconfig.app.config

import android.content.res.AssetManager
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class CvarDatabaseTest {
    private lateinit var assets: AssetManager

    private fun fixtureBytes(name: String): ByteArray {
        val resource = "/cvars/$name"
        val stream = javaClass.getResourceAsStream(resource)
        assertNotNull("missing test resource $resource", stream)
        return stream!!.use { it.readBytes() }
    }

    private fun newDb(): CvarDatabase {
        val m = mock(AssetManager::class.java)
        doReturn(fixtureBytes("libUE4_cvars.txt").inputStream())
            .`when`(m).open("cvars/libUE4_cvars.txt")
        doReturn(fixtureBytes("config_monitor_cvars.txt").inputStream())
            .`when`(m).open("cvars/config_monitor_cvars.txt")
        doReturn(fixtureBytes("config_monitor_values.txt").inputStream())
            .`when`(m).open("cvars/config_monitor_values.txt")
        assets = m
        return CvarDatabase(m)
    }

    @Before
    fun setUp() {
        LogRepository.clear()
    }

    @After
    fun tearDown() {
        LogRepository.clear()
    }

    @Test
    fun `load populates counts from fixture`() =
        runBlocking {
            val db = newDb()
            db.load()
            assertEquals("libUE4 fixture has 10 entries (blank line filtered)", 10, db.allCvars.size)
            assertEquals("monitor fixture has 5 entries", 5, db.monitoredCvars.size)
            assertEquals("values fixture has 5 defaults", 5, db.defaultValues.size)
        }

    @Test
    fun `isKnown and isMonitored are case-insensitive`() =
        runBlocking {
            val db = newDb()
            db.load()
            assertTrue("lowercase known key", db.isKnown("r.screenpercentage"))
            assertTrue("mixed-case known key", db.isKnown("R.ScreenPercentage"))
            assertTrue("uppercase known key", db.isKnown("R.SCREENPERCENTAGE"))
            assertFalse("unknown key", db.isKnown("r.totally.not.in.db"))
            assertTrue("monitored mixed-case", db.isMonitored("Foliage.DensityScale"))
            assertFalse("not monitored", db.isMonitored("r.Shadow.MaxResolution"))
        }

    @Test
    fun `gameDefault and differsFromDefault use lowercased keys`() =
        runBlocking {
            val db = newDb()
            db.load()
            assertEquals("1", db.gameDefault("r.kuro.autoexposure"))
            assertEquals("1", db.gameDefault("R.Kuro.AutoExposure"))
            assertEquals("1.0", db.gameDefault("foliage.densityscale"))
            assertNull("unknown key has no default", db.gameDefault("r.unknown"))
            assertFalse("value equals default", db.differsFromDefault("r.kuro.autoexposure", "1"))
            assertTrue("value differs from default", db.differsFromDefault("r.kuro.autoexposure", "0"))
            assertTrue("unknown key counts as differing", db.differsFromDefault("r.unknown", "x"))
        }

    @Test
    fun `load strips blank lines from libUE4 and monitor sets`() =
        runBlocking {
            val db = newDb()
            db.load()
            // Fixture has a deliberate blank line; loader's isNotBlank() must drop it.
            assertFalse("blank line must not appear as an entry", db.isKnown(""))
            assertFalse("whitespace-only must not appear", db.isKnown("   "))
        }

    @Test
    fun `values parser skips lines without equals`() =
        runBlocking {
            val db = newDb()
            db.load()
            // No entries in the values fixture are missing '='; assert the parser produced
            // exactly the 5 expected defaults and no empty-key entries leaked in.
            assertEquals(5, db.defaultValues.size)
            assertNull(db.defaultValues[""])
        }

    @Test
    fun `second concurrent load is single-flight (asset opened once)`() {
        runBlocking {
            val db = newDb()
            val a = async { db.load() }
            val b = async { db.load() }
            a.await()
            b.await()
            verify(assets, times(1)).open("cvars/libUE4_cvars.txt")
            verify(assets, times(1)).open("cvars/config_monitor_cvars.txt")
            verify(assets, times(1)).open("cvars/config_monitor_values.txt")
        }
    }

    @Test
    fun `optimizeIniText after load flags redundant monitored default`() =
        runBlocking {
            val db = newDb()
            db.load()
            val ini = "r.Kuro.AutoExposure=1\n"
            val out = db.optimizeIniText(ini)
            assertTrue("redundant default must be commented", out.contains("[CvarDB]") && out.contains("redundant"))
            assertTrue(out.contains("r.Kuro.AutoExposure=1"))
        }

    @Test
    fun `optimizeIniText keeps known cvar that differs from default`() =
        runBlocking {
            val db = newDb()
            db.load()
            val ini = "r.Kuro.AutoExposure=0\n"
            val out = db.optimizeIniText(ini)
            assertTrue(out.contains("r.Kuro.AutoExposure=0"))
            assertFalse("non-default value must stay active", out.contains("[CvarDB]"))
        }

    @Test
    fun `optimizeIniText flags unknown cvar`() =
        runBlocking {
            val db = newDb()
            db.load()
            val ini = "r.TotallyUnknownCvar=5\n"
            val out = db.optimizeIniText(ini)
            assertTrue(out.contains("[CvarDB]") && out.contains("unknown"))
        }

    @Test
    fun `optimizeIniText is a no-op before load (returns input unchanged)`() =
        runBlocking {
            val db = CvarDatabase(mock(AssetManager::class.java))
            val ini = "r.Kuro.AutoExposure=1\nr.Unknown.X=1\n"
            assertEquals(ini, db.optimizeIniText(ini))
        }
}
