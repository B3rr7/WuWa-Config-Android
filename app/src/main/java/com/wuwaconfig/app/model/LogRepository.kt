package com.wuwaconfig.app.model

import android.os.Build
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import com.wuwaconfig.app.WuWaConfigApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {
    val entries = mutableStateListOf<LogEntry>()

    private var logFile: File? = null
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diskMutex = Mutex()

    @Volatile
    private var diskWriteWarned = false

    private const val MAX_ENTRIES = 1000
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024L

    /** Downloads root when All-Files-Access is granted, else null. */
    fun publicBaseDir(): File? {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        if (!granted) return null
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "WuWaConfig",
        )
    }

    /** App-scoped storage that never needs a runtime grant. */
    private fun fallbackBaseDir(): File {
        val ext = WuWaConfigApp.instance.getExternalFilesDir(null)
        return File(ext ?: WuWaConfigApp.instance.filesDir, "WuWaConfig")
    }

    fun init() {
        var usedFallback = false
        synchronized(lock) {
            if (logFile != null) return
            val base = publicBaseDir() ?: fallbackBaseDir().also { usedFallback = true }
            val dir = File(base, "logs").also { it.mkdirs() }
            logFile = File(dir, "app.log")
        }
        scope.launch {
            val items = loadFromDiskAsync()
            synchronized(lock) {
                entries.addAll(items)
            }
            if (usedFallback) {
                add("Public Downloads not writable (missing All-Files-Access); disk logs moved to app storage", LogLevel.WARNING)
            }
        }
    }

    fun add(
        message: String,
        level: LogLevel = LogLevel.INFO,
    ) {
        val entry = LogEntry(message, timestamp(), level)
        synchronized(lock) {
            entries.add(entry)
            if (entries.size > MAX_ENTRIES) entries.removeAt(0)
        }
        appendToDisk(entry)
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
        scope.launch {
            diskMutex.withLock {
                try {
                    logFile?.writeText("")
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun saveSnapshot(): File? {
        val fileName = "WuWaConfig_${dateStamp()}.txt"
        var usedFallback = false
        val base = publicBaseDir() ?: fallbackBaseDir().also { usedFallback = true }
        val dir = base.also { it.mkdirs() }
        val file = File(dir, fileName)
        val content = synchronized(lock) { entries.joinToString("\n") { lineFormat(it) } }
        return try {
            withContext(Dispatchers.IO) { file.writeText(content) }
            if (usedFallback) add("Snapshot saved to app storage (Downloads unavailable): ${file.absolutePath}", LogLevel.WARNING)
            file
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveSmartBrainReport(text: String): File? {
        return try {
            var usedFallback = false
            val base = publicBaseDir() ?: fallbackBaseDir().also { usedFallback = true }
            val dir = base.also { it.mkdirs() }
            val file = File(dir, "smartbrain_report.txt")
            withContext(Dispatchers.IO) { file.writeText(text) }
            if (usedFallback) {
                add("SmartBrain report saved to app storage (Downloads unavailable)", LogLevel.WARNING)
            } else {
                add("SmartBrain: report saved to ${file.absolutePath}")
            }
            file
        } catch (e: Exception) {
            add("SmartBrain: failed to save report: ${e.message}", LogLevel.WARNING)
            null
        }
    }

    private fun timestamp(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun dateStamp(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

    private fun lineFormat(entry: LogEntry): String = "[${entry.timestamp}][${entry.level.name}] ${entry.message}"

    private fun loadFromDiskAsync(): List<LogEntry> {
        try {
            val file = logFile ?: return emptyList()
            if (!file.exists()) return emptyList()
            return file.readLines().takeLast(MAX_ENTRIES).mapNotNull { line ->
                parseLine(line)
            }
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun parseLine(line: String): LogEntry? {
        val m = Regex("^\\[(\\d{2}:\\d{2}:\\d{2})\\]\\[(\\w+)\\] (.+)$").find(line) ?: return null
        val ts = m.groupValues[1]
        val level =
            try {
                LogLevel.valueOf(m.groupValues[2])
            } catch (_: Exception) {
                LogLevel.INFO
            }
        val msg = m.groupValues[3]
        return LogEntry(msg, ts, level)
    }

    private fun appendToDisk(entry: LogEntry) {
        scope.launch {
            diskMutex.withLock {
                try {
                    val file = logFile ?: return@withLock
                    file.appendText("${lineFormat(entry)}\n")
                    if (file.length() > MAX_FILE_SIZE) rotate()
                } catch (e: Exception) {
                    // Never recurse into add() from here — record one in-memory
                    // warning so broken disk logging is at least visible once.
                    if (!diskWriteWarned) {
                        diskWriteWarned = true
                        synchronized(lock) {
                            entries.add(
                                LogEntry(
                                    "Disk logging unavailable (${e.message}); keeping memory-only",
                                    timestamp(),
                                    LogLevel.WARNING,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun rotate() {
        val dir = logFile?.parentFile ?: return
        val file1 = File(dir, "app.1.log")
        val file2 = File(dir, "app.2.log")
        file2.delete()
        // Same-directory rename(2) is atomic and cheap — no copy-through-tmp.
        if (file1.exists() && !file1.renameTo(file2)) return
        val current = logFile
        if (current != null && current.exists() && !current.renameTo(file1)) return
        try {
            current?.createNewFile()
        } catch (_: Exception) {
        }
    }
}
