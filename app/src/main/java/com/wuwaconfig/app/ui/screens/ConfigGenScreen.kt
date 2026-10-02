package com.wuwaconfig.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuwaconfig.app.WuWaConfigApp
import com.wuwaconfig.app.config.gameProfile
import com.wuwaconfig.app.model.GameMode
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.model.VerificationReport
import com.wuwaconfig.app.ui.DeployHistoryViewModel
import com.wuwaconfig.app.ui.LogInsightsViewModel
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.CancelledBanner
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GlassDialog
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GlassSwitch
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.components.formatRam
import com.wuwaconfig.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigGenScreen(
    viewModel: MainViewModel,
    deployHistoryViewModel: DeployHistoryViewModel,
    insightsViewModel: LogInsightsViewModel,
    onBack: () -> Unit,
    onNavigateToReviewTune: () -> Unit = {},
) {
    val backendStatus by deployHistoryViewModel.backendStatus.collectAsStateWithLifecycle()
    val isApplying by deployHistoryViewModel.isApplying.collectAsStateWithLifecycle()
    val operationCancelled by deployHistoryViewModel.operationCancelled.collectAsStateWithLifecycle()
    // Analysis progress lives on the insights VM; deploy verification progress lives on the
    // deploy VM. Only one is ever >0 at a time (DeviceOps serializes them), so pick the active one.
    val analysisProgress by insightsViewModel.readingProgress.collectAsStateWithLifecycle()
    val deployReadingProgress by deployHistoryViewModel.readingProgress.collectAsStateWithLifecycle()
    val readingProgress = if (analysisProgress > 0) analysisProgress else deployReadingProgress
    val logInfo by insightsViewModel.logAnalysis.collectAsStateWithLifecycle()
    val brain by insightsViewModel.brainRecommendation.collectAsStateWithLifecycle()
    val analysisFromCache by insightsViewModel.analysisFromCache.collectAsStateWithLifecycle()
    val analysisAgeHours by insightsViewModel.analysisAgeHours.collectAsStateWithLifecycle()
    val deployResult by deployHistoryViewModel.deployResult.collectAsStateWithLifecycle()
    val verificationReport by deployHistoryViewModel.verificationReport.collectAsStateWithLifecycle()
    val colorful by viewModel.colorfulUi.collectAsStateWithLifecycle()

    fun tint(color: Color): Color = if (colorful) color else NeonCyan

    // Gson.fromJson walks the class reflectively; doing it in a remember
    // initializer put that on the main thread during composition. Load it
    // off-main and push the values into the individual option states below.
    val savedOptions by produceState<GeneratorOptions?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { viewModel.loadGeneratorOptions() }
    }

    var selectedPreset by rememberSaveable { mutableStateOf(brain?.preset ?: "balanced") }
    var fps by rememberSaveable { mutableStateOf(60) }
    var unlock120 by rememberSaveable { mutableStateOf(false) }
    var unlockUltra by rememberSaveable { mutableStateOf(true) }
    var vsync by rememberSaveable { mutableStateOf(true) }
    var cooling by rememberSaveable { mutableStateOf(true) }
    var vulkan by rememberSaveable { mutableStateOf(false) }
    var hzb by rememberSaveable { mutableStateOf(false) }
    var fog by rememberSaveable { mutableStateOf(false) }
    var ca by rememberSaveable { mutableStateOf(true) }
    var disableOutline by rememberSaveable { mutableStateOf(false) }
    var disableRadialBlur by rememberSaveable { mutableStateOf(false) }
    var disableBloom by rememberSaveable { mutableStateOf(false) }
    var disableAutoExposure by rememberSaveable { mutableStateOf(false) }
    var disableSSR by rememberSaveable { mutableStateOf(false) }
    var userChangedPreset by rememberSaveable { mutableStateOf(false) }

    var generateEngine by rememberSaveable { mutableStateOf(true) }
    var generateDeviceProfiles by rememberSaveable { mutableStateOf(true) }
    var generateGameUserSettings by rememberSaveable { mutableStateOf(true) }
    var generateScalability by rememberSaveable { mutableStateOf(false) }
    var generateHardware by rememberSaveable { mutableStateOf(false) }

    // Seeded from the app-wide preference, and every change is written back to it.
    // This was pure local state, so "Allow restricted CVars" reset to ON on every
    // visit AND nothing downstream could observe the choice: the pref read by
    // ConfigManager.defaultAllowRestrictedCvars() had no writer at all
    // (WuWaConfigApp.setAllowRestrictedCvarsEnabled was dead code), so the deploy
    // paths fell back to their own defaults.
    var allowRestrictedCvars by
        rememberSaveable { mutableStateOf(WuWaConfigApp.instance.allowRestrictedCvarsEnabled.value) }
    var useAdvancedGen by rememberSaveable { mutableStateOf(false) }
    var optimizeWithCvarDb by rememberSaveable { mutableStateOf(true) }
    var disableAutoAdjust by rememberSaveable { mutableStateOf(false) }
    var enableGSR by rememberSaveable { mutableStateOf(false) }
    var experimentalCvars by rememberSaveable { mutableStateOf(false) }

    var gameMode by rememberSaveable { mutableStateOf(GameMode.Overworld) }
    // Apply the persisted options once, as soon as the off-main load resolves.
    // The individual states keep their rememberSaveable initialisers, so this
    // effect only re-runs on a fresh entry into the screen.
    LaunchedEffect(savedOptions) {
        val o = savedOptions ?: return@LaunchedEffect
        fps = o.fps
        unlock120 = o.unlock120
        unlockUltra = o.unlockUltra
        vsync = o.vsync
        cooling = o.cool
        vulkan = o.vulkan
        hzb = o.hzb
        fog = o.fog
        ca = o.ca
        disableOutline = o.disableOutline
        disableRadialBlur = o.disableRadialBlur
        disableBloom = o.disableBloom
        disableAutoExposure = o.disableAutoExposure
        disableSSR = o.disableSSR
        generateEngine = o.generateEngine
        generateDeviceProfiles = o.generateDeviceProfiles
        generateGameUserSettings = o.generateGameUserSettings
        generateScalability = o.generateScalability
        generateHardware = o.generateHardware
        allowRestrictedCvars = o.allowRestrictedCvars
        useAdvancedGen = o.useAdvancedGen
        optimizeWithCvarDb = o.optimizeWithCvarDb
        disableAutoAdjust = o.disableAutoAdjust
        enableGSR = o.enableGSR
        experimentalCvars = o.experimentalCvars
        gameMode = o.mode
    }

    var showDeployDialog by remember { mutableStateOf(false) }
    var deployDialogResult by remember { mutableStateOf<DeployHistoryViewModel.DeployResult?>(null) }
    var deployHashSyncMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }

    val logPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                // ContentResolver reads are blocking IO — keep them off the main thread.
                scope.launch {
                    val result = deployHistoryViewModel.readUriBytes(uri)
                    if (result.isSuccess) {
                        insightsViewModel.analyzeClientLogBytes(result.getOrThrow(), allowRestrictedCvars)
                    } else {
                        deployHistoryViewModel.addLog("FAILED: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        }

    LaunchedEffect(Unit) {
        insightsViewModel.restoreAnalysisFromCache()
    }

    LaunchedEffect(brain) {
        if (!userChangedPreset) {
            brain?.preset?.let { selectedPreset = it }
        }
    }

    LaunchedEffect(deployResult) {
        deployResult?.let {
            deployDialogResult = it
            // Only a SUCCESS has a hash-sync result to show. Reading it for a
            // failure produced the "KuroConfigMonitor hash refreshed" line on a
            // dialog whose body said the deploy had failed.
            deployHashSyncMessage =
                if (it is DeployHistoryViewModel.DeployResult.Success) {
                    deployHistoryViewModel.deployHashSync.value ?: ""
                } else {
                    ""
                }
            showDeployDialog = true
            deployHistoryViewModel.clearDeployResult()
        }
    }

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
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
                    accentColor = NeonCyan,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                        }
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding =
                    PaddingValues(
                        top = 8.dp + padding.calculateTopPadding(),
                        bottom = 80.dp + padding.calculateBottomPadding(),
                    ),
            ) {
                item(key = "analysis") {
                    AnalysisPanel(
                        isConnected = backendStatus.connected,
                        isApplying = isApplying,
                        readingProgress = readingProgress,
                        logInfo = logInfo,
                        brain = brain,
                        analysisFromCache = analysisFromCache,
                        analysisAgeHours = analysisAgeHours,
                        allowRestrictedCvars = allowRestrictedCvars,
                        onAnalyzeDevice = { insightsViewModel.analyzeClientLog(allowRestrictedCvars) },
                        onImportLog = { logPickerLauncher.launch(arrayOf("*/*")) },
                    )
                }

                verificationReport?.let { report ->
                    item(key = "verification") {
                        VerificationBadge(report)
                    }
                }

                item(key = "preset") {
                    GlassCard(accentColor = tint(NeonPurple)) {
                        GlassCardHeader("Preset", tint(NeonPurple))
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "potato" to "Dead low-end",
                                "endurance" to "Long sessions on mid-tier",
                                "performance" to "Stability first",
                                "competitive" to "Max clarity, no clutter",
                                "balanced" to "Daily default",
                                "high" to "Sharper visuals",
                                "ultra" to "Flagship devices",
                                "cinematic" to "Above ultra, flagship only",
                            ).forEach { (preset, description) ->
                                PresetRow(
                                    name = preset,
                                    description = description,
                                    selected = selectedPreset == preset,
                                    accent = tint(presetColor(preset)),
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
                    GlassCard(accentColor = tint(NeonAmber)) {
                        GlassCardHeader("Tuning", tint(NeonAmber))
                        Spacer(Modifier.height(8.dp))
                        GeneratorSwitch("Advanced per-device tuning", useAdvancedGen, onCheckedChange = { useAdvancedGen = it }, accentColor = tint(NeonAmber))
                        GeneratorSwitch("CVar optimization (comment out defaults)", optimizeWithCvarDb, onCheckedChange = { optimizeWithCvarDb = it }, accentColor = tint(NeonGreen))
                    }
                }

                item(key = "frame_target") {
                    GlassCard(accentColor = tint(NeonBlue)) {
                        GlassCardHeader("Frame Target", tint(NeonBlue))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            gameProfile().supportedFrameCaps.forEach { value ->
                                val chip = tint(fpsColor(value))
                                FilterChip(
                                    selected = fps == value,
                                    onClick = { fps = value },
                                    label = { Text("$value FPS", maxLines = 1) },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = chip.copy(alpha = 0.20f),
                                            selectedLabelColor = chip,
                                        ),
                                )
                            }
                        }
                    }
                }

                item(key = "options") {
                    GlassCard(accentColor = tint(NeonGreen)) {
                        GlassCardHeader("Options", tint(NeonGreen))
                        Spacer(Modifier.height(8.dp))
                        GeneratorSwitch("120 FPS unlock", unlock120, onCheckedChange = { unlock120 = it }, accentColor = tint(NeonGreen))
                        GeneratorSwitch("Ultra quality unlock", unlockUltra, onCheckedChange = { unlockUltra = it }, accentColor = tint(NeonPurple))
                        GeneratorSwitch("VSync", vsync, onCheckedChange = { vsync = it }, accentColor = tint(NeonBlue))
                        GeneratorSwitch("Auto cooling", cooling, onCheckedChange = { cooling = it }, accentColor = tint(NeonCyan))
                        GeneratorSwitch("Force Vulkan safety CVars", vulkan, onCheckedChange = { vulkan = it }, accentColor = tint(NeonBlue))
                        GeneratorSwitch("HZB occlusion", hzb, onCheckedChange = { hzb = it }, accentColor = tint(NeonCyan))
                        GeneratorSwitch("Disable fog", fog, onCheckedChange = { fog = it }, accentColor = tint(NeonBlue))
                        GeneratorSwitch("Disable chromatic aberration", ca, onCheckedChange = { ca = it }, accentColor = tint(NeonPink))
                        GeneratorSwitch("Disable toon outlines", disableOutline, onCheckedChange = { disableOutline = it }, accentColor = tint(NeonPurple))
                        GeneratorSwitch("Disable radial blur", disableRadialBlur, onCheckedChange = { disableRadialBlur = it }, accentColor = tint(NeonCyan))
                        GeneratorSwitch("Disable bloom", disableBloom, onCheckedChange = { disableBloom = it }, accentColor = tint(NeonAmber))
                        GeneratorSwitch("Disable auto exposure", disableAutoExposure, onCheckedChange = { disableAutoExposure = it }, accentColor = tint(NeonGreen))
                        GeneratorSwitch("Disable SSR/reflections", disableSSR, onCheckedChange = { disableSSR = it }, accentColor = tint(NeonBlue))
                        GeneratorSwitch(
                            "Allow restricted CVars",
                            allowRestrictedCvars,
                            onCheckedChange = {
                                allowRestrictedCvars = it
                                WuWaConfigApp.instance.setAllowRestrictedCvarsEnabled(it)
                            },
                            accentColor = tint(NeonRed),
                        )
                        GeneratorSwitch("Disable auto quality adjust", disableAutoAdjust, onCheckedChange = { disableAutoAdjust = it }, accentColor = tint(NeonPink))
                        GeneratorSwitch("GSR upscaling (low-end)", enableGSR, onCheckedChange = { enableGSR = it }, accentColor = tint(NeonGreen))
                        GeneratorSwitch("Experimental CVars", experimentalCvars, onCheckedChange = { experimentalCvars = it }, accentColor = tint(NeonRed))
                    }
                }

                item(key = "game_mode") {
                    GlassCard(accentColor = tint(NeonBlue)) {
                        GlassCardHeader("Game Mode", tint(NeonBlue))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            val modeColors = listOf(NeonGreen, NeonBlue, NeonPurple, NeonPink, NeonAmber)
                            GameMode.entries.forEachIndexed { index, mode ->
                                val chip = tint(modeColors[index % modeColors.size])
                                FilterChip(
                                    selected = gameMode == mode,
                                    onClick = { gameMode = mode },
                                    label = { Text(mode.label, maxLines = 1) },
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = chip.copy(alpha = 0.20f),
                                            selectedLabelColor = chip,
                                        ),
                                )
                            }
                        }
                    }
                }

                item(key = "file_toggles") {
                    GlassCard(accentColor = tint(NeonCyan)) {
                        GlassCardHeader("Files to Generate", tint(NeonCyan))
                        Spacer(Modifier.height(8.dp))
                        GeneratorSwitch("Engine.ini", generateEngine, onCheckedChange = { generateEngine = it }, accentColor = tint(NeonCyan))
                        GeneratorSwitch("DeviceProfiles.ini", generateDeviceProfiles, onCheckedChange = { generateDeviceProfiles = it }, accentColor = tint(NeonPurple))
                        GeneratorSwitch("GameUserSettings.ini", generateGameUserSettings, onCheckedChange = { generateGameUserSettings = it }, accentColor = tint(NeonGreen))
                        GeneratorSwitch("Scalability.ini", generateScalability, onCheckedChange = { generateScalability = it }, accentColor = tint(NeonBlue))
                        GeneratorSwitch("Hardware.ini", generateHardware, onCheckedChange = { generateHardware = it }, accentColor = tint(NeonPink))
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
                                onClick = onGenerate@{
                                    if (isGenerating) return@onGenerate
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
                                            // A cached analysis is up to 24h stale, so it must not
                                            // silently re-enable advanced tuning the user
                                            // switched off, and its CVars must not be merged
                                            // into a fresh Engine.ini. Both were gated on
                                            // `logInfo != null`, which the cache satisfies.
                                            importFromLog = !analysisFromCache && !userChangedPreset && selectedPreset == brain?.preset,
                                            useAdvancedGen = useAdvancedGen || (!analysisFromCache && logInfo != null && !userChangedPreset && selectedPreset == brain?.preset),
                                            optimizeWithCvarDb = optimizeWithCvarDb,
                                            disableAutoAdjust = disableAutoAdjust,
                                            enableGSR = enableGSR,
                                            experimentalCvars = experimentalCvars,
                                        )
                                    viewModel.saveGeneratorOptions(opts)
                                    isGenerating = true
                                    scope.launch {
                                        try {
                                            val generated =
                                                withContext(Dispatchers.Default) {
                                                    viewModel.configGenerator.generate(selectedPreset, opts, logInfo = logInfo ?: com.wuwaconfig.app.model.LogInfo())
                                                }
                                            val payload =
                                                com.wuwaconfig.app.ui.MainViewModel.ReviewTunePayload(
                                                    engine = generated.engine,
                                                    deviceProfiles = generated.deviceProfiles,
                                                    gameUserSettings = generated.gameUserSettings,
                                                    scalability = generated.scalability,
                                                    hardware = generated.hardware,
                                                )
                                            // Editing the generated config invalidates the
                                            // last deploy's verification: that badge counted
                                            // CVars in the file as it was PUSHED, and this
                                            // payload is the starting point for changing it.
                                            deployHistoryViewModel.clearVerificationReport()
                                            viewModel.openReviewTune(payload, opts)
                                            onNavigateToReviewTune()
                                        } finally {
                                            isGenerating = false
                                        }
                                    }
                                },
                                enabled = !isApplying && !isGenerating,
                                accentColor = NeonCyan,
                                contentColor = Color.White,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generate", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (isApplying) {
                            GlassOutlinedButton(
                                onClick = { deployHistoryViewModel.cancelOperation() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = true,
                                accentColor = NeonRed,
                            ) { Text("Cancel Operation", fontWeight = FontWeight.Bold) }
                        } else if (operationCancelled) {
                            // Without this a cancelled deploy is indistinguishable
                            // from one that never started: the buttons just
                            // re-enable and nothing says why.
                            CancelledBanner()
                        }
                    }
                }
            }
        }
    }

    // Success and failure are rendered from a typed result. A failure must never
    // be able to reuse the success chrome (green accent, check mark, "Config
    // Deployed" title, hash-refreshed panel) — that combination is what told the
    // user a deploy had worked while its own body said it had not.
    val deployDialogSucceeded = deployDialogResult is DeployHistoryViewModel.DeployResult.Success
    val deployDialogAccent = if (deployDialogSucceeded) NeonGreen else NeonRed
    if (showDeployDialog) {
        GlassDialog(
            onDismissRequest = { showDeployDialog = false },
            accentColor = deployDialogAccent,
            icon = {
                Icon(
                    if (deployDialogSucceeded) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = deployDialogAccent,
                    modifier = Modifier.size(48.dp),
                )
            },
            title = {
                Text(
                    if (deployDialogSucceeded) "✓ Config Deployed" else "✗ Deploy Failed",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        deployDialogResult?.message.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (deployDialogSucceeded) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeonGreen.copy(alpha = 0.10f))
                                    .border(1.dp, NeonGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeonGreen,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Hash sync",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreen,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    deployHashSyncMessage.ifBlank { "KuroConfigMonitor hash refreshed." },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "The device config was not changed. Nothing was written and the hash file was left alone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                GlassButton(
                    onClick = { showDeployDialog = false },
                    accentColor = NeonGreen,
                    contentColor = Color.Black,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            },
        )
    }
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
    accent: Color,
    onClick: () -> Unit,
) {
    val labelColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = labelColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name.uppercase(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = labelColor)
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
    accentColor: Color = NeonCyan,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                // Toggling the row (not just the switch) plus merged semantics so
                // TalkBack announces "Advanced per-device tuning, switch, on"
                // instead of an unlabelled clickable.
                .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
                .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 0.3.sp,
            color =
                if (checked) {
                    accentColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                },
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(12.dp))
        GlassSwitch(checked = checked, onCheckedChange = onCheckedChange, accentColor = accentColor)
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
    analysisFromCache: Boolean,
    analysisAgeHours: Long?,
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
                        logInfo != null ->
                            buildString {
                                if (analysisFromCache) {
                                    append("Cached")
                                    analysisAgeHours?.let { append(" ${it}h ago") }
                                    append(" — ")
                                } else {
                                    append("Loaded — ")
                                }
                                append(logInfo.gpu ?: "?")
                                append(" • ")
                                append(logInfo.ramMb?.let { formatRam(it) } ?: "?")
                            }
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
                val glitchColors = remember { listOf(NeonRed, NeonAmber, NeonGreen, NeonPurple, NeonCyan, NeonPink) }
                val random = remember { Random }
                var colorIndex by remember { mutableStateOf(0) }
                var glitchX by remember { mutableStateOf(0f) }
                var glitchY by remember { mutableStateOf(0f) }
                LaunchedEffect(readingProgress) {
                    while (isActive) {
                        colorIndex = (colorIndex + 1) % glitchColors.size
                        glitchX = random.nextFloat() * 6f - 3f
                        glitchY = random.nextFloat() * 3f - 1.5f
                        delay(60 + random.nextLong(100))
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
                    DetailRow("RAM", info.ramMb?.let { formatRam(it) } ?: "-")
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

private fun fpsColor(fps: Int): Color =
    when (fps) {
        30 -> NeonRed
        45 -> NeonAmber
        60 -> NeonGreen
        90 -> NeonBlue
        120 -> NeonPurple
        else -> NeonCyan
    }

private fun presetColor(preset: String): Color =
    when (preset) {
        "potato" -> Color(0xFF8B4513)
        "endurance" -> NeonPink
        "performance" -> NeonRed
        "competitive" -> NeonBlue
        "balanced" -> NeonAmber
        "high" -> NeonGreen
        "ultra" -> NeonPurple
        "cinematic" -> NeonCyan
        else -> NeonCyan
    }
