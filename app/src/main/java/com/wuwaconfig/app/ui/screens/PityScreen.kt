package com.wuwaconfig.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.config.GachaStats
import com.wuwaconfig.app.config.KuroGuide
import com.wuwaconfig.app.config.OfficialCharacter
import com.wuwaconfig.app.config.gameProfile
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaHistoryEntry
import com.wuwaconfig.app.model.GachaPoolType
import com.wuwaconfig.app.model.GachaRecord
import com.wuwaconfig.app.model.SsrInterval
import com.wuwaconfig.app.ui.GachaViewModel
import com.wuwaconfig.app.ui.components.BouncingOrb
import com.wuwaconfig.app.ui.components.GachaAvatar
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.components.MiniLogViewer
import com.wuwaconfig.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
/**
 * Astrites/Lunites per single pull, from the game profile.
 *
 * File level because two composables need it and the literal `160` was hardcoded
 * in each. Retuning `currencyPerPull` in the asset used to leave the per-pool
 * "Total Spent" and the overview "Total Astrites" disagreeing, because GachaApi
 * read the profile while these two sites did not.
 */
private val costPerPull: Int = gameProfile().currencyPerPull

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
    // Pre-grouped in the ViewModel on gachaData; the screen used to filter the
    // whole record list once per pool inside the LazyColumn content lambda.
    val recordsByPool by viewModel.recordsByPool.collectAsStateWithLifecycle()

    // Which view of the loaded history is showing. `rememberSaveable` rather than
    // `remember` so the selection survives rotation — losing it drops the player
    // back onto the default tab after an orientation change mid-pull.
    var selectedTab by rememberSaveable { mutableStateOf(GachaTab.VISUAL) }

    // Kuro's official guide, for current character banners and first-party art.
    // Fetched once per screen entry, not per tab: the answer changes on Kuro's
    // schedule, not on a tab switch, and the player may flip between tabs often.
    //
    // Failures are absorbed rather than surfaced — a missing featured strip is a
    // degradation, not an error the player can act on, and the bundled wiki art
    // already covers every portrait it would have drawn.
    var officialCharacters by remember { mutableStateOf<List<OfficialCharacter>>(emptyList()) }
    LaunchedEffect(Unit) {
        KuroGuide.fetchCharacters()
            .onSuccess { fetched ->
                officialCharacters = fetched
                GachaAvatar.setOfficialArt(
                    fetched.mapNotNull { c -> c.cardPictureUrl.takeIf { it.isNotBlank() }?.let { c.name to it } }.toMap(),
                )
            }
    }

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
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = padding,
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

                val data = gachaData
                if (data != null) {
                    // The data body is split across tabs rather than run as one long
                    // scroll. WutheringWavesTool does the same with 直观 / 详情 /
                    // 表格 / 统计, and the reason holds on a phone: the visual, lifetime
                    // and raw-record views answer different questions, and stacking them
                    // buries the one a player opens the screen for — where this banner
                    // stands right now.
                    item { GachaTabBar(selectedTab, onSelect = { selectedTab = it }) }

                    // Current game state, so it leads the player's own history
                    // below. Empty until the fetch resolves, and stays empty if it
                    // never does — the tab is still fully usable without it.
                    item { FeaturedCharactersStrip(officialCharacters) }

                    // Scoped to the item because `data` is bound inside the list
                    // scope, and remembered so switching tabs does not re-walk the
                    // history on every recomposition.
                    item {
                        StandardBannerStrip(remember(data) { GachaStats.aggregate(data) })
                    }

                    when (selectedTab) {
                        GachaTab.VISUAL -> {
                            item { GachaVisualSection(data) }
                        }

                        GachaTab.STATS -> {
                            item { GachaSummary(data) }
                            item { GachaStatsSection(data) }
                        }

                        GachaTab.TABLE -> {
                            item {
                                Text(
                                    "PULL HISTORY",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                )
                            }
                            for (poolType in GachaPoolType.ALL) {
                                val poolRecords = recordsByPool[poolType.type].orEmpty()
                                if (poolRecords.isEmpty()) continue
                                item { PoolHistoryHeader(poolType, poolRecords) }
                                items(poolRecords.size, key = { idx -> "${poolType.type}-$idx" }) { idx ->
                                    RecordRow(poolRecords[idx])
                                }
                                item { Spacer(Modifier.height(8.dp)) }
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
                    MiniLogViewer()
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * The views of a loaded gacha history, mirroring WutheringWavesTool's analysis tabs.
 *
 * [VISUAL] is the default because it answers the question that brings a player to
 * this screen: what does the current banner look like. [STATS] is the lifetime
 * record and [TABLE] the raw pulls.
 *
 * Deliberately three, not WWT's four — WWT also has a cloud backup, which has no
 * equivalent here.
 */
private enum class GachaTab(val label: String, val accent: Color) {
    VISUAL("Visual", NeonCyan),
    STATS("Stats", NeonGold),
    TABLE("Table", NeonPurple),
}

/**
 * The tab selector.
 *
 * A segmented row of pills rather than WWT's toggle group: the same affordance, sized
 * for touch. Selected state is carried by an accent fill plus a bold weight, so it
 * does not rely on colour alone.
 */
@Composable
private fun GachaTabBar(
    selected: GachaTab,
    onSelect: (GachaTab) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GachaTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) {
                            tab.accent.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        },
                    )
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) tab.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun GachaSummary(data: GachaData) {
    // Compute overall UP rate and non-banner rate across character banners
    val totalSsrFromPredictions = data.predictions.sumOf { it.ssrIntervals.size }
    val overallUpRate =
        if (totalSsrFromPredictions > 0) {
            data.predictions.sumOf { it.upRate * it.ssrIntervals.size }.toDouble() / totalSsrFromPredictions
        } else {
            0.0
        }
    val overallNonBannerRate =
        if (totalSsrFromPredictions > 0) {
            data.predictions.sumOf { it.nonBannerRate * it.ssrIntervals.size }.toDouble() / totalSsrFromPredictions
        } else {
            0.0
        }
    val overallAvgPity =
        if (data.predictions.isNotEmpty()) {
            data.predictions.map { it.avgPityThisPool }.filter { it > 0 }.average()
        } else {
            0.0
        }
    val totalPullsOverall = data.totalPulls
    val totalCostFromRecords = data.records.sumOf { (it.count.coerceAtLeast(1) * costPerPull).toLong() }

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
        Spacer(Modifier.height(10.dp))
        // Overall character banner stats
        if (overallAvgPity > 0 || overallUpRate > 0 || overallNonBannerRate > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (overallAvgPity > 0) {
                    StatItem("${"%.1f".format(overallAvgPity)}", "Avg ★5 Pity (Char)", NeonCyan)
                }
                if (overallUpRate > 0) {
                    StatItem("${"%.0f".format(overallUpRate * 100)}%", "UP Rate", NeonGold)
                }
                if (overallNonBannerRate > 0) {
                    StatItem("${"%.0f".format(overallNonBannerRate * 100)}%", "50/50 Loss", NeonAmber)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        // Total Cost (calculated from all records, not just predictions)
        val totalCostOverall = totalCostFromRecords
        if (totalCostOverall > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${formatNumber(totalCostOverall)}", "Total Astrites", NeonPurple)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PoolHistoryHeader(
    pool: GachaPoolType,
    records: List<GachaRecord>,
) {
    val totalPulls = records.sumOf { it.count.coerceAtLeast(1) }
    val pool5 = records.filter { it.qualityLevel == 5 }.sumOf { it.count.coerceAtLeast(1) }
    val pool4 = records.filter { it.qualityLevel == 4 }.sumOf { it.count.coerceAtLeast(1) }
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
                "$totalPulls pulls",
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

// NOT @Composable: these read no composition state, so the annotation only made
// every call site an (incorrect) composable call.
private fun formatCost(cost: Long): String {
    return formatNumber(cost) + " Astrites"
}

/**
 * Groups a number into thousands.
 *
 * `internal` because [GachaStatsSection] is a separate file in this package and
 * formats Astrites the same way. Two formatters for one convention is how "10.7K"
 * and "10700" end up on the same screen.
 */
internal fun formatNumber(number: Long): String {
    return if (number >= 10000) {
        "%.1fK".format(number / 1000.0).replace(".0", "")
    } else if (number >= 1000) {
        "%.1fK".format(number / 1000.0)
    } else {
        number.toString()
    }
}

@Composable
private fun SsrIntervalsTable(intervals: List<SsrInterval>) {
    GlassCard(accentColor = NeonGold) {
        Text(
            "★5 PULL INTERVALS",
            style = MaterialTheme.typography.labelMedium,
            color = NeonGold.copy(alpha = 0.8f),
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("#", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
            Text("5★ Name", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Pity", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(50.dp))
            Text("Date", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        }
        Spacer(Modifier.height(6.dp))

        // Rows
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            intervals.forEachIndexed { index, interval ->
                val pityColor = if (interval.pity >= 75) NeonRed else if (interval.pity >= 66) NeonAmber else NeonGold
                val nameColor = if (interval.pity >= 75) NeonRed else if (interval.pity >= 66) NeonAmber else NeonGold

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
                    Text(
                        interval.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = nameColor,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${interval.pity}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = pityColor,
                        modifier = Modifier.width(50.dp),
                    )
                    Text(
                        if (interval.time.length >= 10) interval.time.substring(0, 10) else interval.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(90.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryBanner(
    entry: GachaHistoryEntry,
    viewModel: GachaViewModel,
) {
    // System.currentTimeMillis() must not be read during composition — it is an
    // impure, non-snapshot read, so the age never ticked. Refresh the
    // ViewModel's StateFlow on a timer instead.
    LaunchedEffect(entry.fetchedAt) {
        while (true) {
            viewModel.refreshGachaHistoryAge()
            delay(60_000)
        }
    }
    val ageHrs by viewModel.gachaHistoryAgeHours.collectAsStateWithLifecycle()
    val isStale by viewModel.gachaHistoryIsStaleFlow.collectAsStateWithLifecycle()
    GlassCard(accentColor = NeonCyan) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.History, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Previous Result", style = MaterialTheme.typography.labelMedium, color = NeonCyan.copy(alpha = 0.7f))
                Text(
                    buildString {
                        append("${entry.totalPulls} pulls · ${entry.fiveStars}★5")
                        // Null age means a cache written before fetchedAt existed.
                        // Saying "0h ago" would be a lie; staying silent is honest.
                        ageHrs?.let { append(" · fetched ${it}h ago") }
                        if (isStale) append(" · may be behind, re-fetch for current numbers")
                    },
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

/**
 * A figure with a caption under it.
 *
 * `internal` rather than private because [GachaStatsSection] is a separate file in
 * this package and reuses it. Duplicating a nine-line composable to avoid widening
 * its visibility would leave two spellings of the same visual token to drift.
 */
@Composable
internal fun StatItem(
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
        val t =
            if (record.time.length >= 16 && record.time[10] == ' ') {
                record.time.substring(11, 16)
            } else {
                ""
            }
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
