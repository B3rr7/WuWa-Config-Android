package com.wuwaconfig.app.model

data class GachaRecord(
    val cardPoolType: String,
    val qualityLevel: Int,
    val name: String,
    val count: Int,
    val time: String,
)

enum class GachaPoolType(val type: String, val label: String) {
    CHARACTER_EVENT("1", "Character Event"),
    WEAPON_EVENT("2", "Weapon Event"),
    STANDARD("3", "Standard"),
    BEGINNER_1("4", "Beginner 1"),
    BEGINNER_2("5", "Beginner 2"),
    WEAPON_2("6", "Weapon 2"),
    CHARACTER_2("7", "Character 2"),
    STANDARD_2("8", "Standard 2"),
    WEAPON_3("9", "Weapon 3"),
    CHARACTER_3("10", "Character 3"),
    STANDARD_3("11", "Standard 3"),
    ;

    companion object {
        val ALL = values().toList()
        private val typeMap = values().associateBy { it.type }

        fun fromType(type: String): GachaPoolType? = typeMap[type]
    }
}

@Deprecated("Use GachaPoolType enum instead", ReplaceWith("GachaPoolType.fromType(type)?.label ?: type"))
data class GachaPool(
    val type: String,
    val label: String,
) {
    companion object {
        @Deprecated("Use GachaPoolType.ALL instead")
        val ALL =
            listOf(
                GachaPool("1", "Character Event"),
                GachaPool("2", "Weapon Event"),
                GachaPool("3", "Standard"),
                GachaPool("4", "Beginner 1"),
                GachaPool("5", "Beginner 2"),
                GachaPool("6", "Weapon 2"),
                GachaPool("7", "Character 2"),
                GachaPool("8", "Standard 2"),
                GachaPool("9", "Weapon 3"),
                GachaPool("10", "Character 3"),
                GachaPool("11", "Standard 3"),
            )
    }
}

data class GachaData(
    val records: List<GachaRecord> = emptyList(),
    val poolsWithData: List<String> = emptyList(),
    val totalPulls: Int = 0,
    val fiveStars: Int = 0,
    val fourStars: Int = 0,
    val avgPity5: Double = 0.0,
    val avgPity4: Double = 0.0,
    val predictions: List<PityPrediction> = emptyList(),
)

data class PityPrediction(
    val poolType: String,
    val poolLabel: String,
    val status: String,
    val lastFiveStarName: String,
    val lastFiveStarTime: String,
    val currentFeaturedName: String = "",
    val currentFeaturedKnown: Boolean = false,
    val pullsSinceLastFive: Int,
    val estimatedNextFive: Int,
    val hardPity: Int = 80,
    val softPityThreshold: Int = 66,
    val isInSoftPity: Boolean = false,
    val pullsUntilHardPity: Int = 80,
    val pullsSinceLastFourStar: Int = 0,
    val estimatedNextFourStar: Int = 10,
    val avgPityThisPool: Double = 0.0,
    val nonBannerRate: Double = 0.0,
    val upRate: Double = 0.0,
    val firstPullDate: String = "",
    val lastPullDate: String = "",
    val ssrIntervals: List<SsrInterval> = emptyList(),
    val totalCost: Long = 0,
    val minPity5: Int = 0,
    val maxPity5: Int = 0,
    val minPity4: Int = 0,
    val maxPity4: Int = 0,
    val isPoolActive: Boolean = true,
)

data class SsrInterval(
    val name: String,
    val count: Int,
    val time: String,
    val pity: Int,
)

data class GachaApiResponse(
    val code: Int = -1,
    val message: String = "",
    val data: List<GachaRecord>? = null,
)

data class GachaHistoryEntry(
    val id: String,
    val expiresAt: Long,
    val totalPulls: Int,
    val fiveStars: Int,
    val fullDataJson: String,
)
