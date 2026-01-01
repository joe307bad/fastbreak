package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
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
import kotlin.math.round
import kotlinx.serialization.json.*

// Team colors for advantage indicators
private val Team1Color = Color(0xFF2196F3) // Blue (away team)
private val Team2Color = Color(0xFFFF5722) // Deep Orange (home team)

/**
 * Helper function to get rank background color
 * Ranks 1-10: Dark green shades
 * Ranks 11-22: Light green to light red (very pale around rank 16)
 * Ranks 23-32: Dark red shades
 */
private fun getRankColor(rank: Int?): Color {
    if (rank == null || rank < 1 || rank > 32) return Color.Transparent

    return when {
        rank <= 10 -> {
            // Ranks 1-10: Dark green to medium green
            // Rank 1: #006400 (dark green), Rank 10: #228B22 (forest green)
            val ratio = (rank - 1) / 9f
            val red = (ratio * 34).toInt()
            val green = (100 + ratio * 39).toInt()
            val blue = (ratio * 34).toInt()
            Color(red, green, blue)
        }
        rank <= 22 -> {
            // Ranks 11-22: Light green to light red (darker middle for readability)
            // Rank 11: Light green, Rank 16: Medium gray-green, Rank 22: Light red
            val ratio = (rank - 11) / 11f
            val red = (140 + ratio * 75).toInt()
            val green = (160 - ratio * 60).toInt()
            val blue = (120 - ratio * 40).toInt()
            Color(red, green, blue)
        }
        else -> {
            // Ranks 23-32: Medium red to dark red
            // Rank 23: #CD5C5C (indian red), Rank 32: #8B0000 (dark red)
            val ratio = (rank - 23) / 9f
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
    advantage: Int = 0 // -1 for left (away team), 0 for even, 1 for right (home team)
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
    advantage: Int = 0
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
                .background(getRankColor(leftRank), RoundedCornerShape(4.dp))
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
                .background(getRankColor(rightRank), RoundedCornerShape(4.dp))
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
 * Screen for displaying MATCHUP_V2 comprehensive stats with tabs for Stats and Charts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchupV2Screen(
    visualization: MatchupV2Visualization,
    modifier: Modifier = Modifier,
    highlightedTeamCodes: Set<String> = emptySet()
) {
    // Filter matchups by highlighted teams if any are selected
    val matchups = remember(visualization.dataPoints, highlightedTeamCodes) {
        if (highlightedTeamCodes.isEmpty()) {
            visualization.dataPoints.toList()
        } else {
            visualization.dataPoints.filter { (key, _) ->
                highlightedTeamCodes.any { code ->
                    key.contains(code.lowercase())
                }
            }.toList()
        }
    }

    var selectedMatchupIndex by remember { mutableStateOf(0) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    // Reset selected index when filtered matchups change
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
        // Dropdown selector
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = "$awayTeam vs $homeTeam",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Select Matchup") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                matchups.forEachIndexed { index, (key, _) ->
                    val teams = key.split("-")
                    DropdownMenuItem(
                        text = {
                            Text("${teams[0].uppercase()} vs ${teams[1].uppercase()}")
                        },
                        onClick = {
                            selectedMatchupIndex = index
                            dropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Stats") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Charts") }
            )
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
            val spread = oddsJson.getDouble("spread")
            val moneyline = oddsJson.getString("moneyline")
            val overUnder = oddsJson.getDouble("over_under")

            if (spread != null || moneyline != null || overUnder != null) {
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
                    spread?.let {
                        val spreadText = if (it > 0) "+${it.format(1)}" else it.format(1)
                        ThreeColumnRow(
                            leftText = spreadText,
                            centerText = "Spread",
                            rightText = "-"
                        )
                    }
                    moneyline?.let {
                        ThreeColumnRow(
                            leftText = it,
                            centerText = "Moneyline",
                            rightText = "-"
                        )
                    }
                    overUnder?.let {
                        ThreeColumnRow(
                            leftText = "-",
                            centerText = "O/U",
                            rightText = it.format(1)
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
        matchup.getString("h2h_record")?.let { record ->
            Text(
                text = "Head-to-Head: $record",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
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
                "passing_epa" to "Pass EPA",
                "passing_cpoe" to "Pass CPOE"
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
            val maxRBs = minOf(awayRBs.size, homeRBs.size, 2) // Show top 2 RBs

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

                    // RB rushing EPA
                    val awayRushingEPA = awayRB.getObject("rushing_epa")
                    val homeRushingEPA = homeRB.getObject("rushing_epa")

                    if (awayRushingEPA != null && homeRushingEPA != null) {
                        val awayValue = awayRushingEPA.getDouble("value")
                        val awayRank = awayRushingEPA.getInt("rank")
                        val homeValue = homeRushingEPA.getDouble("value")
                        val homeRank = homeRushingEPA.getInt("rank")

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
                            centerText = "Rush EPA",
                            rightValue = homeText,
                            rightRank = homeRank,
                            advantage = advantage
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // WR Comparison
        val awayWRs = awayPlayerStats.get("receivers")?.jsonArray
        val homeWRs = homePlayerStats.get("receivers")?.jsonArray

        if (awayWRs != null && homeWRs != null && awayWRs.isNotEmpty() && homeWRs.isNotEmpty()) {
            val maxWRs = minOf(awayWRs.size, homeWRs.size, 2) // Show top 2 WRs

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

                    // WR receiving EPA
                    val awayReceivingEPA = awayWR.getObject("receiving_epa")
                    val homeReceivingEPA = homeWR.getObject("receiving_epa")

                    if (awayReceivingEPA != null && homeReceivingEPA != null) {
                        val awayValue = awayReceivingEPA.getDouble("value")
                        val awayRank = awayReceivingEPA.getInt("rank")
                        val homeValue = homeReceivingEPA.getDouble("value")
                        val homeRank = homeReceivingEPA.getInt("rank")

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
                            centerText = "Rec EPA",
                            rightValue = homeText,
                            rightRank = homeRank,
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
    // Placeholder for charts implementation
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Charts Coming Soon",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Cumulative EPA and EPA by Week visualizations",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
