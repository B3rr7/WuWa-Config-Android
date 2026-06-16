package com.wuwaconfig.app.config

import java.nio.charset.Charset

object LogParser {

    fun decryptWuwaLog(data: ByteArray): ByteArray? {
        if (data.size < 3) return null
        if (data[0] != 0x00.toByte() || data[1] != 0x54.toByte() || data[2] != 0x50.toByte()) return null
        val lut = ByteArray(256)
        for (i in 0..255) {
            lut[i] = (if (i % 2 == 1) (i xor 0xA5) else (i xor 0xEF)).toByte()
        }
        val body = data.copyOfRange(3, data.size)
        for (i in body.indices) {
            body[i] = lut[body[i].toInt() and 0xFF]
        }
        var bom = 0
        if (body.size >= 2 && body[0] == 0xFE.toByte() && body[1] == 0xFF.toByte()) bom = 2
        return body.copyOfRange(bom, body.size)
    }

    fun decodeLogBytes(data: ByteArray): Pair<String, Boolean> {
        val decrypted = decryptWuwaLog(data)
        val payload = decrypted ?: data
        val text = when {
            payload.size >= 2 && payload[0] == 0xFE.toByte() && payload[1] == 0xFF.toByte() ->
                payload.copyOfRange(2, payload.size).toString(Charset.forName("UTF-16BE"))
            payload.size >= 2 && payload[0] == 0xFF.toByte() && payload[1] == 0xFE.toByte() ->
                payload.copyOfRange(2, payload.size).toString(Charset.forName("UTF-16LE"))
            looksUtf16Be(payload) -> payload.toString(Charset.forName("UTF-16BE"))
            looksUtf16Le(payload) -> payload.toString(Charset.forName("UTF-16LE"))
            else -> payload.toString(Charsets.UTF_8)
        }
        return text.trimStart('\uFEFF') to (decrypted != null)
    }

    private fun looksUtf16Be(data: ByteArray): Boolean {
        if (data.size < 8) return false
        var zeroes = 0
        val samples = minOf(data.size, 200)
        for (i in 0 until samples step 2) {
            if (data[i] == 0.toByte()) zeroes++
        }
        return zeroes > samples / 5
    }

    private fun looksUtf16Le(data: ByteArray): Boolean {
        if (data.size < 8) return false
        var zeroes = 0
        val samples = minOf(data.size, 200)
        for (i in 1 until samples step 2) {
            if (data[i] == 0.toByte()) zeroes++
        }
        return zeroes > samples / 5
    }

    fun parseLog(text: String): LogInfo {
        val lower = text.lowercase()

        fun extract(pattern: Regex): String? =
            pattern.find(text)?.groupValues?.get(1)?.trim()

        fun extractInt(pattern: Regex): Int? =
            extract(pattern)?.toIntOrNull()

        fun extractFloat(pattern: Regex): Float? =
            extract(pattern)?.toFloatOrNull()

        val gpu = extract(Regex("""K#GPUFamily\s*:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE))
            ?: extract(Regex("""LogInit.*GPU:\s*([^,\r\n]+)""", RegexOption.IGNORE_CASE))
            ?: extract(Regex("""(adreno\s*\d+|mali-g\d+|mali-\d+|xclipse\s*\d+|maleoon)""", RegexOption.IGNORE_CASE))

        val deviceModel = extract(Regex("""K#DeviceModel\s*:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE))
            ?: extract(Regex("""DeviceModel\s*:\s*([^\r\n,\]]+)""", RegexOption.IGNORE_CASE))

        val socName = extract(Regex("""(snapdragon|dimensity|exynos|kirin|helio)\s*\w*""", RegexOption.IGNORE_CASE))
        val socCode = extract(Regex("""rHn:(\w+)"""))

        val cpuName = extract(Regex("""LogInit.*CPU:\s*([^,\r\n]+)""", RegexOption.IGNORE_CASE))

        val ramMb = extractInt(Regex("""PhysicalMemoryMB:\s*(\d+)""", RegexOption.IGNORE_CASE))
            ?: extract(Regex("""Platform has ~\s*([\d.]+)\s*GB""", RegexOption.IGNORE_CASE))?.let {
                (it.toFloatOrNull()?.times(1024))?.toInt()
            }

        val androidVersion = extract(Regex("""LogInit.*OS:\s*Android\s*\((\d+)\)""", RegexOption.IGNORE_CASE))
        val resolution = extract(Regex("""ViewportSize\s+([\d.]+),\s*[\d.]+\s+Resolution\s+\d+,\s*\d+""", RegexOption.IGNORE_CASE))

        val vulkanStatus = when {
            lower.contains("vulkanrhi") -> "available"
            lower.contains("opengl") -> "not_available"
            else -> null
        }
        val api = when {
            lower.contains("vulkan") -> "Vulkan"
            lower.contains("opengl") -> "OpenGL ES"
            lower.contains("directx") -> "DirectX"
            else -> null
        }

        val deviceProfile = extract(Regex("""Selected Device Profile:\s*\[([^\]]+)\]"""))
        val fpsCap = extractInt(Regex("""r\.FramePace\s*:\s*(?:requesting\s+\d+,\s*)?set\s*(?:as\s+)?(\d+)"""))
        val fpsActual = extractFloat(Regex("""AverageFPS\s*[=:]\s*([\d.]+)"""))

        val screenPct = extractFloat(Regex("""Value remains '(\d+\.?\d*)' .* r\.ScreenPercentage"""))
        val shadowQ = extractInt(Regex("""Value remains '(\d+)' .* sg\.ShadowQuality"""))
        val qualityMode = extract(Regex("""sg\.KuroRenderQuality\s*=\s*"(.*)""""))
        val kuroPostprocess = extractInt(Regex("""Value remains '(\d+)' .* r\.Mobile\.KuroPostprocess"""))
        val isLowMem = when {
            text.contains("IsLowMemoryMobile: True", ignoreCase = true) -> true
            text.contains("IsLowMemoryMobile: False", ignoreCase = true) -> false
            else -> null
        }

        val forbiddenCvars = extractInt(Regex("""contained\s+(\d+)\s+forbidden\s+CVars""")) ?: 0
        val textureErrors = Regex("""Error pixel format|non-streamed mips|failed to load texture|Out of memory""", RegexOption.IGNORE_CASE)
            .findAll(text).count()
        val gpuOom = Regex("""out.?of.?memory|GPU.?OOM|VulkanOOM""", RegexOption.IGNORE_CASE)
            .findAll(text).count()
        val dropFrames = Regex("""frame.?drop|hitch|stutter""", RegexOption.IGNORE_CASE)
            .findAll(text).count()
        val thermalEvents = Regex("""thermal|temp\w*\s*:?\s*\d+""", RegexOption.IGNORE_CASE)
            .findAll(text).count()
        val networkErrors = Regex("""timeout|connection refused|connection reset|unreachable|dns\s*fail|socket.*error|network.*fail|ping.*loss""", RegexOption.IGNORE_CASE)
            .findAll(text).count()

        val activeCvars = mutableMapOf<String, String>()
        Regex("""Setting CVar \[\[([^:]+):([^\]]+)\]\]""").findAll(text).forEach {
            val (k, v) = it.destructured
            activeCvars[k.trim()] = v.trim()
        }
        Regex("""Value remains '([^']+)' .* variable '([^']+)'""").findAll(text).forEach {
            val (v, k) = it.destructured
            activeCvars[k.trim()] = v.trim()
        }

        return LogInfo(
            gpu = gpu,
            deviceModel = deviceModel,
            socName = socName,
            socCode = socCode,
            cpuName = cpuName,
            ramMb = ramMb,
            androidVersion = androidVersion,
            resolution = resolution,
            api = api,
            vulkanStatus = vulkanStatus,
            deviceProfile = deviceProfile,
            fpsCap = fpsCap,
            fpsActual = fpsActual,
            screenPct = screenPct,
            shadowQ = shadowQ,
            qualityMode = qualityMode,
            kuroPostprocess = kuroPostprocess,
            isLowMem = isLowMem,
            textureErrors = textureErrors,
            gpuOom = gpuOom,
            dropFrames = dropFrames,
            forbiddenCvars = forbiddenCvars,
            thermalEvents = thermalEvents,
            networkErrors = networkErrors,
            activeCvars = activeCvars
        )
    }
}
