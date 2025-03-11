import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * A button composable with a physical, 3D-like appearance and interaction.
 *
 * @param onClick Function to execute when the button is clicked
 * @param modifier Modifier for the button
 * @param enabled Whether the button is enabled
 * @param elevation The resting elevation of the button in dp
 * @param pressDepth How deep the button goes when pressed in dp
 * @param backgroundColor The main color of the button
 * @param contentColor The color of the content (text) on the button
 * @param bottomBorderColor The color of the bottom border that creates the 3D effect
 * @param shape The shape of the button
 * @param textSize The size of the text inside the button in sp
 * @param content The content of the button
 */
@Composable
fun PhysicalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    elevation: Dp = 6.dp,
    pressDepth: Dp = 3.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    bottomBorderColor: Color = MaterialTheme.colorScheme.primaryContainer,
    borderColor: Color? = null,
    shape: Shape = RectangleShape,
    textSize: Int = 18,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val translationY by animateFloatAsState(
        targetValue = if (isPressed) pressDepth.value else 0f,
        label = "translationY"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().padding(bottom = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(elevation)
                .offset(y = 23.dp)
                .clip(shape)
                .background(bottomBorderColor)
                .zIndex(2f)
        )

        Surface(
            onClick = if (enabled) onClick else {
                {}
            },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(
                    minWidth = ButtonDefaults.MinWidth,
                    minHeight = ButtonDefaults.MinHeight
                )
                .offset(y = translationY.dp)
                .zIndex(3f)
                .then(
                    if (borderColor != null) Modifier.border(
                        2.dp,
                        borderColor
                    ) else Modifier.border(2.dp, backgroundColor)
                ),
            shape = shape,
            color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.6f),
            contentColor = contentColor,
            interactionSource = interactionSource,
            enabled = enabled
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = ButtonDefaults.MinWidth,
                            minHeight = ButtonDefaults.MinHeight
                        )
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
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