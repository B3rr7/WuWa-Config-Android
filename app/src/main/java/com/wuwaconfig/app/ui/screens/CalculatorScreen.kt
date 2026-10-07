package com.wuwaconfig.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wuwaconfig.app.config.MaterialCalculator
import com.wuwaconfig.app.model.CalculatorCharacter
import com.wuwaconfig.app.model.CalculatorResult
import com.wuwaconfig.app.model.CalculatorWeapon
import com.wuwaconfig.app.model.MaterialGroup
import com.wuwaconfig.app.ui.CalculatorViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.components.MaterialIcons
import com.wuwaconfig.app.ui.components.PortraitAssets
import com.wuwaconfig.app.ui.theme.*

/** Accent per material bucket, so the categories are scannable without reading. */
private fun groupAccent(category: String): Color =
    when (category) {
        MaterialCalculator.SHELL_CREDIT -> NeonGold
        MaterialCalculator.CATEGORY_LOCAL -> NeonGreen
        MaterialCalculator.CATEGORY_COMMON -> NeonCyan
        MaterialCalculator.CATEGORY_BOSS -> NeonRed
        MaterialCalculator.CATEGORY_SKILL -> NeonPurple
        MaterialCalculator.CATEGORY_DROP -> NeonBlue
        MaterialCalculator.CATEGORY_ASCENSION -> NeonAmber
        else -> NeonPink
    }

private fun rarityAccent(rarity: Int): Color =
    when (rarity) {
        5 -> NeonGold
        4 -> NeonPurple
        3 -> NeonBlue
        else -> NeonCyan
    }

/** "1,234" — material counts run to six figures and are unreadable unformatted. */
private fun formatQty(value: Int): String = "%,d".format(value)

/**
 * A circular portrait that falls back to a rarity-coloured initial.
 *
 * The initial is the fallback rather than a placeholder: it stays legible at 36dp
 * and, per [PortraitAssets], a handful of entries genuinely have no bundled art.
 */
@Composable
private fun Portrait(
    name: String,
    rarity: Int,
    weapon: Boolean,
    size: Dp,
) {
    val context = LocalContext.current
    val uri =
        remember(name, weapon) {
            if (weapon) {
                PortraitAssets.weaponUri(context, name)
            } else {
                PortraitAssets.characterUri(context, name)
            }
        }
    val accent = rarityAccent(rarity)
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 50))
                .background(accent.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    onBack: () -> Unit,
) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val weapons by viewModel.weapons.collectAsStateWithLifecycle()
    val selectedCharacter by viewModel.selectedCharacter.collectAsStateWithLifecycle()
    val selectedWeapon by viewModel.selectedWeapon.collectAsStateWithLifecycle()
    val characterResult by viewModel.characterResult.collectAsStateWithLifecycle()
    val weaponResult by viewModel.weaponResult.collectAsStateWithLifecycle()

    var tabIndex by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadLists() }

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = { Text("Calculator", fontWeight = FontWeight.Bold) },
                    accentColor = NeonAmber,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonAmber)
                        }
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

                GlassCard(accentColor = NeonAmber) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(NeonAmber.copy(alpha = 0.08f)).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SegTab("Characters", tabIndex == 0, Modifier.weight(1f)) { tabIndex = 0 }
                        SegTab("Weapons", tabIndex == 1, Modifier.weight(1f)) { tabIndex = 1 }
                    }
                }

                if (tabIndex == 0) {
                    CharacterTab(
                        characters = characters,
                        selected = selectedCharacter,
                        result = characterResult,
                        onSelect = viewModel::selectCharacter,
                        onCalculate = viewModel::calculateCharacter,
                    )
                } else {
                    WeaponTab(
                        weapons = weapons,
                        selected = selectedWeapon,
                        result = weaponResult,
                        onSelect = viewModel::selectWeapon,
                        onCalculate = viewModel::calculateWeapon,
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SegTab(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (active) NeonAmber.copy(alpha = 0.22f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) NeonAmber else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CharacterTab(
    characters: List<CalculatorCharacter>,
    selected: CalculatorCharacter?,
    result: CalculatorResult?,
    onSelect: (CalculatorCharacter?) -> Unit,
    onCalculate: (Int, Int, Int, Int) -> Boolean,
) {
    var fromPhase by rememberSaveable { mutableStateOf(0) }
    var toPhase by rememberSaveable { mutableStateOf(6) }
    var skillFrom by rememberSaveable { mutableStateOf(1) }
    var skillTo by rememberSaveable { mutableStateOf(10) }
    var emptyResult by remember { mutableStateOf(false) }

    if (selected == null) {
        PickerCard(
            title = "Select Character",
            accent = NeonAmber,
            searchHint = "Search characters",
            entries = characters,
            name = { it.name },
            rarity = { it.rarity },
            subtitle = { null },
            weapon = false,
            onPick = { onSelect(it as CalculatorCharacter) },
        )
        return
    }

    SelectionHeader(
        name = selected.name,
        rarity = selected.rarity,
        subtitle = selected.local ?: selected.boss,
        weapon = false,
        onChange = { onSelect(null) },
    )

    // Ascension and skill are independent purchases, so each gets its own
    // Calculate button: picking a level range and then having it silently reset
    // when you touch the skill sliders is the confusing failure mode here.
    GlassCard(accentColor = NeonGreen) {
        GlassCardHeader("Ascension", NeonGreen)
        Spacer(Modifier.height(10.dp))
        PhaseRangeSlider(
            label = "Level",
            fromPhase = fromPhase,
            toPhase = toPhase,
            onFrom = { fromPhase = it.coerceAtMost(toPhase) },
            onTo = { toPhase = it.coerceAtLeast(fromPhase) },
            note = { phase -> "max Lv ${MaterialCalculator.PHASE_MAX_LEVEL.getOrElse(phase) { 90 }}" },
        )
        Spacer(Modifier.height(12.dp))
        GlassButton(
            onClick = { emptyResult = !onCalculate(fromPhase, toPhase, 1, 1) },
            modifier = Modifier.fillMaxWidth(),
            accentColor = NeonGreen,
        ) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Calculate Ascension", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        // The result silently includes inherent skills and stat bonuses, which
        // unlock on ascension rather than on the skill slider. Without this the
        // totals jump by ~180k Shell Credit and look like a bug.
        Text(
            "Includes inherent skills and stat bonuses unlocked by the selected ranks.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }

    GlassCard(accentColor = NeonCyan) {
        GlassCardHeader("Skills", NeonCyan)
        Spacer(Modifier.height(10.dp))
        PhaseRangeSlider(
            label = "Skill",
            fromPhase = skillFrom - 1,
            toPhase = skillTo - 1,
            valueOffset = 1,
            minLevel = 1,
            maxLevel = 10,
            onFrom = { skillFrom = it.coerceAtMost(skillTo) },
            onTo = { skillTo = it.coerceAtLeast(skillFrom) },
            // Skills cap at 10, so the character level table must not be shown here.
            note = { level -> "max Lv ${level + 1}" },
        )
        Spacer(Modifier.height(12.dp))
        GlassButton(
            onClick = { emptyResult = !onCalculate(fromPhase, toPhase, skillFrom, skillTo) },
            modifier = Modifier.fillMaxWidth(),
            accentColor = NeonCyan,
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Calculate Ascension + Skills", fontWeight = FontWeight.Bold)
        }
    }

    if (selected.wsm.isNotEmpty()) {
        MaterialPreviewCard(
            header = "Upgrade Materials",
            accent = NeonPurple,
            rows =
                listOf(
                    "Boss material" to (selected.skillBoss ?: "—"),
                    "Skill material" to selected.wsm.joinToString(", "),
                    "Monster drop" to selected.dwsm.joinToString(", "),
                ),
        )
    }

    ResultSection(result, emptyResult)
}

@Composable
private fun WeaponTab(
    weapons: List<CalculatorWeapon>,
    selected: CalculatorWeapon?,
    result: CalculatorResult?,
    onSelect: (CalculatorWeapon?) -> Unit,
    onCalculate: (Int, Int) -> Boolean,
) {
    var fromPhase by rememberSaveable { mutableStateOf(0) }
    var toPhase by rememberSaveable { mutableStateOf(6) }
    var emptyResult by remember { mutableStateOf(false) }

    if (selected == null) {
        PickerCard(
            title = "Select Weapon",
            accent = NeonAmber,
            searchHint = "Search weapons",
            entries = weapons,
            name = { it.name },
            rarity = { it.rarity },
            subtitle = { it.secondStatType?.let { s -> "$s ${it.secondStat ?: 0.0}" } },
            weapon = true,
            onPick = { onSelect(it as CalculatorWeapon) },
        )
        return
    }

    SelectionHeader(
        name = selected.name,
        rarity = selected.rarity,
        subtitle =
            selected.secondStatType?.let {
                "${selected.baseAtk ?: 0.0} ATK · $it ${selected.secondStat ?: 0.0}"
            },
        weapon = true,
        onChange = { onSelect(null) },
    )

    GlassCard(accentColor = NeonGreen) {
        GlassCardHeader("Ascension", NeonGreen)
        Spacer(Modifier.height(10.dp))
        PhaseRangeSlider(
            label = "Level",
            fromPhase = fromPhase,
            toPhase = toPhase,
            onFrom = { fromPhase = it.coerceAtMost(toPhase) },
            onTo = { toPhase = it.coerceAtLeast(fromPhase) },
            note = { phase -> "max Lv ${MaterialCalculator.PHASE_MAX_LEVEL.getOrElse(phase) { 90 }}" },
        )
        Spacer(Modifier.height(12.dp))
        GlassButton(
            onClick = { emptyResult = !onCalculate(fromPhase, toPhase) },
            modifier = Modifier.fillMaxWidth(),
            accentColor = NeonGreen,
        ) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Calculate", fontWeight = FontWeight.Bold)
        }
    }

    MaterialPreviewCard(
        header = "Upgrade Materials",
        accent = NeonPurple,
        rows =
            listOf(
                "Ascension material" to selected.ascension.joinToString(", "),
                "Common material" to selected.common.joinToString(", "),
            ),
    )

    ResultSection(result, emptyResult)
}

/**
 * Two coupled sliders over an index range, labelled with the levels the indices
 * actually correspond to.
 *
 * [valueOffset] exists because the two ranges are stored differently — skill
 * levels are 1..10 while ascension phases are 0..6 — and showing "Phase 0 → 6"
 * for a skill range would be meaningless to the user.
 */
@Composable
private fun PhaseRangeSlider(
    label: String,
    fromPhase: Int,
    toPhase: Int,
    onFrom: (Int) -> Unit,
    onTo: (Int) -> Unit,
    note: (Int) -> String,
    valueOffset: Int = 0,
    minLevel: Int = 0,
    maxLevel: Int = 6,
) {
    val display = { v: Int -> "${v + valueOffset}" }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$label ${display(fromPhase)} → ${display(toPhase)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NeonGreen,
            )
            Text(
                note(toPhase),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        // Each track is labelled because two stacked sliders are otherwise
        // indistinguishable: green is the lower bound, cyan the upper.
        Text(
            "FROM",
            style = MaterialTheme.typography.labelSmall,
            color = NeonGreen.copy(alpha = 0.8f),
            letterSpacing = 1.sp,
        )
        Slider(
            value = fromPhase.toFloat(),
            onValueChange = { onFrom(it.toInt()) },
            valueRange = minLevel.toFloat()..maxLevel.toFloat(),
            steps = (maxLevel - minLevel - 1).coerceAtLeast(0),
            colors = sliderColors(NeonGreen),
        )
        Text(
            "TO",
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan.copy(alpha = 0.8f),
            letterSpacing = 1.sp,
        )
        Slider(
            value = toPhase.toFloat(),
            onValueChange = { onTo(it.toInt()) },
            valueRange = minLevel.toFloat()..maxLevel.toFloat(),
            steps = (maxLevel - minLevel - 1).coerceAtLeast(0),
            colors = sliderColors(NeonCyan),
        )
    }
}

/**
 * Slider colours for [accent], with the tick marks suppressed.
 *
 * Material3 draws tick dots in the *default* palette when `steps` is non-zero, so a
 * green track came out with purple dots down the middle. The positions are still
 * conveyed by the thumb and the value label, so the ticks were pure noise.
 */
@Composable
private fun sliderColors(accent: Color): SliderColors =
    SliderDefaults.colors(
        thumbColor = accent,
        activeTrackColor = accent.copy(alpha = 0.7f),
        inactiveTrackColor = accent.copy(alpha = 0.15f),
        activeTickColor = Color.Transparent,
        inactiveTickColor = Color.Transparent,
    )

@Composable
private fun <T> PickerCard(
    title: String,
    accent: Color,
    searchHint: String,
    entries: List<T>,
    name: (T) -> String,
    rarity: (T) -> Int,
    subtitle: (T) -> String?,
    weapon: Boolean,
    onPick: (T) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered =
        remember(entries, query) {
            if (query.isBlank()) {
                entries
            } else {
                entries.filter { name(it).contains(query.trim(), ignoreCase = true) }
            }
        }

    GlassCard(accentColor = accent) {
        GlassCardHeader("$title (${entries.size})", accent)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(searchHint, style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = accent) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = accent)
                    }
                }
            },
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        Spacer(Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No match for \"$query\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Height-capped so the picker does not push the rest of the tab off
            // screen; the whole screen is a scroll, and a 170-row list inside it
            // would make the Calculate button unreachable without inner scrolling.
            LazyColumn(modifier = Modifier.height(300.dp)) {
                items(filtered, key = { name(it) }) { entry ->
                    val itemAccent = rarityAccent(rarity(entry))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(itemAccent.copy(alpha = 0.06f))
                                .clickable { onPick(entry) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Portrait(name(entry), rarity(entry), weapon, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                name(entry),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            subtitle(entry)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            "${rarity(entry)}★",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = itemAccent,
                        )
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = itemAccent.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    name: String,
    rarity: Int,
    subtitle: String?,
    weapon: Boolean,
    onChange: () -> Unit,
) {
    val accent = rarityAccent(rarity)
    GlassCard(accentColor = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Portrait(name, rarity, weapon, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$rarity★",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "  ·  $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // Width is mandatory, not cosmetic: GlassButton wraps a Material3
            // Button, which fills the row it is given. Unconstrained it swallowed
            // the whole header and squeezed the name column to one letter wide.
            GlassButton(onClick = onChange, modifier = Modifier.width(104.dp), accentColor = NeonRed, height = 40.dp) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Change", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MaterialPreviewCard(
    header: String,
    accent: Color,
    rows: List<Pair<String, String>>,
) {
    GlassCard(accentColor = accent) {
        GlassCardHeader(header, accent)
        Spacer(Modifier.height(8.dp))
        rows.forEach { (label, value) ->
            Column(modifier = Modifier.padding(vertical = 3.dp)) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                )
                Text(
                    value.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun ResultSection(
    result: CalculatorResult?,
    emptyResult: Boolean,
) {
    AnimatedVisibility(visible = result != null || emptyResult) {
        if (result == null || result.totalItems == 0) {
            GlassCard(accentColor = NeonRed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = NeonRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Nothing to upgrade — widen the level range.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonRed,
                    )
                }
            }
        } else {
            ResultCard(result)
        }
    }
}

@Composable
private fun ResultCard(result: CalculatorResult) {
    val subject = result.characterName ?: result.weaponName.orEmpty()
    GlassCard(accentColor = NeonPurple) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "REQUIRED MATERIALS",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonPurple.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                )
                Text(
                    subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonPurple,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatQty(result.totalItems),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonGreen,
                    textAlign = TextAlign.End,
                )
                Text(
                    "${result.materials.size} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        result.groups.forEach { group ->
            MaterialGroupBlock(group)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MaterialGroupBlock(group: MaterialGroup) {
    val accent = groupAccent(group.category)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.06f))
                .border(1.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                group.category.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                formatQty(group.total),
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        group.materials.forEach { item ->
            MaterialRow(item, accent)
        }
    }
}

/**
 * One material: icon, name, quantity.
 *
 * The icon is optional rather than a fixed slot — reserving width for it would
 * leave a permanent gap on any material the bundle lacks, which is worse than
 * the row simply starting further left.
 */
@Composable
private fun MaterialRow(
    item: com.wuwaconfig.app.model.MaterialItem,
    accent: Color,
) {
    val context = LocalContext.current
    val icon = remember(item.name) { MaterialIcons.uri(context, item.name) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "×${formatQty(item.quantity)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}
