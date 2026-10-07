package com.wuwaconfig.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the filename form [MaterialIcons.slug] produces.
 *
 * The app resolves an icon by rebuilding the downloader's filename and testing
 * membership, so a divergence between the two sides is silent: every row just
 * renders without an icon. The apostrophe cases are the ones that matter —
 * `Loong's Pearl` and `Sentinel's Dagger` are real materials, and a slug that
 * dropped or doubled the separator would miss them.
 */
class MaterialIconsTest {
    @Test
    fun `spaces fold to a single underscore`() {
        assertEquals("shell_credit", MaterialIcons.slug("Shell Credit"))
        assertEquals("lf_howler_core", MaterialIcons.slug("LF Howler Core"))
    }

    @Test
    fun `apostrophes fold to a single underscore`() {
        assertEquals("loong_s_pearl", MaterialIcons.slug("Loong's Pearl"))
        assertEquals("sentinel_s_dagger", MaterialIcons.slug("Sentinel's Dagger"))
        assertEquals("the_netherworld_s_stare", MaterialIcons.slug("The Netherworld's Stare"))
    }

    @Test
    fun `digits and mixed case are preserved`() {
        assertEquals("waveworn_residue_210", MaterialIcons.slug("Waveworn Residue 210"))
        assertEquals("novaburst", MaterialIcons.slug("NovaBurst"))
    }

    @Test
    fun `leading and trailing punctuation is trimmed`() {
        assertEquals("afterlife", MaterialIcons.slug("\"Afterlife\""))
        assertEquals("sword", MaterialIcons.slug("  Sword  "))
    }

    @Test
    fun `repeated separators collapse`() {
        assertEquals("gauntlets_21d", MaterialIcons.slug("Gauntlets#21D"))
        assertEquals("rectifier_25", MaterialIcons.slug("Rectifier #25"))
    }
}
