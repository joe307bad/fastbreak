//import com.joebad.fastbreak.res
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.Theme
import fastbreak.shared.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

@OptIn(ExperimentalResourceApi::class)
@Composable
fun FastbreakLogo(
    theme: Theme? = Theme.Light
) {
    val logoPath = when (theme) {
        Theme.Dark -> "files/fastbreak_logo.png"
        else -> "files/fastbreak_logo_light.png"
    }

    val logoImageBitmap = produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            Res.readBytes(logoPath).decodeToImageBitmap()
        }
    }.value

    logoImageBitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "logo",
            modifier = Modifier.height(200.dp)
        )
    }
}
