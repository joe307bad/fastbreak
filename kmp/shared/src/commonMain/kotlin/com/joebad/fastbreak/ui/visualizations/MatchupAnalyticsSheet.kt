package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.ui.KoalaQuadrantScatterPlot

// Team colors for advantage indicators
private val Team1Color = Color(0xFF2196F3) // Blue
private val Team2Color = Color(0xFFFF9800) // Orange

/**
 * Determines which team has the advantage for a stat comparison
 * Returns: -1 for team1 advantage, 0 for even, 1 for team2 advantage
 */
private fun compareStats(
    team1Value: String,
    team2Value: String,
    higherIsBetter: Boolean = true
): Int {
    // Try to parse as numeric values
    val num1 = team1Value.replace("%", "").replace("+", "").toDoubleOrNull()
    val num2 = team2Value.replace("%", "").replace("+", "").toDoubleOrNull()

    if (num1 == null || num2 == null) return 0

    return when {
        num1 == num2 -> 0
        higherIsBetter -> if (num1 > num2) -1 else 1
        else -> if (num1 < num2) -1 else 1
    }
}

/**
 * Calculates section advantage based on win count
 * Returns: -1 for team1, 0 for even, 1 for team2
 */
private fun calculateSectionAdvantage(team1Wins: Int, team2Wins: Int): Int {
    return when {
        team1Wins > team2Wins -> -1
        team2Wins > team1Wins -> 1
        else -> 0
    }
}

/**
 * Generic three-column layout component for consistent formatting
 */
@Composable
private fun ThreeColumnRow(
    leftText: String,
    centerText: String,
    rightText: String,
    modifier: Modifier = Modifier,
    leftWeight: FontWeight = FontWeight.Medium,
    centerWeight: FontWeight = FontWeight.Normal,
    rightWeight: FontWeight = FontWeight.Medium,
    centerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    advantage: Int = 0 // -1 for left (team1), 0 for even, 1 for right (team2)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            if (advantage == -1) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Team1Color, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = leftText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = leftWeight
            )
        }

        Text(
            text = centerText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            fontWeight = centerWeight,
            color = centerColor
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = rightText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = rightWeight
            )
            if (advantage == 1) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Team2Color, CircleShape)
                )
            }
        }
    }
}

@Composable
fun MatchupAnalyticsSheet(
    analytics: MatchupAnalytics,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Header: Team matchup
            MatchupHeader(analytics.team1, analytics.team2)

            Spacer(modifier = Modifier.height(16.dp))

            // Cumulative EPA Chart
            CumulativeEPAChart(analytics.team1, analytics.team2)

            Spacer(modifier = Modifier.height(16.dp))

            // Weekly EPA Scatter Plot
            WeeklyEPAScatterPlot(analytics.team1, analytics.team2)

            Spacer(modifier = Modifier.height(16.dp))

            // Odds section
            analytics.odds?.let { odds ->
                OddsSection(odds)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Advanced stats comparison
            AdvancedStatsSection(analytics.team1, analytics.team2)

            Spacer(modifier = Modifier.height(16.dp))

            // Key players comparison
            KeyPlayersSection(analytics.team1, analytics.team2)

            Spacer(modifier = Modifier.height(16.dp))

            // Head to head
            if (analytics.headToHead.isNotEmpty()) {
                HeadToHeadSection(
                    results = analytics.headToHead,
                    team1Code = analytics.team1.code,
                    team2Code = analytics.team2.code
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Common opponents
            if (analytics.commonOpponents.isNotEmpty()) {
                CommonOpponentsSection(
                    commonOpponents = analytics.commonOpponents,
                    team1Code = analytics.team1.code,
                    team2Code = analytics.team2.code
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun MatchupHeader(team1: TeamAnalytics, team2: TeamAnalytics) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Team names and seeds
        ThreeColumnRow(
            leftText = "(${team1.seed}) ${team1.code}",
            centerText = "VS",
            rightText = "${team2.code} (${team2.seed})",
            leftWeight = FontWeight.Bold,
            centerWeight = FontWeight.Bold,
            rightWeight = FontWeight.Bold,
            backgroundColor = Color.Transparent
        )

        // Records
        ThreeColumnRow(
            leftText = team1.record,
            centerText = "",
            rightText = team2.record,
            leftWeight = FontWeight.Normal,
            rightWeight = FontWeight.Normal,
            backgroundColor = Color.Transparent
        )
    }
}

@Composable
private fun OddsSection(odds: GameOdds) {
    Text(
        text = "Betting Odds",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ThreeColumnRow(
            leftText = odds.spread,
            centerText = "Spread",
            rightText = odds.favorite
        )

        ThreeColumnRow(
            leftText = odds.moneyline,
            centerText = "ML",
            rightText = "-"
        )

        ThreeColumnRow(
            leftText = "-",
            centerText = "O/U",
            rightText = odds.overUnder
        )

        // Source
        Text(
            text = "Source: ${odds.source}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            fontSize = 9.sp
        )
    }
}

/**
 * Section header with advantage indicator
 */
@Composable
private fun SectionHeader(
    title: String,
    team1Code: String,
    team2Code: String,
    advantage: Int // -1 for team1, 0 for even, 1 for team2
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (advantage) {
                -1 -> {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Team1Color, CircleShape)
                    )
                    Text(
                        text = team1Code,
                        style = MaterialTheme.typography.labelSmall,
                        color = Team1Color,
                        fontWeight = FontWeight.Bold
                    )
                }
                1 -> {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Team2Color, CircleShape)
                    )
                    Text(
                        text = team2Code,
                        style = MaterialTheme.typography.labelSmall,
                        color = Team2Color,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    Text(
                        text = "EVEN",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedStatsSection(team1: TeamAnalytics, team2: TeamAnalytics) {
    // Get all stat keys
    val allStats = (team1.advancedStats.keys + team2.advancedStats.keys).distinct()

    // Calculate which team has advantage
    var team1Wins = 0
    var team2Wins = 0

    // Stats where lower is better
    val lowerIsBetterStats = setOf("Def. Rating", "TOV%")

    allStats.forEach { statName ->
        val team1Value = team1.advancedStats[statName] ?: "-"
        val team2Value = team2.advancedStats[statName] ?: "-"
        val higherIsBetter = !lowerIsBetterStats.contains(statName)
        val advantage = compareStats(team1Value, team2Value, higherIsBetter)
        when (advantage) {
            -1 -> team1Wins++
            1 -> team2Wins++
        }
    }

    val sectionAdvantage = calculateSectionAdvantage(team1Wins, team2Wins)

    SectionHeader(
        title = "Advanced Stats",
        team1Code = team1.code,
        team2Code = team2.code,
        advantage = sectionAdvantage
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        allStats.forEach { statName ->
            val team1Value = team1.advancedStats[statName] ?: "-"
            val team2Value = team2.advancedStats[statName] ?: "-"
            val higherIsBetter = !lowerIsBetterStats.contains(statName)
            val advantage = compareStats(team1Value, team2Value, higherIsBetter)

            StatComparisonRow(
                statName = statName,
                team1Value = team1Value,
                team2Value = team2Value,
                advantage = advantage
            )
        }
    }
}

@Composable
private fun StatComparisonRow(
    statName: String,
    team1Value: String,
    team2Value: String,
    advantage: Int = 0
) {
    ThreeColumnRow(
        leftText = team1Value,
        centerText = statName,
        rightText = team2Value,
        advantage = advantage
    )
}

@Composable
private fun KeyPlayersSection(
    team1: TeamAnalytics,
    team2: TeamAnalytics
) {
    val team1Players = team1.keyPlayers
    val team2Players = team2.keyPlayers

    // Calculate section advantage based on all player stat comparisons
    var team1Wins = 0
    var team2Wins = 0

    val maxPlayers = maxOf(team1Players.size, team2Players.size)
    for (i in 0 until maxPlayers) {
        val player1 = team1Players.getOrNull(i)
        val player2 = team2Players.getOrNull(i)

        if (player1 != null && player2 != null) {
            val stats1 = player1.stats
            val stats2 = player2.stats
            val relevantStats = (stats1.keys + stats2.keys).distinct()

            relevantStats.forEach { statName ->
                val team1Value = stats1[statName] ?: "-"
                val team2Value = stats2[statName] ?: "-"
                val advantage = compareStats(team1Value, team2Value, higherIsBetter = true)
                when (advantage) {
                    -1 -> team1Wins++
                    1 -> team2Wins++
                }
            }
        }
    }

    val sectionAdvantage = calculateSectionAdvantage(team1Wins, team2Wins)

    SectionHeader(
        title = "Key Players",
        team1Code = team1.code,
        team2Code = team2.code,
        advantage = sectionAdvantage
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0 until maxPlayers) {
            val player1 = team1Players.getOrNull(i)
            val player2 = team2Players.getOrNull(i)

            PlayerComparisonRow(player1, player2)
        }
    }
}

@Composable
private fun PlayerComparisonRow(
    player1: PlayerStats?,
    player2: PlayerStats?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Player names
        ThreeColumnRow(
            leftText = player1?.name ?: "-",
            centerText = "VS",
            rightText = player2?.name ?: "-",
            leftWeight = FontWeight.Bold,
            centerWeight = FontWeight.Bold,
            rightWeight = FontWeight.Bold,
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )

        // Player positions
        ThreeColumnRow(
            leftText = player1?.position ?: "",
            centerText = "",
            rightText = player2?.position ?: "",
            leftWeight = FontWeight.Normal,
            rightWeight = FontWeight.Normal,
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )

        // Stats comparison
        val stats1 = player1?.stats ?: emptyMap()
        val stats2 = player2?.stats ?: emptyMap()
        val relevantStats = (stats1.keys + stats2.keys).distinct()

        relevantStats.forEach { statName ->
            val team1Value = stats1[statName] ?: "-"
            val team2Value = stats2[statName] ?: "-"
            val advantage = compareStats(team1Value, team2Value, higherIsBetter = true)

            ThreeColumnRow(
                leftText = team1Value,
                centerText = statName,
                rightText = team2Value,
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                advantage = advantage
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun HeadToHeadSection(
    results: List<HeadToHeadResult>,
    team1Code: String,
    team2Code: String
) {
    // Calculate section advantage
    var team1Wins = 0
    var team2Wins = 0

    results.forEach { result ->
        when {
            result.team1Score > result.team2Score -> team1Wins++
            result.team2Score > result.team1Score -> team2Wins++
        }
    }

    val sectionAdvantage = calculateSectionAdvantage(team1Wins, team2Wins)

    SectionHeader(
        title = "Head to Head",
        team1Code = team1Code,
        team2Code = team2Code,
        advantage = sectionAdvantage
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        results.forEach { result ->
            HeadToHeadRow(result)
        }
    }
}

@Composable
private fun HeadToHeadRow(result: HeadToHeadResult) {
    val advantage = when {
        result.team1Score > result.team2Score -> -1
        result.team2Score > result.team1Score -> 1
        else -> 0
    }

    ThreeColumnRow(
        leftText = result.date,
        centerText = "${result.team1Score} - ${result.team2Score}",
        rightText = result.location,
        centerWeight = FontWeight.Bold,
        advantage = advantage
    )
}

@Composable
private fun CommonOpponentsSection(
    commonOpponents: List<CommonOpponentResult>,
    team1Code: String,
    team2Code: String
) {
    // Calculate section advantage based on wins
    var team1Wins = 0
    var team2Wins = 0

    commonOpponents.forEach { opponent ->
        val team1Won = opponent.team1Result.score.startsWith("W")
        val team2Won = opponent.team2Result.score.startsWith("W")

        if (team1Won && !team2Won) team1Wins++
        else if (team2Won && !team1Won) team2Wins++
    }

    val sectionAdvantage = calculateSectionAdvantage(team1Wins, team2Wins)

    SectionHeader(
        title = "Common Opponents",
        team1Code = team1Code,
        team2Code = team2Code,
        advantage = sectionAdvantage
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        commonOpponents.forEach { opponent ->
            CommonOpponentRow(opponent)
        }
    }
}

@Composable
private fun CommonOpponentRow(opponent: CommonOpponentResult) {
    val team1Won = opponent.team1Result.score.startsWith("W")
    val team2Won = opponent.team2Result.score.startsWith("W")

    val advantage = when {
        team1Won && !team2Won -> -1
        team2Won && !team1Won -> 1
        else -> 0
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Scores
        ThreeColumnRow(
            leftText = opponent.team1Result.score,
            centerText = opponent.opponent,
            rightText = opponent.team2Result.score,
            centerWeight = FontWeight.Bold,
            advantage = advantage
        )

        // Dates and locations
        ThreeColumnRow(
            leftText = "${opponent.team1Result.date} (${opponent.team1Result.location})",
            centerText = "",
            rightText = "${opponent.team2Result.date} (${opponent.team2Result.location})",
            leftWeight = FontWeight.Normal,
            rightWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun CumulativeEPAChart(team1: TeamAnalytics, team2: TeamAnalytics) {
    Text(
        text = "Cumulative EPA Over Season",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    // Convert EPA data to LineChartSeries format with team-specific colors
    val series = listOf(
        LineChartSeries(
            label = team1.code,
            dataPoints = team1.weeklyEPA.map {
                LineChartDataPoint(x = it.week.toDouble(), y = it.cumulativeEPA)
            },
            color = "#2196F3" // Blue for team1
        ),
        LineChartSeries(
            label = team2.code,
            dataPoints = team2.weeklyEPA.map {
                LineChartDataPoint(x = it.week.toDouble(), y = it.cumulativeEPA)
            },
            color = "#FF9800" // Orange for team2
        )
    )

    LineChartComponent(
        series = series,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    )
}

@Composable
private fun WeeklyEPAScatterPlot(team1: TeamAnalytics, team2: TeamAnalytics) {
    Text(
        text = "Weekly Offensive vs Defensive EPA",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    val team1Points = team1.weeklyEPA.map {
        ScatterPlotDataPoint(
            label = "${team1.code} W${it.week}",
            x = it.offensiveEPA,
            y = it.defensiveEPA,
            sum = it.offensiveEPA + it.defensiveEPA,
            teamCode = team1.code,
            color = "#2196F3" // Blue for team1
        )
    }

    val team2Points = team2.weeklyEPA.map {
        ScatterPlotDataPoint(
            label = "${team2.code} W${it.week}",
            x = it.offensiveEPA,
            y = it.defensiveEPA,
            sum = it.offensiveEPA + it.defensiveEPA,
            teamCode = team2.code,
            color = "#FF9800" // Orange for team2
        )
    }

    KoalaQuadrantScatterPlot(
        data = team1Points + team2Points,
        modifier = Modifier.fillMaxWidth(),
        title = "",
        xAxisLabel = "Offensive EPA",
        yAxisLabel = "Defensive EPA",
        invertYAxis = false,
        quadrantTopRight = QuadrantConfig(label = "Elite", color = "#E8F5E9", lightModeColor = "#E8F5E9"),
        quadrantTopLeft = QuadrantConfig(label = "Good Defense", color = "#E3F2FD", lightModeColor = "#E3F2FD"),
        quadrantBottomLeft = QuadrantConfig(label = "Poor", color = "#FFF3E0", lightModeColor = "#FFF3E0"),
        quadrantBottomRight = QuadrantConfig(label = "Good Offense", color = "#FFF9C4", lightModeColor = "#FFF9C4")
    )

    // Add legend for team colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Team1Color, CircleShape)
            )
            Text(
                text = team1.code,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Team2Color, CircleShape)
            )
            Text(
                text = team2.code,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
