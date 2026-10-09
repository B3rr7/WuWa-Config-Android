package com.wuwaconfig.app.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the zero-allocation summary scanner to the straightforward implementation.
 *
 * `scanSummary` re-implements the counting rules against index bounds instead of
 * line Strings, so it is a second implementation of one rule and can silently drift
 * — which it did three times while being written (a day mask that bled into the
 * month, a date key that still carried the time, a `playerId:` that is followed by
 * a space in the live log). Equivalence is asserted here rather than trusted.
 */
class ParserBenchTest {
    private fun line(
        ts: String,
        body: String,
    ) = "[$ts][  0][GameThread]Sharphereal: Display: $body"

    private val sample =
        listOf(
            "Log file open, [2026.10.09-09.39.39]",
            line("2026.10.09-09.39.40:100", "[Audio][CWZ][1] 切换玩家战斗音乐状态: 进入战斗未造成伤害"),
            line("2026.10.09-09.39.41:200", "[DeathComponent]执行角色死亡逻辑 [Entity: [object WorldEntity(Id=1)]]"),
            line("2026.10.09-09.39.42:300", "[C#][38][Info][Performance][XWX][4] SetUserId [playerId: 700670069]"),
            line("2026.10.09-09.39.43:400", "[Buff]本地移除buff [buffId: 1]"),
            line("2026.10.09-09.39.44:500", "[StateMachineNew]远端切换状态，root[C主状态机|10007] 激活节点[覆盖_C进入战斗|132]"),
            line("2026.10.09-12.00.00:600", "[BGM] 切换玩家战斗音乐状态: 进入战斗未造成伤害"),
            line("2026.10.10-09.00.00:700", "[DeathComponent]执行角色死亡逻辑 [Entity: [object WorldEntity(Id=2)]]"),
            line("2026.10.10-09.00.01:800", "[PowerModule][YZY][1] 当前体力数据 [data: [EnergyInfo, EnergyInfo]]"),
            line("2026.10.10-09.05.00:900", "[PowerModule][YZY][2] 当前体力数据 [data: [EnergyInfo, EnergyInfo]]"),
            line("2026.10.10-09.06.00:950", "no timestamp on this line 执行角色死亡逻辑"),
        ).joinToString("\n")

    @Test
    fun `fast scanner agrees with the reference parser`() {
        val text = sample + "\n"
        val fast = LogParser.parseBattleStatsSummary(text)
        val ref = LogParser.parseBattleStatsSummaryReference(text)
        assertEquals(ref.total, fast.total)
        assertEquals(ref.daily, fast.daily)
        assertEquals(ref.accountId, fast.accountId)
    }

    @Test
    fun `fast scanner agrees when the log has no trailing newline`() {
        val text = sample
        assertEquals(
            LogParser.parseBattleStatsSummaryReference(text).daily,
            LogParser.parseBattleStatsSummary(text).daily,
        )
    }

    @Test
    fun `counters survive lines that carry no timestamp`() {
        val s = LogParser.parseBattleStatsSummary(sample + "\n")
        // The final untimestamped line still counts toward the total.
        assertEquals(3, s.total.deaths)
        assertEquals(2, s.total.battles)
        assertEquals("700670069", s.total.playerId)
        // 3.7.0 logs `当前体力数据 [data: [EnergyInfo...]]` and no longer emits the
        // `UPs:` field the stamina delta reads, so this stays 0 on live data.
        assertEquals(0, s.total.staminaUsed)
    }

    @Test
    fun `byte length hint is used verbatim`() {
        assertEquals(4242L, LogParser.parseBattleStatsSummary(sample + "\n", byteLengthHint = 4242L).total.logSizeBytes)
    }
}
