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
}
