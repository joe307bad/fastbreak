
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
import com.joebad.fastbreak.model.dtos.DailyLeaderboard
import com.joebad.fastbreak.ui.Title
import com.joebad.fastbreak.ui.theme.LocalColors
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.Trophy

@Composable
fun LeaderboardScreen(scrollState: ScrollState, leaderboard: DailyLeaderboard?) {
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
                text = "Highest Fastbreak cards for ${leaderboard?.dateCode}",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            leaderboard?.entries?.forEachIndexed { index, entry ->
                val place = index + 1;
                Row(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.height(50.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight()
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
                                            contentDescription = "Trophy",
                                            modifier = Modifier
                                                .width(60.dp)
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
                        }
                        Column (modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                text = entry.userId,
                                color = colors.text,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Column (modifier = Modifier.padding(start = 16.dp)) {
                            Text(
                                text = "${entry.points}",
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