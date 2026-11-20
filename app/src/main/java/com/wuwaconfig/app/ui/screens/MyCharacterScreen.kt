package com.wuwaconfig.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassOutlinedButton
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.components.RemoteImage
import com.wuwaconfig.app.ui.theme.WuWaConfigTheme
import com.wuwaconfig.app.ui.theme.elementAccent
import com.wuwaconfig.app.ui.theme.elementNameForGbId
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
    val rosterLoading by viewModel.rosterLoading.collectAsStateWithLifecycle()
    val buildLoading by viewModel.buildLoading.collectAsStateWithLifecycle()
    val colors = characterColors()

    GradientBackground {
        Scaffold(
            topBar = { CharacterTopBar(onBack, colors, rosterLoading || buildLoading, viewModel::refresh) },
            containerColor = Color.Transparent,
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                AuroraWash(colors)
                if (loggedIn) {
                    LoggedInContent(viewModel, Modifier)
                } else {
                    LoginSection(viewModel, Modifier)
                }
            }
        }
    }
}

// -- palette -----------------------------------------------------------------

/**
 * This screen's own accent set: an aurora violet/magenta pair for chrome, with gold, rose and
 * indigo carrying state. The character's element accent still drives the build panels on top
 * of it — except cyan, which is deliberately remapped (see [nonCyanAccent]).
 *
 * Deliberately fixed rather than read from `Neon*`: the roster and build guide are meant to
 * read as one cohesive surface regardless of which app palette is active. Light mode swaps in
 * darker variants of the same hues so they stay above the AA contrast floor on a light card.
 */
private data class CharacterColors(
    val violet: Color,
    val magenta: Color,
    val gold: Color,
    val rose: Color,
    val indigo: Color,
    val isLight: Boolean,
)

@Composable
private fun characterColors(): CharacterColors {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    return remember(isLight) {
        if (isLight) {
            CharacterColors(
                violet = Color(0xFF6D28D9),
                magenta = Color(0xFFBE185D),
                gold = Color(0xFFA16207),
                rose = Color(0xFFBE123C),
                indigo = Color(0xFF4338CA),
                isLight = true,
            )
        } else {
            CharacterColors(
                violet = Color(0xFFA855F7),
                magenta = Color(0xFFF472B6),
                gold = Color(0xFFFBBF24),
                rose = Color(0xFFFB7185),
                indigo = Color(0xFF818CF8),
                isLight = false,
            )
        }
    }
}

/**
 * The guide's element accent, with any cyan-family result replaced by this screen's magenta.
 *
 * `elementAccent` answers cyan for Glacio *and* for every character whose element string the
 * guide leaves blank, which is what turned the whole build panel cyan. Every other element
 * keeps its own colour; only the blue-green family (blue and green high, red low) is remapped.
 */
private fun nonCyanAccent(
    element: String,
    colors: CharacterColors,
): Color {
    val accent = elementAccent(element)
    val cyanFamily = accent.green > 0.45f && accent.blue > 0.45f && accent.red < 0.35f
    return if (cyanFamily) colors.magenta else accent
}

/**
 * The accent carried by a guide element id, with cyan still remapped for this screen.
 * Falls back to the section's magenta when the guide did not send an element id.
 */
private fun cardAccent(
    elementGbId: String?,
    colors: CharacterColors,
): Color {
    val name = elementGbId?.let(::elementNameForGbId) ?: return colors.magenta
    return nonCyanAccent(name, colors)
}

/** A soft vertical aurora wash behind the content, tied to the section palette. */
@Composable
private fun AuroraWash(colors: CharacterColors) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.violet.copy(alpha = if (colors.isLight) 0.06f else 0.13f),
                        0.42f to Color.Transparent,
                        1f to colors.magenta.copy(alpha = if (colors.isLight) 0.04f else 0.07f),
                    ),
                ),
    )
}

// -- top bar -----------------------------------------------------------------

@Composable
private fun CharacterTopBar(
    onBack: () -> Unit,
    colors: CharacterColors,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, colors.violet.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.violet, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "WUTHERING WAVES",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
            )
            Text(
                "My Characters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // This used to be a decorative sparkle badge — a control that looked tappable and
        // did nothing. It is now the screen's refresh, which the roster and the build both
        // needed a way to re-fetch.
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(1.dp, colors.violet.copy(alpha = 0.30f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.violet, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh roster and build", tint = colors.violet, modifier = Modifier.size(18.dp))
                }
            }
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
    // `prefill` is an async StateFlow, so a plain `remember` captured whatever the
    // first composition saw and silently dropped a later emission — the saved
    // email never pre-filled. Seed once, only while the field is untouched, so a
    // user edit is never clobbered and the value survives rotation.
    var email by rememberSaveable { mutableStateOf("") }
    // Deliberately `remember`, not `rememberSaveable`: saved state is marshalled into the
    // Activity bundle, and a password has no business surviving process death there. The cost
    // is that a rotation clears the password field — the email is the part worth restoring.
    var password by remember { mutableStateOf("") }
    LaunchedEffect(prefill) {
        val seed = prefill
        if (seed != null && email.isEmpty()) email = seed
    }
    val colors = characterColors()

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        LoginHero(colors)
        Text(
            "KURO ACCOUNT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.6.sp,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )
        GlassTextField(email, { email = it }, "Kuro email", Icons.Filled.Email, colors)
        GlassTextField(password, { password = it }, "Password", Icons.Filled.Lock, colors, isPassword = true)
        if (error != null) {
            ErrorBanner(error ?: "", colors)
        }
        GradientCta(
            text = "Sign in",
            busy = busy,
            enabled = !busy,
            colors = colors,
            onClick = { viewModel.login(email, password) },
        )
        SecurityFootnote(colors)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LoginHero(colors: CharacterColors) {
    GlassCard(accentColor = colors.violet) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(colors.violet.copy(alpha = 0.55f), colors.magenta.copy(alpha = 0.18f))))
                        .border(1.dp, colors.violet.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Sign in to continue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Your roster, equipped gear and the guide's build, in one place.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HeroFeature(Icons.Filled.Shield, "Reads your equipped gear", "Kuro's official guide API", colors, colors.gold)
        Spacer(Modifier.height(10.dp))
        HeroFeature(Icons.Filled.Lock, "Password is never stored", "Only the session token is kept", colors, colors.rose)
        Spacer(Modifier.height(14.dp))
        Text(
            "We sign into Kuro's official guide on your behalf to read your account. Your password is used only for that sign-in and is discarded immediately afterwards.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun HeroFeature(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colors: CharacterColors,
    accent: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.gold, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    colors: CharacterColors,
    isPassword: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        leadingIcon = { Icon(icon, contentDescription = null, tint = colors.violet) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Email),
        shape = RoundedCornerShape(16.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.violet,
                unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.7f),
                focusedLabelColor = colors.violet,
                unfocusedLabelColor = scheme.onSurfaceVariant,
                cursorColor = colors.violet,
                focusedContainerColor = scheme.surface.copy(alpha = 0.35f),
                unfocusedContainerColor = scheme.surface.copy(alpha = 0.22f),
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GradientCta(
    text: String,
    busy: Boolean,
    enabled: Boolean,
    colors: CharacterColors,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
                .background(Brush.horizontalGradient(listOf(colors.violet, colors.magenta)), shape),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 0.4.sp)
    }
}

@Composable
private fun SecurityFootnote(colors: CharacterColors) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = colors.gold, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Encrypted session, never your password.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    colors: CharacterColors,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.rose.copy(alpha = 0.10f))
                .border(1.dp, colors.rose.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.rose, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = colors.rose)
    }
}

// -- logged-in flow ----------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
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
    val rosterError by viewModel.rosterError.collectAsStateWithLifecycle()
    // `build` is a delegated property, so it cannot smart-cast; bind it once instead of
    // re-reading it per branch (and instead of the `build ?: return@Column` that used to
    // abandon the rest of the column).
    val currentBuild = build
    val selectablePlayers = players.filter { it.isSelectable }
    val colors = characterColors()

    // Pull-to-refresh: the roster and the selected build are both fetched from the guide,
    // and until now the only way to re-fetch was the retry button on a failed fetch.
    PullToRefreshBox(
        isRefreshing = rosterLoading || buildLoading,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            if (selectablePlayers.isNotEmpty()) {
                AccountMenu(selectablePlayers, chosen, colors) { viewModel.choosePlayer(it) }
            }
            CharacterSlider(
                characters = characters,
                selectedId = selectedId,
                loading = rosterLoading,
                error = rosterError,
                colors = colors,
                onRetry = viewModel::refresh,
                onSelect = { viewModel.selectCharacter(it) },
            )
            when {
                buildLoading -> LoadingCard("Loading build…", colors)
                buildError != null -> ErrorCard(buildError ?: "", onRetry = viewModel::refresh)
                currentBuild != null -> BuildDetail(currentBuild)
                else -> EmptySelectionCard(colors)
            }
            SignOutButton(colors) { viewModel.logout() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmptySelectionCard(colors: CharacterColors) {
    GlassCard(accentColor = colors.violet) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentMedallion(Icons.Filled.AutoAwesome, colors.violet, colors, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "No character selected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Select a character to see your equipped gear and the guide's recommendations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SignOutButton(
    colors: CharacterColors,
    onSignOut: () -> Unit,
) {
    GlassOutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth(), accentColor = colors.rose) {
        Text("Sign out", fontWeight = FontWeight.Bold)
    }
}

/**
 * The account switcher.
 *
 * This was one full-width row per account, which cost ~110dp of vertical space before the
 * roster even started and grew with every region the user had a character in. A single
 * dropdown shows the active account at all times and costs one row no matter how many
 * accounts are linked.
 */
@Composable
private fun AccountMenu(
    selectablePlayers: List<PlayerInfo>,
    chosen: ChosenPlayer?,
    colors: CharacterColors,
    onSelect: (PlayerInfo) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = selectablePlayers.firstOrNull { it.playerId == chosen?.playerId } ?: selectablePlayers.firstOrNull()
    val canSwitch = selectablePlayers.size > 1
    val shape = RoundedCornerShape(16.dp)
    GlassCard(accentColor = colors.violet) {
        SectionHeader(
            Icons.Filled.Person,
            "ACCOUNT",
            colors.violet,
            trailing = if (canSwitch) "${selectablePlayers.size} linked" else null,
        )
        Spacer(Modifier.height(10.dp))
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
                        .border(1.dp, colors.violet.copy(alpha = 0.30f), shape)
                        .clickable(enabled = canSwitch, onClick = { expanded = true })
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(active?.playerName)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        active?.playerName ?: "Unknown account",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        accountSubtitle(active),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (canSwitch) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = "Switch account",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                selectablePlayers.forEach { player ->
                    val isActive = player.playerId == active?.playerId
                    val trailing: (@Composable () -> Unit)? =
                        if (isActive) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Current account",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            null
                        }
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    player.playerName ?: "Unknown account",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    accountSubtitle(player),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        },
                        trailingIcon = trailing,
                        onClick = {
                            expanded = false
                            onSelect(player)
                        },
                    )
                }
            }
        }
    }
}

/** "Asia  ·  Lv.80", with whichever half the roster actually answered. */
private fun accountSubtitle(player: PlayerInfo?): String {
    if (player == null) return "—"
    val parts = listOfNotNull(player.serverName?.takeIf { it.isNotBlank() }, player.level?.let { "Lv.$it" })
    return parts.joinToString("  ·  ").ifBlank { "—" }
}

/**
 * The account initial. Drawn from the scheme rather than an accent so the avatar does not
 * compete with the roster's element colours.
 */
@Composable
private fun AccountAvatar(name: String?) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(scheme.surfaceVariant, scheme.surface)))
                .border(1.dp, scheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (name ?: "?").trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = scheme.onSurfaceVariant,
        )
    }
}

// -- character slider --------------------------------------------------------

/**
 * One character card, at medium size: big enough to read the portrait, small enough that the
 * neighbouring pages are clearly visible and the roster does not dominate the screen.
 */
private val CHARACTER_CARD_WIDTH = 148.dp

/** The portrait square inside [CHARACTER_CARD_WIDTH]; the rest is the name and star row. */
private val CHARACTER_CARD_ART = 124.dp

/**
 * Position indicator for the character carousel.
 *
 * A dot per character was correct when the roster was short and quietly wrong as
 * it grew: at ~40 entries the dots overflowed the card width, so the tail was
 * clipped and could not be tapped to jump to a character. This is a single track
 * whose filled portion tracks `currentPage + offsetFraction`, which stays
 * meaningful for one character or a hundred.
 */
@Composable
private fun CarouselTrack(
    pageCount: Int,
    currentPage: Int,
    offsetFraction: Float,
    accent: Color,
    colors: CharacterColors,
    label: String?,
    position: String,
) {
    if (pageCount <= 1) return
    val progress by animateFloatAsState(
        targetValue = (currentPage + offsetFraction).coerceIn(0f, (pageCount - 1).toFloat()),
        label = "carouselTrack",
    )
    Column {
        // Which character is centred, and how far down the roster it is. Without this the
        // carousel gave no position at all — the only cue was the check mark on the card.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                position,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(7.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // A "one page visible" segment. The 12dp floor keeps it from becoming a
            // sub-pixel sliver on a 40-character roster.
            val segment = (maxWidth / pageCount).coerceAtLeast(12.dp)
            val travel = (maxWidth - segment).coerceAtLeast(0.dp)
            val t = if (pageCount > 1) progress / (pageCount - 1) else 0f

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(segment)
                            .fillMaxHeight()
                            .offset(x = travel * t)
                            .clip(CircleShape)
                            .background(Brush.horizontalGradient(listOf(accent, colors.violet))),
                )
            }
        }
    }
}

/**
 * A placeholder in the shape of a roster card, breathing while the roster loads.
 *
 * The shimmer is a slow opacity pulse rather than a moving highlight: a moving gradient on
 * several cards at once reads as noise on a dark theme, and this screen already animates the
 * carousel and the progress bars.
 */
@Composable
private fun SkeletonCard() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = pulse)
    Column(
        modifier = Modifier.width(CHARACTER_CARD_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(CHARACTER_CARD_ART)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tint),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier =
                Modifier
                    .width(78.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(tint),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier =
                Modifier
                    .width(54.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(tint.copy(alpha = pulse * 0.7f)),
        )
    }
}

@Composable
private fun CharacterSlider(
    characters: List<OfficialCharacter>,
    selectedId: String?,
    loading: Boolean,
    error: String?,
    colors: CharacterColors,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
) {
    // The selected card is the only one that takes a text colour, and that colour comes from
    // her element id in the roster — available immediately, and never a stale build's.
    val selectedAccent = characters.firstOrNull { it.roleGbId == selectedId }?.let { cardAccent(it.elementGbId, colors) } ?: colors.magenta
    GlassCard(accentColor = colors.magenta) {
        SectionHeader(
            Icons.Filled.AutoAwesome,
            "ROSTER",
            colors.magenta,
            trailing = if (characters.isNotEmpty()) characters.size.toString() else null,
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading ->
                Column {
                    // A skeleton of the carousel rather than a bare spinner: the roster card
                    // is the screen's centrepiece, so the space it will occupy is shown while
                    // it loads instead of a line of text.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        repeat(2) { SkeletonCard() }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Loading your roster…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            error != null -> ErrorContent(error, onRetry)
            characters.isEmpty() ->
                Text(
                    "No characters loaded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else -> {
                val pagerState = rememberPagerState(pageCount = { characters.size })
                // Selection -> pager. Keyed on the roster, not its size: switching accounts can
                // swap in a roster of the same length, and a size key left the pager parked on
                // a page index that now pointed at a different character.
                LaunchedEffect(selectedId, characters) {
                    val index = characters.indexOfFirst { it.roleGbId == selectedId }
                    if (index >= 0 && index != pagerState.currentPage) pagerState.animateScrollToPage(index)
                }
                // Pager -> selection. Without this a swipe moved the cards while the build
                // below kept describing the previously selected character, and the centred
                // card carried no check mark.
                LaunchedEffect(pagerState.settledPage) {
                    pageSelection(characters.map { it.roleGbId }, pagerState.settledPage, selectedId)?.let(onSelect)
                }
                // The page slot must be sized to the CARD, not to the viewport.
                //
                // With `pageSize` left at Fill, every page slot was the full pager
                // width minus the content padding, while CharacterCard is a fixed
                // 168dp. The card therefore floated in the middle of a much wider
                // slot, the neighbouring pages were pushed a whole viewport-width
                // away, and only about 14dp of them peeked in at each edge - so
                // the "carousel" rendered as one card alone in black space with no
                // hint that it swiped. Pinning the slot to card + peek is what
                // makes the neighbours visible and turns it back into a carousel.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val peek = 44.dp
                    val pageWidth = CHARACTER_CARD_WIDTH + peek
                    // Centre the run of pages: leading padding must equal
                    // (viewport - pageWidth) / 2 or the "current" page sits left
                    // of centre.
                    val sidePad = ((maxWidth - pageWidth) / 2).coerceAtLeast(0.dp)
                    HorizontalPager(
                        state = pagerState,
                        pageSize = PageSize.Fixed(pageWidth),
                        contentPadding = PaddingValues(horizontal = sidePad),
                        pageSpacing = 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) { page ->
                        val character = characters[page]
                        val isSelected = selectedId == character.roleGbId
                        val offset = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                        val distance = abs(offset).coerceAtMost(1f)
                        val scale = 1f - 0.07f * distance
                        val alpha = 1f - 0.28f * distance
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
                            CharacterCard(
                                character = character,
                                isSelected = isSelected,
                                accent = selectedAccent,
                                colors = colors,
                                onClick = { onSelect(character.roleGbId) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // A single slim track rather than one dot per character.
                //
                // The roster runs to ~40 entries, and 40 dots overflowed the card
                // width outright - the last few were clipped and unreachable, so
                // they could neither be seen nor tapped. A proportional track
                // stays correct at any roster size and does not grow.
                CarouselTrack(
                    pageCount = characters.size,
                    currentPage = pagerState.currentPage,
                    offsetFraction = pagerState.currentPageOffsetFraction,
                    accent = selectedAccent,
                    colors = colors,
                    label = characters.getOrNull(pagerState.currentPage)?.name,
                    position = "${pagerState.currentPage + 1} / ${characters.size}",
                )
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: OfficialCharacter,
    isSelected: Boolean,
    accent: Color,
    colors: CharacterColors,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier =
            Modifier
                .width(CHARACTER_CARD_WIDTH)
                .then(
                    if (isSelected) {
                        Modifier.shadow(
                            elevation = 14.dp,
                            shape = shape,
                            clip = false,
                            ambientColor = accent.copy(alpha = 0.45f),
                            spotColor = accent.copy(alpha = 0.60f),
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(Brush.verticalGradient(listOf(scheme.surfaceVariant.copy(alpha = 0.35f), scheme.surface.copy(alpha = 0.55f))))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) accent.copy(alpha = 0.85f) else scheme.outlineVariant.copy(alpha = 0.45f),
                    shape = shape,
                )
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(CHARACTER_CARD_ART)
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceVariant.copy(alpha = 0.55f)),
        ) {
            if (character.cardPictureUrl.isNotBlank()) {
                // Decorative: the character's name is the Text directly below the art, so a
                // contentDescription here made every card announce its name twice.
                RemoteImage(
                    url = character.cardPictureUrl,
                    contentDescription = null,
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
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f)))),
            )
            if (character.status == OfficialStatus.NEWLY_LAUNCHED || character.status == OfficialStatus.UP) {
                StatusBadge(
                    character.status,
                    colors,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = if (accent.luminance() > 0.5f) Color(0xFF0B0612) else Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            character.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            // Only the selected name takes a colour, and that colour is her element's. Every
            // other name stays on the scheme's text colour, so the roster reads as one list
            // instead of a rainbow.
            color = if (isSelected) accent else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            for (i in 1..5) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (i <= character.star) colors.gold else scheme.onSurface.copy(alpha = 0.18f),
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    status: OfficialStatus,
    colors: CharacterColors,
    modifier: Modifier = Modifier,
) {
    val (text, brush) =
        when (status) {
            OfficialStatus.NEWLY_LAUNCHED -> "NEW" to Brush.horizontalGradient(listOf(colors.rose, colors.magenta))
            OfficialStatus.UP -> "UP" to Brush.horizontalGradient(listOf(colors.gold, Color(0xFFFF9F43)))
            else -> return
        }
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color(0xFF140B04), fontWeight = FontWeight.ExtraBold)
    }
}

// -- build detail ------------------------------------------------------------

@Composable
private fun BuildDetail(build: CharacterBuild) {
    val colors = characterColors()
    val accent =
        build.role.elementGbId.takeIf { it.isNotBlank() }
            ?.let { cardAccent(it, colors) }
            ?: nonCyanAccent(build.role.element, colors)
    HeroBanner(build, accent)
    BuildOverview(build, accent)
    if (build.equippedWeapon != null) {
        GearSection("YOUR EQUIPPED WEAPON", Icons.Filled.Tune, accent) {
            WeaponLine(build.equippedWeapon, accent)
        }
    }
    if (build.equippedEcho != null) {
        GearSection("YOUR EQUIPPED ECHO", Icons.Filled.SwapHoriz, accent) {
            EchoBlock(build.equippedEcho, accent)
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
 * Three rings summarising how far the account is along this character's build: resonance links
 * owned, skills actually upgraded, and stats already at target. Each is a count over the
 * guide's recommendation, so a low number is a to-do rather than a failure.
 */
@Composable
private fun BuildOverview(
    build: CharacterBuild,
    accent: Color,
) {
    val colors = characterColors()
    val resonanceOwned = build.resonance.count { it.isAcquired }
    val skillsUpgraded = build.skillPriority.count { (it.currentLevel ?: 0) > 0 }
    val statsMet = build.recommendedStats.count { it.isFinished }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OverviewRing(
            "RESONANCE",
            resonanceOwned,
            build.resonance.size,
            Icons.Filled.Star,
            accent,
            colors,
            Modifier.weight(1f),
        )
        OverviewRing(
            "SKILLS",
            skillsUpgraded,
            build.skillPriority.size,
            Icons.Filled.Tune,
            accent,
            colors,
            Modifier.weight(1f),
        )
        OverviewRing(
            "STATS",
            statsMet,
            build.recommendedStats.size,
            Icons.Filled.Check,
            accent,
            colors,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun OverviewRing(
    label: String,
    value: Int,
    total: Int,
    icon: ImageVector,
    accent: Color,
    colors: CharacterColors,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total > 0) (value.toFloat() / total).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(targetValue = fraction, label = "overviewRing")
    val complete = total > 0 && value >= total
    val ringColor = if (complete) colors.gold else accent
    // Read outside the Canvas: the draw lambda is not a composition scope.
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.16f), MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)),
                    ),
                )
                .border(1.dp, ringColor.copy(alpha = 0.30f), shape)
                .padding(vertical = 13.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx()
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (animated > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, tint = ringColor, modifier = Modifier.size(12.dp))
                Text(
                    "$value/$total",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeroBanner(
    build: CharacterBuild,
    accent: Color,
) {
    val role = build.role
    val colors = characterColors()
    val elementLabel = elementNameForGbId(role.elementGbId) ?: role.element
    GlassCard(accentColor = accent) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(306.dp)
                    .clip(RoundedCornerShape(18.dp)),
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
                            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent))),
                )
            }
            if (role.elementImageUrl.isNotBlank()) {
                // A watermark behind the title rather than a badge beside it, so the element
                // still reads on a portrait that has no room for one.
                RemoteImage(
                    url = role.elementImageUrl,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 66.dp).size(84.dp).alpha(0.18f),
                    contentScale = ContentScale.Fit,
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.90f)),
                            ),
                        ),
            )
            // The badges live on the art's top edge, clear of the name below.
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (elementLabel.isNotBlank()) ElementBadge(elementLabel, accent, role.elementImageUrl)
                Spacer(Modifier.weight(1f))
                build.level?.let { ArtPill("Lv.$it") }
            }
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
            ) {
                Text(
                    role.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StarRating(role.star, colors, 14.dp)
                    build.source?.let { source ->
                        Text(
                            "· $source",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // A hairline of her element colour along the bottom edge: the one place the
            // banner can carry it without tinting the portrait itself.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(3.dp)
                        .background(Brush.horizontalGradient(listOf(accent, Color.Transparent))),
            )
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
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.dp, accent.copy(alpha = 0.75f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (iconUrl.isNotBlank()) {
            RemoteImage(url = iconUrl, contentDescription = null, modifier = Modifier.size(14.dp), contentScale = ContentScale.Fit)
        }
        Text(element, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun StarRating(
    stars: Int,
    colors: CharacterColors,
    size: androidx.compose.ui.unit.Dp = 13.dp,
) {
    if (stars <= 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (i <= stars) colors.gold else Color.White.copy(alpha = 0.20f),
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
private fun ComboLine(
    text: String,
    accent: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.14f), accent.copy(alpha = 0.03f))))
                .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text("COMBO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    accent: Color,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.06f))))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GearSection(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(accentColor = accent) {
        SectionHeader(icon, title, accent)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ArtTile(
    pictureUrl: String,
    description: String?,
    accent: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))))
                .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(16.dp)),
    ) {
        if (pictureUrl.isNotBlank()) {
            RemoteImage(url = pictureUrl, contentDescription = description, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun WeaponLine(
    weapon: Weapon,
    accent: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)))
                .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtTile(weapon.pictureUrl, weapon.name, accent, 64.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                weapon.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            weapon.star?.let {
                Spacer(Modifier.height(3.dp))
                StarRating(it, characterColors(), 12.dp)
            }
            val sub = listOfNotNull(weapon.type, weapon.effectName).joinToString("  ·  ")
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EchoBlock(
    echo: Echo,
    accent: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)))
                .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtTile(echo.pictureUrl, echo.name, accent, 64.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                echo.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            echo.star?.let {
                Spacer(Modifier.height(3.dp))
                StarRating(it, characterColors(), 12.dp)
            }
            echo.cost?.let {
                Spacer(Modifier.height(5.dp))
                Text("Cost $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (echo.setEffects.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            echo.setEffects.forEach { set ->
                GlassPill(set.name + (set.setLevel?.let { " (${it}pc)" } ?: ""))
            }
        }
    }
    if (echo.attributes.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            echo.attributes.take(4).forEach { attr ->
                GlassPill(attr.name + (attr.value?.let { " · $it" } ?: ""))
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
    val colors = characterColors()
    GearSection("RECOMMENDED", Icons.Filled.SwapHoriz, accent) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(build.weaponOptions.take(3)) { weapon ->
                RecommendedCard(weapon.pictureUrl, weapon.name, "WEAPON", accent)
            }
            build.mainEcho?.let { echo ->
                item(key = "main-echo") { RecommendedCard(echo.pictureUrl, echo.name, "MAIN ECHO", accent) }
            }
            build.spareEcho?.let { echo ->
                item(key = "spare-echo") { RecommendedCard(echo.pictureUrl, echo.name, "ECHO", colors.gold) }
            }
        }
    }
}

@Composable
private fun RecommendedCard(
    pictureUrl: String,
    name: String,
    tag: String,
    accent: Color,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            Modifier
                .width(112.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.40f))
                .border(1.dp, accent.copy(alpha = 0.20f), shape)
                .padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.14f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            if (pictureUrl.isNotBlank()) {
                RemoteImage(url = pictureUrl, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 28.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun SkillPrioritySection(
    priority: List<SkillInfo>,
    accent: Color,
) {
    if (priority.isEmpty()) return
    val colors = characterColors()
    GlassCard(accentColor = accent) {
        SectionHeader(Icons.Filled.Tune, "SKILL PRIORITY", accent)
        Spacer(Modifier.height(10.dp))
        priority.forEachIndexed { index, skill ->
            SkillPriorityRow(index + 1, skill, accent, colors)
        }
    }
}

@Composable
private fun SkillPriorityRow(
    priority: Int,
    skill: SkillInfo,
    accent: Color,
    colors: CharacterColors,
) {
    val finished =
        skill.currentLevel != null &&
            skill.recommendLevel != null &&
            skill.currentLevel >= skill.recommendLevel
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.08f))))
                    .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(priority.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(10.dp))
        ArtTile(skill.pictureUrl, skill.name, accent, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                skill.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (skill.type.isNotBlank()) {
                Text(skill.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        GlassPill(skillLevelLabel(skill))
        if (finished) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Check, contentDescription = "Skill at target level", tint = colors.gold, modifier = Modifier.size(15.dp))
        }
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
        SectionHeader(Icons.Filled.Star, "RESONANCE", accent)
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
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            brush =
                                Brush.radialGradient(
                                    listOf(
                                        if (link.isAcquired) accent.copy(alpha = 0.42f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                                        if (link.isAcquired) accent.copy(alpha = 0.14f) else Color.Transparent,
                                    ),
                                ),
                        ).border(
                            width = if (link.isAcquired) 1.5.dp else 1.dp,
                            color = if (link.isAcquired) accent.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (link.isAcquired) {
                    Icon(Icons.Filled.Check, contentDescription = "Owned", tint = accent, modifier = Modifier.size(17.dp))
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
    val colors = characterColors()
    GlassCard(accentColor = accent) {
        SectionHeader(Icons.Filled.Check, "YOUR STATS", accent)
        Spacer(Modifier.height(10.dp))
        stats.forEach { stat ->
            val finished = stat.isFinished
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.14f), MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))))
                            .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape(10.dp)),
                ) {
                    if (stat.pictureUrl.isNotBlank()) {
                        RemoteImage(url = stat.pictureUrl, contentDescription = stat.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stat.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (finished) {
                    Icon(Icons.Filled.Check, contentDescription = "Target met", tint = colors.gold, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                }
                GlassPill(statProgressLabel(stat))
            }
            StatProgress(stat, accent, colors)
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
    colors: CharacterColors,
) {
    val ratio = statRatio(stat.current, stat.target) ?: return
    // Past the target the bar is clamped at full width, so its colour is the only cue left
    // that the build overshoots: gold above 1.0, the element accent below.
    val barColor = if (ratio > 1f) colors.gold else accent
    val animated by animateFloatAsState(targetValue = ratio.coerceIn(0f, 1f), label = "statProgress")
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.55f), barColor))),
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

/** A decorated icon square shared by section headers and empty states. */
@Composable
private fun AccentMedallion(
    icon: ImageVector,
    accent: Color,
    colors: CharacterColors,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape((size.value / 3).dp))
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.08f))))
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape((size.value / 3).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (colors.isLight) accent else Color.White, modifier = Modifier.size(size / 2.3f))
    }
}

/** A card with a spinner and a one-line message, for any in-flight fetch. */
@Composable
private fun LoadingCard(
    message: String,
    colors: CharacterColors,
) {
    val transition = rememberInfiniteTransition(label = "loading")
    val pulse by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.40f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "loadingPulse",
    )
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = pulse)
    GlassCard(accentColor = colors.magenta) {
        // The build is a long read (weapon, echo, resonance, skills, stats), so the wait is
        // shown as the shape of the thing being fetched rather than a lone spinner.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tint),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier =
                Modifier
                    .width(120.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(tint),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.magenta, strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A failed fetch: the message plus the retry that used to be missing. */
@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = characterColors()
    GlassCard(accentColor = colors.rose) {
        ErrorContent(message, onRetry)
    }
}

/**
 * [ErrorCard] without the card, for callers that already sit inside one.
 *
 * Both failed fetches on this screen used to be dead ends: a roster failure rendered as "No
 * characters loaded yet" — indistinguishable from an account with no characters — and a build
 * failure as a message with no way to try again, so the only recovery was signing out.
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = characterColors()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.rose, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = colors.rose)
        }
        Spacer(Modifier.height(8.dp))
        GlassOutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth(), accentColor = colors.rose) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Which character the carousel must report after settling on [page].
 *
 * `null` means "nothing to report": the page is out of range (a reload shrank the roster) or
 * it already matches [selectedId]. Returning `null` rather than the current id is what keeps
 * the two pager effects from echoing each other — the pager-to-selection effect would
 * otherwise re-select on every settle and re-trigger the selection-to-pager scroll.
 */
internal fun pageSelection(
    ids: List<String>,
    page: Int,
    selectedId: String?,
): String? = ids.getOrNull(page)?.takeIf { it != selectedId }

/** "current / target" so the guide's goal stays visible once a current value exists. */
internal fun statProgressLabel(stat: StatRecommendation): String {
    val current = stat.current?.takeIf { it.isNotBlank() }
    val target = stat.target?.takeIf { it.isNotBlank() }
    return listOfNotNull(current, target).joinToString(" / ").ifBlank { "—" }
}

/**
 * A neutral value chip.
 *
 * These used to take an accent colour each, which is why a single echo row could show four
 * differently coloured pills. Values now read in the scheme's own text colour; only the
 * selected character's name carries a colour, and that one is her element's.
 */
@Composable
private fun GlassPill(text: String) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** The same chip for use over artwork, where a scheme tint would vanish into the picture. */
@Composable
private fun ArtPill(text: String) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.40f))
                .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * A real card preview. The previous pair rendered a literal `Text("MyCharacter Preview")`, so
 * neither showed anything about this file's layout. Network art cannot load in a preview, so
 * these cards exercise the [RemoteImage] error path — now a dimmed warning glyph rather than
 * the blank box that read as a missing asset.
 */
@Preview(name = "Character cards — dark", showBackground = true, widthDp = 380)
@Composable
private fun CharacterCardDarkPreview() {
    WuWaConfigTheme(themeMode = "dark") {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CharacterCard(PreviewCharacter, isSelected = true, accent = Color(0xFFA855F7), colors = characterColors(), onClick = {})
            CharacterCard(
                PreviewCharacter.copy(name = "Cartethyia", star = 4, status = OfficialStatus.UP),
                isSelected = false,
                accent = Color(0xFFA855F7),
                colors = characterColors(),
                onClick = {},
            )
        }
    }
}

@Preview(name = "Character cards — light", showBackground = true, widthDp = 380)
@Composable
private fun CharacterCardLightPreview() {
    WuWaConfigTheme(themeMode = "light") {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CharacterCard(
                PreviewCharacter.copy(status = OfficialStatus.UP),
                isSelected = false,
                accent = Color(0xFFA855F7),
                colors = characterColors(),
                onClick = {},
            )
        }
    }
}

private val PreviewCharacter =
    OfficialCharacter(
        roleGbId = "preview-role",
        name = "Cantarella",
        star = 5,
        status = OfficialStatus.NEWLY_LAUNCHED,
        cardPictureUrl = "https://guide-res.aki-game.net/preview/cantarella.webp",
        illustrationPictureUrl = "",
    )
