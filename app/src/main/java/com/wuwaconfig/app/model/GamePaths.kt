package com.wuwaconfig.app.model

import com.wuwaconfig.app.config.GameProfile

/**
 * Device paths for the game, derived from [GameProfile] so a layout change ships
 * as an asset edit rather than a recompile.
 *
 * These are read from [GameProfile.get], which returns the compiled-in defaults
 * until `WuWaConfigApp.onCreate` has loaded the asset — so a caller that runs
 * before app startup still gets today's correct values rather than blanks. The
 * asset and the defaults are kept identical (see `GameProfile.DEFAULT_*`), which
 * is what makes that ordering harmless.
 *
 * `MONITORED_FILES` is the single source of truth for the five generated INIs.
 * It used to be duplicated as a literal `setOf(...)`/`listOf(...)` in six
 * places, so adding or renaming a generated file was a six-site edit with no
 * compiler help — the failure mode being a file the generator writes but
 * nothing backs up, hashes, or verifies.
 */
object GamePaths {
    private val profile: GameProfile get() = GameProfile.get()

    const val TARGET_PACKAGE = GameProfile.DEFAULT_PACKAGE
    const val CONFIG_PATH = GameProfile.DEFAULT_CONFIG_PATH
    const val LOG_PATH = GameProfile.DEFAULT_LOG_PATH
    const val LOG_FILE_NAME = GameProfile.DEFAULT_LOG_FILE_NAME
    const val HASH_MONITOR_REL_PATH = GameProfile.DEFAULT_HASH_MONITOR_REL_PATH
    const val UE4_COMMAND_LINE_REL_PATH = GameProfile.DEFAULT_UE4_COMMAND_LINE_REL_PATH

    /** The package as configured; falls back to [TARGET_PACKAGE]. */
    val packageName: String get() = profile.targetPackage

    /** Root of the game's own external files directory. */
    val GAME_ROOT: String get() = "/storage/emulated/0/Android/data/$packageName"

    val TARGET_DIR: String get() = "$GAME_ROOT/${profile.configPath}"
    val LOG_DIR: String get() = "$GAME_ROOT/${profile.logPath}"
    val HASH_MONITOR_PATH: String get() = "$GAME_ROOT/${profile.hashMonitorRelPath}"
    val UE4_COMMAND_LINE_PATH: String get() = "$GAME_ROOT/${profile.ue4CommandLineRelPath}"

    val LOG_NAME: String get() = profile.logFileName

    /**
     * The five INIs the app generates, backs up, hashes and verifies. Consumers
     * must read this rather than repeating the literal list.
     */
    val MONITORED_FILES: List<String> = listOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini", "Scalability.ini", "Hardware.ini")

    /** [MONITORED_FILES] as a set, for the `in` checks that dominate the call sites. */
    val MONITORED_FILE_SET: Set<String> = MONITORED_FILES.toSet()
}
