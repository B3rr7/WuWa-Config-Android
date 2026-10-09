package com.wuwaconfig.app.config

import android.os.Build

object ChipsetDetector {
    data class ChipsetInfo(
        val socName: String,
        val manufacturer: String,
        val board: String,
        val isSnapdragon: Boolean,
        val isMediatek: Boolean,
        val isExynos: Boolean,
        val isTensor: Boolean,
    )

    /**
     * Classifies a device from the three platform-reported strings.
     *
     * Extracted from [detect] so it is testable. [detect] reads
     * `android.os.Build.HARDWARE` / `BOARD` / `MANUFACTURER`, which are static
     * finals: under `unitTests.isReturnDefaultValues = true` they come back as
     * empty strings, so the classifier could only ever be exercised on a device.
     * That matters because this drives the chipset badge on the Profile screen
     * and feeds the GPU-tier decision.
     *
     * Inputs are lowercased here rather than by the caller, so the function is
     * correct for any casing and [detect] can pass `Build` values straight
     * through.
     *
     * NOTE — the matching is deliberately left as substring `contains` checks.
     * Short tokens like "sm", "mt" and "gs" are fragile (they will match any
     * longer string that happens to contain them), and tightening them to
     * word-boundary or prefix matching would change which devices classify as
     * what. That is a behaviour change, so it is a separate decision; these
     * tests pin today's semantics so the tightening is reviewable.
     *
     * The markers themselves now come from [TuningProfile], keyed per vendor and
     * per Build field. That indirection is what makes the fragility above
     * visible: adding a MediaTek marker can no longer accidentally become a
     * Snapdragon one, because the vendor each marker belongs to is explicit.
     */
    internal fun classify(
        soc: String,
        board: String,
        manufacturer: String,
        hardware: String = "",
    ): ChipsetInfo {
        val s = soc.lowercase()
        val b = board.lowercase()
        val m = manufacturer.lowercase()

        val fields =
            mapOf(
                "soc" to s,
                "board" to b,
                "manufacturer" to m,
                "hardware" to hardware.lowercase(),
            )
        val vendors = TuningProfile.get()

        return ChipsetInfo(
            socName = s.uppercase(),
            manufacturer = m,
            board = b,
            isSnapdragon = vendors.isVendor("snapdragon", fields),
            isMediatek = vendors.isVendor("mediatek", fields),
            isExynos = vendors.isVendor("exynos", fields),
            isTensor = vendors.isVendor("tensor", fields),
        )
    }

    /**
     * The most specific SoC string the platform will give us, or "" if none.
     *
     * `Build.HARDWARE` alone is nearly useless as a chipset name: on a real
     * device it reads `"qcom"` (verified on a moto g(60), whose actual SoC is
     * SM7150 / SM6150), and on MediaTek it is often just the board name. The
     * Profile screen was showing `—` for every user because it sourced the
     * chipset from the game log, which never names the SoC either — the game
     * only logs `CPU: <device model>`.
     *
     * So this walks the sources best-first:
     *  1. `Build.SOC_MODEL` (API 31+) — the real part number, e.g. `SM7150`.
     *  2. `Build.BOARD` — on many Qualcomm devices this is the codename
     *     (`hanoip` on the moto g(60)), so it is a name, just not a part number.
     *
     * `Build.HARDWARE` is deliberately **last**: it is the string the original
     * [classify] call used, and it is the least specific of the three.
     *
     * `Build.SOC_MODEL` is API 31+ and this module has minSdk 26, so it is read
     * reflectively via [Class.forName] rather than referenced directly — a direct
     * reference would be a `NoSuchFieldError` on API 26-30 at class-load time,
     * not a graceful degradation. Lint cannot see through the string, hence the
     * suppression.
     *
     * Returns "" rather than null so callers can treat it as "not reported"
     * alongside the other empty-string fields, matching [classify]'s contract.
     */
    internal fun detectSocNameInternal(): String {
        socModelField?.let { field ->
            runCatching { (field.get(null) as? String)?.takeIf { it.isNotBlank() } }
                .getOrNull()
                ?.let { return it }
        }
        return Build.BOARD.orEmpty()
    }

    /**
     * Resolved once. `Build.SOC_MODEL` is a static final String on API 31+; if
     * the field is absent (API 26-30) or is not a String, this is null and
     * [detectSocNameInternal] falls through to `Build.BOARD`.
     */
    private val socModelField: java.lang.reflect.Field? =
        runCatching {
            Class.forName("android.os.Build").getField("SOC_MODEL").takeIf { it.type == String::class.java }
        }.getOrNull()

    /**
     * Reads the platform-reported strings and classifies them.
     *
     * The `orEmpty()` is not paranoia about the platform API — `Build.HARDWARE`
     * is non-null in production — it is what makes this total under
     * `unitTests.isReturnDefaultValues = true`, where the statics come back as
     * **null** rather than "" and `String.lowercase()` throws. Without it the
     * classifier is untestable, which is the whole reason for the split.
     *
     * [soc] is [detectSocNameInternal] rather than `Build.HARDWARE` so `socName` — the
     * string the Profile screen renders as the chipset — is the real part
     * number. Classification still sees every field via [board]/[manufacturer],
     * and `qcom` remains reachable there via `Build.HARDWARE` below when the
     * platform gives us nothing better.
     */
    fun detect(): ChipsetInfo {
        val hardware = Build.HARDWARE.orEmpty()
        val soc = detectSocNameInternal().ifBlank { hardware }
        return classify(soc, Build.BOARD.orEmpty(), Build.MANUFACTURER.orEmpty(), hardware)
    }
}
