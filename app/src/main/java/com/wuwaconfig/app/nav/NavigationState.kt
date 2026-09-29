package com.wuwaconfig.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * Navigation state for a single back stack.
 *
 * The official migration guide's `NavigationState` models N top-level routes
 * with a `backStacks: Map<NavKey, NavBackStack>` plus a `topLevelRoute` that
 * decides which of them is showing. This app has no navigation bar and no
 * per-tab stacks — Home is the only entry point and all eleven other
 * destinations push onto the same list — so that structure would be a map with
 * exactly one key and a field that can only ever equal the start route. It is
 * omitted rather than kept vestigially: [NavigationState] here is the back
 * stack and nothing else.
 *
 * If a navigation bar is ever added, that is the moment to switch to the
 * multi-stack shape, and it will be a real change rather than a refactor of
 * something already carrying the weight.
 *
 * [rememberNavBackStack] is a `StateObject` backed by saved state, so the
 * stack survives configuration changes and process death, which is what
 * NavController's saved back stack gave us for free.
 */
class NavigationState(
    val backStack: NavBackStack<NavKey>,
)

@Composable
fun rememberNavigationState(startRoute: NavKey): NavigationState {
    val backStack = rememberNavBackStack(startRoute)
    return remember(backStack) { NavigationState(backStack) }
}

/**
 * Turns the back stack into the entries [androidx.navigation3.ui.NavDisplay]
 * renders.
 *
 * The `SaveableStateHolderNavEntryDecorator` is what keeps `rememberSaveable`
 * state alive per destination while that destination is in the back stack but
 * not on screen — without it, rotating the device or visiting another screen
 * resets any `rememberSaveable` a screen is holding.
 */
@Composable
fun NavigationState.toEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
    val decorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    return rememberDecoratedNavEntries(backStack, listOf(decorator), entryProvider)
}
