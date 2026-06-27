package com.wuwaconfig.app.model

data class VerificationReport(
    val accepted: Set<String>,
    val rejected: Set<String>,
    val recognizedCount: Int,
    val totalCount: Int
) {
    val acceptedRatio: Float get() = if (totalCount > 0) recognizedCount.toFloat() / totalCount else 0f
}
