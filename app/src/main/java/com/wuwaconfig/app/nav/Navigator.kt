package com.wuwaconfig.app.nav

import androidx.navigation3.runtime.NavKey

/**
 * Lets a destination claim the back press before the stack is popped.
 *
 * Navigation 3's [NavDisplay][androidx.navigation3.ui.NavDisplay] calls its
 * `onBack` when a system back gesture commits, and the usual answer is to pop
 * the destination. That is wrong for a screen that holds sub-state which is not
 * itself a nav entry - the INI Editor's file grid and its open file are the same
 * [androidx.navigation3.runtime.NavKey], distinguished only by ViewModel state,
 * so a gesture skipped the grid and jumped to the previous screen.
 *
 * Pass a registrar to such a screen; it registers while the sub-state is live
 * and must clear it on dispose. A handler returning true consumed the press.
 */
fun interface InnerBackRegistrar {
    /** Registers [handler], or clears the registration when it is null. */
    fun set(handler: (() -> Boolean)?)

    /** Discards every registration. Used by previews and tests. */
    companion object {
        val None: InnerBackRegistrar = InnerBackRegistrar { }
    }
}

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
    /**
     * Per-destination inner back handlers, keyed by NavKey. See [goBack].
     * A plain mutableMap: it is only ever touched from the main thread, and it
     * is deliberately NOT Compose state - it must not trigger recomposition,
     * only intercept the next back press.
     */
    private val innerBackHandlers = mutableMapOf<NavKey, () -> Boolean>()

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
        // A destination may hold sub-state that is NOT a nav entry (the INI Editor's
        // file grid vs. open file lives in its ViewModel). Such a destination can
        // register an inner handler here; it is offered the back press first and
        // the stack is only popped if it declines. Without this, a system back
        // gesture skips the sub-state and pops the whole destination - the
        // toolbar arrow worked while the gesture shot straight to Home.
        val top = state.backStack.lastOrNull()
        val inner = top?.let { innerBackHandlers[it] }
        if (inner != null && inner()) return
        if (state.backStack.size > 1) {
            state.backStack.removeLastOrNull()
        }
    }

    /**
     * Registers (or, with a null [handler], clears) the inner back handler for
     * [key]. The handler returns true when it consumed the press.
     *
     * Callers must pair every registration with a clear on dispose; the map is
     * keyed by NavKey so a stale entry cannot hijack a different destination.
     */
    fun setInnerBackHandler(
        key: NavKey,
        handler: (() -> Boolean)?,
    ) {
        if (handler == null) innerBackHandlers.remove(key) else innerBackHandlers[key] = handler
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
