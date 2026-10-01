package com.wuwaconfig.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `BattleStats.plus` folds per-battle stats into a running total, and has fan-in
 * across the whole profile/stats surface. Two of its sixteen fields are NOT
 * additive — a plain `+` there is a plausible-looking bug that inflates the
 * player's month-card balance and can overwrite a known playerId with a blank
 * one from a log line that did not carry it.
 */
class BattleStatsTest {
    private fun stats(
        battles: Int = 0,
        echoesCollected: Int = 0,
        dodgeForward: Int = 0,
        dodgeBack: Int = 0,
        dodgeCounter: Int = 0,
        deaths: Int = 0,
        roleChanges: Int = 0,
        teleports: Int = 0,
        staggers: Int = 0,
        staminaUsed: Int = 0,
        echoSkillsUsed: Int = 0,
        echoTransformUsed: Int = 0,
        monthCards: Int = 0,
        monthCardRemainDays: Int = 0,
        playerId: String = "",
        logSizeBytes: Long = 0,
    ) = BattleStats(
        battles = battles,
        echoesCollected = echoesCollected,
        dodgeForward = dodgeForward,
        dodgeBack = dodgeBack,
        dodgeCounter = dodgeCounter,
        deaths = deaths,
        roleChanges = roleChanges,
        teleports = teleports,
        staggers = staggers,
        staminaUsed = staminaUsed,
        echoSkillsUsed = echoSkillsUsed,
        echoTransformUsed = echoTransformUsed,
        monthCards = monthCards,
        monthCardRemainDays = monthCardRemainDays,
        playerId = playerId,
        logSizeBytes = logSizeBytes,
    )

    @Test
    fun `counter fields add`() {
        val a = stats(battles = 3, echoesCollected = 10, dodgeForward = 4, dodgeBack = 2, dodgeCounter = 1)
        val b = stats(battles = 5, echoesCollected = 7, dodgeForward = 6, dodgeBack = 8, dodgeCounter = 3)
        val sum = a + b
        assertEquals(8, sum.battles)
        assertEquals(17, sum.echoesCollected)
        assertEquals(10, sum.dodgeForward)
        assertEquals(10, sum.dodgeBack)
        assertEquals(4, sum.dodgeCounter)
    }

    @Test
    fun `combat counters add`() {
        val sum =
            stats(deaths = 2, roleChanges = 3, teleports = 40, staggers = 9) +
                stats(deaths = 1, roleChanges = 4, teleports = 25, staggers = 11)
        assertEquals(3, sum.deaths)
        assertEquals(7, sum.roleChanges)
        assertEquals(65, sum.teleports)
        assertEquals(20, sum.staggers)
    }

    @Test
    fun `usage counters and month card count add`() {
        val sum =
            stats(staminaUsed = 100, echoSkillsUsed = 5, echoTransformUsed = 2, monthCards = 1) +
                stats(staminaUsed = 50, echoSkillsUsed = 7, echoTransformUsed = 3, monthCards = 2)
        assertEquals(150, sum.staminaUsed)
        assertEquals(12, sum.echoSkillsUsed)
        assertEquals(5, sum.echoTransformUsed)
        assertEquals("cards PURCHASED is a running count, so it adds", 3, sum.monthCards)
    }

    @Test
    fun `log size adds without overflowing int`() {
        val sum = stats(logSizeBytes = 3_000_000_000L) + stats(logSizeBytes = 2_000_000_000L)
        assertEquals(5_000_000_000L, sum.logSizeBytes)
    }

    // ─────────── monthCardRemainDays: maxOf, not sum ───────────

    @Test
    fun `remaining month card days take the max, never the sum`() {
        // Remaining days is a BALANCE (30 days granted, 12 spent -> 18 left), so
        // adding two sessions' balances would report 60 days on a 30-day card.
        val sum = stats(monthCardRemainDays = 18) + stats(monthCardRemainDays = 12)
        assertEquals(18, sum.monthCardRemainDays)
    }

    @Test
    fun `remaining days take the max regardless of operand order`() {
        val sum = stats(monthCardRemainDays = 5) + stats(monthCardRemainDays = 27)
        assertEquals(27, sum.monthCardRemainDays)
    }

    @Test
    fun `remaining days of zero do not erase a known balance`() {
        // A log with no card line yields 0; folding it in must not wipe 30.
        val sum = stats(monthCardRemainDays = 30) + stats(monthCardRemainDays = 0)
        assertEquals(30, sum.monthCardRemainDays)
    }

    // ─────────── playerId: non-empty wins ───────────

    @Test
    fun `a non-empty playerId wins`() {
        val sum = stats(playerId = "100123456") + stats(playerId = "100999999")
        assertEquals("the later non-empty value wins", "100999999", sum.playerId)
    }

    @Test
    fun `a blank playerId never overwrites a known one`() {
        val sum = stats(playerId = "100123456") + stats(playerId = "")
        assertEquals("100123456", sum.playerId)
    }

    @Test
    fun `a known playerId survives an empty accumulator`() {
        assertEquals("100123456", (stats() + stats(playerId = "100123456")).playerId)
    }

    @Test
    fun `two blank ids stay blank`() {
        assertEquals("", (stats() + stats()).playerId)
    }

    // ─────────── identity / edge cases ───────────

    @Test
    fun `adding a default instance is a no-op`() {
        val base = stats(battles = 3, monthCardRemainDays = 12, playerId = "abc")
        assertEquals(base, base + stats())
    }

    @Test
    fun `adding two defaults yields the default`() {
        assertEquals(stats(), stats() + stats())
    }

    @Test
    fun `the default instance is the additive identity`() {
        val base = stats(battles = 7, deaths = 1, monthCardRemainDays = 9, playerId = "id", logSizeBytes = 5)
        assertEquals(base, base + BattleStats())
    }

    @Test
    fun `plus is associative for the counters`() {
        val a = stats(battles = 1, deaths = 2)
        val b = stats(battles = 3, deaths = 4)
        val c = stats(battles = 5, deaths = 6)
        assertEquals((a + b) + c, a + (b + c))
    }

    @Test
    fun `folding a list left to right accumulates every battle`() {
        val sessions = (1..5).map { stats(battles = it, staminaUsed = it * 10) }
        val total = sessions.fold(stats()) { acc, s -> acc + s }
        assertEquals(1 + 2 + 3 + 4 + 5, total.battles)
        assertEquals((1 + 2 + 3 + 4 + 5) * 10, total.staminaUsed)
    }

    @Test
    fun `a large fold keeps the month-card balance bounded by the max seen`() {
        val sessions = (1..100).map { stats(monthCardRemainDays = it) }
        val total = sessions.fold(stats()) { acc, s -> acc + s }
        assertEquals("never the sum", 100, total.monthCardRemainDays)
    }

    @Test
    fun `plus does not mutate either operand`() {
        val a = stats(battles = 1, monthCardRemainDays = 10, playerId = "a")
        val b = stats(battles = 2, monthCardRemainDays = 20, playerId = "b")
        a + b
        assertEquals(1, a.battles)
        assertEquals(10, a.monthCardRemainDays)
        assertEquals("a", a.playerId)
        assertEquals(2, b.battles)
        assertEquals(20, b.monthCardRemainDays)
        assertEquals("b", b.playerId)
    }

    @Test
    fun `every field of a full merge is accounted for`() {
        val a = stats(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, "a", 15)
        val b = stats(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, "b", 15)
        val sum = a + b
        assertEquals(2, sum.battles)
        assertEquals(4, sum.echoesCollected)
        assertEquals(6, sum.dodgeForward)
        assertEquals(8, sum.dodgeBack)
        assertEquals(10, sum.dodgeCounter)
        assertEquals(12, sum.deaths)
        assertEquals(14, sum.roleChanges)
        assertEquals(16, sum.teleports)
        assertEquals(18, sum.staggers)
        assertEquals(20, sum.staminaUsed)
        assertEquals(22, sum.echoSkillsUsed)
        assertEquals(24, sum.echoTransformUsed)
        assertEquals(26, sum.monthCards)
        assertEquals("non-additive", 14, sum.monthCardRemainDays)
        assertEquals("non-additive", "b", sum.playerId)
        assertEquals(30L, sum.logSizeBytes)
    }

    @Test
    fun `negative counters do not silently drop the remainder`() {
        // Not a documented case, but a parse that yields a negative delta should
        // still sum predictably rather than saturating.
        assertEquals(-2, (stats(battles = -1) + stats(battles = -1)).battles)
    }

    @Test
    fun `plus is commutative for the additive fields`() {
        val a = stats(battles = 5, deaths = 2, logSizeBytes = 99)
        val b = stats(battles = 1, deaths = 7, logSizeBytes = 3)
        assertEquals((a + b).battles, (b + a).battles)
        assertEquals((a + b).deaths, (b + a).deaths)
        assertEquals((a + b).logSizeBytes, (b + a).logSizeBytes)
        assertTrue((a + b).monthCardRemainDays == (b + a).monthCardRemainDays)
    }
}
