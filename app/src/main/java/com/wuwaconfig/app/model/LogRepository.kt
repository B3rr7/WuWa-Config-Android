package com.wuwaconfig.app.model

import android.os.Build
import android.os.Environment
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.util.writeAtomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogRepository {
    // Immutable snapshot held in a StateFlow. Writers publish a new list atomically;
    // Compose reads it via collectAsStateWithLifecycle on the main thread. Unlike the
    // previous mutableStateListOf, this has no read/write data race: the list itself is
    // never mutated in place, and StateFlow's value assignment is thread-safe.
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /**
     * Exposes the log as read-only BY DESIGN. `_entries` is a MutableStateFlow
     * but `asStateFlow()` hands out a read-only StateFlow, and every mutation
     * site builds a fresh immutable list inside `synchronized(lock)` before
     * assigning `_entries.value` — so no mutable list ever escapes this object
     * and no caller can mutate the log behind the lock. Do not "fix" this by
     * returning `_entries` directly.
     */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private var logFile: File? = null
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diskMutex = Mutex()

    @Volatile
    private var diskWriteWarned = false

    private const val MAX_ENTRIES = 1000

    /** Rotated generations. Single source of truth so clear() cannot drift from rotate(). */
    private const val ROTATED_1 = "app.1.log"
    private const val ROTATED_2 = "app.2.log"
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024L
    private const val MAX_TAIL_LINES = 200

    /** Hoisted: this used to be recompiled once per line, i.e. up to 1000 Regex objects per app start. */
    private val entryLineRegex = Regex("^\\[(\\d{2}:\\d{2}:\\d{2})\\]\\[(\\w+)\\] (.+)$")

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
        synchronized(lock) {
            if (logFile != null) return
            // App-scoped storage ONLY. app.log used to be mirrored automatically into
            // Downloads/WuWaConfig/logs/, which made every log line world-readable to
            // any co-installed app on API 26-29 (READ_EXTERNAL_STORAGE is a normal,
            // install-time permission there, auto-granted with no dialog) and to every
            // All-Files-Access holder on API 30+. Log lines include shell command
            // strings, the phone's wireless-ADB host:port, and paths under
            // Android/data/<game>/ — enough to fingerprint the device and to hand an
            // attacker the exact staging filename used for the world-readable
            // /data/local/tmp copies. Sharing is now EXPLICIT: the Logs screen's
            // save/export action calls saveSnapshot(), which is user-initiated.
            val base = fallbackBaseDir()
            val dir = File(base, "logs").also { it.mkdirs() }
            logFile = File(dir, "app.log")
        }
        scope.launch {
            val items = loadFromDiskAsync()
            val snapshot =
                synchronized(lock) {
                    // PREPEND the disk entries: they are the OLDER lines, and
                    // appending them put yesterday's log below today's in the viewer.
                    val next = (items + _entries.value).toMutableList()
                    if (next.size > MAX_ENTRIES) {
                        next.subList(0, next.size - MAX_ENTRIES).clear()
                    }
                    next.toList()
                }
            _entries.value = snapshot
        }
    }

    fun add(
        message: String,
        level: LogLevel = LogLevel.INFO,
    ) {
        val entry = LogEntry(message, timestamp(), level)
        // Publish a new immutable snapshot. StateFlow assignment is atomic and safe
        // from any dispatcher, so callers on Dispatchers.IO can write without the
        // Compose read-side ever observing a half-built list.
        //
        // COST NOTE: this copies the whole 1000-element list on EVERY log line, and
        // add() has a fan-in of 108 call sites, so it is one of the most-executed
        // allocations in the app. It is kept because correctness (immutable
        // snapshots) is worth more than the copy here — but if log volume ever
        // matters, this is the seam to change, e.g. by batching writes or
        // publishing the snapshot at most once per frame.
        _entries.value =
            synchronized(lock) {
                val next = (_entries.value + entry).toMutableList()
                if (next.size > MAX_ENTRIES) next.removeAt(0)
                next.toList()
            }
        appendToDisk(entry)
    }

    fun clear() {
        _entries.value = emptyList()
        scope.launch {
            diskMutex.withLock {
                try {
                    logFile?.writeText("")
                    // ALSO drop the rotated generations. clear() only truncated the
                    // current file, so up to ~10 MB of already-"cleared" lines stayed
                    // readable in app.1.log / app.2.log — including the wireless-ADB
                    // host:port, Android/data paths, and any partially-emitted
                    // decrypted log bytes. That is a false assurance to the user.
                    logFile?.parentFile?.let { dir ->
                        listOf(ROTATED_1, ROTATED_2).forEach { name ->
                            runCatching { File(dir, name).delete() }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun saveSnapshot(): File? {
        // Millisecond discriminator: two snapshots within the same second used
        // to resolve to the same filename and silently overwrite each other.
        val fileName = "WuWaConfig_${snapshotStamp()}.txt"
        var usedFallback = false
        val base = publicBaseDir() ?: fallbackBaseDir().also { usedFallback = true }
        val dir = base.also { it.mkdirs() }
        val file = File(dir, fileName)
        val content = synchronized(lock) { _entries.value.joinToString("\n") { lineFormat(it) } }
        return try {
            // writeAtomic so an interrupted export cannot leave a half-written
            // .txt the user then shares by mistake.
            // Redact credentials at the EXPORT boundary. This is the one path that
            // deliberately leaves app-private storage and lands in Downloads, where any
            // app on API 26-29 (or any All-Files-Access holder) can read it — and the
            // user is about to share it. `record_id` is a bearer credential for the
            // gacha-history endpoint and `player_id` identifies the account; neither is
            // needed to diagnose a deploy problem.
            withContext(Dispatchers.IO) { file.writeAtomic(redactCredentials(content)) }
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

    private fun snapshotStamp(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.US).format(Date())

    private fun lineFormat(entry: LogEntry): String = "[${entry.timestamp}][${entry.level.name}] ${entry.message}"

    private fun loadFromDiskAsync(): List<LogEntry> {
        try {
            val file = logFile ?: return emptyList()
            if (!file.exists()) return emptyList()
            // Ring buffer rather than readLines().takeLast(MAX_ENTRIES):
            // readLines() materialised EVERY line of a 5MB file (plus a Regex
            // per line) just to keep 1000 of them.
            val ring = ArrayDeque<String>(MAX_ENTRIES)
            file.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (ring.size == MAX_ENTRIES) ring.removeFirst()
                    ring.addLast(line)
                }
            }
            return ring.mapNotNull { parseLine(it) }
        } catch (_: Exception) {
            return emptyList()
        }
    }

    private fun parseLine(line: String): LogEntry? {
        val m = entryLineRegex.find(line) ?: return null
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
                        val warnEntry =
                            LogEntry(
                                "Disk logging unavailable (${e.message}); keeping memory-only",
                                timestamp(),
                                LogLevel.WARNING,
                            )
                        _entries.value =
                            synchronized(lock) {
                                val next = (_entries.value + warnEntry).toMutableList()
                                if (next.size > MAX_ENTRIES) next.removeAt(0)
                                next.toList()
                            }
                    }
                }
            }
        }
    }

    private fun rotate() {
        val current = logFile ?: return
        val dir = current.parentFile ?: return
        val file1 = File(dir, ROTATED_1)
        val file2 = File(dir, ROTATED_2)
        file2.delete()
        // Same-directory rename(2) is atomic and cheap — no copy-through-tmp.
        if (file1.exists() && !file1.renameTo(file2)) {
            // app.1.log is stuck (locked / read-only mount). Drop it and retry —
            // recreate an empty placeholder first, otherwise the retry renames a
            // source that no longer exists and always fails.
            file1.delete()
            if (!file1.exists()) {
                try {
                    file1.createNewFile()
                } catch (_: Exception) {
                }
            }
            if (!file1.renameTo(file2)) {
                forceShrink(current)
                return
            }
        }
        if (current.exists() && !current.renameTo(file1)) {
            forceShrink(current)
            return
        }
        try {
            current.createNewFile()
        } catch (_: Exception) {
        }
    }

    /**
     * Last-resort bound enforcement. The old rotate() simply `return`ed on a
     * rename failure, so app.log grew WITHOUT BOUND forever while every guard
     * quietly bailed out — the intended 5MB cap was violated invisibly. When
     * rotation is impossible the cap must still hold: keep the newest lines and
     * discard the rest.
     */
    private fun forceShrink(file: File) {
        try {
            val tail = file.readLines().takeLast(MAX_TAIL_LINES)
            file.writeAtomic(tail.joinToString("\n", postfix = "\n"))
        } catch (_: Exception) {
            // Unwritable AND unreadable: drop it rather than keep growing it.
            try {
                file.delete()
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Masks gacha/account credentials before a log leaves app-private storage.
 *
 * The game's Convene URL carries `player_id` and `record_id` in its fragment;
 * `record_id` is a bearer-style credential for the gacha-history endpoint. Log
 * lines can also carry `SetUserId [playerId:…]`. Neither is needed to debug a
 * config deploy, and the exported file is world-readable on older Android.
 */
internal fun redactCredentials(text: String): String =
    text
        // Kotlin string literals have no \s escape, hence the doubled backslashes.
        .replace(Regex("(?i)(record_id=)[^&\\s\"']+"), "$1<redacted>")
        .replace(Regex("(?i)(player_id=)[^&\\s\"']+"), "$1<redacted>")
        // Query-string AND JSON spellings, because a pasted log or a debug echo can
        // carry either form of the same field.
        .replace(Regex("(?i)(recordId=)[^&\\s,\"']+"), "$1<redacted>")
        .replace(Regex("(?i)(\"recordId\"\\s*:\\s*\")[^\"]*(\")"), "$1<redacted>$2")
        .replace(Regex("(?i)(\"playerId\"\\s*:\\s*\")[^\"]*(\")"), "$1<redacted>$2")
        .replace(Regex("SetUserId \\[playerId:[^\\]]*\\]"), "SetUserId [playerId:<redacted>]")

/** Test-only alias so the redactor is reachable without going through a file write. */
internal fun redactCredentialsForTest(text: String): String = redactCredentials(text)
