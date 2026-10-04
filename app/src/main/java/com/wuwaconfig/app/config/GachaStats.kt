package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaPoolType
import com.wuwaconfig.app.model.GachaRecord

/**
 * Lifetime aggregates over a whole retained pull history.
 *
 * Where [com.wuwaconfig.app.model.PityPrediction] answers "where is this pool right
 * now", this answers "what does the account's whole record look like" — totals,
 * average pity, 50/50 win rate, the character/weapon split. Those are only
 * meaningful over a record set that accumulates, which is why they were impossible
 * while [GachaHistoryStore] deleted itself on a 12-hour TTL.
 *
 * Deliberately a standalone `object` with no Android dependency, for the same
 * reason [CvarCategorizer] is: this is pure logic over a list and belongs under
 * test rather than behind a device.
 *
 * Every pity or 50/50 figure is delegated to [GachaApi] rather than recomputed.
 * `GachaApi` already carries the canonical rules — including two that disagree
 * with each other on purpose (`calculateAvgPity` closes a 4★ group only on a 4★,
 * while `computeMinMaxPity` also accepts a 5★) — and a second copy here would be a
 * third answer to the same question.
 */
object GachaStats {
    /**
     * The `resourceType` labels the record endpoint actually returns.
     *
     * These were originally assumed to be the Chinese `角色` / `武器`, on the strength
     * of WutheringWavesTool's own code and a report that the field looked empty. Both
     * assumptions were wrong, and only a real fetch proved it: on a 3.7.0 global
     * account the endpoint returns the **English** `Resonator` and `Weapon` (and an
     * `Item` for other rows). The Chinese spellings are kept because the field is
     * region- and language-dependent and a CN account plausibly returns those, so
     * matching one alone would silently degrade that account to the pool fallback.
     */
    private val TYPE_CHARACTER_LABELS = setOf("Resonator", "角色")

    private val TYPE_WEAPON_LABELS = setOf("Weapon", "武器")

    private val CHARACTER_POOL_TYPES =
        setOf(GachaPoolType.CHARACTER_EVENT, GachaPoolType.CHARACTER_2, GachaPoolType.CHARACTER_3)
    private val WEAPON_POOL_TYPES =
        setOf(GachaPoolType.WEAPON_EVENT, GachaPoolType.WEAPON_2, GachaPoolType.WEAPON_3)
    private val STANDARD_POOL_TYPES =
        setOf(GachaPoolType.STANDARD, GachaPoolType.STANDARD_2, GachaPoolType.STANDARD_3)

    private val characterPoolTypeNames = CHARACTER_POOL_TYPES.map { it.type }.toSet()
    private val weaponPoolTypeNames = WEAPON_POOL_TYPES.map { it.type }.toSet()
    private val standardPoolTypeNames = STANDARD_POOL_TYPES.map { it.type }.toSet()

    /**
     * Aggregates [data]'s records.
     *
     * `count` is the endpoint's collapse count — a 10-pull arrives as one record
     * with `count = 10` — so every pull figure sums `count` rather than counting
     * rows, the same rule [GachaApi.fetchAllRecords] applies.
     *
     * Five-star counts are summed by `count` while the character/weapon split counts
     * *rows*. That asymmetry is inherited, not introduced: the split answers "how
     * many distinct 5★ items came from character banners", for which a collapsed
     * row is one item, whereas a pull total must weigh the collapse.
     */
    fun aggregate(data: GachaData): GachaStatsResult {
        val records = data.records
        val totalPulls = records.sumOf { it.count.coerceAtLeast(1) }
        val fiveStarRecords = records.filter { it.qualityLevel == 5 }

        // A 5★ drawn in a standard pool is a standard 5★ by definition — that is
        // what makes a character banner's next pull "Guaranteed". Derived from the
        // data rather than a hardcoded id list, so it stays correct as the roster
        // changes.
        val standardFiveStarNames =
            records
                .filter { it.cardPoolType in standardPoolTypeNames && it.qualityLevel == 5 }
                .map { it.name }
                .toSet()

        // The same draws as objects rather than names, so the UI can split the
        // permanent pool into its character and weapon sides and render each with
        // the right portrait. Names alone cannot do that -- a name does not say
        // whether it came off a character or a weapon banner.
        val standardFiveStarItems =
            records
                .filter { it.cardPoolType in standardPoolTypeNames && it.qualityLevel == 5 }
                .groupBy { it.name }
                .map { (name, group) ->
                    GachaItemCount(
                        name = name,
                        kind = group.first().kind(),
                        resourceId = group.first().resourceId,
                        // Times pulled, not times seen: a collapsed 10-pull that
                        // yielded the same standard 5★ twice counts twice.
                        count = group.sumOf { it.count.coerceAtLeast(1) },
                    )
                }.sortedBy { it.name }

        val characterFiveStars = fiveStarRecords.filter { it.belongsTo(GachaItemKind.CHARACTER) }
        val weaponFiveStars = fiveStarRecords.filter { it.belongsTo(GachaItemKind.WEAPON) }
        // "UP" means a 5★ that is not a standard one, i.e. a banner pull rather than
        // an off-banner standard draw. Derived from the same standard set that
        // drives the 50/50 verdict, so the two can never disagree about which 5★s
        // were featured. Weighted by `count` so it is comparable with
        // [GachaStatsResult.fiveStarCount] rather than a row count.
        val upFiveStarCount =
            fiveStarRecords
                .filter { it.name !in standardFiveStarNames }
                .sumOf { it.count.coerceAtLeast(1) }

        return GachaStatsResult(
            totalPulls = totalPulls,
            totalCurrency = totalPulls.toLong() * gameProfile().currencyPerPull,
            fiveStarCount = fiveStarRecords.sumOf { it.count.coerceAtLeast(1) },
            fourStarCount = records.filter { it.qualityLevel == 4 }.sumOf { it.count.coerceAtLeast(1) },
            threeStarCount = records.filter { it.qualityLevel == 3 }.sumOf { it.count.coerceAtLeast(1) },
            averageFiveStarPity = GachaApi.calculateAvgPity(records, 5),
            averageFourStarPity = GachaApi.calculateAvgPity(records, 4),
            // Only character banners have a 50/50 to lose. A weapon banner is 100%
            // UP and a standard pool is off-banner by definition, so including
            // either would drag the rate somewhere meaningless.
            fiftyFiftyWinRate =
                GachaApi.fiftyFiftyWinRate(
                    fiveStarRecords.filter { it.cardPoolType in characterPoolTypeNames },
                    standardFiveStarNames,
                ),
            firstPullTime = records.mapNotNull { it.time.takeIf { t -> t.isNotBlank() } }.minOrNull().orEmpty(),
            lastPullTime = records.mapNotNull { it.time.takeIf { t -> t.isNotBlank() } }.maxOrNull().orEmpty(),
            upFiveStarCount = upFiveStarCount,
            characterPulls = records.filter { it.belongsTo(GachaItemKind.CHARACTER) }.sumOf { it.count.coerceAtLeast(1) },
            // Restricted to the side's own records. Reusing the global average here
            // would let a character-heavy account report a weapon pity it never
            // earned, which is the kind of plausible-but-wrong figure that survives
            // review because it is merely uninteresting when false.
            characterAverageFiveStarPity = GachaApi.calculateAvgPity(records.filter { it.belongsTo(GachaItemKind.CHARACTER) }, 5),
            weaponAverageFiveStarPity = GachaApi.calculateAvgPity(records.filter { it.belongsTo(GachaItemKind.WEAPON) }, 5),
            characterFiveStarItems = characterFiveStars.size,
            weaponPulls = records.filter { it.belongsTo(GachaItemKind.WEAPON) }.sumOf { it.count.coerceAtLeast(1) },
            weaponFiveStarItems = weaponFiveStars.size,
            topFiveStars = topItems(records, 5),
            topFourStars = topItems(records, 4),
            standardFiveStars = standardFiveStarItems,
        )
    }

    /**
     * Pulls per quality level, keyed by `qualityLevel`, ascending.
     *
     * Weights by `count` for the same reason every other pull figure here does: the
     * endpoint collapses a 10-pull into one row. Levels with no pulls are absent
     * rather than zero, so a caller can tell "no 3★s" from "this history has no
     * 3★ rows yet" without a sentinel.
     *
     * Used by the rarity donut, where a zero slice is not a slice.
     */
    fun rarityCounts(records: List<GachaRecord>): Map<Int, Int> {
        val counts = sortedMapOf<Int, Int>()
        for (record in records) {
            val quality = record.qualityLevel
            if (quality <= 0) continue
            counts[quality] = (counts[quality] ?: 0) + record.count.coerceAtLeast(1)
        }
        return counts
    }

    /**
     * The most-pulled items of the given rarity, highest first.
     *
     * Distinct from a pity figure: this answers "which character do you actually
     * pull", which for a 4★ is usually the same handful every time.
     *
     * [rarity] selects one quality level; zero or negative means every level, which
     * is what the per-banner item grid wants — it shows 5★s and 4★s together rather
     * than making the player switch tabs to see what they pulled.
     *
     * [kind] filters to characters or weapons; null keeps both, which is also where
     * a [GachaItemKind.UNKNOWN] item belongs. The endpoint did not label it and its
     * pool was not a banner, so dropping it from an unfiltered ranking would lose
     * real pulls for no reason.
     *
     * [limit] <= 0 returns nothing rather than everything, so a caller cannot
     * accidentally ask for an unbounded list by passing a nonsense bound.
     */
    internal fun topItems(
        records: List<GachaRecord>,
        rarity: Int,
        limit: Int = 5,
        kind: GachaItemKind? = null,
    ): List<GachaItemCount> {
        if (limit <= 0) return emptyList()
        val byName = LinkedHashMap<String, GachaItemCount>()
        for (record in records) {
            if (rarity > 0 && record.qualityLevel != rarity) continue
            if (kind != null && record.kind() != kind) continue
            val existing = byName[record.name]
            byName[record.name] =
                GachaItemCount(
                    name = record.name,
                    kind = record.kind(),
                    // Keep the first id seen. 0 means "the endpoint did not say",
                    // so a later row carrying the real id should replace it rather
                    // than be discarded.
                    resourceId =
                        when {
                            existing == null -> record.resourceId
                            existing.resourceId != 0 -> existing.resourceId
                            else -> record.resourceId
                        },
                    count = (existing?.count ?: 0) + record.count.coerceAtLeast(1),
                )
        }
        return byName.values.sortedByDescending { it.count }.take(limit)
    }

    /**
     * Whether a record is a character or a weapon.
     *
     * Prefers the endpoint's own `resourceType` label and falls back to the pool
     * type. The fallback is not defensive: on a real 3.7.0 account all 544 rows came
     * back with `resourceType` blank, so the pool is what actually carries every
     * character/weapon figure in the app.
     *
     * [GachaItemKind.UNKNOWN] is a real outcome rather than a failure — a beginner
     * or standard pool is neither a character nor a weapon banner.
     */
    internal fun GachaRecord.kind(): GachaItemKind {
        when (resourceType.trim()) {
            in TYPE_CHARACTER_LABELS -> return GachaItemKind.CHARACTER
            in TYPE_WEAPON_LABELS -> return GachaItemKind.WEAPON
        }
        return when (cardPoolType) {
            in characterPoolTypeNames -> GachaItemKind.CHARACTER
            in weaponPoolTypeNames -> GachaItemKind.WEAPON
            else -> GachaItemKind.UNKNOWN
        }
    }

    private fun GachaRecord.belongsTo(kind: GachaItemKind): Boolean = this.kind() == kind
}

/** Lifetime totals over a whole retained history. */
data class GachaStatsResult(
    val totalPulls: Int,
    val totalCurrency: Long,
    val fiveStarCount: Int,
    /** 5★ pulls that were rate-up banner pulls rather than standard-pool ones. */
    val upFiveStarCount: Int,
    val fourStarCount: Int,
    val threeStarCount: Int,
    val averageFiveStarPity: Double,
    val averageFourStarPity: Double,
    val fiftyFiftyWinRate: Double,
    val firstPullTime: String,
    val lastPullTime: String,
    val characterPulls: Int,
    /** Average pulls per 5★ on character records only. Zero when there are none. */
    val characterAverageFiveStarPity: Double,
    val characterFiveStarItems: Int,
    val weaponPulls: Int,
    /** Average pulls per 5★ on weapon records only. Zero when there are none. */
    val weaponAverageFiveStarPity: Double,
    val weaponFiveStarItems: Int,
    val topFiveStars: List<GachaItemCount>,
    val topFourStars: List<GachaItemCount>,
    /**
     * The permanent pool's 5★s, deduplicated by name, split by [GachaItemCount.kind].
     *
     * Scoped to what the player has actually drawn — there is no authoritative
     * roster of the permanent pool anywhere this app can reach, so this is a
     * collection record and not a pool contents list. Sorted by name because there
     * is no meaningful pull order for a set of permanent items.
     */
    val standardFiveStars: List<GachaItemCount>,
) {
    companion object {
        /**
         * The no-history result. Present so callers can return a typed zero instead
         * of null, which the wire schema has no way to express for a non-null
         * object — and so the sixteen fields are written once rather than at each
         * "the player has never fetched" branch.
         *
         * The zeroed averages and rate are genuinely undefined rather than zero,
         * so callers must be told: an empty history is reported as [isStale] by the
         * AppFunction layer for exactly this reason.
         */
        val EMPTY =
            GachaStatsResult(
                totalPulls = 0,
                totalCurrency = 0L,
                fiveStarCount = 0,
                upFiveStarCount = 0,
                fourStarCount = 0,
                threeStarCount = 0,
                averageFiveStarPity = 0.0,
                averageFourStarPity = 0.0,
                fiftyFiftyWinRate = 0.0,
                firstPullTime = "",
                lastPullTime = "",
                characterPulls = 0,
                characterAverageFiveStarPity = 0.0,
                characterFiveStarItems = 0,
                weaponPulls = 0,
                weaponAverageFiveStarPity = 0.0,
                weaponFiveStarItems = 0,
                topFiveStars = emptyList(),
                topFourStars = emptyList(),
                standardFiveStars = emptyList(),
            )
    }
}

/** One item's lifetime pull count. */
data class GachaItemCount(
    val name: String,
    val kind: GachaItemKind,
    val resourceId: Int,
    val count: Int,
)

/** Character, weapon, or neither. See [GachaStats.kind]. */
enum class GachaItemKind {
    CHARACTER,
    WEAPON,
    UNKNOWN,
}
