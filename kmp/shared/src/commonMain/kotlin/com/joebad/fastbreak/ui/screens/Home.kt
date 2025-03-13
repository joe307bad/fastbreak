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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.joebad.fastbreak.ui.CardWithBadge
import com.joebad.fastbreak.ui.TeamCard
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun HomeScreen(listState: LazyListState, animatedAlpha: Float, showModal: MutableState<Boolean>) {
    val colors = LocalColors.current;

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
                CardWithBadge(
                    badgeText = "FEATURED PICK-EM",
                    modifier = Modifier.padding(bottom = 30.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") },
                    badgeColor = colors.accent,
                    badgeTextColor = colors.onAccent,
                    points = "1,000"
                )
                CardWithBadge(
                    badgeText = "TRIVIA",
                    modifier = Modifier.padding(bottom = 30.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") },
                    badgeColor = colors.secondary,
                    badgeTextColor = colors.onSecondary,
                    points = "6,000"
                )
                CardWithBadge(
                    badgeText = "PICK-EM",
                    modifier = Modifier.padding(bottom = 10.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") }
                )
                CardWithBadge(
                    badgeText = "PICK-EM",
                    modifier = Modifier.padding(bottom = 10.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") }
                )
                CardWithBadge(
                    badgeText = "PICK-EM",
                    modifier = Modifier.padding(bottom = 10.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") }
                )
                CardWithBadge(
                    badgeText = "PICK-EM",
                    modifier = Modifier.padding(bottom = 10.dp),
                    content = { TeamCard("Monday", "Sept. 10th", "@ 1pm") }
                )
                Spacer(
                    modifier = Modifier.height(100.dp)
                )
            }
        }
        Box(modifier = Modifier.zIndex(1f)) {
            RoundedBottomHeaderBox(
                "FASTBREAK",
                "Daily fantasy sports games and trivia",
                "Season 1 • Week 50 • Day 3",
                animatedAlpha = animatedAlpha
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f)
        ) {

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                FABWithExactShapeBorder(showModal = { showModal.value = true })
            }
        }
    }
}