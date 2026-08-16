package com.wuwaconfig.app.backend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellUtilsTest {
    @Test
    fun `shQuote wraps simple value in single quotes`() {
        assertEquals("'hello'", shQuote("hello"))
    }

    @Test
    fun `shQuote escapes embedded single quotes posix style`() {
        assertEquals("'a'\"'\"'b'", shQuote("a'b"))
    }

    @Test
    fun `shQuote null yields empty quoted string`() {
        assertEquals("''", shQuote(null))
    }

    @Test
    fun `shQuote prevents unquoted command injection`() {
        val value = "foo'; rm -rf / #"
        val quoted = shQuote(value)
        assertTrue(quoted.startsWith("'"))
        assertTrue(quoted.endsWith("'"))
        assertTrue(quoted.contains("'\"'\"'"))
    }

    @Test
    fun `computeMd5 matches known vectors`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", computeMd5("".toByteArray()))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", computeMd5("abc".toByteArray()))
    }

    @Test
    fun `maxPushChunkSize stays within arg limits and floor`() {
        val size = maxPushChunkSize("/data/local/tmp/x.ini")
        assertTrue(size in 256..MAX_ARG_STRLEN)
        val huge = maxPushChunkSize("a".repeat(MAX_ARG_STRLEN))
        assertEquals(256, huge)
    }

    @Test
    fun `retryIO returns on first success`() {
        runBlocking {
            var calls = 0
            val result =
                retryIO(times = 3) {
                    calls++
                    "ok"
                }
            assertEquals(true, result.isSuccess)
            assertEquals("ok", result.getOrThrow())
            assertEquals(1, calls)
        }
    }

    @Test
    fun `retryIO retries then succeeds`() {
        runBlocking {
            var calls = 0
            val result =
                retryIO(times = 3, backoffMs = 1) {
                    calls++
                    if (calls < 3) throw RuntimeException("transient")
                    "ok"
                }
            assertEquals(true, result.isSuccess)
            assertEquals("ok", result.getOrThrow())
            assertEquals(3, calls)
        }
    }

    @Test
    fun `retryIO fails after exhausting attempts`() {
        runBlocking {
            var calls = 0
            val result =
                retryIO(times = 2, backoffMs = 1) {
                    calls++
                    throw RuntimeException("always")
                }
            assertEquals(true, result.isFailure)
            assertEquals(2, calls)
        }
    }
}
