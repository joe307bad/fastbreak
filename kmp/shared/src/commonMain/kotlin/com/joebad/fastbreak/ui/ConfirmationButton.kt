
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
// Supports bidirectional transitions: unlocked -> locked and locked -> unlocked
fun AnimatedBorderButton(
    onLocked: () -> Unit = {},
    borderColor: Color = Color(0xFF3B82F6),
    textColor: Color = Color(0xFF3B82F6),
    bottomBorderColor: Color = Color(0xCC3B82F6),
    width: Int = 200,
    height: Int = 60,
    cornerRadius: Float = 6f,
    borderWidth: Float = 8f,
    depthAmount: Float = 6f,
    locked: Boolean = false,
    isLoading: Boolean = false,
    unlockText: String = "Lock Card",
    lockedText: String = "Card Locked",
    loadingText: String = "Locking...",
    enableLocking: Boolean? = true,
    onPressDown: () -> Unit = {},
    fullWidth: Boolean = false,
    content: @Composable ((isLocked: Boolean) -> Unit)? = null
) {
    val colors = LocalColors.current
    val buttonColor = colors.secondary
    val hapticFeedback = LocalHapticFeedback.current

    val animationProgress = remember { Animatable(if (locked) 1f else 0f) }

    val lockIconOffsetX = remember { Animatable(if (locked) 0f else -50f) }
    val lockIconAlpha = remember { Animatable(1f) }

    var isPressed by remember { mutableStateOf(locked) }
    var animationCompleted by remember { mutableStateOf(locked) }

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

    LaunchedEffect(isPressed, enableLocking) {

        if (isPressed && !animationCompleted) {
            if (enableLocking == false) {
                onPressDown();
                return@LaunchedEffect;
            }
            animationProgress.snapTo(0f)
            lockIconOffsetX.snapTo(-50f)
            lockIconAlpha.snapTo(1f)

            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                )
            )

            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

            if (enableLocking == true) {
                animationCompleted = true
                onLocked()

                lockIconOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = LinearEasing
                    )
                )
            }

        } else if (!isPressed && !animationCompleted) {
            animationProgress.snapTo(0f)
            lockIconOffsetX.snapTo(-50f)
            lockIconAlpha.snapTo(1f)
        }
    }

    LaunchedEffect(locked, isLoading) {
        if (!locked && !isLoading) {
            animationCompleted = false
            isPressed = false

            animationProgress.snapTo(0f)
            lockIconOffsetX.snapTo(-50f)
            lockIconAlpha.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier
            .let { if (fullWidth) it.fillMaxWidth() else it.width(width.dp) }
            .height(height.dp)
    ) {
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
                            if (!locked && !animationCompleted && !isLoading) {
                                isPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    if (!animationCompleted) {
                                        isPressed = false
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .background(
                        color = buttonColor,
                        shape = RoundedCornerShape(cornerRadius.dp)
                    )
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    AnimatedContent(
                        targetState = when {
                            isLoading || animationProgress.value >= 0.99f -> lockedText
                            else -> unlockText
                        },
                        transitionSpec = {
                            slideInVertically { it } with slideOutVertically { -it }
                        },
                        label = "buttonTextTransition"
                    ) { targetText ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                isLoading || targetText == lockedText -> {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Lock",
                                        tint = colors.onSecondary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                            }
                            Text(
                                text = targetText.uppercase(),
                                color = colors.onSecondary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (animationCompleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cornerRadius.dp))
                            .padding(start = 5.dp)
                    ) {
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(3f)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val cr = cornerRadius

                val borderPath = Path().apply {
                    moveTo(canvasWidth / 2, canvasHeight)

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

                    lineTo(0f, cr)

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

                    lineTo(canvasWidth, canvasHeight - cr)

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

                    lineTo(canvasWidth / 2, canvasHeight)
                }

                val pathMeasure = PathMeasure()
                pathMeasure.setPath(borderPath, false)
                val totalLength = pathMeasure.length

                val bottomCenter = 0f
                val topCenter = totalLength / 2

                val rightPath = Path()
                val leftPath = Path()

                val rightProgress = animationProgress.value * topCenter
                val leftProgress = animationProgress.value * topCenter

                val rightPathSegment = pathMeasure.getSegment(
                    startDistance = bottomCenter,
                    stopDistance = bottomCenter + rightProgress,
                    destination = rightPath,
                    startWithMoveTo = true
                )

                val leftPathSegment = pathMeasure.getSegment(
                    startDistance = totalLength - leftProgress,
                    stopDistance = totalLength,
                    destination = leftPath,
                    startWithMoveTo = true
                )

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