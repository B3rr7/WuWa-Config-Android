package com.wuwaconfig.app.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the serial name of every destination key, plus the start-destination
 * rule.
 *
 * Why the serial name and not "the graph has 13 entries": a pure JVM test
 * cannot see inside the entryProvider, so any assertion about the set of
 * registered destinations would be self-referential. The serial name is the
 * thing that actually has to stay stable across versions, and it is testable —
 * `rememberNavBackStack` persists the stack through saved state, so renaming a
 * key in an app update turns a restored back stack into an entry lookup that
 * misses, and the user lands on an unexpected screen. Renaming a destination
 * is therefore a saved-state format change, and this test is what makes that
 * visible in review rather than in bug reports.
 *
 * Deliberately reads `descriptor.serialName` rather than round-tripping
 * through `Json`: the descriptor is where the name is *registered*, it needs no
 * kotlinx-serialization-json on the test classpath, and it is the same string
 * the saved back stack is keyed on.
 */
class DestinationsTest {
    private fun serialName(key: NavKey): String {
        // The generated `serializer()` is an instance method on the object (the
        // compiler puts it in the object's class, not a separate `$$serializer`
        // class), so it must be invoked on `key` rather than on a null receiver.
        val serializer = key::class.java.getMethod("serializer").invoke(key) as KSerializer<Any>
        return serializer.descriptor.serialName
    }

    private val expectedNames =
        listOf(
            "com.wuwaconfig.app.nav.Setup",
            "com.wuwaconfig.app.nav.Home",
            "com.wuwaconfig.app.nav.Backups",
            "com.wuwaconfig.app.nav.ConfigGen",
            "com.wuwaconfig.app.nav.ReviewTune",
            "com.wuwaconfig.app.nav.Settings",
            "com.wuwaconfig.app.nav.UserGuide",
            "com.wuwaconfig.app.nav.Pity",
            "com.wuwaconfig.app.nav.Profile",
            "com.wuwaconfig.app.nav.BattleStats",
            "com.wuwaconfig.app.nav.Logs",
            "com.wuwaconfig.app.nav.History",
            "com.wuwaconfig.app.nav.IniEditor",
            "com.wuwaconfig.app.nav.MyCharacter",
        )

    @Test
    fun `every destination has a distinct serial name`() {
        val names = ALL_DESTINATIONS.map(::serialName)
        assertEquals(
            "two destinations share a serial name; one is unreachable",
            names.size,
            names.toSet().size,
        )
    }

    @Test
    fun `destination serial names do not change`() {
        // Editing a value below is a saved-state format break for anyone who
        // upgrades while a back stack is pending. Add an entry for a new screen;
        // never edit an existing one, and never rename an existing screen
        // without leaving its old key in place as a deprecated alias.
        assertEquals(expectedNames, ALL_DESTINATIONS.map(::serialName))
    }

    @Test
    fun `inventory has no duplicate entries`() {
        assertEquals(ALL_DESTINATIONS.size, ALL_DESTINATIONS.toSet().size)
    }

    @Test
    fun `start destination is Home once setup is done`() {
        // The regression this guards is an inverted condition: onboarding
        // showing again to a user who already completed it, which is only
        // visible on-device and only after a reinstall wipes the flag.
        assertEquals(Home, startDestination(setupDone = true))
    }

    @Test
    fun `start destination is Setup until setup is done`() {
        assertEquals(Setup, startDestination(setupDone = false))
    }
}
