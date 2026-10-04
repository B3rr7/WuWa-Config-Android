package com.wuwaconfig.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Portrait resolution order for the gacha grids.
 *
 * The bug this pins: `coil-network` is not a dependency, so Coil cannot resolve an
 * `https` URL at all, yet the old code preferred the official Kuro URL and set the
 * bundled asset to null whenever one existed. Every character the guide had art for
 * therefore rendered as a blank circle while 181 bundled `.webp` files went unused.
 *
 * The order is asserted on [portraitModel] rather than on the composable because the
 * asset *filename* needs a `Context` to list `assets/`, and under
 * `unitTests.isReturnDefaultValues = true` that listing returns null — so a test
 * written against the composable would see an empty bundle for every input and pass
 * for the wrong reason. That is the same trap that made `LocalOnlyImageTest` and
 * `isLocalOnlyImageUri` vacuous until the logic was made plain string handling.
 */
class GachaAvatarPortraitTest {
    private val official = "https://guide-res.aki-game.net/card.png"

    // ─────────── the fix: a bundled asset must survive an official URL ───────────

    @Test
    fun `a bundled asset wins over an official url Coil cannot fetch`() {
        assertEquals(
            "file:///android_asset/gacha_avatars/hsin.webp",
            portraitModel(bundledAsset = "hsin.webp", officialUrl = official),
        )
    }

    @Test
    fun `the bundled asset is used even when the official url is present and blank-ish`() {
        // The guide returns empty strings for art it does not have; setOfficialArt
        // filters those out, but portraitModel must not depend on that having run.
        assertEquals(
            "file:///android_asset/gacha_avatars/hsin.webp",
            portraitModel(bundledAsset = "hsin.webp", officialUrl = ""),
        )
    }

    @Test
    fun `a weapon resolves through the bundled bundle with no official url at all`() {
        // officialArtFor returns null for weapons — the guide exposes no weapon data —
        // so this is the path every weapon portrait takes.
        assertEquals(
            "file:///android_asset/gacha_avatars/weapon_broadblade41.webp",
            portraitModel(bundledAsset = "weapon_broadblade41.webp", officialUrl = null),
        )
    }

    // ─────────── the fallbacks, unchanged ───────────

    @Test
    fun `the official url is used when the bundle has no file for the character`() {
        assertEquals(official, portraitModel(bundledAsset = null, officialUrl = official))
    }

    @Test
    fun `nothing resolves to null so the coloured initial is drawn`() {
        assertNull(portraitModel(bundledAsset = null, officialUrl = null))
    }

    @Test
    fun `an unknown 3-star material with neither source resolves to the initial`() {
        // The common case the avatar doc describes: most rows on a global account are
        // materials whose wiki filename does not match the name the endpoint returns.
        assertNull(portraitModel(bundledAsset = null, officialUrl = null))
    }
}
