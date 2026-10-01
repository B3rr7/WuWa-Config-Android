package com.wuwaconfig.app.util

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
 * stays anyway. It costs one substring scan, and the failure mode it protects
 * against — someone adding `coil-network` later, or a fetcher that reaches a
 * remote URI by some other route — is silent network egress. Belt and braces on
 * the one invariant in this app that is a privacy property rather than a
 * preference.
 *
 * Deliberately implemented with plain string operations rather than
 * `android.net.Uri.parse`. Two reasons:
 *
 *  1. **It has no unused `Context` parameter.** The original signature took one
 *     and never read it; leaving it in invites a caller to believe the check
 *     consults device state, which would be a much stronger (and untrue) claim.
 *  2. **It is unit-testable.** `unitTests.isReturnDefaultValues = true` makes
 *     `Uri.parse` return null, so a `Uri`-based version silently returns `false`
 *     for *every* input under test — a privacy guard whose tests pass no matter
 *     what the implementation does. A scheme is fully described by the text
 *     before the first `:` anyway, so there is nothing to gain from the parser.
 */
fun isLocalOnlyImageUri(uri: String?): Boolean {
    if (uri.isNullOrBlank()) return false
    val trimmed = uri.trim()
    val separator = trimmed.indexOf(':')
    // No colon at all: a bare relative path, which resolves against
    // assets/resources and never the network.
    if (separator < 0) return true
    // A colon in the first position means an empty scheme name, which is not a
    // relative path at all ("://host/x" is network-shaped). Reject rather than
    // fall through to the local verdict.
    if (separator == 0) return false
    // "//" after the scheme is what makes it an authority-bearing URI
    // ("scheme://host/…"); a bare "scheme:opaque" form without it is still not
    // a remote fetch, so treat it as local rather than rejecting it.
    val rest = trimmed.substring(separator + 1)
    if (!rest.startsWith("//")) return true
    val scheme = trimmed.substring(0, separator).lowercase()
    return when (scheme) {
        "content", "file", "android.resource", "data" -> true
        else -> false
    }
}
