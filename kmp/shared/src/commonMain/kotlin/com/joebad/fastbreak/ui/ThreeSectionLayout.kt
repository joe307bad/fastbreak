
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.with
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.ui.theme.darken
import io.github.alexzhirkevich.cupertino.CupertinoIcon
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.CheckmarkCircle
import io.github.alexzhirkevich.cupertino.icons.filled.LockOpen
import io.github.alexzhirkevich.cupertino.icons.filled.XmarkApp

/**
 * AnimatedLockIcon - A composable that animates between locked and unlocked icons
 *
 * @param locked Boolean state determining whether to show the locked or unlocked icon
 * @param modifier Modifier for customizing the component's layout
 * @param color The color to tint the icon
 * @param size The size of the icon in dp
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedLockIcon(
    locked: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: Dp = 24.dp
) {
    val colors = LocalColors.current;
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(

            targetState = locked,
            transitionSpec = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(300)
                ) with slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                )
            }
        ) { isLocked ->
            if (isLocked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Lock",
                    tint = colors.onSecondary,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                CupertinoIcon(
                    imageVector = CupertinoIcons.Filled.LockOpen,
                    contentDescription = "Lock",
                    tint = colors.text,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

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
fun FastbreakCard(
    title: String,
    date: String,
    locked: Boolean,
    onDismiss: () -> Unit,
    showCloseButton: Boolean = false
) {
    val colors = LocalColors.current;
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
                            "ID: 5tK67O8uV",
                            modifier = Modifier.weight(1f),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace, fontSize = 17.sp,
                                color = colors.onPrimary
                            )
                        )
                        Text(
                            date,
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    CupertinoIcon(
                        imageVector = CupertinoIcons.Filled.XmarkApp,
                        contentDescription = "Lock",
                        tint = Color.Red,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )
                    Text(
                        "20",
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
                        contentDescription = "Lock",
                        tint = darken(Color.Green, 0.7f),
                        modifier = Modifier.padding(start = 10.dp).size(21.dp)
                    )
                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )
                    Text(
                        "1,345",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            color = colors.onPrimary
                        )
                    )
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
                        CupertinoIcon(
                            imageVector = CupertinoIcons.Filled.XmarkApp,
                            contentDescription = "Lock",
                            tint = Color.Red,
                            modifier = Modifier.padding(start = 10.dp).size(21.dp)
                        )
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
                        CupertinoIcon(
                            imageVector = CupertinoIcons.Filled.CheckmarkCircle,
                            contentDescription = "Lock",
                            tint = darken(Color.Green, 0.7f),
                            modifier = Modifier.padding(start = 10.dp).size(21.dp)
                        )
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
            PerforatedDashedLine()
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
                Text(
                    "100",
                    modifier = Modifier.padding(10.dp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 17.sp,
                        textAlign = TextAlign.End,
                        color = colors.text
                    )
                )
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