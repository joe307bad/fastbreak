import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.joebad.fastbreak.Theme
import com.joebad.fastbreak.ThemePreference
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControl
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlDefaults
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlTab
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun ThemeSelector(themePreference: ThemePreference, onToggleTheme: (theme: Theme) -> Unit) {
    var theme by remember { mutableStateOf(Theme.Dark) }
    val colors = LocalColors.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        theme = themePreference.getTheme()
    }

    CupertinoSegmentedControl(
        colors = CupertinoSegmentedControlDefaults.colors(
            separatorColor = colors.accent,
            indicatorColor = colors.accent
        ),
        modifier = Modifier.fillMaxWidth().background(color = colors.background),
        selectedTabIndex = when (theme) {
            Theme.Light -> 0
            Theme.Dark -> 1
        }
    ) {
        CupertinoSegmentedControlTab(
            isSelected = theme == Theme.Light,
            onClick = {
                theme = Theme.Light
                coroutineScope.launch {
                    onToggleTheme(Theme.Light)
                }
            }
        ) {
            Text("Light", color = if (theme == Theme.Light) colors.onAccent else colors.text)
        }
        CupertinoSegmentedControlTab(
            isSelected = theme == Theme.Dark,
            onClick = {
                theme = Theme.Dark
                coroutineScope.launch {
                    onToggleTheme(Theme.Dark)
                }
            }
        ) {
            Text("Dark", color = if (theme == Theme.Dark) colors.onAccent else colors.text)
        }
    }
}