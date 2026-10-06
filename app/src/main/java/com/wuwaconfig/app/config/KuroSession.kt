package com.wuwaconfig.app.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * The authenticated Kuro guide session this app holds after logging the user in.
 *
 * Every field here is derived from the passport login flow. The [xToken] is a bearer
 * credential, not the password, so it is safe to persist; the password itself is held only
 * transiently inside [KuroClient.login] and never written to disk.
 */
data class KuroSession(
    /** The guide `x-token` — the JWT bearer for every authenticated call. */
    val xToken: String,
    /** The Kuro passport cuid, the numeric account id as a string. */
    val cuid: String,
    /** The passport username, e.g. `U100000001A`. */
    val username: String,
    /** The in-game account chosen to represent; `null` until the user has chosen one. */
    val chosenPlayer: ChosenPlayer?,
    /** The passport email, kept only to pre-fill the next login. */
    val email: String?,
    /** When the underlying access token expires, epoch seconds; `null` when unknown. */
    val expiresAtEpochSec: Long?,
)

/**
 * Pure parsers for the three login hops, split from the [KuroClient] transport so the exact
 * field extraction can be asserted against captured responses without a network.
 *
 * All three responses are wrapped in a `{code?, message?, data:{...}}` envelope; the payloads
 * differ only in which keys `data` carries.
 */
object KuroLoginParser {
    /** The passport's answer to an email+password login: an OAuth code plus the identity. */
    data class EmailPwdResult(
        val code: String,
        val cuid: String,
        val username: String,
        val email: String?,
    )

    /** The OAuth token exchange result. */
    data class GetTokenResult(
        val accessToken: String,
        val expiresInSec: Int?,
    )

    fun parseEmailPwd(json: String): EmailPwdResult? {
        val data = dataObject(json) ?: return null
        val code = data.get("code")?.asStringOrNull() ?: return null
        val cuid = data.get("cuid")?.asStringOrNull() ?: return null
        return EmailPwdResult(
            code = code,
            cuid = cuid,
            username = data.get("username")?.asStringOrNull().orEmpty(),
            email = data.get("email")?.asStringOrNull(),
        )
    }

    fun parseGetToken(json: String): GetTokenResult? {
        val data = dataObject(json) ?: return null
        val accessToken = data.get("access_token")?.asStringOrNull() ?: return null
        return GetTokenResult(
            accessToken = accessToken,
            expiresInSec = data.get("expires_in")?.intOrNull(),
        )
    }

    /** The guide's own login exchange: the bearer [KuroSession.xToken] it will sign with. */
    fun parseGuideToken(json: String): String? = dataObject(json)?.get("token")?.asStringOrNull()

    private fun dataObject(json: String): JsonObject? =
        runCatching { JsonParser.parseString(json) }
            .getOrNull()
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?.get("data")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject

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
}
