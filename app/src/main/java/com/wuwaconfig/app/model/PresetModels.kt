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
    val profile: String = "auto"
)

data class GeneratedIni(
    val engine: String = "",
    val deviceProfiles: String = "",
    val gameUserSettings: String = ""
)
