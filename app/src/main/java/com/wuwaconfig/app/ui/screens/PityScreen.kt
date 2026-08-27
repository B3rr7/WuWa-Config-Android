package com.wuwaconfig.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaHistoryEntry
import com.wuwaconfig.app.model.GachaPool
import com.wuwaconfig.app.model.GachaRecord
import com.wuwaconfig.app.model.PityPrediction
import com.wuwaconfig.app.ui.GachaViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.components.MiniLogViewer
import com.wuwaconfig.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PityScreen(
    viewModel: GachaViewModel,
    onBack: () -> Unit,
    backendStatus: BackendStatus,
    isApplying: Boolean,
) {
    val conveneUrl by viewModel.conveneUrl.collectAsStateWithLifecycle()
    val conveneUrlLoading by viewModel.conveneUrlLoading.collectAsStateWithLifecycle()
    val gachaData by viewModel.gachaData.collectAsStateWithLifecycle()
    val gachaLoading by viewModel.gachaLoading.collectAsStateWithLifecycle()
    val gachaHistory by viewModel.gachaHistory.collectAsStateWithLifecycle()
    val gachaError by viewModel.gachaError.collectAsStateWithLifecycle()

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = { Text("Pity Tracker", fontWeight = FontWeight.Bold) },
                    accentColor = NeonPurple,
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonPurple) }
                    },
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
                        enabled = backendStatus.connected && !isApplying && !conveneUrlLoading && !gachaLoading,
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
                            onClick = { viewModel.stopReading() },
                            modifier = Modifier.fillMaxWidth(),
                            accentColor = NeonRed,
                            contentColor = Color.White,
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Stop Reading", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (conveneUrlLoading || gachaLoading) {
                    item {
                        PityLoadingAnimation(
                            if (conveneUrlLoading) "Reading Client.log for Convene URL..." else "Fetching pull history from Kuro servers...",
                        )
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

                if (gachaError != null) {
                    item {
                        GlassCard(accentColor = NeonRed) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = NeonRed,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    gachaError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonRed,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = { viewModel.clearGachaError() }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = NeonRed.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (gachaData != null) {
                    item { GachaSummary(gachaData!!) }
                    if (gachaData!!.predictions.isNotEmpty()) {
                        item { PredictionSection(gachaData!!.predictions) }
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
                    item {
                        Text(
                            "PULL HISTORY",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        )
                    }
                    for (pool in GachaPool.ALL) {
                        val poolRecords = gachaData!!.records.filter { it.cardPoolType == pool.type }
                        if (poolRecords.isEmpty()) continue
                        item { PoolHistoryHeader(pool, poolRecords) }
                        items(poolRecords.size, key = { idx -> "${pool.type}-$idx" }) { idx -> RecordRow(poolRecords[idx]) }
                        item { Spacer(Modifier.height(8.dp)) }
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
                    MiniLogViewer()
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun GachaSummary(data: GachaData) {
    GlassCard(accentColor = NeonGold) {
        Text(
            "PITY OVERVIEW",
            style = MaterialTheme.typography.labelMedium,
            color = NeonGold.copy(alpha = 0.8f),
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            HeroStat("${data.totalPulls}", "Total Pulls", NeonCyan)
            HeroStat("${data.fiveStars}", "★5", NeonGold)
            HeroStat("${data.fourStars}", "★4", NeonPurple)
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem(if (data.avgPity5 > 0) "%.1f".format(data.avgPity5) else "—", "Avg ★5 Pity", NeonGold)
            StatItem(if (data.avgPity4 > 0) "%.1f".format(data.avgPity4) else "—", "Avg ★4 Pity", NeonPurple)
        }
    }
}

@Composable
private fun PoolHistoryHeader(
    pool: GachaPool,
    records: List<GachaRecord>,
) {
    val pool5 = records.count { it.qualityLevel == 5 }
    val pool4 = records.count { it.qualityLevel == 4 }
    val accent =
        when {
            pool5 > 0 -> NeonGold
            pool4 > 0 -> NeonPurple
            else -> NeonCyan
        }
    GlassCard(accentColor = accent) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                pool.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${records.size} pulls",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pool5 > 0) {
                Spacer(Modifier.width(8.dp))
                Text("★5×$pool5", style = MaterialTheme.typography.labelSmall, color = NeonGold, fontWeight = FontWeight.Bold)
            }
            if (pool4 > 0) {
                Spacer(Modifier.width(6.dp))
                Text("★4×$pool4", style = MaterialTheme.typography.labelSmall, color = NeonPurple)
            }
        }
    }
}

@Composable
private fun PredictionSection(predictions: List<PityPrediction>) {
    Text(
        "NEXT ★5 PREDICTION",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        letterSpacing = 2.sp,
        modifier = Modifier.padding(start = 4.dp),
    )
    Spacer(Modifier.height(10.dp))

    for (pred in predictions) {
        val accent =
            when (pred.status) {
                "Guaranteed" -> NeonGold
                "50/50" -> NeonAmber
                else -> NeonCyan
            }
        GlassCard(accentColor = accent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    pred.poolLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val statusLabel =
                    when (pred.status) {
                        "Guaranteed" -> "Guaranteed"
                        "50/50" -> "50 / 50"
                        "75/25" -> "75 / 25"
                        else -> pred.status
                    }
                StatusPill(statusLabel, accent)
            }
            Spacer(Modifier.height(14.dp))
            PityProgressBar(
                pulls = pred.pullsSinceLastFive,
                hardPity = pred.hardPity,
                softThreshold = pred.softPityThreshold,
                accent = accent,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${pred.pullsSinceLastFive} / ${pred.hardPity}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    "Soft ${pred.softPityThreshold} · Hard ${pred.hardPity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            if (pred.isInSoftPity) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NeonAmber.copy(alpha = 0.15f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Soft pity active — your ★5 rate is boosted!",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (pred.status != "75/25") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Last ★5: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        pred.lastFiveStarName.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonGold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pred.lastFiveStarTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(4.dp))

                val subject = pred.currentCharacterName.ifEmpty { pred.poolLabel }
                if (pred.status == "Guaranteed") {
                    Text(
                        "$subject is guaranteed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonGold,
                    )
                } else if (pred.status == "50/50") {
                    Text(
                        "$subject is 50 / 50.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonAmber,
                    )
                } else {
                    Text(
                        "Not enough pulls yet to estimate — keep wishing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Simple estimate from your pull history — not a guarantee it will happen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(10.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${pred.pullsSinceLastFive}", "Since ★5", accent)
                StatItem("${pred.pullsUntilHardPity}", "To Hard", if (pred.isInSoftPity) NeonAmber else NeonCyan)
                StatItem("~${pred.estimatedNextFive}", "Est. ★5", NeonGold)
            }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${pred.pullsSinceLastFourStar}", "Since ★4", MaterialTheme.colorScheme.onSurfaceVariant)
                StatItem("~${pred.estimatedNextFourStar}", "Est. ★4", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HistoryBanner(
    entry: GachaHistoryEntry,
    viewModel: GachaViewModel,
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
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
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

@Composable
private fun PityProgressBar(
    pulls: Int,
    hardPity: Int,
    softThreshold: Int,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val fillFrac = (pulls.toFloat() / hardPity).coerceIn(0f, 1f)
    val softFrac = (softThreshold.toFloat() / hardPity).coerceIn(0f, 1f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxWidth(1f - softFrac)
                .background(
                    Brush.horizontalGradient(
                        listOf(NeonAmber.copy(alpha = 0.22f), NeonAmber.copy(alpha = 0.45f)),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fillFrac)
                .background(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.65f), accent)),
                    RoundedCornerShape(7.dp),
                ),
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun HeroStat(
    value: String,
    label: String,
    accent: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = accent)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PityLoadingAnimation(text: String) {
    GlassCard(accentColor = NeonPurple) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val orbs = listOf(NeonCyan, NeonGold, NeonPurple)
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
        }
    }
}

@Composable
private fun BouncingOrb(
    color: Color,
    index: Int,
) {
    val transition = rememberInfiniteTransition(label = "orb$index")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 520, delayMillis = index * 160, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "offset$index",
    )
    Box(
        Modifier
            .size(14.dp)
            .offset(y = offset.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.radialGradient(listOf(color, color.copy(alpha = 0.35f))),
            ),
    )
}
