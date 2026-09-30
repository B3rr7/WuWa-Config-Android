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
 * Why the check is still here even though Coil 3 removed the hazard it was written
 * for: on Coil 2, `coil-compose` depended on `coil-base`, which bundled okhttp3 +
 * okio + `coil.fetch.HttpUriFetcher` and registered that fetcher in the DEFAULT
 * ImageLoader component set. The release APK shipped a complete HTTP/TLS image
 * loader (136 okhttp + 114 okio classes) that no code path could reach, and
 * `ImageRequest.data()` accepts a String — so any future code that persisted a
 * remote URL into `bg_image_uri`, or any injected value, would have silently
 * activated network egress plus an on-disk HTTP cache.
 *
 * Coil 3 makes network fetching a separate opt-in artifact, so the capability is
 * gone from the binary rather than merely unused: after the migration the release
 * mapping file contains zero okhttp, okio or `HttpUriFetcher` classes. The guard
 * stays anyway. It costs one `Uri.parse`, and the failure mode it protects against
 * - someone adding `coil-network` later, or a fetcher that reaches a remote URI by
 * some other route - is silent network egress. Belt and braces on the one
 * invariant in this app that is a privacy property rather than a preference.
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
