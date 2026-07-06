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
    private val STANDARD_CHARACTERS =
        setOf(
            "Calcharo",
            "Verina",
            "Lingyang",
            "Jianxin",
            "Encore",
        )

    private val STANDARD_WEAPONS =
        setOf(
            "Static Mist",
            "Everbright Polestar",
            "Pulsation Bracer",
            "Boson Astrolabe",
            "Emerald of Genesis",
            "Abyssal Surge",
        )

    private val CHARACTER_POOLS = setOf("1", "7", "10")

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
                if (result.isFailure) continue
                val response = result.getOrThrow()

                if (response.code == 0 && !response.data.isNullOrEmpty()) {
                    records.addAll(response.data)
                    poolsWithData.add(pool.type)
                }
            }

            val totalPulls = records.size
            val fiveStars = records.count { it.qualityLevel == 5 }
            val fourStars = records.count { it.qualityLevel == 4 }

            val pity5 = calculateAvgPity(records, 5)
            val pity4 = calculateAvgPity(records, 4)

            val predictions = mutableListOf<PityPrediction>()
            for (pool in GachaPool.ALL) {
                val poolRecords = records.filter { it.cardPoolType == pool.type }
                if (poolRecords.isEmpty()) continue

                val isCharacterBanner = pool.type in CHARACTER_POOLS
                val pred =
                    if (isCharacterBanner) {
                        calcCharacterPrediction(poolRecords, pool)
                    } else if (pool.type == "2") {
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
        return try {
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val jsonBody = gson.toJson(body)
            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

            val responseCode = conn.responseCode
            if (responseCode != 200) {
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
                            resourceId = (item["resourceId"] as? Number)?.toLong() ?: 0L,
                            qualityLevel = (item["qualityLevel"] as? Number)?.toInt() ?: 0,
                            resourceType = item["resourceType"] as? String ?: "",
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
        }
    }

    private fun calculateAvgPity(
        records: List<GachaRecord>,
        rarity: Int,
    ): Double {
        val rarities = records.filter { it.qualityLevel == rarity }
        if (rarities.isEmpty()) return 0.0

        val sorted = records.sortedBy { it.time }
        val groups = mutableListOf<Int>()
        var count = 0
        for (rec in sorted) {
            count++
            if (rec.qualityLevel == rarity) {
                groups.add(count)
                count = 0
            }
        }
        if (groups.isEmpty()) return 0.0
        return groups.average()
    }

    private fun isStandardFive(
        name: String,
        resourceType: String,
    ): Boolean {
        return if (resourceType == "Resonator") {
            name in STANDARD_CHARACTERS
        } else {
            name in STANDARD_WEAPONS
        }
    }

    private fun calcPullsSinceLastFourStar(records: List<GachaRecord>): Int {
        val sorted = records.sortedBy { it.time }
        val lastFourIndex = sorted.indexOfLast { it.qualityLevel == 4 || it.qualityLevel == 5 }
        if (lastFourIndex < 0) return sorted.size.coerceAtMost(10)
        return sorted.size - lastFourIndex - 1
    }

    private fun calcCharacterPrediction(
        records: List<GachaRecord>,
        pool: GachaPool,
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
            isLastFiveStandard = isStandardFive(lastFiveName, lastFive.resourceType)
            val lastFiveIndex = sorted.indexOfLast { it.qualityLevel == 5 }
            pullsSinceLastFive = sorted.size - lastFiveIndex - 1
        } else {
            lastFiveName = ""
            lastFiveTime = ""
            isLastFiveStandard = false
            pullsSinceLastFive = sorted.size
        }

        val status =
            if (fiveStarRecords.isEmpty()) {
                "Unknown"
            } else if (isLastFiveStandard) {
                "Guaranteed"
            } else {
                "50/50"
            }

        val nearbyFives =
            fiveStarRecords.filter {
                it.name !in STANDARD_CHARACTERS && it.name !in STANDARD_WEAPONS
            }
        val avgCharPity =
            if (nearbyFives.size >= 2) {
                val pityGroups = mutableListOf<Int>()
                var cnt = 0
                for (rec in sorted) {
                    cnt++
                    if (rec.qualityLevel == 5 && rec in nearbyFives) {
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
                maxOf(SOFT_PITY_START - pullsSinceLastFive + 4, 1) + 6
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

    private fun calcWeaponPrediction(
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
            pullsSinceLastFive = sorted.size - lastFiveIndex - 1
        } else {
            lastFiveName = ""
            lastFiveTime = ""
            pullsSinceLastFive = sorted.size
        }

        val isInSoftPity = pullsSinceLastFive >= SOFT_PITY_START
        val pullsUntilHardPity = maxOf(HARD_PITY - pullsSinceLastFive, 0)
        val estimated =
            if (isInSoftPity) {
                maxOf(SOFT_PITY_START - pullsSinceLastFive + 4, 1) + 4
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
