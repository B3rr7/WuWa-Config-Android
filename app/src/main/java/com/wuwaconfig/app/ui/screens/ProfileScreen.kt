package com.wuwaconfig.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.model.PlayerProfile
import com.wuwaconfig.app.ui.ProfileViewModel
import com.wuwaconfig.app.ui.components.BouncingOrb
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.components.MiniLogViewer
import com.wuwaconfig.app.ui.components.formatRam
import com.wuwaconfig.app.ui.modifyCountLabel
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    backendStatus: BackendStatus,
) {
    val profile by viewModel.playerProfile.collectAsStateWithLifecycle()
    val profileLoading by viewModel.profileLoading.collectAsStateWithLifecycle()
    val profileProgress by viewModel.profileProgress.collectAsStateWithLifecycle()
    val configModifyCounts by viewModel.configModifyCounts.collectAsStateWithLifecycle()

    LaunchedEffect(backendStatus.connected) {
        if (backendStatus.connected && profile == null && !profileLoading) {
            viewModel.loadProfile()
        }
    }

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = { Text("Player Profile", fontWeight = FontWeight.Bold) },
                    accentColor = NeonGreen,
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonGreen) }
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                GlassCard(accentColor = NeonGreen) {
                    Text(
                        "Read-only view of your game data. No files are modified — zero footprint.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (profileLoading && profile == null) {
                    ProfileLoadingAnimation("Reading game data from device...", profileProgress)
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
                        contentColor = Color.White,
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
                        accentColor = NeonGreen,
                    ) {
                        if (profileLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonGreen, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Refresh", fontWeight = FontWeight.Bold)
                    }
                }

                MiniLogViewer()

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
            horizontalAlignment = Alignment.CenterHorizontally,
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BadgeItem("Lv.${profile.playerLevel ?: "—"}", NeonGold)
                BadgeItem(profile.server ?: "—", NeonPurple)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Last login: ${profile.lastLoginTime ?: "N/A"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun BadgeItem(
    text: String,
    accent: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(accent.copy(alpha = 0.15f))
                .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun ProfileContent(
    profile: PlayerProfile,
    configModifyCounts: Map<String, Int>?,
) {
    GameProgressSection(profile)
    GameInfoSection(profile)
    DeviceSection(profile)
    PerformanceSection(profile)
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
                // "guess", not a fact. The only local key is
                // `BattlePassPayButton_<uid>`, and its value is the game's shared
                // boolean-true sentinel `"___1B___"` — the same token used by ~40
                // unrelated keys (NotFirstTimeOpenPush, FirstOpenShop,
                // PayShopRechargeRedDot, ...). It records that the pay button has
                // been *seen*, not that it was paid for, and no purchase flag
                // exists anywhere in LocalStorage; that state is server-side.
                // Verified on-device against all 161 keys.
                //
                // The label is deliberately hedged rather than shown as a plain
                // "Purchased", because "Purchased" is a financial claim the local
                // data cannot support.
                if (profile.battlePassPurchased) "Paid (guess)" else "Free (guess)",
                if (profile.battlePassPurchased) NeonGold else NeonAmber,
                Modifier.weight(1f),
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Lv.$level", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = NeonGold)
                }
            }
        }
    }
}

@Composable
private fun ProgressChip(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.08f))
                .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
private fun InfoChip(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.08f))
                .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun DeviceSection(profile: PlayerProfile) {
    GlassCard(accentColor = NeonCyan) {
        SectionHeader("DEVICE", NeonCyan)
        Spacer(Modifier.height(10.dp))
        // Same treatment as PERFORMANCE: render the fields unconditionally and
        // use `deviceLogRead` to decide whether an empty value is worth
        // explaining. The old `hasData` gate here was what hid the section
        // entirely, and it stayed false on a perfectly readable log whenever the
        // only populated field was the one this screen could not source from the
        // log (the chipset).
        if (!profile.deviceLogRead) {
            Text(
                "Could not read the game log, so these values are unknown. Close the " +
                    "game, play it once, then tap Refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(Icons.Default.Memory, "GPU", profile.gpu ?: NOT_REPORTED, NeonCyan, Modifier.weight(1f))
            InfoChip(Icons.Default.DeveloperBoard, "Chipset", profile.socName ?: NOT_REPORTED, NeonPurple, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(Icons.Default.Storage, "RAM", profile.ramMb?.let { formatRam(it) } ?: NOT_REPORTED, NeonPink, Modifier.weight(1f))
            InfoChip(Icons.Default.Android, "Android", profile.androidVersion ?: NOT_REPORTED, NeonGreen, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(Icons.Default.AspectRatio, "Resolution", profile.resolution ?: NOT_REPORTED, NeonAmber, Modifier.weight(1f))
            InfoChip(Icons.Default.Hub, "Render API", profile.renderApi ?: NOT_REPORTED, NeonGold, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Was conditional on `isNullOrBlank()`. The game does not always emit
            // a Vulkan verdict — it names Vulkan and then falls back to OpenGL ES
            // — so "absent" here genuinely means "the log did not say", which is
            // the same thing the other chips in this section mean.
            InfoChip(
                Icons.Default.CheckCircle,
                "Vulkan",
                profile.vulkanStatus?.takeIf { it.isNotBlank() } ?: NOT_REPORTED,
                NeonCyan,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PerformanceSection(profile: PlayerProfile) {
    GlassCard(accentColor = NeonAmber) {
        SectionHeader("PERFORMANCE", NeonAmber)
        Spacer(Modifier.height(10.dp))
        // No `hasData` gate. Every field below is nullable-or-zero, so a healthy
        // session that simply logged no OOM/thermal/drop events looked exactly
        // like a failed read — the old code collapsed the whole section in both
        // cases. Only `deviceLogRead` tells them apart now.
        if (!profile.deviceLogRead) {
            Text(
                "Could not read the game log, so these values are unknown. Close the game, " +
                    "play it once, then tap Refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(
                Icons.Default.Speed,
                "Avg FPS",
                profile.fpsActual?.let { "%.1f".format(it) } ?: NOT_REPORTED,
                NeonAmber,
                Modifier.weight(1f),
            )
            InfoChip(Icons.Default.Speed, "FPS Cap", profile.fpsCap?.toString() ?: NOT_REPORTED, NeonCyan, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(
                Icons.Default.ZoomIn,
                "Screen %",
                profile.screenPct?.toInt()?.let { "$it%" } ?: NOT_REPORTED,
                NeonPink,
                Modifier.weight(1f),
            )
            InfoChip(Icons.Default.Shield, "Shadow Q", profile.shadowQ?.toString() ?: NOT_REPORTED, NeonPurple, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoChip(Icons.Default.Tune, "Quality", profile.qualityMode ?: NOT_REPORTED, NeonGreen, Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NeonAmber.copy(alpha = 0.15f)))
        Spacer(Modifier.height(10.dp))
        Text("DIAGNOSTICS", style = MaterialTheme.typography.labelMedium, color = NeonAmber.copy(alpha = 0.7f), letterSpacing = 2.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Counted from the game log. Zero means nothing was recorded, which on a " +
                "healthy session is the expected result — not a missing reading.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(8.dp))
        // Always rendered. These used to be hidden unless one was non-zero, which
        // meant a clean profile showed no diagnostics block at all and gave the
        // user no way to tell that apart from an unread log.
        DiagnosticRow("Thermal events", profile.thermalEvents)
        DiagnosticRow("GPU OOM", profile.gpuOom)
        DiagnosticRow("Dropped frames", profile.dropFrames)
        DiagnosticRow("Texture errors", profile.textureErrors)
        DiagnosticRow("Forbidden CVars", profile.forbiddenCvars)
    }
}

/**
 * Shown in place of a value the game never reported.
 *
 * `—` alone was ambiguous: it is what a failed read produces, what a field the
 * game does not emit produces, and what a genuinely absent optional produces.
 * "Avg FPS" is permanently in this state — the game's log has no `AverageFPS`
 * line (that is a desktop-UE stat), confirmed across three 3.7.0 sessions.
 */
private const val NOT_REPORTED = "not reported"

@Composable
private fun DiagnosticRow(
    label: String,
    value: Int,
) {
    val bad = value > 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$value",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (bad) NeonRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ConfigSummarySection(
    profile: PlayerProfile,
    configModifyCounts: Map<String, Int>?,
) {
    GlassCard(accentColor = NeonPink) {
        SectionHeader("CONFIG SUMMARY", NeonPink)
        Spacer(Modifier.height(6.dp))
        Text(
            "Read-only counts — no files modified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(12.dp))

        val maxCount =
            maxOf(
                profile.engineSettingCount,
                profile.deviceProfileCount,
                profile.gameUserSettingCount,
                profile.scalabilitySettingCount,
                profile.hardwareSettingCount,
                1,
            )
        ConfigBar("Engine.ini", profile.engineSettingCount, maxCount, NeonCyan)
        Spacer(Modifier.height(8.dp))
        ConfigBar("DeviceProfiles.ini", profile.deviceProfileCount, maxCount, NeonPurple)
        Spacer(Modifier.height(8.dp))
        ConfigBar("GameUserSettings.ini", profile.gameUserSettingCount, maxCount, NeonPink)
        Spacer(Modifier.height(8.dp))
        ConfigBar("Scalability.ini", profile.scalabilitySettingCount, maxCount, NeonAmber)
        Spacer(Modifier.height(8.dp))
        ConfigBar("Hardware.ini", profile.hardwareSettingCount, maxCount, Color(0xFFAA88FF))

        // Always rendered. A block that vanishes and reappears reads as a
        // rendering glitch; an explicit "unavailable" line reads as missing data.
        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NeonPink.copy(alpha = 0.15f)))
        Spacer(Modifier.height(10.dp))
        Text("MODIFICATIONS", style = MaterialTheme.typography.labelMedium, color = NeonPink.copy(alpha = 0.7f), letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        if (configModifyCounts == null) {
            Text(
                "Unavailable — the device hash file could not be read.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val allFiles = GamePaths.MONITORED_FILES
            for (fileName in allFiles) {
                // Absent from the hash file means the game has not touched this
                // INI, which is not the same as a measured zero.
                val count = configModifyCounts[fileName]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifyCountLabel(configModifyCounts, fileName),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if ((count ?: 0) > 0) NeonAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (count == null) "not modified by the game" else "× modified",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigBar(
    label: String,
    count: Int,
    max: Int,
    accent: Color,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = if (max > 0) count.toFloat() / max else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 100),
        label = "configBar",
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$count", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = animatedFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(listOf(accent.copy(alpha = 0.6f), accent)),
                        ),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    accent: Color,
) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = accent.copy(alpha = 0.7f), letterSpacing = 2.sp)
}

@Composable
private fun ProfileLoadingAnimation(
    text: String,
    progress: Int = 0,
) {
    GlassCard(accentColor = NeonGreen) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val orbs = listOf(NeonGreen, NeonCyan, NeonGold)
                orbs.forEachIndexed { index, color ->
                    BouncingOrb(color, index)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "$progress%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonGreen,
                )
            }
        }
    }
}
