package com.wuwaconfig.app.nav

import androidx.navigation3.runtime.NavKey

/**
 * The only thing in the app that is allowed to mutate the back stack.
 *
 * Navigation 3 has no NavController: there is no navigation object at all,
 * just a `NavBackStack` (a MutableList) that the UI observes. Handing that
 * mutable list to screens would let any destination reorder history behind the
 * navigator's back, which is exactly the class of bug NavController's
 * navigate/pop API existed to prevent. Screens get the [Navigator] instead.
 */
class Navigator(
    private val state: NavigationState,
) {
    /** Pushes a destination, matching NavController.navigate(). */
    fun navigate(key: NavKey) {
        state.backStack.add(key)
    }

    /**
     * Pops one destination, matching NavController.popBackStack().
     *
     * Guarded on stack size, and the guard is load-bearing. `removeLastOrNull()`
     * happily empties a one-element list, but NavDisplay's built-in back handler
     * calls this when a back gesture commits; if it ran with only the root
     * destination left, the display would be handed an empty back stack with
     * nothing to render. Navigation 2's popBackStack() did not have this
     * problem - it returned false and popped nothing at the root.
     */
    fun goBack() {
        if (state.backStack.size > 1) {
            state.backStack.removeLastOrNull()
        }
    }

    /**
     * Replaces the whole stack with [key].
     *
     * Exists for exactly one call site: finishing onboarding. Navigation 2
     * expressed it as `navigate(Home) { popUpTo(Setup) { inclusive = true } }`,
     * whose point is that Setup must not remain in the stack — otherwise the
     * user presses back from Home and lands in onboarding again. `navigate()`
     * alone cannot express that, and silently leaving Setup on the stack is a
     * bug that only shows up on a device that has not completed setup, which
     * is exactly why it is a named method rather than an inline `clear()`.
     */
    fun replaceAllWith(key: NavKey) {
        state.backStack.clear()
        state.backStack.add(key)
    }
}
