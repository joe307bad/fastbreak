package com.example.kmpapp.ui.visualizations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.example.kmpapp.data.model.ScatterPlotDataPoint

/**
 * Reusable scatter plot component using Canvas.
 */
@Composable
fun ScatterPlotComponent(
    data: List<ScatterPlotDataPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.height(400.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Calculate axis ranges with padding
            val xValues = data.map { it.x }
            val yValues = data.map { it.y }

            val minX = xValues.minOrNull() ?: 0.0
            val maxX = xValues.maxOrNull() ?: 1.0
            val minY = yValues.minOrNull() ?: 0.0
            val maxY = yValues.maxOrNull() ?: 1.0

            val xPadding = (maxX - minX) * 0.1
            val yPadding = (maxY - minY) * 0.1

            val xRange = (maxX - minX) + (2 * xPadding)
            val yRange = (maxY - minY) + (2 * yPadding)

            if (xRange == 0.0 || yRange == 0.0) return@Canvas

            // Draw points
            data.forEach { point ->
                val x = (((point.x - minX + xPadding) / xRange) * canvasWidth).toFloat()
                val y = (canvasHeight - (((point.y - minY + yPadding) / yRange) * canvasHeight)).toFloat()

                drawCircle(
                    color = primaryColor,
                    radius = 6f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
