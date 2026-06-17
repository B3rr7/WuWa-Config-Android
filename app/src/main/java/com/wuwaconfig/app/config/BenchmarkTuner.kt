package com.wuwaconfig.app.config

import android.util.Log
import com.wuwaconfig.app.model.GeneratorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

data class BenchmarkResult(
    val avgFps: Float,
    val minFps: Float,
    val frameTimeMs: Float,
    val stabilityPct: Float
)

data class TunerProgress(
    val round: Int,
    val preset: String,
    val avgFps: Float,
    val minFps: Float,
    val targetFps: Int,
    val stage: String
)

object BenchmarkTuner {
    private const val TAG = "BenchmarkTuner"
    private const val CAPTURE_DURATION_MS = 20000L
    private val FPS_LINE_PATTERN = """(?:FPS|fps|frame.*?rate|avg\s*fps)[:\s]*(\d+\.?\d*)""".toRegex()

    val PRESET_ORDER = listOf("ultra", "high", "balanced", "performance")

    suspend fun captureFps(onProgress: (String) -> Unit = {}): Result<BenchmarkResult> =
        withContext(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder("logcat", "-d", "-v", "brief", "-t", "500")
                pb.redirectErrorStream(true)
                val process = pb.start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val fpsValues = mutableListOf<Float>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val match = FPS_LINE_PATTERN.find(line ?: "")
                    match?.let { m ->
                        m.groupValues[1].toFloatOrNull()?.let { fpsValues.add(it) }
                    }
                }
                process.waitFor()
                reader.close()

                if (fpsValues.isEmpty()) {
                    return@withContext Result.failure(Exception("No FPS data found in logcat"))
                }

                val avg = fpsValues.average().toFloat()
                val min = fpsValues.min()
                val stable = fpsValues.count { it >= avg * 0.8f }.toFloat() / fpsValues.size * 100f
                Result.success(
                    BenchmarkResult(
                        avgFps = avg, minFps = min,
                        frameTimeMs = if (avg > 0) 1000f / avg else 0f,
                        stabilityPct = stable
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "captureFps failed: ${e.message}")
                Result.failure(e)
            }
        }

    fun pickPresetForFps(currentPreset: String, avgFps: Float, targetFps: Int): String {
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

    fun adjustOptionsForFps(current: GeneratorOptions, avgFps: Float, targetFps: Int): GeneratorOptions {
        if (avgFps >= targetFps) return current
        val gap = targetFps - avgFps
        var adjusted = current
        if (gap > 15 && !adjusted.disableSSR) adjusted = adjusted.copy(disableSSR = true)
        else if (gap > 10 && !adjusted.disableBloom) adjusted = adjusted.copy(disableBloom = true)
        else if (gap > 8 && !adjusted.disableRadialBlur) adjusted = adjusted.copy(disableRadialBlur = true)
        else if (gap > 5) adjusted = adjusted.copy(shadowOverride = 1)
        return adjusted
    }
}
