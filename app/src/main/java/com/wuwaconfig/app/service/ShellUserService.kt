package com.wuwaconfig.app.service

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
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
            val process = ProcessBuilder("sh", "-c", command).start()
            val stdout = readStream(process.inputStream)
            val stderr = readStream(process.errorStream)
            val exited = process.waitFor(60, TimeUnit.SECONDS)
            if (!exited) {
                process.destroyForcibly()
                "Command timed out after 60s"
            } else {
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    stderr.trim().ifEmpty { "Command failed (exit $exitCode)" }
                } else {
                    stdout
                }
            }
        } catch (e: Exception) {
            Log.e("ShellUserService", "execCommand failed", e)
            e.message ?: "execCommand failed"
        }
    }

    private fun readStream(stream: java.io.InputStream): String {
        return BufferedReader(InputStreamReader(stream)).readText()
    }
}
