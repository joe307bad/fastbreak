import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

/**
 * Represents a user selection for a FastbreakCardItem
 */
data class FastbreakSelection(
    val id: String,
    val userAnswer: String,
    val points: Int,
    val description: String,
    val type: String
)

/**
 * State class that holds the current list of user selections
 */
data class FastbreakSelectionState(
    val selections: List<FastbreakSelection> = emptyList(),
    val totalPoints: Int = 0
)

/**
 * Side effects that can be triggered by the ViewModel
 */
sealed class FastbreakSideEffect {
    data class SelectionAdded(val selection: FastbreakSelection) : FastbreakSideEffect()
    data class SelectionUpdated(val selection: FastbreakSelection) : FastbreakSideEffect()
}

class FastbreakViewModel() : ContainerHost<FastbreakSelectionState, FastbreakSideEffect>,
    CoroutineScope by MainScope() {

    // Initialize the Orbit container with initial state
    override val container: Container<FastbreakSelectionState, FastbreakSideEffect> = container(
        initialState = FastbreakSelectionState()
    )

    /**
     * Updates or adds a selection based on the provided card ID and user answer
     *
     * @param cardId The ID of the EmptyFastbreakCardItem
     * @param userAnswer The user's answer (one of answer1-4, "true"/"false", or "homeTeam"/"awayTeam")
     */
    fun updateSelection(cardId: String, userAnswer: String, points: Int, description: String, type: String) =
        intent {
            val currentSelections = state.selections
            val existingSelectionIndex = currentSelections.indexOfFirst { it.id == cardId }

            val selection = FastbreakSelection(
                id = cardId,
                userAnswer = userAnswer,
                points,
                description,
                type
            )

            if (existingSelectionIndex != -1) {
                // Update existing selection
                val updatedSelections = currentSelections.toMutableList().apply {
                    set(existingSelectionIndex, selection)
                }
                val newTotalPoints = updatedSelections.sumOf { it.points }

                // Update state with the new list
                reduce {
                    state.copy(selections = updatedSelections, totalPoints = newTotalPoints)
                }

                // Post side effect for selection update
                postSideEffect(FastbreakSideEffect.SelectionUpdated(selection))
            } else {
                // Add new selection
                val updatedSelections = currentSelections + selection
                val newTotalPoints = updatedSelections.sumOf { it.points }

                // Update state with the new list
                reduce {
                    state.copy(selections = updatedSelections, totalPoints = newTotalPoints)
                }

                // Post side effect for selection addition
                postSideEffect(FastbreakSideEffect.SelectionAdded(selection))
            }
        }

    /**
     * Returns the current user selection for a given card ID, or null if not selected
     */
    fun getSelectionForCard(cardId: String): FastbreakSelection? {
        return container.stateFlow.value.selections.find { it.id == cardId }
    }

    /**
     * Clears all selections
     */
    fun clearSelections() = intent {
        reduce {
            state.copy(selections = emptyList())
        }
    }

    /**
     * Clean up coroutine scope when ViewModel is no longer needed
     */
    fun onCleared() {
        cancel() // Cancel the coroutine scope
    }
}