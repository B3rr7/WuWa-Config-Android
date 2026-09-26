package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.wuwaconfig.app.util.copySensitive
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.ui.DeployHistoryViewModel
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: DeployHistoryViewModel,
    onBack: () -> Unit,
) {
    val logs by LogRepository.entries.collectAsStateWithLifecycle()
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty()) {
            debouncedQuery = ""
        } else {
            delay(300)
            debouncedQuery = searchQuery
        }
    }

    val logsFeedback by viewModel.logsFeedback.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear all logs?") },
            text = {
                Text(
                    "This deletes all ${logs.size} entries in the on-screen log and truncates app.log on disk. " +
                        "The log is the diagnostic trail for deploy failures and cannot be recovered.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLogs()
                        showClearAllDialog = false
                    },
                ) { Text("Clear All", color = NeonRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
            },
        )
    }

    LaunchedEffect(logsFeedback) {
        logsFeedback?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearLogsFeedback()
        }
    }

    val filtered by remember {
        derivedStateOf {
            var list = logs.toList()
            if (filterLevel != null) list = list.filter { it.level == filterLevel }
            if (debouncedQuery.isNotBlank()) {
                val q = debouncedQuery.lowercase()
                list = list.filter { it.message.lowercase().contains(q) }
            }
            list
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // The platform ClipboardManager is used directly, NOT Compose's
    // LocalClipboardManager, because only the former lets us set
    // ClipDescription.EXTRA_IS_SENSITIVE. Compose's setText(AnnotatedString)
    // has no sensitivity control, so a copied log line would be eligible for the
    // system clipboard history and the keyboard paste preview — and Google
    // Keyboard syncs that history to the signed-in Google account by default,
    // which is an off-device replication path with no further consent.
    val context = LocalContext.current
    val clipboard =
        remember(context) {
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        }

    val isNearTop by remember {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            first != null && first.index < 3
        }
    }

    // Auto-follow the tail. Keying a LaunchedEffect on `filtered` restarted
    // animateScrollToItem(0) on every log emission (many per second during a
    // deploy) and cancelled it mid-animation, which read as visible stutter.
    // snapshotFlow + an instant scrollToItem pins to the newest entry with no
    // animation to cancel.
    LaunchedEffect(listState) {
        snapshotFlow { isNearTop to filtered.size }
            .collect { (near, size) ->
                if (near && filterLevel == null && searchQuery.isBlank() && size > 0) {
                    listState.scrollToItem(0)
                }
            }
    }

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = {
                        Column {
                            Text("Logs", fontWeight = FontWeight.Bold)
                            Text(
                                if (filtered.size != logs.size) {
                                    "${filtered.size} of ${logs.size}"
                                } else {
                                    "${logs.size} entries"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    accentColor = NeonCyan,
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonCyan) }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.saveLogs() }) {
                            Icon(Icons.Default.Save, contentDescription = "Save", tint = NeonGreen)
                        }
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = NeonRed)
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (filtered.isNotEmpty() && !isNearTop) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = NeonCyan.copy(alpha = 0.9f),
                        contentColor = Color.Black,
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to latest")
                    }
                }
            },
            containerColor = Color.Transparent,
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search messages...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan.copy(alpha = 0.6f)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = NeonCyan.copy(alpha = 0.6f))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan.copy(alpha = 0.6f),
                            unfocusedBorderColor = NeonCyan.copy(alpha = 0.2f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = filterLevel == null,
                        onClick = { filterLevel = null },
                        label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan,
                            ),
                    )
                    LogLevel.entries.forEach { level ->
                        val chipColor =
                            when (level) {
                                LogLevel.SUCCESS -> NeonGreen
                                LogLevel.ERROR -> NeonRed
                                LogLevel.WARNING -> NeonAmber
                                LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        FilterChip(
                            selected = filterLevel == level,
                            onClick = { filterLevel = if (filterLevel == level) null else level },
                            label = { Text(level.name, style = MaterialTheme.typography.labelSmall) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipColor.copy(alpha = 0.2f),
                                    selectedLabelColor = chipColor,
                                ),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    "Tap an entry to copy · ${filtered.size} shown",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (searchQuery.isNotBlank() || filterLevel != null) "No matching entries" else "No logs yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        itemsIndexed(filtered.reversed(), key = { _, log -> log.id }) { _, log ->
                            val c =
                                when (log.level) {
                                    LogLevel.SUCCESS -> NeonGreen
                                    LogLevel.ERROR -> NeonRed
                                    LogLevel.WARNING -> NeonAmber
                                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            Row(
                                modifier =
                                    Modifier
                                        // IntrinsicSize.Min gives the Row a real
                                        // height so the severity strip's
                                        // fillMaxHeight() resolves against it —
                                        // with maxHeight = Infinity it fell back
                                        // to minHeight (0) and drew nothing.
                                        .height(IntrinsicSize.Min)
                                        .fillMaxWidth()
                                        .clickable {
                                            clipboard.copySensitive(
                                                "WuWaConfig log",
                                                "[${log.timestamp}] ${log.message}]",
                                            )
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "Copied to clipboard",
                                                    duration = SnackbarDuration.Short,
                                                )
                                            }
                                        }.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(c.copy(alpha = 0.8f)),
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        "[${log.timestamp}]",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = c.copy(alpha = 0.65f),
                                    )
                                    val highlighted =
                                        remember(log.message, debouncedQuery, c) {
                                            buildHighlightedMessage(log.message, debouncedQuery, c)
                                        }
                                    Text(
                                        highlighted,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = c,
                                        modifier = Modifier.padding(top = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildHighlightedMessage(
    message: String,
    query: String,
    baseColor: Color,
): AnnotatedString {
    val base = SpanStyle(color = baseColor)
    if (query.isBlank()) return AnnotatedString(message, base)
    val lower = message.lowercase()
    val q = query.lowercase()
    // pushStyle/append/pop appends in place. The previous version allocated a
    // full AnnotatedString (each with its own ParagraphStyle/SpanStyle copies)
    // for every literal run AND every match, inside a LazyColumn of up to 1000
    // rows. Note: AnnotatedString.Builder exposes `append(CharSequence, start, end)`,
    // not `addText` — the range overload appends without allocating a substring.
    return buildAnnotatedString {
        pushStyle(base)
        var start = 0
        while (start < message.length) {
            val idx = lower.indexOf(q, start)
            if (idx < 0) {
                append(message, start, message.length)
                break
            }
            if (idx > start) {
                append(message, start, idx)
            }
            pushStyle(SpanStyle(color = baseColor, background = NeonCyan.copy(alpha = 0.25f)))
            append(message, idx, (idx + q.length).coerceAtMost(message.length))
            pop()
            start = idx + q.length
        }
        pop()
    }
}
