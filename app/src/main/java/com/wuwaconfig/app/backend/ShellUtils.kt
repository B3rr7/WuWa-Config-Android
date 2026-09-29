package com.wuwaconfig.app.backend

import kotlinx.coroutines.delay
import java.io.File
import java.security.MessageDigest

fun shQuote(value: String?): String {
    // A NUL byte cannot survive execve: it silently truncates the argument, so a
    // crafted path could smuggle a second argument past the caller. Make it explicit.
    require(value?.contains('\u0000') != true) {
        "shell argument contains a NUL byte, which truncates it at the execve boundary"
    }
    return "'${value?.replace("'", "'\"'\"'") ?: ""}'"
}

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
private const val MIN_PUSH_CHUNK = 256

fun maxPushChunkSize(encodedPath: String): Int {
    val pathQuoted = shQuote(encodedPath)
    val overhead = PRINTF_PREFIX.length + CHUNK_QUOTE + REDIRECT.length + pathQuoted.length
    val available = MAX_ARG_STRLEN - overhead
    // Flooring to MIN_PUSH_CHUNK would emit `write` lines longer than the arg limit we
    // are chunking for in the first place; fail loudly instead of corrupting the push.
    require(available >= MIN_PUSH_CHUNK) {
        "push staging path is too long ($overhead chars of overhead): cannot keep each " +
            "argument under $MAX_ARG_STRLEN chars"
    }
    return minOf(available, MAX_ARG_STRLEN)
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

/**
 * Wraps [command] so the WHOLE thing runs under the app's uid via `run-as <pkg>`.
 *
 * The inner `sh -c` plus [shQuote] is load-bearing: an ungrouped
 * `run-as <pkg> base64 -d A > B && rm -f A` would leave the redirect and the `&&`
 * chain to the *outer* shell (uid 2000) — the exact identity that just failed —
 * so only the first token would actually run as the app.
 */
fun runAsCommand(
    pkg: String,
    command: String,
): String = "run-as ${shQuote(pkg)} sh -c ${shQuote(command)}"

private val PERMISSION_DENIED_MARKERS = listOf("permission denied", "operation not permitted")

/** True when a shell failure message indicates an fs-permission problem worth retrying as the app. */
fun isPermissionDenied(message: String?): Boolean {
    val msg = message?.lowercase() ?: return false
    return PERMISSION_DENIED_MARKERS.any { msg.contains(it) }
}

/** True when `run-as` itself refused because the target package is not debuggable. */
fun isNotDebuggable(message: String?): Boolean = message?.contains("not debuggable", ignoreCase = true) == true

/**
 * True when the failure is "there is no shell service bound", rather than a
 * command that ran and failed.
 *
 * This is not a retryable condition: the caller has no UserService to send
 * anything to, so every subsequent attempt fails identically. Retry loops that
 * cannot tell the two apart burn their whole backoff budget on a fault that
 * only a reconnect can clear.
 */
fun isServiceNotConnected(message: String?): Boolean =
    message?.contains("service not connected", ignoreCase = true) == true ||
        // Exact literal thrown by Shizuku.requireService() (dev.rikka.shizuku:api
        // 13.1.5) when bindUserService is called before the binder arrives.
        message?.contains("binder haven't been received", ignoreCase = true) == true

/**
 * Single shared "retry on Permission denied" wrapper used by AdbBackend, AdbClient and
 * ShizukuBackend.
 *
 * Contract: [exec] runs the given command and returns `Result.failure` when the remote
 * command exits nonzero, with the failing command's stdout/stderr in the message. That
 * message is the only signal this helper has, so a backend that swallows exit codes
 * (or discards stderr) silently degrades this retry into a no-op.
 *
 * On a permission failure the command is retried exactly once, grouped through
 * [runAsCommand]. The retry result is returned as-is — including a `not debuggable`
 * failure, so the caller can surface "use SAF or Root" guidance.
 */
suspend fun <T> withRunAsFallback(
    command: String,
    pkg: String,
    exec: suspend (String) -> Result<T>,
): Result<T> {
    val first = exec(command)
    if (first.isSuccess || !isPermissionDenied(first.exceptionOrNull()?.message)) {
        return first
    }
    return exec(runAsCommand(pkg, command))
}

private val MUTATING_VERB_RE =
    Regex("""(^|\s)(rm|mv|cp|dd|chmod|chown|mkdir|rmdir|touch|ln|tee|truncate|shred|install|sed|ed)(\s|$)""")

// `>` / `>>` that are NOT file-descriptor redirects (`2>/dev/null`, `>&2`).
private val SHELL_REDIRECT_RE = Regex("""(?<![0-9])>{1,2}""")

/**
 * Conservative classifier for "may this command be replayed after a transport failure?".
 *
 * Anything that creates, removes, renames, chmods or appends is treated as mutating, as is
 * anything with a shell output redirect (`cmd > file`, `cmd >> file`). Returning `false` for
 * a read-only command only costs one fewer retry; returning `true` for a mutating one
 * silently duplicates side effects (double `mv`, duplicated appended chunk), so the bias is
 * deliberate.
 */
fun isReadOnlyShellCommand(command: String): Boolean {
    if (MUTATING_VERB_RE.containsMatchIn(command)) return false
    if (SHELL_REDIRECT_RE.containsMatchIn(command)) return false
    return true
}

suspend fun <T> retryIO(
    times: Int = PUSH_RETRY_COUNT + 1,
    backoffMs: Long = 500L,
    /**
     * When false the block runs at most once: [block] is allowed to mutate remote state
     * (HashMonitor's `rm`/`mv`, ProfileExtractor's `printf >> tmp`), so replaying it after
     * a `DeadObject` would apply a half-executed command twice. Classify with
     * [isReadOnlyShellCommand].
     */
    idempotent: Boolean = true,
    shouldRetry: (Exception) -> Boolean = { true },
    block: suspend () -> T,
): Result<T> {
    val maxAttempts = if (idempotent) times else 1
    var lastError: Exception? = null
    for (attempt in 0 until maxAttempts) {
        if (attempt > 0) {
            delay(backoffMs * attempt)
        }
        try {
            return Result.success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = e
            if (!shouldRetry(e) || attempt == maxAttempts - 1) {
                break
            }
        }
    }
    // Only reachable when maxAttempts < 1; every normal loop exit leaves lastError set.
    return Result.failure(lastError ?: IllegalStateException("retryIO called with times=$times"))
}
