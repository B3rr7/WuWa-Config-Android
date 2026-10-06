package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [KuroClient.encodePassword] against known vectors computed from Kuro's own
 * `kr-sdk.js` (`btoa` + two seeded shuffles). Getting this wrong is the difference between a
 * rejected plaintext password and a successful login, so it must not drift.
 */
class KuroClientPasswordTest {
    @Test
    fun `the password transform matches Kuro's sdk for known vectors`() {
        assertEquals("VzdGEydDQ=Mz", KuroClient.encodePassword("test1234"))
        assertEquals("FzcGdvc3Qxcm", KuroClient.encodePassword("password1"))
        assertEquals("JjYWVmZGg=Z2", KuroClient.encodePassword("abcdefgh"))
        assertEquals("BzUEcwc3Qhcm", KuroClient.encodePassword("P@ssw0rd!"))
    }

    @Test
    fun `the transform is deterministic`() {
        assertEquals(KuroClient.encodePassword("hunter2"), KuroClient.encodePassword("hunter2"))
    }
}
