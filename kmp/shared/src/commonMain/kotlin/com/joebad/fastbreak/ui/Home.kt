
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.CardWithBadge
import com.joebad.fastbreak.ui.TeamCard

@Composable
fun HomeScreen() {
    // Use LazyColumn for a scrollable container
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            RoundedBottomHeaderBox("FASTBREAK", "Daily sports fantasy games and trivia")
            Spacer(
                modifier = Modifier.padding(bottom = 10.dp)
            )
            WeekNavigator()
            Spacer(
                modifier = Modifier.padding(bottom = 10.dp)
            )
            CardWithBadge(
                badgeText = "PICK-EM",
                dateText = "Nov, 10",
                content = { TeamCard() }
            )
            CardWithBadge(
                badgeText = "PICK-EM",
                dateText = "Nov, 10",
                content = { TeamCard() }
            )
            CardWithBadge(
                badgeText = "PICK-EM",
                dateText = "Nov, 10",
                content = { TeamCard() }
            )
            CardWithBadge(
                badgeText = "PICK-EM",
                dateText = "Nov, 10",
                content = { TeamCard() }
            )
            CardWithBadge(
                badgeText = "PICK-EM",
                dateText = "Nov, 10",
                content = { TeamCard() }
            )
        }
    }
}