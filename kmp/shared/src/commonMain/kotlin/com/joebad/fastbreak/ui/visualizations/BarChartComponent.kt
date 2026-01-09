package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.BarGraphDataPoint
import io.github.koalaplot.core.bar.DefaultVerticalBar
import io.github.koalaplot.core.bar.VerticalBarPlot
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
 * Follows the same conventions as ScatterPlot.kt for consistency.
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun BarChartComponent(
    data: List<BarGraphDataPoint>,
    modifier: Modifier = Modifier,
    highlightedTeamCodes: Set<String> = emptySet()
) {
    if (data.isEmpty()) return

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

    // Add padding to Y range for better visualization
    val yPadding = (maxValue - minValue) * 0.1f
    val paddedMinValue = minValue - yPadding
    val paddedMaxValue = maxValue + yPadding

    // Prepare bar data with colors
    data class ColoredBar(
        val dataPoint: BarGraphDataPoint,
        val color: Color
    )

    val coloredBars = remember(data, isHighlighting, highlightedTeamCodes) {
        var positiveIndex = 0
        var negativeIndex = 0

        data.map { point ->
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

            ColoredBar(point, color)
        }
    }

    // Create axis models
    val xAxisModel = remember(data) {
        CategoryAxisModel(data.map { it.label })
    }

    val yAxisModel = remember(paddedMinValue, paddedMaxValue) {
        FloatLinearAxisModel(
            paddedMinValue..paddedMaxValue,
            allowZooming = true,
            allowPanning = true
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chart
        XYGraph(
            xAxisModel = xAxisModel,
            yAxisModel = yAxisModel,
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
            xAxisLabels = { label: String ->
                val index = data.indexOfFirst { it.label == label }
                val point = data[index]

                // Determine which color this bar uses
                val positiveIndex = data.take(index + 1).count { it.value >= 0 } - 1
                val negativeIndex = data.take(index + 1).count { it.value < 0 } - 1

                val labelColor = if (point.value < 0) {
                    negativeColors[negativeIndex % negativeColors.size]
                } else {
                    positiveColors[positiveIndex % positiveColors.size]
                }

                // Stagger labels with padding
                val topPadding = if (index % 2 == 1) 12.dp else 0.dp

                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = labelColor,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                    modifier = Modifier.padding(top = topPadding)
                )
            },
            xAxisTitle = {},
            yAxisLabels = { value: Float ->
                Text(
                    text = formatToTenth(value),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            yAxisTitle = {},
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
            panZoomEnabled = true,
            modifier = Modifier
                .semantics { contentDescription = "bar chart" }
                .fillMaxWidth()
                .height(400.dp)
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)
        ) {
            // Create bar plot with separate x and y data
            // Replace zero values with tiny positive values to ensure bars render
            VerticalBarPlot(
                xData = data.map { it.label },
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
                bar = @Composable { index: Int ->
                    val barColor = coloredBars[index].color
                    val barValue = data[index].value.toFloat() // Original value

                    Box(modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val barCenterX = size.width / 2f

                            if (barValue == 0f) {
                                // Special handling for zero values
                                // Use same logic as positive bars but with estimated pixels
                                val yRange = paddedMaxValue - paddedMinValue
                                val spaceBelow = if (paddedMinValue < 0) kotlin.math.abs(paddedMinValue) else 0f

                                // Estimate pixel conversion for the full chart
                                val estimatedChartHeight = 315f  // Actual plot area is smaller than 400dp total
                                val estimatedPixelsPerUnit = estimatedChartHeight / yRange
                                val extensionDistance = (spaceBelow * estimatedPixelsPerUnit) + 335f

                                // For zero bars, bottom of bar = x-axis, just like positive bars
                                // size.height is the bottom of the bar
                                drawLine(
                                    color = barColor,
                                    start = Offset(barCenterX, size.height),  // Bottom of bar = x-axis
                                    end = Offset(barCenterX, size.height + extensionDistance),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                )
                            } else {
                                // For positive bars: start at bottom (x-axis is at bottom of bar)
                                // For negative bars: start at top (x-axis is at top of bar)
                                val startY = if (barValue > 0) {
                                    size.height  // Bottom of positive bar (at x-axis)
                                } else {
                                    0f  // Top of negative bar (at x-axis)
                                }

                                // Calculate the distance to extend beyond the bar
                                val yRange = paddedMaxValue - paddedMinValue
                                val barValueRange = kotlin.math.abs(barValue)

                                // Calculate how much space is below the x-axis in the chart
                                val spaceBelow = if (paddedMinValue < 0) {
                                    kotlin.math.abs(paddedMinValue)
                                } else {
                                    0f
                                }

                                // Extension should cover the full space below the x-axis plus label area
                                val pixelsPerUnit = size.height / barValueRange
                                val extensionDistance = (spaceBelow * pixelsPerUnit) + 150f

                                // Draw line from the x-axis down to label area
                                drawLine(
                                    color = barColor,
                                    start = Offset(barCenterX, startY),
                                    end = Offset(barCenterX, startY + extensionDistance),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                )
                            }
                        }
                    ) {
                        // Draw the actual bar
                        // Make zero-value bars transparent so only the connector line shows
                        DefaultVerticalBar(
                            brush = SolidColor(
                                if (barValue == 0f) Color.Transparent
                                else barColor
                            )
                        )
                    }
                }
            )
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
