package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaPoolType
import com.wuwaconfig.app.model.GachaRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lifetime aggregates over a retained gacha history.
 *
 * These only became computable once [GachaHistoryStore] stopped deleting itself on
 * a 12-hour TTL, which is why this file did not exist before.
 */
class GachaStatsTest {
    private fun record(
        pool: GachaPoolType,
        name: String,
        quality: Int = 5,
        count: Int = 1,
        time: String = "2026-01-01",
        resourceId: Int = 0,
        resourceType: String = "",
    ) = GachaRecord(
        cardPoolType = pool.type,
        qualityLevel = quality,
        name = name,
        count = count,
        time = time,
        resourceId = resourceId,
        resourceType = resourceType,
    )

    private fun data(vararg records: GachaRecord) = GachaData(records = records.toList())

    // ─────────── totals ───────────

    @Test
    fun `an empty history aggregates to zeroes`() {
        val r = GachaStats.aggregate(GachaData())
        assertEquals(0, r.totalPulls)
        assertEquals(0L, r.totalCurrency)
        assertEquals(0.0, r.averageFiveStarPity, 0.0001)
        assertEquals(0.0, r.fiftyFiftyWinRate, 0.0001)
        assertEquals("", r.firstPullTime)
        assertTrue(r.topFiveStars.isEmpty())
    }

    @Test
    fun `pull totals weigh the collapsed count, not the row count`() {
        // The endpoint collapses a 10-pull into one row with count=10.
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.CHARACTER_EVENT, "A", quality = 3, count = 10),
                ),
            )
        assertEquals("one row is ten pulls", 10, r.totalPulls)
        assertEquals(10L * gameProfile().currencyPerPull, r.totalCurrency)
        assertEquals(10, r.threeStarCount)
    }

    @Test
    fun `a zero or negative count is treated as one pull`() {
        val r =
            GachaStats.aggregate(
                data(record(GachaPoolType.CHARACTER_EVENT, "A", quality = 3, count = 0)),
            )
        assertEquals(1, r.totalPulls)
    }

    @Test
    fun `the pull time span is the min and max timestamp`() {
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.CHARACTER_EVENT, "A", quality = 3, time = "2026-03-02"),
                    record(GachaPoolType.CHARACTER_EVENT, "B", quality = 3, time = "2026-01-05"),
                    record(GachaPoolType.CHARACTER_EVENT, "C", quality = 3, time = "2026-02-09"),
                ),
            )
        assertEquals("2026-01-05", r.firstPullTime)
        assertEquals("2026-03-02", r.lastPullTime)
    }

    // ─────────── pity ───────────

    @Test
    fun `average pity is the mean of the completed intervals`() {
        // 10 three-stars then a 5★, twice over. Each interval is 11 pulls, not 10:
        // the pull that hits counts toward the pity it satisfies, which is the
        // convention GachaApi.calculateAvgPity has always used.
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.CHARACTER_EVENT, "A", quality = 3, count = 10),
                    record(GachaPoolType.CHARACTER_EVENT, "A", quality = 5),
                    record(GachaPoolType.CHARACTER_EVENT, "B", quality = 3, count = 10),
                    record(GachaPoolType.CHARACTER_EVENT, "B", quality = 5),
                ),
            )
        assertEquals(11.0, r.averageFiveStarPity, 0.0001)
    }

    @Test
    fun `a trailing run with no 5-star yet is not counted as an interval`() {
        // 10 pulls then a hit (one complete interval of 11), then 40 more with no
        // hit. Counting the open run would fold those 40 into the mean and report a
        // far lower average than the account actually achieved.
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.CHARACTER_EVENT, "A", quality = 3, count = 10),
                    record(GachaPoolType.CHARACTER_EVENT, "A", quality = 5),
                    record(GachaPoolType.CHARACTER_EVENT, "C", quality = 3, count = 40),
                ),
            )
        assertEquals(11.0, r.averageFiveStarPity, 0.0001)
    }

    @Test
    fun `average pity is delegated to the shared GachaApi rule`() {
        // Pins that this object does not carry its own copy of the rule: if
        // GachaApi.calculateAvgPity changes, these two must still agree.
        val records =
            listOf(
                record(GachaPoolType.CHARACTER_EVENT, "A", quality = 3, count = 7),
                record(GachaPoolType.CHARACTER_EVENT, "A", quality = 5),
                record(GachaPoolType.CHARACTER_EVENT, "B", quality = 3, count = 13),
                record(GachaPoolType.CHARACTER_EVENT, "B", quality = 5),
            )
        val r = GachaStats.aggregate(data(*records.toTypedArray()))
        assertEquals(GachaApi.calculateAvgPity(records, 5), r.averageFiveStarPity, 0.0001)
        assertEquals(GachaApi.calculateAvgPity(records, 4), r.averageFourStarPity, 0.0001)
    }

    // ─────────── 50/50 ───────────

    @Test
    fun `the fifty fifty win rate excludes draws the loss guarantee covered`() {
        // Character banner: standard 5★ (a loss), then a guaranteed UP, then an UP.
        // Only the loss and the final UP are 50/50s — the guaranteed one cannot lose,
        // so counting it would report 2/3 instead of 1/2.
        val standardFirst = record(GachaPoolType.STANDARD, "StandardHero")
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.CHARACTER_EVENT, "StandardHero"),
                    record(GachaPoolType.CHARACTER_EVENT, "UpA"),
                    record(GachaPoolType.CHARACTER_EVENT, "UpB"),
                    standardFirst,
                ),
            )
        assertEquals(1.0 / 2.0, r.fiftyFiftyWinRate, 0.0001)
    }

    @Test
    fun `the fifty fifty rate ignores weapon banners and the standard pool`() {
        // A weapon banner is always UP and a standard pull is always off-banner;
        // including either would make the rate meaningless.
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.WEAPON_EVENT, "WeaponUp"),
                    record(GachaPoolType.STANDARD, "StandardHero"),
                    record(GachaPoolType.CHARACTER_EVENT, "UpA"),
                ),
            )
        assertEquals(1.0, r.fiftyFiftyWinRate, 0.0001)
    }

    @Test
    fun `the fifty fifty rate is delegated to the shared GachaApi rule`() {
        val fiveStars =
            listOf(
                record(GachaPoolType.CHARACTER_EVENT, "StandardHero"),
                record(GachaPoolType.CHARACTER_EVENT, "UpA"),
            )
        val r =
            GachaStats.aggregate(
                data(
                    *fiveStars.toTypedArray(),
                    record(GachaPoolType.STANDARD, "StandardHero"),
                ),
            )
        assertEquals(
            GachaApi.fiftyFiftyWinRate(fiveStars, setOf("StandardHero")),
            r.fiftyFiftyWinRate,
            0.0001,
        )
    }

    // ─────────── character vs weapon split ───────────

    @Test
    fun `the split prefers resourceType and falls back to the pool`() {
        val r =
            GachaStats.aggregate(
                data(
                    // Labelled a character despite sitting in a weapon pool: the
                    // endpoint's own label wins.
                    record(GachaPoolType.WEAPON_EVENT, "Sword", resourceType = "角色", resourceId = 5),
                    // No label, so the pool decides.
                    record(GachaPoolType.WEAPON_EVENT, "Axe"),
                    record(GachaPoolType.CHARACTER_EVENT, "UpA"),
                ),
            )
        assertEquals("Sword and UpA", 2, r.characterPulls)
        assertEquals(2, r.characterFiveStarItems)
        assertEquals("Axe only", 1, r.weaponPulls)
        assertEquals(1, r.weaponFiveStarItems)
    }

    @Test
    fun `a cache written before resourceType existed still splits by pool`() {
        // Every record here has a blank resourceType, which is exactly the shape
        // Gson produces from JSON written before the field was added. Without the
        // pool fallback the whole split would read zero after an upgrade.
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.CHARACTER_EVENT, "UpA", resourceType = ""),
                    record(GachaPoolType.WEAPON_EVENT, "Sword", resourceType = ""),
                ),
            )
        assertEquals(1, r.characterPulls)
        assertEquals(1, r.weaponPulls)
    }

    // ─────────── top items ───────────

    @Test
    fun `top items group by name, sum the count, and sort descending`() {
        val items =
            GachaStats.topItems(
                listOf(
                    record(GachaPoolType.CHARACTER_EVENT, "A", count = 3),
                    record(GachaPoolType.WEAPON_EVENT, "B", count = 7),
                    record(GachaPoolType.CHARACTER_EVENT, "A", count = 2),
                    record(GachaPoolType.CHARACTER_EVENT, "C", count = 5, quality = 4),
                ),
                rarity = 5,
            )
        assertEquals(listOf("B", "A"), items.map { it.name })
        assertEquals(7, items.first().count)
        assertEquals(5, items[1].count)
    }

    @Test
    fun `top items honour the limit and reject a nonsense one`() {
        val records =
            (1..10).map { record(GachaPoolType.CHARACTER_EVENT, "Hero$it") }
        assertEquals(3, GachaStats.topItems(records, rarity = 5, limit = 3).size)
        assertTrue(GachaStats.topItems(records, rarity = 5, limit = 0).isEmpty())
        assertTrue(GachaStats.topItems(records, rarity = 5, limit = -1).isEmpty())
    }

    @Test
    fun `a later row supplies the resource id an earlier one lacked`() {
        // 0 means "the endpoint did not say", so a later row carrying the real id
        // must win rather than being discarded.
        val items =
            GachaStats.topItems(
                listOf(
                    record(GachaPoolType.CHARACTER_EVENT, "A", resourceId = 0),
                    record(GachaPoolType.CHARACTER_EVENT, "A", resourceId = 1234),
                ),
                rarity = 5,
            )
        assertEquals(1, items.size)
        assertEquals(1234, items.first().resourceId)
        assertEquals(2, items.first().count)
    }

    @Test
    fun `a known resource id is not overwritten by a later zero`() {
        val items =
            GachaStats.topItems(
                listOf(
                    record(GachaPoolType.CHARACTER_EVENT, "A", resourceId = 99),
                    record(GachaPoolType.CHARACTER_EVENT, "A", resourceId = 0),
                ),
                rarity = 5,
            )
        assertEquals(99, items.first().resourceId)
    }

    @Test
    fun `top items filter by rarity`() {
        val records =
            listOf(
                record(GachaPoolType.CHARACTER_EVENT, "Five", quality = 5),
                record(GachaPoolType.CHARACTER_EVENT, "Four", quality = 4),
                record(GachaPoolType.CHARACTER_EVENT, "Three", quality = 3),
            )
        assertEquals(listOf("Five"), GachaStats.topItems(records, 5).map { it.name })
        assertEquals(listOf("Four"), GachaStats.topItems(records, 4).map { it.name })
    }

    // ─────────── UP 5★ count ───────────

    @Test
    fun `UP five stars exclude the standard pool ones`() {
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.CHARACTER_EVENT, "UpA"),
                    record(GachaPoolType.CHARACTER_EVENT, "UpB", count = 2),
                    record(GachaPoolType.STANDARD, "StandardHero"),
                    record(GachaPoolType.STANDARD, "OtherStandard", count = 3),
                ),
            )
        assertEquals("UpA 1 + UpB 2", 3, r.upFiveStarCount)
        // The two standard rows contribute 1 + 3, so the total is 7 rather than a row count of 4.
        assertEquals(7, r.fiveStarCount)
    }

    @Test
    fun `every five star being standard leaves no UP five stars`() {
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.STANDARD, "StandardHero"),
                    record(GachaPoolType.STANDARD, "OtherStandard"),
                ),
            )
        assertEquals(0, r.upFiveStarCount)
    }

    // ─────────── per-kind pity ───────────

    @Test
    fun `kind pity uses only that side's records`() {
        // A character-heavy account must not report a weapon average it never earned.
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.CHARACTER_EVENT, "UpA", quality = 3, count = 10),
                    record(GachaPoolType.CHARACTER_EVENT, "UpA", quality = 5),
                    record(GachaPoolType.WEAPON_EVENT, "Sword", quality = 3, count = 30),
                    record(GachaPoolType.WEAPON_EVENT, "Sword", quality = 5),
                ),
            )
        assertEquals(11.0, r.characterAverageFiveStarPity, 0.0001)
        assertEquals(31.0, r.weaponAverageFiveStarPity, 0.0001)
        assertEquals(
            "the global average spans both sides",
            (11.0 + 31.0) / 2.0,
            r.averageFiveStarPity,
            0.0001,
        )
    }

    @Test
    fun `kind pity is zero when that side has no five stars`() {
        val r =
            GachaStats.aggregate(
                data(
                    record(GachaPoolType.CHARACTER_EVENT, "UpA", quality = 3, count = 5),
                    record(GachaPoolType.CHARACTER_EVENT, "UpA", quality = 5),
                ),
            )
        // 5 three-stars then the 5★ that satisfies them, so the interval is 6.
        assertEquals(6.0, r.characterAverageFiveStarPity, 0.0001)
        assertEquals(0.0, r.weaponAverageFiveStarPity, 0.0001)
    }

    // ─────────── kind classification and filtering ───────────

    @Test
    fun `kind reads the resourceType label when present`() {
        // `kind` is a member-extension on GachaRecord inside the object, so the
        // dispatch receiver has to be in scope to call it.
        with(GachaStats) {
            // English labels, as the 3.7.0 endpoint actually returns them.
            assertEquals(GachaItemKind.CHARACTER, record(GachaPoolType.WEAPON_EVENT, "Sword", resourceType = "Resonator").kind())
            assertEquals(GachaItemKind.WEAPON, record(GachaPoolType.CHARACTER_EVENT, "UpA", resourceType = "Weapon").kind())
            // The Chinese spellings stay supported: the field is language-dependent.
            assertEquals(GachaItemKind.CHARACTER, record(GachaPoolType.WEAPON_EVENT, "Sword", resourceType = "角色").kind())
            assertEquals(GachaItemKind.WEAPON, record(GachaPoolType.CHARACTER_EVENT, "UpA", resourceType = "武器").kind())
            // An unrecognised label falls through to the pool rather than guessing.
            assertEquals(GachaItemKind.UNKNOWN, record(GachaPoolType.BEGINNER_1, "X", resourceType = "Item").kind())
        }
    }

    @Test
    fun `kind falls back to the pool when the label is absent`() {
        with(GachaStats) {
            assertEquals(GachaItemKind.CHARACTER, record(GachaPoolType.CHARACTER_EVENT, "UpA").kind())
            assertEquals(GachaItemKind.WEAPON, record(GachaPoolType.WEAPON_2, "Sword").kind())
            // A beginner pool is neither, which is a real outcome and not a failure.
            assertEquals(GachaItemKind.UNKNOWN, record(GachaPoolType.BEGINNER_1, "Anyone").kind())
            assertEquals(GachaItemKind.UNKNOWN, record(GachaPoolType.STANDARD, "StandardHero").kind())
        }
    }

    @Test
    fun `top items filter to a kind and keep unknown ones when unfiltered`() {
        val records =
            listOf(
                record(GachaPoolType.CHARACTER_EVENT, "UpA"),
                record(GachaPoolType.WEAPON_EVENT, "Sword"),
                record(GachaPoolType.BEGINNER_1, "Starter"),
            )
        assertEquals(listOf("UpA"), GachaStats.topItems(records, 5, kind = GachaItemKind.CHARACTER).map { it.name })
        assertEquals(listOf("Sword"), GachaStats.topItems(records, 5, kind = GachaItemKind.WEAPON).map { it.name })
        assertEquals(
            "unfiltered must not drop the beginner-pool item",
            listOf("UpA", "Sword", "Starter"),
            GachaStats.topItems(records, 5, kind = null).map { it.name },
        )
    }

    @Test
    fun `a ranked item reports its own kind`() {
        val items =
            GachaStats.topItems(
                listOf(
                    record(GachaPoolType.CHARACTER_EVENT, "UpA"),
                    record(GachaPoolType.WEAPON_EVENT, "Sword"),
                ),
                rarity = 5,
            )
        assertEquals(GachaItemKind.CHARACTER, items.first { it.name == "UpA" }.kind)
        assertEquals(GachaItemKind.WEAPON, items.first { it.name == "Sword" }.kind)
    }

    // ─────────── rarity counts ───────────

    @Test
    fun `rarity counts weight the collapsed count and skip empty levels`() {
        val counts =
            GachaStats.rarityCounts(
                listOf(
                    record(GachaPoolType.CHARACTER_EVENT, "A", quality = 3, count = 10),
                    record(GachaPoolType.CHARACTER_EVENT, "B", quality = 3, count = 2),
                    record(GachaPoolType.CHARACTER_EVENT, "C", quality = 5),
                ),
            )
        assertEquals(12, counts[3])
        assertEquals(1, counts[5])
        // A level with no pulls is absent, not zero: the donut must not draw it.
        assertNull(counts[4])
    }

    @Test
    fun `rarity counts of an empty history are empty`() {
        assertTrue(GachaStats.rarityCounts(emptyList()).isEmpty())
    }

    // ─────────── top items across every rarity ───────────

    @Test
    fun `a non-positive rarity returns every level, not nothing`() {
        // The per-banner item grid shows 5★s and 4★s together. Filtering on a single
        // level there would hide most of what the player pulled.
        val records =
            listOf(
                record(GachaPoolType.CHARACTER_EVENT, "Five", quality = 5),
                record(GachaPoolType.CHARACTER_EVENT, "Four", quality = 4),
                record(GachaPoolType.CHARACTER_EVENT, "Three", quality = 3),
            )
        assertEquals(
            listOf("Five", "Four", "Three"),
            GachaStats.topItems(records, rarity = 0).map { it.name },
        )
        assertEquals(
            listOf("Five", "Four", "Three"),
            GachaStats.topItems(records, rarity = -1).map { it.name },
        )
    }

    @Test
    fun `a positive rarity still filters`() {
        val records =
            listOf(
                record(GachaPoolType.CHARACTER_EVENT, "Five", quality = 5),
                record(GachaPoolType.CHARACTER_EVENT, "Four", quality = 4),
            )
        assertEquals(listOf("Five"), GachaStats.topItems(records, rarity = 5).map { it.name })
        assertEquals(listOf("Four"), GachaStats.topItems(records, rarity = 4).map { it.name })
    }
}
