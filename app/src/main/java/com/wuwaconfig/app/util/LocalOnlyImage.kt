package com.wuwaconfig.app.util

import android.net.Uri

/**
 * True when [uri] can only ever resolve to on-device bytes.
 *
 * Both Coil call sites (`GradientBackground`, `SettingsScreen`) render a value the
 * user picked through `ActivityResultContracts.OpenDocument()`, so the only valid
 * form is a `content://` URI. That is checked here so the invariant is enforced
 * rather than assumed.
 *
 * Why it matters: coil-compose 2.x depends on coil-base, which bundles
 * okhttp3 + okio + coil.fetch.HttpUriFetcher and registers that fetcher in the
 * DEFAULT ImageLoader component set. So the release APK ships a complete HTTP/TLS
 * image loader (verified in app/build/outputs/mapping/release/mapping.txt:
 * `coil.fetch.HttpUriFetcher`, 136 okhttp + 114 okio classes) even though no code
 * path can currently reach it. `ImageRequest.data()` accepts a String, so any future
 * code that persisted a remote URL into `bg_image_uri` — or any injected value —
 * would silently activate network egress plus an on-disk HTTP cache. Failing closed
 * here removes that dormant capability. The full fix is the Coil 3 migration
 * (`io.coil-kt.coil3`), where network fetching is a separate opt-in artifact;
 * see the TODO in gradle/libs.versions.toml.
 */
fun isLocalOnlyImageUri(
    uri: String?,
    context: android.content.Context,
): Boolean {
    if (uri.isNullOrBlank()) return false
    val scheme =
        runCatching { Uri.parse(uri).scheme?.lowercase() }.getOrNull() ?: return false
    return when (scheme) {
        "content", "file", "android.resource" -> true
        // A bare relative path resolves against assets/resources, not the network.
        else -> !uri.contains("://")
    }
}
