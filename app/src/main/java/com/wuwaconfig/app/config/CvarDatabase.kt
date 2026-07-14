package com.wuwaconfig.app.config

import android.content.res.AssetManager
import com.wuwaconfig.app.model.CvarCategory
import com.wuwaconfig.app.model.CvarDetail
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CvarDatabase(private val assets: AssetManager) {
    @Volatile
    private var _allCvars: Set<String>? = null

    @Volatile
    private var _monitoredCvars: Set<String>? = null

    @Volatile
    private var _defaultValues: Map<String, String>? = null

    suspend fun load() =
        withContext(Dispatchers.IO) {
            if (_allCvars != null) return@withContext
            LogRepository.add("CvarDatabase: loading from assets")
            _allCvars =
                assets.open("cvars/libUE4_cvars.txt").bufferedReader().readLines()
                    .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            _monitoredCvars =
                assets.open("cvars/config_monitor_cvars.txt").bufferedReader().readLines()
                    .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            _defaultValues =
                assets.open("cvars/config_monitor_values.txt").bufferedReader().readLines()
                    .mapNotNull { line ->
                        val trimmed = line.trim()
                        if (trimmed.isBlank()) return@mapNotNull null
                        val eq = trimmed.indexOf('=')
                        if (eq <= 0) return@mapNotNull null
                        trimmed.substring(0, eq).trim().lowercase() to trimmed.substring(eq + 1).trim()
                    }.toMap()
            LogRepository.add(
                "CvarDatabase: loaded ${_allCvars!!.size} CVars, ${_monitoredCvars!!.size} monitored, ${_defaultValues!!.size} defaults",
                LogLevel.SUCCESS,
            )
        }

    private fun ensureLoaded() {
        if (_allCvars != null) return
        LogRepository.add("CvarDatabase: loading from assets (synchronous fallback)")
        try {
            _allCvars =
                assets.open("cvars/libUE4_cvars.txt").bufferedReader().readLines()
                    .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            _monitoredCvars =
                assets.open("cvars/config_monitor_cvars.txt").bufferedReader().readLines()
                    .map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            _defaultValues =
                assets.open("cvars/config_monitor_values.txt").bufferedReader().readLines()
                    .mapNotNull { line ->
                        val trimmed = line.trim()
                        if (trimmed.isBlank()) return@mapNotNull null
                        val eq = trimmed.indexOf('=')
                        if (eq <= 0) return@mapNotNull null
                        trimmed.substring(0, eq).trim().lowercase() to trimmed.substring(eq + 1).trim()
                    }.toMap()
        } catch (e: Exception) {
            LogRepository.add("CvarDatabase: ASSETS LOAD FAILED: ${e.message}", LogLevel.ERROR)
            _allCvars = emptySet()
            _monitoredCvars = emptySet()
            _defaultValues = emptyMap()
        }
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
        ensureLoaded()
        val result =
            text.lines().mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.isEmpty() || trimmed.startsWith("[")) {
                    return@mapNotNull line
                }
                val cvarLine = trimmed.removePrefix("+CVars=").removePrefix("-CVars=").trim()
                if (cvarLine.isEmpty() || cvarLine.startsWith(";") || cvarLine.startsWith("#") || cvarLine.startsWith("//") || cvarLine.startsWith("[")) {
                    return@mapNotNull line
                }
                val eq = cvarLine.indexOf('=')
                if (eq <= 0) return@mapNotNull line
                // Keep every CVar line intact: do not annotate or drop default-matching
                // entries, so the generated config stays explicit and complete. (The old
                // "; REDUNDANT (matches game default)" / "; UNKNOWN CVar" comments were
                // removed at the user's request; dropping the lines themselves just made
                // the config shorter without benefit.)
                line
            }
        return result.joinToString("\n")
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
                matchesDefault = gd != null && cvarValues[key]?.let { gd == it } == true,
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
