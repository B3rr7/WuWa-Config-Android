package com.wuwaconfig.app.config

object ForbiddenCvars {
    val ALL: Set<String> =
        setOf(
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

    private val commonVariants =
        run {
            val variants = mutableSetOf<String>()
            for (key in ALL) {
                val lower = key.lowercase()
                variants.add(lower)
                variants.add("+" + lower)
                variants.add("-" + lower)
            }
            variants.toSet()
        }

    fun isForbidden(cvarKey: String): Boolean {
        return commonVariants.contains(cvarKey.trim().lowercase())
    }

    fun stripForbiddenCvars(iniContent: String): String = stripForbiddenCvarsWithReport(iniContent).first

    /**
     * Strips every forbidden CVar line from [iniContent] and reports which keys were
     * removed. The single implementation behind [stripForbiddenCvars] so the two can
     * never disagree.
     */
    fun stripForbiddenCvarsWithReport(iniContent: String): Pair<String, List<String>> {
        val kept = mutableListOf<String>()
        val removed = mutableListOf<String>()
        for (line in iniContent.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith(";") || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.isEmpty()) {
                kept.add(line)
                continue
            }
            // Strip the leading +CVars= / -CVars= directive BEFORE splitting on '=',
            // otherwise the delimiter inside the directive is parsed as the key.
            val body = trimmed.removePrefix("+CVars=").removePrefix("-CVars=").trim()
            val eqIdx = body.indexOf('=')
            val keyPart = if (eqIdx >= 0) body.substring(0, eqIdx).trim() else body.trim()
            if (isForbidden(keyPart)) {
                removed.add(keyPart)
            } else {
                kept.add(line)
            }
        }
        // Re-join WITHOUT a trailing newline. String.lines() on a newline-terminated
        // input already yields a trailing "" element, and kept carries that element
        // through, so joinToString("\n") reproduces the input's structure exactly:
        // the output has precisely (input lines - removed lines) lines.
        //
        // The previous implementation used appendLine() per retained line, which added
        // one phantom trailing newline per invocation — across the 5 generated INIs that
        // inflated the count by 5 and made the caller's "stripped N" arithmetic go
        // NEGATIVE, so the log line reporting the removals never fired. An intermediate
        // fix used trimEnd('\n'), which restored the count but also ate legitimate
        // trailing BLANK lines. Re-joining the filtered line list has neither problem
        // and does not need to reason about the input's termination at all.
        return kept.joinToString("\n") to removed
    }
}
