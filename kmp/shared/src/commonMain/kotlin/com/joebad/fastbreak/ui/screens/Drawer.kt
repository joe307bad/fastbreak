
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.Theme
import com.joebad.fastbreak.ThemePreference
import com.joebad.fastbreak.ui.PhysicalButton
import com.joebad.fastbreak.ui.Title
import com.joebad.fastbreak.ui.theme.LocalColors
import com.joebad.fastbreak.ui.theme.darken
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

fun formatEpochSecondsToDate(epochSeconds: Long): String {
    // Convert seconds to Instant
    val instant = Instant.fromEpochSeconds(epochSeconds)

    // Convert to LocalDateTime in system timezone
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    // Create a custom format for "Monday, May 6, 2025 3:30:45 PM"
    val dateFormat = LocalDateTime.Format {
        dayOfWeek(DayOfWeekNames.ENGLISH_FULL)  // Monday
        chars(", ")
        monthName(MonthNames.ENGLISH_FULL)      // May
        char(' ')
        dayOfMonth()                            // 6
        chars(", ")
        year()                                  // 2025
        char(' ')
        amPmHour()                             // 12-hour format with AM/PM
        char(':')
        minute()                               // 30
        char(':')
        second()                               // 45
        char(' ')
        amPmMarker("AM", "PM")
    }

    return localDateTime.format(dateFormat)
}


@Serializable
data class StatSheetItemView(
    val statSheetType: StatSheetType,
    val leftColumnText: String,
    val rightColumnText: String,
)

enum class StatSheetType {
    Button,
    MonoSpace
}


@Composable
fun StatSheetRow(
    statSheetType: StatSheetType,
    leftColumnText: String,
    rightColumnText: String,
    onClick: (Boolean) -> Unit
) {
    val colors = LocalColors.current;

    if (leftColumnText == "null") {
        return Row(
            content = {}
        );
    }

    Row(
        modifier = Modifier.height(50.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(100.dp).height(50.dp), //.background(color = Color.Red),
//            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (statSheetType) {
                StatSheetType.Button -> {
                    PhysicalButton(
                        bottomBorderColor = darken(colors.secondary, 0.7f),
                        onClick = { onClick(true) },
                        elevation = 8.dp,
                        pressDepth = 4.dp,
                        backgroundColor = colors.secondary
                    ) {
                        Text(
                            text = leftColumnText,
                            color = colors.onSecondary,
                            fontFamily = FontFamily.Monospace,

                            )
                    }
                }

                StatSheetType.MonoSpace -> {
                    Box(
                        modifier = Modifier
                            .background(
                                colors.accent,
                            )
                            .height(50.dp)
                            .fillMaxWidth()
                            .zIndex(2f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = leftColumnText,
                            color = colors.onAccent,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
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
    onShowLastweeksFastbreakCard: () -> Unit,
    themePreference: ThemePreference,
    onToggleTheme: (theme: Theme) -> Unit,
    goToSettings: () -> Unit,
    statSheetItems: List<StatSheetItemView>?,
    lastFetchedDate: Long,
    onSync: () -> Unit,
    username: String
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
                                end = 10.dp,
                                bottom = 20.dp
                            )
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Icon(
                                    Icons.Default.Person,
                                    tint = colors.onPrimary,
                                    contentDescription = "User"
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = username.replace("\"", ""),
                                        color = colors.onPrimary,
                                        style = MaterialTheme.typography.h6
                                    )
                                }
                                Box(
                                    modifier = Modifier.padding(start = 10.dp).clickable(onClick = {
                                        goToSettings()
                                    })
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        tint = colors.onPrimary,
                                        contentDescription = "User"
                                    )
                                }
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
                    if (statSheetItems.isNullOrEmpty() || statSheetItems.any { i -> i.leftColumnText == "null" }) {
                        Text(
                            text = "No Stat Sheet found. Check back tomorrow.",
                            color = colors.onPrimary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LazyColumn {
                            items(statSheetItems) { item ->
                                StatSheetRow(
                                    item.statSheetType,
                                    item.leftColumnText,
                                    item.rightColumnText,
                                    onClick = { isButton -> if (isButton) onShowLastweeksFastbreakCard() }
                                );
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}