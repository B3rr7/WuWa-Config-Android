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
     */
    internal fun classify(
        soc: String,
        board: String,
        manufacturer: String,
    ): ChipsetInfo {
        val s = soc.lowercase()
        val b = board.lowercase()
        val m = manufacturer.lowercase()

        val isSnapdragon =
            s.contains("sm") || s.contains("qcom") ||
                b.contains("kalama") || b.contains("shima") ||
                b.contains("lahaina") || b.contains("kona") ||
                s.contains("sun") || s.contains("taro") ||
                s.contains("pitti") || b.contains("parrot") ||
                b.contains("crow") || b.contains("garnet")

        val isMediatek = s.contains("mt") || m.contains("mediatek")
        val isExynos = s.contains("exynos") || b.contains("exynos")
        val isTensor = s.contains("gs") || s.contains("tensor") || b.contains("gscaler")

        return ChipsetInfo(
            socName = s.uppercase(),
            manufacturer = m,
            board = b,
            isSnapdragon = isSnapdragon,
            isMediatek = isMediatek,
            isExynos = isExynos,
            isTensor = isTensor,
        )
    }

    /**
     * Reads the three platform-reported strings and classifies them.
     *
     * The `orEmpty()` is not paranoia about the platform API — `Build.HARDWARE`
     * is non-null in production — it is what makes this total under
     * `unitTests.isReturnDefaultValues = true`, where the statics come back as
     * **null** rather than "" and `String.lowercase()` throws. Without it the
     * classifier is untestable, which is the whole reason for the split.
     */
    fun detect(): ChipsetInfo = classify(Build.HARDWARE.orEmpty(), Build.BOARD.orEmpty(), Build.MANUFACTURER.orEmpty())
}
