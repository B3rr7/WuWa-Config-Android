package com.wuwaconfig.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.tooling.preview.Preview
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
import com.wuwaconfig.app.model.MaterialItem
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
            val inPicker =
                (tabIndex == 0 && selectedCharacter == null) ||
                    (tabIndex == 1 && selectedWeapon == null)
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
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

                if (inPicker) {
                    // Picker mode: the portrait grid owns the rest of the screen.
                    // It used to be capped at 336dp inside a scrolling page, which
                    // left a large dead band below the card on any tall device.
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (tabIndex == 0) {
                            CharacterTab(
                                characters = characters,
                                selected = selectedCharacter,
                                result = characterResult,
                                onSelect = viewModel::selectCharacter,
                                onCalculate = viewModel::calculateCharacter,
                                fill = true,
                            )
                        } else {
                            WeaponTab(
                                weapons = weapons,
                                selected = selectedWeapon,
                                result = weaponResult,
                                onSelect = viewModel::selectWeapon,
                                onCalculate = viewModel::calculateWeapon,
                                fill = true,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
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
                    }
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

/**
 * Character planning: pick a character, set both ranges, read live totals.
 *
 * The two former "Calculate" buttons are gone on purpose. Both buttons fed the
 * same underlying call — the skills one passed the ascension range too — so the
 * separation was an illusion, and a stale press-order bug class (ranges changed,
 * button not re-pressed) came with it. Results now recompute whenever any input
 * changes, via [LaunchedEffect], and selection changes clear the result in the
 * ViewModel, which structurally kills the stale "Nothing to upgrade" banner the
 * old `emptyResult` flag needed a reset effect for.
 */
@Composable
private fun CharacterTab(
    characters: List<CalculatorCharacter>,
    selected: CalculatorCharacter?,
    result: CalculatorResult?,
    onSelect: (CalculatorCharacter?) -> Unit,
    onCalculate: (Int, Int, Int, Int) -> Boolean,
    fill: Boolean = false,
) {
    var fromPhase by rememberSaveable { mutableStateOf(0) }
    var toPhase by rememberSaveable { mutableStateOf(6) }
    // Skills default to "no purchase" (1 -> 1) so the live totals on selection
    // reflect ascension only; dragging the skill range adds skill cost visibly.
    var skillFrom by rememberSaveable { mutableStateOf(1) }
    var skillTo by rememberSaveable { mutableStateOf(1) }

    // Live recalculation: every input change re-runs the (pure, table-sum)
    // calculator. Consecutive relaunches cancel the previous one, so a slider
    // drag settles into exactly one call — no Calculate button to forget.
    LaunchedEffect(selected, fromPhase, toPhase, skillFrom, skillTo) {
        if (selected != null) onCalculate(fromPhase, toPhase, skillFrom, skillTo)
    }

    if (selected == null) {
        PickerCard(
            title = "Select Character",
            accent = NeonAmber,
            searchHint = "Search characters",
            entries = characters,
            name = { it.name },
            rarity = { it.rarity },
            weapon = false,
            onPick = { onSelect(it as CalculatorCharacter) },
            fill = fill,
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

    GlassCard(accentColor = NeonGreen) {
        GlassCardHeader("Plan", NeonGreen)
        Spacer(Modifier.height(10.dp))

        // Ascension. Phase index 0..6 maps to the level table below.
        RangeField(
            label = "Level",
            from = fromPhase,
            to = toPhase,
            range = 0f..6f,
            accent = NeonGreen,
            note = { phase -> "max Lv ${MaterialCalculator.PHASE_MAX_LEVEL.getOrElse(phase) { 90 }}" },
            presets =
                listOf(
                    "Full 0→6" to { 0 to 6 },
                    "To max" to { fromPhase to 6 },
                ),
            onFrom = { fromPhase = it },
            onTo = { toPhase = it },
        )

        Spacer(Modifier.height(14.dp))

        // Skills are 1..10 and the slider range is 1..10, so the slider value
        // IS the skill level (see the removed valueOffset bug in history).
        RangeField(
            label = "Skills",
            from = skillFrom,
            to = skillTo,
            range = 1f..10f,
            accent = NeonCyan,
            note = { level -> "Lv $level" },
            presets =
                listOf(
                    "None 1→1" to { 1 to 1 },
                    "Max 1→10" to { 1 to 10 },
                ),
            onFrom = { skillFrom = it },
            onTo = { skillTo = it },
        )

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

    ResultSection(result)
}

@Composable
private fun WeaponTab(
    weapons: List<CalculatorWeapon>,
    selected: CalculatorWeapon?,
    result: CalculatorResult?,
    onSelect: (CalculatorWeapon?) -> Unit,
    onCalculate: (Int, Int) -> Boolean,
    fill: Boolean = false,
) {
    var fromPhase by rememberSaveable { mutableStateOf(0) }
    var toPhase by rememberSaveable { mutableStateOf(6) }

    // Same live-recalculation contract as CharacterTab.
    LaunchedEffect(selected, fromPhase, toPhase) {
        if (selected != null) onCalculate(fromPhase, toPhase)
    }

    if (selected == null) {
        PickerCard(
            title = "Select Weapon",
            accent = NeonAmber,
            searchHint = "Search weapons",
            entries = weapons,
            name = { it.name },
            rarity = { it.rarity },
            weapon = true,
            onPick = { onSelect(it as CalculatorWeapon) },
            fill = fill,
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
        RangeField(
            label = "Level",
            from = fromPhase,
            to = toPhase,
            range = 0f..6f,
            accent = NeonGreen,
            note = { phase -> "max Lv ${MaterialCalculator.PHASE_MAX_LEVEL.getOrElse(phase) { 90 }}" },
            presets =
                listOf(
                    "Full 0→6" to { 0 to 6 },
                    "To max" to { fromPhase to 6 },
                ),
            onFrom = { fromPhase = it },
            onTo = { toPhase = it },
        )
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

    ResultSection(result)
}

/**
 * One labelled range: a two-thumb Material3 [RangeSlider], the resolved bounds
 * and a preset chip row.
 *
 * Replaces the old stacked FROM/TO slider pair. Two thumbs on one track cannot
 * cross (the widget enforces from <= to), which removes the manual
 * coerceAtMost/coerceAtLeast coupling the two-slider version needed, and
 * halves the vertical space.
 */
@Composable
private fun RangeField(
    label: String,
    from: Int,
    to: Int,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    note: (Int) -> String,
    presets: List<Pair<String, () -> Pair<Int, Int>>>,
    onFrom: (Int) -> Unit,
    onTo: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$label  $from → $to",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                note(to),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        RangeSlider(
            value = from.toFloat()..to.toFloat(),
            onValueChange = { r ->
                onFrom(r.start.toInt())
                onTo(r.endInclusive.toInt())
            },
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            colors = sliderColors(accent),
        )
        if (presets.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (name, resolve) ->
                    PresetChip(label = name, accent = accent, onClick = {
                        val (f, t) = resolve()
                        onFrom(f)
                        onTo(t)
                    })
                }
            }
        }
    }
}

/** Small pill that applies a preset range. Same glass language as [SegTab]. */
@Composable
private fun PresetChip(
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.08f))
                .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
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

/**
 * Search + portrait grid picker.
 *
 * A 4-column grid replaces the old 300dp row list: with ~170 entries, name-plus-
 * portrait cells scan far faster than text rows, and four visible rows carry
 * roughly three times as many entries per viewport.
 */
@Composable
private fun <T> PickerCard(
    title: String,
    accent: Color,
    searchHint: String,
    entries: List<T>,
    name: (T) -> String,
    rarity: (T) -> Int,
    weapon: Boolean,
    onPick: (T) -> Unit,
    fill: Boolean = false,
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

    // fill = true makes the card consume the height the parent offers, so the
    // picker grows with the screen; fill = false keeps the scrolling layout.
    GlassCard(modifier = if (fill) Modifier.fillMaxSize() else Modifier, accentColor = accent) {
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
            // In picker mode the grid absorbs every pixel the parent offers
            // (weight in a bounded column); in scrolling mode it is height-capped
            // so it does not push the plan card out of reach.
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier =
                    if (fill) {
                        Modifier.weight(1f).fillMaxWidth()
                    } else {
                        Modifier.height(336.dp)
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { name(it) }) { entry ->
                    PickerCell(
                        label = name(entry),
                        rarity = rarity(entry),
                        weapon = weapon,
                        onClick = { onPick(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerCell(
    label: String,
    rarity: Int,
    weapon: Boolean,
    onClick: () -> Unit,
) {
    val accent = rarityAccent(rarity)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.06f))
                .border(1.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Portrait(label, rarity, weapon, 52.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$rarity★",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
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
            // 176dp, not the old 104dp: Material's default contentPadding (24dp)
            // plus GlassButton's inner Row padding (16dp) eats 80dp of the width,
            // so anything under ~160dp silently clips the word "Change".
            GlassButton(onClick = onChange, modifier = Modifier.width(176.dp), accentColor = NeonRed, height = 40.dp) {
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

/**
 * Live result area: the empty case is derived from the result itself rather
 * than from a screen-local flag, so a selection change can never show a stale
 * banner (the ViewModel nulls the result on selection).
 */
@Composable
private fun ResultSection(result: CalculatorResult?) {
    val isEmpty = result != null && result.totalItems == 0
    AnimatedVisibility(visible = result != null) {
        if (result == null || isEmpty) {
            GlassCard(accentColor = NeonRed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = NeonRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Nothing to upgrade — widen the range.",
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

/**
 * The totals card. Shell Credit dominates every real query (a full character is
 * ~1.2M), so it is lifted out of the group blocks into a hero row and the
 * remaining groups sort by total descending — the expensive bucket is always
 * the first thing read.
 */
@Composable
private fun ResultCard(result: CalculatorResult) {
    val subject = result.characterName ?: result.weaponName.orEmpty()
    val hero = result.groups.firstOrNull { it.category == MaterialCalculator.SHELL_CREDIT }
    val rest = result.groups.filter { it.category != MaterialCalculator.SHELL_CREDIT }.sortedByDescending { it.total }

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

        hero?.let {
            Spacer(Modifier.height(12.dp))
            ShellCreditHero(it)
        }

        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            rest.forEachIndexed { index, group ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                MaterialGroupBlock(group)
            }
        }
    }
}

/** The dominant cost, promoted: coin icon, label, and a single large number. */
@Composable
private fun ShellCreditHero(group: MaterialGroup) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NeonGold.copy(alpha = 0.1f))
                .border(1.dp, NeonGold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Paid,
            contentDescription = null,
            tint = NeonGold,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "SHELL CREDIT",
                style = MaterialTheme.typography.labelSmall,
                color = NeonGold.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                formatQty(group.total),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NeonGold,
            )
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
    item: MaterialItem,
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

@Preview(name = "Result Card — hero + groups")
@Composable
private fun ResultCardPreview() {
    val fake =
        CalculatorResult(
            characterName = "Jinhsi",
            totalItems = 27,
            groups =
                listOf(
                    MaterialGroup(
                        MaterialCalculator.SHELL_CREDIT,
                        listOf(MaterialItem("Shell Credit", 1_208_000)),
                    ),
                    MaterialGroup(
                        MaterialCalculator.CATEGORY_COMMON,
                        listOf(
                            MaterialItem("Resonance Potion", 48),
                            MaterialItem("Energy Core", 32),
                        ),
                    ),
                    MaterialGroup(
                        MaterialCalculator.CATEGORY_BOSS,
                        listOf(MaterialItem("Elegy Tacet Core", 13)),
                    ),
                ),
        )
    WuWaConfigTheme(themeMode = "dark") {
        GradientBackground {
            Column(Modifier.padding(16.dp)) {
                ResultCard(fake)
            }
        }
    }
}

@Preview(name = "Picker Cell — 5★ / 4★ / fallback")
@Composable
private fun PickerCellPreview() {
    WuWaConfigTheme(themeMode = "light") {
        GradientBackground {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerCell("Jinhsi", 5, weapon = false, onClick = {})
                PickerCell("Static Mist", 4, weapon = true, onClick = {})
                PickerCell("Zz", 3, weapon = false, onClick = {})
            }
        }
    }
}
