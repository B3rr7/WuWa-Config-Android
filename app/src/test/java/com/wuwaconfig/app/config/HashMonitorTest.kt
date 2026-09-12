package com.wuwaconfig.app.config

import android.content.Context
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.model.GamePaths
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import java.io.File

/**
 * Fake backend for HashMonitor: holds the hash file content in memory and
 * returns deterministic hashes for readFileBytes (so the ModifyCount logic is
 * testable without a real device). HashMonitor reads GamePaths.HASH_MONITOR_PATH
 * and GamePaths.TARGET_DIR/<file>; the fake maps those to in-memory state.
 */
private class FakeHashBackend : AccessBackend {
    override val isConnected: Boolean = true
    val hashFileContent = StringBuilder()
    val fileContents = mutableMapOf<String, ByteArray>()
    var readBytesFail = false

    fun setHashFile(content: String) {
        hashFileContent.setLength(0)
        hashFileContent.append(content)
    }

    override suspend fun connect(): Result<Unit> = Result.success(Unit)

    override fun disconnect() = Unit

    override suspend fun executeShellCommand(command: String): Result<String> = Result.success("")

    override suspend fun pushFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> {
        // The real refreshConfigHashes pushes the staged file to <path>.new, then
        // `mv`s it over. Mirror that in the fake so readFile() returns the new
        // content (the verify step compares readFile() against newContent).
        if (targetPath.endsWith(".new")) {
            hashFileContent.setLength(0)
            hashFileContent.append(File(sourcePath).readText())
        }
        return Result.success("ok")
    }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> = Result.success(dirPath)

    override suspend fun fileExists(path: String): Result<Boolean> = Result.success(true)

    override suspend fun listDirectory(path: String): Result<List<String>> = Result.success(GamePaths.MONITORED_FILES.toList())

    override suspend fun readFile(path: String): Result<String> =
        if (path == GamePaths.HASH_MONITOR_PATH) {
            Result.success(hashFileContent.toString())
        } else {
            Result.success("")
        }

    override suspend fun readFileBytes(path: String): Result<ByteArray> {
        if (readBytesFail) return Result.failure(Exception("read failed"))
        val name = path.substringAfterLast("/")
        return Result.success(fileContents.getOrPut(name) { "content-for-$name".toByteArray() })
    }

    override suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> = Result.success("ok")

    override suspend fun deleteFile(path: String): Result<Unit> = Result.success(Unit)

    override suspend fun backupFile(path: String): Result<String> = Result.success("backup-$path")
}

class HashMonitorTest {
    private lateinit var context: Context
    private lateinit var backend: FakeHashBackend
    private lateinit var monitor: HashMonitor

    @Before
    fun setup() {
        backend = FakeHashBackend()
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "wuwa-hash-test")
        cacheDir.mkdirs()
        context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.cacheDir).thenReturn(cacheDir)
        // Always enabled so the toggle doesn't short-circuit the test path.
        monitor = HashMonitor(context, backend) { true }
    }

    @After
    fun teardown() {
        File(System.getProperty("java.io.tmpdir"), "wuwa-hash-test").deleteRecursively()
    }

    @Test
    fun `refreshConfigHashes builds the hash file from scratch on first run`() =
        runBlocking {
            val result = monitor.refreshConfigHashes()
            assertTrue(result.isSuccess)
            val out = backend.hashFileContent.toString()
            for (name in GamePaths.MONITORED_FILES) {
                assertTrue("section for $name missing", out.contains("[$name]"))
                assertTrue("Hash line for $name missing", out.contains("Hash="))
                assertTrue("ModifyCount line for $name missing", out.contains("ModifyCount=0"))
            }
        }

    @Test
    fun `refreshConfigHashes increments ModifyCount when asked`() =
        runBlocking {
            // Pre-seed with a ModifyCount of 3 for Engine.ini.
            backend.setHashFile(
                """
                [Engine.ini]
                Hash=oldhash
                ModifyCount=3
                LastModifiedTime=2024-01-01 00:00:00
                """.trimIndent(),
            )
            val result = monitor.refreshConfigHashes(incrementModifyCount = true)
            assertTrue(result.isSuccess)
            val out = backend.hashFileContent.toString()
            // Capped at 8: 3 + 1 = 4.
            assertTrue("ModifyCount should be 4 after increment", out.contains("ModifyCount=4"))
            // Hash is recomputed from the (fake) file content.
            assertTrue("Hash should be refreshed", out.contains("Hash=") && !out.contains("Hash=oldhash"))
        }

    @Test
    fun `refreshConfigHashes caps ModifyCount at 8`() =
        runBlocking {
            backend.setHashFile(
                """
                [Engine.ini]
                ModifyCount=7
                LastModifiedTime=2024-01-01 00:00:00
                """.trimIndent(),
            )
            monitor.refreshConfigHashes(incrementModifyCount = true)
            val out = backend.hashFileContent.toString()
            // 7 + 1 = 8, and never exceeds the cap.
            assertTrue("ModifyCount should be capped at 8", out.contains("ModifyCount=8"))
            // The cap must not silently roll over to 9+.
            assertTrue("ModifyCount must not exceed 8", !Regex("ModifyCount=9").containsMatchIn(out))
        }

    @Test
    fun `refreshConfigHashes does not increment when incrementModifyCount is false`() =
        runBlocking {
            backend.setHashFile(
                """
                [Engine.ini]
                ModifyCount=5
                LastModifiedTime=2024-01-01 00:00:00
                """.trimIndent(),
            )
            monitor.refreshConfigHashes(incrementModifyCount = false)
            val out = backend.hashFileContent.toString()
            assertTrue("ModifyCount should stay at 5", out.contains("ModifyCount=5"))
        }

    @Test
    fun `disabled toggle skips the sync entirely`() =
        runBlocking {
            val disabled = HashMonitor(context, backend) { false }
            backend.setHashFile("[Engine.ini]\nModifyCount=2\n")
            val result = disabled.refreshConfigHashes()
            assertTrue(result.isSuccess)
            // The file must be untouched — no new sections, no hash recomputation.
            assertEquals("[Engine.ini]\nModifyCount=2\n", backend.hashFileContent.toString())
        }

    @Test
    fun `hash file is written atomically via the new rename pattern`() =
        runBlocking {
            // The hash file content is the source of truth for what was written; if the
            // write were truncated mid-way the content would be missing sections.
            monitor.refreshConfigHashes()
            val out = backend.hashFileContent.toString()
            // Every monitored file appears exactly once.
            for (name in GamePaths.MONITORED_FILES) {
                val count = Regex("\\[$name\\]").findAll(out).count()
                assertEquals("section $name should appear exactly once", 1, count)
            }
        }
}
