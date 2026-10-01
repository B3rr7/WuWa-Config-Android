package com.wuwaconfig.app.config

import com.google.gson.Gson
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.model.ConfigFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * The backup listing: ordering, private/public de-dup, corrupt-JSON tolerance.
 *
 * This logic lived inside `BackupStore.getLocalBackups`, which could not be
 * constructed in a unit test — it takes a `Context` and an `AccessBackend`, and
 * its public half reads `Environment.getExternalStoragePublicDirectory`, which
 * is not stubbed. The listing itself is pure `File` work, so extracting it is
 * what makes these rules pin-able at all.
 *
 * The de-duplication rule is the one worth pinning hardest: a public copy of a
 * private backup shares its data, so listing both shows a single backup twice
 * in the Backups screen.
 */
class BackupStoreTest {
    private lateinit var root: File
    private lateinit var backupDir: File
    private lateinit var publicDir: File
    private val gson = Gson()

    @Before
    fun setUp() {
        root =
            File.createTempFile("backupstore", "test").also {
                it.delete()
                it.mkdirs()
            }
        backupDir = File(root, "backups").also { it.mkdirs() }
        publicDir = File(root, "Downloads/WuWaConfig").also { it.mkdirs() }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    // ── fixtures ──

    private fun writePrivateBackup(
        name: String,
        timestamp: Long,
        type: String = "manual",
    ): ConfigBackup {
        val backup =
            ConfigBackup(
                id = UUID.randomUUID().toString(),
                name = name,
                timestamp = timestamp,
                files = listOf(ConfigFile(name = "Engine.ini", content = "[Core.System]\nPaths=x")),
                type = type,
            )
        File(backupDir, "${backup.id}.json").writeText(gson.toJson(backup))
        return backup
    }

    /** Mirrors what `exportPublicCopy` writes: `<sanitized>_<id8>/` with INIs. */
    private fun writePublicCopy(
        backup: ConfigBackup,
        idSuffix: Boolean = true,
    ) {
        val dirName =
            if (idSuffix) {
                BackupStore.sanitizeDirName(backup.name) + "_" + backup.id.take(8)
            } else {
                BackupStore.sanitizeDirName(backup.name)
            }
        val dir = File(File(publicDir, "Backups"), dirName).also { it.mkdirs() }
        File(dir, "Engine.ini").writeText("[Core.System]\nPaths=x")
    }

    private fun writeLegacyPublicDir(
        name: String,
        mtime: Long,
    ): File {
        val dir = File(File(publicDir, "Backups"), name).also { it.mkdirs() }
        File(dir, "Engine.ini").writeText("[Core.System]\nPaths=x")
        dir.setLastModified(mtime)
        return dir
    }

    private fun listing() = BackupStore.listBackups(backupDir, publicDir, gson)

    private fun names() = listing().map { it.name }

    // ── ordering ──

    @Test
    fun `backups are listed newest first`() {
        writePrivateBackup("oldest", timestamp = 1_000L)
        writePrivateBackup("newest", timestamp = 3_000L)
        writePrivateBackup("middle", timestamp = 2_000L)
        assertEquals(listOf("newest", "middle", "oldest"), names())
    }

    @Test
    fun `a missing backup directory yields an empty listing, not a failure`() {
        backupDir.deleteRecursively()
        assertTrue(listing().isEmpty())
    }

    @Test
    fun `an empty backup directory yields an empty listing`() {
        assertTrue(listing().isEmpty())
    }

    @Test
    fun `a missing public directory is tolerated`() {
        publicDir.deleteRecursively()
        writePrivateBackup("only", timestamp = 1_000L)
        assertEquals(listOf("only"), names())
    }

    // ── private / public de-duplication ──

    @Test
    fun `a public copy of a private backup is not listed twice`() {
        val backup = writePrivateBackup("daily", timestamp = 5_000L)
        writePublicCopy(backup, idSuffix = true)
        // Exactly one entry, and it is the private one (which carries the real
        // timestamp and the "manual" type rather than "legacy").
        val result = listing()
        assertEquals(1, result.size)
        assertEquals("daily", result.single().name)
        assertEquals("manual", result.single().type)
    }

    @Test
    fun `the unsuffixed legacy public layout is also de-duplicated`() {
        // Versions before ids were appended wrote `<sanitized>/` with no suffix.
        val backup = writePrivateBackup("legacy", timestamp = 5_000L)
        writePublicCopy(backup, idSuffix = false)
        assertEquals(listOf("legacy"), names())
    }

    @Test
    fun `a public-only backup is still listed`() {
        // Nothing private corresponds to it, so it must appear.
        writeLegacyPublicDir("public-only", mtime = 4_000L)
        val result = listing()
        assertEquals(listOf("public-only"), names())
        assertEquals("legacy", result.single().type)
    }

    @Test
    fun `a public dir whose name sanitizes to a private backup name is de-duplicated`() {
        // "My Backup" and "My/Backup" sanitise to the same string; the id suffix
        // is what keeps them distinct, so the unsuffixed form must still match.
        val backup = writePrivateBackup("My/Backup", timestamp = 5_000L)
        writePublicCopy(backup, idSuffix = false)
        assertEquals(listOf("My/Backup"), names())
    }

    @Test
    fun `a public dir with no ini files is skipped`() {
        val empty = File(File(publicDir, "Backups"), "no-ini-here").also { it.mkdirs() }
        File(empty, "notes.txt").writeText("not a config")
        writePrivateBackup("real", timestamp = 1_000L)
        assertEquals(listOf("real"), names())
    }

    @Test
    fun `a public dir that is actually a file is skipped`() {
        File(publicDir, "Backups").mkdirs()
        File(File(publicDir, "Backups"), "stray-file").writeText("x")
        writePrivateBackup("real", timestamp = 1_000L)
        assertEquals(listOf("real"), names())
    }

    // ── corrupt data ──

    @Test
    fun `a corrupt private json is skipped without hiding the others`() {
        File(backupDir, "deadbeef.json").writeText("{ this is not json")
        writePrivateBackup("alive", timestamp = 1_000L)
        assertEquals(listOf("alive"), names())
    }

    @Test
    fun `a truncated private json is skipped`() {
        File(backupDir, "truncated.json").writeText("{\"id\":\"x\",\"name\":\"cut-off")
        writePrivateBackup("alive", timestamp = 1_000L)
        assertEquals(listOf("alive"), names())
    }

    @Test
    fun `a non-json file in the backup dir is ignored`() {
        File(backupDir, "README.txt").writeText("not a backup")
        writePrivateBackup("real", timestamp = 1_000L)
        assertEquals(listOf("real"), names())
    }

    @Test
    fun `a corrupt public ini directory does not abort the listing`() {
        val dir = File(File(publicDir, "Backups"), "broken").also { it.mkdirs() }
        File(dir, "Engine.ini").writeText("[Core.System]\nPaths=x")
        // Make the directory unreadable-as-a-config by removing the ini after the
        // fact is not possible without changing mtime semantics; instead assert a
        // directory whose ini is a directory is tolerated.
        File(dir, "Scalability.ini").mkdirs()
        writePrivateBackup("real", timestamp = 1_000L)
        assertEquals(listOf("broken", "real"), names())
    }

    // ── mixed listings ──

    @Test
    fun `private and public-only backups interleave by timestamp`() {
        writePrivateBackup("private-old", timestamp = 1_000L)
        writePrivateBackup("private-new", timestamp = 9_000L)
        writeLegacyPublicDir("public-mid", mtime = 5_000L)
        assertEquals(listOf("private-new", "public-mid", "private-old"), names())
    }

    @Test
    fun `a public-only backup sorts by its directory mtime`() {
        val older = writeLegacyPublicDir("older", mtime = 1_000L)
        val newer = writeLegacyPublicDir("newer", mtime = 2_000L)
        assertEquals(listOf("newer", "older"), names())
        assertTrue(older.exists() && newer.exists())
    }

    @Test
    fun `a public-only backup with a zero mtime is coerced rather than sorting first`() {
        // lastModified() can return 0 on some filesystems; coerceAtLeast(1L) keeps
        // it from sorting ahead of everything as if it were epoch 0... which is
        // actually last, so assert it lands after a real timestamp.
        val dir = File(File(publicDir, "Backups"), "zero-mtime").also { it.mkdirs() }
        File(dir, "Engine.ini").writeText("x")
        dir.setLastModified(0L)
        writePrivateBackup("real", timestamp = 1_000L)
        assertEquals(listOf("real", "zero-mtime"), names())
    }

    @Test
    fun `the listing is stable across repeated calls`() {
        writePrivateBackup("a", timestamp = 1_000L)
        writePrivateBackup("b", timestamp = 2_000L)
        writeLegacyPublicDir("c", mtime = 3_000L)
        assertEquals(listing(), listing())
    }

    @Test
    fun `a large listing keeps newest first`() {
        // 40 backups, written out of order, plus public copies that must not
        // double up.
        val backups = (1..40).map { writePrivateBackup("backup-$it", timestamp = it * 1000L) }
        backups.take(10).forEach { writePublicCopy(it, idSuffix = true) }
        val result = listing()
        assertEquals(40, result.size)
        assertEquals("backup-40", result.first().name)
        assertEquals("backup-1", result.last().name)
        // Strictly descending, so no tie-ordering surprise.
        val timestamps = result.map { it.timestamp }
        assertEquals(timestamps.sortedDescending(), timestamps)
    }

    // ── sanitizeDirName, the de-dup key ──

    @Test
    fun `sanitizeDirName replaces filesystem-hostile characters`() {
        assertEquals("My_Backup", BackupStore.sanitizeDirName("My/Backup"))
        assertEquals("a_b_c", BackupStore.sanitizeDirName("a:b|c"))
        assertEquals("plain", BackupStore.sanitizeDirName("plain"))
    }

    @Test
    fun `sanitizeDirName truncates a very long name`() {
        val long = "x".repeat(500)
        assertTrue(BackupStore.sanitizeDirName(long).length <= 100)
    }

    @Test
    fun `sanitizeDirName keeps dots and dashes`() {
        assertEquals("v1.2-beta", BackupStore.sanitizeDirName("v1.2-beta"))
    }
}
