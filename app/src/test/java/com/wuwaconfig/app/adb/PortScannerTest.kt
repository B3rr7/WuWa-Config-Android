package com.wuwaconfig.app.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortScannerTest {
    private fun message(
        command: ByteArray,
        arg0: Int,
        payload: ByteArray = ByteArray(0),
    ) = AdbProtocol.AdbMessage(command = command, arg0 = arg0, arg1 = 0, payload = payload)

    private val tokenChallenge =
        message(AdbProtocol.AUTH, AdbProtocol.AUTH_TOKEN, ByteArray(20) { it.toByte() })

    @Test
    fun `a genuine AUTH token challenge is accepted`() {
        assertTrue(PortScanner.isAuthChallenge(tokenChallenge))
    }

    @Test
    fun `a bare CNXN is rejected`() {
        // This is the spoofing case. Any TCP listener on the LAN can answer with a
        // 32-byte CNXN frame, and the previous check accepted exactly that — after
        // which the app SENT ITS RSA PUBLIC KEY to the unknown peer. A real adbd
        // never answers a connect with CNXN; it always issues AUTH + a 20-byte token.
        assertFalse(PortScanner.isAuthChallenge(message(AdbProtocol.CNXN, 0, ByteArray(0))))
    }

    @Test
    fun `other commands are rejected`() {
        for (command in listOf(AdbProtocol.OPEN, AdbProtocol.OKAY, AdbProtocol.CLSE, AdbProtocol.WRTE, AdbProtocol.STLS)) {
            assertFalse(
                "command ${AdbProtocol.hex(command)} must not be treated as an auth challenge",
                PortScanner.isAuthChallenge(message(command, AdbProtocol.AUTH_TOKEN, ByteArray(20))),
            )
        }
    }

    @Test
    fun `AUTH with the wrong token type is rejected`() {
        // arg0 must be AUTH_TOKEN specifically, not merely "some AUTH".
        assertFalse(
            PortScanner.isAuthChallenge(
                message(AdbProtocol.AUTH, AdbProtocol.AUTH_RSA_PUBLIC, ByteArray(20) { it.toByte() }),
            ),
        )
    }

    @Test
    fun `AUTH with a wrong sized token payload is rejected`() {
        // A real adbd always sends exactly 20 bytes. Anything else is not adbd.
        for (size in listOf(0, 1, 16, 19, 21, 32, 64)) {
            assertFalse(
                "a $size-byte token payload must be rejected",
                PortScanner.isAuthChallenge(
                    message(AdbProtocol.AUTH, AdbProtocol.AUTH_TOKEN, ByteArray(size) { it.toByte() }),
                ),
            )
        }
    }

    @Test
    fun `no response is rejected`() {
        assertFalse(PortScanner.isAuthChallenge(null))
    }

    // describeBanner decides whether an unauthenticated CNXN is worth telling the
    // user about. Getting this wrong is user-visible in both directions: a missed
    // device means "ADB port not found" again, and a false one sends the user to a
    // port that is not a phone.
    private val motoBanner =
        (
            "device::ro.product.name=hanoip_retail;ro.product.model=moto g(60);" +
                "ro.product.device=hanoip;features=shell_v2,cmd"
        ).toByteArray()

    @Test
    fun `a real device banner is named by its model`() {
        assertEquals("device moto g(60)", PortScanner.describeBanner(motoBanner))
    }

    @Test
    fun `a device banner without a model is still a device`() {
        assertEquals(
            "an unnamed device",
            PortScanner.describeBanner("device::ro.product.name=x;features=cmd".toByteArray()),
        )
    }

    @Test
    fun `a host banner is not evidence of a device`() {
        // Observed on-device: a transient listener inside the 37000..44000 sweep
        // answered with this and would otherwise have been reported as the phone.
        assertNull(PortScanner.describeBanner("host::".toByteArray()))
    }

    @Test
    fun `an empty or unrelated banner is not evidence of a device`() {
        assertNull(PortScanner.describeBanner(ByteArray(0)))
        assertNull(PortScanner.describeBanner("nonsense".toByteArray()))
    }

    @Test
    fun `loopback is the only address an unauthenticated banner may be trusted on`() {
        // The property the whole Local ADB path rests on: an unauthenticated
        // device:: banner is acceptable over loopback because no LAN peer can
        // bind that address, and unacceptable anywhere else.
        assertEquals("127.0.0.1", PortScanner.LOOPBACK)
        assertNotEquals(PortScanner.LOOPBACK, "10.237.87.112")
    }
}
