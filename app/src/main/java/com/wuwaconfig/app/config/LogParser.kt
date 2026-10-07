package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.LogInfo
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import java.nio.charset.Charset

object LogParser {
    enum class DecodeResult {
        DECRYPTED,
        PLAINTEXT,
    }

    interface DecryptStrategy {
        val name: String

        fun decrypt(data: ByteArray): ByteArray?
    }

    class XorLutStrategy : DecryptStrategy {
        override val name: String = "XOR_LUT"

        override fun decrypt(data: ByteArray): ByteArray? {
            val result = data.copyOf()
            for (i in result.indices) {
                result[i] = XOR_LUT[result[i].toInt() and 0xFF]
            }
            return result
        }
    }

    class XorConstantStrategy(private val key: Byte) : DecryptStrategy {
        override val name: String = "XOR_${key.toInt().toString(16)}"

        override fun decrypt(data: ByteArray): ByteArray? {
            val result = data.copyOf()
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor key.toInt()).toByte()
            }
            return result
        }
    }

    class DoubleXorStrategy : DecryptStrategy {
        override val name: String = "DOUBLE_XOR"

        override fun decrypt(data: ByteArray): ByteArray? {
            val step1 = ByteArray(data.size)
            for (i in data.indices) {
                step1[i] = (data[i].toInt() xor 0x4A).toByte()
            }
            val result = ByteArray(step1.size)
            for (i in step1.indices) {
                result[i] = XOR_LUT[step1[i].toInt() and 0xFF]
            }
            return result
        }
    }

    private val strategies: List<DecryptStrategy> =
        listOf(
            XorLutStrategy(),
            DoubleXorStrategy(),
            XorConstantStrategy(0x4A.toByte()),
            XorConstantStrategy(0xA5.toByte()),
            XorConstantStrategy(0xEF.toByte()),
        )

    /**
     * Dynamic multi-strategy fallback decryption.
     * Tries each [DecryptStrategy] in order; validates the decrypted output
     * with a 512-byte pre-flight check. Returns the first strategy whose
     * output passes, or `null` if none does — an unvalidated payload is never
     * returned, because downstream parsing would silently consume noise.
     *
     * @param body the raw payload to decrypt
     * @return the decrypted [ByteArray] if any strategy produces valid UE4 content, else `null`
     */
    fun decryptWithFallback(body: ByteArray): ByteArray? {
        for (strategy in strategies) {
            val decrypted = strategy.decrypt(body) ?: continue
            if (verifyDecryption(decrypted)) {
                return decrypted
            }
        }
        // No strategy produced content containing a known UE4 keyword. The old code
        // returned XorLutStrategy().decrypt(body) here — the SAME transform that was
        // just rejected — so it could never "succeed" and could only hand back
        // unvalidated noise. That noise was tagged DecodeResult.DECRYPTED, so
        // parseLog ran on garbage and verifyDeployedCvars reported EVERY generated
        // CVar as "rejected" after a genuinely successful deploy.
        //
        // A keyword scan alone is too strict for short or early-file payloads, so
        // fall back to the first strategy ONLY when its output is plausibly decoded
        // text. Note the check runs on the TRANSFORM OUTPUT: a plain UTF-8-BOM text
        // file passes decryptBackupLog's `EF BB BF` magic gate, but XOR-LUT'ing it
        // produces non-text, so it is rejected here instead of being mislabelled.
        val firstPass = XorLutStrategy().decrypt(body)
        if (firstPass != null && looksLikeDecodedText(firstPass)) {
            LogRepository.add(
                "LogParser: no UE4 keyword found; accepted payload on text-plausibility alone",
                LogLevel.INFO,
            )
            return firstPass
        }
        LogRepository.add(
            "LogParser: no decrypt strategy validated — treating payload as unreadable",
            LogLevel.WARNING,
        )
        return null
    }

    /**
     * Pre-flight verification that a decrypted payload really is a UE4 log.
     *
     * Screens the first min(16 KB, decrypted.size) bytes for engine keywords like
     * "LogInit", "LogRHI", "Core.System", "GameUserSettings", and returns true if
     * the decrypted content looks like a valid UE4 log.
     *
     * This is the strong check. There is also a weaker [looksLikeEngineLogText]
     * for payloads that decrypted cleanly but are too short, or start too early
     * in the file, to contain any of these markers. The strong check is applied
     * to the TRANSFORM OUTPUT, never to the input: a plain UTF-8-BOM text file
     * passed to [decryptBackupLog] clears the `EF BB BF` magic gate, but
     * XOR-LUT transforming it yields non-text, so this check rejects it. Real
     * decrypted log content is overwhelmingly printable text, so it passes.
     */
    private fun verifyDecryption(decrypted: ByteArray): Boolean {
        // 16 KB, not 512 B. Measured on a real device log: this game opens with a long
        // burst of repeated launcher lines ("Sharphereal: Display: [...] calculate all
        // size of lang res") before any engine category appears, and the first
        // LogInit landed at byte 43,798 while LogKuroRendering was at 47,652. A 512-byte
        // sample therefore reported a perfectly valid decrypted log as unverified, which
        // pushed every read onto the best-effort fallback path.
        val sampleSize = minOf(16 * 1024, decrypted.size)
        if (sampleSize < 8) return false
        val sample = decrypted.copyOfRange(0, sampleSize)
        val sampleStr = String(sample, Charsets.UTF_8)
        return UE4_KEYWORDS.any { keyword ->
            sampleStr.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * True when [text] carries at least one engine-log marker.
     *
     * Used by the multi-log reader to drop a log file whose head could not be
     * decrypted, without which one undecryptable file injects noise into a merged
     * analysis. The markers are deliberately broad — this is a "is this a log at
     * all" test, not a content test.
     */
    fun looksLikeEngineLogText(text: String): Boolean {
        if (text.isBlank()) return false
        val sample = text.take(4096).lowercase()
        return LOG_SHAPE_TOKENS.any { sample.contains(it) }
    }

    private fun looksLikeDecodedText(decoded: ByteArray): Boolean {
        val sampleSize = minOf(512, decoded.size)
        if (sampleSize == 0) return false
        var asciiOrWhitespace = 0
        var nuls = 0
        for (i in 0 until sampleSize) {
            val b = decoded[i].toInt() and 0xFF
            when {
                b == 0x00 -> nuls++
                b == 0x09 || b == 0x0A || b == 0x0D || (b in 0x20..0x7E) -> asciiOrWhitespace++
            }
        }
        // UTF-16 content is ~50% NUL by construction, so a flat ASCII ratio would
        // reject it. Detect the UTF-16 shape and accept it explicitly.
        if (sampleSize >= 2) {
            val b0 = decoded[0].toInt() and 0xFF
            val b1 = decoded[1].toInt() and 0xFF
            if ((b0 == 0xFE && b1 == 0xFF) || (b0 == 0xFF && b1 == 0xFE)) return true
        }
        if (nuls * 100 >= sampleSize * 35 && asciiOrWhitespace * 100 >= sampleSize * 40) return true
        if (asciiOrWhitespace * 100 >= sampleSize * 90) {
            // Printable, yes — but is it a LOG? For anything long enough to judge, a
            // real decoded UE log carries at least one log-shaped token, and noise
            // does not reliably carry one. See MIN_SAMPLE_FOR_KEYWORD_SCAN.
            if (sampleSize < MIN_SAMPLE_FOR_KEYWORD_SCAN) return true
            val text = String(decoded.copyOfRange(0, sampleSize), Charsets.UTF_8).lowercase()
            return LOG_SHAPE_TOKENS.any { text.contains(it) }
        }
        // High-byte-dominant: CJK log content. Require it to decode as VALID UTF-8
        // with no replacement characters, which is what separates real CJK text from
        // an XOR-LUT'd plain text file or a high-entropy blob. A raw byte-count or
        // byte-diversity heuristic is not enough here: an all-NUL input transforms to
        // 512 identical 0xEF bytes, and a random blob has maximal diversity, so both
        // would sail through a loose "these bytes are >= 0xC2" test.
        return isValidUtf8(decoded, sampleSize)
    }

    /** True when the first [sampleSize] bytes of [data] form well-formed UTF-8. */
    private fun isValidUtf8(
        data: ByteArray,
        sampleSize: Int,
    ): Boolean {
        // A truncated multi-byte sequence at the sample boundary is not malformed, so
        // stop rather than fail when fewer than 4 bytes remain.
        if (sampleSize < 4) return true
        var i = 0
        while (i < sampleSize) {
            val b = data[i].toInt() and 0xFF
            val extra =
                when {
                    b <= 0x7F -> 0
                    b in 0xC2..0xDF -> 1
                    b in 0xE0..0xEF -> 2
                    b in 0xF0..0xF4 -> 3
                    // 0x80..0xC1 and 0xF5..0xFF are never a valid lead byte.
                    else -> return false
                }
            if (i + extra >= sampleSize) return true
            for (k in 1..extra) {
                val cont = data[i + k].toInt() and 0xFF
                if (cont !in 0x80..0xBF) return false
            }
            i += extra + 1
        }
        return true
    }

    /**
     * Markers that prove a decoded payload is an engine log. Loaded from
     * `assets/config/game_profile.properties` (`engineKeywords`) with
     * [GameProfile.DEFAULT_ENGINE_KEYWORDS] as the compiled-in fallback.
     *
     * Externalised because this is the gate that decides whether a log is
     * readable *at all*: a build that renames a category stops matching, every
     * read falls through to the best-effort path, and the user is told "No
     * readable Client.log found" rather than being told the marker list is
     * stale. Keeping it as data means a retune is an asset edit.
     */
    private val UE4_KEYWORDS: List<String> = gameProfile().engineKeywords

    /**
     * Much broader, lower-confidence log-shape tokens. Used ONLY for payloads too
     * short to keyword-validate, where the alternative is refusing to read a
     * correctly-encrypted-but-tiny log at all.
     */
    private val LOG_SHAPE_TOKENS =
        listOf(
            // Multi-character markers only. A single "[" was in this list and is a
            // no-op: at the 4-16 KB sample sizes used here, essentially any text
            // contains a bracket, so it contributed false confidence and no signal.
            "log",
            "error",
            "warning",
            "verbose",
            "engine",
            "texture",
            "shader",
        )

    /**
     * Below this many bytes the UE4_KEYWORDS scan is unreliable, so a short payload
     * is accepted on plausibility alone. Above it, plausibility alone is NOT enough:
     *
     * A real failure seen on-device — an empty main Client.log made the app fall back
     * to a backup log that decoded to printable-but-meaningless text. Accepting that
     * on "92% printable" merged noise into the analysis and produced
     * "VERIFY: 0/93 CVars accepted" — a confident, completely fabricated result.
     * Printability is not evidence: XOR-LUT output of arbitrary bytes is often
     * printable. Log SHAPE is.
     */
    private const val MIN_SAMPLE_FOR_KEYWORD_SCAN = 128

    fun decryptWuwaLog(data: ByteArray): ByteArray? {
        if (data.size < 3) return null
        if (data[0] != 0x00.toByte() || data[1] != 0x54.toByte() || data[2] != 0x50.toByte()) return null
        val body = data.copyOfRange(3, data.size)
        val decrypted = decryptWithFallback(body) ?: return null
        var bom = 0
        if (decrypted.size >= 2 && decrypted[0] == 0xFE.toByte() && decrypted[1] == 0xFF.toByte()) bom = 2
        return decrypted.copyOfRange(bom, decrypted.size)
    }

    fun decryptBackupLog(data: ByteArray): ByteArray? {
        if (data.size < 3) return null
        if (data[0] != 0xEF.toByte() || data[1] != 0xBB.toByte() || data[2] != 0xBF.toByte()) return null
        val body = data.copyOfRange(3, data.size)
        val decrypted = decryptWithFallback(body) ?: return null
        return decrypted
    }

    fun applyXorLut(data: ByteArray): ByteArray {
        // LUT is NOT self-inverse: LUT(LUT(b)) = b xor 0x4A for ALL b.
        // The game stores plaintext as LUT(plaintext xor 0x4A) so a single pass restores it.
        // Delegates to XorLutStrategy to keep a single crypto path.
        return checkNotNull(XorLutStrategy().decrypt(data)) { "XorLutStrategy failed" }
    }

    fun decodeLogBytes(data: ByteArray): Pair<String, DecodeResult> {
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
        return text.trimStart('\uFEFF') to
            (if (decrypted != null || backupDecrypted != null) DecodeResult.DECRYPTED else DecodeResult.PLAINTEXT)
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
        var engineVersion: String? = null
        var fpsCap: Int? = null
        var fpsActual: Float? = null
        var screenPct: Float? = null
        var shadowQ: Int? = null
        var qualityMode: String? = null
        var isLowMem: Boolean? = null

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

        // Sticky: once the log states Vulkan is unavailable, a later bare mention
        // of the word (e.g. `Setting CVar [[r.Vulkan.DisableSubpassDeferred:1]]`)
        // must not flip [hasVulkan] back on. See FLAG_RE below.
        var vulkanUnavailable = false

        for (line in text.lineSequence()) {
            // ── Counting (single pass) ──
            // NOTE: UI dynamic-atlas format warnings ("LogDynamicAtlas ... Error pixel
            // format") are unrelated to streaming/VRAM pressure and must not be counted
            // here, otherwise low-end devices get falsely flagged as VRAM-starved.
            val oomHit = OOM_RE.containsMatchIn(line)
            if (oomHit) gpuOom++
            if (!oomHit && !TEXTURE_SKIP_RE.containsMatchIn(line) && TEXTURE_HIT_RE.containsMatchIn(line)) {
                textureErrors++
            }
            if (FRAME_DROP_RE.containsMatchIn(line)) dropFrames++
            if (THERMAL_RE.containsMatchIn(line)) thermalEvents++
            if (ADJUST_TRIGGER_RE.containsMatchIn(line)) autoAdjustTriggers++
            if (ADJUST_RECOVER_RE.containsMatchIn(line)) autoAdjustRecoveries++
            if (NETWORK_RE.containsMatchIn(line)) networkErrors++

            // ── Flags (combined single-pass match) ──
            LOW_MEM_RE.find(line)?.let { m ->
                isLowMem = m.groupValues[1].lowercase() == "true"
            }

            // Authoritative Vulkan availability, checked BEFORE the substring flags
            // below. The game names Vulkan even when it fails to initialise it —
            // on this device it logged "Vulkan library detected", "Failed to init
            // Vulkan because of current driver version 0x801f6000 less than ...",
            // "Vulkan driver NOT available." and "VulkanAvailable: false", then ran
            // on OpenGL ES. The bare-substring flag below saw those and reported
            // Vulkan. These two lines are the ones that actually decide.
            VULKAN_AVAILABLE_RE.find(line)?.let { m ->
                if (m.groupValues[1].equals("true", ignoreCase = true)) {
                    hasVulkan = true
                } else {
                    vulkanUnavailable = true
                    hasVulkan = false
                    hasVulkanRhi = false
                }
            }
            if (VULKAN_FAILED_RE.containsMatchIn(line)) {
                vulkanUnavailable = true
                hasVulkan = false
                hasVulkanRhi = false
            }
            if (OPENGL_ES_USED_RE.containsMatchIn(line)) {
                hasOpenGl = true
            }

            FLAG_RE.findAll(line).forEach { m ->
                val g = m.groupValues
                if (g[1].isNotEmpty() && !vulkanUnavailable) hasVulkanRhi = true
                if (g[2].isNotEmpty() || g[3].isNotEmpty()) hasOpenGl = true
                if (g[4].isNotEmpty() && !vulkanUnavailable) hasVulkan = true
                if (g[5].isNotEmpty()) hasDirectX = true
                if (g[6].isNotEmpty()) hasMetal = true
            }
            // "vulkanrhi" contains "vulkan", so mirror the original substring behaviour.
            if (hasVulkanRhi && !vulkanUnavailable) hasVulkan = true

            // ── Field extraction (first match wins) ──
            if (gpu == null) {
                GPU_RE.find(line)?.let { gpu = it.groupValues[1].trim() }
                if (gpu == null) {
                    GPU_LOGINIT_RE.find(line)?.let { gpu = it.groupValues[1].trim() }
                }
                if (gpu == null) {
                    GPU_GENERIC_RE.find(line)?.let { gpu = it.groupValues[1].trim() }
                }
            }
            if (deviceModel == null) {
                DEVMODEL_RE.find(line)?.let { deviceModel = it.groupValues[1].trim() }
                if (deviceModel == null) {
                    DEVMODEL_FALLBACK_RE.find(line)?.let { deviceModel = it.groupValues[1].trim() }
                }
            }
            if (socName == null) SOC_RE.find(line)?.let { socName = it.value }
            if (socCode == null) SOC_CODE_RE.find(line)?.let { socCode = it.groupValues[1] }
            if (cpuName == null) CPU_RE.find(line)?.let { cpuName = it.groupValues[1].trim() }
            if (ramMb == null) {
                RAM_RE.find(line)?.let { ramMb = it.groupValues[1].toIntOrNull() }
                if (ramMb == null) {
                    RAM_GB_RE.find(line)?.let {
                        ramMb = (it.groupValues[1].toFloatOrNull()?.times(1024))?.toInt()
                    }
                }
                if (ramMb == null) {
                    RAM_PHYSICAL_RE.find(line)?.let {
                        ramMb = it.groupValues[1].toFloatOrNull()?.toInt()
                    }
                }
            }
            if (androidVersion == null) {
                OS_RE.find(line)?.let { androidVersion = it.groupValues[1] }
            }
            if (resolution == null) {
                RES_RE.find(line)?.let {
                    resolution = "${it.groupValues[1]}x${it.groupValues[2]}"
                }
            }
            if (resolution == null) {
                LOGIC_RES_RE.find(line)?.let {
                    resolution = "${it.groupValues[1]}x${it.groupValues[2]}"
                }
            }
            if (resolution == null) {
                VIEWPORT_RE.find(line)?.let {
                    val w = it.groupValues[1].toFloatOrNull()?.toInt()?.toString() ?: it.groupValues[1]
                    val h = it.groupValues[2].toFloatOrNull()?.toInt()?.toString() ?: it.groupValues[2]
                    resolution = "${w}x$h"
                }
            }
            if (deviceProfile == null) {
                DEV_PROFILE_RE.find(line)?.let { deviceProfile = it.groupValues[1] }
            }
            if (engineVersion == null) {
                ENGINE_BUILD_RE.find(line)?.let { m ->
                    // Reassemble the banner token itself; LogInfo carries the
                    // version string, not a bare generation number, so the
                    // CVar gate can report what it actually saw.
                    engineVersion = "${m.groupValues[1]}+${m.groupValues[2]}"
                }
            }
            if (fpsCap == null) {
                FRAME_PACE_RE.find(line)?.let {
                    fpsCap = it.groupValues[1].toIntOrNull()
                }
            }
            if (fpsActual == null) {
                AVG_FPS_RE.find(line)?.let { fpsActual = it.groupValues[1].toFloatOrNull() }
            }
            if (qualityMode == null) {
                QUALITY_MODE_RE.find(line)?.let { qualityMode = it.groupValues[1] }
            }
            // ── CVar extraction ──
            CVar_SETTING_RE.find(line)?.let {
                val value = it.groupValues[2].trim().substringBefore(';').trim()
                activeCvars[it.groupValues[1].trim()] = value
            }
            CVar_EFFECTIVE_RE.find(line)?.let {
                val name = it.groupValues[1].trim()
                val value = it.groupValues[2].trim().substringBefore(';').trim()
                // The engine prints the literal placeholder `unknown?` when a
                // console variable's name could not be resolved. A real CVar
                // name cannot contain '?', so this drops the placeholder and any
                // future variant of it without needing an exact-string list.
                if (name.isNotEmpty() && '?' !in name) activeCvars[name] = value
            }

            // ── Game API from LogRHI line ──
            if (gameApi == null) {
                RHI_RE.find(line)?.let { m ->
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
        // Computed once after the scan: every key is only discovered mid-loop, so
        // there is nothing to memoize per-line. Was a `var` + null guard that was
        // never assigned inside the loop, so the guard could never be false.
        val forbiddenCvarCount = activeCvars.keys.count { ForbiddenCvars.isForbidden(it) }

        // ── screenPct / shadowQ, read off the finished map ──
        // Both are reported on the same LogConsoleManager lines that
        // CVar_EFFECTIVE_RE captures, so deriving them here instead of with a
        // per-line regex keeps a single source of truth AND yields the value in
        // effect rather than the value that was requested. Their dedicated
        // SCREEN_PCT_RE / SHADOW_Q_RE predated this and matched nothing.
        val lcActive = HashMap<String, String>(activeCvars.size)
        for ((k, v) in activeCvars) lcActive[k.lowercase()] = v
        screenPct = screenPct ?: lcActive["r.screenpercentage"]?.toFloatOrNull()
        shadowQ = shadowQ ?: lcActive["sg.shadowquality"]?.toIntOrNull()

        // ── Post-loop API resolution (single source of truth) ──
        val explicitApi =
            gameApi
                ?: deviceProfile?.let { if (it.endsWith("_GL", ignoreCase = true)) "OpenGL ES" else null }
                ?: apiFromRhiToken(activeCvars["r.RHI"])
        gameApi = explicitApi
        val api = explicitApi ?: apiFromFlags(hasVulkan, hasOpenGl, hasDirectX, hasMetal)

        val vulkanStatus =
            when (explicitApi) {
                "Vulkan" -> "available"
                "OpenGL ES" -> "not_available"
                else ->
                    when {
                        // An explicit "VulkanAvailable: false" is an answer even when
                        // the log never goes on to name the API that was used.
                        vulkanUnavailable -> "not_available"
                        hasVulkanRhi -> "available"
                        hasOpenGl -> "not_available"
                        else -> null
                    }
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
            engineVersion = engineVersion,
            fpsCap = fpsCap,
            fpsActual = fpsActual,
            screenPct = screenPct,
            shadowQ = shadowQ,
            qualityMode = qualityMode,
            isLowMem = isLowMem,
            textureErrors = textureErrors,
            gpuOom = gpuOom,
            dropFrames = dropFrames,
            forbiddenCvars = forbiddenCvarCount,
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
        var monthCardRemainDays = 0
        var playerId = ""
        var currentStrength = 0

        for (line in lines) {
            when {
                "切换玩家战斗音乐状态: 进入战斗" in line ||
                    "切换玩家状态: 进入战斗造成伤害" in line -> battles++
                "初次幻象收服" in line || "初次幻象捕捉" in line -> echoesCollected++
                "极限闪避前闪" in line -> dodgeForward++
                "极限闪避后闪" in line -> dodgeBack++
                "极限闪避反击" in line -> dodgeCounter++
                "执行角色死亡逻辑" in line || "前台角色死亡进行切人" in line -> deaths++
                "角色下场" in line -> roleChanges++
                ("传送:" in line && "完成" in line) || "传送:完成" in line -> teleports++
                "进入倒地状态" in line -> staggers++
                "召唤系幻象的出生特效" in line -> echoSkillsUsed++
                "变身幻象" in line -> echoTransformUsed++
                "月卡每日奖励" in line || "【月卡每日奖励】信息推送" in line -> {
                    monthCards++
                    val m = REMAIN_DAYS_RE.find(line)
                    if (m != null) monthCardRemainDays = m.groupValues[1].toIntOrNull() ?: monthCardRemainDays
                }
                "SetUserId [playerId:" in line -> {
                    val m = PLAYER_ID_RE.find(line)
                    if (m != null) playerId = m.groupValues[1]
                }
                "当前体力数据" in line -> {
                    val m = UPS_RE.find(line)
                    if (m != null) {
                        val v = m.groupValues[1].toIntOrNull() ?: 0
                        if (v < currentStrength) staminaUsed += currentStrength - v
                        currentStrength = v
                    }
                }
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
            monthCardRemainDays = monthCardRemainDays,
            playerId = playerId,
        )
    }

    fun parseBattleStats(text: String): BattleStats {
        val stats = parseBattleStatsLines(text.lines())
        // Byte length, not char count (UTF-16 source logs inflate char counts).
        return stats.copy(logSizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong())
    }

    fun parseBattleStatsSummary(text: String): com.wuwaconfig.app.model.BattleStatsSummary {
        val lines = text.lines()
        val total = parseBattleStatsLines(lines)
        val withBytes = total.copy(logSizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong())

        val timestamps = mutableListOf<Pair<String, Long>>()
        for (line in lines) {
            val m = LOG_TIMESTAMP_RE.find(line) ?: continue
            val dateStr = m.groupValues[1]
            val epochSec = parseLogTimestampToEpochSec(dateStr, m.groupValues[2])
            if (epochSec != null) timestamps += dateStr to epochSec
        }

        val sessions = estimateSessions(timestamps)
        val playtimeSeconds =
            if (timestamps.size >= 2) {
                val sorted = timestamps.sortedBy { it.second }
                (sorted.last().second - sorted.first().second).coerceAtLeast(0L)
            } else {
                0L
            }

        val totalWithPlaytime = withBytes.copy(playtimeSeconds = playtimeSeconds, sessions = sessions)
        val daily = buildDailyBreakdown(lines, timestamps)
        return com.wuwaconfig.app.model.BattleStatsSummary(
            total = totalWithPlaytime,
            daily = daily,
            accountId = totalWithPlaytime.playerId,
            timestampMs = System.currentTimeMillis(),
        )
    }

    private fun estimateSessions(timestamps: List<Pair<String, Long>>): Int {
        if (timestamps.isEmpty()) return 0
        val sorted = timestamps.sortedBy { it.second }
        var sessions = 1
        var prev = sorted.first().second
        for ((_, epoch) in sorted.drop(1)) {
            if (epoch - prev > SESSION_GAP_SECONDS) sessions++
            prev = epoch
        }
        return sessions
    }

    private fun buildDailyBreakdown(
        lines: List<String>,
        timestamps: List<Pair<String, Long>>,
    ): List<com.wuwaconfig.app.model.DailyBattleStats> {
        if (timestamps.isEmpty()) return emptyList()
        val dateOfLine = Array(lines.size) { i -> LOG_TIMESTAMP_RE.find(lines[i])?.groupValues?.get(1) }
        val grouped = mutableMapOf<String, MutableList<String>>()
        for (i in lines.indices) {
            val date = dateOfLine[i] ?: continue
            grouped.getOrPut(date) { mutableListOf() } += lines[i]
        }
        return grouped.entries.sortedBy { it.key }.map { (date, dayLines) ->
            com.wuwaconfig.app.model.DailyBattleStats(date, parseBattleStatsLines(dayLines))
        }
    }

    private fun parseLogTimestampToEpochSec(
        datePart: String,
        timePart: String,
    ): Long? =
        runCatching {
            val fmt = java.text.SimpleDateFormat("yyyy.MM.dd-HH.mm.ss", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getDefault()
            val hhmmss = timePart.substringBefore(':')
            fmt.parse("$datePart-$hhmmss")!!.time / 1000L
        }.getOrNull()

    /** Maps an RHI CVar value (e.g. from `r.RHI`) to a normalized API name. */
    private fun apiFromRhiToken(token: String?): String? =
        when {
            token == null -> null
            "Vulkan" in token -> "Vulkan"
            "OpenGL" in token -> "OpenGL ES"
            else -> null
        }

    /** Derives the rendering API from the per-line graphics-API flags. */
    private fun apiFromFlags(
        hasVulkan: Boolean,
        hasOpenGl: Boolean,
        hasDirectX: Boolean,
        hasMetal: Boolean,
    ): String? =
        when {
            hasVulkan -> "Vulkan"
            hasOpenGl -> "OpenGL ES"
            hasDirectX -> "DirectX"
            hasMetal -> "Metal"
            else -> null
        }

    private val XOR_LUT =
        ByteArray(256) { i -> (if (i % 2 == 1) (i xor 0xA5) else (i xor 0xEF)).toByte() }

    private val FRAME_DROP_RE =
        Regex("""frame\s*drop|hitch\s*detected|stutter\s*detected""", RegexOption.IGNORE_CASE)
    private val THERMAL_RE =
        Regex("""thermal\s*(?:throttle|limit|event|warning)""", RegexOption.IGNORE_CASE)
    private val ADJUST_TRIGGER_RE = Regex("""自动渲染调节触发前""")
    private val ADJUST_RECOVER_RE = Regex("""自动渲染调节恢复前""")

    // Combined matchers — each scans the line once instead of one substring
    // search per keyword, cutting the per-line cost from ~22 scans to a handful.
    private val TEXTURE_SKIP_RE = Regex("""logdynamicatlas""", RegexOption.IGNORE_CASE)
    private val TEXTURE_HIT_RE = Regex("""non-streamed mips|failed to load texture""", RegexOption.IGNORE_CASE)
    private val OOM_RE = Regex("""out of memory|gpu oom|vulkanoom""", RegexOption.IGNORE_CASE)
    private val NETWORK_RE =
        Regex(
            """timeout|connection refused|connection reset|unreachable|dns fail|dns failure|socket error|network fail|network failure|ping loss""",
            RegexOption.IGNORE_CASE,
        )
    private val LOW_MEM_RE = Regex("""islowmemorymobile:\s*(true|false)""", RegexOption.IGNORE_CASE)
    private val FLAG_RE =
        Regex("""(vulkanrhi)|(opengl es)|(opengl)|(vulkan)|(directx)|(metal)""", RegexOption.IGNORE_CASE)

    /** `LogAndroid:   VulkanAvailable: false` — the authoritative answer. */
    private val VULKAN_AVAILABLE_RE =
        Regex("""VulkanAvailable\s*:\s*(true|false)\b""", RegexOption.IGNORE_CASE)

    /** The engine's own words for a failed Vulkan init. */
    private val VULKAN_FAILED_RE =
        Regex(
            """Vulkan driver NOT available|Failed to init Vulkan|Vulkan is not supported""",
            RegexOption.IGNORE_CASE,
        )

    /** `LogAndroid: OpenGL ES will be used.` */
    private val OPENGL_ES_USED_RE =
        Regex("""OpenGL ES will be used|packaged for OpenGL ES""", RegexOption.IGNORE_CASE)

    private val GPU_RE = Regex("""K#GPUFamily\s*:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE)
    private val GPU_LOGINIT_RE = Regex("""LogInit.*GPU:\s*([^,\r\n]+)""", RegexOption.IGNORE_CASE)
    private val GPU_GENERIC_RE =
        Regex("""(adreno\s*\d+|mali-g\d+|mali-\d+|xclipse\s*\d+|maleoon)""", RegexOption.IGNORE_CASE)
    private val DEVMODEL_RE =
        Regex("""K#DeviceModel\s*:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE)
    private val DEVMODEL_FALLBACK_RE =
        Regex("""DeviceModel\s*:\s*([^\r\n,\]]+)""", RegexOption.IGNORE_CASE)
    private val SOC_RE =
        Regex("""(snapdragon|dimensity|exynos|kirin|helio)\s*\w*""", RegexOption.IGNORE_CASE)

    /**
     * Intended as a Qualcomm SoC code, but matched nothing on any observed log.
     * Kept rather than deleted: it costs one failed `find` per scan and removing it
     * would silently retire `LogInfo.socCode` on any future build that does emit it.
     * `socName` is unavailable for the same underlying reason — this game never
     * names its SoC (only GPU and CPU model), which is why `socName` is null on
     * real hardware rather than because of a pattern bug.
     */
    private val SOC_CODE_RE = Regex("""rHn:(\w+)""", RegexOption.IGNORE_CASE)
    private val CPU_RE = Regex("""LogInit.*CPU:\s*([^,\r\n]+)""", RegexOption.IGNORE_CASE)
    private val RAM_RE = Regex("""PhysicalMemoryMB:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val RAM_GB_RE =
        Regex("""Platform has ~\s*([\d.]+)\s*GB""", RegexOption.IGNORE_CASE)

    /**
     * The wording this game actually emits. Neither [RAM_RE] nor [RAM_GB_RE]
     * matched it, so total RAM was always null on this device:
     * `LogInit: Memory total: Physical=5642.29MB (6GB approx) Available=...`
     */
    private val RAM_PHYSICAL_RE =
        Regex("""Physical=([\d.]+)\s*MB""", RegexOption.IGNORE_CASE)
    private val OS_RE = Regex("""LogInit.*OS:\s*Android\s*\((\d+)\)""", RegexOption.IGNORE_CASE)
    private val RES_RE =
        Regex("""Resolution\s+(\d+)\s*[,xX×]?\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val VIEWPORT_RE =
        Regex("""ViewportSize\s+([\d.]+),\s*([\d.]+)""", RegexOption.IGNORE_CASE)

    /**
     * The panel size, as this game reports it:
     * `Setting Android Resolution, logic resolution Width=2456 and Height=1080,
     *  final Width=1632 and Height=720 (requested scale = 1.000000, ...)`
     *
     * [RES_RE] needed the digits right after the word "Resolution" and
     * [VIEWPORT_RE] needed a literal "ViewportSize" — neither occurs, so
     * resolution was always null. Deliberately matches the *logic* (panel)
     * values, which are identical on every one of these lines, rather than
     * "final Width=", which differs between the engine's first guess and its
     * settled value. That keeps the result independent of which line is seen
     * first. "final" is deliberately NOT used: it is 1632x720 on the first
     * line and 2456x1080 on the last, so a first-match-wins scan would report
     * a scaled-down buffer instead of the display's real resolution.
     */
    private val LOGIC_RES_RE =
        Regex("""logic resolution Width=(\d+) and Height=(\d+)""", RegexOption.IGNORE_CASE)
    private val DEV_PROFILE_RE =
        Regex("""Selected Device Profile:\s*\[([^\]]+)\]""", RegexOption.IGNORE_CASE)

    /**
     * Engine build string from the `LogInit: Build:` banner, e.g.
     * `++UE4+Release-4.27-CL-12345678` or `++UE5+5.3-...`.
     *
     * Anchored on `++UE` rather than on `Build:` because this log format emits a
     * `Build:` line for other subsystems too, and only the bracketed token
     * identifies the engine generation. Capture stops at whitespace so the CL
     * number and any trailing flags do not end up in the value.
     */
    private val ENGINE_BUILD_RE =
        Regex("""\+\+(UE\d)\+(\S*)""")
    private val FRAME_PACE_RE =
        Regex(
            """r\.FramePace\s*:\s*(?:requesting\s+\d+,\s*)?set\s*(?:as\s+)?(\d+)""",
            RegexOption.IGNORE_CASE,
        )

    /**
     * Desktop-UE stat line. This game never emits it — zero matches across three
     * 3.7.0 sessions, which had no `AverageFPS` line of any kind — so
     * `LogInfo.fpsActual` is structurally always null for WuWa. `SmartBrain`
     * gates its "competitive" recommendation on `fpsActual != null`, so that
     * branch is currently unreachable from log data; see SmartBrain:457.
     */
    private val AVG_FPS_RE = Regex("""AverageFPS\s*[=:]\s*([\d.]+)""", RegexOption.IGNORE_CASE)
    private val QUALITY_MODE_RE =
        Regex("""sg\.KuroRenderQuality\s*=\s*"(.*)"""", RegexOption.IGNORE_CASE)
    private val CVar_SETTING_RE =
        Regex("""Setting CVar \[\[([^:]+):([^\]]+)\]\]""", RegexOption.IGNORE_CASE)

    /**
     * The value actually in effect, as opposed to the one that was requested.
     *
     * `LogConsoleManager: Warning: Setting the console variable 'r.ScreenPercentage'
     *  with 'SetByScalability' was ignored as it is lower priority than the previous
     *  'SetByProjectSetting'. Value remains '80'`
     *
     * The NAME comes first and the value last. The previous pattern expected
     * `Value remains 'v' ... variable 'n'` and matched 0 lines across three 3.7.0
     * sessions, which cost two things: `sg.ShadowQuality` never entered
     * activeCvars at all (it is only ever reported this way), and 8-11 CVars were
     * recorded at their requested value rather than the effective one —
     * r.ScreenPercentage read 85 while 80 was in force.
     *
     * These lines always follow their `Setting CVar` counterpart (the :888 warning
     * trails the :874 write), so assigning into activeCvars here lets the effective
     * value overwrite the requested one without any extra precedence logic.
     */
    private val CVar_EFFECTIVE_RE =
        Regex("""console variable '([^']+)'.*Value remains '([^']*)'""", RegexOption.IGNORE_CASE)
    private val RHI_RE =
        Regex("""LogRHI:\s*Initializing\s+(\S+(?:\s+\S+)*?)\s*RHI""", RegexOption.IGNORE_CASE)

    private val UPS_RE = Regex("""UPs:(\d+)""")
    private val REMAIN_DAYS_RE = Regex("""remainDays:\s*(\d+)""")
    private val PLAYER_ID_RE = Regex("""playerId:\s*(\d+)""")
    private val LOG_TIMESTAMP_RE = Regex("""\[(\d{4}\.\d{2}\.\d{2})-(\d{2}\.\d{2}\.\d{2}:\d+)\]""")

    /** A gap longer than this between log lines marks a new session. */
    private const val SESSION_GAP_SECONDS = 30L * 60L
}
