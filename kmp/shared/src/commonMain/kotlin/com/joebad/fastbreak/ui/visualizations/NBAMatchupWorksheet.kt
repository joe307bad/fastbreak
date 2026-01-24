package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.NBAMatchupVisualization
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.components.ShareFab
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round

/**
 * Helper to format doubles with specified decimal places
 */
private fun Double.formatStat(decimals: Int = 1): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> 10.0
    }
    val rounded = round(this * multiplier) / multiplier
    return when (decimals) {
        0 -> rounded.toInt().toString()
        else -> {
            val str = rounded.toString()
            if (str.contains('.')) {
                val parts = str.split('.')
                val decimalPart = parts[1].padEnd(decimals, '0').take(decimals)
                "${parts[0]}.$decimalPart"
            } else {
                "$str.${"0".repeat(decimals)}"
            }
        }
    }
}

/**
 * NBA Matchup Worksheet component with two-row navigation:
 * - First row: Date badges to filter games by date
 * - Second row: Matchup badges for the selected date
 */
@Composable
fun NBAMatchupWorksheet(
    visualization: NBAMatchupVisualization,
    modifier: Modifier = Modifier
) {
    // Group matchups by date in Eastern timezone and sort dates chronologically
    val matchupsByDate = remember(visualization.dataPoints) {
        visualization.dataPoints
            .groupBy { matchup ->
                // Parse ISO 8601 date and extract date part in Eastern timezone
                val instant = Instant.parse(matchup.gameDate)
                instant.toLocalDateTime(TimeZone.of("America/New_York")).date
            }
            .toList()
            .sortedBy { (date, _) -> date }
            .toMap()
    }

    val dates = remember(matchupsByDate) { matchupsByDate.keys.toList() }

    // State for selected date and matchup
    var selectedDateIndex by remember { mutableStateOf(0) }
    var selectedMatchupIndex by remember { mutableStateOf(0) }

    // Reset matchup index when date changes
    LaunchedEffect(selectedDateIndex) {
        selectedMatchupIndex = 0
    }

    // Get matchups for selected date
    val selectedDate = if (dates.isNotEmpty()) dates[selectedDateIndex] else null
    val matchupsForDate = selectedDate?.let { matchupsByDate[it] } ?: emptyList()

    if (dates.isEmpty() || matchupsForDate.isEmpty()) {
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

    val selectedMatchup = matchupsForDate.getOrNull(selectedMatchupIndex) ?: matchupsForDate.firstOrNull() ?: return

    // State for view selection: 0 = Team, 1 = Away Off vs Home Def, 2 = Home Off vs Away Def
    var viewSelection by remember { mutableStateOf(0) }

    // Graphics layer for capturing share image
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    val imageExporter = remember { getImageExporter() }
    var isCapturing by remember { mutableStateOf(false) }

    // Format date and event label for share image (matching NFL format)
    val eventLabel = remember(selectedMatchup.gameDate) {
        "Regular Season"
    }

    val formattedDate = remember(selectedMatchup.gameDate) {
        try {
            val instant = Instant.parse(selectedMatchup.gameDate)
            val dateTime = instant.toLocalDateTime(TimeZone.of("America/New_York"))
            val hour = if (dateTime.hour == 0) 12 else if (dateTime.hour > 12) dateTime.hour - 12 else dateTime.hour
            val amPm = if (dateTime.hour < 12) "am" else "pm"
            val dayOfWeek = dateTime.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            val month = dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }
            "$dayOfWeek, $month ${dateTime.dayOfMonth}, @ ${hour}:${dateTime.minute.toString().padStart(2, '0')}$amPm ET"
        } catch (e: Exception) {
            ""
        }
    }

    val shareTitle = remember(selectedMatchup) {
        "${selectedMatchup.awayTeam.abbreviation} @ ${selectedMatchup.homeTeam.abbreviation} - NBA Matchup"
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // First row: Date badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                dates.forEachIndexed { index, date ->
                    DateBadge(
                        date = date,
                        isSelected = selectedDateIndex == index,
                        onClick = { selectedDateIndex = index }
                    )
                }
            }

            // Second row: Matchup badges for selected date
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                matchupsForDate.forEachIndexed { index, matchup ->
                    MatchupBadge(
                        awayTeam = matchup.awayTeam.abbreviation,
                        homeTeam = matchup.homeTeam.abbreviation,
                        gameDate = matchup.gameDate,
                        isSelected = selectedMatchupIndex == index,
                        onClick = { selectedMatchupIndex = index }
                    )
                }
            }

            // Matchup content with pinned header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 22.dp) // Space for pinned header
                ) {
                    NBAMatchupContent(
                        matchup = selectedMatchup,
                        viewSelection = viewSelection,
                        onViewSelectionChange = { viewSelection = it }
                    )
                }

                // Pinned header
                PinnedMatchupHeader(
                    awayTeam = selectedMatchup.awayTeam.abbreviation,
                    homeTeam = selectedMatchup.homeTeam.abbreviation,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

        // Share button
        ShareFab(
            onClick = {
                if (!isCapturing) {
                    coroutineScope.launch {
                        isCapturing = true
                        try {
                            // Wait for composition to complete
                            kotlinx.coroutines.delay(100)
                            val bitmap = graphicsLayer.toImageBitmap()
                            println("📸 NBA Matchup Share: Captured bitmap size: ${bitmap.width}x${bitmap.height}")
                            imageExporter.shareImage(bitmap, shareTitle)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            isCapturing = false
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        // Off-screen shareable content for capture (wide landscape, high-res)
        CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
            Box(
                modifier = Modifier
                    .requiredWidth(3400.dp)
                    .requiredHeight(1800.dp)
                    .offset { IntOffset(-10000, 0) }  // Off-screen
                    .drawWithCache {
                        onDrawWithContent {
                            graphicsLayer.record {
                                this@onDrawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                    }
            ) {
                // Build stat boxes from NBA matchup data
                val gameInfo = ShareGameInfo(
                    awayTeam = selectedMatchup.awayTeam.abbreviation,
                    homeTeam = selectedMatchup.homeTeam.abbreviation,
                    eventLabel = eventLabel,
                    formattedDate = formattedDate,
                    source = "ESPN"
                )

                val odds = selectedMatchup.odds?.let {
                    ShareOdds(
                        awayMoneyline = it.awayMoneyline,
                        homeMoneyline = it.homeMoneyline,
                        awaySpread = it.spread?.let { spread ->
                            if (spread > 0) "+$spread" else spread.toString()
                        },
                        homeSpread = it.spread?.let { spread ->
                            if (spread < 0) "+${-spread}" else (-spread).toString()
                        },
                        overUnder = it.overUnder?.toString()
                    )
                }

                // Build the 6 stat boxes (2 rows x 3 columns)
                val statBoxes = buildList {
                    // Box 1: Offensive Team Stats
                    add(ShareStatBox(
                        title = "Offensive Stats",
                        fiveColStats = selectedMatchup.comparisons?.sideBySide?.offense?.mapNotNull { (key, stat) ->
                            val awayValue = stat.away.value?.formatStat(2) ?: return@mapNotNull null
                            val homeValue = stat.home.value?.formatStat(2) ?: return@mapNotNull null
                            val advantage = if (stat.away.rank != null && stat.home.rank != null) {
                                when {
                                    stat.away.rank < stat.home.rank -> -1
                                    stat.away.rank > stat.home.rank -> 1
                                    else -> 0
                                }
                            } else 0

                            ShareFiveColStat(
                                leftValue = awayValue,
                                leftRank = stat.away.rank,
                                leftRankDisplay = stat.away.rankDisplay,
                                centerText = stat.label,
                                rightValue = homeValue,
                                rightRank = stat.home.rank,
                                rightRankDisplay = stat.home.rankDisplay,
                                advantage = advantage,
                                usePlayerRanks = false
                            )
                        }?.take(9) ?: emptyList()
                    ))

                    // Box 2: Defensive Team Stats
                    add(ShareStatBox(
                        title = "Defensive Stats",
                        fiveColStats = selectedMatchup.comparisons?.sideBySide?.defense?.mapNotNull { (key, stat) ->
                            val awayValue = stat.away.value?.formatStat(2) ?: return@mapNotNull null
                            val homeValue = stat.home.value?.formatStat(2) ?: return@mapNotNull null
                            val advantage = if (stat.away.rank != null && stat.home.rank != null) {
                                when {
                                    stat.away.rank < stat.home.rank -> -1
                                    stat.away.rank > stat.home.rank -> 1
                                    else -> 0
                                }
                            } else 0

                            ShareFiveColStat(
                                leftValue = awayValue,
                                leftRank = stat.away.rank,
                                leftRankDisplay = stat.away.rankDisplay,
                                centerText = stat.label,
                                rightValue = homeValue,
                                rightRank = stat.home.rank,
                                rightRankDisplay = stat.home.rankDisplay,
                                advantage = advantage,
                                usePlayerRanks = false
                            )
                        }?.take(9) ?: emptyList()
                    ))

                    // Box 3: Away Off vs Home Def
                    add(ShareStatBox(
                        title = "${selectedMatchup.awayTeam.abbreviation} Off vs ${selectedMatchup.homeTeam.abbreviation} Def",
                        leftLabel = "${selectedMatchup.awayTeam.abbreviation} Off",
                        middleLabel = "vs",
                        rightLabel = "${selectedMatchup.homeTeam.abbreviation} Def",
                        fiveColStats = selectedMatchup.comparisons?.awayOffVsHomeDef?.mapNotNull { (key, stat) ->
                            val offValue = stat.offense.value?.formatStat(2) ?: return@mapNotNull null
                            val defValue = stat.defense.value?.formatStat(2) ?: return@mapNotNull null

                            ShareFiveColStat(
                                leftValue = offValue,
                                leftRank = stat.offense.rank,
                                leftRankDisplay = stat.offense.rankDisplay,
                                centerText = stat.offLabel,
                                rightValue = defValue,
                                rightRank = stat.defense.rank,
                                rightRankDisplay = stat.defense.rankDisplay,
                                advantage = stat.advantage ?: 0,
                                usePlayerRanks = false
                            )
                        }?.take(9) ?: emptyList()
                    ))

                    // Box 4: Home Off vs Away Def
                    add(ShareStatBox(
                        title = "${selectedMatchup.homeTeam.abbreviation} Off vs ${selectedMatchup.awayTeam.abbreviation} Def",
                        leftLabel = "${selectedMatchup.homeTeam.abbreviation} Off",
                        middleLabel = "vs",
                        rightLabel = "${selectedMatchup.awayTeam.abbreviation} Def",
                        fiveColStats = selectedMatchup.comparisons?.homeOffVsAwayDef?.mapNotNull { (key, stat) ->
                            val offValue = stat.offense.value?.formatStat(2) ?: return@mapNotNull null
                            val defValue = stat.defense.value?.formatStat(2) ?: return@mapNotNull null

                            ShareFiveColStat(
                                leftValue = offValue,
                                leftRank = stat.offense.rank,
                                leftRankDisplay = stat.offense.rankDisplay,
                                centerText = stat.offLabel,
                                rightValue = defValue,
                                rightRank = stat.defense.rank,
                                rightRankDisplay = stat.defense.rankDisplay,
                                advantage = stat.advantage ?: 0,
                                usePlayerRanks = false
                            )
                        }?.take(9) ?: emptyList()
                    ))

                    // Box 5: Key Player #1
                    if (selectedMatchup.awayPlayers.isNotEmpty() && selectedMatchup.homePlayers.isNotEmpty()) {
                        val awayPlayer = selectedMatchup.awayPlayers[0]
                        val homePlayer = selectedMatchup.homePlayers[0]

                        add(ShareStatBox(
                            title = "${awayPlayer.name} vs ${homePlayer.name}",
                            leftLabel = awayPlayer.name,
                            middleLabel = "vs",
                            rightLabel = homePlayer.name,
                            fiveColStats = listOfNotNull(
                                ShareFiveColStat(
                                    leftValue = awayPlayer.points_per_game.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.points_per_game.rank,
                                    leftRankDisplay = awayPlayer.points_per_game.rankDisplay,
                                    centerText = "Pts/Game",
                                    rightValue = homePlayer.points_per_game.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.points_per_game.rank,
                                    rightRankDisplay = homePlayer.points_per_game.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                ShareFiveColStat(
                                    leftValue = awayPlayer.rebounds_per_game.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.rebounds_per_game.rank,
                                    leftRankDisplay = awayPlayer.rebounds_per_game.rankDisplay,
                                    centerText = "Reb/Game",
                                    rightValue = homePlayer.rebounds_per_game.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.rebounds_per_game.rank,
                                    rightRankDisplay = homePlayer.rebounds_per_game.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                ShareFiveColStat(
                                    leftValue = awayPlayer.assists_per_game.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.assists_per_game.rank,
                                    leftRankDisplay = awayPlayer.assists_per_game.rankDisplay,
                                    centerText = "Ast/Game",
                                    rightValue = homePlayer.assists_per_game.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.assists_per_game.rank,
                                    rightRankDisplay = homePlayer.assists_per_game.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                ShareFiveColStat(
                                    leftValue = awayPlayer.field_goal_pct.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.field_goal_pct.rank,
                                    leftRankDisplay = awayPlayer.field_goal_pct.rankDisplay,
                                    centerText = "FG%",
                                    rightValue = homePlayer.field_goal_pct.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.field_goal_pct.rank,
                                    rightRankDisplay = homePlayer.field_goal_pct.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                awayPlayer.true_shooting_pct.value?.let { awayTS ->
                                    homePlayer.true_shooting_pct.value?.let { homeTS ->
                                        ShareFiveColStat(
                                            leftValue = (awayTS * 100).formatStat(1),
                                            leftRank = awayPlayer.true_shooting_pct.rank,
                                            leftRankDisplay = awayPlayer.true_shooting_pct.rankDisplay,
                                            centerText = "TS%",
                                            rightValue = (homeTS * 100).formatStat(1),
                                            rightRank = homePlayer.true_shooting_pct.rank,
                                            rightRankDisplay = homePlayer.true_shooting_pct.rankDisplay,
                                            advantage = 0,
                                            usePlayerRanks = true
                                        )
                                    }
                                }
                            ).take(9)
                        ))
                    }

                    // Box 6: Key Player #2 (if exists)
                    if (selectedMatchup.awayPlayers.size > 1 && selectedMatchup.homePlayers.size > 1) {
                        val awayPlayer = selectedMatchup.awayPlayers[1]
                        val homePlayer = selectedMatchup.homePlayers[1]

                        add(ShareStatBox(
                            title = "${awayPlayer.name} vs ${homePlayer.name}",
                            leftLabel = awayPlayer.name,
                            middleLabel = "vs",
                            rightLabel = homePlayer.name,
                            fiveColStats = listOfNotNull(
                                ShareFiveColStat(
                                    leftValue = awayPlayer.points_per_game.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.points_per_game.rank,
                                    leftRankDisplay = awayPlayer.points_per_game.rankDisplay,
                                    centerText = "Pts/Game",
                                    rightValue = homePlayer.points_per_game.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.points_per_game.rank,
                                    rightRankDisplay = homePlayer.points_per_game.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                ShareFiveColStat(
                                    leftValue = awayPlayer.rebounds_per_game.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.rebounds_per_game.rank,
                                    leftRankDisplay = awayPlayer.rebounds_per_game.rankDisplay,
                                    centerText = "Reb/Game",
                                    rightValue = homePlayer.rebounds_per_game.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.rebounds_per_game.rank,
                                    rightRankDisplay = homePlayer.rebounds_per_game.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                ShareFiveColStat(
                                    leftValue = awayPlayer.assists_per_game.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.assists_per_game.rank,
                                    leftRankDisplay = awayPlayer.assists_per_game.rankDisplay,
                                    centerText = "Ast/Game",
                                    rightValue = homePlayer.assists_per_game.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.assists_per_game.rank,
                                    rightRankDisplay = homePlayer.assists_per_game.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                ShareFiveColStat(
                                    leftValue = awayPlayer.field_goal_pct.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.field_goal_pct.rank,
                                    leftRankDisplay = awayPlayer.field_goal_pct.rankDisplay,
                                    centerText = "FG%",
                                    rightValue = homePlayer.field_goal_pct.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.field_goal_pct.rank,
                                    rightRankDisplay = homePlayer.field_goal_pct.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                awayPlayer.true_shooting_pct.value?.let { awayTS ->
                                    homePlayer.true_shooting_pct.value?.let { homeTS ->
                                        ShareFiveColStat(
                                            leftValue = (awayTS * 100).formatStat(1),
                                            leftRank = awayPlayer.true_shooting_pct.rank,
                                            leftRankDisplay = awayPlayer.true_shooting_pct.rankDisplay,
                                            centerText = "TS%",
                                            rightValue = (homeTS * 100).formatStat(1),
                                            rightRank = homePlayer.true_shooting_pct.rank,
                                            rightRankDisplay = homePlayer.true_shooting_pct.rankDisplay,
                                            advantage = 0,
                                            usePlayerRanks = true
                                        )
                                    }
                                }
                            ).take(9)
                        ))
                    }
                }

                // Ensure we have exactly 6 boxes by filling with empty boxes if needed
                val finalStatBoxes = statBoxes + List(6 - statBoxes.size) {
                    ShareStatBox(title = "", fiveColStats = emptyList())
                }

                GenericMatchupShareImage(
                    gameInfo = gameInfo,
                    odds = odds,
                    statBoxes = finalStatBoxes.take(6),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * NBA Matchup content displaying betting odds and team stats
 */
@Composable
private fun NBAMatchupContent(
    matchup: com.joebad.fastbreak.data.model.NBAMatchup,
    viewSelection: Int,
    onViewSelectionChange: (Int) -> Unit
) {
    Column {
        // Extra spacing below pinned team header
        Spacer(modifier = Modifier.height(6.dp))

        // Betting Odds Section
        matchup.odds?.let { odds ->
            val hasOdds = odds.spread != null || odds.overUnder != null ||
                         odds.homeMoneyline != null || odds.awayMoneyline != null

            if (hasOdds) {
                SectionHeader("Betting Odds")

                // Spread
                odds.spread?.let { spread ->
                    val awaySpread = if (spread > 0) "+$spread" else spread.toString()
                    val homeSpread = if (spread < 0) "+${-spread}" else (-spread).toString()
                    ThreeColumnRow(
                        leftText = awaySpread,
                        centerText = "Spread",
                        rightText = homeSpread
                    )
                }

                // Moneyline
                if (odds.homeMoneyline != null || odds.awayMoneyline != null) {
                    ThreeColumnRow(
                        leftText = odds.awayMoneyline ?: "",
                        centerText = "Moneyline",
                        rightText = odds.homeMoneyline ?: ""
                    )
                }

                // Over/Under
                odds.overUnder?.let { ou ->
                    ThreeColumnRow(
                        leftText = "",
                        centerText = "O/U",
                        rightText = ou.toString()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // View Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamStatsNavBadge(
                text = "Team",
                isSelected = viewSelection == 0,
                onClick = { onViewSelectionChange(0) }
            )
            Spacer(modifier = Modifier.width(6.dp))
            TeamStatsNavBadge(
                text = "${matchup.awayTeam.abbreviation} Off vs ${matchup.homeTeam.abbreviation} Def",
                isSelected = viewSelection == 1,
                onClick = { onViewSelectionChange(1) }
            )
            Spacer(modifier = Modifier.width(6.dp))
            TeamStatsNavBadge(
                text = "${matchup.homeTeam.abbreviation} Off vs ${matchup.awayTeam.abbreviation} Def",
                isSelected = viewSelection == 2,
                onClick = { onViewSelectionChange(2) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats based on view selection
        when (viewSelection) {
            0 -> TeamStatsView(matchup)
            1 -> VersusView(matchup, isAwayOffense = true)
            2 -> VersusView(matchup, isAwayOffense = false)
        }

        // Player Stats Section
        Spacer(modifier = Modifier.height(12.dp))
        PlayerStatsView(matchup)
    }
}

/**
 * Team stats view - comparing team stats side by side
 */
@Composable
private fun TeamStatsView(matchup: com.joebad.fastbreak.data.model.NBAMatchup) {
    SectionHeader("Team Stats")

    Column {
        // Offensive Stats
        SubsectionHeader("Offensive Stats")
        Spacer(modifier = Modifier.height(4.dp))

        matchup.comparisons?.sideBySide?.offense?.forEach { (key, stat) ->
            val awayValue = stat.away.value
            val awayRank = stat.away.rank
            val awayRankDisplay = stat.away.rankDisplay
            val homeValue = stat.home.value
            val homeRank = stat.home.rank
            val homeRankDisplay = stat.home.rankDisplay
            val label = stat.label

            // Use rank-based advantage (sport-agnostic: lower rank is always better)
            val advantage = if (awayRank != null && homeRank != null) {
                when {
                    awayRank < homeRank -> -1  // away team has better rank (advantage)
                    awayRank > homeRank -> 1   // home team has better rank (advantage)
                    else -> 0
                }
            } else 0

            val awayText = awayValue?.formatStat(2) ?: "-"
            val homeText = homeValue?.formatStat(2) ?: "-"

            FiveColumnRowWithRanks(
                leftValue = awayText,
                leftRank = awayRank,
                leftRankDisplay = awayRankDisplay,
                centerText = label,
                rightValue = homeText,
                rightRank = homeRank,
                rightRankDisplay = homeRankDisplay,
                advantage = advantage,
                useNBARanks = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Defensive Stats
        SubsectionHeader("Defensive Stats")
        Spacer(modifier = Modifier.height(4.dp))

        matchup.comparisons?.sideBySide?.defense?.forEach { (key, stat) ->
            val awayValue = stat.away.value
            val awayRank = stat.away.rank
            val awayRankDisplay = stat.away.rankDisplay
            val homeValue = stat.home.value
            val homeRank = stat.home.rank
            val homeRankDisplay = stat.home.rankDisplay
            val label = stat.label

            // Use rank-based advantage (sport-agnostic: lower rank is always better)
            val advantage = if (awayRank != null && homeRank != null) {
                when {
                    awayRank < homeRank -> -1  // away team has better rank (advantage)
                    awayRank > homeRank -> 1   // home team has better rank (advantage)
                    else -> 0
                }
            } else 0

            val awayText = awayValue?.formatStat(2) ?: "-"
            val homeText = homeValue?.formatStat(2) ?: "-"

            FiveColumnRowWithRanks(
                leftValue = awayText,
                leftRank = awayRank,
                leftRankDisplay = awayRankDisplay,
                centerText = label,
                rightValue = homeText,
                rightRank = homeRank,
                rightRankDisplay = homeRankDisplay,
                advantage = advantage,
                useNBARanks = true
            )
        }
    }
}

/**
 * Versus view - comparing one team's offense vs another team's defense
 */
@Composable
private fun VersusView(
    matchup: com.joebad.fastbreak.data.model.NBAMatchup,
    isAwayOffense: Boolean
) {
    val offenseTeam = if (isAwayOffense) matchup.awayTeam else matchup.homeTeam
    val defenseTeam = if (isAwayOffense) matchup.homeTeam else matchup.awayTeam

    SectionHeader("${offenseTeam.abbreviation} Offense vs ${defenseTeam.abbreviation} Defense")
    Spacer(modifier = Modifier.height(4.dp))

    matchup.comparisons?.let { comparisons ->
        val currentComparison = if (isAwayOffense) {
            comparisons.awayOffVsHomeDef
        } else {
            comparisons.homeOffVsAwayDef
        }

        Column {
            currentComparison.forEach { (statKey, stat) ->
                val advantage = stat.advantage ?: 0
                val offLabel = stat.offLabel
                val defLabel = stat.defLabel

                val offValue = stat.offense.value
                val offRank = stat.offense.rank
                val offRankDisplay = stat.offense.rankDisplay

                val defValue = stat.defense.value
                val defRank = stat.defense.rank
                val defRankDisplay = stat.defense.rankDisplay

                if (offValue != null && defValue != null) {
                    FiveColumnRowWithRanks(
                        leftValue = offValue.formatStat(2),
                        leftRank = offRank,
                        leftRankDisplay = offRankDisplay,
                        centerText = offLabel,
                        rightValue = defValue.formatStat(2),
                        rightRank = defRank,
                        rightRankDisplay = defRankDisplay,
                        advantage = advantage,
                        useNBARanks = true
                    )
                }
            }
        }
    }
}

/**
 * Player stats view - comparing top players from each team using shared component
 */
@Composable
private fun PlayerStatsView(matchup: com.joebad.fastbreak.data.model.NBAMatchup) {
    SectionHeader("Key Players")

    val awayPlayers = matchup.awayPlayers
    val homePlayers = matchup.homePlayers

    // Only compare players that exist in both lists
    val playerCount = minOf(awayPlayers.size, homePlayers.size)

    // Safety check - don't display if no players
    if (playerCount == 0) return

    // Configure which stats to display
    val statsConfig = listOf(
        PlayerStatConfig<com.joebad.fastbreak.data.model.NBAPlayerInfo>(
            label = "Min/Game",
            decimals = 1,
            accessor = { player -> PlayerStatValue(player.minutes_per_game.value, player.minutes_per_game.rank, player.minutes_per_game.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "Games",
            decimals = 0,
            accessor = { player -> PlayerStatValue(player.games_played.value, player.games_played.rank, player.games_played.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "Pts/Game",
            decimals = 1,
            accessor = { player -> PlayerStatValue(player.points_per_game.value, player.points_per_game.rank, player.points_per_game.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "Reb/Game",
            decimals = 1,
            accessor = { player -> PlayerStatValue(player.rebounds_per_game.value, player.rebounds_per_game.rank, player.rebounds_per_game.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "Ast/Game",
            decimals = 1,
            accessor = { player -> PlayerStatValue(player.assists_per_game.value, player.assists_per_game.rank, player.assists_per_game.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "FG%",
            decimals = 1,
            accessor = { player -> PlayerStatValue(player.field_goal_pct.value, player.field_goal_pct.rank, player.field_goal_pct.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "3P%",
            decimals = 1,
            accessor = { player -> PlayerStatValue(player.three_pt_pct.value, player.three_pt_pct.rank, player.three_pt_pct.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "TS%",
            decimals = 1,
            accessor = { player ->
                val value = player.true_shooting_pct.value?.let { it * 100 }
                PlayerStatValue(value, player.true_shooting_pct.rank, player.true_shooting_pct.rankDisplay)
            }
        ),
        PlayerStatConfig(
            label = "Usage%",
            decimals = 1,
            accessor = { player ->
                val value = player.usage_pct.value?.let { it * 100 }
                PlayerStatValue(value, player.usage_pct.rank, player.usage_pct.rankDisplay)
            }
        )
    )

    // Compare players from each team
    for (i in 0 until playerCount) {
        // Safety check to prevent index out of bounds
        if (i >= awayPlayers.size || i >= homePlayers.size) break

        val awayPlayer = awayPlayers.getOrNull(i) ?: break
        val homePlayer = homePlayers.getOrNull(i) ?: break

        PlayerComparisonSection(
            awayPlayerName = awayPlayer.name,
            homePlayerName = homePlayer.name,
            awayPlayerStats = awayPlayer,
            homePlayerStats = homePlayer,
            positionLabel = "${awayPlayer.position} / ${homePlayer.position}",
            statsConfig = statsConfig,
            usePlayerRanks = true // Use player rank colors (1-30 green, 31-60 orange-red, 61+ dark red)
        )
    }
}

/**
 * Date badge for filtering games by date
 */
@Composable
private fun DateBadge(
    date: LocalDate,
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = date.dayOfWeek.name.take(3),  // Mon, Tue, etc.
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = textColor.copy(alpha = 0.8f)
            )
            Text(
                text = "${date.month.name.take(3)} ${date.dayOfMonth}",  // Jan 23
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

/**
 * Matchup badge showing team abbreviations and game time
 */
@Composable
private fun MatchupBadge(
    awayTeam: String,
    homeTeam: String,
    gameDate: String,
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

    // Parse time in Eastern timezone
    val gameTime = try {
        val instant = Instant.parse(gameDate)
        val easternTime = instant.toLocalDateTime(TimeZone.of("America/New_York"))
        val hour = if (easternTime.hour == 0) 12 else if (easternTime.hour > 12) easternTime.hour - 12 else easternTime.hour
        val amPm = if (easternTime.hour >= 12) "PM" else "AM"
        val minute = easternTime.minute.toString().padStart(2, '0')
        "$hour:$minute $amPm"
    } catch (e: Exception) {
        ""
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
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
                    text = "@",
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
            if (gameTime.isNotEmpty()) {
                Text(
                    text = gameTime,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Section header composable
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/**
 * Subsection header composable
 */
@Composable
private fun SubsectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    )
}

/**
 * Format game date for display
 */
private fun formatGameDate(gameDate: String): String {
    return try {
        val instant = Instant.parse(gameDate)
        val dateTime = instant.toLocalDateTime(TimeZone.UTC)
        "${dateTime.month.name.take(3)} ${dateTime.dayOfMonth}, ${dateTime.year}"
    } catch (e: Exception) {
        gameDate
    }
}
