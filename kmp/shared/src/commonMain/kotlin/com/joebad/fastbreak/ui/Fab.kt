
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.LockOpen

@Composable
fun FABWithExactShapeBorder() {
    val colors = LocalColors.current

    // Create infinite transition for animation
    val infiniteTransition = rememberInfiniteTransition()
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // The segment length as a fraction of the total path length
    val segmentLength = 0.15f

    // Custom modifier that draws a precise traveling border
    val preciseBorderModifier = Modifier.drawWithCache {
        // Create a path that follows the exact shape of the FAB (including rounded corners)
        val borderPath = Path()
        val outlineShape = CircleShape.createOutline(size, layoutDirection, this)
        borderPath.addOutline(outlineShape)

        // Create a path measure to calculate positions along the path
        val pathMeasure = PathMeasure()
        pathMeasure.setPath(borderPath, true) // true = closed path

        val totalLength = pathMeasure.length
        val dashLength = totalLength * segmentLength
        val dashPhase = totalLength * progress

        // Create the effect that only shows a segment of the path
        val pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashLength, totalLength - dashLength),
            dashPhase
        )

        onDrawWithContent {
            // First draw the content
            drawContent()

            // Then draw our precise border segment
            drawPath(
                path = borderPath,
                color = colors.accent,
                style = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = pathEffect
                )
            )
        }
    }

    // Apply the custom modifier to the FAB
    FloatingActionButton(
        modifier = preciseBorderModifier.padding(0.dp),
        onClick = { /* Your click handler */ },
        backgroundColor = colors.primary,
        contentColor = colors.onPrimary
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(20.dp)
        ) {
            CupertinoIcon(
                imageVector = CupertinoIcons.Filled.LockOpen,
                contentDescription = "Lock",
                tint = colors.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "2,324",
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )
        }
    }
}