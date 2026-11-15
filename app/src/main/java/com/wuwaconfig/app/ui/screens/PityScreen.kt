package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.config.GachaApi
import com.wuwaconfig.app.config.KuroGuide
import com.wuwaconfig.app.config.OfficialCharacter
import com.wuwaconfig.app.config.OfficialStatus
import com.wuwaconfig.app.config.gameProfile
import com.wuwaconfig.app.model.BannerFamily
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaHistoryEntry
import com.wuwaconfig.app.model.GachaPoolType
import com.wuwaconfig.app.model.GachaRecord
import com.wuwaconfig.app.model.PityPrediction
import com.wuwaconfig.app.ui.GachaViewModel
import com.wuwaconfig.app.ui.anim.GeoLoadingCard
import com.wuwaconfig.app.ui.anim.GeoLoadingKind
import com.wuwaconfig.app.ui.components.GachaAvatar
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.components.MiniLogViewer
import com.wuwaconfig.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Astrites/Lunites per single pull, from the game profile.
 *
 * File level so the banner card and the history footer agree; the literal `160`
 * used to be hardcoded in two places and could disagree when the asset changed.
 */
private val costPerPull: Int = gameProfile().currencyPerPull

/** How many recent ★5s and history rows this simplified screen shows. */
private const val RECENT_FIVE_STARS = 8
private const val HISTORY_ROWS = 120

/**
 * The Pity Tracker, kept deliberately to one scroll.
 *
 * It answers three questions and nothing else:
 *  1. Which banner is running and where do I stand on it (pity, 50/50 or guaranteed)?
 *  2. What were my last ★5 pulls, and did each one win or lose the 50/50?
 *  3. What does my recent pull history look like?
 *
 * The data comes from two places, by design: the **game log / Kuro record API**
 * (the player's own pulls — the authority on pity and history) and **Kuro's guide**
 * (which characters are featured right now, for the banner's portraits and names).
 * The guide is enrichment only; every number is computed from the player's pulls.
 */
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

    // Kuro's official guide, for the current banner's featured characters and art.
    // Fetched once per screen entry; a failure just means the banner card falls back
    // to the name carried on the player's own pull history.
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

    // The weapon banner features the signature weapon of the main featured 5★
    // resonator. The guide's build page exposes it as the first recommended weapon,
    // which is the only first-party source for the *live* weapon banner — the avatar
    // list carries no weapon data at all.
    var featuredWeapon by remember { mutableStateOf<KuroGuide.FeaturedWeapon?>(null) }
    LaunchedEffect(officialCharacters) {
        if (officialCharacters.isEmpty()) return@LaunchedEffect
        val main =
            officialCharacters
                .filter { it.status == OfficialStatus.NEWLY_LAUNCHED || it.status == OfficialStatus.UP }
                .firstOrNull { it.star >= 5 }
                ?: return@LaunchedEffect
        KuroGuide.fetchFeaturedWeapon(main.roleGbId).onSuccess { featuredWeapon = it }
    }

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = { Text("Pity Tracker", fontWeight = FontWeight.Bold) },
                    accentColor = NeonPurple,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonPurple)
                        }
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
                    item {
                        GeoLoadingCard(
                            kind = GeoLoadingKind.PITY,
                            text = if (conveneUrlLoading) "Reading Client.log for the Convene URL..." else "Fetching pull history...",
                            accentColor = NeonPurple,
                        )
                    }
                }

                if (!backendStatus.connected && gachaData == null) {
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

                if (gachaError != null) {
                    item {
                        GlassCard(accentColor = NeonRed) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = NeonRed, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    gachaError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonRed,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.clearGachaError() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = NeonRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                if (gachaHistory != null && gachaData == null) {
                    item { HistoryBanner(gachaHistory!!, viewModel) }
                }

                val data = gachaData
                if (data != null) {
                    val active = activeFamily(data.records)
                    val standardNames = standardFiveStarNames(data.records)

                    item { BannersOverview(data, active, officialCharacters, featuredWeapon) }
                    item { RecentFiveStarsCard(data.records, standardNames) }
                    item { InsightsCard(data.records) }
                    item { PullHistoryCard(data.records) }
                } else if (conveneUrl != null) {
                    item {
                        GlassCard(accentColor = NeonAmber) {
                            Text(
                                "URL extracted. Tap 'Fetch Gacha History' to load your pulls.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonAmber,
                            )
                        }
                    }
                }

                item { MiniLogViewer() }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ─────────── banner overview: character, weapon, standard ───────────

/**
 * One card per banner family, so the screen shows the Featured Resonator banner,
 * the Featured Weapon banner and the always-open Standard pool together — not only
 * whichever one the player last pulled on. The active family (from the newest pull
 * in the game log) is badged CURRENT.
 */
@Composable
private fun BannersOverview(
    data: GachaData,
    activeFamily: BannerFamily?,
    officialCharacters: List<OfficialCharacter>,
    featuredWeapon: KuroGuide.FeaturedWeapon?,
) {
    val banners = remember(data, officialCharacters, featuredWeapon) { bannerPities(data, officialCharacters, featuredWeapon) }
    val currentIndex = banners.indexOfFirst { it.family == activeFamily }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        banners.forEachIndexed { index, banner ->
            BannerCard(banner, isCurrent = index == currentIndex)
        }
    }
}

@Composable
private fun BannerCard(
    banner: BannerPity,
    isCurrent: Boolean,
) {
    val accent =
        when (banner.family) {
            BannerFamily.CHARACTER_EVENT -> NeonGold
            BannerFamily.WEAPON_EVENT -> NeonCyan
            BannerFamily.STANDARD -> NeonPurple
            BannerFamily.BEGINNER -> NeonAmber
        }
    val (statusLabel, statusColor) =
        when (banner.family) {
            BannerFamily.CHARACTER_EVENT -> if (banner.guaranteed) "GUARANTEED" to NeonGreen else "50 / 50" to NeonAmber
            BannerFamily.WEAPON_EVENT -> "100% UP" to NeonGreen
            BannerFamily.STANDARD -> "PERMANENT" to NeonCyan
            BannerFamily.BEGINNER -> "NOVICE" to NeonAmber
        }

    GlassCard(accentColor = accent) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(banner.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
            Spacer(Modifier.width(8.dp))
            if (isCurrent) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text("CURRENT", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            StatusChip(statusLabel, statusColor)
        }
        Spacer(Modifier.height(10.dp))

        if (banner.featured.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                banner.featured.forEach { unit -> FeaturedUnit(unit) }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (banner.empty) {
            Text(
                "No pulls on this banner yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@GlassCard
        }

        val frac = (banner.pullsSinceLastFive.toFloat() / banner.hardPity.coerceAtLeast(1)).coerceIn(0f, 1f)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${banner.pullsSinceLastFive} / ${banner.hardPity}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Spacer(Modifier.width(8.dp))
            Text("to ★5", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .background(if (banner.guaranteed) NeonGreen else accent),
            )
        }
        if (banner.worstCase != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Worst case: ${banner.worstCase} pulls (${formatNumber(banner.worstCost ?: 0)} ◎)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 4★ pity alongside the 5★ line: "or better every 10".
        Spacer(Modifier.height(6.dp))
        Text(
            "★4 pity: ${banner.pullsSinceLastFour} / ${gameProfile().fourStarGuarantee}",
            style = MaterialTheme.typography.labelSmall,
            color = NeonPurple,
        )
    }
}

@Composable
private fun FeaturedUnit(unit: Featurable) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (unit.imageUrl != null) {
                AsyncImage(
                    model = unit.imageUrl,
                    contentDescription = unit.name,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(50)),
                )
            } else {
                GachaAvatar(name = unit.name, resourceType = unit.resourceType, size = 56.dp)
            }
            if (unit.isNew) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonAmber)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text("NEW", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            unit.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 92.dp),
        )
    }
}

@Composable
private fun StatusChip(
    label: String,
    color: Color,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

// ─────────── recent five-stars, won or lost ───────────

@Composable
private fun RecentFiveStarsCard(
    records: List<GachaRecord>,
    standardNames: Set<String>,
) {
    val recent =
        remember(records) {
            records.filter { it.qualityLevel == 5 }.sortedByDescending { it.time }.take(RECENT_FIVE_STARS)
        }
    if (recent.isEmpty()) return

    GlassCard(accentColor = NeonGold) {
        Text("RECENT ★5", style = MaterialTheme.typography.labelMedium, color = NeonGold, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        recent.forEach { record ->
            val outcome = fiveStarOutcome(record, standardNames)
            FiveStarRow(record, outcome)
        }
    }
}

@Composable
private fun FiveStarRow(
    record: GachaRecord,
    outcome: FiveStarOutcome,
) {
    val (badge, color) =
        when (outcome) {
            FiveStarOutcome.WON -> "WON" to NeonGreen
            FiveStarOutcome.LOST -> "LOST" to NeonRed
            FiveStarOutcome.STANDARD -> "STANDARD" to NeonCyan
            FiveStarOutcome.NOVICE -> "NOVICE" to NeonAmber
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GachaAvatar(name = record.name, resourceType = portraitType(record), size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                record.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = NeonGold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val pool = GachaPoolType.fromType(record.cardPoolType)?.label ?: "Banner"
            Text(
                pool,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(badge, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────── statistics ───────────

/**
 * Pull statistics in the shape wuwatracker shows: totals, pulls per rarity, pulls
 * per day, and average 5★ pity against the ~55.6 expected from Kuro's 1.8 % rate.
 *
 * The "luck" label is a *local* comparison to that expected mean, not a percentile
 * against other accounts — the app has no cross-player dataset to rank against, and
 * inventing one would be dishonest.
 */
@Composable
private fun InsightsCard(records: List<GachaRecord>) {
    if (records.isEmpty()) return
    val total = records.sumOf { it.count.coerceAtLeast(1) }
    val c5 = records.filter { it.qualityLevel == 5 }.sumOf { it.count.coerceAtLeast(1) }
    val c4 = records.filter { it.qualityLevel == 4 }.sumOf { it.count.coerceAtLeast(1) }
    val c3 = records.filter { it.qualityLevel == 3 }.sumOf { it.count.coerceAtLeast(1) }
    val avg5 = remember(records) { GachaApi.calculateAvgPity(records, 5) }
    val days = remember(records) { pullDaySpan(records) }
    val perDay = if (days > 0) total.toFloat() / days else 0f
    val expected = 55.6
    val (luckLabel, luckColor) =
        when {
            avg5 <= 0.0 -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
            avg5 < expected * 0.9 -> "Lucky" to NeonGreen
            avg5 > expected * 1.1 -> "Unlucky" to NeonRed
            else -> "Average" to NeonAmber
        }
    GlassCard(accentColor = NeonGold) {
        Text("STATISTICS", style = MaterialTheme.typography.labelMedium, color = NeonGold, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("$total", "Pulls", NeonCyan)
            StatItem("$c5", "★5", NeonGold)
            StatItem("$c4", "★4", NeonPurple)
            StatItem("$c3", "★3", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("%.1f".format(perDay), "Pulls / Day", NeonCyan)
            StatItem(if (avg5 > 0) "%.1f".format(avg5) else "—", "Avg ★5 Pity", NeonGold)
            StatItem(luckLabel, "Luck (vs ~55.6)", luckColor)
        }
    }
}

/** Inclusive day span between the first and last pull, or 0 when unknown. */
private fun pullDaySpan(records: List<GachaRecord>): Int {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
    val times = records.mapNotNull { runCatching { fmt.parse(it.time)?.time }.getOrNull() }
    if (times.isEmpty()) return 0
    val spanMs = (times.maxOrNull() ?: 0L) - (times.minOrNull() ?: 0L)
    return (spanMs / 86_400_000L).toInt() + 1
}

// ─────────── pull history ───────────

/** A history row with its position and pity inside its own banner. */
private data class HistoryRow(
    val record: GachaRecord,
    val pullNo: Int,
    val pity: Int,
    val seq: Long,
)

/**
 * Assigns each record its pull number and the pity counter at that pull, walked
 * oldest→newest within each banner family. `count > 1` rows advance the counter by
 * their collapse count, matching how the API represents a 10-pull.
 */
private fun enrichHistory(records: List<GachaRecord>): List<HistoryRow> {
    val rows = mutableListOf<HistoryRow>()
    var seq = 0L
    for ((_, recs) in records.groupBy { GachaPoolType.fromType(it.cardPoolType)?.family }) {
        var pullNo = 0
        var pity = 0
        for (record in recs.sortedBy { it.time }) {
            val n = record.count.coerceAtLeast(1)
            pullNo += n
            pity += n
            rows += HistoryRow(record, pullNo, pity, seq++)
            if (record.qualityLevel == 5) pity = 0
        }
    }
    return rows
}

@Composable
private fun PullHistoryCard(records: List<GachaRecord>) {
    if (records.isEmpty()) return
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    val rows = remember(records) { enrichHistory(records) }
    val ordered =
        remember(rows, newestFirst) {
            val sorted =
                if (newestFirst) {
                    rows.sortedWith(compareByDescending<HistoryRow> { it.record.time }.thenByDescending { it.seq })
                } else {
                    rows.sortedWith(compareBy<HistoryRow> { it.record.time }.thenBy { it.seq })
                }
            sorted.take(HISTORY_ROWS)
        }
    GlassCard(accentColor = NeonPurple) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("PULL HISTORY", style = MaterialTheme.typography.labelMedium, color = NeonPurple, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("last ${ordered.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortChip("Newest First", newestFirst) { newestFirst = true }
            SortChip("Since Last Appearance", !newestFirst) { newestFirst = false }
        }
        Spacer(Modifier.height(8.dp))
        ordered.forEach { RecordRow(it) }
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) NeonPurple.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) NeonPurple else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun RecordRow(row: HistoryRow) {
    val record = row.record
    val color =
        when (record.qualityLevel) {
            5 -> NeonGold
            4 -> NeonPurple
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GachaAvatar(name = record.name, resourceType = portraitType(record), size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                record.name,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = if (record.qualityLevel >= 4) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Pull #${row.pullNo} · Pity ${row.pity}" + if (record.count > 1) " · ×${record.count}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
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

// ─────────── derived data ───────────

/** The banner family the player most recently pulled on, from the newest record. */
private fun activeFamily(records: List<GachaRecord>): BannerFamily? = records.maxByOrNull { it.time }?.let { GachaPoolType.fromType(it.cardPoolType)?.family }

/** The prediction whose family matches [family]; null when that family has none. */
private fun predictionFor(
    data: GachaData,
    family: BannerFamily?,
): PityPrediction? {
    // No family (nothing pulled yet) falls back to any prediction so the screen
    // still has something to show. A KNOWN family must not borrow another family's
    // prediction — doing so made the Novice card print the character banner's
    // 80-pull ceiling (and its pity) instead of its own 50.
    if (family == null) return data.predictions.firstOrNull()
    return data.predictions.firstOrNull { it.familyLabel == family.label }
        ?: data.predictions.firstOrNull { GachaPoolType.fromType(it.poolType)?.family == family }
}

/**
 * Names known to be permanent-pool ★5s, used to spot a lost 50/50.
 *
 * Includes the **beginner** banner's characters as well as the standard pool's: a
 * permanent character first drawn from the novice banner is still a standard
 * character, so if that same name later appears on a featured banner it is a LOST
 * 50/50, not a win. Deriving the set from the standard pool alone left those
 * characters unrecognised and mislabelled every one of them as WON.
 */
private fun standardFiveStarNames(records: List<GachaRecord>): Set<String> =
    records
        .filter {
            val family = GachaPoolType.fromType(it.cardPoolType)?.family
            (family == BannerFamily.STANDARD || family == BannerFamily.BEGINNER) && it.qualityLevel == 5
        }
        .map { it.name }
        .toSet()

private fun portraitType(record: GachaRecord): String =
    record.resourceType.takeIf { it.isNotBlank() }
        ?: if (GachaPoolType.fromType(record.cardPoolType)?.family == BannerFamily.WEAPON_EVENT) "Weapon" else "Resonator"

/** A banner 5★ either won its 50/50, lost it, or is a permanent/novice draw. */
private enum class FiveStarOutcome { WON, LOST, STANDARD, NOVICE }

private fun fiveStarOutcome(
    record: GachaRecord,
    standardNames: Set<String>,
): FiveStarOutcome =
    when (GachaPoolType.fromType(record.cardPoolType)?.family) {
        BannerFamily.CHARACTER_EVENT -> if (record.name in standardNames) FiveStarOutcome.LOST else FiveStarOutcome.WON
        // Every weapon-banner 5★ is the featured weapon: there is no 50/50.
        BannerFamily.WEAPON_EVENT -> FiveStarOutcome.WON
        BannerFamily.STANDARD -> FiveStarOutcome.STANDARD
        BannerFamily.BEGINNER -> FiveStarOutcome.NOVICE
        null -> FiveStarOutcome.WON
    }

/** One featured unit on the current banner, with the art + name to draw it. */
private data class Featurable(
    val name: String,
    val resourceType: String,
    val isNew: Boolean,
    /** Official CDN art from Kuro's guide, used when the bundled portrait is absent. */
    val imageUrl: String? = null,
)

/**
 * The current banner's featured units.
 *
 * A character banner uses Kuro's guide (its NEW/UP list); a weapon banner has no
 * guide data, so it shows the most recent weapon the player actually pulled, which
 * is the only weapon name the app can trust.
 */
private fun featuredFor(
    family: BannerFamily,
    prediction: PityPrediction?,
    officialCharacters: List<OfficialCharacter>,
    featuredWeapon: KuroGuide.FeaturedWeapon?,
    allRecords: List<GachaRecord>,
): List<Featurable> =
    when (family) {
        BannerFamily.CHARACTER_EVENT -> {
            val fromGuide =
                KuroGuide.featuredCharacters(officialCharacters).map {
                    Featurable(it.name, "Resonator", it.status == OfficialStatus.NEWLY_LAUNCHED)
                }
            if (fromGuide.isNotEmpty()) {
                fromGuide
            } else {
                prediction?.currentFeaturedName?.takeIf { it.isNotBlank() }?.let { listOf(Featurable(it, "Resonator", false)) }.orEmpty()
            }
        }
        BannerFamily.WEAPON_EVENT -> {
            // The featured weapon is the signature weapon from the guide's build
            // page (first recommended). Its payload only carries a Chinese name, so
            // prefer the English name from the player's own gacha records when the
            // weapon id matches one they have pulled; the guide's official art is
            // used regardless. Falls back to the last pulled weapon if the guide is
            // unavailable.
            val weapon = featuredWeapon
            if (weapon != null) {
                val englishName =
                    allRecords.firstOrNull { it.resourceId != 0 && it.resourceId.toString() == weapon.gbId }?.name
                listOf(Featurable(englishName ?: weapon.name, "Weapon", false, weapon.pictureUrl))
            } else {
                prediction?.currentFeaturedName?.takeIf { it.isNotBlank() }?.let { listOf(Featurable(it, "Weapon", false)) }.orEmpty()
            }
        }
        else -> emptyList()
    }

/** One banner family's live pity state, ready to render. */
private data class BannerPity(
    val family: BannerFamily,
    /** Display label — the permanent pool is split into Resonator/Weapon cards. */
    val title: String,
    val pullsSinceLastFive: Int,
    val pullsSinceLastFour: Int,
    val hardPity: Int,
    val guaranteed: Boolean,
    val featured: List<Featurable>,
    val worstCase: Int?,
    val worstCost: Long?,
    val empty: Boolean,
)

/**
 * Pity state for every banner, in the order wuwatracker lists them: Featured
 * Resonator, Featured Weapon, Permanent Resonator, Permanent Weapon, Novice.
 *
 * The event families come from the predictions; the permanent and novice pity is
 * computed here from the records, because a permanent banner has no featured unit
 * and so no prediction of its own. The permanent pool is split into its Resonator
 * and Weapon halves by item kind — the real game keeps those two pity counters
 * apart, and lumping them together is wrong.
 */
private fun bannerPities(
    data: GachaData,
    officialCharacters: List<OfficialCharacter>,
    featuredWeapon: KuroGuide.FeaturedWeapon?,
): List<BannerPity> {
    val standardNames = standardFiveStarNames(data.records)
    val byFamily = data.records.groupBy { GachaPoolType.fromType(it.cardPoolType)?.family }
    val out = mutableListOf<BannerPity>()

    for (family in listOf(BannerFamily.CHARACTER_EVENT, BannerFamily.WEAPON_EVENT)) {
        val records = byFamily[family].orEmpty()
        if (records.isEmpty()) continue
        out +=
            buildBannerPity(
                family, family.label, records, predictionFor(data, family),
                standardNames, officialCharacters, featuredWeapon, data.records, alwaysOpen = false,
            )
    }

    // Permanent pool, always open, split by item kind.
    val permanent = byFamily[BannerFamily.STANDARD].orEmpty()
    out +=
        buildBannerPity(
            BannerFamily.STANDARD, "Permanent Resonator", permanent.filter { !it.isWeaponItem() }, null,
            standardNames, officialCharacters, featuredWeapon, data.records, alwaysOpen = true,
        )
    out +=
        buildBannerPity(
            BannerFamily.STANDARD, "Permanent Weapon", permanent.filter { it.isWeaponItem() }, null,
            standardNames, officialCharacters, featuredWeapon, data.records, alwaysOpen = true,
        )

    // Novice is a one-time 50-pull guarantee that closes once its 5★ drops.
    val beginner = byFamily[BannerFamily.BEGINNER].orEmpty()
    if (beginner.isNotEmpty() && beginner.none { it.qualityLevel == 5 }) {
        out +=
            buildBannerPity(
                BannerFamily.BEGINNER, "Novice", beginner, predictionFor(data, BannerFamily.BEGINNER),
                standardNames, officialCharacters, featuredWeapon, data.records, alwaysOpen = false,
            )
    }
    return out
}

private fun buildBannerPity(
    family: BannerFamily,
    title: String,
    records: List<GachaRecord>,
    prediction: PityPrediction?,
    standardNames: Set<String>,
    officialCharacters: List<OfficialCharacter>,
    featuredWeapon: KuroGuide.FeaturedWeapon?,
    allRecords: List<GachaRecord>,
    alwaysOpen: Boolean,
): BannerPity {
    val empty = records.isEmpty()
    val pity5 = prediction?.pullsSinceLastFive ?: pullsSinceLastFive(records)
    val pity4 = prediction?.pullsSinceLastFourStar ?: pullsSinceLastFour(records)
    val hardPity = prediction?.hardPity ?: family.hardPity
    val guaranteed =
        when (family) {
            BannerFamily.CHARACTER_EVENT -> prediction?.isGuaranteedNext ?: lastFiveIsStandard(records, standardNames)
            // A weapon banner's next 5★ is always the featured weapon.
            BannerFamily.WEAPON_EVENT -> true
            else -> false
        }
    val distToNext = maxOf(hardPity - pity5, 1)
    val worst =
        when (family) {
            BannerFamily.CHARACTER_EVENT -> if (guaranteed) distToNext else distToNext + hardPity
            BannerFamily.WEAPON_EVENT -> distToNext
            else -> null
        }
    return BannerPity(
        family = family,
        title = title,
        pullsSinceLastFive = pity5,
        pullsSinceLastFour = pity4,
        hardPity = hardPity,
        guaranteed = guaranteed,
        featured = featuredFor(family, prediction, officialCharacters, featuredWeapon, allRecords),
        worstCase = worst,
        worstCost = worst?.let { it.toLong() * costPerPull },
        empty = empty && !alwaysOpen,
    )
}

/** Pulls since the most recent 5★ within a family's records. */
private fun pullsSinceLastFive(records: List<GachaRecord>): Int {
    val sorted = records.sortedBy { it.time }
    val lastFiveIndex = sorted.indexOfLast { it.qualityLevel == 5 }
    return if (lastFiveIndex < 0) {
        sorted.sumOf { it.count.coerceAtLeast(1) }
    } else {
        sorted.drop(lastFiveIndex + 1).sumOf { it.count.coerceAtLeast(1) }
    }
}

/** Pulls since the most recent 4★ or better (a 5★ also resets the 4★ counter). */
private fun pullsSinceLastFour(records: List<GachaRecord>): Int {
    val sorted = records.sortedBy { it.time }
    val lastIndex = sorted.indexOfLast { it.qualityLevel == 4 || it.qualityLevel == 5 }
    return if (lastIndex < 0) {
        sorted.sumOf { it.count.coerceAtLeast(1) }.coerceAtMost(10)
    } else {
        sorted.drop(lastIndex + 1).sumOf { it.count.coerceAtLeast(1) }
    }
}

/** A standard-pool record that is a weapon rather than a resonator. */
private fun GachaRecord.isWeaponItem(): Boolean = portraitType(this) == "Weapon"

/** True when the most recent 5★ in [records] was a permanent-pool character. */
private fun lastFiveIsStandard(
    records: List<GachaRecord>,
    standardNames: Set<String>,
): Boolean = records.filter { it.qualityLevel == 5 }.maxByOrNull { it.time }?.name in standardNames

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
private fun HistoryBanner(
    entry: GachaHistoryEntry,
    viewModel: GachaViewModel,
) {
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
 * `internal` because [GachaStatsSection] is a separate file in this package and
 * reuses it.
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
