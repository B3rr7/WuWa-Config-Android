package com.wuwaconfig.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuwaconfig.app.ui.theme.NeonAmber

/**
 * Shows that the last device operation was cancelled.
 *
 * `DeviceOps.operationCancelled` is set by `requestCancel` and cleared when the
 * next operation starts, so this is true only for the window between a cancel
 * and the following operation. Without it a cancelled deploy is indistinguishable
 * from one that never started: the buttons simply re-enable and nothing says
 * why.
 *
 * Rendered only when nothing is running — while an operation is in flight the
 * Cancel button is the visible state.
 */
@Composable
fun CancelledBanner(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(NeonAmber.copy(alpha = 0.10f))
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Cancel,
            contentDescription = null,
            tint = NeonAmber,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Operation cancelled",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = NeonAmber,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Nothing was written to the device. The config on the game is unchanged.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
