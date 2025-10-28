package com.example.kmpapp.ui.visualizations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kmpapp.data.model.BarGraphDataPoint
import kotlin.math.*

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
 * Reusable bar chart component using Canvas.
 * Supports both positive and negative values with pan and zoom.
 */
@Composable
fun BarChartComponent(
    data: List<BarGraphDataPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
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
                .height(400.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 3f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val leftPadding = 80f
            val rightPadding = 30f
            val topPadding = 30f
            val bottomPadding = 70f

            val minValue = data.minOfOrNull { it.value }?.toFloat() ?: 0f
            val maxValue = data.maxOfOrNull { it.value }?.toFloat() ?: 1f

            // Calculate visible range based on zoom and pan
            val baseRange = maxValue - minValue
            val centerValue = (minValue + maxValue) / 2
            val zoomedRange = baseRange / scale
            val panYOffset = offsetY / (height - topPadding - bottomPadding) * zoomedRange

            val visibleMinY = (centerValue - zoomedRange / 2 + panYOffset)
            val visibleMaxY = (centerValue + zoomedRange / 2 + panYOffset)
            val visibleRange = visibleMaxY - visibleMinY

            if (visibleRange == 0f) return@Canvas

            // Draw border frame
            drawRect(
                color = axisColor,
                topLeft = Offset(leftPadding, topPadding),
                size = Size(
                    width - leftPadding - rightPadding,
                    height - topPadding - bottomPadding
                ),
                style = Stroke(width = 2f)
            )

            // Calculate Y-axis ticks
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

            // Draw Y-axis ticks and grid lines
            val yTicks = calculateNiceTicksY(visibleRange)
            yTicks.forEach { tickValue ->
                val y = height - bottomPadding - (height - topPadding - bottomPadding) * (tickValue - visibleMinY) / visibleRange

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

            // Calculate zero line position
            val zeroY = if (visibleMinY < 0 && visibleMaxY > 0) {
                height - bottomPadding - (height - topPadding - bottomPadding) * (0f - visibleMinY) / visibleRange
            } else if (visibleMaxY <= 0) {
                topPadding
            } else {
                height - bottomPadding
            }

            // Draw zero line if in visible range
            if (0f >= visibleMinY && 0f <= visibleMaxY) {
                drawLine(
                    color = axisColor.copy(alpha = 0.5f),
                    start = Offset(leftPadding, zeroY),
                    end = Offset(width - rightPadding, zeroY),
                    strokeWidth = 2f
                )
            }

            // Draw bars with clipping
            val barWidth = (width - leftPadding - rightPadding) / (data.size * 1.5f)
            val barSpacing = barWidth * 0.5f

            clipRect(
                left = leftPadding,
                top = topPadding,
                right = width - rightPadding,
                bottom = height - bottomPadding
            ) {
                data.forEachIndexed { index, point ->
                    val x = leftPadding + index * (barWidth + barSpacing)

                    // Calculate bar position based on value and visible range
                    val topY = height - bottomPadding - (height - topPadding - bottomPadding) * (point.value.toFloat() - visibleMinY) / visibleRange
                    val barHeight = abs(topY - zeroY)

                    val y = if (point.value >= 0) {
                        topY
                    } else {
                        zeroY
                    }

                    val color = if (point.value < 0) errorColor else primaryColor

                    // Draw bar without clamping - let Canvas naturally clip
                    drawRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight)
                    )
                }
            }

            // Draw X-axis labels (team names) - show all labels in staggered rows
            data.forEachIndexed { index, point ->
                val x = leftPadding + index * (barWidth + barSpacing)
                val labelX = x + barWidth / 2
                val measured = textMeasurer.measure(point.label, labelTextStyle.copy(fontSize = 9.sp))
                // Stagger labels: even indices at base position, odd indices offset down
                val yOffset = if (index % 2 == 0) 10f else 26f
                drawText(
                    textMeasurer,
                    point.label,
                    topLeft = Offset(labelX - measured.size.width / 2, height - bottomPadding + yOffset),
                    style = labelTextStyle.copy(fontSize = 9.sp)
                )
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

            val xAxisLabelText = "Teams"
            val xAxisLabel = textMeasurer.measure(xAxisLabelText, labelTextStyle.copy(fontWeight = FontWeight.Bold))
            drawText(
                textMeasurer,
                xAxisLabelText,
                topLeft = Offset((width - xAxisLabel.size.width) / 2, height - bottomPadding + 45),
                style = labelTextStyle.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Info text
        Text(
            text = "Pinch to zoom • Drag to pan",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
