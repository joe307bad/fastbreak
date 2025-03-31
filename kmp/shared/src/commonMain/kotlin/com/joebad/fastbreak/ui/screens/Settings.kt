import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.theme.LocalColors


@Composable
fun SettingsScreen(onLogout: () -> Unit) {
    val colors = LocalColors.current;
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(10.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedBorderButton(
            locked = false,
            onLocked = {},
            unlockText = "LOGOUT",
            enableLocking = false,
            borderColor = Color.Transparent,
            onPressDown = {
                onLogout()
            },
//            borderColor = colors.accent, //darken(colors.accent, 0.7f),
            bottomBorderColor = colors.accent //darken(colors.accent, 0.7f)
        )
    }
}