package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.BarGraphDataPoint
import io.github.koalaplot.core.bar.DefaultBar
import io.github.koalaplot.core.bar.VerticalBarPlot
import io.github.koalaplot.core.gestures.GestureConfig
import io.github.koalaplot.core.line.LinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.VerticalRotation
import io.github.koalaplot.core.util.rotateVertically
import io.github.koalaplot.core.xygraph.CategoryAxisModel
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.Point
import io.github.koalaplot.core.xygraph.TickPosition
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberAxisStyle
import kotlin.math.roundToInt

// Format number to one decimal place
private fun formatToTenth(value: Float): String {
    val rounded = (value * 10).roundToInt() / 10.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        val intPart = rounded.toLong()
        val decPart = ((rounded - intPart) * 10).toInt().let { kotlin.math.abs(it) }
        if (rounded < 0 && intPart == 0L) {
            "-0.$decPart"
        } else {
            "$intPart.$decPart"
        }
    }
}

/**
 * Reusable bar chart component using Koala Plot.
 * Supports both positive and negative values with pan and zoom.
 *
 * Features:
 * - Pan and zoom support via pinch/drag gestures
 * - Labels and connector lines move with the chart during zoom/pan
 * - Alternating colors for positive/negative values
 * - Team highlighting support
 * - Diagonal label positioning to minimize overlaps
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun BarChartComponent(
    data: List<BarGraphDataPoint>,
    modifier: Modifier = Modifier,
    highlightedTeamCodes: Set<String> = emptySet()
) {
    if (data.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val labelBackgroundColor = MaterialTheme.colorScheme.surface

    // Define alternating colors for positive and negative values
    val positiveColors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50)  // Green
    )
    val negativeColors = listOf(
        Color(0xFFF44336), // Red
        Color(0xFF9C27B0)  // Purple
    )

    // Check if highlighting is active
    val isHighlighting = highlightedTeamCodes.isNotEmpty()

    // Calculate bounds with padding
    val minValue = (data.minOfOrNull { it.value } ?: 0.0).toFloat()
    val maxValue = (data.maxOfOrNull { it.value } ?: 1.0).toFloat()

    // Add padding to Y range for better visualization and to ensure labels at edges are visible
    val yPadding = (maxValue - minValue) * 0.35f  // 35% padding to ensure extreme end labels are fully visible
    val paddedMinValue = minValue - yPadding
    val paddedMaxValue = maxValue + yPadding

    // Prepare bar data with colors and label positions
    data class BarWithLayout(
        val dataPoint: BarGraphDataPoint,
        val color: Color,
        val labelYPosition: Float  // Y position along the diagonal line (in data coordinates)
    )

    val barsWithLayout = remember(data, isHighlighting, highlightedTeamCodes, paddedMaxValue, paddedMinValue) {
        var positiveIndex = 0
        var negativeIndex = 0

        // First pass: assign colors
        val coloredBars = data.map { point ->
            // Check if this bar should be highlighted
            val isHighlighted = isHighlighting && highlightedTeamCodes.any { code ->
                point.label.contains(code, ignoreCase = true)
            }

            // Determine color with alternating pattern
            val baseColor = if (point.value < 0) {
                val color = negativeColors[negativeIndex % negativeColors.size]
                negativeIndex++
                color
            } else {
                val color = positiveColors[positiveIndex % positiveColors.size]
                positiveIndex++
                color
            }

            val color = if (isHighlighting && !isHighlighted) {
                baseColor.copy(alpha = 0.2f)
            } else {
                baseColor
            }

            point to color
        }

        // Second pass: calculate label Y positions along diagonal line
        // For each bar, the label's Y position is determined by its X position (index)
        // Positive bars: diagonal line from paddedMaxValue (leftmost) to 0 (rightmost)
        // Negative bars: diagonal line from paddedMinValue (leftmost) to 0 (rightmost)

        val positiveIndices = data.indices.filter { data[it].value >= 0 }
        val negativeIndices = data.indices.filter { data[it].value < 0 }

        val labelPositions = mutableMapOf<Int, Float>()

        // Positive bars: line descends from max to 0 as we go left to right
        if (positiveIndices.isNotEmpty()) {
            val startY = paddedMaxValue
            val endY = 0f
            positiveIndices.forEachIndexed { idx, dataIndex ->
                // Linear interpolation based on bar index
                val t = idx.toFloat() / positiveIndices.size.coerceAtLeast(1).toFloat()
                val yValue = startY + (endY - startY) * t
                labelPositions[dataIndex] = yValue
            }
        }

        // Negative bars: line descends from 0 to min as we go left to right
        // First negative bar (closest to 0) should have label near 0
        // Last negative bar (most negative) should have label near paddedMinValue
        if (negativeIndices.isNotEmpty()) {
            val startY = 0f
            val endY = paddedMinValue
            negativeIndices.forEachIndexed { idx, dataIndex ->
                // Linear interpolation based on position in negative bar sequence
                val t = idx.toFloat() / negativeIndices.size.coerceAtLeast(1).toFloat()
                val yValue = startY + (endY - startY) * t
                labelPositions[dataIndex] = yValue
            }
        }

        coloredBars.mapIndexed { index, (point, color) ->
            BarWithLayout(point, color, labelPositions[index] ?: 0f)
        }
    }

    // Create axis models - use FloatLinearAxisModel for both to enable zoom/pan
    val xAxisModel = remember(data) {
        val range = 0f..(data.size - 1).toFloat()
        val rangeSize = range.endInclusive - range.start
        FloatLinearAxisModel(
            range = range,
            minViewExtent = rangeSize * 0.1f, // Allow zooming in to 10% of full range
            maxViewExtent = rangeSize // Full range when zoomed out
        )
    }

    val yAxisModel = remember(paddedMinValue, paddedMaxValue) {
        val rangeSize = paddedMaxValue - paddedMinValue
        FloatLinearAxisModel(
            range = paddedMinValue..paddedMaxValue,
            minViewExtent = rangeSize * 0.1f, // Allow zooming in to 10% of full range
            maxViewExtent = rangeSize // Full range when zoomed out
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chart with label overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            XYGraph(
            xAxisModel = xAxisModel,
            yAxisModel = yAxisModel,
            gestureConfig = GestureConfig(
                panXEnabled = true,
                panYEnabled = true,
                zoomXEnabled = true,
                zoomYEnabled = true
            ),
            xAxisStyle = rememberAxisStyle(
                color = MaterialTheme.colorScheme.onSurface,
                tickPosition = TickPosition.Outside,
                labelRotation = 0
            ),
            yAxisStyle = rememberAxisStyle(
                color = MaterialTheme.colorScheme.onSurface,
                tickPosition = TickPosition.Outside,
                labelRotation = 0
            ),
            xAxisLabels = @Composable { value: Float ->
                // Show custom labels for integer values that correspond to bar indices
                val index = value.toInt()
                if (value == index.toFloat() && index in data.indices) {
                    Text(
                        text = data[index].label,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.rotateVertically(VerticalRotation.COUNTER_CLOCKWISE)
                    )
                }
            },
            xAxisTitle = @Composable {},
            yAxisLabels = @Composable { value: Float ->
                Text(
                    text = formatToTenth(value),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            yAxisTitle = @Composable {},
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
                .semantics { contentDescription = "bar chart" }
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)
        ) {
            // Create bar plot with numeric x-axis indices for zoom support
            // Replace zero values with tiny positive values to ensure bars render
            VerticalBarPlot(
                xData = data.indices.map { it.toFloat() },
                yData = data.map {
                    val value = it.value.toFloat()
                    // Use 0.1% of the range as minimum to ensure zero bars render with enough space
                    if (value == 0f) {
                        val range = paddedMaxValue - paddedMinValue
                        range * 0.001f
                    } else {
                        value
                    }
                },
                barWidth = 0.7f,
                bar = { seriesIndex, groupIndex, entry ->
                    val barInfo = barsWithLayout[groupIndex]
                    val barColor = barInfo.color

                    DefaultBar(brush = SolidColor(barColor))
                }
            )

            // Draw connector lines from label to bar using numeric indices
            data.forEachIndexed { index, point ->
                val barInfo = barsWithLayout[index]
                val barColor = barInfo.color
                val barValue = point.value.toFloat()
                val labelYValue = barInfo.labelYPosition

                // Draw connector line using numeric index for x-coordinate
                LinePlot(
                    data = listOf(
                        Point(index.toFloat(), labelYValue),
                        Point(index.toFloat(), barValue)
                    ),
                    lineStyle = LineStyle(
                        brush = SolidColor(barColor.copy(alpha = 0.5f)),
                        strokeWidth = 1.dp
                    )
                )
            }

            // Draw labels positioned along diagonal lines
            // Access current viewport ranges to make labels move with zoom/pan
            val currentXRange = xAxisModel.viewRange.value
            val currentYRange = yAxisModel.viewRange.value

            Canvas(modifier = Modifier.fillMaxSize()) {
                data.forEachIndexed { index, point ->
                    val barInfo = barsWithLayout[index]
                    val barColor = barInfo.color
                    val barValue = point.value.toFloat()
                    val barLabel = point.label
                    val labelYValue = barInfo.labelYPosition

                    // Check if this bar is within the current visible x-range
                    val barXValue = index.toFloat()
                    if (barXValue < currentXRange.start || barXValue > currentXRange.endInclusive) {
                        return@forEachIndexed  // Skip labels outside visible area
                    }

                    // Calculate pixel position from data coordinates using current viewport
                    val xDataRange = currentXRange.endInclusive - currentXRange.start
                    val xNormalized = if (xDataRange > 0) {
                        (barXValue - currentXRange.start) / xDataRange
                    } else 0.5f
                    val labelX = size.width * xNormalized

                    val yDataRange = currentYRange.endInclusive - currentYRange.start
                    val yNormalized = if (yDataRange > 0) {
                        (labelYValue - currentYRange.start) / yDataRange
                    } else 0.5f
                    val labelY = size.height * (1f - yNormalized)

                    // Prepare and draw text with background
                    val textStyle = TextStyle(
                        color = barColor,
                        fontSize = 9.sp
                    )
                    val textLayoutResult = textMeasurer.measure(barLabel, textStyle)
                    val rotationAngle = if (barValue >= 0) -25f else -45f
                    val backgroundPadding = 2.dp.toPx()

                    rotate(degrees = rotationAngle, pivot = Offset(labelX, labelY)) {
                        val bgWidth = textLayoutResult.size.width + backgroundPadding * 2
                        val bgHeight = textLayoutResult.size.height + backgroundPadding * 2
                        val bgLeft = labelX - bgWidth / 2f
                        val bgTop = labelY - bgHeight / 2f

                        drawRoundRect(
                            color = labelBackgroundColor,
                            topLeft = Offset(bgLeft, bgTop),
                            size = androidx.compose.ui.geometry.Size(bgWidth, bgHeight),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )

                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                labelX - textLayoutResult.size.width / 2f,
                                labelY - textLayoutResult.size.height / 2f
                            )
                        )
                    }
                }
            }
        }
        }

        // Interaction hint
        Text(
            text = "Pinch to zoom • Drag to pan",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
