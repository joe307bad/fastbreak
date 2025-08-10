import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.Theme

@Composable
fun FastbreakLogo(
    theme: Theme? = Theme.Light
) {
    println("🔄 FastbreakLogo using fallback text logo for stability")
    
    // Using fallback text logo to avoid iOS resource loading crashes
    Box(
        modifier = Modifier
            .size(200.dp)
            .background(
                color = when (theme) {
                    Theme.Dark -> Color(0xFF1E1E1E)
                    else -> Color(0xFFF5F5F5)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🏀",
                fontSize = 48.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "FASTBREAK",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = when (theme) {
                    Theme.Dark -> Color.White
                    else -> Color.Black
                },
                textAlign = TextAlign.Center
            )
        }
    }
}
