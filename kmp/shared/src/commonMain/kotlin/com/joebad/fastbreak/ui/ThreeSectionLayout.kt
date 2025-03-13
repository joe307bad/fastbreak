import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.ui.theme.darken

@Composable
fun ThreeSectionLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    footer: @Composable () -> Unit
) {
    val colors = LocalColors.current;
    Column(
        modifier = modifier.fillMaxSize().background(color = colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            header()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        ) {
            footer()
        }
    }
}

@Composable
fun MyScreen(onDismiss: () -> Unit) {
    val colors = LocalColors.current;
    ThreeSectionLayout(
        header = {
            Column {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "My Daily Fastbreak Card",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimary
                        )
                    )
                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )
                    Text(
                        "ID: 5tK67O8uVEHUsqe3VQZfKv",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 17.sp,
                            color = colors.onPrimary
                        )
                    )
                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )
                    Barcode()
                }
                PerforatedDashedLine()
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.Start,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row {
                        SmallCircle(color = colors.accent)
                        Spacer(
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            "FEATURED PICK-EM",
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
                        "Philadelphia Eagles to win against the Pittsburgh Steelers",
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
                        "1,000",
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 17.sp,
                            textAlign = TextAlign.End,
                            color = colors.onPrimary
                        )
                    )
                }
                Divider(modifier = Modifier.background(color = colors.onPrimary))
                Column(modifier = Modifier.padding(20.dp)) {
                    Row {
                        SmallCircle(color = colors.secondary)
                        Spacer(
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            "PICK-EM",
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
                        "Pittsburgh Penguins to win against the Minnesota Wild",
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
                        "100",
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
        },
        footer = {
            Row {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        PhysicalButton(
                            bottomBorderColor = darken(colors.secondary, 0.7f),
                            onClick = { onDismiss() },
                            elevation = 8.dp,
                            pressDepth = 4.dp,
                            backgroundColor = colors.secondary
                        ) {
                            Text(
                                "LOCK CARD",
                                color = colors.onSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        PhysicalButton(
                            bottomBorderColor = colors.accent,
                            onClick = { onDismiss() },
                            elevation = 8.dp,
                            pressDepth = 4.dp,
                            borderColor = colors.accent,
                            backgroundColor = colors.background
                        ) {
                            Text("CANCEL", color = colors.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    )
}