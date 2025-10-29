package com.joebad.fastbreak.data.api

import com.joebad.fastbreak.data.model.ChartDefinition
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.VizType
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Mock API for generating registry data.
 * Simulates network delay and returns a predefined registry of charts.
 */
class MockRegistryApi {

    /**
     * Fetches the mock registry with a simulated network delay.
     * @return Result containing the Registry or an error
     */
    suspend fun fetchRegistry(): Result<Registry> = runCatching {
        // Simulate network delay
        delay(600)

        val now = Clock.System.now()

        Registry(
            version = "1.0",
            lastUpdated = now,
            charts = buildChartList(now)
        )
    }

    private fun buildChartList(now: kotlinx.datetime.Instant): List<ChartDefinition> {
        return listOf(
            // NFL Charts (4 charts)
            ChartDefinition(
                id = "nfl-efficiency-scatter",
                sport = Sport.NFL,
                title = "Team Efficiency Analysis",
                subtitle = "Offensive vs Defensive Performance",
                lastUpdated = now.minus(2.hours),
                visualizationType = VizType.SCATTER_PLOT,
                mockDataType = "scatter"
            ),
            ChartDefinition(
                id = "nfl-point-differential",
                sport = Sport.NFL,
                title = "Point Differential by Team",
                subtitle = "Season performance margins",
                lastUpdated = now.minus(3.hours),
                visualizationType = VizType.BAR_GRAPH,
                mockDataType = "bar"
            ),
            ChartDefinition(
                id = "nfl-season-progression",
                sport = Sport.NFL,
                title = "Win-Loss Progression",
                subtitle = "Cumulative wins over season",
                lastUpdated = now.minus(1.hours),
                visualizationType = VizType.LINE_CHART,
                mockDataType = "line"
            ),
            ChartDefinition(
                id = "nfl-passing-leaders",
                sport = Sport.NFL,
                title = "Passing Yards Leaders",
                subtitle = "Top quarterbacks by yards",
                lastUpdated = now.minus(45.minutes),
                visualizationType = VizType.BAR_GRAPH,
                mockDataType = "bar"
            ),

            // NBA Charts (4 charts)
            ChartDefinition(
                id = "nba-efficiency-rating",
                sport = Sport.NBA,
                title = "Player Efficiency Rating",
                subtitle = "PER vs Usage Rate",
                lastUpdated = now.minus(1.hours),
                visualizationType = VizType.SCATTER_PLOT,
                mockDataType = "scatter"
            ),
            ChartDefinition(
                id = "nba-scoring-differential",
                sport = Sport.NBA,
                title = "Team Net Rating",
                subtitle = "Points per 100 possessions",
                lastUpdated = now.minus(2.hours),
                visualizationType = VizType.BAR_GRAPH,
                mockDataType = "bar"
            ),
            ChartDefinition(
                id = "nba-season-standings",
                sport = Sport.NBA,
                title = "Season Win Progression",
                subtitle = "Wins accumulated over time",
                lastUpdated = now.minus(30.minutes),
                visualizationType = VizType.LINE_CHART,
                mockDataType = "line"
            ),
            ChartDefinition(
                id = "nba-three-point-leaders",
                sport = Sport.NBA,
                title = "Three-Point Shooting Leaders",
                subtitle = "Made 3-pointers by player",
                lastUpdated = now.minus(90.minutes),
                visualizationType = VizType.BAR_GRAPH,
                mockDataType = "bar"
            ),

            // MLB Charts (4 charts)
            ChartDefinition(
                id = "mlb-batting-analysis",
                sport = Sport.MLB,
                title = "Batting Performance",
                subtitle = "Average vs Home Runs",
                lastUpdated = now.minus(4.hours),
                visualizationType = VizType.SCATTER_PLOT,
                mockDataType = "scatter"
            ),
            ChartDefinition(
                id = "mlb-run-differential",
                sport = Sport.MLB,
                title = "Team Run Differential",
                subtitle = "Runs scored vs allowed",
                lastUpdated = now.minus(5.hours),
                visualizationType = VizType.BAR_GRAPH,
                mockDataType = "bar"
            ),
            ChartDefinition(
                id = "mlb-season-wins",
                sport = Sport.MLB,
                title = "Season Win Progression",
                subtitle = "Cumulative wins by team",
                lastUpdated = now.minus(2.hours),
                visualizationType = VizType.LINE_CHART,
                mockDataType = "line"
            ),
            ChartDefinition(
                id = "mlb-era-leaders",
                sport = Sport.MLB,
                title = "ERA Leaders",
                subtitle = "Top pitchers by ERA",
                lastUpdated = now.minus(3.hours),
                visualizationType = VizType.BAR_GRAPH,
                mockDataType = "bar"
            ),

            // NHL Charts (4 charts)
            ChartDefinition(
                id = "nhl-player-production",
                sport = Sport.NHL,
                title = "Player Production",
                subtitle = "Goals vs Assists",
                lastUpdated = now.minus(1.hours),
                visualizationType = VizType.SCATTER_PLOT,
                mockDataType = "scatter"
            ),
            ChartDefinition(
                id = "nhl-goal-differential",
                sport = Sport.NHL,
                title = "Team Goal Differential",
                subtitle = "Goals for vs against",
                lastUpdated = now.minus(2.hours),
                visualizationType = VizType.BAR_GRAPH,
                mockDataType = "bar"
            ),
            ChartDefinition(
                id = "nhl-season-points",
                sport = Sport.NHL,
                title = "Season Points Progression",
                subtitle = "Points accumulated over season",
                lastUpdated = now.minus(90.minutes),
                visualizationType = VizType.LINE_CHART,
                mockDataType = "line"
            ),
            ChartDefinition(
                id = "nhl-save-percentage",
                sport = Sport.NHL,
                title = "Goalie Save Percentage",
                subtitle = "Top goalies by save %",
                lastUpdated = now.minus(4.hours),
                visualizationType = VizType.BAR_GRAPH,
                mockDataType = "bar"
            )
        )
    }
}
