package com.wuwaconfig.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
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
}
