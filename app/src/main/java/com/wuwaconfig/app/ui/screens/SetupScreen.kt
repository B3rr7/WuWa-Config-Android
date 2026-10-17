package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.ui.BackupViewModel
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: MainViewModel,
    backupViewModel: BackupViewModel,
    initialBackupDir: String,
    onComplete: () -> Unit,
) {
    // rememberSaveable so the in-progress path survives process death. Seeded from
    // the single owner of the pref (BackupViewModel.backupStorageDirFlow) rather
    // than from a second, never-updated copy in MainViewModel.
    var backupDir by rememberSaveable { mutableStateOf(initialBackupDir) }

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = { Text("Setup", fontWeight = FontWeight.Bold) },
                    accentColor = NeonCyan,
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            Column(
                // imePadding BEFORE verticalScroll (the ordering is load-bearing: the
                // reverse would let the scroll container consume the keyboard inset
                // while the field is still off-screen). This screen's OutlinedTextField
                // is the last thing above the Confirm button, so without it the field
                // sits under the keyboard on API 30+ where adjustResize is inert
                // because enableEdgeToEdge() has turned off decor fitting.
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Configure where your data is stored.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                GlassCard(accentColor = NeonCyan) {
                    GlassCardHeader("Game Config Location", NeonCyan)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        viewModel.gameConfigDir,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Configs are applied to this directory via ADB or Root.", style = MaterialTheme.typography.bodySmall)
                }

                GlassCard(accentColor = NeonPurple) {
                    GlassCardHeader("Backup Directory", NeonPurple)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupDir,
                        onValueChange = { backupDir = it },
                        label = { Text("Path") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonPurple.copy(alpha = 0.5f),
                                // Branch on luminance like the rest of the app's themed
                                // controls: 10%-alpha white was invisible on a light
                                // background (the screen's primary input field).
                                unfocusedBorderColor =
                                    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    } else {
                                        Color.White.copy(alpha = 0.1f)
                                    },
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Backups are stored here with auto-timestamped files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                GlassButton(
                    onClick = {
                        // Route the directory through BackupViewModel BEFORE marking
                        // setup done. MainViewModel.finishSetup() only persists the
                        // pref — BackupViewModel's flow snapshot was already seeded in
                        // its init and nothing reconciled afterwards, so the whole
                        // session kept resolving backups against the STALE default dir
                        // (until a process restart) while deploy history read the new
                        // one. changeBackupDir() is the owner's write path: it updates
                        // the flow AND refreshes the list.
                        val dir = backupDir.ifBlank { backupViewModel.backupStorageDirFlow.value }
                        backupViewModel.changeBackupDir(dir)
                        viewModel.finishSetup(dir)
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = NeonCyan,
                    contentColor = Color.White,
                ) {
                    Text("Confirm & Start", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview(name = "Setup Light", showBackground = true)
@Composable
private fun SetupScreenLightPreview() {
    WuWaConfigTheme(themeMode = "light") {
        GradientBackground {
            Text("Setup Preview")
        }
    }
}

@Preview(name = "Setup Dark", showBackground = true)
@Composable
private fun SetupScreenDarkPreview() {
    WuWaConfigTheme(themeMode = "dark") {
        GradientBackground {
            Text("Setup Preview")
        }
    }
}
