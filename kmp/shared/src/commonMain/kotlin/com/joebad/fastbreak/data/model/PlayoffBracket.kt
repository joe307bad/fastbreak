@file:UseSerializers(InstantSerializer::class)

package com.joebad.fastbreak.data.model

import com.joebad.fastbreak.data.serializers.InstantSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Represents a single team in the playoff bracket
 */
@Serializable
data class PlayoffTeam(
    val name: String,
    val code: String, // 3-letter team code (e.g., "KC", "BUF", "PHI")
    val seed: Int,
    val conference: String
)

/**
 * Represents a single matchup in a playoff round
 */
@Serializable
data class PlayoffMatchup(
    val team1: PlayoffTeam?,
    val team2: PlayoffTeam?,
    val winner: PlayoffTeam? = null
)

/**
 * Represents a playoff round (e.g., Wild Card, Divisional, Conference Championship, Super Bowl)
 */
@Serializable
data class PlayoffRound(
    val name: String,
    val matchups: List<PlayoffMatchup>
)

/**
 * Playoff bracket visualization
 */
@Serializable
data class PlayoffBracketVisualization(
    override val sport: String,
    override val visualizationType: String,
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    override val source: String? = null,
    val rounds: List<PlayoffRound>
) : VisualizationType
