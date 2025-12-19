package com.joebad.fastbreak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.QuadrantConfig
import com.joebad.fastbreak.data.model.ScatterPlotDataPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Helper function for formatting floats in KMP common code
private fun Float.formatTo(decimals: Int): String {
    val multiplier = when (decimals) {
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
    val rounded = (this * multiplier).roundToInt() / multiplier
    return rounded.toString()
}

private fun Double.formatTo(decimals: Int): String {
    return this.toFloat().formatTo(decimals)
}

// Helper to parse hex color string to Compose Color
private fun parseHexColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    return Color(("FF$cleanHex").toLong(16))
}

@Composable
fun FourQuadrantScatterPlot(
    data: List<ScatterPlotDataPoint>,
    modifier: Modifier = Modifier,
    title: String = "4-Quadrant Scatter Plot",
    xAxisLabel: String = "X Axis",
    yAxisLabel: String = "Y Axis",
    invertYAxis: Boolean = false,
    quadrantTopRight: QuadrantConfig? = null,
    quadrantTopLeft: QuadrantConfig? = null,
    quadrantBottomLeft: QuadrantConfig? = null,
    quadrantBottomRight: QuadrantConfig? = null
) {
    // Resolve quadrant colors (use config or defaults)
    val topRightColor = quadrantTopRight?.let { parseHexColor(it.color) } ?: Color(0xFF4CAF50)
    val topLeftColor = quadrantTopLeft?.let { parseHexColor(it.color) } ?: Color(0xFF2196F3)
    val bottomLeftColor = quadrantBottomLeft?.let { parseHexColor(it.color) } ?: Color(0xFFFF9800)
    val bottomRightColor = quadrantBottomRight?.let { parseHexColor(it.color) } ?: Color(0xFFF44336)

    // Resolve quadrant labels (use config or defaults)
    val topRightLabel = quadrantTopRight?.label ?: "Elite"
    val topLeftLabel = quadrantTopLeft?.label ?: "Efficient"
    val bottomLeftLabel = quadrantBottomLeft?.label ?: "Struggling"
    val bottomRightLabel = quadrantBottomRight?.label ?: "Inefficient"

    // Zoom and pan state - start zoomed out for better overview
    var scale by remember { mutableStateOf(0.75f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // When invertYAxis is true, we multiply Y values by -1 for positioning
    // This keeps panning natural while visually inverting the axis
    val yMultiplier = if (invertYAxis) -1.0 else 1.0

    val (minX, maxX, minY, maxY) = remember(data, invertYAxis) {
        val minX = data.minOfOrNull { it.x } ?: 50.0
        val maxX = data.maxOfOrNull { it.x } ?: 100.0
        // Transform Y values for positioning
        val transformedYValues = data.map { it.y * yMultiplier }
        val minY = transformedYValues.minOrNull() ?: -0.2
        val maxY = transformedYValues.maxOrNull() ?: 0.4
        listOf(minX, maxX, minY, maxY)
    }

    // Calculate actual data averages (fixed, not based on visible range)
    // Use transformed Y values for positioning
    val (avgPFF, avgEPA) = remember(data, invertYAxis) {
        val avgX = data.map { it.x }.average()
        val avgY = data.map { it.y * yMultiplier }.average()
        Pair(avgX, avgY)
    }

    // Calculate linear regression (line of best fit) using transformed Y values
    val (slope, intercept) = remember(data, invertYAxis) {
        val n = data.size.toDouble()
        val sumX = data.sumOf { it.x }
        val sumY = data.sumOf { it.y * yMultiplier }
        val sumXY = data.sumOf { it.x * it.y * yMultiplier }
        val sumX2 = data.sumOf { it.x * it.x }

        // y = mx + b
        // slope (m) = (n*Σxy - Σx*Σy) / (n*Σx² - (Σx)²)
        // intercept (b) = (Σy - m*Σx) / n
        val m = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val b = (sumY - m * sumX) / n

        Pair(m, b)
    }

    // Only apply shading logic if there are more than 15 data points
    val useSelectiveHighlighting = data.size > 15

    // Calculate extreme players (outliers in each direction from the averages)
    // These will always show labels, others will be transparent
    val extremePlayerIndices = remember(data, avgPFF, avgEPA, yMultiplier, useSelectiveHighlighting) {
        if (!useSelectiveHighlighting) {
            // If <= 15 points, treat all as "extreme" (all visible with labels)
            return@remember data.indices.toSet()
        }

        val indexed = data.mapIndexed { index, point ->
            val transformedY = point.y * yMultiplier
            index to point.copy(y = transformedY)
        }

        val extremes = mutableSetOf<Int>()

        // Top 4 highest Y (best in Y metric)
        indexed.sortedByDescending { it.second.y }.take(4).forEach { extremes.add(it.first) }
        // Bottom 4 lowest Y (worst in Y metric)
        indexed.sortedBy { it.second.y }.take(4).forEach { extremes.add(it.first) }
        // Top 4 highest X (best in X metric)
        indexed.sortedByDescending { it.second.x }.take(4).forEach { extremes.add(it.first) }
        // Bottom 4 lowest X (worst in X metric)
        indexed.sortedBy { it.second.x }.take(4).forEach { extremes.add(it.first) }

        // Also include corner extremes (best/worst in both dimensions)
        // Top-right quadrant extremes (high X, high Y)
        indexed.filter { it.second.x > avgPFF && it.second.y > avgEPA }
            .sortedByDescending { it.second.x + it.second.y }
            .take(3).forEach { extremes.add(it.first) }
        // Bottom-left quadrant extremes (low X, low Y)
        indexed.filter { it.second.x < avgPFF && it.second.y < avgEPA }
            .sortedBy { it.second.x + it.second.y }
            .take(3).forEach { extremes.add(it.first) }

        // Include 1-2 players closest to the center (most average)
        indexed.sortedBy { (_, point) ->
            val dx = point.x - avgPFF
            val dy = point.y - avgEPA
            sqrt(dx * dx + dy * dy)
        }.take(2).forEach { extremes.add(it.first) }

        extremes
    }

    // Calculate which labels can be shown without overlapping
    // Recalculates when zoom changes - more labels fit when zoomed in
    // Returns: Pair(direction, canShowLabel) for each point
    // Direction: 0=right, 1=left, 2=above, 3=below, 4=above-right, 5=above-left, 6=below-right, 7=below-left
    val labelPlacements = remember(data, avgPFF, avgEPA, yMultiplier, extremePlayerIndices, scale, useSelectiveHighlighting) {
        // If not using selective highlighting (≤15 points), show all labels with simple positioning
        if (!useSelectiveHighlighting) {
            return@remember data.indices.map { index ->
                val point = data[index]
                val transformedY = point.y * yMultiplier
                val isRight = point.x >= avgPFF
                val isTop = transformedY >= avgEPA
                val direction = when {
                    isRight && isTop -> 1   // top-right: label left
                    !isRight && isTop -> 0  // top-left: label right
                    isRight && !isTop -> 1  // bottom-right: label left
                    else -> 0               // bottom-left: label right
                }
                direction to true // All can show
            }
        }

        // When zoomed out, only consider extreme points for labels
        // When zoomed in, allow non-extreme points too
        val zoomedOut = scale < 1.2f

        // Scale factor: labels take up less normalized space when zoomed in
        val zoomFactor = 1f / scale
        val xMin = data.minOfOrNull { it.x } ?: 0.0
        val xMax = data.maxOfOrNull { it.x } ?: 100.0
        val xRange = (xMax - xMin).coerceAtLeast(0.001)

        val yMin = data.minOfOrNull { it.y * yMultiplier } ?: 0.0
        val yMax = data.maxOfOrNull { it.y * yMultiplier } ?: 1.0
        val yRange = (yMax - yMin).coerceAtLeast(0.001)

        // Normalize coordinates to 0-1 range for consistent overlap detection
        data class NormalizedPoint(
            val index: Int,
            val nx: Double,
            val ny: Double,
            val labelLen: Int,
            val isExtreme: Boolean,
            val distFromEdge: Double // How far from the nearest edge (higher = more extreme)
        )

        val allPoints = data.mapIndexed { index, point ->
            val nx = (point.x - xMin) / xRange
            val ny = (point.y * yMultiplier - yMin) / yRange
            // Distance from nearest edge - points near edges are more "extreme"
            val distFromEdge = maxOf(
                maxOf(nx, 1.0 - nx), // horizontal distance from edge
                maxOf(ny, 1.0 - ny)  // vertical distance from edge
            )
            NormalizedPoint(index, nx, ny, point.label.length, extremePlayerIndices.contains(index), distFromEdge)
        }

        // Label sizes - make them larger when zoomed out to ensure proper collision detection
        val labelH = 0.045 * zoomFactor

        // Scale label width based on actual label length (approx 0.01 per character for mobile)
        fun labelWidth(labelLen: Int): Double = (labelLen * 0.01 * zoomFactor).coerceIn(0.06 * zoomFactor, 0.22 * zoomFactor)

        // For each direction, calculate the label rectangle
        fun labelRect(nx: Double, ny: Double, labelLen: Int, direction: Int): List<Double> {
            val labelW = labelWidth(labelLen)
            val gap = 0.018 * zoomFactor // Gap between dot and label
            val (ox, oy) = when (direction) {
                0 -> gap to -labelH / 2          // right
                1 -> -labelW - gap to -labelH / 2 // left
                2 -> -labelW / 2 to -labelH - gap // above
                3 -> -labelW / 2 to gap           // below
                4 -> gap to -labelH - gap         // above-right
                5 -> -labelW - gap to -labelH - gap // above-left
                6 -> gap to gap                   // below-right
                7 -> -labelW - gap to gap         // below-left
                else -> gap to -labelH / 2
            }
            return listOf(nx + ox, ny + oy, nx + ox + labelW, ny + oy + labelH)
        }

        fun rectsOverlap(r1: List<Double>, r2: List<Double>): Boolean {
            return !(r1[2] < r2[0] || r2[2] < r1[0] || r1[3] < r2[1] || r2[3] < r1[1])
        }

        fun getDirectionOrder(nx: Double, ny: Double): List<Int> {
            val isRight = nx >= 0.5
            val isTop = ny >= 0.5
            val nearRight = nx > 0.85
            val nearLeft = nx < 0.15
            val nearTop = ny > 0.9
            val nearBottom = ny < 0.1

            return when {
                nearRight && nearTop -> listOf(5, 1, 7, 2, 3, 0, 4, 6)
                nearRight && nearBottom -> listOf(5, 1, 4, 2, 3, 0, 7, 6)
                nearLeft && nearTop -> listOf(4, 0, 6, 2, 3, 1, 5, 7)
                nearLeft && nearBottom -> listOf(4, 0, 5, 2, 3, 1, 6, 7)
                nearRight -> listOf(1, 5, 7, 2, 3, 0, 4, 6)
                nearLeft -> listOf(0, 4, 6, 2, 3, 1, 5, 7)
                nearTop -> listOf(3, 6, 7, 0, 1, 2, 4, 5)
                nearBottom -> listOf(2, 4, 5, 0, 1, 3, 6, 7)
                isRight && isTop -> listOf(1, 5, 7, 2, 0, 3, 4, 6)
                !isRight && isTop -> listOf(0, 4, 6, 2, 1, 3, 5, 7)
                isRight && !isTop -> listOf(1, 5, 4, 2, 0, 3, 7, 6)
                else -> listOf(0, 4, 5, 2, 1, 3, 6, 7)
            }
        }

        // Greedy placement with smart collision detection
        val placedLabels = mutableListOf<Pair<Int, List<Double>>>()
        val results = mutableMapOf<Int, Pair<Int, Boolean>>() // index -> (direction, canShow)

        // Sort extreme points by how "extreme" they are (distance from edge)
        // Then sort non-extreme points by distance from center too
        val sortedPoints = allPoints.sortedWith(
            compareByDescending<NormalizedPoint> { it.isExtreme }
                .thenByDescending { it.distFromEdge }
                .thenByDescending {
                    val dx = it.nx - 0.5
                    val dy = it.ny - 0.5
                    dx * dx + dy * dy
                }
        )

        for (point in sortedPoints) {
            // When zoomed out, skip non-extreme points entirely
            if (zoomedOut && !point.isExtreme) {
                results[point.index] = 0 to false
                continue
            }

            val directionOrder = getDirectionOrder(point.nx, point.ny)
            var bestDirection = directionOrder[0]
            var foundNonOverlapping = false

            for (dir in directionOrder) {
                val rect = labelRect(point.nx, point.ny, point.labelLen, dir)
                val hasOverlap = placedLabels.any { (_, placedRect) -> rectsOverlap(rect, placedRect) }
                if (!hasOverlap) {
                    bestDirection = dir
                    foundNonOverlapping = true
                    break
                }
            }

            // Only show label if we found a non-overlapping position
            // Even extreme points won't show if they overlap with already-placed labels
            val canShow = foundNonOverlapping

            if (canShow) {
                val finalRect = labelRect(point.nx, point.ny, point.labelLen, bestDirection)
                placedLabels.add(point.index to finalRect)
            }

            results[point.index] = bestDirection to canShow
        }

        // Return placements for all data points
        data.indices.map { results[it] ?: (0 to false) }
    }

    val axisColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textMeasurer = rememberTextMeasurer()
    val labelTextStyle = TextStyle(
        color = axisColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 3f)

                        // Apply pan with limits based on initial data range
                        val tentativeOffsetX = offsetX + pan.x
                        val tentativeOffsetY = offsetY + pan.y

                        // Calculate how far the pan would take us
                        val baseXRange = maxX - minX
                        val baseYRange = maxY - minY
                        val centerX = (minX + maxX) / 2
                        val centerY = (minY + maxY) / 2

                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val leftPad = 80f
                        val rightPad = 40f
                        val topPad = 30f
                        val bottomPad = 60f

                        val zoomedXRange = baseXRange / scale
                        val zoomedYRange = baseYRange / scale

                        val panXOffset = -tentativeOffsetX / (canvasWidth - leftPad - rightPad) * zoomedXRange
                        val panYOffset = tentativeOffsetY / (canvasHeight - topPad - bottomPad) * zoomedYRange

                        val visibleMinX = (centerX - zoomedXRange / 2 + panXOffset).toFloat()
                        val visibleMaxX = (centerX + zoomedXRange / 2 + panXOffset).toFloat()
                        val visibleMinY = (centerY - zoomedYRange / 2 + panYOffset).toFloat()
                        val visibleMaxY = (centerY + zoomedYRange / 2 + panYOffset).toFloat()

                        // Limit panning: adjust based on zoom level (more relaxed when zoomed out)
                        val maxPanBeyond = 0.5f / scale
                        val allowedMinX = minX.toFloat() - baseXRange.toFloat() * maxPanBeyond
                        val allowedMaxX = maxX.toFloat() + baseXRange.toFloat() * maxPanBeyond
                        val allowedMinY = minY.toFloat() - baseYRange.toFloat() * maxPanBeyond
                        val allowedMaxY = maxY.toFloat() + baseYRange.toFloat() * maxPanBeyond

                        val xInBounds = visibleMinX >= allowedMinX && visibleMaxX <= allowedMaxX
                        val yInBounds = visibleMinY >= allowedMinY && visibleMaxY <= allowedMaxY

                        if (xInBounds) offsetX = tentativeOffsetX
                        if (yInBounds) offsetY = tentativeOffsetY
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val leftPadding = 80f
            val rightPadding = 40f
            val topPadding = 30f
            val bottomPadding = 60f

            // Calculate visible range based on zoom and pan
            val baseXRange = maxX - minX
            val baseYRange = maxY - minY

            val centerX = (minX + maxX) / 2
            val centerY = (minY + maxY) / 2

            // Adjust visible range based on zoom (centered zoom)
            val zoomedXRange = baseXRange / scale
            val zoomedYRange = baseYRange / scale

            // Apply pan offset
            val panXOffset = -offsetX / (width - leftPadding - rightPadding) * zoomedXRange
            val panYOffset = offsetY / (height - topPadding - bottomPadding) * zoomedYRange

            val visibleMinX = (centerX - zoomedXRange / 2 + panXOffset).toFloat()
            val visibleMaxX = (centerX + zoomedXRange / 2 + panXOffset).toFloat()
            val visibleMinY = (centerY - zoomedYRange / 2 + panYOffset).toFloat()
            val visibleMaxY = (centerY + zoomedYRange / 2 + panYOffset).toFloat()

            val adjustedXRange = visibleMaxX - visibleMinX
            val adjustedYRange = visibleMaxY - visibleMinY

            // Draw border frame
            drawRect(
                color = axisColor,
                topLeft = Offset(leftPadding, topPadding),
                size = androidx.compose.ui.geometry.Size(
                    width - leftPadding - rightPadding,
                    height - topPadding - bottomPadding
                ),
                style = Stroke(width = 2f)
            )

            // Calculate tick intervals
            fun calculateNiceTicks(range: Float, targetCount: Int = 8): List<Float> {
                val roughInterval = range / targetCount
                val magnitude = 10.0.pow(floor(log10(roughInterval.toDouble()))).toFloat()
                val niceInterval = when {
                    roughInterval / magnitude < 1.5f -> magnitude
                    roughInterval / magnitude < 3f -> 2 * magnitude
                    roughInterval / magnitude < 7f -> 5 * magnitude
                    else -> 10 * magnitude
                }

                val start = ceil(visibleMinX / niceInterval) * niceInterval
                val ticks = mutableListOf<Float>()
                var tick = start
                while (tick <= visibleMaxX) {
                    ticks.add(tick)
                    tick += niceInterval
                }
                return ticks
            }

            fun calculateNiceTicksY(range: Float, targetCount: Int = 8): List<Float> {
                val roughInterval = range / targetCount
                val magnitude = 10.0.pow(floor(log10(roughInterval.toDouble()))).toFloat()
                val niceInterval = when {
                    roughInterval / magnitude < 1.5f -> magnitude
                    roughInterval / magnitude < 3f -> 2 * magnitude
                    roughInterval / magnitude < 7f -> 5 * magnitude
                    else -> 10 * magnitude
                }

                val start = ceil(visibleMinY / niceInterval) * niceInterval
                val ticks = mutableListOf<Float>()
                var tick = start
                while (tick <= visibleMaxY) {
                    ticks.add(tick)
                    tick += niceInterval
                }
                return ticks
            }

            // Draw X-axis ticks and labels
            val xTicks = calculateNiceTicks(adjustedXRange)
            xTicks.forEach { tickValue ->
                val x = leftPadding + (width - leftPadding - rightPadding) * (tickValue - visibleMinX) / adjustedXRange

                // Draw tick mark
                drawLine(
                    color = axisColor,
                    start = Offset(x, height - bottomPadding),
                    end = Offset(x, height - bottomPadding + 5),
                    strokeWidth = 2f
                )

                // Draw grid line
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = Offset(x, topPadding),
                    end = Offset(x, height - bottomPadding),
                    strokeWidth = 1f
                )

                // Draw tick label
                val tickLabel = tickValue.formatTo(1)
                val measured = textMeasurer.measure(tickLabel, labelTextStyle)
                drawText(
                    textMeasurer,
                    tickLabel,
                    topLeft = Offset(x - measured.size.width / 2, height - bottomPadding + 10),
                    style = labelTextStyle
                )
            }

            // Draw Y-axis ticks and labels
            val yTicks = calculateNiceTicksY(adjustedYRange)
            yTicks.forEach { tickValue ->
                // Normal Y coordinate calculation (higher values at top)
                val y = height - bottomPadding - (height - topPadding - bottomPadding) * (tickValue - visibleMinY) / adjustedYRange

                // Draw tick mark
                drawLine(
                    color = axisColor,
                    start = Offset(leftPadding - 5, y),
                    end = Offset(leftPadding, y),
                    strokeWidth = 2f
                )

                // Draw grid line
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = Offset(leftPadding, y),
                    end = Offset(width - rightPadding, y),
                    strokeWidth = 1f
                )

                // Draw tick label - show original value (multiply by -1 when inverted)
                val displayValue = if (invertYAxis) -tickValue else tickValue
                val tickLabel = displayValue.formatTo(2)
                val measured = textMeasurer.measure(tickLabel, labelTextStyle)
                drawText(
                    textMeasurer,
                    tickLabel,
                    topLeft = Offset(leftPadding - measured.size.width - 8, y - measured.size.height / 2),
                    style = labelTextStyle
                )
            }

            // Draw quadrant divider lines (based on data averages)
            // These lines move as you pan/zoom

            // Vertical line at average PFF - only draw if in visible range
            if (avgPFF.toFloat() >= visibleMinX && avgPFF.toFloat() <= visibleMaxX) {
                val avgX = leftPadding + (width - leftPadding - rightPadding) * (avgPFF.toFloat() - visibleMinX) / adjustedXRange
                drawLine(
                    color = Color(0xFF9C27B0).copy(alpha = 0.6f), // Purple for better visibility
                    start = Offset(avgX, topPadding),
                    end = Offset(avgX, height - bottomPadding),
                    strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
                )

                // Label for the average PFF line
                val avgLabel = "Avg: ${avgPFF.formatTo(1)}"
                val measured = textMeasurer.measure(avgLabel, labelTextStyle.copy(fontSize = 10.sp))
                drawText(
                    textMeasurer,
                    avgLabel,
                    topLeft = Offset(avgX - measured.size.width / 2, topPadding + 5),
                    style = labelTextStyle.copy(fontSize = 10.sp, color = Color(0xFF9C27B0))
                )
            }

            // Horizontal line at average EPA - only draw if in visible range
            if (avgEPA.toFloat() >= visibleMinY && avgEPA.toFloat() <= visibleMaxY) {
                val avgY = height - bottomPadding - (height - topPadding - bottomPadding) * (avgEPA.toFloat() - visibleMinY) / adjustedYRange
                drawLine(
                    color = Color(0xFF9C27B0).copy(alpha = 0.6f), // Purple for better visibility
                    start = Offset(leftPadding, avgY),
                    end = Offset(width - rightPadding, avgY),
                    strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
                )

                // Label for the average EPA line - show original value
                val displayAvgEPA = if (invertYAxis) -avgEPA else avgEPA
                val avgLabel = "Avg: ${displayAvgEPA.formatTo(2)}"
                val measured = textMeasurer.measure(avgLabel, labelTextStyle.copy(fontSize = 10.sp))
                drawText(
                    textMeasurer,
                    avgLabel,
                    topLeft = Offset(leftPadding + 10f, avgY + 5f),
                    style = labelTextStyle.copy(fontSize = 10.sp, color = Color(0xFF9C27B0))
                )
            }

            // Draw trend line and data points with clipping to keep within graph bounds
            clipRect(
                left = leftPadding,
                top = topPadding,
                right = width - rightPadding,
                bottom = height - bottomPadding
            ) {
                // Draw trend line (line of best fit)
                // Calculate y-values at the visible x-range boundaries
                val trendYStart = slope * visibleMinX + intercept
                val trendYEnd = slope * visibleMaxX + intercept

                // Convert to screen coordinates (normal positioning)
                val trendX1 = leftPadding
                val trendY1 = height - bottomPadding - (height - topPadding - bottomPadding) * (trendYStart.toFloat() - visibleMinY) / adjustedYRange
                val trendX2 = width - rightPadding
                val trendY2 = height - bottomPadding - (height - topPadding - bottomPadding) * (trendYEnd.toFloat() - visibleMinY) / adjustedYRange

                // Draw the trend line
                drawLine(
                    color = Color(0xFFFF6F00).copy(alpha = 0.7f), // Deep orange for visibility
                    start = Offset(trendX1, trendY1),
                    end = Offset(trendX2, trendY2),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            }

            // Draw axis labels
            val xAxisLabelMeasured = textMeasurer.measure(xAxisLabel, labelTextStyle.copy(fontWeight = FontWeight.Bold))
            drawText(
                textMeasurer,
                xAxisLabel,
                topLeft = Offset((width - xAxisLabelMeasured.size.width) / 2, height - bottomPadding + 30),
                style = labelTextStyle.copy(fontWeight = FontWeight.Bold)
            )

            val yAxisLabelMeasured = textMeasurer.measure(yAxisLabel, labelTextStyle.copy(fontWeight = FontWeight.Bold))
            withTransform({
                rotate(-90f, pivot = Offset(yAxisLabelMeasured.size.height / 2f, height / 2f))
            }) {
                drawText(
                    textMeasurer,
                    yAxisLabel,
                    topLeft = Offset(
                        -yAxisLabelMeasured.size.width / 2f + yAxisLabelMeasured.size.height / 2f,
                        height / 2f - yAxisLabelMeasured.size.height / 2f
                    ),
                    style = labelTextStyle.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Plot data points with labels (with clipping)
            clipRect(
                left = leftPadding,
                top = topPadding,
                right = width - rightPadding,
                bottom = height - bottomPadding
            ) {
                // First pass: draw points that won't show labels (transparent dots)
                data.forEachIndexed { index, point ->
                    val (_, canShowLabel) = labelPlacements.getOrElse(index) { 0 to false }
                    if (canShowLabel) return@forEachIndexed // Skip, will draw in second pass

                    val x = leftPadding + (width - leftPadding - rightPadding) * (point.x.toFloat() - visibleMinX) / adjustedXRange
                    val transformedY = (point.y * yMultiplier).toFloat()
                    val y = height - bottomPadding - (height - topPadding - bottomPadding) * (transformedY - visibleMinY) / adjustedYRange

                    if (x >= leftPadding && x <= width - rightPadding && y >= topPadding && y <= height - bottomPadding) {
                        val pointColor = when {
                            point.x >= avgPFF && transformedY >= avgEPA -> topRightColor
                            point.x < avgPFF && transformedY >= avgEPA -> topLeftColor
                            point.x < avgPFF && transformedY < avgEPA -> bottomLeftColor
                            else -> bottomRightColor
                        }

                        // Draw transparent dot for points without labels
                        drawCircle(
                            color = pointColor.copy(alpha = 0.3f),
                            radius = 6f,
                            center = Offset(x, y)
                        )
                    }
                }

                // Second pass: draw points that can show labels (full opacity with labels)
                data.forEachIndexed { index, point ->
                    val (direction, canShowLabel) = labelPlacements.getOrElse(index) { 0 to false }
                    if (!canShowLabel) return@forEachIndexed // Already drawn in first pass

                    val x = leftPadding + (width - leftPadding - rightPadding) * (point.x.toFloat() - visibleMinX) / adjustedXRange
                    val transformedY = (point.y * yMultiplier).toFloat()
                    val y = height - bottomPadding - (height - topPadding - bottomPadding) * (transformedY - visibleMinY) / adjustedYRange

                    if (x >= leftPadding && x <= width - rightPadding && y >= topPadding && y <= height - bottomPadding) {
                        val pointColor = when {
                            point.x >= avgPFF && transformedY >= avgEPA -> topRightColor
                            point.x < avgPFF && transformedY >= avgEPA -> topLeftColor
                            point.x < avgPFF && transformedY < avgEPA -> bottomLeftColor
                            else -> bottomRightColor
                        }

                        // Draw point with full opacity
                        drawCircle(
                            color = pointColor,
                            radius = 7f,
                            center = Offset(x, y)
                        )

                        drawCircle(
                            color = axisColor,
                            radius = 7f,
                            center = Offset(x, y),
                            style = Stroke(width = 1.5f)
                        )

                        // Draw label with collision-free positioning
                        val labelMeasured = textMeasurer.measure(point.label, labelTextStyle)
                        val labelWidth = labelMeasured.size.width.toFloat()
                        val labelHeight = labelMeasured.size.height.toFloat()

                        val labelOffset = when (direction) {
                            0 -> Offset(10f, -labelHeight / 2)                    // right
                            1 -> Offset(-labelWidth - 10f, -labelHeight / 2)      // left
                            2 -> Offset(-labelWidth / 2, -labelHeight - 5f)       // above
                            3 -> Offset(-labelWidth / 2, 10f)                     // below
                            4 -> Offset(10f, -labelHeight - 5f)                   // above-right
                            5 -> Offset(-labelWidth - 10f, -labelHeight - 5f)     // above-left
                            6 -> Offset(10f, 10f)                                 // below-right
                            7 -> Offset(-labelWidth - 10f, 10f)                   // below-left
                            else -> Offset(10f, -labelHeight / 2)
                        }

                        drawText(
                            textMeasurer,
                            point.label,
                            topLeft = Offset(x + labelOffset.x, y + labelOffset.y),
                            style = labelTextStyle
                        )
                    }
                }
            }
        }

        // Info text
        Text(
            text = "Pinch to zoom • Drag to pan",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )

        // Quadrant legend - uses FlowRow to wrap to multiple lines if needed
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QuadrantLabel(topRightLabel, topRightColor)
            QuadrantLabel(topLeftLabel, topLeftColor)
            QuadrantLabel(bottomLeftLabel, bottomLeftColor)
            QuadrantLabel(bottomRightLabel, bottomRightColor)
        }
    }
}

@Composable
private fun QuadrantLabel(text: String, color: Color) {
    // Dot and label grouped together so they stay on the same line
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}
