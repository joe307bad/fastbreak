
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.ThemePreference
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.ui.theme.darken

data class StatSheetItem(
    val highlight: Boolean,
    val leftColumnText: String,
    val rightColumnText: String,
)

val statSheetItems = listOf(
    StatSheetItem(true, "1,900,065", "My Week 10 score"),
    StatSheetItem(false, "10,065", "Days in a row locking in your FastBreak card"),
    StatSheetItem(false, "365", "Your highest Fastbreak card"),
    StatSheetItem(false, "123", "Days in a row with a winning pick"),
    StatSheetItem(false, "34", "Number of perfect Fastbreak cards"),
    StatSheetItem(false, "87", "Number of weekly wins"),
)

@Composable
fun StatSheetRow(highlight: Boolean, leftColumnText: String, rightColumnText: String) {
    val colors = LocalColors.current;
    Row(
        modifier = Modifier.height(50.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(100.dp).height(50.dp), //.background(color = Color.Red),
//            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (highlight) colors.secondary else colors.accent,
                        RoundedCornerShape(8.dp)
                    )
                    .height(45.dp)
                    .fillMaxWidth()
                    .zIndex(2f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = leftColumnText,
                    color = if (highlight) colors.onSecondary else colors.onAccent,
                    style = MaterialTheme.typography.body1,
                )
            }
            Box(
                modifier = Modifier
                    .zIndex(1f)
                    .width(98.dp)
                    .offset(y = (-1).dp)
                    .align(Alignment.BottomCenter)
//                    .offset(y = (-13).dp)
                    .background(
                        if (highlight) darken(
                            colors.secondary,
                            0.7f
                        ) else darken(colors.accent, 0.7f),
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    )
            ) {
                Spacer(modifier = Modifier.height(15.dp))
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = rightColumnText,
            color = colors.text,
            style = MaterialTheme.typography.body1
        )
    }
}

@Composable
fun DrawerContent(
    themePreference: ThemePreference,
    onToggleTheme: (theme: Theme) -> Unit
) {
    val colors = LocalColors.current;
    Column(
        modifier = Modifier.fillMaxSize().background(color = colors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            Column {
                Column(modifier = Modifier.background(color = colors.primary)) {
                    Column {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Title("FASTBREAK")
                        }
                        Column(
                            modifier = Modifier.padding(
                                start = 10.dp,
                                bottom = 20.dp
                            )
                        ) {
                            Row {
                                Icon(
                                    Icons.Default.Person,
                                    tint = colors.onPrimary,
                                    contentDescription = "User"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "joebad",
                                    color = colors.onPrimary,
                                    style = MaterialTheme.typography.h6
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp).weight(1f).fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "My Stat Sheet",
                        color = colors.text,
                        style = MaterialTheme.typography.h6
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn {
                        items(statSheetItems) { item ->
                            StatSheetRow(item.highlight, item.leftColumnText, item.rightColumnText);
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
//                    Row(
//                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Column(
//                            modifier = Modifier.fillMaxHeight()
//                            // Make column fill the row height
//                        ) {
//                            Box(
//                                modifier = Modifier
//                                    .background(colors.secondary, RoundedCornerShape(8.dp))
//                                    .padding(
//                                        horizontal = 8.dp,
//                                        vertical = 4.dp
//                                    ) // Adjust padding as needed
//                                    .fillMaxHeight()
//                                    .width(100.dp), // Make box fill the height of the row
//                                contentAlignment = Alignment.Center // Center the text vertically
//                            ) {
//                                Text(
//                                    text = "1,900,065",
//                                    color = colors.onSecondary,
//                                    style = MaterialTheme.typography.body1,
//                                )
//                            }
//                        }
//                        Spacer(modifier = Modifier.width(10.dp))
//                        Text(
//                            text = "My Week 10 score",
//                            color = colors.text,
//                            style = MaterialTheme.typography.body1
//                        )
//                    }
//                    Spacer(modifier = Modifier.height(10.dp))
//                    Row(
//                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Column(
//                            modifier = Modifier.fillMaxHeight()
//                            // Make column fill the row height
//                        ) {
//                            Box(
//                                modifier = Modifier
//                                    .background(colors.accent, RoundedCornerShape(8.dp))
//                                    .padding(
//                                        horizontal = 8.dp,
//                                        vertical = 4.dp
//                                    ) // Adjust padding as needed
//                                    .fillMaxHeight()
//                                    .width(100.dp), // Make box fill the height of the row
//                                contentAlignment = Alignment.Center // Center the text vertically
//                            ) {
//                                Text(
//                                    text = "10,065",
//                                    color = colors.onAccent,
//                                    style = MaterialTheme.typography.body1,
//                                )
//                            }
//                        }
//                        Spacer(modifier = Modifier.width(10.dp))
//                        Text(
//                            text = "Days in a row locking in your FastBreak card",
//                            color = colors.text,
//                            style = MaterialTheme.typography.body1
//                        )
//                    }
//                    Spacer(modifier = Modifier.height(10.dp))
//                    Row(
//                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Column(
//                            modifier = Modifier.fillMaxHeight()
//                        ) {
//                            Box(
//                                modifier = Modifier
//                                    .background(colors.accent, RoundedCornerShape(8.dp))
//                                    .padding(
//                                        horizontal = 8.dp,
//                                        vertical = 4.dp
//                                    ) // Adjust padding as needed
//                                    .fillMaxHeight()
//                                    .width(100.dp), // Make box fill the height of the row
//                                contentAlignment = Alignment.Center // Center the text vertically
//                            ) {
//                                Text(
//                                    text = "365",
//                                    color = colors.onAccent,
//                                    style = MaterialTheme.typography.body1,
//                                )
//                            }
//                        }
//                        Spacer(modifier = Modifier.width(10.dp))
//                        Text(
//                            text = "Your highest Fastbreak card",
//                            color = colors.text,
//                            style = MaterialTheme.typography.body1
//                        )
//                    }
//                    Spacer(modifier = Modifier.height(10.dp))
//                    Row(
//                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Column(
//                            modifier = Modifier.fillMaxHeight()
//                        ) {
//                            Box(
//                                modifier = Modifier
//                                    .background(colors.accent, RoundedCornerShape(8.dp))
//                                    .padding(
//                                        horizontal = 8.dp,
//                                        vertical = 4.dp
//                                    ) // Adjust padding as needed
//                                    .fillMaxHeight()
//                                    .width(100.dp), // Make box fill the height of the row
//                                contentAlignment = Alignment.Center // Center the text vertically
//                            ) {
//                                Text(
//                                    text = "123",
//                                    color = colors.onAccent,
//                                    style = MaterialTheme.typography.body1,
//                                )
//                            }
//                        }
//                        Spacer(modifier = Modifier.width(10.dp))
//                        Text(
//                            text = "Days in a row with a winning pick",
//                            color = colors.text,
//                            style = MaterialTheme.typography.body1
//                        )
//                    }
//                    Spacer(modifier = Modifier.height(10.dp))
//                    Row(
//                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Column(
//                            modifier = Modifier.fillMaxHeight()
//                        ) {
//                            Box(
//                                modifier = Modifier
//                                    .background(colors.accent, RoundedCornerShape(8.dp))
//                                    .padding(
//                                        horizontal = 8.dp,
//                                        vertical = 4.dp
//                                    ) // Adjust padding as needed
//                                    .fillMaxHeight()
//                                    .width(100.dp), // Make box fill the height of the row
//                                contentAlignment = Alignment.Center // Center the text vertically
//                            ) {
//                                Text(
//                                    text = "34",
//                                    color = colors.onAccent,
//                                    style = MaterialTheme.typography.body1,
//                                )
//                            }
//                        }
//                        Spacer(modifier = Modifier.width(10.dp))
//                        Text(
//                            text = "Number of perfect Fastbreak cards",
//                            color = colors.text,
//                            style = MaterialTheme.typography.body1
//                        )
//                    }
//                    Spacer(modifier = Modifier.height(10.dp))
//                    Row(
//                        modifier = Modifier.height(50.dp), // Ensures children match the tallest height
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        Column(
//                            modifier = Modifier.fillMaxHeight()
//                        ) {
//                            Box(
//                                modifier = Modifier
//                                    .background(colors.accent, RoundedCornerShape(8.dp))
//                                    .padding(
//                                        horizontal = 8.dp,
//                                        vertical = 4.dp
//                                    ) // Adjust padding as needed
//                                    .fillMaxHeight()
//                                    .width(100.dp), // Make box fill the height of the row
//                                contentAlignment = Alignment.Center // Center the text vertically
//                            ) {
//                                Text(
//                                    text = "87",
//                                    color = colors.onAccent,
//                                    style = MaterialTheme.typography.body1,
//                                )
//                            }
//                        }
//                        Spacer(modifier = Modifier.width(10.dp))
//                        Text(
//                            text = "Number of weekly wins",
//                            color = colors.text,
//                            style = MaterialTheme.typography.body1
//                        )
//                    }

                }
            }
        }
        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Divider()
            Spacer(modifier = Modifier.height(10.dp))
            Column(modifier = Modifier.padding(horizontal = 10.dp)) {
                Text(
                    text = "Last sync: 10/12/2025 @ 10:56am",
                    color = colors.text,
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(5.dp).fillMaxWidth()
                )
                PhysicalButton(
                    bottomBorderColor = darken(colors.secondary, 0.7f),
                    onClick = { },
                    elevation = 8.dp,
                    pressDepth = 4.dp,
                    backgroundColor = colors.secondary

                ) {
                    Text(
                        "SYNC",
                        color = colors.onSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(10.dp))
            Divider()
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Theme",
                color = colors.text,
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(5.dp).fillMaxWidth()
            )
            ThemeSelector(themePreference = themePreference, onToggleTheme = onToggleTheme)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}