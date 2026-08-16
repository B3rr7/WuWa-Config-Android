package com.wuwaconfig.app.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PushFilePlanTest {
    private val tmpPath = "/data/local/tmp/wb64_test.b64"
    private val targetPath = "/sdcard/Android/data/com.kurogame.wutheringwaves.global/files/Game.ini"

    private fun extractChunk(cmd: String): String {
        val m = Regex("""printf '%s' '([^']*)' (>>|>)""").find(cmd)!!
        return m.groupValues[1]
    }

    private fun reconstruct(plan: PushFilePlan): String = plan.writes.joinToString("") { extractChunk(it) }

    @Test
    fun `single small file fits one command and round-trips`() {
        val bytes = "r.kuro.autoexposure=0.5\n[Section]\nKey=Value\n".toByteArray()
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val plan = buildPushFilePlan(encoded, targetPath, tmpPath)

        assertTrue(plan.fitsSingleCommand)
        assertEquals(encoded, reconstruct(plan))
        assertEquals(bytes.toList(), Base64.getDecoder().decode(reconstruct(plan)).toList())
        assertEquals(1, plan.writes.size)
        assertTrue(plan.writes[0].contains(" > "))
    }

    @Test
    fun `large file exceeds single command and every chunk is under the arg limit`() {
        val bytes = ByteArray(300 * 1024) { (it * 31 % 256).toByte() }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val plan = buildPushFilePlan(encoded, targetPath, tmpPath)

        assertFalse(plan.fitsSingleCommand)
        assertEquals(encoded, reconstruct(plan))
        assertEquals(bytes.toList(), Base64.getDecoder().decode(reconstruct(plan)).toList())

        plan.writes.forEachIndexed { i, w ->
            assertTrue(w.length <= MAX_ARG_STRLEN)
            val redir = if (i == 0) " > " else " >> "
            assertTrue(w.contains(redir))
        }
    }

    @Test
    fun `plan always includes setup decode and verify commands`() {
        val bytes = "config content".toByteArray()
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val plan = buildPushFilePlan(encoded, targetPath, tmpPath)

        assertTrue(plan.setup.contains("mkdir -p"))
        assertTrue(plan.decode.startsWith("base64 -d"))
        assertTrue(plan.decode.contains("rm -f"))
        assertTrue(plan.verify.contains("md5sum"))
        assertEquals(
            listOf(plan.setup) + plan.writes + listOf(plan.decode, plan.verify),
            plan.commands,
        )
    }

    @Test
    fun `chunk size respects the arg limit with no truncation`() {
        val bytes = ByteArray(50 * 1024) { it.toByte() }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val plan = buildPushFilePlan(encoded, targetPath, tmpPath)
        val chunkSize = maxPushChunkSize(tmpPath)
        plan.writes.forEach { w ->
            val chunk = extractChunk(w)
            assertTrue(chunk.length <= chunkSize)
        }
        assertEquals(encoded, reconstruct(plan))
    }
}
