package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.LogInfo

/**
 * Target platform for CVar applicability.
 *
 * Two-dimensional on purpose: Android runs both OpenGL ES and Vulkan, and a CVar
 * that is dead on one is live on the other. A single "is this Android?" axis cannot
 * express that, and collapsing the two would mark 84 working `r.Vulkan.*` CVars as
 * dead on every Vulkan device (or revive them on an OpenGL ES one).
 */
enum class TargetPlatform(val label: String) {
    ANDROID_GLES("ANDROID_GLES"),
    ANDROID_VULKAN("ANDROID_VULKAN"),
    WINDOWS("WINDOWS"),
    APPLE("APPLE"),
    LINUX_VULKAN("LINUX_VULKAN"),

    /**
     * Not enough information to judge. Every classifier returns "alive" here — the
     * checker must never guess, and a generate with no uploaded log
     * (`ConfigGenScreen` passes `logInfo ?: LogInfo()`) legitimately lands here.
     */
    UNKNOWN("UNKNOWN"),
    ;

    val isAndroid: Boolean get() = this == ANDROID_GLES || this == ANDROID_VULKAN
}

/** The platform a CVar's naming convention targets. */
enum class PlatformScope {
    /** No platform signal in the name — always considered usable. */
    ANY,

    PC,
    APPLE,
    CONSOLE,
    VULKAN,
    OPENGL,
}

sealed interface CvarVerdict {
    data object Alive : CvarVerdict

    /** [scope] provably excludes [target]; [reason] is shown in the INI marker. */
    data class Dead(
        val scope: PlatformScope,
        val reason: String,
    ) : CvarVerdict
}

private enum class TokenMatch {
    PREFIX,
    SUFFIX,
    EXACT,
}

/**
 * One naming-convention rule.
 *
 * Every token here was verified against the 5,889 names in
 * `assets/cvars/libUE4_cvars.txt` — the counts in the comments are the real
 * occurrences, not estimates.
 */
private data class CvarScopeRule(
    val token: String,
    val match: TokenMatch,
    val scope: PlatformScope,
    val reason: String,
)

/**
 * Platform naming conventions present in this game's CVar corpus.
 *
 * EXACT, VERIFIED TOKENS ONLY. Loose prefix matching is actively dangerous here:
 * `r.PSO.*` (Pipeline State Objects — a Vulkan/D3D12/Metal concept that works
 * perfectly well on Android) looks exactly like `r.PS4.*` (PlayStation). A naive
 * `r.PS` prefix rule flagged 19 perfectly valid CVars, and the generator really does
 * emit two of them (`r.PSO.CompilationMode`, `r.PSO.CacheEvictScheme` in
 * ConfigGenerator). PlayStation is therefore anchored to the full `r.PS4` token, and
 * `r.PSO` carries its own ANY rule so the collision is documented rather than latent.
 */
private val SCOPE_RULES =
    listOf(
        // ── Desktop / Windows ──
        CvarScopeRule(
            "_pc",
            TokenMatch.SUFFIX,
            PlatformScope.PC,
            "PC-only variant",
        ), // 3: r.ForceOpaqueInPreZ_PC, r.Kuro.GlobalLightQuality_PC, r.Kuro.GlobalLightShadowQuality_PC
        CvarScopeRule(
            "r.d3d",
            TokenMatch.PREFIX,
            PlatformScope.PC,
            "DirectX-only",
        ), // 4
        CvarScopeRule(
            "r.shadermodel",
            TokenMatch.PREFIX,
            PlatformScope.PC,
            "shader-model specific",
        ), // 0 in corpus today; present for forward-compat

        // ── Apple ──
        CvarScopeRule(
            "r.metal",
            TokenMatch.PREFIX,
            PlatformScope.APPLE,
            "Metal-only",
        ), // 8 (r.Metal.* and r.metal.*)

        // ── Console ──
        CvarScopeRule(
            "r.ps4",
            TokenMatch.PREFIX,
            PlatformScope.CONSOLE,
            "PlayStation 4 only",
        ), // 1: r.PS4MixedModeShaderDebugInfo
        CvarScopeRule(
            "r.ps5",
            TokenMatch.PREFIX,
            PlatformScope.CONSOLE,
            "PlayStation 5 only",
        ), // 0 today
        CvarScopeRule(
            "r.xsx",
            TokenMatch.PREFIX,
            PlatformScope.CONSOLE,
            "Xbox Series only",
        ), // 0 today

        // ── Graphics API ──
        CvarScopeRule(
            "r.vulkan",
            TokenMatch.PREFIX,
            PlatformScope.VULKAN,
            "Vulkan-only (device is not on Vulkan)",
        ), // 84
        CvarScopeRule(
            "r.opengl",
            TokenMatch.PREFIX,
            PlatformScope.OPENGL,
            "OpenGL-only (device is not on OpenGL)",
        ), // 32
        CvarScopeRule(
            "r.metal.",
            TokenMatch.PREFIX,
            PlatformScope.APPLE,
            "Metal-only",
        ),

        // ── Explicit ANY anchors, listed to document what must NOT be flagged ──
        CvarScopeRule(
            "r.pso",
            TokenMatch.PREFIX,
            PlatformScope.ANY,
            "Pipeline State Object (API-agnostic)",
        ), // 19+ — collides with the PlayStation rules above, and is valid on Android
        CvarScopeRule(
            "r.mobile",
            TokenMatch.PREFIX,
            PlatformScope.ANY,
            "Mobile renderer",
        ), // 250 — the single largest family, and the one most at risk from a loose rule
    )

/**
 * Engine-generation-gated CVars.
 *
 * Wuthering Waves ships a UE4 build (the on-device path is `.../UE4Game/...`, visible
 * in the device log), so UE5-only CVars are inert. There is no engine-version signal
 * in [LogInfo] today, so this is a hardcoded fact about the target rather than a
 * detected condition.
 *
 * TODO(engine-detect): parse the engine version out of the client log
 * (`LogInit: Build: ++UE4+...`) and gate this on it instead of assuming UE4.
 *
 * The list is deliberately per-CVar and NOT a `r.temporalaa.` prefix: that family is
 * mostly legitimate UE4 (`r.TemporalAA.Sharpness`, `.MobileFrameWeight`,
 * `.PauseCorrect`, `.FilterSize`) and prefix-matching would have marked ~9 working
 * CVars dead. All three entries below appear in the engine's own
 * "not recognised" list.
 */
private val UE5_ONLY_CVARS =
    setOf(
        "r.temporalaa.upsampling",
        "r.temporalaa.algorithm",
        "r.temporalaacatmullrom",
    )

/**
 * Derives the target platform from a parsed client log.
 *
 * Uses only signals [LogInfo] already carries: the render API (`apiFromRhiToken` /
 * `apiFromFlags` in LogParser already resolve these to exactly
 * `"Vulkan" | "OpenGL ES" | "DirectX" | "Metal"`) and `androidVersion`, which is what
 * separates Android from desktop Linux — both can report Vulkan.
 *
 * Returns [TargetPlatform.UNKNOWN] whenever the API cannot be determined, so a
 * generate without an uploaded log marks nothing.
 */
fun detectPlatform(info: LogInfo): TargetPlatform {
    val api = (info.api ?: info.gameApi)?.trim().orEmpty()
    if (api.isEmpty()) return TargetPlatform.UNKNOWN
    val isAndroid = info.androidVersion != null
    return when (api.lowercase()) {
        "opengl es", "opengles", "opengl" ->
            if (isAndroid) TargetPlatform.ANDROID_GLES else TargetPlatform.UNKNOWN
        "vulkan" ->
            if (isAndroid) TargetPlatform.ANDROID_VULKAN else TargetPlatform.LINUX_VULKAN
        "directx", "d3d11", "d3d12" -> TargetPlatform.WINDOWS
        "metal" -> if (isAndroid) TargetPlatform.UNKNOWN else TargetPlatform.APPLE
        else -> TargetPlatform.UNKNOWN
    }
}

/** True when [scope] can never be honoured by [target]. */
private fun scopeExcluded(
    scope: PlatformScope,
    target: TargetPlatform,
): Boolean =
    when (scope) {
        PlatformScope.ANY -> false
        PlatformScope.PC -> target !in setOf(TargetPlatform.WINDOWS)
        PlatformScope.APPLE -> target != TargetPlatform.APPLE
        PlatformScope.CONSOLE -> target !in setOf(TargetPlatform.WINDOWS, TargetPlatform.APPLE)
        PlatformScope.VULKAN -> target != TargetPlatform.ANDROID_VULKAN && target != TargetPlatform.LINUX_VULKAN
        PlatformScope.OPENGL -> target != TargetPlatform.ANDROID_GLES
    }

/**
 * Classifies one CVar name against [target].
 *
 * Fails safe in two directions: an [TargetPlatform.UNKNOWN] target, and any name no
 * rule matches, both come back [CvarVerdict.Alive]. A CVar is only ever called dead
 * when a rule's scope *provably* excludes the detected platform.
 */
fun classifyCvar(
    name: String,
    target: TargetPlatform,
): CvarVerdict {
    if (target == TargetPlatform.UNKNOWN) return CvarVerdict.Alive
    val lower = name.trim().lowercase()

    if (lower in UE5_ONLY_CVARS) {
        return CvarVerdict.Dead(PlatformScope.PC, "UE5-only CVar; this is a UE4 build")
    }

    // Longest token wins so `r.metal.` (specific) beats a hypothetical `r.met`.
    var best: CvarScopeRule? = null
    for (rule in SCOPE_RULES) {
        val matched =
            when (rule.match) {
                TokenMatch.PREFIX -> lower.startsWith(rule.token)
                TokenMatch.SUFFIX -> lower.endsWith(rule.token)
                TokenMatch.EXACT -> lower == rule.token
            }
        if (matched && (best == null || rule.token.length > best.token.length)) best = rule
    }

    val rule = best ?: return CvarVerdict.Alive
    if (rule.scope == PlatformScope.ANY) return CvarVerdict.Alive
    if (!scopeExcluded(rule.scope, target)) return CvarVerdict.Alive
    return CvarVerdict.Dead(rule.scope, rule.reason)
}
