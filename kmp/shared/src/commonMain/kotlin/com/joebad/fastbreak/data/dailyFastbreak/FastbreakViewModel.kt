package com.joebad.fastbreak.data.dailyFastbreak

import com.joebad.fastbreak.model.dtos.FastbreakSelectionsResult
import getRandomId
import kotlinx.serialization.Serializable

@Serializable
data class StatSheetItemView(
    val statSheetType: StatSheetType,
    val leftColumnText: String,
    val rightColumnText: String,
)

enum class StatSheetType {
    Button,
    MonoSpace
}

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
    val selections: List<FastbreakSelection>? = null,
    val totalPoints: Int = 0,
    val cardId: String = getRandomId(),
    val locked: Boolean? = false,
    val date: String,
    val statSheetItems: List<StatSheetItemView> = emptyList(),
    val results: FastbreakSelectionsResult? = null,
    val lastLockedCardResults: FastbreakSelectionState? = null,
    val isSavingUserName: Boolean = false,
    val isLocking: Boolean = false
)

sealed class FastbreakSideEffect {

    data class SelectionAdded(val selection: FastbreakSelection) : FastbreakSideEffect()
    data class SelectionUpdated(val selection: FastbreakSelection) : FastbreakSideEffect()
    data class SelectionDeleted(val selection: FastbreakSelection) : FastbreakSideEffect()
    data class CardLocked(val state: FastbreakSelectionState) : FastbreakSideEffect()
    object ShowSigninBottomSheet : FastbreakSideEffect()
}

