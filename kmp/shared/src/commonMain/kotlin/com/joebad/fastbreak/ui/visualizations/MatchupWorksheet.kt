package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.ui.QuadrantScatterPlot
import kotlin.math.round
import kotlinx.serialization.json.*

// Team colors for advantage indicators
private val Team1Color = Color(0xFF2196F3) // Blue (away team)
private val Team2Color = Color(0xFFFF5722) // Deep Orange (home team)

/**
 * Helper function to get rank background color for team/QB stats (1-32 scale)
 * Ranks 1-10: Dark green shades
 * Ranks 11-22: Green to orange transition
 * Ranks 23-32: Red shades
 * Ranks >32: Same as rank 32 (dark red)
 */
private fun getTeamRankColor(rank: Int?): Color {
    if (rank == null || rank < 1) return Color.Transparent

    // Clamp rank to 32 for color calculation
    val clampedRank = if (rank > 32) 32 else rank

    return when {
        clampedRank <= 10 -> {
            // Ranks 1-10: Dark green to medium green
            val ratio = (clampedRank - 1) / 9f
            val red = (0 + ratio * 34).toInt()
            val green = (100 + ratio * 39).toInt()
            val blue = (0 + ratio * 34).toInt()
            Color(red, green, blue)
        }
        clampedRank <= 22 -> {
            // Ranks 11-22: Light green to orange transition
            val ratio = (clampedRank - 11) / 11f
            val red = (140 + ratio * 75).toInt()
            val green = (160 - ratio * 60).toInt()
            val blue = (120 - ratio * 40).toInt()
            Color(red, green, blue)
        }
        else -> {
            // Ranks 23-32: Medium red to dark red
            val ratio = (clampedRank - 23) / 9f
            val red = (205 - ratio * 66).toInt()
            val green = (92 - ratio * 92).toInt()
            val blue = (92 - ratio * 92).toInt()
            Color(red, green, blue)
        }
    }
}

/**
 * Helper function to get rank background color for player stats (1-64 scale)
 * Ranks 1-21: Green shades (dark to medium green)
 * Ranks 22-43: Orange shades (green-orange to red-orange transition)
 * Ranks 44-64: Red shades (medium to dark red)
 * Ranks >64: Same as rank 64 (dark red)
 */
private fun getPlayerRankColor(rank: Int?): Color {
    if (rank == null || rank < 1) return Color.Transparent

    // Clamp rank to 64 for color calculation
    val clampedRank = if (rank > 64) 64 else rank

    return when {
        clampedRank <= 21 -> {
            // Ranks 1-21: Dark green to medium green
            // Rank 1: #006400 (dark green), Rank 21: #32CD32 (lime green)
            val ratio = (clampedRank - 1) / 20f
            val red = (0 + ratio * 50).toInt()
            val green = (100 + ratio * 105).toInt()
            val blue = (0 + ratio * 50).toInt()
            Color(red, green, blue)
        }
        clampedRank <= 43 -> {
            // Ranks 22-43: Green-orange to red-orange transition
            // Rank 22: #FFA500 (orange), Rank 43: #FF6347 (tomato)
            val ratio = (clampedRank - 22) / 21f
            val red = (180 + ratio * 75).toInt()
            val green = (140 - ratio * 41).toInt()
            val blue = (50 - ratio * 21).toInt()
            Color(red, green, blue)
        }
        else -> {
            // Ranks 44-64+: Medium red to dark red
            // Rank 44: #CD5C5C (indian red), Rank 64: #8B0000 (dark red)
            val ratio = (clampedRank - 44) / 20f
            val red = (205 - ratio * 66).toInt()
            val green = (92 - ratio * 92).toInt()
            val blue = (92 - ratio * 92).toInt()
            Color(red, green, blue)
        }
    }
}

/**
 * Helper function to format a Double to a specific number of decimal places
 */
private fun Double.format(decimals: Int): String {
    val multiplier = when (decimals) {
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> 1.0
    }
    return (round(this * multiplier) / multiplier).toString()
}

/**
 * Helper to safely get a JsonObject from another JsonObject
 */
private fun JsonObject.getObject(key: String): JsonObject? {
    return this[key]?.jsonObject
}

/**
 * Helper to safely get a String from a JsonObject
 */
private fun JsonObject.getString(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull
}

/**
 * Helper to safely get a Double from a JsonObject
 */
private fun JsonObject.getDouble(key: String): Double? {
    return this[key]?.jsonPrimitive?.doubleOrNull
}

/**
 * Helper to safely get an Int from a JsonObject
 */
private fun JsonObject.getInt(key: String): Int? {
    return this[key]?.jsonPrimitive?.intOrNull
}

/**
 * Generic three-column layout component for consistent compact formatting
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
    leftColor: Color = MaterialTheme.colorScheme.onSurface,
    centerColor: Color = MaterialTheme.colorScheme.primary,
    rightColor: Color = MaterialTheme.colorScheme.onSurface,
    advantage: Int = 0, // -1 for left (away team), 0 for even, 1 for right (home team)
    centerMaxLines: Int = Int.MAX_VALUE,
    centerOverflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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
                fontWeight = leftWeight,
                color = leftColor
            )
        }

        Text(
            text = centerText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            fontWeight = centerWeight,
            color = centerColor,
            maxLines = centerMaxLines,
            overflow = centerOverflow,
            softWrap = false
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
                fontWeight = rightWeight,
                color = rightColor
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

/**
 * Five-column layout component with color-coded rank boxes
 * Layout: [value] [rank] [label] [rank] [value]
 */
@Composable
private fun FiveColumnRowWithRanks(
    leftValue: String,
    leftRank: Int?,
    centerText: String,
    rightValue: String,
    rightRank: Int?,
    advantage: Int = 0,
    usePlayerRankColors: Boolean = false // true for player stats (64 scale), false for team stats (32 scale)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left value with advantage indicator
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
                text = leftValue,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Left rank box (uniform padding)
        Box(
            modifier = Modifier
                .width(28.dp)
                .background(
                    if (usePlayerRankColors) getPlayerRankColor(leftRank) else getTeamRankColor(leftRank),
                    RoundedCornerShape(4.dp)
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = leftRank?.toString() ?: "-",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Center label (no word breaks, more weight for longer labels)
        Text(
            text = centerText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Right rank box (uniform padding)
        Box(
            modifier = Modifier
                .width(28.dp)
                .background(
                    if (usePlayerRankColors) getPlayerRankColor(rightRank) else getTeamRankColor(rightRank),
                    RoundedCornerShape(4.dp)
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rightRank?.toString() ?: "-",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Right value with advantage indicator
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = rightValue,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
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

/**
 * Navigation badge for Stats/Charts toggle
 */
@Composable
private fun NavigationBadge(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * Matchup badge showing team abbreviations
 */
@Composable
private fun MatchupBadge(
    awayTeam: String,
    homeTeam: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = awayTeam,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
            Text(
                text = "vs",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = textColor.copy(alpha = 0.7f)
            )
            Text(
                text = homeTeam,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

/**
 * Screen for displaying MATCHUP_V2 comprehensive stats with tabs for Stats and Charts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchupWorksheet(
    visualization: MatchupV2Visualization,
    modifier: Modifier = Modifier,
    highlightedTeamCodes: Set<String> = emptySet()
) {
    // Reorder matchups to put highlighted team matchups first
    val matchups = remember(visualization.dataPoints, highlightedTeamCodes) {
        val allMatchups = visualization.dataPoints.toList()

        if (highlightedTeamCodes.isEmpty()) {
            allMatchups
        } else {
            // Partition matchups into those with highlighted teams and those without
            // Matchup keys are in format "away-home" (e.g., "buf-kc")
            // Search for the 3-letter team code (case-insensitive) in the key
            val (pinnedMatchups, otherMatchups) = allMatchups.partition { (key, _) ->
                val teams = key.split("-")
                highlightedTeamCodes.any { code ->
                    teams.any { team -> team.equals(code, ignoreCase = true) }
                }
            }
            // Put pinned matchups first, followed by others
            pinnedMatchups + otherMatchups
        }
    }

    var selectedMatchupIndex by remember(highlightedTeamCodes) { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(0) }

    // Reset selected index when matchups list changes (e.g., data updated)
    LaunchedEffect(matchups.size) {
        if (selectedMatchupIndex >= matchups.size) {
            selectedMatchupIndex = 0
        }
    }

    if (matchups.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No matchups available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val (matchupKey, selectedMatchup) = matchups[selectedMatchupIndex]
    val teams = matchupKey.split("-")
    val awayTeam = teams[0].uppercase()
    val homeTeam = teams[1].uppercase()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Badge-based navigation row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stats/Charts toggle badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NavigationBadge(
                    text = "Stats",
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBadge(
                    text = "Charts",
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }

            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .padding(horizontal = 8.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // Horizontally scrollable matchup badges
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                matchups.forEachIndexed { index, (key, _) ->
                    val teams = key.split("-")
                    MatchupBadge(
                        awayTeam = teams[0].uppercase(),
                        homeTeam = teams[1].uppercase(),
                        isSelected = selectedMatchupIndex == index,
                        onClick = { selectedMatchupIndex = index }
                    )
                }
            }
        }

        // Tab content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> StatsTab(
                    awayTeam = awayTeam,
                    homeTeam = homeTeam,
                    matchup = selectedMatchup
                )
                1 -> ChartsTab(
                    awayTeam = awayTeam,
                    homeTeam = homeTeam,
                    matchup = selectedMatchup
                )
            }
        }

        // Source attribution
        visualization.source?.let { source ->
            Text(
                text = "Source: $source",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun StatsTab(
    awayTeam: String,
    homeTeam: String,
    matchup: MatchupV2
) {
    val scrollState = rememberScrollState()

    // Extract team data from JSON
    val awayTeamData = matchup.getObject(awayTeam.lowercase())
    val homeTeamData = matchup.getObject(homeTeam.lowercase())

    if (awayTeamData == null || homeTeamData == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Team data not available")
        }
        return
    }

    // Extract team stats from JSON structure
    val awayTeamStats = awayTeamData.getObject("team_stats")
    val homeTeamStats = homeTeamData.getObject("team_stats")
    val awayCurrentStats = awayTeamStats?.getObject("current")
    val homeCurrentStats = homeTeamStats?.getObject("current")

    Box(modifier = Modifier
        .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface) // Match surface background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(top = 30.dp, bottom = 8.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

        // Odds section
        matchup.getObject("odds")?.let { oddsJson ->
            val homeSpread = oddsJson.getString("home_spread")
            val awaySpread = oddsJson.getString("away_spread")
            val homeMoneyline = oddsJson.getString("home_moneyline")
            val awayMoneyline = oddsJson.getString("away_moneyline")
            val overUnder = oddsJson.getDouble("over_under")

            if (homeSpread != null || awaySpread != null || homeMoneyline != null || awayMoneyline != null || overUnder != null) {
                Text(
                    text = "Betting Odds",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Spread row - show both teams
                    if (awaySpread != null && homeSpread != null) {
                        ThreeColumnRow(
                            leftText = awaySpread,
                            centerText = "Spread",
                            rightText = homeSpread
                        )
                    }

                    // Moneyline row - show both teams
                    if (awayMoneyline != null && homeMoneyline != null) {
                        ThreeColumnRow(
                            leftText = awayMoneyline,
                            centerText = "Moneyline",
                            rightText = homeMoneyline
                        )
                    }

                    // Over/Under - show under the favored team (negative spread/moneyline)
                    overUnder?.let { ou ->
                        // Determine which team is favored (has negative spread or moneyline starting with "-")
                        val homeFavored = homeSpread?.startsWith("-") == true || homeMoneyline?.startsWith("-") == true
                        val awayFavored = awaySpread?.startsWith("-") == true || awayMoneyline?.startsWith("-") == true

                        val leftText = if (awayFavored) ou.format(1) else ""
                        val rightText = if (homeFavored) ou.format(1) else ""

                        ThreeColumnRow(
                            leftText = leftText,
                            centerText = "O/U",
                            rightText = rightText
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Team Stats Section
        if (awayCurrentStats != null && homeCurrentStats != null) {
            Text(
                text = "Team Stats",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            TeamStatsComparisonJson(
                awayTeam = awayTeam,
                homeTeam = homeTeam,
                awayStats = awayCurrentStats,
                homeStats = homeCurrentStats
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Player Stats Section
        val awayPlayers = awayTeamData.getObject("players")
        val homePlayers = homeTeamData.getObject("players")

        if (awayPlayers != null && homePlayers != null) {
            Text(
                text = "Key Players",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            PlayerStatsComparisonJson(
                awayTeam = awayTeam,
                homeTeam = homeTeam,
                awayPlayerStats = awayPlayers,
                homePlayerStats = homePlayers
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // H2H Record
        matchup.get("h2h_record")?.let { h2hElement ->
            // Check if it's an array before trying to access as jsonArray
            if (h2hElement is kotlinx.serialization.json.JsonArray && h2hElement.isNotEmpty()) {
                Text(
                    text = "Head-to-Head",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                HeadToHeadComparison(
                    awayTeam = awayTeam,
                    homeTeam = homeTeam,
                    h2hMatchups = h2hElement
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Common Opponents Section
        matchup.getObject("common_opponents")?.let { commonOpponents ->
            if (commonOpponents.isNotEmpty()) {
                Text(
                    text = "Common Opponents",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                CommonOpponentsComparison(
                    awayTeam = awayTeam,
                    homeTeam = homeTeam,
                    commonOpponents = commonOpponents
                )
            }
        }
            }
        }

        // Pinned team header at the top
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = 8.dp, end = 8.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = awayTeam,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 11.sp
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Text(
                    text = "VS",
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 11.sp
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = homeTeam,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 11.sp
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TeamHeader(
    awayTeam: String,
    homeTeam: String,
    modifier: Modifier = Modifier
) {
    ThreeColumnRow(
        leftText = awayTeam,
        centerText = "VS",
        rightText = homeTeam,
        modifier = modifier,
        leftWeight = FontWeight.Bold,
        centerWeight = FontWeight.Bold,
        rightWeight = FontWeight.Bold
    )
}

@Composable
private fun TeamStatsComparisonJson(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject,
    homeStats: JsonObject
) {
    val statLabels = mapOf(
        "off_epa" to "Offensive EPA",
        "def_epa" to "Defensive EPA",
        "passing_epa" to "Passing EPA",
        "rushing_epa" to "Rushing EPA",
        "passing_cpoe" to "Pass CPOE",
        "receiving_epa" to "Receiving EPA",
        "pacr" to "PACR",
        "passing_first_downs" to "Pass 1st Downs",
        "sacks_suffered" to "Sacks Suffered"
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        statLabels.forEach { (key, label) ->
            val awayStatObj = awayStats.getObject(key)
            val homeStatObj = homeStats.getObject(key)

            if (awayStatObj != null && homeStatObj != null) {
                val awayValue = awayStatObj.getDouble("value")
                val awayRank = awayStatObj.getInt("rank")
                val homeValue = homeStatObj.getDouble("value")
                val homeRank = homeStatObj.getInt("rank")

                // Determine advantage (lower is better for defensive stats and sacks)
                val lowerIsBetter = key.contains("def") || key.contains("sacks")
                val advantage = if (awayValue != null && homeValue != null) {
                    if (lowerIsBetter) {
                        when {
                            awayValue < homeValue -> -1
                            awayValue > homeValue -> 1
                            else -> 0
                        }
                    } else {
                        when {
                            awayValue > homeValue -> -1
                            awayValue < homeValue -> 1
                            else -> 0
                        }
                    }
                } else 0

                // Format values without ranks
                val awayText = awayValue?.format(2) ?: "-"
                val homeText = homeValue?.format(2) ?: "-"

                // Five-column row with ranks
                FiveColumnRowWithRanks(
                    leftValue = awayText,
                    leftRank = awayRank,
                    centerText = label,
                    rightValue = homeText,
                    rightRank = homeRank,
                    advantage = advantage
                )
            }
        }
    }
}

@Composable
private fun PlayerStatsComparisonJson(
    awayTeam: String,
    homeTeam: String,
    awayPlayerStats: JsonObject,
    homePlayerStats: JsonObject
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // QB Comparison
        val awayQB = awayPlayerStats.getObject("qb")
        val homeQB = homePlayerStats.getObject("qb")

        if (awayQB != null && homeQB != null) {
            // QB Names
            val awayQBName = awayQB.getString("name") ?: "-"
            val homeQBName = homeQB.getString("name") ?: "-"

            ThreeColumnRow(
                leftText = awayQBName,
                centerText = "QB",
                rightText = homeQBName,
                leftWeight = FontWeight.Bold,
                centerWeight = FontWeight.Bold,
                rightWeight = FontWeight.Bold
            )

            // QB Stats
            val qbStats = listOf(
                "total_epa" to "Total EPA",
                "passing_epa" to "Passing EPA",
                "passing_cpoe" to "Pass CPOE",
                "pacr" to "PACR",
                "passing_air_yards" to "Air Yards"
            )

            qbStats.forEach { (key, label) ->
                val awayStatObj = awayQB.getObject(key)
                val homeStatObj = homeQB.getObject(key)

                if (awayStatObj != null && homeStatObj != null) {
                    val awayValue = awayStatObj.getDouble("value")
                    val awayRank = awayStatObj.getInt("rank")
                    val homeValue = homeStatObj.getDouble("value")
                    val homeRank = homeStatObj.getInt("rank")

                    val advantage = if (awayValue != null && homeValue != null) {
                        when {
                            awayValue > homeValue -> -1
                            awayValue < homeValue -> 1
                            else -> 0
                        }
                    } else 0

                    val awayText = awayValue?.format(2) ?: "-"
                    val homeText = homeValue?.format(2) ?: "-"

                    FiveColumnRowWithRanks(
                        leftValue = awayText,
                        leftRank = awayRank,
                        centerText = label,
                        rightValue = homeText,
                        rightRank = homeRank,
                        advantage = advantage
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // RB Comparison
        val awayRBs = awayPlayerStats.get("rbs")?.jsonArray
        val homeRBs = homePlayerStats.get("rbs")?.jsonArray

        if (awayRBs != null && homeRBs != null && awayRBs.isNotEmpty() && homeRBs.isNotEmpty()) {
            val maxRBs = minOf(awayRBs.size, homeRBs.size) // Show all RBs from JSON

            for (i in 0 until maxRBs) {
                val awayRB = awayRBs.getOrNull(i)?.jsonObject
                val homeRB = homeRBs.getOrNull(i)?.jsonObject

                if (awayRB != null && homeRB != null) {
                    val awayRBName = awayRB.getString("name") ?: "-"
                    val homeRBName = homeRB.getString("name") ?: "-"

                    ThreeColumnRow(
                        leftText = awayRBName,
                        centerText = "RB",
                        rightText = homeRBName,
                        leftWeight = FontWeight.Bold,
                        centerWeight = FontWeight.Bold,
                        rightWeight = FontWeight.Bold
                    )

                    // RB Stats
                    val rbStats = listOf(
                        "rushing_epa" to "Rush EPA",
                        "rushing_first_downs" to "Rush 1st Dn",
                        "carries" to "Carries",
                        "receiving_epa" to "Rec EPA",
                        "targets" to "Targets",
                        "target_share" to "Target Share"
                    )

                    rbStats.forEach { (key, label) ->
                        val awayStatObj = awayRB.getObject(key)
                        val homeStatObj = homeRB.getObject(key)

                        if (awayStatObj != null && homeStatObj != null) {
                            val awayValue = awayStatObj.getDouble("value")
                            val awayRank = awayStatObj.getInt("rank")
                            val homeValue = homeStatObj.getDouble("value")
                            val homeRank = homeStatObj.getInt("rank")

                            val advantage = if (awayValue != null && homeValue != null) {
                                when {
                                    awayValue > homeValue -> -1
                                    awayValue < homeValue -> 1
                                    else -> 0
                                }
                            } else 0

                            // Format based on stat type
                            val decimals = when (key) {
                                "carries", "targets", "rushing_first_downs" -> 0
                                "target_share" -> 1
                                else -> 2
                            }
                            val awayText = awayValue?.let {
                                if (decimals == 0) it.toInt().toString() else it.format(decimals)
                            } ?: "-"
                            val homeText = homeValue?.let {
                                if (decimals == 0) it.toInt().toString() else it.format(decimals)
                            } ?: "-"

                            FiveColumnRowWithRanks(
                                leftValue = awayText,
                                leftRank = awayRank,
                                centerText = label,
                                rightValue = homeText,
                                rightRank = homeRank,
                                advantage = advantage,
                                usePlayerRankColors = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // WR Comparison
        val awayWRs = awayPlayerStats.get("receivers")?.jsonArray
        val homeWRs = homePlayerStats.get("receivers")?.jsonArray

        if (awayWRs != null && homeWRs != null && awayWRs.isNotEmpty() && homeWRs.isNotEmpty()) {
            val maxWRs = minOf(awayWRs.size, homeWRs.size) // Show all receivers from JSON

            for (i in 0 until maxWRs) {
                val awayWR = awayWRs.getOrNull(i)?.jsonObject
                val homeWR = homeWRs.getOrNull(i)?.jsonObject

                if (awayWR != null && homeWR != null) {
                    val awayWRName = awayWR.getString("name") ?: "-"
                    val homeWRName = homeWR.getString("name") ?: "-"

                    ThreeColumnRow(
                        leftText = awayWRName,
                        centerText = "WR",
                        rightText = homeWRName,
                        leftWeight = FontWeight.Bold,
                        centerWeight = FontWeight.Bold,
                        rightWeight = FontWeight.Bold
                    )

                    // Receiver Stats
                    val receiverStats = listOf(
                        "wopr" to "WOPR",
                        "receiving_epa" to "Rec EPA",
                        "racr" to "RACR",
                        "target_share" to "Target Share",
                        "air_yards_share" to "Air Yards %"
                    )

                    receiverStats.forEach { (key, label) ->
                        val awayStatObj = awayWR.getObject(key)
                        val homeStatObj = homeWR.getObject(key)

                        if (awayStatObj != null && homeStatObj != null) {
                            val awayValue = awayStatObj.getDouble("value")
                            val awayRank = awayStatObj.getInt("rank")
                            val homeValue = homeStatObj.getDouble("value")
                            val homeRank = homeStatObj.getInt("rank")

                            val advantage = if (awayValue != null && homeValue != null) {
                                when {
                                    awayValue > homeValue -> -1
                                    awayValue < homeValue -> 1
                                    else -> 0
                                }
                            } else 0

                            // Format based on stat type
                            val decimals = when (key) {
                                "target_share", "air_yards_share" -> 1
                                else -> 2
                            }
                            val awayText = awayValue?.format(decimals) ?: "-"
                            val homeText = homeValue?.format(decimals) ?: "-"

                            FiveColumnRowWithRanks(
                                leftValue = awayText,
                                leftRank = awayRank,
                                centerText = label,
                                rightValue = homeText,
                                rightRank = homeRank,
                                advantage = advantage,
                                usePlayerRankColors = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun HeadToHeadComparison(
    awayTeam: String,
    homeTeam: String,
    h2hMatchups: kotlinx.serialization.json.JsonArray
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        h2hMatchups.forEach { matchupElement ->
            val matchupObj = matchupElement.jsonObject
            val week = matchupObj.getInt("week")
            val finalScore = matchupObj.getString("finalScore")
            val winner = matchupObj.getString("winner")

            if (week != null && finalScore != null && winner != null) {
                // Determine advantage based on winner
                val advantage = when (winner.uppercase()) {
                    awayTeam -> -1
                    homeTeam -> 1
                    else -> 0 // TIE or unknown
                }

                ThreeColumnRow(
                    leftText = if (winner == awayTeam) "W" else if (winner == homeTeam) "L" else "T",
                    centerText = "W$week: $finalScore",
                    rightText = if (winner == homeTeam) "W" else if (winner == awayTeam) "L" else "T",
                    advantage = advantage,
                    centerMaxLines = 1,
                    centerOverflow = androidx.compose.ui.text.style.TextOverflow.Visible
                )
            }
        }
    }
}

@Composable
private fun CommonOpponentsComparison(
    awayTeam: String,
    homeTeam: String,
    commonOpponents: JsonObject
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Iterate through each common opponent
        commonOpponents.keys.forEach { opponentCode ->
            val opponentData = commonOpponents.getObject(opponentCode)
            if (opponentData != null) {
                val awayGames = opponentData.get(awayTeam.lowercase())?.jsonArray
                val homeGames = opponentData.get(homeTeam.lowercase())?.jsonArray

                if (awayGames != null && homeGames != null) {
                    // Opponent header
                    ThreeColumnRow(
                        leftText = "",
                        centerText = opponentCode.uppercase(),
                        rightText = "",
                        centerWeight = FontWeight.Bold
                    )

                    // Get max number of games
                    val maxGames = maxOf(awayGames.size, homeGames.size)

                    // Display all games for both teams
                    for (i in 0 until maxGames) {
                        val awayGame = awayGames.getOrNull(i)?.jsonObject
                        val homeGame = homeGames.getOrNull(i)?.jsonObject

                        val awayResult = awayGame?.getString("result") ?: ""
                        val awayScore = awayGame?.getString("score") ?: ""
                        val awayWeek = awayGame?.getInt("week")

                        val homeResult = homeGame?.getString("result") ?: ""
                        val homeScore = homeGame?.getString("score") ?: ""
                        val homeWeek = homeGame?.getInt("week")

                        // Format left side (away team)
                        val leftText = if (awayGame != null) {
                            "$awayResult $awayScore (W$awayWeek)"
                        } else ""

                        // Format right side (home team)
                        val rightText = if (homeGame != null) {
                            "$homeResult $homeScore (W$homeWeek)"
                        } else ""

                        // Determine advantage based on result
                        val advantage = when {
                            awayResult == "W" && homeResult == "L" -> -1
                            awayResult == "L" && homeResult == "W" -> 1
                            awayResult == "W" && homeResult == "" -> -1
                            awayResult == "" && homeResult == "W" -> 1
                            awayResult == "L" && homeResult == "" -> 1
                            awayResult == "" && homeResult == "L" -> -1
                            else -> 0
                        }

                        ThreeColumnRow(
                            leftText = leftText,
                            centerText = "",
                            rightText = rightText,
                            advantage = advantage
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ChartsTab(
    awayTeam: String,
    homeTeam: String,
    matchup: MatchupV2
) {
    val scrollState = rememberScrollState()

    // Extract team data from JSON
    val awayTeamData = matchup.getObject(awayTeam.lowercase())
    val homeTeamData = matchup.getObject(homeTeam.lowercase())

    if (awayTeamData == null || homeTeamData == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Team data not available")
        }
        return
    }

    // Extract team stats
    val awayTeamStats = awayTeamData.getObject("team_stats")
    val homeTeamStats = homeTeamData.getObject("team_stats")

    if (awayTeamStats == null || homeTeamStats == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Chart data not available")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 8.dp)
    ) {
        // Cumulative EPA Chart
        CumulativeEPAChartV2(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayTeamStats = awayTeamStats,
            homeTeamStats = homeTeamStats
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Weekly EPA Scatter Plot
        WeeklyEPAScatterPlotV2(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayTeamStats = awayTeamStats,
            homeTeamStats = homeTeamStats
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CumulativeEPAChartV2(
    awayTeam: String,
    homeTeam: String,
    awayTeamStats: JsonObject,
    homeTeamStats: JsonObject
) {
    Text(
        text = "Cumulative EPA Over Season",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    // Extract cum_epa_by_week data
    val awayCumEPA = awayTeamStats.getObject("cum_epa_by_week")
    val homeCumEPA = homeTeamStats.getObject("cum_epa_by_week")

    if (awayCumEPA == null || homeCumEPA == null) {
        Text(
            text = "Cumulative EPA data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Parse data points for line chart
    val awayDataPoints = awayCumEPA.keys.mapNotNull { weekKey ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val epaValue = awayCumEPA.getDouble(weekKey)
        if (weekNum != null && epaValue != null) {
            LineChartDataPoint(x = weekNum.toDouble(), y = epaValue)
        } else null
    }.sortedBy { it.x }

    val homeDataPoints = homeCumEPA.keys.mapNotNull { weekKey ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val epaValue = homeCumEPA.getDouble(weekKey)
        if (weekNum != null && epaValue != null) {
            LineChartDataPoint(x = weekNum.toDouble(), y = epaValue)
        } else null
    }.sortedBy { it.x }

    // Create series with team colors
    val series = listOf(
        LineChartSeries(
            label = awayTeam,
            dataPoints = awayDataPoints,
            color = "#2196F3" // Blue for away team
        ),
        LineChartSeries(
            label = homeTeam,
            dataPoints = homeDataPoints,
            color = "#FF5722" // Deep Orange for home team
        )
    )

    LineChartComponent(
        series = series,
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        yAxisTitle = "Cumulative EPA"
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
                text = awayTeam,
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
                text = homeTeam,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WeeklyEPAScatterPlotV2(
    awayTeam: String,
    homeTeam: String,
    awayTeamStats: JsonObject,
    homeTeamStats: JsonObject
) {
    // State for selected week range
    var selectedWeekRange by remember { mutableStateOf<IntRange?>(null) }

    Text(
        text = "Weekly Offensive vs Defensive EPA",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    // Week range filter badges - horizontally scrollable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WeekRangeBadge(
            label = "All Weeks",
            isSelected = selectedWeekRange == null,
            onClick = { selectedWeekRange = null }
        )
        WeekRangeBadge(
            label = "Weeks 1-6",
            isSelected = selectedWeekRange == 1..6,
            onClick = { selectedWeekRange = 1..6 }
        )
        WeekRangeBadge(
            label = "Weeks 7-12",
            isSelected = selectedWeekRange == 7..12,
            onClick = { selectedWeekRange = 7..12 }
        )
        WeekRangeBadge(
            label = "Weeks 13-18",
            isSelected = selectedWeekRange == 13..18,
            onClick = { selectedWeekRange = 13..18 }
        )
    }

    // Extract epa_by_week data
    val awayEPAByWeek = awayTeamStats.getObject("epa_by_week")
    val homeEPAByWeek = homeTeamStats.getObject("epa_by_week")

    if (awayEPAByWeek == null || homeEPAByWeek == null) {
        Text(
            text = "Weekly EPA data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Parse scatter plot data points
    val awayPoints = awayEPAByWeek.keys.mapNotNull { weekKey ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val weekData = awayEPAByWeek.getObject(weekKey)
        val offEPA = weekData?.getDouble("off")
        val defEPA = weekData?.getDouble("def")

        if (weekNum != null && offEPA != null && defEPA != null) {
            ScatterPlotDataPoint(
                label = "$awayTeam W$weekNum",
                x = offEPA,
                y = defEPA,
                sum = offEPA + defEPA,
                teamCode = awayTeam,
                color = "#2196F3" // Blue for away team
            )
        } else null
    }

    val homePoints = homeEPAByWeek.keys.mapNotNull { weekKey ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val weekData = homeEPAByWeek.getObject(weekKey)
        val offEPA = weekData?.getDouble("off")
        val defEPA = weekData?.getDouble("def")

        if (weekNum != null && offEPA != null && defEPA != null) {
            ScatterPlotDataPoint(
                label = "$homeTeam W$weekNum",
                x = offEPA,
                y = defEPA,
                sum = offEPA + defEPA,
                teamCode = homeTeam,
                color = "#FF5722" // Deep Orange for home team
            )
        } else null
    }

    // Build highlighted labels set based on selected week range
    val weekRange = selectedWeekRange
    val highlightedLabels = if (weekRange != null) {
        (awayPoints + homePoints)
            .filter { point ->
                val weekMatch = "W(\\d+)".toRegex().find(point.label)
                weekMatch?.groupValues?.get(1)?.toIntOrNull()?.let { week ->
                    week in weekRange
                } ?: false
            }
            .map { it.label }
            .toSet()
    } else {
        emptySet()
    }

    QuadrantScatterPlot(
        data = awayPoints + homePoints,
        modifier = Modifier.fillMaxWidth(),
        title = "",
        xAxisLabel = "Offensive EPA",
        yAxisLabel = "Defensive EPA",
        invertYAxis = false,
        highlightedPlayerLabels = highlightedLabels,
        quadrantTopRight = QuadrantConfig(label = "Elite", color = "#4CAF50", lightModeColor = "#4CAF50"),
        quadrantTopLeft = QuadrantConfig(label = "Good Defense", color = "#FFEB3B", lightModeColor = "#FFEB3B"),
        quadrantBottomLeft = QuadrantConfig(label = "Poor", color = "#F44336", lightModeColor = "#F44336"),
        quadrantBottomRight = QuadrantConfig(label = "Good Offense", color = "#FF9800", lightModeColor = "#FF9800")
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
                text = awayTeam,
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
                text = homeTeam,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WeekRangeBadge(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.sp
        )
    }
}

