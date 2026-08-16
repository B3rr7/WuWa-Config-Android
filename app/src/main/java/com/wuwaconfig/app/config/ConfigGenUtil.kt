package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.CvarEntry
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.model.LogInfo

private val CVAR_PREFIXES =
    listOf(
        "a.", "bbm.", "compat.", "cook.", "fx.", "foliage.", "gc.", "grass.",
        "kuro.", "lod.", "n.", "niagara.", "r.", "s.", "sg.", "slate.",
        "t.", "tick.", "vr.", "wp.",
    )

fun extractCvarNames(iniText: String): Set<String> {
    val names = linkedSetOf<String>()
    for (line in iniText.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#") ||
            trimmed.startsWith("//") || trimmed.startsWith("[")
        ) {
            continue
        }

        val kv = trimmed.removePrefix("+CVars=").removePrefix("-CVars=").trim()
        val eq = kv.indexOf('=')
        if (eq <= 0) continue
        val key = kv.substring(0, eq).trim()
        val keyLower = key.lowercase()
        if (CVAR_PREFIXES.any { keyLower.startsWith(it) }) names.add(key)
    }
    return names
}

fun replaceCoreSystemPaths(
    engineIni: String,
    corePaths: List<String>,
): String {
    val lines = engineIni.lines()
    val result = mutableListOf<String>()
    var i = 0
    var replaced = false

    while (i < lines.size) {
        val trimmed = lines[i].trim()
        if (trimmed.equals("[Core.System]", ignoreCase = true)) {
            result.addAll(corePaths)
            replaced = true
            i++
            while (i < lines.size && !lines[i].trim().startsWith("[")) i++
            continue
        }
        result.add(lines[i])
        i++
    }

    if (!replaced) {
        val insertAt =
            result.indexOfFirst { it.trim().startsWith("[SystemSettings]", ignoreCase = true) }
                .let { if (it >= 0) it else 0 }
        result.addAll(insertAt, corePaths + "")
    }
    return result.joinToString("\n")
}

fun extractCoreSystemPaths(
    engineIni: String?,
    defaultCoreSystem: List<String>,
): List<String> {
    if (engineIni == null) return defaultCoreSystem
    val lines = engineIni.lines()
    val inCore = lines.indexOfFirst { it.trim().equals("[Core.System]", ignoreCase = true) }
    if (inCore == -1) return defaultCoreSystem
    val paths = mutableListOf("[Core.System]")
    for (i in (inCore + 1) until lines.size) {
        val line = lines[i]
        if (line.isBlank()) continue
        if (line.trim().startsWith("[")) break
        if (line.trim().startsWith("Paths=", ignoreCase = true)) paths.add(line.trimEnd())
    }
    return if (paths.size > 1) paths else defaultCoreSystem
}

fun mergeWithLogCvars(
    generatedIni: String,
    logCvars: Map<String, String>,
    opts: GeneratorOptions,
): String {
    val generatedKeys = extractCvarNames(generatedIni).map { it.lowercase() }.toSet()
    val logLines = mutableListOf<String>()
    for ((key, value) in logCvars) {
        val kl = key.lowercase()
        val isMergeable =
            kl.startsWith("sg.") || kl.startsWith("r.") || kl.startsWith("fx.") ||
                kl.startsWith("foliage.") || kl.startsWith("grass.") || kl.startsWith("a.") ||
                kl.startsWith("niagara.") || kl.startsWith("kuro.") || kl.startsWith("lod.") ||
                kl.startsWith("s.") || kl.startsWith("gc.") || kl.startsWith("compat.") ||
                kl.startsWith("cook.")
        if (isMergeable) {
            if (kl !in generatedKeys) {
                logLines.add("$key=$value")
            }
        }
    }
    if (logLines.isEmpty()) return generatedIni
    val lines = generatedIni.lines().toMutableList()
    val ssIdx = lines.indexOfLast { it.trim().equals("[SystemSettings]", ignoreCase = true) }
    val insertIdx =
        if (ssIdx >= 0) {
            var after = ssIdx + 1
            while (after < lines.size && lines[after].isBlank()) after++
            after
        } else {
            lines.size
        }
    lines.addAll(insertIdx, listOf("", "; ── IMPORTED FROM Client.log (not in preset) ─────") + logLines + listOf(""))
    return deduplicateIniText(lines.joinToString("\n"))
}

fun deduplicateIniText(text: String): String {
    val lines = text.lines()
    val seen = mutableMapOf<String, Int>()
    val toRemove = mutableSetOf<Int>()
    for ((i, line) in lines.withIndex()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("[")) continue
        val cvarLine = trimmed.removePrefix("+CVars=").removePrefix("-CVars=").trim()
        if (cvarLine.isEmpty() || cvarLine.startsWith(";") || cvarLine.startsWith("#") || cvarLine.startsWith("//") || cvarLine.startsWith("[")) continue
        val eq = cvarLine.indexOf('=')
        if (eq <= 0) continue
        val key = cvarLine.substring(0, eq).trim().lowercase()
        if (!CVAR_PREFIXES.any { key.startsWith(it) }) continue
        val prev = seen[key]
        if (prev != null) toRemove.add(prev)
        seen[key] = i
    }
    if (toRemove.isEmpty()) return text
    return lines.filterIndexed { i, _ -> i !in toRemove }.joinToString("\n")
}

fun parseResolution(res: String?): Pair<Int, Int>? {
    if (res.isNullOrBlank()) return null
    val parts = res.trim().split(Regex("\\s*[xX*]\\s*"))
    val w = parts.firstOrNull()?.toIntOrNull() ?: return null
    val h = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (w <= 0 || h <= 0) return null
    return w to h
}

fun parseCvarEntries(
    engineIni: String,
    logInfo: LogInfo = LogInfo(),
): List<CvarEntry> {
    val entries = mutableListOf<CvarEntry>()
    var currentCategory = ""
    for (line in engineIni.lines()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("[")) continue
        if (trimmed.startsWith(";")) {
            val cat = trimmed.removePrefix(";").trim().removePrefix("──").trim().removeSuffix("──").trim()
            if (cat.isNotEmpty() && !cat.startsWith("═")) currentCategory = cat
            continue
        }
        val eq = trimmed.indexOf('=')
        if (eq > 0) {
            val key = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (key.isNotEmpty() && !key.startsWith("+")) {
                entries.add(CvarEntry(key = key, value = value, category = currentCategory))
            }
        }
    }
    return entries
}

fun applyCvarOverrides(
    text: String,
    overrides: Map<String, String>,
): String {
    if (overrides.isEmpty()) return text
    val lines = text.lines().toMutableList()
    for ((key, newValue) in overrides) {
        val idx =
            lines.indexOfFirst { line ->
                val trimmed = line.trim()
                val eq = trimmed.indexOf('=')
                eq > 0 && trimmed.substring(0, eq).trim() == key
            }
        if (idx >= 0) {
            val raw = lines[idx]
            val trimmed = raw.trim()
            val eq = trimmed.indexOf('=')
            val existingVal = trimmed.substring(eq + 1).trim()
            if (existingVal != newValue) {
                val rawEq = raw.indexOf('=')
                lines[idx] = raw.substring(0, rawEq + 1) + newValue
            }
        }
    }
    return lines.joinToString("\n")
}
