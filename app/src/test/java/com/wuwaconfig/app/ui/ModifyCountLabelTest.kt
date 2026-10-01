package com.wuwaconfig.app.ui

import com.wuwaconfig.app.model.GamePaths
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The three states the MODIFICATIONS block can be in.
 *
 * This is the decision that made a failed hash-file read render as a confident,
 * all-zero profile: `configModifyCounts` was a non-nullable map that failure
 * reset to `emptyMap()`, and the screen rendered an absent file as `0`.
 *
 * The ViewModel half of the fix (a nullable flow, populated independently of the
 * profile read) cannot be unit tested — ProfileViewModel is an AndroidViewModel
 * and `getApplication()` is null under `isReturnDefaultValues`. This pins the
 * presentation decision, which is where the user-visible bug actually was.
 */
class ModifyCountLabelTest {
    private val engine = "Engine.ini"
    private val scalability = "Scalability.ini"

    // ── a failed read ──

    @Test
    fun `a failed read is labelled unavailable for every file`() {
        for (name in GamePaths.MONITORED_FILES) {
            assertEquals("unavailable", modifyCountLabel(null, name))
        }
    }

    @Test
    fun `a failed read is never rendered as a number`() {
        // The whole point: "0" and "unavailable" must not be interchangeable.
        val label = modifyCountLabel(null, engine)
        assertEquals(false, label == "0")
        assertEquals(false, label == "—")
    }

    // ── a successful read ──

    @Test
    fun `a real count is rendered as that count`() {
        val counts = mapOf(engine to 8, scalability to 3)
        assertEquals("8", modifyCountLabel(counts, engine))
        assertEquals("3", modifyCountLabel(counts, scalability))
    }

    @Test
    fun `a measured zero is rendered as zero, not as a dash`() {
        // Zero is a real measurement here and must stay distinguishable from
        // "the game has not touched this file".
        assertEquals("0", modifyCountLabel(mapOf(engine to 0), engine))
    }

    @Test
    fun `a file absent from the hash file is rendered as a dash`() {
        // HashMonitor only emits a ModifyCount line for files the game has
        // written, so absence is meaningful and is not a zero.
        assertEquals("—", modifyCountLabel(mapOf(engine to 8), scalability))
    }

    @Test
    fun `an empty map means every file is untouched, not that the read failed`() {
        // An empty map is a successful read of a hash file with no counts. It is
        // distinct from null, which is a failed read.
        for (name in GamePaths.MONITORED_FILES) {
            assertEquals("—", modifyCountLabel(emptyMap(), name))
        }
    }

    // ── the distinction the bug erased ──

    @Test
    fun `failed read and untouched file produce different labels`() {
        // Before the fix both rendered as "0" (failure -> emptyMap, absent -> 0),
        // so a broken hash file looked like a pristine one.
        val failed = modifyCountLabel(null, engine)
        val untouched = modifyCountLabel(mapOf(engine to 8), scalability)
        assertEquals("unavailable", failed)
        assertEquals("—", untouched)
        assertEquals(false, failed == untouched)
    }

    @Test
    fun `failed read and measured zero produce different labels`() {
        val failed = modifyCountLabel(null, engine)
        val zero = modifyCountLabel(mapOf(engine to 0), engine)
        assertEquals("unavailable", failed)
        assertEquals("0", zero)
        assertEquals(false, failed == zero)
    }

    @Test
    fun `untouched file and measured zero produce different labels`() {
        // The subtlest pair: both are "nothing has happened", but one is a
        // measurement and the other is an absence of evidence.
        val untouched = modifyCountLabel(mapOf(engine to 8), scalability)
        val zero = modifyCountLabel(mapOf(scalability to 0), scalability)
        assertEquals("—", untouched)
        assertEquals("0", zero)
        assertEquals(false, untouched == zero)
    }

    @Test
    fun `the label is stable for a given state`() {
        val counts = mapOf(engine to 4)
        assertEquals(modifyCountLabel(counts, engine), modifyCountLabel(counts, engine))
        assertEquals(modifyCountLabel(null, engine), modifyCountLabel(null, engine))
    }

    @Test
    fun `every monitored file gets a label in every state`() {
        // No file may be skipped or throw; the screen renders all five rows.
        for (counts in listOf<Map<String, Int>?>(null, emptyMap(), mapOf(engine to 8))) {
            for (name in GamePaths.MONITORED_FILES) {
                val label = modifyCountLabel(counts, name)
                assertEquals(false, label.isBlank())
                assertEquals(false, label.contains("null"))
            }
        }
    }
}
