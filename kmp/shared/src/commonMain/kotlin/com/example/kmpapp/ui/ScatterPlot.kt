package com.example.kmpapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kmpapp.data.model.DataPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun FourQuadrantScatterPlot(
    data: List<DataPoint>,
    modifier: Modifier = Modifier,
    title: String = "4-Quadrant Scatter Plot"
) {
    val (minX, maxX, minY, maxY) = remember(data) {
        val minX = data.minOfOrNull { it.x } ?: -10.0
        val maxX = data.maxOfOrNull { it.x } ?: 10.0
        val minY = data.minOfOrNull { it.y } ?: -10.0
        val maxY = data.maxOfOrNull { it.y } ?: 10.0
        listOf(minX, maxX, minY, maxY)
    }

    val axisColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(32.dp)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val width = size.width
            val height = size.height
            val padding = 40f

            // Calculate axis ranges
            val xAxisRange = abs(maxX - minX).toFloat()
            val yAxisRange = abs(maxY - minY).toFloat()

            // Draw grid lines
            val gridLines = 5
            for (i in 0..gridLines) {
                val x = padding + (width - 2 * padding) * i / gridLines
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = Offset(x, padding),
                    end = Offset(x, height - padding),
                    strokeWidth = 1f
                )

                val y = padding + (height - 2 * padding) * i / gridLines
                drawLine(
                    color = gridColor.copy(alpha = 0.3f),
                    start = Offset(padding, y),
                    end = Offset(width - padding, y),
                    strokeWidth = 1f
                )
            }

            // Draw main axes (at 0,0)
            val zeroX = padding + (width - 2 * padding) * (0 - minX.toFloat()) / xAxisRange
            val zeroY = height - padding - (height - 2 * padding) * (0 - minY.toFloat()) / yAxisRange

            // X-axis (horizontal at y=0)
            drawLine(
                color = axisColor,
                start = Offset(padding, zeroY),
                end = Offset(width - padding, zeroY),
                strokeWidth = 3f
            )

            // Y-axis (vertical at x=0)
            drawLine(
                color = axisColor,
                start = Offset(zeroX, padding),
                end = Offset(zeroX, height - padding),
                strokeWidth = 3f
            )

            // Plot data points
            data.forEach { point ->
                val x = padding + (width - 2 * padding) * (point.x.toFloat() - minX.toFloat()) / xAxisRange
                val y = height - padding - (height - 2 * padding) * (point.y.toFloat() - minY.toFloat()) / yAxisRange

                val pointColor = when {
                    point.x >= 0 && point.y >= 0 -> Color(0xFF4CAF50) // Q1 - Green
                    point.x < 0 && point.y >= 0 -> Color(0xFF2196F3)  // Q2 - Blue
                    point.x < 0 && point.y < 0 -> Color(0xFFFF9800)   // Q3 - Orange
                    else -> Color(0xFFF44336)                         // Q4 - Red
                }

                drawCircle(
                    color = pointColor,
                    radius = 8f,
                    center = Offset(x, y)
                )

                drawCircle(
                    color = axisColor,
                    radius = 8f,
                    center = Offset(x, y),
                    style = Stroke(width = 2f)
                )
            }
        }

        // Quadrant legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuadrantLabel("Q1 (+,+)", Color(0xFF4CAF50))
            QuadrantLabel("Q2 (-,+)", Color(0xFF2196F3))
            QuadrantLabel("Q3 (-,-)", Color(0xFFFF9800))
            QuadrantLabel("Q4 (+,-)", Color(0xFFF44336))
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
