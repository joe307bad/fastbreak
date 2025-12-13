package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.model.*
import kotlin.math.roundToInt

/**
 * Helper function to format doubles to a specific number of decimal places.
 */
private fun Double.formatTo(decimals: Int): String {
    val multiplier = when (decimals) {
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
    val rounded = (this * multiplier).roundToInt() / multiplier
    return rounded.toString()
}

/**
 * Generic data table component that displays data from any visualization type.
 */
@Composable
fun DataTableComponent(
    visualization: VisualizationType,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        when (visualization) {
            is ScatterPlotVisualization -> ScatterDataTable(visualization.dataPoints)
            is BarGraphVisualization -> BarDataTable(visualization.dataPoints)
            is LineChartVisualization -> LineDataTable(visualization.series)
            else -> Text("No data available", modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
private fun ScatterDataTable(data: List<ScatterPlotDataPoint>) {
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TableHeader("Rank", 40.dp)
            TableHeader("Player/Team", 120.dp)
            TableHeader("X Value", 80.dp)
            TableHeader("Y Value", 80.dp)
            TableHeader("Sum", 80.dp)
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )

        // Data rows sorted by sum
        data.sortedByDescending { it.sum }.forEachIndexed { index, point ->
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TableCell((index + 1).toString(), 40.dp)
                TableCell(point.label, 120.dp)
                TableCell(point.x.formatTo(2), 80.dp)
                TableCell(point.y.formatTo(2), 80.dp)
                TableCell(point.sum.formatTo(2), 80.dp)
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun BarDataTable(data: List<BarGraphDataPoint>) {
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TableHeader("Rank", 40.dp)
            TableHeader("Team", 120.dp)
            TableHeader("Value", 100.dp)
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )

        // Data rows sorted by value (descending)
        data.sortedByDescending { it.value }.forEachIndexed { index, point ->
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TableCell((index + 1).toString(), 40.dp)
                TableCell(point.label, 120.dp)
                TableCell(point.value.formatTo(1), 100.dp)
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun LineDataTable(series: List<LineChartSeries>) {
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
    ) {
        // Header - show series names
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TableHeader("Week/Game", 80.dp)
            series.forEach { s ->
                TableHeader(s.label, 100.dp)
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )

        // Data rows - show values for each week/game
        val maxPoints = series.maxOfOrNull { it.dataPoints.size } ?: 0
        val sampledIndices = if (maxPoints > 20) {
            // Sample every Nth point to keep table manageable
            val step = maxPoints / 20
            (0 until maxPoints step step).toList()
        } else {
            (0 until maxPoints).toList()
        }

        sampledIndices.forEach { pointIndex ->
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val xValue = series.firstOrNull()?.dataPoints?.getOrNull(pointIndex)?.x?.toInt() ?: pointIndex
                TableCell(xValue.toString(), 80.dp)

                series.forEach { s ->
                    val point = s.dataPoints.getOrNull(pointIndex)
                    val value = point?.y?.formatTo(1) ?: "-"
                    TableCell(value, 100.dp)
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun TableHeader(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun TableCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}
