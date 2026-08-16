package com.wuwaconfig.app.backend

import kotlinx.coroutines.delay
import java.io.File
import java.security.MessageDigest

fun shQuote(value: String?): String = "'${value?.replace("'", "'\"'\"'") ?: ""}'"

fun computeMd5(file: File): String = computeMd5(file.readBytes())

fun computeMd5(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("MD5").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}

const val PUSH_RETRY_COUNT = 2

const val MAX_ARG_STRLEN = 4096
private const val PRINTF_PREFIX = "printf '%s' " // 12 chars
private const val CHUNK_QUOTE = 2 // single quotes wrapping the chunk payload
private const val REDIRECT = " >> " // 4 chars, longest of " > "/" >> "

fun maxPushChunkSize(encodedPath: String): Int {
    val pathQuoted = shQuote(encodedPath)
    val overhead = PRINTF_PREFIX.length + CHUNK_QUOTE + REDIRECT.length + pathQuoted.length
    return (MAX_ARG_STRLEN - overhead).coerceIn(256, MAX_ARG_STRLEN)
}

data class PushFilePlan(
    val setup: String,
    val writes: List<String>,
    val decode: String,
    val verify: String,
) {
    val commands: List<String> get() = listOf(setup) + writes + listOf(decode, verify)
    val joinedCommand: String get() = commands.joinToString(" && ")
    val fitsSingleCommand: Boolean get() = joinedCommand.length <= MAX_ARG_STRLEN
}

fun buildPushFilePlan(
    encoded: String,
    targetPath: String,
    tmpPath: String,
): PushFilePlan {
    val parent = File(targetPath).parent ?: throw IllegalArgumentException("Invalid target path")
    val target = shQuote(targetPath)
    val tq = shQuote(tmpPath)
    val setup = "rm -f $tq && mkdir -p ${shQuote(parent)}"
    val chunkSize = maxPushChunkSize(tmpPath)
    val chunks = encoded.chunked(chunkSize)
    val writes =
        chunks.mapIndexed { i, chunk ->
            val redir = if (i == 0) ">" else ">>"
            "printf '%s' ${shQuote(chunk)} $redir $tq"
        }
    val decode = "base64 -d $tq > $target && rm -f $tq"
    val verify = "md5sum $target 2>/dev/null | cut -d' ' -f1"
    return PushFilePlan(setup, writes, decode, verify)
}

suspend fun <T> retryIO(
    times: Int = PUSH_RETRY_COUNT + 1,
    backoffMs: Long = 500L,
    shouldRetry: (Exception) -> Boolean = { true },
    block: suspend () -> T,
): Result<T> {
    var lastError: Exception? = null
    for (attempt in 0 until times) {
        if (attempt > 0) {
            delay(backoffMs * attempt)
        }
        try {
            return Result.success(block())
        } catch (e: Exception) {
            lastError = e
            if (!shouldRetry(e) || attempt == times - 1) {
                break
            }
        }
    }
    return Result.failure(lastError ?: Exception("Operation failed after $times attempts"))
}
