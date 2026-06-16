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
import com.wuwaconfig.app.config.ConfigGenerator
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigGenScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val backendStatus by viewModel.backendStatus.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()
    val logInfo by viewModel.logAnalysis.collectAsState()
    val brain by viewModel.brainRecommendation.collectAsState()

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

    LaunchedEffect(brain) {
        brain?.preset?.let { selectedPreset = it }
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
            containerColor = Color.Transparent
            ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
            ) {
                item(key = "analysis") {
                    GlassCard(accentColor = if (backendStatus.connected) NeonGreen else NeonAmber) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Device Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        isApplying -> "Analyzing..."
                                        logInfo != null -> "Loaded — ${logInfo!!.gpu ?: "?"} • ${logInfo!!.ramMb?.let { "$it MB" } ?: "?"}"
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
                        }

                        if (logInfo != null && !isApplying) {
                            val info = logInfo!!

                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                DetailRow("Device", info.deviceModel ?: info.cpuName ?: "-")
                                DetailRow("GPU", info.gpu ?: "-")
                                DetailRow("API", info.api ?: "-")
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
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                onClick = { viewModel.analyzeClientLog() },
                                enabled = backendStatus.connected && !isApplying,
                                accentColor = NeonCyan,
                                contentColor = Color.White,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Device Log", fontWeight = FontWeight.Bold)
                            }
                            GlassOutlinedButton(
                                onClick = { logPickerLauncher.launch(arrayOf("*/*")) },
                                enabled = !isApplying,
                                accentColor = NeonAmber,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Import Log", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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
                                    onClick = { selectedPreset = preset }
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
                    }
                }

                item(key = "actions") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassOutlinedButton(
                            onClick = onBack,
                            accentColor = NeonPink,
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                        GlassButton(
                            onClick = {
                                val generated = ConfigGenerator.generate(
                                    selectedPreset,
                                    GeneratorOptions(
                                        fps = fps,
                                        unlock120 = unlock120,
                                        unlockUltra = unlockUltra,
                                        vsync = vsync,
                                        cool = cooling,
                                        vulkan = vulkan,
                                        hzb = hzb,
                                        fog = fog,
                                        ca = ca
                                    )
                                )
                                viewModel.deployGeneratedConfigs(generated)
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
                }
            }
        }
    }
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

@Composable
private fun IssueBadge(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" $count", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}
