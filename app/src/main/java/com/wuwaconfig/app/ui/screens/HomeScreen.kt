package com.wuwaconfig.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.adb.PortScanner
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.*
import com.wuwaconfig.app.ui.theme.*

private data class PickedFile(
    val displayName: String,
    val targetName: String,
    val content: String
)

private enum class CustomConfigState {
    IDLE, REVIEW
}

private val TARGET_NAMES = listOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini")

private fun matchTarget(displayName: String): String? {
    val name = displayName.lowercase().replace(" ", "")
    return when {
        "engine" in name -> "Engine.ini"
        "deviceprofile" in name -> "DeviceProfiles.ini"
        "gameusersetting" in name -> "GameUserSettings.ini"
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToBackups: () -> Unit,
    onNavigateToConfigGen: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val backendStatus by viewModel.backendStatus.collectAsState()
    val backups by viewModel.backups.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()

    var customConfigState by remember { mutableStateOf(CustomConfigState.IDLE) }
    var pickedFiles by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAdbDialog by remember { mutableStateOf(false) }
    var adbHost by remember { mutableStateOf(PortScanner.getDeviceIp()) }
    var adbPort by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val matched = uris.mapNotNull { uri ->
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
                            Text("WuWaP42", fontWeight = FontWeight.Bold)
                            Text("Config Toolkit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NeonPurple)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = NeonPurple
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }

                // --- Backend Status ---
                item {
                    GlassCard(accentColor = NeonPurple) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("Wuthering Waves", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Manage configs, backups, and device tuned presets.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                item {
                    BackendStatusCard(status = backendStatus, onToggle = {
                        viewModel.switchTo(
                            if (backendStatus.method == AccessMethod.ADB) AccessMethod.ROOT else AccessMethod.ADB
                        )
                    })
                }
                item {
                    if (backendStatus.method == AccessMethod.ADB && !backendStatus.connected) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(
                                onClick = { viewModel.connect() },
                                modifier = Modifier.weight(1f),
                                enabled = !backendStatus.connected,
                                accentColor = NeonCyan,
                                contentColor = Color.White
                            ) { Text("Connect", fontWeight = FontWeight.Bold) }
                            GlassOutlinedButton(
                                onClick = { showAdbDialog = true },
                                modifier = Modifier.weight(1f),
                                enabled = !backendStatus.connected,
                                accentColor = NeonAmber
                            ) { Icon(Icons.Default.Edit, contentDescription = "Manual", modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Manual") }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton(
                                onClick = { viewModel.connect() },
                                modifier = Modifier.weight(1f),
                                enabled = !backendStatus.connected,
                                accentColor = NeonCyan,
                                contentColor = Color.White
                            ) { Text("Connect", fontWeight = FontWeight.Bold) }
                            GlassOutlinedButton(
                                onClick = { viewModel.disconnect() },
                                modifier = Modifier.weight(1f),
                                enabled = backendStatus.connected,
                                accentColor = NeonRed
                            ) { Text("Disconnect", fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                // --- F2P Tips / Sponsor ---
                item {
                    GlassCard(accentColor = NeonAmber) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("F2P Tips", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Save your Astrites! Do your dailies & events.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Support Player42!", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = CrimsonRed)
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
                                Text("Select 1-3 .ini files to replace. Matching Engine, DeviceProfiles, or GameUserSettings will be backed up and applied.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                GlassButton(
                                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = backendStatus.connected,
                                    accentColor = NeonCyan,
                                    contentColor = Color.White
                                ) { Text("Select Custom Configs", fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.height(8.dp))
                                GlassOutlinedButton(
                                    onClick = { showDeleteDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = backendStatus.connected,
                                    accentColor = NeonRed
                                ) { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Delete Config Files", fontWeight = FontWeight.Bold) }
                                if (!backendStatus.connected) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Connect to device first", style = MaterialTheme.typography.bodySmall, color = NeonRed.copy(alpha = 0.7f))
                                }
                            }
                            CustomConfigState.REVIEW -> {
                                Text("Matched files:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                pickedFiles.forEach { f ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonGreen)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text("→ ${f.targetName}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = NeonCyan)
                                            Text("from ${f.displayName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                val missing = TARGET_NAMES.filter { t -> pickedFiles.none { it.targetName == t } }
                                if (missing.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("Will skip: ${missing.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GlassButton(
                                        onClick = {
                                            val engine = pickedFiles.firstOrNull { it.targetName == "Engine.ini" }?.content
                                            val device = pickedFiles.firstOrNull { it.targetName == "DeviceProfiles.ini" }?.content
                                            val gus = pickedFiles.firstOrNull { it.targetName == "GameUserSettings.ini" }?.content
                                            viewModel.applyCustomFiles(engine, device, gus)
                                            customConfigState = CustomConfigState.IDLE
                                            pickedFiles = emptyList()
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !isApplying,
                                        accentColor = NeonPurple,
                                        contentColor = Color.White
                                    ) { Text("Apply", fontWeight = FontWeight.Bold) }
                                    GlassOutlinedButton(
                                        onClick = {
                                            customConfigState = CustomConfigState.IDLE
                                            pickedFiles = emptyList()
                                        },
                                        modifier = Modifier.weight(1f),
                                        accentColor = NeonPink
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
                                onClick = onNavigateToBackups,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = NeonPink.copy(alpha = 0.08f),
                                    contentColor = NeonPink
                                ),
                                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 0.dp)
                            ) {
                                Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Backups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text("${backups.size} saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            ElevatedButton(
                                onClick = { viewModel.collectClientLog() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                enabled = backendStatus.connected && !isApplying,
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = NeonGreen.copy(alpha = 0.08f),
                                    contentColor = NeonGreen
                                ),
                                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 0.dp)
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Collect Client.log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text("Device log", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            ElevatedButton(
                                onClick = onNavigateToConfigGen,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                enabled = !isApplying,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.elevatedButtonColors(
                                    containerColor = NeonAmber.copy(alpha = 0.08f),
                                    contentColor = NeonAmber
                                ),
                                elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 0.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Config Generator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text("Preset deploy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                }

                // --- Log ---
                item {
                    LogViewer(logs)
                }
            }
        }
    }

    // Auto-show ADB dialog when auto-scan fails
    LaunchedEffect(backendStatus.errorMessage) {
        if (backendStatus.method == AccessMethod.ADB &&
            !backendStatus.connected &&
            backendStatus.errorMessage.contains("ADB port not found", ignoreCase = true)) {
            showAdbDialog = true
        }
    }

    if (showAdbDialog) {
        AlertDialog(
            onDismissRequest = { showAdbDialog = false },
            containerColor = CardSurface,
            icon = { Icon(Icons.Default.Adb, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp)) },
            title = { Text("Wireless Debugging", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter the IP:port from Developer Options > Wireless Debugging.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = adbHost,
                        onValueChange = { adbHost = it },
                        label = { Text("IP Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            focusedLabelColor = NeonCyan
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adbPort,
                        onValueChange = { adbPort = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            focusedLabelColor = NeonCyan
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAdbDialog = false
                        viewModel.connectAdbManual(adbHost, adbPort)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan.copy(alpha = 0.15f),
                        contentColor = NeonCyan
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Connect", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAdbDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = CardSurface,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = NeonRed, modifier = Modifier.size(32.dp)) },
            title = { Text("Delete Config Files", color = NeonRed, fontWeight = FontWeight.Bold) },
            text = {
                Text("Engine.ini, DeviceProfiles.ini, and GameUserSettings.ini will be deleted from the game directory. This cannot be undone.", style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteConfigFiles()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonRed.copy(alpha = 0.15f),
                        contentColor = NeonRed
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Delete", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
