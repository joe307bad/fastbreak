package com.joebad.fastbreak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

/**
 * A modal HSV color picker. Presents a saturation/value area, a hue slider,
 * a live preview, and confirm/cancel actions. When [onReset] is provided a
 * "reset" button is shown (used to revert a slot back to its team color).
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    title: String = "pick a color",
    onReset: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) { rgbToHsv(initialColor) }
    var hue by remember { mutableStateOf(initialHsv.first) }
    var saturation by remember { mutableStateOf(initialHsv.second) }
    var value by remember { mutableStateOf(initialHsv.third) }

    val selectedColor = Color.hsv(hue, saturation, value)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Saturation / value 2D area
                var svSize by remember { mutableStateOf(IntSize.Zero) }
                val hueColor = Color.hsv(hue, 1f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                        .onSizeChanged { svSize = it }
                        .pointerInput(svSize) {
                            if (svSize.width > 0 && svSize.height > 0) {
                                detectTapGestures { offset ->
                                    saturation = (offset.x / svSize.width).coerceIn(0f, 1f)
                                    value = (1f - offset.y / svSize.height).coerceIn(0f, 1f)
                                }
                            }
                        }
                        .pointerInput(svSize) {
                            if (svSize.width > 0 && svSize.height > 0) {
                                detectDragGestures { change, _ ->
                                    saturation = (change.position.x / svSize.width).coerceIn(0f, 1f)
                                    value = (1f - change.position.y / svSize.height).coerceIn(0f, 1f)
                                }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val cx = saturation * size.width
                            val cy = (1f - value) * size.height
                            drawCircle(
                                color = Color.Black,
                                radius = 9.dp.toPx(),
                                center = Offset(cx, cy),
                                style = Stroke(width = 3.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 9.dp.toPx(),
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                )

                // Hue slider
                var hueSize by remember { mutableStateOf(IntSize.Zero) }
                val hueSpectrum = remember {
                    listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it, 1f, 1f) }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(hueSpectrum))
                        .onSizeChanged { hueSize = it }
                        .pointerInput(hueSize) {
                            if (hueSize.width > 0) {
                                detectTapGestures { offset ->
                                    hue = (offset.x / hueSize.width * 360f).coerceIn(0f, 360f)
                                }
                            }
                        }
                        .pointerInput(hueSize) {
                            if (hueSize.width > 0) {
                                detectDragGestures { change, _ ->
                                    hue = (change.position.x / hueSize.width * 360f).coerceIn(0f, 360f)
                                }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            val r = size.height / 2f
                            val cx = (hue / 360f * size.width).coerceIn(r, size.width - r)
                            drawCircle(
                                color = Color.White,
                                radius = r - 2.dp.toPx(),
                                center = Offset(cx, r),
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                )

                // Preview + hex
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(selectedColor)
                    )
                    Text(
                        text = selectedColor.toHexString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onReset != null) {
                        TextButton(onClick = onReset) {
                            Text("reset", fontFamily = FontFamily.Monospace)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("cancel", fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(onClick = { onConfirm(selectedColor) }) {
                        Text("done", fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

/** Converts an RGB [Color] to (hue 0..360, saturation 0..1, value 0..1). */
private fun rgbToHsv(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val saturation = if (max == 0f) 0f else delta / max
    return Triple(hue, saturation, max)
}

/** Formats a [Color] as an uppercase "#RRGGBB" string. */
internal fun Color.toHexString(): String {
    fun channel(v: Float): String =
        (v * 255f).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
    return "#${channel(red)}${channel(green)}${channel(blue)}"
}
