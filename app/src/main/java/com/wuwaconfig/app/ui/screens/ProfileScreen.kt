package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuwaconfig.app.model.PlayerProfile
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.components.MiniLogViewer
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val profile by viewModel.playerProfile.collectAsState()
    val profileLoading by viewModel.profileLoading.collectAsState()
    val backendStatus by viewModel.backendStatus.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val configModifyCounts by viewModel.configModifyCounts.collectAsState()

    LaunchedEffect(Unit) {
        if (profile == null && backendStatus.connected) {
            viewModel.loadProfile()
        }
    }

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Player Profile", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonGreen) } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = NeonGreen
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                GlassCard(accentColor = NeonGreen) {
                    Text(
                        "Read-only view of your game data. No files are modified — zero footprint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (profileLoading && profile == null) {
                    GlassButton(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        accentColor = NeonGreen,
                        contentColor = Color.White
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Loading...", fontWeight = FontWeight.Bold)
                    }
                }

                if (profile != null) {
                    val p = profile ?: return@Column
                    UidHeader(p)
                    ProfileContent(p, configModifyCounts)
                } else if (!profileLoading) {
                    GlassButton(
                        onClick = { viewModel.loadProfile() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = backendStatus.connected,
                        accentColor = NeonGreen,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Load Profile", fontWeight = FontWeight.Bold)
                    }
                }

                if (!backendStatus.connected) {
                    GlassCard(accentColor = NeonRed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = NeonRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect to a device first", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                        }
                    }
                }

                if (profile != null) {
                    GlassOutlinedButton(
                        onClick = { viewModel.loadProfile(forceRefresh = true) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !profileLoading,
                        accentColor = NeonGreen
                    ) {
                        if (profileLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonGreen, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Refresh", fontWeight = FontWeight.Bold)
                    }
                }

                MiniLogViewer(logs)

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun UidHeader(profile: PlayerProfile) {
    GlassCard(accentColor = NeonCyan) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ACCOUNT", style = MaterialTheme.typography.labelSmall, color = NeonCyan.copy(alpha = 0.5f), letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))

            Text(
                profile.uid ?: "—",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = NeonCyan,
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgeItem("Lv.${profile.playerLevel ?: "—"}", NeonGold)
                BadgeItem(profile.server ?: "—", NeonPurple)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Last login: ${profile.lastLoginTime ?: "N/A"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun BadgeItem(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun ProfileContent(profile: PlayerProfile, configModifyCounts: Map<String, Int>) {
    GameProgressSection(profile)
    GameInfoSection(profile)
    ConfigSummarySection(profile, configModifyCounts)
}

@Composable
private fun GameProgressSection(profile: PlayerProfile) {
    GlassCard(accentColor = NeonGreen) {
        SectionHeader("GAME PROGRESS", NeonGreen)
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (profile.towerFloor != null) {
                ProgressChip(Icons.Default.Stairs, "Tower", "F${profile.towerFloor}", NeonGold, Modifier.weight(1f))
            }
            if (profile.loopTowerSeason != null) {
                ProgressChip(Icons.Default.Analytics, "Season", "${profile.loopTowerSeason}", NeonPurple, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (profile.weeklyRogueScore != null) {
                ProgressChip(Icons.Default.Explore, "Weekly Rogue", "${profile.weeklyRogueScore}", NeonCyan, Modifier.weight(1f))
            }
            ProgressChip(
                if (profile.battlePassPurchased) Icons.Default.LockOpen else Icons.Default.Lock,
                "Battle Pass",
                if (profile.battlePassPurchased) "Purchased" else "Free",
                if (profile.battlePassPurchased) NeonGold else NeonAmber,
                Modifier.weight(1f)
            )
        }

        if (profile.serverLevels.size > 1) {
            Spacer(Modifier.height(10.dp))
            Text("Server Accounts", style = MaterialTheme.typography.labelSmall, color = NeonGreen.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))
            profile.serverLevels.forEach { (region, level) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Lv.$level", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = NeonGold)
                }
            }
        }
    }
}

@Composable
private fun ProgressChip(icon: ImageVector, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun GameInfoSection(profile: PlayerProfile) {
    GlassCard(accentColor = NeonPurple) {
        SectionHeader("GAME INFO", NeonPurple)
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(Icons.Default.Info, "Version", profile.gameVersion ?: "—", NeonPurple, Modifier.weight(1f))
            InfoChip(Icons.Default.Star, "Patch", profile.patchVersion ?: "—", NeonPink, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(Icons.Default.Computer, "Launcher", profile.launcherVersion ?: "—", NeonCyan, Modifier.weight(1f))
            InfoChip(Icons.Default.Language, "Language", profile.language?.uppercase() ?: "—", NeonGold, Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun ConfigSummarySection(profile: PlayerProfile, configModifyCounts: Map<String, Int>) {
    GlassCard(accentColor = NeonPink) {
        SectionHeader("CONFIG SUMMARY", NeonPink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Read-only counts — no files modified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))

        val maxCount = maxOf(profile.engineSettingCount, profile.deviceProfileCount, profile.gameUserSettingCount, 1)
        ConfigBar("Engine.ini", profile.engineSettingCount, maxCount, NeonCyan)
        Spacer(Modifier.height(8.dp))
        ConfigBar("DeviceProfiles.ini", profile.deviceProfileCount, maxCount, NeonPurple)
        Spacer(Modifier.height(8.dp))
        ConfigBar("GameUserSettings.ini", profile.gameUserSettingCount, maxCount, NeonPink)

        if (configModifyCounts.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NeonPink.copy(alpha = 0.15f)))
            Spacer(Modifier.height(10.dp))
            Text("MODIFICATIONS", style = MaterialTheme.typography.labelMedium, color = NeonPink.copy(alpha = 0.7f), letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            val allFiles = listOf("Engine.ini", "DeviceProfiles.ini", "GameUserSettings.ini", "Scalability.ini", "Hardware.ini")
            for (fileName in allFiles) {
                val count = configModifyCounts[fileName] ?: 0
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$count",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (count > 0) NeonAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "× modified",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigBar(label: String, count: Int, max: Int, accent: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = count.toFloat() / max)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(listOf(accent.copy(alpha = 0.6f), accent))
                    )
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, accent: Color) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = accent.copy(alpha = 0.7f), letterSpacing = 2.sp)
}
