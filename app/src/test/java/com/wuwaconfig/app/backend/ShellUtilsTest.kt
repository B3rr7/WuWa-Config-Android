package com.wuwaconfig.app.backend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellUtilsTest {
    // ── shQuote ──

    @Test
    fun `shQuote wraps plain values in single quotes`() {
        assertEquals("'hello'", shQuote("hello"))
    }

    @Test
    fun `shQuote escapes embedded single quotes`() {
        // Single-quoted shell: a literal ' is inserted as '"'"'.
        assertEquals("'it'\"'\"'s here'", shQuote("it's here"))
    }

    @Test
    fun `shQuote handles null as empty string`() {
        assertEquals("''", shQuote(null))
    }

    @Test
    fun `shQuote never produces an unquoted token`() {
        // Even hostile input stays inside the quotes, so it can't break out of printf '%s'.
        val out = shQuote("foo; rm -rf /")
        assertTrue(out.startsWith("'"))
        assertTrue(out.endsWith("'"))
        // The hostile content is preserved verbatim between the quotes (it is not
        // stripped — the point is it is inert inside single quotes).
        assertEquals("foo; rm -rf /", out.substring(1, out.length - 1))
    }

    @Test
    fun `shQuote rejects a value containing a NUL byte`() {
        // A NUL cannot survive execve: it silently truncates the argument at the syscall
        // boundary, so "a\0b" reaches the shell as "a" and the rest of the command runs
        // with a different (or missing) argument. Failing loudly is the only safe option —
        // the caller can then reject the path instead of shipping a truncated command.
        var thrown: Throwable? = null
        try {
            shQuote("a\u0000b")
        } catch (e: Throwable) {
            thrown = e
        }
        assertNotNull("shQuote must reject a NUL byte instead of truncating silently", thrown)
        assertTrue(
            "the failure should say why, got: $thrown",
            thrown!!.message?.contains("NUL") == true,
        )
    }

    @Test
    fun `shQuote rejects a NUL byte anywhere in the value`() {
        for (hostile in listOf("\u0000", "trailing\u0000", "\u0000leading", "a\u0000b\u0000c")) {
            var thrown: Throwable? = null
            try {
                shQuote(hostile)
            } catch (e: Throwable) {
                thrown = e
            }
            assertNotNull("shQuote must reject NUL in '$hostile'", thrown)
        }
    }

    @Test
    fun `shQuote still accepts ordinary values including spaces and quotes`() {
        assertEquals("'a b'", shQuote("a b"))
        assertEquals("''", shQuote(""))
        // A single quote inside a single-quoted shell word must close the quote, emit an
        // escaped quote, and reopen: 'it'"'"'s'. The naive "'it's'" is NOT valid shell —
        // it terminates the word at "it" and then parses `s` as a new token.
        assertEquals("""'it'"'"'s'""", shQuote("it's"))
        // A tab is a legal, non-truncating shell character and must not be rejected.
        assertEquals("'a\tb'", shQuote("a\tb"))
    }

    // ── buildPushFilePlan ──

    @Test
    fun `buildPushFilePlan fits a small payload in one command`() {
        val plan = buildPushFilePlan("aGVsbG8=", "/data/data/x/Engine.ini", "/tmp/t")
        assertTrue(plan.fitsSingleCommand)
        assertEquals(1, plan.writes.size)
    }

    @Test
    fun `buildPushFilePlan chunks payloads that exceed MAX_ARG_STRLEN`() {
        val big = "A".repeat(MAX_ARG_STRLEN * 3)
        val plan = buildPushFilePlan(big, "/data/data/x/Engine.ini", "/tmp/t")
        assertFalse(plan.fitsSingleCommand)
        // Each chunk must be individually safe to pass as a single shell arg.
        for (w in plan.writes) {
            assertTrue(w.length <= MAX_ARG_STRLEN)
        }
        // The chunk count must cover the payload: ceil(payload / chunkSize).
        val chunkSize = maxPushChunkSize("/tmp/t")
        val expectedChunks = (big.length + chunkSize - 1) / chunkSize
        assertEquals(expectedChunks, plan.writes.size)
    }

    @Test
    fun `buildPushFilePlan chunk size never drops below the floor`() {
        // The floor (256) bounds the *computed* chunk size, not the last actual
        // chunk — a payload that isn't an exact multiple leaves a small tail.
        assertTrue(maxPushChunkSize("/tmp/t") >= 256)
        val encoded = "A".repeat(MAX_ARG_STRLEN * 2)
        val plan = buildPushFilePlan(encoded, "/data/data/x/Engine.ini", "/tmp/t")
        for (w in plan.writes) {
            // Each write command fits within MAX_ARG_STRLEN.
            assertTrue(w.length <= MAX_ARG_STRLEN)
        }
        // The first chunk's payload equals the computed chunk size.
        val firstPayload = plan.writes.first().substringAfter("printf '%s' ").removePrefix("'").substringBefore("'")
        assertEquals(maxPushChunkSize("/tmp/t"), firstPayload.length)
    }

    @Test
    fun `buildPushFilePlan decode and verify are present`() {
        val plan = buildPushFilePlan("aGVsbG8=", "/data/data/x/Engine.ini", "/tmp/t")
        assertTrue(plan.decode.contains("base64 -d"))
        assertTrue(plan.decode.contains("/tmp/t"))
        assertTrue(plan.verify.contains("md5sum"))
    }

    // ── retryIO ──

    @Test
    fun `retryIO succeeds on first attempt`() =
        runBlocking {
            var calls = 0
            val r =
                retryIO {
                    calls++
                    "ok"
                }
            assertTrue(r.isSuccess)
            assertEquals("ok", r.getOrThrow())
            assertEquals(1, calls)
        }

    @Test
    fun `retryIO retries then succeeds`() =
        runBlocking {
            var calls = 0
            val r =
                retryIO(times = 3, backoffMs = 1) {
                    calls++
                    if (calls < 2) throw Exception("transient")
                    "recovered"
                }
            assertTrue(r.isSuccess)
            assertEquals(2, calls)
        }

    @Test
    fun `retryIO fails after exhausting attempts`() =
        runBlocking {
            var calls = 0
            val r =
                retryIO(times = 2, backoffMs = 1) {
                    calls++
                    throw Exception("always")
                }
            assertFalse(r.isSuccess)
            assertEquals(2, calls)
        }

    @Test
    fun `retryIO honors shouldRetry`() =
        runBlocking {
            var calls = 0
            val r =
                retryIO(times = 5, backoffMs = 1, shouldRetry = { false }) {
                    calls++
                    throw Exception("fatal")
                }
            assertFalse(r.isSuccess)
            assertEquals(1, calls)
        }
}
