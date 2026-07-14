package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.LogInfo
import java.nio.charset.Charset

object LogParser {
    fun decryptWuwaLog(data: ByteArray): ByteArray? {
        if (data.size < 3) return null
        if (data[0] != 0x00.toByte() || data[1] != 0x54.toByte() || data[2] != 0x50.toByte()) return null
        val body = applyXorLut(data.copyOfRange(3, data.size))
        var bom = 0
        if (body.size >= 2 && body[0] == 0xFE.toByte() && body[1] == 0xFF.toByte()) bom = 2
        return body.copyOfRange(bom, body.size)
    }

    fun decryptBackupLog(data: ByteArray): ByteArray? {
        if (data.size < 3) return null
        if (data[0] != 0xEF.toByte() || data[1] != 0xBB.toByte() || data[2] != 0xBF.toByte()) return null
        return applyXorLut(data.copyOfRange(3, data.size))
    }

    fun applyXorLut(data: ByteArray): ByteArray {
        // LUT is NOT self-inverse: LUT(LUT(b)) = b xor 0x4A for ALL b.
        // The game stores plaintext as LUT(plaintext xor 0x4A) so a single pass restores it.
        val lut = ByteArray(256) { i -> (if (i % 2 == 1) (i xor 0xA5) else (i xor 0xEF)).toByte() }
        val result = data.copyOf()
        for (i in result.indices) {
            result[i] = lut[result[i].toInt() and 0xFF]
        }
        return result
    }

    fun decodeXorBytes(data: ByteArray): Pair<String, Boolean> {
        val decoded = applyXorLut(data)
        return decodeLogBytes(decoded).let { it.first to true }
    }

    fun decodeLogBytes(data: ByteArray): Pair<String, Boolean> {
        val decrypted = decryptWuwaLog(data)
        val backupDecrypted = if (decrypted == null) decryptBackupLog(data) else null
        val payload = decrypted ?: backupDecrypted ?: data
        val text =
            when {
                payload.size >= 2 && payload[0] == 0xFE.toByte() && payload[1] == 0xFF.toByte() ->
                    payload.copyOfRange(2, payload.size).toString(Charset.forName("UTF-16BE"))
                payload.size >= 2 && payload[0] == 0xFF.toByte() && payload[1] == 0xFE.toByte() ->
                    payload.copyOfRange(2, payload.size).toString(Charset.forName("UTF-16LE"))
                looksUtf16Be(payload) -> payload.toString(Charset.forName("UTF-16BE"))
                looksUtf16Le(payload) -> payload.toString(Charset.forName("UTF-16LE"))
                else -> payload.toString(Charsets.UTF_8)
            }
        return text.trimStart('\uFEFF') to (decrypted != null || backupDecrypted != null)
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

    private val CONVENE_URL_REGEX =
        Regex(
            """https://aki-gm-resources(-oversea)?\.aki-game\.(net|com)/aki/gacha/index\.html#/record[^"\s]*""",
            RegexOption.IGNORE_CASE,
        )

    fun extractConveneUrl(text: String): String? {
        return CONVENE_URL_REGEX.find(text)?.value
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
        var isLowMem: Boolean? = null
        var forbiddenCvars: Int? = null
        var textureErrors = 0
        var gpuOom = 0
        var dropFrames = 0
        var thermalEvents = 0
        var autoAdjustTriggers = 0
        var autoAdjustRecoveries = 0
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
            // NOTE: UI dynamic-atlas format warnings ("LogDynamicAtlas ... Error pixel
            // format") are unrelated to streaming/VRAM pressure and must not be counted
            // here, otherwise low-end devices get falsely flagged as VRAM-starved.
            if ("logdynamicatlas" !in l &&
                ("non-streamed mips" in l || "failed to load texture" in l || "out of memory" in l)
            ) {
                textureErrors++
            }
            if ("out of memory" in l || "gpu oom" in l || "vulkanoom" in l) gpuOom++
            if (Regex("""frame\s*drop|hitch\s*detected|stutter\s*detected""", RegexOption.IGNORE_CASE).containsMatchIn(line)) dropFrames++
            if (Regex("""thermal\s*(?:throttle|limit|event|warning)""", RegexOption.IGNORE_CASE).containsMatchIn(line)) thermalEvents++
            if (Regex("""自动渲染调节触发前""").containsMatchIn(line)) autoAdjustTriggers++
            if (Regex("""自动渲染调节恢复前""").containsMatchIn(line)) autoAdjustRecoveries++
            if ("timeout" in l || "connection refused" in l || "connection reset" in l ||
                "unreachable" in l || "dns fail" in l || "dns failure" in l ||
                "socket error" in l || "network fail" in l || "network failure" in l ||
                "ping loss" in l
            ) {
                networkErrors++
            }

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
                if (gpu == null) {
                    Regex(
                        """LogInit.*GPU:\s*([^,\r\n]+)""",
                        RegexOption.IGNORE_CASE,
                    ).find(line)?.let { gpu = it.groupValues[1].trim() }
                }
                if (gpu == null) {
                    Regex(
                        """(adreno\s*\d+|mali-g\d+|mali-\d+|xclipse\s*\d+|maleoon)""",
                        RegexOption.IGNORE_CASE,
                    ).find(line)?.let {
                        gpu = it.groupValues[1].trim()
                    }
                }
            }
            if (deviceModel == null) {
                Regex(
                    """K#DeviceModel\s*:\s*([^\r\n]+)""",
                    RegexOption.IGNORE_CASE,
                ).find(line)?.let { deviceModel = it.groupValues[1].trim() }
                if (deviceModel == null) {
                    Regex("""DeviceModel\s*:\s*([^\r\n,\]]+)""", RegexOption.IGNORE_CASE).find(line)?.let {
                        deviceModel = it.groupValues[1].trim()
                    }
                }
            }
            if (socName == null) {
                Regex("""(snapdragon|dimensity|exynos|kirin|helio)\s*\w*""", RegexOption.IGNORE_CASE).find(line)?.let { socName = it.value }
            }
            if (socCode == null) Regex("""rHn:(\w+)""", RegexOption.IGNORE_CASE).find(line)?.let { socCode = it.groupValues[1] }
            if (cpuName == null) {
                Regex("""LogInit.*CPU:\s*([^,\r\n]+)""", RegexOption.IGNORE_CASE).find(line)?.let {
                    cpuName = it.groupValues[1].trim()
                }
            }
            if (ramMb == null) {
                Regex("""PhysicalMemoryMB:\s*(\d+)""", RegexOption.IGNORE_CASE).find(line)?.let { ramMb = it.groupValues[1].toIntOrNull() }
                if (ramMb == null) {
                    Regex("""Platform has ~\s*([\d.]+)\s*GB""", RegexOption.IGNORE_CASE).find(line)?.let {
                        ramMb = (it.groupValues[1].toFloatOrNull()?.times(1024))?.toInt()
                    }
                }
            }
            if (androidVersion == null) {
                Regex("""LogInit.*OS:\s*Android\s*\((\d+)\)""", RegexOption.IGNORE_CASE).find(line)?.let {
                    androidVersion = it.groupValues[1]
                }
            }
            if (resolution == null) {
                Regex("""Resolution\s+(\d+)\s*[,xX×]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(line)?.let {
                    resolution = "${it.groupValues[1]}x${it.groupValues[2]}"
                }
            }
            if (resolution == null) {
                Regex("""ViewportSize\s+([\d.]+),\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(line)?.let {
                    val w = it.groupValues[1].toFloatOrNull()?.toInt()?.toString() ?: it.groupValues[1]
                    val h = it.groupValues[2].toFloatOrNull()?.toInt()?.toString() ?: it.groupValues[2]
                    resolution = "${w}x$h"
                }
            }
            if (deviceProfile == null) {
                Regex("""Selected Device Profile:\s*\[([^\]]+)\]""", RegexOption.IGNORE_CASE).find(line)?.let {
                    deviceProfile = it.groupValues[1]
                }
            }
            if (fpsCap == null) {
                Regex(
                    """r\.FramePace\s*:\s*(?:requesting\s+\d+,\s*)?set\s*(?:as\s+)?(\d+)""",
                    RegexOption.IGNORE_CASE,
                ).find(line)?.let {
                    fpsCap = it.groupValues[1].toIntOrNull()
                }
            }
            if (fpsActual == null) {
                Regex("""AverageFPS\s*[=:]\s*([\d.]+)""", RegexOption.IGNORE_CASE).find(line)?.let {
                    fpsActual = it.groupValues[1].toFloatOrNull()
                }
            }
            if (screenPct == null) {
                Regex(
                    """Value remains '(\d+\.?\d*)' .* r\.ScreenPercentage""",
                    RegexOption.IGNORE_CASE,
                ).find(line)?.let {
                    screenPct = it.groupValues[1].toFloatOrNull()
                }
            }
            if (shadowQ == null) {
                Regex("""Value remains '(\d+)' .* sg\.ShadowQuality""", RegexOption.IGNORE_CASE).find(line)?.let {
                    shadowQ = it.groupValues[1].toIntOrNull()
                }
            }
            if (qualityMode == null) {
                Regex("""sg\.KuroRenderQuality\s*=\s*"(.*)"""", RegexOption.IGNORE_CASE).find(line)?.let {
                    qualityMode = it.groupValues[1]
                }
            }
            // ── CVar extraction ──
            Regex("""Setting CVar \[\[([^:]+):([^\]]+)\]\]""", RegexOption.IGNORE_CASE).find(line)?.let {
                val value = it.groupValues[2].trim().substringBefore(';').trim()
                activeCvars[it.groupValues[1].trim()] = value
            }
            Regex("""Value remains '([^']+)' .* variable '([^']+)'""", RegexOption.IGNORE_CASE).find(line)?.let {
                val value = it.groupValues[1].trim().substringBefore(';').trim()
                activeCvars[it.groupValues[2].trim()] = value
            }

            // ── Game API from LogRHI line ──
            if (gameApi == null) {
                Regex("""LogRHI:\s*Initializing\s+(\S+(?:\s+\S+)*?)\s*RHI""", RegexOption.IGNORE_CASE).find(line)?.let { m ->
                    val rhi = m.groupValues[1]
                    gameApi =
                        when {
                            "Vulkan" in rhi -> "Vulkan"
                            "OpenGL" in rhi -> "OpenGL ES"
                            "DirectX" in rhi -> "DirectX"
                            "Metal" in rhi -> "Metal"
                            else -> null
                        }
                }
            }
        }

        // ── Count forbidden CVars from extracted activeCvars ──
        if (forbiddenCvars == null) {
            forbiddenCvars = activeCvars.keys.count { ForbiddenCvars.isForbidden(it) }
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

        val vulkanStatus =
            when (gameApi) {
                "Vulkan" -> "available"
                "OpenGL ES" -> "not_available"
                else ->
                    when {
                        hasVulkanRhi -> "available"
                        hasOpenGl -> "not_available"
                        else -> null
                    }
            }
        val api =
            gameApi ?: when {
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
            isLowMem = isLowMem,
            textureErrors = textureErrors,
            gpuOom = gpuOom,
            dropFrames = dropFrames,
            forbiddenCvars = forbiddenCvars,
            thermalEvents = thermalEvents,
            autoAdjustTriggers = autoAdjustTriggers,
            autoAdjustRecoveries = autoAdjustRecoveries,
            networkErrors = networkErrors,
            activeCvars = activeCvars,
        )
    }

    fun parseBattleStatsLines(lines: List<String>): BattleStats {
        var battles = 0
        var echoesCollected = 0
        var dodgeForward = 0
        var dodgeBack = 0
        var dodgeCounter = 0
        var deaths = 0
        var roleChanges = 0
        var teleports = 0
        var staggers = 0
        var staminaUsed = 0
        var echoSkillsUsed = 0
        var echoTransformUsed = 0
        var monthCards = 0

        for (line in lines) {
            when {
                "切换玩家战斗音乐状态: 进入战斗" in line -> battles++
                "初次幻象收服" in line || "初次幻象捕捉" in line -> echoesCollected++
                "极限闪避前闪" in line -> dodgeForward++
                "极限闪避后闪" in line -> dodgeBack++
                "极限闪避反击" in line -> dodgeCounter++
                "执行角色死亡逻辑" in line -> deaths++
                "角色下场" in line -> roleChanges++
                "传送:" in line && "完成" in line -> teleports++
                "进入倒地状态" in line -> staggers++
                line.contains("当前体力数据") && "UPs:" in line -> {
                    val matches = Regex("UPs:(\\d+)").findAll(line)
                    staminaUsed += matches.sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
                }
                "召唤系幻象的出生特效" in line -> echoSkillsUsed++
                "变身幻象" in line -> echoTransformUsed++
                "月卡每日奖励" in line -> monthCards++
            }
        }

        return BattleStats(
            battles = battles,
            echoesCollected = echoesCollected,
            dodgeForward = dodgeForward,
            dodgeBack = dodgeBack,
            dodgeCounter = dodgeCounter,
            deaths = deaths,
            roleChanges = roleChanges,
            teleports = teleports,
            staggers = staggers,
            staminaUsed = staminaUsed,
            echoSkillsUsed = echoSkillsUsed,
            echoTransformUsed = echoTransformUsed,
            monthCards = monthCards,
        )
    }

    fun parseBattleStats(text: String): BattleStats {
        val stats = parseBattleStatsLines(text.lines())
        return stats.copy(logSizeBytes = text.length.toLong())
    }
}
