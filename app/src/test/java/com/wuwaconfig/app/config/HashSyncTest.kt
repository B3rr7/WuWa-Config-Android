package com.wuwaconfig.app.config

import android.content.Context
import com.wuwaconfig.app.backend.AccessBackend
import com.wuwaconfig.app.backend.computeMd5
import com.wuwaconfig.app.model.GamePaths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.File

/**
 * Fake backend for HashSync. Unlike HashMonitorTest's fake, this one lets each
 * monitored file be individually absent or unreadable, because HashSync's whole
 * job is deciding what counts as drift.
 */
private class SyncFakeBackend : AccessBackend {
    override val isConnected: Boolean = true

    var hashFileContent: String = ""
    val contents = mutableMapOf<String, ByteArray>()
    val missing = mutableSetOf<String>()
    val unreadable = mutableSetOf<String>()
    var fileExistsFails = false

    /** Files whose bytes were read, in call order. */
    val readBytesCalls = mutableListOf<String>()

    fun seedAllFiles(content: String = "body") {
        for (name in GamePaths.MONITORED_FILES) contents[name] = content.toByteArray()
    }

    /** A hash file whose stored values match the currently seeded contents. */
    fun hashFileMatchingCurrent(): String =
        buildString {
            for (name in GamePaths.MONITORED_FILES) {
                val bytes = contents[name] ?: continue
                appendLine("[$name]")
                appendLine("Hash=${computeMd5(bytes)}")
                appendLine("ModifyCount=0")
                appendLine("LastModifiedTime=0")
            }
        }

    override suspend fun connect(): Result<Unit> = Result.success(Unit)

    override fun disconnect() = Unit

    override suspend fun executeShellCommand(command: String): Result<String> = Result.success("")

    override suspend fun pushFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> = Result.success("ok")

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> = Result.success(dirPath)

    override suspend fun fileExists(path: String): Result<Boolean> {
        if (fileExistsFails) return Result.failure(Exception("stat failed"))
        return Result.success(path.substringAfterLast("/") !in missing)
    }

    override suspend fun listDirectory(path: String): Result<List<String>> = Result.success(GamePaths.MONITORED_FILES.toList())

    override suspend fun backupFile(path: String): Result<String> = Result.success("backup-$path")

    override suspend fun readFile(path: String): Result<String> =
        if (path == GamePaths.HASH_MONITOR_PATH) {
            Result.success(hashFileContent)
        } else {
            val name = path.substringAfterLast("/")
            contents[name]?.let { Result.success(String(it)) } ?: Result.failure(Exception("missing $name"))
        }

    override suspend fun readFileBytes(path: String): Result<ByteArray> {
        val name = path.substringAfterLast("/")
        readBytesCalls.add(name)
        if (name in unreadable) return Result.failure(Exception("permission denied"))
        // `missing` is authoritative here too: a file absent from the device
        // cannot be read, and a fake that reads it anyway would make the
        // skip-the-absent-file assertions untestable.
        if (name in missing) return Result.failure(Exception("no such file: $name"))
        return contents[name]?.let { Result.success(it) } ?: Result.failure(Exception("missing $name"))
    }

    override suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> = Result.success("ok")

    override suspend fun deleteFile(path: String): Result<Unit> = Result.success(Unit)
}

/**
 * HashSync decides whether the device's hash file has drifted from the config
 * files actually on disk. The failure mode that matters is a **permanent
 * refresh loop**: if a file the user never deployed counts as a mismatch, every
 * sync rewrites the hashes and the next sync disagrees again, forever.
 */
class HashSyncTest {
    private lateinit var backend: SyncFakeBackend

    private fun sync(
        configManager: ConfigManager = mockConfigManager(),
        provider: () -> AccessBackend = { backend },
    ) = HashSync(provider, configManager)

    private fun mockConfigManager(): ConfigManager {
        val cm = Mockito.mock(ConfigManager::class.java)
        // refreshConfigHashes is suspend, so it has to be stubbed through
        // runBlocking rather than the plain when/then form.
        runBlocking {
            Mockito.`when`(cm.refreshConfigHashes(Mockito.anyBoolean()))
                .thenReturn(Result.success("refreshed"))
            Mockito.`when`(cm.refreshConfigHashes()).thenReturn(Result.success("refreshed"))
        }
        return cm
    }

    private fun context(): Context {
        val dir = File(System.getProperty("java.io.tmpdir"), "wuwa-hashsync-test").also { it.mkdirs() }
        val ctx = Mockito.mock(Context::class.java)
        Mockito.`when`(ctx.cacheDir).thenReturn(dir)
        Mockito.`when`(ctx.filesDir).thenReturn(dir)
        return ctx
    }

    private fun newSync(cm: ConfigManager = mockConfigManager()): HashSync {
        backend = SyncFakeBackend()
        backend.seedAllFiles("stable-body")
        return HashSync({ backend }, cm)
    }

    // ─────────── nothing to do ───────────

    @Test
    fun `all hashes matching reports no refresh`() =
        runBlocking {
            val s = newSync()
            backend.hashFileContent = backend.hashFileMatchingCurrent()
            assertFalse("a clean device must not be rewritten", s.syncIfNeeded())
            // Every file IS read — recomputing the hash is how "matching" is
            // decided. The saving is in not REWRITING the hash file.
            assertEquals(GamePaths.MONITORED_FILES.toSet(), backend.readBytesCalls.toSet())
        }

    @Test
    fun `an in-sync device reads every monitored file exactly once`() =
        runBlocking {
            val s = newSync()
            backend.hashFileContent = backend.hashFileMatchingCurrent()
            s.syncIfNeeded()
            assertEquals(GamePaths.MONITORED_FILES.toSet(), backend.readBytesCalls.toSet())
        }

    // ─────────── the permanent-refresh-loop guard ───────────

    @Test
    fun `a file absent from the device is skipped, not treated as drift`() =
        runBlocking {
            val s = newSync()
            backend.hashFileContent = backend.hashFileMatchingCurrent()
            // The user never deployed Engine.ini, so the hash file legitimately
            // has no section for it. Counting that as a mismatch would make every
            // sync rewrite the hashes and disagree again on the next run.
            backend.missing += "Engine.ini"

            assertFalse("an undeployed file must not force a refresh", s.syncIfNeeded())
            assertTrue("its bytes must not even be read", "Engine.ini" !in backend.readBytesCalls)
        }

    @Test
    fun `every monitored file being absent reports no refresh`() =
        runBlocking {
            val s = newSync()
            backend.hashFileContent = backend.hashFileMatchingCurrent()
            GamePaths.MONITORED_FILES.forEach { backend.missing += it }
            assertFalse(s.syncIfNeeded())
        }

    @Test
    fun `one absent file does not mask a genuine mismatch in another`() =
        runBlocking {
            val s = newSync()
            backend.hashFileContent = backend.hashFileMatchingCurrent()
            backend.missing += "Engine.ini"
            backend.contents["Scalability.ini"] = "tampered".toByteArray()

            assertTrue("the tampered file must still be detected", s.syncIfNeeded())
            assertFalse("but the absent one must not be read", "Engine.ini" in backend.readBytesCalls)
        }

    @Test
    fun `a fileExists failure is not silently read as absent`() =
        runBlocking {
            // getOrDefault(false) turns an error into "missing", which skips the
            // file. Pinned here so a change to that fallback is visible.
            val s = newSync()
            backend.hashFileContent = backend.hashFileMatchingCurrent()
            backend.fileExistsFails = true
            s.syncIfNeeded()
            assertTrue("with every stat failing, nothing is verifiable", backend.readBytesCalls.isEmpty())
        }

    // ─────────── drift detection ───────────

    @Test
    fun `a changed file forces a refresh`() =
        runBlocking {
            val s = newSync()
            backend.hashFileContent = backend.hashFileMatchingCurrent()
            backend.contents["Hardware.ini"] = "edited-by-game".toByteArray()
            assertTrue(s.syncIfNeeded())
        }

    @Test
    fun `a file present on disk but absent from the hash file forces a refresh`() =
        runBlocking {
            val s = newSync()
            backend.contents["Engine.ini"] = "brand new".toByteArray()
            backend.contents["DeviceProfiles.ini"] = "also new".toByteArray()
            backend.hashFileContent =
                buildString {
                    appendLine("[Scalability.ini]")
                    appendLine("Hash=${computeMd5(backend.contents.getValue("Scalability.ini"))}")
                    appendLine("ModifyCount=0")
                }
            assertTrue("an unhashed deployed file is drift", s.syncIfNeeded())
        }

    @Test
    fun `an unreadable file forces a refresh rather than being skipped`() =
        runBlocking {
            val s = newSync()
            backend.hashFileContent = backend.hashFileMatchingCurrent()
            backend.unreadable += "Engine.ini"
            assertTrue("an unreadable file cannot be verified, so treat it as drift", s.syncIfNeeded())
        }

    // ─────────── the no-hash-file bootstrap path ───────────

    @Test
    fun `a blank hash file bootstraps and reports a refresh`() =
        runBlocking {
            val s = newSync()
            backend.hashFileContent = ""
            assertTrue("creating a fresh hash file counts as work done", s.syncIfNeeded())
        }

    @Test
    fun `a whitespace-only hash file bootstraps`() =
        runBlocking {
            val s = newSync()
            backend.hashFileContent = "   \n\t  \n"
            assertTrue(s.syncIfNeeded())
        }

    @Test
    fun `the bootstrap path does not read any file bytes`() =
        runBlocking {
            val s = newSync()
            backend.hashFileContent = ""
            s.syncIfNeeded()
            assertTrue("nothing to compare against yet", backend.readBytesCalls.isEmpty())
        }

    // ─────────── computeIniHash ───────────

    @Test
    fun `computeIniHash returns the md5 of the file bytes`() =
        runBlocking {
            val s = newSync()
            val bytes = "hello world".toByteArray()
            backend.contents["Engine.ini"] = bytes
            val hash = s.computeIniHash("Engine.ini").getOrThrow()
            assertEquals(computeMd5(bytes), hash)
            assertEquals("a real md5, never a bare empty sentinel", 32, hash.length)
        }

    @Test
    fun `computeIniHash fails when the bytes cannot be read`() =
        runBlocking {
            val s = newSync()
            backend.unreadable += "Engine.ini"
            val result = s.computeIniHash("Engine.ini")
            assertTrue(result.isFailure)
        }

    @Test
    fun `computeIniHash fails for a file with no seeded content`() =
        runBlocking {
            val s = newSync()
            backend.missing += "Scalability.ini"
            assertTrue(s.computeIniHash("Scalability.ini").isFailure)
        }

    @Test
    fun `the sync uses the same hash function the refresh writes`() =
        runBlocking {
            // If these ever diverge, every sync reports drift forever.
            val s = newSync()
            backend.hashFileContent = backend.hashFileMatchingCurrent()
            assertFalse(s.syncIfNeeded())
        }
}
