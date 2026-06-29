package com.wuwaconfig.app.model

data class CvarDetail(
    val isKnown: Boolean,
    val isMonitored: Boolean,
    val gameDefault: String?,
    val matchesDefault: Boolean
)

data class VerificationReport(
    val accepted: Set<String>,
    val rejected: Set<String>,
    val recognizedCount: Int,
    val totalCount: Int,
    val cvarDetails: Map<String, CvarDetail> = emptyMap()
) {
    val acceptedRatio: Float get() = if (totalCount > 0) recognizedCount.toFloat() / totalCount else 0f
    val redundantCount: Int get() = cvarDetails.values.count { it.matchesDefault }
    val unknownCount: Int get() = cvarDetails.values.count { !it.isKnown }
    val monitoredCount: Int get() = cvarDetails.values.count { it.isMonitored }
}
