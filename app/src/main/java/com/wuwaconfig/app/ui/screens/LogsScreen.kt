package com.wuwaconfig.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.model.LogLevel
import com.wuwaconfig.app.model.LogRepository
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    viewModel: MainViewModel,
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

    val logsFeedback by viewModel.logsFeedback.collectAsState()
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

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Log", fontWeight = FontWeight.Bold) },
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
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = NeonCyan,
                        ),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            "Search CVars or messages...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan.copy(alpha = 0.6f)) },
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
                    "${filtered.size} entries",
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
                    val listState = rememberLazyListState()
                    val isAtBottom by remember {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                            last != null && last.index >= listState.layoutInfo.totalItemsCount - 3
                        }
                    }
                    LaunchedEffect(filtered.size) {
                        if (filterLevel == null && searchQuery.isBlank() && isAtBottom) {
                            listState.animateScrollToItem(filtered.size - 1)
                        }
                    }
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
                            Text(
                                "[${log.timestamp}] ${log.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c,
                                modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
