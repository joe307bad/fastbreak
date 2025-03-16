import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.ui.theme.LocalColors

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedBorderButton(
    onLocked: () -> Unit = {},
    borderColor: Color = Color(0xFF3B82F6), // Blue color
    textColor: Color = Color(0xFF3B82F6),
    bottomBorderColor: Color = Color(0xCC3B82F6), // Slightly darker blue for bottom border
    width: Int = 200,
    height: Int = 60,
    cornerRadius: Float = 6f,
    borderWidth: Float = 8f,
    depthAmount: Float = 6f, // Amount of depth for the bottom border
    locked: Boolean = false, // New parameter to initialize in locked state
    unlockText: String = "Lock Card", // Text when unlocked
    lockedText: String = "Card Locked", // Text when locked
    content: @Composable ((isLocked: Boolean) -> Unit)? = null // Modified to receive lock state
) {
    val colors = LocalColors.current
    val buttonColor = colors.secondary
    val hapticFeedback = LocalHapticFeedback.current

    // Animation progress (0.0 to 1.0)
    val animationProgress = remember { Animatable(if (locked) 1f else 0f) }

    // Lock icon animation
    val lockIconOffsetX = remember { Animatable(if (locked) 0f else -50f) }
    val lockIconAlpha = remember { Animatable(1f) }

    // Button press state
    var isPressed by remember { mutableStateOf(locked) }
    // Track animation completion
    var animationCompleted by remember { mutableStateOf(locked) }

    // Text state - simply tied to whether animation is completed
    val buttonText = if (animationCompleted || locked) lockedText else unlockText

    // Animated properties for press effect
    val pressOffset by animateFloatAsState(
        targetValue = if (isPressed || animationCompleted) depthAmount * 0.85f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pressOffset"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed || animationCompleted) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed && !animationCompleted) {
            // Reset animation state if needed
            animationProgress.snapTo(0f)
            lockIconOffsetX.snapTo(-50f)
            lockIconAlpha.snapTo(1f)

            // Start animation
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                )
            )

            // Animation complete - trigger haptic feedback and mark as completed
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            animationCompleted = true
            onLocked()

            // Animate lock icon entrance
            lockIconOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 300,
                    easing = LinearEasing
                )
            )

            // Text will automatically update since we're using animationCompleted state
        } else if (!isPressed && !animationCompleted) {
            // Reset animation when not pressed (only if animation wasn't completed)
            animationProgress.snapTo(0f)
            lockIconOffsetX.snapTo(-50f)
            lockIconAlpha.snapTo(1f)
            // Text handled automatically by buttonText
        }
    }

    // Initialize in locked state if required
    LaunchedEffect(locked) {
        if (locked && !animationCompleted) {
            // Set states for locked appearance
            isPressed = true
            animationCompleted = true

            // Set animation values directly
            animationProgress.snapTo(1f)
            lockIconOffsetX.snapTo(0f)
        }
    }

    // No separate effect needed for text - it's directly tied to animationCompleted state

    Box(
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
    ) {
        // Bottom layer (creates the 3D effect)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .zIndex(0f)
                .offset(y = depthAmount.dp - 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .background(
                        color = bottomBorderColor,
                        shape = RoundedCornerShape(
                            bottomStart = cornerRadius.dp,
                            bottomEnd = cornerRadius.dp
                        )
                    )
                    .align(Alignment.BottomCenter)
            ) {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Top button layer (slides down when pressed)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = pressOffset.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .zIndex(1f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            if (!locked && !animationCompleted) {
                                isPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    // If animation completed, don't change pressed state
                                    if (!animationCompleted) {
                                        isPressed = false
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            // Button background (lower z-index)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .background(
                        color = buttonColor,
                        shape = RoundedCornerShape(cornerRadius.dp)
                    )
            )

            // Button content (middle z-index)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
            ) {
                // Use custom content if provided, otherwise use default text
//                if (content != null) {
//                    content(animationCompleted)
//                } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    AnimatedContent(
                        targetState = if (animationProgress.value >= 0.99f) lockedText else unlockText,
                        transitionSpec = {
                            slideInVertically { it } with slideOutVertically { -it }
                        },
                        label = "buttonTextTransition"
                    ) { targetText ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Show the lock icon only when locked
                            if (targetText == lockedText) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Lock",
                                    tint = colors.onSecondary,
                                    modifier = Modifier.size(17.dp)
                                ) // Space between text and icon
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Text(
                                text = targetText.uppercase(),
                                color = colors.onSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
//                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
//                }

                // Lock icon that overlays without affecting layout
                if (animationCompleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cornerRadius.dp))
                            .padding(start = 5.dp)
                    ) {
                        // Lock icon would go here
                        // Originally commented out in the provided code
                    }
                }
            }

            // Animated border (highest z-index)
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(3f)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val cr = cornerRadius

                // Create a rounded rectangle path for the button border
                // Now we'll create it with the starting point at the bottom center
                val borderPath = Path().apply {
                    moveTo(canvasWidth / 2, canvasHeight) // Start at bottom center

                    // Bottom left
                    lineTo(cr, canvasHeight)
                    arcTo(
                        rect = Rect(
                            left = 0f,
                            top = canvasHeight - 2 * cr,
                            right = 2 * cr,
                            bottom = canvasHeight
                        ),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )

                    // Left side
                    lineTo(0f, cr)

                    // Top left corner
                    arcTo(
                        rect = Rect(
                            left = 0f,
                            top = 0f,
                            right = 2 * cr,
                            bottom = 2 * cr
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )

                    // Top side
                    lineTo(canvasWidth - cr, 0f)

                    // Top right corner
                    arcTo(
                        rect = Rect(
                            left = canvasWidth - 2 * cr,
                            top = 0f,
                            right = canvasWidth,
                            bottom = 2 * cr
                        ),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )

                    // Right side
                    lineTo(canvasWidth, canvasHeight - cr)

                    // Bottom right corner
                    arcTo(
                        rect = Rect(
                            left = canvasWidth - 2 * cr,
                            top = canvasHeight - 2 * cr,
                            right = canvasWidth,
                            bottom = canvasHeight
                        ),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )

                    // Back to bottom center
                    lineTo(canvasWidth / 2, canvasHeight)
                }

                // Create a PathMeasure to get the total length of the path
                val pathMeasure = PathMeasure()
                pathMeasure.setPath(borderPath, false)
                val totalLength = pathMeasure.length

                // Calculate path positions for animation
                // Start from bottom center (0) and end at top center (totalLength/2)
                val bottomCenter = 0f
                val topCenter = totalLength / 2

                // Create paths for the right and left sides
                val rightPath = Path()
                val leftPath = Path()

                // Calculate progress for both sides
                val rightProgress = animationProgress.value * topCenter
                val leftProgress = animationProgress.value * topCenter

                // Extract the right path (clockwise from bottom center to top center)
                val rightPathSegment = pathMeasure.getSegment(
                    startDistance = bottomCenter,
                    stopDistance = bottomCenter + rightProgress,
                    destination = rightPath,
                    startWithMoveTo = true
                )

                // Extract the left path (counter-clockwise from bottom center to top center)
                val leftPathSegment = pathMeasure.getSegment(
                    startDistance = totalLength - leftProgress,
                    stopDistance = totalLength,
                    destination = leftPath,
                    startWithMoveTo = true
                )

                // Draw the paths
                drawPath(
                    path = rightPath,
                    color = borderColor,
                    style = Stroke(
                        width = borderWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                drawPath(
                    path = leftPath,
                    color = borderColor,
                    style = Stroke(
                        width = borderWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}