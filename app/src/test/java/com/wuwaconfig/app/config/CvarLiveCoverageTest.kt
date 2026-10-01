package com.wuwaconfig.app.config

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The CVar asset must be a SUPERSET of every CVar the game actually sets.
 *
 * `CvarDatabase.optimizeIniTextImpl` treats any name missing from
 * `allCvars` as `unknown CVar` and comments the line out. So a CVar the game
 * sets but the asset does not list is not merely unrecognised — it gets
 * *stripped from the user's config*, silently disabling a setting they can see
 * in the file and reasonably expect to work.
 *
 * This test exists because the binary cannot be mined for a complete list.
 * Of the 706 CVar names observed across three 3.7.0 sessions, 150 appear
 * nowhere in `libUE4.so` — not as standalone UTF-16LE strings, not inside help
 * text. `Electra.Video.MaxDecodedFrames` is present in the binary but is dropped
 * by `mine_so_cvars.py`'s prefix allowlist; `r.Android.VulkanSetting` exists
 * only inside the help string "Vulkan SM5 is disabled via r.Android.VulkanSetting."
 * Relaxing the allowlist was measured and rejected: it admits ~1,590 extra
 * identifiers that are overwhelmingly UFUNCTION/UPROPERTY names and filenames,
 * and junk in `allCvars` would *stop* the gate flagging genuinely unknown CVars.
 *
 * So the decrypted Client.log — the game enumerating its own CVars at startup —
 * is the only complete source, and this test is what keeps it authoritative.
 *
 * `live_cvars_observed.txt` is a committed snapshot of the CVar names collected
 * from the device's `Saved/Logs` (see apk_extract/device_logs). Refresh it when
 * the game updates: `cut -f1 cvars_live_values.tsv | tail -n +2 | sort -f`.
 */
class CvarLiveCoverageTest {
    @Test
    fun `every CVar observed in the game log is active in the asset`() {
        val active = readActiveAsset()
        assertTrue("asset should hold a substantial CVar set, got ${active.size}", active.size > 5_000)

        val observed = readFixture()
        assertTrue("fixture should be populated, got ${observed.size}", observed.size > 500)

        val stripped = observed.filter { it.lowercase() !in active }
        assertTrue(
            "these ${stripped.size} CVar(s) are set by the game but inactive in libUE4_cvars.txt, " +
                "so optimizeIniTextImpl would strip them as 'unknown CVar': " +
                stripped.sorted().joinToString(", "),
            stripped.isEmpty(),
        )
    }

    /** Mirrors CvarDatabase.isActiveCvarName so the test and the loader cannot drift. */
    private fun readActiveAsset(): Set<String> {
        val candidates =
            listOf(
                File("src/main/assets/cvars/libUE4_cvars.txt"),
                File("app/src/main/assets/cvars/libUE4_cvars.txt"),
            )
        val file =
            candidates.firstOrNull { it.isFile }
                ?: throw AssertionError("libUE4_cvars.txt not found from ${System.getProperty("user.dir")}")
        return file.readLines().map { it.trim().lowercase() }.filter { it.isActiveCvarName() }.toSet()
    }

    private fun readFixture(): List<String> {
        val stream =
            checkNotNull(javaClass.classLoader?.getResourceAsStream("cvars/live_cvars_observed.txt")) {
                "live_cvars_observed.txt missing from test resources"
            }
        return stream.bufferedReader().use { r -> r.readLines().map { it.trim() }.filter { it.isNotEmpty() } }
    }
}
