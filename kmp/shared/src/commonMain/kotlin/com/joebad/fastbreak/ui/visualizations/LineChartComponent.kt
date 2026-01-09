package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import com.joebad.fastbreak.data.model.LineChartSeries
import io.github.koalaplot.core.line.LinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberAxisStyle
import io.github.koalaplot.core.util.VerticalRotation
import io.github.koalaplot.core.util.rotateVertically
import kotlin.math.abs
import kotlin.math.roundToInt


/**
 * Format number to one decimal place
 */
private fun formatToTenth(value: Float): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        val intPart = rounded.toLong()
        val decPart = ((rounded - intPart) * 10).toInt().let { abs(it) }
        if (rounded < 0 && intPart == 0L) {
            "-0.$decPart"
        } else {
            "$intPart.$decPart"
        }
    }
}

/**
 * Parses a hex color string (e.g., "#FF5722") to a Compose Color.
 * Returns null if the string is invalid.
 */
private fun parseHexColor(hex: String?): Color? {
    if (hex == null) return null
    return try {
        val colorString = hex.removePrefix("#")
        val colorLong = when (colorString.length) {
            6 -> "FF$colorString".toLong(16) // Add alpha if not present
            8 -> colorString.toLong(16)
            else -> return null
        }
        Color(colorLong)
    } catch (e: Exception) {
        null
    }
}

/**
 * Reusable line chart component using Koala Plot.
 * Supports multiple series with different colors and team highlighting.
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun LineChartComponent(
    series: List<LineChartSeries>,
    modifier: Modifier = Modifier,
    highlightedTeamCodes: Set<String> = emptySet(),
    yAxisTitle: String? = null
) {
    if (series.isEmpty() || series.all { it.dataPoints.isEmpty() }) return

    // Check if highlighting is active
    val isHighlighting = highlightedTeamCodes.isNotEmpty()

    // Default color palette as fallback when series don't specify colors
    val defaultColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        Color(0xFF9C27B0), // Purple
        Color(0xFF009688), // Teal
        Color(0xFFFF9800), // Orange
        Color(0xFF795548)  // Brown
    )

    // Parse custom colors from series data, falling back to default palette
    val seriesColors = series.mapIndexed { index, lineSeries ->
        parseHexColor(lineSeries.color) ?: defaultColors[index % defaultColors.size]
    }

    // Calculate axis bounds
    val bounds = remember(series) {
        val allPoints = series.flatMap { it.dataPoints }
        val xValues = allPoints.map { it.x.toFloat() }
        val yValues = allPoints.map { it.y.toFloat() }

        val minX = xValues.minOrNull() ?: 0f
        val maxX = xValues.maxOrNull() ?: 1f
        val minY = yValues.minOrNull() ?: 0f
        val maxY = yValues.maxOrNull() ?: 1f

        val xPadding = (maxX - minX) * 0.05f
        val yPadding = (maxY - minY) * 0.1f

        object {
            val xMin = (minX - xPadding)
            val xMax = (maxX + xPadding)
            val yMin = (minY - yPadding)
            val yMax = (maxY + yPadding)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        XYGraph(
            xAxisModel = FloatLinearAxisModel(
                range = bounds.xMin..bounds.xMax
            ),
            yAxisModel = FloatLinearAxisModel(
                range = bounds.yMin..bounds.yMax
            ),
            xAxisStyle = rememberAxisStyle(
                color = MaterialTheme.colorScheme.onSurface
            ),
            yAxisStyle = rememberAxisStyle(
                color = MaterialTheme.colorScheme.onSurface
            ),
            xAxisLabels = { value ->
                Text(
                    text = value.toInt().toString(),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            yAxisLabels = { value ->
                Text(
                    text = formatToTenth(value),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            xAxisTitle = {
                Text(
                    text = "Week/Game",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            },
            yAxisTitle = {
                Text(
                    text = yAxisTitle ?: "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .rotateVertically(VerticalRotation.COUNTER_CLOCKWISE)
                        .padding(end = 4.dp)
                )
            },
            horizontalMajorGridLineStyle = LineStyle(
                brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                strokeWidth = 1.dp
            ),
            horizontalMinorGridLineStyle = null,
            verticalMajorGridLineStyle = LineStyle(
                brush = SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                strokeWidth = 1.dp
            ),
            verticalMinorGridLineStyle = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)
        ) {
            series.forEachIndexed { seriesIndex, lineSeries ->
                if (lineSeries.dataPoints.size < 2) return@forEachIndexed

                // Check if this series should be highlighted
                val isHighlighted = isHighlighting && highlightedTeamCodes.any { code ->
                    lineSeries.label.contains(code, ignoreCase = true)
                }

                // Apply transparency if highlighting is active and this series is not highlighted
                val baseColor = seriesColors[seriesIndex]
                val color = if (isHighlighting && !isHighlighted) {
                    baseColor.copy(alpha = 0.2f)
                } else {
                    baseColor
                }

                // Convert data points to DefaultPoint
                val points = lineSeries.dataPoints.map { point ->
                    DefaultPoint(point.x.toFloat(), point.y.toFloat())
                }

                LinePlot(
                    data = points,
                    lineStyle = LineStyle(
                        brush = SolidColor(color),
                        strokeWidth = 2.dp
                    )
                )
            }
        }

        // Series legend - max 5 items per row
        val maxItemsPerRow = 5
        val numRows = ceil(series.size.toDouble() / maxItemsPerRow).toInt()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(numRows) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val startIndex = rowIndex * maxItemsPerRow
                    val endIndex = minOf(startIndex + maxItemsPerRow, series.size)

                    for (index in startIndex until endIndex) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(seriesColors[index], CircleShape)
                            )
                            Text(
                                text = series[index].label,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
