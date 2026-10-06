package com.wuwaconfig.app.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * A character build exactly as Kuro's guide describes it.
 *
 * The guide serves one build payload in two guises, and both map to this model:
 *
 *  - a **curated** build (a community or official `/introduction` post), and
 *  - the **player's own** equipped items, fetched through the authenticated
 *    `/user/player` endpoints once the user has logged in.
 *
 * The renderable fields — [resonance], [skills], [equippedWeapon], [weaponOptions],
 * [mainEcho], [spareEcho], [recommendedStats] — are the same in both, so the native
 * [CharacterBuild] view renders either without special-casing. The one field only the
 * authenticated payload carries is [level]; a curated build has no notion of the
 * author's in-game level, so it is `null` there.
 *
 * Parsing lives in [CharacterBuildParser], a pure function of the JSON string, so it can
 * be unit-tested against a captured payload without a network or a WebView.
 */
data class CharacterBuild(
    val id: Int?,
    val role: CharacterRole,
    /** The character's combo sequences ("连招") from the build's text. */
    val skillDisplay: String,
    val recommendedStats: List<StatRecommendation>,
    /** All six resonance links, in chain order 1..6. */
    val resonance: List<ResonanceLink>,
    /** The skills this build invests levels into. */
    val skills: List<SkillInfo>,
    /**
     * The recommended upgrade priority (`roleSkill.addPointTarget` order). Each entry carries
     * both the recommended level and the player's own [SkillInfo.currentLevel], so the view
     * can render "Lv 8/10" — the guide's recommendation against what is actually upgraded.
     */
    val skillPriority: List<SkillInfo>,
    /** The signature/keystone skill, if the build names one. */
    val keynoteSkill: SkillInfo?,
    /** The weapon actually equipped (curated builds usually leave this unset). */
    val equippedWeapon: Weapon?,
    val weaponOptions: List<Weapon>,
    /** The echo currently equipped; only present on the player's authenticated build. */
    val equippedEcho: Echo?,
    val mainEcho: Echo?,
    val spareEcho: Echo?,
    /** Who authored a curated build; `null` for the player's own build. */
    val source: String?,
    val likeCount: Int?,
    /** In-game level; only present for the player's own authenticated build. */
    val level: Int?,
)

data class CharacterRole(
    val roleGbId: String,
    val name: String,
    val star: Int,
    val element: String,
    val elementImageUrl: String,
    val cardPictureUrl: String,
    val illustrationPictureUrl: String,
)

data class SkillInfo(
    val name: String,
    /** Resonance Skill / Resonance Liberation / Outro Skill / … */
    val type: String,
    val description: String?,
    val pictureUrl: String,
    /** The player's own upgraded level; `0`/absent on curated builds without a token. */
    val currentLevel: Int?,
    /** The guide's recommended level for this skill. */
    val recommendLevel: Int?,
)

data class ResonanceLink(
    /** Chain position, 1..6. */
    val sequence: Int,
    val name: String,
    val description: String?,
    /** Whether the build considers this link unlocked. */
    val isAcquired: Boolean,
    val status: Int?,
    val pictureUrl: String,
)

data class Weapon(
    val name: String,
    val effectName: String?,
    val effectDescription: String?,
    val star: Int?,
    /** Weapon slot type (Grand Piano / Tonfa / …), localized name. */
    val type: String?,
    val pictureUrl: String,
)

data class Echo(
    val name: String,
    val star: Int?,
    val cost: Int?,
    val skillDescription: String?,
    val pictureUrl: String,
    val setEffects: List<EchoSetEffect>,
    val attributes: List<EchoAttribute>,
)

data class EchoSetEffect(
    val name: String,
    val description: String?,
    /** How many pieces of the set are activated. */
    val setLevel: Int?,
)

data class EchoAttribute(
    val name: String,
    /** The stat value the build equips / has on this piece. */
    val value: String?,
)

data class StatRecommendation(
    val name: String,
    /** The target value ("70.0%"). */
    val target: String?,
    /** The player's own current value; `0.0%`-ish on curated builds until the player fills it. */
    val current: String?,
    val pictureUrl: String,
    /** Whether the player's current value already meets the target. */
    val isFinished: Boolean,
)

/**
 * Maps a guide build payload ([`/introduction/info`], or the authenticated equivalent)
 * into a [CharacterBuild].
 *
 * Language selection mirrors [KuroGuide]: prefer [preferredLanguage], then a common
 * fallback, then the first entry that has the field — a build published only in one
 * language must still render rather than come out blank.
 */
object CharacterBuildParser {
    /**
     * Parses the envelope. A `null`/absent `data` (the guide's answer for a curated
     * post whose detail isn't public, and for a bad id) yields `null` so the caller can
     * distinguish "no build" from a parse error.
     */
    fun parse(
        json: String,
        preferredLanguage: String = "en",
    ): CharacterBuild? {
        val root = JsonParser.parseString(json)
        if (!root.isJsonObject) return null
        val obj = root.asJsonObject
        val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return buildFrom(data, preferredLanguage)
    }

    /** Parses a bare build object (no `{code,data}` envelope), used by tests. */
    internal fun buildFrom(
        data: JsonObject,
        preferredLanguage: String = "en",
    ): CharacterBuild {
        val role = parseRole(data.json("role") ?: JsonObject(), preferredLanguage)

        val base = languageObject(data.list("baseTexts"), preferredLanguage)
        val source = base?.get("introductionSource")?.asStringOrNull().orEmpty()

        return CharacterBuild(
            id = data.get("id")?.intOrNull(),
            role = role,
            skillDisplay = base?.get("skillDisplay")?.asStringOrNull().orEmpty(),
            recommendedStats = parseStats(data.json("roleAttribute"), preferredLanguage),
            resonance = parseResonance(data.json("roleResonance"), preferredLanguage),
            skills = parseSkills(data.json("roleSkill"), preferredLanguage),
            skillPriority = parseSkillPriority(data.json("roleSkill"), preferredLanguage),
            keynoteSkill = parseKeynote(data.json("roleSkill"), preferredLanguage),
            equippedWeapon = data.json("weapon")?.json("current")?.let { parseWeapon(it, preferredLanguage) },
            weaponOptions = parseWeapons(data.json("weapon"), preferredLanguage),
            equippedEcho = parseEcho(data.json("echo")?.json("current"), preferredLanguage),
            mainEcho = parseEcho(data.json("echo")?.json("main"), preferredLanguage),
            spareEcho = parseEcho(data.json("echo")?.json("spare"), preferredLanguage),
            source = source.ifEmpty { null },
            likeCount = data.get("likeCount")?.intOrNull(),
            level = data.get("level")?.intOrNull(),
        )
    }

    // -- role -----------------------------------------------------------------

    private fun parseRole(
        obj: JsonObject,
        lang: String,
    ): CharacterRole {
        val roleTexts = languageObject(obj.list("texts"), lang)
        val element = obj.json("element")
        val elementTexts = languageObject(element?.list("texts"), lang)
        return CharacterRole(
            roleGbId = obj.get("roleGbId")?.asStringOrNull().orEmpty(),
            name = roleTexts?.get("name")?.asStringOrNull().orEmpty(),
            star = obj.get("star")?.intOrNull() ?: 0,
            element = elementTexts?.get("name")?.asStringOrNull().orEmpty(),
            elementImageUrl = element?.get("pictureUrl")?.asStringOrNull().orEmpty(),
            cardPictureUrl = obj.get("cardPictureUrl")?.asStringOrNull().orEmpty(),
            illustrationPictureUrl = obj.get("illustrationPictureUrl")?.asStringOrNull().orEmpty(),
        )
    }

    // -- skills ---------------------------------------------------------------

    private fun parseSkills(
        obj: JsonObject?,
        lang: String,
    ): List<SkillInfo> {
        val rows = obj?.list("addPointSequence") ?: return emptyList()
        return rows.mapNotNull { if (it.isJsonObject) parseSkill(it.asJsonObject, lang) else null }
    }

    private fun parseKeynote(
        obj: JsonObject?,
        lang: String,
    ): SkillInfo? {
        // A build may carry a single keystone skill, or the first of the keystone list.
        val single = obj?.json("keynoteSkill")
        if (single != null) return parseSkill(single, lang)
        val first = obj?.list("keynoteSkills")?.firstOrNull { it.isJsonObject }?.asJsonObject
        return first?.let { parseSkill(it, lang) }
    }

    private fun parseSkill(
        obj: JsonObject,
        lang: String,
    ): SkillInfo? {
        val texts = languageObject(obj.list("texts"), lang) ?: return null
        val name = texts.get("name")?.asStringOrNull().orEmpty()
        if (name.isEmpty()) return null
        val type =
            languageObject(obj.json("skillType")?.list("texts"), lang)
                ?.get("name")?.asStringOrNull()
                .orEmpty()
        return SkillInfo(
            name = name,
            type = type,
            description = texts.get("description")?.asStringOrNull(),
            pictureUrl = obj.get("pictureUrl")?.asStringOrNull().orEmpty(),
            currentLevel = obj.get("currentLevel")?.intOrNull(),
            recommendLevel = obj.get("recommendLevel")?.intOrNull(),
        )
    }

    /**
     * The recommended upgrade priority: `roleSkill.addPointTarget` in the guide's priority
     * order, each entry carrying the player's own `currentLevel` when authenticated.
     */
    private fun parseSkillPriority(
        obj: JsonObject?,
        lang: String,
    ): List<SkillInfo> {
        val rows = obj?.list("addPointTarget") ?: return emptyList()
        return rows.mapNotNull { if (it.isJsonObject) parseSkill(it.asJsonObject, lang) else null }
    }

    // -- resonance ------------------------------------------------------------

    private fun parseResonance(
        obj: JsonObject?,
        lang: String,
    ): List<ResonanceLink> {
        val rows = obj?.list("items") ?: return emptyList()
        return rows.mapNotNull {
            if (!it.isJsonObject) return@mapNotNull null
            val o = it.asJsonObject
            val texts = languageObject(o.list("texts"), lang)
            val sequence = o.get("resonanceSequence")?.intOrNull() ?: 0
            ResonanceLink(
                sequence = sequence,
                name = texts?.get("name")?.asStringOrNull().orEmpty(),
                description = texts?.get("description")?.asStringOrNull(),
                isAcquired = o.get("isAcquired")?.boolOrNull() ?: false,
                status = o.get("status")?.intOrNull(),
                pictureUrl = o.get("pictureUrl")?.asStringOrNull().orEmpty(),
            )
        }.sortedBy { it.sequence }
    }

    // -- weapons --------------------------------------------------------------

    private fun parseWeapons(
        obj: JsonObject?,
        lang: String,
    ): List<Weapon> {
        val rows = obj?.list("items") ?: return emptyList()
        return rows.mapNotNull { if (it.isJsonObject) parseWeapon(it.asJsonObject, lang) else null }
    }

    private fun parseWeapon(
        obj: JsonObject,
        lang: String,
    ): Weapon? {
        val texts = languageObject(obj.list("texts"), lang) ?: return null
        val name = texts.get("name")?.asStringOrNull().orEmpty()
        if (name.isEmpty()) return null
        val type =
            languageObject(obj.json("weaponType")?.list("texts"), lang)
                ?.get("name")?.asStringOrNull()
        return Weapon(
            name = name,
            effectName = texts.get("effectName")?.asStringOrNull(),
            effectDescription = texts.get("effectDescription")?.asStringOrNull(),
            star = obj.get("star")?.intOrNull(),
            type = type,
            pictureUrl = obj.get("pictureUrl")?.asStringOrNull().orEmpty(),
        )
    }

    // -- echoes ---------------------------------------------------------------

    private fun parseEcho(
        obj: JsonObject?,
        lang: String,
    ): Echo? {
        if (obj == null) return null
        val props = obj.json("echoProps") ?: return null
        val propTexts = languageObject(props.list("texts"), lang)
        val name = propTexts?.get("name")?.asStringOrNull().orEmpty()
        if (name.isEmpty()) return null
        return Echo(
            name = name,
            star = props.get("star")?.intOrNull(),
            cost = props.get("cost")?.intOrNull(),
            skillDescription = propTexts?.get("skillDescription")?.asStringOrNull(),
            pictureUrl = props.get("pictureUrl")?.asStringOrNull().orEmpty(),
            setEffects =
                obj.list("echoSetEffects")?.let { rows ->
                    rows.mapNotNull { e ->
                        if (!e.isJsonObject) return@mapNotNull null
                        val o = e.asJsonObject
                        val t = languageObject(o.list("texts"), lang)
                        val set = t?.get("name")?.asStringOrNull().orEmpty()
                        if (set.isEmpty()) {
                            null
                        } else {
                            EchoSetEffect(
                                name = set,
                                description = t?.get("description")?.asStringOrNull(),
                                setLevel = o.get("echoSet")?.intOrNull(),
                            )
                        }
                    }
                }.orEmpty(),
            attributes =
                obj.list("echoAttributes")?.let { rows ->
                    rows.mapNotNull { a ->
                        if (!a.isJsonObject) return@mapNotNull null
                        val o = a.asJsonObject
                        val attrTexts =
                            languageObject(
                                o.json("attribute")?.list("texts"),
                                lang,
                            )
                        val attrName = attrTexts?.get("name")?.asStringOrNull().orEmpty()
                        if (attrName.isEmpty()) {
                            null
                        } else {
                            EchoAttribute(
                                name = attrName,
                                value = o.get("value")?.asStringOrNull() ?: o.get("currentLevel")?.asStringOrNull(),
                            )
                        }
                    }
                }.orEmpty(),
        )
    }

    // -- stats ----------------------------------------------------------------

    private fun parseStats(
        obj: JsonObject?,
        lang: String,
    ): List<StatRecommendation> {
        val rows = obj?.list("items") ?: return emptyList()
        return rows.mapNotNull {
            if (!it.isJsonObject) return@mapNotNull null
            val o = it.asJsonObject
            val t = languageObject(o.list("texts"), lang)
            val name = t?.get("name")?.asStringOrNull().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            StatRecommendation(
                name = name,
                target = o.get("recommendAmount")?.asStringOrNull(),
                current = o.get("currentAmount")?.asStringOrNull(),
                pictureUrl = o.get("pictureUrl")?.asStringOrNull().orEmpty(),
                isFinished = o.get("isFinished")?.boolOrNull() ?: false,
            )
        }
    }

    // -- shared helpers -------------------------------------------------------

    /**
     * Picks the localized text object for [lang], falling back to `zh-Hans` and then to
     * the first entry that carries the needed keys, so a single-language build still
     * resolves.
     */
    private fun languageObject(
        texts: JsonArray?,
        lang: String,
    ): JsonObject? {
        if (texts == null) return null
        var any: JsonObject? = null
        var zh: JsonObject? = null
        for (entry in texts) {
            if (!entry.isJsonObject) continue
            val o = entry.asJsonObject
            if (any == null) any = o
            val entryLang = o.get("language")?.asStringOrNull()
            when (entryLang) {
                lang -> return o
                "zh-Hans" -> if (zh == null) zh = o
            }
        }
        return zh ?: any
    }

    private fun JsonElement?.asStringOrNull(): String? =
        when {
            this == null || isJsonNull -> null
            isJsonPrimitive -> asString
            else -> null
        }

    private fun JsonElement?.intOrNull(): Int? =
        when {
            this == null || isJsonNull -> null
            isJsonPrimitive -> runCatching { asInt }.getOrNull()
            else -> null
        }

    private fun JsonElement?.boolOrNull(): Boolean? =
        when {
            this == null || isJsonNull -> null
            isJsonPrimitive -> runCatching { asBoolean }.getOrNull()
            else -> null
        }

    /**
     * A child object that is null-safe in the way Gson's `getAsJsonObject` is not: an
     * absent key, a `JsonNull` (a field the server explicitly sets to null, such as
     * `weapon.current` on a build that leaves no weapon equipped), or a wrong type all
     * read as `null` rather than throwing.
     */
    private fun JsonObject?.json(name: String): JsonObject? = this?.get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject?.list(name: String): JsonArray? = this?.get(name)?.takeIf { it.isJsonArray }?.asJsonArray
}
