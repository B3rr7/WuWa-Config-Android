package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
    val logs = LogRepository.entries
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
    val clipboard = LocalClipboardManager.current

    val isAtBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            last != null && last.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(filtered.size) {
        if (filterLevel == null && searchQuery.isBlank() && isAtBottom) {
            listState.animateScrollToItem((filtered.size - 1).coerceAtLeast(0))
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
                        IconButton(onClick = { viewModel.clearLogs() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = NeonRed)
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (filtered.isNotEmpty() && !isAtBottom) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch { listState.animateScrollToItem((filtered.size - 1).coerceAtLeast(0)) }
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
                                        .fillMaxWidth()
                                        .clickable {
                                            clipboard.setText(
                                                AnnotatedString("[${log.timestamp}] ${log.message}"),
                                            )
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "Copied to clipboard",
                                                    duration = SnackbarDuration.Short,
                                                )
                                            }
                                        }.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.Top,
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
                                    Text(
                                        buildHighlightedMessage(log.message, debouncedQuery, c),
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

@Composable
private fun buildHighlightedMessage(
    message: String,
    query: String,
    baseColor: Color,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(message, SpanStyle(color = baseColor))
    val lower = message.lowercase()
    val q = query.lowercase()
    val builder = AnnotatedString.Builder()
    var start = 0
    while (start < message.length) {
        val idx = lower.indexOf(q, start)
        if (idx < 0) {
            builder.append(AnnotatedString(message.substring(start), SpanStyle(color = baseColor)))
            break
        }
        if (idx > start) {
            builder.append(AnnotatedString(message.substring(start, idx), SpanStyle(color = baseColor)))
        }
        builder.append(
            AnnotatedString(
                message.substring(idx, (idx + q.length).coerceAtMost(message.length)),
                SpanStyle(color = baseColor, background = NeonCyan.copy(alpha = 0.25f)),
            ),
        )
        start = idx + q.length
    }
    return builder.toAnnotatedString()
}
