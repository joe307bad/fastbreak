package com.example.kmpapp.data.model

import kotlinx.datetime.Instant

data class Visualization(
    val id: String,
    val title: String,
    val description: String,
    val lastUpdated: Instant
)

object VisualizationData {
    val nflVisualizations = listOf(
        Visualization(
            id = "nfl_power_rankings",
            title = "Power Rankings",
            description = "Team strength over time",
            lastUpdated = Instant.parse("2025-01-15T08:30:00Z")
        ),
        Visualization(
            id = "nfl_elo_ratings",
            title = "Elo Ratings",
            description = "Statistical team ratings",
            lastUpdated = Instant.parse("2025-01-15T09:15:00Z")
        ),
        Visualization(
            id = "nfl_playoff_probability",
            title = "Playoff Probability",
            description = "Postseason chances",
            lastUpdated = Instant.parse("2025-01-14T22:45:00Z")
        ),
        Visualization(
            id = "nfl_offensive_efficiency",
            title = "Offensive Efficiency",
            description = "Points per drive analysis",
            lastUpdated = Instant.parse("2025-01-14T18:20:00Z")
        ),
        Visualization(
            id = "nfl_defensive_rankings",
            title = "Defensive Rankings",
            description = "Yards allowed comparison",
            lastUpdated = Instant.parse("2025-01-15T06:00:00Z")
        ),
        Visualization(
            id = "nfl_turnover_differential",
            title = "Turnover Differential",
            description = "Ball security metrics",
            lastUpdated = Instant.parse("2025-01-15T10:30:00Z")
        )
    )

    val nbaVisualizations = listOf(
        Visualization(
            id = "nba_power_rankings",
            title = "Power Rankings",
            description = "Team performance trends",
            lastUpdated = Instant.parse("2025-01-15T07:00:00Z")
        ),
        Visualization(
            id = "nba_offensive_rating",
            title = "Offensive Rating",
            description = "Points per 100 possessions",
            lastUpdated = Instant.parse("2025-01-15T11:20:00Z")
        ),
        Visualization(
            id = "nba_defensive_rating",
            title = "Defensive Rating",
            description = "Defensive efficiency",
            lastUpdated = Instant.parse("2025-01-14T23:30:00Z")
        ),
        Visualization(
            id = "nba_net_rating",
            title = "Net Rating",
            description = "Overall team efficiency",
            lastUpdated = Instant.parse("2025-01-15T05:45:00Z")
        ),
        Visualization(
            id = "nba_pace_analysis",
            title = "Pace Analysis",
            description = "Game speed metrics",
            lastUpdated = Instant.parse("2025-01-15T09:00:00Z")
        ),
        Visualization(
            id = "nba_three_point_trends",
            title = "Three-Point Trends",
            description = "Long range shooting stats",
            lastUpdated = Instant.parse("2025-01-14T20:15:00Z")
        ),
        Visualization(
            id = "nba_player_impact",
            title = "Player Impact",
            description = "Individual contributions",
            lastUpdated = Instant.parse("2025-01-15T08:00:00Z")
        )
    )

    val mlbVisualizations = listOf(
        Visualization(
            id = "mlb_power_rankings",
            title = "Power Rankings",
            description = "Team standings analysis",
            lastUpdated = Instant.parse("2025-01-14T19:30:00Z")
        ),
        Visualization(
            id = "mlb_run_differential",
            title = "Run Differential",
            description = "Scoring margin trends",
            lastUpdated = Instant.parse("2025-01-15T06:45:00Z")
        ),
        Visualization(
            id = "mlb_pitching_stats",
            title = "Pitching Stats",
            description = "ERA and strikeout rates",
            lastUpdated = Instant.parse("2025-01-15T10:00:00Z")
        ),
        Visualization(
            id = "mlb_batting_average",
            title = "Batting Average",
            description = "Offensive performance",
            lastUpdated = Instant.parse("2025-01-14T21:30:00Z")
        ),
        Visualization(
            id = "mlb_home_runs",
            title = "Home Run Leaders",
            description = "Power hitting metrics",
            lastUpdated = Instant.parse("2025-01-15T07:15:00Z")
        ),
        Visualization(
            id = "mlb_stolen_bases",
            title = "Stolen Bases",
            description = "Base running analysis",
            lastUpdated = Instant.parse("2025-01-14T17:00:00Z")
        ),
        Visualization(
            id = "mlb_playoff_odds",
            title = "Playoff Odds",
            description = "Postseason probability",
            lastUpdated = Instant.parse("2025-01-15T09:45:00Z")
        )
    )

    fun getVisualizationsForSport(sport: Sport): List<Visualization> {
        return when (sport) {
            Sport.NFL -> nflVisualizations
            Sport.NBA -> nbaVisualizations
            Sport.MLB -> mlbVisualizations
        }
    }
}
