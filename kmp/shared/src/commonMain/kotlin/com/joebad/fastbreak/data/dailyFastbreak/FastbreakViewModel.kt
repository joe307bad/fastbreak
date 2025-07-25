package com.joebad.fastbreak.data.dailyFastbreak

import AuthRepository
import ProfileRepository
import StatSheetItemView
import StatSheetType
import com.joebad.fastbreak.model.dtos.FastbreakSelectionsResult
import com.joebad.fastbreak.model.dtos.StatSheetItem
import getRandomId
import getWeekNumber
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
    val statSheetItems: List<StatSheetItemView> = emptyList(),
    val results: FastbreakSelectionsResult? = null,
    val lastLockedCardResults: FastbreakSelectionState? = null,
    val isSavingUserName: Boolean = false
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
    authRepository: AuthRepository,
    statSheetItems: StatSheetItem?,
    selectedDate: String?,
    lastLockedCard: FastbreakSelectionState?,
    lastLockedCardWithResults: FastbreakSelectionState?
) : ContainerHost<FastbreakSelectionState, FastbreakSideEffect>, CoroutineScope by MainScope() {

    private val persistence = FastbreakSelectionsPersistence(database, authRepository)
    private val _profileRepository = ProfileRepository(authRepository)
    override val container: Container<FastbreakSelectionState, FastbreakSideEffect> = container(
        initialState = FastbreakSelectionState(
            date = date,
            locked = lastLockedCard?.date == selectedDate
        )
    )

    init {
        launch {
            if (lastLockedCard != null && lastLockedCard.date == selectedDate) {
                try {
                    persistence.saveSelections(
                        lastLockedCard.cardId,
                        lastLockedCard.selections,
                        true,
                        lastLockedCard.date
                    )
                } catch (e: Exception) {
                    println(e)
                }
            }
            loadSavedSelections()
        }
        setStatSheetItems(
            statSheetItems,
            lastLockedCardWithResults?.results?.totalPoints.toString(),
            lastLockedCardWithResults?.date
        );
        setlastLockedCardResults(lastLockedCardWithResults)
        container.sideEffectFlow
            .onEach { sideEffect ->
                when (sideEffect) {
                    is FastbreakSideEffect.CardLocked -> onLock(sideEffect.state)
                    else -> {}
                }
            }
            .launchIn(MainScope())
    }

    private fun setlastLockedCardResults(lastLockedCardResults: FastbreakSelectionState?) {
        intent {
            reduce {
                state.copy(lastLockedCardResults = lastLockedCardResults)
            }
        }
    }

    private fun setStatSheetItems(
        statSheetItems: StatSheetItem?,
        lastCardPoints: String?,
        lastCardDate: String?
    ) {
        intent {
            reduce {
                val statSheetItemViewList = mutableListOf<StatSheetItemView>()

                val currentWeek = statSheetItems?.currentWeek;
                val lastWeek = statSheetItems?.lastWeek;
                val highest = statSheetItems?.highestFastbreakCardEver;
                val streak = statSheetItems?.lockedCardStreak;


                statSheetItemViewList.add(
                    StatSheetItemView(
                        statSheetType = StatSheetType.Button,
                        leftColumnText = lastCardPoints ?: "",
                        rightColumnText = "My Fastbreak card results\nfor $lastCardDate"
                    )
                )

                statSheetItemViewList.add(
                    StatSheetItemView(
                        statSheetType = StatSheetType.MonoSpace,
                        leftColumnText = currentWeek?.total.toString(),
                        rightColumnText = "Current week's total\nWeek ${getWeekNumber(currentWeek?.days?.first()?.dateCode)}"
                    )
                )

                statSheetItemViewList.add(
                    StatSheetItemView(
                        statSheetType = StatSheetType.MonoSpace,
                        leftColumnText = lastWeek?.total.toString(),
                        rightColumnText = "Last week's total\nWeek ${getWeekNumber(lastWeek?.days?.first()?.dateCode)}"
                    )
                )

                statSheetItemViewList.add(
                    StatSheetItemView(
                        statSheetType = StatSheetType.MonoSpace,
                        leftColumnText = highest?.points.toString(),
                        rightColumnText = "My highest Fastbreak card ever\n${highest?.date}"
                    )
                )

//                statSheetItemViewList.add(
//                    StatSheetItemView(
//                        statSheetType = StatSheetType.MonoSpace,
//                        leftColumnText = winningPickStreak?.longest/current.toString(),
//                        rightColumnText = "Days in a row with a winning pick"
//                    )
//                )

                statSheetItemViewList.add(
                    StatSheetItemView(
                        statSheetType = StatSheetType.MonoSpace,
                        leftColumnText = streak?.current.toString(),
                        rightColumnText = "My current locked Fastbreak card streak"
                    )
                )

                statSheetItemViewList.add(
                    StatSheetItemView(
                        statSheetType = StatSheetType.MonoSpace,
                        leftColumnText = streak?.longest.toString(),
                        rightColumnText = "My longest locked Fastbreak card streak"
                    )
                )

                state.copy(statSheetItems = statSheetItemViewList)
            }
        }
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
                persistence.saveSelections(
                    state.cardId ?: "",
                    state.selections,
                    state.locked,
                    state.date
                );
            } catch (e: Exception) {
                println(e)
            }
        }
    }

    private fun loadSavedSelections() {
        launch {
            try {
                val state = container.stateFlow.value;
                val savedSelections = persistence.loadSelections(state.date)
                if (savedSelections != null) {
                    intent {
                        reduce {
                            state.copy(
                                cardId = savedSelections.cardId,
                                selections = savedSelections.selectionDtos,
                                totalPoints = if (savedSelections.selectionDtos.isEmpty()) 0 else savedSelections.selectionDtos.sumOf { it.points },
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

    fun saveUserName(userName: String) {
        intent {
            reduce {
                state.copy(isSavingUserName = true)
            }
        }

        launch {
            try {
                _profileRepository.saveUserName(userName)
            } catch (e: Exception) {
                println(e.message)
            } finally {
                intent {
                    reduce {
                        state.copy(isSavingUserName = false)
                    }
                }
            }
        }
    }

}

