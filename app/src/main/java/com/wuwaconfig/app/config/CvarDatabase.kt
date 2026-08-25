package com.wuwaconfig.app.config

import android.content.res.AssetManager
import com.wuwaconfig.app.model.CvarCategory
import com.wuwaconfig.app.model.CvarDetail
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CvarDatabase(private val assets: AssetManager) {
    @Volatile
    private var _allCvars: Set<String>? = null

    @Volatile
    private var _monitoredCvars: Set<String>? = null

    @Volatile
    private var _defaultValues: Map<String, String>? = null

    private val loadMutex = Mutex()

    suspend fun load() =
        loadMutex.withLock {
            withContext(Dispatchers.IO) {
                if (_allCvars != null) return@withContext
                LogRepository.add("CvarDatabase: loading from assets")
                // Build everything locally and publish atomically: getters key off
                // _allCvars, so no reader can observe a half-populated database.
                val all =
                    assets.open("cvars/libUE4_cvars.txt").bufferedReader().readLines()
                        .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                val monitored =
                    assets.open("cvars/config_monitor_cvars.txt").bufferedReader().readLines()
                        .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
                val defaults =
                    assets.open("cvars/config_monitor_values.txt").bufferedReader().readLines()
                        .mapNotNull { line ->
                            val trimmed = line.trim()
                            if (trimmed.isBlank()) return@mapNotNull null
                            val eq = trimmed.indexOf('=')
                            if (eq <= 0) return@mapNotNull null
                            trimmed.substring(0, eq).trim().lowercase() to trimmed.substring(eq + 1).trim()
                        }.toMap()
                _monitoredCvars = monitored
                _defaultValues = defaults
                _allCvars = all
                LogRepository.add(
                    "CvarDatabase: loaded ${all.size} CVars, ${monitored.size} monitored, ${defaults.size} defaults",
                    LogLevel.SUCCESS,
                )
            }
        }

    private fun ensureLoaded() {
        if (_allCvars != null) return
        // Avoid blocking the caller (e.g. the main thread). The async load() invoked at
        // Application.onCreate populates these; the mutex makes concurrent triggers
        // single-flight so assets are never read twice. Until it lands, optimizers skip
        // safely instead of doing a synchronous asset read here.
        LogRepository.add("CvarDatabase: not loaded yet; triggering async load", LogLevel.WARNING)
        CoroutineScope(Dispatchers.IO).launch { load() }
    }

    val allCvars: Set<String> get() {
        ensureLoaded()
        return _allCvars ?: emptySet()
    }
    val monitoredCvars: Set<String> get() {
        ensureLoaded()
        return _monitoredCvars ?: emptySet()
    }
    val defaultValues: Map<String, String> get() {
        ensureLoaded()
        return _defaultValues ?: emptyMap()
    }

    fun isKnown(key: String): Boolean = key.lowercase() in allCvars

    fun isMonitored(key: String): Boolean = key.lowercase() in monitoredCvars

    fun gameDefault(key: String): String? = defaultValues[key.lowercase()]

    fun differsFromDefault(
        key: String,
        value: String,
    ): Boolean = gameDefault(key)?.let { it != value } ?: true

    fun categorize(key: String): CvarCategory = CvarCategorizer.categorize(key)

    fun detailFor(
        key: String,
        value: String,
    ): CvarDetail {
        val k = key.lowercase()
        val gd = defaultValues[k]
        return CvarDetail(
            isKnown = k in allCvars,
            isMonitored = k in monitoredCvars,
            gameDefault = gd,
            matchesDefault = gd != null && gd == value,
            category = categorize(key),
        )
    }

    fun optimizeIniText(text: String): String {
        val all = _allCvars ?: return text
        val monitored = _monitoredCvars ?: emptySet()
        val defaults = _defaultValues ?: emptyMap()
        return optimizeIniTextImpl(text, all, monitored, defaults)
    }

    fun buildCvarDetails(
        cvars: Set<String>,
        cvarValues: Map<String, String>,
    ): Map<String, CvarDetail> {
        ensureLoaded()
        return cvars.associateWith { key ->
            val k = key.lowercase()
            val gd = defaultValues[k]
            CvarDetail(
                isKnown = k in allCvars,
                isMonitored = k in monitoredCvars,
                gameDefault = gd,
                matchesDefault = gd != null && cvarValues[k]?.let { gd == it } == true,
                category = categorize(key),
            )
        }
    }

    fun extractCvarValues(iniText: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in iniText.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("[") || trimmed.isEmpty()) continue
            val cvarLine = trimmed.removePrefix("+CVars=").removePrefix("-CVars=").trim()
            if (cvarLine.isEmpty() || cvarLine.startsWith(";") || cvarLine.startsWith("#") || cvarLine.startsWith("//") || cvarLine.startsWith("[")) continue
            val eq = cvarLine.indexOf('=')
            if (eq <= 0) continue
            val key = cvarLine.substring(0, eq).trim()
            val value = cvarLine.substring(eq + 1).trim()
            if (key.isNotEmpty()) result[key] = value
        }
        return result
    }
}

internal fun optimizeIniTextImpl(
    text: String,
    allCvars: Set<String>,
    monitoredCvars: Set<String>,
    defaultValues: Map<String, String>,
): String {
    val out = mutableListOf<String>()
    for (line in text.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() ||
            trimmed.startsWith(";") ||
            trimmed.startsWith("#") ||
            trimmed.startsWith("//") ||
            trimmed.startsWith("[")
        ) {
            out.add(line)
            continue
        }
        // A removal directive (`-CVars=...`, case-insensitive) is never "unknown" — keep it verbatim.
        if (trimmed.startsWith("-CVars=", ignoreCase = true)) {
            out.add(line)
            continue
        }
        val cvarLine = trimmed.removePrefix("+CVars=").trim()
        if (cvarLine.isEmpty() ||
            cvarLine.startsWith(";") ||
            cvarLine.startsWith("#") ||
            cvarLine.startsWith("//") ||
            cvarLine.startsWith("[")
        ) {
            out.add(line)
            continue
        }
        val eq = cvarLine.indexOf('=')
        if (eq <= 0) {
            out.add(line)
            continue
        }
        val key = cvarLine.substring(0, eq).trim()
        // Strip an inline trailing comment so redundant/default comparisons see the
        // real value (UE INI treats ';' as a comment marker, never a value char).
        val rawValue = cvarLine.substring(eq + 1).trim()
        val value = rawValue.substringBefore(';').trim()
        val k = key.lowercase()
        val reason =
            when {
                k !in allCvars -> "unknown CVar"
                k in monitoredCvars && defaultValues[k] == value ->
                    "redundant (matches default ${defaultValues[k]})"
                else -> null
            }
        if (reason != null) {
            out.add(";$line ; [CvarDB] $reason")
        } else {
            out.add(line)
        }
    }
    return out.joinToString("\n")
}
