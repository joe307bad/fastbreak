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
import com.joebad.fastbreak.ui.home.FastbreakHomeList

@Composable
fun FastbreakHome(
    locked: Boolean,
    listState: LazyListState,
    animatedAlpha: Float,
    showModal: MutableState<Boolean>,
    dailyFastbreak: DailyFastbreak?,
    fastbreakViewModel: FastbreakViewModel
) {

    val totalPoints = fastbreakViewModel.container.stateFlow.collectAsState().value.totalPoints;
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
                    modifier = Modifier.height(130.dp)
                )
                if (dailyFastbreak == null)
                    LoadingDailyFastbreak()
                else
                    FastbreakHomeList(dailyFastbreak, fastbreakViewModel)
            }
        }
        Box(modifier = Modifier.zIndex(1f)) {
            RoundedBottomHeaderBox(
                "FASTBREAK",
                "Daily sports pick-em and trivia",
                "Season 1 • Week 50 • Day 3",
                animatedAlpha = animatedAlpha
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f)
        ) {

            if (dailyFastbreak != null)
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