package com.wuwaconfig.app.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * The app's navigation motion, in one place.
 *
 * These used to be inline literals in MainActivity. They are extracted so the
 * push, the pop and the Setup cross-fade cannot drift apart — a transition that
 * disagrees with its counterpart is exactly the kind of thing that reads as
 * "cheap" without anyone being able to say why.
 *
 * ## Why these numbers
 *
 * Navigation is the most-repeated animation in the app — tens of times a day,
 * opening the INI editor or the calculator again and again. That repetition is
 * the dominant constraint: motion that is pleasant once is latency forever on
 * the hundredth press. So the durations sit at the fast end of the range, and
 * the exit is deliberately shorter than the enter. A screen that is going away
 * does not need to be admired on the way out; a screen that is arriving does,
 * because the user is reading it while it settles.
 *
 * ## Why a slide and not a cross-fade
 *
 * The slide is what makes predictive back work. `NavDisplay` scrubs the
 * committed transition with the user's gesture, so a back swipe reveals the
 * previous screen exactly where the finger is. A cross-fade gives the scrub
 * nothing to track and the gesture feels disconnected from the result. This is
 * also why Setup is the one cross-fade: it has no "from" to slide back to, and
 * it is the first thing a new user sees, where a cross-fade reads as calm
 * rather than as another screen sliding in.
 */
object Motion {
    /**
     * Ease-out for anything entering. Starts fast, so the screen is visibly
     * moving on the first frame — which is the moment the user is actually
     * watching. Material's FastOutSlowInEasing (0.2, 0, 0, 1) is flatter in the
     * middle and settles without any snap; this front-loads the movement.
     */
    val EnterEasing: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /**
     * The same curve for exits. The direction of travel does not reverse the
     * easing — an exit that accelerates away reads as sluggish for the same
     * reason an ease-in entry does. What makes an exit feel fast is its shorter
     * duration, not a different curve.
     */
    val ExitEasing: Easing = EnterEasing

    /** Enter duration, ms. Deliberately well under the usual 300ms UI ceiling. */
    const val EnterMs = 200

    /** Exit duration, ms. ~0.7x the enter: leaving should not cost a beat. */
    const val ExitMs = 140

    /**
     * The subtle scale applied to an arriving screen. Not scale(0) — nothing in
     * the physical world appears from nothing, and a screen that pops in from
     * zero reads as a glitch. 0.94 is far enough to give the transition depth,
     * close enough to 1 that it reads as the same screen settling rather than
     * as a zoom.
     */
    const val EnterScale = 0.94f

    /** How far the outgoing screen parallaxes, as a fraction of its width. */
    const val ExitParallax = 0.25f

    /**
     * Enter transition for a destination pushed on top of the current one.
     * The slide offset lambda receives that container's own width, so this
     * needs no size parameter — and it stays correct when the two containers
     * have different widths, which is exactly what a predictive-back scrub
     * produces.
     */
    private fun enterFromRight(): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(EnterMs, easing = EnterEasing),
        ) + fadeIn(animationSpec = tween(EnterMs, easing = EnterEasing)) +
            scaleIn(
                initialScale = EnterScale,
                animationSpec = tween(EnterMs, easing = EnterEasing),
            )

    /** Enter transition for the screen revealed by a pop: it comes from the left. */
    private fun enterFromLeft(): EnterTransition =
        slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(EnterMs, easing = EnterEasing),
        ) + fadeIn(animationSpec = tween(EnterMs, easing = EnterEasing)) +
            scaleIn(
                initialScale = EnterScale,
                animationSpec = tween(EnterMs, easing = EnterEasing),
            )

    /** Exit transition for a screen being covered: it parallaxes a short way left. */
    private fun exitToLeft(): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { -(it * ExitParallax).toInt() },
            animationSpec = tween(ExitMs, easing = ExitEasing),
        ) + fadeOut(animationSpec = tween(ExitMs, easing = ExitEasing))

    /** Exit transition for the top screen on a pop: it leaves to the right. */
    private fun exitToRight(): ExitTransition =
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(ExitMs, easing = ExitEasing),
        ) + fadeOut(animationSpec = tween(ExitMs, easing = ExitEasing))

    /** Screen pushed on top of another. */
    fun push(): ContentTransform = enterFromRight() togetherWith exitToLeft()

    /** Screen revealed by popping the top destination. */
    fun pop(): ContentTransform = enterFromLeft() togetherWith exitToRight()

    /**
     * Setup's cross-fade. No shared spatial relationship with anything, so
     * there is nothing for a slide to track.
     */
    fun crossFade(): ContentTransform =
        fadeIn(animationSpec = tween(260, easing = EnterEasing)) togetherWith
            fadeOut(animationSpec = tween(180, easing = ExitEasing))
}
