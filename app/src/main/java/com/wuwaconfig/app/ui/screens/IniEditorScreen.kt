package com.wuwaconfig.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import com.wuwaconfig.app.ui.IniEditorViewModel
import com.wuwaconfig.app.ui.components.BouncingOrb
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassDialog
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
    viewModel: IniEditorViewModel,
    onBack: () -> Unit,
) {
    val editingFileName by viewModel.editingFileName.collectAsStateWithLifecycle()
    val iniContent by viewModel.iniEditorContent.collectAsStateWithLifecycle()
    val isLoading by viewModel.iniEditorLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.iniEditorError.collectAsStateWithLifecycle()
    val successMessage by viewModel.iniEditorSuccess.collectAsStateWithLifecycle()

    var editorText by rememberSaveable { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var currentMatch by remember { mutableIntStateOf(0) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val vertical = rememberScrollState()
    // The gutter has its OWN ScrollState; it is deliberately mirrored from
    // `vertical` below. Attaching one ScrollState to two verticalScroll
    // containers only worked by accident (last-registered nested-scroll node
    // won) and desynced on fling.
    val gutterScroll = rememberLazyListState()

    // Precomputed newline offsets: line numbers for search matches and the
    // gutter length are then O(log n) / O(1) instead of a full-string scan.
    val newlineIdx = remember(editorText) { newlineOffsets(editorText) }
    val lineCount by remember { derivedStateOf { editorText.count { it == '\n' } + 1 } }
    val matches = remember(query, editorText) { findMatches(editorText, query) }
    val safeMatch = if (matches.isEmpty()) 0 else currentMatch.coerceIn(0, matches.lastIndex)
    val isDirty = editorText != (iniContent ?: "")
    val goBackToGrid: () -> Unit = {
        viewModel.returnToFileList()
        showSearch = false
        query = ""
        currentMatch = 0
    }

    // Keyed on the match SET only. `safeMatch` used to be a key, so every
    // Next/Prev press (and every keystroke, because onQueryChange resets
    // currentMatch) allocated a new VisualTransformation and re-filtered the
    // whole document for nothing. The current index is read as snapshot state
    // inside the lambda, at filter time, so the lambda's identity is stable.
    val iniTransform =
        remember(matches) {
            VisualTransformation { annotated ->
                val current =
                    if (matches.isEmpty()) {
                        0
                    } else {
                        currentMatch.coerceIn(0, matches.lastIndex)
                    }
                val highlighted = highlightIni(annotated.text, matches, current)
                TransformedText(
                    highlighted,
                    object : OffsetMapping {
                        override fun originalToTransformed(offset: Int): Int = offset

                        override fun transformedToOriginal(offset: Int): Int = offset.coerceAtMost(annotated.text.length)
                    },
                )
            }
        }

    LaunchedEffect(Unit) {
        viewModel.syncConfigHashes()
    }
    var lastLoadedFile by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(editingFileName, iniContent) {
        val content = iniContent
        if (content != null && editingFileName != lastLoadedFile) {
            editorText = content
            lastLoadedFile = editingFileName
        }
    }
    LaunchedEffect(editingFileName) {
        if (editingFileName == null) {
            lastLoadedFile = null
            editorText = ""
        }
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
    LaunchedEffect(safeMatch, query, newlineIdx, vertical.maxValue) {
        if (matches.isNotEmpty() && lineCount > 0) {
            // Binary search over the precomputed newline index — no
            // `substring(0, offset)` copy of the whole document per match change.
            val line = lineIndexOfOffset(newlineIdx, matches[safeMatch].first)
            val target = ((line.toFloat() / lineCount) * vertical.maxValue).toInt()
            vertical.scrollTo(target.coerceAtMost(vertical.maxValue))
        }
    }

    // Keep the gutter glued to the editor while the user types / flings.
    // The gutter is a LazyColumn (an eager Column in a verticalScroll composed
    // one Text node per line on every keystroke), so it needs a LazyListState and
    // the editor's pixel offset must be converted to a line index via the
    // precomputed newline table rather than mirrored as raw pixels.
    LaunchedEffect(vertical, newlineIdx) {
        snapshotFlow { vertical.value }.collect { offset ->
            val firstLine = if (offset <= 0) 0 else lineIndexOfOffset(newlineIdx, offset)
            if (firstLine != gutterScroll.firstVisibleItemIndex) {
                gutterScroll.scrollToItem(firstLine.coerceIn(0, (lineCount - 1).coerceAtLeast(0)))
            }
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
                                fontFamily = RajdhaniBold,
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
                                if (isDirty) showDiscardDialog = true else goBackToGrid()
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
            containerColor = Color.Transparent,
        ) { padding ->
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        IniLoadingAnimation(
                            if (editingFileName != null) "Saving $editingFileName..." else "Loading...",
                        )
                    }
                }
                editingFileName != null && iniContent != null -> {
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        IniEditorStatusBar(
                            lineCount = lineCount,
                            isDirty = isDirty,
                        )
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
                                // LazyColumn, not a Column in a verticalScroll: an
                                // eager gutter composes/measures a Text node per
                                // line, so a 3000-line file meant 3000 text nodes
                                // re-laid out on every keystroke.
                                LazyColumn(
                                    state = gutterScroll,
                                    modifier =
                                        Modifier
                                            .width(52.dp)
                                            .fillMaxHeight()
                                            .background(GUTTER_BG)
                                            .padding(vertical = 8.dp),
                                ) {
                                    items(count = lineCount) { i ->
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
                                                autoCorrectEnabled = false,
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
                    IniFileGrid(
                        padding = padding,
                        onFileClick = { viewModel.readIniFile(it) },
                    )
                }
            }

            errorMessage?.let { msg ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Card(
                        modifier =
                            Modifier.fillMaxWidth().padding(12.dp).clickable {
                                viewModel.clearIniEditorError()
                            },
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
                    // Top-aligned + IME-padded so the card is not behind the
                    // keyboard, and pointer-blocking so taps never fall through
                    // to the BasicTextField underneath.
                    modifier = Modifier.fillMaxSize().padding(padding).imePadding().consumeAllPointerInput(),
                    contentAlignment = Alignment.TopCenter,
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

            if (showDiscardDialog) {
                GlassDialog(
                    onDismissRequest = { showDiscardDialog = false },
                    title = { Text("Discard changes?", fontWeight = FontWeight.Bold) },
                    text = { Text("You have unsaved changes to $editingFileName. Discard them?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDiscardDialog = false
                            goBackToGrid()
                        }) {
                            Text("Discard", color = NeonRed, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
                    },
                )
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
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
private fun IniFileGrid(
    padding: PaddingValues,
    onFileClick: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(NeonCyan, NeonBlue))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Config Files",
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = RajdhaniBold,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${GamePaths.MONITORED_FILES.size} monitored INI files · tap to edit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        itemsIndexed(GamePaths.MONITORED_FILES) { _, fileName ->
            IniFileTile(
                fileName = fileName,
                description = iniFileDescription(fileName),
                accent = iniFileAccent(fileName),
                onClick = { onFileClick(fileName) },
            )
        }
        item(span = { GridItemSpan(2) }) {
            GlassCard(accentColor = NeonCyan) {
                Text(
                    "Changes are pushed directly to device and hashes are refreshed — the game cannot detect tampering.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun IniFileTile(
    fileName: String,
    description: String,
    accent: Color,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        accentColor = accent,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.5f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                fileName,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = RajdhaniBold,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Edit", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = accent, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun IniEditorStatusBar(
    lineCount: Int,
    isDirty: Boolean,
) {
    GlassCard(accentColor = if (isDirty) NeonAmber else NeonCyan) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$lineCount lines",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDirty) NeonAmber else NeonCyan,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (isDirty) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(NeonRed),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Unsaved", style = MaterialTheme.typography.labelSmall, color = NeonRed, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("Unchanged", style = MaterialTheme.typography.labelSmall, color = NeonGreen, fontWeight = FontWeight.SemiBold)
            }
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

/** Char offsets of every `'\n'` in [text], ascending. Computed once per document. */
private fun newlineOffsets(text: String): IntArray {
    var positions = IntArray(64)
    var n = 0
    var i = text.indexOf('\n')
    while (i >= 0) {
        if (n == positions.size) positions = positions.copyOf(positions.size * 2)
        positions[n++] = i
        i = text.indexOf('\n', i + 1)
    }
    return if (n == positions.size) positions else positions.copyOf(n)
}

/** Number of newlines strictly before [offset] — i.e. the 0-based line index. */
private fun lineIndexOfOffset(
    offsets: IntArray,
    offset: Int,
): Int {
    var lo = 0
    var hi = offsets.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (offsets[mid] < offset) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * Consumes every pointer event on this node so a full-screen overlay never lets
 * hit-testing fall through to the editor beneath it. A plain Box has no pointer
 * input at all, so the user kept typing while a "Saved" card sat on top.
 */
private fun Modifier.consumeAllPointerInput(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }

/**
 * INI syntax highlighting + search-match highlighting, used as a
 * [VisualTransformation]. Called by BasicTextField on EVERY text-layout pass, on
 * the main thread, on every keystroke — so it must not allocate `text.lines()`
 * and must not rescan the whole match list per line.
 *
 * [matches] come out of [findMatches] in ascending start-offset order (verified
 * there); a single monotonically advancing cursor consumes them per line.
 */
private fun highlightIni(
    text: String,
    matches: List<IntRange>,
    currentMatch: Int,
): AnnotatedString =
    buildAnnotatedString {
        val n = text.length
        var lineStart = 0
        var mi = 0
        while (lineStart <= n) {
            var lineEnd = text.indexOf('\n', lineStart)
            val hasNewline = lineEnd >= 0
            if (!hasNewline) lineEnd = n
            val lineLen = lineEnd - lineStart

            val commentIdx = text.indexOf(';', lineStart).let { if (it >= lineStart && it < lineEnd) it else -1 }
            val codeEnd = if (commentIdx >= 0) commentIdx - lineStart else lineLen

            var isBlank = true
            for (i in lineStart until lineEnd) {
                if (!text[i].isWhitespace()) {
                    isBlank = false
                    break
                }
            }

            if (!isBlank) {
                if (text.startsWith("[", lineStart)) {
                    addStyle(INI_SECTION, lineStart, lineEnd)
                } else {
                    val eq = text.indexOf('=', lineStart)
                    if (eq > lineStart && eq < lineStart + codeEnd) {
                        addStyle(INI_KEY, lineStart, eq)
                        addStyle(INI_EQ, eq, eq + 1)
                        if (eq + 1 < lineStart + codeEnd) {
                            addStyle(INI_VALUE, eq + 1, lineStart + codeEnd)
                        }
                    }
                }
            }
            if (commentIdx >= 0) {
                addStyle(INI_COMMENT, commentIdx, lineEnd)
            }

            // Advance past any match that ended on a previous line, then paint
            // only the matches that fall inside this one.
            while (mi < matches.size && matches[mi].first < lineStart) mi++
            var k = mi
            while (k < matches.size && matches[k].first < lineEnd) {
                val m = matches[k]
                val style = if (k == currentMatch) INI_SEARCH_CURRENT else INI_SEARCH
                val end = (m.last + 1).coerceAtMost(lineEnd)
                if (end > m.first) addStyle(style, m.first, end)
                k++
            }
            if (k > mi) mi = k

            append(text, lineStart, lineEnd)
            if (!hasNewline) break
            append("\n")
            lineStart = lineEnd + 1
        }
    }

@Composable
private fun IniLoadingAnimation(text: String) {
    GlassCard(accentColor = NeonCyan) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val orbs = listOf(NeonCyan, NeonPurple, NeonGreen)
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

