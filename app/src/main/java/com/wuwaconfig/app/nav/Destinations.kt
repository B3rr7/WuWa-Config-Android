package com.wuwaconfig.app.nav

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations.
 *
 * These replace the 13 bare strings that used to be passed to
 * `composable("home")` / `navigate("home")`. Two failure modes went away with
 * them: a typo in a `navigate()` call was a runtime no-op that silently did
 * nothing, and a renamed screen was a string search that could miss a call
 * site. Both are now compile errors.
 *
 * All 13 are argument-less, which is deliberate — nothing navigates *to* a
 * particular config file or record, it just says "open the editor" and the
 * screen reads its own state. If a destination ever needs an argument, add it
 * as a constructor property on a `data class` here; do not add a query
 * parameter to the string.
 *
 * This is `@Serializable` and is matched by Navigation2 by the key's serial
 * name, which is why the serial names are pinned in DestinationsTest. The keys
 * do NOT yet implement nav3's `NavKey` interface: keeping that out means this
 * file has no dependency on navigation3, so the type-safe-routes change stands
 * on its own and is not entangled with a Nav2 -> Nav3 swap. The interface is
 * added in that change, not this one.
 */
@Serializable
data object Setup

@Serializable
data object Home

@Serializable
data object Backups

@Serializable
data object ConfigGen

@Serializable
data object ReviewTune

@Serializable
data object Settings

@Serializable
data object UserGuide

@Serializable
data object Pity

@Serializable
data object Profile

@Serializable
data object BattleStats

@Serializable
data object Logs

@Serializable
data object History

@Serializable
data object IniEditor

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
val ALL_DESTINATIONS: List<Any> = listOf(
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
)

/**
 * The destination the app launches into.
 *
 * Extracted as a function purely so the inversion risk is testable: the original
 * was an inline `if (setupDone) "home" else "setup"` in the composition, where
 * flipping the branches compiles fine and ships an onboarding loop to users who
 * already finished it.
 */
fun startDestination(setupDone: Boolean): Any = if (setupDone) Home else Setup
