package com.wuwaconfig.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.ui.MainViewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val backendStatus by viewModel.backendStatus.collectAsState()
    val chipset = viewModel.chipsetInfo
    var showBackupDirDialog by remember { mutableStateOf(false) }
    var newBackupDir by remember { mutableStateOf("") }
    var showRemoveBgDialog by remember { mutableStateOf(false) }

    val ctx = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.setBackgroundImageUri(it.toString())
        }
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.setBackgroundVideoUri(it.toString())
        }
    }

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
                            color = if (backendStatus.connected) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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

                GlassCard(accentColor = NeonPink) {
                    GlassCardHeader("Custom Background", NeonPink)
                    Spacer(Modifier.height(8.dp))
                    val imageUri by viewModel.backgroundImageUri.collectAsState()
                    val videoUri by viewModel.backgroundVideoUri.collectAsState()
                    val bgAlpha by viewModel.backgroundOpacity.collectAsState()
                    val hasBg = imageUri != null || videoUri != null

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPink.copy(alpha = 0.12f), contentColor = NeonPink)
                            ) { Text("Image", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { videoPickerLauncher.launch("video/*") },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.12f), contentColor = NeonCyan)
                            ) { Text("Video", fontWeight = FontWeight.Bold) }
                            OutlinedButton(
                                onClick = { showRemoveBgDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                                border = BorderStroke(1.dp, NeonRed.copy(alpha = if (hasBg) 0.4f else 0.12f))
                            ) { Text("Remove", fontWeight = FontWeight.Bold, color = if (hasBg) NeonRed else NeonRed.copy(alpha = 0.3f)) }
                        }
                    }
                    if (imageUri != null && videoUri == null) {
                        Spacer(Modifier.height(10.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.2f))) {
                            val previewPainter = rememberAsyncImagePainter(
                                ImageRequest.Builder(ctx).data(imageUri).crossfade(true).build()
                            )
                            Image(
                                painter = previewPainter,
                                contentDescription = "Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(modifier = Modifier.fillMaxSize().background(NeonPink.copy(alpha = 0.08f)))
                        }
                    } else if (videoUri != null) {
                        Spacer(Modifier.height(10.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            Text("Video Background", color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (hasBg) {
                        Spacer(Modifier.height(12.dp))
                        Text("Opacity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("5%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Slider(
                                value = bgAlpha,
                                onValueChange = { viewModel.setBackgroundOpacity(it) },
                                valueRange = 0.05f..0.70f,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonPink,
                                    activeTrackColor = NeonPink.copy(alpha = 0.6f),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                            Text("70%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Text("${(bgAlpha * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NeonPink)
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

                GlassCard(accentColor = NeonPurple) {
                    GlassCardHeader("Links", NeonPurple)
                    Spacer(Modifier.height(8.dp))
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

    if (showRemoveBgDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveBgDialog = false },
            containerColor = CardSurface,
            title = { Text("Remove Background", color = NeonRed, fontWeight = FontWeight.Bold) },
            text = { Text("Remove the custom background image/video and revert to the default gradient?") },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveBgDialog = false
                        val curVideoUri = viewModel.backgroundVideoUri.value
                        val curImageUri = viewModel.backgroundImageUri.value
                        if (curVideoUri != null) {
                            try { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(curVideoUri), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                            viewModel.setBackgroundVideoUri(null)
                        }
                        if (curImageUri != null) {
                            try { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(curImageUri), Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                            viewModel.setBackgroundImageUri(null)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed.copy(alpha = 0.15f), contentColor = NeonRed),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Remove", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveBgDialog = false }) { Text("Cancel") }
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
