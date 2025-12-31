@file:UseSerializers(InstantSerializer::class)

package com.joebad.fastbreak.data.model

import com.joebad.fastbreak.data.serializers.InstantSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Comprehensive matchup analytics for a playoff game
 */
@Serializable
data class MatchupAnalytics(
    val team1: TeamAnalytics,
    val team2: TeamAnalytics,
    val odds: GameOdds?,
    val headToHead: List<HeadToHeadResult>,
    val commonOpponents: List<CommonOpponentResult>
)

@Serializable
data class TeamAnalytics(
    val name: String,
    val code: String, // 3-letter team code (e.g., "KC", "BUF", "PHI")
    val seed: Int,
    val conference: String,
    val record: String,
    val advancedStats: Map<String, String>, // e.g., "Off. Rating" -> "115.2"
    val keyPlayers: List<PlayerStats>,
    val weeklyEPA: List<WeeklyEPA> // EPA data for each week
)

@Serializable
data class WeeklyEPA(
    val week: Int,
    val offensiveEPA: Double,
    val defensiveEPA: Double,
    val cumulativeEPA: Double
)

@Serializable
data class PlayerStats(
    val name: String,
    val position: String,
    val stats: Map<String, String> // e.g., "PPG" -> "28.5", "RPG" -> "8.2"
)

@Serializable
data class GameOdds(
    val favorite: String, // Team name
    val spread: String, // e.g., "-3.5"
    val moneyline: String, // e.g., "-150"
    val overUnder: String, // e.g., "220.5"
    val source: String
)

@Serializable
data class HeadToHeadResult(
    val date: String,
    val team1Score: Int,
    val team2Score: Int,
    val location: String // "Home", "Away", or "Neutral"
)

@Serializable
data class CommonOpponentResult(
    val opponent: String,
    val team1Result: GameResult,
    val team2Result: GameResult
)

@Serializable
data class GameResult(
    val date: String,
    val score: String, // e.g., "W 115-110" or "L 98-102"
    val location: String // "H", "A", or "N"
)
