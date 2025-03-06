
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.createFontLoader
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun Title(title: String, animatedAlpha: Float) {
    val fontLoader = createFontLoader()
    val customFont = fontLoader.loadFont("CodeBold")
    val colors = LocalColors.current;

    androidx.compose.material.Text(
        fontSize = 30.sp,
        text = title,
        fontWeight = FontWeight.Bold,
        fontFamily = customFont,
        color = colors.onPrimary.copy(alpha = animatedAlpha),
        modifier = Modifier.padding(top = 4.5.dp)
    )
}

@Composable
fun RoundedBottomHeaderBox(
    title: String,
    subtitle: String,
    secondText: String,
    animatedAlpha: Float
) {
    val colors = LocalColors.current;

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.primary)
            .padding(24.dp)
            .height(120.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Title(title, animatedAlpha)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = colors.onPrimary.copy(alpha = animatedAlpha),
                textAlign = TextAlign.Center
            )
            Text(
                text = secondText,
                color = colors.onPrimary.copy(alpha = animatedAlpha),
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}