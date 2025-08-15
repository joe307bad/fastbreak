package com.joebad.fastbreak.ui.screens.schedule

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

data class ScheduleState(
    val selectedWinners: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false
)

sealed class ScheduleAction {
    data class SelectWinner(val gameId: String, val teamName: String) : ScheduleAction()
    data class ClearSelection(val gameId: String) : ScheduleAction()
    data object ClearAllSelections : ScheduleAction()
}

sealed class ScheduleSideEffect {
    data class ShowToast(val message: String) : ScheduleSideEffect()
}

class ScheduleViewModel : ContainerHost<ScheduleState, ScheduleSideEffect> {
    
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override val container: Container<ScheduleState, ScheduleSideEffect> = 
        viewModelScope.container(ScheduleState())

    fun handleAction(action: ScheduleAction) {
        when (action) {
            is ScheduleAction.SelectWinner -> selectWinner(action.gameId, action.teamName)
            is ScheduleAction.ClearSelection -> clearSelection(action.gameId)
            is ScheduleAction.ClearAllSelections -> clearAllSelections()
        }
    }

    private fun selectWinner(gameId: String, teamName: String) = intent {
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
}