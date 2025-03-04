
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.createFontLoader

@Composable
fun Title() {
    val fontLoader = createFontLoader()
    val customFont = fontLoader.loadFont("CodeBold")

    androidx.compose.material.Text(
        fontSize = 50.sp,
        text = "FASTBREAK",
        fontWeight = FontWeight.Bold,
        fontFamily = customFont,
        color = Color.White,
        modifier = Modifier.padding(top = 5.dp)
    )
}

@Composable
fun RoundedBottomHeaderBox(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
//            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(MaterialTheme.colors.primary)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Title()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ScreenWithRoundedBottomHeader() {
    Box(modifier = Modifier.fillMaxSize()) {
        // This header will have full bleed to the top and rounded bottom corners
        RoundedBottomHeaderBox(
            title = "Welcome",
            subtitle = "Your journey begins here",
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Your other content goes here
    }
}