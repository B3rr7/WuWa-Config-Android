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

    fun detect(): ChipsetInfo {
        val soc = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()

        val isSnapdragon =
            soc.contains("sm") || soc.contains("qcom") ||
                board.contains("kalama") || board.contains("shima") ||
                board.contains("lahaina") || board.contains("kona") ||
                soc.contains("sun") || soc.contains("taro") ||
                soc.contains("pitti") || soc.contains("parrot") ||
                soc.contains("crow") || soc.contains("garnet")

        val isMediatek = soc.contains("mt") || manufacturer.contains("mediatek")
        val isExynos = soc.contains("exynos") || board.contains("exynos")
        val isTensor = soc.contains("gs") || soc.contains("tensor") || board.contains("gscaler")

        return ChipsetInfo(
            socName = soc.uppercase(),
            manufacturer = manufacturer,
            board = board,
            isSnapdragon = isSnapdragon,
            isMediatek = isMediatek,
            isExynos = isExynos,
            isTensor = isTensor,
        )
    }
}
