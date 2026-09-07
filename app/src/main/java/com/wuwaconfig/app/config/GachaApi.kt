package com.wuwaconfig.app.config

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wuwaconfig.app.model.GachaApiResponse
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaPool
import com.wuwaconfig.app.model.GachaRecord
import com.wuwaconfig.app.model.PityPrediction
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GachaApi {
    // Standard pools are the permanent "Standard" banners; any 5★ pulled there is
    // a standard 5★ (used to decide character-banner soft-pity status).
    private val STANDARD_POOLS = setOf("3", "8", "11")
    private val CHARACTER_POOLS = setOf("1", "7", "10")
    private val WEAPON_POOLS = setOf("2", "6", "9")

    // WuWa's base 5★ rate (constant rate before soft pity kicks in).
    private const val BASE_RATE = 0.067

    private val gson = Gson()

    data class GachaUrlParams(
        val playerId: String,
        val recordId: String,
        val cardPoolId: String,
        val cardPoolType: String,
        val serverId: String,
        val languageCode: String,
    )

    fun parseUrl(url: String): GachaUrlParams? {
        val fragment = url.substringAfter("#/record?")
        val params =
            fragment.split("&").associate {
                val parts = it.split("=", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }
        val playerId = params["player_id"] ?: return null
        val recordId = params["record_id"] ?: return null
        val cardPoolId = params["resources_id"] ?: return null
        val cardPoolType = params["gacha_type"] ?: return null
        val serverId = params["svr_id"] ?: return null
        val languageCode = params["lang"] ?: "en"
        return GachaUrlParams(playerId, recordId, cardPoolId, cardPoolType, serverId, languageCode)
    }

    private fun getEndpoint(playerId: String): String {
        return if (playerId.startsWith("1")) {
            "https://gmserver-api.aki-game2.com/gacha/record/query"
        } else {
            "https://gmserver-api.aki-game2.net/gacha/record/query"
        }
    }

    fun fetchAllRecords(params: GachaUrlParams): Result<GachaData> {
        return try {
            val endpoint = getEndpoint(params.playerId)
            val records = mutableListOf<GachaRecord>()
            val poolsWithData = mutableListOf<String>()

            var anyFailure: Throwable? = null
            var anySuccess = false
            var lastErrorMsg: String? = null
            for (pool in GachaPool.ALL) {
                val body =
                    mapOf(
                        "playerId" to params.playerId,
                        "recordId" to params.recordId,
                        "cardPoolId" to params.cardPoolId,
                        "cardPoolType" to pool.type,
                        "serverId" to params.serverId,
                        "languageCode" to params.languageCode,
                    )

                val result = postRequest(endpoint, body)
                if (result.isFailure) {
                    anyFailure = result.exceptionOrNull()
                    continue
                }
                val response = result.getOrThrow()

                if (response.code == 0 && !response.data.isNullOrEmpty()) {
                    records.addAll(response.data)
                    poolsWithData.add(pool.type)
                    anySuccess = true
                } else if (response.code != 0) {
                    lastErrorMsg = response.message
                }
            }

            if (!anySuccess && anyFailure != null) {
                return Result.failure(anyFailure)
            }
            // Every pool answered but rejected the query (bad/expired recordId,
            // wrong playerId...) — that must not masquerade as an empty history.
            if (!anySuccess) {
                return Result.failure(
                    Exception(lastErrorMsg ?: "Server returned no gacha data for any pool"),
                )
            }

            val totalPulls = records.sumOf { it.count.coerceAtLeast(1) }
            val fiveStars = records.filter { it.qualityLevel == 5 }.sumOf { it.count.coerceAtLeast(1) }
            val fourStars = records.filter { it.qualityLevel == 4 }.sumOf { it.count.coerceAtLeast(1) }

            val pity5 = calculateAvgPity(records, 5)
            val pity4 = calculateAvgPity(records, 4)

            val predictions = mutableListOf<PityPrediction>()
            val standardFiveStars =
                records
                    .filter { it.cardPoolType in STANDARD_POOLS && it.qualityLevel == 5 }
                    .map { it.name }
                    .toSet()
            for (pool in GachaPool.ALL) {
                val poolRecords = records.filter { it.cardPoolType == pool.type }
                if (poolRecords.isEmpty()) continue

                val isCharacterBanner = pool.type in CHARACTER_POOLS
                val isWeaponBanner = pool.type in WEAPON_POOLS
                val pred =
                    if (isCharacterBanner) {
                        calcCharacterPrediction(poolRecords, pool, standardFiveStars)
                    } else if (isWeaponBanner) {
                        calcWeaponPrediction(poolRecords, pool)
                    } else {
                        null
                    }
                if (pred != null) predictions.add(pred)
            }

            Result.success(
                GachaData(
                    records = records.sortedByDescending { it.time },
                    poolsWithData = poolsWithData,
                    totalPulls = totalPulls,
                    fiveStars = fiveStars,
                    fourStars = fourStars,
                    avgPity5 = pity5,
                    avgPity4 = pity4,
                    predictions = predictions,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postRequest(
        endpoint: String,
        body: Map<String, String>,
    ): Result<GachaApiResponse> {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val jsonBody = gson.toJson(body)
            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
                conn.errorStream?.bufferedReader()?.use { it.readText() }
                return Result.failure(Exception("HTTP $responseCode"))
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val map: Map<String, Any?> = gson.fromJson(responseText, mapType)

            val code = (map["code"] as? Double)?.toInt() ?: -1
            val message = map["message"] as? String ?: ""
            val dataRaw = map["data"] as? List<Map<String, Any?>> ?: emptyList()

            val records =
                dataRaw.mapNotNull { item ->
                    try {
                        GachaRecord(
                            cardPoolType = (item["cardPoolType"] as? String) ?: return@mapNotNull null,
                            qualityLevel = (item["qualityLevel"] as? Number)?.toInt() ?: 0,
                            name = item["name"] as? String ?: "",
                            count = (item["count"] as? Number)?.toInt() ?: 1,
                            time = item["time"] as? String ?: "",
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

            Result.success(GachaApiResponse(code = code, message = message, data = records))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    internal fun calculateAvgPity(
        records: List<GachaRecord>,
        rarity: Int,
    ): Double {
        if (records.isEmpty()) return 0.0
        // Walk records in chronological order, treating each record as `count` pulls
        // (Kuro's API collapses 10-pulls into a single entry with count=10).
        val sorted = records.sortedBy { it.time }
        val groups = mutableListOf<Int>()
        var count = 0
        for (rec in sorted) {
            count += rec.count.coerceAtLeast(1)
            if (rec.qualityLevel == rarity) {
                groups.add(count)
                count = 0
            }
        }
        if (groups.isEmpty()) return 0.0
        return groups.average()
    }

    internal fun isStandardFive(
        name: String,
        standardFiveStars: Set<String>,
    ): Boolean {
        return name in standardFiveStars
    }

    internal fun calcPullsSinceLastFourStar(records: List<GachaRecord>): Int {
        val sorted = records.sortedBy { it.time }
        val lastFourIndex = sorted.indexOfLast { it.qualityLevel == 4 || it.qualityLevel == 5 }
        if (lastFourIndex < 0) return sorted.sumOf { it.count.coerceAtLeast(1) }.coerceAtMost(10)
        return sorted.drop(lastFourIndex + 1).sumOf { it.count.coerceAtLeast(1) }
    }

    internal fun estimatedSoftPityPulls(
        pullsSinceLastFive: Int,
        softPityStart: Int,
        hardPity: Int,
    ): Int {
        // WuWa's soft pity rate ramps from ~0.067 (6.7%) at pull `softPityStart`
        // up to ~1.0 (100%) at `hardPity`. Linear approximation:
        //   p_effective(p) = 0.067 + (p - softPityStart) * 0.933 / (hardPity - softPityStart)
        // Expected additional pulls = 1 / p_effective, rounded up (ceil) so users
        // see a real number even in late soft-pity, clamped to [1, hardPity].
        if (pullsSinceLastFive >= hardPity) return 1
        val rate =
            BASE_RATE +
                (pullsSinceLastFive - softPityStart) *
                (1.0 - BASE_RATE) /
                (hardPity - softPityStart)
        val safeRate = rate.coerceAtLeast(BASE_RATE)
        val expected = (1.0 / safeRate).let { kotlin.math.ceil(it).toInt() }
        return expected.coerceIn(1, hardPity)
    }

    internal fun calcCharacterPrediction(
        records: List<GachaRecord>,
        pool: GachaPool,
        standardFiveStars: Set<String>,
    ): PityPrediction {
        val HARD_PITY = 80
        val SOFT_PITY_START = 66
        val sorted = records.sortedBy { it.time }
        val fiveStarRecords = sorted.filter { it.qualityLevel == 5 }

        val pullsSinceLastFive: Int
        val lastFiveName: String
        val lastFiveTime: String
        val isLastFiveStandard: Boolean

        if (fiveStarRecords.isNotEmpty()) {
            val lastFive = fiveStarRecords.last()
            lastFiveName = lastFive.name
            lastFiveTime = lastFive.time
            isLastFiveStandard = isStandardFive(lastFiveName, standardFiveStars)
            val lastFiveIndex = sorted.indexOfLast { it.qualityLevel == 5 }
            // Account for count (Kuro API may collapse multi-pulls into one record).
            pullsSinceLastFive = sorted.drop(lastFiveIndex + 1).sumOf { it.count.coerceAtLeast(1) }
        } else {
            lastFiveName = ""
            lastFiveTime = ""
            isLastFiveStandard = false
            pullsSinceLastFive = sorted.sumOf { it.count.coerceAtLeast(1) }
        }

        val status =
            if (fiveStarRecords.isEmpty()) {
                "Unknown"
            } else if (isLastFiveStandard) {
                "Guaranteed"
            } else {
                "50/50"
            }

        // The featured (rate-up) character of the current/last character banner: the most
        // recent non-standard 5★ pulled. Used to name the prediction instead of the
        // generic "Next ★5". Falls back to "" (UI then uses the pool label).
        val currentCharacterName =
            fiveStarRecords
                .filter { !isStandardFive(it.name, standardFiveStars) }
                .lastOrNull()
                ?.name ?: ""

        val nearbyFives =
            fiveStarRecords.filter {
                it.name !in standardFiveStars
            }
        val avgCharPity =
            if (nearbyFives.size >= 2) {
                val nearbySet = nearbyFives.toSet()
                val pityGroups = mutableListOf<Int>()
                var cnt = 0
                for (rec in sorted) {
                    cnt += rec.count.coerceAtLeast(1)
                    if (rec.qualityLevel == 5 && rec in nearbySet) {
                        pityGroups.add(cnt)
                        cnt = 0
                    }
                }
                if (pityGroups.isNotEmpty()) pityGroups.average().toInt() else HARD_PITY
            } else {
                HARD_PITY
            }

        val isInSoftPity = pullsSinceLastFive >= SOFT_PITY_START
        val pullsUntilHardPity = maxOf(HARD_PITY - pullsSinceLastFive, 0)

        val estimated =
            if (isInSoftPity) {
                estimatedSoftPityPulls(pullsSinceLastFive, SOFT_PITY_START, HARD_PITY)
            } else {
                maxOf(avgCharPity - pullsSinceLastFive, 1)
            }

        val pulls4 = calcPullsSinceLastFourStar(sorted)

        return PityPrediction(
            poolType = pool.type,
            poolLabel = pool.label,
            status = status,
            lastFiveStarName = lastFiveName,
            lastFiveStarTime = lastFiveTime,
            currentCharacterName = currentCharacterName,
            // When Guaranteed but we have no recent featured ★5 to name, the
            // current banner's featured character is *unknown* from the API.
            currentFeaturedKnown = currentCharacterName.isNotEmpty(),
            pullsSinceLastFive = pullsSinceLastFive,
            estimatedNextFive = estimated,
            hardPity = HARD_PITY,
            softPityThreshold = SOFT_PITY_START,
            isInSoftPity = isInSoftPity,
            pullsUntilHardPity = pullsUntilHardPity,
            pullsSinceLastFourStar = pulls4,
            estimatedNextFourStar = maxOf(10 - pulls4, 1),
        )
    }

    internal fun calcWeaponPrediction(
        records: List<GachaRecord>,
        pool: GachaPool,
    ): PityPrediction {
        val HARD_PITY = 70
        val SOFT_PITY_START = 57
        val sorted = records.sortedBy { it.time }
        val fiveStarRecords = sorted.filter { it.qualityLevel == 5 }

        val pullsSinceLastFive: Int
        val lastFiveName: String
        val lastFiveTime: String

        if (fiveStarRecords.isNotEmpty()) {
            val lastFive = fiveStarRecords.last()
            lastFiveName = lastFive.name
            lastFiveTime = lastFive.time
            val lastFiveIndex = sorted.indexOfLast { it.qualityLevel == 5 }
            pullsSinceLastFive = sorted.drop(lastFiveIndex + 1).sumOf { it.count.coerceAtLeast(1) }
        } else {
            lastFiveName = ""
            lastFiveTime = ""
            pullsSinceLastFive = sorted.sumOf { it.count.coerceAtLeast(1) }
        }

        val isInSoftPity = pullsSinceLastFive >= SOFT_PITY_START
        val pullsUntilHardPity = maxOf(HARD_PITY - pullsSinceLastFive, 0)
        val estimated =
            if (isInSoftPity) {
                estimatedSoftPityPulls(pullsSinceLastFive, SOFT_PITY_START, HARD_PITY)
            } else {
                maxOf(65 - pullsSinceLastFive, 1)
            }

        val pulls4 = calcPullsSinceLastFourStar(sorted)

        return PityPrediction(
            poolType = pool.type,
            poolLabel = pool.label,
            status = "75/25",
            lastFiveStarName = lastFiveName,
            lastFiveStarTime = lastFiveTime,
            pullsSinceLastFive = pullsSinceLastFive,
            estimatedNextFive = estimated,
            hardPity = HARD_PITY,
            softPityThreshold = SOFT_PITY_START,
            isInSoftPity = isInSoftPity,
            pullsUntilHardPity = pullsUntilHardPity,
            pullsSinceLastFourStar = pulls4,
            estimatedNextFourStar = maxOf(10 - pulls4, 1),
        )
    }
}
