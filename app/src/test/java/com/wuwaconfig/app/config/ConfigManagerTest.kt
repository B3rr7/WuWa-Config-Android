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

    private fun manager() = ConfigManager(context, { backend }, { tempDir.absolutePath })

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
            val result = manager().pushSingleFile("Engine.ini", content, onProgress = { })
            assertTrue(result.isSuccess)
            val out = backend.pushed.single()
            assertFalse(out.contains("r.Streaming.Boost"))
            assertFalse(out.contains("r.ScreenPercentage"))
            assertTrue(out.contains("r.ShadowQuality=3"))
        }

    // ── pushSingleFile honours the CALLER's allowRestrictedCvars ──
    // pushSingleFile used to re-read the GLOBAL pref (WuWaConfigApp.instance
    // .allowRestrictedCvarsEnabled) instead of the opts.allowRestrictedCvars the generator
    // was actually driven with. Whenever the two disagreed the generator's strip decision
    // was silently overridden here — so a config generated with restricted CVars OFF could
    // still reach the device with r.ScreenPercentage on it, and vice versa.
    //
    // The parameter is now part of the signature, so passing it BY NAME is the regression
    // guard: the old signature would not compile with this call. Note the global pref
    // itself cannot be set from a headless JVM test (WuWaConfigApp.instance is a lateinit
    // Application that is never initialised outside an instrumented process, and the default
    // argument already fails closed to "strip"), so the two explicit-argument directions
    // below are what pins the caller's value winning.

    private val contentWithRestricted =
        """
        [SystemSettings]
        r.ShadowQuality=3
        r.Streaming.Boost=1
        r.ScreenPercentage=100
        r.FramePace=60
        """.trimIndent() + "\n"

    @Test
    fun `pushSingleFile strips restricted CVars when the caller passes false`() =
        runBlocking {
            backend.ensureOk = true
            val result =
                manager().pushSingleFile(
                    "Engine.ini",
                    contentWithRestricted,
                    onProgress = { },
                    allowRestrictedCvars = false,
                )
            assertTrue(result.isSuccess)

            val out = backend.pushed.single()
            assertFalse("r.Streaming.Boost must be stripped", out.contains("r.Streaming.Boost"))
            assertFalse("r.ScreenPercentage must be stripped", out.contains("r.ScreenPercentage"))
            // Non-restricted CVars must survive.
            assertTrue(out.contains("r.ShadowQuality=3"))
            assertTrue(out.contains("r.FramePace=60"))
        }

    @Test
    fun `pushSingleFile keeps restricted CVars when the caller passes true`() =
        runBlocking {
            backend.ensureOk = true
            val result =
                manager().pushSingleFile(
                    "Engine.ini",
                    contentWithRestricted,
                    onProgress = { },
                    allowRestrictedCvars = true,
                )
            assertTrue(result.isSuccess)

            val out = backend.pushed.single()
            assertTrue("r.Streaming.Boost must be preserved", out.contains("r.Streaming.Boost"))
            assertTrue("r.ScreenPercentage must be preserved", out.contains("r.ScreenPercentage"))
            assertTrue(out.contains("r.ShadowQuality=3"))
        }

    @Test
    fun `pushSingleFile strip decision is not affected by the order of the two calls`() =
        runBlocking {
            // Same manager, same backend, opposite caller values. If any global/pref state
            // leaked between calls the second result would be a copy of the first.
            backend.ensureOk = true
            val m = manager()

            m.pushSingleFile("Engine.ini", contentWithRestricted, onProgress = { }, allowRestrictedCvars = true)
            m.pushSingleFile("Engine.ini", contentWithRestricted, onProgress = { }, allowRestrictedCvars = false)

            val keep = backend.pushed.toList()[0]
            val strip = backend.pushed.toList()[1]
            assertTrue(keep.contains("r.Streaming.Boost"))
            assertFalse(strip.contains("r.Streaming.Boost"))
        }
}
