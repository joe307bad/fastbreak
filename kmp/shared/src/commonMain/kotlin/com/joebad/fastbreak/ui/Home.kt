import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.CardWithBadge
import com.joebad.fastbreak.ui.TeamCard
import com.joebad.fastbreak.ui.theme.LocalColors

@Composable
fun HomeScreen(listState: LazyListState) {
    val colors = LocalColors.current;
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
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
                points = "1000"
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
}