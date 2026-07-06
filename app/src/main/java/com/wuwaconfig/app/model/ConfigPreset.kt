package com.wuwaconfig.app.model

data class ConfigFile(
    val name: String,
    val content: String,
    val targetPath: String = "",
)

data class ConfigBackup(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val files: List<ConfigFile>,
    val type: String = "manual",
)
