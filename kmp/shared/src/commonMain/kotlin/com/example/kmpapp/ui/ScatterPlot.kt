package com.example.kmpapp.ui

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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmpapp.data.model.ScatterPlotDataPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

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

@Composable
fun FourQuadrantScatterPlot(
    data: List<ScatterPlotDataPoint>,
    modifier: Modifier = Modifier,
    title: String = "4-Quadrant Scatter Plot"
) {
    // Zoom and pan state
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val (minX, maxX, minY, maxY) = remember(data) {
        val minX = data.minOfOrNull { it.x } ?: 50.0
        val maxX = data.maxOfOrNull { it.x } ?: 100.0
        val minY = data.minOfOrNull { it.y } ?: -0.2
        val maxY = data.maxOfOrNull { it.y } ?: 0.4
        listOf(minX, maxX, minY, maxY)
    }

    // Calculate actual data averages (fixed, not based on visible range)
    val (avgPFF, avgEPA) = remember(data) {
        val avgX = data.map { it.x }.average()
        val avgY = data.map { it.y }.average()
        Pair(avgX, avgY)
    }

    // Calculate linear regression (line of best fit)
    val (slope, intercept) = remember(data) {
        val n = data.size.toDouble()
        val sumX = data.sumOf { it.x }
        val sumY = data.sumOf { it.y }
        val sumXY = data.sumOf { it.x * it.y }
        val sumX2 = data.sumOf { it.x * it.x }

        // y = mx + b
        // slope (m) = (n*Σxy - Σx*Σy) / (n*Σx² - (Σx)²)
        // intercept (b) = (Σy - m*Σx) / n
        val m = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val b = (sumY - m * sumX) / n

        Pair(m, b)
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

                // Draw tick label
                val tickLabel = tickValue.formatTo(2)
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

                // Label for the average EPA line
                val avgLabel = "Avg: ${avgEPA.formatTo(2)}"
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

                // Convert to screen coordinates
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
            val xAxisLabelText = "PFF Offense Grade"
            val xAxisLabel = textMeasurer.measure(xAxisLabelText, labelTextStyle.copy(fontWeight = FontWeight.Bold))
            drawText(
                textMeasurer,
                xAxisLabelText,
                topLeft = Offset((width - xAxisLabel.size.width) / 2, height - bottomPadding + 30),
                style = labelTextStyle.copy(fontWeight = FontWeight.Bold)
            )

            val yAxisLabelText = "EPA per Play"
            val yAxisLabel = textMeasurer.measure(yAxisLabelText, labelTextStyle.copy(fontWeight = FontWeight.Bold))
            drawText(
                textMeasurer,
                yAxisLabelText,
                topLeft = Offset(5f, (height - yAxisLabel.size.width) / 2),
                style = labelTextStyle.copy(fontWeight = FontWeight.Bold)
            )

            // Plot data points with labels (with clipping)
            clipRect(
                left = leftPadding,
                top = topPadding,
                right = width - rightPadding,
                bottom = height - bottomPadding
            ) {
                data.forEach { point ->
                    val x = leftPadding + (width - leftPadding - rightPadding) * (point.x.toFloat() - visibleMinX) / adjustedXRange
                    val y = height - bottomPadding - (height - topPadding - bottomPadding) * (point.y.toFloat() - visibleMinY) / adjustedYRange

                    // Only draw if point is in visible range
                    if (x >= leftPadding && x <= width - rightPadding && y >= topPadding && y <= height - bottomPadding) {
                        // Determine quadrant based on comparison to data averages (not visible averages)
                        val pointColor = when {
                            point.x >= avgPFF && point.y >= avgEPA -> Color(0xFF4CAF50) // Q1 - High PFF, High EPA (Green)
                            point.x < avgPFF && point.y >= avgEPA -> Color(0xFF2196F3)  // Q2 - Low PFF, High EPA (Blue)
                            point.x < avgPFF && point.y < avgEPA -> Color(0xFFFF9800)   // Q3 - Low PFF, Low EPA (Orange)
                            else -> Color(0xFFF44336)                                   // Q4 - High PFF, Low EPA (Red)
                        }

                        // Draw point
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

                        // Draw QB name label next to point
                        drawText(
                            textMeasurer,
                            point.label,
                            topLeft = Offset(x + 10f, y - 6f),
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

        // Quadrant legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuadrantLabel("Elite", Color(0xFF4CAF50))
            QuadrantLabel("Efficient", Color(0xFF2196F3))
            QuadrantLabel("Struggling", Color(0xFFFF9800))
            QuadrantLabel("Inefficient", Color(0xFFF44336))
        }
    }
}

@Composable
private fun QuadrantLabel(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
