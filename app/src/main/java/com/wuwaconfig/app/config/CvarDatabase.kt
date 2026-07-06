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
    }

    val allCvars: Set<String> get() {
        ensureLoaded()
        return _allCvars!!
    }
    val monitoredCvars: Set<String> get() {
        ensureLoaded()
        return _monitoredCvars!!
    }
    val defaultValues: Map<String, String> get() {
        ensureLoaded()
        return _defaultValues!!
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

    fun getCvarsByCategory(cvars: Map<String, String>): Map<CvarCategory, List<Pair<String, String>>> {
        val result = mutableMapOf<CvarCategory, MutableList<Pair<String, String>>>()
        for ((key, value) in cvars) {
            val cat = categorize(key)
            result.getOrPut(cat) { mutableListOf() }.add(key to value)
        }
        return result
    }

    fun categoryCounts(cvars: Set<String>): Map<CvarCategory, Int> {
        val result = mutableMapOf<CvarCategory, Int>()
        for (key in cvars) {
            val cat = categorize(key)
            result[cat] = (result[cat] ?: 0) + 1
        }
        return result
    }

    fun optimizeIniText(text: String): String {
        ensureLoaded()
        val result =
            text.lines().map { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.isEmpty() || trimmed.startsWith("[")) {
                    return@map line
                }
                val cvarLine = trimmed.removePrefix("+CVars=").removePrefix("-CVars=").trim()
                if (cvarLine.isEmpty() || cvarLine.startsWith(";") || cvarLine.startsWith("#") || cvarLine.startsWith("//") || cvarLine.startsWith("[")) {
                    return@map line
                }
                val eq = cvarLine.indexOf('=')
                if (eq <= 0) return@map line
                val key = cvarLine.substring(0, eq).trim()
                val value = cvarLine.substring(eq + 1).trim()
                val kLower = key.lowercase()

                if (kLower in _allCvars!! && kLower in _defaultValues!! && _defaultValues!![kLower] == value) {
                    "; REDUNDANT (matches game default) — $key=$value"
                } else if (kLower !in _allCvars!!) {
                    "; UNKNOWN CVar (not in libUE4.so dump) — $key=$value"
                } else {
                    line
                }
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
