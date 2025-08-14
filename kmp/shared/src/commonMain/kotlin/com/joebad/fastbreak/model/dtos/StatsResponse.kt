package com.joebad.fastbreak.model.dtos

import kotlinx.serialization.Serializable

@Serializable
data class StatsResponse(
    val weeklyLeaderboard: LeaderboardResult? = null,
    val statSheetForUser: StatSheetResponse? = null,
    val weekStartDate: String,
    val requestedDate: String,
    val previousDay: String
)