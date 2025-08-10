import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.Theme
import com.joebad.fastbreak.ThemePreference
import com.joebad.fastbreak.ui.PhysicalButton
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.ui.theme.darken
import com.joebad.fastbreak.util.AppVersion


@Composable
fun SettingsScreen(
    lastFetchedDate: Long,
    onSync: () -> Unit,
    themePreference: ThemePreference,
    onToggleTheme: (theme: Theme) -> Unit,
    error: String? = null
) {
    val colors = LocalColors.current;
    Column(
        modifier = Modifier.fillMaxSize().background(colors.background).padding(10.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Divider()
            Spacer(modifier = Modifier.height(10.dp))
            Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                Text(
                    text = "Last sync date:",
                    color = colors.text,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().offset(y=5.dp)
                )
                Text(
                    text = formatEpochSecondsToDate(lastFetchedDate),
                    color = colors.text,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(5.dp).fillMaxWidth()
                )
                PhysicalButton(
                    bottomBorderColor = darken(colors.secondary, 0.7f),
                    onClick = { onSync() },
                    elevation = 8.dp,
                    pressDepth = 4.dp,
                    backgroundColor = colors.secondary

                ) {
                    Text(
                        "SYNC",
                        color = colors.onSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                error?.let { errorMessage ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = colors.error,
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(10.dp))
            Divider()
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Theme",
                color = colors.text,
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(5.dp).fillMaxWidth()
            )
            ThemeSelector(themePreference = themePreference, onToggleTheme = onToggleTheme)
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = "v${AppVersion.getVersionName()}",
            color = colors.text.copy(alpha = 0.6f),
            style = MaterialTheme.typography.caption,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
        
    }
}