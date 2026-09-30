package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.CvarEntry
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository

internal val CVAR_PREFIXES =
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
): String {
    val generatedKeys = extractCvarNames(generatedIni).map { it.lowercase() }.toSet()
    val logLines = mutableListOf<String>()
    for ((key, value) in logCvars) {
        val kl = key.lowercase()
        // Same prefix family as the generator emits — keeps merge scope in lockstep
        // with extract/dedup instead of a drifted inline copy.
        if (CVAR_PREFIXES.any { kl.startsWith(it) } && kl !in generatedKeys) {
            logLines.add("$key=$value")
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
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            // Keys are scoped to their section: an r.* CVar legitimately emitted
            // under two different sections must not delete the earlier occurrence.
            seen.clear()
            continue
        }
        if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("//")) continue
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

private val RESOLUTION_SPLIT_REGEX = Regex("\\s*[xX*]\\s*")

/** Marker tag on a CVar line that the engine cannot use on this target. */
const val DEAD_CVAR_MARKER = "WuWaConfig:DEAD"

/**
 * Result of the platform-availability pass over the generated INIs.
 */
data class DeadCvarPassResult(
    val platform: TargetPlatform,
    /** Per-file count of CVars marked dead. */
    val markedByFile: Map<String, Int>,
    val totalMarked: Int,
    val reasons: Set<String>,
) {
    /** One-line summary for the log, so the active mode is never ambiguous. */
    fun summary(): String =
        if (platform == TargetPlatform.UNKNOWN) {
            "Platform UNKNOWN (no usable log) — no CVar marked dead"
        } else if (totalMarked == 0) {
            "Platform $platform — no CVar marked dead"
        } else {
            "Platform $platform — $totalMarked CVar(s) marked dead " +
                "(${markedByFile.entries.joinToString { "${it.key}:${it.value}" }}) " +
                "[${reasons.joinToString("; ")}]"
        }
}

/**
 * Comments out CVars that the target platform can never use, without deleting them.
 *
 * The line is emitted as `;r.Foo=1 ; [WuWaConfig:DEAD] reason`. UE4 treats a leading
 * `;` as a comment, so the CVar goes inert — which is a no-op, because the engine was
 * already ignoring it — while the text and the value stay visible and re-enablable by
 * deleting one character. This mirrors the marker shape CvarDatabase already emits
 * (`;$line ; [CvarDB] $reason`), so both passes read the same way in the file.
 *
 * The verdict is recomputed from scratch on every call — nothing is persisted, so
 * there is no sticky "dead" state to clear. If the detected platform changes (e.g.
 * the user turns on Vulkan in the game, and the log then reports Vulkan instead of
 * OpenGL ES), the `r.Vulkan.*` family comes back automatically on the next generate.
 *
 * Only lines that pass [CVAR_PREFIXES] and are not already commented are touched, so
 * `[Core.System] Paths=` lines and section headers are left alone.
 */
fun markPlatformDeadCvars(
    iniText: String,
    platform: TargetPlatform,
    fileLabel: String,
    accumulator: MutableMap<String, Int> = mutableMapOf(),
    reasonSink: MutableSet<String> = mutableSetOf(),
    engine: EngineGeneration = EngineGeneration.UE4,
): String {
    // Nothing to judge: fail safe rather than guess.
    if (platform == TargetPlatform.UNKNOWN) return iniText
    val out = iniText.lines().toMutableList()
    for (i in out.indices) {
        val raw = out[i]
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) continue
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) continue
        if (trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("//")) continue

        val cvarLine = stripCvarsDirective(trimmed)
        val eq = cvarLine.indexOf('=')
        if (eq <= 0) continue
        val key = cvarLine.substring(0, eq).trim()
        val keyLower = key.lowercase()
        if (!CVAR_PREFIXES.any { keyLower.startsWith(it) }) continue

        val verdict = classifyCvar(key, platform, engine)
        if (verdict !is CvarVerdict.Dead) continue

        val indent = raw.substring(0, raw.length - raw.trimStart().length)
        // Use `trimmed`, not `raw`: `raw` still carries the original indentation, so
        // interpolating it after the ';' produced "    ;    +CVars=..." and put the
        // indent inside the comment. UE4 only treats a line as a comment when ';' is
        // its FIRST character.
        out[i] = "$indent;$trimmed ; [$DEAD_CVAR_MARKER] ${verdict.reason} (${verdict.scope})"
        accumulator.merge(fileLabel, 1, Int::plus)
        reasonSink.add(verdict.reason)
    }
    return out.joinToString("\n")
}

/**
 * Removes a leading `+CVars=` / `-CVars=` directive prefix, case-insensitively
 * (the game's INIs are not consistent about the directive's casing — see
 * `CvarDatabase.optimizeIniTextImpl`, which matches it with ignoreCase = true).
 */
private fun stripCvarsDirective(line: String): String {
    if (line.startsWith("+CVars=", ignoreCase = true)) return line.substring("+CVars=".length).trim()
    if (line.startsWith("-CVars=", ignoreCase = true)) return line.substring("-CVars=".length).trim()
    return line
}

fun parseResolution(res: String?): Pair<Int, Int>? {
    if (res.isNullOrBlank()) return null
    val parts = res.trim().split(RESOLUTION_SPLIT_REGEX)
    val w = parts.firstOrNull()?.toIntOrNull() ?: return null
    val h = parts.getOrNull(1)?.toIntOrNull() ?: return null
    if (w <= 0 || h <= 0) return null
    return w to h
}

fun parseCvarEntries(engineIni: String): List<CvarEntry> {
    val entries = mutableListOf<CvarEntry>()
    for (line in engineIni.lines()) {
        val trimmed = line.trim()
        // Mirror the skip-set of extractCvarNames/deduplicateIniText: section headers,
        // all three comment styles, and the +CVars= / -CVars= directive lines. Without
        // this the parser emitted bogus entries such as CvarEntry(key="# foo", ...) and
        // CvarEntry(key="-CVars=r.Foo", value="").
        if (trimmed.isEmpty() || trimmed.startsWith("[") || trimmed.startsWith(";")) continue
        if (trimmed.startsWith("#") || trimmed.startsWith("//")) continue
        val body = stripCvarsDirective(trimmed)
        if (body.isEmpty() || body.startsWith(";") || body.startsWith("#") || body.startsWith("//") || body.startsWith("[")) continue
        val eq = body.indexOf('=')
        if (eq > 0) {
            val key = body.substring(0, eq).trim()
            val value = body.substring(eq + 1).trim()
            if (key.isNotEmpty() && !key.startsWith("+")) {
                entries.add(CvarEntry(key = key, value = value))
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
    // Every index per key — deduplicateIniText keeps the LAST occurrence, so an
    // override applied only to the first occurrence was silently discarded for
    // any cvar emitted more than once (~20 keys via perf-tweaks/ToA/thermal).
    // Keys are lowercased on BOTH sides: the generator emits mixed case
    // (r.Kuro.AutoExposure) while extractCvarNames / deduplicateIniText and the
    // override keys themselves are lowercase, so a case-sensitive index made
    // every stored override vanish silently.
    val indicesByKey = mutableMapOf<String, MutableList<Int>>()
    for (i in lines.indices) {
        val trimmed = lines[i].trim()
        val eq = trimmed.indexOf('=')
        if (eq > 0) {
            val key = trimmed.substring(0, eq).trim().lowercase()
            indicesByKey.getOrPut(key) { mutableListOf() }.add(i)
        }
    }
    for ((key, newValue) in overrides) {
        val idxs = indicesByKey[key.trim().lowercase()]
        if (idxs == null) {
            LogRepository.add("ConfigGenUtil: override key '$key' not present in INI — ignored", LogLevel.WARNING)
            continue
        }
        for (idx in idxs) {
            val raw = lines[idx]
            val rawEq = raw.indexOf('=')
            val existingVal = raw.substring(rawEq + 1).trim()
            if (existingVal != newValue) {
                lines[idx] = raw.substring(0, rawEq + 1) + newValue
            }
        }
    }
    return lines.joinToString("\n")
}
