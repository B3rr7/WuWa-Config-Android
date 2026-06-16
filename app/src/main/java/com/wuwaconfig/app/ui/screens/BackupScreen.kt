package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val backendStatus by viewModel.backendStatus.collectAsState()
    val backups by viewModel.backups.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var backupName by remember { mutableStateOf("") }

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Backups", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonPink) } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = NeonPink
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            if (backups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(64.dp), tint = NeonPink.copy(alpha = 0.4f))
                        Spacer(Modifier.height(12.dp))
                        Text("No backups yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(Modifier.height(4.dp))
                        Text("Connect to get started", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Spacer(Modifier.height(24.dp))
                        GlassButton(
                            onClick = { showCreateDialog = true },
                            enabled = backendStatus.connected,
                            accentColor = NeonPink,
                            contentColor = Color.White
                        ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Create Backup", fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)
                    ) {
                        item {
                            Text("${backups.size} backup(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Spacer(Modifier.height(4.dp))
                        }
                        items(backups, key = { it.id }) { backup ->
                            BackupManageCard(
                                backup = backup,
                                onRestore = { viewModel.restoreBackup(backup) },
                                onDelete = { viewModel.deleteBackup(backup) },
                                isApplying = isApplying,
                                connected = backendStatus.connected
                            )
                        }
                    }
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                        GlassButton(
                            onClick = { showCreateDialog = true },
                            enabled = backendStatus.connected,
                            accentColor = NeonPink,
                            contentColor = Color.White,
                            modifier = Modifier.widthIn(min = 200.dp)
                        ) { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Create Backup", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false; backupName = "" },
                containerColor = CardSurface,
                titleContentColor = NeonPink,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                title = { Text("Create Backup", color = NeonPink, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = backupName,
                        onValueChange = { backupName = it },
                        label = { Text("Backup name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonPink.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (backupName.isNotBlank()) {
                                viewModel.createBackup(backupName)
                                showCreateDialog = false
                                backupName = ""
                            }
                        },
                        enabled = backupName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPink.copy(alpha = 0.15f),
                            contentColor = NeonPink
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false; backupName = "" }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun BackupManageCard(
    backup: ConfigBackup,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    isApplying: Boolean,
    connected: Boolean
) {
    val isAuto = backup.type == "auto"
    val accent = if (isAuto) NeonAmber else NeonPurple
    val label = if (isAuto) "Auto" else "Manual"
    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(backup.timestamp))

    GlassCard(accentColor = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(24.dp), tint = accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(backup.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, color = accent) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = accent.copy(alpha = 0.1f)),
                        border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = accent.copy(alpha = 0.2f))
                    )
                }
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            backup.files.forEach { file ->
                SuggestionChip(
                    onClick = {},
                    label = { Text(file.name, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = accent.copy(alpha = 0.08f)),
                    border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = accent.copy(alpha = 0.15f))
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            GlassOutlinedButton(
                onClick = onDelete,
                accentColor = NeonRed,
                modifier = Modifier.height(40.dp)
            ) { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Delete", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            GlassButton(
                onClick = onRestore,
                enabled = connected && !isApplying,
                accentColor = accent,
                contentColor = Color.White,
                modifier = Modifier.height(40.dp)
            ) { Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Restore", fontWeight = FontWeight.Bold) }
        }
    }
}
