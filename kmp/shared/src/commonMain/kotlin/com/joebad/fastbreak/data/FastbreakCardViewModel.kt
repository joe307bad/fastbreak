
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

// Data models remain the same
sealed interface FastbreakCardSelection {
    val solution: String
}

data class PickEm(override val solution: String) : FastbreakCardSelection
data class Trivia(override val solution: String) : FastbreakCardSelection

data class FastbreakCardItem(
    val id: String,
    val selection: FastbreakCardSelection
)

data class FastbreakCardState(
    val cards: List<FastbreakCardItem> = emptyList(),
    val isLoading: Boolean = true  // Start with loading = true
)

// Simplified ViewModel with proper cross-platform support
class FastbreakCardViewModel : ContainerHost<FastbreakCardState, Nothing>, CoroutineScope by MainScope() {

    override val container: Container<FastbreakCardState, Nothing> = container(
        initialState = FastbreakCardState(isLoading = true)  // Initialize with loading = true
    )

    // Optionally load data automatically when ViewModel is created
    init {
        loadGameStates()
    }

    fun loadGameStates() = intent {
        // Already in loading state from initialization, but ensure it's set
        reduce {
            state.copy(isLoading = true)
        }

        // Simulate network delay
        delay(1000)

        // Set cards and explicitly set loading to false
        reduce {
            state.copy(
                cards = listOf(
                    FastbreakCardItem("1", PickEm("Team A")),
                    FastbreakCardItem("2", Trivia("42")),
                    FastbreakCardItem("3", PickEm("Team B"))
                ),
                isLoading = false
            )
        }
    }

    fun updateSelection(id: String, newSelection: FastbreakCardSelection) = intent {
        reduce {
            state.copy(
                cards = state.cards.map { item ->
                    if (item.id == id) item.copy(selection = newSelection) else item
                }
            )
        }
    }

    fun clear() {
        cancel()
    }
}