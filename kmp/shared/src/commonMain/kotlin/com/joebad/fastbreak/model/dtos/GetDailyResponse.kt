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
    val leaderboard: LeaderboardResult,
    val fastbreakCard: List<EmptyFastbreakCardItem>,
    val statSheet: StatSheetResponse? = null,
    val lastLockedCardResults: FastbreakSelectionState? = null,
    val lastFetchedDate: Long? = null
)

@Serializable
data class LeaderboardEntry(
    val userId: String,
    val points: Int
)

@Serializable
data class DailyLeaderboard(
    val dateCode: String,
    val entries: List<LeaderboardEntry>
)

@Serializable
data class LeaderboardResult(
    val dailyLeaderboards: List<DailyLeaderboard>,
    val weeklyTotals: List<LeaderboardEntry>
)

@Serializable
data class DailyResponse(
    val leaderboard: LeaderboardResult,
    val fastbreakCard: List<EmptyFastbreakCardItem>,
    val lockedCardForUser: FastbreakSelectionState? = null,
    val statSheetForUser: StatSheetResponse? = null,
    val lastLockedCardResults: FastbreakSelectionState? = null
)