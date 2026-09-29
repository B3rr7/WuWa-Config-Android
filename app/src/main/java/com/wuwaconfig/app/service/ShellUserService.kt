package com.wuwaconfig.app.service

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.util.concurrent.TimeUnit

class ShellUserService : Binder() {
    companion object {
        /**
         * Process name suffix of the UserService process, i.e. the final process
         * name is `com.wuwaconfig.app:shell`. Single source of truth: the value is
         * passed to `Shizuku.UserServiceArgs.processNameSuffix()` by ShizukuBackend
         * AND used by WuWaConfigApp to recognise that it is being constructed inside
         * that process (see WuWaConfigApp.isUserServiceProcess). If the two ever
         * drifted, the app would run its full bootstrap in a uid-2000 process and
         * crash it on startup — which is indistinguishable, from the client, from
         * a bind timeout.
         */
        const val PROCESS_NAME_SUFFIX = "shell"

        private const val TRANSACTION_EXEC_COMMAND = IBinder.FIRST_CALL_TRANSACTION + 1

        /**
         * A process has ONE binder buffer of roughly 1 MB shared by the whole transaction
         * (command + reply), so a 900 KB reply left almost no headroom and pushed
         * `writeString` into TransactionTooLargeException. 512 KB keeps a wide margin; larger
         * output must go through a temp file (readViaTemp) instead.
         */
        private const val MAX_BINDER_OUTPUT = 512 * 1024
    }

    init {
        attachInterface(null, "com.wuwaconfig.app.IShellService")
    }

    override fun onTransact(
        code: Int,
        data: Parcel,
        reply: Parcel?,
        flags: Int,
    ): Boolean {
        return when (code) {
            TRANSACTION_EXEC_COMMAND -> {
                data.enforceInterface("com.wuwaconfig.app.IShellService")
                val command = data.readString() ?: ""
                // Never let a failure leave the reply parcel unwritten: the client is blocked
                // in transact() and a TransactionTooLargeException / DeadObjectException here
                // would surface as an unexplained "service not connected".
                val result =
                    try {
                        execCommand(command)
                    } catch (e: Throwable) {
                        Log.e("ShellUserService", "execCommand failed for: ${command.take(80)}", e)
                        "ERROR: ${e.message ?: e.javaClass.simpleName}"
                    }
                try {
                    reply?.writeNoException()
                    reply?.writeString(result)
                } catch (e: Throwable) {
                    Log.e("ShellUserService", "reply write failed (${result.length} chars)", e)
                }
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    fun execCommand(command: String): String {
        return try {
            val process =
                ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    // stdin is inherited by default and nobody ever closes it, so a command
                    // that reads stdin (`cat` with no args) would block until the watchdog
                    // killed it. INHERIT hands over the UserService's own stdin, which is
                    // already at EOF in a Binder-spawned process.
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .start()
            // Watchdog destroys the process at the deadline even if it produces no
            // output (a plain read would block forever on a silent hang).
            val watchdog =
                Thread {
                    try {
                        if (!process.waitFor(60, TimeUnit.SECONDS)) process.destroyForcibly()
                    } catch (_: InterruptedException) {
                    }
                }
            watchdog.isDaemon = true
            watchdog.start()
            val output = readBounded(process.inputStream)
            val exited = process.waitFor(5, TimeUnit.SECONDS)
            watchdog.interrupt()
            if (!exited) {
                process.destroyForcibly()
                "Command timed out (process did not exit after output drained)"
            } else {
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    "SHIZUKU_EXIT=$exitCode\n${output.trim().ifEmpty { "Command failed (exit $exitCode)" }}"
                } else {
                    output
                }
            }
        } catch (e: Exception) {
            Log.e("ShellUserService", "execCommand failed", e)
            e.message ?: "execCommand failed"
        }
    }

    /**
     * Drains the stream with a hard cap so oversized output cannot blow past the binder
     * transaction buffer in [onTransact]'s writeString.
     * If the output is truncated, the returned string is prefixed with
     * SHIZUKU_TRUNCATED so callers can detect it instead of silently
     * receiving partial data (e.g. a truncated Client.log missing CVars).
     */
    private fun readBounded(stream: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream(MAX_BINDER_OUTPUT)
        val buf = ByteArray(8192)
        var total = 0
        while (total < MAX_BINDER_OUTPUT) {
            val toRead = minOf(buf.size, MAX_BINDER_OUTPUT - total)
            val n = stream.read(buf, 0, toRead)
            if (n < 0) break
            out.write(buf, 0, n)
            total += n
        }
        // Probe with available(), never read(): a blocking read here waits out the full 60s
        // watchdog for a command that produced >= MAX_BINDER_OUTPUT and is still alive, and
        // then discards the output in favour of the timeout message — which made the
        // SHIZUKU_TRUNCATED prefix unreachable exactly on the path that needs it.
        val truncated = total >= MAX_BINDER_OUTPUT && runCatching { stream.available() > 0 }.getOrDefault(false)
        val text = out.toString("UTF-8")
        return if (truncated) "SHIZUKU_TRUNCATED\n$text" else text
    }
}
