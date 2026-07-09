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
    val logSizeBytes: Long = 0,
) {
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
            logSizeBytes = logSizeBytes + other.logSizeBytes,
        )
}
