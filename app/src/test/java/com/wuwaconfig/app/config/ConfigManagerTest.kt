package com.wuwaconfig.app.config

import android.content.Context
import android.os.Environment
import com.wuwaconfig.app.backend.AccessBackend
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.io.File

/**
 * Minimal AccessBackend for the pushSingleFile strip test. Mockito chokes on
 * kotlin.Result (a final class) when the mock returns it, so a hand-rolled fake
 * is simpler and lets us assert on the staged file's contents.
 */
private class FakeBackend : AccessBackend {
    override val isConnected: Boolean = true
    val pushed = java.util.concurrent.ConcurrentLinkedQueue<String>()
    var ensureOk: Boolean = true
    var exists: Boolean = false

    override suspend fun connect(): Result<Unit> = Result.success(Unit)

    override fun disconnect() = Unit

    override suspend fun executeShellCommand(command: String): Result<String> = Result.success("")

    override suspend fun pushFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> {
        pushed.add(File(sourcePath).readText())
        return Result.success("ok")
    }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> = if (ensureOk) Result.success(dirPath) else Result.failure(Exception("nope"))

    override suspend fun fileExists(path: String): Result<Boolean> = Result.success(exists)

    override suspend fun listDirectory(path: String): Result<List<String>> = Result.success(emptyList())

    override suspend fun backupFile(path: String): Result<String> = Result.success(path)

    override suspend fun readFile(path: String): Result<String> = Result.success("")

    override suspend fun readFileBytes(path: String): Result<ByteArray> = Result.success(ByteArray(0))

    override suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> = Result.success("ok")

    override suspend fun deleteFile(path: String): Result<Unit> = Result.success(Unit)
}

class ConfigManagerTest {
    private lateinit var mockedEnv: MockedStatic<Environment>
    private lateinit var backend: FakeBackend
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
        backend = FakeBackend()
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
            backend.exists = true
            val result = manager().deleteConfigFiles(setOf("Engine.ini"))
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().contains("Deleted 1"))
        }

    @Test
    fun `deleteConfigFiles empty set is a no-op success`() =
        runBlocking {
            val result = manager().deleteConfigFiles(emptySet())
            assertTrue(result.isSuccess)
        }

    @Test
    fun `cleanConfigFiles reports clean when none exist`() =
        runBlocking {
            backend.exists = false
            val result = manager().cleanConfigFiles { }
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().contains("already clean"))
        }

    // ── pushSingleFile restricted-CVar strip (P0-2) ──
    // The strip must run on every push path, not just the generator. The fake
    // backend captures the staged file's content so we can assert what actually
    // reaches the device.

    @Test
    fun `pushSingleFile strips forbidden CVars by default`() =
        runBlocking {
            backend.ensureOk = true
            val content =
                """
                [SystemSettings]
                r.ShadowQuality=3
                r.Streaming.Boost=1
                r.ScreenPercentage=100
                """.trimIndent()
            val result = manager().pushSingleFile("Engine.ini", content) { }
            assertTrue(result.isSuccess)
            val out = backend.pushed.single()
            assertFalse(out.contains("r.Streaming.Boost"))
            assertFalse(out.contains("r.ScreenPercentage"))
            assertTrue(out.contains("r.ShadowQuality=3"))
        }
}
