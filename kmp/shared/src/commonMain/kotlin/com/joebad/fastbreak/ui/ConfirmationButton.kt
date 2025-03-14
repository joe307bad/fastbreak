import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
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

@Composable
fun AnimatedBorderButton(
//    buttonColor: Color = Color.White,
    borderColor: Color = Color(0xFF3B82F6), // Blue color
    textColor: Color = Color(0xFF3B82F6),
    bottomBorderColor: Color = Color(0xCC3B82F6), // Slightly darker blue for bottom border
    width: Int = 160,
    height: Int = 60,
    cornerRadius: Float = 12f,
    borderWidth: Float = 10f,
    depthAmount: Float = 6f, // Amount of depth for the bottom border
    content: @Composable () -> Unit = {
        Text(
            text = "hey",
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
) {
    val colors = LocalColors.current
    val buttonColor = colors.primary
    val hapticFeedback = LocalHapticFeedback.current

    // Animation progress (0.0 to 1.0)
    val animationProgress = remember { Animatable(0f) }

    // Button press state
    var isPressed by remember { mutableStateOf(false) }
    // Track animation completion
    var animationCompleted by remember { mutableStateOf(false) }

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

    // Track animation completion
//    var animationCompleted by remember { mutableStateOf(false) }

    // Handle button press animation
    LaunchedEffect(isPressed) {
        if (isPressed && !animationCompleted) {
            // Reset animation state
            animationProgress.snapTo(0f)

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
//            animationCompleted = true
        } else if (!isPressed && !animationCompleted) {
            // Reset animation when not pressed (only if animation wasn't completed)
            animationProgress.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
    ) {
        // Bottom layer (creates the 3D effect)
        Box(
            modifier = Modifier.fillMaxHeight().fillMaxWidth()
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
                .background(
                    color = buttonColor,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
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
                    )
                }
                .zIndex(1f)
        ) {
            // Button content
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
                    .offset(y = (-1).dp).zIndex(1f)
            ) {
                content()
            }

            // Animated border
            Canvas(
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(cornerRadius.dp))
                    .background(color = colors.secondary).zIndex(0f)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val cr = cornerRadius

                // Create a rounded rectangle path for the button border
                // We'll create it with the starting point at the top center
                val borderPath = Path().apply {
                    moveTo(canvasWidth / 2, 0f) // Start at top center

                    // Top right
                    lineTo(canvasWidth - cr, 0f)
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

                    // Bottom side to center
                    lineTo(canvasWidth / 2, canvasHeight)

                    // Bottom side from center to left
                    lineTo(cr, canvasHeight)

                    // Bottom left corner
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

                    // Back to top center
                    lineTo(canvasWidth / 2, 0f)
                }

                // Create a PathMeasure to get the total length of the path
                val pathMeasure = PathMeasure()
                pathMeasure.setPath(borderPath, false)
                val totalLength = pathMeasure.length

                // Calculate path positions for animation
                // Start from top center (0) and end at bottom center (totalLength/2)
                val topCenter = 0f
                val bottomCenter = totalLength / 2

                // Create paths for the right and left sides
                val rightPath = Path()
                val leftPath = Path()

                // Calculate progress for both sides
                val rightProgress = animationProgress.value * bottomCenter
                val leftProgress = animationProgress.value * bottomCenter

                // Extract the right path (clockwise from top center to bottom center)
                val rightPathSegment = pathMeasure.getSegment(
                    startDistance = topCenter,
                    stopDistance = topCenter + rightProgress,
                    destination = rightPath,
                    startWithMoveTo = true
                )

                // Extract the left path (counter-clockwise from top center to bottom center)
                // For this to work properly, we need to go from the end point (totalLength)
                // and move backwards
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