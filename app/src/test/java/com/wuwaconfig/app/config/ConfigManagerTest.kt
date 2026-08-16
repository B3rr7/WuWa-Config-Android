package com.wuwaconfig.app.config

import android.content.Context
import android.os.Environment
import com.wuwaconfig.app.backend.AccessBackend
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.io.File

class ConfigManagerTest {
    private lateinit var mockedEnv: MockedStatic<Environment>
    private lateinit var backend: AccessBackend
    private lateinit var context: Context
    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir =
            File.createTempFile("cfg", "tmp").also {
                it.delete()
                it.mkdirs()
            }
        mockedEnv = Mockito.mockStatic(Environment::class.java)
        Mockito
            .`when`(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            .thenReturn(tempDir as File?)
        backend = Mockito.mock(AccessBackend::class.java)
        context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.filesDir).thenReturn(tempDir)
        Mockito.`when`(context.cacheDir).thenReturn(File(tempDir, "cache"))
    }

    @After
    fun teardown() {
        mockedEnv.close()
        tempDir.deleteRecursively()
    }

    private fun manager() = ConfigManager(context, { backend }, tempDir.absolutePath)

    @Test
    fun `deleteConfigFiles reports deleted count`() =
        runBlocking {
            Mockito.`when`(backend.fileExists(Mockito.anyString())).thenReturn(Result.success(true))
            Mockito.`when`(backend.deleteFile(Mockito.anyString())).thenReturn(Result.success(Unit))
            val result = manager().deleteConfigFiles(setOf("Engine.ini"))
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().contains("Deleted 1"))
        }

    @Test
    fun `deleteConfigFiles empty set is a no-op success`() =
        runBlocking {
            val result = manager().deleteConfigFiles(emptySet())
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().contains("No files selected"))
        }

    @Test
    fun `cleanConfigFiles reports clean when none exist`() =
        runBlocking {
            Mockito.`when`(backend.fileExists(Mockito.anyString())).thenReturn(Result.success(false))
            val result = manager().cleanConfigFiles { }
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().contains("already clean"))
        }
}
