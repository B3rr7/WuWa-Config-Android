package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.DeployRecord
import com.wuwaconfig.app.model.LogInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * DeployHistoryStore is the one store whose eviction and "first write wins"
 * rules can silently corrupt what the History screen shows: a dropped record
 * reads exactly like a deploy that never happened.
 */
class DeployHistoryStoreTest {
    private lateinit var tempDir: File
    private lateinit var storeFile: File

    @Before
    fun setUp() {
        tempDir =
            File.createTempFile("deployHist", "test").also {
                it.delete()
                it.mkdirs()
            }
        storeFile = File(tempDir, "deploy_history.json")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun record(
        id: String,
        timestamp: Long = 0L,
        presetName: String = "balanced",
        baselineFps: Float? = null,
        outcomeTimestamp: Long? = null,
        baselineClientLogSnippet: String = "",
    ) = DeployRecord(
        id = id,
        timestamp = timestamp,
        presetName = presetName,
        baselineFps = baselineFps,
        outcomeTimestamp = outcomeTimestamp,
        baselineClientLogSnippet = baselineClientLogSnippet,
    )

    private fun outcome(
        fps: Float? = 60f,
        thermal: Int = 0,
        oom: Int = 0,
        drops: Int = 0,
    ) = LogInfo(fpsActual = fps, thermalEvents = thermal, gpuOom = oom, dropFrames = drops)

    // ─────────── add / ordering ───────────

    @Test
    fun `the most recently added record comes first`() {
        // Ordering is insertion order, NOT timestamp order — addRecord prepends at
        // index 0 and nothing re-sorts. That is the right semantic for a deploy
        // log ("what did I just do"), but it means a record back-dated by a clock
        // change stays where it was added.
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a", timestamp = 100))
        store.addRecord(record("b", timestamp = 300))
        store.addRecord(record("c", timestamp = 200))
        assertEquals(listOf("c", "b", "a"), store.getAllRecords().map { it.id })
    }

    @Test
    fun `getRecord returns the stored instance or null`() {
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a", presetName = "cinematic"))
        assertEquals("cinematic", store.getRecord("a")?.presetName)
        assertNull(store.getRecord("missing"))
    }

    @Test
    fun `getAllRecords returns a copy, not the live list`() {
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a"))
        val snapshot = store.getAllRecords()
        store.addRecord(record("b"))
        assertEquals("mutating the store must not change a previously returned list", 1, snapshot.size)
    }

    // ─────────── MAX_RECORDS eviction ───────────

    @Test
    fun `history is capped at twenty records, dropping the oldest`() {
        val store = DeployHistoryStore(storeFile)
        repeat(30) { store.addRecord(record("id$it", timestamp = it.toLong())) }
        val all = store.getAllRecords()
        assertEquals("MAX_RECORDS = 20", 20, all.size)
        // Newest-first, so the tail is the oldest survivors.
        assertEquals("id29", all.first().id)
        assertEquals("id10", all.last().id)
        assertFalse("the oldest records must be evicted", all.any { it.id == "id9" })
    }

    @Test
    fun `the cap holds across a reload, not just in memory`() {
        DeployHistoryStore(storeFile).apply {
            repeat(30) { addRecord(record("id$it", timestamp = it.toLong())) }
        }
        assertEquals(20, DeployHistoryStore(storeFile).getAllRecords().size)
    }

    @Test
    fun `the cap is exactly twenty, not twenty-one`() {
        val store = DeployHistoryStore(storeFile)
        repeat(20) { store.addRecord(record("id$it", timestamp = it.toLong())) }
        assertEquals("nothing to evict yet", 20, store.getAllRecords().size)
        assertEquals("id0", store.getAllRecords().last().id)
        store.addRecord(record("id20", timestamp = 20))
        assertEquals(20, store.getAllRecords().size)
        assertFalse(store.getAllRecords().any { it.id == "id0" })
    }

    // ─────────── updateOutcome ───────────

    @Test
    fun `updateOutcome copies the metrics onto the record and stamps it`() {
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a", baselineFps = 55f))
        assertTrue(store.updateOutcome("a", outcome(fps = 61f, thermal = 2, oom = 1, drops = 7)))

        val updated = store.getRecord("a")!!
        assertEquals(61f, updated.outcomeFps!!, 0.001f)
        assertEquals(2, updated.outcomeThermal)
        assertEquals(1, updated.outcomeOom)
        assertEquals(7, updated.outcomeDrops)
        assertNotNull("hasOutcome keys off outcomeTimestamp", updated.outcomeTimestamp)
        assertTrue(updated.hasOutcome)
    }

    @Test
    fun `updateOutcome reports false for an unknown id`() {
        val store = DeployHistoryStore(storeFile)
        assertFalse(store.updateOutcome("nope", outcome()))
        assertEquals(0, store.getAllRecords().size)
    }

    @Test
    fun `the baseline snippet is written once and never overwritten`() {
        // The snippet is captured at deploy time, before the outcome is known. A
        // later update must not clobber it with post-run log text.
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a", baselineClientLogSnippet = "original"))
        store.updateOutcome("a", outcome(), snippet = "first")
        store.updateOutcome("a", outcome(), snippet = "second")
        assertEquals("original", store.getRecord("a")!!.baselineClientLogSnippet)
    }

    @Test
    fun `a snippet fills an empty baseline exactly once`() {
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a"))
        store.updateOutcome("a", outcome(), snippet = "captured")
        assertEquals("captured", store.getRecord("a")!!.baselineClientLogSnippet)
        store.updateOutcome("a", outcome(), snippet = "later")
        assertEquals("captured", store.getRecord("a")!!.baselineClientLogSnippet)
    }

    @Test
    fun `updateOutcome preserves the rest of the record`() {
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a", presetName = "potato", baselineFps = 30f))
        store.updateOutcome("a", outcome(fps = 29f))
        val updated = store.getRecord("a")!!
        assertEquals("potato", updated.presetName)
        assertEquals("the baseline is what the comparison is measured against", 30f, updated.baselineFps!!, 0.001f)
    }

    @Test
    fun `an outcome with a null fps still stamps the record`() {
        // gpuOom/dropFrames come from log markers that can be absent. The record
        // must still count as resolved, otherwise hasOutcome stays false forever
        // and compare() returns null in the UI.
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a", baselineFps = 50f))
        assertTrue(store.updateOutcome("a", outcome(fps = null)))
        assertTrue(store.getRecord("a")!!.hasOutcome)
    }

    // ─────────── compare ───────────

    @Test
    fun `compare returns deltas when both sides are present`() {
        val store = DeployHistoryStore(storeFile)
        store.addRecord(
            DeployRecord(
                id = "a",
                timestamp = 0,
                presetName = "balanced",
                baselineFps = 55f,
                baselineThermal = 1,
                baselineOom = 0,
                baselineDrops = 3,
            ),
        )
        store.updateOutcome("a", outcome(fps = 61f, thermal = 4, oom = 2, drops = 1))

        val cmp = store.compare("a")!!
        assertEquals(6f, cmp.fpsDelta!!, 0.001f)
        assertEquals(3, cmp.thermalDelta)
        assertEquals(2, cmp.oomDelta)
        assertEquals(-2, cmp.dropFramesDelta)
    }

    @Test
    fun `compare is null until an outcome is recorded`() {
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a", baselineFps = 55f))
        assertNull("no outcome yet, so there is nothing to compare", store.compare("a"))
    }

    @Test
    fun `compare is null for an unknown id`() {
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a"))
        store.updateOutcome("a", outcome())
        assertNull(store.compare("missing"))
    }

    @Test
    fun `compare leaves fpsDelta null when the baseline is missing`() {
        // A baseline is only captured when the pre-deploy log could be read, so
        // the null has to survive into the comparison rather than becoming 0.
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a", baselineFps = null))
        store.updateOutcome("a", outcome(fps = 60f))
        assertNull(store.compare("a")!!.fpsDelta)
    }

    // ─────────── delete / clear ───────────

    @Test
    fun `deleteRecord removes only the named record and persists`() {
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a"))
        store.addRecord(record("b"))
        store.deleteRecord("a")
        assertEquals(listOf("b"), store.getAllRecords().map { it.id })
        assertEquals(listOf("b"), DeployHistoryStore(storeFile).getAllRecords().map { it.id })
    }

    @Test
    fun `deleting an unknown id is a no-op, not a crash`() {
        val store = DeployHistoryStore(storeFile)
        store.addRecord(record("a"))
        store.deleteRecord("missing")
        assertEquals(1, store.getAllRecords().size)
    }

    @Test
    fun `clear empties the store on disk too`() {
        val store = DeployHistoryStore(storeFile)
        repeat(3) { store.addRecord(record("id$it")) }
        store.clear()
        assertEquals(0, store.getAllRecords().size)
        assertEquals(0, DeployHistoryStore(storeFile).getAllRecords().size)
    }

    // ─────────── persistence ───────────

    @Test
    fun `records survive a reload with their fields intact`() {
        DeployHistoryStore(storeFile).apply {
            addRecord(
                DeployRecord(
                    id = "a",
                    timestamp = 1234,
                    presetName = "cinematic",
                    acceptedCount = 7,
                    totalCount = 9,
                    baselineFps = 58f,
                    baselineClientLogSnippet = "snip",
                ),
            )
        }
        val reloaded = DeployHistoryStore(storeFile).getRecord("a")!!
        assertEquals(1234L, reloaded.timestamp)
        assertEquals("cinematic", reloaded.presetName)
        assertEquals(7, reloaded.acceptedCount)
        assertEquals(58f, reloaded.baselineFps!!, 0.001f)
        assertEquals("snip", reloaded.baselineClientLogSnippet)
    }

    @Test
    fun `a missing store file starts empty`() {
        assertFalse(storeFile.exists())
        assertEquals(0, DeployHistoryStore(storeFile).getAllRecords().size)
    }

    @Test
    fun `corrupt json degrades to empty instead of throwing`() {
        storeFile.writeText("{ this is not json")
        val store = DeployHistoryStore(storeFile)
        assertEquals("a corrupt store must not take the app down", 0, store.getAllRecords().size)
        // And the store must still be usable afterwards.
        store.addRecord(record("a"))
        assertEquals(listOf("a"), store.getAllRecords().map { it.id })
    }

    @Test
    fun `an empty or blank store file starts empty`() {
        storeFile.writeText("")
        assertEquals(0, DeployHistoryStore(storeFile).getAllRecords().size)
        storeFile.writeText("   \n  ")
        assertEquals(0, DeployHistoryStore(storeFile).getAllRecords().size)
    }

    @Test
    fun `a literal null json payload starts empty`() {
        storeFile.writeText("null")
        assertEquals(0, DeployHistoryStore(storeFile).getAllRecords().size)
    }

    @Test
    fun `writeAtomic leaves no temp files behind in the store dir`() {
        val store = DeployHistoryStore(storeFile)
        repeat(5) { store.addRecord(record("id$it")) }
        val leftovers = tempDir.listFiles()!!.map { it.name }.filter { it != "deploy_history.json" }
        assertTrue("stray temp files: $leftovers", leftovers.isEmpty())
    }
}
