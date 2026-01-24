package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.joebad.fastbreak.data.model.ReferenceLine
import io.github.koalaplot.core.bar.VerticalBarPlot
import io.github.koalaplot.core.bar.verticalSolidBar
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

// Parse hex color string to Color
private fun parseHexColor(hexColor: String): Color {
    val hex = hexColor.removePrefix("#")
    return when (hex.length) {
        6 -> Color(("FF" + hex).toLong(16))
        8 -> Color(hex.toLong(16))
        else -> Color.Gray
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
    highlightedTeamCodes: Set<String> = emptySet(),
    title: String = "Bar Chart",
    showShareButton: Boolean = false,
    onShareClick: ((() -> Unit)?) -> Unit = {},
    source: String = "",
    topReferenceLine: ReferenceLine? = null,
    bottomReferenceLine: ReferenceLine? = null
) {
    if (data.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val labelBackgroundColor = MaterialTheme.colorScheme.surface

    // State for reference line selection: "top", "bottom", or empty set (all shown)
    var selectedReferenceLines by remember { mutableStateOf(setOf<String>()) }

    // Define color palettes for different regions
    val aboveTopColors = listOf(
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50)  // Green
    )
    val betweenColors = listOf(
        Color(0xFFFF9800), // Orange
        Color(0xFFBF6F00)  // Dark Orange (darker version of orange)
    )
    val belowBottomColors = listOf(
        Color(0xFFF44336), // Red
        Color(0xFF9C27B0)  // Purple
    )

    // Fallback colors when no reference lines exist (original behavior)
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

    val barsWithLayout = remember(data, isHighlighting, highlightedTeamCodes, selectedReferenceLines, topReferenceLine, bottomReferenceLine, paddedMaxValue, paddedMinValue, textMeasurer) {
        // Counters for each region
        var aboveTopIndex = 0
        var betweenIndex = 0
        var belowBottomIndex = 0
        var positiveIndex = 0
        var negativeIndex = 0

        // First pass: assign colors
        val coloredBars = data.map { point ->
            // Check if this bar should be highlighted by team codes
            val isHighlightedByTeam = isHighlighting && highlightedTeamCodes.any { code ->
                point.label.contains(code, ignoreCase = true)
            }

            // Determine which region this bar is in (if reference lines exist)
            val barRegion = when {
                topReferenceLine != null && bottomReferenceLine != null -> {
                    when {
                        point.value >= topReferenceLine.value -> "aboveTop"
                        point.value <= bottomReferenceLine.value -> "belowBottom"
                        else -> "between"
                    }
                }
                topReferenceLine != null -> {
                    if (point.value >= topReferenceLine.value) "aboveTop" else "belowTop"
                }
                bottomReferenceLine != null -> {
                    if (point.value <= bottomReferenceLine.value) "belowBottom" else "aboveBottom"
                }
                else -> null
            }

            // Check if this bar matches the reference line selection
            val matchesReferenceLineSelection = if (selectedReferenceLines.isEmpty()) {
                true
            } else {
                val aboveTop = topReferenceLine != null &&
                              selectedReferenceLines.contains("top") &&
                              point.value >= topReferenceLine.value
                val belowBottom = bottomReferenceLine != null &&
                                 selectedReferenceLines.contains("bottom") &&
                                 point.value <= bottomReferenceLine.value
                aboveTop || belowBottom
            }

            // Determine color with alternating pattern based on region
            val baseColor = if (barRegion != null) {
                // Use region-based colors when reference lines exist
                when (barRegion) {
                    "aboveTop" -> {
                        val color = aboveTopColors[aboveTopIndex % aboveTopColors.size]
                        aboveTopIndex++
                        color
                    }
                    "between" -> {
                        val color = betweenColors[betweenIndex % betweenColors.size]
                        betweenIndex++
                        color
                    }
                    "belowBottom" -> {
                        val color = belowBottomColors[belowBottomIndex % belowBottomColors.size]
                        belowBottomIndex++
                        color
                    }
                    "belowTop" -> {
                        val color = betweenColors[betweenIndex % betweenColors.size]
                        betweenIndex++
                        color
                    }
                    "aboveBottom" -> {
                        val color = betweenColors[betweenIndex % betweenColors.size]
                        betweenIndex++
                        color
                    }
                    else -> Color.Gray
                }
            } else {
                // Fallback to original positive/negative color scheme
                if (point.value < 0) {
                    val color = negativeColors[negativeIndex % negativeColors.size]
                    negativeIndex++
                    color
                } else {
                    val color = positiveColors[positiveIndex % positiveColors.size]
                    positiveIndex++
                    color
                }
            }

            val color = when {
                // If team highlighting is active and this bar is not highlighted by team
                isHighlighting && !isHighlightedByTeam -> baseColor.copy(alpha = 0.2f)
                // If reference line selection is active and this bar doesn't match
                selectedReferenceLines.isNotEmpty() && !matchesReferenceLineSelection -> baseColor.copy(alpha = 0.2f)
                // Otherwise, use the base color
                else -> baseColor
            }

            point to color
        }

        // Second pass: calculate label Y positions
        // If all values are positive or all negative, align labels to top of each bar
        // Otherwise use diagonal line positioning for mixed positive/negative charts

        val allPositive = data.all { it.value >= 0 }
        val allNegative = data.all { it.value < 0 }

        val labelPositions = mutableMapOf<Int, Float>()

        if (allPositive || allNegative) {
            // Simple stepped layout - start high and step down consistently

            // Start a few units above the highest value
            var currentY = if (allPositive) {
                maxValue + 4f
            } else {
                minValue - 4f
            }

            // Step size - 3% of data range
            val stepSize = (maxValue - minValue) * 0.03f

            // First pass: calculate initial positions
            val initialPositions = mutableMapOf<Int, Float>()
            data.indices.forEach { index ->
                initialPositions[index] = currentY
                if (allPositive) {
                    currentY -= stepSize
                } else {
                    currentY += stepSize
                }
            }

            // Second pass: find the minimum distance between any label and its bar
            val minDistance = data.indices.minOfOrNull { index ->
                val labelY = initialPositions[index] ?: 0f
                val barY = data[index].value.toFloat()
                if (allPositive) {
                    labelY - barY  // Distance above the bar
                } else {
                    barY - labelY  // Distance below the bar
                }
            } ?: 0f

            // Third pass: shift all labels down by the minimum distance to get as close as possible
            data.indices.forEach { index ->
                val initialY = initialPositions[index] ?: 0f
                labelPositions[index] = if (allPositive) {
                    initialY - minDistance
                } else {
                    initialY + minDistance
                }
            }
        } else {
            // Mixed positive/negative - use diagonal line positioning
            val positiveIndices = data.indices.filter { data[it].value >= 0 }
            val negativeIndices = data.indices.filter { data[it].value < 0 }

            // Positive bars: line descends from max value (with small buffer) to 0 as we go left to right
            if (positiveIndices.isNotEmpty()) {
                val buffer = (maxValue - minValue) * 0.05f
                val startY = maxValue + buffer
                val endY = 0f
                positiveIndices.forEachIndexed { idx, dataIndex ->
                    // Linear interpolation based on bar index
                    val t = idx.toFloat() / positiveIndices.size.coerceAtLeast(1).toFloat()
                    val yValue = startY + (endY - startY) * t
                    labelPositions[dataIndex] = yValue
                }
            }

            // Negative bars: line descends from 0 to min value (with small buffer) as we go left to right
            if (negativeIndices.isNotEmpty()) {
                val buffer = (maxValue - minValue) * 0.05f
                val startY = 0f
                val endY = minValue - buffer
                negativeIndices.forEachIndexed { idx, dataIndex ->
                    // Linear interpolation based on position in negative bar sequence
                    val t = idx.toFloat() / negativeIndices.size.coerceAtLeast(1).toFloat()
                    val yValue = startY + (endY - startY) * t
                    labelPositions[dataIndex] = yValue
                }
            }
        }

        coloredBars.mapIndexed { index, (point, color) ->
            BarWithLayout(point, color, labelPositions[index] ?: 0f)
        }
    }

    // Create axis models - use FloatLinearAxisModel for both to enable zoom/pan
    val xAxisModel = remember(data) {
        val dataRange = 0f..(data.size - 1).toFloat()
        val rangeSize = dataRange.endInclusive - dataRange.start
        // Add padding to show labels at edges (10% on each side)
        val padding = rangeSize * 0.1f
        val paddedRange = (dataRange.start - padding)..(dataRange.endInclusive + padding)
        FloatLinearAxisModel(
            range = paddedRange,
            minViewExtent = rangeSize * 0.1f, // Allow zooming in to 10% of full range
            maxViewExtent = paddedRange.endInclusive - paddedRange.start // Full padded range when zoomed out
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

    ShareableChartContainer(
        title = title,
        source = source,
        showShareButton = showShareButton,
        onShareClick = onShareClick,
        modifier = modifier
    ) {
        Column(
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
            // Draw reference line shading first (behind everything)
            topReferenceLine?.let { refLine ->
                val refValue = refLine.value.toFloat()
                val refColor = parseHexColor(refLine.color)

                // Create horizontal line shading above the reference line
                val numLines = 50
                val yStep = (yAxisModel.range.endInclusive - refValue) / numLines

                for (i in 0 until numLines) {
                    val y = refValue + (i * yStep)
                    LinePlot(
                        data = listOf(
                            Point(xAxisModel.range.start, y),
                            Point(xAxisModel.range.endInclusive, y)
                        ),
                        lineStyle = LineStyle(
                            brush = SolidColor(refColor.copy(alpha = 0.15f)),
                            strokeWidth = 2.dp
                        )
                    )
                }
            }

            bottomReferenceLine?.let { refLine ->
                val refValue = refLine.value.toFloat()
                val refColor = parseHexColor(refLine.color)

                // Create horizontal line shading below the reference line
                val numLines = 50
                val yStep = (refValue - yAxisModel.range.start) / numLines

                for (i in 0 until numLines) {
                    val y = yAxisModel.range.start + (i * yStep)
                    LinePlot(
                        data = listOf(
                            Point(xAxisModel.range.start, y),
                            Point(xAxisModel.range.endInclusive, y)
                        ),
                        lineStyle = LineStyle(
                            brush = SolidColor(refColor.copy(alpha = 0.15f)),
                            strokeWidth = 2.dp
                        )
                    )
                }
            }

            // Draw reference lines (on top of shading, behind bars)
            topReferenceLine?.let { refLine ->
                val refValue = refLine.value.toFloat()
                val refColor = parseHexColor(refLine.color)

                LinePlot(
                    data = listOf(
                        Point(xAxisModel.range.start, refValue),
                        Point(xAxisModel.range.endInclusive, refValue)
                    ),
                    lineStyle = LineStyle(
                        brush = SolidColor(refColor),
                        strokeWidth = 2.dp
                    )
                )
            }

            bottomReferenceLine?.let { refLine ->
                val refValue = refLine.value.toFloat()
                val refColor = parseHexColor(refLine.color)

                LinePlot(
                    data = listOf(
                        Point(xAxisModel.range.start, refValue),
                        Point(xAxisModel.range.endInclusive, refValue)
                    ),
                    lineStyle = LineStyle(
                        brush = SolidColor(refColor),
                        strokeWidth = 2.dp
                    )
                )
            }

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
                bar = { seriesIndex, groupIndex, barEntry ->
                    val barColor = barsWithLayout.getOrNull(seriesIndex)?.color ?: Color.Magenta
                    verticalSolidBar<Float, Float>(color = barColor)(this, seriesIndex, groupIndex, barEntry)
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
                // Draw bar labels
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

            // Interactive legend for reference lines
            if (topReferenceLine != null || bottomReferenceLine != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topReferenceLine?.let { refLine ->
                        val refColor = parseHexColor(refLine.color)
                        val isSelected = selectedReferenceLines.isEmpty() || selectedReferenceLines.contains("top")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .clickable {
                                    selectedReferenceLines = if (selectedReferenceLines.contains("top")) {
                                        selectedReferenceLines - "top"
                                    } else {
                                        selectedReferenceLines + "top"
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Canvas(modifier = Modifier.size(16.dp, 3.dp)) {
                                drawRect(
                                    color = if (isSelected) refColor else refColor.copy(alpha = 0.3f)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = refLine.label,
                                fontSize = 11.sp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                }
                            )
                        }
                    }

                    bottomReferenceLine?.let { refLine ->
                        val refColor = parseHexColor(refLine.color)
                        val isSelected = selectedReferenceLines.isEmpty() || selectedReferenceLines.contains("bottom")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    selectedReferenceLines = if (selectedReferenceLines.contains("bottom")) {
                                        selectedReferenceLines - "bottom"
                                    } else {
                                        selectedReferenceLines + "bottom"
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Canvas(modifier = Modifier.size(16.dp, 3.dp)) {
                                drawRect(
                                    color = if (isSelected) refColor else refColor.copy(alpha = 0.3f)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = refLine.label,
                                fontSize = 11.sp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
