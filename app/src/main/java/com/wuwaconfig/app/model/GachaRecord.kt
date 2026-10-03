package com.wuwaconfig.app.model

/**
 * One gacha pull as the Kuro record endpoint returns it.
 *
 * [resourceId] and [resourceType] were absent until the record id was needed to
 * resolve a pull to an avatar and to split character pulls from weapon pulls.
 * Both default rather than being required because Gson populates a cache written
 * by an older build: a field the endpoint omits, or one absent from JSON already
 * on disk, has to hydrate to something inert instead of throwing. Zero means
 * "the endpoint did not say", which is not the same as resource id 0.
 */
data class GachaRecord(
    val cardPoolType: String,
    val qualityLevel: Int,
    val name: String,
    val count: Int,
    val time: String,
    /**
     * Stable in-game id of the pulled character or weapon, e.g. 1109 for a
     * character or 21050046 for a weapon. This is what keys an avatar lookup.
     * Zero means the endpoint did not supply one.
     */
    val resourceId: Int = 0,
    /**
     * What was pulled, as the endpoint spells it. The 3.7.0 global record endpoint
     * returns the English `Resonator` / `Weapon` / `Item`; other regions may return
     * the Chinese `角色` / `武器`, so consumers must accept both.
     */
    val resourceType: String = "",
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
    /**
     * Pity/cost fields default to the values in the game profile rather than to
     * literals. They are deliberately *defaults* and not constants: a
     * `PityPrediction` deserialized from a cached gacha response was produced
     * under whatever economy was configured at the time, and re-defaulting those
     * on a later launch would silently restate a historical prediction using
     * today's numbers.
     */
    val hardPity: Int = com.wuwaconfig.app.config.GameProfile.get().hardPity,
    val softPityThreshold: Int = com.wuwaconfig.app.config.GameProfile.get().softPityStart,
    val isInSoftPity: Boolean = false,
    val pullsUntilHardPity: Int = com.wuwaconfig.app.config.GameProfile.get().hardPity,
    val pullsSinceLastFourStar: Int = 0,
    val estimatedNextFourStar: Int = com.wuwaconfig.app.config.GameProfile.get().fourStarGuarantee,
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
    /**
     * When the underlying records were fetched, as epoch milliseconds. Zero for
     * caches written before this field existed, which is why [isStale] treats
     * zero as "unknown age" rather than "infinitely old".
     */
    val fetchedAt: Long = 0L,
    /**
     * When this entry stops being kept, as epoch milliseconds. Derived from
     * `gachaHistoryRetentionHours`, and deliberately *not* the freshness window:
     * the file is now history, so it outlives the point where it stops being a
     * current snapshot. See [com.wuwaconfig.app.config.GachaHistoryStore].
     */
    val expiresAt: Long,
    val totalPulls: Int,
    val fiveStars: Int,
    /**
     * The full [GachaData] as JSON. Not a credential: the Convene URL's
     * `record_id` lives only in memory and is never written here.
     */
    val fullDataJson: String,
)
