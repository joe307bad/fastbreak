package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.platform.addTitleToBitmap
import com.joebad.fastbreak.platform.getImageExporter
import kotlinx.coroutines.launch

/**
 * A container that wraps chart content and provides sharing functionality.
 *
 * This composable handles:
 * - Capturing the chart as a bitmap using graphics layer
 * - Adding a title to the captured image
 * - Displaying a floating action button for sharing
 * - Triggering the platform-specific share dialog
 *
 * @param title The title to add to the shared image
 * @param source Optional source attribution text
 * @param showShareButton Whether to display the share FAB button
 * @param onShareClick Callback that provides the share handler function
 * @param modifier Modifier to be applied to the container
 * @param content The chart content to be displayed and captured
 */
@Composable
fun ShareableChartContainer(
    title: String,
    source: String = "",
    showShareButton: Boolean = false,
    onShareClick: ((() -> Unit)?) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Detect theme mode
    val isDark = MaterialTheme.colorScheme.background == Color.Black ||
                 MaterialTheme.colorScheme.background == Color(0xFF0A0A0A)

    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground

    // Create graphics layer for capturing the chart
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    val imageExporter = remember { getImageExporter() }
    var isCapturing by remember { mutableStateOf(false) }
    var shareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Share function
    val onShare: () -> Unit = {
        coroutineScope.launch {
            try {
                isCapturing = true
                val chartBitmap = graphicsLayer.toImageBitmap()

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
        Unit
    }

    // Pass the share handler to parent when not showing internal button
    LaunchedEffect(showShareButton) {
        if (!showShareButton) {
            onShareClick(onShare)
        } else {
            onShareClick(null)
        }
    }

    Box(modifier = modifier) {
        // Wrap chart content with graphics layer for capture
        Box(
            modifier = Modifier
                .background(backgroundColor)
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        // Record to graphics layer for captures
                        graphicsLayer.record {
                            this@onDrawWithContent.drawContent()
                        }
                    }
                }
        ) {
            content()
        }

        // Share FAB button positioned at bottom right
        if (showShareButton) {
            FloatingActionButton(
                onClick = onShare,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share chart"
                )
            }
        }
    }
}
