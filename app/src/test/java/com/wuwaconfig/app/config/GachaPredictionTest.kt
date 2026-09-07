package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.GachaPool
import com.wuwaconfig.app.model.GachaRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GachaPredictionTest {
    private val characterPool = GachaPool("1", "Character Event")
    private val weaponPool = GachaPool("2", "Weapon Event")
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
        // p=66, rate=0.067, expected ~ 14
        val est = GachaApi.estimatedSoftPityPulls(66, 66, 80)
        assertTrue(
            "expected ~14 pulls, got $est",
            est in 13..16,
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
        // weapon: soft 57, hard 70. Int representation has limited resolution
        // in the late-soft-pity region; verify the steep part is monotonic.
        val at57 = GachaApi.estimatedSoftPityPulls(57, 57, 70)
        val at60 = GachaApi.estimatedSoftPityPulls(60, 57, 70)
        val at63 = GachaApi.estimatedSoftPityPulls(63, 57, 70)
        val at67 = GachaApi.estimatedSoftPityPulls(67, 57, 70)
        assertTrue("at57 ($at57) should be > at60 ($at60)", at57 > at60)
        assertTrue("at60 ($at60) should be > at63 ($at63)", at60 > at63)
        assertTrue("at63 ($at63) should be >= at67 ($at67)", at63 >= at67)
        assertEquals(1, GachaApi.estimatedSoftPityPulls(70, 57, 70))
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
        assertEquals("OldFeatured", pred.currentCharacterName)
    }

    @Test
    fun `currentCharacterName is empty when only standard 5stars exist (e_g_, 50 over 50 loss)`() {
        val records =
            listOf(
                rec("Jiyan", 5, "2024-01-01 10:00:00"),
                rec("Yinlin", 5, "2024-02-01 10:00:00"),
            )
        val pred = GachaApi.calcCharacterPrediction(records, characterPool, standardFives)
        assertEquals("", pred.currentCharacterName)
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
        assertEquals("Featured1", pred.currentCharacterName)
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
    fun `weapon prediction always returns 75 over 25 status`() {
        val records =
            listOf(
                rec("FeaturedWeapon", 5, "2024-01-01 10:00:00"),
                rec("4-star", 4, "2024-02-01 10:00:00"),
            )
        val pred = GachaApi.calcWeaponPrediction(records, weaponPool)
        assertEquals("75/25", pred.status)
    }

    @Test
    fun `weapon hard pity is 70 and soft pity starts at 57`() {
        val records = listOf(rec("3-star", 3, "2024-01-01 10:00:00"))
        val pred = GachaApi.calcWeaponPrediction(records, weaponPool)
        assertEquals(70, pred.hardPity)
        assertEquals(57, pred.softPityThreshold)
    }

    @Test
    fun `weapon isInSoftPity true at and after 57 pulls`() {
        val records56 = (0 until 56).map { rec("3-star", 3, "2024-01-${it + 1} 10:00:00") }
        val records57 = records56 + rec("3-star", 3, "2024-03-01 10:00:00")
        val pred56 = GachaApi.calcWeaponPrediction(records56, weaponPool)
        val pred57 = GachaApi.calcWeaponPrediction(records57, weaponPool)
        assertFalse(pred56.isInSoftPity)
        assertTrue(pred57.isInSoftPity)
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
        assertEquals("Featured1", pred1.currentCharacterName)
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
        assertEquals("Featured1", pred2.currentCharacterName)

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
        assertEquals("", pred3.currentCharacterName)
        assertFalse(pred3.currentFeaturedKnown)
        // And the names should differ between scenarios
        assertNotEquals(pred2.currentCharacterName, pred3.currentCharacterName)
    }
}
