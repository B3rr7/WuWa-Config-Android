package com.wuwaconfig.app.config

import com.google.gson.JsonElement
import com.google.gson.JsonParser

/**
 * One entry of the guide's `/introduction/list` for a character — a curated build, just its
 * id, its popularity, and its name. The full build (weapons, echoes, resonance, skills) is a
 * separate `/introduction/info` call keyed by this [id].
 */
data class BuildSummary(
    val id: Int,
    val likeCount: Int?,
    val collectCount: Int?,
    val name: String?,
)

/**
 * Pure parser for `/introduction/list`.
 *
 * The first element is the most-curated build; this app uses it as the *recommendation*
 * context for a character, alongside the player's own equipment (which the authenticated
 * `/introduction/info` fills in via the `.current` fields regardless of which build id is
 * requested).
 *
 * The endpoint used to return that popularity order itself. It stopped: a live capture on
 * 2026-11-19 for Camellya came back ids ascending (`10008, 10009, …, 14050`) with the 8,689-like
 * build **last**, so trusting the server order made `first()` an archived id. Those archived ids
 * answer `/introduction/info` with `data: null`, which is why the screen reported "no build
 * details" for characters that plainly had one. The list is therefore sorted here, by likes
 * with the newest id as the tiebreak, and [com.wuwaconfig.app.ui.CharacterViewModel] still walks
 * down the list because not every id in a popularity-ordered list resolves either.
 */
object BuildListParser {
    fun parse(
        json: String,
        preferredLanguage: String = "en",
    ): List<BuildSummary> {
        val rows =
            runCatching { JsonParser.parseString(json) }
                .getOrNull()
                ?.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?.get("data")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?: return emptyList()
        return rows.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val o = element.asJsonObject
            val id = o.get("id")?.intOrNull() ?: return@mapNotNull null
            val roleTexts =
                o.get("role")
                    ?.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.get("texts")
                    ?.takeIf { it.isJsonArray }
            BuildSummary(
                id = id,
                likeCount = o.get("likeCount")?.intOrNull(),
                collectCount = o.get("collectCount")?.intOrNull(),
                name = pickName(roleTexts, preferredLanguage),
            )
        }.sortedWith(
            compareByDescending<BuildSummary> { it.likeCount ?: 0 }
                .thenByDescending { it.id },
        )
    }

    /** Picks a localized name from the build's `role.texts`, preferring [lang]. */
    private fun pickName(
        texts: JsonElement?,
        lang: String,
    ): String? {
        if (texts == null || !texts.isJsonArray) return null
        var any: String? = null
        for (entry in texts.asJsonArray) {
            if (!entry.isJsonObject) continue
            val o = entry.asJsonObject
            val name = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            if (name.isNullOrEmpty()) continue
            if (any == null) any = name
            if (o.get("language")?.takeIf { it.isJsonPrimitive }?.asString == lang) return name
        }
        return any
    }

    private fun JsonElement?.intOrNull(): Int? =
        when {
            this == null || isJsonNull -> null
            isJsonPrimitive -> runCatching { asInt }.getOrNull()
            else -> null
        }
}
