package com.wuwaconfig.app.model

import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {
    private val _entries = mutableListOf<LogEntry>()
    private val _entriesFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entriesFlow.asStateFlow()

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
            loadFromDisk()
        }
    }

    fun add(
        message: String,
        level: LogLevel = LogLevel.INFO,
    ) {
        val entry = LogEntry(message, timestamp(), level)
        synchronized(lock) {
            _entries.add(entry)
            if (_entries.size > MAX_ENTRIES) _entries.removeAt(0)
            _entriesFlow.value = _entries.toList()
        }
        appendToDisk(entry)
    }

    fun clear() {
        synchronized(lock) {
            _entries.clear()
            _entriesFlow.value = emptyList()
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
            val content = _entries.joinToString("\n") { lineFormat(it) }
            file.writeText(content)
        }
        return file
    }

    private fun timestamp(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun dateStamp(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

    private fun lineFormat(entry: LogEntry): String = "[${entry.timestamp}][${entry.level.name}] ${entry.message}"

    private fun loadFromDisk() {
        try {
            val file = logFile ?: return
            if (!file.exists()) return
            val entries =
                file.readLines().takeLast(MAX_ENTRIES).mapNotNull { line ->
                    parseLine(line)
                }
            _entries.addAll(entries)
            _entriesFlow.value = _entries.toList()
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
            file2.delete()
            file1.copyTo(file2, overwrite = true)
            file1.delete()
            logFile?.copyTo(file1, overwrite = true)
            logFile?.writeText("")
        } catch (_: Exception) {
        }
    }
}
