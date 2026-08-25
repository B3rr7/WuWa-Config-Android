package com.wuwaconfig.app.service

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.util.concurrent.TimeUnit

class ShellUserService : Binder() {
    companion object {
        private const val TRANSACTION_DESTROY = IBinder.FIRST_CALL_TRANSACTION
        private const val TRANSACTION_EXEC_COMMAND = IBinder.FIRST_CALL_TRANSACTION + 1
        private const val MAX_BINDER_OUTPUT = 900 * 1024
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
            TRANSACTION_DESTROY -> {
                data.enforceInterface("com.wuwaconfig.app.IShellService")
                destroy()
                reply?.writeNoException()
                true
            }
            TRANSACTION_EXEC_COMMAND -> {
                data.enforceInterface("com.wuwaconfig.app.IShellService")
                val command = data.readString() ?: ""
                val result = execCommand(command)
                reply?.writeNoException()
                reply?.writeString(result)
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    fun destroy() {
        System.exit(0)
    }

    fun execCommand(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
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
                "Command timed out after 60s"
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
     * Drains the stream with a hard cap so oversized output cannot blow past the
     * ~1 MB binder transaction buffer in [onTransact]'s writeString.
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
        return out.toString("UTF-8")
    }
}
