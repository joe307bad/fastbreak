
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakViewModel
import com.joebad.fastbreak.model.dtos.DailyFastbreak
import com.joebad.fastbreak.ui.FABWithExactShapeBorder
import com.joebad.fastbreak.ui.RoundedBottomHeaderBox
import com.joebad.fastbreak.ui.home.ErrorDailyFastbreak
import com.joebad.fastbreak.ui.home.FastbreakHomeList
import com.joebad.fastbreak.ui.home.LoadingDailyFastbreak
import com.joebad.fastbreak.ui.home.hasActiveItems
import com.joebad.fastbreak.utils.DateUtils
import kotlinx.datetime.LocalDate

@Composable
fun HomeScreen(
    locked: Boolean,
    listState: LazyListState,
    animatedAlpha: Float,
    showModal: MutableState<Boolean>,
    dailyFastbreak: DailyFastbreak?,
    fastbreakViewModel: FastbreakViewModel?,
    selectedDate: String,
    error: String?
) {

    val totalPoints = fastbreakViewModel?.container?.stateFlow?.collectAsState()?.value?.totalPoints;
    
    // Convert selectedDate (yyyyMMdd) to LocalDate and calculate Season/Week/Day
    val dateComponents = try {
        val year = selectedDate.substring(0, 4).toInt()
        val month = selectedDate.substring(4, 6).toInt()
        val day = selectedDate.substring(6, 8).toInt()
        val localDate = LocalDate(year, month, day)
        DateUtils.getSeasonWeekDay(localDate)
    } catch (e: Exception) {
        Triple(1, 1, 1) // fallback values
    }
    
    val (season, week, dayOfWeek) = dateComponents
    val headerSubtitle = "Season $season • Week $week • Day $dayOfWeek"
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .zIndex(2f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(
                    modifier = Modifier.height(105.dp)
                )
                if (error != null) {
                    // Show error message instead of loading
                    ErrorDailyFastbreak(error)
                } else if (dailyFastbreak == null || fastbreakViewModel == null) {
                    LoadingDailyFastbreak()
                } else {
                    FastbreakHomeList(dailyFastbreak, fastbreakViewModel)
                }
            }
        }
        Box(modifier = Modifier.zIndex(1f)) {
            RoundedBottomHeaderBox(
                "FASTBREAK",
                headerSubtitle,
                animatedAlpha = animatedAlpha
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f)
        ) {
            if (dailyFastbreak != null && totalPoints != null && hasActiveItems(dailyFastbreak.fastbreakCard))
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    FABWithExactShapeBorder(
                        locked,
                        showModal = { showModal.value = true },
                        totalPoints
                    )
                }
        }
    }
}