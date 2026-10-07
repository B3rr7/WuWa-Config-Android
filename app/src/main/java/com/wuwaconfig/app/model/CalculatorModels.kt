package com.wuwaconfig.app.model

data class AscensionPhaseCost(
    val phase: Int,
    val shellCredit: Int,
    val local: Int,
    val common1: Int,
    val common2: Int,
    val common3: Int,
    val common4: Int,
    val boss: Int,
)

data class WeaponAscensionPhaseCost(
    val phase: Int,
    val shellCredit: Int,
    val ascension1: Int,
    val ascension2: Int,
    val ascension3: Int,
    val ascension4: Int,
    val common1: Int,
    val common2: Int,
    val common3: Int,
    val common4: Int,
)

data class SkillLevelCost(
    val level: Int,
    val credit: Int,
    val wsm1: Int,
    val dwsm1: Int,
    val wsm2: Int,
    val dwsm2: Int,
    val wsm3: Int,
    val dwsm3: Int,
    val wsm4: Int,
    val dwsm4: Int,
    val boss: Int,
    /**
     * Ascension rank that unlocks this row, or null when it is ungated.
     *
     * The wiki states this only as a Lua comment per row (`-- need Rank.2`), so
     * `tools/update_calculator_data.py` scrapes it into the asset rather than
     * leaving it to be transcribed by hand — the two tables this actually gates
     * (`inherent`, `statBonus`) are worth 180,000 Shell Credit on a full
     * character, so a stale constant here is a visible cost error rather than a
     * cosmetic one.
     */
    val unlockRank: Int? = null,
)

data class CalculatorCharacter(
    val name: String,
    val rarity: Int,
    val boss: String?,
    val local: String?,
    val common: List<String>,
    val wsm: List<String>,
    val dwsm: List<String>,
    val skillBoss: String?,
)

data class CalculatorWeapon(
    val name: String,
    val rarity: Int,
    val ascension: List<String>,
    val common: List<String>,
    val baseAtk: Double?,
    val secondStatType: String?,
    val secondStat: Double?,
)

data class MaterialItem(
    val name: String,
    val quantity: Int,
)

/**
 * A named bucket of materials, e.g. every "Common Material" together.
 *
 * The categories come from the *role a material plays in an ascension*, not from
 * string matching on its name: the same name can appear in two buckets across
 * different characters (Shorekeeper's ascension "Nova" is a local speciality,
 * while a weapon of hers may draw the same string as a skill material), so a
 * name-prefix heuristic would mis-file it.
 */
data class MaterialGroup(
    val category: String,
    val materials: List<MaterialItem>,
) {
    val total: Int get() = materials.sumOf { it.quantity }
}

data class CalculatorResult(
    val characterName: String? = null,
    val weaponName: String? = null,
    val groups: List<MaterialGroup> = emptyList(),
    val materials: List<MaterialItem> = emptyList(),
    val totalItems: Int = 0,
)
