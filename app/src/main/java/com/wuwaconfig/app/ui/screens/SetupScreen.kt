package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: MainViewModel,
    onComplete: () -> Unit,
) {
    var backupDir by remember { mutableStateOf(viewModel.backupStorageDir) }

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Setup", fontWeight = FontWeight.Bold) },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = NeonCyan,
                        ),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
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
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
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
                        viewModel.finishSetup(backupDir)
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
