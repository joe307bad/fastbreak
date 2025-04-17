package com.joebad.fastbreak.model.dtos
import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import kotlinx.serialization.Serializable

@Serializable
data class LeaderboardItem(val id: String, val user: String, val points: Int)


@Serializable
data class EmptyFastbreakCardItem(
    val id: String,
    val type: String,
    val homeTeam: String? = null,
    val homeTeamSubtitle: String? = null,
    val awayTeam: String? = null,
    val awayTeamSubtitle: String? = null,
    val dateLine1: String? = null,
    val dateLine2: String? = null,
    val dateLine3: String? = null,
    val points: Int,
    val question: String? = null,
    val answer1: String? = null,
    val answer2: String? = null,
    val answer3: String? = null,
    val answer4: String? = null,
    val correctAnswer: String? = null
)


@Serializable
data class DailyFastbreak(
    val leaderboard: List<LeaderboardItem>,
    val fastbreakCard: List<EmptyFastbreakCardItem>,
)


@Serializable
data class DailyResponse(
    val leaderboard: List<LeaderboardItem>,
    val fastbreakCard: List<EmptyFastbreakCardItem>,
    val lockedCardForUser: FastbreakSelectionState? = null
)