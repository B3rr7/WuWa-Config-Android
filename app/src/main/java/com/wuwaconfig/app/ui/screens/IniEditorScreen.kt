package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuwaconfig.app.model.GamePaths
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*

private val LINE_HEIGHT = 20.sp
private val EDITOR_BG = Color(0xFF16162A)
private val GUTTER_BG = Color(0xFF101020)
private val BASE_TEXT = Color(0xFFE0E0E0)
private val GUTTER_TEXT = Color(0xFF5A5A78)

private val INI_SECTION = SpanStyle(color = NeonPurple, fontWeight = FontWeight.Bold)
private val INI_KEY = SpanStyle(color = NeonCyan)
private val INI_VALUE = SpanStyle(color = NeonGreen)
private val INI_COMMENT = SpanStyle(color = Color(0xFF6A9955))
private val INI_EQ = SpanStyle(color = Color(0xFF8A8AA0))
private val INI_SEARCH = SpanStyle(background = Color(0xFF3A2E00))
private val INI_SEARCH_CURRENT = SpanStyle(background = Color(0xFF6B5300))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IniEditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val editingFileName by viewModel.editingFileName.collectAsStateWithLifecycle()
    val iniContent by viewModel.iniEditorContent.collectAsStateWithLifecycle()
    val isLoading by viewModel.iniEditorLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.iniEditorError.collectAsStateWithLifecycle()
    val successMessage by viewModel.iniEditorSuccess.collectAsStateWithLifecycle()

    var editorText by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var currentMatch by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val vertical = rememberScrollState()

    val lineCount = editorText.lines().size + if (editorText.endsWith("\n")) 1 else 0
    val matches = remember(query, editorText) { findMatches(editorText, query) }
    val safeMatch = if (matches.isEmpty()) 0 else currentMatch.coerceIn(0, matches.lastIndex)
    val isDirty = editorText != (iniContent ?: "")

    val iniTransform =
        remember(matches, safeMatch) {
            VisualTransformation { annotated ->
                TransformedText(highlightIni(annotated.text, matches, safeMatch), OffsetMapping.Identity)
            }
        }

    LaunchedEffect(Unit) {
        viewModel.syncConfigHashes()
    }
    LaunchedEffect(iniContent) {
        iniContent?.let { editorText = it }
    }
    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearIniEditorSuccess()
        }
    }
    LaunchedEffect(showSearch) {
        if (showSearch) focusRequester.requestFocus()
    }
    LaunchedEffect(safeMatch, query) {
        if (matches.isNotEmpty() && lineCount > 0) {
            val line = editorText.substring(0, matches[safeMatch].first).count { it == '\n' }
            val target = ((line.toFloat() / lineCount) * vertical.maxValue).toInt()
            vertical.scrollTo(target.coerceAtMost(vertical.maxValue))
        }
    }

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (editingFileName != null) editingFileName!! else "INI Editor",
                                fontWeight = FontWeight.Bold,
                            )
                            if (editingFileName != null && isDirty) {
                                Spacer(Modifier.width(8.dp))
                                Text("●", color = NeonRed, fontSize = 14.sp)
                            }
                        }
                    },
                    accentColor = NeonCyan,
                    navigationIcon = {
                        IconButton(onClick = {
                            if (editingFileName != null) {
                                viewModel.returnToFileList()
                                showSearch = false
                                query = ""
                                currentMatch = 0
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NeonPurple)
                        }
                    },
                    actions = {
                        if (editingFileName != null) {
                            IconButton(
                                onClick = {
                                    showSearch = !showSearch
                                    if (!showSearch) {
                                        query = ""
                                        currentMatch = 0
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    "Search",
                                    tint = if (showSearch) NeonAmber else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                            IconButton(
                                onClick = { viewModel.saveIniFile(editorText) },
                                enabled = !isLoading,
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    "Save",
                                    tint =
                                        if (!isLoading) {
                                            if (isDirty) NeonAmber else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        },
                                )
                            }
                        }
                    },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NeonCyan)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (editingFileName != null) "Saving $editingFileName..." else "Loading...",
                                color = NeonCyan,
                            )
                        }
                    }
                }
                editingFileName != null && iniContent != null -> {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (showSearch) {
                            IniSearchBar(
                                query = query,
                                onQueryChange = {
                                    query = it
                                    currentMatch = 0
                                },
                                matchIndex = safeMatch,
                                matchCount = matches.size,
                                onPrev = {
                                    if (matches.isNotEmpty()) {
                                        currentMatch = if (currentMatch - 1 < 0) matches.lastIndex else currentMatch - 1
                                    }
                                },
                                onNext = {
                                    if (matches.isNotEmpty()) {
                                        currentMatch = (currentMatch + 1) % matches.size
                                    }
                                },
                                onClose = {
                                    showSearch = false
                                    query = ""
                                    currentMatch = 0
                                },
                                focusRequester = focusRequester,
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .imePadding(),
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                Column(
                                    modifier =
                                        Modifier
                                            .width(52.dp)
                                            .fillMaxHeight()
                                            .background(GUTTER_BG)
                                            .verticalScroll(vertical)
                                            .padding(vertical = 8.dp),
                                ) {
                                    repeat(lineCount) { i ->
                                        Text(
                                            "${i + 1}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            lineHeight = LINE_HEIGHT,
                                            textAlign = TextAlign.End,
                                            color = GUTTER_TEXT,
                                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                                        )
                                    }
                                }
                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(EDITOR_BG)
                                            .padding(8.dp),
                                ) {
                                    BasicTextField(
                                        value = editorText,
                                        onValueChange = { editorText = it },
                                        textStyle =
                                            MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                lineHeight = LINE_HEIGHT,
                                                color = BASE_TEXT,
                                            ),
                                        visualTransformation = iniTransform,
                                        keyboardOptions =
                                            KeyboardOptions(
                                                keyboardType = KeyboardType.Ascii,
                                                autoCorrect = false,
                                            ),
                                        cursorBrush = SolidColor(NeonAmber),
                                        modifier = Modifier.fillMaxSize().verticalScroll(vertical),
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Description,
                                        null,
                                        tint = NeonAmber,
                                        modifier = Modifier.size(26.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Config Files",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = NeonAmber,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${GamePaths.MONITORED_FILES.size} monitored INI files · tap to edit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        itemsIndexed(GamePaths.MONITORED_FILES) { index, fileName ->
                            IniFileCard(
                                fileName = fileName,
                                description = iniFileDescription(fileName),
                                accent = iniFileAccent(fileName),
                                shape = iniFileShape(index),
                                onClick = { viewModel.readIniFile(fileName) },
                            )
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = GUTTER_BG.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    "Changes are pushed directly to device and hashes are refreshed — the game cannot detect tampering.",
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }

            errorMessage?.let { msg ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1E8A).copy(alpha = 0.95f)),
                    ) {
                        Text(
                            msg,
                            modifier = Modifier.padding(16.dp),
                            color = NeonRed,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            successMessage?.let { msg ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF004D40).copy(alpha = 0.95f)),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                "Success",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(msg, color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IniSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchIndex: Int,
    matchCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(GUTTER_BG)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, null, tint = NeonAmber)
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search…", color = Color(0xFF8A8AA0)) },
            textStyle = MaterialTheme.typography.bodySmall.copy(color = BASE_TEXT, fontSize = 13.sp),
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = NeonAmber,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NeonAmber,
                    focusedPlaceholderColor = Color(0xFF8A8AA0),
                    unfocusedPlaceholderColor = Color(0xFF8A8AA0),
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrect = false),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        Text(
            if (matchCount == 0) "0/0" else "${matchIndex + 1}/$matchCount",
            color = Color(0xFF8A8AA0),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = onPrev, enabled = matchCount > 0) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                null,
                tint = if (matchCount > 0) NeonAmber else Color(0xFF4A4A6A),
            )
        }
        IconButton(onClick = onNext, enabled = matchCount > 0) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                null,
                tint = if (matchCount > 0) NeonAmber else Color(0xFF4A4A6A),
            )
        }
    }
}

@Composable
private fun IniFileCard(
    fileName: String,
    description: String,
    accent: Color,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.clickable(onClick = onClick),
        accentColor = accent,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .background(accent.copy(alpha = 0.16f), shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Description,
                    null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Edit ›",
                style = MaterialTheme.typography.bodySmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun iniFileDescription(fileName: String): String =
    when (fileName) {
        "Engine.ini" -> "Rendering, CVars & core engine tuning"
        "DeviceProfiles.ini" -> "Per-device scalability overrides"
        "GameUserSettings.ini" -> "Resolution, fullscreen & user prefs"
        "Scalability.ini" -> "Quality tier group definitions"
        "Hardware.ini" -> "Hardware-specific defaults"
        else -> "Configuration file"
    }

private fun iniFileAccent(fileName: String): Color =
    when (fileName) {
        "Engine.ini" -> NeonCyan
        "DeviceProfiles.ini" -> NeonPurple
        "GameUserSettings.ini" -> NeonGreen
        "Scalability.ini" -> NeonAmber
        "Hardware.ini" -> NeonPink
        else -> NeonBlue
    }

private fun iniFileShape(index: Int): androidx.compose.ui.graphics.Shape =
    when (index % 5) {
        0 -> RoundedCornerShape(18.dp)
        1 -> RoundedCornerShape(topStart = 22.dp, bottomEnd = 22.dp, topEnd = 6.dp, bottomStart = 6.dp)
        2 -> RoundedCornerShape(6.dp)
        3 -> RoundedCornerShape(topStart = 6.dp, bottomEnd = 6.dp, topEnd = 22.dp, bottomStart = 22.dp)
        else -> RoundedCornerShape(14.dp)
    }

private fun findMatches(
    text: String,
    query: String,
): List<IntRange> {
    if (query.isBlank()) return emptyList()
    val result = mutableListOf<IntRange>()
    var idx = text.indexOf(query, ignoreCase = true)
    while (idx >= 0) {
        result.add(idx..(idx + query.length - 1))
        idx = text.indexOf(query, idx + query.length, ignoreCase = true)
    }
    return result
}

private fun highlightIni(
    text: String,
    matches: List<IntRange>,
    currentMatch: Int,
): AnnotatedString =
    buildAnnotatedString {
        val srcLines = text.lines()
        val effectiveLines = if (text.endsWith("\n")) srcLines + "" else srcLines
        var offset = 0
        effectiveLines.forEachIndexed { i, line ->
            val lineStart = offset
            val commentIdx = line.indexOf(';')
            val codeEnd = if (commentIdx >= 0) commentIdx else line.length
            if (line.isNotBlank()) {
                if (line.startsWith("[")) {
                    addStyle(INI_SECTION, lineStart, lineStart + line.length)
                } else {
                    val eq = line.indexOf('=')
                    if (eq > 0 && eq < codeEnd) {
                        addStyle(INI_KEY, lineStart, lineStart + eq)
                        addStyle(INI_EQ, lineStart + eq, lineStart + eq + 1)
                        if (lineStart + eq + 1 < lineStart + codeEnd) {
                            addStyle(INI_VALUE, lineStart + eq + 1, lineStart + codeEnd)
                        }
                    }
                }
            }
            if (commentIdx >= 0) {
                addStyle(INI_COMMENT, lineStart + commentIdx, lineStart + line.length)
            }
            matches.forEachIndexed { mi, m ->
                if (m.first >= lineStart && m.last < lineStart + line.length) {
                    val style = if (mi == currentMatch) INI_SEARCH_CURRENT else INI_SEARCH
                    addStyle(style, m.first, m.last + 1)
                }
            }
            append(line)
            offset += line.length
            if (i < effectiveLines.lastIndex) {
                append("\n")
                offset += 1
            }
        }
    }
