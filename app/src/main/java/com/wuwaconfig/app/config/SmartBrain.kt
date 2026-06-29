package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.LogInfo

data class BrainRecommendation(
    val preset: String,
    val confidence: Int,
    val score: Int,
    val signals: List<String>,
    val warnings: List<String>
)

object SmartBrain {
    fun getGPUTier(gpu: String?): String {
        if (gpu == null) return "unknown"
        val g = gpu.lowercase()
        return when {
            Regex("""adreno.*8[3-9]\d|adreno.*8[12]\d""").containsMatchIn(g) -> "flagship"
            Regex("""tensor\s*g[34]""").containsMatchIn(g) -> "flagship"
            Regex("""dimensity\s*9[3-9]\d\d?""").containsMatchIn(g) -> "flagship"

            Regex("""adreno.*7[5-9]\d|adreno.*8[0]\d""").containsMatchIn(g) -> "high"
            Regex("""tensor\s*g[12]""").containsMatchIn(g) -> "high"
            Regex("""dimensity\s*(9[0-2]\d|8[5-9]\d)""").containsMatchIn(g) -> "high"
            Regex("""exynos\s*2200""").containsMatchIn(g) -> "high"
            Regex("""kirin\s*9000""").containsMatchIn(g) -> "high"
            Regex("""mali-g[78]\d\d|mali-g9""").containsMatchIn(g) -> "high"

            Regex("""adreno.*7[0-4]\d|adreno.*6[5-9]\d""").containsMatchIn(g) -> "mid_high"
            Regex("""dimensity\s*(8[0-4]\d|7[3-9]\d)""").containsMatchIn(g) -> "mid_high"
            Regex("""tensor""").containsMatchIn(g) -> "mid_high"
            Regex("""exynos\s*2[1-3]00""").containsMatchIn(g) -> "mid_high"
            Regex("""kirin\s*9[1-9]\d\d?""").containsMatchIn(g) -> "mid_high"
            Regex("""xclipse""").containsMatchIn(g) -> "mid_high"

            Regex("""adreno.*6[0-4]\d|mali-g[6-7]\d\d|mali-g615""").containsMatchIn(g) -> "mid"
            Regex("""dimensity\s*[0-9]{3}""").containsMatchIn(g) -> "mid"
            Regex("""exynos\s*[0-9]{4}""").containsMatchIn(g) -> "mid"
            Regex("""kirin\s*[0-9]{4}""").containsMatchIn(g) -> "mid"

            Regex("""adreno.*5\d\d|mali-g5[0-9]\d|mali-g57""").containsMatchIn(g) -> "mid_low"

            Regex("""adreno.*[34]\d\d|mali-g[34]""").containsMatchIn(g) -> "low"
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
        if (g.contains("tensor")) return "Tensor"
        if (g.contains("exynos")) return "Exynos"
        if (g.contains("dimensity")) return "Dimensity"
        if (g.contains("kirin")) return "Kirin"
        return null
    }

    private data class ResInfo(val width: Int, val height: Int?)

    private fun parseResolution(res: String?): ResInfo? {
        if (res == null) return null
        val parts = res.trim().split(Regex("\\s*[xX*]\\s*"))
        val w = parts.firstOrNull()?.toIntOrNull() ?: return null
        val h = parts.getOrNull(1)?.toIntOrNull()
        return ResInfo(w, h)
    }

    private fun hasCvar(cvars: Map<String, String>, key: String): Boolean =
        cvars.any { it.key.contains(key, ignoreCase = true) }

    private fun cvarValue(cvars: Map<String, String>, key: String): String? =
        cvars.entries.firstOrNull { it.key.contains(key, ignoreCase = true) }?.value

    fun scoreRecommendation(info: LogInfo): BrainRecommendation {
        var score = 50
        val signals = mutableListOf<String>()
        val warnings = mutableListOf<String>()

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
        if (info.forbiddenCvars > 0) {
            if (!com.wuwaconfig.app.config.ConfigGenerator.allowRestrictedCvars) {
                score -= info.forbiddenCvars * 5; signals.add("Forbidden CVars x${info.forbiddenCvars}: -5 each")
            }
        }
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

        // ── Resolution analysis ────────────────────────────────────
        val res = parseResolution(info.resolution)
        val effectiveRes = res?.let { minOf(it.width, it.height ?: 0) } ?: 0
        val isHighRes = effectiveRes >= 1440
        val is4k = effectiveRes >= 2160
        if (is4k) {
            score -= 10; signals.add("4K resolution: -10")
            when (tier) {
                "mid", "mid_low", "low", "unknown" -> {
                    score -= 8; signals.add("4K on ${tier} GPU: -8")
                    warnings.add("4K on mid/low-end GPU — ultra preset not advisable")
                }
            }
        } else if (isHighRes) {
            score -= 4; signals.add("QHD+ resolution: -4")
            if (tier == "mid_low" || tier == "low" || tier == "unknown") {
                score -= 6; signals.add("QHD+ on ${tier} GPU: -6")
            }
        }

        // ── Texture errors (VRAM pressure) ─────────────────────────
        if (info.textureErrors > 0) {
            val texPenalty = minOf(info.textureErrors, 20)
            score -= texPenalty; signals.add("Texture errors x${info.textureErrors}: -$texPenalty")
            if (info.textureErrors >= 5) warnings.add("Frequent texture errors — possible VRAM pressure, lower texture quality")
        }

        // ── Active CVar analysis ───────────────────────────────────
        val cvars = info.activeCvars
        if (cvars.isNotEmpty()) {
            val known = cvars.keys.count { com.wuwaconfig.app.config.CvarDatabase.isKnown(it) }
            val unknown = cvars.keys.size - known
            val monitored = cvars.keys.count { com.wuwaconfig.app.config.CvarDatabase.isMonitored(it) }
            val differFromDefault = cvars.count { (k, v) ->
                com.wuwaconfig.app.config.CvarDatabase.differsFromDefault(k, v)
            }
            if (unknown > 5) { score -= 5; signals.add("$unknown unknown CVars in log: -5") }
            if (monitored > 10) { score += 3; signals.add("$monitored monitored CVars tracked: +3") }
            if (differFromDefault > 20) { score += 5; signals.add("$differFromDefault CVars differ from defaults (optimized): +5") }
            if (differFromDefault < 5 && cvars.size > 10) { score -= 8; signals.add("Most CVars match game defaults (room to optimize): -8") }
        }

        if (cvars.isNotEmpty()) {
            val shadowQ = cvarValue(cvars, "sg.ShadowQuality")?.toIntOrNull()
            val texQ = cvarValue(cvars, "sg.TextureQuality")?.toIntOrNull()
            val resScale = cvarValue(cvars, "r.ScreenPercentage")?.toFloatOrNull()
            val fpsLimit = cvarValue(cvars, "r.FramePace")?.toIntOrNull()
            val ssao = hasCvar(cvars, "r.Mobile.SSAO")
            val fsr = hasCvar(cvars, "r.FidelityFX.FSR.RCAS")
            val bloom = cvarValue(cvars, "r.BloomQuality")?.toIntOrNull()

            if (shadowQ != null) {
                when {
                    shadowQ >= 3 && (tier == "mid_low" || tier == "low") -> {
                        score -= 6; signals.add("High shadows on low GPU: -6")
                    }
                    shadowQ >= 3 && ram < 6000 -> {
                        score -= 4; signals.add("High shadows + <6GB RAM: -4")
                    }
                }
            }
            if (texQ != null && texQ >= 3 && ram < 6000) {
                score -= 5; signals.add("High textures + <6GB RAM: -5")
                if (info.textureErrors > 0) {
                    score -= 4; signals.add("High textures + texture errors: -4")
                }
            }
            if (resScale != null && resScale > 100f) {
                val overScale = ((resScale - 100) / 10).toInt()
                score -= minOf(overScale, 8); signals.add("Render scale >100%: -${minOf(overScale, 8)}")
            }
            if (fpsLimit != null && fpsLimit >= 90 && info.thermalEvents >= 3) {
                score -= 6; signals.add("High FPS target + thermal: -6")
                warnings.add("High FPS target (${fpsLimit}fps) on thermally throttled device")
            }
            if (fsr) {
                score -= 8; signals.add("FSR RCAS enabled: -8")
            }
            if (ssao && (tier == "mid_low" || tier == "low")) {
                score -= 5; signals.add("SSAO on low GPU: -5")
            }
            if (bloom != null && bloom >= 3 && info.thermalEvents >= 3) {
                score -= 3; signals.add("High bloom + thermal: -3")
            }
        }

        // ── Combined signals ──────────────────────────────────────
        val isLowRam = ram < 6000
        if (isLowRam && isHighRes) {
            score -= 5; signals.add("Low RAM + high res: -5")
            warnings.add("High resolution on device with <6GB RAM")
        }
        if (info.thermalEvents >= 3 && (tier == "mid_low" || tier == "low")) {
            val extra = info.thermalEvents.coerceAtMost(6)
            score -= extra; signals.add("Thermal + low GPU combo: -$extra")
        }
        if (info.gpuOom > 0 && info.textureErrors > 3) {
            score -= 5; signals.add("OOM + texture errors: -5")
        }

        score = score.coerceIn(0, 100)

        if (info.gpuOom > 0) warnings.add("GPU OOM detected — performance preset recommended")
        if (info.thermalEvents >= 3) warnings.add("Heavy thermal throttling — consider lower preset")
        if (info.forbiddenCvars > 0 && !com.wuwaconfig.app.config.ConfigGenerator.allowRestrictedCvars) {
            warnings.add("${info.forbiddenCvars} forbidden CVars found in log")
        }

        val preset = recommendPreset(score, info, tier, isHighRes)
        return BrainRecommendation(preset, score, score, signals, warnings)
    }

    private fun recommendPreset(score: Int, info: LogInfo, tier: String, isHighRes: Boolean): String {
        return when {
            info.gpuOom >= 2 -> "potato"
            info.isLowMem == true || score <= 20 -> "potato"
            score >= 80 && info.vulkanStatus == "available" && tier == "flagship" && !isHighRes -> "ultra"
            score >= 75 && (tier == "flagship" || tier == "high") && info.vulkanStatus == "available" -> "high"
            score >= 70 && (tier == "flagship" || tier == "high") -> "high"
            score >= 40 -> "balanced"
            score >= 20 -> "performance"
            else -> "potato"
        }
    }
}
