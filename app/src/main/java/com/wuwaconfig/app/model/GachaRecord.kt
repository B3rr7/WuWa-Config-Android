package com.wuwaconfig.app.model

data class GachaRecord(
    val cardPoolType: String,
    val qualityLevel: Int,
    val resourceType: String,
    val name: String,
    val count: Int,
    val time: String,
)

data class GachaPool(
    val type: String,
    val label: String,
) {
    companion object {
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
    val pullsSinceLastFive: Int,
    val estimatedNextFive: Int,
    val hardPity: Int = 80,
    val softPityThreshold: Int = 66,
    val isInSoftPity: Boolean = false,
    val pullsUntilHardPity: Int = 80,
    val pullsSinceLastFourStar: Int = 0,
    val estimatedNextFourStar: Int = 10,
)

data class GachaApiResponse(
    val code: Int = -1,
    val message: String = "",
    val data: List<GachaRecord>? = null,
)

data class GachaHistoryEntry(
    val id: String,
    val createdAt: Long,
    val expiresAt: Long,
    val totalPulls: Int,
    val fiveStars: Int,
    val fourStars: Int,
    val avgPity5: Double,
    val avgPity4: Double,
    val predictions: List<PityPrediction>,
    val fullDataJson: String,
)
