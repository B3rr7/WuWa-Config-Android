package com.wuwaconfig.app.config

import com.wuwaconfig.app.model.CvarCategory

object CvarCategorizer {
    private data class CatRule(val prefix: String, val category: CvarCategory)

    private data class CatOverride(val cvar: String, val category: CvarCategory)

    private val topLevelRules =
        listOf(
            CatRule("a.", CvarCategory.ANIMATION),
            CatRule("fx.", CvarCategory.EFFECTS),
            CatRule("niagara.", CvarCategory.EFFECTS),
            CatRule("foliage.", CvarCategory.ENVIRONMENT),
            CatRule("grass.", CvarCategory.ENVIRONMENT),
            CatRule("gc.", CvarCategory.MEMORY),
            CatRule("s.", CvarCategory.TEXTURE_STREAMING),
            CatRule("sg.", CvarCategory.SCALABILITY),
            CatRule("wp.", CvarCategory.WORLD),
            CatRule("lod.", CvarCategory.LOD_CULLING),
            CatRule("n.", CvarCategory.WORLD),
            CatRule("t.", CvarCategory.SYSTEM),
            CatRule("kuro.", CvarCategory.SYSTEM),
        )

    private val rSubRules =
        listOf(
            CatRule("r.shadow.", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.lightfunction", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.lightshaft", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.ambientocclusion", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.csmupdate", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.distancefield", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.capsuleshadows", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.contactshadows", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.ssgi", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.kuro.globallightquality", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.kuro.globallightshadowquality", CvarCategory.LIGHTING_SHADOW),
            CatRule("r.reflectionenvironment", CvarCategory.ENVIRONMENT),
            CatRule("r.fog", CvarCategory.ENVIRONMENT),
            CatRule("r.volumetricfog", CvarCategory.ENVIRONMENT),
            CatRule("r.kurovolumec", CvarCategory.ENVIRONMENT),
            CatRule("r.landscape", CvarCategory.ENVIRONMENT),
            CatRule("r.kuro.foliage", CvarCategory.ENVIRONMENT),
            CatRule("r.kuro.landscapecapture", CvarCategory.ENVIRONMENT),
            CatRule("r.fogvisibilityculling", CvarCategory.ENVIRONMENT),
            CatRule("r.ssr", CvarCategory.REFLECTION),
            CatRule("r.mobile.ssr", CvarCategory.REFLECTION),
            CatRule("r.mobile.waterssr", CvarCategory.REFLECTION),
            CatRule("r.mobile.sceneobjmobilessr", CvarCategory.REFLECTION),
            CatRule("r.mobile.pixelprojectedreflectionquality", CvarCategory.REFLECTION),
            CatRule("r.kuro.enableplanarreflection", CvarCategory.REFLECTION),
            CatRule("r.bloomquality", CvarCategory.POST_PROCESS),
            CatRule("r.kurobloom", CvarCategory.POST_PROCESS),
            CatRule("r.kuro.kurobloom", CvarCategory.POST_PROCESS),
            CatRule("r.kuro.kuroenablefftbloom", CvarCategory.POST_PROCESS),
            CatRule("r.kuro.kuroenabletoonfftbloom", CvarCategory.POST_PROCESS),
            CatRule("r.kuro.kurobloomstreak", CvarCategory.POST_PROCESS),
            CatRule("r.tonemapper", CvarCategory.POST_PROCESS),
            CatRule("r.kurotonemapping", CvarCategory.POST_PROCESS),
            CatRule("r.temporalaa", CvarCategory.POST_PROCESS),
            CatRule("r.postprocessaaquality", CvarCategory.POST_PROCESS),
            CatRule("r.defaultfeature.antialiasing", CvarCategory.POST_PROCESS),
            CatRule("r.upscale", CvarCategory.POST_PROCESS),
            CatRule("r.eyeadaptationquality", CvarCategory.POST_PROCESS),
            CatRule("r.motionblurquality", CvarCategory.POST_PROCESS),
            CatRule("r.depthoffieldquality", CvarCategory.POST_PROCESS),
            CatRule("r.lensflarequality", CvarCategory.POST_PROCESS),
            CatRule("r.scenecolorfringequal", CvarCategory.POST_PROCESS),
            CatRule("r.tonemapper.grainquantization", CvarCategory.POST_PROCESS),
            CatRule("r.disabledistortion", CvarCategory.POST_PROCESS),
            CatRule("r.defaultfeature.lensflare", CvarCategory.POST_PROCESS),
            CatRule("r.kuro.autexposure", CvarCategory.POST_PROCESS),
            CatRule("r.kuro.radialblur", CvarCategory.POST_PROCESS),
            CatRule("r.kuro.lguiblurtexture", CvarCategory.POST_PROCESS),
            CatRule("r.fidelityfx.fsr.rcas", CvarCategory.POST_PROCESS),
            CatRule("r.temporalaa.sharpness", CvarCategory.POST_PROCESS),
            CatRule("r.streaming.", CvarCategory.TEXTURE_STREAMING),
            CatRule("r.texturestreaming", CvarCategory.TEXTURE_STREAMING),
            CatRule("r.maxanisotropy", CvarCategory.TEXTURE_STREAMING),
            CatRule("r.rendertargetpoolmin", CvarCategory.TEXTURE_STREAMING),
            CatRule("r.anisotropicmaterials", CvarCategory.TEXTURE_STREAMING),
            CatRule("r.kuro.toonoutline", CvarCategory.CHARACTER),
            CatRule("r.kuro.tooneye", CvarCategory.CHARACTER),
            CatRule("r.kuro.toonface", CvarCategory.CHARACTER),
            CatRule("r.kuro.skeletalm", CvarCategory.CHARACTER),
            CatRule("r.kuro.npcdisapp", CvarCategory.CHARACTER),
            CatRule("r.mobile.outlinescale", CvarCategory.CHARACTER),
            CatRule("r.mobile.treerimlight", CvarCategory.CHARACTER),
            CatRule("r.subsurfacescattering", CvarCategory.CHARACTER),
            CatRule("r.sss.", CvarCategory.CHARACTER),
            CatRule("r.skincache", CvarCategory.CHARACTER),
            CatRule("r.morphtarget", CvarCategory.CHARACTER),
            CatRule("r.bbm.", CvarCategory.CHARACTER),
            CatRule("r.clearcatnormal", CvarCategory.CHARACTER),
            CatRule("r.basepassoutputsvelocity", CvarCategory.CHARACTER),
            CatRule("r.mobile.", CvarCategory.MOBILE),
            CatRule("r.mobilehdr", CvarCategory.MOBILE),
            CatRule("r.mobilemsaa", CvarCategory.MOBILE),
            CatRule("r.android.", CvarCategory.MOBILE),
            CatRule("r.allowhdr", CvarCategory.MOBILE),
            CatRule("r.vulkan.", CvarCategory.PIPELINE_RHI),
            CatRule("r.rhicmd", CvarCategory.PIPELINE_RHI),
            CatRule("r.pso.", CvarCategory.PIPELINE_RHI),
            CatRule("r.cachecomputepso", CvarCategory.PIPELINE_RHI),
            CatRule("r.asynccomputepso", CvarCategory.PIPELINE_RHI),
            CatRule("r.shaderpipelinecache", CvarCategory.PIPELINE_RHI),
            CatRule("r.hzbocclusion", CvarCategory.LOD_CULLING),
            CatRule("r.culldistancevolume", CvarCategory.LOD_CULLING),
            CatRule("r.minscreenradiuspercentage", CvarCategory.LOD_CULLING),
            CatRule("r.maxscreenradiuspercentage", CvarCategory.LOD_CULLING),
            CatRule("r.staticmeshloddistancescale", CvarCategory.LOD_CULLING),
            CatRule("r.screensizecullratiofactor", CvarCategory.LOD_CULLING),
            CatRule("r.kuromaxfovforlod", CvarCategory.LOD_CULLING),
            CatRule("r.allowprecomputedvisibility", CvarCategory.LOD_CULLING),
            CatRule("r.allowhardwareocclusion", CvarCategory.LOD_CULLING),
            CatRule("r.allowsoftwareocclusion", CvarCategory.LOD_CULLING),
            CatRule("r.allowocclusionqueries", CvarCategory.LOD_CULLING),
            CatRule("r.kuro.mobileism", CvarCategory.LOD_CULLING),
            CatRule("r.mdcfallback.", CvarCategory.LOD_CULLING),
            CatRule("r.mobile.enablevoidgt", CvarCategory.PERFORMANCE),
            CatRule("r.enablemeshpassprocessorscache", CvarCategory.PERFORMANCE),
            CatRule("r.finishcurrentframe", CvarCategory.PERFORMANCE),
            CatRule("r.framepace", CvarCategory.PERFORMANCE),
            CatRule("r.vsync", CvarCategory.PERFORMANCE),
            CatRule("r.useclustereddeferredshading", CvarCategory.PERFORMANCE),
            CatRule("r.parallelfrustumcull", CvarCategory.PERFORMANCE),
            CatRule("r.enablegetdynelm", CvarCategory.PERFORMANCE),
            CatRule("r.kurofi", CvarCategory.PERFORMANCE),
            CatRule("r.vrs.", CvarCategory.PERFORMANCE),
            CatRule("r.kuro.autocool", CvarCategory.THERMAL),
            CatRule("r.kuro.thermalcontrolmode", CvarCategory.THERMAL),
            CatRule("r.dontlimitonbattery", CvarCategory.THERMAL),
            CatRule("r.emitterspawnratescale", CvarCategory.EFFECTS),
            CatRule("r.allowstaticlighting", CvarCategory.SYSTEM),
            CatRule("r.allowglobalclipplane", CvarCategory.SYSTEM),
        )

    private val overrides =
        listOf(
            CatOverride("compat.usedxt5normalmaps", CvarCategory.TEXTURE_STREAMING),
            CatOverride("enablenavpartition", CvarCategory.WORLD),
            CatOverride("enablesceneinfosframing", CvarCategory.SYSTEM),
            CatOverride("fx.allowgpubarticles", CvarCategory.EFFECTS),
            CatOverride("hismbatcher.enabled", CvarCategory.LOD_CULLING),
            CatOverride("kuro.collision.useseparatedbody", CvarCategory.SYSTEM),
            CatOverride("kuro.compactmatrix.enablecompactmatrixincook", CvarCategory.SYSTEM),
            CatOverride("kuro.interactiveleavestickasync", CvarCategory.ENVIRONMENT),
            CatOverride("kuro.script.enablecsharpenv", CvarCategory.SYSTEM),
            CatOverride("kuro.sharphereal.jsbridgestringispassbypointer", CvarCategory.SYSTEM),
            CatOverride("magt.enablemagt", CvarCategory.PERFORMANCE),
            CatOverride("niagara.gpuculling", CvarCategory.EFFECTS),
            CatOverride("niagara.gpudrawindirectargsbufferslack", CvarCategory.EFFECTS),
            CatOverride("niagara.gpusorting.usemaxprecision", CvarCategory.EFFECTS),
            CatOverride("sequencer.enableoldlgui bindings", CvarCategory.SYSTEM),
            CatOverride("ubinstancing.enabled", CvarCategory.LOD_CULLING),
            CatOverride("ubinstancing.enableddepth", CvarCategory.LOD_CULLING),
            CatOverride("ubinstancing.enabledshaders", CvarCategory.LOD_CULLING),
            CatOverride("ubinstancing.enabledshadow", CvarCategory.LOD_CULLING),
            CatOverride("a.skinmeshsupportsrenderstatic", CvarCategory.CHARACTER),
            CatOverride("a.useswappyforframepacing", CvarCategory.PERFORMANCE),
            CatOverride("cook.skiplandscapematerialupdatecontext", CvarCategory.SYSTEM),
            CatOverride("foliage.densityscale", CvarCategory.ENVIRONMENT),
            CatOverride("foliage.lodoptimize", CvarCategory.ENVIRONMENT),
            CatOverride("grass.cull", CvarCategory.ENVIRONMENT),
            CatOverride("grass.densityscale", CvarCategory.ENVIRONMENT),
            CatOverride("kuro.lowmemlevelmask", CvarCategory.MEMORY),
            CatOverride("lod.temporallag", CvarCategory.LOD_CULLING),
            CatOverride("m.lowmemorydevicethreshold", CvarCategory.MEMORY),
        )

    private val mobileSubExceptions =
        setOf(
            "r.mobile.ssr", "r.mobile.waterssr", "r.mobile.sceneobjmobilessr",
            "r.mobile.pixelprojectedreflectionquality",
            "r.mobile.hbao", "r.mobile.hbao.blurtype", "r.mobile.hbao.largeaofactor", "r.mobile.hbao.smallaofactor",
            "r.mobile.numdynamicpointlights", "r.mobile.enablemovablespotlights",
            "r.mobile.enablemovablespotlightsshadow", "r.mobile.enablekurospotlightsshadow",
            "r.mobile.enablemovablelightcsmshaderculling", "r.mobile.enablestaticandcsmshadowreceivers",
            "r.mobile.disablelocallightspeculardistance", "r.mobile.allowmovabledirectionallights",
            "r.mobile.enablevoidgt", "r.mobile.ssao", "r.mobile.outlinescale", "r.mobile.treerimlight",
        )

    fun categorize(key: String): CvarCategory {
        val k = key.lowercase().trim()
        if (k.isBlank()) return CvarCategory.UNKNOWN

        val override = overrides.firstOrNull { k == it.cvar }
        if (override != null) return override.category

        if (k.startsWith("r.") && k.length > 2) {
            val subKey = k.substring(2)
            if (!subKey.startsWith("mobile.")) {
                val subRule = rSubRules.firstOrNull { k.startsWith(it.prefix) }
                if (subRule != null) return subRule.category
            } else {
                val exception = mobileSubExceptions.firstOrNull { k.startsWith(it) }
                if (exception == null) {
                    val subRule = rSubRules.firstOrNull { k.startsWith(it.prefix) }
                    if (subRule != null) return subRule.category
                }
                val lightShadowPrefixes =
                    listOf(
                        "ssr", "waterssr", "sceneobjmobilessr", "pixelprojectedreflectionquality",
                        "hbao", "numdynamicpointlights", "enablemovable", "enablekurospotlightsshadow",
                        "enablestaticandcsm", "disablelocallight", "allowmovabledirectional", "ssao",
                    )
                        .map { "r.mobile.$it" }
                if (lightShadowPrefixes.any { k.startsWith(it) }) return CvarCategory.LIGHTING_SHADOW
                if (k.startsWith("r.mobile.outlinescale") || k.startsWith("r.mobile.treerimlight")) return CvarCategory.CHARACTER
                return CvarCategory.MOBILE
            }

            val topRule = rSubRules.firstOrNull { k.startsWith(it.prefix) }
            if (topRule != null) return topRule.category

            val knownCategories =
                mapOf(
                    "r.shadow" to CvarCategory.LIGHTING_SHADOW,
                    "r.mobile" to CvarCategory.MOBILE,
                    "r.streaming" to CvarCategory.TEXTURE_STREAMING,
                    "r.bloom" to CvarCategory.POST_PROCESS,
                    "r.tonemapper" to CvarCategory.POST_PROCESS,
                    "r.temporal" to CvarCategory.POST_PROCESS,
                    "r.postprocess" to CvarCategory.POST_PROCESS,
                    "r.defaultfeature" to CvarCategory.POST_PROCESS,
                    "r.upscale" to CvarCategory.POST_PROCESS,
                    "r.eyeadaptation" to CvarCategory.POST_PROCESS,
                    "r.motionblur" to CvarCategory.POST_PROCESS,
                    "r.depthoffield" to CvarCategory.POST_PROCESS,
                    "r.lensflare" to CvarCategory.POST_PROCESS,
                    "r.scenecolorfringe" to CvarCategory.POST_PROCESS,
                    "r.distortion" to CvarCategory.POST_PROCESS,
                    "r.lightfunction" to CvarCategory.LIGHTING_SHADOW,
                    "r.lightshaft" to CvarCategory.LIGHTING_SHADOW,
                    "r.ambientocclusion" to CvarCategory.LIGHTING_SHADOW,
                    "r.ssgi" to CvarCategory.LIGHTING_SHADOW,
                    "r.ssr" to CvarCategory.REFLECTION,
                    "r.volumetricfog" to CvarCategory.ENVIRONMENT,
                    "r.fog" to CvarCategory.ENVIRONMENT,
                    "r.landscape" to CvarCategory.ENVIRONMENT,
                    "r.foliage" to CvarCategory.ENVIRONMENT,
                    "r.distancefield" to CvarCategory.LIGHTING_SHADOW,
                    "r.skincache" to CvarCategory.CHARACTER,
                    "r.morphtarget" to CvarCategory.CHARACTER,
                    "r.bbm" to CvarCategory.CHARACTER,
                    "r.vulkan" to CvarCategory.PIPELINE_RHI,
                    "r.rhicmd" to CvarCategory.PIPELINE_RHI,
                    "r.pso" to CvarCategory.PIPELINE_RHI,
                    "r.shaderpipelinecache" to CvarCategory.PIPELINE_RHI,
                    "r.kuro" to CvarCategory.SYSTEM,
                    "r.hzb" to CvarCategory.LOD_CULLING,
                    "r.cull" to CvarCategory.LOD_CULLING,
                    "r.screensize" to CvarCategory.LOD_CULLING,
                    "r.screenradius" to CvarCategory.LOD_CULLING,
                    "r.staticmesh" to CvarCategory.LOD_CULLING,
                    "r.allowprecomputed" to CvarCategory.LOD_CULLING,
                    "r.allows oftwareocclusion" to CvarCategory.LOD_CULLING,
                    "r.vrs" to CvarCategory.PERFORMANCE,
                    "r.framepace" to CvarCategory.PERFORMANCE,
                    "r.vsync" to CvarCategory.PERFORMANCE,
                    "r.finishcurrent" to CvarCategory.PERFORMANCE,
                    "r.texture" to CvarCategory.TEXTURE_STREAMING,
                    "r.maxanisotropy" to CvarCategory.TEXTURE_STREAMING,
                    "r.rendertarget" to CvarCategory.TEXTURE_STREAMING,
                    "r.android" to CvarCategory.MOBILE,
                    "r.subsurface" to CvarCategory.CHARACTER,
                    "r.sss" to CvarCategory.CHARACTER,
                    "r.emitter" to CvarCategory.EFFECTS,
                    "r.kurofi" to CvarCategory.PERFORMANCE,
                    "r.mobilehdr" to CvarCategory.MOBILE,
                    "r.mobilemsaa" to CvarCategory.MOBILE,
                )
            val match = knownCategories.entries.firstOrNull { (prefix, _) -> k.startsWith(prefix) }
            if (match != null) return match.value
            return CvarCategory.UNKNOWN
        }

        if (k.startsWith("compat.")) return CvarCategory.SYSTEM
        if (k.startsWith("cook.")) return CvarCategory.SYSTEM

        val topRule = topLevelRules.firstOrNull { k.startsWith(it.prefix) }
        return topRule?.category ?: CvarCategory.UNKNOWN
    }
}
