package com.wuwaconfig.app.backend

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.util.Base64
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.service.ShellUserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ShizukuBackend(private val context: android.content.Context) : AccessBackend {
    // Written from the main thread (onServiceConnected) and read from IO workers.
    @Volatile
    private var shellService: IShellService? = null

    @Volatile
    private var serviceConnection: ServiceConnection? = null

    @Volatile
    private var boundBinder: IBinder? = null

    @Volatile
    private var deathRecipient: IBinder.DeathRecipient? = null

    /** Serializes [connect]: two concurrent binds would cross-assign [serviceConnection]. */
    private val connectMutex = Mutex()

    interface IShellService {
        fun execCommand(command: String): String
    }

    private class ShellServiceProxy(val binder: IBinder) : IShellService {
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
        get() {
            return try {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return false
                // The only implementation ever stored is a ShellServiceProxy, so
                // `as?` could never be null: ask the binder directly.
                val svc = shellService as? ShellServiceProxy ?: return false
                svc.binder.pingBinder()
            } catch (_: Exception) {
                false
            }
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
                // Serialized: two concurrent connects each assigned serviceConnection and
                // then dereferenced it with !!, so one could bind with the other's
                // connection and disconnect() would unbind a connection it does not own.
                connectMutex.withLock {
                    bindUserService()
                }
                LogRepository.add("Shizuku connected successfully", LogLevel.SUCCESS)
                Result.success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
                        try {
                            boundBinder?.let { old ->
                                deathRecipient?.let { old.unlinkToDeath(it, 0) }
                            }
                        } catch (_: Exception) {
                        }
                        val recipient =
                            IBinder.DeathRecipient {
                                shellService = null
                                serviceConnection = null
                                boundBinder = null
                                deathRecipient = null
                            }
                        try {
                            binder.linkToDeath(recipient, 0)
                            boundBinder = binder
                            deathRecipient = recipient
                        } catch (_: Exception) {
                            // Binder already dead — leave shellService null.
                        }
                        if (binder.isBinderAlive) {
                            shellService = ShellServiceProxy(binder)
                        }
                    }
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    try {
                        boundBinder?.let { b ->
                            deathRecipient?.let { b.unlinkToDeath(it, 0) }
                        }
                    } catch (_: Exception) {
                    }
                    boundBinder = null
                    deathRecipient = null
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
        try {
            boundBinder?.let { b ->
                deathRecipient?.let { b.unlinkToDeath(it, 0) }
            }
        } catch (_: Exception) {
        }
        boundBinder = null
        deathRecipient = null
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
            // A DeadObject / "remote call failed" can happen AFTER the shell already ran a
            // mutating command, so only replay read-only ones: this API is used for
            // HashMonitor's `rm`/`mv` and ProfileExtractor's append pipelines too.
            val replayable = isReadOnlyShellCommand(command)
            val result =
                try {
                    retryIO(
                        times = 3,
                        backoffMs = 500L,
                        idempotent = replayable,
                        shouldRetry = { e ->
                            val msg = e.message?.lowercase() ?: ""
                            msg.contains("service not connected") ||
                                msg.contains("remote call failed") ||
                                msg.contains("deadobject") ||
                                msg.contains("broken pipe")
                        },
                    ) {
                        parseServiceResult(
                            withTimeout(SHIZUKU_CALL_TIMEOUT_MS) { svc.execCommand(command) },
                        ).trim()
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
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
            val remoteParent = targetPath.substringBeforeLast('/', "")
            if (remoteParent.isEmpty() || !targetPath.contains('/')) {
                return@withContext Result.failure(Exception("Invalid target path"))
            }
            // This push's own base64 staging file. Cleanup deletes exactly this: the old
            // `rm -f /data/local/tmp/wb64_*` wiped a concurrent push's in-flight staging.
            var staging: String? = null
            // Decode into a sibling and `mv` only after the hash matches. Writing the
            // target directly meant that a read-but-not-writable target (scoped storage)
            // produced a mismatch that the old code resolved by deleting the user's
            // working config.
            val stagingTarget = targetPath + STAGING_SUFFIX

            suspend fun cleanupStaging(path: String?) {
                if (path == null) return
                // Wrapped: a failing cleanup must not replace the real push error.
                runCatching { execOrThrow("rm -f ${shQuote(path)}") }
            }

            /** Removes only our own `.wuwa_new` sibling — never the deployed target. */
            suspend fun cleanupStagedTarget() {
                runCatching { execOrThrow("rm -f ${shQuote(stagingTarget)}") }
            }

            suspend fun doPush(): Result<String> {
                val tmpB64 = "/data/local/tmp/wb64_${System.currentTimeMillis()}_${UUID.randomUUID()}"
                staging = tmpB64
                val plan = buildPushFilePlan(encoded, stagingTarget, tmpB64)

                val result: String
                if (plan.fitsSingleCommand) {
                    result = execOrThrowWithRunAs(plan.joinedCommand)
                } else {
                    // Payload exceeds a single shell argument limit. Each write line is
                    // already < MAX_ARG_STRLEN, so push the base64 in small per-chunk
                    // commands instead of slicing the joined command string.
                    // Use run-as for setup/decode/verify which touch Android/data.
                    execOrThrowWithRunAs(plan.setup)
                    for (w in plan.writes) execOrThrow(w)
                    execOrThrowWithRunAs(plan.decode)
                    result = execOrThrowWithRunAs(plan.verify)
                }

                val remoteMd5 = result.trim()
                if (remoteMd5.length == 32) {
                    if (remoteMd5 != localMd5) {
                        cleanupStaging(staging)
                        cleanupStagedTarget()
                        return@doPush Result.failure(Exception("MD5 mismatch after push: local=$localMd5 remote=$remoteMd5"))
                    }
                } else {
                    val sizeCmd = "wc -c < ${shQuote(stagingTarget)} 2>/dev/null"
                    // Do not collapse a transport failure into 0L: that produced a
                    // misleading "Size mismatch: local=20480 remote=0".
                    val remoteSize = execOrThrow(sizeCmd).trim().toLong()
                    if (remoteSize != bytes.size.toLong()) {
                        cleanupStaging(staging)
                        cleanupStagedTarget()
                        return@doPush Result.failure(Exception("Size mismatch after push: local=${bytes.size} remote=$remoteSize"))
                    }
                }
                execOrThrowWithRunAs("mv ${shQuote(stagingTarget)} ${shQuote(targetPath)}")
                cleanupStaging(staging)
                LogRepository.add("Shizuku push completed: $targetPath", LogLevel.SUCCESS)
                return@doPush Result.success("Pushed to $targetPath")
            }

            try {
                var lastError: Result<String>? = null
                for (attempt in 0..PUSH_RETRY_COUNT) {
                    val result = doPush()
                    if (result.isSuccess) return@withContext result
                    lastError = result
                    cleanupStaging(staging)
                }
                LogRepository.add("Shizuku push failed after retries: ${lastError?.exceptionOrNull()?.message}", LogLevel.ERROR)
                return@withContext lastError ?: Result.failure(Exception("Push failed"))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("Shizuku push exception: ${e.message}", LogLevel.ERROR)
                cleanupStaging(staging)
                Result.failure(e)
            }
        }

    override suspend fun ensureDirectoryExists(dirPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val out = execOrThrowWithRunAs("mkdir -p ${shQuote(dirPath)}")
                Result.success(out)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun fileExists(path: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val out = execOrThrowWithRunAs("test -f ${shQuote(path)} && echo 1 || echo 0")
                Result.success(out.trim() == "1")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Permission denied with run-as fallback failed -> real failure, not "not exists".
                // Mirror AdbBackend parity: surface failure so disable path doesn't falsely succeed.
                if (isPermissionDenied(e.message)) {
                    Result.failure(e)
                } else {
                    Result.success(false)
                }
            }
        }

    override suspend fun listDirectory(path: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val out = execOrThrow("ls -1 ${shQuote(path)}")
                Result.success(out.trim().lines().filter { it.isNotBlank() })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Reads [path] by redirecting the output to a world-readable file under
     * /data/local/tmp and reporting the inner command's real exit status.
     *
     * The status marker is emitted for the *inner* command (`{ cmd; echo DONE=$?; }`), not
     * as a trailing `echo DONE` for the whole compound: a trailing marker made the exit
     * status always 0, so the permission-denied branch below was unreachable and only the
     * empty-file heuristic was left. stderr is captured to a side file and folded into the
     * thrown message for the same reason.
     */
    private suspend fun <T> readViaTemp(
        path: String,
        shellCmd: String,
        decode: (File) -> T,
    ): Result<T> {
        var lastError: Exception? = null
        var runAsTried = false
        for (attempt in 0..2) {
            if (attempt > 0) delay(500L * attempt)
            // Stage through /data/local/tmp, NOT cacheDir: the UserService runs as
            // shell (uid 2000), which cannot traverse the app's private cache dir —
            // so redirecting there always produced an empty file.
            val nonce = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val tmpFile = "/data/local/tmp/wuwa_read_${System.currentTimeMillis()}_$nonce.tmp"
            val tmpQuote = shQuote(tmpFile)
            val errFile = "$tmpFile.err"
            val errQuote = shQuote(errFile)
            try {
                val inner =
                    if (runAsTried) {
                        "${runAsCommand(GamePaths.TARGET_PACKAGE, "$shellCmd ${shQuote(path)}")} > $tmpQuote 2>$errQuote"
                    } else {
                        "$shellCmd ${shQuote(path)} > $tmpQuote 2>$errQuote"
                    }
                val cmd = "{ $inner ; echo $STATUS_PREFIX\$? ; } ; chmod 644 $tmpQuote $errQuote 2>/dev/null"
                val result = execOrThrow(cmd)
                val statusLine =
                    result.lineSequence().firstOrNull { it.trimStart().startsWith(STATUS_PREFIX) }
                        ?: throw Exception("Command produced no status marker: ${result.take(120)}")
                val status =
                    statusLine.trim().removePrefix(STATUS_PREFIX).trim().toIntOrNull()
                        ?: throw Exception("Unparseable status marker: $statusLine")
                if (status != 0) {
                    val errBody =
                        runCatching { File(errFile).takeIf { it.exists() }?.readText()?.trim().orEmpty() }
                            .getOrDefault("")
                    throw Exception(
                        "Read failed (exit $status) for ${path.substringAfterLast("/")}: " +
                            errBody.ifBlank { "no stderr captured" },
                    )
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                LogRepository.add("Shizuku readViaTemp attempt $attempt failed: ${e.message}", LogLevel.WARNING)
                // Switch identity for the remaining attempts, but never gate this on the
                // attempt index: a transient failure later on can still be a permission one.
                if (!runAsTried && isPermissionDenied(e.message)) {
                    runAsTried = true
                    LogRepository.add(
                        "Shizuku readViaTemp: Permission denied, retrying via run-as",
                        LogLevel.WARNING,
                    )
                }
            } finally {
                // Best-effort: both the stage and its stderr sidecar must go.
                runCatching { execOrThrow("rm -f $tmpQuote $errQuote") }
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
                val parent = targetPath.substringBeforeLast('/', "")
                if (parent.isEmpty()) return@withContext Result.failure(Exception("Invalid target path"))
                execOrThrowWithRunAs("mkdir -p ${shQuote(parent)}")
                execOrThrowWithRunAs("cp ${shQuote(sourcePath)} ${shQuote(targetPath)}")
                Result.success(targetPath)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogRepository.add("Shizuku copyFile failed: ${e.message}", LogLevel.ERROR)
                Result.failure(e)
            }
        }

    override suspend fun deleteFile(path: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            LogRepository.add("Shizuku delete: $path")
            try {
                execOrThrowWithRunAs("rm -f ${shQuote(path)}")
                LogRepository.add("Shizuku delete completed: $path", LogLevel.SUCCESS)
                Result.success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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

    /**
     * Mirrors AdbBackend: a command that fails with "Permission denied" (typically a write into
     * the game's `Android/data`, which some ROMs block for `shell`/uid 2000) is retried via
     * `run-as <game>`. This only helps debuggable builds; for the production game it still fails,
     * but we log a clear "use SAF or Root" pointer instead of a bare `sh: can't create`.
     *
     * The retry itself goes through the shared [withRunAsFallback] contract, which groups the
     * command with `sh -c` (an ungrouped `run-as pkg cmd > f && rm f` leaves the redirect and
     * the chain to the outer uid-2000 shell) and no longer swallows stderr — the
     * "not debuggable" text run-as prints is what makes the guidance below reachable.
     */
    private suspend fun execOrThrowWithRunAs(command: String): String {
        var usedRunAs = false
        val result =
            withRunAsFallback(command, GamePaths.TARGET_PACKAGE) { cmd ->
                usedRunAs = cmd != command
                try {
                    Result.success(execOrThrow(cmd))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        if (result.isSuccess) {
            if (usedRunAs) {
                LogRepository.add("Shizuku: run-as fallback succeeded", LogLevel.SUCCESS)
            }
            return result.getOrThrow()
        }
        val err = result.exceptionOrNull() ?: Exception("Shizuku command failed")
        if (isNotDebuggable(err.message)) {
            LogRepository.add(
                "Shizuku: run-as unavailable — ${GamePaths.TARGET_PACKAGE} is not debuggable. " +
                    "Use the SAF or Root access method for this ROM.",
                LogLevel.ERROR,
            )
        } else if (isPermissionDenied(err.message)) {
            LogRepository.add("Shizuku: Permission denied, retrying via run-as ${GamePaths.TARGET_PACKAGE}", LogLevel.WARNING)
        }
        throw err
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
        if (output.startsWith("SHIZUKU_TRUNCATED")) {
            throw Exception("Output truncated at ~900KB binder limit — use readViaTemp for large files")
        }
        if (output.startsWith("SHIZUKU_EXIT=")) {
            val rest = output.removePrefix("SHIZUKU_EXIT=")
            val nl = rest.indexOf('\n')
            val code = if (nl < 0) rest.toIntOrNull() ?: 1 else rest.substring(0, nl).toIntOrNull() ?: 1
            if (code != 0) {
                val msg = if (nl < 0) "" else rest.substring(nl + 1)
                throw Exception(msg.ifBlank { "Command failed (exit $code)" })
            }
            val body = if (nl < 0) "" else rest.substring(nl + 1)
            if (body.startsWith("SHIZUKU_TRUNCATED")) {
                throw Exception("Output truncated at ~900KB binder limit — use readViaTemp for large files")
            }
            return body
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

        /** Marker for the inner command's status inside [readViaTemp]'s compound command. */
        private const val STATUS_PREFIX = "DONE="

        /** Decode-then-verify-then-rename staging sibling for a pushed config file. */
        private const val STAGING_SUFFIX = ".wuwa_new"
    }
}
