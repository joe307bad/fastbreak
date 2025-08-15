package com.joebad.fastbreak.data.picks

import AuthRepository
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelection
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import getRandomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

data class ScheduleState(
    val selectedWinners: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isLocked: Boolean = false
)

sealed class ScheduleAction {
    data class SelectWinner(val gameId: String, val teamName: String) : ScheduleAction()
    data class ClearSelection(val gameId: String) : ScheduleAction()
    data object ClearAllSelections : ScheduleAction()
    data object LockPicks : ScheduleAction()
}

sealed class ScheduleSideEffect {
    data class ShowToast(val message: String) : ScheduleSideEffect()
}

class ScheduleViewModel(private val authRepository: AuthRepository) : ContainerHost<ScheduleState, ScheduleSideEffect> {
    
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val picksRepository = PicksRepository(authRepository)
    
    override val container: Container<ScheduleState, ScheduleSideEffect> =
        viewModelScope.container(ScheduleState())

    fun handleAction(action: ScheduleAction) {
        when (action) {
            is ScheduleAction.SelectWinner -> selectWinner(action.gameId, action.teamName)
            is ScheduleAction.ClearSelection -> clearSelection(action.gameId)
            is ScheduleAction.ClearAllSelections -> clearAllSelections()
            is ScheduleAction.LockPicks -> lockPicks()
        }
    }

    private fun selectWinner(gameId: String, teamName: String) = intent {
        // Prevent selections when locked
        if (state.isLocked) {
            postSideEffect(ScheduleSideEffect.ShowToast("Cannot change selections when locked"))
            return@intent
        }
        
        val currentSelection = state.selectedWinners[gameId]
        
        reduce {
            state.copy(
                selectedWinners = if (currentSelection == teamName) {
                    // If same team is selected, deselect it
                    state.selectedWinners - gameId
                } else {
                    // Select the new team
                    state.selectedWinners + (gameId to teamName)
                }
            )
        }
        
        // Optional: Show feedback
        val message = if (currentSelection == teamName) {
            "Deselected $teamName"
        } else {
            "Selected $teamName to win"
        }
        postSideEffect(ScheduleSideEffect.ShowToast(message))
    }

    private fun clearSelection(gameId: String) = intent {
        reduce {
            state.copy(
                selectedWinners = state.selectedWinners - gameId
            )
        }
    }

    private fun clearAllSelections() = intent {
        reduce {
            state.copy(
                selectedWinners = emptyMap()
            )
        }
        postSideEffect(ScheduleSideEffect.ShowToast("All selections cleared"))
    }
    
    private fun lockPicks() = intent {
        reduce { state.copy(isLoading = true) }
        
        try {
            // Convert current picks to FastbreakSelectionState format
            val selections = state.selectedWinners.map { (gameId, teamName) ->
                FastbreakSelection(
                    _id = gameId,
                    userAnswer = teamName,
                    points = 0, // Points calculated server-side
                    description = "Selected $teamName to win",
                    type = "team_selection"
                )
            }
            
            val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            
            val selectionState = FastbreakSelectionState(
                selections = selections,
                totalPoints = 0,
                cardId = getRandomId(),
                locked = true,
                date = currentDate
            )
            
            val result = picksRepository.lockPicks(selectionState)
            
            if (result != null) {
                reduce { 
                    state.copy(
                        isLoading = false,
                        isLocked = true
                    )
                }
                postSideEffect(ScheduleSideEffect.ShowToast("Picks submitted and locked!"))
            } else {
                reduce { state.copy(isLoading = false) }
                postSideEffect(ScheduleSideEffect.ShowToast("Failed to submit picks"))
            }
        } catch (e: Exception) {
            reduce { state.copy(isLoading = false) }
            postSideEffect(ScheduleSideEffect.ShowToast("Error submitting picks: ${e.message}"))
        }
    }
}