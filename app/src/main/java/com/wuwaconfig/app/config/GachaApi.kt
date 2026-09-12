package com.wuwaconfig.app.config

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wuwaconfig.app.model.GachaApiResponse
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaPool
import com.wuwaconfig.app.model.GachaPoolType
import com.wuwaconfig.app.model.GachaRecord
import com.wuwaconfig.app.model.PityPrediction
import com.wuwaconfig.app.model.SsrInterval
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GachaApi {
    // Standard pools are the permanent "Standard" banners; any 5★ pulled there is
    // a standard 5★ (used to decide character-banner soft-pity status).
    private val STANDARD_POOLS = setOf(GachaPoolType.STANDARD, GachaPoolType.STANDARD_2, GachaPoolType.STANDARD_3)
    private val CHARACTER_POOLS = setOf(GachaPoolType.CHARACTER_EVENT, GachaPoolType.CHARACTER_2, GachaPoolType.CHARACTER_3)
    private val WEAPON_POOLS = setOf(GachaPoolType.WEAPON_EVENT, GachaPoolType.WEAPON_2, GachaPoolType.WEAPON_3)

    // Per-pull probability at the soft-pity threshold (pull 66), derived from the
    // wuwatracker.com empirical distribution (394 125 samples): the conditional
    // rate jumps from ~0.56 % (flat region, pulls 1–65) to ~8.8 % at pull 66,
    // climbing to ~100 % (guaranteed) at pull 80. We model the ramp as a linear
    // interpolation from 15 % → 100 % (pulls 66 → 80), which matches the empirical
    // conditional-expectation curve within ~2 pulls on average.
    private const val SOFT_PITY_RATE_AT_THRESHOLD = 0.15

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
            var anyRejected = false
            var lastErrorMsg: String? = null
            val failedPools = mutableListOf<GachaPoolType>()
            for (pool in GachaPoolType.ALL) {
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
                    failedPools.add(pool)
                    continue
                }
                val response = result.getOrThrow()

                if (response.code == 0 && !response.data.isNullOrEmpty()) {
                    records.addAll(response.data)
                    poolsWithData.add(pool.type)
                    anySuccess = true
                } else if (response.code != 0) {
                    anyRejected = true
                    lastErrorMsg = response.message
                }
            }

            if (!anySuccess && anyFailure != null) {
                return Result.failure(anyFailure)
            }
            // Every pool answered but was rejected by the server (code != 0 — bad/expired
            // recordId, wrong playerId...) — that must not masquerade as an empty
            // history. A pool returning code 0 with empty data is a *valid* empty history
            // (a player with zero pulls in that pool) and is allowed through.
            if (!anySuccess && anyRejected) {
                return Result.failure(
                    Exception(lastErrorMsg ?: "Server returned no gacha data for any pool"),
                )
            }

            // P0-3: a pool whose HTTP call threw is still a failure. By this point every
            // pool either returned code 0 (with data or a valid empty history) or was
            // already rejected above, so failedPools holds only transport failures.
            // The returned GachaData would otherwise be an incomplete history —
            // PityScreen would render predictions for only some pools and the 12h
            // cache would persist the partial result. Surface the failed pool types
            // so the caller can retry instead of caching silently.
            if (failedPools.isNotEmpty()) {
                return Result.failure(
                    Exception(
                        "Gacha data incomplete — failed pools: ${failedPools.joinToString { it.type }}; retry required",
                    ),
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
                    .filter { it.cardPoolType in STANDARD_POOLS.map { it.type } && it.qualityLevel == 5 }
                    .map { it.name }
                    .toSet()
            for (poolType in GachaPoolType.ALL) {
                val poolRecords = records.filter { it.cardPoolType == poolType.type }
                if (poolRecords.isEmpty()) continue

                val isCharacterBanner = poolType in CHARACTER_POOLS
                val isWeaponBanner = poolType in WEAPON_POOLS
                val pool = GachaPool(poolType.type, poolType.label)
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

    // Test seam: fetchAllRecords iterates GachaPoolType.ALL and calls postRequest per
    // pool, so the only way to assert the partial-failure contract without hitting the
    // network is to override the HTTP layer. The real implementation is
    // postRequestReal; postRequest delegates to the override when one is set.
    private var postRequestOverride: ((String, Map<String, String>) -> Result<GachaApiResponse>)? = null

    internal fun setPostRequestForTest(override: ((String, Map<String, String>) -> Result<GachaApiResponse>)?) {
        postRequestOverride = override
    }

    internal fun postRequest(
        endpoint: String,
        body: Map<String, String>,
    ): Result<GachaApiResponse> = postRequestOverride?.invoke(endpoint, body) ?: postRequestReal(endpoint, body)

    private fun postRequestReal(
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

    private fun computeSsrIntervals(
        records: List<GachaRecord>,
        standardFiveStars: Set<String> = emptySet(),
    ): List<SsrInterval> {
        val sorted = records.sortedBy { it.time }
        val ssrRecords = sorted.filter { it.qualityLevel == 5 }
        if (ssrRecords.size < 2) return emptyList()

        val intervals = mutableListOf<SsrInterval>()
        var cnt = 0
        for (rec in sorted) {
            cnt += rec.count.coerceAtLeast(1)
            if (rec.qualityLevel == 5) {
                intervals.add(
                    SsrInterval(
                        name = rec.name,
                        // Interval size = number of pulls since previous SSR
                        count = cnt,
                        time = rec.time,
                        // Pity = interval pull count (same semantics as reference SsrData.count)
                        pity = cnt,
                    ),
                )
                cnt = 0
            }
        }
        return intervals
    }

    private fun computeTotalCost(records: List<GachaRecord>): Long {
        // 1 pull = 160 Astrites/Lunites, 10-pull = 1600
        // Records may have count > 1 (collapsed 10-pulls)
        val totalPulls = records.sumOf { it.count.coerceAtLeast(1) }
        return (totalPulls.toLong() * 160)
    }

    private fun computeMinMaxPity(
        records: List<GachaRecord>,
        rarity: Int,
    ): Pair<Int, Int> {
        val sorted = records.sortedBy { it.time }
        val groups = mutableListOf<Int>()
        var cnt = 0
        for (rec in sorted) {
            cnt += rec.count.coerceAtLeast(1)
            // For 5-star intervals, reset only on 5-star hits.
            // For 4-star intervals, 5-star hits also reset the 4-star guarantee.
            if (rec.qualityLevel == rarity || (rarity == 4 && rec.qualityLevel == 5)) {
                groups.add(cnt)
                cnt = 0
            }
        }
        if (groups.isEmpty()) return 0 to 0
        return groups.minOrNull()!! to groups.maxOrNull()!!
    }

    internal fun estimatedSoftPityPulls(
        pullsSinceLastFive: Int,
        softPityStart: Int,
        hardPity: Int,
    ): Int {
        // WuWa's soft-pity rate ramps from ~15 % at pull `softPityStart`
        // up to 100 % at `hardPity` (hard guarantee). Linear approximation:
        //   p_effective(p) = 0.15 + (p - softPityStart) * 0.85 / (hardPity - softPityStart)
        // Expected additional pulls = 1 / p_effective, rounded up (ceil) so users
        // see a real number even in late soft-pity, clamped to [1, hardPity].
        if (pullsSinceLastFive >= hardPity) return 1
        val rate =
            SOFT_PITY_RATE_AT_THRESHOLD +
                (pullsSinceLastFive - softPityStart) *
                (1.0 - SOFT_PITY_RATE_AT_THRESHOLD) /
                (hardPity - softPityStart)
        val safeRate = rate.coerceAtLeast(SOFT_PITY_RATE_AT_THRESHOLD)
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

        // Average pity for this banner: average interval between all 5-star hits
        // (standard + featured), since every 5-star resets the pity cycle.
        val avgCharPity =
            if (fiveStarRecords.size >= 2) {
                val pityGroups = mutableListOf<Int>()
                var cnt = 0
                for (rec in sorted) {
                    cnt += rec.count.coerceAtLeast(1)
                    if (rec.qualityLevel == 5) {
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

        // Compute additional stats
        val ssrIntervals = computeSsrIntervals(sorted, standardFiveStars)
        val totalCost = computeTotalCost(sorted)
        val (minPity5, maxPity5) = computeMinMaxPity(sorted, 5)
        val (minPity4, maxPity4) = computeMinMaxPity(sorted, 4)
        val isPoolActive = records.isNotEmpty()

        // UP rate = featured / total 5-stars
        val upSsrCount = fiveStarRecords.count { !isStandardFive(it.name, standardFiveStars) }
        val upRateValue = if (fiveStarRecords.isNotEmpty()) upSsrCount.toDouble() / fiveStarRecords.size else 0.0

        // Non-banner rate (50/50 loss rate): traverse oldest -> newest, tracking guarantee.
        var isGuaranteedNext = false
        var totalFiftyFifty = 0
        var wonFiftyFifty = 0
        for (star in fiveStarRecords) {
            val isUp = !isStandardFive(star.name, standardFiveStars)
            if (isGuaranteedNext) {
                isGuaranteedNext = false
            } else {
                totalFiftyFifty++
                if (isUp) {
                    wonFiftyFifty++
                } else {
                    isGuaranteedNext = true
                }
            }
        }
        val nonBannerRateValue = if (totalFiftyFifty > 0) wonFiftyFifty.toDouble() / totalFiftyFifty else 0.0

        return PityPrediction(
            poolType = pool.type,
            poolLabel = pool.label,
            status = status,
            lastFiveStarName = lastFiveName,
            lastFiveStarTime = lastFiveTime,
            currentFeaturedName = currentCharacterName,
            currentFeaturedKnown = currentCharacterName.isNotEmpty(),
            pullsSinceLastFive = pullsSinceLastFive,
            estimatedNextFive = estimated,
            hardPity = HARD_PITY,
            softPityThreshold = SOFT_PITY_START,
            isInSoftPity = isInSoftPity,
            pullsUntilHardPity = pullsUntilHardPity,
            pullsSinceLastFourStar = pulls4,
            estimatedNextFourStar = maxOf(10 - pulls4, 1),
            avgPityThisPool = avgCharPity.toDouble(),
            nonBannerRate = nonBannerRateValue,
            upRate = upRateValue,
            firstPullDate = sorted.firstOrNull()?.time ?: "",
            lastPullDate = sorted.lastOrNull()?.time ?: "",
            ssrIntervals = ssrIntervals,
            totalCost = totalCost,
            minPity5 = minPity5,
            maxPity5 = maxPity5,
            minPity4 = minPity4,
            maxPity4 = maxPity4,
            isPoolActive = isPoolActive,
        )
    }

    internal fun calcWeaponPrediction(
        records: List<GachaRecord>,
        pool: GachaPool,
    ): PityPrediction {
        // Empirically, Wuthering Waves uses the same hard pity (80) and soft-pity
        // threshold (66) for *all* banner types — confirmed by the wuwatracker.com
        // dataset (394 125 samples). Weapon Event is "100% guaranteed featured": every
        // ★5 pulled is the rate-up weapon, unlike the character banner's 50/50.
        val HARD_PITY = 80
        val SOFT_PITY_START = 66
        val sorted = records.sortedBy { it.time }
        val fiveStarRecords = sorted.filter { it.qualityLevel == 5 }

        val pullsSinceLastFive: Int
        val lastFiveName: String
        val lastFiveTime: String
        val currentWeaponName: String

        if (fiveStarRecords.isNotEmpty()) {
            val lastFive = fiveStarRecords.last()
            lastFiveName = lastFive.name
            lastFiveTime = lastFive.time
            currentWeaponName = lastFive.name
            val lastFiveIndex = sorted.indexOfLast { it.qualityLevel == 5 }
            pullsSinceLastFive = sorted.drop(lastFiveIndex + 1).sumOf { it.count.coerceAtLeast(1) }
        } else {
            lastFiveName = ""
            lastFiveTime = ""
            currentWeaponName = ""
            pullsSinceLastFive = sorted.sumOf { it.count.coerceAtLeast(1) }
        }

        val isInSoftPity = pullsSinceLastFive >= SOFT_PITY_START
        val pullsUntilHardPity = maxOf(HARD_PITY - pullsSinceLastFive, 0)

        // Calculate average pity for this weapon pool (same logic as character)
        val avgWeaponPity =
            if (fiveStarRecords.size >= 2) {
                val pityGroups = mutableListOf<Int>()
                var cnt = 0
                for (rec in sorted) {
                    cnt += rec.count.coerceAtLeast(1)
                    if (rec.qualityLevel == 5) {
                        pityGroups.add(cnt)
                        cnt = 0
                    }
                }
                if (pityGroups.isNotEmpty()) pityGroups.average().toInt() else HARD_PITY
            } else {
                57 // Empirical mean from wuwatracker.com
            }

        val estimated =
            if (isInSoftPity) {
                estimatedSoftPityPulls(pullsSinceLastFive, SOFT_PITY_START, HARD_PITY)
            } else {
                maxOf(avgWeaponPity - pullsSinceLastFive, 1)
            }

        val pulls4 = calcPullsSinceLastFourStar(sorted)

        // Compute additional stats
        val ssrIntervals = computeSsrIntervals(sorted)
        val totalCost = computeTotalCost(sorted)
        val (minPity5, maxPity5) = computeMinMaxPity(sorted, 5)
        val (minPity4, maxPity4) = computeMinMaxPity(sorted, 4)
        val isPoolActive = records.isNotEmpty()

        return PityPrediction(
            poolType = pool.type,
            poolLabel = pool.label,
            status = "Guaranteed",
            lastFiveStarName = lastFiveName,
            lastFiveStarTime = lastFiveTime,
            currentFeaturedName = currentWeaponName,
            currentFeaturedKnown = currentWeaponName.isNotEmpty(),
            pullsSinceLastFive = pullsSinceLastFive,
            estimatedNextFive = estimated,
            hardPity = HARD_PITY,
            softPityThreshold = SOFT_PITY_START,
            isInSoftPity = isInSoftPity,
            pullsUntilHardPity = pullsUntilHardPity,
            pullsSinceLastFourStar = pulls4,
            estimatedNextFourStar = maxOf(10 - pulls4, 1),
            avgPityThisPool = avgWeaponPity.toDouble(),
            // Weapon banner has no non-banner 5★
            nonBannerRate = 0.0,
            // 100% UP on weapon banner
            upRate = 1.0,
            firstPullDate = sorted.firstOrNull()?.time ?: "",
            lastPullDate = sorted.lastOrNull()?.time ?: "",
            ssrIntervals = ssrIntervals,
            totalCost = totalCost,
            minPity5 = minPity5,
            maxPity5 = maxPity5,
            minPity4 = minPity4,
            maxPity4 = maxPity4,
            isPoolActive = isPoolActive,
        )
    }
}
