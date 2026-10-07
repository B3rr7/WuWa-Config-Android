package com.wuwaconfig.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the filename matching in [PortraitAssets].
 *
 * The whole resolver is a join between wiki names and filenames written by a
 * different script, so the cases that matter are the ones where the two spellings
 * diverge: `#`, apostrophes, colons, spaces. A regression here is invisible — the
 * row simply falls back to an initial — so it has to be asserted directly.
 */
class PortraitAssetsTest {
    private val bundle =
        listOf(
            "aalto.webp",
            "shorekeeper.webp",
            "yangyang.webp",
            "yangyang_xuanling.webp",
            "rover.webp",
            "weapon_azure_oath.webp",
            "weapon_broadblade41.webp",
            "weapon_daybreaker_s_spine.webp",
            "weapon_stellar_symphony.webp",
        )

    @Test
    fun `normalizeKey drops every non-alphanumeric character`() {
        assertEquals("broadblade41", PortraitAssets.normalizeKey("Broadblade#41"))
        assertEquals("daybreakersspine", PortraitAssets.normalizeKey("Daybreaker's Spine"))
        assertEquals("yangyangxuanling", PortraitAssets.normalizeKey("Yangyang: Xuanling"))
        assertEquals("azureoath", PortraitAssets.normalizeKey("Azure Oath"))
    }

    @Test
    fun `character index resolves names whose slug differs`() {
        val index = PortraitAssets.buildIndex(bundle, weapon = false)
        assertEquals("shorekeeper.webp", index[PortraitAssets.normalizeKey("Shorekeeper")])
        // The wiki writes a colon; the file uses an underscore.
        assertEquals("yangyang_xuanling.webp", index[PortraitAssets.normalizeKey("Yangyang: Xuanling")])
    }

    @Test
    fun `character index ignores weapon files`() {
        val index = PortraitAssets.buildIndex(bundle, weapon = false)
        assertNull(index[PortraitAssets.normalizeKey("Azure Oath")])
    }

    @Test
    fun `weapon index keys on the name with the prefix stripped`() {
        val index = PortraitAssets.buildIndex(bundle, weapon = true)
        // The prefix must not leak into the key or every lookup misses.
        assertEquals("weapon_azure_oath.webp", index[PortraitAssets.normalizeKey("Azure Oath")])
        assertEquals("weapon_daybreaker_s_spine.webp", index[PortraitAssets.normalizeKey("Daybreaker's Spine")])
    }

    @Test
    fun `weapon index resolves a hash name that the gacha slug rule misses`() {
        val index = PortraitAssets.buildIndex(bundle, weapon = true)
        // GachaAvatar.assetFile would ask for weapon_broadblade_41.webp here.
        assertEquals("weapon_broadblade41.webp", index[PortraitAssets.normalizeKey("Broadblade#41")])
    }

    @Test
    fun `weapon index ignores character files`() {
        val index = PortraitAssets.buildIndex(bundle, weapon = true)
        assertNull(index[PortraitAssets.normalizeKey("Shorekeeper")])
    }

    @Test
    fun `a name with no portrait resolves to nothing rather than throwing`() {
        val index = PortraitAssets.buildIndex(bundle, weapon = false)
        assertNull(index[PortraitAssets.normalizeKey("Not A Real Character")])
    }

    @Test
    fun `non-webp entries are skipped`() {
        val index = PortraitAssets.buildIndex(bundle + "notes.txt", weapon = false)
        assertNull(index[PortraitAssets.normalizeKey("notes")])
    }

    @Test
    fun `an empty directory yields an empty index`() {
        assertEquals(emptyMap<String, String>(), PortraitAssets.buildIndex(emptyList(), weapon = false))
    }
}
