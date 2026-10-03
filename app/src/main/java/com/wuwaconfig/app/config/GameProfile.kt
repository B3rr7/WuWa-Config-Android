package com.wuwaconfig.app.config

import android.content.res.AssetManager
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import java.util.Properties

/**
 * Every value coupled to a specific Wuthering Waves build, loaded from
 * `assets/config/game_profile.properties`.
 *
 * ## Why this exists
 *
 * The app's game knowledge used to live entirely in Kotlin literals. Every one
 * of them is correct only for the build it was written against, and a game
 * update turns the stale ones into *silent* failures rather than crashes:
 *
 *  - a renamed log file ⇒ "No readable Client.log found"
 *  - a moved config directory ⇒ deploy writes to a path that does not exist
 *  - a renamed CVar ⇒ the generator writes a dead key the engine ignores
 *  - a retuned pity guarantee ⇒ every prediction is quietly wrong
 *
 * Moving them here means a retune ships as a one-line asset edit, and — more
 * usefully — that `gameVersion`/`versionCode` become *data the app can read and
 * report*, which is what lets a future session tell whether the CVar database
 * in `assets/cvars/` is still in sync with the installed game.
 *
 * ## Failure contract
 *
 * Every field has a compiled-in default, and the defaults are the current
 * hardcoded literals. A missing, empty or partially-valid asset therefore
 * degrades to exactly today's behaviour rather than breaking the app: the whole
 * point of externalising config is to remove a failure mode, not add one. A
 * parse problem is logged and reported through [loadWarnings]; the
 * [version] getters fall back to the built-in version so the UI never shows a
 * blank version string.
 *
 * The load is synchronous and happens once, from `WuWaConfigApp.onCreate`,
 * because the values are needed by `GamePaths`' own construction. It reads one
 * small file, so the cost is a single asset open.
 */
class GameProfile internal constructor(
    private val props: Properties,
    /** Non-fatal problems found while loading, surfaced in the app log. */
    val loadWarnings: List<String> = emptyList(),
) {
    private fun raw(key: String): String? = props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

    private fun str(
        key: String,
        default: String,
    ): String = raw(key) ?: default

    private fun int(
        key: String,
        default: Int,
    ): Int = raw(key)?.toIntOrNull() ?: default

    private fun list(
        key: String,
        default: List<String>,
    ): List<String> = raw(key)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: default

    // ── build identity ──

    val gameVersion: String get() = str(KEY_GAME_VERSION, DEFAULT_GAME_VERSION)

    val versionCode: Int get() = int(KEY_VERSION_CODE, DEFAULT_VERSION_CODE)

    val engineGeneration: String get() = str(KEY_ENGINE_GENERATION, DEFAULT_ENGINE_GENERATION)

    /**
     * The lowest [versionCode] whose CVar database contents this build's data set
     * is valid for. The app compares it against the installed package's
     * versionCode and warns when the game is newer than the database, which is
     * the one staleness signal that was previously only a comment at the top of
     * `libUE4_cvars.txt`.
     */
    val minCvarDbVersionCode: Int get() = int(KEY_MIN_CVAR_DB_VERSION_CODE, versionCode)

    /**
     * True when the installed game is newer than the CVar database this APK
     * shipped with. Not fatal — the database is a superset that still strips
     * redundant keys correctly — but the user should know before trusting
     * "CVarDB: N redundant removed" counts.
     */
    fun isCvarDbStaleFor(installedVersionCode: Int): Boolean = installedVersionCode > minCvarDbVersionCode

    // ── paths ──

    val targetPackage: String get() = str(KEY_PACKAGE, DEFAULT_PACKAGE)

    val configPath: String get() = str(KEY_CONFIG_PATH, DEFAULT_CONFIG_PATH)

    val logPath: String get() = str(KEY_LOG_PATH, DEFAULT_LOG_PATH)

    val logFileName: String get() = str(KEY_LOG_FILE_NAME, DEFAULT_LOG_FILE_NAME)

    val hashMonitorRelPath: String get() = str(KEY_HASH_MONITOR_REL_PATH, DEFAULT_HASH_MONITOR_REL_PATH)

    val ue4CommandLineRelPath: String get() = str(KEY_UE4_COMMAND_LINE_REL_PATH, DEFAULT_UE4_COMMAND_LINE_REL_PATH)

    val backupLogNamePattern: String get() = str(KEY_BACKUP_LOG_NAME_PATTERN, DEFAULT_BACKUP_LOG_NAME_PATTERN)

    val backupStampPattern: String get() = str(KEY_BACKUP_STAMP_PATTERN, DEFAULT_BACKUP_STAMP_PATTERN)

    val localStorageDbRel: String get() = str(KEY_LOCAL_STORAGE_DB_REL, DEFAULT_LOCAL_STORAGE_DB_REL)

    val deviceStorageDbRel: String get() = str(KEY_DEVICE_STORAGE_DB_REL, DEFAULT_DEVICE_STORAGE_DB_REL)

    val localStorageTable: String get() = str(KEY_LOCAL_STORAGE_TABLE, DEFAULT_LOCAL_STORAGE_TABLE)

    val deviceStorageTable: String get() = str(KEY_DEVICE_STORAGE_TABLE, DEFAULT_DEVICE_STORAGE_TABLE)

    // ── game flags / caps ──

    val forceCSharpEnvFlag: String get() = str(KEY_FORCE_CSHARP_FLAG, DEFAULT_FORCE_CSHARP_FLAG)

    val hashModifyCountCap: Int get() = int(KEY_HASH_MODIFY_COUNT_CAP, DEFAULT_HASH_MODIFY_COUNT_CAP)

    // ── gacha economy ──

    val hardPity: Int get() = int(KEY_HARD_PITY, DEFAULT_HARD_PITY)

    val softPityStart: Int get() = int(KEY_SOFT_PITY_START, DEFAULT_SOFT_PITY_START)

    val softPityRateAtThreshold: Double
        get() = raw(KEY_SOFT_PITY_RATE)?.toDoubleOrNull() ?: DEFAULT_SOFT_PITY_RATE

    val fourStarGuarantee: Int get() = int(KEY_FOUR_STAR_GUARANTEE, DEFAULT_FOUR_STAR_GUARANTEE)

    val currencyPerPull: Int get() = int(KEY_CURRENCY_PER_PULL, DEFAULT_CURRENCY_PER_PULL)

    val avgWeaponPityFallback: Int get() = int(KEY_AVG_WEAPON_PITY, DEFAULT_AVG_WEAPON_PITY)

    val gachaHostIdPrefix1: String get() = str(KEY_GACHA_HOST_ID_PREFIX1, DEFAULT_GACHA_HOST_ID_PREFIX1)

    val gachaHostOther: String get() = str(KEY_GACHA_HOST_OTHER, DEFAULT_GACHA_HOST_OTHER)

    val gachaQueryPath: String get() = str(KEY_GACHA_QUERY_PATH, DEFAULT_GACHA_QUERY_PATH)

    val conveneUrlPattern: String get() = str(KEY_CONVENE_URL_PATTERN, DEFAULT_CONVENE_URL_PATTERN)

    /**
     * How long a fetched gacha record still counts as a *current snapshot*, in
     * hours. Past this the data is still perfectly usable history — it is just no
     * longer what the player's account looks like now, so the UI says so and an
     * agent is told the numbers may be behind.
     *
     * Distinct from [gachaHistoryRetentionHours] on purpose. Collapsing the two
     * is what turned the history file into a self-deleting cache.
     */
    val gachaHistoryFreshHours: Int get() = int(KEY_GACHA_FRESH_HOURS, DEFAULT_GACHA_FRESH_HOURS)

    /**
     * How long the fetched gacha history is kept on disk, in hours. The default
     * is one year rather than "forever" so the file cannot grow without bound if
     * the player never clears it.
     */
    val gachaHistoryRetentionHours: Int get() = int(KEY_GACHA_RETENTION_HOURS, DEFAULT_GACHA_RETENTION_HOURS)

    // ── CVar data ──

    val supportedFrameCaps: List<Int>
        get() =
            list(KEY_SUPPORTED_FRAME_CAPS, DEFAULT_SUPPORTED_FRAME_CAPS.map { it.toString() }).mapNotNull { it.toIntOrNull() }
                .ifEmpty { DEFAULT_SUPPORTED_FRAME_CAPS }

    val cvarPrefixes: List<String> get() = list(KEY_CVAR_PREFIXES, DEFAULT_CVAR_PREFIXES)

    val forbiddenCvars: List<String> get() = list(KEY_FORBIDDEN_CVARS, DEFAULT_FORBIDDEN_CVARS)

    val ue5OnlyCvars: List<String> get() = list(KEY_UE5_ONLY_CVARS, DEFAULT_UE5_ONLY_CVARS)

    val engineKeywords: List<String> get() = list(KEY_ENGINE_KEYWORDS, DEFAULT_ENGINE_KEYWORDS)

    companion object {
        private const val ASSET_PATH = "config/game_profile.properties"

        // ── keys ──
        private const val KEY_GAME_VERSION = "gameVersion"
        private const val KEY_VERSION_CODE = "versionCode"
        private const val KEY_ENGINE_GENERATION = "engineGeneration"
        private const val KEY_MIN_CVAR_DB_VERSION_CODE = "minCvarDbVersionCode"
        private const val KEY_PACKAGE = "package"
        private const val KEY_CONFIG_PATH = "configPath"
        private const val KEY_LOG_PATH = "logPath"
        private const val KEY_LOG_FILE_NAME = "logFileName"
        private const val KEY_HASH_MONITOR_REL_PATH = "hashMonitorRelPath"
        private const val KEY_UE4_COMMAND_LINE_REL_PATH = "ue4CommandLineRelPath"
        private const val KEY_BACKUP_LOG_NAME_PATTERN = "backupLogNamePattern"
        private const val KEY_BACKUP_STAMP_PATTERN = "backupStampPattern"
        private const val KEY_LOCAL_STORAGE_DB_REL = "localStorageDbRel"
        private const val KEY_DEVICE_STORAGE_DB_REL = "deviceStorageDbRel"
        private const val KEY_LOCAL_STORAGE_TABLE = "localStorageTable"
        private const val KEY_DEVICE_STORAGE_TABLE = "deviceStorageTable"
        private const val KEY_FORCE_CSHARP_FLAG = "forceCSharpEnvFlag"
        private const val KEY_HASH_MODIFY_COUNT_CAP = "hashModifyCountCap"
        private const val KEY_HARD_PITY = "hardPity"
        private const val KEY_SOFT_PITY_START = "softPityStart"
        private const val KEY_SOFT_PITY_RATE = "softPityRateAtThreshold"
        private const val KEY_FOUR_STAR_GUARANTEE = "fourStarGuarantee"
        private const val KEY_CURRENCY_PER_PULL = "currencyPerPull"
        private const val KEY_AVG_WEAPON_PITY = "avgWeaponPityFallback"
        private const val KEY_GACHA_HOST_ID_PREFIX1 = "gachaHostIdPrefix1"
        private const val KEY_GACHA_HOST_OTHER = "gachaHostOther"
        private const val KEY_GACHA_QUERY_PATH = "gachaQueryPath"
        private const val KEY_CONVENE_URL_PATTERN = "conveneUrlPattern"
        private const val KEY_GACHA_FRESH_HOURS = "gachaHistoryFreshHours"
        private const val KEY_GACHA_RETENTION_HOURS = "gachaHistoryRetentionHours"
        private const val KEY_SUPPORTED_FRAME_CAPS = "supportedFrameCaps"
        private const val KEY_CVAR_PREFIXES = "cvarPrefixes"
        private const val KEY_FORBIDDEN_CVARS = "forbiddenCvars"
        private const val KEY_UE5_ONLY_CVARS = "ue5OnlyCvars"
        private const val KEY_ENGINE_KEYWORDS = "engineKeywords"

        // ── compiled-in defaults: the values this app shipped with. Every one of
        // these is also the value the corresponding asset key must contain, so a
        // missing key is a no-op rather than a regression. ──
        const val DEFAULT_GAME_VERSION = "3.7.0"
        const val DEFAULT_VERSION_CODE = 180055720
        const val DEFAULT_ENGINE_GENERATION = "UE4"
        const val DEFAULT_PACKAGE = "com.kurogame.wutheringwaves.global"
        const val DEFAULT_CONFIG_PATH = "files/UE4Game/Client/Client/Saved/Config/Android"
        const val DEFAULT_LOG_PATH = "files/UE4Game/Client/Client/Saved/Logs"
        const val DEFAULT_LOG_FILE_NAME = "Client.log"
        const val DEFAULT_HASH_MONITOR_REL_PATH = "files/UE4Game/Client/Client/Config/Kuro/KuroConfigMonitor.hash"
        const val DEFAULT_UE4_COMMAND_LINE_REL_PATH = "files/UE4Game/Client/UE4CommandLine.txt"
        const val DEFAULT_BACKUP_LOG_NAME_PATTERN = """Client-backup-[A-Za-z0-9._-]+\.log"""
        const val DEFAULT_BACKUP_STAMP_PATTERN = """(\d{4})\.(\d{2})\.(\d{2})-(\d{2})\.(\d{2})\.(\d{2})"""
        const val DEFAULT_LOCAL_STORAGE_DB_REL = "LocalStorage/LocalStorage.db"
        const val DEFAULT_DEVICE_STORAGE_DB_REL = "DeviceSaved/DeviceStorage.db"
        const val DEFAULT_LOCAL_STORAGE_TABLE = "LocalStorage"
        const val DEFAULT_DEVICE_STORAGE_TABLE = "LocalStorage"
        const val DEFAULT_FORCE_CSHARP_FLAG = "-ForceEnableCSharpEnvironment"
        const val DEFAULT_HASH_MODIFY_COUNT_CAP = 8
        const val DEFAULT_HARD_PITY = 80
        const val DEFAULT_SOFT_PITY_START = 66
        const val DEFAULT_SOFT_PITY_RATE = 0.15
        const val DEFAULT_FOUR_STAR_GUARANTEE = 10
        const val DEFAULT_CURRENCY_PER_PULL = 160
        const val DEFAULT_AVG_WEAPON_PITY = 57
        const val DEFAULT_GACHA_HOST_ID_PREFIX1 = "https://gmserver-api.aki-game2.com"
        const val DEFAULT_GACHA_HOST_OTHER = "https://gmserver-api.aki-game2.net"
        const val DEFAULT_GACHA_QUERY_PATH = "/gacha/record/query"

        const val DEFAULT_CONVENE_URL_PATTERN =
            """https://aki-gm-resources(-oversea)?\.aki-game\.(net|com)/aki/gacha/index\.html#/record[^"\s]*"""

        /** Was the store's single TTL. Kept as the freshness window so the "re-fetch me" hint is unchanged. */
        const val DEFAULT_GACHA_FRESH_HOURS = 12

        /** 8760h = 365d. Long enough to be history, bounded so the file cannot grow without limit. */
        const val DEFAULT_GACHA_RETENTION_HOURS = 8760

        val DEFAULT_SUPPORTED_FRAME_CAPS = listOf(30, 45, 60, 90, 120)

        val DEFAULT_CVAR_PREFIXES =
            listOf(
                "a.", "bbm.", "compat.", "cook.", "fx.", "foliage.", "gc.", "grass.",
                "kuro.", "lod.", "m.", "magt.", "n.", "niagara.", "r.", "s.", "sg.",
                "slate.", "t.", "tick.", "vr.", "wp.",
            )

        val DEFAULT_FORBIDDEN_CVARS =
            listOf(
                "r.Kuro.SkeletalMesh.LODDistanceScale",
                "r.Streaming.Boost",
                "r.Streaming.PoolSize",
                "r.Streaming.LimitPoolSizeTOVRAM",
                "r.Shadow.MaxCSMResolution",
                "r.Streaming.MinBoost",
                "r.MipMapLODBias",
                "r.TextureGroup.Landscape.TextureLODBias",
                "r.Kuro.TexturePool.ExtraBudgetMB",
                "r.Streaming.CPUReadback",
                "r.Streaming.UseAsyncCPUReadback",
                "r.Streaming.MaxNumTexturesToStreamPerFrame",
                "r.Streaming.MinMipForSplitRequest",
                "r.Streaming.UseFixedPoolsize",
                "r.Streaming.UseAllMips",
                "r.Streaming.MaxTempMemoryAllowed",
                "r.RayTracing.LimitDevice",
                "r.DetailMode",
                "r.MaterialQualityLevel",
                "r.KuroMaterialQualityLevel",
                "r.ViewDistanceScale",
                "Kuro.CppEffectsSystem.UseLowMemoryPlayerEffectLruCapacity",
                "Kuro.CppEffectSystem.UseLowMemoryPlayerEffectLruCapacity",
                "r.AsyncComputePSO",
                "r.Streamline.DLSSG.RetainResourcesWhenOff",
                "r.MobileContentScaleFactor",
                "r.SecondaryScreenPercentage.GameViewport",
                "r.ScreenPercentage",
                "r.AFME.Enable",
                "r.MFRC.Enable",
                "r.FEstimation.Option",
            )

        val DEFAULT_UE5_ONLY_CVARS =
            listOf("r.temporalaa.upsampling", "r.temporalaa.algorithm", "r.temporalaacatmullrom")

        /**
         * The markers that prove a decoded payload is an engine log rather than
         * noise. This list is the *first* gate a decrypted log passes, so it has to
         * carry the vocabulary this game actually emits: a stock-UE4 list alone
         * failed on a real device log, which does not use the classic desktop
         * `LogInit:` / `LogRHI:` / `Core.System` forms. The Kuro-specific entries
         * are load-bearing, not decoration.
         */
        val DEFAULT_ENGINE_KEYWORDS =
            listOf(
                "LogInit",
                "LogRHI",
                "Core.System",
                "GameUserSettings",
                "K#GPUFamily",
                "Selected Device Profile",
                "Resolution",
                "AverageFPS",
                "r.ScreenPercentage",
                "sg.ShadowQuality",
                "PhysicalMemoryMB",
                "LogDynamicAtlas",
                "stdout",
                "LogMemory",
                "LogKuroRendering",
                "LogKuroLogging",
                "LogKuroStreaming",
                "LogAndroid",
                "LogPakFile",
                "LogConsoleManager",
                "LogStreaming",
                "LogContentStreaming",
                "LogFramePacer",
                "LogKuro",
                "GameThread",
                "Log file open",
            )

        @Volatile
        private var instance: GameProfile? = null

        /**
         * Idempotent, and deliberately NOT lazy-on-first-use: [com.wuwaconfig.app.model.GamePaths]
         * reads these during class initialisation, so a lazy holder would be read
         * before any trigger point and silently serve defaults forever.
         */
        fun load(
            assets: AssetManager,
            force: Boolean = false,
        ): GameProfile {
            instance?.let { if (!force) return it }
            val loaded = read(assets)
            instance = loaded
            if (loaded.loadWarnings.isEmpty()) {
                LogRepository.add(
                    "GameProfile: loaded $ASSET_PATH (game ${loaded.gameVersion}, v${loaded.versionCode})",
                    LogLevel.SUCCESS,
                )
            } else {
                LogRepository.add(
                    "GameProfile: $ASSET_PATH used with built-in defaults for " +
                        "${loaded.loadWarnings.size} field(s): ${loaded.loadWarnings.joinToString("; ")}",
                    LogLevel.WARNING,
                )
            }
            return loaded
        }

        /** The loaded profile, or the all-defaults profile if [load] never ran. */
        fun get(): GameProfile = instance ?: GameProfile(Properties()).also { instance = it }

        private fun read(assets: AssetManager): GameProfile {
            val warnings = mutableListOf<String>()
            val props = Properties()
            return try {
                assets.open(ASSET_PATH).bufferedReader().use { props.load(it) }
                val profile = GameProfile(props)
                // A key that parses to an empty list is a data bug, not a valid
                // configuration — catching it here is what stops a typo in the
                // asset from silently disabling an entire subsystem.
                for (
                (key, value) in
                listOf(
                    KEY_CVAR_PREFIXES to profile.cvarPrefixes,
                    KEY_FORBIDDEN_CVARS to profile.forbiddenCvars,
                    KEY_UE5_ONLY_CVARS to profile.ue5OnlyCvars,
                    KEY_ENGINE_KEYWORDS to profile.engineKeywords,
                    KEY_SUPPORTED_FRAME_CAPS to profile.supportedFrameCaps,
                )
                ) {
                    if (value.isEmpty()) warnings += "$key (empty)"
                }
                if (props.isEmpty) warnings += "asset parsed to zero keys"
                GameProfile(props, warnings)
            } catch (e: Exception) {
                // Built-in defaults: the app must keep working when the asset is
                // missing from a split APK, unreadable, or malformed.
                GameProfile(Properties(), listOf("load failed (${e.javaClass.simpleName}: ${e.message})"))
            }
        }
    }
}

/** Convenience for the common `get()?.x` call site shape. */
fun gameProfile(): GameProfile = GameProfile.get()
