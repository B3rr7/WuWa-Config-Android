package com.wuwaconfig.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuwaconfig.app.config.BenchmarkTuner
import com.wuwaconfig.app.config.RoundResult
import com.wuwaconfig.app.config.TunerStage
import com.wuwaconfig.app.config.TunerState
import com.wuwaconfig.app.model.GameMode
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.model.LogAnalysisStore
import com.wuwaconfig.app.model.VerificationReport
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigGenScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val backendStatus by viewModel.backendStatus.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()
    val readingProgress by viewModel.readingProgress.collectAsState()
    val logInfo by viewModel.logAnalysis.collectAsState()
    val brain by viewModel.brainRecommendation.collectAsState()
    val deployResult by viewModel.deployResult.collectAsState()
    val verificationReport by viewModel.verificationReport.collectAsState()

    var selectedPreset by remember { mutableStateOf(brain?.preset ?: "balanced") }
    var fps by remember { mutableStateOf(60) }
    var unlock120 by remember { mutableStateOf(false) }
    var unlockUltra by remember { mutableStateOf(true) }
    var vsync by remember { mutableStateOf(true) }
    var cooling by remember { mutableStateOf(true) }
    var vulkan by remember { mutableStateOf(false) }
    var hzb by remember { mutableStateOf(false) }
    var fog by remember { mutableStateOf(false) }
    var ca by remember { mutableStateOf(true) }
    var disableOutline by remember { mutableStateOf(false) }
    var disableRadialBlur by remember { mutableStateOf(false) }
    var disableBloom by remember { mutableStateOf(false) }
    var disableAutoExposure by remember { mutableStateOf(false) }
    var disableSSR by remember { mutableStateOf(false) }
    var userChangedPreset by remember { mutableStateOf(false) }

    var generateEngine by remember { mutableStateOf(true) }
    var generateDeviceProfiles by remember { mutableStateOf(true) }
    var generateGameUserSettings by remember { mutableStateOf(true) }
    var generateScalability by remember { mutableStateOf(false) }
    var generateHardware by remember { mutableStateOf(false) }

    var allowRestrictedCvars by remember { mutableStateOf(true) }
    var useAdvancedGen by remember { mutableStateOf(false) }
    var optimizeWithCvarDb by remember { mutableStateOf(true) }

    var gameMode by remember { mutableStateOf(GameMode.Overworld) }
    var showReview by remember { mutableStateOf(false) }
    var reviewEngineText by remember { mutableStateOf("") }
    var reviewDeviceProfilesText by remember { mutableStateOf("") }
    var reviewGameUserSettingsText by remember { mutableStateOf("") }
    var reviewScalabilityText by remember { mutableStateOf("") }
    var reviewHardwareText by remember { mutableStateOf("") }
    var tunerState by remember { mutableStateOf(TunerState()) }
    var showGoPlayDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showDeployDialog by remember { mutableStateOf(false) }
    var deployDialogMessage by remember { mutableStateOf("") }

    val logPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                val result = viewModel.readUriBytes(uri)
                if (result.isSuccess) {
                    viewModel.analyzeClientLogBytes(result.getOrThrow())
                } else {
                    viewModel.addLog("FAILED: ${result.exceptionOrNull()?.message}")
                }
            }
        }

    val scope = rememberCoroutineScope()

    fun runDeployAndWait() {
        scope.launch {
            val preset = tunerState.preset
            val opts = tunerState.options

            tunerState = tunerState.copy(stage = TunerStage.DEPLOYING)
            BenchmarkTuner.saveState(tunerState)

            val generated = viewModel.configGenerator.generate(preset, opts, logInfo = logInfo ?: com.wuwaconfig.app.model.LogInfo())
            viewModel.deployGeneratedConfigs(generated, opts)
            var waitMs = 0
            while (viewModel.isApplying.value && waitMs < 30000) {
                delay(200)
                waitMs += 200
            }
            if (viewModel.isApplying.value) {
                tunerState = tunerState.copy(stage = TunerStage.COMPLETE, error = "Deploy timed out")
                BenchmarkTuner.saveState(tunerState)
                showResultDialog = true
                return@launch
            }
            tunerState = tunerState.copy(stage = TunerStage.WAITING_FOR_PLAY)
            BenchmarkTuner.saveState(tunerState)
            showGoPlayDialog = true
        }
    }

    fun captureAndAnalyze() {
        scope.launch {
            tunerState = tunerState.copy(stage = TunerStage.CAPTURING)
            BenchmarkTuner.saveState(tunerState)

            val round = tunerState.round
            val currentPreset = tunerState.preset
            val targetFps = tunerState.targetFps

            val logcatResult = viewModel.executeShellCommand("logcat -d -v brief -t 500")
            val result =
                if (logcatResult.isSuccess) {
                    BenchmarkTuner.parseFpsLogcat(logcatResult.getOrThrow())
                } else {
                    Result.failure(Exception("logcat via backend failed: ${logcatResult.exceptionOrNull()?.message}"))
                }

            if (result.isSuccess) {
                val r = result.getOrThrow()
                val newResults = tunerState.results + RoundResult(round, currentPreset, r.avgFps, r.minFps, r.stabilityPct)
                if (r.avgFps >= targetFps || round >= 5) {
                    tunerState =
                        TunerState(
                            stage = TunerStage.COMPLETE, round = round, preset = currentPreset,
                            options = tunerState.options, targetFps = targetFps,
                            results = newResults, finalPreset = currentPreset,
                        )
                    BenchmarkTuner.saveState(tunerState)
                    showResultDialog = true
                    return@launch
                }
                val nextPreset = BenchmarkTuner.pickPresetForFps(currentPreset, r.avgFps, targetFps)
                val nextOpts = BenchmarkTuner.adjustOptionsForFps(tunerState.options, r.avgFps, targetFps)
                tunerState =
                    TunerState(
                        stage = TunerStage.DEPLOYING, round = round + 1, preset = nextPreset,
                        options = nextOpts, targetFps = targetFps, results = newResults,
                    )
                BenchmarkTuner.saveState(tunerState)
                runDeployAndWait()
            } else {
                tunerState = tunerState.copy(stage = TunerStage.COMPLETE, error = result.exceptionOrNull()?.message)
                BenchmarkTuner.saveState(tunerState)
                showResultDialog = true
            }
        }
    }

    fun startTuner() {
        val opts =
            GeneratorOptions(
                fps = fps, unlock120 = unlock120, unlockUltra = unlockUltra,
                vsync = vsync, cool = cooling, vulkan = vulkan, hzb = hzb,
                fog = fog, ca = ca, disableOutline = disableOutline,
                disableRadialBlur = disableRadialBlur, disableBloom = disableBloom,
                disableAutoExposure = disableAutoExposure, disableSSR = disableSSR,
                mode = gameMode,
                generateEngine = generateEngine, generateDeviceProfiles = generateDeviceProfiles,
                generateGameUserSettings = generateGameUserSettings, generateScalability = generateScalability,
                generateHardware = generateHardware, allowRestrictedCvars = allowRestrictedCvars,
                importFromLog = false, useAdvancedGen = useAdvancedGen,
                optimizeWithCvarDb = optimizeWithCvarDb,
            )
        tunerState =
            TunerState(
                stage = TunerStage.DEPLOYING, round = 1, preset = selectedPreset,
                options = opts, targetFps = fps,
            )
        BenchmarkTuner.saveState(tunerState)
        runDeployAndWait()
    }

    LaunchedEffect(Unit) {
        val saved = BenchmarkTuner.loadState()
        if (saved != null && saved.stage != TunerStage.IDLE) {
            tunerState = saved
            when (saved.stage) {
                TunerStage.WAITING_FOR_PLAY -> showGoPlayDialog = true
                TunerStage.COMPLETE -> showResultDialog = true
                TunerStage.DEPLOYING, TunerStage.CAPTURING -> {
                    tunerState = saved.copy(stage = TunerStage.WAITING_FOR_PLAY)
                    BenchmarkTuner.saveState(tunerState)
                    showGoPlayDialog = true
                }
                else -> {}
            }
        }
        viewModel.restoreAnalysisFromCache()
    }

    LaunchedEffect(brain) {
        if (!userChangedPreset) {
            brain?.preset?.let { selectedPreset = it }
        }
    }

    LaunchedEffect(deployResult) {
        deployResult?.let {
            deployDialogMessage = it
            showDeployDialog = true
            viewModel.clearDeployResult()
        }
    }

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Config Generator", fontWeight = FontWeight.Bold)
                            Text(
                                "WuWaConfig presets",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = NeonCyan,
                        ),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            ) {
                item(key = "analysis") {
                    AnalysisPanel(
                        isConnected = backendStatus.connected,
                        isApplying = isApplying,
                        readingProgress = readingProgress,
                        logInfo = logInfo,
                        brain = brain,
                        allowRestrictedCvars = allowRestrictedCvars,
                        onAnalyzeDevice = { viewModel.analyzeClientLog() },
                        onImportLog = { logPickerLauncher.launch(arrayOf("*/*")) },
                    )
                }

                verificationReport?.let { report ->
                    item(key = "verification") {
                        VerificationBadge(report)
                    }
                }

                item(key = "preset") {
                    GlassCard(accentColor = NeonPurple) {
                        GlassCardHeader("Preset", NeonPurple)
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "potato" to "Dead low-end",
                                "performance" to "Stability first",
                                "balanced" to "Daily default",
                                "high" to "Sharper visuals",
                                "ultra" to "Flagship devices",
                            ).forEach { (preset, description) ->
                                PresetRow(
                                    name = preset,
                                    description = description,
                                    selected = selectedPreset == preset,
                                    onClick = {
                                        selectedPreset = preset
                                        userChangedPreset = true
                                    },
                                )
                            }
                        }
                    }
                }

                item(key = "tuning") {
                    GlassCard(accentColor = NeonAmber) {
                        GlassCardHeader("Tuning", NeonAmber)
                        Spacer(Modifier.height(8.dp))
                        GeneratorSwitch("Advanced per-device tuning", useAdvancedGen) { useAdvancedGen = it }
                        GeneratorSwitch("CVar optimization (comment out defaults)", optimizeWithCvarDb) { optimizeWithCvarDb = it }
                    }
                }

                item(key = "frame_target") {
                    GlassCard(accentColor = NeonBlue) {
                        GlassCardHeader("Frame Target", NeonBlue)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(30, 45, 60, 90, 120).forEach { value ->
                                FilterChip(
                                    selected = fps == value,
                                    onClick = { fps = value },
                                    label = { Text("$value FPS") },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonBlue.copy(alpha = 0.18f),
                                            selectedLabelColor = NeonBlue,
                                        ),
                                )
                            }
                        }
                    }
                }

                item(key = "options") {
                    GlassCard(accentColor = NeonGreen) {
                        GlassCardHeader("Options", NeonGreen)
                        Spacer(Modifier.height(8.dp))
                        GeneratorSwitch("120 FPS unlock", unlock120) { unlock120 = it }
                        GeneratorSwitch("Ultra quality unlock", unlockUltra) { unlockUltra = it }
                        GeneratorSwitch("VSync", vsync) { vsync = it }
                        GeneratorSwitch("Auto cooling", cooling) { cooling = it }
                        GeneratorSwitch("Force Vulkan safety CVars", vulkan) { vulkan = it }
                        GeneratorSwitch("HZB occlusion", hzb) { hzb = it }
                        GeneratorSwitch("Disable fog", fog) { fog = it }
                        GeneratorSwitch("Disable chromatic aberration", ca) { ca = it }
                        GeneratorSwitch("Disable toon outlines", disableOutline) { disableOutline = it }
                        GeneratorSwitch("Disable radial blur", disableRadialBlur) { disableRadialBlur = it }
                        GeneratorSwitch("Disable bloom", disableBloom) { disableBloom = it }
                        GeneratorSwitch("Disable auto exposure", disableAutoExposure) { disableAutoExposure = it }
                        GeneratorSwitch("Disable SSR/reflections", disableSSR) { disableSSR = it }
                        GeneratorSwitch("Allow restricted CVars", allowRestrictedCvars) { allowRestrictedCvars = it }
                    }
                }

                item(key = "game_mode") {
                    GlassCard(accentColor = NeonBlue) {
                        GlassCardHeader("Game Mode", NeonBlue)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            GameMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = gameMode == mode,
                                    onClick = { gameMode = mode },
                                    label = { Text(mode.label, maxLines = 1) },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonBlue.copy(alpha = 0.18f),
                                            selectedLabelColor = NeonBlue,
                                        ),
                                )
                            }
                        }
                    }
                }

                item(key = "file_toggles") {
                    GlassCard(accentColor = NeonCyan) {
                        GlassCardHeader("Files to Generate", NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        GeneratorSwitch("Engine.ini", generateEngine) { generateEngine = it }
                        GeneratorSwitch("DeviceProfiles.ini", generateDeviceProfiles) { generateDeviceProfiles = it }
                        GeneratorSwitch("GameUserSettings.ini", generateGameUserSettings) { generateGameUserSettings = it }
                        GeneratorSwitch("Scalability.ini", generateScalability) { generateScalability = it }
                        GeneratorSwitch("Hardware.ini", generateHardware) { generateHardware = it }
                    }
                }

                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassOutlinedButton(
                                onClick = onBack,
                                accentColor = NeonPink,
                                modifier = Modifier.weight(1f),
                            ) { Text("Back") }
                            GlassButton(
                                onClick = {
                                    val opts =
                                        GeneratorOptions(
                                            fps = fps, unlock120 = unlock120, unlockUltra = unlockUltra,
                                            vsync = vsync, cool = cooling, vulkan = vulkan, hzb = hzb,
                                            fog = fog, ca = ca, disableOutline = disableOutline,
                                            disableRadialBlur = disableRadialBlur, disableBloom = disableBloom,
                                            disableAutoExposure = disableAutoExposure, disableSSR = disableSSR,
                                            mode = gameMode,
                                            generateEngine = generateEngine, generateDeviceProfiles = generateDeviceProfiles,
                                            generateGameUserSettings = generateGameUserSettings, generateScalability = generateScalability,
                                            generateHardware = generateHardware, allowRestrictedCvars = allowRestrictedCvars,
                                            importFromLog = !userChangedPreset && selectedPreset == brain?.preset,
                                            useAdvancedGen = useAdvancedGen,
                                            optimizeWithCvarDb = optimizeWithCvarDb,
                                        )
                                    val generated = viewModel.configGenerator.generate(selectedPreset, opts, logInfo = logInfo ?: com.wuwaconfig.app.model.LogInfo())
                                    reviewEngineText = generated.engine
                                    reviewDeviceProfilesText = generated.deviceProfiles
                                    reviewGameUserSettingsText = generated.gameUserSettings
                                    reviewScalabilityText = generated.scalability
                                    reviewHardwareText = generated.hardware
                                    showReview = true
                                },
                                enabled = !isApplying,
                                accentColor = NeonCyan,
                                contentColor = Color.White,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generate", fontWeight = FontWeight.Bold)
                            }
                        }
                        when (tunerState.stage) {
                            TunerStage.DEPLOYING, TunerStage.CAPTURING -> {
                                GlassOutlinedButton(
                                    onClick = {
                                        viewModel.cancelOperation()
                                        BenchmarkTuner.clearState()
                                        tunerState = TunerState()
                                        showGoPlayDialog = false
                                        showResultDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = true,
                                    accentColor = NeonRed,
                                ) {
                                    val label =
                                        when (tunerState.stage) {
                                            TunerStage.DEPLOYING -> "Round ${tunerState.round}: deploying ${tunerState.preset}..."
                                            TunerStage.CAPTURING -> "Round ${tunerState.round}: capturing FPS..."
                                            else -> "Cancel Tuner"
                                        }
                                    Text(label, fontWeight = FontWeight.Bold)
                                }
                            }
                            TunerStage.WAITING_FOR_PLAY, TunerStage.COMPLETE -> {}
                            TunerStage.IDLE -> {
                                GlassOutlinedButton(
                                    onClick = { startTuner() },
                                    enabled = backendStatus.connected && !isApplying,
                                    accentColor = NeonRed,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Auto-Tune", fontWeight = FontWeight.Bold) }
                            }
                        }
                        if (isApplying) {
                            GlassOutlinedButton(
                                onClick = { viewModel.cancelOperation() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = true,
                                accentColor = NeonRed,
                            ) { Text("Cancel Operation", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }

    if (showReview && reviewEngineText.isNotEmpty()) {
        IniReviewDialog(
            engineText = reviewEngineText,
            deviceProfilesText = reviewDeviceProfilesText,
            gameUserSettingsText = reviewGameUserSettingsText,
            scalabilityText = reviewScalabilityText,
            hardwareText = reviewHardwareText,
            onDismiss = { showReview = false },
            onRedeploy = { newEngine, newDevice, newSettings, newScalability, newHardware ->
                val overrides =
                    viewModel.configGenerator.parseCvarEntries(newEngine)
                        .filter { it.isOverridden }
                        .associate { it.key to it.value }
                val opts =
                    GeneratorOptions(
                        fps = fps, unlock120 = unlock120, unlockUltra = unlockUltra,
                        vsync = vsync, cool = cooling, vulkan = vulkan, hzb = hzb,
                        fog = fog, ca = ca, disableOutline = disableOutline,
                        disableRadialBlur = disableRadialBlur, disableBloom = disableBloom,
                        disableAutoExposure = disableAutoExposure, disableSSR = disableSSR,
                        mode = gameMode, cvarOverrides = overrides,
                        generateEngine = generateEngine, generateDeviceProfiles = generateDeviceProfiles,
                        generateGameUserSettings = generateGameUserSettings, generateScalability = generateScalability,
                        generateHardware = generateHardware, allowRestrictedCvars = allowRestrictedCvars,
                        importFromLog = false,
                        useAdvancedGen = useAdvancedGen,
                        optimizeWithCvarDb = optimizeWithCvarDb,
                    )
                reviewEngineText = newEngine
                reviewDeviceProfilesText = newDevice
                reviewGameUserSettingsText = newSettings
                reviewScalabilityText = newScalability
                reviewHardwareText = newHardware
                viewModel.deployGeneratedConfigs(
                    com.wuwaconfig.app.model.GeneratedIni(
                        engine = newEngine,
                        deviceProfiles = newDevice,
                        gameUserSettings = newSettings,
                        scalability = newScalability,
                        hardware = newHardware,
                    ),
                    opts,
                )
            },
        )
    }

    if (showGoPlayDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Round ${tunerState.round}: Play the Game") },
            text = {
                Column {
                    Text("Deployed ${tunerState.preset} preset (${tunerState.options.fps} FPS target).")
                    Spacer(Modifier.height(12.dp))
                    Text("Go play the game for about 30 seconds, then come back and tap Continue.")
                    Spacer(Modifier.height(8.dp))
                    Text("The tuner will read FPS data from the game's log output.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showGoPlayDialog = false
                    captureAndAnalyze()
                }) {
                    Text("Continue", color = NeonGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.cancelOperation()
                    BenchmarkTuner.clearState()
                    tunerState = TunerState()
                    showGoPlayDialog = false
                }) { Text("Cancel Tuner", color = NeonRed) }
            },
        )
    }

    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(if (tunerState.error != null) "Auto-Tune Failed" else "Auto-Tune Complete") },
            text = {
                Column {
                    if (tunerState.error != null) {
                        Text("Error: ${tunerState.error}", color = NeonRed)
                    } else {
                        tunerState.finalPreset?.let { Text("Best preset: $it", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        Text("Results (${tunerState.results.size} round(s)):", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        tunerState.results.forEach { r ->
                            Text("Round ${r.round} (${r.preset}): ${r.avgFps.toInt()} FPS, ${r.stabilityPct.toInt()}% stable")
                        }
                        if (tunerState.results.isEmpty()) {
                            Text("Target FPS reached on first deployment.")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tunerState.stage == TunerStage.COMPLETE) {
                        selectedPreset = tunerState.finalPreset ?: selectedPreset
                    }
                    BenchmarkTuner.clearState()
                    tunerState = TunerState()
                    showResultDialog = false
                }) { Text("Dismiss", color = NeonCyan) }
            },
        )
    }

    if (showDeployDialog) {
        AlertDialog(
            onDismissRequest = { showDeployDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(48.dp),
                )
            },
            title = {
                Text(
                    "✓ Config Deployed",
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        deployDialogMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NeonGreen.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Config files written to device and KuroConfigMonitor hash refreshed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonGreen.copy(alpha = 0.8f),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDeployDialog = false },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = NeonGreen.copy(alpha = 0.15f),
                            contentColor = NeonGreen,
                        ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) { Text("OK", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IniReviewDialog(
    engineText: String,
    deviceProfilesText: String,
    gameUserSettingsText: String,
    scalabilityText: String = "",
    hardwareText: String = "",
    onDismiss: () -> Unit,
    onRedeploy: (engine: String, deviceProfiles: String, gameUserSettings: String, scalability: String, hardware: String) -> Unit,
) {
    var engineSrc by remember(engineText) { mutableStateOf(engineText) }
    var dpSrc by remember(deviceProfilesText) { mutableStateOf(deviceProfilesText) }
    var gusSrc by remember(gameUserSettingsText) { mutableStateOf(gameUserSettingsText) }
    var slSrc by remember(scalabilityText) { mutableStateOf(scalabilityText) }
    var hwSrc by remember(hardwareText) { mutableStateOf(hardwareText) }

    var tab by remember { mutableStateOf(0) }
    val tabs = mutableListOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini")
    if (scalabilityText.isNotBlank()) tabs.add("Scalability.ini")
    if (hardwareText.isNotBlank()) tabs.add("Hardware.ini")
    val accents = listOf(NeonCyan, NeonPurple, NeonGreen, NeonAmber, NeonCyan)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Review & Tune Config", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    edgePadding = 0.dp,
                    divider = {},
                    containerColor = Color.Transparent,
                ) {
                    tabs.forEachIndexed { i, label ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            selectedContentColor = accents[i],
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        text = {
            val src =
                when (tab) {
                    0 -> engineSrc
                    1 -> dpSrc
                    2 -> gusSrc
                    tabs.indexOf("Scalability.ini") -> slSrc
                    else -> hwSrc
                }
            val onEdit: (String) -> Unit =
                when (tab) {
                    0 -> { v -> engineSrc = v }
                    1 -> { v -> dpSrc = v }
                    2 -> { v -> gusSrc = v }
                    tabs.indexOf("Scalability.ini") -> { v -> slSrc = v }
                    else -> { v -> hwSrc = v }
                }
            val accent = accents[tab.coerceAtMost(accents.lastIndex)]
            OutlinedTextField(
                value = src,
                onValueChange = onEdit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                textStyle =
                    LocalTextStyle.current.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent.copy(alpha = 0.4f),
                        unfocusedBorderColor = accent.copy(alpha = 0.15f),
                    ),
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Close") }
                Button(onClick = { onRedeploy(engineSrc, dpSrc, gusSrc, slSrc, hwSrc) }) {
                    Text("Deploy", fontWeight = FontWeight.Bold)
                }
            }
        },
    )
}

@Composable
private fun LogActionContent(label: String) {
    val icon = if (label == "Device Log") Icons.Default.Analytics else Icons.Default.FileUpload
    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PresetRow(
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (selected) NeonPurple else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) NeonPurple.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name.uppercase(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accent)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GeneratorSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisPanel(
    isConnected: Boolean,
    isApplying: Boolean,
    readingProgress: Int,
    logInfo: com.wuwaconfig.app.model.LogInfo?,
    brain: com.wuwaconfig.app.config.BrainRecommendation?,
    allowRestrictedCvars: Boolean = true,
    onAnalyzeDevice: () -> Unit,
    onImportLog: () -> Unit,
) {
    GlassCard(accentColor = if (isConnected) NeonGreen else NeonAmber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Analytics, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Device Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        isApplying -> {
                            val pct = readingProgress
                            if (pct > 0) "Reading log ($pct%)..." else "Analyzing..."
                        }
                        logInfo != null -> "Loaded — ${logInfo.gpu ?: "?"} • ${logInfo.ramMb?.let { "$it MB" } ?: "?"}"
                        else -> "Analyze from device or import an encrypted Client.log file."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        when {
                            isApplying -> MaterialTheme.colorScheme.onSurfaceVariant
                            logInfo != null -> NeonGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            if (isApplying && readingProgress > 0) {
                Spacer(Modifier.height(8.dp))
                val glitchColors = listOf(NeonRed, NeonAmber, NeonGreen, NeonPurple, NeonCyan, NeonPink)
                var colorIndex by remember { mutableStateOf(0) }
                var glitchX by remember { mutableStateOf(0f) }
                var glitchY by remember { mutableStateOf(0f) }
                LaunchedEffect(readingProgress) {
                    while (isActive) {
                        colorIndex = (colorIndex + 1) % glitchColors.size
                        glitchX = Random.nextFloat() * 6f - 3f
                        glitchY = Random.nextFloat() * 3f - 1.5f
                        delay(60 + Random.nextLong(100))
                    }
                }
                Text(
                    "$readingProgress%",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 4.sp),
                    color = glitchColors[colorIndex],
                    modifier = Modifier.offset(x = glitchX.dp, y = glitchY.dp),
                )
            }
        }

        if (logInfo != null && !isApplying) {
            val info = logInfo
            val hasData =
                info.gpu != null || info.deviceModel != null || info.cpuName != null ||
                    info.ramMb != null || info.androidVersion != null

            Spacer(Modifier.height(12.dp))
            if (hasData) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    DetailRow("Device", info.deviceModel ?: info.cpuName ?: "-")
                    DetailRow("GPU", info.gpu ?: "-")
                    DetailRow("API", info.gameApi ?: info.api ?: "-")
                    DetailRow("Android", info.androidVersion?.let { "Android $it" } ?: "-")
                    DetailRow("RAM", info.ramMb?.let { "$it MB" } ?: "-")
                }
            } else {
                Text(
                    "No device data could be extracted from the log.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Text(
                    "The log may be from a very short session, or the format may have changed in a game update.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }

            val issues = mutableListOf<Pair<String, Int>>()
            if (info.thermalEvents > 0) issues.add("Thermal" to info.thermalEvents)
            if (info.dropFrames > 0) issues.add("Frame Drops" to info.dropFrames)
            if (info.forbiddenCvars > 0 && !allowRestrictedCvars) issues.add("Restricted CVars" to info.forbiddenCvars)
            if (info.textureErrors > 0) issues.add("Tex Errors" to info.textureErrors)
            if (info.gpuOom > 0) issues.add("GPU OOM" to info.gpuOom)
            if (info.networkErrors > 0) issues.add("Network" to info.networkErrors)
            if (info.isLowMem == true) issues.add("Low Memory" to 1)
            if (issues.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    issues.forEach { (label, count) ->
                        IssueBadge(
                            label,
                            count,
                            when (label) {
                                "GPU OOM", "Low Memory" -> NeonRed
                                "Restricted CVars" -> if (count > 0) NeonRed else NeonGreen
                                "Network" -> NeonAmber
                                else -> if (count > 10) NeonPink else NeonAmber
                            },
                        )
                    }
                }
            }

            brain?.let { rec ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(NeonCyan.copy(alpha = 0.12f)))
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Smart Brain", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = NeonPurple)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        rec.preset.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = presetColor(rec.preset),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "(${rec.score}/100)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))

                val barColor = presetColor(rec.preset)
                val barWidth = (rec.score.coerceIn(0, 100) / 100f)
                Box(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(barColor.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(barWidth).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(barColor),
                    )
                }
                Spacer(Modifier.height(6.dp))

                rec.signals.takeIf { it.isNotEmpty() }?.let { sigs ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        sigs.forEach { sig ->
                            val isPositive = sig.contains("+")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isPositive) "+" else "−",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) NeonGreen else NeonRed,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    sig.removePrefix("+").removePrefix("-"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                rec.warnings.takeIf { it.isNotEmpty() }?.let { warnings ->
                    Spacer(Modifier.height(6.dp))
                    warnings.forEach { warning ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("⚠", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(4.dp))
                            Text(warning, style = MaterialTheme.typography.bodySmall, color = NeonAmber)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                onClick = onAnalyzeDevice,
                enabled = isConnected && !isApplying,
                accentColor = NeonCyan,
                contentColor = Color.White,
                modifier = Modifier.weight(1f),
            ) { LogActionContent("Device Log") }
            GlassOutlinedButton(
                onClick = onImportLog,
                enabled = !isApplying,
                accentColor = NeonAmber,
                modifier = Modifier.weight(1f),
            ) { LogActionContent("Import Log") }
        }
    }
}

@Composable
private fun IssueBadge(
    label: String,
    count: Int,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" ×$count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun VerificationBadge(report: VerificationReport) {
    val color =
        if (report.acceptedRatio >= 0.8f) {
            NeonGreen
        } else if (report.acceptedRatio >= 0.5f) {
            NeonAmber
        } else {
            NeonRed
        }
    GlassCard(accentColor = color) {
        GlassCardHeader("Deploy Verification", color)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${report.recognizedCount}/${report.totalCount} CVars accepted by engine",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        if (report.cvarDetails.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val redundant = report.redundantCount
                val unknown = report.unknownCount
                val monitored = report.monitoredCount
                if (redundant > 0) {
                    TagChip("$redundant redundant", NeonGreen.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                }
                if (unknown > 0) {
                    TagChip("$unknown unknown", NeonAmber.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                }
                if (monitored > 0) {
                    TagChip("$monitored monitored", NeonBlue.copy(alpha = 0.6f))
                }
            }
        }
        if (report.rejected.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            val sample = report.rejected.take(8).joinToString(", ")
            Text(
                "Rejected (${report.rejected.size}): $sample",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (report.rejected.size > 8) {
                Text(
                    "...and ${report.rejected.size - 8} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun TagChip(
    text: String,
    color: Color,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

private fun presetColor(preset: String): Color =
    when (preset) {
        "potato" -> Color(0xFF8B4513)
        "performance" -> NeonRed
        "balanced" -> NeonAmber
        "high" -> NeonGreen
        "ultra" -> NeonPurple
        else -> NeonCyan
    }
