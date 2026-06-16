package com.wuwaconfig.app.config

data class BrainRecommendation(
    val preset: String,
    val confidence: Int,
    val score: Int,
    val signals: List<String>,
    val warnings: List<String>
)

object SmartBrain {
    private val KNOWN_FORBIDDEN = mapOf(
        "r.FidelityFX.FSR.RCAS.Enabled" to "FSR RCAS causes instability on mobile",
        "r.TemporalAA.Sharpness" to "Can cause shimmering/artifacts",
        "r.Mobile.SSAO" to "SSAO causes GPU overhead on mobile",
        "r.Mobile.EnableVoidGT" to "VoidGT causes driver issues",
        "r.DefaultFeature.LensFlare" to "Lens flare can cause performance drops"
    )

    fun getGPUTier(gpu: String?): String {
        if (gpu == null) return "unknown"
        val g = gpu.lowercase()
        return when {
            Regex("""adreno.*8[3-9]\d|adreno.*8[12]\d""", RegexOption.IGNORE_CASE).containsMatchIn(g) -> "flagship"
            Regex("""adreno.*7[5-9]\d|adreno.*8[0]\d""", RegexOption.IGNORE_CASE).containsMatchIn(g) -> "high"
            Regex("""adreno.*7[0-4]\d|adreno.*6[5-9]\d""", RegexOption.IGNORE_CASE).containsMatchIn(g) -> "mid_high"
            Regex("""adreno.*6[0-4]\d|mali-g[6-7]\d\d|mali-g615""", RegexOption.IGNORE_CASE).containsMatchIn(g) -> "mid"
            Regex("""adreno.*5\d\d|mali-g5[0-9]\d|mali-g57""", RegexOption.IGNORE_CASE).containsMatchIn(g) -> "mid_low"
            Regex("""adreno.*[34]\d\d|mali-g[34]""", RegexOption.IGNORE_CASE).containsMatchIn(g) -> "low"
            else -> "unknown"
        }
    }

    fun getGPUFamily(gpu: String?): String? {
        if (gpu == null) return null
        val g = gpu.lowercase()
        val adreno = Regex("""adreno\s*(?:\s*\(tm\))?\s*(\d)""", RegexOption.IGNORE_CASE).find(g)
        if (adreno != null) return "Adreno_${adreno.groupValues[1]}xx"
        val mali = Regex("""mali-g(\d)""", RegexOption.IGNORE_CASE).find(g)
        if (mali != null) return "Mali_G${mali.groupValues[1]}xx"
        if (g.contains("xclipse")) return "Xclipse"
        if (g.contains("power")) return "PowerVR"
        return null
    }

    fun scoreRecommendation(info: LogInfo): BrainRecommendation {
        var score = 50
        val signals = mutableListOf<String>()

        val tier = getGPUTier(info.gpu)
        when (tier) {
            "flagship" -> { score += 30; signals.add("Flagship GPU: +30") }
            "high" -> { score += 20; signals.add("High-end GPU: +20") }
            "mid_high" -> { score += 10; signals.add("Mid-high GPU: +10") }
            "mid" -> { /* +0 */ }
            "mid_low" -> { score -= 10; signals.add("Low-mid GPU: -10") }
            else -> { score -= 20; signals.add("Low-end GPU: -20") }
        }

        val ram = info.ramMb ?: 0
        when {
            ram >= 8000 -> { score += 8; signals.add("8GB+ RAM: +8") }
            ram >= 6000 -> { score += 5; signals.add("6GB+ RAM: +5") }
            ram in 4000..5999 -> { /* +0 */ }
            ram in 1..3999 -> { score -= 15; signals.add("<4GB RAM: -15") }
        }

        if (info.vulkanStatus == "available") { score += 8; signals.add("Vulkan: +8") }

            info.fpsActual?.let { actual ->
                info.fpsCap?.let { cap ->
                    if (cap > 0) {
                        val dropPct = ((cap - actual) / cap) * 100
                        when {
                            dropPct > 30 -> { score -= 18; signals.add("FPS drop >30%: -18") }
                            dropPct > 20 -> { score -= 12; signals.add("FPS drop 20-30%: -12") }
                            dropPct > 10 -> { score -= 6; signals.add("FPS drop 10-20%: -6") }
                            actual >= cap * 0.95f -> { score += 5; signals.add("FPS at target: +5") }
                        }
                    }
                }
                if (actual < 30) { score -= 15; signals.add("FPS <30: -15") }
                if (actual in 30f..44f) { score -= 8; signals.add("FPS 30-45: -8") }
            }

        when {
            info.thermalEvents >= 5 -> { score -= 20; signals.add("Thermal throttling x${info.thermalEvents}: -20") }
            info.thermalEvents >= 3 -> { score -= 12; signals.add("Thermal events x${info.thermalEvents}: -12") }
            info.thermalEvents >= 1 -> { score -= 5; signals.add("Thermal events x${info.thermalEvents}: -5") }
        }
        when {
            info.gpuOom >= 3 -> { score -= 30; signals.add("GPU OOM x${info.gpuOom}: -30") }
            info.gpuOom >= 2 -> { score -= 20; signals.add("GPU OOM x${info.gpuOom}: -20") }
            info.gpuOom >= 1 -> { score -= 12; signals.add("GPU OOM x${info.gpuOom}: -12") }
        }
        when {
            info.dropFrames >= 15 -> { score -= 10; signals.add("Frame drops x${info.dropFrames}: -10") }
            info.dropFrames >= 5 -> { score -= 5; signals.add("Frame drops x${info.dropFrames}: -5") }
        }
        if (info.isLowMem == true) { score -= 15; signals.add("Low memory device: -15") }
        if (info.forbiddenCvars > 0) { score -= info.forbiddenCvars * 5; signals.add("Forbidden CVars x${info.forbiddenCvars}: -5 each") }
        when {
            info.networkErrors >= 10 -> { score -= 10; signals.add("Network issues x${info.networkErrors}: -10") }
            info.networkErrors >= 5 -> { score -= 5; signals.add("Network issues x${info.networkErrors}: -5") }
        }
        info.screenPct?.let { sp ->
            when {
                sp >= 125f -> { score += 3; signals.add("High render scale: +3") }
                sp >= 110f -> { score += 5; signals.add("Very high render scale: +5") }
                sp < 70f -> { score -= 10; signals.add("Low render scale <70%: -10") }
                else -> {}
            }
        }

        score = score.coerceIn(0, 100)

        val warnings = mutableListOf<String>()
        if (info.gpuOom > 0) warnings.add("GPU OOM detected — performance preset recommended")
        if (info.thermalEvents >= 3) warnings.add("Heavy thermal throttling — consider lower preset")
        if (info.forbiddenCvars > 0) warnings.add("${info.forbiddenCvars} forbidden CVars found in log")

        val preset = recommendPreset(score, info)
        return BrainRecommendation(preset, score, score, signals, warnings)
    }

    private fun recommendPreset(score: Int, info: LogInfo): String {
        val tier = getGPUTier(info.gpu)
        return when {
            info.gpuOom > 0 || info.isLowMem == true -> "performance"
            score >= 80 && info.vulkanStatus == "available" && tier == "flagship" -> "ultra"
            score >= 70 && (tier == "flagship" || tier == "high") -> "high"
            score >= 60 -> "balanced"
            score >= 45 -> "balanced"
            else -> "performance"
        }
    }
}
