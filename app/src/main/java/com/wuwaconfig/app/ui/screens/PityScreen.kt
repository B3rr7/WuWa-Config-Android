package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaHistoryEntry
import com.wuwaconfig.app.model.GachaPool
import com.wuwaconfig.app.model.GachaRecord
import com.wuwaconfig.app.model.PityPrediction
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.components.MiniLogViewer
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PityScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val conveneUrl by viewModel.conveneUrl.collectAsState()
    val conveneUrlLoading by viewModel.conveneUrlLoading.collectAsState()
    val gachaData by viewModel.gachaData.collectAsState()
    val gachaLoading by viewModel.gachaLoading.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val backendStatus by viewModel.backendStatus.collectAsState()
    val gachaHistory by viewModel.gachaHistory.collectAsState()

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Pity Tracker", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonPurple) }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = NeonPurple,
                        ),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                item {
                    GlassCard(accentColor = NeonPurple) {
                        Text(
                            "Extract Convene URL from Client.log to fetch your complete pull history from Kuro's servers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    GlassButton(
                        onClick = { viewModel.extractConveneUrl() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = backendStatus.connected && !conveneUrlLoading && !gachaLoading,
                        accentColor = NeonPurple,
                        contentColor = Color.White,
                    ) {
                        if (conveneUrlLoading || gachaLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(if (conveneUrlLoading) "Reading log..." else "Fetching data...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Fetch Gacha History", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (conveneUrlLoading || gachaLoading) {
                    item {
                        GlassButton(
                            onClick = { viewModel.startBackgroundPoll() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = backendStatus.connected,
                            accentColor = NeonCyan,
                            contentColor = Color.White,
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Poll in Background", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!backendStatus.connected) {
                    item {
                        GlassCard(accentColor = NeonRed) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = NeonRed, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Connect to a device first", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                            }
                        }
                    }
                }

                if (gachaHistory != null && gachaData == null) {
                    item { HistoryBanner(gachaHistory!!, viewModel) }
                }

                if (gachaData != null) {
                    item { GachaSummary(gachaData!!) }
                    if (gachaData!!.predictions.isNotEmpty()) {
                        item { PredictionSection(gachaData!!.predictions, gachaData!!.records) }
                    }
                    if (gachaData!!.predictions.isEmpty()) {
                        item {
                            GlassCard(accentColor = NeonAmber) {
                                Text(
                                    "No pity predictions available — need character or weapon banner pulls.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonAmber,
                                )
                            }
                        }
                    }
                } else if (conveneUrl != null) {
                    item {
                        GlassCard(accentColor = NeonAmber) {
                            Text(
                                "URL extracted. Tap 'Fetch Gacha History' to load pull data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonAmber,
                            )
                        }
                    }
                }

                item {
                    MiniLogViewer(logs)
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun GachaSummary(data: GachaData) {
    GlassCard(accentColor = NeonGold) {
        Text("Summary", style = MaterialTheme.typography.labelMedium, color = NeonGold.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("${data.totalPulls}", "Pulls", NeonCyan)
            StatItem("${data.fiveStars}", "★5", NeonGold)
            StatItem("${data.fourStars}", "★4", NeonPurple)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem(if (data.avgPity5 > 0) "%.1f".format(data.avgPity5) else "—", "Avg ★5 Pity", NeonGold)
            StatItem(if (data.avgPity4 > 0) "%.1f".format(data.avgPity4) else "—", "Avg ★4 Pity", NeonPurple)
        }
    }

    for (pool in GachaPool.ALL) {
        val poolRecords = data.records.filter { it.cardPoolType == pool.type }
        if (poolRecords.isEmpty()) continue

        val pool5 = poolRecords.count { it.qualityLevel == 5 }
        val pool4 = poolRecords.count { it.qualityLevel == 4 }
        val accent =
            when {
                pool5 > 0 -> NeonGold
                pool4 > 0 -> NeonPurple
                else -> NeonCyan
            }

        GlassCard(accentColor = accent) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    pool.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${poolRecords.size} pulls",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pool5 > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("★5×$pool5", style = MaterialTheme.typography.labelSmall, color = NeonGold)
                }
                if (pool4 > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("★4×$pool4", style = MaterialTheme.typography.labelSmall, color = NeonPurple)
                }
            }
            Spacer(Modifier.height(6.dp))

            poolRecords.take(50).forEach { record ->
                RecordRow(record)
            }
            if (poolRecords.size > 50) {
                Text(
                    "+ ${poolRecords.size - 50} more...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PredictionSection(
    predictions: List<PityPrediction>,
    @Suppress("UNUSED_PARAMETER") allRecords: List<GachaRecord>,
) {
    for (pred in predictions) {
        val accent =
            when (pred.status) {
                "Guaranteed" -> NeonGold
                "50/50" -> NeonAmber
                else -> NeonCyan
            }
        GlassCard(accentColor = accent) {
            Text("Next ★5 Prediction — ${pred.poolLabel}", style = MaterialTheme.typography.labelMedium, color = accent.copy(alpha = 0.7f))
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val statusLabel =
                    when (pred.status) {
                        "Guaranteed" -> "Guaranteed ★5 Limited"
                        "50/50" -> "50/50 — Win or Lose?"
                        "75/25" -> "75/25 — Weapon Banner"
                        else -> pred.status
                    }
                Text(statusLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = accent)
            }

            if (pred.isInSoftPity) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "⚠ In soft pity (pull ${pred.pullsSinceLastFive}/${pred.hardPity})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            if (pred.status != "75/25" && pred.lastFiveStarName.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Last ★5: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(pred.lastFiveStarName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = NeonGold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pred.lastFiveStarTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(4.dp))

                if (pred.status == "Guaranteed") {
                    Text(
                        "You lost 50/50 to ${pred.lastFiveStarName}. Next ★5 is guaranteed!",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonGold,
                    )
                } else if (pred.status == "50/50") {
                    Text(
                        "You won 50/50 on ${pred.lastFiveStarName}. Next ★5 is 50/50.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonAmber,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${pred.pullsSinceLastFive}", "Pulls Since ★5", accent)
                StatItem("${pred.pullsUntilHardPity}", "To Hard Pity", if (pred.isInSoftPity) NeonAmber else NeonCyan)
                StatItem("~${pred.estimatedNextFive}", "Est. Next ★5", NeonGold)
            }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${pred.pullsSinceLastFourStar}", "Since ★4", MaterialTheme.colorScheme.onSurfaceVariant)
                StatItem("~${pred.estimatedNextFourStar}", "Est. Next ★4", MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (pred.status == "50/50") {
                Spacer(Modifier.height(6.dp))
                Text(
                    "If you lose 50/50 → next is guaranteed.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun HistoryBanner(
    entry: GachaHistoryEntry,
    viewModel: MainViewModel,
) {
    val remainingHrs = viewModel.gachaHistoryRemainingHours()
    GlassCard(accentColor = NeonCyan) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.History, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Previous Result", style = MaterialTheme.typography.labelMedium, color = NeonCyan.copy(alpha = 0.7f))
                Text(
                    "${entry.totalPulls} pulls · ${entry.fiveStars}★5 · expires in ${remainingHrs}h",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                onClick = { viewModel.restoreGachaFromHistory() },
                modifier = Modifier.weight(1f),
                accentColor = NeonCyan,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Load", fontWeight = FontWeight.Bold)
            }
            GlassButton(
                onClick = { viewModel.clearGachaHistory() },
                modifier = Modifier.weight(1f),
                accentColor = NeonRed.copy(alpha = 0.6f),
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clear", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    accent: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecordRow(record: GachaRecord) {
    val color =
        when (record.qualityLevel) {
            5 -> NeonGold
            4 -> NeonPurple
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            record.name,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = if (record.qualityLevel >= 4) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (record.count > 1) {
            Text("×${record.count}", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
            Spacer(Modifier.width(6.dp))
        }
        val t = record.time.substringAfter(" ").take(5)
        Text(t, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}
