package com.joebad.fastbreak.model.dtos

import kotlinx.serialization.Serializable

@Serializable
data class DayInfo(
    val dayOfWeek: String,
    val dateCode: String,
    val totalPoints: Int? = null
)

@Serializable
data class Week(
    val days: List<DayInfo>,
    val total: Int
)

@Serializable
data class Streak(
    val longest: Int,
    val current: Int
)

@Serializable
data class FastbreakCard(
    val date: String,
    val points: Int
)

@Serializable
data class PerfectFastbreakCards(
    val cards: List<FastbreakCard>,
    val highest: FastbreakCard
)

@Serializable
data class StatSheetItem(
    val currentWeek: Week,
    val lastWeek: Week,
    val lockedCardStreak: Streak,
    val highestFastbreakCardEver: FastbreakCard,
    val perfectFastbreakCards: PerfectFastbreakCards,
    val cardResults: FastbreakSelectionsResult
)

@Serializable
data class StatSheetResponse(
    val userId: String,
    val date: String,
    val items: StatSheetItem,
    val createdAt: String
)

@Serializable

data class FastbreakSelectionsResult(
    val totalPoints: Int,
    val totalCorrect: Int,
    val totalIncorrect: Int,
    val correct: Array<String>,
    val incorrect: Array<String>,
    val date: String
)