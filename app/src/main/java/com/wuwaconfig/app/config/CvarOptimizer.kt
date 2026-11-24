package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.DeployComparison
import com.wuwaconfig.app.model.LogInfo

object CvarOptimizer {
    /**
     * GPU string to tier name, in priority order -- the first match wins, so the
     * table's order is the matching rule. From [TuningProfile], which carries the
     * compiled-in default.
     *
     * Compiled once, at TuningProfile load time, rather than per call: the old
     * body rebuilt ~25 Regex objects on every [getGPUTier] invocation.
     *
     * Externalised because a new SoC or GPU generation is an asset edit, and a
     * device that matches nothing silently fell back to the most conservative
     * tier.
     */
    private val gpuTierPatterns: List<TuningProfile.RegexTier>
        get() = TuningProfile.get().gpuTierPatterns

    fun getGPUTier(gpu: String?): String {
        val g = gpu?.lowercase() ?: return "unknown"
        for (entry in gpuTierPatterns) {
            if (entry.regex.containsMatchIn(g)) return entry.value
        }
        return "unknown"
    }

    data class OptimizedProfile(
        val screen: Int,
        val shadow: Int,
        val shadowRes: Int,
        val ssr: Int,
        val mipbias: Int,
        val streaming: Double,
        val vd: Double,
        val flod: Double,
        val detail: Int,
        val lod_bias: Int,
        val grasscull: Int,
    )

    // The four fine-grained fields must be derived from the OptimizedProfile, not
    // left at their PresetProfile defaults (characterDetail=2, postProcess=2,
    // staticLighting=true, cutsceneQuality=2). On a hard-limited device under
    // useAdvancedGen that emitted r.AllowStaticLighting=1 and
    // r.KuroMaterialQualityLevel=2 alongside detail=0 / shadow=0 — the weakest
    // hardware path contradicted by the most expensive settings.
    fun toPresetProfile(opt: OptimizedProfile): PresetProfile {
        val fineDetail = (opt.detail / 2).coerceIn(0, 3)
        return PresetProfile(
            screen = opt.screen, shadow = opt.shadow, shadowRes = opt.shadowRes,
            ssr = opt.ssr, mipbias = opt.mipbias, streaming = opt.streaming,
            vd = opt.vd, flod = opt.flod, detail = opt.detail,
            lod_bias = opt.lod_bias, grasscull = opt.grasscull,
            characterDetail = fineDetail,
            postProcess = fineDetail,
            cutsceneQuality = fineDetail,
            staticLighting = opt.shadow >= 2,
        )
    }

    fun optimizeProfile(info: LogInfo): OptimizedProfile {
        val tier = getGPUTier(info.gpu)
        val ram = info.ramMb ?: 4096
        val hasThermal = info.thermalEvents >= 3
        val hasOom = info.gpuOom >= 1
        val hasTextureErrors = info.textureErrors >= 5

        val isHardLimited = hasOom || (hasThermal && tier in HARD_LIMITED_TIERS)
        val isConstrained = hasThermal || hasTextureErrors || ram < 6000

        val shadow = shadowFor(tier, isHardLimited, isConstrained)
        val detail = detailFor(tier, ram, isHardLimited, isConstrained)

        return OptimizedProfile(
            screen = screenFor(tier, isHardLimited, isConstrained),
            shadow = shadow,
            shadowRes = shadowResFor(shadow),
            ssr = ssrFor(tier, ram, isHardLimited, isConstrained),
            mipbias = mipbiasFor(ram, isHardLimited),
            streaming = streamingFor(tier, ram, isHardLimited),
            vd = vdFor(tier, isHardLimited),
            flod = flodFor(tier, isHardLimited),
            detail = detail,
            lod_bias = lodBiasFor(detail, isHardLimited),
            grasscull = grasscullFor(tier, detail, isHardLimited),
        )
    }

    // --- per-field decision tables, extracted so optimizeProfile stays readable.
    // Each helper is a small, independently testable mapping. Behaviour is
    // identical to the previous inline when-blocks (locked by
    // CvarOptimizerCharacterizationTest).

    private val HARD_LIMITED_TIERS = listOf("mid_low", "low", "unknown")

    private fun screenFor(
        tier: String,
        isHardLimited: Boolean,
        isConstrained: Boolean,
    ): Int =
        when {
            isHardLimited -> 50
            isConstrained && tier == "flagship" -> 80
            isConstrained -> 60
            tier == "flagship" || tier == "high" -> 100
            tier == "mid_high" || tier == "mid" -> 80
            else -> 60
        }

    private fun shadowFor(
        tier: String,
        isHardLimited: Boolean,
        isConstrained: Boolean,
    ): Int =
        when {
            isHardLimited -> 0
            isConstrained && tier == "flagship" -> 2
            isConstrained -> 0
            tier == "flagship" -> 5
            tier == "high" -> 4
            tier == "mid_high" || tier == "mid" -> 2
            else -> 0
        }

    private fun shadowResFor(shadow: Int): Int =
        when {
            shadow >= 4 -> 2048
            shadow >= 2 -> 1024
            shadow >= 1 -> 512
            else -> 256
        }

    private fun ssrFor(
        tier: String,
        ram: Int,
        isHardLimited: Boolean,
        isConstrained: Boolean,
    ): Int =
        when {
            isHardLimited || (isConstrained && tier !in listOf("flagship", "high")) -> 0
            tier == "flagship" && ram >= 8000 -> 4
            tier == "high" && ram >= 8000 -> 2
            tier in listOf("flagship", "high") -> 1
            else -> 0
        }

    private fun mipbiasFor(
        ram: Int,
        isHardLimited: Boolean,
    ): Int =
        when {
            isHardLimited || ram < 4000 -> 3
            ram < 6000 -> 3
            else -> 0
        }

    private fun streamingFor(
        tier: String,
        ram: Int,
        isHardLimited: Boolean,
    ): Double =
        when {
            isHardLimited -> 0.3
            ram < 4000 -> 0.5
            ram < 6000 -> 1.0
            ram < 8000 -> 2.0
            tier == "flagship" -> 4.0
            tier == "high" -> 3.0
            else -> 2.0
        }

    private fun vdFor(
        tier: String,
        isHardLimited: Boolean,
    ): Double =
        when {
            isHardLimited -> 0.3
            tier == "flagship" -> 3.0
            tier == "high" -> 2.0
            tier == "mid_high" -> 1.5
            else -> 0.5
        }

    private fun flodFor(
        tier: String,
        isHardLimited: Boolean,
    ): Double =
        when {
            isHardLimited -> 0.4
            tier == "flagship" -> 3.0
            tier == "high" -> 2.5
            tier == "mid_high" -> 2.0
            else -> 0.6
        }

    private fun detailFor(
        tier: String,
        ram: Int,
        isHardLimited: Boolean,
        isConstrained: Boolean,
    ): Int =
        when {
            isHardLimited -> 0
            isConstrained && tier !in listOf("flagship", "high") -> 0
            isConstrained && tier == "flagship" -> 2
            isConstrained && tier == "high" -> 1
            tier == "flagship" && ram >= 12000 -> 7
            tier == "flagship" && ram >= 8000 -> 6
            tier == "flagship" -> 5
            tier == "high" && ram >= 8000 -> 5
            tier == "high" -> 4
            tier == "mid_high" -> 2
            tier == "mid" -> 1
            else -> 0
        }

    private fun lodBiasFor(
        detail: Int,
        isHardLimited: Boolean,
    ): Int =
        when {
            isHardLimited -> 5
            detail == 0 -> 3
            else -> 0
        }

    private fun grasscullFor(
        tier: String,
        detail: Int,
        isHardLimited: Boolean,
    ): Int =
        when {
            isHardLimited -> 1500
            detail == 0 -> 4500
            tier == "flagship" -> 30000
            tier == "high" -> 20000
            else -> 15000
        }

    fun adjustProfile(
        current: OptimizedProfile,
        comparison: DeployComparison,
    ): OptimizedProfile {
        val fpsDelta = comparison.fpsDelta ?: 0f
        val oomDelta = comparison.oomDelta ?: 0
        val thermalDelta = comparison.thermalDelta ?: 0
        val dropDelta = comparison.dropFramesDelta ?: 0

        val wasStable = fpsDelta in -5f..5f && oomDelta <= 0 && thermalDelta <= 2 && dropDelta <= 5
        val degraded = fpsDelta < -5f || oomDelta > 0 || thermalDelta > 2 || dropDelta > 5
        val improved = fpsDelta > 5f && oomDelta <= 0 && thermalDelta <= 0 && dropDelta <= 0

        if (wasStable) return current

        if (oomDelta > 0) {
            return OptimizedProfile(50, 0, 256, 0, 3, 0.3, 0.3, 0.4, 0, 5, 1500)
        }

        if (degraded) {
            val newScreen = (current.screen * 0.75f).toInt().coerceIn(50, 100)
            val newShadow = (current.shadow - 2).coerceAtLeast(0)
            val newDetail = (current.detail - 1).coerceAtLeast(0)
            val newSsr = (current.ssr - 1).coerceAtLeast(0)
            val newMipbias = (current.mipbias + 1).coerceAtMost(3)
            val newVd = (current.vd * 0.6).coerceAtMost(current.vd)
            val newFlod = (current.flod * 0.6).coerceAtMost(current.flod)
            return current.copy(
                screen = newScreen,
                shadow = newShadow,
                shadowRes =
                    when (newShadow) {
                        3, 4, 5 -> 1024 // reduced from 2048 on degrade to save VRAM
                        1, 2 -> 256
                        else -> 128
                    },
                ssr = newSsr,
                mipbias = newMipbias,
                vd = newVd,
                flod = newFlod,
                detail = newDetail,
                lod_bias = if (newDetail == 0) 3 else current.lod_bias,
                grasscull = if (newDetail == 0) 4500 else current.grasscull,
            )
        }

        if (improved) {
            val newScreen = (current.screen * 1.15f).toInt().coerceIn(50, 100)
            val newShadow = (current.shadow + 1).coerceAtMost(5)
            val newDetail = (current.detail + 1).coerceAtMost(7)
            val newSsr = (current.ssr + 1).coerceAtMost(4)
            return current.copy(
                screen = newScreen,
                shadow = newShadow,
                shadowRes =
                    when (newShadow) {
                        4, 5 -> 2048
                        2, 3 -> 1024
                        else -> 256
                    },
                ssr = newSsr,
                detail = newDetail,
                grasscull =
                    when {
                        newDetail == 2 && current.grasscull < 20000 -> 30000
                        else -> current.grasscull
                    },
            )
        }

        // Reachable, NOT dead: `improved` is stricter than the negation of
        // (!wasStable && !degraded) — it requires thermalDelta <= 0, not <= 2. An input
        // like fpsDelta=6, thermalDelta=1 satisfies none of wasStable / oomDelta>0 /
        // degraded / improved, so the profile is returned unchanged.
        return current
    }
}
