package com.joebad.fastbreak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.QuadrantConfig
import com.joebad.fastbreak.data.model.ScatterPlotDataPoint
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.platform.addTitleToBitmap
import kotlinx.coroutines.launch
import io.github.koalaplot.core.Symbol
import io.github.koalaplot.core.gestures.GestureConfig
import io.github.koalaplot.core.line.LinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.util.VerticalRotation
import io.github.koalaplot.core.util.rotateVertically
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.TickPosition
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.XYGraphScope
import io.github.koalaplot.core.xygraph.rememberAxisStyle
import kotlin.math.roundToInt

private fun parseHexColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    return Color(("FF$cleanHex").toLong(16))
}

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

// Data class for regression line parameters
private data class RegressionLine(
    val slope: Float,
    val intercept: Float
)

// Calculate linear regression using least squares
private fun calculateRegression(points: List<Pair<Float, Float>>): RegressionLine? {
    if (points.size < 2) return null

    val n = points.size
    val sumX = points.sumOf { it.first.toDouble() }
    val sumY = points.sumOf { it.second.toDouble() }
    val sumXY = points.sumOf { it.first.toDouble() * it.second.toDouble() }
    val sumX2 = points.sumOf { it.first.toDouble() * it.first.toDouble() }

    val denominator = n * sumX2 - sumX * sumX
    if (denominator == 0.0) return null

    val slope = (n * sumXY - sumX * sumY) / denominator
    val intercept = (sumY - slope * sumX) / n

    return RegressionLine(slope.toFloat(), intercept.toFloat())
}

// Data class for label positioning with collision detection
private data class LabelPosition(
    val label: String,
    val x: Float,
    val y: Float,
    val color: Color,
    val offsetY: Float = -14f // Default offset above point
)

// Smart label placement that tries different positions to avoid overlapping with dots
private fun placeLabelsSmartly(
    labels: List<LabelPosition>,
    allPoints: List<Pair<Float, Float>>, // All data point positions
    xRange: Float,
    yRange: Float
): List<LabelPosition> {
    // Label dimensions as fraction of range
    val labelWidthFraction = 0.04f
    val labelHeightFraction = 0.025f

    val labelWidth = xRange * labelWidthFraction
    val labelHeight = yRange * labelHeightFraction

    // Possible label offset positions (in dp, converted to fraction of range)
    // Try above, below, left, and right of the point
    val offsetPositions = listOf(
        0f to -14f,    // Above (default)
        0f to 14f,     // Below
        -12f to -7f,   // Upper left
        12f to -7f,    // Upper right
        -12f to 7f,    // Lower left
        12f to 7f      // Lower right
    )

    val placedLabels = mutableListOf<LabelPosition>()

    // Sort by distance from center (prioritize outliers)
    val avgX = labels.map { it.x }.average().toFloat()
    val avgY = labels.map { it.y }.average().toFloat()

    val sortedLabels = labels.sortedByDescending { label ->
        val dx = (label.x - avgX) / xRange
        val dy = (label.y - avgY) / yRange
        dx * dx + dy * dy
    }

    for (labelPos in sortedLabels) {
        var bestPosition: LabelPosition? = null
        var minCollisions = Int.MAX_VALUE

        // Try each offset position
        for ((offsetXDp, offsetYDp) in offsetPositions) {
            // Convert dp offsets to data coordinates (rough approximation)
            val offsetX = (offsetXDp / 400f) * xRange // Assuming ~400dp chart width
            val offsetY = (offsetYDp / 400f) * yRange

            val labelCenterX = labelPos.x + offsetX
            val labelCenterY = labelPos.y + offsetY

            // Count collisions with other data points
            var collisionCount = 0

            // Check collision with all data points (except its own)
            for ((pointX, pointY) in allPoints) {
                if (pointX == labelPos.x && pointY == labelPos.y) continue // Skip own point

                // Check if point is within label bounds
                val xDist = kotlin.math.abs(pointX - labelCenterX)
                val yDist = kotlin.math.abs(pointY - labelCenterY)

                if (xDist < labelWidth / 2 && yDist < labelHeight / 2) {
                    collisionCount++
                }
            }

            // Check collision with already placed labels
            for (placed in placedLabels) {
                val placedCenterX = placed.x + ((placed.offsetY / 400f) * xRange)
                val placedCenterY = placed.y + ((placed.offsetY / 400f) * yRange)

                val xDist = kotlin.math.abs(labelCenterX - placedCenterX)
                val yDist = kotlin.math.abs(labelCenterY - placedCenterY)

                if (xDist < labelWidth && yDist < labelHeight) {
                    collisionCount += 5 // Heavily penalize label-label collisions
                }
            }

            // Keep track of best position
            if (collisionCount < minCollisions) {
                minCollisions = collisionCount
                bestPosition = labelPos.copy(offsetY = offsetYDp)
            }

            // If we found a collision-free position, use it
            if (collisionCount == 0) break
        }

        // Add the label with its best position
        if (bestPosition != null) {
            placedLabels.add(bestPosition)
        }
    }

    return placedLabels
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun QuadrantScatterPlot(
    data: List<ScatterPlotDataPoint>,
    modifier: Modifier = Modifier,
    title: String = "4-Quadrant Scatter Plot",
    xAxisLabel: String = "X Axis",
    yAxisLabel: String = "Y Axis",
    invertYAxis: Boolean = false,
    quadrantTopRight: QuadrantConfig? = null,
    quadrantTopLeft: QuadrantConfig? = null,
    quadrantBottomLeft: QuadrantConfig? = null,
    quadrantBottomRight: QuadrantConfig? = null,
    subject: String? = null,
    highlightedTeamCodes: Set<String> = emptySet(),
    highlightedPlayerLabels: Set<String> = emptySet(),
    showShareButton: Boolean = false,
    onShareClick: ((()-> Unit) -> Unit)? = null,
    source: String = ""
) {
    // Use MaterialTheme colorScheme to detect app theme (not system theme)
    val isDark = MaterialTheme.colorScheme.background == Color.Black ||
                 MaterialTheme.colorScheme.background == Color(0xFF0A0A0A)

    // Use theme background for both display and capture (no more white background for capture)
    val backgroundColor = MaterialTheme.colorScheme.background

    // Create graphics layer for capturing the chart
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    val imageExporter = remember { getImageExporter() }
    var isCapturing by remember { mutableStateOf(false) }

    // Text colors: use theme color always
    val textColor = MaterialTheme.colorScheme.onBackground

    // Debug logging for quadrant configs
    println("======================================")
    println("KoalaScatterPlot - THEME CHECK")
    println("KoalaScatterPlot - Background color: ${MaterialTheme.colorScheme.background}")
    println("KoalaScatterPlot - isDark (computed from background): $isDark")
    println("KoalaScatterPlot - quadrantBottomRight: color=${quadrantBottomRight?.color}, lightModeColor=${quadrantBottomRight?.lightModeColor}")
    println("======================================")

    // Quadrant colors - use lightModeColor if in light mode and provided
    // Don't use remember() here so colors update immediately when theme changes
    val topRightColor = quadrantTopRight?.let {
        if (!isDark && it.lightModeColor != null) {
            println("KoalaScatterPlot - Using lightModeColor for topRight: ${it.lightModeColor}")
            parseHexColor(it.lightModeColor)
        } else {
            println("KoalaScatterPlot - Using regular color for topRight: ${it.color}")
            parseHexColor(it.color)
        }
    } ?: Color(0xFF4CAF50)

    val topLeftColor = quadrantTopLeft?.let {
        if (!isDark && it.lightModeColor != null) {
            println("KoalaScatterPlot - Using lightModeColor for topLeft: ${it.lightModeColor}")
            parseHexColor(it.lightModeColor)
        } else {
            println("KoalaScatterPlot - Using regular color for topLeft: ${it.color}")
            parseHexColor(it.color)
        }
    } ?: Color(0xFF2196F3)

    val bottomLeftColor = quadrantBottomLeft?.let {
        if (!isDark && it.lightModeColor != null) {
            println("KoalaScatterPlot - Using lightModeColor for bottomLeft: ${it.lightModeColor}")
            parseHexColor(it.lightModeColor)
        } else {
            println("KoalaScatterPlot - Using regular color for bottomLeft: ${it.color}")
            parseHexColor(it.color)
        }
    } ?: Color(0xFFFF9800)

    val bottomRightColor = quadrantBottomRight?.let {
        if (!isDark && it.lightModeColor != null) {
            println("KoalaScatterPlot - Using lightModeColor for bottomRight: ${it.lightModeColor}")
            parseHexColor(it.lightModeColor)
        } else {
            println("KoalaScatterPlot - Using regular color for bottomRight: ${it.color}")
            parseHexColor(it.color)
        }
    } ?: Color(0xFFF44336)

    println("KoalaScatterPlot - bottomRightColor result: $bottomRightColor")

    // Quadrant labels
    val topRightLabel = quadrantTopRight?.label ?: "Elite"
    val topLeftLabel = quadrantTopLeft?.label ?: "Efficient"
    val bottomLeftLabel = quadrantBottomLeft?.label ?: "Struggling"
    val bottomRightLabel = quadrantBottomRight?.label ?: "Inefficient"

    // Y multiplier for axis inversion
    val yMultiplier = if (invertYAxis) -1.0 else 1.0

    // Calculate bounds with padding
    data class PlotBounds(
        val xMin: Float,
        val xMax: Float,
        val yMin: Float,
        val yMax: Float,
        val avgX: Float,
        val avgY: Float
    )

    val bounds = remember(data, invertYAxis) {
        val xValues = data.map { it.x }
        val yValues = data.map { it.y * yMultiplier }

        val minX = xValues.minOrNull() ?: 0.0
        val maxX = xValues.maxOrNull() ?: 100.0
        val minY = yValues.minOrNull() ?: -0.5
        val maxY = yValues.maxOrNull() ?: 0.5

        val xPadding = (maxX - minX) * 0.1
        val yPadding = (maxY - minY) * 0.1

        val avgX = xValues.average()
        val avgY = yValues.average()

        PlotBounds(
            xMin = (minX - xPadding).toFloat(),
            xMax = (maxX + xPadding).toFloat(),
            yMin = (minY - yPadding).toFloat(),
            yMax = (maxY + yPadding).toFloat(),
            avgX = avgX.toFloat(),
            avgY = avgY.toFloat()
        )
    }

    val xMin = bounds.xMin
    val xMax = bounds.xMax
    val yMin = bounds.yMin
    val yMax = bounds.yMax
    val avgX = bounds.avgX
    val avgY = bounds.avgY

    // Calculate regression line
    val regression = remember(data, yMultiplier) {
        val points = data.map { it.x.toFloat() to (it.y * yMultiplier).toFloat() }
        calculateRegression(points)
    }

    // Group data points by quadrant and prepare labels
    data class ColoredPoint(
        val point: DefaultPoint<Float, Float>,
        val color: Color
    )

    data class QuadrantData(
        val topRight: List<ColoredPoint>,
        val topLeft: List<ColoredPoint>,
        val bottomLeft: List<ColoredPoint>,
        val bottomRight: List<ColoredPoint>,
        val labels: List<LabelPosition>
    )

    val quadrantData = remember(data, avgX, avgY, yMultiplier, topRightColor, topLeftColor, bottomLeftColor, bottomRightColor, subject, highlightedTeamCodes, highlightedPlayerLabels) {
        println("🎨 KoalaScatterPlot - Subject: $subject, Highlighted Team Codes: $highlightedTeamCodes, Highlighted Player Labels: $highlightedPlayerLabels, Data Points: ${data.size}")

        val trNormal = mutableListOf<ColoredPoint>()
        val tlNormal = mutableListOf<ColoredPoint>()
        val blNormal = mutableListOf<ColoredPoint>()
        val brNormal = mutableListOf<ColoredPoint>()
        val trHighlighted = mutableListOf<ColoredPoint>()
        val tlHighlighted = mutableListOf<ColoredPoint>()
        val blHighlighted = mutableListOf<ColoredPoint>()
        val brHighlighted = mutableListOf<ColoredPoint>()
        val allLabels = mutableListOf<LabelPosition>()

        // Check if highlighting is active (either team codes or player labels)
        val isHighlighting = highlightedTeamCodes.isNotEmpty() || highlightedPlayerLabels.isNotEmpty()

        println("🎯 KoalaScatterPlot - Subject: $subject")
        println("🎯 KoalaScatterPlot - highlightedTeamCodes: $highlightedTeamCodes")
        println("🎯 KoalaScatterPlot - highlightedPlayerLabels: $highlightedPlayerLabels")
        println("🎯 KoalaScatterPlot - isHighlighting: $isHighlighting")
        println("🎯 KoalaScatterPlot - First 3 data point labels: ${data.take(3).map { it.label }}")

        var highlightedCount = 0
        data.forEach { point ->
            val x = point.x.toFloat()
            val y = (point.y * yMultiplier).toFloat()
            val dp = DefaultPoint(x, y)

            // Check if this point should be highlighted (using OR logic - match ANY criteria)
            val matchesPlayerLabel = highlightedPlayerLabels.isNotEmpty() && highlightedPlayerLabels.contains(point.label)
            val matchesTeamCode = when {
                subject == "TEAM" && highlightedTeamCodes.isNotEmpty() -> {
                    highlightedTeamCodes.any { code -> point.label.contains(code, ignoreCase = true) }
                }
                highlightedTeamCodes.isNotEmpty() -> {
                    highlightedTeamCodes.contains(point.teamCode)
                }
                else -> false
            }

            val isHighlighted = matchesPlayerLabel || matchesTeamCode

            if (isHighlighted) {
                highlightedCount++
                if (matchesPlayerLabel) {
                    println("🎯 MATCH! Point label '${point.label}' is in highlightedPlayerLabels")
                }
                if (matchesTeamCode) {
                    println("🎯 MATCH! Point teamCode '${point.teamCode}' matches team filter")
                }
            }

            // Use custom color from data point if provided, otherwise use quadrant color
            val baseColor = if (point.color != null) {
                parseHexColor(point.color) ?: when {
                    x >= avgX && y >= avgY -> topRightColor
                    x < avgX && y >= avgY -> topLeftColor
                    x < avgX && y < avgY -> bottomLeftColor
                    else -> bottomRightColor
                }
            } else {
                when {
                    x >= avgX && y >= avgY -> topRightColor
                    x < avgX && y >= avgY -> topLeftColor
                    x < avgX && y < avgY -> bottomLeftColor
                    else -> bottomRightColor
                }
            }

            // Apply transparency if highlighting is active and this point is not highlighted
            val color = if (isHighlighting && !isHighlighted) {
                baseColor.copy(alpha = 0.1f)
            } else {
                baseColor
            }

            // Add colored point to appropriate quadrant (separate lists for highlighted vs normal)
            val coloredPoint = ColoredPoint(dp, color)
            when {
                x >= avgX && y >= avgY -> if (isHighlighted) trHighlighted.add(coloredPoint) else trNormal.add(coloredPoint)
                x < avgX && y >= avgY -> if (isHighlighted) tlHighlighted.add(coloredPoint) else tlNormal.add(coloredPoint)
                x < avgX && y < avgY -> if (isHighlighted) blHighlighted.add(coloredPoint) else blNormal.add(coloredPoint)
                else -> if (isHighlighted) brHighlighted.add(coloredPoint) else brNormal.add(coloredPoint)
            }

            allLabels.add(LabelPosition(point.label, x, y, color))
        }

        println("🎯 KoalaScatterPlot - Total points highlighted: $highlightedCount out of ${data.size}")

        // Combine lists with normal points first, highlighted points last (so they render on top)
        QuadrantData(
            topRight = trNormal + trHighlighted,
            topLeft = tlNormal + tlHighlighted,
            bottomLeft = blNormal + blHighlighted,
            bottomRight = brNormal + brHighlighted,
            labels = allLabels
        )
    }

    val topRightPoints = quadrantData.topRight
    val topLeftPoints = quadrantData.topLeft
    val bottomLeftPoints = quadrantData.bottomLeft
    val bottomRightPoints = quadrantData.bottomRight

    // Track initial ranges for zoom calculation
    val initialXRange = remember(xMin, xMax) { xMax - xMin }
    val initialYRange = remember(yMin, yMax) { yMax - yMin }

    // Create axis models with zoom/pan support
    val xAxisModel = remember(xMin, xMax) {
        val rangeSize = xMax - xMin
        FloatLinearAxisModel(
            range = xMin..xMax,
            minViewExtent = rangeSize * 0.1f, // Allow zooming in to 10% of full range
            maxViewExtent = rangeSize // Full range when zoomed out
        )
    }

    val yAxisModel = remember(yMin, yMax) {
        val rangeSize = yMax - yMin
        FloatLinearAxisModel(
            range = yMin..yMax,
            minViewExtent = rangeSize * 0.1f, // Allow zooming in to 10% of full range
            maxViewExtent = rangeSize // Full range when zoomed out
        )
    }

    // Use smart label placement to avoid overlapping with dots
    val visibleLabels = remember(quadrantData.labels, data, yMultiplier) {
        // Collect all data point positions for collision detection
        val allPoints = data.map { it.x.toFloat() to (it.y * yMultiplier).toFloat() }

        placeLabelsSmartly(
            labels = quadrantData.labels,
            allPoints = allPoints,
            xRange = xMax - xMin,
            yRange = yMax - yMin
        )
    }

    // Set up share callback if provided
    LaunchedEffect(Unit) {
        onShareClick?.invoke {
            coroutineScope.launch {
                try {
                    isCapturing = true
                    val chartBitmap = graphicsLayer.toImageBitmap()
                    // Add title programmatically to the bitmap with theme info
                    // Convert Compose Color to ARGB Int
                    val textColorInt = (textColor.alpha * 255).toInt() shl 24 or
                                      ((textColor.red * 255).toInt() shl 16) or
                                      ((textColor.green * 255).toInt() shl 8) or
                                      (textColor.blue * 255).toInt()
                    val bitmapWithTitle = addTitleToBitmap(chartBitmap, title, isDark, textColorInt, source)
                    imageExporter.shareImage(bitmapWithTitle, title)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isCapturing = false
                }
            }
        }
    }

    Box(modifier = modifier) {
        // Wrap chart and legend in a Box with graphics layer for capture
        Box(
            modifier = Modifier
                .background(backgroundColor)
                .drawWithCache {
                    // Record the content into the graphics layer
                    onDrawWithContent {
                        // Draw content
                        drawContent()
                        // Record to graphics layer for captures
                        graphicsLayer.record {
                            this@onDrawWithContent.drawContent()
                        }
                    }
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Chart
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
                color = textColor,
                tickPosition = TickPosition.Outside,
                labelRotation = 0
            ),
            yAxisStyle = rememberAxisStyle(
                color = textColor,
                tickPosition = TickPosition.Outside,
                labelRotation = 0
            ),
            xAxisLabels = @Composable { value: Float ->
                Text(
                    text = formatToTenth(value),
                    fontSize = 10.sp,
                    color = textColor
                )
            },
            yAxisLabels = @Composable { value: Float ->
                // When invertYAxis is true, display the original (non-inverted) values
                val displayValue = if (invertYAxis) value * -1f else value
                Text(
                    text = formatToTenth(displayValue),
                    fontSize = 10.sp,
                    color = textColor
                )
            },
            xAxisTitle = @Composable {
                Text(
                    text = xAxisLabel,
                    fontSize = 11.sp,
                    color = textColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
            },
            yAxisTitle = @Composable {
                Text(
                    text = yAxisLabel,
                    fontSize = 11.sp,
                    color = textColor,
                    modifier = Modifier
                        .rotateVertically(VerticalRotation.COUNTER_CLOCKWISE)
                        .padding(end = 4.dp)
                )
            },
            horizontalMajorGridLineStyle = LineStyle(
                brush = SolidColor(Color.Gray.copy(alpha = 0.3f)), // Gray grid lines for white background
                strokeWidth = 1.dp
            ),
            horizontalMinorGridLineStyle = null,
            verticalMajorGridLineStyle = LineStyle(
                brush = SolidColor(Color.Gray.copy(alpha = 0.3f)), // Gray grid lines for white background
                strokeWidth = 1.dp
            ),
            verticalMinorGridLineStyle = null,
            modifier = Modifier
                .semantics { contentDescription = "chart" }
                .fillMaxWidth()
                .height(400.dp)
        ) {
            // Draw quadrant backgrounds first (behind everything)
            // Top Right quadrant (x >= avgX, y >= avgY)
            QuadrantBackground(
                xMin = avgX,
                xMax = xMax,
                yMin = avgY,
                yMax = yMax,
                color = topRightColor.copy(alpha = 0.15f)
            )

            // Top Left quadrant (x < avgX, y >= avgY)
            QuadrantBackground(
                xMin = xMin,
                xMax = avgX,
                yMin = avgY,
                yMax = yMax,
                color = topLeftColor.copy(alpha = 0.15f)
            )

            // Bottom Left quadrant (x < avgX, y < avgY)
            QuadrantBackground(
                xMin = xMin,
                xMax = avgX,
                yMin = yMin,
                yMax = avgY,
                color = bottomLeftColor.copy(alpha = 0.15f)
            )

            // Bottom Right quadrant (x >= avgX, y < avgY)
            QuadrantBackground(
                xMin = avgX,
                xMax = xMax,
                yMin = yMin,
                yMax = avgY,
                color = bottomRightColor.copy(alpha = 0.15f)
            )

            // Draw regression line (on top of backgrounds)
            if (regression != null) {
                val regY1 = regression.slope * xMin + regression.intercept
                val regY2 = regression.slope * xMax + regression.intercept
                LinePlot(
                    data = listOf(DefaultPoint(xMin, regY1), DefaultPoint(xMax, regY2)),
                    lineStyle = LineStyle(
                        brush = SolidColor(Color(0xFF607D8B).copy(alpha = 0.7f)),
                        strokeWidth = 2.dp
                    )
                )
            }

            // Draw quadrant divider lines at averages
            // Vertical line at avgX
            LinePlot(
                data = listOf(DefaultPoint(avgX, yMin), DefaultPoint(avgX, yMax)),
                lineStyle = LineStyle(
                    brush = SolidColor(Color(0xFF9C27B0).copy(alpha = 0.6f)),
                    strokeWidth = 2.dp,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
                )
            )

            // Horizontal line at avgY
            LinePlot(
                data = listOf(DefaultPoint(xMin, avgY), DefaultPoint(xMax, avgY)),
                lineStyle = LineStyle(
                    brush = SolidColor(Color(0xFF9C27B0).copy(alpha = 0.6f)),
                    strokeWidth = 2.dp,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f))
                )
            )

            // Draw labels first so they appear behind the dots
            visibleLabels.forEach { labelPos ->
                PointLabel(
                    x = labelPos.x,
                    y = labelPos.y,
                    label = labelPos.label,
                    color = labelPos.color,
                    offsetY = labelPos.offsetY,
                    backgroundColor = backgroundColor
                )
            }

            // Plot each point individually with its own color (on top of labels)
            topRightPoints.forEach { coloredPoint ->
                LinePlot(
                    data = listOf(coloredPoint.point),
                    lineStyle = null,
                    symbol = { QuadrantSymbol(coloredPoint.color) }
                )
            }

            topLeftPoints.forEach { coloredPoint ->
                LinePlot(
                    data = listOf(coloredPoint.point),
                    lineStyle = null,
                    symbol = { QuadrantSymbol(coloredPoint.color) }
                )
            }

            bottomLeftPoints.forEach { coloredPoint ->
                LinePlot(
                    data = listOf(coloredPoint.point),
                    lineStyle = null,
                    symbol = { QuadrantSymbol(coloredPoint.color) }
                )
            }

            bottomRightPoints.forEach { coloredPoint ->
                LinePlot(
                    data = listOf(coloredPoint.point),
                    lineStyle = null,
                    symbol = { QuadrantSymbol(coloredPoint.color) }
                )
            }
                }

                // Quadrant legend with regression line
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LegendItem(topRightLabel, topRightColor, textColor)
                    LegendItem(topLeftLabel, topLeftColor, textColor)
                    LegendItem(bottomLeftLabel, bottomLeftColor, textColor)
                    LegendItem(bottomRightLabel, bottomRightColor, textColor)
                    if (regression != null) {
                        LegendItem("Trend", Color(0xFF607D8B), textColor)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun QuadrantSymbol(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape)
    )
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun XYGraphScope<Float, Float>.QuadrantBackground(
    xMin: Float,
    xMax: Float,
    yMin: Float,
    yMax: Float,
    color: Color
) {
    // Create multiple horizontal lines to fill the area
    // Optimized approach: Use 50 lines with 2dp stroke width for better performance
    val numLines = 50
    val yStep = (yMax - yMin) / numLines

    for (i in 0 until numLines) {
        val y = yMin + (i * yStep)
        LinePlot(
            data = listOf(DefaultPoint(xMin, y), DefaultPoint(xMax, y)),
            lineStyle = LineStyle(
                brush = SolidColor(color),
                strokeWidth = 2.dp
            )
        )
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun XYGraphScope<Float, Float>.PointLabel(
    x: Float,
    y: Float,
    label: String,
    color: Color,
    offsetY: Float = -14f,
    backgroundColor: Color = Color.White
) {
    // Determine if we're in dark mode by checking background
    val isDarkMode = backgroundColor == Color.Black || backgroundColor == Color(0xFF0A0A0A)

    // Calculate label color
    // In dark mode: always use the quadrant color
    // In light mode: use black for light colors, quadrant color for dark colors
    val labelColor = if (isDarkMode) {
        // Dark mode: always use quadrant color
        color.copy(alpha = if (color.alpha < 0.5f) color.alpha else 0.9f)
    } else {
        // Light mode: use black for light colors
        if (isColorTooLight(color)) {
            Color.Black.copy(alpha = if (color.alpha < 0.5f) color.alpha else 1f)
        } else {
            color.copy(alpha = if (color.alpha < 0.5f) color.alpha else 0.9f)
        }
    }

    // Background color for label
    // Reduce background opacity if point is dimmed
    val backgroundAlpha = if (color.alpha < 0.5f) color.alpha * 0.5f else 0.85f
    val labelBackgroundColor = backgroundColor.copy(alpha = backgroundAlpha)

    // Use LinePlot with a single point and custom symbol that includes text
    LinePlot(
        data = listOf(DefaultPoint(x, y)),
        lineStyle = null,
        symbol = {
            // Label positioned with smart offset and background
            Text(
                text = label,
                fontSize = 8.sp,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                lineHeight = 8.sp, // Match font size to eliminate vertical spacing
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .padding(horizontal = 4.dp, vertical = 0.dp)
                    .background(labelBackgroundColor, CircleShape)
            )
        }
    )
}

// Helper function to determine if a color is too light for visibility in light mode
private fun isColorTooLight(color: Color): Boolean {
    // Calculate relative luminance
    val r = color.red
    val g = color.green
    val b = color.blue

    // Use standard luminance formula
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b

    // If luminance > 0.5, consider it too light (yellow is around 0.92, orange is ~0.6)
    // Lower threshold to catch more light colors
    return luminance > 0.5f
}

@Composable
private fun LegendItem(text: String, color: Color, textColor: Color) {
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
            color = textColor,
            maxLines = 1
        )
    }
}

