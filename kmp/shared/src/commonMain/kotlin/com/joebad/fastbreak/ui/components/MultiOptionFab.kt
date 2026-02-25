package com.joebad.fastbreak.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Data class representing an option in the multi-option FAB.
 */
data class FabOption(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

/**
 * A floating action button that expands to show multiple options stacked vertically above it.
 * When clicked, smaller FABs animate upward from the center.
 *
 * @param options List of options to display when expanded
 * @param modifier Modifier for the container
 */
@Composable
fun MultiOptionFab(
    options: List<FabOption>,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Animation for rotation of main FAB icon
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "rotation"
    )

    // Animation for slide in/out
    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "expansion"
    )

    // Vertical spacing between option labels
    val spacing = 64.dp
    val mainFabOffset = 60.dp  // Offset to clear the main FAB

    BoxWithConstraints(
        contentAlignment = Alignment.BottomEnd,
        modifier = modifier
    ) {
        // Slide far enough to ensure longest option is fully off-screen
        val slideDistance = maxWidth.value + 200f

        // Option labels - stacked vertically above main FAB
        options.forEachIndexed { index, option ->
            // Fixed vertical position, slide in horizontally from right
            val offsetY = -(mainFabOffset + spacing * (options.size - index)).value.roundToInt()
            val offsetX = ((1f - expansionProgress) * slideDistance).roundToInt()

            Surface(
                onClick = {
                    option.onClick()
                    isExpanded = false
                },
                color = Color(0xFFFF6B6B),  // Red matching main FAB
                contentColor = Color.White,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .offset { IntOffset(offsetX, offsetY) }
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Main FAB
        FloatingActionButton(
            onClick = { isExpanded = !isExpanded },
            containerColor = Color(0xFFFF6B6B),  // Red
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = if (isExpanded) "Close" else "Share options",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Self-contained demo of MultiOptionFab with built-in AlertDialog.
 * Each option shows an alert when clicked. Drop this into any screen to test.
 */
@Composable
fun MultiOptionFabDemo(
    modifier: Modifier = Modifier
) {
    var alertMessage by remember { mutableStateOf<String?>(null) }

    val options = listOf(
        FabOption(
            icon = Icons.Filled.Share,
            label = "Share",
            onClick = { alertMessage = "Share clicked!" }
        ),
        FabOption(
            icon = Icons.Filled.Email,
            label = "Email",
            onClick = { alertMessage = "Email clicked!" }
        ),
        FabOption(
            icon = Icons.Filled.Star,
            label = "Favorite",
            onClick = { alertMessage = "Favorite clicked!" }
        )
    )

    MultiOptionFab(
        options = options,
        modifier = modifier
    )

    // Alert dialog
    alertMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            title = { Text("Action") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { alertMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
}
