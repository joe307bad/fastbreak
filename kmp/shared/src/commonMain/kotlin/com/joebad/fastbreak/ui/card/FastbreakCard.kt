import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakViewModel
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.ui.theme.darken
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.CheckmarkCircle
import io.github.alexzhirkevich.cupertino.icons.filled.XmarkApp


@Composable
fun FastbreakCard(
    title: String,
    date: String?,
    locked: Boolean,
    onDismiss: () -> Unit,
    showCloseButton: Boolean = false,
    fastbreakViewModel: FastbreakViewModel? = null,
    fastbreakResultsCard: Boolean = false
) {
    val colors = LocalColors.current;
    val state = fastbreakViewModel?.container?.stateFlow?.collectAsState()?.value;
    ThreeSectionLayout(
        header = {
            Column {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text(
                        title,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimary,
                        ),
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "ID: ${state?.cardId}",
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace, fontSize = 17.sp,
                                color = colors.onPrimary
                            )
                        )
                        Text(
                            if (fastbreakResultsCard) state?.lastLockedCardResults?.date ?: "" else date ?: "",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace, fontSize = 17.sp,
                                color = colors.onPrimary,
                                textAlign = TextAlign.End
                            )
                        )
                    }
                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )
                    Barcode()
                }
                Spacer(
                    modifier = Modifier.height(20.dp)
                )
                if (fastbreakResultsCard) {
                    val results = state?.lastLockedCardResults?.results
                    if (results != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            CupertinoIcon(
                                imageVector = CupertinoIcons.Filled.XmarkApp,
                                contentDescription = "Incorrect",
                                tint = Color.Red,
                                modifier = Modifier.size(21.dp)
                            )
                            Spacer(
                                modifier = Modifier.width(10.dp)
                            )
                            Text(
                                results.totalIncorrect.toString(),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    color = colors.onPrimary
                                )
                            )
                            Spacer(
                                modifier = Modifier.width(10.dp)
                            )
                            CupertinoIcon(
                                imageVector = CupertinoIcons.Filled.CheckmarkCircle,
                                contentDescription = "Correct",
                                tint = darken(Color.Green, 0.7f),
                                modifier = Modifier.padding(start = 10.dp).size(21.dp)
                            )
                            Spacer(
                                modifier = Modifier.width(10.dp)
                            )
                            Text(
                                results.totalCorrect.toString(),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    color = colors.onPrimary
                                )
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            SmallCircle(color = colors.accent)
                            Spacer(
                                modifier = Modifier.width(10.dp)
                            )
                            Text(
                                results.totalPoints.toString(),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    color = colors.onPrimary,
                                    textAlign = TextAlign.End
                                )
                            )
                        }
                    }
                }
                PerforatedDashedLine(color = colors.accent)
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.Start,
            ) {
                if (fastbreakResultsCard) {
                    val selection = state?.lastLockedCardResults?.selections;
                    val results = state?.lastLockedCardResults?.results;
                    selection?.forEachIndexed { index, item ->
                        val correct = results?.correct?.contains(item._id)
                        val incorrect = results?.incorrect?.contains(item._id)
                        val icon = if (correct == false && incorrect == false) null else correct
                        PickEmRow(item.type, item.description, item.points.toString(), icon)
                        if (index < selection.lastIndex) {
                            Divider(modifier = Modifier.background(color = colors.accent))
                        }
                    }
                } else {
                    state?.selections?.forEachIndexed { index, item ->
                        PickEmRow(item.type, item.description, item.points.toString())
                        if (index < state.selections.lastIndex) {
                            Divider(modifier = Modifier.background(color = colors.accent))
                        }
                    }
                }
            }
        },
        footer = {
            PerforatedDashedLine(color = colors.accent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (showCloseButton)
                        Text(
                            "CLOSE",
                            modifier = Modifier.padding(10.dp).clickable(onClick = { onDismiss() }),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                color = colors.text
                            )
                        )
                }

                AnimatedLockIcon(
                    locked = locked,
                    color = colors.text,
                    size = 17.dp
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (state != null) {
                    Text(
                        state.totalPoints.toString(),
                        modifier = Modifier.padding(10.dp),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 17.sp,
                            textAlign = TextAlign.End,
                            color = colors.text
                        )
                    )
                }
            }
//            Row {
//                Row(
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Box(
//                        modifier = Modifier
//                            .weight(1f),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        PhysicalButton(
//                            bottomBorderColor = darken(colors.secondary, 0.7f),
//                            onClick = { onDismiss() },
//                            elevation = 8.dp,
//                            pressDepth = 4.dp,
//                            backgroundColor = colors.secondary
//                        ) {
//                            Text(
//                                "LOCK CARD",
//                                color = colors.onSecondary,
//                                fontWeight = FontWeight.Bold
//                            )
//                        }
//                    }
//                    Spacer(modifier = Modifier.width(5.dp))
//                    Box(
//                        modifier = Modifier
//                            .weight(1f),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        PhysicalButton(
//                            bottomBorderColor = colors.accent,
//                            onClick = { onDismiss() },
//                            elevation = 8.dp,
//                            pressDepth = 4.dp,
//                            borderColor = colors.accent,
//                            backgroundColor = colors.background
//                        ) {
//                            Text("CANCEL", color = colors.onPrimary, fontWeight = FontWeight.Bold)
//                        }
//                    }
//                }
//            }
        }
    )
}

fun Text(totalPoints: Int, style: TextStyle) {

}
