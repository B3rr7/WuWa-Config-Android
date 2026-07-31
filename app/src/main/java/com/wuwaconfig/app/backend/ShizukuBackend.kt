package com.wuwaconfig.app.backend

import android.content.ComponentName
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

class ShizukuBackend : AccessBackend {
    private var shellService: IShellService? = null
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
                LogRepository.add("Shizuku connect failed: ${e.message}", LogLevel.ERROR)
                Result.failure(Exception("Shizuku is not running. Start Shizuku first."))
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
                    e.message?.contains("timed out", ignoreCase = true) == true
                }) {
                    val output = withTimeout(60_000) { svc.execCommand(command) }
                    if (output.contains("Command timed out", ignoreCase = true) ||
                        output.contains("Command failed", ignoreCase = true)
                    ) {
                        throw Exception(output.trim())
                    }
                    val filtered = filterPermissionDenied(output)
                    if (filtered != output) {
                        LogRepository.add("Shizuku shell permission denied", LogLevel.ERROR)
                        throw Exception(filtered.trim().ifEmpty { "Permission denied" })
                    }
                    filtered.trim()
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
            val localMd5 = computeMd5(sourceFile)
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val target = shQuote(targetPath)
            val parent = File(targetPath).parent ?: return@withContext Result.failure(Exception("Invalid target path"))

            suspend fun doPush(): Result<String> {
                val tmpB64 = "/data/local/tmp/wb64_${System.currentTimeMillis()}_${(0..9999).random()}"
                val tq = shQuote(tmpB64)
                val setup = "rm -f /data/local/tmp/wb64_* && mkdir -p ${shQuote(parent)}"
                val chunkSize = maxPushChunkSize(tq)
                val chunks = encoded.chunked(chunkSize)
                val writes =
                    chunks.mapIndexed { i, chunk ->
                        val redir = if (i == 0) ">" else ">>"
                        "printf '%s' ${shQuote(chunk)} $redir $tq"
                    }
                val decode = "base64 -d $tq > $target && rm -f $tq"
                val verify = "md5sum $target 2>/dev/null | cut -d' ' -f1"
                val fullCmd = (listOf(setup) + writes + listOf(decode, verify)).joinToString(" && ")

                val result: String
                if (fullCmd.length <= MAX_ARG_STRLEN) {
                    result = execOrThrow(fullCmd)
                } else {
                    val scriptPath = "/data/local/tmp/wuwa_push_${System.currentTimeMillis()}.sh"
                    val sq = shQuote(scriptPath)
                    val scriptChunks = fullCmd.chunked(4096)
                    val writeScript =
                        scriptChunks.mapIndexed { i, chunk ->
                            val redir = if (i == 0) ">" else ">>"
                            "printf '%s' ${shQuote(chunk)} $redir $sq"
                        }.joinToString(" && ")
                    execOrThrow(writeScript)
                    result = execOrThrow("chmod +x $sq && sh $sq && rm -f $sq")
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

    override suspend fun readFile(path: String): Result<String> =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            val cacheDir = com.wuwaconfig.app.WuWaConfigApp.instance.cacheDir.absolutePath
            for (attempt in 0..2) {
                if (attempt > 0) delay(500L * attempt)
                try {
                    val tmpFile = "$cacheDir/wuwa_read_${System.currentTimeMillis()}_${(0..9999).random()}.txt"
                    val cmd = "cat ${shQuote(path)} > ${shQuote(tmpFile)} 2>/dev/null; echo DONE"
                    val result = execOrThrow(cmd)
                    if (!result.contains("DONE")) {
                        throw Exception("Command failed: $result")
                    }
                    val localFile = java.io.File(tmpFile)
                    if (!localFile.exists() || localFile.length() == 0L) {
                        throw Exception("Temp file not found or empty: $tmpFile")
                    }
                    val out = localFile.readText()
                    execOrThrow("rm -f ${shQuote(tmpFile)}")
                    return@withContext Result.success(out)
                } catch (e: Exception) {
                    lastError = e
                    LogRepository.add("Shizuku readFile attempt $attempt failed: ${e.message}", LogLevel.WARNING)
                }
            }
            LogRepository.add("Shizuku readFile failed: ${lastError?.message}", LogLevel.ERROR)
            Result.failure(lastError ?: Exception("readFile failed"))
        }

    override suspend fun readFileBytes(path: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            val cacheDir = com.wuwaconfig.app.WuWaConfigApp.instance.cacheDir.absolutePath
            for (attempt in 0..2) {
                if (attempt > 0) delay(500L * attempt)
                try {
                    val tmpFile = "$cacheDir/wuwa_read_${System.currentTimeMillis()}_${(0..9999).random()}.b64"
                    val cmd = "base64 -w0 ${shQuote(path)} > ${shQuote(tmpFile)} 2>/dev/null; echo DONE"
                    val result = execOrThrow(cmd)
                    if (!result.contains("DONE")) {
                        throw Exception("Command failed: $result")
                    }
                    val localFile = java.io.File(tmpFile)
                    if (!localFile.exists() || localFile.length() == 0L) {
                        throw Exception("Temp file not found or empty: $tmpFile")
                    }
                    val b64 = localFile.readText().trim()
                    execOrThrow("rm -f ${shQuote(tmpFile)}")
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    return@withContext Result.success(bytes)
                } catch (e: Exception) {
                    lastError = e
                    LogRepository.add("Shizuku readFileBytes attempt $attempt failed: ${e.message}", LogLevel.WARNING)
                }
            }
            LogRepository.add("Shizuku readFileBytes failed: ${lastError?.message}", LogLevel.ERROR)
            Result.failure(lastError ?: Exception("readFileBytes failed"))
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

    private suspend fun execOrThrow(command: String): String {
        val svc = shellService ?: throw Exception("Shizuku service not connected")
        val output = withTimeout(60_000) { svc.execCommand(command) }
        if (output.contains("Command timed out", ignoreCase = true)) {
            throw Exception(output.trim())
        }
        if (output.contains("Command failed", ignoreCase = true)) {
            throw Exception(output.trim())
        }
        return output
    }

    private fun filterPermissionDenied(output: String): String {
        val deniedLines = output.lines().filter { it.contains("Permission denied", ignoreCase = true) }
        return if (deniedLines.isNotEmpty()) deniedLines.joinToString("\n") else output
    }
}
