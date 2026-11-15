package com.wuwaconfig.app.config

import com.google.gson.JsonParser
import com.wuwaconfig.app.util.writeAtomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kuro's official Wuthering Waves guide, used for **characters only**.
 *
 * This is the first-party alternative to the wiki scrape. It is the sole source in
 * the app that speaks about *current* game state rather than the player's own
 * history, so it is also the only thing that can say "these characters are featured
 * right now."
 *
 * ## What it is not
 *
 * The endpoint returns no banner entity. There is no banner id, no pool, no start
 * or end date, and **no weapon data at all** — 14 candidate paths under the
 * weapon, gacha, banner and pool route families on this host all answer 404, and
 * the guide's own JS bundle references no such route. Every path this app uses
 * lives here, and the list was extracted from the bundle rather than guessed:
 * `/role/avatar/list`, `/role/info`, and the `/introduction` and `/user` families.
 *
 * So the [OfficialStatus] below is an *editorial* flag on a build guide, not a
 * gacha banner. Observed on 2026-10-04: it named Hsin (NEW) and Iuno, Chisa and
 * Mornye (UP), while the banners actually running were Hsin, Iuno and Chisa —
 * **Mornye was a false positive.** It is reported as-is, including the falsehood,
 * because a silently trimmed list would misrepresent a source as more reliable than
 * it is. [featuredCharacters] therefore keeps everything the guide claims.
 *
 * The player's own pull history remains the authority on what they have pulled; this
 * only speaks about what Kuro is featuring.
 */
object KuroGuide {
    /**
     * Kuro's official guide is used ONLY by the My Character feature and the Pity
     * Tracker — the two places that legitimately need current guide/character data.
     * Everything else (calculator tables, etc.) is served from our own repo under
     * `tools/data/`, gathered by a GitHub Action.
     */
    private const val AVATAR_LIST = "https://guide-server.aki-game.net/role/avatar/list"
    private const val GUIDE_BASE = "https://guide-server.aki-game.net"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    /**
     * On-disk cache of the last avatar list fetched from [AVATAR_LIST], with an
     * ETag for conditional GET and a timestamp for the TTL window. Mirrors the
     * calculator-data refresh pattern in [MaterialData] so a page visit does not
     * re-ping Kuro's guide every time.
     */
    private const val CACHE_FILE = "kuro_avatar_list.json"
    private const val TTL_MS = 6L * 60 * 60 * 1000

    @Volatile
    private var cachedBody: String? = null

    @Volatile
    private var cachedEtag: String? = null

    @Volatile
    private var cachedTimestamp: Long = 0L

    /**
     * Bounded so a hostile or truncated response cannot drive an unbounded read into
     * the heap. The real payload for 59 characters is roughly 60 KB.
     */
    private const val MAX_RESPONSE_BYTES = 2L * 1024 * 1024

    /**
     * Fetches every character with its Kuro art and editorial status.
     *
     * Returns [Result.failure] rather than throwing so a caller can fall back to
     * bundled art without special-casing; this is enrichment, never a hard
     * dependency.
     */
    suspend fun fetchCharacters(language: String = "en"): Result<List<OfficialCharacter>> =
        withContext(Dispatchers.IO) {
            try {
                val context = com.wuwaconfig.app.WuWaConfigApp.instance
                val cacheFile = java.io.File(context.filesDir, CACHE_FILE)
                val now = System.currentTimeMillis()

                // In-memory fast path.
                if (cachedBody != null && now - cachedTimestamp < TTL_MS) {
                    return@withContext Result.success(parseAvatarList(cachedBody!!, language))
                }

                val disk = readCache(cacheFile)
                if (disk != null && now - disk.timestamp < TTL_MS) {
                    cachedBody = disk.body
                    cachedEtag = disk.etag
                    cachedTimestamp = disk.timestamp
                    return@withContext Result.success(parseAvatarList(disk.body, language))
                }

                val etag = disk?.etag ?: cachedEtag
                return@withContext when (val result = httpFetch(AVATAR_LIST, etag)) {
                    is FetchResult.NotModified -> {
                        val ts = now
                        val body = disk?.body ?: cachedBody
                        if (body != null) {
                            writeCache(cacheFile, body, result.etag ?: disk?.etag, ts)
                            cachedBody = body
                            cachedEtag = result.etag ?: disk?.etag
                            cachedTimestamp = ts
                            Result.success(parseAvatarList(body, language))
                        } else {
                            Result.failure(IllegalStateException("no cached body on 304"))
                        }
                    }
                    is FetchResult.Modified -> {
                        val parsed = runCatching { parseAvatarList(result.body, language) }.getOrNull()
                        if (parsed != null) {
                            writeCache(cacheFile, result.body, result.etag, now)
                            cachedBody = result.body
                            cachedEtag = result.etag
                            cachedTimestamp = now
                            Result.success(parsed)
                        } else {
                            // Keep whatever is current rather than swapping in garbage.
                            val body = disk?.body ?: cachedBody
                            if (body != null) {
                                Result.success(parseAvatarList(body, language))
                            } else {
                                Result.failure(IllegalStateException("malformed avatar payload"))
                            }
                        }
                    }
                    is FetchResult.Failed -> {
                        val body = disk?.body ?: cachedBody
                        if (body != null) {
                            Result.success(parseAvatarList(body, language))
                        } else {
                            Result.failure(IllegalStateException(result.error))
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * The guide characters already cached on disk, **without any network request**.
     *
     * The Pity tracker uses this so that opening it never talks to a web server; the
     * cache is written by [fetchCharacters], which only the My Character feature
     * calls. Returns an empty list when no cache exists yet (the tracker then falls
     * back to the player's own pull history).
     */
    fun cachedCharacters(language: String = "en"): List<OfficialCharacter> =
        try {
            val context = com.wuwaconfig.app.WuWaConfigApp.instance
            val disk = readCache(java.io.File(context.filesDir, CACHE_FILE)) ?: return emptyList()
            parseAvatarList(disk.body, language)
        } catch (e: Exception) {
            emptyList()
        }

    /** A weapon the guide recommends for a resonator; `status == 1` is the signature one. */
    data class FeaturedWeapon(
        val gbId: String,
        val name: String,
        val pictureUrl: String,
        val star: Int,
    )

    /**
     * The signature (featured) weapon of [roleGbId], from the guide's build page.
     *
     * The page's "Recom m. Weapons" block is `data.weapon.items`: the entry with
     * `status == 1` is the signature weapon and the rest are alternatives. Kuro's
     * weapon banner features exactly that weapon. This is a guide-server call, which
     * is allowed for the Pity Tracker (the tracker is one of the two features
     * permitted to use Kuro's guide).
     */
    suspend fun fetchFeaturedWeapon(roleGbId: String): Result<FeaturedWeapon> =
        withContext(Dispatchers.IO) {
            try {
                val strategyId =
                    primaryStrategyId(roleGbId)
                        ?: return@withContext Result.failure(IllegalStateException("no build guide for role $roleGbId"))
                val info =
                    getJson("$GUIDE_BASE/introduction/info?id=$strategyId")
                        ?: return@withContext Result.failure(IllegalStateException("no build info for strategy $strategyId"))
                val weapon =
                    JsonParser.parseString(info).asJsonObject["data"]?.asJsonObject?.get("weapon")?.asJsonObject
                        ?: return@withContext Result.failure(IllegalStateException("no weapon block"))
                val items =
                    weapon["items"]?.takeIf { it.isJsonArray }?.asJsonArray
                        ?: return@withContext Result.failure(IllegalStateException("no weapon list"))
                if (items.size() == 0) return@withContext Result.failure(IllegalStateException("empty weapon list"))
                val primary =
                    items.map { it.asJsonObject }.firstOrNull { it.get("status")?.asInt == 1 }
                        ?: items[0].asJsonObject
                val gbId =
                    primary["gbId"]?.takeIf { !it.isJsonNull }?.asString
                        ?: return@withContext Result.failure(IllegalStateException("weapon missing gbId"))
                val star = primary["star"]?.takeIf { !it.isJsonNull }?.asInt ?: 5
                val picture = primary["pictureUrl"]?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val name =
                    primary["texts"]?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.firstOrNull()?.asJsonObject?.get("name")?.asString.orEmpty()
                Result.success(FeaturedWeapon(gbId, name, picture, star))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun primaryStrategyId(roleGbId: String): Int? {
        val body = getJson("$GUIDE_BASE/introduction/list?roleGbId=$roleGbId") ?: return null
        val arr = JsonParser.parseString(body).asJsonObject["data"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        return arr.firstOrNull()?.asJsonObject?.get("id")?.asInt
    }

    /** Small bounded GET (the avatar list has its own ETag path). */
    private fun getJson(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "WuWaConfig/1.0")
                }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val out = StringBuilder()
            val buffer = CharArray(8192)
            var total = 0L
            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val read = reader.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) return null
                    out.append(buffer, 0, read)
                }
            }
            out.toString()
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun httpFetch(
        url: String,
        etag: String?,
    ): FetchResult {
        var connection: HttpURLConnection? = null
        return try {
            connection =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "WuWaConfig/1.0")
                    etag?.takeIf { it.isNotEmpty() }?.let { setRequestProperty("If-None-Match", it) }
                }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> FetchResult.NotModified(connection.getHeaderField("ETag"))
                HttpURLConnection.HTTP_OK -> {
                    val text = StringBuilder()
                    val buffer = CharArray(8192)
                    var total = 0L
                    connection.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val read = reader.read(buffer)
                            if (read == -1) break
                            total += read
                            if (total > MAX_RESPONSE_BYTES) {
                                throw IllegalStateException("Response exceeds the $MAX_RESPONSE_BYTES byte cap")
                            }
                            text.append(buffer, 0, read)
                        }
                    }
                    FetchResult.Modified(text.toString(), connection.getHeaderField("ETag"))
                }
                else -> {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                    FetchResult.Failed("HTTP $code")
                }
            }
        } catch (e: Exception) {
            FetchResult.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    private data class AvatarCache(
        val body: String,
        val etag: String?,
        val timestamp: Long,
    )

    private fun readCache(file: java.io.File): AvatarCache? {
        if (!file.exists()) return null
        return try {
            val root = com.google.gson.JsonParser.parseReader(file.bufferedReader()).asJsonObject
            val body = root.get("body")?.asString ?: return null
            val etag = root.get("etag")?.takeIf { !it.isJsonNull }?.asString
            val timestamp = root.get("timestamp")?.asLong ?: return null
            AvatarCache(body, etag, timestamp)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(
        file: java.io.File,
        body: String,
        etag: String?,
        timestamp: Long,
    ) {
        val root =
            com.google.gson.JsonObject().apply {
                addProperty("body", body)
                add("etag", etag?.let { com.google.gson.JsonPrimitive(it) } ?: com.google.gson.JsonNull.INSTANCE)
                addProperty("timestamp", timestamp)
            }
        file.writeAtomic(root.toString())
    }

    /**
     * Parses `/role/avatar/list`.
     *
     * Internal and pure so it can be tested without a network. Two shapes are
     * tolerated deliberately: the documented `{code, data:[...]}`, and a bare array,
     * because the endpoint returns the envelope today and a future revision could
     * reasonably drop it. A row missing an id or a name is skipped rather than
     * rendered as a blank card.
     */
    internal fun parseAvatarList(
        json: String,
        preferredLanguage: String = "en",
    ): List<OfficialCharacter> {
        val root = JsonParser.parseString(json)
        val rows =
            when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject && root.asJsonObject.has("data") -> root.asJsonObject["data"].asJsonArray
                else -> throw IllegalArgumentException("Unexpected avatar list shape")
            }

        val result = ArrayList<OfficialCharacter>(rows.size())
        for (element in rows) {
            if (!element.isJsonObject) continue
            val obj = element.asJsonObject
            val roleGbId = obj.get("roleGbId")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            if (roleGbId.isEmpty()) continue

            val texts = obj.getAsJsonArray("texts")
            val name =
                findName(texts, preferredLanguage)
                    ?: findName(texts, "en")
                    ?: findName(texts, null)
                    ?: continue

            result.add(
                OfficialCharacter(
                    roleGbId = roleGbId,
                    name = name,
                    star = obj.get("star")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    status = OfficialStatus.fromCode(obj.get("roleStatus")?.takeIf { !it.isJsonNull }?.asInt),
                    cardPictureUrl = obj.get("cardPictureUrl")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                    illustrationPictureUrl = obj.get("illustrationPictureUrl")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                ),
            )
        }
        return result
    }

    /**
     * Picks a name out of the `texts` array.
     *
     * [fallbackToAny] exists because a language can be missing entirely for a
     * newly added character, and a card with a name in the wrong language still
     * beats no card at all.
     */
    private fun findName(
        texts: com.google.gson.JsonElement?,
        language: String?,
    ): String? {
        if (texts == null || !texts.isJsonArray) return null
        var anyName: String? = null
        for (entry in texts.asJsonArray) {
            if (!entry.isJsonObject) continue
            val obj = entry.asJsonObject
            val entryLanguage = obj.get("language")?.takeIf { !it.isJsonNull }?.asString
            val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            if (name.isEmpty()) continue
            if (anyName == null) anyName = name
            if (language != null && entryLanguage == language) return name
        }
        return if (language == null) anyName else null
    }

    /** The characters the guide is currently featuring: NEW plus UP. */
    fun featuredCharacters(characters: List<OfficialCharacter>): List<OfficialCharacter> = characters.filter { it.status == OfficialStatus.NEWLY_LAUNCHED || it.status == OfficialStatus.UP }
}

/**
 * The guide's per-character flag.
 *
 * The codes were decoded from the site bundle, where they are declared as
 * `ALREADY_ONLINE=1, NEWLY_LAUNCHED=2, UP=3, PROSPECT=4` — those names are kept
 * because inventing friendlier ones would hide where the meaning comes from.
 */
enum class OfficialStatus(val code: Int) {
    ALREADY_ONLINE(1),
    NEWLY_LAUNCHED(2),
    UP(3),
    PROSPECT(4),

    /** A code the site adds later. Treated as ordinary content, not as "featured". */
    UNKNOWN(-1),
    ;

    companion object {
        fun fromCode(code: Int?): OfficialStatus = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** One character as the official guide describes it. */
data class OfficialCharacter(
    val roleGbId: String,
    val name: String,
    val star: Int,
    val status: OfficialStatus,
    /** Square icon from Kuro's CDN; empty when the guide has no art for this entry. */
    val cardPictureUrl: String,
    /** Full-body splash from Kuro's CDN; empty when absent. */
    val illustrationPictureUrl: String,
)
