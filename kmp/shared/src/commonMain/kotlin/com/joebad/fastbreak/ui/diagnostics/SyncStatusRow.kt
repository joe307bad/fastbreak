package com.joebad.fastbreak.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Compact one-line sync status indicator.
 * Shows health status dot, status text, and cache info.
 */
@Composable
fun SyncStatusRow(
    diagnostics: DiagnosticsInfo,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        // Status dot (8dp colored circle)
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = when {
                        diagnostics.isSyncing -> Color(0xFF2196F3) // Blue
                        diagnostics.lastError != null -> Color(0xFFF44336) // Red
                        diagnostics.isStale -> Color(0xFFFFAA00) // Amber/Yellow
                        else -> Color(0xFF4CAF50) // Green
                    },
                    shape = CircleShape
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Status text
        Text(
            text = when {
                diagnostics.isSyncing -> "Syncing..."
                diagnostics.lastError != null -> "Error"
                diagnostics.isStale -> "Stale"
                else -> "Up to date"
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Cache info (charts count • size)
        Text(
            text = "${diagnostics.cachedChartsCount} charts • ${formatBytes(diagnostics.totalCacheSize)}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}
