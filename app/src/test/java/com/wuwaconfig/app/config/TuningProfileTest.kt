package com.wuwaconfig.app.config

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Asset↔default parity for the tuning data.
 *
 * `TuningProfile` exists so a retune ships as an asset edit rather than a
 * recompile. That only holds if the asset and the compiled-in defaults carry the
 * same values — they are duplicated by design, so editing the asset without
 * editing the default would make behaviour depend on whether the asset loaded.
 *
 * This reads the real asset off disk and compares it field-by-field against
 * [TuningProfile.DEFAULT_PRESETS], the same way [GameProfileTest] does for the
 * properties file. It is the test that makes the duplication safe.
 */
class TuningProfileTest {
    private val gson = Gson()

    private fun realAsset(): Map<String, Any?> {
        val file = realAssetFile()
        assertTrue("tuning.json not found at ${file.absolutePath}", file.isFile)
        return JsonParser.parseReader(file.inputStream().bufferedReader()).asJsonObject
            .getAsJsonObject("presets")
            .entrySet()
            .associate { it.key to gson.fromJson(it.value, Map::class.java) }
    }

    @Test
    fun `the asset exists and is not empty`() {
        val text = realAssetFile().readText()
        assertTrue("tuning.json is empty", text.isNotBlank())
        assertTrue("no presets parsed", text.contains("\"presets\""))
    }

    @Test
    fun `the asset carries every preset the defaults define`() {
        val asset = realAsset()
        assertEquals(
            "the asset and the defaults must name the same presets",
            TuningProfile.DEFAULT_PRESETS.keys.toList(),
            asset.keys.toList(),
        )
    }

    @Test
    fun `every preset matches the compiled-in default field for field`() {
        val asset = realAsset()
        for ((name, default) in TuningProfile.DEFAULT_PRESETS) {
            val assetFields = asset.getValue(name) as Map<*, *>
            val expected =
                mapOf(
                    "screen" to default.screen,
                    "shadow" to default.shadow,
                    "shadowRes" to default.shadowRes,
                    "ssr" to default.ssr,
                    "mipbias" to default.mipbias,
                    "streaming" to default.streaming,
                    "vd" to default.vd,
                    "flod" to default.flod,
                    "detail" to default.detail,
                    "lod_bias" to default.lod_bias,
                    "grasscull" to default.grasscull,
                    "characterDetail" to default.characterDetail,
                    "postProcess" to default.postProcess,
                    "staticLighting" to default.staticLighting,
                    "cutsceneQuality" to default.cutsceneQuality,
                )
            for ((key, value) in expected) {
                // Gson's Map decoder turns every JSON number into a Double, so an
                // Int field would otherwise compare 60.0 against 60. Compare
                // numerically for numbers and exactly for booleans.
                val actual = assetFields[key]
                if (value is Boolean) {
                    assertEquals("$name.$key: asset=$actual default=$value", value, actual)
                } else {
                    assertEquals(
                        "$name.$key: asset=$actual default=$value",
                        (value as Number).toDouble(),
                        (actual as Number).toDouble(),
                        0.0,
                    )
                }
            }
        }
    }

    @Test
    fun `the eight presets occupy distinct detail ranks`() {
        // The comment on PresetProfile records that high/ultra/cinematic used to
        // collapse to identical output until the detail ranks were spread. Pinned
        // here so a retune cannot silently re-collapse them.
        val ranks = TuningProfile.DEFAULT_PRESETS.map { it.key to it.value.detail }.toMap()
        assertEquals(8, ranks.values.toSet().size)
        assertEquals(0, ranks.getValue("potato"))
        assertEquals(7, ranks.getValue("cinematic"))
    }

    @Test
    fun `the presets are ordered potato to cinematic`() {
        // Order is load-bearing: BaseWuWaAppFunctionService.listPresets iterates
        // PRESETS.entries, so a reordered asset changes the order agents see.
        assertEquals(
            listOf("potato", "endurance", "performance", "competitive", "balanced", "high", "ultra", "cinematic"),
            TuningProfile.DEFAULT_PRESETS.keys.toList(),
        )
    }

    @Test
    fun `a missing asset key falls back to the default rather than zero`() {
        // Gson does not honour Kotlin default values, so a field dropped from the
        // asset would silently become 0/false. The parser applies the default
        // explicitly; this pins that a partial asset degrades safely.
        val partial = com.google.gson.JsonObject().apply { addProperty("screen", 42) }
        val parsed =
            TuningProfile(
                com.google.gson.JsonObject().apply {
                    add(
                        "presets",
                        com.google.gson.JsonObject().apply { add("potato", partial) },
                    )
                },
            )
        val potato = parsed.presets.getValue("potato")
        assertEquals("a present field wins", 42, potato.screen)
        assertEquals(
            "an absent field falls back to the default, not 0",
            TuningProfile.DEFAULT_PRESETS.getValue("potato").shadow,
            potato.shadow,
        )
        assertEquals(
            TuningProfile.DEFAULT_PRESETS.getValue("potato").staticLighting,
            potato.staticLighting,
        )
    }

    @Test
    fun `a preset missing from the asset is dropped rather than defaulted`() {
        // A typo in the asset must not resurrect a removed preset with default
        // values; it should disappear from the listing.
        val json =
            com.google.gson.JsonObject().apply {
                add(
                    "presets",
                    com.google.gson.JsonObject().apply {
                        add("potato", com.google.gson.JsonObject().apply { addProperty("screen", 60) })
                    },
                )
            }
        val parsed = TuningProfile(json)
        assertEquals(listOf("potato"), parsed.presets.keys.toList())
    }

    @Test
    fun `an empty asset yields every default`() {
        val parsed = TuningProfile(com.google.gson.JsonObject())
        assertEquals(TuningProfile.DEFAULT_PRESETS, parsed.presets)
    }

    @Test
    fun `the loaded profile agrees with the defaults`() {
        // The path the app actually takes: TuningProfile.get() after a load.
        val loaded = TuningProfile.get()
        assertEquals(TuningProfile.DEFAULT_PRESETS, loaded.presets)
    }

    /** Locates the asset by walking up from the working directory. */
    private fun realAssetFile(): File {
        val relative = "app/src/main/assets/config/tuning.json"
        val cwd = System.getProperty("user.dir") ?: "."
        if (File(cwd, relative).isFile) return File(cwd, relative)
        var dir: File? = File(cwd).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("tuning.json not found from $cwd")
    }
}
