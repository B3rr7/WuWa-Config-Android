package com.wuwaconfig.app.model

data class DeployRecord(
    val id: String,
    val timestamp: Long,
    val presetName: String,
    val generationMethod: String = "classic",
    val filesDeployed: List<String> = emptyList(),
    val cvarCount: Int = 0,
    val acceptedCount: Int = 0,
    val totalCount: Int = 0,
    val redundantCount: Int = 0,
    val unknownCount: Int = 0,
    val monitoredCount: Int = 0,
    val baselineFps: Float? = null,
    val baselineThermal: Int = 0,
    val baselineOom: Int = 0,
    val baselineDrops: Int = 0,
    val baselineClientLogSnippet: String = "",
    val outcomeFps: Float? = null,
    val outcomeThermal: Int? = null,
    val outcomeOom: Int? = null,
    val outcomeDrops: Int? = null,
    val outcomeTimestamp: Long? = null
) {
    val hasOutcome: Boolean get() = outcomeTimestamp != null

    fun comparison(): DeployComparison = DeployComparison(
        fpsDelta = if (baselineFps != null && outcomeFps != null) outcomeFps - baselineFps else null,
        thermalDelta = if (outcomeThermal != null) outcomeThermal - baselineThermal else null,
        oomDelta = if (outcomeOom != null) outcomeOom - baselineOom else null,
        dropFramesDelta = if (outcomeDrops != null) outcomeDrops - baselineDrops else null
    )
}

data class DeployComparison(
    val fpsDelta: Float?,
    val thermalDelta: Int?,
    val oomDelta: Int?,
    val dropFramesDelta: Int?
)
