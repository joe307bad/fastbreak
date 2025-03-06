import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.ui.theme.LocalColors
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekNavigator() {
    val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val weeks = remember { generateMondayToMondayWeeks(currentDate) }
    val cardWidth = 250.dp
    val colors = LocalColors.current;

    val listState = rememberLazyListState()
    var selectedWeekIndex by remember { mutableStateOf(weeks.size - 1) } // Default to current week
    val coroutineScope = rememberCoroutineScope()

    // Scroll to the last week when the composable first appears
    LaunchedEffect(Unit) {
        listState.scrollToItem(selectedWeekIndex)
    }

    LazyRow(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        contentPadding = PaddingValues(horizontal = cardWidth / 3.1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
            .background(color = colors.background)
    ) {
        itemsIndexed(weeks) { index, week ->
            WeekCard(
                weekNumber = index + 1,
                startDate = week.first,
                endDate = week.second,
                isSelected = index == selectedWeekIndex,
                modifier = Modifier
                    .width(cardWidth)
                    .background(color = colors.background)
                    .clickable {
                        selectedWeekIndex = index
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    }
            )
        }
    }
}


@Composable
fun WeekCard(
    weekNumber: Int,
    startDate: LocalDate,
    endDate: LocalDate,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalColors.current;
    Card(
        modifier = modifier,
        elevation = if (isSelected) 8.dp else 4.dp,
        shape = RectangleShape
    ) {
        Column(
            Modifier.background(color = colors.background)
                .border(width = 1.dp, color = colors.primary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.primary)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Week #$weekNumber",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = colors.onPrimary
                )
            }

            // Week dates
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(color = colors.background),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${startDate.format()} - ${endDate.format()}",
                    fontSize = 14.sp,
                    color = colors.text
                )
            }
        }
    }
}

// Helper extension function to format date
fun LocalDate.format(): String = "${monthName()} $dayOfMonth"

// Helper function to get month name
fun LocalDate.monthName(): String = when (month) {
    Month.JANUARY -> "Jan"
    Month.FEBRUARY -> "Feb"
    Month.MARCH -> "Mar"
    Month.APRIL -> "Apr"
    Month.MAY -> "May"
    Month.JUNE -> "Jun"
    Month.JULY -> "Jul"
    Month.AUGUST -> "Aug"
    Month.SEPTEMBER -> "Sep"
    Month.OCTOBER -> "Oct"
    Month.NOVEMBER -> "Nov"
    Month.DECEMBER -> "Dec"
    else -> TODO()
}

// Generate weeks from the first Monday of the year to the current week
fun generateMondayToMondayWeeks(currentDate: LocalDate): List<Pair<LocalDate, LocalDate>> {
    var firstMonday = LocalDate(currentDate.year, Month.JANUARY, 1)
    while (firstMonday.dayOfWeek != DayOfWeek.MONDAY) {
        firstMonday = firstMonday.plus(1, DateTimeUnit.DAY)
    }

    val weeks = mutableListOf<Pair<LocalDate, LocalDate>>()
    var weekStart = firstMonday
    while (weekStart.year == currentDate.year && weekStart <= currentDate) {
        val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
        weeks.add(weekStart to weekEnd)
        weekStart = weekStart.plus(7, DateTimeUnit.DAY)
    }

    return weeks
}
