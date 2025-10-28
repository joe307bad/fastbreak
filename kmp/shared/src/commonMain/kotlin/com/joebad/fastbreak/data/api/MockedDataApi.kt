package com.joebad.fastbreak.data.api

import com.joebad.fastbreak.data.model.*
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Mocked API that generates sports analytics data for multiple sports and visualization types.
 * Supports: NFL, NBA, MLB, NHL
 * Visualization types: Scatter Plot, Bar Graph, Line Chart
 */
class MockedDataApi {

    enum class Sport {
        NFL, NBA, MLB, NHL
    }

    enum class VizType {
        SCATTER, BAR, LINE
    }

    /**
     * Fetches visualization data for a specific sport and visualization type.
     */
    suspend fun fetchVisualizationData(sport: Sport, vizType: VizType): VisualizationType {
        // Simulate network delay
        delay(800)

        return when (vizType) {
            VizType.SCATTER -> generateScatterPlotData(sport)
            VizType.BAR -> generateBarGraphData(sport)
            VizType.LINE -> generateLineChartData(sport)
        }
    }

    // ===== SCATTER PLOT DATA GENERATION =====

    private fun generateScatterPlotData(sport: Sport): ScatterPlotVisualization {
        val (title, xLabel, yLabel) = when (sport) {
            Sport.NFL -> Triple("QB Performance Matrix", "Passer Rating", "EPA per Play")
            Sport.NBA -> Triple("Player Efficiency Analysis", "Usage Rate %", "True Shooting %")
            Sport.MLB -> Triple("Pitcher Performance", "ERA", "WHIP")
            Sport.NHL -> Triple("Goalie Performance", "Save %", "Goals Against Avg")
        }

        val dataPoints = generateScatterDataPoints(sport)

        return ScatterPlotVisualization(
            title = title,
            subtitle = "${sport.name} 2024 Season",
            description = "Performance comparison: $xLabel vs $yLabel",
            lastUpdated = Clock.System.now(),
            dataPoints = dataPoints
        )
    }

    private fun generateScatterDataPoints(sport: Sport): List<ScatterPlotDataPoint> {
        return when (sport) {
            Sport.NFL -> listOf(
                "P. Mahomes", "J. Allen", "L. Jackson", "J. Burrow", "J. Herbert",
                "T. Tagovailoa", "D. Prescott", "J. Hurts", "B. Purdy", "G. Smith",
                "K. Cousins", "M. Stafford", "J. Goff", "D. Jones", "R. Wilson"
            ).mapIndexed { index, name ->
                val rating = Random.nextDouble(75.0, 110.0)
                val epa = Random.nextDouble(-0.1, 0.35)
                ScatterPlotDataPoint(name, rating, epa, rating + (epa * 100))
            }
            Sport.NBA -> listOf(
                "L. James", "G. Antetokounmpo", "J. Embiid", "L. Doncic", "N. Jokic",
                "K. Durant", "S. Curry", "D. Lillard", "J. Tatum", "J. Butler",
                "A. Davis", "K. Irving", "D. Booker", "T. Young", "J. Murray"
            ).mapIndexed { index, name ->
                val usage = Random.nextDouble(20.0, 35.0)
                val ts = Random.nextDouble(52.0, 68.0)
                ScatterPlotDataPoint(name, usage, ts, usage + ts)
            }
            Sport.MLB -> listOf(
                "G. Cole", "S. Alcantara", "C. Burnes", "Z. Wheeler", "M. Fried",
                "B. Snell", "K. Gausman", "L. Castillo", "N. Syndergaard", "D. Cease",
                "C. Kershaw", "A. Nola", "Y. Darvish", "J. Verlander", "M. Scherzer"
            ).mapIndexed { index, name ->
                val era = Random.nextDouble(2.5, 4.5)
                val whip = Random.nextDouble(0.95, 1.4)
                ScatterPlotDataPoint(name, era, whip, era + whip)
            }
            Sport.NHL -> listOf(
                "A. Shesterkin", "L. Ullmark", "I. Shesterkin", "J. Oettinger", "C. Hellebuyck",
                "I. Sorokin", "F. Andersen", "J. Markstrom", "A. Vasilevskiy", "T. Jarry",
                "J. Campbell", "P. Grubauer", "D. Kuemper", "J. Quick", "M. Fleury"
            ).mapIndexed { index, name ->
                val savePct = Random.nextDouble(0.900, 0.930)
                val gaa = Random.nextDouble(2.0, 3.2)
                ScatterPlotDataPoint(name, savePct * 100, gaa, (savePct * 100) + gaa)
            }
        }
    }

    // ===== BAR GRAPH DATA GENERATION =====

    private fun generateBarGraphData(sport: Sport): BarGraphVisualization {
        val (title, metric) = when (sport) {
            Sport.NFL -> Pair("Team Total Yards", "Total Yards")
            Sport.NBA -> Pair("Team Points Per Game", "PPG")
            Sport.MLB -> Pair("Team Home Runs", "Home Runs")
            Sport.NHL -> Pair("Team Goals Scored", "Goals")
        }

        val dataPoints = generateBarDataPoints(sport)

        return BarGraphVisualization(
            title = title,
            subtitle = "${sport.name} 2024 Season",
            description = "Team performance by $metric",
            lastUpdated = Clock.System.now(),
            dataPoints = dataPoints
        )
    }

    private fun generateBarDataPoints(sport: Sport): List<BarGraphDataPoint> {
        return when (sport) {
            Sport.NFL -> listOf(
                "49ers", "Cowboys", "Bills", "Eagles", "Chiefs",
                "Lions", "Dolphins", "Ravens", "Bengals", "Packers",
                "Rams", "Browns", "Cardinals", "Bears", "Panthers"
            ).map { team ->
                // Include some negative values for differential
                val value = if (Random.nextBoolean()) {
                    Random.nextDouble(250.0, 450.0)
                } else {
                    Random.nextDouble(-150.0, -50.0)
                }
                BarGraphDataPoint(team, value)
            }
            Sport.NBA -> listOf(
                "Celtics", "Bucks", "Nuggets", "Suns", "Lakers",
                "Warriors", "Heat", "76ers", "Mavericks", "Kings",
                "Cavaliers", "Knicks", "Pelicans", "Clippers", "Grizzlies"
            ).map { team ->
                val value = if (Random.nextBoolean()) {
                    Random.nextDouble(105.0, 125.0)
                } else {
                    Random.nextDouble(-15.0, -5.0)
                }
                BarGraphDataPoint(team, value)
            }
            Sport.MLB -> listOf(
                "Yankees", "Dodgers", "Braves", "Astros", "Rays",
                "Blue Jays", "Mets", "Padres", "Cardinals", "Phillies",
                "Mariners", "Rangers", "Orioles", "Twins", "Guardians"
            ).map { team ->
                val value = if (Random.nextBoolean()) {
                    Random.nextDouble(180.0, 260.0)
                } else {
                    Random.nextDouble(-40.0, -10.0)
                }
                BarGraphDataPoint(team, value)
            }
            Sport.NHL -> listOf(
                "Bruins", "Hurricanes", "Avalanche", "Oilers", "Rangers",
                "Devils", "Stars", "Maple Leafs", "Panthers", "Kraken",
                "Wild", "Jets", "Golden Knights", "Kings", "Flames"
            ).map { team ->
                val value = if (Random.nextBoolean()) {
                    Random.nextDouble(200.0, 320.0)
                } else {
                    Random.nextDouble(-50.0, -15.0)
                }
                BarGraphDataPoint(team, value)
            }
        }
    }

    // ===== LINE CHART DATA GENERATION =====

    private fun generateLineChartData(sport: Sport): LineChartVisualization {
        val (title, yLabel) = when (sport) {
            Sport.NFL -> Pair("Season Win Progression", "Wins")
            Sport.NBA -> Pair("Season Win Progression", "Wins")
            Sport.MLB -> Pair("Season Win Progression", "Wins")
            Sport.NHL -> Pair("Season Points Progression", "Points")
        }

        val series = generateLineChartSeries(sport)

        return LineChartVisualization(
            title = title,
            subtitle = "${sport.name} 2024 Season - Top Teams",
            description = "Week-by-week progression of $yLabel",
            lastUpdated = Clock.System.now(),
            series = series
        )
    }

    private fun generateLineChartSeries(sport: Sport): List<LineChartSeries> {
        val (teams, weeks, range) = when (sport) {
            Sport.NFL -> Triple(
                listOf("Chiefs", "49ers", "Ravens", "Eagles"),
                18,
                0.0 to 17.0
            )
            Sport.NBA -> Triple(
                listOf("Celtics", "Nuggets", "Bucks", "Suns"),
                82,
                0.0 to 82.0
            )
            Sport.MLB -> Triple(
                listOf("Braves", "Dodgers", "Astros", "Yankees"),
                162,
                0.0 to 162.0
            )
            Sport.NHL -> Triple(
                listOf("Bruins", "Hurricanes", "Avalanche", "Rangers"),
                82,
                0.0 to 164.0 // NHL uses points (max 164 for 82 wins)
            )
        }

        return teams.map { team ->
            val dataPoints = generateProgressionData(weeks, range.first, range.second)
            LineChartSeries(team, dataPoints)
        }
    }

    private fun generateProgressionData(weeks: Int, minY: Double, maxY: Double): List<LineChartDataPoint> {
        val points = mutableListOf<LineChartDataPoint>()
        var currentY = 0.0
        val increment = maxY / weeks

        for (week in 0..weeks) {
            // Add some randomness but generally trending upward
            val noise = Random.nextDouble(-increment * 0.3, increment * 0.3)
            currentY = (currentY + increment + noise).coerceIn(0.0, maxY)
            points.add(LineChartDataPoint(week.toDouble(), currentY))
        }

        return points
    }
}
