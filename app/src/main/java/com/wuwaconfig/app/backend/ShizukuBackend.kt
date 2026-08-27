package com.wuwaconfig.app.backend

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.util.Base64
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.service.ShellUserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShizukuBackend(private val context: android.content.Context) : AccessBackend {
    // Written from the main thread (onServiceConnected) and read from IO workers.
    @Volatile
    private var shellService: IShellService? = null

    @Volatile
    private var serviceConnection: ServiceConnection? = null

    interface IShellService {
        fun execCommand(command: String): String
    }

    private class ShellServiceProxy(private val binder: IBinder) : IShellService {
        companion object {
            private const val TRANSACTION_EXEC_COMMAND = IBinder.FIRST_CALL_TRANSACTION + 1
        }

        override fun execCommand(command: String): String {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken("com.wuwaconfig.app.IShellService")
                data.writeString(command)
                if (!binder.transact(TRANSACTION_EXEC_COMMAND, data, reply, 0)) {
                    throw Exception("Remote call failed")
                }
                reply.readException()
                return reply.readString() ?: ""
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }

    override val isConnected: Boolean
        get() =
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED && shellService != null
            } catch (_: Exception) {
                false
            }

    override suspend fun connect(): Result<Unit> =
        withContext(Dispatchers.IO) {
            LogRepository.add("Shizuku connect: checking...")
            try {
                val version = Shizuku.getVersion()
                if (version < 0) {
                    LogRepository.add("Shizuku not running", LogLevel.ERROR)
                    return@withContext Result.failure(Exception("Shizuku is not running. Start Shizuku first."))
                }
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    LogRepository.add("Shizuku permission not granted", LogLevel.ERROR)
                    return@withContext Result.failure(Exception("Shizuku permission not granted."))
                }
                if (version < 10) {
                    LogRepository.add("Shizuku API < 10, cannot use UserService", LogLevel.ERROR)
                    return@withContext Result.failure(Exception("Shizuku API version too old. Need v10+."))
                }
                bindUserService()
                LogRepository.add("Shizuku connected successfully", LogLevel.SUCCESS)
                Result.success(Unit)
            } catch (e: Exception) {
                disconnect()
                LogRepository.add("Shizuku connect failed: ${e.message}", LogLevel.ERROR)
                Result.failure(Exception("Shizuku connect failed: ${e.message ?: "unknown error"}"))
            }
        }

    private fun bindUserService() {
        val latch = CountDownLatch(1)
        val args =
            Shizuku.UserServiceArgs(
                ComponentName(
                    "com.wuwaconfig.app",
                    ShellUserService::class.java.name,
                ),
            )
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(false)
                .version(1)

        serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    binder: IBinder?,
                ) {
                    if (binder != null && binder.pingBinder()) {
                        shellService = ShellServiceProxy(binder)
                    }
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    shellService = null
                    latch.countDown()
                }
            }

        Shizuku.bindUserService(args, serviceConnection!!)
        if (!latch.await(15, TimeUnit.SECONDS)) {
            // Unbind so a late onServiceConnected doesn't leave a bound UserService
            // with no tracked cleanup.
            try {
                Shizuku.unbindUserService(args, serviceConnection!!, true)
            } catch (_: Exception) {
            }
            serviceConnection = null
            shellService = null
            throw Exception("UserService bind timed out")
        }
        if (shellService == null) {
            throw Exception("Failed to bind UserService")
        }
    }

    override fun disconnect() {
        LogRepository.add("Shizuku disconnect")
        serviceConnection?.let {
            try {
                val args =
                    Shizuku.UserServiceArgs(
                        ComponentName(
                            "com.wuwaconfig.app",
                            ShellUserService::class.java.name,
                        ),
                    )
                        .daemon(false)
                        .processNameSuffix("shell")
                        .debuggable(false)
                        .version(1)
                Shizuku.unbindUserService(args, it, true)
            } catch (_: Exception) {
            }
        }
        shellService = null
        serviceConnection = null
    }

    override suspend fun executeShellCommand(command: String): Result<String> =
        withContext(Dispatchers.IO) {
            LogRepository.add("Shizuku shell: ${command.take(120)}")
            val svc = shellService
            if (svc == null) {
                LogRepository.add("Shizuku service not connected", LogLevel.ERROR)
                return@withContext Result.failure(Exception("Shizuku service not connected"))
            }
            val result =
                retryIO(times = 3, backoffMs = 500L, shouldRetry = { e ->
                    val msg = e.message?.lowercase() ?: ""
                    msg.contains("service not connected") ||
                        msg.contains("remote call failed") ||
                        msg.contains("deadobject") ||
                        msg.contains("broken pipe")
                }) {
                    parseServiceResult(
                        withTimeout(SHIZUKU_CALL_TIMEOUT_MS) { svc.execCommand(command) },
                    ).trim()
                }
            if (result.isFailure) {
                LogRepository.add("Shizuku shell exhausted: ${result.exceptionOrNull()?.message}", LogLevel.ERROR)
            }
            result
        }

    override suspend fun pushFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            LogRepository.add("Shizuku push: $sourcePath -> $targetPath")
            val sourceFile = File(sourcePath)
            val bytes = sourceFile.readBytes()
            val localMd5 = computeMd5(bytes)
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            if (File(targetPath).parent == null) {
                return@withContext Result.failure(Exception("Invalid target path"))
            }

            suspend fun doPush(): Result<String> {
                val tmpB64 = "/data/local/tmp/wb64_${System.currentTimeMillis()}_${(0..9999).random()}"
                val target = shQuote(targetPath)
                val plan = buildPushFilePlan(encoded, targetPath, tmpB64)

                val result: String
                if (plan.fitsSingleCommand) {
                    result = execOrThrow(plan.joinedCommand)
                } else {
                    // Payload exceeds a single shell argument limit. Each write line is
                    // already < MAX_ARG_STRLEN, so push the base64 in small per-chunk
                    // commands instead of slicing the joined command string.
                    execOrThrow(plan.setup)
                    for (w in plan.writes) execOrThrow(w)
                    execOrThrowWithRunAs(plan.decode)
                    result = execOrThrowWithRunAs(plan.verify)
                }

                val remoteMd5 = result.trim()
                if (remoteMd5.length == 32) {
                    if (remoteMd5 != localMd5) {
                        execOrThrow("rm -f $target")
                        return@doPush Result.failure(Exception("MD5 mismatch after push: local=$localMd5 remote=$remoteMd5"))
                    }
                } else {
                    val sizeCmd = "wc -c < $target 2>/dev/null"
                    val remoteSize =
                        try {
                            execOrThrow(sizeCmd).trim().toLong()
                        } catch (_: Exception) {
                            0L
                        }
                    if (remoteSize != bytes.size.toLong()) {
                        execOrThrow("rm -f $target")
                        return@doPush Result.failure(Exception("Size mismatch after push: local=${bytes.size} remote=$remoteSize"))
                    }
                }
                LogRepository.add("Shizuku push completed: $targetPath", LogLevel.SUCCESS)
                return@doPush Result.success("Pushed to $targetPath")
            }

            try {
                var lastError: Result<String>? = null
                for (attempt in 0..PUSH_RETRY_COUNT) {
                    val result = doPush()
                    if (result.isSuccess) return@withContext result
                    lastError = result
                    execOrThrow("rm -f /data/local/tmp/wb64_* /data/local/tmp/wuwa_push_*.sh")
                }
                LogRepository.add("Shizuku push failed after retries: ${lastError?.exceptionOrNull()?.message}", LogLevel.ERROR)
                return@withContext lastError ?: Result.failure(Exception("Push failed"))
            } catch (e: Exception) {
                LogRepository.add("Shizuku push exception: ${e.message}", LogLevel.ERROR)
                execOrThrow("rm -f /data/local/tmp/wb64_* /data/local/tmp/wuwa_push_*.sh")
                Result.failure(e)
            }
        }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val out = execOrThrow("mkdir -p ${shQuote(dirPath)}")
                Result.success(out)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun fileExists(path: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val out = execOrThrow("test -f ${shQuote(path)} && echo 1 || echo 0")
                Result.success(out.trim() == "1")
            } catch (_: Exception) {
                Result.success(false)
            }
        }

    override suspend fun listDirectory(path: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val out = execOrThrow("ls -1 ${shQuote(path)}")
                Result.success(out.trim().lines().filter { it.isNotBlank() })
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun backupFile(path: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val backupPath = "$path.backup_${System.currentTimeMillis()}"
                execOrThrow("cp ${shQuote(path)} ${shQuote(backupPath)}")
                Result.success(backupPath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun <T> readViaTemp(
        path: String,
        shellCmd: String,
        decode: (File) -> T,
    ): Result<T> {
        var lastError: Exception? = null
        for (attempt in 0..2) {
            if (attempt > 0) delay(500L * attempt)
            // Stage through /data/local/tmp, NOT cacheDir: the UserService runs as
            // shell (uid 2000), which cannot traverse the app's private cache dir —
            // so redirecting there always produced an empty file.
            val nonce = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val tmpFile = "/data/local/tmp/wuwa_read_${System.currentTimeMillis()}_$nonce.tmp"
            val tmpQuote = shQuote(tmpFile)
            try {
                val cmd = "$shellCmd ${shQuote(path)} > $tmpQuote 2>/dev/null; chmod 644 $tmpQuote 2>/dev/null; echo DONE"
                val result = execOrThrow(cmd)
                if (!result.contains("DONE")) {
                    throw Exception("Command failed: $result")
                }
                val localFile = File(tmpFile)
                if (!localFile.exists()) {
                    throw Exception("Temp file not found: $tmpFile")
                }
                if (localFile.length() == 0L) {
                    // An empty stage usually means the inner command failed silently
                    // (permission denied) while the redirect still created the file.
                    throw Exception("Remote read produced no data for ${path.substringAfterLast("/")}")
                }
                val out = decode(localFile)
                return Result.success(out)
            } catch (e: Exception) {
                lastError = e
                LogRepository.add("Shizuku readViaTemp attempt $attempt failed: ${e.message}", LogLevel.WARNING)
            } finally {
                try {
                    execOrThrow("rm -f $tmpQuote")
                } catch (_: Exception) {
                }
            }
        }
        LogRepository.add("Shizuku readViaTemp failed: ${lastError?.message}", LogLevel.ERROR)
        return Result.failure(lastError ?: Exception("readViaTemp failed"))
    }

    override suspend fun readFile(path: String): Result<String> =
        withContext(Dispatchers.IO) {
            readViaTemp(path, "cat") { it.readText() }
        }

    override suspend fun readFileBytes(path: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            readViaTemp(path, "base64 -w0") { Base64.decode(it.readText().trim(), Base64.DEFAULT) }
        }

    override suspend fun copyFile(
        sourcePath: String,
        targetPath: String,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val parent = java.io.File(targetPath).parent ?: return@withContext Result.failure(Exception("Invalid target path"))
                execOrThrow("mkdir -p ${shQuote(parent)}")
                execOrThrow("cp ${shQuote(sourcePath)} ${shQuote(targetPath)}")
                Result.success(targetPath)
            } catch (e: Exception) {
                LogRepository.add("Shizuku copyFile failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun deleteFile(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            LogRepository.add("Shizuku delete: $path")
            try {
                execOrThrow("rm -f ${shQuote(path)}")
                LogRepository.add("Shizuku delete completed: $path", LogLevel.SUCCESS)
                Result.success(Unit)
            } catch (e: Exception) {
                LogRepository.add("Shizuku delete failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    private suspend fun execOrThrow(command: String): String {
        val svc = shellService ?: throw Exception("Shizuku service not connected")
        val output = withTimeout(SHIZUKU_CALL_TIMEOUT_MS) { svc.execCommand(command) }
        return parseServiceResult(output)
    }

    private val gamePkg = "com.kurogame.wutheringwaves.global"

    /**
     * Mirrors AdbBackend: a command that fails with "Permission denied" (typically a write into
     * the game's `Android/data`, which some ROMs block for `shell`/uid 2000) is retried via
     * `run-as <game>`. This only helps debuggable builds; for the production game it still fails,
     * but we log a clear "use SAF or Root" pointer instead of a bare `sh: can't create`.
     */
    private suspend fun execOrThrowWithRunAs(command: String): String {
        return try {
            execOrThrow(command)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("Permission denied", ignoreCase = true)) {
                LogRepository.add("Shizuku: Permission denied, retrying via run-as $gamePkg", LogLevel.WARNING)
                try {
                    val alt = execOrThrow("run-as ${shQuote(gamePkg)} $command 2>/dev/null")
                    LogRepository.add("Shizuku: run-as fallback succeeded", LogLevel.SUCCESS)
                    return alt
                } catch (altErr: Exception) {
                    val altMsg = altErr.message ?: ""
                    if (altMsg.contains("not debuggable", ignoreCase = true) ||
                        altMsg.contains("Package not debuggable", ignoreCase = true)
                    ) {
                        LogRepository.add(
                            "Shizuku: run-as unavailable — $gamePkg is not debuggable. " +
                                "Use the SAF or Root access method for this ROM.",
                            LogLevel.ERROR,
                        )
                    }
                    throw altErr
                }
            }
            throw e
        }
    }

    /**
     * Decodes the structured result returned by [ShellUserService]:
     * - A successful command (exit 0) returns its raw stdout.
     * - A failed command returns "SHIZUKU_EXIT=<code>\n<stderr>" and is surfaced as an exception.
     * - A service-side timeout returns "Command timed out after 60s" and is surfaced as an exception.
     *
     * This avoids the previous behavior of matching raw stdout substrings (e.g. a log file that
     * literally contains "Permission denied"), which both discarded legitimate output and could
     * misreport failures.
     */
    private fun parseServiceResult(output: String): String {
        if (output.startsWith("SHIZUKU_EXIT=")) {
            val rest = output.removePrefix("SHIZUKU_EXIT=")
            val nl = rest.indexOf('\n')
            val code = if (nl < 0) rest.toIntOrNull() ?: 1 else rest.substring(0, nl).toIntOrNull() ?: 1
            if (code != 0) {
                val msg = if (nl < 0) "" else rest.substring(nl + 1)
                throw Exception(msg.ifBlank { "Command failed (exit $code)" })
            }
            return if (nl < 0) "" else rest.substring(nl + 1)
        }
        // The service returns exactly this string when its watchdog kills a hung
        // command. Match only as a prefix so log *content* containing the phrase
        // is not misreported as a timeout.
        if (output.trimStart().startsWith("Command timed out")) {
            throw Exception(output.trim())
        }
        return output
    }

    companion object {
        // Must exceed ShellUserService's internal 60s command timeout so the service always
        // returns its result string before the coroutine is cancelled (binder transact is not
        // interruptible, so an early client timeout would leak the in-flight transaction).
        private const val SHIZUKU_CALL_TIMEOUT_MS = 75_000L
    }
}
