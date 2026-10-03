package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wuwaconfig.app.config.GachaItemKind
import com.wuwaconfig.app.config.GachaStats
import com.wuwaconfig.app.config.GachaStatsResult
import com.wuwaconfig.app.config.gameProfile
import com.wuwaconfig.app.model.GachaData
import com.wuwaconfig.app.model.GachaPoolType
import com.wuwaconfig.app.model.GachaRecord
import com.wuwaconfig.app.model.PityPrediction
import com.wuwaconfig.app.ui.components.GachaAvatar
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.theme.NeonAmber
import com.wuwaconfig.app.ui.theme.NeonBlue
import com.wuwaconfig.app.ui.theme.NeonCyan
import com.wuwaconfig.app.ui.theme.NeonGold
import com.wuwaconfig.app.ui.theme.NeonGreen
import com.wuwaconfig.app.ui.theme.NeonPink
import com.wuwaconfig.app.ui.theme.NeonPurple
import com.wuwaconfig.app.ui.theme.NeonRed

/**
 * Lifetime statistics over the retained gacha history — the 统计 view of
 * WutheringWavesTool's card analysis, rebuilt for Compose.
 *
 * WWT is JavaFX, so its `CardStatView.fxml` cannot be reused; only its information
 * architecture is carried over. Its layout, and where each part lands here:
 *
 * | WWT | here |
 * |---|---|
 * | 4-up `stat-card` grid (总抽数 / ★5 / ★4 / UP ★5) | [LifetimeStatCards] |
 * | 稀有度分布 donut + `rarityLegend` + `statGroup` filters | [RarityDonut] |
 * | `roleCard` / `weaponCard` `pool-summary` pair | [KindSummaryCards] |
 * | 抽取排行 list with rarity filter | [PullRanking] |
 *
 * Every figure comes from [GachaStats.aggregate], the same computation
 * `getGachaStats` exposes to agents, so the screen and the AppFunction cannot
 * disagree. This section deliberately does not recompute anything.
 */
@Composable
fun GachaStatsSection(
    data: GachaData,
    modifier: Modifier = Modifier,
) {
    val stats = remember(data) { GachaStats.aggregate(data) }
    if (stats.totalPulls <= 0) return

    Column(modifier = modifier) {
        LifetimeStatCards(stats)
        Spacer(Modifier.height(12.dp))
        RarityDonut(GachaStats.rarityCounts(data.records), stats.totalPulls)
        Spacer(Modifier.height(12.dp))
        KindSummaryCards(stats)
        Spacer(Modifier.height(12.dp))
        PullRanking(data)
    }
}

/**
 * WWT's top row: 总抽数, ★5, ★4, and UP ★5.
 *
 * A 3-up Row rather than WWT's 4-column GridPane, because at phone width four
 * figures with a label each wrap unreadably. The UP ★5 figure wraps onto its own
 * line instead of being dropped.
 */
@Composable
private fun LifetimeStatCards(stats: GachaStatsResult) {
    GlassCard(accentColor = NeonGold) {
        GlassCardHeader("LIFETIME", NeonGold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("${stats.totalPulls}", "Total Pulls", NeonCyan)
            StatItem("${stats.fiveStarCount}", "★5", NeonGold)
            StatItem("${stats.fourStarCount}", "★4", NeonPurple)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("${stats.upFiveStarCount}", "UP ★5", NeonGreen)
            StatItem("${stats.characterFiveStarItems}", "★5Chars", NeonPink)
            StatItem("${stats.weaponFiveStarItems}", "★5Weapons", NeonBlue)
        }
    }
}

/**
 * WWT's 稀有度分布: a pie with the total in the middle and a legend beside it.
 *
 * Drawn on a [Canvas] as a donut rather than a filled pie, because the centre has
 * to stay clear for the total — which is exactly why WWT stacked a second pane over
 * its `PieChart`.
 *
 * [counts] is quality level → pulls, as from [GachaStats.rarityCounts]. A level
 * with no pulls is simply absent, so the donut never draws a zero-width slice.
 *
 * Shared by the lifetime view and the per-banner view: both show the same chart
 * over a different record set, and two donut implementations would drift.
 */
@Composable
private fun RarityDonut(
    counts: Map<Int, Int>,
    total: Int,
) {
    val slices =
        remember(counts) {
            listOf(
                Triple("★5", counts[5] ?: 0, NeonGold),
                Triple("★4", counts[4] ?: 0, NeonPurple),
                Triple("★3", counts[3] ?: 0, NeonCyan),
            ).filter { it.second > 0 }
        }
    if (total <= 0 || slices.isEmpty()) return

    GlassCard(accentColor = NeonCyan) {
        GlassCardHeader("RARITY DISTRIBUTION", NeonCyan)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(132.dp)) {
                    val stroke = 22.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    // Start at 12 o'clock and sweep clockwise, which is how WWT's
                    // JavaFX PieChart defaults and where the eye expects it to start.
                    var startAngle = -90f
                    slices.forEach { (_, count, color) ->
                        val sweep = 360f * count / total
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep - 1.5f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = stroke),
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$total",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "pulls",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                slices.forEach { (label, count, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(26.dp),
                        )
                        Text(
                            "${"%.1f".format(count * 100.0 / total)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * WWT's `roleCard` / `weaponCard` pair: pulls, 5★ items and average pity per side.
 *
 * The pity figures come from [GachaStats.averageFiveStarPity] restricted to the
 * side's own records, not from the global average, so a character-heavy account
 * does not report a weapon average it never earned.
 */
@Composable
private fun KindSummaryCards(stats: GachaStatsResult) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(accentColor = NeonPink, modifier = Modifier.weight(1f)) {
            Text(
                "CHARACTERS",
                style = MaterialTheme.typography.labelMedium,
                color = NeonPink.copy(alpha = 0.85f),
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            StatItem("${stats.characterPulls}", "Pulls", NeonPink)
            Spacer(Modifier.height(8.dp))
            StatItem("${stats.characterFiveStarItems}", "★5 Obtained", NeonGold)
            Spacer(Modifier.height(8.dp))
            StatItem(pityOrDash(stats.characterAverageFiveStarPity), "Avg ★5 Pity", NeonCyan)
        }
        GlassCard(accentColor = NeonBlue, modifier = Modifier.weight(1f)) {
            Text(
                "WEAPONS",
                style = MaterialTheme.typography.labelMedium,
                color = NeonBlue.copy(alpha = 0.85f),
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            StatItem("${stats.weaponPulls}", "Pulls", NeonBlue)
            Spacer(Modifier.height(8.dp))
            StatItem("${stats.weaponFiveStarItems}", "★5 Obtained", NeonGold)
            Spacer(Modifier.height(8.dp))
            StatItem(pityOrDash(stats.weaponAverageFiveStarPity), "Avg ★5 Pity", NeonCyan)
        }
    }
}

/**
 * WWT's 抽取排行: most-pulled items, filterable by character/weapon and by rarity.
 *
 * The filters are local `remember`ed state rather than hoisted: this list is a leaf
 * of the screen and nothing else reads the selection.
 */
@Composable
private fun PullRanking(data: GachaData) {
    var kind by remember { mutableStateOf<GachaItemKind?>(null) }
    var rarity by remember { mutableStateOf(4) }

    val items =
        remember(data, kind, rarity) {
            GachaStats.topItems(data.records, rarity, limit = 8, kind = kind)
        }

    GlassCard(accentColor = NeonPurple) {
        GlassCardHeader("PULL RANKING", NeonPurple)
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("All", kind == null && rarity == 4, NeonPurple) {
                kind = null
                rarity = 4
            }
            FilterChip("Chars", kind == GachaItemKind.CHARACTER, NeonPink) { kind = GachaItemKind.CHARACTER }
            FilterChip("Weapons", kind == GachaItemKind.WEAPON, NeonBlue) { kind = GachaItemKind.WEAPON }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip("★5", rarity == 5, NeonGold) { rarity = 5 }
            FilterChip("★4", rarity == 4, NeonPurple) { rarity = 4 }
        }
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                "No matching pulls in the stored history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEachIndexed { index, item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${item.count}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/**
 * A small selectable pill. [GlassButton] is sized for a full-width action and is
 * the wrong control for a filter, so this is a clickable Box.
 *
 * `indication = null` because an unlabelled pill inside a card reads as a button
 * only when it responds; the accent fill and bold weight already carry selection.
 */
@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** A pity average of 0 means "no 5★ on that side yet", not "average zero". */
private fun pityOrDash(value: Double): String = if (value > 0) "%.1f".format(value) else "—"

// ─────────── the per-banner visual view ───────────
//
// WutheringWavesTool's 直观 tab, rebuilt for a phone. Its desktop layout is a left
// banner sidebar, a centre chart column and a right item grid at 1200x700. A phone
// has none of that width, so the same three concerns are stacked: a horizontally
// scrollable banner selector, the selected banner's chart and figures, then its
// item grid. Nothing is dropped — only rearranged.

/** One banner the player has actually pulled in, with its own records and prediction. */
private data class BannerData(
    val type: String,
    val label: String,
    val records: List<GachaRecord>,
    val prediction: PityPrediction?,
)

/**
 * The per-banner visual view.
 *
 * Every figure comes from the same [GachaData] the per-pool predictions use, so the
 * two views cannot disagree. The banner selector lists only banners with records:
 * an empty banner has no chart to draw and no items to show, and offering it would
 * put a dead control on screen.
 */
@Composable
fun GachaVisualSection(
    data: GachaData,
    modifier: Modifier = Modifier,
) {
    val banners =
        remember(data) {
            data.records
                .groupBy { it.cardPoolType }
                .map { (type, records) ->
                    BannerData(
                        type = type,
                        label = GachaPoolType.fromType(type)?.label ?: type,
                        records = records,
                        prediction = data.predictions.firstOrNull { it.poolType == type },
                    )
                }
                .sortedBy { it.type }
        }
    if (banners.isEmpty()) return

    var selectedType by remember { mutableStateOf(banners.first().type) }
    val selected = banners.firstOrNull { it.type == selectedType } ?: banners.first()

    Column(modifier = modifier) {
        BannerSelector(banners, selectedType, onSelect = { selectedType = it })
        Spacer(Modifier.height(12.dp))
        BannerDetail(selected)
        Spacer(Modifier.height(12.dp))
        BannerItemGrid(selected)
    }
}

/**
 * The banner selector.
 *
 * A horizontally scrolling row of chips rather than WWT's fixed left sidebar: the
 * list is short (a handful of banners) and a phone is wider than it is tall, so
 * scrolling sideways keeps every banner one tap away instead of pushing the chart
 * below the fold.
 */
@Composable
private fun BannerSelector(
    banners: List<BannerData>,
    selectedType: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        banners.forEach { banner ->
            val selected = banner.type == selectedType
            val pulls = banner.records.sumOf { it.count.coerceAtLeast(1) }
            Column(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) {
                            NeonCyan.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        },
                    )
                    .clickable { onSelect(banner.type) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    banner.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) NeonCyan else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
                Text(
                    "$pulls",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The selected banner's chart and figures: rarity donut, pity progress, and the
 * per-banner detail stats.
 *
 * The pity block is gated on a prediction existing. A banner can have pulls without
 * one — a standard or beginner pool is not a 50/50 banner, so no prediction is
 * generated for it — and showing "0 pulls since ★5" there would assert a pity the
 * banner does not have.
 */
@Composable
private fun BannerDetail(banner: BannerData) {
    val records = banner.records
    val totalPulls = records.sumOf { it.count.coerceAtLeast(1) }
    val counts = remember(records) { GachaStats.rarityCounts(records) }
    val prediction = banner.prediction
    val costPerPull = gameProfile().currencyPerPull

    GlassCard(accentColor = NeonCyan) {
        GlassCardHeader(banner.label.uppercase(), NeonCyan)
        Spacer(Modifier.height(14.dp))
        RarityDonut(counts, totalPulls)
        if (prediction != null) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${prediction.pullsSinceLastFive}", "Since ★5", NeonGold)
                StatItem("${prediction.pullsSinceLastFourStar}", "Since ★4", NeonPurple)
                StatItem(formatNumber(totalPulls.toLong() * costPerPull), "Astrites", NeonAmber)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(pityOrDash(prediction.avgPityThisPool), "Avg ★5 Pity", NeonCyan)
                StatItem("${prediction.minPity5}", "Luckiest", NeonGreen)
                StatItem("${prediction.maxPity5}", "Unluckiest", NeonRed)
                StatItem("${"%.0f".format(prediction.nonBannerRate * 100)}%", "50/50 Win", NeonAmber)
            }
        }
    }
}

/**
 * The items pulled in the selected banner, most-pulled first.
 *
 * A plain chunked Row grid rather than a nested lazy list: this sits inside the
 * screen's LazyColumn, and a lazy list inside a lazy list is a scrolling bug. The
 * item count per banner is small enough that laying them out eagerly is cheaper
 * than a grid composable.
 *
 * The featured unit is badged, matching WWT's `UP` marker, so the player can see at
 * a glance which pulls were the rate-up one.
 */
@Composable
private fun BannerItemGrid(banner: BannerData) {
    val items =
        remember(banner) {
            GachaStats.topItems(banner.records, rarity = 0, limit = 24)
        }
    if (items.isEmpty()) return
    val featured = banner.prediction?.currentFeaturedName?.takeIf { it.isNotBlank() }

    GlassCard(accentColor = NeonPurple) {
        GlassCardHeader("PULLED IN THIS BANNER", NeonPurple)
        Spacer(Modifier.height(12.dp))
        items.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { item ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            GachaAvatar(
                                name = item.name,
                                resourceType = if (item.kind == GachaItemKind.WEAPON) "Weapon" else "Resonator",
                                size = 44.dp,
                            )
                            if (item.name == featured) {
                                Text(
                                    "UP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier =
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(NeonAmber)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${item.count}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                // Keep a short last row aligned to the same columns as a full one.
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
