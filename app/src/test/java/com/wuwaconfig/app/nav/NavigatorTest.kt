package com.wuwaconfig.app.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the back-stack mutations, including the one that is easy to get wrong
 * and impossible to see without a device that has not completed onboarding.
 *
 * `NavBackStack` is a plain MutableList-backed class with a public no-arg
 * constructor, so none of this needs a device, a composition or Robolectric —
 * it is the one part of a Navigation 3 migration that is directly unit
 * testable, and it is the part where a mistake is silent.
 */
class NavigatorTest {
    private fun navigatorStartingAt(key: NavKey): Pair<Navigator, NavBackStack<NavKey>> {
        val backStack = NavBackStack(key)
        return Navigator(NavigationState(backStack)) to backStack
    }

    @Test
    fun `navigate pushes onto the stack`() {
        val (navigator, backStack) = navigatorStartingAt(Home)
        navigator.navigate(Backups)
        assertEquals(listOf(Home, Backups), backStack.toList())
    }

    @Test
    fun `goBack pops one destination`() {
        val (navigator, backStack) = navigatorStartingAt(Home)
        navigator.navigate(Backups)
        navigator.navigate(Settings)
        navigator.goBack()
        assertEquals(listOf(Home, Backups), backStack.toList())
    }

    @Test
    fun `goBack at the root is a no-op rather than emptying the stack`() {
        // NavDisplay calls onBack after a back gesture commits. An empty back
        // stack would leave it with nothing to render.
        val (navigator, backStack) = navigatorStartingAt(Home)
        navigator.goBack()
        navigator.goBack()
        assertEquals(listOf(Home), backStack.toList())
    }

    @Test
    fun `replaceAllWith removes Setup so back from Home exits the app`() {
        // This is the regression the method exists for. Navigation 2 spelled it
        // navigate(Home) { popUpTo(Setup) { inclusive = true } }. If Setup were
        // left in the stack, a user who finished onboarding would press back
        // from Home and land in onboarding again.
        val (navigator, backStack) = navigatorStartingAt(Setup)
        navigator.replaceAllWith(Home)
        assertEquals(listOf(Home), backStack.toList())
    }

    @Test
    fun `back from Home after completing setup leaves an empty stack`() {
        val (navigator, backStack) = navigatorStartingAt(Setup)
        navigator.replaceAllWith(Home)
        navigator.goBack()
        assertTrue(
            "back from Home must exit rather than reveal Setup",
            backStack.none { it is Setup },
        )
    }

    @Test
    fun `configgen to reviewtune back returns to configgen`() {
        val (navigator, backStack) = navigatorStartingAt(Home)
        navigator.navigate(ConfigGen)
        navigator.navigate(ReviewTune)
        navigator.goBack()
        assertEquals(listOf(Home, ConfigGen), backStack.toList())
    }

    @Test
    fun `replaceAllWith discards a deep stack, not just the top`() {
        val (navigator, backStack) = navigatorStartingAt(Home)
        navigator.navigate(Backups)
        navigator.navigate(Settings)
        navigator.navigate(UserGuide)
        navigator.replaceAllWith(Home)
        assertEquals(listOf(Home), backStack.toList())
    }

    // -- inner back handlers -------------------------------------------------
    //
    // The INI Editor's file grid and its open file share one NavKey; only the
    // ViewModel tells them apart, so NavDisplay's onBack used to pop the whole
    // destination and jump to Home instead of returning to the grid.

    @Test
    fun `an inner handler consumes the press so the stack is untouched`() {
        val (navigator, backStack) = navigatorStartingAt(Home)
        navigator.navigate(IniEditor)
        var consumed = 0
        navigator.setInnerBackHandler(IniEditor) {
            consumed++
            true
        }
        navigator.goBack()
        assertEquals(1, consumed)
        assertEquals(listOf(Home, IniEditor), backStack.toList())
    }

    @Test
    fun `an inner handler returning false lets the stack pop normally`() {
        val (navigator, backStack) = navigatorStartingAt(Home)
        navigator.navigate(IniEditor)
        navigator.setInnerBackHandler(IniEditor) { false }
        navigator.goBack()
        assertEquals(listOf(Home), backStack.toList())
    }

    @Test
    fun `clearing the handler restores plain pop behaviour`() {
        // The screen disposes the registration on its way out; a leaked handler
        // would keep swallowing back presses for a destination the user left.
        val (navigator, backStack) = navigatorStartingAt(Home)
        navigator.navigate(IniEditor)
        navigator.setInnerBackHandler(IniEditor) { true }
        navigator.setInnerBackHandler(IniEditor, null)
        navigator.goBack()
        assertEquals(listOf(Home), backStack.toList())
    }

    @Test
    fun `a handler only applies to its own destination`() {
        // Keyed by NavKey so a stale registration cannot hijack the next screen.
        val (navigator, backStack) = navigatorStartingAt(Home)
        navigator.navigate(IniEditor)
        navigator.setInnerBackHandler(IniEditor) { true }
        navigator.navigate(Settings)
        navigator.goBack()
        assertEquals(listOf(Home, IniEditor), backStack.toList())
    }

    @Test
    fun `an inner handler still cannot empty the stack at the root`() {
        val (navigator, backStack) = navigatorStartingAt(Home)
        navigator.setInnerBackHandler(Home) { false }
        navigator.goBack()
        assertEquals(listOf(Home), backStack.toList())
    }
}
