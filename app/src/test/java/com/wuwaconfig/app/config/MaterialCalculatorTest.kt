package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.AscensionPhaseCost
import com.wuwaconfig.app.model.CalculatorCharacter
import com.wuwaconfig.app.model.CalculatorWeapon
import com.wuwaconfig.app.model.SkillLevelCost
import com.wuwaconfig.app.model.WeaponAscensionPhaseCost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the row-selection arithmetic in [MaterialCalculator].
 *
 * Both ranges are half-open in opposite directions, which is the whole reason
 * these tests exist: an ascension row `phase = n` buys phase n-1 -> n, so
 * "phase 0 to 6" is rows 1..6, while a skill row `level = n` buys level n ->
 * n+1, so "level 1 to 10" is rows 1..9. Reading either one as the other produces
 * a plausible-looking number that is wrong by one row — the material totals would
 * look reasonable and no assertion on "is it non-empty" would catch it.
 *
 * Fixtures use one material per category with a row-unique quantity, so the
 * expected total is a sum of row indices rather than a magic constant.
 */
class MaterialCalculatorTest {
    private fun phase(
        n: Int,
        shellCredit: Int = n * 1000,
        local: Int = 0,
        common: Int = 0,
        boss: Int = 0,
    ) = AscensionPhaseCost(
        phase = n,
        shellCredit = shellCredit,
        local = local,
        common1 = common,
        common2 = 0,
        common3 = 0,
        common4 = 0,
        boss = boss,
    )

    private fun skillRow(
        n: Int,
        credit: Int = n * 100,
        wsm: Int = 0,
        unlockRank: Int? = null,
    ) = SkillLevelCost(
        level = n,
        credit = credit,
        wsm1 = wsm,
        dwsm1 = 0,
        wsm2 = 0,
        dwsm2 = 0,
        wsm3 = 0,
        dwsm3 = 0,
        wsm4 = 0,
        dwsm4 = 0,
        boss = 0,
        unlockRank = unlockRank,
    )

    private fun weaponPhase(
        n: Int,
        shellCredit: Int = n * 1000,
        common: Int = 0,
    ) = WeaponAscensionPhaseCost(
        phase = n,
        shellCredit = shellCredit,
        ascension1 = 0,
        ascension2 = 0,
        ascension3 = 0,
        ascension4 = 0,
        common1 = common,
        common2 = 0,
        common3 = 0,
        common4 = 0,
    )

    private fun fixture(
        phases: List<AscensionPhaseCost> = (1..6).map { phase(it, local = it, common = it, boss = it) },
        skills: List<SkillLevelCost> = (1..9).map { skillRow(it, wsm = it) },
        inherent: List<SkillLevelCost> = emptyList(),
        statBonus: List<SkillLevelCost> = emptyList(),
        weaponPhases: List<WeaponAscensionPhaseCost> = (1..6).map { weaponPhase(it, common = it) },
        hero: CalculatorCharacter =
            CalculatorCharacter(
                name = "Hero",
                rarity = 5,
                boss = "Boss Mat",
                local = "Local Mat",
                common = listOf("C1", "C2", "C3", "C4"),
                wsm = listOf("W1", "W2", "W3", "W4"),
                dwsm = listOf("D1", "D2", "D3", "D4"),
                skillBoss = "Skill Boss",
            ),
    ) = MaterialData(
        characters = mapOf("Hero" to hero),
        weapons =
            mapOf(
                "Blade" to
                    CalculatorWeapon(
                        name = "Blade",
                        rarity = 5,
                        ascension = listOf("A1", "A2", "A3", "A4"),
                        common = listOf("C1", "C2", "C3", "C4"),
                        baseAtk = 47.0,
                        secondStatType = "Crit. Rate",
                        secondStat = 5.4,
                    ),
            ),
        characterAscensionPhases = phases,
        skillMainCosts = skills,
        skillInherentCosts = inherent,
        skillStatBonusCosts = statBonus,
        weaponAscensionByRarity = mapOf("5" to weaponPhases),
    )

    /**
     * The production shape of the two secondary Forte tables: two rows each, with
     * distinct quantities so a test can tell which rows were charged.
     *
     * These quantities mirror the real asset's totals (10k + 20k inherent,
     * 50k + 100k stat bonus = 180,000 Shell Credit for a full character) so the
     * "this omission was worth 180,000" claim in [MaterialCalculator] stays
     * checkable against a real number rather than a fixture artefact.
     */
    private fun secondaryForteFixture() {
        MaterialData.installForTest(
            fixture(
                inherent =
                    listOf(
                        skillRow(1, credit = 10_000, wsm = 3, unlockRank = 2),
                        skillRow(2, credit = 20_000, wsm = 3, unlockRank = 4),
                    ),
                statBonus =
                    listOf(
                        skillRow(1, credit = 50_000, wsm = 3, unlockRank = 3),
                        skillRow(2, credit = 100_000, wsm = 3, unlockRank = 4),
                    ),
            ),
        )
    }

    @Before
    fun setUp() {
        MaterialData.installForTest(fixture())
    }

    @After
    fun tearDown() {
        MaterialData.installForTest(null)
    }

    private fun groupTotal(
        result: com.wuwaconfig.app.model.CalculatorResult,
        category: String,
    ): Int = result.groups.firstOrNull { it.category == category }?.total ?: 0

    @Test
    fun `full ascension spans rows 1 through 6`() {
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 0, toPhase = 6, skillFrom = 1, skillTo = 1)
        // Shell credit rows 1..6 => 1000+2000+...+6000 = 21000.
        assertEquals(21000, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
        // Local material appears once per row with that row's index as quantity.
        assertEquals(1 + 2 + 3 + 4 + 5 + 6, groupTotal(result, MaterialCalculator.CATEGORY_LOCAL))
        assertEquals(1 + 2 + 3 + 4 + 5 + 6, groupTotal(result, MaterialCalculator.CATEGORY_BOSS))
    }

    @Test
    fun `a single ascension step charges only that row`() {
        // Phase 4 -> 5 must be row 5 alone, not rows 1..5.
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 4, toPhase = 5, skillFrom = 1, skillTo = 1)
        assertEquals(5000, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
        assertEquals(5, groupTotal(result, MaterialCalculator.CATEGORY_LOCAL))
    }

    @Test
    fun `a zero-width ascension range costs nothing`() {
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 3, toPhase = 3, skillFrom = 1, skillTo = 1)
        assertEquals(0, result.totalItems)
        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun `full skill range spans rows 1 through 9`() {
        // Skill row n is level n -> n+1, so level 1 -> 10 is rows 1..9.
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 0, toPhase = 0, skillFrom = 1, skillTo = 10)
        assertEquals(100 + 200 + 300 + 400 + 500 + 600 + 700 + 800 + 900, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
        assertEquals(1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9, groupTotal(result, MaterialCalculator.CATEGORY_SKILL))
    }

    @Test
    fun `a single skill step charges only that row`() {
        // Level 3 -> 4 is row 3, which is the case that catches a swapped bound.
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 0, toPhase = 0, skillFrom = 3, skillTo = 4)
        assertEquals(300, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
        assertEquals(3, groupTotal(result, MaterialCalculator.CATEGORY_SKILL))
    }

    @Test
    fun `a zero-width skill range costs nothing`() {
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 0, toPhase = 0, skillFrom = 5, skillTo = 5)
        assertEquals(0, result.totalItems)
    }

    @Test
    fun `ascension and skill shell credit accumulate into one bucket`() {
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 0, toPhase = 1, skillFrom = 1, skillTo = 3)
        // Ascension row 1 = 1000; skill rows 1 and 2 = 100 + 200.
        assertEquals(1300, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
    }

    @Test
    fun `weapon ascension uses the rarity table`() {
        val result = MaterialCalculator.buildWeaponMaterials("Blade", fromPhase = 0, toPhase = 6)
        assertEquals(21000, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
        assertEquals(1 + 2 + 3 + 4 + 5 + 6, groupTotal(result, MaterialCalculator.CATEGORY_COMMON))
    }

    @Test
    fun `an unknown character yields nothing instead of throwing`() {
        val result = MaterialCalculator.buildCharacterMaterials("Nobody", fromPhase = 0, toPhase = 6, skillFrom = 1, skillTo = 10)
        assertEquals(0, result.totalItems)
        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun `a character with no skill materials still reports ascension cost`() {
        MaterialData.installForTest(
            fixture(
                hero =
                    CalculatorCharacter(
                        name = "Hero",
                        rarity = 5,
                        boss = "Boss Mat",
                        local = "Local Mat",
                        common = listOf("C1", "C2", "C3", "C4"),
                        wsm = emptyList(),
                        dwsm = emptyList(),
                        skillBoss = null,
                    ),
            ),
        )
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 0, toPhase = 1, skillFrom = 1, skillTo = 10)
        // Ascension row 1 is 1000; skill rows 1..9 still cost shell credit even
        // with no material rows attached, so 5500 rather than 1000.
        assertEquals(5500, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
        assertEquals(0, groupTotal(result, MaterialCalculator.CATEGORY_SKILL))
    }

    @Test
    fun `a full ascension includes inherent and stat bonus costs`() {
        secondaryForteFixture()
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 0, toPhase = 6, skillFrom = 1, skillTo = 1)
        // Ascension rows 1..6 = 21000; the four secondary Forte rows = 180000.
        // Skill slider untouched, so no skill rows at all.
        assertEquals(21000 + 180_000, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
    }

    @Test
    fun `a secondary Forte row is charged only once its unlock rank is crossed`() {
        secondaryForteFixture()
        // Rank 2 is crossed; ranks 3 and 4 are not. Only inherent row 1 applies.
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 1, toPhase = 2, skillFrom = 1, skillTo = 1)
        // Ascension row 2 = 2000, plus inherent row 1 = 10000.
        assertEquals(12_000, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
    }

    @Test
    fun `ascending from rank 3 charges the two rank 4 rows but not the rank 2 one`() {
        secondaryForteFixture()
        // Rank 2 was already passed, so inherent 1 is behind the player; rank 4
        // brings inherent 2 (20000) and stat bonus 2 (100000).
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 3, toPhase = 4, skillFrom = 1, skillTo = 1)
        assertEquals(4000 + 20_000 + 100_000, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
    }

    @Test
    fun `a range wholly above every unlock rank adds no secondary Forte cost`() {
        secondaryForteFixture()
        // Ranks 5 and 6 gate nothing, so only the ascension rows are charged.
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 4, toPhase = 6, skillFrom = 1, skillTo = 1)
        assertEquals(5000 + 6000, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
    }

    @Test
    fun `secondary Forte rows draw on the skill pool not the ascension pool`() {
        secondaryForteFixture()
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 1, toPhase = 2, skillFrom = 1, skillTo = 1)
        // wsm1 must resolve to the character skill material "W1", never the
        // ascension-phase materials ("Local Mat" / "C1") or the ascension boss.
        val skillGroup = result.groups.firstOrNull { it.category == MaterialCalculator.CATEGORY_SKILL }
        assertEquals(listOf("W1" to 3), skillGroup?.materials?.map { it.name to it.quantity })
        // The boss drop in this range comes from the ascension row alone (the
        // fixture gives phase 2 a boss of 2). The secondary rows' `boss` field is
        // 0 here, so nothing was added to it — if the secondary rows were being
        // routed through the ascension boss material this would not be 2.
        assertEquals(2, groupTotal(result, MaterialCalculator.CATEGORY_BOSS))
    }

    @Test
    fun `an ungated secondary Forte row is charged regardless of rank`() {
        // The gate comes from each row's `unlockRank`. If the asset ever loses
        // that field, `null` must mean "always available" rather than "never" —
        // reading it as rank 0 would silently drop 180,000 Shell Credit again.
        MaterialData.installForTest(
            fixture(
                inherent = listOf(skillRow(1, credit = 10_000, wsm = 3, unlockRank = null)),
                statBonus = emptyList(),
            ),
        )
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 0, toPhase = 0, skillFrom = 1, skillTo = 1)
        assertEquals(10_000, groupTotal(result, MaterialCalculator.SHELL_CREDIT))
    }

    @Test
    fun `phase level ranges map onto the documented level caps`() {
        assertEquals(1..20, MaterialCalculator.phaseLevelRange(0))
        assertEquals(20..40, MaterialCalculator.phaseLevelRange(1))
        assertEquals(80..90, MaterialCalculator.phaseLevelRange(6))
    }

    @Test
    fun `group order is stable and shell credit leads`() {
        val result = MaterialCalculator.buildCharacterMaterials("Hero", fromPhase = 0, toPhase = 6, skillFrom = 1, skillTo = 10)
        val categories = result.groups.map { it.category }
        assertEquals(MaterialCalculator.SHELL_CREDIT, categories.first())
        // Each category appears exactly once even though ascension and skill both
        // spend shell credit and both spend common drops.
        assertEquals(categories.size, categories.toSet().size)
    }
}
