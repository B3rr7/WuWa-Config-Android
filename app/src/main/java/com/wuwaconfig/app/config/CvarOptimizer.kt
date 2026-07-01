package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.LogInfo

object CvarOptimizer {

    fun getGPUTier(gpu: String?): String {
        val g = gpu?.lowercase() ?: return "unknown"
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
            Regex("""xclipse""").containsMatchIn(g) -> "mid_high"
            Regex("""adreno.*6[0-4]\d|mali-g[6-7]\d\d|mali-g615""").containsMatchIn(g) -> "mid"
            Regex("""dimensity\s*[0-9]{3}""").containsMatchIn(g) -> "mid"
            Regex("""adreno.*5\d\d|mali-g5[0-9]\d|mali-g57""").containsMatchIn(g) -> "mid_low"
            Regex("""adreno.*[34]\d\d|mali-g[34]""").containsMatchIn(g) -> "low"
            else -> "unknown"
        }
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
        val grasscull: Int
    )

    fun toPresetProfile(opt: OptimizedProfile): PresetProfile = PresetProfile(
        screen = opt.screen, shadow = opt.shadow, shadowRes = opt.shadowRes,
        ssr = opt.ssr, mipbias = opt.mipbias, streaming = opt.streaming,
        vd = opt.vd, flod = opt.flod, detail = opt.detail,
        lod_bias = opt.lod_bias, grasscull = opt.grasscull
    )

    fun optimizeProfile(info: LogInfo): OptimizedProfile {
        val tier = getGPUTier(info.gpu)
        val ram = info.ramMb ?: 4096
        val hasThermal = info.thermalEvents >= 3
        val hasOom = info.gpuOom >= 1
        val hasTextureErrors = info.textureErrors >= 5

        val isHardLimited = hasOom || (hasThermal && tier in listOf("mid_low", "low", "unknown"))
        val isConstrained = hasThermal || hasTextureErrors || ram < 6000

        val screen = when {
            isHardLimited -> 50
            isConstrained && tier == "flagship" -> 80
            isConstrained -> 60
            tier == "flagship" -> 100
            tier == "high" -> 100
            tier == "mid_high" -> 80
            tier == "mid" -> 80
            else -> 60
        }

        val shadow = when {
            isHardLimited -> 0
            isConstrained && tier == "flagship" -> 2
            isConstrained -> 0
            tier == "flagship" -> 5
            tier == "high" -> 4
            tier == "mid_high" -> 2
            tier == "mid" -> 2
            else -> 0
        }

        val shadowRes = when {
            shadow >= 4 -> 2048
            shadow >= 2 -> 1024
            shadow >= 1 -> 512
            else -> 256
        }

        val ssr = when {
            isHardLimited || (isConstrained && tier !in listOf("flagship", "high")) -> 0
            tier == "flagship" && ram >= 8000 -> 4
            tier == "high" && ram >= 8000 -> 2
            tier in listOf("flagship", "high") -> 1
            else -> 0
        }

        val mipbias = when {
            isHardLimited || ram < 4000 -> 3
            ram < 6000 -> 3
            ram < 8000 -> 0
            else -> 0
        }

        val streaming: Double = when {
            isHardLimited -> 0.3
            ram < 4000 -> 0.5
            ram < 6000 -> 1.0
            ram < 8000 -> 2.0
            tier == "flagship" -> 4.0
            tier == "high" -> 3.0
            else -> 2.0
        }

        val vd: Double = when {
            isHardLimited -> 0.3
            tier == "flagship" -> 3.0
            tier == "high" -> 2.0
            tier == "mid_high" -> 1.5
            else -> 0.5
        }

        val flod: Double = when {
            isHardLimited -> 0.4
            tier == "flagship" -> 3.0
            tier == "high" -> 2.5
            tier == "mid_high" -> 2.0
            else -> 0.6
        }

        val detail = when {
            isHardLimited -> 0
            isConstrained && tier !in listOf("flagship", "high") -> 0
            tier == "flagship" -> 2
            tier == "high" -> 2
            tier == "mid_high" -> 1
            tier == "mid" -> 1
            else -> 0
        }

        val lod_bias = when {
            isHardLimited -> 5
            detail == 0 -> 3
            else -> 0
        }

        val grasscull = when {
            isHardLimited -> 1500
            detail == 0 -> 4500
            tier == "flagship" -> 30000
            tier == "high" -> 20000
            tier == "mid_high" -> 15000
            else -> 15000
        }

        return OptimizedProfile(
            screen = screen, shadow = shadow, shadowRes = shadowRes,
            ssr = ssr, mipbias = mipbias, streaming = streaming,
            vd = vd, flod = flod, detail = detail,
            lod_bias = lod_bias, grasscull = grasscull
        )
    }
}
