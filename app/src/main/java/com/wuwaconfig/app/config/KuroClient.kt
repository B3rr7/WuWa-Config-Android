package com.wuwaconfig.app.config

import android.util.Log
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * The native Kuro client: the fully-offline-from-WebView login flow plus the authenticated
 * guide-server data calls that back the player's own character view.
 *
 * ## Login
 *
 * Kuro's passport login is a plain form-encoded OAuth exchange against
 * `sdkapi.kurogame-service.com` — no signature, no captcha — and the `client_id`/secret used
 * are the ones shipped in Kuro's public web bundle, not a server secret. The flow is:
 *
 *  1. `login/emailPwd.lg`  — email + password -> an OAuth `code` and the identity.
 *  2. `auth/getToken.lg`    — `code` -> an `access_token`.
 *  3. `guide /user/login/sdk` — `access_token` -> the guide's bearer `x-token`.
 *
 * Only the [KuroSession.xToken] is persisted (a bearer credential); the password is held in
 * memory for the duration of [login] and never written to disk.
 *
 * ## Data
 *
 * Once a player is chosen ([choosePlayer]), the guide's `/introduction/info` resolves the
 * player's *own* equipped weapon/echo through the `.current` fields; the recommendation
 * context is the top curated build for the character ([fetchBuilds]).
 */
object KuroClient {
    private const val SDK_BASE = "https://sdkapi.kurogame-service.com/sdkcom/v2"
    private const val GUIDE_BASE = "https://guide-server.aki-game.net"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024

    // Shipped in Kuro's public web bundle; an identifier pair, not a server secret.
    private const val CLIENT_ID = "7rxmydkibzzsf12om5asjnoo"
    private const val CLIENT_SECRET = "32gh5r0p35ullmxrzzwk40ly"
    private const val CHANNEL_ID = "18"
    private const val PROJECT_ID = "G153"
    private const val PRODUCT_ID = "A1838"
    private const val SDK_VERSION = "2.3.0"

    // Kuro's endpoints reject requests that lack the browser provenance a real guide client
    // sends. These mirror the values in the working web session; without the Origin/Referer
    // pair the passport login answers HTTP 200 with an error body instead of a code.
    private const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64; rv:157.0) Gecko/20100101 Firefox/157.0"
    private const val ACCEPT = "application/json, text/plain, */*"
    private const val SDK_ORIGIN = "https://sdk-prod-cdn-aws.kurogame-service.com"
    private const val GUIDE_ORIGIN = "https://wuwaguide.kurogames.com"

    /** A login hop failed; [step] names which, for a user-facing message. */
    class KuroLoginException(
        val step: String,
        message: String,
    ) : Exception(message)

    /**
     * Runs the passport login and returns the bearer session. Returns [Result.failure] (never
     * throws) so the calling screen can render the error without a try/catch.
     */
    suspend fun login(
        email: String,
        password: String,
        deviceNum: String = UUID.randomUUID().toString(),
        language: String = "en",
    ): Result<KuroSession> =
        withContext(Dispatchers.IO) {
            try {
                val emailRaw =
                    postForm(
                        "$SDK_BASE/login/emailPwd.lg",
                        baseForm(deviceNum) +
                            "&response_type=code&email=${enc(email)}&password=${enc(encodePassword(password))}&client_id=$CLIENT_ID",
                    )
                val emailResult =
                    KuroLoginParser.parseEmailPwd(emailRaw)
                        ?: fail("login", "passport returned no code", emailRaw)

                val tokenRaw =
                    postForm(
                        "$SDK_BASE/auth/getToken.lg",
                        baseForm(deviceNum) +
                            "&response_type=code&grant_type=authorization_code&code=${enc(emailResult.code)}" +
                            "&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET",
                    )
                val tokenResult =
                    KuroLoginParser.parseGetToken(tokenRaw)
                        ?: fail("token", "token exchange returned no access token", tokenRaw)

                val guideRaw =
                    postJson(
                        "$GUIDE_BASE/user/login/sdk",
                        guideLoginBody(emailResult.cuid, emailResult.username, tokenResult.accessToken),
                        language,
                    )
                val xToken =
                    KuroLoginParser.parseGuideToken(guideRaw)
                        ?: fail("guide", "guide login returned no token", guideRaw)

                Result.success(
                    KuroSession(
                        xToken = xToken,
                        cuid = emailResult.cuid,
                        username = emailResult.username,
                        chosenPlayer = null,
                        email = email,
                        expiresAtEpochSec =
                            tokenResult.expiresInSec?.let {
                                System.currentTimeMillis() / 1000L + it
                            },
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** The user's in-game accounts across regions; only the non-null ones are selectable. */
    suspend fun fetchPlayers(
        xToken: String,
        language: String = "en",
    ): Result<List<PlayerInfo>> =
        withContext(Dispatchers.IO) {
            try {
                val raw = get("$GUIDE_BASE/user/player/list", language, xToken)
                Result.success(PlayerRosterParser.parsePlayers(raw))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Marks the chosen in-game account so subsequent calls resolve against it. */
    suspend fun choosePlayer(
        xToken: String,
        playerId: Long,
        serverId: String,
        language: String = "en",
    ): Result<ChosenPlayer> =
        withContext(Dispatchers.IO) {
            try {
                val body = """{"playerId":$playerId,"serverId":"$serverId"}"""
                val chosen =
                    PlayerRosterParser.parseChosen(postJson("$GUIDE_BASE/user/player/choose", body, language, xToken))
                        ?: throw IllegalStateException("no chosen player in response")
                Result.success(chosen)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** The curated builds for a character, ordered by popularity; the top one is the recommendation context. */
    suspend fun fetchBuilds(
        roleGbId: String,
        xToken: String?,
        language: String = "en",
    ): Result<List<BuildSummary>> =
        withContext(Dispatchers.IO) {
            try {
                val raw = get("$GUIDE_BASE/introduction/list?roleGbId=${enc(roleGbId)}", language, xToken)
                Result.success(BuildListParser.parse(raw, language))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * One build. With an [xToken] the guide fills `.current` with the chosen player's actually
     * equipped weapon/echo; without it those come back unset. Returns `Result.success(null)`
     * when the guide has no public data for the id.
     */
    suspend fun fetchBuild(
        roleGbId: String,
        buildId: Int,
        xToken: String?,
        language: String = "en",
    ): Result<CharacterBuild?> =
        withContext(Dispatchers.IO) {
            try {
                val raw =
                    get("$GUIDE_BASE/introduction/info?roleGbId=${enc(roleGbId)}&id=$buildId", language, xToken)
                Result.success(CharacterBuildParser.parse(raw, language))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // -- transport ------------------------------------------------------------

    private fun baseForm(deviceNum: String): String =
        buildString {
            append("redirect_uri=1&__e__=1&pack_mark=1&platform=h5")
            append("&version=$SDK_VERSION&sdkVersion=$SDK_VERSION")
            append("&deviceNum=${enc(deviceNum)}")
            append("&projectId=$PROJECT_ID&productId=$PRODUCT_ID&channelId=$CHANNEL_ID")
        }

    private fun guideLoginBody(
        cuid: String,
        cName: String,
        accessToken: String,
    ): String {
        val body = JsonObject()
        body.addProperty("cUid", cuid)
        body.addProperty("cName", cName)
        body.addProperty("accessToken", accessToken)
        return body.toString()
    }

    private fun postForm(
        url: String,
        body: String,
    ): String =
        execute(url, "POST") { connection ->
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("Kr-Ver", SDK_VERSION)
            connection.setRequestProperty("Origin", SDK_ORIGIN)
            connection.setRequestProperty("Referer", "$SDK_ORIGIN/")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }

    private fun postJson(
        url: String,
        json: String,
        language: String,
        xToken: String? = null,
    ): String =
        execute(url, "POST") { connection ->
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
            connection.setRequestProperty("x-language", language)
            connection.setRequestProperty("Origin", GUIDE_ORIGIN)
            connection.setRequestProperty("Referer", "$GUIDE_ORIGIN/")
            if (xToken != null) connection.setRequestProperty("x-token", xToken)
            connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }

    private fun get(
        url: String,
        language: String?,
        xToken: String?,
    ): String =
        execute(url, "GET") { connection ->
            if (language != null) connection.setRequestProperty("x-language", language)
            connection.setRequestProperty("Origin", GUIDE_ORIGIN)
            connection.setRequestProperty("Referer", "$GUIDE_ORIGIN/")
            if (xToken != null) connection.setRequestProperty("x-token", xToken)
        }

    private fun execute(
        url: String,
        method: String,
        configure: (HttpURLConnection) -> Unit,
    ): String {
        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = method != "GET"
                setRequestProperty("Accept", ACCEPT)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
        return try {
            configure(connection)
            val status = connection.responseCode
            if (status != 200) {
                Log.w("KuroLogin", "HTTP $status for $method ${url.substringBefore('?')}")
                throw KuroLoginException("http", "HTTP $status for $method ${url.substringBefore('?')}")
            }
            val text = StringBuilder()
            val buffer = CharArray(8192)
            var total = 0L
            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val read = reader.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) {
                        throw KuroLoginException("http", "response exceeds the $MAX_RESPONSE_BYTES byte cap")
                    }
                    text.append(buffer, 0, read)
                }
            }
            text.toString()
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Logs the offending Kuro response body (an error envelope, never the password) and aborts the hop. */
    private fun fail(
        step: String,
        detail: String,
        raw: String,
    ): Nothing {
        Log.w("KuroLogin", "$step rejected — ${raw.take(400)}")
        throw KuroLoginException(step, detail)
    }

    // -- password transform (mirrors Kuro's kr-sdk.js encodePassword) -----------

    /**
     * Reproduces Kuro's client-side password transform: base64-encode, then two deterministic
     * character shuffles. The passport server expects exactly this encoding — a plaintext
     * password is rejected with a generic "account or password" error, which is why the first
     * attempt failed even though the credentials were correct.
     *
     * `internal` so the transform can be pinned against known vectors in a unit test.
     */
    internal fun encodePassword(password: String): String {
        val b64 = base64Encode(password.toByteArray(Charsets.UTF_8))
        val chars = b64.toCharArray()
        shuffle(chars, 0)
        shuffle(chars, 1)
        return String(chars)
    }

    /**
     * The SDK's shuffle. The JS runs the swap as a side effect of the loop *condition*, so the
     * swap at the boundary index still fires before the loop stops — a check-then-swap reading
     * drops it and produces the wrong password. Swap first, then decide whether to continue.
     */
    private fun shuffle(
        chars: CharArray,
        seed: Int,
    ) {
        var s = seed
        while (s < chars.size) {
            if (s + 2 >= chars.size) break
            val tmp = chars[s]
            chars[s] = chars[s + 2]
            chars[s + 2] = tmp
            if (s + 6 >= chars.size) break
            s += 4
        }
    }

    // -- base64 (hand-rolled: android.util.Base64 is stubbed in unit tests, and the
    //    transform must be pure so it can be pinned against known vectors) -------

    private val B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /** Standard base64 with `=` padding and no line breaks — the equivalent of JS `btoa`. */
    private fun base64Encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 3 <= bytes.size) {
            val n =
                (bytes[i].toInt() and 0xFF shl 16) or
                    (bytes[i + 1].toInt() and 0xFF shl 8) or
                    (bytes[i + 2].toInt() and 0xFF)
            sb.append(B64_ALPHABET[(n shr 18) and 63])
            sb.append(B64_ALPHABET[(n shr 12) and 63])
            sb.append(B64_ALPHABET[(n shr 6) and 63])
            sb.append(B64_ALPHABET[n and 63])
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val n = bytes[i].toInt() and 0xFF shl 16
                sb.append(B64_ALPHABET[(n shr 18) and 63])
                sb.append(B64_ALPHABET[(n shr 12) and 63])
                sb.append("==")
            }
            2 -> {
                val n = (bytes[i].toInt() and 0xFF shl 16) or (bytes[i + 1].toInt() and 0xFF shl 8)
                sb.append(B64_ALPHABET[(n shr 18) and 63])
                sb.append(B64_ALPHABET[(n shr 12) and 63])
                sb.append(B64_ALPHABET[(n shr 6) and 63])
                sb.append("=")
            }
        }
        return sb.toString()
    }
}
