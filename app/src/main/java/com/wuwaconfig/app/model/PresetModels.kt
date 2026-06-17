package com.wuwaconfig.app.model

data class PresetsFile(
    val android: Map<String, PresetTier> = emptyMap(),
    val pc: Map<String, PresetTier> = emptyMap()
)

data class PresetTier(
    val settings: Map<String, Any> = emptyMap(),
    val deviceprofiles: Map<String, Any> = emptyMap(),
    val gameusersettings: Map<String, Any> = emptyMap()
)

enum class GameMode(val label: String) {
    Overworld("Overworld"),
    Domain("Domain / Tower")
}

data class CvarEntry(
    val key: String,
    val value: String,
    val category: String = "",
    val isOverridden: Boolean = false,
    val originalValue: String = value
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
    val cvarOverrides: Map<String, String> = emptyMap()
)

data class GeneratedIni(
    val engine: String = "",
    val deviceProfiles: String = "",
    val gameUserSettings: String = ""
)

data class ModeConfig(
    val preset: String = "balanced",
    val options: GeneratorOptions = GeneratorOptions()
)
