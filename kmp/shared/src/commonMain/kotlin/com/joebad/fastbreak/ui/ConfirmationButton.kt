
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun AnimatedBorderButton(
    buttonColor: Color = Color.White,
    borderColor: Color = Color(0xFF3B82F6), // Blue color
    textColor: Color = Color(0xFF3B82F6),
    width: Int = 160,
    height: Int = 60,
    cornerRadius: Float = 12f,
    borderWidth: Float = 3f,
    content: @Composable () -> Unit
) {
    val colors = LocalColors.current;
    val hapticFeedback = LocalHapticFeedback.current

    // Animation progress (0.0 to 1.0)
    val animationProgress = remember { Animatable(0f) }

    // Button press state
    var isPressed by remember { mutableStateOf(false) }

    // Handle button press animation
    LaunchedEffect(isPressed) {
        if (isPressed) {
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

            // Animation complete - trigger haptic feedback
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            // Reset animation when not pressed
            animationProgress.snapTo(0f)
        }
    }
    Box(
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
            .padding(borderWidth.dp / 2)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    }
                )
            }
    ) {
        // Button content
        Text(
            text = "hey",
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )

        // Animated border
        Canvas(modifier = Modifier.matchParentSize()) {
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