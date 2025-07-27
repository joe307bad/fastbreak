
package com.joebad.fastbreak.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

fun Modifier.shimmerEffect(
    baseColor: Color = Color.LightGray.copy(alpha = 0.6f),
    highlightColor: Color = Color.White.copy(alpha = 0.9f),
    durationMillis: Int = 2000
) = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")

    var width by remember { mutableStateOf(0f) } // Store the width of the composable

    val translateAnim by transition.animateFloat(
        initialValue = -2 * width, // Start completely off the left
        targetValue = 2 * width,   // Move fully past the right
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    this.then(
        Modifier.drawWithCache {
            width = size.width // Capture the actual width dynamically
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(baseColor, highlightColor, baseColor),
                start = Offset(translateAnim, 0f),
                end = Offset(translateAnim + width, size.height),
                tileMode = TileMode.Clamp
            )
            onDrawBehind {
                drawRect(shimmerBrush)
            }
        }
    )
}
