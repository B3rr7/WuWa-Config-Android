package com.wuwaconfig.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.backend.AccessMethod
import com.wuwaconfig.app.backend.BackendStatus
import com.wuwaconfig.app.ui.theme.*

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    shape: Shape = RoundedCornerShape(8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardStart = if (isLight) MaterialTheme.colorScheme.surface else accentColor.copy(alpha = 0.06f)
    val cardEnd = if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.02f)
    val borderColor = if (isLight) accentColor.copy(alpha = 0.32f) else accentColor.copy(alpha = 0.15f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(if (isLight) 1.dp else 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            cardStart,
                            cardEnd
                        )
                    ),
                    shape = shape
                )
                .border(0.5.dp, borderColor, shape)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun GlassCardHeader(
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor.copy(alpha = 0.8f))
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = NeonCyan,
    contentColor: Color = Color.Black,
    content: @Composable RowScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val buttonContainer = if (isLight) accentColor.copy(alpha = 0.16f) else accentColor.copy(alpha = 0.12f)
    val disabledContainer = if (isLight) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.04f)
    val disabledContent = if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.25f)
    val resolvedContentColor = if (isLight) accentColor else contentColor

    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonContainer,
            contentColor = resolvedContentColor,
            disabledContainerColor = disabledContainer,
            disabledContentColor = disabledContent
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(accentColor.copy(alpha = 0.08f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(0.5.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = NeonRed,
    content: @Composable RowScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val disabledContent = if (isLight) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.25f)
    val borderColor = if (isLight) accentColor.copy(alpha = 0.55f) else accentColor.copy(alpha = 0.3f)

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = accentColor,
            disabledContentColor = disabledContent
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .border(0.5.dp, borderColor, RoundedCornerShape(8.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

    @Composable
    fun BackendStatusCard(status: BackendStatus, onToggle: () -> Unit) {
        val accentColor by animateColorAsState(
            targetValue = when {
                status.connected -> NeonGreen
                status.errorMessage.isNotBlank() -> NeonRed
                else -> NeonAmber
            },
            label = "accent"
        )

        GlassCard(accentColor = accentColor) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor)
                            .border(1.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Access: ${status.method.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                status.connected -> "Connected"
                                status.errorMessage.isNotBlank() -> status.errorMessage
                                else -> "Not connected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                SuggestionChip(
                    onClick = onToggle,
                    label = {
                        val next = when (status.method) {
                            AccessMethod.ADB -> "SHIZUKU"
                            AccessMethod.SHIZUKU -> "ROOT"
                            AccessMethod.ROOT -> "ADB"
                        }
                        Text(next, fontWeight = FontWeight.Bold)
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = accentColor.copy(alpha = 0.1f),
                        labelColor = accentColor
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = accentColor.copy(alpha = 0.2f))
                )
            }
        }
    }

@Composable
fun LogViewer(logs: List<String>, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, accentColor = NeonCyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NeonCyan.copy(alpha = 0.8f))
            )
            Spacer(Modifier.width(8.dp))
            Text("Log", style = MaterialTheme.typography.titleSmall, color = NeonCyan, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 200.dp)
        ) {
            if (logs.isEmpty()) {
                Text("No logs yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    logs.reversed().forEach { log ->
                        val c = when {
                            log.contains("SUCCESS") -> NeonGreen
                            log.contains("FAILED") || log.contains("ERROR") -> NeonRed
                            log.contains("Applying") || log.contains("Connected") -> NeonCyan
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                        Text(log, style = MaterialTheme.typography.labelMedium, color = c, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GradientBackground(content: @Composable () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(background, surface)
                )
            )
    ) {
        content()
    }
}
