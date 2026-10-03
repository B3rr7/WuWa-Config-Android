package com.wuwaconfig.app.appfunctions

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.DisplayMetrics
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.appfunctions.AppFunctionSystemUnknownException
import com.google.gson.reflect.TypeToken
import com.wuwaconfig.app.PREFS_NAME
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.BackupStore
import com.wuwaconfig.app.config.CvarOptimizer
import com.wuwaconfig.app.config.ForbiddenCvars
import com.wuwaconfig.app.config.GachaHistoryStore
import com.wuwaconfig.app.config.GachaStats
import com.wuwaconfig.app.config.GachaStatsResult
import com.wuwaconfig.app.config.LogParser
import com.wuwaconfig.app.config.PRESETS
import com.wuwaconfig.app.config.SmartBrain
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.model.BattleStatsStore
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.LogAnalysisStore
import com.wuwaconfig.app.model.LogInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * System-agent callable surface for the config generator and log analyser.
 *
 * Scope is deliberately the local half of the app. Every function here is read-only or
 * purely in-memory: [com.wuwaconfig.app.ui.DeployHistoryViewModel] and
 * [com.wuwaconfig.app.ui.BackupViewModel] own the device-mutating half, and those
 * operations either need a live ADB/Shizuku/Root session or destroy state the user
 * cannot undo, so none of them are reachable from an agent. That also means no function
 * here depends on a backend session, which is why none of them consult
 * [WuWaConfigApp.backendStatusValue] or the [com.wuwaconfig.app.ui.DeviceOps] busy flag.
 *
 * There is no Hilt graph in this app (see [WuWaConfigApp]): state lives in `lateinit`
 * application singletons and `object` singletons, so the service reaches them through
 * `applicationContext as WuWaConfigApp` — the same service-locator shape every ViewModel
 * uses. The ViewModels themselves are unreachable here (several are constructed inside a
 * composable rather than by the Activity), so this class reuses the stores and the
 * generator directly instead of going through them.
 *
 * KSP generates the concrete `WuWaConfigAppFunctionService` subclass and the assets
 * schema XML named by [AppFunctionServiceEntryPoint]. Do not reference the generated name.
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "WuWaConfigAppFunctionService",
    appFunctionXmlFileName = "wuwaconfig_app_function_service",
)
abstract class BaseWuWaAppFunctionService : AppFunctionService() {
    private val app: WuWaConfigApp
        get() = applicationContext as WuWaConfigApp

    /**
     * List the available graphics presets and what each one costs.
     *
     * Required workflow: call this before "generateConfig" when the caller asked for a quality
     * level rather than a named preset, so a preset name is chosen rather than guessed.
     *
     * @return Every preset, ordered from lowest to highest quality.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listPresets(): List<PresetInfo> =
        withContext(Dispatchers.Default) {
            // The config screen keeps its own hardcoded copy of this table and it has already
            // drifted from the source, so reading PRESETS is the only version worth returning.
            PRESETS.entries
                .sortedBy { it.value.detail }
                .map { (name, profile) ->
                    PresetInfo(
                        name = name,
                        detailRank = profile.detail,
                        summary = PRESET_SUMMARIES[name] ?: "Custom tuning profile",
                        renderScalePercent = profile.screen,
                        shadowQuality = profile.shadow,
                        shadowResolution = profile.shadowRes,
                        screenSpaceReflections = profile.ssr,
                        textureStreamingMultiplier = profile.streaming,
                        viewDistanceScale = profile.vd,
                        foliageDistanceScale = profile.flod,
                        grassCullDistance = profile.grasscull,
                        levelOfDetailBias = profile.lod_bias,
                        mipBias = profile.mipbias,
                        characterDetail = profile.characterDetail,
                        postProcessQuality = profile.postProcess,
                        staticLightingEnabled = profile.staticLighting,
                        cutsceneQuality = profile.cutsceneQuality,
                    )
                }
        }

    /**
     * Generate Wuthering Waves graphics configuration text for a preset.
     *
     * Nothing is written to the game; the configuration is returned as text for the user to
     * review. To choose the preset, call "recommendPreset" or "listPresets" first rather than
     * guessing a name.
     *
     * @param preset Preset identifier from "listPresets". An unrecognised name falls back to "balanced".
     * @param options Optional generator settings. Omit to use the app's own defaults.
     * @param includeCvarNames Set true to also return every CVar name in the output. Off by default because the list roughly doubles the response size.
     * @return The generated configuration files as text.
     * @throws AppFunctionInvalidArgumentException If a frame cap or game mode is outside the supported set.
     * @throws AppFunctionSystemUnknownException If generation fails unexpectedly; suggest retrying, and if it persists, having the user generate the configuration in the app instead.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun generateConfig(
        preset: String,
        options: ConfigOptions? = null,
        includeCvarNames: Boolean = false,
    ): GeneratedConfig =
        withContext(Dispatchers.IO) {
            val opts = (options ?: ConfigOptions()).toGeneratorOptions()
            val generator = app.configGenerator
            // generateWithCorePaths consults the CVar database to comment out redundant
            // entries. The database loads asynchronously in Application.onCreate, so without
            // this await a cold start silently produces unoptimised output.
            app.cvarDatabase.load()
            val result =
                try {
                    generator.generateWithCorePaths(preset, opts, generator.DEFAULT_CORE_SYSTEM)
                } catch (e: IllegalArgumentException) {
                    throw AppFunctionInvalidArgumentException("Preset '$preset' could not be generated: ${e.message}")
                } catch (e: Exception) {
                    throw AppFunctionSystemUnknownException("Config generation failed: ${e.message}")
                }
            GeneratedConfig(
                presetName = result.activePreset,
                cvarCount = result.cvarNames.size,
                cvarNames = if (includeCvarNames) result.cvarNames.sorted() else emptyList(),
                engineIni = result.ini.engine,
                deviceProfilesIni = result.ini.deviceProfiles,
                gameUserSettingsIni = result.ini.gameUserSettings,
                scalabilityIni = result.ini.scalability,
                hardwareIni = result.ini.hardware,
            )
        }

    /**
     * Read the device and account profile the app last captured.
     *
     * The profile is a local cache written after a device read, so an empty field means the
     * app has not been connected to the game yet rather than that the value is zero.
     *
     * @return The cached profile, with empty strings and zeroes for fields never captured.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDeviceProfile(): DeviceProfileInfo =
        withContext(Dispatchers.IO) {
            val profile = app.profileStore.load()
            DeviceProfileInfo(
                playerUid = profile?.uid ?: "",
                server = profile?.server ?: "",
                playerLevel = profile?.playerLevel ?: 0,
                gameVersion = profile?.gameVersion ?: "",
                androidVersion = profile?.androidVersion ?: "",
                gpu = profile?.gpu ?: "",
                socName = profile?.socName ?: "",
                ramMb = profile?.ramMb ?: 0,
                resolution = profile?.resolution ?: "",
                renderApi = profile?.renderApi ?: "",
                vulkanStatus = profile?.vulkanStatus ?: "",
                frameRateCap = profile?.fpsCap ?: 0,
                measuredFrameRate = profile?.fpsActual ?: 0f,
                renderScalePercent = profile?.screenPct ?: 0f,
                shadowQuality = profile?.shadowQ ?: 0,
                qualityMode = profile?.qualityMode ?: "",
                thermalEvents = profile?.thermalEvents ?: 0,
                gpuOutOfMemoryEvents = profile?.gpuOom ?: 0,
                droppedFrames = profile?.dropFrames ?: 0,
                textureErrors = profile?.textureErrors ?: 0,
            )
        }

    /**
     * Score the device and recommend a graphics preset.
     *
     * Required workflow: call "analyzeGameLog" first when a game log is available, so the score
     * reflects real measurements rather than the previous cached analysis. The returned
     * presetName can be passed straight to "generateConfig".
     *
     * @param useLogAnalysis Set false to ignore the cached analysis and score only the hardware this app can read directly. Defaults to true.
     * @return The recommended preset, its 0-100 score, and the reasons behind it.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun recommendPreset(useLogAnalysis: Boolean = true): PresetRecommendation =
        withContext(Dispatchers.IO) {
            val cached = if (useLogAnalysis) LogAnalysisStore.load(applicationContext) else null
            val info =
                cached?.logInfo?.let { analysed ->
                    // Copy rather than reuse: LogInfo is shared with the UI and its
                    // activeCvars map would be handed to SmartBrain unchanged.
                    analysed.copy(gpu = analysed.gpu ?: app.chipsetInfo.socName)
                } ?: hardwareOnlyLogInfo(applicationContext, app.chipsetInfo.socName)
            app.cvarDatabase.load()
            val rec = SmartBrain.scoreRecommendation(info, app.cvarDatabase)
            PresetRecommendation(
                presetName = rec.preset,
                score = rec.score,
                gpuTier = CvarOptimizer.getGPUTier(info.gpu),
                signals = rec.signals,
                // Say plainly when the score is hardware-only. A score built from the SoC name
                // alone looks as confident as a measurement-backed one but cannot see frame
                // drops or thermal pressure, which are most of the signal.
                warnings =
                    if (cached != null) {
                        rec.warnings
                    } else {
                        listOf(
                            "No analysed game log is available, so this score uses hardware identifiers only. " +
                                "Call \"analyzeGameLog\" with a game log for a measurement-based recommendation.",
                        ) + rec.warnings
                    },
            )
        }

    /**
     * Analyse a Wuthering Waves game log the user supplies.
     *
     * The game's own log is XOR-obfuscated; raw log bytes are accepted and decrypted
     * automatically, and plain text logs are read as-is. Logs from game builds 36 and 38 are
     * known to parse fully, while newer builds can yield little, so a near-empty result does
     * not mean the log is unusable.
     *
     * The result is not written to the app's cache; call "getCachedLogAnalysis" to read what
     * the app stored during its own last analysis run.
     *
     * @param logBytes Raw bytes of the game log, obfuscated or plain.
     * @return Device capabilities, stability counters, combat statistics, and a preset recommendation.
     * @throws AppFunctionInvalidArgumentException If the log is empty or holds no readable text.
     * @throws AppFunctionSystemUnknownException If the log cannot be parsed; suggest asking the user for a log from the current game version.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun analyzeGameLog(logBytes: ByteArray): GameLogAnalysis =
        withContext(Dispatchers.IO) {
            if (logBytes.isEmpty()) {
                throw AppFunctionInvalidArgumentException("The supplied game log is empty.")
            }
            val (text, decodeResult) = LogParser.decodeLogBytes(logBytes)
            if (text.isBlank()) {
                throw AppFunctionInvalidArgumentException("The supplied game log contains no readable text.")
            }
            app.cvarDatabase.load()
            val info =
                try {
                    withContext(Dispatchers.Default) { LogParser.parseLog(text) }
                } catch (e: Exception) {
                    throw AppFunctionSystemUnknownException("The game log could not be parsed: ${e.message}")
                }
            val battleStats = withContext(Dispatchers.Default) { LogParser.parseBattleStats(text) }
            val rec = SmartBrain.scoreRecommendation(info, app.cvarDatabase)
            GameLogAnalysis(
                deviceModel = info.deviceModel ?: "",
                gpu = info.gpu ?: "",
                socName = info.socName ?: "",
                ramMb = info.ramMb ?: 0,
                androidVersion = info.androidVersion ?: "",
                resolution = info.resolution ?: "",
                renderApi = info.gameApi ?: info.api ?: "",
                vulkanStatus = info.vulkanStatus ?: "",
                frameRateCap = info.fpsCap ?: 0,
                measuredFrameRate = info.fpsActual ?: 0f,
                renderScalePercent = info.screenPct ?: 0f,
                shadowQuality = info.shadowQ ?: 0,
                qualityMode = info.qualityMode ?: "",
                lowMemoryDevice = info.isLowMem ?: false,
                droppedFrames = info.dropFrames,
                thermalEvents = info.thermalEvents,
                gpuOutOfMemoryEvents = info.gpuOom,
                textureErrors = info.textureErrors,
                restrictedCvarCount = info.forbiddenCvars,
                activeCvarCount = info.activeCvars.size,
                wasEncrypted = decodeResult == LogParser.DecodeResult.DECRYPTED,
                logSizeBytes = logBytes.size.toLong(),
                battleStats = battleStats.toInfo(),
                recommendation =
                    PresetRecommendation(
                        presetName = rec.preset,
                        score = rec.score,
                        gpuTier = CvarOptimizer.getGPUTier(info.gpu),
                        signals = rec.signals,
                        warnings = rec.warnings,
                    ),
            )
        }

    /**
     * Read the log analysis the app cached during its own last analysis run.
     *
     * Required workflow: prefer "analyzeGameLog" when a log is available. This returns the
     * previous run's numbers, which are up to 24 hours old, and returns all-zero values when
     * no analysis has been run.
     *
     * @return The cached counters and the preset that was recommended at the time.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getCachedLogAnalysis(): CachedLogAnalysisInfo =
        withContext(Dispatchers.IO) {
            val cached = LogAnalysisStore.load(applicationContext)
            CachedLogAnalysisInfo(
                analysedAtEpochMillis = cached?.timestamp ?: 0L,
                measuredFrameRate = cached?.logInfo?.fpsActual ?: 0f,
                droppedFrames = cached?.logInfo?.dropFrames ?: 0,
                thermalEvents = cached?.logInfo?.thermalEvents ?: 0,
                gpuOutOfMemoryEvents = cached?.logInfo?.gpuOom ?: 0,
                textureErrors = cached?.logInfo?.textureErrors ?: 0,
                restrictedCvarCount = cached?.logInfo?.forbiddenCvars ?: 0,
                recommendedPreset = cached?.brainRecommendation?.preset ?: "",
                recommendationScore = cached?.brainRecommendation?.score ?: 0,
            )
        }

    /**
     * Read combat and exploration statistics from the app's cached analysis.
     *
     * Required workflow: prefer the battleStats field of "analyzeGameLog" when a log is
     * available. This returns the previous run's counts, up to 24 hours old, and all-zero values
     * when no analysis has been run.
     *
     * @return The cached battle statistics. The account identifier is withheld.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getCachedBattleStats(): BattleStatsInfo =
        withContext(Dispatchers.IO) {
            // BattleStats.playerId is an account identifier, deliberately not exposed here; the
            // app redacts the same field before writing it into app.log.
            (BattleStatsStore.load(applicationContext) ?: BattleStats()).toInfo()
        }

    /**
     * List past configuration deployments and whether each one improved performance.
     *
     * This is the only record of real-world effect. Every delta is zero when a deployment was
     * never measured, so check hasOutcome before quoting a number to the user.
     *
     * @param limit Maximum number of deployments to return, newest first. Capped at 20, the app's retention limit.
     * @return Deployments with their measured frame rate, thermal and memory deltas.
     * @throws AppFunctionInvalidArgumentException If limit is below 1.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDeployHistory(limit: Int = 5): List<DeployOutcome> =
        withContext(Dispatchers.IO) {
            if (limit < 1) {
                throw AppFunctionInvalidArgumentException("limit must be at least 1, but was $limit.")
            }
            app.deployHistoryStore
                .getAllRecords()
                .take(limit.coerceAtMost(MAX_DEPLOY_RECORDS))
                .map { it.toOutcome() }
        }

    /**
     * Retrieve a single past deployment by identifier.
     *
     * Required workflow: call "getDeployHistory" to obtain the identifier.
     *
     * @param deployId Identifier returned by "getDeployHistory".
     * @return That deployment and its measured outcome.
     * @throws AppFunctionElementNotFoundException If no deployment has that identifier; suggest calling "getDeployHistory" for the current list of identifiers.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getDeployOutcome(deployId: String): DeployOutcome =
        withContext(Dispatchers.IO) {
            val record =
                app.deployHistoryStore.getRecord(deployId)
                    ?: throw AppFunctionElementNotFoundException(
                        "No deployment has id '$deployId'. Call \"getDeployHistory\" for the current list of identifiers.",
                    )
            record.toOutcome()
        }

    /**
     * Read the gacha pity predictions from the app's stored pull history.
     *
     * The history is written when the app fetches gacha records and kept for a year,
     * so a result here is normally the account's whole record rather than one
     * session. It stops counting as a *current* snapshot after 12 hours; check
     * "getGachaStats" for the age and staleness before quoting the numbers as
     * current. An empty list means no history has ever been fetched.
     *
     * @return Per-pool pity status. The account identifier and the raw gacha URL are withheld.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getGachaPrediction(): List<GachaPredictionInfo> =
        withContext(Dispatchers.IO) {
            val entry = GachaHistoryStore.load(applicationContext) ?: return@withContext emptyList()
            val type = object : TypeToken<GachaData>() {}.type
            val data =
                GachaHistoryStore.gson.fromJson<GachaData>(entry.fullDataJson, type)
                    ?: return@withContext emptyList()
            val safeData = data.copy(predictions = data.predictions ?: emptyList())
            safeData.predictions.map { it.toGachaPredictionInfo() }
        }

    /**
     * Read lifetime totals across the whole stored gacha history.
     *
     * Complements "getGachaPrediction", which reports where each pool stands right
     * now: this reports what the account's full record amounts to — total pulls,
     * average pity, 50/50 win rate, and the character/weapon split.
     *
     * Both read the same file. The records are kept for a year but stop being a
     * current snapshot after 12 hours, so check isStale before describing any figure
     * as the player's present position. The account identifier and the raw gacha URL
     * are withheld.
     *
     * @return Lifetime aggregates, or zeroes when no history has ever been fetched.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getGachaStats(): GachaStatsInfo =
        withContext(Dispatchers.IO) {
            val entry =
                GachaHistoryStore.load(applicationContext)
                    ?: return@withContext GachaStatsResult.EMPTY.toInfo(ageHours = null, isStale = true)
            val type = object : TypeToken<GachaData>() {}.type
            val data =
                GachaHistoryStore.gson.fromJson<GachaData>(entry.fullDataJson, type)
                    ?: return@withContext GachaStatsResult.EMPTY.toInfo(ageHours = null, isStale = true)
            GachaStats.aggregate(data).toInfo(
                ageHours = GachaHistoryStore.ageHours(entry),
                isStale = GachaHistoryStore.isStale(entry),
            )
        }

    /**
     * Look up a CVar in the game's CVar database.
     *
     * Tells the caller whether the name is a real CVar the game registers, whether the
     * app monitors it for drift, what the game's default value is, which functional
     * category it belongs to, and whether it is one of the restricted CVars the game
     * mishandles.
     *
     * @param name CVar name as the engine spells it, for example "r.Shadow.MaxResolution". Case-insensitive.
     * @return The lookup result. An unrecognised name returns isKnown=false rather than throwing.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun lookupCvar(name: String): CvarInfo =
        withContext(Dispatchers.IO) {
            app.cvarDatabase.load()
            CvarInfo(
                name = name,
                isKnown = app.cvarDatabase.isKnown(name),
                isMonitored = app.cvarDatabase.isMonitored(name),
                gameDefault = app.cvarDatabase.gameDefault(name),
                category = app.cvarDatabase.categorize(name).displayName,
                isForbidden = ForbiddenCvars.isForbidden(name),
            )
        }

    /**
     * List configuration backups the app has stored.
     *
     * Read-only: this lists backups so the caller can decide which one to restore, but
     * does not restore anything. Restore overwrites the game's live configuration and
     * is deliberately not exposed to agents.
     *
     * @return Backups with their contained files, newest first. Empty when no backups exist.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun listBackups(): List<BackupInfo> =
        withContext(Dispatchers.IO) {
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val defaultBackupDir = applicationContext.filesDir.resolve("backups").absolutePath
            val backupDir = File(prefs.getString("backup_dir", defaultBackupDir) ?: defaultBackupDir)
            val publicDir =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "WuWaConfig",
                )
            BackupStore.listBackups(backupDir, publicDir).map { it.toBackupInfo() }
        }

    private companion object {
        /**
         * The most [LogInfo] can hold without a game log: hardware the platform reports
         * directly. Frame rate, thermal and memory-pressure fields stay at their defaults,
         * which is why callers must be told the score is hardware-only.
         */
        fun hardwareOnlyLogInfo(
            context: Context,
            socName: String,
        ): LogInfo {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            val metrics =
                DisplayMetrics().also {
                    @Suppress("DEPRECATION")
                    context.resources.displayMetrics
                }
            return LogInfo(
                gpu = socName,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                socName = socName,
                ramMb = (memoryInfo.totalMem / (1024L * 1024L)).toInt(),
                androidVersion = Build.VERSION.RELEASE,
                resolution = "${metrics.widthPixels}x${metrics.heightPixels}",
                isLowMem = memoryInfo.lowMemory,
            )
        }
    }
}
