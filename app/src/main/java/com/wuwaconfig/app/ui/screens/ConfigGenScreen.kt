package com.wuwaconfig.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import com.wuwaconfig.app.config.ConfigGenerator
import com.wuwaconfig.app.model.CvarEntry
import com.wuwaconfig.app.model.GameMode
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigGenScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val backendStatus by viewModel.backendStatus.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()
    val readingProgress by viewModel.readingProgress.collectAsState()
    val logInfo by viewModel.logAnalysis.collectAsState()
    val brain by viewModel.brainRecommendation.collectAsState()
    val deployResult by viewModel.deployResult.collectAsState()

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

    var gameMode by remember { mutableStateOf(GameMode.Overworld) }
    var showReview by remember { mutableStateOf(false) }
    var reviewEngineText by remember { mutableStateOf("") }
    var reviewDeviceProfilesText by remember { mutableStateOf("") }
    var reviewGameUserSettingsText by remember { mutableStateOf("") }
    var tunerActive by remember { mutableStateOf(false) }
    var tunerProgress by remember { mutableStateOf("") }

    val logPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(brain) {
        if (!userChangedPreset) {
            brain?.preset?.let { selectedPreset = it }
        }
    }

    LaunchedEffect(deployResult) {
        deployResult?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
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
                            Text("WuWaP42 presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = NeonCyan
                    )
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
            ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                item(key = "analysis") {
                    AnalysisPanel(
                        isConnected = backendStatus.connected,
                        isApplying = isApplying,
                        readingProgress = readingProgress,
                        logInfo = logInfo,
                        brain = brain,
                        onAnalyzeDevice = { viewModel.analyzeClientLog() },
                        onImportLog = { logPickerLauncher.launch(arrayOf("*/*")) }
                    )
                }

                item(key = "preset") {
                    GlassCard(accentColor = NeonPurple) {
                        GlassCardHeader("Preset", NeonPurple)
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "performance" to "Stability first",
                                "balanced" to "Daily default",
                                "high" to "Sharper visuals",
                                "ultra" to "Flagship devices"
                            ).forEach { (preset, description) ->
                                PresetRow(
                                    name = preset,
                                    description = description,
                                    selected = selectedPreset == preset,
                                    onClick = { selectedPreset = preset; userChangedPreset = true }
                                )
                            }
                        }
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
                                    label = { Text("${value} FPS") },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonBlue.copy(alpha = 0.18f),
                                        selectedLabelColor = NeonBlue
                                    )
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
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonBlue.copy(alpha = 0.18f),
                                        selectedLabelColor = NeonBlue
                                    )
                                )
                            }
                        }
                    }
                }

                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassOutlinedButton(
                                onClick = onBack,
                                accentColor = NeonPink,
                                modifier = Modifier.weight(1f)
                            ) { Text("Back") }
                            GlassButton(
                                onClick = {
                                    val opts = GeneratorOptions(
                                        fps = fps,
                                        unlock120 = unlock120,
                                        unlockUltra = unlockUltra,
                                        vsync = vsync,
                                        cool = cooling,
                                        vulkan = vulkan,
                                        hzb = hzb,
                                        fog = fog,
                                        ca = ca,
                                        disableOutline = disableOutline,
                                        disableRadialBlur = disableRadialBlur,
                                        disableBloom = disableBloom,
                                        disableAutoExposure = disableAutoExposure,
                                        disableSSR = disableSSR,
                                        mode = gameMode
                                    )
                                    val generated = ConfigGenerator.generate(selectedPreset, opts)
                                    reviewEngineText = generated.engine
                                    reviewDeviceProfilesText = generated.deviceProfiles
                                    reviewGameUserSettingsText = generated.gameUserSettings
                                    viewModel.deployGeneratedConfigs(generated, opts)
                                    showReview = true
                                },
                                enabled = backendStatus.connected && !isApplying,
                                accentColor = NeonCyan,
                                contentColor = Color.White,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Deploy", fontWeight = FontWeight.Bold)
                            }
                        }
                        if (reviewEngineText.isNotEmpty()) {
                            GlassOutlinedButton(
                                onClick = { showReview = true },
                                enabled = !isApplying,
                                accentColor = NeonAmber,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Review & Tune Config", fontWeight = FontWeight.Bold) }
                        }
                        if (tunerActive) {
                            GlassOutlinedButton(
                                onClick = { viewModel.cancelOperation() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = true,
                                accentColor = NeonRed
                            ) { Text(tunerProgress, fontWeight = FontWeight.Bold) }
                        } else {
                            GlassOutlinedButton(
                                onClick = {
                                    tunerActive = true
                                    scope.launch {
                                        var currentPreset = selectedPreset
                                        var currentOpts = GeneratorOptions(
                                            fps = fps, unlock120 = unlock120, unlockUltra = unlockUltra,
                                            vsync = vsync, cool = cooling, vulkan = vulkan, hzb = hzb,
                                            fog = fog, ca = ca, disableOutline = disableOutline,
                                            disableRadialBlur = disableRadialBlur, disableBloom = disableBloom,
                                            disableAutoExposure = disableAutoExposure, disableSSR = disableSSR,
                                            mode = gameMode
                                        )
                                        for (round in 1..5) {
                                            tunerProgress = "Round $round: deploying ${currentPreset}..."
                                            val generated = ConfigGenerator.generate(currentPreset, currentOpts)
                                            viewModel.deployGeneratedConfigs(generated, currentOpts)
                                            delay(3000)
                                            tunerProgress = "Round $round: capturing FPS..."
                                            val result = BenchmarkTuner.captureFps()
                                            if (result.isSuccess) {
                                                val r = result.getOrThrow()
                                                tunerProgress = "Round $round: ${r.avgFps.toInt()} FPS (target ${fps})"
                                                delay(2000)
                                                if (r.avgFps >= fps) { tunerProgress = "Stable at ${r.avgFps.toInt()} FPS!"; break }
                                                currentPreset = BenchmarkTuner.pickPresetForFps(currentPreset, r.avgFps, fps)
                                                currentOpts = BenchmarkTuner.adjustOptionsForFps(currentOpts, r.avgFps, fps)
                                            } else {
                                                tunerProgress = "FPS capture failed: ${result.exceptionOrNull()?.message}"
                                                break
                                            }
                                        }
                                        tunerActive = false
                                        tunerProgress = ""
                                    }
                                },
                                enabled = backendStatus.connected && !isApplying,
                                accentColor = NeonRed,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Auto-Tune", fontWeight = FontWeight.Bold) }
                        }
                        if (isApplying) {
                            GlassOutlinedButton(
                                onClick = { viewModel.cancelOperation() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = true,
                                accentColor = NeonRed
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
            onDismiss = { showReview = false },
            onRedeploy = { newEngine, newDevice, newSettings ->
                val overrides = ConfigGenerator.parseCvarEntries(newEngine)
                    .filter { it.isOverridden }
                    .associate { it.key to it.value }
                val opts = GeneratorOptions(
                    fps = fps, unlock120 = unlock120, unlockUltra = unlockUltra,
                    vsync = vsync, cool = cooling, vulkan = vulkan, hzb = hzb,
                    fog = fog, ca = ca, disableOutline = disableOutline,
                    disableRadialBlur = disableRadialBlur, disableBloom = disableBloom,
                    disableAutoExposure = disableAutoExposure, disableSSR = disableSSR,
                    mode = gameMode, cvarOverrides = overrides
                )
                val generated = ConfigGenerator.generate(selectedPreset, opts)
                reviewEngineText = newEngine
                reviewDeviceProfilesText = newDevice
                reviewGameUserSettingsText = newSettings
                viewModel.deployGeneratedConfigs(
                    com.wuwaconfig.app.model.GeneratedIni(engine = newEngine, deviceProfiles = newDevice, gameUserSettings = newSettings),
                    opts
                )
            }
        )
    }
}

@Composable
private fun IniReviewTab(
    label: String,
    entries: List<CvarEntry>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    var edited by remember(entries) { mutableStateOf(entries.map { it.copy() }) }
    var filter by remember { mutableStateOf("") }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Filter...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                modifier = Modifier.weight(1f).height(44.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent.copy(alpha = 0.4f))
            )
            Text(
                "${edited.count { it.isOverridden }} edited",
                style = MaterialTheme.typography.labelSmall,
                color = if (edited.any { it.isOverridden }) NeonAmber else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
            val displayEntries = if (filter.isBlank()) edited
                else edited.filter { it.key.contains(filter, ignoreCase = true) || it.value.contains(filter, ignoreCase = true) }
            itemsIndexed(displayEntries) { i, entry ->
                val realIdx = edited.indexOfFirst { it.key == entry.key && it.category == entry.category }
                val isOverridden = realIdx >= 0 && edited[realIdx].isOverridden
                var editing by remember { mutableStateOf(false) }
                var editVal by remember(entry.value) { mutableStateOf(entry.value) }
                Surface(
                    onClick = { editing = true; editVal = edited[realIdx].value },
                    color = if (isOverridden) accent.copy(alpha = 0.06f) else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    if (editing) {
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = editVal,
                                onValueChange = { editVal = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f).height(40.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = NeonCyan.copy(alpha = 0.4f))
                            )
                            IconButton(onClick = {
                                edited = edited.toMutableList().also { list ->
                                    list[realIdx] = list[realIdx].copy(value = editVal, isOverridden = editVal != list[realIdx].originalValue)
                                }
                                editing = false
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Check, contentDescription = "Set", tint = NeonGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                entry.key,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isOverridden) accent else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "= ${entry.value}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOverridden) accent else NeonGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IniReviewDialog(
    engineText: String,
    deviceProfilesText: String,
    gameUserSettingsText: String,
    onDismiss: () -> Unit,
    onRedeploy: (engine: String, deviceProfiles: String, gameUserSettings: String) -> Unit
) {
    val engineEntries = remember(engineText) { ConfigGenerator.parseCvarEntries(engineText) }
    val dpEntries = remember(deviceProfilesText) { ConfigGenerator.parseDeviceProfileEntries(deviceProfilesText) }
    val gusEntries = remember(gameUserSettingsText) { ConfigGenerator.parseGameUserSettingsEntries(gameUserSettingsText) }

    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Engine.ini (${engineEntries.size})", "DeviceProfiles.ini (${dpEntries.size})", "GameUserSettings.ini (${gusEntries.size})")
    val accents = listOf(NeonCyan, NeonPurple, NeonGreen)

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
                    containerColor = Color.Transparent
                ) {
                    tabs.forEachIndexed { i, label ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = {
                                Text(label, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            selectedContentColor = accents[i],
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            when (tab) {
                0 -> IniReviewTab("Engine", engineEntries, NeonCyan)
                1 -> IniReviewTab("DeviceProfiles", dpEntries, NeonPurple)
                2 -> IniReviewTab("GameUserSettings", gusEntries, NeonGreen)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Close") }
                Button(onClick = { onRedeploy(engineText, deviceProfilesText, gameUserSettingsText) }) {
                    Text("Redeploy", fontWeight = FontWeight.Bold)
                }
            }
        }
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
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PresetRow(
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = if (selected) NeonPurple else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) NeonPurple.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
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
private fun GeneratorSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(68.dp))
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
    onAnalyzeDevice: () -> Unit,
    onImportLog: () -> Unit
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
                    color = when {
                        isApplying -> MaterialTheme.colorScheme.onSurfaceVariant
                        logInfo != null -> NeonGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
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
                    "${readingProgress}%",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black, letterSpacing = 4.sp
                    ),
                    color = glitchColors[colorIndex],
                    modifier = Modifier.offset(x = glitchX.dp, y = glitchY.dp)
                )
            }
        }

        if (logInfo != null && !isApplying) {
            val info = logInfo
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                DetailRow("Device", info.deviceModel ?: info.cpuName ?: "-")
                DetailRow("GPU", info.gpu ?: "-")
                DetailRow("API", info.gameApi ?: info.api ?: "-")
                DetailRow("Android", info.androidVersion?.let { "Android $it" } ?: "-")
                DetailRow("RAM", info.ramMb?.let { "$it MB" } ?: "-")
            }

            val issues = mutableListOf<Pair<String, Int>>()
            if (info.thermalEvents > 0) issues.add("Thermal" to info.thermalEvents)
            if (info.dropFrames > 0) issues.add("Drops" to info.dropFrames)
            if (info.forbiddenCvars > 0) issues.add("CVars" to info.forbiddenCvars)
            if (info.textureErrors > 0) issues.add("TexErr" to info.textureErrors)
            if (info.gpuOom > 0) issues.add("OOM" to info.gpuOom)
            if (info.networkErrors > 0) issues.add("Network" to info.networkErrors)
            if (info.isLowMem == true) issues.add("LowMem" to 1)

            if (issues.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    issues.forEach { (label, count) ->
                        IssueBadge(label, count, when (label) {
                            "OOM", "LowMem" -> NeonRed
                            "CVars" -> if (count > 0) NeonRed else NeonGreen
                            "Network" -> NeonAmber
                            else -> if (count > 10) NeonPink else NeonAmber
                        })
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
                    Text(rec.preset.uppercase(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = NeonGreen)
                    Text(" (score: ${rec.score})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                rec.signals.takeIf { it.isNotEmpty() }?.let { sigs ->
                    Spacer(Modifier.height(2.dp))
                    Text(sigs.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                rec.warnings.takeIf { it.isNotEmpty() }?.let { warnings ->
                    Spacer(Modifier.height(4.dp))
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
                accentColor = NeonCyan, contentColor = Color.White, modifier = Modifier.weight(1f)
            ) { LogActionContent("Device Log") }
            GlassOutlinedButton(
                onClick = onImportLog,
                enabled = !isApplying, accentColor = NeonAmber, modifier = Modifier.weight(1f)
            ) { LogActionContent("Import Log") }
        }
    }
}

@Composable
private fun IssueBadge(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" $count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}
