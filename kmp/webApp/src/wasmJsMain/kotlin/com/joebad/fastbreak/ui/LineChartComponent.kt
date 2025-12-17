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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.LineChartSeries
import kotlin.math.*

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

private fun Float.formatTo(decimals: Int): String {
    val multiplier = when (decimals) {
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
    val rounded = (this * multiplier).roundToInt() / multiplier
    return rounded.toString()
}

/**
 * Reusable line chart component using Canvas.
 * Supports multiple series with different colors, pan and zoom.
 */
@Composable
fun LineChartComponent(
    series: List<LineChartSeries>,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty() || series.all { it.dataPoints.isEmpty() }) return

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

    val axisColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    // Zoom and pan state
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

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
                .weight(1f)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 3f)

                        // Apply pan with limits based on initial data range
                        val tentativeOffsetX = offsetX + pan.x
                        val tentativeOffsetY = offsetY + pan.y

                        // Calculate how far the pan would take us
                        val allPoints = series.flatMap { it.dataPoints }
                        val minX = allPoints.minOfOrNull { it.x }?.toFloat() ?: 0f
                        val maxX = allPoints.maxOfOrNull { it.x }?.toFloat() ?: 1f
                        val minY = allPoints.minOfOrNull { it.y }?.toFloat() ?: 0f
                        val maxY = allPoints.maxOfOrNull { it.y }?.toFloat() ?: 1f

                        val baseXRange = maxX - minX
                        val baseYRange = maxY - minY
                        val centerX = (minX + maxX) / 2
                        val centerY = (minY + maxY) / 2

                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val leftPad = 80f
                        val rightPad = 30f
                        val topPad = 30f
                        val bottomPad = 60f

                        val zoomedXRange = baseXRange / scale
                        val zoomedYRange = baseYRange / scale

                        val panXOffset = -tentativeOffsetX / (canvasWidth - leftPad - rightPad) * zoomedXRange
                        val panYOffset = tentativeOffsetY / (canvasHeight - topPad - bottomPad) * zoomedYRange

                        val visibleMinX = centerX - zoomedXRange / 2 + panXOffset
                        val visibleMaxX = centerX + zoomedXRange / 2 + panXOffset
                        val visibleMinY = centerY - zoomedYRange / 2 + panYOffset
                        val visibleMaxY = centerY + zoomedYRange / 2 + panYOffset

                        // Limit panning: adjust based on zoom level (more relaxed when zoomed out)
                        val maxPanBeyond = 1.5f / scale
                        val allowedMinX = minX - baseXRange * maxPanBeyond
                        val allowedMaxX = maxX + baseXRange * maxPanBeyond
                        val allowedMinY = minY - baseYRange * maxPanBeyond
                        val allowedMaxY = maxY + baseYRange * maxPanBeyond

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
            val rightPadding = 30f
            val topPadding = 30f
            val bottomPadding = 60f

            // Calculate axis ranges
            val allPoints = series.flatMap { it.dataPoints }
            val minX = allPoints.minOfOrNull { it.x }?.toFloat() ?: 0f
            val maxX = allPoints.maxOfOrNull { it.x }?.toFloat() ?: 1f
            val minY = allPoints.minOfOrNull { it.y }?.toFloat() ?: 0f
            val maxY = allPoints.maxOfOrNull { it.y }?.toFloat() ?: 1f

            // Calculate visible range based on zoom and pan
            val baseXRange = maxX - minX
            val baseYRange = maxY - minY
            val centerX = (minX + maxX) / 2
            val centerY = (minY + maxY) / 2

            val zoomedXRange = baseXRange / scale
            val zoomedYRange = baseYRange / scale

            val panXOffset = -offsetX / (width - leftPadding - rightPadding) * zoomedXRange
            val panYOffset = offsetY / (height - topPadding - bottomPadding) * zoomedYRange

            val visibleMinX = (centerX - zoomedXRange / 2 + panXOffset)
            val visibleMaxX = (centerX + zoomedXRange / 2 + panXOffset)
            val visibleMinY = (centerY - zoomedYRange / 2 + panYOffset)
            val visibleMaxY = (centerY + zoomedYRange / 2 + panYOffset)

            val xRange = visibleMaxX - visibleMinX
            val yRange = visibleMaxY - visibleMinY

            if (xRange == 0f || yRange == 0f) return@Canvas

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

            fun calculateNiceTicksY(range: Float, targetCount: Int = 6): List<Float> {
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
            val xTicks = calculateNiceTicks(xRange)
            xTicks.forEach { tickValue ->
                val x = leftPadding + (width - leftPadding - rightPadding) * (tickValue - visibleMinX) / xRange

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
                val tickLabel = tickValue.formatTo(0)
                val measured = textMeasurer.measure(tickLabel, labelTextStyle)
                drawText(
                    textMeasurer,
                    tickLabel,
                    topLeft = Offset(x - measured.size.width / 2, height - bottomPadding + 10),
                    style = labelTextStyle
                )
            }

            // Draw Y-axis ticks and labels
            val yTicks = calculateNiceTicksY(yRange)
            yTicks.forEach { tickValue ->
                val y = height - bottomPadding - (height - topPadding - bottomPadding) * (tickValue - visibleMinY) / yRange

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

                // Draw tick label
                val tickLabel = tickValue.formatTo(1)
                val measured = textMeasurer.measure(tickLabel, labelTextStyle)
                drawText(
                    textMeasurer,
                    tickLabel,
                    topLeft = Offset(leftPadding - measured.size.width - 8, y - measured.size.height / 2),
                    style = labelTextStyle
                )
            }

            // Draw each series with clipping to keep within graph bounds
            clipRect(
                left = leftPadding,
                top = topPadding,
                right = width - rightPadding,
                bottom = height - bottomPadding
            ) {
                series.forEachIndexed { seriesIndex, lineSeries ->
                    if (lineSeries.dataPoints.size < 2) return@forEachIndexed

                    val color = seriesColors[seriesIndex]
                    val path = Path()
                    var pathStarted = false

                    // Draw lines
                    lineSeries.dataPoints.forEach { point ->
                        val pointX = point.x.toFloat()
                        val pointY = point.y.toFloat()

                        // Only include points in visible range (with some buffer)
                        if (pointX >= visibleMinX - xRange * 0.1f && pointX <= visibleMaxX + xRange * 0.1f) {
                            val x = leftPadding + (width - leftPadding - rightPadding) * (pointX - visibleMinX) / xRange
                            val y = height - bottomPadding - (height - topPadding - bottomPadding) * (pointY - visibleMinY) / yRange

                            if (!pathStarted) {
                                path.moveTo(x, y)
                                pathStarted = true
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                    }

                    // Draw the path
                    if (pathStarted) {
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = 3f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Draw points (only in visible range)
                    lineSeries.dataPoints.forEach { point ->
                        val pointX = point.x.toFloat()
                        val pointY = point.y.toFloat()

                        if (pointX >= visibleMinX && pointX <= visibleMaxX) {
                            val x = leftPadding + (width - leftPadding - rightPadding) * (pointX - visibleMinX) / xRange
                            val y = height - bottomPadding - (height - topPadding - bottomPadding) * (pointY - visibleMinY) / yRange

                            drawCircle(
                                color = color,
                                radius = 4f,
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }

            // Draw axis labels
            val yAxisLabelText = "Value"
            val yAxisLabel = textMeasurer.measure(yAxisLabelText, labelTextStyle.copy(fontWeight = FontWeight.Bold))
            drawText(
                textMeasurer,
                yAxisLabelText,
                topLeft = Offset(5f, (height - yAxisLabel.size.width) / 2),
                style = labelTextStyle.copy(fontWeight = FontWeight.Bold)
            )

            val xAxisLabelText = "Week/Game"
            val xAxisLabel = textMeasurer.measure(xAxisLabelText, labelTextStyle.copy(fontWeight = FontWeight.Bold))
            drawText(
                textMeasurer,
                xAxisLabelText,
                topLeft = Offset((width - xAxisLabel.size.width) / 2, height - bottomPadding + 35),
                style = labelTextStyle.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Info text
        Text(
            text = "Pinch to zoom • Drag to pan",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )

        // Series legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            series.forEachIndexed { index, lineSeries ->
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
                        text = lineSeries.label,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
