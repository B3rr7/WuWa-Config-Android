package com.wuwaconfig.app.config

import android.util.Log
import com.google.gson.Gson
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.model.GeneratorOptions
import java.io.File

enum class TunerStage { IDLE, DEPLOYING, WAITING_FOR_PLAY, CAPTURING, COMPLETE }

data class RoundResult(
    val round: Int,
    val preset: String,
    val avgFps: Float,
    val minFps: Float,
    val stabilityPct: Float,
)

data class TunerState(
    val stage: TunerStage = TunerStage.IDLE,
    val round: Int = 1,
    val preset: String = "balanced",
    val options: GeneratorOptions = GeneratorOptions(),
    val targetFps: Int = 60,
    val results: List<RoundResult> = emptyList(),
    val finalPreset: String? = null,
    val error: String? = null,
)

data class BenchmarkResult(
    val avgFps: Float,
    val minFps: Float,
    val frameTimeMs: Float,
    val stabilityPct: Float,
)

object BenchmarkTuner {
    private const val TAG = "BenchmarkTuner"
    private const val STATE_FILE = "benchmark_tuner_state.json"
    private val FPS_LINE_PATTERN = """(?:FPS|fps|frame.*?rate|avg\s*fps)[:\s]*(\d+\.?\d*)""".toRegex()

    val PRESET_ORDER = listOf("ultra", "high", "balanced", "performance", "potato")

    private val gson = Gson()

    fun loadState(): TunerState? {
        return try {
            val file = File(WuWaConfigApp.instance.filesDir, STATE_FILE)
            if (!file.exists()) return null
            gson.fromJson(file.readText(), TunerState::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tuner state", e)
            null
        }
    }

    fun saveState(state: TunerState) {
        try {
            File(WuWaConfigApp.instance.filesDir, STATE_FILE).writeText(gson.toJson(state))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save tuner state", e)
        }
    }

    fun clearState() {
        try {
            File(WuWaConfigApp.instance.filesDir, STATE_FILE).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear tuner state", e)
        }
    }

    fun parseFpsLogcat(logcatText: String): Result<BenchmarkResult> {
        val fpsValues = mutableListOf<Float>()
        for (line in logcatText.lines()) {
            val match = FPS_LINE_PATTERN.find(line)
            match?.let { m ->
                m.groupValues[1].toFloatOrNull()?.let { fpsValues.add(it) }
            }
        }
        if (fpsValues.isEmpty()) {
            return Result.failure(Exception("No FPS data found in logcat"))
        }
        val avg = fpsValues.average().toFloat()
        val min = fpsValues.min()
        val stable = fpsValues.count { it >= avg * 0.8f }.toFloat() / fpsValues.size * 100f
        return Result.success(
            BenchmarkResult(
                avgFps = avg,
                minFps = min,
                frameTimeMs = if (avg > 0) 1000f / avg else 0f,
                stabilityPct = stable,
            ),
        )
    }

    fun pickPresetForFps(
        currentPreset: String,
        avgFps: Float,
        targetFps: Int,
    ): String {
        if (targetFps <= 0) return currentPreset
        val idx = PRESET_ORDER.indexOf(currentPreset)
        if (avgFps >= targetFps && idx > 0) {
            val stepUp = (avgFps - targetFps) / targetFps
            return if (stepUp > 0.15f && idx > 0) PRESET_ORDER[idx - 1] else currentPreset
        }
        if (avgFps < targetFps * 0.85f && idx < PRESET_ORDER.lastIndex) {
            return PRESET_ORDER[idx + 1]
        }
        return currentPreset
    }

    fun adjustOptionsForFps(
        current: GeneratorOptions,
        avgFps: Float,
        targetFps: Int,
    ): GeneratorOptions {
        if (avgFps >= targetFps) return current
        val gap = targetFps - avgFps
        var adjusted = current
        if (gap > 15 && !adjusted.disableSSR) {
            adjusted = adjusted.copy(disableSSR = true)
        } else if (gap > 10 && !adjusted.disableBloom) {
            adjusted = adjusted.copy(disableBloom = true)
        } else if (gap > 8 && !adjusted.disableRadialBlur) {
            adjusted = adjusted.copy(disableRadialBlur = true)
        } else if (gap > 5) {
            adjusted = adjusted.copy(shadowOverride = 1)
        }
        return adjusted
    }
}
