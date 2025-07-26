import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.ui.theme.darken
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.CheckmarkCircle
import io.github.alexzhirkevich.cupertino.icons.filled.XmarkApp

@Composable
fun PickEmRow(title: String, description: String, points: String, correct: Boolean? = null) {
    val colors = LocalColors.current;

    Column(modifier = Modifier.padding(20.dp)) {
        Row {
            when (title) {
                "FEATURED PICK-EM" -> SmallCircle(color = colors.accent)
                "PICK-EM" -> SmallCircle(color = colors.primary)
                "TRIVIA" -> SmallCircle(color = colors.secondary)
                else -> println("Unknown type")
            }
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
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            when (correct) {
                true -> CupertinoIcon(
                    imageVector = CupertinoIcons.Filled.CheckmarkCircle,
                    contentDescription = "Correct",
                    tint = darken(Color.Green, 0.7f),
                    modifier = Modifier.size(21.dp)
                )
                false -> CupertinoIcon(
                    imageVector = CupertinoIcons.Filled.XmarkApp,
                    contentDescription = "Incorrect",
                    tint = Color.Red,
                    modifier = Modifier.size(21.dp)
                )
                null -> {}
            }
            Spacer(
                modifier = Modifier.width(10.dp)
            )
            Text(
                points,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 17.sp,
                    textAlign = TextAlign.End,
                    color = colors.onPrimary
                )
            )
        }
    }
}