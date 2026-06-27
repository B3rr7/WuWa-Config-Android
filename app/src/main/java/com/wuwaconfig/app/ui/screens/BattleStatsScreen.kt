package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuwaconfig.app.model.BattleStats
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BattleStatsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val stats by viewModel.battleStats.collectAsState()
    val loading by viewModel.battleStatsLoading.collectAsState()
    val backendStatus by viewModel.backendStatus.collectAsState()
    val logs by viewModel.logs.collectAsState()

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Battle Stats", fontWeight = FontWeight.Bold) },
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
                        "Battle statistics extracted from Client.log. Data from all game sessions is cumulative.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (stats == null) {
                    GlassButton(
                        onClick = { viewModel.loadBattleStats() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = backendStatus.connected && !loading,
                        accentColor = NeonGreen,
                        contentColor = Color.White
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Loading...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Load Battle Stats", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    val s = stats ?: return@Column
                    BattleStatsHeader(s)
                    BattleStatsContent(s)
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

                if (stats != null) {
                    GlassButton(
                        onClick = { viewModel.loadBattleStats() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        accentColor = NeonGreen,
                        contentColor = Color.White
                    ) { Text("Refresh", fontWeight = FontWeight.Bold) }
                }

                if (logs.isNotEmpty()) {
                    GlassCard(accentColor = NeonAmber) {
                        Text("Status", style = MaterialTheme.typography.labelMedium, color = NeonAmber.copy(alpha = 0.7f))
                        Spacer(Modifier.height(6.dp))
                        logs.takeLast(5).forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BattleStatsHeader(stats: BattleStats) {
    GlassCard(accentColor = NeonRed) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("TOTAL BATTLES", style = MaterialTheme.typography.labelSmall, color = NeonRed.copy(alpha = 0.5f), letterSpacing = 3.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "${stats.battles}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = NeonRed,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniBadge("${stats.deaths} Deaths", NeonRed)
                MiniBadge("${stats.staggers} Staggers", NeonAmber)
                MiniBadge("${stats.staminaUsed} Stamina", NeonCyan)
            }
        }
    }
}

@Composable
private fun MiniBadge(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = accent)
    }
}

@Composable
private fun BattleStatsContent(stats: BattleStats) {
    SectionCard("COMBAT", NeonRed) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(Icons.Default.Favorite, "Echoes", "${stats.echoesCollected}", NeonRed, Modifier.weight(1f))
            StatCell(Icons.Default.Warning, "Deaths", "${stats.deaths}", NeonRed.copy(alpha = 0.7f), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(Icons.Default.Bolt, "Staggers", "${stats.staggers}", NeonAmber, Modifier.weight(1f))
            StatCell(Icons.Default.LocalFireDepartment, "Stamina", "${stats.staminaUsed}", NeonCyan, Modifier.weight(1f))
        }
    }

    SectionCard("DODGE", NeonCyan) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(Icons.AutoMirrored.Filled.ArrowForward, "Forward", "${stats.dodgeForward}", NeonCyan, Modifier.weight(1f))
            StatCell(Icons.AutoMirrored.Filled.ArrowBack, "Back", "${stats.dodgeBack}", NeonCyan.copy(alpha = 0.7f), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        StatCell(Icons.Default.SwapHoriz, "Counter", "${stats.dodgeCounter}", NeonCyan, Modifier.fillMaxWidth())
    }

    SectionCard("MOVEMENT", NeonPurple) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(Icons.Default.NearMe, "Teleports", "${stats.teleports}", NeonPurple, Modifier.weight(1f))
            StatCell(Icons.Default.SwapVert, "Role Changes", "${stats.roleChanges}", NeonPurple.copy(alpha = 0.7f), Modifier.weight(1f))
        }
    }

    SectionCard("ECHO SKILLS", NeonAmber) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(Icons.Default.AutoAwesome, "Skills Used", "${stats.echoSkillsUsed}", NeonAmber, Modifier.weight(1f))
            StatCell(Icons.Default.Transform, "Transforms", "${stats.echoTransformUsed}", NeonAmber.copy(alpha = 0.7f), Modifier.weight(1f))
        }
    }

    SectionCard("OTHER", NeonPink) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell(Icons.Default.CardMembership, "Monthly Cards", "${stats.monthCards}", NeonPink, Modifier.weight(1f))
            StatCell(Icons.Default.Storage, "Log Size", formatBytes(stats.logSizeBytes), NeonGreen, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SectionCard(title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(accentColor = accent) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = accent.copy(alpha = 0.7f), letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun StatCell(icon: ImageVector, label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
        bytes >= 1_000 -> "${"%.1f".format(bytes / 1_000.0)} KB"
        else -> "$bytes B"
    }
}
