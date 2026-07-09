package com.wuwaconfig.app.model

enum class GameMode(val label: String) {
    Overworld("Overworld"),
    Domain("Domain / Tower"),
}

data class CvarEntry(
    val key: String,
    val value: String,
    val category: String = "",
    val isOverridden: Boolean = false,
    val originalValue: String = value,
)

data class GeneratorOptions(
    val fps: Int = 60,
    val unlock120: Boolean = false,
    val unlockUltra: Boolean = true,
    val vsync: Boolean = true,
    val cool: Boolean = true,
    val vulkan: Boolean = false,
    val hzb: Boolean = false,
    val fog: Boolean = false,
    val ca: Boolean = true,
    val net: Boolean = true,
    val profile: String = "auto",
    val disableOutline: Boolean = false,
    val disableRadialBlur: Boolean = false,
    val disableBloom: Boolean = false,
    val disableAutoExposure: Boolean = false,
    val disableSSR: Boolean = false,
    val texOverride: Int = -1,
    val shadowOverride: Int = -1,
    val mode: GameMode = GameMode.Overworld,
    val cvarOverrides: Map<String, String> = emptyMap(),
    val generateEngine: Boolean = true,
    val generateDeviceProfiles: Boolean = true,
    val generateGameUserSettings: Boolean = true,
    val generateScalability: Boolean = false,
    val generateHardware: Boolean = false,
    val allowRestrictedCvars: Boolean = true,
    val optimizeWithCvarDb: Boolean = true,
    val importFromLog: Boolean = false,
    val useAdvancedGen: Boolean = false,
)

data class GeneratedIni(
    val engine: String = "",
    val deviceProfiles: String = "",
    val gameUserSettings: String = "",
    val scalability: String = "",
    val hardware: String = "",
)
