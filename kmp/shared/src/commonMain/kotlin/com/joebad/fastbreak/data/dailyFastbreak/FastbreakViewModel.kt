package com.joebad.fastbreak.data.dailyFastbreak
import AuthRepository
import getRandomId
import kotbase.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

@Serializable
data class FastbreakSelection(
    val _id: String,
    val userAnswer: String,
    val points: Int,
    val description: String,
    val type: String
)

@Serializable
data class FastbreakSelectionState(
    val selections: List<FastbreakSelection> = emptyList(),
    val totalPoints: Int = 0,
    val cardId: String = getRandomId(),
    val locked: Boolean? = false,
    val date: String,
)

sealed class FastbreakSideEffect {

    data class SelectionAdded(val selection: FastbreakSelection) : FastbreakSideEffect()
    data class SelectionUpdated(val selection: FastbreakSelection) : FastbreakSideEffect()
    data class SelectionDeleted(val selection: FastbreakSelection) : FastbreakSideEffect()
    data class CardLocked(val state: FastbreakSelectionState) : FastbreakSideEffect()
}


class FastbreakViewModel(
    database: Database,
    onLock: (state: FastbreakSelectionState) -> Unit,
    date: String,
    private val authRepository: AuthRepository?
) : ContainerHost<FastbreakSelectionState, FastbreakSideEffect>, CoroutineScope by MainScope() {

    private val persistence = FastbreakSelectionsPersistence(database, authRepository)

    override val container: Container<FastbreakSelectionState, FastbreakSideEffect> = container(
        initialState = FastbreakSelectionState(date = date)
    )

    init {
        loadSavedSelections()
        container.sideEffectFlow
            .onEach { sideEffect ->
                when (sideEffect) {
                    is FastbreakSideEffect.CardLocked -> onLock(sideEffect.state)
                    else -> {}
                }
            }
            .launchIn(MainScope())
    }

    fun lockCard() {
        intent {
            reduce {
                state.copy(locked = true)
            }
            postSideEffect(FastbreakSideEffect.CardLocked(state))
        }

        saveSelections()
    }

    fun updateSelection(
        selectionId: String,
        userAnswer: String,
        points: Int,
        description: String,
        type: String
    ) =
        intent {
            if (state.locked == true) {
                return@intent;
            }


            val currentSelections = state.selections
            val existingSelectionIndex = currentSelections.indexOfFirst { it._id == selectionId }

            val selection = FastbreakSelection(
                _id = selectionId,
                userAnswer = userAnswer,
                points = points,
                description = description,
                type = type
            )

            if (existingSelectionIndex != -1) {

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
                    val updatedSelections = currentSelections.toMutableList().apply {
                        set(existingSelectionIndex, selection)
                    }
                    val newTotalPoints = updatedSelections.sumOf { it.points }

                    reduce {
                        state.copy(selections = updatedSelections, totalPoints = newTotalPoints)
                    }

                    postSideEffect(FastbreakSideEffect.SelectionUpdated(selection))
                }
            } else {
                val updatedSelections = currentSelections + selection
                val newTotalPoints = updatedSelections.sumOf { it.points }

                reduce {
                    state.copy(selections = updatedSelections, totalPoints = newTotalPoints)
                }

                postSideEffect(FastbreakSideEffect.SelectionAdded(selection))
            }

            saveSelections()
        }

    private fun saveSelections() {
        launch {
            try {
                val state = container.stateFlow.value;
                persistence.saveSelections(state.cardId ?: "", state.selections, state.locked);
            } catch (e: Exception) {
                println(e)
            }
        }
    }

    private fun loadSavedSelections() {
        launch {
            try {
                val savedSelections = persistence.loadTodaySelections()
                if (savedSelections != null) {
                    intent {
                        reduce {
                            state.copy(
                                cardId = savedSelections.cardId,
                                selections = savedSelections.selectionDtos,
                                totalPoints = savedSelections.selectionDtos.sumOf { it.points },
                                locked = savedSelections.locked
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                println(e.message)
            }
        }
    }
}