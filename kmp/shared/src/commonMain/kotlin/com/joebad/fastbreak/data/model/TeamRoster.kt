@file:UseSerializers(InstantSerializer::class)

package com.joebad.fastbreak.data.model

import com.joebad.fastbreak.data.serializers.InstantSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Represents a single team in a league roster.
 */
@Serializable
data class Team(
    val code: String,           // Team abbreviation (e.g., "PHI", "LAL", "BOS")
    val longLabel: String,      // Searchable label (e.g., "NFL - Philadelphia Eagles")
    val conference: String,     // Conference name (e.g., "NFC", "Eastern", "American League")
    val division: String        // Division name (e.g., "NFC East", "Atlantic", "AL East")
)

/**
 * Represents the complete roster of teams for a specific sport/league.
 */
@Serializable
data class TeamRoster(
    val sport: String,              // Sport identifier (e.g., "NFL", "NBA", "NHL", "MLB")
    val lastUpdated: Instant,       // When this roster was last updated
    val teams: List<Team>           // List of all teams in the league
)

/**
 * Represents a user's pinned team for filtering.
 */
@Serializable
data class PinnedTeam(
    val sport: String,
    val teamCode: String,
    val teamLabel: String,          // Store for display without needing to re-fetch roster
    val pinnedAt: Instant           // When the team was pinned
)
