package com.wuwaconfig.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage

/**
 * A remote image from Kuro's CDN, loaded through Coil.
 *
 * ## Why Coil rather than the `HttpURLConnection` + `LruCache` loader this replaced
 *
 * That loader kept decoded bitmaps in a process-lifetime `LruCache` and nothing else, so
 * leaving the screen and coming back re-downloaded every portrait; a cold start downloaded
 * them all again. Coil brings a disk cache, in-flight request de-duplication (two
 * composables asking for the same URL no longer race two sockets), connection reuse, and a
 * `memoryCacheKey` that survives recomposition. It is also what the rest of the app uses —
 * `coil-network-okhttp` was already on the classpath (see `GachaAvatar`), so this file was
 * the only place paying for a second HTTP stack.
 *
 * ## Why the error and loading slots are explicit
 *
 * The old loader drew an empty `Color.White.copy(alpha = 0.05f)` box when a fetch failed and
 * nothing at all while it was in flight. A failure was therefore indistinguishable from a
 * genuinely absent asset, which is exactly the report that sent someone looking for missing
 * files in `assets/`. A failure now draws a dimmed warning glyph so the two cases read
 * differently, and the loading state stays empty so the caller's own container shows through
 * instead of flashing a second background.
 *
 * `contentScale` is passed straight through: the icon call sites need `Fit` so square
 * weapon/echo/skill art is not cropped, and only the banner call sites want `Crop`.
 */
@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = { Box(Modifier.fillMaxSize()) },
        error = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxSize(0.35f),
                )
            }
        },
    )
}
