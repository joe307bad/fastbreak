package com.joebad.fastbreak.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun PhysicalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disabled: Boolean = false, // New Disabled prop
    destructive: Boolean = false, // New Destructive prop
    elevation: Dp = 6.dp,
    pressDepth: Dp = 3.dp,
    backgroundColor: Color? = null,
    contentColor: Color? = null,
    bottomBorderColor: Color? = null,
    borderColor: Color? = null,
    shape: Shape = RectangleShape,
    textSize: Int = 18,
    loading: Boolean? = false,
    zIndex: Float = 0f,
    loadingColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // If disabled or loading, the button starts in the pressed position
    val translationY by animateFloatAsState(
        targetValue = if (disabled || (loading == true) || isPressed) pressDepth.value else 0f,
        label = "translationY"
    )

    // Apply different styles when disabled or destructive
    val finalBackgroundColor = when {
        disabled -> Color.LightGray
        destructive -> Color.Transparent // Transparent background for outlined look
        else -> backgroundColor ?: colors.secondary
    }
    val finalContentColor = when {
        disabled -> Color.Black
        destructive -> colors.error // Use error color for destructive text
        else -> contentColor ?: colors.onSecondary
    }
    val finalBorderColor = when {
        destructive -> colors.error // Use error color for destructive border
        borderColor != null -> borderColor
        else -> backgroundColor ?: colors.accent
    }
    val finalBottomBorderColor = when {
        destructive -> colors.error // Use error color for destructive bottom border
        else -> bottomBorderColor ?: colors.accent
    }
    val finalLoadingColor = when {
        destructive -> colors.error // Use error color for destructive loading indicator
        else -> loadingColor ?: colors.onSecondary
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(bottom = 2.dp).zIndex(zIndex),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(elevation)
                .offset(y = 23.dp)
                .clip(shape)
                .background(finalBottomBorderColor)
                .zIndex(2f)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(
                    minWidth = ButtonDefaults.MinWidth,
                    minHeight = ButtonDefaults.MinHeight
                )
                .offset(y = translationY.dp)
                .zIndex(3f)
                .border(2.dp, finalBorderColor)
                .then(
                    if (!disabled) Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current, // Removes ripple effect if disabled
                        onClick = onClick
                    ) else Modifier.clickable(
                        indication = null,
                        onClick = {},
                        interactionSource = null
                    )
                ),// Fix here
            shape = shape,
            color = finalBackgroundColor,
            contentColor = finalContentColor,
        ) {
            CompositionLocalProvider(LocalContentColor provides finalContentColor) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = ButtonDefaults.MinWidth,
                            minHeight = ButtonDefaults.MinHeight
                        )
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Loading icon positioned absolutely on the left
                        val loadingAlpha by animateFloatAsState(
                            targetValue = if (loading == true) 1f else 0f,
                            label = "loadingAlpha"
                        )
                        
                        CircularProgressIndicator(
                            color = finalLoadingColor,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(20.dp)
                                .graphicsLayer { alpha = loadingAlpha },
                            strokeWidth = 3.dp
                        )
                        
                        // Content always visible in center
                        Box(modifier = Modifier.align(Alignment.Center)) {
                            CompositionLocalProvider(
                                LocalTextStyle provides TextStyle(
                                    fontSize = textSize.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
        }

    }
}
