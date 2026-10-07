package com.wuwaconfig.app.config

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.wuwaconfig.app.model.AscensionPhaseCost
import com.wuwaconfig.app.model.CalculatorCharacter
import com.wuwaconfig.app.model.CalculatorWeapon
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.model.SkillLevelCost
import com.wuwaconfig.app.model.WeaponAscensionPhaseCost
import com.wuwaconfig.app.util.writeAtomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MaterialData internal constructor(
    val characters: Map<String, CalculatorCharacter>,
    val weapons: Map<String, CalculatorWeapon>,
    val characterAscensionPhases: List<AscensionPhaseCost>,
    val skillMainCosts: List<SkillLevelCost>,
    val skillInherentCosts: List<SkillLevelCost>,
    val skillStatBonusCosts: List<SkillLevelCost>,
    val weaponAscensionByRarity: Map<String, List<WeaponAscensionPhaseCost>>,
) {
    companion object {
        private const val ASSET_PATH = "config/calculator_materials.json"

        /** On-disk copy of the last JSON fetched from [GameProfile.calculatorDataUrl]. */
        private const val CACHE_FILE = "calculator_materials_remote.json"

        /**
         * How often the app re-checks the remote JSON. Six hours matches the
         * GitHub Action's publish cadence, so a wiki edit lands in the app within
         * one Action tick plus one app tick.
         */
        private const val TTL_MS = 6L * 60 * 60 * 1000

        @Volatile
        private var instance: MaterialData? = null

        fun load(assets: AssetManager): MaterialData {
            instance?.let { return it }
            val loaded = read(assets)
            instance = loaded
            return loaded
        }

        fun get(): MaterialData = instance ?: read(null)

        /**
         * Overrides the cached instance so [MaterialCalculator] can be exercised
         * without an AssetManager.
         *
         * The real asset cannot be read in a JVM test (`Context.assets` is
         * stubbed to null under `isReturnDefaultValues`), and `get()` would
         * otherwise hand every calculation an empty table — which is precisely
         * the state in which an off-by-one in row selection still returns an
         * empty list and passes. Passing null restores the production default.
         */
        internal fun installForTest(data: MaterialData?) {
            instance = data
        }

        /**
         * Kicks off a background refresh of the calculator data from
         * [GameProfile.calculatorDataUrl].
         *
         * Fire-and-forget and silent by design: the calculator keeps showing the
         * current data (bundled asset or last-good remote) until a strictly newer
         * payload lands, at which point the live instance is swapped. A failed or
         * slow fetch therefore never blocks or degrades the UI — the worst case
         * is that the data is as old as the last APK.
         *
         * The whole refresh is wrapped so a bug in the network path can never
         * crash the app; the calculator is not worth a crash over.
         */
        fun refresh(
            context: Context,
            scope: CoroutineScope,
        ) {
            val url = gameProfile().calculatorDataUrl
            if (url.isBlank()) return
            scope.launch(Dispatchers.IO) {
                runCatching { refreshInternal(context, url) }
                    .onFailure { LogRepository.add("Calculator data refresh failed: ${it.message}", LogLevel.WARNING) }
            }
        }

        private fun refreshInternal(
            context: Context,
            url: String,
        ) {
            val cacheFile = File(context.filesDir, CACHE_FILE)
            refresh(cacheFile, url, System.currentTimeMillis())
        }

        /**
         * The refresh core, split out from [refreshInternal] so it can be unit
         * tested without a `Context` or a network: the cache file, URL and clock
         * are all injected. [fetcher] is the test seam for the HTTP layer.
         */
        internal fun refresh(
            cacheFile: File,
            url: String,
            now: Long,
        ) {
            val cached = readCache(cacheFile)

            // Fresh cache: skip the network entirely. This is the common case —
            // the wiki changes a few times a day, not every launch.
            if (cached != null && now - cached.timestamp < TTL_MS) return

            val result = fetcher?.fetch(url, cached?.etag) ?: httpFetch(url, cached?.etag)
            when (result) {
                is FetchResult.NotModified -> {
                    // The file is unchanged; slide the TTL window forward so the
                    // next check is a full TTL away.
                    if (cached != null) writeCache(cacheFile, cached.body, result.etag ?: cached.etag, now)
                }
                is FetchResult.Modified -> {
                    val parsed =
                        runCatching {
                            parse(JsonParser.parseReader(result.body.reader()).asJsonObject)
                        }.getOrNull()
                    // Only swap in data that actually parses. A malformed payload
                    // would otherwise replace good data with empty tables.
                    if (parsed != null) {
                        writeCache(cacheFile, result.body, result.etag, now)
                        instance = parsed
                        LogRepository.add("Calculator data updated from remote", LogLevel.SUCCESS)
                    }
                }
                is FetchResult.Failed -> {
                    // Keep whatever is current. Retry on the next tick.
                }
            }
        }

        /** The conditional-GET seam. Tests override this to avoid the network. */
        internal var fetcher: RemoteFetcher? = null

        internal fun setFetcherForTest(f: RemoteFetcher?) {
            fetcher = f
        }

        /**
         * Conditional GET against the raw GitHub URL. Sends `If-None-Match` when we
         * have an ETag, so an unchanged file costs a 304 (a few hundred bytes)
         * rather than the full ~90KB payload.
         */
        private fun httpFetch(
            url: String,
            etag: String?,
        ): FetchResult {
            var conn: HttpURLConnection? = null
            return try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                etag?.takeIf { it.isNotEmpty() }?.let { conn.setRequestProperty("If-None-Match", it) }

                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> FetchResult.NotModified(conn.getHeaderField("ETag"))
                    HttpURLConnection.HTTP_OK -> {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        FetchResult.Modified(body, conn.getHeaderField("ETag"))
                    }
                    else -> {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }
                        FetchResult.Failed("HTTP $code")
                    }
                }
            } catch (e: Exception) {
                FetchResult.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                conn?.disconnect()
            }
        }

        /** The cached remote payload: the JSON body plus the ETag to revalidate with. */
        private data class RemoteCache(
            val body: String,
            val etag: String?,
            val timestamp: Long,
        )

        private fun readCache(file: File): RemoteCache? {
            if (!file.exists()) return null
            return try {
                val root = JsonParser.parseReader(file.bufferedReader()).asJsonObject
                val body = root.get("body")?.asString ?: return null
                val etag = root.get("etag")?.takeIf { !it.isJsonNull }?.asString
                val timestamp = root.get("timestamp")?.asLong ?: return null
                RemoteCache(body, etag, timestamp)
            } catch (_: Exception) {
                null
            }
        }

        private fun writeCache(
            file: File,
            body: String,
            etag: String?,
            timestamp: Long,
        ) {
            val root =
                JsonObject().apply {
                    addProperty("body", body)
                    add("etag", etag?.let { com.google.gson.JsonPrimitive(it) } ?: com.google.gson.JsonNull.INSTANCE)
                    addProperty("timestamp", timestamp)
                }
            file.writeAtomic(root.toString())
        }

        private fun read(assets: AssetManager?): MaterialData {
            val root =
                try {
                    if (assets != null) {
                        assets.open(ASSET_PATH).bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
                    } else {
                        JsonObject()
                    }
                } catch (e: Exception) {
                    JsonObject()
                }
            return parse(root) ?: MaterialData(
                characters = emptyMap(),
                weapons = emptyMap(),
                characterAscensionPhases = emptyList(),
                skillMainCosts = emptyList(),
                skillInherentCosts = emptyList(),
                skillStatBonusCosts = emptyList(),
                weaponAscensionByRarity = emptyMap(),
            )
        }

        /** Returns null when the payload has no usable tables, so a malformed remote file is rejected. */
        private fun parse(root: JsonObject): MaterialData? {
            val characters = mutableMapOf<String, CalculatorCharacter>()
            runCatching { root.getAsJsonObject("characters") }.getOrNull()?.entrySet()?.forEach { (name, el) ->
                runCatching {
                    val obj = el.asJsonObject
                    val rarity = obj.intOr("rarity")
                    if (rarity <= 0) return@forEach
                    characters[name] =
                        CalculatorCharacter(
                            name = name,
                            rarity = rarity,
                            boss = obj.stringOrNull("boss"),
                            local = obj.stringOrNull("local"),
                            common = obj.stringListOr("common"),
                            wsm = obj.stringListOr("wsm"),
                            dwsm = obj.stringListOr("dwsm"),
                            skillBoss = obj.stringOrNull("skillBoss"),
                        )
                }
            }

            val weapons = mutableMapOf<String, CalculatorWeapon>()
            runCatching { root.getAsJsonObject("weapons") }.getOrNull()?.entrySet()?.forEach { (name, el) ->
                runCatching {
                    val obj = el.asJsonObject
                    val rarity = obj.intOr("rarity")
                    if (rarity <= 0) return@forEach
                    weapons[name] =
                        CalculatorWeapon(
                            name = name,
                            rarity = rarity,
                            ascension = obj.stringListOr("ascension"),
                            common = obj.stringListOr("common"),
                            baseAtk = obj.doubleOrNull("baseAtk"),
                            secondStatType = obj.stringOrNull("secondStatType"),
                            secondStat = obj.doubleOrNull("secondStat"),
                        )
                }
            }

            val charPhases =
                runCatching {
                    root.getAsJsonArray("characterAscensionPhases")?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            AscensionPhaseCost(
                                phase = o.intOr("phase"),
                                shellCredit = o.intOr("shellCredit"),
                                local = o.intOr("local"),
                                common1 = o.intOr("common1"),
                                common2 = o.intOr("common2"),
                                common3 = o.intOr("common3"),
                                common4 = o.intOr("common4"),
                                boss = o.intOr("boss"),
                            )
                        }.getOrNull()
                    } ?: emptyList()
                }.getOrDefault(emptyList())

            fun parseSkillList(json: com.google.gson.JsonArray?): List<SkillLevelCost> =
                runCatching {
                    json?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            SkillLevelCost(
                                level = o.intOr("level"),
                                credit = o.intOr("credit"),
                                wsm1 = o.intOr("wsm1"),
                                dwsm1 = o.intOr("dwsm1"),
                                wsm2 = o.intOr("wsm2"),
                                dwsm2 = o.intOr("dwsm2"),
                                wsm3 = o.intOr("wsm3"),
                                dwsm3 = o.intOr("dwsm3"),
                                wsm4 = o.intOr("wsm4"),
                                dwsm4 = o.intOr("dwsm4"),
                                boss = o.intOr("boss"),
                                // Absent means "no rank gate", which is distinct
                                // from rank 0 — Gson would coerce a missing field
                                // to 0 rather than leaving the default null.
                                unlockRank = if (o.has("unlockRank")) o.intOr("unlockRank") else null,
                            )
                        }.getOrNull()
                    } ?: emptyList()
                }.getOrDefault(emptyList())

            val weaponAscensionByRarity = mutableMapOf<String, List<WeaponAscensionPhaseCost>>()
            runCatching { root.getAsJsonObject("weaponAscensionByRarity") }.getOrNull()?.entrySet()?.forEach { (rarity, el) ->
                runCatching {
                    weaponAscensionByRarity[rarity] =
                        el.asJsonArray.mapNotNull { phaseEl ->
                            runCatching {
                                val o = phaseEl.asJsonObject
                                WeaponAscensionPhaseCost(
                                    phase = o.intOr("phase"),
                                    shellCredit = o.intOr("shellCredit"),
                                    ascension1 = o.intOr("ascension1"),
                                    ascension2 = o.intOr("ascension2"),
                                    ascension3 = o.intOr("ascension3"),
                                    ascension4 = o.intOr("ascension4"),
                                    common1 = o.intOr("common1"),
                                    common2 = o.intOr("common2"),
                                    common3 = o.intOr("common3"),
                                    common4 = o.intOr("common4"),
                                )
                            }.getOrNull()
                        }
                }
            }

            val skillMain = parseSkillList(runCatching { root.getAsJsonArray("skillMainCosts") }.getOrNull())
            val skillInherent = parseSkillList(runCatching { root.getAsJsonArray("skillInherentCosts") }.getOrNull())
            val skillStatBonus = parseSkillList(runCatching { root.getAsJsonArray("skillStatBonusCosts") }.getOrNull())

            // A payload with no usable tables is rejected rather than swapped in:
            // it would silently replace good data with empty ones, and every
            // downstream total would read as 0 while looking plausible.
            if (characters.isEmpty() && weapons.isEmpty() && charPhases.isEmpty() &&
                skillMain.isEmpty() && skillInherent.isEmpty() && skillStatBonus.isEmpty() &&
                weaponAscensionByRarity.isEmpty()
            ) {
                return null
            }

            return MaterialData(
                characters = characters,
                weapons = weapons,
                characterAscensionPhases = charPhases,
                skillMainCosts = skillMain,
                skillInherentCosts = skillInherent,
                skillStatBonusCosts = skillStatBonus,
                weaponAscensionByRarity = weaponAscensionByRarity,
            )
        }

        private fun JsonObject.intOr(
            name: String,
            default: Int = 0,
        ): Int =
            try {
                get(name)?.takeIf { !it.isJsonNull }?.asInt ?: default
            } catch (_: Exception) {
                default
            }

        private fun JsonObject.stringOrNull(name: String): String? =
            try {
                get(name)?.takeIf { !it.isJsonNull }?.asString
            } catch (_: Exception) {
                null
            }

        private fun JsonObject.doubleOrNull(name: String): Double? =
            try {
                get(name)?.takeIf { !it.isJsonNull }?.asDouble
            } catch (_: Exception) {
                null
            }

        private fun JsonObject.stringListOr(name: String): List<String> =
            try {
                getAsJsonArray(name)?.mapNotNull {
                    runCatching { it.takeIf { e -> !e.isJsonNull }?.asString }.getOrNull()
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
    }
}

/** A remote fetch outcome. [etag] is the new ETag to store, if any. */
internal sealed class FetchResult {
    data class NotModified(val etag: String?) : FetchResult()

    data class Modified(
        val body: String,
        val etag: String?,
    ) : FetchResult()

    data class Failed(val error: String) : FetchResult()
}

/** The conditional-GET seam. Tests override this to avoid the network. */
internal interface RemoteFetcher {
    fun fetch(
        url: String,
        etag: String?,
    ): FetchResult
}
