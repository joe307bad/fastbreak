import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun PickEmRow(title: String, description: String, points: String) {
    val colors = LocalColors.current;

    Column(modifier = Modifier.padding(20.dp)) {
        Row {
            when (title) {
                "FEATURED PICK-EM" -> SmallCircle(color = colors.accent)
                "PICK-EM" -> SmallCircle(color = colors.primary)
                "TRIVIA" -> SmallCircle(color = colors.secondary)
                else -> println("Unknown type")
            }
//                        CupertinoIcon(
//                            imageVector = CupertinoIcons.Filled.XmarkApp,
//                            contentDescription = "Lock",
//                            tint = Color.Red,
//                            modifier = Modifier.padding(start = 10.dp).size(21.dp)
//                        )
            Spacer(
                modifier = Modifier.width(20.dp)
            )
            Text(
                title,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 17.sp,
                    color = colors.onPrimary
                )
            )
        }
        Spacer(
            modifier = Modifier.height(20.dp)
        )
        Text(
            description,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = colors.onPrimary
            )
        )
        Spacer(
            modifier = Modifier.height(20.dp)
        )
        Text(
            points,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
                textAlign = TextAlign.End,
                color = colors.onPrimary
            )
        )
    }
}