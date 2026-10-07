package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuwaconfig.app.config.CharacterBuild
import com.wuwaconfig.app.config.ChosenPlayer
import com.wuwaconfig.app.config.Echo
import com.wuwaconfig.app.config.OfficialCharacter
import com.wuwaconfig.app.config.OfficialStatus
import com.wuwaconfig.app.config.PlayerInfo
import com.wuwaconfig.app.config.ResonanceLink
import com.wuwaconfig.app.config.SkillInfo
import com.wuwaconfig.app.config.StatRecommendation
import com.wuwaconfig.app.config.Weapon
import com.wuwaconfig.app.ui.CharacterViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.components.RemoteImage
import com.wuwaconfig.app.ui.theme.NeonCyan
import com.wuwaconfig.app.ui.theme.NeonGold
import com.wuwaconfig.app.ui.theme.NeonGreen
import com.wuwaconfig.app.ui.theme.NeonPurple
import com.wuwaconfig.app.ui.theme.NeonRed
import com.wuwaconfig.app.ui.theme.elementAccent
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The player's own character view, styled after Kuro's official Wuthering Waves guide.
 * The selected character's element colour themes every data section below the roster,
 * so the whole screen shifts hue (Glacio cyan, Fusion amber, Aero green, …) with the
 * character. Skills are omitted to keep the view visual; the player's own upgraded
 * skills appear under SKILL PRIORITY for their own characters only.
 *
 * Character/equipment art is loaded from Kuro's CDN through [RemoteImage]. This
 * introduces remote `https` image loading, which the rest of the app deliberately
 * avoids — the art URLs come from Kuro's API responses, not user-injectable config.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCharacterScreen(
    viewModel: CharacterViewModel,
    onBack: () -> Unit,
) {
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = { Text("My Characters", fontWeight = FontWeight.Bold) },
                    accentColor = NeonCyan,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = NeonCyan,
                            )
                        }
                    },
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            if (loggedIn) LoggedInContent(viewModel, Modifier.padding(padding)) else LoginSection(viewModel, Modifier.padding(padding))
        }
    }
}

// -- login -------------------------------------------------------------------

@Composable
private fun LoginSection(
    viewModel: CharacterViewModel,
    modifier: Modifier,
) {
    val busy by viewModel.loginBusy.collectAsStateWithLifecycle()
    val error by viewModel.loginError.collectAsStateWithLifecycle()
    val prefill by viewModel.storedEmail.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf(prefill ?: "") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        GlassCard(accentColor = NeonCyan) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(NeonCyan.copy(alpha = 0.30f), NeonCyan.copy(alpha = 0.08f))))
                            .border(1.dp, NeonCyan.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Sign in with your Kuro account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "We log into Kuro's official guide to read your equipped items. Your password is used only to sign in and is never stored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Kuro email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            GlassCard(accentColor = NeonRed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = NeonRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                }
            }
        }
        GlassButton(
            onClick = { viewModel.login(email, password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            accentColor = NeonCyan,
            contentColor = Color.White,
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Sign in", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// -- logged-in flow ----------------------------------------------------------

@Composable
private fun LoggedInContent(
    viewModel: CharacterViewModel,
    modifier: Modifier,
) {
    val players by viewModel.players.collectAsStateWithLifecycle()
    val chosen by viewModel.chosenPlayer.collectAsStateWithLifecycle()
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val rosterLoading by viewModel.rosterLoading.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedRoleGbId.collectAsStateWithLifecycle()
    val build by viewModel.build.collectAsStateWithLifecycle()
    val buildLoading by viewModel.buildLoading.collectAsStateWithLifecycle()
    val buildError by viewModel.buildError.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        if (players.any { it.isSelectable }) {
            PlayerPicker(players, chosen) { viewModel.choosePlayer(it) }
        }
        CharacterSlider(characters, selectedId, rosterLoading) { viewModel.selectCharacter(it) }
        when {
            buildLoading ->
                GlassCard(accentColor = NeonCyan) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = NeonCyan, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading build…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            buildError != null ->
                GlassCard(accentColor = NeonRed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = NeonRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(buildError ?: "", style = MaterialTheme.typography.bodySmall, color = NeonRed)
                    }
                }
            build != null -> BuildDetail(build ?: return@Column)
            else ->
                GlassCard(accentColor = NeonCyan) {
                    Text(
                        "Select a character to see your equipped gear and the guide's recommendations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
        GlassOutlinedButton(onClick = { viewModel.logout() }, modifier = Modifier.fillMaxWidth(), accentColor = NeonRed) {
            Text("Sign out", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PlayerPicker(
    players: List<PlayerInfo>,
    chosen: ChosenPlayer?,
    onSelect: (PlayerInfo) -> Unit,
) {
    GlassCard(accentColor = NeonPurple) {
        GlassCardHeader("YOUR ACCOUNTS", NeonPurple)
        Spacer(Modifier.height(8.dp))
        players.filter { it.isSelectable }.forEach { player ->
            PlayerRow(player, chosen?.playerId == player.playerId) { onSelect(player) }
        }
    }
}

@Composable
private fun PlayerRow(
    player: PlayerInfo,
    isChosen: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isChosen) NeonPurple.copy(alpha = 0.15f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(player.playerName ?: "Unknown", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                (player.serverName ?: "—") + (player.level?.let { " · Lv.$it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isChosen) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = NeonPurple, modifier = Modifier.size(18.dp))
        }
    }
}

// -- character slider --------------------------------------------------------

@Composable
private fun CharacterSlider(
    characters: List<OfficialCharacter>,
    selectedId: String?,
    loading: Boolean,
    onSelect: (String) -> Unit,
) {
    GlassCard(accentColor = NeonGreen) {
        GlassCardHeader("CHARACTERS", NeonGreen)
        Spacer(Modifier.height(10.dp))
        when {
            loading ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonGreen, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading characters…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            characters.isEmpty() ->
                Text(
                    "No characters loaded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else -> {
                val pagerState = rememberPagerState(pageCount = { characters.size })
                val scope = rememberCoroutineScope()
                LaunchedEffect(selectedId, characters.size) {
                    val index = characters.indexOfFirst { it.roleGbId == selectedId }
                    if (index >= 0 && index != pagerState.currentPage) {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    pageSpacing = 10.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    val character = characters[page]
                    val offset = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                    val distance = abs(offset).coerceAtMost(1f)
                    val scale = 1f - 0.14f * distance
                    val alpha = 1f - 0.45f * distance
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        CharacterCard(character, selectedId == character.roleGbId) { onSelect(character.roleGbId) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    characters.forEachIndexed { index, _ ->
                        val isActive = index == pagerState.currentPage
                        Box(
                            modifier =
                                Modifier
                                    .padding(horizontal = 2.5.dp)
                                    .size(if (isActive) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) NeonGreen else NeonGreen.copy(alpha = 0.25f))
                                    .clickable { if (!isActive) scope.launch { pagerState.animateScrollToPage(index) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: OfficialCharacter,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val accent = NeonGreen
    val rarityTint =
        when {
            character.star >= 5 -> NeonGold
            character.star == 4 -> NeonPurple
            else -> Color.White
        }
    Column(
        modifier =
            Modifier
                .width(168.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush =
                        Brush.verticalGradient(
                            listOf(
                                if (isSelected) accent.copy(alpha = 0.24f) else rarityTint.copy(alpha = 0.09f),
                                Color.White.copy(alpha = 0.02f),
                            ),
                        ),
                )
                .border(
                    if (isSelected) 1.5.dp else 1.dp,
                    if (isSelected) accent.copy(alpha = 0.7f) else rarityTint.copy(alpha = 0.22f),
                    RoundedCornerShape(20.dp),
                )
                .clickable(onClick = onClick)
                .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(
                        1.dp,
                        if (isSelected) accent.copy(alpha = 0.45f) else rarityTint.copy(alpha = 0.30f),
                        RoundedCornerShape(16.dp),
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(rarityTint.copy(alpha = 0.18f), Color.Transparent))),
            )
            if (character.cardPictureUrl.isNotBlank()) {
                RemoteImage(
                    url = character.cardPictureUrl,
                    contentDescription = character.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))),
            )
            if (character.status == OfficialStatus.NEWLY_LAUNCHED || character.status == OfficialStatus.UP) {
                StatusBadge(
                    character.status,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .border(1.dp, Color.Black.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(13.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            character.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            for (i in 1..5) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (i <= character.star) NeonGold else Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    status: OfficialStatus,
    modifier: Modifier = Modifier,
) {
    val (text, color) =
        when (status) {
            OfficialStatus.NEWLY_LAUNCHED -> "NEW" to NeonRed
            OfficialStatus.UP -> "UP" to NeonGold
            else -> return
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(5.dp))
                .background(color.copy(alpha = 0.92f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

// -- build detail ------------------------------------------------------------

@Composable
private fun BuildDetail(build: CharacterBuild) {
    val accent = elementAccent(build.role.element)
    HeroBanner(build, accent)
    BuildOverview(build, accent)
    if (build.equippedWeapon != null) {
        GearSection("YOUR EQUIPPED WEAPON", Icons.Filled.Tune, accent) {
            WeaponLine(build.equippedWeapon)
        }
    }
    if (build.equippedEcho != null) {
        GearSection("YOUR EQUIPPED ECHO", Icons.Filled.SwapHoriz, accent) {
            EchoBlock(build.equippedEcho)
        }
    }
    ResonanceSection(build.resonance, accent)
    if (build.skillPriority.any { (it.currentLevel ?: 0) > 0 }) {
        SkillPrioritySection(build.skillPriority, accent)
    }
    RecommendedSection(build, accent)
    StatsSection(build.recommendedStats, accent)
}

/**
 * Three tiles summarising how far the account is along this character's build: resonance links
 * owned, skills actually upgraded, and stats already at target. Each is a count over the
 * guide's recommendation, so a low number is a to-do rather than a failure.
 */
@Composable
private fun BuildOverview(
    build: CharacterBuild,
    accent: Color,
) {
    val resonanceOwned = build.resonance.count { it.isAcquired }
    val skillsUpgraded = build.skillPriority.count { (it.currentLevel ?: 0) > 0 }
    val statsMet = build.recommendedStats.count { it.isFinished }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OverviewTile("RESONANCE", "$resonanceOwned/${build.resonance.size}", Icons.Filled.Star, accent, Modifier.weight(1f))
        OverviewTile("SKILLS", "$skillsUpgraded/${build.skillPriority.size}", Icons.Filled.Tune, accent, Modifier.weight(1f))
        OverviewTile("STATS", "$statsMet/${build.recommendedStats.size}", Icons.Filled.Check, accent, Modifier.weight(1f))
    }
}

@Composable
private fun OverviewTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.14f), Color.White.copy(alpha = 0.02f))))
                .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun HeroBanner(
    build: CharacterBuild,
    accent: Color,
) {
    val role = build.role
    GlassCard(accentColor = accent) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(260.dp),
        ) {
            if (role.illustrationPictureUrl.isNotBlank()) {
                RemoteImage(
                    url = role.illustrationPictureUrl,
                    contentDescription = role.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent))),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)),
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))),
                        ),
            )
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (role.element.isNotBlank()) ElementBadge(role.element, accent, role.elementImageUrl)
                    build.level?.let { Pill("Lv.$it", accent) }
                }
                Spacer(Modifier.height(6.dp))
                Text(role.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StarRating(role.star)
                    build.source?.let { source ->
                        Text("· $source", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (build.skillDisplay.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            ComboLine(build.skillDisplay, accent)
        }
    }
}

@Composable
private fun ElementBadge(
    element: String,
    accent: Color,
    iconUrl: String,
) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.16f))
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (iconUrl.isNotBlank()) {
            RemoteImage(url = iconUrl, contentDescription = null, modifier = Modifier.size(14.dp), contentScale = ContentScale.Fit)
        }
        Text(element, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StarRating(stars: Int) {
    if (stars <= 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (i <= stars) NeonGold else Color.White.copy(alpha = 0.20f),
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun ComboLine(
    text: String,
    accent: Color,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.07f))
                .padding(10.dp),
    ) {
        Text("COMBO", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun GearSection(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable () -> Unit,
) {
    GlassCard(accentColor = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            GlassCardHeader(title, accent)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun WeaponLine(weapon: Weapon) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
        ) {
            if (weapon.pictureUrl.isNotBlank()) {
                RemoteImage(url = weapon.pictureUrl, contentDescription = weapon.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(weapon.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            weapon.star?.let { StarRating(it) }
            val sub = listOfNotNull(weapon.type, weapon.effectName).joinToString("  ·  ")
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EchoBlock(echo: Echo) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
        ) {
            if (echo.pictureUrl.isNotBlank()) {
                RemoteImage(url = echo.pictureUrl, contentDescription = echo.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(echo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            echo.star?.let { StarRating(it) }
            echo.cost?.let {
                Spacer(Modifier.height(4.dp))
                Text("Cost $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (echo.setEffects.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            echo.setEffects.forEach { set ->
                Pill(set.name + (set.setLevel?.let { " (${it}pc)" } ?: ""), NeonCyan)
            }
        }
    }
    if (echo.attributes.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            echo.attributes.take(4).forEach { attr ->
                Pill(attr.name + (attr.value?.let { " · $it" } ?: ""), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecommendedSection(
    build: CharacterBuild,
    accent: Color,
) {
    if (build.weaponOptions.isEmpty() && build.mainEcho == null && build.spareEcho == null) return
    GearSection("RECOMMENDED", Icons.Filled.SwapHoriz, accent) {
        build.weaponOptions.take(3).forEach { w ->
            RecommendedRow(w.pictureUrl, w.name)
        }
        build.mainEcho?.let { RecommendedRow(it.pictureUrl, it.name) }
        build.spareEcho?.let { RecommendedRow(it.pictureUrl, it.name) }
    }
}

@Composable
private fun RecommendedRow(
    pictureUrl: String,
    name: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
        ) {
            if (pictureUrl.isNotBlank()) {
                RemoteImage(url = pictureUrl, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SkillPrioritySection(
    priority: List<SkillInfo>,
    accent: Color,
) {
    if (priority.isEmpty()) return
    GlassCard(accentColor = accent) {
        GlassCardHeader("SKILL PRIORITY", accent)
        Spacer(Modifier.height(10.dp))
        priority.forEachIndexed { index, skill ->
            SkillPriorityRow(index + 1, skill, accent)
        }
    }
}

@Composable
private fun SkillPriorityRow(
    priority: Int,
    skill: SkillInfo,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(priority.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
        ) {
            if (skill.pictureUrl.isNotBlank()) {
                RemoteImage(url = skill.pictureUrl, contentDescription = skill.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(skill.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (skill.type.isNotBlank()) {
                Text(skill.type, style = MaterialTheme.typography.labelSmall, color = accent)
            }
        }
        Spacer(Modifier.width(8.dp))
        Pill(skillLevelLabel(skill), accent)
    }
}

private fun skillLevelLabel(skill: SkillInfo): String {
    val current = skill.currentLevel
    val recommend = skill.recommendLevel
    return when {
        current != null && recommend != null -> "Lv $current/$recommend"
        current != null -> "Lv $current"
        recommend != null -> "Lv $recommend"
        else -> "—"
    }
}

@Composable
private fun ResonanceSection(
    links: List<ResonanceLink>,
    accent: Color,
) {
    if (links.isEmpty()) return
    GlassCard(accentColor = accent) {
        GlassCardHeader("RESONANCE", accent)
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            itemsIndexed(links, key = { _, link -> link.sequence }) { index, link ->
                ResonanceNode(link, accent, previousAcquired = links.getOrNull(index - 1)?.isAcquired == true)
            }
        }
    }
}

/**
 * One chain link. The connector to the previous link is drawn as this node's own leading
 * spacer, lit only when the previous link is owned — so the chain reads as continuous up to
 * the break rather than as six unrelated icons.
 */
@Composable
private fun ResonanceNode(
    link: ResonanceLink,
    accent: Color,
    previousAcquired: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .width(12.dp)
                        .height(2.dp)
                        .background(if (previousAcquired) accent.copy(alpha = 0.5f) else Color.Transparent),
            )
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            brush =
                                Brush.radialGradient(
                                    listOf(
                                        if (link.isAcquired) accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.04f),
                                        if (link.isAcquired) accent.copy(alpha = 0.12f) else Color.Transparent,
                                    ),
                                ),
                        ).border(
                            width = if (link.isAcquired) 1.5.dp else 1.dp,
                            color = if (link.isAcquired) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (link.isAcquired) {
                    Icon(Icons.Filled.Check, contentDescription = "Owned", tint = accent, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        link.sequence.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .width(12.dp)
                        .height(2.dp)
                        .background(if (link.isAcquired) accent.copy(alpha = 0.5f) else Color.Transparent),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            link.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (link.isAcquired) FontWeight.Bold else FontWeight.Normal,
            color = if (link.isAcquired) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatsSection(
    stats: List<StatRecommendation>,
    accent: Color,
) {
    if (stats.isEmpty()) return
    GlassCard(accentColor = accent) {
        GlassCardHeader("YOUR STATS", accent)
        Spacer(Modifier.height(10.dp))
        stats.forEach { stat ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                ) {
                    if (stat.pictureUrl.isNotBlank()) {
                        RemoteImage(url = stat.pictureUrl, contentDescription = stat.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(stat.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (stat.isFinished) {
                    Icon(Icons.Filled.Check, contentDescription = "Target met", tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Pill(stat.current?.takeIf { it.isNotBlank() } ?: stat.target ?: "—", accent)
            }
            StatProgress(stat, accent)
        }
    }
}

/**
 * A thin bar of the player's current value against the guide's target. Parsed from the
 * "291.6%"/"240.0%" strings; a value Kuro does not format as a number simply draws nothing
 * rather than a misleading full bar.
 */
@Composable
private fun StatProgress(
    stat: StatRecommendation,
    accent: Color,
) {
    val ratio = statRatio(stat.current, stat.target) ?: return
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.55f), accent)),
                    ),
        )
    }
}

/** `current / target` when both parse as numbers, else `null`. `internal` so it can be pinned by a test. */
internal fun statRatio(
    current: String?,
    target: String?,
): Float? {
    val c = current?.trim()?.removeSuffix("%")?.toFloatOrNull() ?: return null
    val t = target?.trim()?.removeSuffix("%")?.toFloatOrNull() ?: return null
    if (t <= 0f) return null
    return c / t
}

// -- shared bits -------------------------------------------------------------

@Composable
private fun Pill(
    text: String,
    accent: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
