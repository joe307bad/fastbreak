package com.joebad.fastbreak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.model.dtos.EmptyFastbreakCardItem
import com.joebad.fastbreak.ui.theme.AppColors
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControl
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlDefaults
import io.github.alexzhirkevich.cupertino.CupertinoSegmentedControlTab
import io.github.alexzhirkevich.cupertino.ExperimentalCupertinoApi

enum class TeamPickOption {
    AWAY, PICK, HOME
}

@OptIn(ExperimentalCupertinoApi::class)
@Composable
fun GameDetailsScreen(
    game: EmptyFastbreakCardItem,
    colors: AppColors,
    onBackClick: () -> Unit
) {
    var selectedPick by remember { mutableStateOf(TeamPickOption.PICK) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(bottom = 80.dp) // Reduced space for bottom segmented control
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Basic game info
                    GameBasicInfo(game = game, colors = colors)
                }
                
                item {
                    // Two-column team layout
                    TeamComparisonSection(game = game, colors = colors)
                }
                
                item {
                    // Data table
                    DataTable(colors = colors)
                }
                
                item {
                    // Insights section
                    InsightsSection(colors = colors)
                }
                
                item {
                    // Predictive analysis section
                    PredictiveAnalysisSection(game = game, colors = colors)
                }
                
                item {
                    // PLAYER STATS header
                    Text(
                        text = "PLAYER STATS",
                        style = MaterialTheme.typography.caption,
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                item {
                    // Scrollable player stats
                    ScrollableStats(colors = colors)
                }
            }
        }
        
        // Bottom segmented control for team selection
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.background.copy(alpha = 0.95f))
                .padding(16.dp)
        ) {
            if (game.awayTeam != null && game.homeTeam != null) {
                CupertinoSegmentedControl(
                    colors = CupertinoSegmentedControlDefaults.colors(
                        separatorColor = colors.accent.copy(alpha = 0.8f),
                        indicatorColor = colors.accent.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    selectedTabIndex = when (selectedPick) {
                        TeamPickOption.AWAY -> 0
                        TeamPickOption.PICK -> 1
                        TeamPickOption.HOME -> 2
                    },
                    shape = RectangleShape
                ) {
                    CupertinoSegmentedControlTab(
                        isSelected = selectedPick == TeamPickOption.AWAY,
                        onClick = { selectedPick = TeamPickOption.AWAY }
                    ) {
                        Text(
                            game.homeTeam,
                            color = colors.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                    CupertinoSegmentedControlTab(
                        isSelected = selectedPick == TeamPickOption.PICK,
                        onClick = { selectedPick = TeamPickOption.PICK }
                    ) {
                        Text(
                            "NONE",
                            color = colors.onSurface.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                    CupertinoSegmentedControlTab(
                        isSelected = selectedPick == TeamPickOption.HOME,
                        onClick = { selectedPick = TeamPickOption.HOME }
                    ) {
                        Text(
                            game.homeTeam,
                            color = colors.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            } else {
                // For non-team games, show generic pick option
                Text(
                    text = "PICK UNAVAILABLE",
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class PlayerStat(
    val label: String,
    val value: String,
    val isBold: Boolean = false
)

data class PlayerStatsRow(
    val playerName: String,
    val stats: List<PlayerStat>
)

@Composable
fun GameBasicInfo(
    game: EmptyFastbreakCardItem,
    colors: AppColors
) {
    // Sample game info - in a real app this would come from the game data
    val gameInfo = "Dec 15, 2024 • 8:15 PM EST • Crypto.com Arena, Los Angeles, CA"
    
    Text(
        text = gameInfo,
        style = MaterialTheme.typography.caption,
        color = colors.onSurface.copy(alpha = 0.7f),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TeamComparisonSection(
    game: EmptyFastbreakCardItem,
    colors: AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        // Away team
        TeamColumn(
            teamName = game.awayTeam ?: "",
            eloRating = if (game.awayTeam != null) (1650 + kotlin.random.Random.nextInt(200)).toString() else "---",
            powerRanking = if (game.awayTeam != null) kotlin.random.Random.nextInt(1, 33).toString() else "---",
            colors = colors
        )
        
        // Home team
        TeamColumn(
            teamName = game.homeTeam ?: "",
            eloRating = if (game.homeTeam != null) (1650 + kotlin.random.Random.nextInt(200)).toString() else "---",
            powerRanking = if (game.homeTeam != null) kotlin.random.Random.nextInt(1, 33).toString() else "---",
            colors = colors
        )
    }
}

@Composable
private fun TeamColumn(
    teamName: String,
    eloRating: String,
    powerRanking: String,
    colors: AppColors
) {
    Column(
        horizontalAlignment = Alignment.Start
    ) {
        // Team name - bold and not truncated
        Text(
            text = teamName,
            style = MaterialTheme.typography.body1,
            color = colors.onSurface,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // ELO Rating
        Text(
            text = "ELO: $eloRating",
            style = MaterialTheme.typography.body2,
            color = colors.accent,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Power Ranking
        Text(
            text = "PWR: $powerRanking",
            style = MaterialTheme.typography.body2,
            color = colors.accent,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun DataTable(colors: AppColors) {
    Column {
        // Table header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "STAT",
                style = MaterialTheme.typography.caption,
                color = colors.accent,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "HOME",
                style = MaterialTheme.typography.caption,
                color = colors.accent,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "AWAY",
                style = MaterialTheme.typography.caption,
                color = colors.accent,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                text = "DIFF",
                style = MaterialTheme.typography.caption,
                color = colors.accent,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
        
        Divider(
            color = colors.onSurface.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        
        // Table rows
        val tableData = listOf(
            listOf("PPG", "112.3", "108.7", "+3.6"),
            listOf("FG%", "47.2", "44.8", "+2.4"),
            listOf("3P%", "38.1", "35.9", "+2.2"),
            listOf("REB", "43.2", "41.8", "+1.4"),
            listOf("AST", "24.6", "22.3", "+2.3"),
            listOf("TO", "13.2", "14.7", "-1.5")
        )
        
        tableData.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = row[0],
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = row[1],
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = row[2],
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = row[3],
                    style = MaterialTheme.typography.caption,
                    color = if (row[3].startsWith("+")) colors.accent else colors.error,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun InsightsSection(colors: AppColors) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "INSIGHTS BASED ON STATISTICAL ANALYSIS",
            style = MaterialTheme.typography.caption,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        
        val insights = listOf(
            "Home team advantage is amplified by superior offensive line play, allowing 2.1 fewer sacks per game on average",
            "Away team's defensive secondary has allowed 15% fewer passing yards in the red zone over their last 5 games",
            "Weather conditions favor ground-heavy offensive schemes, with historical data showing 23% increase in rushing attempts"
        )
        
        insights.forEach { insight ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 8.dp, top = 1.dp)
                )
                Text(
                    text = insight,
                    style = MaterialTheme.typography.caption,
                    color = colors.onSurface,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun PredictiveAnalysisSection(
    game: EmptyFastbreakCardItem,
    colors: AppColors
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "PREDICTIVE ANALYSIS",
            style = MaterialTheme.typography.caption,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        
        val prediction = if (game.homeTeam != null && game.awayTeam != null) {
            val homeTeamName = game.homeTeam
            val awayTeamName = game.awayTeam
            val favoredTeam = if (kotlin.random.Random.nextBoolean()) homeTeamName to game.homeTeam else awayTeamName to game.awayTeam
            val margin = kotlin.random.Random.nextInt(3, 14)
            
            "Model predicts **${favoredTeam.second}** will win by $margin points based on recent form and matchup analysis. " +
            "Their superior third-down conversion rate (68.2% vs 52.1%) and defensive pressure in crucial situations " +
            "should provide the decisive edge in a closely contested game."
        } else {
            "Prediction unavailable - teams not yet determined for this matchup."
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.caption,
                color = colors.onSurface,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 8.dp, top = 1.dp)
            )
            // Create annotated string for bold team names
            val annotatedPrediction = buildAnnotatedString {
                val cleanPrediction = prediction.replace("**", "")
                var currentIndex = 0
                val boldPattern = Regex("(Lakers|Warriors|Celtics|Heat|Bulls|Knicks|Nets|Sixers|Bucks|Raptors|Hawks|Hornets|Magic|Wizards|Pistons|Pacers|Cavaliers|Nuggets|Jazz|Thunder|Trail Blazers|Timberwolves|Kings|Suns|Clippers|Mavericks|Rockets|Grizzlies|Pelicans|Spurs)")
                
                boldPattern.findAll(cleanPrediction).forEach { matchResult ->
                    // Add text before the team name
                    append(cleanPrediction.substring(currentIndex, matchResult.range.first))
                    
                    // Add the team name with bold styling
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = colors.accent)) {
                        append(matchResult.value)
                    }
                    
                    currentIndex = matchResult.range.last + 1
                }
                
                // Add remaining text
                append(cleanPrediction.substring(currentIndex))
            }
            
            Text(
                text = annotatedPrediction,
                style = MaterialTheme.typography.caption,
                color = colors.onSurface,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ScrollableStats(colors: AppColors) {
    // Sample player data
    val playerStats = listOf(
        PlayerStatsRow(
            playerName = "J. Herbert",
            stats = listOf(
                PlayerStat("Impact Score", "24.6", isBold = true),
                PlayerStat("Pass yds", "287"),
                PlayerStat("Pass TD", "2"),
                PlayerStat("INT", "1"),
                PlayerStat("Rating", "94.2"),
                PlayerStat("Rush yds", "23"),
                PlayerStat("Rush TD", "0"),
                PlayerStat("Fumbles", "0"),
                PlayerStat("Comp%", "68.5")
            )
        ),
        PlayerStatsRow(
            playerName = "A. Ekeler",
            stats = listOf(
                PlayerStat("Impact Score", "18.3", isBold = true),
                PlayerStat("Rush yds", "112"),
                PlayerStat("Rush TD", "1"),
                PlayerStat("Rec", "7"),
                PlayerStat("Rec yds", "65"),
                PlayerStat("Rec TD", "1"),
                PlayerStat("Fumbles", "0"),
                PlayerStat("YPC", "4.8"),
                PlayerStat("Long", "18")
            )
        ),
        PlayerStatsRow(
            playerName = "K. Allen",
            stats = listOf(
                PlayerStat("Impact Score", "21.7", isBold = true),
                PlayerStat("Rec", "8"),
                PlayerStat("Rec yds", "109"),
                PlayerStat("Rec TD", "1"),
                PlayerStat("Targets", "12"),
                PlayerStat("YPR", "13.6"),
                PlayerStat("Long", "24"),
                PlayerStat("Drops", "1"),
                PlayerStat("Catch%", "66.7")
            )
        ),
        PlayerStatsRow(
            playerName = "M. Williams",
            stats = listOf(
                PlayerStat("Impact Score", "15.2", isBold = true),
                PlayerStat("Rec", "5"),
                PlayerStat("Rec yds", "87"),
                PlayerStat("Rec TD", "0"),
                PlayerStat("Targets", "8"),
                PlayerStat("YPR", "17.4"),
                PlayerStat("Long", "31"),
                PlayerStat("Drops", "0"),
                PlayerStat("Catch%", "62.5")
            )
        ),
        PlayerStatsRow(
            playerName = "C. Mack",
            stats = listOf(
                PlayerStat("Impact Score", "19.4", isBold = true),
                PlayerStat("Tackles", "9"),
                PlayerStat("Sacks", "2.5"),
                PlayerStat("TFL", "3"),
                PlayerStat("QB Hits", "4"),
                PlayerStat("PD", "1"),
                PlayerStat("FF", "1"),
                PlayerStat("FR", "0"),
                PlayerStat("Int", "0")
            )
        )
    )
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        playerStats.forEach { playerRow ->
            PlayerStatRow(
                playerRow = playerRow,
                colors = colors
            )
        }
    }
}

@Composable
private fun PlayerStatRow(
    playerRow: PlayerStatsRow,
    colors: AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Player name - fixed width
        Text(
            text = playerRow.playerName,
            style = MaterialTheme.typography.caption,
            color = colors.onSurface,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(90.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Horizontally scrollable stats
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(playerRow.stats) { stat ->
                StatItem(
                    stat = stat,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    stat: PlayerStat,
    colors: AppColors
) {
    Text(
        text = "${stat.label}: ${stat.value}",
        style = MaterialTheme.typography.caption,
        color = if (stat.isBold) colors.accent else colors.onSurface.copy(alpha = 0.8f),
        fontFamily = FontFamily.Monospace,
        fontWeight = if (stat.isBold) FontWeight.Bold else FontWeight.Normal,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}