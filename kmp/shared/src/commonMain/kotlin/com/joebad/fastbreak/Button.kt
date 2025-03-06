package com.joebad.fastbreak
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.Heart

@Composable
fun IconButtonWithText() {
    var isPressed by remember { mutableStateOf(false) }

    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 0f else 4f,
        label = "elevation"
    )

    val offsetY by animateFloatAsState(
        targetValue = if (isPressed) 2f else 0f,
        label = "offset"
    )

    val colors = LocalColors.current;
    Box(
        modifier = Modifier
            .size(44.dp)
            .offset(y = offsetY.dp)
            .shadow(
                elevation = elevation.dp,
                shape = RoundedCornerShape(8.dp)
            )
            .background(colors.background, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF007AFF), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val db = FastBreakDatabase();
                        db.run {
                            db.createDb();
                        }
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        CupertinoIcon(
            imageVector = CupertinoIcons.Filled.Heart,
            contentDescription = "Heart",
            tint = Color.Red,
            modifier = Modifier.size(24.dp)
        )
    }
}