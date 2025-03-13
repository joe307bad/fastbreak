
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.Trophy

@Composable
fun LeaderboardScreen(scrollState: ScrollState) {
    val colors = LocalColors.current;
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).verticalScroll(
                scrollState
            )
        ) {
            Title("LEADERBOARD")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                color = colors.text,
                text = "Results from Week 10 (Sept. 6th - Sept. 13th)",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            repeat(100) { index ->
                val place = index + 1;
                Row(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.height(50.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight()
                                .width(80.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight().width(60.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (index == 0)

                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)  // Fixed width for up to 3-digit numbers
                                            .height(40.dp)  // Oval shape effect
                                            .background(
                                                colors.secondary,
                                                shape = RoundedCornerShape(50)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            CupertinoIcons.Filled.Trophy,
                                            tint = colors.onSecondary,
                                            contentDescription = "Trophy"
                                        )
                                    }
                                else
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)  // Fixed width for up to 3-digit numbers
                                            .height(40.dp),  // Oval shape effect
//                                            .background(
//                                                colors.primary,
//                                                shape = RoundedCornerShape(50)
//                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$place",
                                            color = colors.onPrimary,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                            }
//                            Box(
//                                modifier = Modifier
//                                    .background(colors.accent, RoundedCornerShape(8.dp))
//                                    .padding(
//                                        horizontal = 8.dp,
//                                        vertical = 4.dp
//                                    )
//                                    .fillMaxHeight()
//                                    .width(60.dp),
//                                contentAlignment = Alignment.Center
//                            ) {

//                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column (modifier = Modifier.weight(1f)) {
                            Text(
                                text = "joebad",
                                color = colors.text,
                                fontSize = 20.sp,
                            )
                        }
                        Column {
                            Text(
                                text = "1,023",
                                color = colors.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(5.dp))
                Divider(color = colors.text)
                Spacer(modifier = Modifier.height(5.dp))
            }
        }
    }
}