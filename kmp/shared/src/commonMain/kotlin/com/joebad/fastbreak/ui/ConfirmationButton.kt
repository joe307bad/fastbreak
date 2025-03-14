
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AnimatedBorderButton(
    text: String = "Hold Me",
    buttonColor: Color = Color.White,
    borderColor: Color = Color(0xFF3B82F6), // Blue color
    textColor: Color = Color(0xFF3B82F6),
    width: Int = 160,
    height: Int = 60,
    cornerRadius: Float = 12f,
    borderWidth: Float = 3f
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Animation progress (0.0 to 1.0)
    val animationProgress = remember { Animatable(0f) }

    // Button press state
    var isPressed by remember { mutableStateOf(false) }

    // Animation complete state for haptic feedback
    var isAnimationComplete by remember { mutableStateOf(false) }

    // Handle button press animation
    LaunchedEffect(isPressed) {
        if (isPressed) {
            // Reset animation state
            animationProgress.snapTo(0f)
            isAnimationComplete = false

            // Start animation
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                )
            )

            // Animation complete - trigger haptic feedback
            isAnimationComplete = true
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            // Reset animation when not pressed
            animationProgress.snapTo(0f)
            isAnimationComplete = false
        }
    }

    Box(
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
            .padding(borderWidth.dp)
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
            .drawWithContent {
                // Draw the button content
                drawContent()

                // Calculate lengths for top, right, bottom, and left sides
                val buttonWidth = size.width
                val buttonHeight = size.height

                val topLength = buttonWidth
                val rightLength = buttonHeight
                val bottomLength = buttonWidth
                val leftLength = buttonHeight

                val totalLength = 2 * (topLength + rightLength)
                val halfLength = totalLength / 2

                // Calculate current stroke length based on animation progress
                val currentLength = halfLength * animationProgress.value

                // Starting point at top center
                val startX = buttonWidth / 2
                val startY = 0f

                // Border radius adjustment
                val cr = cornerRadius

                // Draw the right-going path (clockwise)
                if (currentLength > 0) {
                    val rightPath = Path().apply {
                        moveTo(startX, startY)

                        var remainingLength = currentLength

                        // Draw top right part
                        val topRightLength = topLength / 2
                        if (remainingLength <= topRightLength) {
                            // Partial line
                            lineTo(startX + remainingLength, startY)
                            remainingLength = 0f
                        } else {
                            // Full line to top right corner (minus corner radius)
                            lineTo(buttonWidth - cr, startY)
                            remainingLength -= topRightLength - cr

                            // Top right corner arc
                            if (remainingLength > 0) {
                                val arcLength = cr * 1.57f // Approx PI/2
                                if (remainingLength <= arcLength) {
                                    // Partial arc - approximate with line to simplify
                                    val angle = remainingLength / arcLength * 90f

                                    // Calculate position without Math functions
                                    val angleNormalized = angle / 90f // 0 to 1 range
                                    // Approximate sin and cos for 0-90 degrees
                                    val sinAngle = angleNormalized // Approximation for small angles
                                    val cosAngle = 1f - angleNormalized * angleNormalized / 2f // Approximation for small angles

                                    lineTo(
                                        buttonWidth - cr + cr * sinAngle,
                                        cr - cr * cosAngle
                                    )
                                    remainingLength = 0f
                                } else {
                                    // Full arc
                                    arcTo(
                                        rect = androidx.compose.ui.geometry.Rect(
                                            left = buttonWidth - cr * 2,
                                            top = 0f,
                                            right = buttonWidth,
                                            bottom = cr * 2
                                        ),
                                        startAngleDegrees = 270f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    remainingLength -= arcLength
                                }
                            }

                            // Right side
                            if (remainingLength > 0) {
                                val rightSideLength = rightLength - 2 * cr
                                if (remainingLength <= rightSideLength) {
                                    // Partial line
                                    lineTo(buttonWidth, cr + (remainingLength / rightSideLength) * (buttonHeight - 2 * cr))
                                    remainingLength = 0f
                                } else {
                                    // Full line
                                    lineTo(buttonWidth, buttonHeight - cr)
                                    remainingLength -= rightSideLength

                                    // Bottom right corner arc
                                    if (remainingLength > 0) {
                                        val arcLength = cr * 1.57f // Approx PI/2
                                        if (remainingLength <= arcLength) {
                                            // Partial arc - approximate with line
                                            val angle = remainingLength / arcLength * 90f

                                            // Calculate position without Math functions
                                            val angleNormalized = angle / 90f // 0 to 1 range
                                            // Approximate sin and cos for 0-90 degrees
                                            val sinAngle = angleNormalized // Approximation for small angles
                                            val cosAngle = 1f - angleNormalized * angleNormalized / 2f // Approximation for small angles

                                            lineTo(
                                                buttonWidth - cr * sinAngle,
                                                buttonHeight - cr + cr * cosAngle
                                            )
                                            remainingLength = 0f
                                        } else {
                                            // Full arc
                                            arcTo(
                                                rect = androidx.compose.ui.geometry.Rect(
                                                    left = buttonWidth - cr * 2,
                                                    top = buttonHeight - cr * 2,
                                                    right = buttonWidth,
                                                    bottom = buttonHeight
                                                ),
                                                startAngleDegrees = 0f,
                                                sweepAngleDegrees = 90f,
                                                forceMoveTo = false
                                            )
                                            remainingLength -= arcLength
                                        }
                                    }

                                    // Bottom part
                                    if (remainingLength > 0) {
                                        val bottomRightLength = bottomLength / 2
                                        if (remainingLength <= bottomRightLength) {
                                            // Partial line
                                            lineTo(buttonWidth - remainingLength, buttonHeight)
                                            remainingLength = 0f
                                        } else {
                                            // Full line to center bottom
                                            lineTo(buttonWidth / 2, buttonHeight)
                                            remainingLength -= bottomRightLength
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Draw the right path
                    drawPath(
                        path = rightPath,
                        color = borderColor,
                        style = Stroke(
                            width = borderWidth,
                            cap = StrokeCap.Round
                        )
                    )
                }

                // Draw the left-going path (counter-clockwise)
                if (currentLength > 0) {
                    val leftPath = Path().apply {
                        moveTo(startX, startY)

                        var remainingLength = currentLength

                        // Draw top left part
                        val topLeftLength = topLength / 2
                        if (remainingLength <= topLeftLength) {
                            // Partial line
                            lineTo(startX - remainingLength, startY)
                            remainingLength = 0f
                        } else {
                            // Full line to top left corner (plus corner radius)
                            lineTo(cr, startY)
                            remainingLength -= topLeftLength - cr

                            // Top left corner arc
                            if (remainingLength > 0) {
                                val arcLength = cr * 1.57f // Approx PI/2
                                if (remainingLength <= arcLength) {
                                    // Partial arc - approximate with line
                                    val angle = remainingLength / arcLength * 90f

                                    // Calculate position without Math functions
                                    val angleNormalized = angle / 90f // 0 to 1 range
                                    // Approximate sin and cos for 0-90 degrees
                                    val sinAngle = angleNormalized // Approximation for small angles
                                    val cosAngle = 1f - angleNormalized * angleNormalized / 2f // Approximation for small angles

                                    lineTo(
                                        cr - cr * sinAngle,
                                        cr - cr * cosAngle
                                    )
                                    remainingLength = 0f
                                } else {
                                    // Full arc
                                    arcTo(
                                        rect = androidx.compose.ui.geometry.Rect(
                                            left = 0f,
                                            top = 0f,
                                            right = cr * 2,
                                            bottom = cr * 2
                                        ),
                                        startAngleDegrees = 0f,
                                        sweepAngleDegrees = -90f,
                                        forceMoveTo = false
                                    )
                                    remainingLength -= arcLength
                                }
                            }

                            // Left side
                            if (remainingLength > 0) {
                                val leftSideLength = leftLength - 2 * cr
                                if (remainingLength <= leftSideLength) {
                                    // Partial line
                                    lineTo(0f, cr + (remainingLength / leftSideLength) * (buttonHeight - 2 * cr))
                                    remainingLength = 0f
                                } else {
                                    // Full line
                                    lineTo(0f, buttonHeight - cr)
                                    remainingLength -= leftSideLength

                                    // Bottom left corner arc
                                    if (remainingLength > 0) {
                                        val arcLength = cr * 1.57f // Approx PI/2
                                        if (remainingLength <= arcLength) {
                                            // Partial arc - approximate with line
                                            val angle = remainingLength / arcLength * 90f

                                            // Calculate position without Math functions
                                            val angleNormalized = angle / 90f // 0 to 1 range
                                            // Approximate sin and cos for 0-90 degrees
                                            val sinAngle = angleNormalized // Approximation for small angles
                                            val cosAngle = 1f - angleNormalized * angleNormalized / 2f // Approximation for small angles

                                            lineTo(
                                                cr * sinAngle,
                                                buttonHeight - cr + cr * cosAngle
                                            )
                                            remainingLength = 0f
                                        } else {
                                            // Full arc
                                            arcTo(
                                                rect = androidx.compose.ui.geometry.Rect(
                                                    left = 0f,
                                                    top = buttonHeight - cr * 2,
                                                    right = cr * 2,
                                                    bottom = buttonHeight
                                                ),
                                                startAngleDegrees = 180f,
                                                sweepAngleDegrees = 90f,
                                                forceMoveTo = false
                                            )
                                            remainingLength -= arcLength
                                        }
                                    }

                                    // Bottom part
                                    if (remainingLength > 0) {
                                        val bottomLeftLength = bottomLength / 2
                                        if (remainingLength <= bottomLeftLength) {
                                            // Partial line
                                            lineTo(remainingLength, buttonHeight)
                                            remainingLength = 0f
                                        } else {
                                            // Full line to center bottom
                                            lineTo(buttonWidth / 2, buttonHeight)
                                            remainingLength -= bottomLeftLength
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Draw the left path
                    drawPath(
                        path = leftPath,
                        color = borderColor,
                        style = Stroke(
                            width = borderWidth,
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
    ) {
        // Button content
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}