package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The rollback-journal path used when staging the game's local-storage databases.
 *
 * `ProfileExtractor.pullDb` used to `base64` only the `.db`. SQLite's default journal
 * mode is `DELETE`, so a write interrupted mid-transaction leaves the original pages
 * in a `-journal` sibling; without it the copy opened on the device can be a database
 * caught mid-write with nothing to roll it back with.
 *
 * This asserts the shipped [journalPathFor] rather than a local copy of the
 * convention, for the reason [LogReadingTest] documents for `backupLogSortKey`: a test
 * carrying its own copy keeps passing after the production rule drifts.
 *
 * Scope, stated honestly: this pins the filename rule only. The recovery itself —
 * `SQLiteDatabase.openDatabase` rolling back a hot journal — is an `android.jar` call
 * and returns a default value under `unitTests.isReturnDefaultValues = true`, so it
 * cannot be exercised here without instrumented tests. What was measured on-device
 * (3.7.0, versionCode 180055720) is recorded on `pullDb`: the journal was present but
 * not hot, and both variants read 161 identical rows.
 */
class ProfileJournalPathTest {
    @Test
    fun `the journal sits beside the database as a dash journal sibling`() {
        assertEquals(
            "/data/.../LocalStorage.db-journal",
            journalPathFor("/data/.../LocalStorage.db"),
        )
    }

    @Test
    fun `it applies to the second database too`() {
        assertEquals(
            "/data/.../DeviceStorage.db-journal",
            journalPathFor("/data/.../DeviceStorage.db"),
        )
    }

    @Test
    fun `the journal path is distinct from the database path`() {
        // The whole fix is that these are two files. An off-by-one or a shared-trap
        // that returned the database path would silently reintroduce the bug.
        assertNotEquals(
            journalPathFor("/data/.../LocalStorage.db"),
            "/data/.../LocalStorage.db",
        )
    }

    @Test
    fun `it is a pure suffix rule and does not normalise the input`() {
        // Purity matters: this runs against a caller-supplied local cache path, and a
        // path that got rewritten here would stage the journal somewhere SQLite would
        // never look beside the database.
        assertEquals("a.db-journal", journalPathFor("a.db"))
        assertEquals("./x/y.db-journal", journalPathFor("./x/y.db"))

        // Degenerate input is still a pure suffix, not a crash and not a rewrite.
        // Callers always pass a real cache path; this only pins that the rule has no
        // special case that could relocate the journal away from its database.
        assertEquals("-journal", journalPathFor(""))
    }
}
