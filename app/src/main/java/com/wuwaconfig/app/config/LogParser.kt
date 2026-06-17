package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.LogInfo
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
        var gpu: String? = null
        var deviceModel: String? = null
        var socName: String? = null
        var socCode: String? = null
        var cpuName: String? = null
        var ramMb: Int? = null
        var androidVersion: String? = null
        var resolution: String? = null
        var deviceProfile: String? = null
        var fpsCap: Int? = null
        var fpsActual: Float? = null
        var screenPct: Float? = null
        var shadowQ: Int? = null
        var qualityMode: String? = null
        var kuroPostprocess: Int? = null
        var isLowMem: Boolean? = null
        var forbiddenCvars: Int? = null
        var textureErrors = 0
        var gpuOom = 0
        var dropFrames = 0
        var thermalEvents = 0
        var networkErrors = 0
        val activeCvars = mutableMapOf<String, String>()
        var gameApi: String? = null
        var hasVulkanRhi = false
        var hasOpenGl = false
        var hasVulkan = false
        var hasDirectX = false
        var hasMetal = false

        for (line in text.lineSequence()) {
            val l = line.lowercase()

            // ── Counting (single pass) ──
            if ("error pixel format" in l || "non-streamed mips" in l ||
                "failed to load texture" in l || "out of memory" in l
            ) textureErrors++
            if ("out of memory" in l || "gpu oom" in l || "vulkanoom" in l) gpuOom++
            if ("frame drop" in l || "hitch" in l || "stutter" in l) dropFrames++
            if ("thermal" in l || Regex("""temp\w*\s*:?\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(l)) thermalEvents++
            if ("timeout" in l || "connection refused" in l || "connection reset" in l ||
                "unreachable" in l || "dns fail" in l || "dns failure" in l ||
                "socket error" in l || "network fail" in l || "network failure" in l ||
                "ping loss" in l
            ) networkErrors++

            // ── Flags ──
            if ("islowmemorymobile: true" in l) isLowMem = true
            if ("islowmemorymobile: false" in l) isLowMem = false
            if ("vulkanrhi" in l) hasVulkanRhi = true
            if ("opengl" in l || "opengl es" in l) hasOpenGl = true
            if ("vulkan" in l) hasVulkan = true
            if ("directx" in l) hasDirectX = true
            if ("metal" in l) hasMetal = true

            // ── Field extraction (first match wins) ──
            if (gpu == null) {
                Regex("""K#GPUFamily\s*:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE).find(line)?.let { gpu = it.groupValues[1].trim() }
                if (gpu == null) Regex("""LogInit.*GPU:\s*([^,\r\n]+)""", RegexOption.IGNORE_CASE).find(line)?.let { gpu = it.groupValues[1].trim() }
                if (gpu == null) Regex("""(adreno\s*\d+|mali-g\d+|mali-\d+|xclipse\s*\d+|maleoon)""", RegexOption.IGNORE_CASE).find(line)?.let { gpu = it.groupValues[1].trim() }
            }
            if (deviceModel == null) {
                Regex("""K#DeviceModel\s*:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE).find(line)?.let { deviceModel = it.groupValues[1].trim() }
                if (deviceModel == null) Regex("""DeviceModel\s*:\s*([^\r\n,\]]+)""", RegexOption.IGNORE_CASE).find(line)?.let { deviceModel = it.groupValues[1].trim() }
            }
            if (socName == null) {
                Regex("""(snapdragon|dimensity|exynos|kirin|helio)\s*\w*""", RegexOption.IGNORE_CASE).find(line)?.let { socName = it.value }
            }
            if (socCode == null) Regex("""rHn:(\w+)""", RegexOption.IGNORE_CASE).find(line)?.let { socCode = it.groupValues[1] }
            if (cpuName == null) Regex("""LogInit.*CPU:\s*([^,\r\n]+)""", RegexOption.IGNORE_CASE).find(line)?.let { cpuName = it.groupValues[1].trim() }
            if (ramMb == null) {
                Regex("""PhysicalMemoryMB:\s*(\d+)""", RegexOption.IGNORE_CASE).find(line)?.let { ramMb = it.groupValues[1].toIntOrNull() }
                if (ramMb == null) Regex("""Platform has ~\s*([\d.]+)\s*GB""", RegexOption.IGNORE_CASE).find(line)?.let {
                    ramMb = (it.groupValues[1].toFloatOrNull()?.times(1024))?.toInt()
                }
            }
            if (androidVersion == null) Regex("""LogInit.*OS:\s*Android\s*\((\d+)\)""", RegexOption.IGNORE_CASE).find(line)?.let { androidVersion = it.groupValues[1] }
            if (resolution == null) Regex("""ViewportSize\s+([\d.]+),\s*[\d.]+\s+Resolution\s+\d+,\s*\d+""", RegexOption.IGNORE_CASE).find(line)?.let { resolution = it.groupValues[1] }
            if (deviceProfile == null) Regex("""Selected Device Profile:\s*\[([^\]]+)\]""", RegexOption.IGNORE_CASE).find(line)?.let { deviceProfile = it.groupValues[1] }
            if (fpsCap == null) Regex("""r\.FramePace\s*:\s*(?:requesting\s+\d+,\s*)?set\s*(?:as\s+)?(\d+)""", RegexOption.IGNORE_CASE).find(line)?.let { fpsCap = it.groupValues[1].toIntOrNull() }
            if (fpsActual == null) Regex("""AverageFPS\s*[=:]\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(line)?.let { fpsActual = it.groupValues[1].toFloatOrNull() }
            if (screenPct == null) Regex("""Value remains '(\d+\.?\d*)' .* r\.ScreenPercentage""", RegexOption.IGNORE_CASE).find(line)?.let { screenPct = it.groupValues[1].toFloatOrNull() }
            if (shadowQ == null) Regex("""Value remains '(\d+)' .* sg\.ShadowQuality""", RegexOption.IGNORE_CASE).find(line)?.let { shadowQ = it.groupValues[1].toIntOrNull() }
            if (qualityMode == null) Regex("""sg\.KuroRenderQuality\s*=\s*"(.*)"""", RegexOption.IGNORE_CASE).find(line)?.let { qualityMode = it.groupValues[1] }
            if (kuroPostprocess == null) Regex("""Value remains '(\d+)' .* r\.Mobile\.KuroPostprocess""", RegexOption.IGNORE_CASE).find(line)?.let { kuroPostprocess = it.groupValues[1].toIntOrNull() }
            if (forbiddenCvars == null) Regex("""contained\s+(\d+)\s+forbidden\s+CVars""", RegexOption.IGNORE_CASE).find(line)?.let { forbiddenCvars = it.groupValues[1].toIntOrNull() }

            // ── CVar extraction ──
            Regex("""Setting CVar \[\[([^:]+):([^\]]+)\]\]""", RegexOption.IGNORE_CASE).find(line)?.let {
                activeCvars[it.groupValues[1].trim()] = it.groupValues[2].trim()
            }
            Regex("""Value remains '([^']+)' .* variable '([^']+)'""", RegexOption.IGNORE_CASE).find(line)?.let {
                activeCvars[it.groupValues[2].trim()] = it.groupValues[1].trim()
            }

            // ── Game API from LogRHI line ──
            if (gameApi == null) {
                Regex("""LogRHI:\s*Initializing\s+(\S+(?:\s+\S+)*?)\s*RHI""", RegexOption.IGNORE_CASE).find(line)?.let { m ->
                    val rhi = m.groupValues[1]
                    gameApi = when {
                        "Vulkan" in rhi -> "Vulkan"
                        "OpenGL" in rhi -> "OpenGL ES"
                        "DirectX" in rhi -> "DirectX"
                        "Metal" in rhi -> "Metal"
                        else -> null
                    }
                }
            }
        }

        // ── Post-loop resolution ──
        gameApi = gameApi
            ?: deviceProfile?.let { if (it.endsWith("_GL", ignoreCase = true)) "OpenGL ES" else null }
            ?: activeCvars["r.RHI"]?.let { cvar ->
                when {
                    "Vulkan" in cvar -> "Vulkan"
                    "OpenGL" in cvar -> "OpenGL ES"
                    else -> null
                }
            }

        val vulkanStatus = when (gameApi) {
            "Vulkan" -> "available"
            "OpenGL ES" -> "not_available"
            else -> when {
                hasVulkanRhi -> "available"
                hasOpenGl -> "not_available"
                else -> null
            }
        }
        val api = gameApi ?: when {
            hasVulkan -> "Vulkan"
            hasOpenGl -> "OpenGL ES"
            hasDirectX -> "DirectX"
            hasMetal -> "Metal"
            else -> null
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
            gameApi = gameApi,
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
            forbiddenCvars = forbiddenCvars ?: 0,
            thermalEvents = thermalEvents,
            networkErrors = networkErrors,
            activeCvars = activeCvars
        )
    }
}
