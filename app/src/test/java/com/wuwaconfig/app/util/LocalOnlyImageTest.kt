package com.wuwaconfig.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This is the app's only privacy invariant rather than a user preference: the
 * function decides whether a stored image URI may reach Coil's loader, and the
 * failure mode of getting it wrong is silent network egress from a config
 * utility that ships no network permission for images.
 *
 * It was previously untestable in practice. Under
 * `unitTests.isReturnDefaultValues = true` the `android.net.Uri.parse` call it
 * used returned null, so the function returned false for EVERY input and any
 * test written against it would have passed regardless of the implementation.
 * It is now plain string logic, which is also why it no longer takes a Context
 * it never read.
 */
class LocalOnlyImageTest {
    // ─────────── local schemes: must be allowed ───────────

    @Test
    fun `a content uri is local`() {
        assertTrue(
            isLocalOnlyImageUri(
                "content://com.android.providers.media.documents/document/image%3A42",
            ),
        )
    }

    @Test
    fun `a file uri is local`() {
        assertTrue(isLocalOnlyImageUri("file:///storage/emulated/0/Images/bg.png"))
    }

    @Test
    fun `an android resource uri is local`() {
        assertTrue(isLocalOnlyImageUri("android.resource://com.wuwaconfig.app/drawable/bg"))
    }

    @Test
    fun `a data uri is local`() {
        assertTrue(isLocalOnlyImageUri("data:image/png;base64,iVBORw0KGgo="))
    }

    @Test
    fun `a bare relative path is local`() {
        // Resolves against assets/resources, never the network.
        assertTrue(isLocalOnlyImageUri("backgrounds/default.png"))
    }

    @Test
    fun `an opaque scheme with no authority is local`() {
        assertTrue(isLocalOnlyImageUri("android.resource:drawable/bg"))
    }

    // ─────────── remote schemes: must be rejected ───────────

    @Test
    fun `http is rejected`() {
        assertFalse(isLocalOnlyImageUri("http://example.com/bg.png"))
    }

    @Test
    fun `https is rejected`() {
        assertFalse(isLocalOnlyImageUri("https://example.com/bg.png"))
    }

    @Test
    fun `ftp is rejected`() {
        assertFalse(isLocalOnlyImageUri("ftp://example.com/bg.png"))
    }

    @Test
    fun `an unknown authority-bearing scheme is rejected by default`() {
        // Deny by default: a scheme nobody has thought about must not be the
        // thing that re-opens network egress.
        assertFalse(isLocalOnlyImageUri("gopher://example.com/bg"))
        assertFalse(isLocalOnlyImageUri("ws://example.com/socket"))
    }

    @Test
    fun `scheme matching is case insensitive`() {
        assertFalse(isLocalOnlyImageUri("HTTPS://example.com/bg.png"))
        assertFalse(isLocalOnlyImageUri("HtTpS://example.com/bg.png"))
    }

    @Test
    fun `a local scheme in mixed case is still accepted`() {
        assertTrue(isLocalOnlyImageUri("CONTENT://com.android.providers/document/1"))
        assertTrue(isLocalOnlyImageUri("File:///storage/bg.png"))
    }

    // ─────────── empty / absent ───────────

    @Test
    fun `null is rejected`() {
        assertFalse(isLocalOnlyImageUri(null))
    }

    @Test
    fun `an empty string is rejected`() {
        assertFalse(isLocalOnlyImageUri(""))
    }

    @Test
    fun `a whitespace-only string is rejected`() {
        assertFalse(isLocalOnlyImageUri("   "))
        assertFalse(isLocalOnlyImageUri("\t\n"))
    }

    @Test
    fun `surrounding whitespace on a real uri does not change the verdict`() {
        assertTrue(isLocalOnlyImageUri("  file:///storage/bg.png  "))
        assertFalse(isLocalOnlyImageUri("  https://example.com/bg.png  "))
    }

    // ─────────── edge cases in the parsing itself ───────────

    @Test
    fun `a leading colon is not a scheme`() {
        // indexOf(':') == 0 means there is no scheme name at all. Treating this
        // as local would let ":\\evil" through on a lenient parser.
        assertFalse(isLocalOnlyImageUri("://example.com/bg.png"))
    }

    @Test
    fun `a colon inside a relative path does not make it remote`() {
        assertTrue(isLocalOnlyImageUri("backgrounds/a:b.png"))
    }

    @Test
    fun `a filename containing a remote-looking substring is still local`() {
        assertTrue(isLocalOnlyImageUri("backups/https_example.com.png"))
    }

    @Test
    fun `a localhost url is rejected`() {
        // The cleartext policy permits 127.0.0.1 for the gacha endpoint, but
        // that permission is not a licence for the image loader to fetch.
        assertFalse(isLocalOnlyImageUri("http://127.0.0.1:8080/bg.png"))
    }

    @Test
    fun `a network path query string cannot smuggle a local verdict`() {
        assertFalse(isLocalOnlyImageUri("https://example.com/bg.png?next=file:///etc/passwd"))
    }

    @Test
    fun `a relative path that looks like a url fragment is local`() {
        assertTrue(isLocalOnlyImageUri("./assets/bg.png"))
        assertTrue(isLocalOnlyImageUri("../shared/bg.png"))
    }
}
