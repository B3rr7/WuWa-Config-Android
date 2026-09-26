package com.wuwaconfig.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.File

class UpdateManagerTest {
    private fun makeContext(
        pm: PackageManager,
        packageName: String,
    ): Context {
        val ctx = Mockito.mock(Context::class.java)
        Mockito.`when`(ctx.packageManager).thenReturn(pm)
        Mockito.`when`(ctx.packageName).thenReturn(packageName)
        return ctx
    }

    private fun sigWith(bytes: String): Signature {
        val sig = Mockito.mock(Signature::class.java)
        Mockito.`when`(sig.toByteArray()).thenReturn(bytes.toByteArray())
        return sig
    }

    // Deliberately builds the legacy `PackageInfo.signatures` field: these
    // tests exercise the API 26/27 fallback in UpdateManager.signingCerts,
    // where `signingInfo` (API 28+, GET_SIGNING_CERTIFICATES) is unavailable.
    @Suppress("DEPRECATION")
    private fun pkgInfoWith(vararg sigs: Signature): PackageInfo {
        val pi = PackageInfo()
        pi.signatures = arrayOf(*sigs)
        val signingInfo = Mockito.mock(SigningInfo::class.java)
        Mockito.`when`(signingInfo.apkContentsSigners).thenReturn(arrayOf(*sigs))
        pi.signingInfo = signingInfo
        return pi
    }

    @Test
    fun `matching signatures pass verification`() {
        val sig = sigWith("same-cert-bytes")
        val installedPi = pkgInfoWith(sig)
        val archivePi = pkgInfoWith(sig)
        val pm = Mockito.mock(PackageManager::class.java)
        Mockito.`when`(pm.getPackageInfo(Mockito.eq("com.wuwaconfig.app"), Mockito.anyInt())).thenReturn(installedPi)
        Mockito.`when`(pm.getPackageArchiveInfo(Mockito.anyString(), Mockito.anyInt())).thenReturn(archivePi)
        val ctx = makeContext(pm, "com.wuwaconfig.app")
        val apk = File.createTempFile("fake", ".apk")
        assertTrue(UpdateManager.verifySignatureMatchesInstalled(ctx, apk))
        apk.delete()
    }

    @Test
    fun `mismatched signatures fail verification`() {
        val installed = sigWith("installed-cert")
        val other = sigWith("other-cert")
        val installedPi = pkgInfoWith(installed)
        val archivePi = pkgInfoWith(other)
        val pm = Mockito.mock(PackageManager::class.java)
        Mockito.`when`(pm.getPackageInfo(Mockito.eq("com.wuwaconfig.app"), Mockito.anyInt())).thenReturn(installedPi)
        Mockito.`when`(pm.getPackageArchiveInfo(Mockito.anyString(), Mockito.anyInt())).thenReturn(archivePi)
        val ctx = makeContext(pm, "com.wuwaconfig.app")
        val apk = File.createTempFile("fake", ".apk")
        assertFalse(UpdateManager.verifySignatureMatchesInstalled(ctx, apk))
        apk.delete()
    }

    @Test
    fun `missing installed package info fails verification`() {
        val pm = Mockito.mock(PackageManager::class.java)
        Mockito.`when`(pm.getPackageInfo(Mockito.eq("com.wuwaconfig.app"), Mockito.anyInt())).thenReturn(null)
        val ctx = makeContext(pm, "com.wuwaconfig.app")
        val apk = File.createTempFile("fake", ".apk")
        assertFalse(UpdateManager.verifySignatureMatchesInstalled(ctx, apk))
        apk.delete()
    }

    // Legacy-path test: an archive PackageInfo whose signingInfo is null, so
    // UpdateManager must read the deprecated `signatures` field instead.
    @Suppress("DEPRECATION")
    @Test
    fun `archive with null signingInfo falls back to deprecated signatures`() {
        val sig = sigWith("same-cert-bytes")
        val installedPi = pkgInfoWith(sig)
        val archivePi = PackageInfo()
        archivePi.signatures = arrayOf(sig)
        archivePi.signingInfo = null
        val pm = Mockito.mock(PackageManager::class.java)
        Mockito.`when`(pm.getPackageInfo(Mockito.eq("com.wuwaconfig.app"), Mockito.anyInt())).thenReturn(installedPi)
        Mockito.`when`(pm.getPackageArchiveInfo(Mockito.anyString(), Mockito.anyInt())).thenReturn(archivePi)
        val ctx = makeContext(pm, "com.wuwaconfig.app")
        val apk = File.createTempFile("fake", ".apk")
        assertTrue(UpdateManager.verifySignatureMatchesInstalled(ctx, apk))
        apk.delete()
    }

    // Legacy-path setup: installed package also populates the deprecated
    // `signatures` field, mirroring what real API 26/27 devices report.
    @Suppress("DEPRECATION")
    @Test
    fun `key rotation history accepts rotated archive cert`() {
        val current = sigWith("current-cert")
        val old = sigWith("old-cert")
        val installedPi = PackageInfo()
        installedPi.signatures = arrayOf(current)
        val signingInfo = Mockito.mock(SigningInfo::class.java)
        Mockito.`when`(signingInfo.apkContentsSigners).thenReturn(arrayOf(current))
        Mockito.`when`(signingInfo.signingCertificateHistory).thenReturn(arrayOf(current, old))
        installedPi.signingInfo = signingInfo
        val archivePi = pkgInfoWith(old)
        val pm = Mockito.mock(PackageManager::class.java)
        Mockito.`when`(pm.getPackageInfo(Mockito.eq("com.wuwaconfig.app"), Mockito.anyInt())).thenReturn(installedPi)
        Mockito.`when`(pm.getPackageArchiveInfo(Mockito.anyString(), Mockito.anyInt())).thenReturn(archivePi)
        val ctx = makeContext(pm, "com.wuwaconfig.app")
        val apk = File.createTempFile("fake", ".apk")
        assertTrue(UpdateManager.verifySignatureMatchesInstalled(ctx, apk))
        apk.delete()
    }

    // ── pre-release version ordering ──
    // parseVersion used to split on "." and "-" and keep digits only, so "1.2.0-rc1"
    // collapsed to [1,2,0] — byte-identical to the final release. An RC was therefore never
    // offered as an update, and "1.2.0" compared as "not newer" than its own RC. The mirror
    // image was "1.1.5-1" (an Android build suffix) becoming [1,1,5,1], i.e. NEWER than
    // "1.1.5". A pre-release tag must now sort BELOW the matching final release.

    @Test
    fun `a final release is newer than its own release candidate`() {
        assertTrue("1.2.0 must be newer than 1.2.0-rc1", UpdateManager.isNewer("1.2.0", "1.2.0-rc1"))
        assertTrue("1.2.0 must be newer than 1.2.0-rc2", UpdateManager.isNewer("1.2.0", "1.2.0-rc2"))
        assertFalse("a release is not newer than itself", UpdateManager.isNewer("1.2.0", "1.2.0"))
    }

    @Test
    fun `a release candidate is not newer than its final release`() {
        assertFalse("1.2.0-rc1 must not be offered as newer than 1.2.0", UpdateManager.isNewer("1.2.0-rc1", "1.2.0"))
    }

    @Test
    fun `an Android build suffix does not outrank the plain release`() {
        // "1.1.5-1" is Android buildNumber 1 OF 1.1.5, so it is a build of that version,
        // not a version after it. The release must outrank it, and it must never be
        // offered as an upgrade over the release.
        assertFalse("1.1.5-1 must not be offered over 1.1.5", UpdateManager.isNewer("1.1.5-1", "1.1.5"))
        assertTrue("1.1.5 outranks its own build 1", UpdateManager.isNewer("1.1.5", "1.1.5-1"))
    }

    @Test
    fun `pre-release ordering is stable across tag casing and whitespace`() {
        // The lowercase/trim/`v`-prefix normalisation must not create a spurious
        // ordering, but the tag's own rank and counter still have to order correctly:
        // RC2 is genuinely a later release candidate than RC1.
        assertTrue(UpdateManager.isNewer("1.2.0-RC2", "1.2.0-rc1"))
        assertFalse(UpdateManager.isNewer("1.2.0-rc1", "1.2.0-RC2"))
        assertTrue(UpdateManager.isNewer("v1.2.0", "1.2.0-rc1"))
        assertTrue(UpdateManager.isNewer(" 1.2.0 ", "1.2.0-rc1"))
    }

    @Test
    fun `pre-release tags order alpha before beta before rc`() {
        // A lexicographic fold of the first two tag letters saturated and pinned every
        // 2-letter tag to the same rank, so alpha/beta/rc were indistinguishable.
        assertTrue(UpdateManager.isNewer("1.2.0-beta", "1.2.0-alpha"))
        assertTrue(UpdateManager.isNewer("1.2.0-rc1", "1.2.0-beta"))
        assertFalse(UpdateManager.isNewer("1.2.0-alpha", "1.2.0-beta"))
        assertFalse(UpdateManager.isNewer("1.2.0-beta", "1.2.0-rc1"))
    }

    @Test
    fun `an Android build suffix orders among its own siblings`() {
        assertTrue(UpdateManager.isNewer("1.1.5-43", "1.1.5-42"))
        assertFalse(UpdateManager.isNewer("1.1.5-41", "1.1.5-42"))
    }

    @Test
    fun `plain release version comparison is unchanged`() {
        assertTrue(UpdateManager.isNewer("1.2.0", "1.1.5"))
        assertFalse(UpdateManager.isNewer("1.1.5", "1.2.0"))
        assertTrue(UpdateManager.isNewer("1.11.0", "1.9.0"))
        assertFalse(UpdateManager.isNewer("1.1.5", "1.1.5"))
    }

    @Test
    fun `parseVersion encodes the pre-release tag as a low-order tiebreaker`() {
        // Not the raw list, but the ordering it produces must satisfy:
        //   parse("1.2.0") > parse("1.2.0-rc1")
        // Both sides share the release prefix [1,2,0] and diverge only on the tag suffix, so
        // this pins that the suffix is appended rather than the tag being discarded (which is
        // what made "1.2.0-rc1" collapse to exactly [1,2,0]).
        val release = UpdateManager.parseVersion("1.2.0")
        val rc1 = UpdateManager.parseVersion("1.2.0-rc1")

        assertEquals(listOf(1, 2, 0), release.subList(0, 3))
        assertEquals("the release number must survive the pre-release suffix", release.subList(0, 3), rc1.subList(0, 3))
        assertTrue("a final release must parse above its pre-releases", compare(release, rc1) > 0)
    }

    /** Lexicographic comparison, padding the shorter list with zeros — mirrors isNewer. */
    private fun compare(
        a: List<Int>,
        b: List<Int>,
    ): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}
