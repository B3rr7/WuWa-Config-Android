package com.wuwaconfig.app.config

object ForbiddenCvars {
    val ALL: Set<String> = setOf(
        "r.Kuro.SkeletalMesh.LODDistanceScale",
        "r.Streaming.Boost",
        "r.Streaming.PoolSize",
        "r.Streaming.LimitPoolSizeTOVRAM",
        "r.Shadow.MaxCSMResolution",
        "r.Streaming.MinBoost",
        "r.MipMapLODBias",
        "r.TextureGroup.Landscape.TextureLODBias",
        "r.Kuro.TexturePool.ExtraBudgetMB",
        "r.Streaming.CPUReadback",
        "r.Streaming.UseAsyncCPUReadback",
        "r.Streaming.MaxNumTexturesToStreamPerFrame",
        "r.Streaming.MinMipForSplitRequest",
        "r.Streaming.UseFixedPoolsize",
        "r.Streaming.UseAllMips",
        "r.Streaming.MaxTempMemoryAllowed",
        "r.RayTracing.LimitDevice",
        "r.DetailMode",
        "r.MaterialQualityLevel",
        "r.KuroMaterialQualityLevel",
        "r.ViewDistanceScale",
        "Kuro.CppEffectsSystem.UseLowMemoryPlayerEffectLruCapacity",
        "Kuro.CppEffectSystem.UseLowMemoryPlayerEffectLruCapacity",
        "r.AsyncComputePSO",
        "r.Streamline.DLSSG.RetainResourcesWhenOff",
        "r.MobileContentScaleFactor",
        "r.SecondaryScreenPercentage.GameViewport",
        "r.ScreenPercentage",
        "r.AFME.Enable",
        "r.MFRC.Enable",
        "r.FEstimation.Option",
    )

    private val forbiddenLower = ALL.map { it.lowercase() }.toSet()

    private val commonVariants = run {
        val variants = mutableSetOf<String>()
        for (key in ALL) {
            val lower = key.lowercase()
            variants.add(lower)
            variants.add("+" + lower)
            variants.add("-" + lower)
            if (!lower.startsWith("r.") && !lower.startsWith("kuro.")) {
                variants.add("r." + lower)
            }
        }
        variants.toSet()
    }

    fun isForbidden(cvarKey: String): Boolean {
        val key = cvarKey.trim().lowercase()
        if (commonVariants.contains(key)) return true
        if (forbiddenLower.any { key == it || key == "+$it" || key == "-$it" }) return true
        val base = key.removePrefix("+").removePrefix("-")
        if (forbiddenLower.contains(base)) return true
        return false
    }

    fun stripForbiddenCvars(iniContent: String): String {
        val sb = StringBuilder()
        for (line in iniContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.isEmpty()) {
                sb.appendLine(line)
                continue
            }
            val eqIdx = trimmed.indexOf('=')
            val keyPart = if (eqIdx >= 0) trimmed.substring(0, eqIdx).trim() else trimmed.trim()
            if (!isForbidden(keyPart)) {
                sb.appendLine(line)
            }
        }
        return sb.toString()
    }
}
