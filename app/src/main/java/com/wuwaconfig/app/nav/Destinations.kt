package com.wuwaconfig.app.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations.
 *
 * These replace the bare strings that used to be passed to
 * `composable("home")` / `navigate("home")`. Two failure modes went away with
 * them: a typo in a `navigate()` call was a runtime no-op that silently did
 * nothing, and a renamed screen was a string search that could miss a call
 * site. Both are now compile errors.
 *
 * All are argument-less, which is deliberate — nothing navigates *to* a
 * particular config file or record, it just says "open the editor" and the
 * screen reads its own state. If a destination ever needs an argument, add it
 * as a constructor property on a `data class` here; do not add a query
 * parameter to the string.
 *
 * Each key is `@Serializable` (the annotation is what gives the key a stable
 * serial name for the saved back stack, pinned in DestinationsTest) and
 * implements navigation3's `NavKey`, which is the type `rememberNavBackStack`
 * and `entry<T>` are keyed on.
 */
@Serializable
data object Setup : NavKey

@Serializable
data object Home : NavKey

@Serializable
data object Backups : NavKey

@Serializable
data object ConfigGen : NavKey

@Serializable
data object ReviewTune : NavKey

@Serializable
data object Settings : NavKey

@Serializable
data object UserGuide : NavKey

@Serializable
data object Pity : NavKey

@Serializable
data object Profile : NavKey

@Serializable
data object BattleStats : NavKey

@Serializable
data object Logs : NavKey

@Serializable
data object History : NavKey

@Serializable
data object IniEditor : NavKey

@Serializable
data object MyCharacter : NavKey

/**
 * The full destination set, in declaration order.
 *
 * Exists so the destination inventory has one definition that the test and
 * `DestinationsTest` both read, rather than a list maintained beside the graph.
 * Note what this cannot do: a pure JVM test cannot see inside a `NavHost`, so
 * this list cannot prove the graph matches. What it does pin is the *wire
 * format* of each key (see DestinationsTest), which is the property that makes
 * a saved back stack survive both a process death and an app update.
 */
val ALL_DESTINATIONS: List<NavKey> =
    listOf(
        Setup,
        Home,
        Backups,
        ConfigGen,
        ReviewTune,
        Settings,
        UserGuide,
        Pity,
        Profile,
        BattleStats,
        Logs,
        History,
        IniEditor,
        MyCharacter,
    )

/**
 * The destination the app launches into.
 *
 * Extracted as a function purely so the inversion risk is testable: the original
 * was an inline `if (setupDone) "home" else "setup"` in the composition, where
 * flipping the branches compiles fine and ships an onboarding loop to users who
 * already finished it.
 */
fun startDestination(setupDone: Boolean): NavKey = if (setupDone) Home else Setup
