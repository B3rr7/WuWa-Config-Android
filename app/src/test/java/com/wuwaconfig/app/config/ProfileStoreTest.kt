package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.PlayerProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * ProfileStore persists the player's extracted profile with no TTL. The one
 * behaviour worth pinning is the self-heal: a corrupt file is DELETED, so a bad
 * write cannot wedge the app on every launch.
 */
class ProfileStoreTest {
    private lateinit var tempDir: File
    private lateinit var storeFile: File

    @Before
    fun setUp() {
        tempDir =
            File.createTempFile("profile", "test").also {
                it.delete()
                it.mkdirs()
            }
        storeFile = File(tempDir, "player_profile.json")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun profile(
        uid: String? = "100123456",
        playerLevel: Int? = 60,
        server: String? = " Kuro ",
    ) = PlayerProfile(uid = uid, playerLevel = playerLevel, server = server)

    @Test
    fun `load is null when nothing has been saved`() {
        assertNull(ProfileStore(storeFile).load())
    }

    @Test
    fun `a profile round-trips through the store`() {
        ProfileStore(storeFile).save(profile())
        val loaded = ProfileStore(storeFile).load()
        assertNotNull(loaded)
        assertEquals("100123456", loaded!!.uid)
        assertEquals(60, loaded.playerLevel)
    }

    @Test
    fun `nullable fields survive as null`() {
        ProfileStore(storeFile).save(profile(uid = null, playerLevel = null, server = null))
        val loaded = ProfileStore(storeFile).load()!!
        assertNull(loaded.uid)
        assertNull(loaded.playerLevel)
        assertNull(loaded.server)
    }

    @Test
    fun `save overwrites the previous profile`() {
        val store = ProfileStore(storeFile)
        store.save(profile(uid = "first"))
        store.save(profile(uid = "second"))
        assertEquals("second", store.load()!!.uid)
    }

    @Test
    fun `a corrupt file loads as null and is deleted`() {
        // The self-heal matters: without the delete, every subsequent launch
        // re-reads the same bad bytes and takes the catch branch forever.
        storeFile.writeText("{ this is not json")
        assertNull(ProfileStore(storeFile).load())
        assertTrue("the corrupt file must be removed, not just ignored", !storeFile.exists())
    }

    @Test
    fun `the store is usable again after self-healing`() {
        storeFile.writeText("garbage")
        val store = ProfileStore(storeFile)
        assertNull(store.load())
        store.save(profile(uid = "fresh"))
        assertEquals("fresh", ProfileStore(storeFile).load()!!.uid)
    }

    @Test
    fun `an empty file loads as null`() {
        storeFile.writeText("")
        assertNull(ProfileStore(storeFile).load())
    }

    @Test
    fun `a literal null payload loads as null`() {
        storeFile.writeText("null")
        assertNull(ProfileStore(storeFile).load())
    }

    @Test
    fun `save leaves no temp files behind`() {
        val store = ProfileStore(storeFile)
        repeat(4) { store.save(profile(uid = "u$it")) }
        val leftovers = tempDir.listFiles()!!.map { it.name }.filter { it != "player_profile.json" }
        assertTrue("stray temp files: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `two store instances over the same file agree`() {
        ProfileStore(storeFile).save(profile(uid = "shared"))
        assertEquals("shared", ProfileStore(storeFile).load()!!.uid)
    }
}
