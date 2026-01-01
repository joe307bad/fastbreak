@file:UseSerializers(InstantSerializer::class)

package com.joebad.fastbreak.data.model

import com.joebad.fastbreak.data.serializers.InstantSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonPrimitive

// Base interface for all visualization types
sealed interface VisualizationType {
    val sport: String
    val visualizationType: String
    val title: String
    val subtitle: String
    val description: String
    val lastUpdated: Instant
    val source: String?
}

// Bar Graph data structures
@Serializable
data class BarGraphDataPoint(
    val label: String,
    val value: Double,
    val division: String? = null,
    val conference: String? = null
)

@Serializable
data class BarGraphVisualization(
    override val sport: String,
    override val visualizationType: String,
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    override val source: String? = null,
    val dataPoints: List<BarGraphDataPoint>
) : VisualizationType

// Scatter Plot data structures
@Serializable
data class ScatterPlotDataPoint(
    val label: String,
    val x: Double,
    val y: Double,
    val sum: Double,
    val teamCode: String? = null,
    val division: String? = null,
    val conference: String? = null,
    val color: String? = null // Optional hex color for the dot (e.g., "#2196F3")
)

@Serializable
data class QuadrantConfig(
    val color: String,
    val label: String,
    val lightModeColor: String? = null
)

@Serializable
data class ScatterPlotVisualization(
    override val sport: String,
    override val visualizationType: String,
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    override val source: String? = null,
    val xAxisLabel: String,
    val yAxisLabel: String,
    val xColumnLabel: String? = null,
    val yColumnLabel: String? = null,
    val invertYAxis: Boolean = false,
    val quadrantTopRight: QuadrantConfig? = null,
    val quadrantTopLeft: QuadrantConfig? = null,
    val quadrantBottomLeft: QuadrantConfig? = null,
    val quadrantBottomRight: QuadrantConfig? = null,
    val subject: String? = null,
    val dataPoints: List<ScatterPlotDataPoint>
) : VisualizationType

// Line Chart data structures
@Serializable
data class LineChartDataPoint(
    val x: Double,
    val y: Double
)

@Serializable
data class LineChartSeries(
    val label: String,
    val dataPoints: List<LineChartDataPoint>,
    /**
     * Optional hex color for this series (e.g., "#FF5722")
     * If not provided, a default color from the palette will be used
     */
    val color: String? = null,
    val division: String? = null,
    val conference: String? = null
)

@Serializable
data class LineChartVisualization(
    override val sport: String,
    override val visualizationType: String,
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    override val source: String? = null,
    val series: List<LineChartSeries>
) : VisualizationType

// Table View data structures
@Serializable
data class TableColumn(
    val label: String,
    val value: String
)

@Serializable
data class TableDataPoint(
    val label: String,
    val columns: List<TableColumn>
)

@Serializable
data class TableVisualization(
    override val sport: String,
    override val visualizationType: String,
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    override val source: String? = null,
    val dataPoints: List<TableDataPoint>
) : VisualizationType

// Matchup Report Card data structures
@Serializable
data class MatchupComparison(
    val title: String,
    val homeTeamValue: JsonPrimitive,
    val awayTeamValue: JsonPrimitive,
    /** When true, lower values are better (e.g., defensive stats, points allowed) */
    val inverted: Boolean = false
) {
    /**
     * Returns the home team value as a Double if it's numeric, null otherwise
     */
    fun homeValueAsDouble(): Double? = homeTeamValue.content.toDoubleOrNull()

    /**
     * Returns the away team value as a Double if it's numeric, null otherwise
     */
    fun awayValueAsDouble(): Double? = awayTeamValue.content.toDoubleOrNull()

    /**
     * Returns the home team value as a display string
     */
    fun homeValueDisplay(): String = homeTeamValue.content

    /**
     * Returns the away team value as a display string
     */
    fun awayValueDisplay(): String = awayTeamValue.content
}

@Serializable
data class Matchup(
    val homeTeam: String,
    val awayTeam: String,
    val week: Int,
    val gameTime: String? = null,
    val comparisons: List<MatchupComparison>,
    val homeTeamDivision: String? = null,
    val homeTeamConference: String? = null,
    val awayTeamDivision: String? = null,
    val awayTeamConference: String? = null
)

@Serializable
data class MatchupVisualization(
    override val sport: String,
    override val visualizationType: String,
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    override val source: String? = null,
    val week: Int,
    val dataPoints: List<Matchup>
) : VisualizationType

// MATCHUP_V2 data structures (new comprehensive matchup stats)
@Serializable
data class StatWithRank(
    val value: Double?,
    val rank: Int?
)

@Serializable
data class EPAByWeek(
    val off: Double?,
    val def: Double?
)

@Serializable
data class TeamStatsV2(
    val cum_epa_by_week: Map<String, Double>, // e.g., "week-1" -> 0.5
    val epa_by_week: Map<String, EPAByWeek>,  // e.g., "week-1" -> { off: 0.1, def: -0.2 }
    val current: Map<String, StatWithRank>     // e.g., "off_epa" -> { value: 0.5, rank: 12 }
)

@Serializable
data class QBStats(
    val name: String,
    val passing_epa: StatWithRank?,
    val passing_cpoe: StatWithRank?,
    val pacr: StatWithRank?,
    val passing_air_yards: StatWithRank?
)

@Serializable
data class RBStats(
    val name: String,
    val rushing_epa: StatWithRank?,
    val rushing_first_downs: StatWithRank?,
    val carries: StatWithRank?,
    val receiving_epa: StatWithRank?,
    val target_share: StatWithRank?
)

@Serializable
data class ReceiverStats(
    val name: String,
    val wopr: StatWithRank?,
    val receiving_epa: StatWithRank?,
    val racr: StatWithRank?,
    val target_share: StatWithRank?,
    val air_yards_share: StatWithRank?
)

@Serializable
data class PlayerStatsV2(
    val qb: QBStats?,
    val rbs: List<RBStats>,
    val receivers: List<ReceiverStats>
)

@Serializable
data class TeamDataV2(
    val team_stats: TeamStatsV2,
    val players: PlayerStatsV2
)

@Serializable
data class GameOddsV2(
    val spread: Double?,
    val moneyline: String?,
    val over_under: Double?
)

@Serializable
data class CommonOpponentGame(
    val week: Int,
    val result: String, // "W" or "L"
    val score: String   // e.g., "27-19"
)

// Using JsonObject to handle dynamic team properties
typealias MatchupV2 = kotlinx.serialization.json.JsonObject

@Serializable
data class MatchupV2Visualization(
    override val sport: String,
    override val visualizationType: String,
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    override val source: String? = null,
    val week: Int,
    val dataPoints: Map<String, MatchupV2> // matchup key (e.g., "car-tb") -> matchup data
) : VisualizationType
