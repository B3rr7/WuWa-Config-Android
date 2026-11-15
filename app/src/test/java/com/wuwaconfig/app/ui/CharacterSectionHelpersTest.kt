package com.wuwaconfig.app.ui

import com.wuwaconfig.app.config.StatRecommendation
import com.wuwaconfig.app.ui.screens.pageSelection
import com.wuwaconfig.app.ui.screens.statProgressLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure decisions behind the character section's two interactive surfaces: which character
 * a settled carousel page stands for, and what a stat row's value pill reads.
 *
 * Both are extracted from the composables precisely so they can be pinned here — the pager
 * effects and the stats list are the parts of `MyCharacterScreen` that were wrong (a swipe
 * never selected anything, and the guide's target disappeared as soon as a current value
 * existed) and neither is reachable from a unit test in place.
 */
class CharacterSectionHelpersTest {
    private val ids = listOf("aalto", "baizhi", "calcharo", "camellya")

    @Test
    fun `settling on a page other than the selected one reports that page's character`() {
        assertEquals("camellya", pageSelection(ids, 3, "aalto"))
        assertEquals("baizhi", pageSelection(ids, 1, "camellya"))
    }

    @Test
    fun `settling on the selected page reports nothing so the two pager effects cannot echo`() {
        assertNull(pageSelection(ids, 1, "baizhi"))
    }

    @Test
    fun `an out-of-range page reports nothing instead of throwing`() {
        assertNull(pageSelection(ids, 4, "aalto"))
        assertNull(pageSelection(ids, -1, "aalto"))
        assertNull(pageSelection(emptyList(), 0, "aalto"))
    }

    @Test
    fun `the first settle with nothing selected adopts the first character`() {
        assertEquals("aalto", pageSelection(ids, 0, null))
    }

    @Test
    fun `a stat row shows current over target so the goal stays visible`() {
        assertEquals("291.6% / 240.0%", statProgressLabel(stat(current = "291.6%", target = "240.0%")))
    }

    @Test
    fun `a missing current value falls back to the target alone`() {
        assertEquals("70.0%", statProgressLabel(stat(current = null, target = "70.0%")))
        assertEquals("70.0%", statProgressLabel(stat(current = "", target = "70.0%")))
    }

    @Test
    fun `a missing target falls back to the current value alone`() {
        assertEquals("48.8%", statProgressLabel(stat(current = "48.8%", target = null)))
    }

    @Test
    fun `a stat with neither value reads as an em dash`() {
        assertEquals("—", statProgressLabel(stat(current = null, target = null)))
        assertEquals("—", statProgressLabel(stat(current = "  ", target = "")))
    }

    private fun stat(
        current: String?,
        target: String?,
    ) = StatRecommendation(
        name = "Energy Regen",
        target = target,
        current = current,
        pictureUrl = "",
        isFinished = false,
    )
}
