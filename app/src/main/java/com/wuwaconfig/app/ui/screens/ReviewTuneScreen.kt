package com.wuwaconfig.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wuwaconfig.app.model.GeneratedIni
import com.wuwaconfig.app.model.GeneratorOptions
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassDialog
import com.wuwaconfig.app.ui.components.GlassTopBar
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*
import com.wuwaconfig.app.util.DiffLine
import com.wuwaconfig.app.util.Hashing
import com.wuwaconfig.app.util.LineDiff

private val ReviewMonitoredFiles =
    listOf(
        "Engine.ini",
        "DeviceProfiles.ini",
        "GameUserSettings.ini",
        "Scalability.ini",
        "Hardware.ini",
    )

private val FileAccents = listOf(NeonCyan, NeonPurple, NeonGreen, NeonAmber, NeonPink)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewTuneScreen(
    viewModel: MainViewModel,
    generatorOptions: GeneratorOptions,
    onBack: () -> Unit,
    onDeploy: (GeneratedIni, GeneratorOptions) -> Unit,
) {
    val payload by viewModel.reviewTunePayload.collectAsStateWithLifecycle()
    val newFiles by viewModel.reviewTuneNewFiles.collectAsStateWithLifecycle()
    val currentDevice by viewModel.reviewTuneCurrentDevice.collectAsStateWithLifecycle()
    val currentDeviceLoading by viewModel.reviewTuneCurrentDeviceLoading.collectAsStateWithLifecycle()
    val currentDeviceError by viewModel.reviewTuneCurrentDeviceError.collectAsStateWithLifecycle()
    val isApplying by viewModel.isApplying.collectAsStateWithLifecycle()

    val available =
        ReviewMonitoredFiles.filter { f ->
            (newFiles[f]?.isNotBlank() == true) || (currentDevice[f]?.isNotBlank() == true)
        }

    var selectedTab by rememberSaveable {
        mutableStateOf(0)
    }
    LaunchedEffect(available) {
        if (available.isNotEmpty()) {
            selectedTab = selectedTab.coerceIn(0, available.lastIndex)
        } else {
            selectedTab = 0
        }
    }

    var readOnly by rememberSaveable { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableStateOf("editor") }
    var showSummary by rememberSaveable { mutableStateOf(false) }
    var showDiscardConfirm by rememberSaveable { mutableStateOf(false) }

    val currentFile = available.getOrNull(selectedTab) ?: ReviewMonitoredFiles.first()
    val newText = newFiles[currentFile].orEmpty()
    val deviceText = currentDevice[currentFile].orEmpty()
    val originalGenerated = payloadText(payload, currentFile)

    val dirty = newText != originalGenerated
    val deviceTextPresent = deviceText.isNotBlank()
    val deviceMd5 = remember(deviceText) { if (deviceTextPresent) Hashing.md5Of(deviceText) else "n/a" }
    val newMd5 = remember(newText) { Hashing.md5Of(newText) }
    val oldMd5 = remember(originalGenerated) { Hashing.md5Of(originalGenerated) }

    val diff =
        remember(deviceText, newText) {
            if (deviceTextPresent) LineDiff.compute(deviceText, newText) else null
        }

    LaunchedEffect(currentFile) {
        val key = currentFile
        if (currentDevice[key].isNullOrBlank() && key.isNotBlank()) {
            viewModel.reloadDeviceFileForReview(key)
        }
    }

    val ctx = LocalContext.current

    GradientBackground {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Review & Tune", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (dirty) NeonAmber.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    if (dirty) {
                                        "unsaved"
                                    } else if (readOnly) {
                                        "locked"
                                    } else {
                                        currentFile
                                    },
                                    fontSize = 10.sp,
                                    color = if (dirty) NeonAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (dirty) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    },
                    accentColor = NeonCyan,
                    navigationIcon = {
                        IconButton(onClick = {
                            if (dirty) showDiscardConfirm = true else onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NeonPurple)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText(currentFile, newText))
                            android.widget.Toast.makeText(ctx, "Copied $currentFile", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = NeonCyan)
                        }
                        IconButton(onClick = { viewModel.reloadDeviceFileForReview(currentFile) }) {
                            Icon(Icons.Default.Refresh, "Reload device file", tint = NeonGreen)
                        }
                        IconButton(onClick = { readOnly = !readOnly }) {
                            Icon(
                                if (readOnly) Icons.Default.Lock else Icons.Default.Edit,
                                if (readOnly) "Enable editing" else "Read-only mode",
                                tint = if (readOnly) MaterialTheme.colorScheme.onSurfaceVariant else NeonGreen,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                ReviewBottomBar(
                    availableCount = available.size,
                    dirty = dirty,
                    isApplying = isApplying,
                    hasDiff = deviceTextPresent && diff != null,
                    viewMode = viewMode,
                    onToggleView = { viewMode = if (viewMode == "editor") "diff" else "editor" },
                    onResetGenerated = {
                        available.forEach { f ->
                            viewModel.updateReviewTuneFile(f, payloadText(payload, f))
                        }
                    },
                    onDeploy = { showSummary = true },
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                FileTabRow(
                    available = available,
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    dirtyMap = available.associateWith { f -> newFiles[f].orEmpty() != payloadText(payload, f) },
                )

                FileMetaBar(
                    fileName = currentFile,
                    deviceTextPresent = deviceTextPresent,
                    deviceLoading = currentDeviceLoading == currentFile,
                    deviceError = currentDeviceError,
                    deviceMd5 = deviceMd5,
                    oldMd5 = oldMd5,
                    newMd5 = newMd5,
                    summary = diff?.summary,
                )

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    if (available.isEmpty()) {
                        EmptyState(onBack = onBack)
                    } else if (viewMode == "diff" && diff != null) {
                        DiffPane(
                            diff = diff,
                            accent = FileAccents[ReviewMonitoredFiles.indexOf(currentFile).coerceIn(0, FileAccents.lastIndex)],
                        )
                    } else {
                        EditorPane(
                            text = newText,
                            readOnly = readOnly,
                            accent = FileAccents[ReviewMonitoredFiles.indexOf(currentFile).coerceIn(0, FileAccents.lastIndex)],
                            onChange = { viewModel.updateReviewTuneFile(currentFile, it) },
                        )
                    }
                }
            }
        }
    }

    if (showSummary) {
        DeploySummaryDialog(
            available = available,
            newFiles = newFiles,
            currentDevice = currentDevice,
            onCancel = { showSummary = false },
            onConfirm = {
                showSummary = false
                val engine = newFiles["Engine.ini"].orEmpty()
                val device = newFiles["DeviceProfiles.ini"].orEmpty()
                val gus = newFiles["GameUserSettings.ini"].orEmpty()
                val scal = newFiles["Scalability.ini"].orEmpty()
                val hw = newFiles["Hardware.ini"].orEmpty()
                val overrides =
                    viewModel.configGenerator.parseCvarEntries(engine)
                        .filter { it.isOverridden }
                        .associate { it.key to it.value }
                val opts = generatorOptions.copy(cvarOverrides = overrides)
                onDeploy(
                    GeneratedIni(
                        engine = engine,
                        deviceProfiles = device,
                        gameUserSettings = gus,
                        scalability = scal,
                        hardware = hw,
                    ),
                    opts,
                )
            },
        )
    }

    if (showDiscardConfirm) {
        GlassDialog(
            onDismissRequest = { showDiscardConfirm = false },
            accentColor = NeonPink,
            title = { Text("Discard changes?", fontWeight = FontWeight.Bold) },
            text = { Text("You have unsaved edits to this generated config. Leaving will lose them.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onBack()
                }) { Text("Discard", color = NeonPink) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") }
            },
        )
    }
}

private fun payloadText(
    p: MainViewModel.ReviewTunePayload,
    fileName: String,
): String =
    when (fileName) {
        "Engine.ini" -> p.engine
        "DeviceProfiles.ini" -> p.deviceProfiles
        "GameUserSettings.ini" -> p.gameUserSettings
        "Scalability.ini" -> p.scalability
        "Hardware.ini" -> p.hardware
        else -> ""
    }

@Composable
private fun FileTabRow(
    available: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    dirtyMap: Map<String, Boolean>,
) {
    val scroll = rememberScrollState()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        available.forEachIndexed { i, label ->
            val isSel = i == selected
            val isDirty = dirtyMap[label] == true
            val accent = FileAccents[ReviewMonitoredFiles.indexOf(label).coerceIn(0, FileAccents.lastIndex)]
            FilterChip(
                selected = isSel,
                onClick = { onSelect(i) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDirty) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(NeonAmber),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.35f),
                        selectedLabelColor = accent,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                border =
                    FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSel,
                        borderColor = accent.copy(alpha = 0.5f),
                        selectedBorderColor = accent,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 2.dp,
                    ),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun FileMetaBar(
    fileName: String,
    deviceTextPresent: Boolean,
    deviceLoading: Boolean,
    deviceError: String?,
    deviceMd5: String,
    oldMd5: String,
    newMd5: String,
    summary: com.wuwaconfig.app.util.DiffSummary?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (deviceLoading) {
                    "Fetching $fileName…"
                } else if (!deviceTextPresent) {
                    "No on-device file"
                } else if (fileName.isEmpty()) {
                    "$"
                } else {
                    "Device: ${deviceMd5.take(12)}"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "New: ${newMd5.take(12)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        if (deviceError != null) {
            Text(
                "Device read failed: $deviceError",
                fontSize = 10.sp,
                color = NeonPink,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (summary != null && deviceTextPresent) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DiffBadge(label = "+${summary.added}", color = NeonGreen)
                DiffBadge(label = "-${summary.removed}", color = NeonPink)
                DiffBadge(label = "=${summary.unchanged}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DiffBadge(
    label: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EditorPane(
    text: String,
    readOnly: Boolean,
    accent: Color,
    onChange: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    val lineCount = text.count { it == '\n' } + 1
    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A2E))
                .border(width = 1.dp, color = accent.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier =
                Modifier
                    .width(48.dp)
                    .background(Color(0xFF11111F))
                    .padding(top = 8.dp, end = 6.dp, bottom = 8.dp)
                    .verticalScroll(scroll),
            horizontalAlignment = Alignment.End,
        ) {
            for (i in 1..lineCount) {
                Text(
                    "$i",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        BasicTextField(
            value = text,
            onValueChange = onChange,
            readOnly = readOnly,
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (readOnly) Color(0xFFB0B0B0) else Color(0xFFE0E0E0),
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    .verticalScroll(scroll),
        )
    }
}

@Composable
private fun DiffPane(
    diff: com.wuwaconfig.app.util.DiffResult,
    accent: Color,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A2E))
                .border(width = 1.dp, color = accent.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
                .padding(8.dp),
    ) {
        items(diff.lines) { line ->
            DiffRow(line)
        }
    }
}

@Composable
private fun DiffRow(line: DiffLine) {
    val (bg, bar, prefix) =
        when (line.kind) {
            DiffLine.Kind.ADDED -> Triple(Color(0x3322C55E), NeonGreen, "+")
            DiffLine.Kind.REMOVED -> Triple(Color(0x33EF4444), NeonPink, "-")
            DiffLine.Kind.CONTEXT -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant, " ")
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bg)
                .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .background(bar),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            line.oldLineNumber?.toString() ?: " ",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(28.dp),
        )
        Text(
            line.newLineNumber?.toString() ?: " ",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(28.dp),
        )
        Text(
            "$prefix ${line.text}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (line.kind == DiffLine.Kind.CONTEXT) Color(0xFFB0B0B0) else bar,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun EmptyState(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Tune, null, tint = NeonCyan, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("No generated configs yet.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Press Generate on the previous screen, then come back here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        FilledTonalButton(onClick = onBack) { Text("Back to ConfigGen") }
    }
}

@Composable
private fun ReviewBottomBar(
    availableCount: Int,
    dirty: Boolean,
    isApplying: Boolean,
    hasDiff: Boolean,
    viewMode: String,
    onToggleView: () -> Unit,
    onResetGenerated: () -> Unit,
    onDeploy: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = onToggleView,
                label = { Text(if (viewMode == "diff") "View Editor" else "View Diff", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(
                        if (viewMode == "diff") Icons.Default.Edit else Icons.Default.Visibility,
                        null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                enabled = hasDiff || viewMode == "diff",
            )
            AssistChip(
                onClick = onResetGenerated,
                label = { Text("Reset", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Restore, null, modifier = Modifier.size(14.dp)) },
                enabled = dirty,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "$availableCount files",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onDeploy,
                enabled = availableCount > 0 && !isApplying,
            ) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Deploy", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DeploySummaryDialog(
    available: List<String>,
    newFiles: Map<String, String>,
    currentDevice: Map<String, String>,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    GlassDialog(
        onDismissRequest = onCancel,
        accentColor = NeonGreen,
        title = { Text("Deploy changes?", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                available.forEach { file ->
                    val new = newFiles[file].orEmpty()
                    val dev = currentDevice[file].orEmpty()
                    val summary = if (dev.isBlank()) null else LineDiff.compute(dev, new).summary
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ListAlt,
                            null,
                            tint = FileAccents[ReviewMonitoredFiles.indexOf(file).coerceIn(0, FileAccents.lastIndex)],
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(file, fontWeight = FontWeight.SemiBold)
                    }
                    if (summary == null) {
                        Text(
                            "(new install — no on-device file to compare)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                    } else {
                        Text(
                            "+${summary.added}  -${summary.removed}  =${summary.unchanged}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Deploy", fontWeight = FontWeight.Bold, color = NeonGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}
