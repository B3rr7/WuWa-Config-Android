package com.wuwaconfig.app.model

data class BattleStats(
    val battles: Int = 0,
    val echoesCollected: Int = 0,
    val dodgeForward: Int = 0,
    val dodgeBack: Int = 0,
    val dodgeCounter: Int = 0,
    val deaths: Int = 0,
    val roleChanges: Int = 0,
    val teleports: Int = 0,
    val staggers: Int = 0,
    val staminaUsed: Int = 0,
    val echoSkillsUsed: Int = 0,
    val echoTransformUsed: Int = 0,
    val monthCards: Int = 0,
    val monthCardRemainDays: Int = 0,
    val playerId: String = "",
    val logSizeBytes: Long = 0,
    val playtimeSeconds: Long = 0L,
    val sessions: Int = 0,
) {
    val totalDodges: Int get() = dodgeForward + dodgeBack + dodgeCounter

    val battlesPerHour: Double
        get() = if (playtimeSeconds > 0L) battles * 3600.0 / playtimeSeconds else 0.0

    val deathsPerBattle: Double
        get() = if (battles > 0) deaths.toDouble() / battles else 0.0

    val dodgesPerBattle: Double
        get() = if (battles > 0) totalDodges.toDouble() / battles else 0.0

    val echoSkillsPerBattle: Double
        get() = if (battles > 0) echoSkillsUsed.toDouble() / battles else 0.0

    val staminaPerBattle: Double
        get() = if (battles > 0) staminaUsed.toDouble() / battles else 0.0

    val playtimeHours: Double
        get() = playtimeSeconds / 3600.0

    operator fun plus(other: BattleStats): BattleStats =
        BattleStats(
            battles = battles + other.battles,
            echoesCollected = echoesCollected + other.echoesCollected,
            dodgeForward = dodgeForward + other.dodgeForward,
            dodgeBack = dodgeBack + other.dodgeBack,
            dodgeCounter = dodgeCounter + other.dodgeCounter,
            deaths = deaths + other.deaths,
            roleChanges = roleChanges + other.roleChanges,
            teleports = teleports + other.teleports,
            staggers = staggers + other.staggers,
            staminaUsed = staminaUsed + other.staminaUsed,
            echoSkillsUsed = echoSkillsUsed + other.echoSkillsUsed,
            echoTransformUsed = echoTransformUsed + other.echoTransformUsed,
            monthCards = monthCards + other.monthCards,
            monthCardRemainDays = maxOf(monthCardRemainDays, other.monthCardRemainDays),
            playerId = if (other.playerId.isNotEmpty()) other.playerId else playerId,
            logSizeBytes = logSizeBytes + other.logSizeBytes,
            playtimeSeconds = playtimeSeconds + other.playtimeSeconds,
            sessions = sessions + other.sessions,
        )
}

data class DailyBattleStats(
    val date: String,
    val stats: BattleStats,
)

data class BattleStatsSummary(
    val total: BattleStats,
    val daily: List<DailyBattleStats> = emptyList(),
    val accountId: String = "",
    val timestampMs: Long = 0L,
) {
    fun lastNDays(n: Int): List<DailyBattleStats> = daily.sortedBy { it.date }.takeLast(n)
}
