import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakStateRepository
import io.ktor.client.HttpClient
import kotbase.Database
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun FastbreakStateDisplay(
    repository: FastbreakStateRepository = FastbreakStateRepository(
        Database("fastbreak"),
        HttpClient(),
        authRepository = null
    )
) {
    var fastbreakState by remember { mutableStateOf<Any?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val currentDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    LaunchedEffect(key1 = Unit) {
        coroutineScope.launch {
            try {
                fastbreakState = repository.getDailyFastbreakState(currentDate)
                print(fastbreakState);
            } catch (e: Exception) {
                error = "Failed to fetch state: ${e.message}"
            }
        }
    }

    if (error != null) {
        Text("Error: $error")
    } else if (fastbreakState != null) {
//        Text("Card: ${fastbreakState?.card}")
//        Text("Leaderboard: ${fastbreakState?.leaderboard}")
//        Text("Stat Sheet: ${fastbreakState?.statSheet}")
//        Text("Week: ${fastbreakState?.week}")
//        Text("Season: ${fastbreakState?.season}")
//        Text("Day: ${fastbreakState?.day}")
    } else {
        Text("Loading...")
    }
}