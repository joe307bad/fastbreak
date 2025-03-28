import kotbase.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

/**
 * Represents a user selection for a FastbreakCardItem
 */

@Serializable
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
    val totalPoints: Int = 0,
    val id: String? = getRandomId(),
    val locked: Boolean? = false
)

/**
 * Side effects that can be triggered by the ViewModel
 */
sealed class FastbreakSideEffect {
    data class SelectionAdded(val selection: FastbreakSelection) : FastbreakSideEffect()
    data class SelectionUpdated(val selection: FastbreakSelection) : FastbreakSideEffect()
    data class SelectionDeleted(val selection: FastbreakSelection) : FastbreakSideEffect()
}


class FastbreakViewModel(
    private val database: Database
) : ContainerHost<FastbreakSelectionState, FastbreakSideEffect>, CoroutineScope by MainScope() {

    private val persistence = FastbreakSelectionsPersistence(database)

    // Initialize the Orbit container with initial state
    override val container: Container<FastbreakSelectionState, FastbreakSideEffect> = container(
        initialState = FastbreakSelectionState()
    )

    init {
        // Load saved selections when ViewModel is created
        loadSavedSelections()
    }

    fun lockCard() {
        intent {
            reduce {
                state.copy(locked = true)
            }
        }

        saveSelections()
    }

    /**
     * Updates or adds a selection based on the provided card ID and user answer
     */
    fun updateSelection(
        selectionId: String,
        userAnswer: String,
        points: Int,
        description: String,
        type: String
    ) =
        intent {
            if(state.locked == true) {
                return@intent;
            }


            val currentSelections = state.selections
            val existingSelectionIndex = currentSelections.indexOfFirst { it.id == selectionId }

            val selection = FastbreakSelection(
                id = selectionId,
                userAnswer = userAnswer,
                points = points,
                description = description,
                type = type
            )

            if (existingSelectionIndex != -1) {

                // previous selection
                val previousSelection = currentSelections[existingSelectionIndex];

                if (previousSelection.userAnswer == userAnswer) {

                    val updatedSelections = currentSelections.toMutableList()
                    updatedSelections.removeAt(existingSelectionIndex)

                    val newTotalPoints = updatedSelections.sumOf { it.points }
                    reduce {
                        state.copy(selections = updatedSelections, totalPoints = newTotalPoints)
                    }

                    postSideEffect(FastbreakSideEffect.SelectionDeleted(selection))
                } else {
                    // Update existing selection
                    val updatedSelections = currentSelections.toMutableList().apply {
                        set(existingSelectionIndex, selection)
                    }
                    val newTotalPoints = updatedSelections.sumOf { it.points }

                    // Update state with the new list
                    reduce {
                        state.copy(selections = updatedSelections, totalPoints = newTotalPoints)
                    }

                    postSideEffect(FastbreakSideEffect.SelectionUpdated(selection))
                }
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

            // Save updated selections to database
            saveSelections()
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
            state.copy(selections = emptyList(), totalPoints = 0)
        }

        // Save cleared selections to database
        saveSelections()
    }

    /**
     * Save current selections to the database
     */
    private fun saveSelections() {
        launch {
            try {
                val state = container.stateFlow.value;
                persistence.saveSelections(state.id ?: "", state.selections, state.locked);
            } catch (e: Exception) {
                println(e)
                // Handle error (log, retry, etc.)
            }
        }
    }

    /**
     * Load saved selections from the database
     */
    private fun loadSavedSelections() {
        launch {
            try {
                val savedSelections = persistence.loadTodaySelections()
                if (savedSelections != null) {
                    intent {
                        reduce {
                            state.copy(
                                id = savedSelections.id,
                                selections = savedSelections.selectionDtos,
                                totalPoints = savedSelections.selectionDtos.sumOf { it.points },
                                locked = savedSelections.locked
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                println(e.message)
                // Handle error (log, etc.)
            }
        }
    }

    /**
     * Clean up coroutine scope when ViewModel is no longer needed
     */
    fun onCleared() {
        cancel() // Cancel the coroutine scope
    }
}