import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.joebad.fastbreak.data.dailyFastbreak.DailyFastbreak
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakViewModel

@Composable
fun HomeScreen(
    locked: Boolean,
    listState: LazyListState,
    animatedAlpha: Float,
    showModal: MutableState<Boolean>,
    dailyFastbreak: DailyFastbreak?,
    viewModel: FastbreakViewModel
) {
    FastbreakHome(
        locked,
        listState,
        animatedAlpha,
        showModal,
        dailyFastbreak,
        viewModel
    )
}