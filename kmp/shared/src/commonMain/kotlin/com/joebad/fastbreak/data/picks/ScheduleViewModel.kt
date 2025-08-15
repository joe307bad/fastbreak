package com.joebad.fastbreak.data.picks

import AuthRepository
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelection
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import getRandomId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

data class ScheduleState(
    val selectedWinners: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val isLocked: Boolean = false,
    val isRefreshingToken: Boolean = false
)

sealed class ScheduleAction {
    data class SelectWinner(val gameId: String, val teamName: String) : ScheduleAction()
    data class ClearSelection(val gameId: String) : ScheduleAction()
    data object ClearAllSelections : ScheduleAction()
    data object LockPicks : ScheduleAction()
}

sealed class ScheduleSideEffect {
    data class ShowToast(val message: String) : ScheduleSideEffect()
    object RequireLogin : ScheduleSideEffect()
}

class ScheduleViewModel(private val dateCode: String, private val authRepository: AuthRepository) :
    ContainerHost<ScheduleState, ScheduleSideEffect> {

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
        // Prevent selections when locked or during token refresh
        if (state.isLocked || state.isRefreshingToken) {
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
                    type = "PICK_EM"
                )
            }

            val selectionState = FastbreakSelectionState(
                selections = selections,
                totalPoints = 0,
                cardId = getRandomId(),
                locked = true,
                date = dateCode
            )

            val result = picksRepository.lockPicks(selectionState)

            when (result) {
                is LockPicksResult.Success -> {
                    reduce {
                        state.copy(
                            isLoading = false,
                            isLocked = true,
                            isRefreshingToken = false
                        )
                    }
                    postSideEffect(ScheduleSideEffect.ShowToast("Picks submitted and locked!"))
                }

                is LockPicksResult.TokenRefreshRequired -> {
                    // Set refreshing state and attempt transparent refresh
                    reduce {
                        state.copy(
                            isRefreshingToken = true,
                            isLoading = true  // Keep loading state during refresh
                        )
                    }

                    // Attempt refresh and retry
                    val refreshResult = picksRepository.lockPicksWithRefreshedToken(selectionState)
                    when (refreshResult) {
                        is LockPicksResult.Success -> {
                            reduce {
                                state.copy(
                                    isLoading = false,
                                    isLocked = true,
                                    isRefreshingToken = false
                                )
                            }
                            postSideEffect(ScheduleSideEffect.ShowToast("Picks submitted and locked!"))
                        }

                        is LockPicksResult.Error -> {
                            reduce {
                                state.copy(
                                    isLoading = false,
                                    isRefreshingToken = false
                                )
                            }
                            postSideEffect(ScheduleSideEffect.ShowToast("Session expired. Please sign in again."))
                            postSideEffect(ScheduleSideEffect.RequireLogin)
                        }

                        is LockPicksResult.TokenRefreshRequired -> {
                            // Should not happen after refresh attempt
                            reduce {
                                state.copy(
                                    isLoading = false,
                                    isRefreshingToken = false
                                )
                            }
                            postSideEffect(ScheduleSideEffect.ShowToast("Authentication error. Please sign in again."))
                            postSideEffect(ScheduleSideEffect.RequireLogin)
                        }
                    }
                }

                is LockPicksResult.Error -> {
                    reduce {
                        state.copy(
                            isLoading = false,
                            isRefreshingToken = false
                        )
                    }
                    postSideEffect(ScheduleSideEffect.ShowToast("Failed to submit picks: ${result.message}"))
                }
            }
        } catch (e: Exception) {
            reduce {
                state.copy(
                    isLoading = false,
                    isRefreshingToken = false
                )
            }
            postSideEffect(ScheduleSideEffect.ShowToast("Error submitting picks: ${e.message}"))
        }
    }
}