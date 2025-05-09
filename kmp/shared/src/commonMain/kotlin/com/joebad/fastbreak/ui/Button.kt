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

@Composable
fun PhysicalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disabled: Boolean = false, // New Disabled prop
    elevation: Dp = 6.dp,
    pressDepth: Dp = 3.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    bottomBorderColor: Color = MaterialTheme.colorScheme.primaryContainer,
    borderColor: Color? = null,
    shape: Shape = RectangleShape,
    textSize: Int = 18,
    loading: Boolean = false,
    zIndex: Float = 0f,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // If disabled, the button starts in the pressed position
    val translationY by animateFloatAsState(
        targetValue = if (disabled || isPressed) pressDepth.value else 0f,
        label = "translationY"
    )

    // Apply different styles when disabled
    val finalBackgroundColor = if (disabled) Color.LightGray else backgroundColor
    val finalContentColor = if (disabled) Color.Black else contentColor

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
                .background(bottomBorderColor)
                .zIndex(2f)
        )

        Surface(
//            onClick = if (enabled && !disabled) onClick else {
//                {}
//            },
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
                    ) else Modifier.border(2.dp, finalBackgroundColor)
                )
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
//            interactionSource = interactionSource,
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
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterStart).size(20.dp)
                                    .offset(x = (-3).dp), strokeWidth = 3.dp
                            )
                        }
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
