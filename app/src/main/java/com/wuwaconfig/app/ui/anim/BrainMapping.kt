package com.wuwaconfig.app.ui.anim

/*
 * Maps a parsed LogInfo (model/) onto the lines the brain animation reads.
 *
 * Pure, JVM-testable logic: no Compose, no android.jar. `LogEntry` is taken
 * by model.LogRepository (log messages), so the animation's line type is
 * BrainLine — this is the only place the two formats meet. The category enum
 * keeps its `LogCategory` name; no collision exists for it. Ram strings reuse
 * the production `formatRam` (Components.kt) rather than a private twin.
 */

import com.wuwaconfig.app.model.LogInfo
import com.wuwaconfig.app.ui.components.formatRam

private const val MAX_LINES = 10

/**
 * One line per extracted signal, `KEY=value` shaped for the file laser scan
 * and the category cards. CVars last (the card counts against [LogInfo]
 * activeCvars size). Smoothed over real data: a missing field simply shortens
 * the read. Capped at 10 — BrainSim's file holds N=10 rows.
 */
fun LogInfo.toBrainLines(): List<BrainLine> {
    val lines = ArrayList<BrainLine>(7)
    val device = deviceModel ?: cpuName
    if (device != null) lines.add(BrainLine("DEVICE=$device", LogCategory.DEVICE))
    socName?.let { lines.add(BrainLine("SoC=$it", LogCategory.DEVICE)) }
    gpu?.let { lines.add(BrainLine("GPU=$it", LogCategory.GPU)) }
    (gameApi ?: api)?.let { lines.add(BrainLine("API=$it", LogCategory.API)) }
    ramMb?.let { lines.add(BrainLine("RAM=${formatRam(it)}", LogCategory.MEMORY)) }
    androidVersion?.let { lines.add(BrainLine("OS=Android $it", LogCategory.OS)) }
    return if (lines.size < MAX_LINES) {
        lines + BrainLine("CVARS=${activeCvars.size} active", LogCategory.CVARS)
    } else {
        lines.take(MAX_LINES - 1) + BrainLine("CVARS=${activeCvars.size} active", LogCategory.CVARS)
    }
}
