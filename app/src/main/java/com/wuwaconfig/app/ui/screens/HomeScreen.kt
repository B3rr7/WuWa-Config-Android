package com.wuwaconfig.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.adb.PortScanner
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.*
import com.wuwaconfig.app.ui.theme.*

private data class PickedFile(
    val displayName: String,
    val targetName: String,
    val content: String,
)

private enum class CustomConfigState {
    IDLE,
    REVIEW,
}

private val TARGET_NAMES = listOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini", "Scalability.ini", "Hardware.ini")

private fun matchTarget(displayName: String): String? {
    val name = displayName.lowercase().replace(" ", "")
    return when {
        "engine" in name -> "Engine.ini"
        "deviceprofile" in name -> "DeviceProfiles.ini"
        "gameusersetting" in name -> "GameUserSettings.ini"
        "scalability" in name -> "Scalability.ini"
        "hardware" in name -> "Hardware.ini"
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToBackups: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToConfigGen: () -> Unit,
    onNavigateToPity: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToBattleStats: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToIniEditor: () -> Unit = {},
) {
    val backendStatus by viewModel.backendStatus.collectAsState()
    val backups by viewModel.backups.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()
    val deployRecords by viewModel.deployRecords.collectAsState()
    val deployHistoryEnabled by viewModel.deployHistoryEnabled.collectAsState()
    val customDeploySuccess by viewModel.customDeploySuccess.collectAsState()

    var customConfigState by remember { mutableStateOf(CustomConfigState.IDLE) }
    var pickedFiles by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var showCleanDialog by remember { mutableStateOf(false) }
    var showAdbDialog by remember { mutableStateOf(false) }
    var showBackupScopeDialog by remember { mutableStateOf(false) }
    var pendingApply by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var adbHost by remember { mutableStateOf(PortScanner.getDeviceIp()) }
    var adbPort by remember { mutableStateOf("") }

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                val matched =
                    uris.mapNotNull { uri ->
                        val name = viewModel.getFileName(uri) ?: return@mapNotNull null
                        val target = matchTarget(name) ?: return@mapNotNull null
                        val content = viewModel.readUriContent(uri).getOrNull() ?: return@mapNotNull null
                        PickedFile(displayName = name, targetName = target, content = content)
                    }
                if (matched.isNotEmpty()) {
                    pickedFiles = matched
                    customConfigState = CustomConfigState.REVIEW
                }
            }
        }

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            GlitchText(fontWeight = FontWeight.Bold)
                            Text(
                                "Config Toolkit",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        val infiniteTransition = rememberInfiniteTransition()
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(2000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart,
                                ),
                        )
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = NeonPurple,
                                modifier = Modifier.rotate(rotation),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = NeonPurple,
                        ),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                // --- Backend Status ---
                item {
                    GlassCard(accentColor = NeonPurple) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                "Wuthering Waves",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Manage configs, backups, and device tuned presets.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                item {
                    BackendStatusCard(
                        status = backendStatus,
                        onToggle = {
                            val next =
                                when (backendStatus.method) {
                                    AccessMethod.ADB -> AccessMethod.SHIZUKU
                                    AccessMethod.SHIZUKU -> AccessMethod.ROOT
                                    AccessMethod.ROOT -> AccessMethod.SAF
                                    AccessMethod.SAF -> AccessMethod.ADB
                                }
                            viewModel.switchTo(next)
                        },
                    )
                }
                item {
                    val safTreeLauncher =
                        rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenDocumentTree(),
                        ) { uri: Uri? ->
                            if (uri != null) viewModel.saveSafTreeUri(uri)
                        }

                    if (!backendStatus.connected) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(
                                onClick = { viewModel.connect() },
                                modifier = Modifier.weight(1f),
                                enabled = true,
                                accentColor = NeonCyan,
                                contentColor = Color.White,
                            ) { Text("Connect", fontWeight = FontWeight.Bold) }
                            when (backendStatus.method) {
                                AccessMethod.ADB ->
                                    GlassOutlinedButton(
                                        onClick = { showAdbDialog = true },
                                        modifier = Modifier.weight(1f),
                                        enabled = true,
                                        accentColor = NeonAmber,
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Manual", modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Manual")
                                    }
                                AccessMethod.SHIZUKU ->
                                    GlassOutlinedButton(
                                        onClick = { viewModel.requestShizukuPermission() },
                                        modifier = Modifier.weight(1f),
                                        enabled = true,
                                        accentColor = NeonAmber,
                                    ) {
                                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Permit")
                                    }
                                AccessMethod.SAF ->
                                    GlassOutlinedButton(
                                        onClick = { safTreeLauncher.launch(null) },
                                        modifier = Modifier.weight(1f),
                                        enabled = true,
                                        accentColor = NeonAmber,
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Pick Dir")
                                    }
                                AccessMethod.ROOT ->
                                    GlassOutlinedButton(
                                        onClick = { viewModel.connect() },
                                        modifier = Modifier.weight(1f),
                                        enabled = true,
                                        accentColor = NeonAmber,
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Test Root")
                                    }
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(
                                onClick = { viewModel.connect() },
                                modifier = Modifier.weight(1f),
                                enabled = true,
                                accentColor = NeonCyan,
                                contentColor = Color.White,
                            ) { Text("Reconnect", fontWeight = FontWeight.Bold) }
                            GlassOutlinedButton(
                                onClick = { viewModel.disconnect() },
                                modifier = Modifier.weight(1f),
                                enabled = true,
                                accentColor = NeonRed,
                            ) { Text("Disconnect", fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                // --- F2P Tips / Sponsor ---
                item {
                    val f2pTips =
                        remember {
                            listOf(
                                "Save your Astrites! Do your dailies & events.",
                                "Weapon banner pity carries over — plan your pulls.",
                                "Use your Waveplates daily — don't cap at 240.",
                                "Check the Tower of Adversity reset every 2 weeks.",
                                "Level one main DPS first before splitting resources.",
                                "Echo main stats matter more than set bonuses early on.",
                                "Don't skip exploration — those chests add up.",
                                "Join a union for extra rewards and support echoes.",
                                "Save your premium currency for limited banners only.",
                                "Talent materials have specific farm days — plan ahead.",
                                "Use the data bank to track which echoes you own.",
                                "Forgery challenges rotate daily — check what you need.",
                                "Your rover is actually viable — invest in it.",
                                "Don't pull on standard banner with Astrites.",
                                "Co-op lets you farm materials without spending waveplates.",
                                "Pincer Maneuver events give free 4-star weapons.",
                                "Level your weapon to max before switching characters.",
                                "The Crucible gives free battle pass exp every week.",
                                "Save your crystal solvents for double-drop events.",
                                "You can preview all echo skills in the data bank.",
                            )
                        }
                    GlassCard(accentColor = NeonAmber) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("F2P Tips", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                GlitchText(
                                    names = f2pTips,
                                    intervalMs = 15000L,
                                    fontWeight = FontWeight.Normal,
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                )
                            }
                        }
                    }
                }

                // --- Custom Config ---
                item {
                    GlassCard(accentColor = NeonCyan) {
                        GlassCardHeader("Custom Config", NeonCyan)
                        Spacer(Modifier.height(8.dp))
                        when (customConfigState) {
                            CustomConfigState.IDLE -> {
                                Text(
                                    "Select .ini files to replace. Files matching Engine, DeviceProfiles, GameUserSettings, Scalability, or Hardware will be backed up and applied.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                GlassButton(
                                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = backendStatus.connected,
                                    accentColor = NeonCyan,
                                    contentColor = Color.White,
                                ) { Text("Select Custom Configs", fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.height(8.dp))
                                GlassOutlinedButton(
                                    onClick = { showCleanDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = backendStatus.connected,
                                    accentColor = NeonRed,
                                ) {
                                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Clean Config Files", fontWeight = FontWeight.Bold)
                                }
                                if (!backendStatus.connected) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Connect to device first",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeonRed.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            CustomConfigState.REVIEW -> {
                                Text("Matched files:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                pickedFiles.forEach { f ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = NeonGreen,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                "→ ${f.targetName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = NeonCyan,
                                            )
                                            Text(
                                                "from ${f.displayName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            )
                                        }
                                    }
                                }
                                val missing = TARGET_NAMES.filter { t -> pickedFiles.none { it.targetName == t } }
                                if (missing.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Will skip: ${missing.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GlassButton(
                                        onClick = {
                                            val selectedTargets = pickedFiles.map { it.targetName }.toSet()
                                            if (selectedTargets.size < 5) {
                                                pendingApply = pickedFiles
                                                showBackupScopeDialog = true
                                            } else {
                                                val engine = pickedFiles.firstOrNull { it.targetName == "Engine.ini" }?.content
                                                val device = pickedFiles.firstOrNull { it.targetName == "DeviceProfiles.ini" }?.content
                                                val gus = pickedFiles.firstOrNull { it.targetName == "GameUserSettings.ini" }?.content
                                                val scalability = pickedFiles.firstOrNull { it.targetName == "Scalability.ini" }?.content
                                                val hardware = pickedFiles.firstOrNull { it.targetName == "Hardware.ini" }?.content
                                                viewModel.applyCustomFiles(engine, device, gus, scalability, hardware)
                                                customConfigState = CustomConfigState.IDLE
                                                pickedFiles = emptyList()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isApplying,
                                        accentColor = NeonPurple,
                                        contentColor = Color.White,
                                    ) { Text("Apply", fontWeight = FontWeight.Bold) }
                                    GlassOutlinedButton(
                                        onClick = {
                                            customConfigState = CustomConfigState.IDLE
                                            pickedFiles = emptyList()
                                        },
                                        modifier = Modifier.weight(1f),
                                        accentColor = NeonPink,
                                    ) { Text("Cancel") }
                                }
                            }
                        }
                    }
                }

                // --- Actions ---
                item {
                    GlassCard(accentColor = NeonPink) {
                        GlassCardHeader("Actions", NeonPink)
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ElevatedButton(
                                onClick = onNavigateToProfile,
                                modifier = Modifier.fillMaxWidth().height(84.dp),
                                enabled = true,
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.elevatedButtonColors(
                                        containerColor = NeonGreen.copy(alpha = 0.08f),
                                        contentColor = NeonGreen,
                                    ),
                                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 0.dp),
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "View",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                )
                            }
                            ElevatedButton(
                                onClick = onNavigateToBattleStats,
                                modifier = Modifier.fillMaxWidth().height(84.dp),
                                enabled = true,
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.elevatedButtonColors(
                                        containerColor = NeonRed.copy(alpha = 0.08f),
                                        contentColor = NeonRed,
                                    ),
                                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 0.dp),
                            ) {
                                Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Battle Stats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "Stats",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                )
                            }
                            ElevatedButton(
                                onClick = onNavigateToPity,
                                modifier = Modifier.fillMaxWidth().height(84.dp),
                                enabled = true,
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.elevatedButtonColors(
                                        containerColor = NeonPurple.copy(alpha = 0.08f),
                                        contentColor = NeonPurple,
                                    ),
                                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 0.dp),
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Pity Tracker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "Import",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                )
                            }
                            ElevatedButton(
                                onClick = onNavigateToBackups,
                                modifier = Modifier.fillMaxWidth().height(84.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.elevatedButtonColors(
                                        containerColor = NeonPink.copy(alpha = 0.08f),
                                        contentColor = NeonPink,
                                    ),
                                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 0.dp),
                            ) {
                                Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Backups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${backups.size} saved",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                )
                            }
                            ElevatedButton(
                                onClick = { viewModel.collectClientLog() },
                                modifier = Modifier.fillMaxWidth().height(84.dp),
                                enabled = backendStatus.connected && !isApplying,
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.elevatedButtonColors(
                                        containerColor = NeonGreen.copy(alpha = 0.08f),
                                        contentColor = NeonGreen,
                                    ),
                                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 0.dp),
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Collect Client.log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "Device log",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                )
                            }
                            ElevatedButton(
                                onClick = onNavigateToConfigGen,
                                modifier = Modifier.fillMaxWidth().height(84.dp),
                                enabled = true,
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.elevatedButtonColors(
                                        containerColor = NeonAmber.copy(alpha = 0.08f),
                                        contentColor = NeonAmber,
                                    ),
                                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 0.dp),
                            ) {
                                Icon(Icons.Default.Construction, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Config Generator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "Generate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                )
                            }
                            ElevatedButton(
                                onClick = onNavigateToIniEditor,
                                modifier = Modifier.fillMaxWidth().height(84.dp),
                                enabled = backendStatus.connected && !isApplying,
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                    ButtonDefaults.elevatedButtonColors(
                                        containerColor = NeonBlue.copy(alpha = 0.08f),
                                        contentColor = NeonBlue,
                                    ),
                                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 0.dp),
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("INI Editor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "Edit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                )
                            }
                            if (isApplying) {
                                GlassOutlinedButton(
                                    onClick = { viewModel.cancelOperation() },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = true,
                                    accentColor = NeonRed,
                                ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }

                // --- Deploy History ---
                if (deployHistoryEnabled && deployRecords.isNotEmpty()) {
                    val latest = deployRecords.first()
                    item {
                        GlassCard(
                            modifier = Modifier.clickable { onNavigateToHistory() },
                            accentColor = NeonBlue.copy(alpha = 0.7f),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(NeonBlue.copy(alpha = 0.8f)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Deploy History",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NeonBlue,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (latest.hasOutcome) "Compared" else "Tap to compare",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "View",
                                    tint = NeonBlue.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(
                                        "${latest.presetName.uppercase()} — ${java.text.SimpleDateFormat(
                                            "MMM d, HH:mm",
                                            java.util.Locale.US,
                                        ).format(java.util.Date(latest.timestamp))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        "${latest.totalCount} CVars · ${latest.acceptedCount}/${latest.totalCount} accepted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                                if (latest.hasOutcome) {
                                    val c = latest.comparison()
                                    val fpsText = c.fpsDelta?.let { f -> "${if (f >= 0) "+" else ""}${"%.1f".format(f)} FPS" } ?: ""
                                    Text(
                                        fpsText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if ((c.fpsDelta ?: 0f) >= 0) NeonGreen else NeonRed,
                                    )
                                } else {
                                    Text(
                                        "?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    )
                                }
                            }
                        }
                    }
                }

                item { RecentLogCard(onNavigateToLogs = onNavigateToLogs) }
            }
        }
    }

    LaunchedEffect(backendStatus.errorMessage) {
        if (backendStatus.method == AccessMethod.ADB &&
            !backendStatus.connected &&
            backendStatus.errorMessage.contains("ADB port not found", ignoreCase = true)
        ) {
            showAdbDialog = true
        }
    }

    if (showAdbDialog) {
        AlertDialog(
            onDismissRequest = { showAdbDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            icon = { Icon(Icons.Default.Adb, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp)) },
            title = { Text("Wireless Debugging", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter the IP:port from Developer Options > Wireless Debugging.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = adbHost,
                        onValueChange = { adbHost = it },
                        label = { Text("IP Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                focusedLabelColor = NeonCyan,
                            ),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adbPort,
                        onValueChange = { adbPort = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                focusedLabelColor = NeonCyan,
                            ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAdbDialog = false
                        viewModel.connectAdbManual(adbHost, adbPort)
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = NeonCyan.copy(alpha = 0.15f),
                            contentColor = NeonCyan,
                        ),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Connect", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAdbDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showCleanDialog) {
        val iniFiles = remember { listOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini", "Scalability.ini", "Hardware.ini") }
        val cleanSelection = remember { mutableStateMapOf(*iniFiles.map { it to true }.toTypedArray()) }
        AlertDialog(
            onDismissRequest = { showCleanDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            icon = { Icon(Icons.Default.CleaningServices, contentDescription = null, tint = NeonRed, modifier = Modifier.size(32.dp)) },
            title = { Text("Config Files", color = NeonRed, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Select files to process. Clean strips CVars (preserves [Core.System] paths). Delete removes files entirely.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    iniFiles.forEach { name ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { cleanSelection[name] = !(cleanSelection[name] ?: true) }.padding(vertical = 4.dp)) {
                            Checkbox(
                                checked = cleanSelection[name] ?: true,
                                onCheckedChange = { cleanSelection[name] = it },
                                colors = CheckboxDefaults.colors(checkedColor = NeonRed, uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showCleanDialog = false
                            val selected = cleanSelection.filterValues { it }.keys
                            if (selected.isNotEmpty()) viewModel.cleanConfigFiles()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.15f), contentColor = NeonRed),
                        shape = RoundedCornerShape(10.dp),
                        enabled = cleanSelection.any { it.value },
                    ) { Text("Clean", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = {
                            showCleanDialog = false
                            val selected = cleanSelection.filterValues { it }.keys
                            if (selected.isNotEmpty()) viewModel.deleteSelectedConfigFiles(selected)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.25f), contentColor = NeonRed),
                        shape = RoundedCornerShape(10.dp),
                        enabled = cleanSelection.any { it.value },
                    ) { Text("Delete", fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showBackupScopeDialog) {
        val selectedNames = pendingApply.map { it.targetName }
        AlertDialog(
            onDismissRequest = {
                showBackupScopeDialog = false
                pendingApply = emptyList()
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = NeonAmber,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Backup Scope", color = NeonAmber, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("You are about to overwrite: ${selectedNames.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Back up the other INI files too, or only the ones being overwritten?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val engine = pendingApply.firstOrNull { it.targetName == "Engine.ini" }?.content
                        val device = pendingApply.firstOrNull { it.targetName == "DeviceProfiles.ini" }?.content
                        val gus = pendingApply.firstOrNull { it.targetName == "GameUserSettings.ini" }?.content
                        val scalability = pendingApply.firstOrNull { it.targetName == "Scalability.ini" }?.content
                        val hardware = pendingApply.firstOrNull { it.targetName == "Hardware.ini" }?.content
                        viewModel.applyCustomFiles(engine, device, gus, scalability, hardware, backupAllInis = true)
                        showBackupScopeDialog = false
                        pendingApply = emptyList()
                        customConfigState = CustomConfigState.IDLE
                        pickedFiles = emptyList()
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = NeonAmber.copy(alpha = 0.15f),
                            contentColor = NeonAmber,
                        ),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Back Up All 5 INIs") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showBackupScopeDialog = false
                        pendingApply = emptyList()
                    }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val engine = pendingApply.firstOrNull { it.targetName == "Engine.ini" }?.content
                            val device = pendingApply.firstOrNull { it.targetName == "DeviceProfiles.ini" }?.content
                            val gus = pendingApply.firstOrNull { it.targetName == "GameUserSettings.ini" }?.content
                            val scalability = pendingApply.firstOrNull { it.targetName == "Scalability.ini" }?.content
                            val hardware = pendingApply.firstOrNull { it.targetName == "Hardware.ini" }?.content
                            viewModel.applyCustomFiles(engine, device, gus, scalability, hardware, backupAllInis = false)
                            showBackupScopeDialog = false
                            pendingApply = emptyList()
                            customConfigState = CustomConfigState.IDLE
                            pickedFiles = emptyList()
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = NeonPurple.copy(alpha = 0.15f),
                                contentColor = NeonPurple,
                            ),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Only Overwritten") }
                }
            },
        )
    }

    customDeploySuccess?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearCustomDeploySuccess() },
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
                    "✓ Files Deployed",
                    color = NeonGreen,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
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
                    onClick = { viewModel.clearCustomDeploySuccess() },
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

@Composable
private fun RecentLogCard(onNavigateToLogs: () -> Unit) {
    val logs = LogRepository.entries
    GlassCard(
        modifier = Modifier.clickable { onNavigateToLogs() },
        accentColor = NeonCyan,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonCyan.copy(alpha = 0.8f)),
            )
            Spacer(Modifier.width(8.dp))
            Text("Recent Log", style = MaterialTheme.typography.titleSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "${logs.size} entries",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View all",
                tint = NeonCyan.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (logs.isEmpty()) {
            Text(
                "No logs yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        } else {
            Column {
                logs.takeLast(5).forEach { log ->
                    val c =
                        when (log.level) {
                            LogLevel.SUCCESS -> NeonGreen
                            LogLevel.ERROR -> NeonRed
                            LogLevel.WARNING -> NeonAmber
                            LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    Text("[${log.timestamp}] ${log.message}", style = MaterialTheme.typography.bodySmall, color = c)
                }
            }
        }
    }
}
