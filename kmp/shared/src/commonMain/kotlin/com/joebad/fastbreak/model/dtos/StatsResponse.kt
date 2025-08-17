package com.joebad.fastbreak.model.dtos

import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import kotlinx.serialization.Serializable

@Serializable
data class StatsResponse(
    val lockedCardForDate: FastbreakSelectionState? = null,
    val weeklyLeaderboard: LeaderboardResult? = null,
    val statSheetForUser: StatSheetResponse? = null,
    val weekStartDate: String,
    val requestedDate: String,
    val previousDay: String
)