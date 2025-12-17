package com.joebad.fastbreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
            is ScatterPlotVisualization -> ScatterDataTable(
                data = visualization.dataPoints,
                invertYAxis = visualization.invertYAxis,
                xColumnLabel = visualization.xColumnLabel,
                yColumnLabel = visualization.yColumnLabel,
                quadrantTopRight = visualization.quadrantTopRight,
                quadrantTopLeft = visualization.quadrantTopLeft,
                quadrantBottomLeft = visualization.quadrantBottomLeft,
                quadrantBottomRight = visualization.quadrantBottomRight
            )
            is BarGraphVisualization -> BarDataTable(visualization.dataPoints)
            is LineChartVisualization -> LineDataTable(visualization.series)
            is TableVisualization -> GenericDataTable(visualization.dataPoints)
        }
    }
}

// Helper to parse hex color string to Compose Color
private fun parseHexColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    return Color(("FF$cleanHex").toLong(16))
}

@Composable
private fun ScatterDataTable(
    data: List<ScatterPlotDataPoint>,
    invertYAxis: Boolean = false,
    xColumnLabel: String? = null,
    yColumnLabel: String? = null,
    quadrantTopRight: QuadrantConfig? = null,
    quadrantTopLeft: QuadrantConfig? = null,
    quadrantBottomLeft: QuadrantConfig? = null,
    quadrantBottomRight: QuadrantConfig? = null
) {
    val horizontalScrollState = rememberScrollState()

    // Calculate averages for quadrant determination
    val avgX = data.map { it.x }.average()
    val avgY = data.map { it.y }.average()

    // Resolve quadrant colors (use config or defaults)
    val topRightColor = quadrantTopRight?.let { parseHexColor(it.color) } ?: Color(0xFF4CAF50)
    val topLeftColor = quadrantTopLeft?.let { parseHexColor(it.color) } ?: Color(0xFF2196F3)
    val bottomLeftColor = quadrantBottomLeft?.let { parseHexColor(it.color) } ?: Color(0xFFFF9800)
    val bottomRightColor = quadrantBottomRight?.let { parseHexColor(it.color) } ?: Color(0xFFF44336)

    // When invertYAxis is true, lower Y values are better (e.g., defensive EPA)
    // So we sort by x - y (higher x, lower y = better) instead of x + y
    val sortedData = if (invertYAxis) {
        data.sortedByDescending { it.x - it.y }
    } else {
        data.sortedByDescending { it.sum }
    }

    // Helper to get quadrant color for a point
    fun getQuadrantColor(point: ScatterPlotDataPoint): Color {
        // When invertYAxis is true, "good Y" means LOWER values
        val isGoodY = if (invertYAxis) point.y < avgY else point.y >= avgY
        return when {
            point.x >= avgX && isGoodY -> topRightColor
            point.x < avgX && isGoodY -> topLeftColor
            point.x < avgX && !isGoodY -> bottomLeftColor
            else -> bottomRightColor
        }
    }

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
            TableHeader("Player/Team", 130.dp)
            TableHeader(xColumnLabel ?: "X Value", 80.dp)
            TableHeader(yColumnLabel ?: "Y Value", 80.dp)
            TableHeader("Score", 80.dp)
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )

        // Data rows sorted by score (accounting for Y-axis inversion)
        sortedData.forEachIndexed { index, point ->
            // Calculate display score based on inversion
            val score = if (invertYAxis) point.x - point.y else point.sum
            val quadrantColor = getQuadrantColor(point)

            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell((index + 1).toString(), 40.dp)
                // Team name with colored dot
                Row(
                    modifier = Modifier.width(130.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(quadrantColor, CircleShape)
                    )
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TableCell(point.x.formatTo(2), 80.dp)
                TableCell(point.y.formatTo(2), 80.dp)
                TableCell(score.formatTo(2), 80.dp)
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

/**
 * Generic data table with pinned header row and pinned left column.
 * Uses synchronized scroll states for smooth scrolling experience.
 */
@Composable
private fun GenericDataTable(data: List<TableDataPoint>) {
    if (data.isEmpty()) {
        Text("No data available", modifier = Modifier.padding(8.dp))
        return
    }

    // Get column headers from first data point
    val columns = data.firstOrNull()?.columns ?: emptyList()

    // Scroll states for synchronized scrolling
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val headerHorizontalScrollState = rememberScrollState()
    val leftColumnVerticalScrollState = rememberScrollState()

    // Sync horizontal scroll between header and data area
    LaunchedEffect(horizontalScrollState) {
        snapshotFlow { horizontalScrollState.value }
            .collect { value ->
                if (headerHorizontalScrollState.value != value) {
                    headerHorizontalScrollState.scrollTo(value)
                }
            }
    }

    LaunchedEffect(headerHorizontalScrollState) {
        snapshotFlow { headerHorizontalScrollState.value }
            .collect { value ->
                if (horizontalScrollState.value != value) {
                    horizontalScrollState.scrollTo(value)
                }
            }
    }

    // Sync vertical scroll between left column and data area
    LaunchedEffect(verticalScrollState) {
        snapshotFlow { verticalScrollState.value }
            .collect { value ->
                if (leftColumnVerticalScrollState.value != value) {
                    leftColumnVerticalScrollState.scrollTo(value)
                }
            }
    }

    LaunchedEffect(leftColumnVerticalScrollState) {
        snapshotFlow { leftColumnVerticalScrollState.value }
            .collect { value ->
                if (verticalScrollState.value != value) {
                    verticalScrollState.scrollTo(value)
                }
            }
    }

    val labelColumnWidth = 100.dp
    val dataColumnWidth = 90.dp
    val rowHeight = 36.dp
    val headerHeight = 40.dp
    val backgroundColor = MaterialTheme.colorScheme.background
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Column(modifier = Modifier.fillMaxSize()) {
        // Top row: pinned corner + scrollable header (fixed height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
        ) {
            // Top-left corner: pinned "Team" header
            Box(
                modifier = Modifier
                    .width(labelColumnWidth)
                    .fillMaxHeight()
                    .background(backgroundColor),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Team",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Vertical divider between pinned column and scrollable area
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = dividerColor,
                thickness = 1.dp
            )

            // Top-right: scrollable column headers
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(headerHorizontalScrollState)
                    .background(backgroundColor),
                verticalAlignment = Alignment.CenterVertically
            ) {
                columns.forEach { column ->
                    Box(
                        modifier = Modifier
                            .width(dataColumnWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = column.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        // Header divider
        HorizontalDivider(color = dividerColor, thickness = 1.dp)

        // Bottom row: pinned left column + scrollable data (takes remaining space)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)  // Take remaining vertical space
        ) {
            // Bottom-left: pinned team names column (vertically scrollable)
            Column(
                modifier = Modifier
                    .width(labelColumnWidth)
                    .fillMaxHeight()
                    .verticalScroll(leftColumnVerticalScrollState)
                    .background(backgroundColor)
            ) {
                data.forEach { point ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                }
            }

            // Vertical divider between pinned column and scrollable area
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = dividerColor,
                thickness = 1.dp
            )

            // Bottom-right: scrollable data area (both directions)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(horizontalScrollState)
                    .verticalScroll(verticalScrollState)
            ) {
                data.forEach { point ->
                    Row(
                        modifier = Modifier.height(rowHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        point.columns.forEach { column ->
                            Box(
                                modifier = Modifier
                                    .width(dataColumnWidth)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = column.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                }
            }
        }
    }
}
