package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuwaconfig.app.model.ConfigBackup
import com.wuwaconfig.app.ui.BackupViewModel
import com.wuwaconfig.app.ui.DeployHistoryViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassDialog
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

private val ALL_INI_FILES = listOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini", "Scalability.ini", "Hardware.ini")

/** Shared formatter for backup rows; see the `remember` note at the use site. */
private val BACKUP_STAMP_FMT = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    deployHistoryViewModel: DeployHistoryViewModel,
    onBack: () -> Unit,
) {
    val backendStatus by deployHistoryViewModel.backendStatus.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val isApplying by deployHistoryViewModel.isApplying.collectAsStateWithLifecycle()
    // Dialog state is saveable (simple types); restoreTarget holds a data class
    // and stays plain remember — worst case on rotation the restore dialog is
    // closed rather than acting on a stale backup reference.
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var backupName by rememberSaveable { mutableStateOf("") }
    var selectedCreateFiles by rememberSaveable { mutableStateOf(ALL_INI_FILES.toSet()) }
    var restoreTarget by remember { mutableStateOf<ConfigBackup?>(null) }
    var selectedRestoreFiles by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    // Delete is irreversible, so it goes through the same confirmation path the
    // Restore dialog already uses. Previously it was one unconfirmed tap — and
    // the 40dp adjacent Restore/Delete buttons made a near-miss land on it.
    var pendingDelete by remember { mutableStateOf<ConfigBackup?>(null) }
    val dialogBackupDir by viewModel.backupStorageDirFlow.collectAsStateWithLifecycle()
    val backupFeedback by viewModel.backupFeedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Clear the feedback BEFORE showing: showSnackbar suspends for the full
    // duration, so a second message arriving during that window cancelled this
    // effect, leaving the first message un-cleared and re-displayed later.
    LaunchedEffect(backupFeedback) {
        val message = backupFeedback ?: return@LaunchedEffect
        viewModel.clearBackupFeedback()
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
    }

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = { Text("Backups", fontWeight = FontWeight.Bold) },
                    accentColor = NeonPink,
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonPink) }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
        ) { padding ->
            if (backups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.RestorePage,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = NeonPink.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No backups yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Connect to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                        Spacer(Modifier.height(24.dp))
                        GlassButton(
                            onClick = {
                                selectedCreateFiles = ALL_INI_FILES.toSet()
                                showCreateDialog = true
                            },
                            enabled = backendStatus.connected,
                            accentColor = NeonPink,
                            contentColor = Color.White,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create Backup", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        // Scaffold insets go HERE, not on the parent Box: padding the
                        // parent would clip the list so it can never scroll under the
                        // status/navigation bars. 96.dp bottom keeps the last card clear
                        // of the floating "Create Backup" button pinned below.
                        contentPadding =
                            PaddingValues(
                                start = 0.dp,
                                end = 0.dp,
                                top = padding.calculateTopPadding() + 16.dp,
                                bottom = 96.dp + padding.calculateBottomPadding(),
                            ),
                    ) {
                        item {
                            Text(
                                "${backups.size} backup(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        items(backups, key = { it.id }) { backup ->
                            BackupManageCard(
                                backup = backup,
                                onRestore = {
                                    selectedRestoreFiles = backup.files.map { it.name }.toSet()
                                    restoreTarget = backup
                                },
                                onDelete = { pendingDelete = backup },
                                isApplying = isApplying,
                                connected = backendStatus.connected,
                            )
                        }
                    }
                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                        GlassButton(
                            onClick = {
                                selectedCreateFiles = ALL_INI_FILES.toSet()
                                showCreateDialog = true
                            },
                            enabled = backendStatus.connected,
                            accentColor = NeonPink,
                            contentColor = Color.White,
                            modifier = Modifier.widthIn(min = 200.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create Backup", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            GlassDialog(
                onDismissRequest = {
                    showCreateDialog = false
                    backupName = ""
                },
                accentColor = NeonPink,
                title = { Text("Create Backup", color = NeonPink, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = backupName,
                            onValueChange = { backupName = it },
                            label = { Text("Backup name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonPink.copy(alpha = 0.5f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                ),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Select files to back up:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        ALL_INI_FILES.forEach { name ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            ) {
                                Checkbox(
                                    checked = name in selectedCreateFiles,
                                    onCheckedChange = { checked ->
                                        selectedCreateFiles = if (checked) selectedCreateFiles + name else selectedCreateFiles - name
                                    },
                                    colors =
                                        CheckboxDefaults.colors(
                                            checkedColor = NeonPink,
                                            uncheckedColor = Color.White.copy(alpha = 0.3f),
                                        ),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (backupName.isNotBlank() && selectedCreateFiles.isNotEmpty()) {
                                viewModel.createBackup(backupName, selectedCreateFiles)
                                showCreateDialog = false
                                backupName = ""
                            }
                        },
                        enabled = backupName.isNotBlank() && selectedCreateFiles.isNotEmpty(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = NeonPink.copy(alpha = 0.15f),
                                contentColor = NeonPink,
                            ),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Create", color = NeonPink) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCreateDialog = false
                        backupName = ""
                    }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
                },
            )
        }

        restoreTarget?.let { backup ->
            GlassDialog(
                onDismissRequest = { restoreTarget = null },
                accentColor = NeonPurple,
                title = { Text("Restore: ${backup.name}", color = NeonPurple, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Select files to restore:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        backup.files.forEach { file ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            ) {
                                Checkbox(
                                    checked = file.name in selectedRestoreFiles,
                                    onCheckedChange = { checked ->
                                        selectedRestoreFiles = if (checked) selectedRestoreFiles + file.name else selectedRestoreFiles - file.name
                                    },
                                    colors =
                                        CheckboxDefaults.colors(
                                            checkedColor = NeonPurple,
                                            uncheckedColor = Color.White.copy(alpha = 0.3f),
                                        ),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(file.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (selectedRestoreFiles.isNotEmpty()) {
                                viewModel.restoreBackup(backup, selectedRestoreFiles)
                                restoreTarget = null
                            }
                        },
                        enabled = selectedRestoreFiles.isNotEmpty() && !isApplying,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = NeonPurple.copy(alpha = 0.15f),
                                contentColor = NeonPurple,
                            ),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Restore", color = NeonPurple) }
                },
                dismissButton = {
                    TextButton(onClick = { restoreTarget = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
                },
            )
        }

        pendingDelete?.let { backup ->
            GlassDialog(
                onDismissRequest = { pendingDelete = null },
                accentColor = NeonRed,
                title = { Text("Delete backup?", color = NeonRed, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            // Prevent the stale-reference trap the restore dialog has:
                            // if the list refreshed while the dialog was open, the held
                            // ConfigBackup may no longer exist. Resolve by id at confirm
                            // time instead of acting on the captured object.
                            "\"${backup.name}\" will be permanently removed from \"$dialogBackupDir\". This cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Only delete if it is still in the (fresh) list.
                            if (backups.any { it.id == backup.id }) {
                                viewModel.deleteBackup(backup)
                            }
                            pendingDelete = null
                        },
                        enabled = !isApplying,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = NeonRed.copy(alpha = 0.15f),
                                contentColor = NeonRed,
                            ),
                        shape = RoundedCornerShape(10.dp),
                    ) { Text("Delete", color = NeonRed, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurface) }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BackupManageCard(
    backup: ConfigBackup,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    isApplying: Boolean,
    connected: Boolean,
) {
    val isAuto = backup.type == "auto"
    val accent = if (isAuto) NeonAmber else NeonPurple
    val label = if (isAuto) "Auto" else "Manual"
    // remember() so SimpleDateFormat is not constructed (pattern parse +
    // Locale/Calendar lookup) on every recomposition of this list item. The
    // shared instance is safe because Compose composes on a single thread.
    val date = remember(backup.timestamp) { BACKUP_STAMP_FMT.format(java.util.Date(backup.timestamp)) }

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
                        enabled = false,
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, color = accent) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = accent.copy(alpha = 0.1f)),
                        border = SuggestionChipDefaults.suggestionChipBorder(enabled = false, borderColor = accent.copy(alpha = 0.2f)),
                    )
                }
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            backup.files.forEach { file ->
                SuggestionChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(file.name, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = accent.copy(alpha = 0.08f)),
                    border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = accent.copy(alpha = 0.15f)),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            // 48dp, not 40dp: destructive Delete sits directly next to Restore in
            // this row, so sub-48dp targets made a near-miss land on Delete.
            GlassOutlinedButton(
                onClick = onDelete,
                accentColor = NeonRed,
                height = 48.dp,
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Delete", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            GlassButton(
                onClick = onRestore,
                enabled = connected && !isApplying,
                accentColor = accent,
                contentColor = Color.White,
                height = 48.dp,
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Restore", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(name = "Backup Light", showBackground = true)
@Composable
private fun BackupScreenLightPreview() {
    WuWaConfigTheme(themeMode = "light") {
        GradientBackground {
            Text("Backup Preview")
        }
    }
}

@Preview(name = "Backup Dark", showBackground = true)
@Composable
private fun BackupScreenDarkPreview() {
    WuWaConfigTheme(themeMode = "dark") {
        GradientBackground {
            Text("Backup Preview")
        }
    }
}
