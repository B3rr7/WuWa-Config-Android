package com.wuwaconfig.app.model

enum class CvarCategory(val displayName: String) {
    CHARACTER("Character"),
    ENVIRONMENT("Environment"),
    LIGHTING_SHADOW("Lighting & Shadow"),
    POST_PROCESS("Post Processing"),
    REFLECTION("Reflections"),
    EFFECTS("Effects & Particles"),
    TEXTURE_STREAMING("Texture Streaming"),
    LOD_CULLING("LOD & Culling"),
    ANIMATION("Animation"),
    MOBILE("Mobile"),
    PIPELINE_RHI("Pipeline & RHI"),
    PERFORMANCE("Performance"),
    THERMAL("Thermal & Stability"),
    MEMORY("Memory & GC"),
    WORLD("World & Navigation"),
    SCALABILITY("Scalability"),
    SYSTEM("Engine System"),
    UNKNOWN("Other")
}

data class CvarDetail(
    val isKnown: Boolean,
    val isMonitored: Boolean,
    val gameDefault: String?,
    val matchesDefault: Boolean,
    val category: CvarCategory = CvarCategory.UNKNOWN
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
