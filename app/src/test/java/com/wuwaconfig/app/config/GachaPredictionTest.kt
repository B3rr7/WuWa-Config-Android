package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.GachaPool
import com.wuwaconfig.app.model.GachaPoolType
import com.wuwaconfig.app.model.GachaRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GachaPredictionTest {
    private val characterPool = GachaPool(GachaPoolType.CHARACTER_EVENT.type, GachaPoolType.CHARACTER_EVENT.label)
    private val weaponPool = GachaPool(GachaPoolType.WEAPON_EVENT.type, GachaPoolType.WEAPON_EVENT.label)
    private val standardFives = setOf("Jiyan", "Yinlin")

    private fun rec(
        name: String,
        quality: Int,
        time: String,
        pool: String = "1",
        count: Int = 1,
    ) = GachaRecord(
        cardPoolType = pool,
        qualityLevel = quality,
        name = name,
        count = count,
        time = time,
    )

    // ─────────── estimatedSoftPityPulls (piecewise-linear curve) ───────────

    @Test
    fun `estimatedSoftPityPulls at soft pity start gives largest expected value`() {
        // p=66, rate=0.15, expected ~ 7 (matches wuwatracker empirical ~4.7)
        val est = GachaApi.estimatedSoftPityPulls(66, 66, 80)
        assertTrue(
            "expected ~7 pulls, got $est",
            est in 6..9,
        )
    }

    @Test
    fun `estimatedSoftPityPulls decreases through soft pity`() {
        // Int representation has limited resolution in the late-soft-pity region.
        // Verify the value drops monotonically in the steep part of the curve.
        val at66 = GachaApi.estimatedSoftPityPulls(66, 66, 80)
        val at68 = GachaApi.estimatedSoftPityPulls(68, 66, 80)
        val at70 = GachaApi.estimatedSoftPityPulls(70, 66, 80)
        val at73 = GachaApi.estimatedSoftPityPulls(73, 66, 80)
        val at79 = GachaApi.estimatedSoftPityPulls(79, 66, 80)
        assertTrue("at66 ($at66) should be > at68 ($at68)", at66 > at68)
        assertTrue("at68 ($at68) should be > at70 ($at70)", at68 > at70)
        assertTrue("at70 ($at70) should be >= at73 ($at73)", at70 >= at73)
        assertTrue("at73 ($at73) should be >= at79 ($at79)", at73 >= at79)
    }

    @Test
    fun `estimatedSoftPityPulls at hard pity is clamped to 1`() {
        val est = GachaApi.estimatedSoftPityPulls(80, 66, 80)
        assertEquals(1, est)
    }

    @Test
    fun `estimatedSoftPityPulls for weapon banner has same shape`() {
        // All banners (incl. weapon) now use soft 66, hard 80 per wuwatracker data.
        val at66 = GachaApi.estimatedSoftPityPulls(66, 66, 80)
        val at68 = GachaApi.estimatedSoftPityPulls(68, 66, 80)
        val at70 = GachaApi.estimatedSoftPityPulls(70, 66, 80)
        val at73 = GachaApi.estimatedSoftPityPulls(73, 66, 80)
        assertTrue("at66 ($at66) should be > at68 ($at68)", at66 > at68)
        assertTrue("at68 ($at68) should be > at70 ($at70)", at68 > at70)
        assertTrue("at70 ($at70) should be >= at73 ($at73)", at70 >= at73)
        assertEquals(1, GachaApi.estimatedSoftPityPulls(80, 66, 80))
    }

    // ─────────── Character prediction: status logic ───────────

    @Test
    fun `character prediction with no 5stars returns Unknown status`() {
        val records =
            listOf(
                rec("3-star A", 3, "2024-01-01 10:00:00"),
                rec("4-star B", 4, "2024-01-02 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals("Unknown", pred.status)
    }

    @Test
    fun `character prediction with last 5star standard returns Guaranteed`() {
        val records =
            listOf(
                rec("Jiyan", 5, "2024-01-01 10:00:00"),
                rec("4-star", 4, "2024-02-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals("Guaranteed", pred.status)
    }

    @Test
    fun `character prediction with last 5star featured returns 50 over 50`() {
        val records =
            listOf(
                rec("FeaturedChar", 5, "2024-01-01 10:00:00"),
                rec("4-star", 4, "2024-02-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals("50/50", pred.status)
    }

    @Test
    fun `character prediction with last 5star unknown name returns 50 over 50 (not standard)`() {
        val records =
            listOf(
                rec("UnknownNameNotInStandardSet", 5, "2024-01-01 10:00:00"),
                rec("4-star", 4, "2024-02-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals("50/50", pred.status)
    }

    // ─────────── Character prediction: currentCharacterName ───────────

    @Test
    fun `currentCharacterName picks most recent non-standard 5star`() {
        val records =
            listOf(
                rec("OldFeatured", 5, "2024-01-01 10:00:00"),
                // standard 5★, last
                rec("Jiyan", 5, "2024-02-01 10:00:00"),
                rec("4-star", 4, "2024-03-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals("OldFeatured", pred.currentFeaturedName)
    }

    @Test
    fun `currentCharacterName is empty when only standard 5stars exist (e g, 50 over 50 loss)`() {
        val records =
            listOf(
                rec("Jiyan", 5, "2024-01-01 10:00:00"),
                rec("Yinlin", 5, "2024-02-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals("", pred.currentFeaturedName)
        assertFalse(
            "currentFeaturedKnown should be false when no recent featured ★5 exists",
            pred.currentFeaturedKnown,
        )
    }

    @Test
    fun `currentCharacterName and currentFeaturedKnown align with status`() {
        // Guaranteed (last 5★ standard) but we have a previous featured 5★
        val records =
            listOf(
                rec("Featured1", 5, "2024-01-01 10:00:00"),
                rec("Jiyan", 5, "2024-02-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals("Guaranteed", pred.status)
        assertEquals("Featured1", pred.currentFeaturedName)
        assertTrue(
            "currentFeaturedKnown should be true when a featured ★5 exists",
            pred.currentFeaturedKnown,
        )
    }

    // ─────────── Character prediction: pullsSinceLastFive ───────────

    @Test
    fun `pullsSinceLastFive counts records after the last 5star`() {
        val records =
            listOf(
                rec("Featured1", 5, "2024-01-01 10:00:00"),
                rec("3-star A", 3, "2024-02-01 10:00:00"),
                rec("3-star B", 3, "2024-03-01 10:00:00"),
                rec("4-star C", 4, "2024-04-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals(3, pred.pullsSinceLastFive)
    }

    @Test
    fun `pullsSinceLastFive honors count for collapsed 10pulls`() {
        val records =
            listOf(
                rec("Featured1", 5, "2024-01-01 10:00:00"),
                // one record, 10 pulls
                rec("3-star A", 3, "2024-02-01 10:00:00", count = 10),
                rec("4-star C", 4, "2024-03-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals(11, pred.pullsSinceLastFive)
    }

    @Test
    fun `pullsSinceLastFive equals total when no 5star yet`() {
        val records =
            listOf(
                rec("3-star A", 3, "2024-01-01 10:00:00"),
                rec("3-star B", 3, "2024-02-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals(2, pred.pullsSinceLastFive)
    }

    // ─────────── Character prediction: soft-pity / hard-pity thresholds ───────────

    @Test
    fun `character soft pity threshold is 66 and hard pity is 80`() {
        val records = listOf(rec("3-star", 3, "2024-01-01 10:00:00"))
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals(80, pred.hardPity)
        assertEquals(66, pred.softPityThreshold)
    }

    @Test
    fun `character isInSoftPity true at and after 66 pulls`() {
        val records65 = (0 until 65).map { rec("3-star", 3, "2024-01-${it + 1} 10:00:00") }
        val records66 = records65 + rec("3-star", 3, "2024-03-01 10:00:00")
        val pred65 = GachaApi.calcCharacterPrediction(records65, characterPool, standardFives)
        val pred66 = GachaApi.calcCharacterPrediction(records66, characterPool, standardFives)
        assertFalse("65 pulls should not be in soft pity", pred65.isInSoftPity)
        assertTrue("66 pulls should be in soft pity", pred66.isInSoftPity)
    }

    @Test
    fun `pullsUntilHardPity never goes negative`() {
        // 90 pulls since last 5★, hard pity is 80
        val records = (0 until 90).map { rec("3-star", 3, "2024-01-${it + 1} 10:00:00") }
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals(0, pred.pullsUntilHardPity)
    }

    // ─────────── Weapon prediction ───────────

    @Test
    fun `weapon prediction always returns Guaranteed status`() {
        val records =
            listOf(
                rec("FeaturedWeapon", 5, "2024-01-01 10:00:00"),
                rec("4-star", 4, "2024-02-01 10:00:00"),
            )
        val pred = GachaApi.calcWeaponPrediction(records, weaponPool)
        assertEquals("Guaranteed", pred.status)
    }

    @Test
    fun `weapon hard pity is 80 and soft pity starts at 66`() {
        val records = listOf(rec("3-star", 3, "2024-01-01 10:00:00"))
        val pred = GachaApi.calcWeaponPrediction(records, weaponPool)
        assertEquals(80, pred.hardPity)
        assertEquals(66, pred.softPityThreshold)
    }

    @Test
    fun `weapon isInSoftPity true at and after 66 pulls`() {
        val records65 = (0 until 65).map { rec("3-star", 3, "2024-01-${it + 1} 10:00:00") }
        val records66 = records65 + rec("3-star", 3, "2024-03-01 10:00:00")
        val pred65 = GachaApi.calcWeaponPrediction(records65, weaponPool)
        val pred66 = GachaApi.calcWeaponPrediction(records66, weaponPool)
        assertFalse(pred65.isInSoftPity)
        assertTrue(pred66.isInSoftPity)
    }

    @Test
    fun `weapon prediction honors count for 10pull collapsing`() {
        val records =
            listOf(
                rec("FeaturedWeapon", 5, "2024-01-01 10:00:00"),
                rec("3-star A", 3, "2024-02-01 10:00:00", count = 10),
            )
        val pred = GachaApi.calcWeaponPrediction(records, weaponPool)
        assertEquals(10, pred.pullsSinceLastFive)
    }

    @Test
    fun `weapon prediction tracks featured weapon name when no 5star yet`() {
        val records = listOf(rec("3-star", 3, "2024-01-01 10:00:00"))
        val pred = GachaApi.calcWeaponPrediction(records, weaponPool)
        assertEquals("", pred.currentFeaturedName)
        assertFalse(pred.currentFeaturedKnown)
    }

    @Test
    fun `weapon prediction sets featured known when a 5star exists`() {
        val records =
            listOf(
                rec("LustreWeapon", 5, "2024-01-01 10:00:00"),
                rec("3-star", 3, "2024-02-01 10:00:00"),
            )
        val pred = GachaApi.calcWeaponPrediction(records, weaponPool)
        assertEquals("LustreWeapon", pred.currentFeaturedName)
        assertEquals("LustreWeapon", pred.lastFiveStarName)
        assertTrue(pred.currentFeaturedKnown)
    }

    // ─────────── calculateAvgPity ───────────

    @Test
    fun `calculateAvgPity returns 0 for empty records`() {
        assertEquals(0.0, GachaApi.calculateAvgPity(emptyList(), 5), 0.0)
    }

    @Test
    fun `calculateAvgPity honors count for collapsed 10pulls`() {
        // 9 3-stars collapsed into 1 record (count=9), then a 5-star as pull 10.
        val records =
            listOf(
                rec("3-star A", 3, "2024-01-01 10:00:00", count = 9),
                rec("5-star", 5, "2024-02-01 10:00:00"),
            )
        val avg = GachaApi.calculateAvgPity(records, 5)
        assertEquals(10.0, avg, 0.0)
    }

    @Test
    fun `calculateAvgPity counts pulls between hits`() {
        val records =
            listOf(
                // 1
                rec("3-star", 3, "2024-01-01 10:00:00"),
                // 2
                rec("3-star", 3, "2024-02-01 10:00:00"),
                // hit at 3
                rec("5-star A", 5, "2024-03-01 10:00:00"),
                // 1
                rec("3-star", 3, "2024-04-01 10:00:00"),
                // hit at 2
                rec("5-star B", 5, "2024-05-01 10:00:00"),
            )
        val avg = GachaApi.calculateAvgPity(records, 5)
        assertEquals(2.5, avg, 0.0)
    }

    // ─────────── isStandardFive ───────────

    @Test
    fun `isStandardFive returns true for names in the set`() {
        assertTrue(GachaApi.isStandardFive("Jiyan", standardFives))
        assertTrue(GachaApi.isStandardFive("Yinlin", standardFives))
    }

    @Test
    fun `isStandardFive returns false for unknown names`() {
        assertFalse(GachaApi.isStandardFive("Featured", standardFives))
        assertFalse(GachaApi.isStandardFive("AnyName", emptySet()))
    }

    // ─────────── calcPullsSinceLastFourStar ───────────

    @Test
    fun `calcPullsSinceLastFourStar counts records after last 4 or 5 star`() {
        val records =
            listOf(
                rec("3-star A", 3, "2024-01-01 10:00:00"),
                rec("4-star B", 4, "2024-02-01 10:00:00"),
                rec("3-star C", 3, "2024-03-01 10:00:00"),
            )
        val pulls = GachaApi.calcPullsSinceLastFourStar(records)
        assertEquals(1, pulls)
    }

    @Test
    fun `calcPullsSinceLastFourStar caps at 10 when no 4 or 5 star yet`() {
        val records = (0 until 20).map { rec("3-star", 3, "2024-01-${it + 1} 10:00:00") }
        val pulls = GachaApi.calcPullsSinceLastFourStar(records)
        assertEquals(10, pulls)
    }

    @Test
    fun `calcPullsSinceLastFourStar honors count for 10pull collapsing`() {
        val records =
            listOf(
                rec("4-star A", 4, "2024-01-01 10:00:00"),
                rec("3-star B", 3, "2024-02-01 10:00:00", count = 10),
            )
        val pulls = GachaApi.calcPullsSinceLastFourStar(records)
        assertEquals(10, pulls)
    }

    // ─────────── status & character name consistency ───────────

    @Test
    fun `currentCharacterName is consistent with status across multiple scenarios`() {
        // Scenario 1: 50/50 with named featured
        val pred1 =
            GachaApi.calcCharacterPrediction(
                listOf(
                    rec("Featured1", 5, "2024-01-01 10:00:00"),
                    rec("3-star", 3, "2024-02-01 10:00:00"),
                ),
                characterPool,
                standardFives,
            )
        assertEquals("50/50", pred1.status)
        assertEquals("Featured1", pred1.currentFeaturedName)
        assertTrue(pred1.currentFeaturedKnown)

        // Scenario 2: Guaranteed with previous featured
        val pred2 =
            GachaApi.calcCharacterPrediction(
                listOf(
                    rec("Featured1", 5, "2024-01-01 10:00:00"),
                    rec("Jiyan", 5, "2024-02-01 10:00:00"),
                ),
                characterPool,
                standardFives,
            )
        assertEquals("Guaranteed", pred2.status)
        assertEquals("Featured1", pred2.currentFeaturedName)

        // Scenario 3: Guaranteed with no prior featured
        val pred3 =
            GachaApi.calcCharacterPrediction(
                listOf(
                    rec("Jiyan", 5, "2024-01-01 10:00:00"),
                ),
                characterPool,
                standardFives,
            )
        assertEquals("Guaranteed", pred3.status)
        assertEquals("", pred3.currentFeaturedName)
        assertFalse(pred3.currentFeaturedKnown)
        // And the names should differ between scenarios
        assertNotEquals(pred2.currentFeaturedName, pred3.currentFeaturedName)
    }

    // ─────────── Regression: new stats logic ───────────

    @Test
    fun `avgCharPity includes standard five stars in interval calculation`() {
        // Featured -> Standard -> Featured => intervals: [3, 2] avg=2.5 (not 3)
        val records =
            listOf(
                rec("Featured1", 5, "2024-01-01 10:00:00"),
                rec("3-star A", 3, "2024-01-02 10:00:00"),
                rec("3-star B", 3, "2024-01-03 10:00:00"),
                rec("Jiyan", 5, "2024-02-01 10:00:00"),
                rec("3-star C", 3, "2024-03-01 10:00:00"),
                rec("Featured2", 5, "2024-03-02 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals(2.0, pred.avgPityThisPool, 0.5)
    }

    @Test
    fun `nonBannerRate calculates 50 over 50 loss rate correctly`() {
        // Featured (win), Standard (loss -> guaranteed next), Featured (guaranteed -> consumes, no 50/50)
        // Featured (win), Standard (loss -> guaranteed next), Featured (guaranteed -> consumes)
        val records =
            listOf(
                rec("Featured1", 5, "2024-01-01 10:00:00"),
                rec("Jiyan", 5, "2024-02-01 10:00:00"),
                rec("Featured2", 5, "2024-03-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        // 1 50/50 cycle (Featured1 -> Jiyan), won = 1, total = 1 => 100%? Wait let's recalc:
        // Start isGuaranteed=false. Featured1: 50/50, win => won=1, total=1, isGuaranteed stays false.
        // Jiyan: isGuaranteed=false? Actually after Featured1 win, isGuaranteed false. Jiyan is standard => 50/50, lose => total=2, won=1, isGuaranteed=true.
        // Featured2: isGuaranteed=true => consume guarantee, no 50/50 cycle.
        // So total=2, won=1 => rate=0.5
        assertEquals(0.5, pred.nonBannerRate, 0.01)
        assertEquals(2.0 / 3.0, pred.upRate, 0.01)
    }

    @Test
    fun `weapon prediction upRate is 100 percent and nonBannerRate is 0`() {
        val records = listOf(rec("WeaponA", 5, "2024-01-01 10:00:00"))
        val pred = GachaApi.calcWeaponPrediction(records, weaponPool)
        assertEquals(1.0, pred.upRate, 0.01)
        assertEquals(0.0, pred.nonBannerRate, 0.01)
    }

    @Test
    fun `computeSsrIntervals produces correct interval count`() {
        // Two SSR hits with one 3-star between them => intervals: [1, 2]
        val records =
            listOf(
                rec("Featured1", 5, "2024-01-01 10:00:00"),
                rec("3-star", 3, "2024-02-01 10:00:00"),
                rec("Featured2", 5, "2024-03-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertTrue("SSR intervals should contain 2 entries", pred.ssrIntervals.size == 2)
        assertEquals("First interval pity should be 1 pull", 1, pred.ssrIntervals[0].pity)
        assertEquals("Second interval pity should be 2 pulls", 2, pred.ssrIntervals[1].pity)
    }

    @Test
    fun `computeMinMaxPity for 5 stars reflects all 5 star hits`() {
        val records =
            listOf(
                rec("A", 5, "2024-01-01 10:00:00"),
                rec("B", 5, "2024-03-01 10:00:00"),
                rec("C", 5, "2024-05-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertTrue("minPity5 should be >0", pred.minPity5 > 0)
        assertTrue("maxPity5 should be >= minPity5", pred.maxPity5 >= pred.minPity5)
    }

    @Test
    fun `computeTotalCost calculates astrites correctly`() {
        val records = listOf(rec("3-star", 3, "2024-01-01 10:00:00", count = 10))
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals("10 pulls * 160 = 1600", 1600L, pred.totalCost)
    }

    @Test
    fun `isPoolActive true when records present`() {
        val pred =
            GachaApi.calcCharacterPrediction(
                listOf(rec("3-star", 3, "2024-01-01 10:00:00")),
                characterPool,
                standardFives,
            )
        assertTrue(pred.isPoolActive)
    }
}
