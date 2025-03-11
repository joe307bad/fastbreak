import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.ui.theme.LocalColors
import kotlin.random.Random

@Composable
fun SmallCircle(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .background(color, shape = CircleShape)
            .border(width = 1.dp, color = Color.Black, shape = CircleShape)
    )
}


@Composable
fun PerforatedDashedLine(
    modifier: Modifier = Modifier,
    dashWidth: Dp = 8.dp,
    gapWidth: Dp = 6.dp,
    color: Color = Color.Gray,
    thickness: Dp = 2.dp,
    highlightColor: Color = Color.White.copy(alpha = 0.6f)
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness * 2)
    ) {
        val dashPx = dashWidth.toPx()
        val gapPx = gapWidth.toPx()
        val totalWidth = size.width
        val y = size.height / 2

        var currentX = 0f

        while (currentX < totalWidth) {
            drawLine(
                color = color,
                start = Offset(currentX, y),
                end = Offset(currentX + dashPx, y),
                strokeWidth = thickness.toPx()
            )

            drawLine(
                color = highlightColor,
                start = Offset(currentX, y - thickness.toPx() / 2),
                end = Offset(currentX + dashPx, y - thickness.toPx() / 2),
                strokeWidth = (thickness.toPx() / 2)
            )

            currentX += dashPx + gapPx
        }
    }
}


@Composable
fun Barcode(
    modifier: Modifier = Modifier,
    barCount: Int = 150,
    barWidth: Dp = 4.dp,
    barHeight: Dp = 20.dp
) {
    val colors = LocalColors.current;
    val random = remember { Random }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(barCount) {
            val isBlack = random.nextBoolean()
            val barThickness = if (isBlack) barWidth else barWidth / 2
            Box(
                modifier = Modifier
                    .width(barThickness)
                    .fillMaxHeight()
                    .background(if (isBlack) colors.onPrimary else Color.Transparent)
            )
        }
    }
}

@Composable
fun ScrollableColumn(content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .verticalScroll(scrollState)
    ) {
        content()
    }
}


@Composable
fun BlurredScreen(open: Boolean, onDismiss: () -> Unit) {
    val colors = LocalColors.current;
    AnimatedVisibility(
        modifier = Modifier.zIndex(4f),
        visible = open,
        enter = fadeIn(animationSpec = tween(durationMillis = 300)),
        exit = fadeOut(animationSpec = tween(durationMillis = 200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 10.dp, start = 20.dp, end = 20.dp),
                shape = RoundedCornerShape(topEnd = 16.dp, topStart = 16.dp)
            ) {
                MyScreen(onDismiss)
            }
        }
    }
}