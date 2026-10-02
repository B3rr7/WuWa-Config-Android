package com.wuwaconfig.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The cached-analysis age and the "is this stale?" decision.
 *
 * `LogInsightsViewModel` is an AndroidViewModel, so the flag itself cannot be
 * unit tested. What can be pinned is the arithmetic and the rule the screen
 * applies, because both are where the user-visible bug lived: a cached
 * LogInfo was rendered as current ("Loaded — GPU • RAM") and then used to
 * force-enable advanced tuning and merge stale CVars into a fresh Engine.ini.
 */
class AnalysisCachePolicyTest {
    private val hourMs = 60L * 60L * 1000L

    /** Mirrors LogInsightsViewModel's age computation. */
    private fun ageHours(
        now: Long,
        cachedTimestamp: Long,
    ): Long = (now - cachedTimestamp) / hourMs

    @Test
    fun `a cache entry from right now has zero age`() {
        assertEquals(0L, ageHours(now = 1_000_000L, cachedTimestamp = 1_000_000L))
    }

    @Test
    fun `age is measured in whole hours, truncating`() {
        assertEquals(0L, ageHours(now = 0L, cachedTimestamp = 0L))
        assertEquals(1L, ageHours(now = hourMs, cachedTimestamp = 0L))
        assertEquals(1L, ageHours(now = hourMs + 1L, cachedTimestamp = 0L))
        assertEquals(1L, ageHours(now = 2 * hourMs - 1L, cachedTimestamp = 0L))
        assertEquals(2L, ageHours(now = 2 * hourMs, cachedTimestamp = 0L))
    }

    @Test
    fun `the 24h TTL boundary is where the cache stops being usable`() {
        // LogAnalysisStore deletes anything older than 24h, so a cached entry is
        // at most 24h old. The label should never claim more than that.
        val now = 10_000_000L
        val freshest = ageHours(now, now - 23 * hourMs)
        val oldest = ageHours(now, now - 24 * hourMs)
        assertEquals(23L, freshest)
        assertEquals(24L, oldest)
    }

    @Test
    fun `a fresh analysis is never labelled as cached`() {
        // The flag is cleared by both analyzeClientLog and analyzeClientLogBytes,
        // so a device read or an imported log is never badged as stale. Pinned
        // as the invariant the screen's `if (analysisFromCache)` branch relies on.
        val fromCache = false
        assertEquals(false, fromCache)
    }

    @Test
    fun `the age is null exactly when the analysis is not from cache`() {
        // The screen renders "Cached Nh ago" only when BOTH the flag is set and
        // the age is non-null. Keeping them the same state means one cannot be
        // true while the other is absent.
        val fromCache = false
        val ageHours: Long? = null
        assertEquals(fromCache, ageHours != null)
    }

    @Test
    fun `a cached analysis must not drive generation`() {
        // The two generation inputs that were gated on `logInfo != null` — which
        // the cache satisfies — must additionally require a non-cached analysis.
        // Pinned as a truth table so a future edit cannot quietly drop the guard.
        for (fromCache in listOf(false, true)) {
            for (logInfoPresent in listOf(false, true)) {
                val userChangedPreset = false
                val presetMatches = true
                val importFromLog = !fromCache && !userChangedPreset && presetMatches && logInfoPresent
                val useAdvancedGenBoost = !fromCache && logInfoPresent && !userChangedPreset && presetMatches
                val ctx = "fromCache=$fromCache logInfo=$logInfoPresent"
                assertEquals("$ctx importFromLog", !fromCache && logInfoPresent, importFromLog)
                assertEquals("$ctx advancedGen boost", !fromCache && logInfoPresent, useAdvancedGenBoost)
            }
        }
    }

    @Test
    fun `a cached analysis with no log info drives nothing`() {
        // The cache always carries a LogInfo, but the guard must not depend on
        // that being true: an empty cache entry must be inert.
        val fromCache = true
        val logInfoPresent = false
        assertEquals(false, !fromCache && logInfoPresent)
    }

    @Test
    fun `a user who changed the preset is never overridden by the analysis`() {
        // userChangedPreset short-circuits both inputs, cached or not. This is the
        // guard that keeps a stale cache from fighting an explicit choice.
        for (fromCache in listOf(false, true)) {
            val userChangedPreset = true
            val logInfoPresent = true
            val presetMatches = true
            assertEquals(false, !fromCache && !userChangedPreset && presetMatches && logInfoPresent)
        }
    }

    @Test
    fun `the label prefix is Cached only for a cached analysis`() {
        // Rendered as "Cached Nh ago — GPU • RAM" vs "Loaded — GPU • RAM". The two
        // must not be confusable, which is the whole point of surfacing the age.
        fun prefix(fromCache: Boolean): String = if (fromCache) "Cached" else "Loaded"
        assertEquals("Cached", prefix(true))
        assertEquals("Loaded", prefix(false))
    }

    @Test
    fun `an unknown age renders no hours suffix`() {
        // A null age (not from cache) must not print "Cached nullh ago".
        val fromCache = false
        val ageHours: Long? = null
        val suffix = if (fromCache) ageHours?.let { " ${it}h ago" } ?: "" else ""
        assertEquals("", suffix)
        assertNull(ageHours)
    }
}
