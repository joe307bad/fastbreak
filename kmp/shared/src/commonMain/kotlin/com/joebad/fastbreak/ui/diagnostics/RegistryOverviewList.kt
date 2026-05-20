package com.joebad.fastbreak.ui.diagnostics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.model.ChartDefinition
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.data.model.Sport

/**
 * Scrollable list showing all charts in the registry, grouped by sport.
 * Each chart is displayed as a single line with ellipsis overflow.
 */
@Composable
fun RegistryOverviewList(
    registry: Registry = Registry.empty(),
    onChartClick: (ChartDefinition) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (registry.charts.isEmpty()) {
        Text(
            text = "No registry loaded",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier) {
        // Group charts by sport
        Sport.entries.forEach { sport ->
            val sportCharts = registry.chartsForSport(sport)
            if (sportCharts.isNotEmpty()) {
                // Sport header
                Text(
                    text = sport.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )

                // Chart items (sorted alphabetically by title)
                sportCharts.sortedBy { it.title }.forEach { chart ->
                    ChartOverviewItem(
                        chart = chart,
                        onClick = { onChartClick(chart) }
                    )
                }
            }
        }
    }
}

/**
 * Single line chart item showing title and last updated time.
 *
 * Placeholders (charts that exist in the registry but whose data hasn't
 * landed in cache yet — cachedAt == null) render disabled so clicking
 * doesn't open DataVizScreen to a missing-data error.
 */
@Composable
private fun ChartOverviewItem(
    chart: ChartDefinition,
    onClick: () -> Unit
) {
    val isReady = chart.cachedAt != null
    val alpha = if (isReady) 1f else 0.5f
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))
        .let { if (isReady) it.clickable(onClick = onClick) else it }
        .padding(vertical = 4.dp, horizontal = 4.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier
    ) {
        Text(
            text = chart.title,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTimeAgo(chart.lastUpdated),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f * alpha),
            maxLines = 1
        )
    }
}
