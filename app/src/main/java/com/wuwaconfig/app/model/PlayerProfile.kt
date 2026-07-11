package com.wuwaconfig.app.model

data class PlayerProfile(
    val uid: String? = null,
    val server: String? = null,
    val playerLevel: Int? = null,
    val gameVersion: String? = null,
    val patchVersion: String? = null,
    val launcherVersion: String? = null,
    val language: String? = null,
    val engineSettingCount: Int = 0,
    val deviceProfileCount: Int = 0,
    val gameUserSettingCount: Int = 0,
    val scalabilitySettingCount: Int = 0,
    val hardwareSettingCount: Int = 0,
    val lastLoginTime: String? = null,
    val towerFloor: Int? = null,
    val weeklyRogueScore: Int? = null,
    val battlePassPurchased: Boolean = false,
    val serverLevels: List<Pair<String, Int>> = emptyList(),
    val loopTowerSeason: Int? = null,
)
