
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.SmallFloatingActionButton
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
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            CardWithBadge(
                badgeText = "PICK-EM",
                content = { TeamCard() }
            )
            CardWithBadge(
                badgeText = "PICK-EM",
                content = { TeamCard() }
            )
            CardWithBadge(
                badgeText = "PICK-EM",
                content = { TeamCard() }
            )
            CardWithBadge(
                badgeText = "PICK-EM",
                content = { TeamCard() }
            )
            CardWithBadge(
                badgeText = "PICK-EM",
                content = { TeamCard() }
            )
        }
    }
}