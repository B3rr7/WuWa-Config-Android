package com.wuwaconfig.app.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * The in-game accounts bound to one Kuro passport, as reported by
 * `GET /user/player/list` on the guide server.
 *
 * A passport can own a character on more than one region; the guide lists every region it
 * knows about, and the ones with no character carry `null` ids. Only a row with a
 * non-null [PlayerInfo.playerId] is a real, selectable in-game account.
 */
data class PlayerInfo(
    val playerId: Long?,
    val playerName: String?,
    val serverId: String?,
    val serverName: String?,
    val level: Int?,
) {
    /** `true` when this region row actually has a character the user can pick. */
    val isSelectable: Boolean get() = playerId != null
}

/**
 * The single in-game account the user chose to act as, from `POST /user/player/choose`.
 * Subsequent `/introduction` calls resolve the player's *own* equipped items against this
 * chosen account, so the app must pick one before it asks for a character's equipment.
 */
data class ChosenPlayer(
    val playerId: Long,
    val playerName: String?,
    val serverId: String,
    val serverName: String?,
    val level: Int?,
)

/**
 * Pure parsers for the two player endpoints, kept out of the [KuroClient] HTTP layer so the
 * exact field extraction can be asserted against captured payloads with no network.
 */
object PlayerRosterParser {
    /**
     * Parses `GET /user/player/list`.
     *
     * Returns every region row, selectable or not, so the caller can still show "no
     * character here" for the empty regions instead of hiding them. A malformed or absent
     * `data` yields an empty list, never a throw.
     */
    fun parsePlayers(json: String): List<PlayerInfo> {
        val rows = dataArray(json) ?: return emptyList()
        return rows.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val o = element.asJsonObject
            PlayerInfo(
                playerId = o.get("playerId")?.longOrNull(),
                playerName = o.get("playerName")?.asStringOrNull(),
                serverId = o.get("serverId")?.asStringOrNull(),
                serverName = o.get("serverName")?.asStringOrNull(),
                level = o.get("level")?.intOrNull(),
            )
        }
    }

    /** Parses `POST /user/player/choose`; `null` when the chosen account is missing. */
    fun parseChosen(json: String): ChosenPlayer? {
        val chosen =
            runCatching { JsonParser.parseString(json) }
                .getOrNull()
                ?.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?.json("data")
                ?.json("profile")
                ?.json("chosenPlayer")
                ?: return null
        val playerId = chosen.get("playerId")?.longOrNull() ?: return null
        return ChosenPlayer(
            playerId = playerId,
            playerName = chosen.get("playerName")?.asStringOrNull(),
            serverId = chosen.get("serverId")?.asStringOrNull().orEmpty(),
            serverName = chosen.get("serverName")?.asStringOrNull(),
            level = chosen.get("level")?.intOrNull(),
        )
    }

    private fun dataArray(json: String): JsonArray? =
        runCatching { JsonParser.parseString(json) }
            .getOrNull()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?.list("data")

    // -- helpers (mirror CharacterBuildParser's null-safe accessors) ----------

    private fun JsonObject?.json(name: String): JsonObject? = this?.get(name)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject?.list(name: String): JsonArray? = this?.get(name)?.takeIf { it.isJsonArray }?.asJsonArray

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

    private fun JsonElement?.longOrNull(): Long? =
        when {
            this == null || isJsonNull -> null
            isJsonPrimitive -> runCatching { asLong }.getOrNull()
            else -> null
        }
}
