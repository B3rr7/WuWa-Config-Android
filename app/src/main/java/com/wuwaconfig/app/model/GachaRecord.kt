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

/**
 * The pity *counter* a pool belongs to.
 *
 * Kuro's Convene rules — the same statement repeated in the launch notice and in
 * every banner notice up to 3.7 Phase II — make the count **shared by every
 * banner of the same type and carried over until a 5★ drops**. So the several
 * `cardPoolType` slots a client can query (1 / 7 / 10, etc.) are concurrent or
 * successive *banners*, not separate pity counters: 30 pulls on one and 20 on the
 * next leaves you at 50, exactly as if all 50 had been on one banner.
 *
 * The same rule carries the 50/50 loss: if the last 3★…5★ on the character family
 * was not the featured unit, the next 5★ is guaranteed featured, and that
 * guarantee does not expire at a version or banner change.
 *
 * Grouping keys therefore must be the *family*, not the slot. Grouping by slot
 * under-reports pity on every banner rotation — the common case.
 */
enum class BannerFamily(val label: String) {
    /** Featured Resonator Convenes: 80 hard pity, 50/50, guarantee carries. */
    CHARACTER_EVENT("Featured Resonator"),

    /** Featured Weapon Convenes: 80 hard pity, 100% featured, no 50/50. */
    WEAPON_EVENT("Featured Weapon"),

    /** Permanent pools: Tidal Chorus (characters) / Lustrous Tide (weapons). */
    STANDARD("Standard"),

    /**
     * Novice banners. Kuro's novice banner guarantees a 5★ within **50** pulls and
     * closes once one arrives, so it is the one family whose hard pity is not 80.
     */
    BEGINNER("Novice"),
    ;

    /** True when the family's counter is shared across its slots and carries over. */
    val isShared: Boolean get() = this == CHARACTER_EVENT || this == WEAPON_EVENT

    /** Hard-pity ceiling for this family, from the game profile. */
    val hardPity: Int
        get() = if (this == BEGINNER) BEGINNER_HARD_PITY else com.wuwaconfig.app.config.GameProfile.get().hardPity

    companion object {
        const val BEGINNER_HARD_PITY = 50
    }
}

enum class GachaPoolType(
    val type: String,
    val label: String,
    val family: BannerFamily,
) {
    CHARACTER_EVENT("1", "Character Event", BannerFamily.CHARACTER_EVENT),
    WEAPON_EVENT("2", "Weapon Event", BannerFamily.WEAPON_EVENT),
    STANDARD("3", "Standard", BannerFamily.STANDARD),
    BEGINNER_1("4", "Beginner 1", BannerFamily.BEGINNER),
    BEGINNER_2("5", "Beginner 2", BannerFamily.BEGINNER),
    WEAPON_2("6", "Weapon 2", BannerFamily.WEAPON_EVENT),
    CHARACTER_2("7", "Character 2", BannerFamily.CHARACTER_EVENT),
    STANDARD_2("8", "Standard 2", BannerFamily.STANDARD),
    WEAPON_3("9", "Weapon 3", BannerFamily.WEAPON_EVENT),
    CHARACTER_3("10", "Character 3", BannerFamily.CHARACTER_EVENT),
    STANDARD_3("11", "Standard 3", BannerFamily.STANDARD),
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
     * The banner family this prediction covers. Non-empty for a current record
     * (e.g. `"Featured Resonator"`) and empty for a cache written before
     * families existed.
     */
    val familyLabel: String = "",
    /**
     * True when a lost 50/50 is banked: the next 5★ on this family is guaranteed
     * to be the featured unit. Kuro's rule — the guarantee travels with the pity
     * count across banner and version changes, so it belongs to the family state,
     * not to the banner that happened to be running when the loss occurred.
     */
    val isGuaranteedNext: Boolean = false,
    /**
     * Worst case from now before the *featured* unit is secured, in pulls.
     *
     * This is the number a player budgets against, and it is the one figure the
     * per-pool tracker got wrong. With a banked guarantee it is just the distance
     * to the next 5★; without one it is that distance plus a full 80-pull cycle,
     * because losing the 50/50 starts you over on the guarantee. Kuro's published
     * ceiling for a featured Resonator from a clean slate is 160.
     */
    val pullsUntilGuaranteedFeatured: Int = 0,
    /** [pullsUntilGuaranteedFeatured] at [currencyPerPull], for budgeting. */
    val worstCaseCostAstrite: Long = 0,
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
