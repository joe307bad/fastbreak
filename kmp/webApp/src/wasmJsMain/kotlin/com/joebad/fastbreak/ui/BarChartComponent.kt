package com.joebad.fastbreak.ui

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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.BarGraphDataPoint
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
 * Zoom widens bars horizontally for easier inspection.
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

    // Zoom and pan state - start slightly zoomed out to show all bars
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
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                val zoomFactor = if (scrollDelta > 0) 0.9f else 1.1f
                                scale = (scale * zoomFactor).coerceIn(0.5f, 5f)
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                        scale = newScale
                        // Scale pan speed with zoom level for natural feel
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val leftPadding = 60f
            val rightPadding = 20f
            val topPadding = 30f
            val bottomPadding = 70f

            val chartWidth = width - leftPadding - rightPadding
            val chartHeight = height - topPadding - bottomPadding

            val minValue = data.minOfOrNull { it.value }?.toFloat() ?: 0f
            val maxValue = data.maxOfOrNull { it.value }?.toFloat() ?: 1f

            // Add padding to Y range for better visualization
            val yPadding = (maxValue - minValue) * 0.1f
            val paddedMinValue = minValue - yPadding
            val paddedMaxValue = maxValue + yPadding
            val valueRange = paddedMaxValue - paddedMinValue

            if (valueRange == 0f) return@Canvas

            // Calculate total content width based on zoom
            // Base bar width + spacing for all bars
            val baseBarWidth = chartWidth / (data.size * 1.5f)
            val scaledBarWidth = baseBarWidth * scale
            val barSpacing = scaledBarWidth * 0.5f
            val totalContentWidth = data.size * (scaledBarWidth + barSpacing) - barSpacing

            // Constrain horizontal pan to keep content in view
            val maxOffsetX = if (totalContentWidth > chartWidth) {
                (totalContentWidth - chartWidth) / 2 + chartWidth * 0.1f
            } else {
                chartWidth * 0.1f
            }
            val constrainedOffsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)

            // Draw border frame
            drawRect(
                color = axisColor,
                topLeft = Offset(leftPadding, topPadding),
                size = Size(chartWidth, chartHeight),
                style = Stroke(width = 2f)
            )

            // Calculate Y-axis ticks
            fun calculateNiceTicksY(min: Float, max: Float, targetCount: Int = 6): List<Float> {
                val range = max - min
                val roughInterval = range / targetCount
                val magnitude = 10.0.pow(floor(log10(roughInterval.toDouble()))).toFloat()
                val niceInterval = when {
                    roughInterval / magnitude < 1.5f -> magnitude
                    roughInterval / magnitude < 3f -> 2 * magnitude
                    roughInterval / magnitude < 7f -> 5 * magnitude
                    else -> 10 * magnitude
                }

                val start = ceil(min / niceInterval) * niceInterval
                val ticks = mutableListOf<Float>()
                var tick = start
                while (tick <= max) {
                    ticks.add(tick)
                    tick += niceInterval
                }
                return ticks
            }

            // Draw Y-axis ticks and grid lines
            val yTicks = calculateNiceTicksY(paddedMinValue, paddedMaxValue)
            yTicks.forEach { tickValue ->
                val y = height - bottomPadding - chartHeight * (tickValue - paddedMinValue) / valueRange

                if (y >= topPadding && y <= height - bottomPadding) {
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
                    val tickLabel = tickValue.toInt().toString()
                    val measured = textMeasurer.measure(tickLabel, labelTextStyle)
                    drawText(
                        textMeasurer,
                        tickLabel,
                        topLeft = Offset(leftPadding - measured.size.width - 8, y - measured.size.height / 2),
                        style = labelTextStyle
                    )
                }
            }

            // Calculate zero line position
            val zeroY = height - bottomPadding - chartHeight * (0f - paddedMinValue) / valueRange

            // Draw zero line if in visible range
            if (0f >= paddedMinValue && 0f <= paddedMaxValue) {
                drawLine(
                    color = axisColor.copy(alpha = 0.7f),
                    start = Offset(leftPadding, zeroY),
                    end = Offset(width - rightPadding, zeroY),
                    strokeWidth = 2f
                )
            }

            // Calculate starting X position (centered when not zoomed, panned otherwise)
            val contentStartX = if (totalContentWidth < chartWidth) {
                // Center content when it fits
                leftPadding + (chartWidth - totalContentWidth) / 2 + constrainedOffsetX
            } else {
                // Start from left edge with pan offset
                leftPadding + constrainedOffsetX - (totalContentWidth - chartWidth) / 2
            }

            // Draw bars with clipping
            clipRect(
                left = leftPadding,
                top = topPadding,
                right = width - rightPadding,
                bottom = height - bottomPadding
            ) {
                data.forEachIndexed { index, point ->
                    val barX = contentStartX + index * (scaledBarWidth + barSpacing)

                    // Skip bars that are completely outside visible area
                    if (barX + scaledBarWidth < leftPadding || barX > width - rightPadding) {
                        return@forEachIndexed
                    }

                    // Calculate bar position based on value
                    val valueY = height - bottomPadding - chartHeight * (point.value.toFloat() - paddedMinValue) / valueRange
                    val barHeight = abs(valueY - zeroY)

                    val barTop = if (point.value >= 0) valueY else zeroY
                    val color = if (point.value < 0) errorColor else primaryColor

                    drawRect(
                        color = color,
                        topLeft = Offset(barX, barTop),
                        size = Size(scaledBarWidth, barHeight)
                    )
                }
            }

            // Draw X-axis labels with clipping
            clipRect(
                left = leftPadding,
                top = height - bottomPadding,
                right = width - rightPadding,
                bottom = height
            ) {
                // Calculate font size based on zoom level
                val labelFontSize = (9 + (scale - 1) * 2).coerceIn(9f, 14f).sp

                data.forEachIndexed { index, point ->
                    val barX = contentStartX + index * (scaledBarWidth + barSpacing)
                    val labelX = barX + scaledBarWidth / 2

                    // Skip labels that are outside visible area
                    if (labelX < leftPadding - 20 || labelX > width - rightPadding + 20) {
                        return@forEachIndexed
                    }

                    val measured = textMeasurer.measure(point.label, labelTextStyle.copy(fontSize = labelFontSize))

                    // Stagger labels when zoomed out, single row when zoomed in
                    val yOffset = if (scale < 1.5f && index % 2 == 1) 22f else 8f

                    drawText(
                        textMeasurer,
                        point.label,
                        topLeft = Offset(labelX - measured.size.width / 2, height - bottomPadding + yOffset),
                        style = labelTextStyle.copy(fontSize = labelFontSize)
                    )
                }
            }

            // Draw Y-axis label
            val yAxisLabelText = "Value"
            val yAxisLabel = textMeasurer.measure(yAxisLabelText, labelTextStyle.copy(fontWeight = FontWeight.Bold))
            drawText(
                textMeasurer,
                yAxisLabelText,
                topLeft = Offset(5f, (height - yAxisLabel.size.width) / 2),
                style = labelTextStyle.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Info text
        Text(
            text = "Scroll to zoom • Drag to pan",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
