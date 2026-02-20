package com.joebad.fastbreak.ui.visualizations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.joebad.fastbreak.data.model.LineChartDataPoint
import com.joebad.fastbreak.data.model.LineChartSeries
import com.joebad.fastbreak.data.model.NBAMatchup
import com.joebad.fastbreak.data.model.NBAMatchupVisualization
import com.joebad.fastbreak.data.model.ScatterPlotDataPoint
import com.joebad.fastbreak.data.model.ScatterPlotQuadrants
import com.joebad.fastbreak.data.model.QuadrantConfig
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.QuadrantScatterPlot
import com.joebad.fastbreak.ui.components.ShareFab
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round

/**
 * Data class to hold schedule toggle state and handler
 */
data class ScheduleToggleHandler(
    val isExpanded: Boolean,
    val toggle: () -> Unit
)

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
    modifier: Modifier = Modifier,
    pinnedTeams: List<com.joebad.fastbreak.data.model.PinnedTeam> = emptyList(),
    highlightedTeamCodes: Set<String> = emptySet(),
    onScheduleToggleHandlerChanged: ((ScheduleToggleHandler?) -> Unit)? = null
) {
    // Combine NBA pinned teams with highlighted team codes from deep links
    val nbaPinnedTeamCodes = remember(pinnedTeams, highlightedTeamCodes) {
        val pinned = pinnedTeams.filter { it.sport == "NBA" }.map { it.teamCode }.toSet()
        pinned + highlightedTeamCodes
    }

    // Group matchups by date in Eastern timezone and sort dates chronologically
    // Within each date, prioritize pinned team matchups first
    val matchupsByDate = remember(visualization.dataPoints, nbaPinnedTeamCodes) {
        visualization.dataPoints
            .groupBy { matchup ->
                // Parse ISO 8601 date and extract date part in Eastern timezone
                val instant = Instant.parse(matchup.gameDate)
                instant.toLocalDateTime(TimeZone.of("America/New_York")).date
            }
            .mapValues { (_, matchups) ->
                // Sort matchups: pinned teams first, then others
                matchups.sortedByDescending { matchup ->
                    val hasPinnedTeam = nbaPinnedTeamCodes.contains(matchup.awayTeam.abbreviation) ||
                                       nbaPinnedTeamCodes.contains(matchup.homeTeam.abbreviation)
                    if (hasPinnedTeam) 1 else 0
                }
            }
            .toList()
            .sortedBy { (date, _) -> date }
            .toMap()
    }

    val dates = remember(matchupsByDate) { matchupsByDate.keys.toList() }

    // Calculate initial date index based on highlighted teams (from deep links)
    // Find the first date that contains a matchup with the highlighted team
    val initialDateIndex = remember(dates, matchupsByDate, highlightedTeamCodes) {
        if (highlightedTeamCodes.isEmpty()) {
            0
        } else {
            dates.indexOfFirst { date ->
                matchupsByDate[date]?.any { matchup ->
                    highlightedTeamCodes.contains(matchup.awayTeam.abbreviation) ||
                    highlightedTeamCodes.contains(matchup.homeTeam.abbreviation)
                } == true
            }.takeIf { it >= 0 } ?: 0
        }
    }

    // State for selected date and matchup
    var selectedDateIndex by remember { mutableStateOf(initialDateIndex) }
    var selectedMatchupIndex by remember { mutableStateOf(0) }

    // State for collapsing/expanding schedule selection
    var isScheduleExpanded by remember { mutableStateOf(true) }

    // Expose schedule toggle handler to parent
    LaunchedEffect(isScheduleExpanded) {
        onScheduleToggleHandlerChanged?.invoke(
            ScheduleToggleHandler(
                isExpanded = isScheduleExpanded,
                toggle = { isScheduleExpanded = !isScheduleExpanded }
            )
        )
    }

    // Cleanup when leaving
    DisposableEffect(Unit) {
        onDispose {
            onScheduleToggleHandlerChanged?.invoke(null)
        }
    }

    // Get matchups for selected date
    val selectedDate = if (dates.isNotEmpty()) dates[selectedDateIndex] else null
    val matchupsForDate = selectedDate?.let { matchupsByDate[it] } ?: emptyList()

    // Only reset matchup index if it's out of bounds for the new date
    LaunchedEffect(selectedDateIndex, matchupsForDate.size) {
        if (selectedMatchupIndex >= matchupsForDate.size && matchupsForDate.isNotEmpty()) {
            selectedMatchupIndex = 0
        }
    }

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

    // State for Stats/Charts tab selection: 0 = Stats, 1 = Charts
    var selectedTab by remember { mutableStateOf(0) }

    // Graphics layer for capturing share image
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    val imageExporter = remember { getImageExporter() }
    var isCapturing by remember { mutableStateOf(false) }

    // Format date and event label for share image (matching NFL format)
    val eventLabel = remember(selectedMatchup.gameDate, selectedMatchup.location) {
        val location = selectedMatchup.location?.fullLocation
        if (location != null && location.isNotBlank()) {
            "Regular Season • $location"
        } else {
            "Regular Season"
        }
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
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            // Collapsible schedule selection
            AnimatedVisibility(
                visible = isScheduleExpanded,
                enter = expandVertically(
                    animationSpec = tween(durationMillis = 200),
                    expandFrom = Alignment.Top
                ),
                exit = shrinkVertically(
                    animationSpec = tween(durationMillis = 200),
                    shrinkTowards = Alignment.Top
                )
            ) {
                Column {
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Second row: Matchup badges + Stats/Charts selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Stats/Charts toggle badges
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                        Spacer(modifier = Modifier.width(8.dp))

                        // Matchup badges (scrollable)
                        Row(
                            modifier = Modifier
                                .weight(1f)
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
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // Matchup content with pinned header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> {
                        // Stats Tab
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 20.dp) // Space for pinned header (one row now)
                        ) {
                            // Record and conference rank section (scrollable)
                            RecordAndConferenceSection(
                                awayTeam = selectedMatchup.awayTeam.abbreviation,
                                homeTeam = selectedMatchup.homeTeam.abbreviation,
                                awayWins = selectedMatchup.awayTeam.wins,
                                awayLosses = selectedMatchup.awayTeam.losses,
                                awayConferenceRank = selectedMatchup.awayTeam.conferenceRank,
                                awayConference = selectedMatchup.awayTeam.conference,
                                homeWins = selectedMatchup.homeTeam.wins,
                                homeLosses = selectedMatchup.homeTeam.losses,
                                homeConferenceRank = selectedMatchup.homeTeam.conferenceRank,
                                homeConference = selectedMatchup.homeTeam.conference
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            NBAMatchupContent(
                                matchup = selectedMatchup,
                                viewSelection = viewSelection,
                                onViewSelectionChange = { viewSelection = it }
                            )
                        }

                        // Pinned header (only team abbreviations)
                        PinnedMatchupHeader(
                            awayTeam = selectedMatchup.awayTeam.abbreviation,
                            homeTeam = selectedMatchup.homeTeam.abbreviation,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                    1 -> {
                        // Charts Tab
                        NBAChartsTab(
                            awayTeam = selectedMatchup.awayTeam.abbreviation,
                            homeTeam = selectedMatchup.homeTeam.abbreviation,
                            matchup = selectedMatchup,
                            quadrantConfig = visualization.scatterPlotQuadrants
                        )
                    }
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
                        .padding(horizontal = 8.dp, vertical = 2.dp)
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
                    .requiredHeight(1900.dp)
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
                    source = "hoopR / ESPN",
                    awayRecord = selectedMatchup.awayTeam.wins?.let { w ->
                        selectedMatchup.awayTeam.losses?.let { l -> "$w-$l" }
                    },
                    homeRecord = selectedMatchup.homeTeam.wins?.let { w ->
                        selectedMatchup.homeTeam.losses?.let { l -> "$w-$l" }
                    },
                    awayConferenceRank = selectedMatchup.awayTeam.conferenceRank,
                    homeConferenceRank = selectedMatchup.homeTeam.conferenceRank,
                    awayConference = selectedMatchup.awayTeam.conference,
                    homeConference = selectedMatchup.homeTeam.conference
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
                        leftColor = Team2Color,  // Home team
                        rightColor = Team1Color, // Away team
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
                                },
                                ShareFiveColStat(
                                    leftValue = awayPlayer.steals_per_game.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.steals_per_game.rank,
                                    leftRankDisplay = awayPlayer.steals_per_game.rankDisplay,
                                    centerText = "Stl/Game",
                                    rightValue = homePlayer.steals_per_game.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.steals_per_game.rank,
                                    rightRankDisplay = homePlayer.steals_per_game.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                ShareFiveColStat(
                                    leftValue = awayPlayer.blocks_per_game.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.blocks_per_game.rank,
                                    leftRankDisplay = awayPlayer.blocks_per_game.rankDisplay,
                                    centerText = "Blk/Game",
                                    rightValue = homePlayer.blocks_per_game.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.blocks_per_game.rank,
                                    rightRankDisplay = homePlayer.blocks_per_game.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                ShareFiveColStat(
                                    leftValue = awayPlayer.three_pt_pct.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.three_pt_pct.rank,
                                    leftRankDisplay = awayPlayer.three_pt_pct.rankDisplay,
                                    centerText = "3PT%",
                                    rightValue = homePlayer.three_pt_pct.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.three_pt_pct.rank,
                                    rightRankDisplay = homePlayer.three_pt_pct.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                awayPlayer.usage_pct.value?.let { awayUsage ->
                                    homePlayer.usage_pct.value?.let { homeUsage ->
                                        ShareFiveColStat(
                                            leftValue = (awayUsage * 100).formatStat(1),
                                            leftRank = awayPlayer.usage_pct.rank,
                                            leftRankDisplay = awayPlayer.usage_pct.rankDisplay,
                                            centerText = "Usage%",
                                            rightValue = (homeUsage * 100).formatStat(1),
                                            rightRank = homePlayer.usage_pct.rank,
                                            rightRankDisplay = homePlayer.usage_pct.rankDisplay,
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
                                },
                                ShareFiveColStat(
                                    leftValue = awayPlayer.steals_per_game.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.steals_per_game.rank,
                                    leftRankDisplay = awayPlayer.steals_per_game.rankDisplay,
                                    centerText = "Stl/Game",
                                    rightValue = homePlayer.steals_per_game.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.steals_per_game.rank,
                                    rightRankDisplay = homePlayer.steals_per_game.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                ShareFiveColStat(
                                    leftValue = awayPlayer.blocks_per_game.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.blocks_per_game.rank,
                                    leftRankDisplay = awayPlayer.blocks_per_game.rankDisplay,
                                    centerText = "Blk/Game",
                                    rightValue = homePlayer.blocks_per_game.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.blocks_per_game.rank,
                                    rightRankDisplay = homePlayer.blocks_per_game.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                ShareFiveColStat(
                                    leftValue = awayPlayer.three_pt_pct.value?.formatStat(1) ?: "-",
                                    leftRank = awayPlayer.three_pt_pct.rank,
                                    leftRankDisplay = awayPlayer.three_pt_pct.rankDisplay,
                                    centerText = "3PT%",
                                    rightValue = homePlayer.three_pt_pct.value?.formatStat(1) ?: "-",
                                    rightRank = homePlayer.three_pt_pct.rank,
                                    rightRankDisplay = homePlayer.three_pt_pct.rankDisplay,
                                    advantage = 0,
                                    usePlayerRanks = true
                                ),
                                awayPlayer.usage_pct.value?.let { awayUsage ->
                                    homePlayer.usage_pct.value?.let { homeUsage ->
                                        ShareFiveColStat(
                                            leftValue = (awayUsage * 100).formatStat(1),
                                            leftRank = awayPlayer.usage_pct.rank,
                                            leftRankDisplay = awayPlayer.usage_pct.rankDisplay,
                                            centerText = "Usage%",
                                            rightValue = (homeUsage * 100).formatStat(1),
                                            rightRank = homePlayer.usage_pct.rank,
                                            rightRankDisplay = homePlayer.usage_pct.rankDisplay,
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
        Spacer(modifier = Modifier.height(3.dp))

        // Betting Odds Section
        matchup.odds?.let { odds ->
            val hasOdds = odds.spread != null || odds.overUnder != null ||
                         odds.homeMoneyline != null || odds.awayMoneyline != null

            if (hasOdds) {
                SectionHeader("Betting Odds")

                // Spread (Note: spread value is the home team's spread from the API)
                odds.spread?.let { spread ->
                    // spread is home team's spread (negative = favored, positive = underdog)
                    val homeSpread = if (spread > 0) "+$spread" else spread.toString()
                    // away team gets the opposite
                    val awaySpread = if (spread < 0) "+${-spread}" else (-spread).toString()
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

                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // One Month Trend Section
        OneMonthTrendSection(
            awayTeam = matchup.awayTeam.abbreviation,
            homeTeam = matchup.homeTeam.abbreviation,
            awayStats = matchup.awayTeam.stats,
            homeStats = matchup.homeTeam.stats
        )

        // View Navigation
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamStatsNavBadge(
                text = "Team",
                isSelected = viewSelection == 0,
                onClick = { onViewSelectionChange(0) }
            )
            TeamStatsNavBadge(
                text = "${matchup.awayTeam.abbreviation} Off vs ${matchup.homeTeam.abbreviation} Def",
                isSelected = viewSelection == 1,
                onClick = { onViewSelectionChange(1) }
            )
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
 * Record and conference rank section that scrolls with content
 */
@Composable
private fun RecordAndConferenceSection(
    awayTeam: String,
    homeTeam: String,
    awayWins: Int?,
    awayLosses: Int?,
    awayConferenceRank: Int?,
    awayConference: String?,
    homeWins: Int?,
    homeLosses: Int?,
    homeConferenceRank: Int?,
    homeConference: String?
) {
    // Helper to format conference name
    fun formatConference(conf: String?): String {
        return when (conf?.lowercase()) {
            "east" -> "East"
            "west" -> "West"
            else -> "Conf"
        }
    }

    // Build record strings
    val awayRecord = if (awayWins != null && awayLosses != null && awayConferenceRank != null) {
        "$awayWins-$awayLosses / ${formatOrdinal(awayConferenceRank)} / ${formatConference(awayConference)}"
    } else {
        null
    }

    val homeRecord = if (homeWins != null && homeLosses != null && homeConferenceRank != null) {
        "$homeWins-$homeLosses / ${formatOrdinal(homeConferenceRank)} / ${formatConference(homeConference)}"
    } else {
        null
    }

    if (awayRecord != null || homeRecord != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Away team record with conference rank indicator
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                if (awayConferenceRank != null) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = getConferenceRankColor(awayConferenceRank),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = awayRecord ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }

            // Home team record with conference rank indicator
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = homeRecord ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
                if (homeConferenceRank != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = getConferenceRankColor(homeConferenceRank),
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }
        }
    }
}

/**
 * Helper function to format ordinal numbers (1st, 2nd, 3rd, etc.)
 */
private fun formatOrdinal(number: Int): String {
    return when {
        number % 100 in 11..13 -> "${number}th"
        number % 10 == 1 -> "${number}st"
        number % 10 == 2 -> "${number}nd"
        number % 10 == 3 -> "${number}rd"
        else -> "${number}th"
    }
}

/**
 * Date badge for filtering games by date
 */
@Composable
internal fun DateBadge(
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
internal fun MatchupBadge(
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

/**
 * Navigation badge for Stats/Charts tab selection
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
 * Charts tab showing cumulative net rating and weekly efficiency charts
 */
@Composable
private fun NBAChartsTab(
    awayTeam: String,
    homeTeam: String,
    matchup: NBAMatchup,
    quadrantConfig: ScatterPlotQuadrants? = null
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cumulative Net Rating Chart
        CumulativeNetRatingChart(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayStats = matchup.awayTeam.stats,
            homeStats = matchup.homeTeam.stats,
            tenthNetRatingByWeek = matchup.tenthNetRatingByWeek
        )

        // Weekly Efficiency Scatter Plot
        WeeklyEfficiencyScatterPlot(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayStats = matchup.awayTeam.stats,
            homeStats = matchup.homeTeam.stats,
            leagueStats = matchup.leagueEfficiencyStats,
            quadrantConfig = quadrantConfig
        )
    }
}

/**
 * Cumulative Net Rating line chart comparing both teams over the season
 */
@Composable
private fun CumulativeNetRatingChart(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject,
    homeStats: JsonObject,
    tenthNetRatingByWeek: JsonObject? = null
) {
    Text(
        text = "Cumulative Net Rating Over Season",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    // Parse data points from cumNetRatingByWeek
    val awayDataPoints = parseNetRatingByWeek(awayStats)
    val homeDataPoints = parseNetRatingByWeek(homeStats)

    if (awayDataPoints.isEmpty() && homeDataPoints.isEmpty()) {
        Text(
            text = "Cumulative net rating data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Parse #10 net rating reference line
    val tenthNetRatingPoints = parseTenthNetRatingByWeek(tenthNetRatingByWeek)
    val referenceLines = if (tenthNetRatingPoints.isNotEmpty()) {
        listOf(
            HorizontalReferenceLine(
                yValue = tenthNetRatingPoints,
                color = "#4CAF50", // Green for "playoff cutoff" line
                label = "#10"
            )
        )
    } else {
        emptyList()
    }

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
        yAxisTitle = "Net Rating",
        referenceLines = referenceLines
    )

    // Add legend for team colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
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

        // Add #10 reference line legend if available
        if (tenthNetRatingByWeek != null) {
            Spacer(modifier = Modifier.width(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(2.dp)
                        .background(Color(0xFF4CAF50)) // Green color for reference line
                )
                Text(
                    text = "#10 Rating",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Weekly Efficiency scatter plot showing offensive vs defensive rating for last 10 weeks
 */
@Composable
private fun WeeklyEfficiencyScatterPlot(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject,
    homeStats: JsonObject,
    leagueStats: com.joebad.fastbreak.data.model.LeagueEfficiencyStats? = null,
    quadrantConfig: ScatterPlotQuadrants? = null
) {
    // State for week filter: 0 = Last 10 weeks (all), 1 = Last 5 weeks, 2 = Prior 5 weeks
    var weekFilter by remember { mutableStateOf(0) }

    // Parse efficiency by week data
    val awayEfficiency = parseEfficiencyByWeek(awayStats)
    val homeEfficiency = parseEfficiencyByWeek(homeStats)

    if (awayEfficiency.isEmpty() && homeEfficiency.isEmpty()) {
        Text(
            text = "Weekly efficiency data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Get the max week number to determine week ranges
    val maxWeek = maxOf(
        awayEfficiency.maxOfOrNull { it.week } ?: 0,
        homeEfficiency.maxOfOrNull { it.week } ?: 0
    )

    // Filter data based on selection
    val filteredAwayEfficiency = when (weekFilter) {
        0 -> awayEfficiency.filter { it.week > maxWeek - 10 } // Last 10 weeks (all)
        1 -> awayEfficiency.filter { it.week > maxWeek - 5 } // Last 5 weeks
        2 -> awayEfficiency.filter { it.week <= maxWeek - 5 && it.week > maxWeek - 10 } // Prior 5 weeks
        else -> awayEfficiency
    }

    val filteredHomeEfficiency = when (weekFilter) {
        0 -> homeEfficiency.filter { it.week > maxWeek - 10 } // Last 10 weeks (all)
        1 -> homeEfficiency.filter { it.week > maxWeek - 5 } // Last 5 weeks
        2 -> homeEfficiency.filter { it.week <= maxWeek - 5 && it.week > maxWeek - 10 } // Prior 5 weeks
        else -> homeEfficiency
    }

    Column {
        // Title
        Text(
            text = "Weekly Off vs Def Rating",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Filter badges - centered
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeamStatsNavBadge(
                    text = "Last 10 Wks",
                    isSelected = weekFilter == 0,
                    onClick = { weekFilter = 0 }
                )
                TeamStatsNavBadge(
                    text = "Last 5 Wks",
                    isSelected = weekFilter == 1,
                    onClick = { weekFilter = 1 }
                )
                TeamStatsNavBadge(
                    text = "Prior 5 Wks",
                    isSelected = weekFilter == 2,
                    onClick = { weekFilter = 2 }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Create scatter plot data points from filtered data
        val scatterData = mutableListOf<ScatterPlotDataPoint>()

        filteredAwayEfficiency.forEach { (week, offRating, defRating) ->
            scatterData.add(
                ScatterPlotDataPoint(
                    label = "$awayTeam W$week",
                    x = offRating,
                    y = defRating,
                    sum = offRating - defRating, // Net rating
                    teamCode = awayTeam,
                    color = "#2196F3"
                )
            )
        }

        filteredHomeEfficiency.forEach { (week, offRating, defRating) ->
            scatterData.add(
                ScatterPlotDataPoint(
                    label = "$homeTeam W$week",
                    x = offRating,
                    y = defRating,
                    sum = offRating - defRating, // Net rating
                    teamCode = homeTeam,
                    color = "#FF5722"
                )
            )
        }

        if (scatterData.isEmpty()) {
            Text(
                text = "No data for selected week range",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Use quadrant config from data, with defaults as fallback
            val topRight = quadrantConfig?.topRight ?: QuadrantConfig(label = "Elite", color = "#4CAF50", lightModeColor = "#4CAF50")
            val topLeft = quadrantConfig?.topLeft ?: QuadrantConfig(label = "Defensive", color = "#2196F3", lightModeColor = "#2196F3")
            val bottomLeft = quadrantConfig?.bottomLeft ?: QuadrantConfig(label = "Struggling", color = "#F44336", lightModeColor = "#F44336")
            val bottomRight = quadrantConfig?.bottomRight ?: QuadrantConfig(label = "Offensive", color = "#FF9800", lightModeColor = "#FF9800")

            // State for selected quadrants (for filtering)
            var selectedQuadrants by remember { mutableStateOf(setOf<String>()) }

            // State for selected teams (for filtering)
            var selectedTeams by remember { mutableStateOf(setOf<String>()) }

            // Calculate center points for quadrant determination
            val centerX = leagueStats?.avgOffRating ?: scatterData.map { it.x }.average()
            val centerY = leagueStats?.avgDefRating ?: scatterData.map { it.y }.average()

            // Helper to determine which quadrant a point belongs to
            // With invertYAxis: lower Y (better defense) = top, higher Y (worse defense) = bottom
            fun getQuadrant(x: Double, y: Double): String {
                val isRight = x >= centerX  // Good offense
                val isTop = y <= centerY    // Good defense (lower rating is better)
                return when {
                    isTop && isRight -> "topRight"
                    isTop && !isRight -> "topLeft"
                    !isTop && !isRight -> "bottomLeft"
                    else -> "bottomRight"
                }
            }

            // Filter data based on selected quadrants and selected teams
            val filteredScatterData = scatterData.filter { point ->
                val quadrantMatch = selectedQuadrants.isEmpty() ||
                    selectedQuadrants.contains(getQuadrant(point.x, point.y))
                val teamMatch = selectedTeams.isEmpty() ||
                    selectedTeams.contains(point.teamCode)
                quadrantMatch && teamMatch
            }

            QuadrantScatterPlot(
                data = filteredScatterData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                title = "",
                xAxisLabel = "Offensive Rating",
                yAxisLabel = "Defensive Rating",
                invertYAxis = true, // Lower defensive rating is better
                highlightedTeamCodes = setOf(awayTeam, homeTeam),
                // Quadrant labels for NBA Off vs Def Rating (with inverted Y)
                // Top = good defense (low rating), Bottom = poor defense (high rating)
                // Right = good offense (high rating), Left = poor offense (low rating)
                quadrantTopRight = topRight,
                quadrantTopLeft = topLeft,
                quadrantBottomLeft = bottomLeft,
                quadrantBottomRight = bottomRight,
                // Use league-wide stats for consistent scaling across all matchups
                customCenterX = leagueStats?.avgOffRating,
                customCenterY = leagueStats?.avgDefRating,
                customXMin = leagueStats?.minOffRating,
                customXMax = leagueStats?.maxOffRating,
                customYMin = leagueStats?.minDefRating,
                customYMax = leagueStats?.maxDefRating,
                // Use unfiltered data for regression line so it stays fixed when filtering
                regressionData = scatterData
            )

            // Team Legend (interactive - click to filter by team)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Away team legend item (clickable)
                    val awaySelected = selectedTeams.contains(awayTeam)
                    val awayColor = Color(0xFF2196F3)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                selectedTeams = if (awaySelected) {
                                    selectedTeams - awayTeam
                                } else {
                                    selectedTeams + awayTeam
                                }
                            }
                            .background(
                                if (awaySelected) awayColor.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (awaySelected || selectedTeams.isEmpty()) awayColor
                                    else awayColor.copy(alpha = 0.3f),
                                    CircleShape
                                )
                        )
                        Text(
                            text = awayTeam,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (awaySelected || selectedTeams.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    // Home team legend item (clickable)
                    val homeSelected = selectedTeams.contains(homeTeam)
                    val homeColor = Color(0xFFFF5722)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                selectedTeams = if (homeSelected) {
                                    selectedTeams - homeTeam
                                } else {
                                    selectedTeams + homeTeam
                                }
                            }
                            .background(
                                if (homeSelected) homeColor.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (homeSelected || selectedTeams.isEmpty()) homeColor
                                    else homeColor.copy(alpha = 0.3f),
                                    CircleShape
                                )
                        )
                        Text(
                            text = homeTeam,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (homeSelected || selectedTeams.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Quadrant Legend (interactive - click to filter, 2x2 grid)
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top row: Elite and Defensive
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Elite (top right)
                    ScatterLegendItem(
                        label = topRight.label,
                        color = Color(0xFF4CAF50),
                        isSelected = selectedQuadrants.isEmpty() || selectedQuadrants.contains("topRight"),
                        onClick = {
                            selectedQuadrants = if (selectedQuadrants.contains("topRight")) {
                                selectedQuadrants - "topRight"
                            } else {
                                selectedQuadrants + "topRight"
                            }
                        }
                    )
                    // Defensive (top left)
                    ScatterLegendItem(
                        label = topLeft.label,
                        color = Color(0xFF2196F3),
                        isSelected = selectedQuadrants.isEmpty() || selectedQuadrants.contains("topLeft"),
                        onClick = {
                            selectedQuadrants = if (selectedQuadrants.contains("topLeft")) {
                                selectedQuadrants - "topLeft"
                            } else {
                                selectedQuadrants + "topLeft"
                            }
                        }
                    )
                }
                // Bottom row: Struggling and Offensive
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Struggling (bottom left)
                    ScatterLegendItem(
                        label = bottomLeft.label,
                        color = Color(0xFFF44336),
                        isSelected = selectedQuadrants.isEmpty() || selectedQuadrants.contains("bottomLeft"),
                        onClick = {
                            selectedQuadrants = if (selectedQuadrants.contains("bottomLeft")) {
                                selectedQuadrants - "bottomLeft"
                            } else {
                                selectedQuadrants + "bottomLeft"
                            }
                        }
                    )
                    // Offensive (bottom right)
                    ScatterLegendItem(
                        label = bottomRight.label,
                        color = Color(0xFFFF9800),
                        isSelected = selectedQuadrants.isEmpty() || selectedQuadrants.contains("bottomRight"),
                        onClick = {
                            selectedQuadrants = if (selectedQuadrants.contains("bottomRight")) {
                                selectedQuadrants - "bottomRight"
                            } else {
                                selectedQuadrants + "bottomRight"
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Interactive legend item for scatter plot quadrant filtering
 */
@Composable
private fun ScatterLegendItem(
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (isSelected) color else color.copy(alpha = 0.3f),
                    CircleShape
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
    }
}

/**
 * Helper to parse cumulative net rating by week from stats JsonObject
 */
private fun parseNetRatingByWeek(stats: JsonObject): List<LineChartDataPoint> {
    val dataPoints = mutableListOf<LineChartDataPoint>()

    // Get the cumNetRatingByWeek object
    val cumNetRating = stats["cumNetRatingByWeek"]
    if (cumNetRating is JsonObject) {
        cumNetRating.forEach { (weekKey, value) ->
            val weekNum = weekKey.removePrefix("week-").toIntOrNull()
            val netRating = (value as? JsonPrimitive)?.doubleOrNull
            if (weekNum != null && netRating != null) {
                dataPoints.add(LineChartDataPoint(x = weekNum.toDouble(), y = netRating))
            }
        }
    }

    return dataPoints.sortedBy { it.x }
}

/**
 * Helper to parse #10 net rating by week from matchup JsonObject
 * Returns a list of (x, y) pairs representing the reference line
 */
private fun parseTenthNetRatingByWeek(data: JsonObject?): List<Pair<Double, Double>> {
    if (data == null) return emptyList()

    val points = mutableListOf<Pair<Double, Double>>()

    data.forEach { (weekKey, value) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val netRating = (value as? JsonPrimitive)?.doubleOrNull
        if (weekNum != null && netRating != null) {
            points.add(Pair(weekNum.toDouble(), netRating))
        }
    }

    return points.sortedBy { it.first }
}

/**
 * Helper data class for weekly efficiency
 */
private data class WeeklyEfficiencyData(
    val week: Int,
    val offRating: Double,
    val defRating: Double
)

/**
 * Helper to parse weekly efficiency data from stats JsonObject
 */
private fun parseEfficiencyByWeek(stats: JsonObject): List<WeeklyEfficiencyData> {
    val efficiencyList = mutableListOf<WeeklyEfficiencyData>()

    // Get the efficiencyByWeek object
    val efficiency = stats["efficiencyByWeek"]
    if (efficiency is JsonObject) {
        efficiency.forEach { (weekKey, value) ->
            val weekNum = weekKey.removePrefix("week-").toIntOrNull()
            if (weekNum != null && value is JsonObject) {
                val offRating = (value["offRating"] as? JsonPrimitive)?.doubleOrNull
                val defRating = (value["defRating"] as? JsonPrimitive)?.doubleOrNull
                if (offRating != null && defRating != null) {
                    efficiencyList.add(WeeklyEfficiencyData(weekNum, offRating, defRating))
                }
            }
        }
    }

    return efficiencyList.sortedBy { it.week }
}

/**
 * One Month Trend section comparing teams over the last 4 weeks
 */
@Composable
private fun OneMonthTrendSection(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject,
    homeStats: JsonObject
) {
    val awayTrend = parseMonthTrend(awayStats)
    val homeTrend = parseMonthTrend(homeStats)

    // Only show section if we have trend data for at least one team
    if (awayTrend == null && homeTrend == null) {
        return
    }

    SectionHeader("One Month Trend")
    Spacer(modifier = Modifier.height(4.dp))

    // Record
    val awayRecord = awayTrend?.let { "${it.wins}-${it.losses}" } ?: "-"
    val homeRecord = homeTrend?.let { "${it.wins}-${it.losses}" } ?: "-"
    val recordAdvantage = when {
        awayTrend?.recordRank != null && homeTrend?.recordRank != null ->
            when {
                awayTrend.recordRank < homeTrend.recordRank -> -1
                homeTrend.recordRank < awayTrend.recordRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayRecord,
        leftRank = awayTrend?.recordRank,
        leftRankDisplay = awayTrend?.recordRankDisplay,
        centerText = "Record",
        rightValue = homeRecord,
        rightRank = homeTrend?.recordRank,
        rightRankDisplay = homeTrend?.recordRankDisplay,
        advantage = recordAdvantage
    )

    // Net Rating
    val netRatingAdvantage = when {
        awayTrend?.netRatingRank != null && homeTrend?.netRatingRank != null ->
            when {
                awayTrend.netRatingRank < homeTrend.netRatingRank -> -1
                homeTrend.netRatingRank < awayTrend.netRatingRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.netRating?.formatStat(1) ?: "-",
        leftRank = awayTrend?.netRatingRank,
        leftRankDisplay = awayTrend?.netRatingRankDisplay,
        centerText = "Net Rating",
        rightValue = homeTrend?.netRating?.formatStat(1) ?: "-",
        rightRank = homeTrend?.netRatingRank,
        rightRankDisplay = homeTrend?.netRatingRankDisplay,
        advantage = netRatingAdvantage
    )

    // Offensive Rating
    val offRatingAdvantage = when {
        awayTrend?.offRatingRank != null && homeTrend?.offRatingRank != null ->
            when {
                awayTrend.offRatingRank < homeTrend.offRatingRank -> -1
                homeTrend.offRatingRank < awayTrend.offRatingRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.offRating?.formatStat(1) ?: "-",
        leftRank = awayTrend?.offRatingRank,
        leftRankDisplay = awayTrend?.offRatingRankDisplay,
        centerText = "Off Rating",
        rightValue = homeTrend?.offRating?.formatStat(1) ?: "-",
        rightRank = homeTrend?.offRatingRank,
        rightRankDisplay = homeTrend?.offRatingRankDisplay,
        advantage = offRatingAdvantage
    )

    // Defensive Rating (lower rank is better)
    val defRatingAdvantage = when {
        awayTrend?.defRatingRank != null && homeTrend?.defRatingRank != null ->
            when {
                awayTrend.defRatingRank < homeTrend.defRatingRank -> -1
                homeTrend.defRatingRank < awayTrend.defRatingRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.defRating?.formatStat(1) ?: "-",
        leftRank = awayTrend?.defRatingRank,
        leftRankDisplay = awayTrend?.defRatingRankDisplay,
        centerText = "Def Rating",
        rightValue = homeTrend?.defRating?.formatStat(1) ?: "-",
        rightRank = homeTrend?.defRatingRank,
        rightRankDisplay = homeTrend?.defRatingRankDisplay,
        advantage = defRatingAdvantage
    )

    // Points Per Game
    val ppgAdvantage = when {
        awayTrend?.ppgRank != null && homeTrend?.ppgRank != null ->
            when {
                awayTrend.ppgRank < homeTrend.ppgRank -> -1
                homeTrend.ppgRank < awayTrend.ppgRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.ppg?.formatStat(1) ?: "-",
        leftRank = awayTrend?.ppgRank,
        leftRankDisplay = awayTrend?.ppgRankDisplay,
        centerText = "PPG",
        rightValue = homeTrend?.ppg?.formatStat(1) ?: "-",
        rightRank = homeTrend?.ppgRank,
        rightRankDisplay = homeTrend?.ppgRankDisplay,
        advantage = ppgAdvantage
    )

    // Assists Per Game
    val apgAdvantage = when {
        awayTrend?.apgRank != null && homeTrend?.apgRank != null ->
            when {
                awayTrend.apgRank < homeTrend.apgRank -> -1
                homeTrend.apgRank < awayTrend.apgRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.apg?.formatStat(1) ?: "-",
        leftRank = awayTrend?.apgRank,
        leftRankDisplay = awayTrend?.apgRankDisplay,
        centerText = "APG",
        rightValue = homeTrend?.apg?.formatStat(1) ?: "-",
        rightRank = homeTrend?.apgRank,
        rightRankDisplay = homeTrend?.apgRankDisplay,
        advantage = apgAdvantage
    )

    // Turnovers Per Game (lower is better)
    val tpgAdvantage = when {
        awayTrend?.tpgRank != null && homeTrend?.tpgRank != null ->
            when {
                awayTrend.tpgRank < homeTrend.tpgRank -> -1
                homeTrend.tpgRank < awayTrend.tpgRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.tpg?.formatStat(1) ?: "-",
        leftRank = awayTrend?.tpgRank,
        leftRankDisplay = awayTrend?.tpgRankDisplay,
        centerText = "TOV",
        rightValue = homeTrend?.tpg?.formatStat(1) ?: "-",
        rightRank = homeTrend?.tpgRank,
        rightRankDisplay = homeTrend?.tpgRankDisplay,
        advantage = tpgAdvantage
    )

    // Turnover Differential (higher is better)
    val tovDiffAdvantage = when {
        awayTrend?.tovDiffRank != null && homeTrend?.tovDiffRank != null ->
            when {
                awayTrend.tovDiffRank < homeTrend.tovDiffRank -> -1
                homeTrend.tovDiffRank < awayTrend.tovDiffRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.tovDiff?.let { if (it >= 0) "+${it.formatStat(1)}" else it.formatStat(1) } ?: "-",
        leftRank = awayTrend?.tovDiffRank,
        leftRankDisplay = awayTrend?.tovDiffRankDisplay,
        centerText = "TOV Diff",
        rightValue = homeTrend?.tovDiff?.let { if (it >= 0) "+${it.formatStat(1)}" else it.formatStat(1) } ?: "-",
        rightRank = homeTrend?.tovDiffRank,
        rightRankDisplay = homeTrend?.tovDiffRankDisplay,
        advantage = tovDiffAdvantage
    )

    Spacer(modifier = Modifier.height(6.dp))
}

/**
 * Data class for month trend stats
 */
private data class MonthTrendData(
    val wins: Int,
    val losses: Int,
    val recordRank: Int?,
    val recordRankDisplay: String?,
    val netRating: Double?,
    val netRatingRank: Int?,
    val netRatingRankDisplay: String?,
    val offRating: Double?,
    val offRatingRank: Int?,
    val offRatingRankDisplay: String?,
    val defRating: Double?,
    val defRatingRank: Int?,
    val defRatingRankDisplay: String?,
    val ppg: Double?,
    val ppgRank: Int?,
    val ppgRankDisplay: String?,
    val apg: Double?,
    val apgRank: Int?,
    val apgRankDisplay: String?,
    val tpg: Double?,
    val tpgRank: Int?,
    val tpgRankDisplay: String?,
    val tovDiff: Double?,
    val tovDiffRank: Int?,
    val tovDiffRankDisplay: String?
)

/**
 * Helper to parse month trend data from stats JsonObject
 */
private fun parseMonthTrend(stats: JsonObject): MonthTrendData? {
    val monthTrend = stats["monthTrend"]
    if (monthTrend !is JsonObject) {
        return null
    }

    // Parse record
    val record = monthTrend["record"] as? JsonObject
    val wins = (record?.get("wins") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    val losses = (record?.get("losses") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    val recordRank = (record?.get("rank") as? JsonPrimitive)?.content?.toIntOrNull()
    val recordRankDisplay = (record?.get("rankDisplay") as? JsonPrimitive)?.content

    // Parse net rating
    val netRatingObj = monthTrend["netRating"] as? JsonObject
    val netRating = (netRatingObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val netRatingRank = (netRatingObj?.get("rank") as? JsonPrimitive)?.content?.toIntOrNull()
    val netRatingRankDisplay = (netRatingObj?.get("rankDisplay") as? JsonPrimitive)?.content

    // Parse offensive rating
    val offRatingObj = monthTrend["offensiveRating"] as? JsonObject
    val offRating = (offRatingObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val offRatingRank = (offRatingObj?.get("rank") as? JsonPrimitive)?.content?.toIntOrNull()
    val offRatingRankDisplay = (offRatingObj?.get("rankDisplay") as? JsonPrimitive)?.content

    // Parse defensive rating
    val defRatingObj = monthTrend["defensiveRating"] as? JsonObject
    val defRating = (defRatingObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val defRatingRank = (defRatingObj?.get("rank") as? JsonPrimitive)?.content?.toIntOrNull()
    val defRatingRankDisplay = (defRatingObj?.get("rankDisplay") as? JsonPrimitive)?.content

    // Parse points per game
    val ppgObj = monthTrend["pointsPerGame"] as? JsonObject
    val ppg = (ppgObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val ppgRank = (ppgObj?.get("rank") as? JsonPrimitive)?.content?.toIntOrNull()
    val ppgRankDisplay = (ppgObj?.get("rankDisplay") as? JsonPrimitive)?.content

    // Parse assists per game
    val apgObj = monthTrend["assistsPerGame"] as? JsonObject
    val apg = (apgObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val apgRank = (apgObj?.get("rank") as? JsonPrimitive)?.content?.toIntOrNull()
    val apgRankDisplay = (apgObj?.get("rankDisplay") as? JsonPrimitive)?.content

    // Parse turnovers per game
    val tpgObj = monthTrend["turnoversPerGame"] as? JsonObject
    val tpg = (tpgObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val tpgRank = (tpgObj?.get("rank") as? JsonPrimitive)?.content?.toIntOrNull()
    val tpgRankDisplay = (tpgObj?.get("rankDisplay") as? JsonPrimitive)?.content

    // Parse turnover differential
    val tovDiffObj = monthTrend["turnoverDiff"] as? JsonObject
    val tovDiff = (tovDiffObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val tovDiffRank = (tovDiffObj?.get("rank") as? JsonPrimitive)?.content?.toIntOrNull()
    val tovDiffRankDisplay = (tovDiffObj?.get("rankDisplay") as? JsonPrimitive)?.content

    return MonthTrendData(
        wins = wins,
        losses = losses,
        recordRank = recordRank,
        recordRankDisplay = recordRankDisplay,
        netRating = netRating,
        netRatingRank = netRatingRank,
        netRatingRankDisplay = netRatingRankDisplay,
        offRating = offRating,
        offRatingRank = offRatingRank,
        offRatingRankDisplay = offRatingRankDisplay,
        defRating = defRating,
        defRatingRank = defRatingRank,
        defRatingRankDisplay = defRatingRankDisplay,
        ppg = ppg,
        ppgRank = ppgRank,
        ppgRankDisplay = ppgRankDisplay,
        apg = apg,
        apgRank = apgRank,
        apgRankDisplay = apgRankDisplay,
        tpg = tpg,
        tpgRank = tpgRank,
        tpgRankDisplay = tpgRankDisplay,
        tovDiff = tovDiff,
        tovDiffRank = tovDiffRank,
        tovDiffRankDisplay = tovDiffRankDisplay
    )
}
