package com.wuwaconfig.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.wuwaconfig.app.model.DeployRecord
import com.wuwaconfig.app.ui.MainViewModel
import com.wuwaconfig.app.ui.components.GlassButton
import com.wuwaconfig.app.ui.components.GlassCard
import com.wuwaconfig.app.ui.components.GlassCardHeader
import com.wuwaconfig.app.ui.components.GradientBackground
import com.wuwaconfig.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val deployRecords by viewModel.deployRecords.collectAsState()
    val backendStatus by viewModel.backendStatus.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()

    GradientBackground {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Deploy History", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonAmber) }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = NeonAmber,
                        ),
                )
            },
            containerColor = Color.Transparent,
        ) { padding ->
            if (deployRecords.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        "No deploy records yet. Generate and deploy a config first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    itemsIndexed(deployRecords) { _, record ->
                        DeployHistoryCard(
                            record = record,
                            isConnected = backendStatus.connected && !isApplying,
                            onCompare = { viewModel.compareDeployOutcome(record.id) },
                            onRetune = { viewModel.retuneAndDeploy(record.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeployHistoryCard(
    record: DeployRecord,
    isConnected: Boolean,
    onCompare: () -> Unit,
    onRetune: () -> Unit = {},
) {
    val dateStr =
        remember(record.timestamp) {
            SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(record.timestamp))
        }
    val comparison = if (record.hasOutcome) record.comparison() else null

    GlassCard(accentColor = if (record.hasOutcome) NeonGreen else NeonBlue) {
        GlassCardHeader(
            title = "${record.presetName.uppercase()} — $dateStr",
            accentColor = if (record.hasOutcome) NeonGreen else NeonBlue,
        )
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Method: ${record.generationMethod}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${record.filesDeployed.size} file(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "CVars: ${record.totalCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Accepted: ${record.acceptedCount}/${record.totalCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (record.redundantCount > 0 || record.unknownCount > 0 || record.monitoredCount > 0) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (record.redundantCount > 0) {
                    Surface(shape = RoundedCornerShape(4.dp), color = NeonGreen.copy(alpha = 0.15f)) {
                        Text(
                            "${record.redundantCount} redundant",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonGreen,
                        )
                    }
                }
                if (record.unknownCount > 0) {
                    Surface(shape = RoundedCornerShape(4.dp), color = NeonAmber.copy(alpha = 0.15f)) {
                        Text(
                            "${record.unknownCount} unknown",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonAmber,
                        )
                    }
                }
                if (record.monitoredCount > 0) {
                    Surface(shape = RoundedCornerShape(4.dp), color = NeonBlue.copy(alpha = 0.15f)) {
                        Text(
                            "${record.monitoredCount} monitored",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonBlue,
                        )
                    }
                }
            }
        }

        if (comparison != null) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Spacer(Modifier.height(8.dp))
            Text("vs Baseline", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NeonGreen)
            Spacer(Modifier.height(4.dp))
            ComparisonRow("FPS", comparison.fpsDelta, "fps", inverted = false)
            ComparisonRow("Thermal", comparison.thermalDelta?.toFloat(), "events", inverted = true)
            ComparisonRow("OOM", comparison.oomDelta?.toFloat(), "events", inverted = true)
            ComparisonRow("Drops", comparison.dropFramesDelta?.toFloat(), "frames", inverted = true)
        }

        if (record.hasOutcome && record.optimizedProfile != null && isConnected) {
            Spacer(Modifier.height(8.dp))
            GlassButton(
                onClick = onRetune,
                modifier = Modifier.fillMaxWidth(),
                accentColor = NeonRed,
                contentColor = Color.White,
            ) { Text("Retune & Deploy (Auto-Adjust)", fontWeight = FontWeight.Bold) }
        }

        if (!record.hasOutcome && isConnected) {
            Spacer(Modifier.height(10.dp))
            GlassButton(
                onClick = onCompare,
                modifier = Modifier.fillMaxWidth(),
                accentColor = NeonCyan,
                contentColor = Color.White,
            ) { Text("Compare Now (Pull Client.log)", fontWeight = FontWeight.Bold) }
        }

        if (!record.hasOutcome && !isConnected) {
            Spacer(Modifier.height(6.dp))
            Text("Connect to device to compare", style = MaterialTheme.typography.bodySmall, color = NeonRed.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    delta: Float?,
    unit: String,
    inverted: Boolean,
) {
    if (delta == null) return
    val isGood = if (inverted) delta < 0 else delta >= 0
    val sign = if (delta >= 0) "+" else ""
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "$sign${"%.1f".format(delta)} $unit",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (isGood) NeonGreen else NeonRed,
        )
    }
}
