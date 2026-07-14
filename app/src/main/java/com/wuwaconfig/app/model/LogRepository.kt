package com.wuwaconfig.app.model

import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {
    val entries = mutableStateListOf<LogEntry>()

    private var logFile: File? = null
    private val lock = Any()

    private const val MAX_ENTRIES = 1000
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024L

    fun init() {
        synchronized(lock) {
            if (logFile != null) return
            val dir =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "WuWaConfig/logs",
                ).also { it.mkdirs() }
            logFile = File(dir, "app.log")
            runBlocking(Dispatchers.IO) { loadFromDisk() }
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
        try {
            logFile?.writeText("")
        } catch (_: Exception) {
        }
    }

    fun saveSnapshot(): File? {
        val fileName = "WuWaConfig_${dateStamp()}.txt"
        val dir =
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "WuWaConfig",
            ).also { it.mkdirs() }
        val file = File(dir, fileName)
        synchronized(lock) {
            val content = entries.joinToString("\n") { lineFormat(it) }
            file.writeText(content)
        }
        return file
    }

    fun saveSmartBrainReport(text: String): File? {
        return try {
            val dir =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "WuWaConfig",
                ).also { it.mkdirs() }
            val file = File(dir, "smartbrain_report.txt")
            file.writeText(text)
            add("SmartBrain: report saved to ${file.absolutePath}")
            file
        } catch (e: Exception) {
            add("SmartBrain: failed to save report: ${e.message}", LogLevel.WARNING)
            null
        }
    }

    private fun timestamp(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun dateStamp(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

    private fun lineFormat(entry: LogEntry): String = "[${entry.timestamp}][${entry.level.name}] ${entry.message}"

    private fun loadFromDisk() {
        try {
            val file = logFile ?: return
            if (!file.exists()) return
            val items =
                file.readLines().takeLast(MAX_ENTRIES).mapNotNull { line ->
                    parseLine(line)
                }
            entries.addAll(items)
        } catch (_: Exception) {
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
        try {
            val file = logFile ?: return
            file.appendText("${lineFormat(entry)}\n")
            if (file.length() > MAX_FILE_SIZE) rotate()
        } catch (_: Exception) {
        }
    }

    private fun rotate() {
        try {
            val dir = logFile?.parentFile ?: return
            val file1 = File(dir, "app.1.log")
            val file2 = File(dir, "app.2.log")
            val tmp = File(dir, "app.rot")
            file2.delete()
            file1.takeIf { it.exists() }?.let {
                it.copyTo(tmp, overwrite = true)
                tmp.renameTo(file2)
            }
            logFile?.takeIf { it.exists() }?.let {
                it.copyTo(tmp, overwrite = true)
                tmp.renameTo(file1)
            }
            logFile?.writeText("")
        } catch (_: Exception) {
        }
    }
}
