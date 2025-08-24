package com.joebad.fastbreak.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.LockOpen

@Composable
fun LockableButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disabled: Boolean = false,
    destructive: Boolean = false,
    lockable: Boolean = false,
    onLock: (() -> Unit)? = null,
    isLocked: Boolean = false,
    elevation: Dp = 2.dp,
    pressDepth: Dp = 1.5.dp,
    backgroundColor: Color? = null,
    contentColor: Color? = null,
    bottomBorderColor: Color? = null,
    borderColor: Color? = null,
    shape: Shape = RectangleShape,
    textSize: Int = 14,
    loading: Boolean? = false,
    zIndex: Float = 0f,
    loadingColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalColors.current
    val hapticFeedback = LocalHapticFeedback.current

    val animationProgress = remember { Animatable(if (isLocked) 1f else 0f) }
    
    var isPressed by remember { mutableStateOf(isLocked) }
    var animationCompleted by remember { mutableStateOf(isLocked) }

    val translationY by animateFloatAsState(
        targetValue = if (disabled || (loading == true) || isPressed || animationCompleted) pressDepth.value else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "translationY"
    )

    val finalBackgroundColor = when {
        disabled -> Color.LightGray
        destructive -> Color.Transparent
        else -> backgroundColor ?: colors.secondary
    }
    val finalContentColor = when {
        disabled -> Color.Black
        destructive -> colors.error
        else -> contentColor ?: colors.onSecondary
    }
    val finalBorderColor = when {
        destructive -> colors.error
        borderColor != null -> borderColor
        isLocked || animationCompleted -> colors.accent
        else -> backgroundColor ?: colors.accent
    }
    val finalBottomBorderColor = when {
        destructive -> colors.error
        isLocked || animationCompleted -> colors.accent
        else -> bottomBorderColor ?: colors.accent
    }
    val finalLoadingColor = when {
        destructive -> colors.error
        else -> loadingColor ?: colors.onSecondary
    }

    LaunchedEffect(isPressed, lockable, isLocked, animationCompleted) {
        if (isPressed && lockable && !isLocked && !animationCompleted) {
            animationProgress.snapTo(0f)
            
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1800,
                    easing = LinearEasing
                )
            )
            
            if (animationProgress.value >= 1f) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                animationCompleted = true
                onLock?.invoke()
            }
        } else if (!isPressed && !animationCompleted && !isLocked) {
            animationProgress.snapTo(0f)
        }
    }

    LaunchedEffect(isLocked, loading) {
        if (isLocked && loading != true) {
            animationCompleted = true
            isPressed = true
            animationProgress.snapTo(1f)
        } else if (!isLocked && loading != true) {
            animationCompleted = false
            isPressed = false
            animationProgress.snapTo(0f)
        }
    }


    // Thin depth indicator line posit
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .zIndex(1f),
        contentAlignment = Alignment.Center
    ) {
        // Bottom border (shadow)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(elevation)
                .offset(y = elevation + pressDepth)
                .clip(shape)
                .background(finalBottomBorderColor)
                .zIndex(2f)
        )


        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = translationY.dp)
                .zIndex(3f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (lockable && !isLocked && !animationCompleted && loading != true) {
                                isPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    if (!animationCompleted) {
                                        isPressed = false
                                    }
                                }
                            }
                        },
                        onTap = {
                            if (!lockable || (!isLocked && !animationCompleted)) {
                                onClick()
                            }
                        }
                    )
                }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(
                        minWidth = ButtonDefaults.MinWidth,
                        minHeight = ButtonDefaults.MinHeight
                    )
                    .border(0.5.dp, finalBorderColor),
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
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
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
                            
                            Box(modifier = Modifier.align(Alignment.Center)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (lockable) {
                                        AnimatedContent(
                                            targetState = isLocked || animationCompleted,
                                            transitionSpec = {
                                                slideInVertically { it } togetherWith slideOutVertically { -it }
                                            },
                                            label = "lockIconTransition"
                                        ) { isLockedState ->
                                            Icon(
                                                imageVector = if (isLockedState) Icons.Default.Lock else CupertinoIcons.Filled.LockOpen,
                                                contentDescription = if (isLockedState) "Locked" else "Unlocked",
                                                modifier = Modifier.size(16.dp),
                                                tint = finalContentColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    
                                    CompositionLocalProvider(
                                        LocalTextStyle provides TextStyle(
                                            fontSize = textSize.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
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

            // Draw the lock animation border
            if (lockable && animationProgress.value > 0f) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(4f)
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    // Create rectangular border path (no rounded corners)
                    val borderPath = Path().apply {
                        // Start at bottom middle
                        moveTo(canvasWidth / 2f, canvasHeight)
                        // Go to bottom-left corner
                        lineTo(0f, canvasHeight)
                        // Go up left side
                        lineTo(0f, 0f)
                        // Go across top
                        lineTo(canvasWidth, 0f)
                        // Go down right side
                        lineTo(canvasWidth, canvasHeight)
                        // Return to bottom middle
                        lineTo(canvasWidth / 2f, canvasHeight)
                    }
                    
                    val pathMeasure = PathMeasure()
                    pathMeasure.setPath(borderPath, false)
                    val totalLength = pathMeasure.length
                    
                    val bottomCenter = 0f
                    val topCenter = totalLength / 2f
                    
                    val rightPath = Path()
                    val leftPath = Path()
                    
                    val rightProgress = animationProgress.value * topCenter
                    val leftProgress = animationProgress.value * topCenter
                    
                    // Right segment: goes from bottom center clockwise
                    pathMeasure.getSegment(
                        startDistance = bottomCenter,
                        stopDistance = bottomCenter + rightProgress,
                        destination = rightPath,
                        startWithMoveTo = true
                    )
                    
                    // Left segment: goes from bottom center counter-clockwise
                    pathMeasure.getSegment(
                        startDistance = totalLength - leftProgress,
                        stopDistance = totalLength,
                        destination = leftPath,
                        startWithMoveTo = true
                    )
                    
                    // Draw both segments
                    drawPath(
                        path = rightPath,
                        color = colors.accent,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    
                    drawPath(
                        path = leftPath,
                        color = colors.accent,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }



        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .offset(y = -4.dp)
            .background(colors.accent)
            .zIndex(0f)
    ) {
        androidx.compose.material3.Text(text = " ")
    }
}