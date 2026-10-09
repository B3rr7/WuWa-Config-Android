package com.wuwaconfig.app.model

data class PlayerProfile(
    val uid: String? = null,
    val server: String? = null,
    val playerLevel: Int? = null,
    val gameVersion: String? = null,
    val patchVersion: String? = null,
    val launcherVersion: String? = null,
    val language: String? = null,
    val engineSettingCount: Int = 0,
    val deviceProfileCount: Int = 0,
    val gameUserSettingCount: Int = 0,
    val scalabilitySettingCount: Int = 0,
    val hardwareSettingCount: Int = 0,
    val lastLoginTime: String? = null,
    val towerFloor: Int? = null,
    val weeklyRogueScore: Int? = null,
    val battlePassPurchased: Boolean = false,
    val serverLevels: List<Pair<String, Int>> = emptyList(),
    val loopTowerSeason: Int? = null,
    // Device / hardware (from Client.log)
    val gpu: String? = null,
    val socName: String? = null,
    val ramMb: Int? = null,
    val androidVersion: String? = null,
    val resolution: String? = null,
    val renderApi: String? = null,
    val vulkanStatus: String? = null,
    // Performance / diagnostics (from Client.log)
    val fpsActual: Float? = null,
    val fpsCap: Int? = null,
    val screenPct: Float? = null,
    val shadowQ: Int? = null,
    val qualityMode: String? = null,
    val thermalEvents: Int = 0,
    val gpuOom: Int = 0,
    val dropFrames: Int = 0,
    val textureErrors: Int = 0,
    val forbiddenCvars: Int = 0,
    /**
     * Whether the Client.log read actually produced a parsed [LogInfo].
     *
     * This exists to separate two states the UI previously rendered identically.
     * Every counter above defaults to 0 and every nullable field to null, so a
     * *healthy* session (read succeeded, log simply contains no OOM/thermal/drop
     * events) was indistinguishable from a *failed* read — both collapsed the
     * DIAGNOSTICS block and showed the "no performance data in the game log"
     * empty state. Verified on-device: a clean run has all five counters at 0 and
     * no `AverageFPS` line at all, which is not a UE4 stat this game emits, so
     * `fpsActual` is permanently null regardless of how well the read went.
     *
     * `false` means "we could not read the log", which is the only condition
     * under which an empty section is a problem worth telling the user about.
     *
     * Defaults to `false` so a stale or hand-built JSON cache (and every existing
     * unit test constructing a profile directly) is treated as "unread" — the
     * conservative direction, since it shows the explanatory text rather than
     * asserting a clean bill of health that was never measured.
     */
    val deviceLogRead: Boolean = false,
)
