package com.wuwaconfig.app.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether decorative motion should be suppressed this session.
 *
 * Reads the three platform animation scales and requires **all** of them to be
 * zero. That is exactly what the accessibility "Remove animations" toggle does,
 * and also what the developer "Animation scale off" setting does; deliberately
 * requiring all three means a user who scaled only one channel down (e.g.
 * window animations to 0.5) does not lose the app's own effects. Before this
 * helper the check lived twice, inline and duplicated — once in [com.wuwaconfig.app.ui.components.GlitchText]
 * and once in HomeScreen — and each read only [Settings.Global.ANIMATOR_DURATION_SCALE],
 * so it partially answered the accessibility question with a developer-settings
 * key.
 *
 * Named `ReducedMotion.kt`, not `Motion.kt`: that file already exists here for
 * the navigation transitions, and a second `Motion` declaration would collide
 * with `MainActivity`'s import.
 *
 * Fail-open design: any resolver error returns `false` so the animation stays
 * on rather than leaving the top bar frozen for everyone.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val resolver = context.contentResolver
            listOf(
                Settings.Global.ANIMATOR_DURATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.WINDOW_ANIMATION_SCALE,
            ).all { Settings.Global.getFloat(resolver, it, 1f) == 0f }
        }.getOrDefault(false)
    }
}
