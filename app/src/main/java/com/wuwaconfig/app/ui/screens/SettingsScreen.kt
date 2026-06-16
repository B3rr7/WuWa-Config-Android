package com.wuwaconfig.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val backendStatus by viewModel.backendStatus.collectAsState()
    val chipset = viewModel.chipsetInfo
    var showBackupDirDialog by remember { mutableStateOf(false) }
    var newBackupDir by remember { mutableStateOf("") }

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonAmber) } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = NeonAmber
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassCard(accentColor = NeonAmber) {
                    GlassCardHeader("Access Method", NeonAmber)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current: ${backendStatus.method.name}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (backendStatus.connected) "Connected" else "Disconnected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (backendStatus.connected) NeonGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (backendStatus.method == AccessMethod.ADB)
                            "ADB: Needs Wireless Debugging enabled in Developer Options. Works on Android 11-15."
                        else
                            "ROOT: Uses su command. Requires a rooted device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                GlassCard(accentColor = NeonGreen) {
                    GlassCardHeader("Theme", NeonGreen)
                    Spacer(Modifier.height(8.dp))
                    val currentTheme by viewModel.themeMode.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("system" to "System", "dark" to "Dark", "light" to "Light").forEach { (value, label) ->
                            val selected = currentTheme == value
                            Button(
                                onClick = { viewModel.setThemeMode(value) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) NeonGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (selected) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
                        }
                    }
                }

                GlassCard(accentColor = NeonBlue) {
                    GlassCardHeader("Device", NeonBlue)
                    Spacer(Modifier.height(8.dp))
                    InfoSetting("SoC", chipset.socName)
                    InfoSetting("Board", chipset.board)
                    InfoSetting("Manufacturer", chipset.manufacturer)
                    InfoSetting("Type", when { chipset.isSnapdragon -> "Snapdragon"; chipset.isMediatek -> "MediaTek"; chipset.isExynos -> "Exynos"; chipset.isTensor -> "Tensor"; else -> "Other" })
                }

                GlassCard(accentColor = NeonCyan) {
                    GlassCardHeader("Storage & Sources", NeonCyan)
                    Spacer(Modifier.height(8.dp))
                    InfoSetting("Game Config", viewModel.gameConfigDir)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Backups", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(viewModel.backupStorageDir, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(2f))
                        Spacer(Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = { newBackupDir = viewModel.backupStorageDir; showBackupDirDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = NeonCyan.copy(alpha = 0.1f),
                                contentColor = NeonCyan
                            )
                        ) { Icon(Icons.Default.Edit, contentDescription = "Change", modifier = Modifier.size(16.dp)) }
                    }
                }

                GlassCard(accentColor = CrimsonRed) {
                    GlassCardHeader("F2P Tips", CrimsonRed)
                    Spacer(Modifier.height(8.dp))
                    Text("• Do your dailies & events every day", style = MaterialTheme.typography.bodySmall)
                    Text("• Save Astrites — only pull for chars you love", style = MaterialTheme.typography.bodySmall)
                    Text("• Build 2-3 strong teams, not every unit", style = MaterialTheme.typography.bodySmall)
                    Text("• Support Player42!", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = CrimsonRed)
                }

                GlassCard(accentColor = NeonPurple) {
                    GlassCardHeader("Links", NeonPurple)
                    Spacer(Modifier.height(8.dp))
                    val ctx = LocalContext.current
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinkButton(
                                icon = Icons.Default.Code,
                                label = "GitHub",
                                url = "https://github.com/Berry7650/WuWap42",
                                color = NeonCyan,
                                modifier = Modifier.weight(1f),
                                context = ctx
                            )
                            LinkButton(
                                icon = Icons.Default.PlayArrow,
                                label = "YouTube",
                                url = "https://www.youtube.com/@Player42_g",
                                color = NeonRed,
                                modifier = Modifier.weight(1f),
                                context = ctx
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinkButton(
                                icon = Icons.Default.Send,
                                label = "Telegram",
                                url = "https://t.me/Yt_Player42",
                                color = NeonBlue,
                                modifier = Modifier.weight(1f),
                                context = ctx
                            )
                            LinkButton(
                                icon = Icons.Default.Forum,
                                label = "Discord",
                                url = "https://discord.gg/5WP9nN2e2s",
                                color = NeonPurple,
                                modifier = Modifier.weight(1f),
                                context = ctx
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBackupDirDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDirDialog = false },
            containerColor = CardSurface,
            title = { Text("Backup Directory", color = NeonCyan, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newBackupDir,
                    onValueChange = { newBackupDir = it },
                    label = { Text("Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newBackupDir.isNotBlank()) {
                            viewModel.changeBackupDir(newBackupDir)
                            showBackupDirDialog = false
                        }
                    },
                    enabled = newBackupDir.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan.copy(alpha = 0.15f),
                        contentColor = NeonCyan,
                        disabledContainerColor = Color.White.copy(alpha = 0.04f),
                        disabledContentColor = Color.White.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDirDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InfoSetting(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LinkButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    url: String,
    color: Color,
    modifier: Modifier = Modifier,
    context: android.content.Context
) {
    Button(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                // silently fail
            }
        },
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.12f),
            contentColor = color
        )
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.Bold)
    }
}
