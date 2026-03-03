package com.joebad.fastbreak.ui.visualizations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.LineChartDataPoint
import com.joebad.fastbreak.data.model.LineChartSeries
import com.joebad.fastbreak.data.model.NHLMatchup
import com.joebad.fastbreak.data.model.NHLMatchupVisualization
import com.joebad.fastbreak.data.model.NHLPlayerInfo
import com.joebad.fastbreak.data.model.PlayoffProbability
import com.joebad.fastbreak.data.model.QuadrantConfig
import com.joebad.fastbreak.data.model.LeagueCumXgStats
import com.joebad.fastbreak.data.model.LeagueXgVsPointsStats
import com.joebad.fastbreak.data.model.ScatterPlotDataPoint
import com.joebad.fastbreak.ui.QuadrantScatterPlot
import com.joebad.fastbreak.ui.TeamLegendEntry
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.components.MultiOptionFab
import com.joebad.fastbreak.ui.components.FabOption
import com.joebad.fastbreak.ui.components.ShareFab
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.math.round

/**
 * Capture target for on-demand share image generation.
 */
private enum class NhlCaptureTarget {
    PRE_GAME,
    POST_GAME
}

/**
 * Data class to hold capture state and title for sharing
 */
private data class NhlCaptureRequest(
    val target: NhlCaptureTarget,
    val title: String
)

/**
 * Helper to format percentage values stored as 0-1 decimals
 */
private fun Double?.formatPct(decimals: Int = 1): String {
    return this?.let { (it * 100).formatStat(decimals) + "%" } ?: "-"
}

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
 * NHL Matchup Worksheet component with two-row navigation:
 * - First row: Date badges to filter games by date
 * - Second row: Matchup badges for the selected date
 */
@Composable
fun NHLMatchupWorksheet(
    visualization: NHLMatchupVisualization,
    modifier: Modifier = Modifier,
    pinnedTeams: List<com.joebad.fastbreak.data.model.PinnedTeam> = emptyList(),
    highlightedTeamCodes: Set<String> = emptySet(),
    onScheduleToggleHandlerChanged: ((ScheduleToggleHandler?) -> Unit)? = null
) {
    // Combine NHL pinned teams with highlighted team codes from deep links
    val nhlPinnedTeamCodes = remember(pinnedTeams, highlightedTeamCodes) {
        val pinned = pinnedTeams.filter { it.sport == "NHL" }.map { it.teamCode }.toSet()
        pinned + highlightedTeamCodes
    }

    // Group matchups by date in Eastern timezone and sort dates chronologically
    // Within each date, prioritize pinned team matchups first
    val matchupsByDate = remember(visualization.dataPoints, nhlPinnedTeamCodes) {
        visualization.dataPoints
            .groupBy { matchup ->
                // Parse ISO 8601 date and extract date part in Eastern timezone
                val instant = Instant.parse(matchup.gameDate)
                instant.toLocalDateTime(TimeZone.of("America/New_York")).date
            }
            .mapValues { (_, matchups) ->
                // Sort matchups: pinned teams first, then others
                matchups.sortedByDescending { matchup ->
                    val hasPinnedTeam = nhlPinnedTeamCodes.contains(matchup.awayTeam.abbreviation) ||
                                       nhlPinnedTeamCodes.contains(matchup.homeTeam.abbreviation)
                    if (hasPinnedTeam) 1 else 0
                }
            }
            .toList()
            .sortedBy { (date, _) -> date }
            .toMap()
    }

    val dates = remember(matchupsByDate) { matchupsByDate.keys.toList() }

    // Calculate initial date index based on highlighted teams or current date
    val initialDateIndex = remember(dates, matchupsByDate, highlightedTeamCodes) {
        if (highlightedTeamCodes.isNotEmpty()) {
            dates.indexOfFirst { date ->
                matchupsByDate[date]?.any { matchup ->
                    highlightedTeamCodes.contains(matchup.awayTeam.abbreviation) ||
                    highlightedTeamCodes.contains(matchup.homeTeam.abbreviation)
                } == true
            }.takeIf { it >= 0 } ?: 0
        } else {
            val now = Clock.System.now()
            val today = Instant.fromEpochMilliseconds(now.toEpochMilliseconds())
                .toLocalDateTime(TimeZone.of("America/New_York")).date
            dates.indexOfFirst { it == today }.takeIf { it >= 0 } ?: 0
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

    // On-demand capture
    var captureRequest by remember { mutableStateOf<NhlCaptureRequest?>(null) }
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }

    // Share callbacks from charts (set by chart components)
    var cumXgfShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var xgVsPointsShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Format date and event label for share image
    val eventLabel = remember(selectedMatchup.gameDate, selectedMatchup.location) {
        val location = selectedMatchup.location?.fullLocation
        if (location != null && location.isNotBlank()) {
            "Regular Season - $location"
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
        "${selectedMatchup.awayTeam.abbreviation} @ ${selectedMatchup.homeTeam.abbreviation} - NHL Matchup"
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
                            NHLNavigationBadge(
                                text = "Stats",
                                isSelected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            )
                            NHLNavigationBadge(
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
                                .padding(top = 20.dp)
                        ) {
                            // Record section
                            NHLRecordSection(
                                awayTeam = selectedMatchup.awayTeam.abbreviation,
                                homeTeam = selectedMatchup.homeTeam.abbreviation,
                                awayWins = selectedMatchup.awayTeam.wins,
                                awayLosses = selectedMatchup.awayTeam.losses,
                                awayOtLosses = selectedMatchup.awayTeam.otLosses,
                                awayPoints = selectedMatchup.awayTeam.points,
                                awayConferenceRank = selectedMatchup.awayTeam.conferenceRank,
                                awayConference = selectedMatchup.awayTeam.conference,
                                homeWins = selectedMatchup.homeTeam.wins,
                                homeLosses = selectedMatchup.homeTeam.losses,
                                homeOtLosses = selectedMatchup.homeTeam.otLosses,
                                homePoints = selectedMatchup.homeTeam.points,
                                homeConferenceRank = selectedMatchup.homeTeam.conferenceRank,
                                homeConference = selectedMatchup.homeTeam.conference
                            )

                            NHLPlayoffProbabilitySection(
                                awayProb = selectedMatchup.awayTeam.playoffProbability,
                                homeProb = selectedMatchup.homeTeam.playoffProbability
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            NHLMatchupContent(
                                matchup = selectedMatchup,
                                viewSelection = viewSelection,
                                onViewSelectionChange = { viewSelection = it }
                            )

                            // Source attribution at bottom of scrollable content
                            visualization.source?.let { source ->
                                Text(
                                    text = "Source: $source",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                        }

                        // Pinned header
                        PinnedMatchupHeader(
                            awayTeam = selectedMatchup.awayTeam.abbreviation,
                            homeTeam = selectedMatchup.homeTeam.abbreviation,
                            awayScore = selectedMatchup.results?.finalScore?.away,
                            homeScore = selectedMatchup.results?.finalScore?.home,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                    1 -> {
                        // Charts Tab
                        NHLChartsTab(
                            awayTeam = selectedMatchup.awayTeam.abbreviation,
                            homeTeam = selectedMatchup.homeTeam.abbreviation,
                            matchup = selectedMatchup,
                            source = visualization.source,
                            onCumXgfShareClick = { callback -> cumXgfShareCallback = callback },
                            onXgVsPointsShareClick = { callback -> xgVsPointsShareCallback = callback }
                        )
                    }
                }
            }
        }

        // Build FAB options based on current tab and results availability
        val hasResults = selectedMatchup.results != null

        when {
            // Charts tab: show chart share options
            selectedTab == 1 -> {
                val chartOptions = listOf(
                    FabOption(
                        icon = Icons.Filled.TrendingUp,
                        label = "Cumulative xG%",
                        onClick = { cumXgfShareCallback?.invoke() }
                    ),
                    FabOption(
                        icon = Icons.Filled.Star,
                        label = "xG% vs Points%",
                        onClick = { xgVsPointsShareCallback?.invoke() }
                    )
                )
                MultiOptionFab(
                    options = chartOptions,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
            // Stats tab with results: show Pre Game and Post Game options
            selectedTab == 0 && hasResults -> {
                val statsOptions = listOf(
                    FabOption(
                        icon = Icons.Filled.PlayArrow,
                        label = "Pre Game",
                        onClick = { captureRequest = NhlCaptureRequest(NhlCaptureTarget.PRE_GAME, "$shareTitle - Pre Game") }
                    ),
                    FabOption(
                        icon = Icons.Filled.Check,
                        label = "Post Game",
                        onClick = { captureRequest = NhlCaptureRequest(NhlCaptureTarget.POST_GAME, "$shareTitle - Results") }
                    )
                )
                MultiOptionFab(
                    options = statsOptions,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
            selectedTab == 0 -> {
                ShareFab(
                    onClick = { captureRequest = NhlCaptureRequest(NhlCaptureTarget.PRE_GAME, "$shareTitle - Pre Game") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
        }

        // On-demand capture
        captureRequest?.let { request ->
            val (captureWidth, captureHeight) = when (request.target) {
                NhlCaptureTarget.PRE_GAME -> 3400.dp to 1900.dp
                NhlCaptureTarget.POST_GAME -> 400.dp to 340.dp
            }

            LaunchedEffect(request) {
                kotlinx.coroutines.delay(50)
                try {
                    val bitmap = graphicsLayer.toImageBitmap()
                    println("NHL Matchup Share: Captured bitmap size: ${bitmap.width}x${bitmap.height}")
                    imageExporter.shareImage(bitmap, request.title)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    captureRequest = null
                }
            }

            when (request.target) {
                NhlCaptureTarget.PRE_GAME -> {
                    CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                        Box(
                            modifier = Modifier
                                .requiredWidth(captureWidth)
                                .requiredHeight(captureHeight)
                                .offset { IntOffset(-10000, 0) }
                                .drawWithContent {
                                    graphicsLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawLayer(graphicsLayer)
                                }
                        ) {
                            val gameInfo = ShareGameInfo(
                                awayTeam = selectedMatchup.awayTeam.abbreviation,
                                homeTeam = selectedMatchup.homeTeam.abbreviation,
                                eventLabel = eventLabel,
                                formattedDate = formattedDate,
                                source = "NHL API",
                                awayRecord = selectedMatchup.awayTeam.let { team ->
                                    if (team.wins != null && team.losses != null) {
                                        val otl = team.otLosses ?: 0
                                        "${team.wins}-${team.losses}-$otl"
                                    } else null
                                },
                                homeRecord = selectedMatchup.homeTeam.let { team ->
                                    if (team.wins != null && team.losses != null) {
                                        val otl = team.otLosses ?: 0
                                        "${team.wins}-${team.losses}-$otl"
                                    } else null
                                },
                                awayConferenceRank = selectedMatchup.awayTeam.conferenceRank,
                                homeConferenceRank = selectedMatchup.homeTeam.conferenceRank,
                                awayConference = selectedMatchup.awayTeam.conference,
                                homeConference = selectedMatchup.homeTeam.conference,
                                awayPlayoffProb = selectedMatchup.awayTeam.playoffProbability?.playoffProb,
                                homePlayoffProb = selectedMatchup.homeTeam.playoffProbability?.playoffProb,
                                awayChampProb = selectedMatchup.awayTeam.playoffProbability?.champProb,
                                homeChampProb = selectedMatchup.homeTeam.playoffProbability?.champProb
                            )

                            val odds = selectedMatchup.odds?.let {
                                ShareOdds(
                                    awayMoneyline = it.awayMoneyline?.toString(),
                                    homeMoneyline = it.homeMoneyline?.toString(),
                                    awaySpread = it.spread?.let { spread ->
                                        if (spread > 0) "+$spread" else spread.toString()
                                    },
                                    homeSpread = it.spread?.let { spread ->
                                        if (spread < 0) "+${-spread}" else (-spread).toString()
                                    },
                                    overUnder = it.overUnder?.toString()
                                )
                            }

                            val statBoxes = buildNHLShareStatBoxes(selectedMatchup)

                            GenericMatchupShareImage(
                                gameInfo = gameInfo,
                                odds = odds,
                                statBoxes = statBoxes,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                NhlCaptureTarget.POST_GAME -> {
                    CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                        Box(
                            modifier = Modifier
                                .requiredWidth(captureWidth)
                                .requiredHeight(captureHeight)
                                .offset { IntOffset(-10000, 0) }
                                .drawWithContent {
                                    graphicsLayer.record {
                                        this@drawWithContent.drawContent()
                                    }
                                    drawLayer(graphicsLayer)
                                }
                        ) {
                            NHLPostGameShareImage(
                                matchup = selectedMatchup,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Build stat boxes for NHL share image (6 boxes like NBA)
 */
private fun buildNHLShareStatBoxes(matchup: NHLMatchup): List<ShareStatBox> {
    return buildList {
        // Box 1: Offensive Team Stats
        add(ShareStatBox(
            title = "Offensive Stats",
            fiveColStats = matchup.comparisons?.sideBySide?.offense?.mapNotNull { (_, stat) ->
                val isPct = stat.label.contains("%")
                val awayValue = stat.away.value?.let { if (isPct) (it * 100).formatStat(1) + "%" else it.formatStat(2) } ?: return@mapNotNull null
                val homeValue = stat.home.value?.let { if (isPct) (it * 100).formatStat(1) + "%" else it.formatStat(2) } ?: return@mapNotNull null
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
                    advantage = advantage
                )
            }?.take(9) ?: emptyList()
        ))

        // Box 2: Defensive Team Stats
        add(ShareStatBox(
            title = "Defensive Stats",
            fiveColStats = matchup.comparisons?.sideBySide?.defense?.mapNotNull { (_, stat) ->
                val isPct = stat.label.contains("%")
                val awayValue = stat.away.value?.let { if (isPct) (it * 100).formatStat(1) + "%" else it.formatStat(2) } ?: return@mapNotNull null
                val homeValue = stat.home.value?.let { if (isPct) (it * 100).formatStat(1) + "%" else it.formatStat(2) } ?: return@mapNotNull null
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
                    advantage = advantage
                )
            }?.take(9) ?: emptyList()
        ))

        // Box 3: Away Off vs Home Def
        add(ShareStatBox(
            title = "${matchup.awayTeam.abbreviation} Off vs ${matchup.homeTeam.abbreviation} Def",
            leftLabel = "${matchup.awayTeam.abbreviation} Off",
            middleLabel = "vs",
            rightLabel = "${matchup.homeTeam.abbreviation} Def",
            fiveColStats = matchup.comparisons?.awayOffVsHomeDef?.mapNotNull { (_, stat) ->
                val isPct = stat.offLabel.contains("%")
                val offValue = stat.offense.value?.let { if (isPct) (it * 100).formatStat(1) + "%" else it.formatStat(2) } ?: return@mapNotNull null
                val defValue = stat.defense.value?.let { if (isPct) (it * 100).formatStat(1) + "%" else it.formatStat(2) } ?: return@mapNotNull null

                ShareFiveColStat(
                    leftValue = offValue,
                    leftRank = stat.offense.rank,
                    leftRankDisplay = stat.offense.rankDisplay,
                    centerText = stat.offLabel,
                    rightValue = defValue,
                    rightRank = stat.defense.rank,
                    rightRankDisplay = stat.defense.rankDisplay,
                    advantage = stat.advantage ?: 0
                )
            }?.take(9) ?: emptyList()
        ))

        // Box 4: Home Off vs Away Def
        add(ShareStatBox(
            title = "${matchup.homeTeam.abbreviation} Off vs ${matchup.awayTeam.abbreviation} Def",
            leftLabel = "${matchup.homeTeam.abbreviation} Off",
            middleLabel = "vs",
            rightLabel = "${matchup.awayTeam.abbreviation} Def",
            leftColor = Team2Color,
            rightColor = Team1Color,
            fiveColStats = matchup.comparisons?.homeOffVsAwayDef?.mapNotNull { (_, stat) ->
                val isPct = stat.offLabel.contains("%")
                val offValue = stat.offense.value?.let { if (isPct) (it * 100).formatStat(1) + "%" else it.formatStat(2) } ?: return@mapNotNull null
                val defValue = stat.defense.value?.let { if (isPct) (it * 100).formatStat(1) + "%" else it.formatStat(2) } ?: return@mapNotNull null

                ShareFiveColStat(
                    leftValue = offValue,
                    leftRank = stat.offense.rank,
                    leftRankDisplay = stat.offense.rankDisplay,
                    centerText = stat.offLabel,
                    rightValue = defValue,
                    rightRank = stat.defense.rank,
                    rightRankDisplay = stat.defense.rankDisplay,
                    advantage = stat.advantage ?: 0
                )
            }?.take(9) ?: emptyList()
        ))

        // Box 5: Key Player #1
        if (matchup.awayPlayers.isNotEmpty() && matchup.homePlayers.isNotEmpty()) {
            val awayPlayer = matchup.awayPlayers[0]
            val homePlayer = matchup.homePlayers[0]

            add(ShareStatBox(
                title = "${awayPlayer.name} vs ${homePlayer.name}",
                leftLabel = awayPlayer.name,
                middleLabel = "vs",
                rightLabel = homePlayer.name,
                fiveColStats = listOfNotNull(
                    ShareFiveColStat(
                        leftValue = awayPlayer.goals.value?.toInt()?.toString() ?: "-",
                        leftRank = awayPlayer.goals.rank,
                        leftRankDisplay = awayPlayer.goals.rankDisplay,
                        centerText = "Goals",
                        rightValue = homePlayer.goals.value?.toInt()?.toString() ?: "-",
                        rightRank = homePlayer.goals.rank,
                        rightRankDisplay = homePlayer.goals.rankDisplay,
                        advantage = 0,
                        usePlayerRanks = true
                    ),
                    ShareFiveColStat(
                        leftValue = awayPlayer.assists.value?.toInt()?.toString() ?: "-",
                        leftRank = awayPlayer.assists.rank,
                        leftRankDisplay = awayPlayer.assists.rankDisplay,
                        centerText = "Assists",
                        rightValue = homePlayer.assists.value?.toInt()?.toString() ?: "-",
                        rightRank = homePlayer.assists.rank,
                        rightRankDisplay = homePlayer.assists.rankDisplay,
                        advantage = 0,
                        usePlayerRanks = true
                    ),
                    ShareFiveColStat(
                        leftValue = awayPlayer.points.value?.toInt()?.toString() ?: "-",
                        leftRank = awayPlayer.points.rank,
                        leftRankDisplay = awayPlayer.points.rankDisplay,
                        centerText = "Points",
                        rightValue = homePlayer.points.value?.toInt()?.toString() ?: "-",
                        rightRank = homePlayer.points.rank,
                        rightRankDisplay = homePlayer.points.rankDisplay,
                        advantage = 0,
                        usePlayerRanks = true
                    ),
                    ShareFiveColStat(
                        leftValue = awayPlayer.plusMinus.value?.let { if (it >= 0) "+${it.toInt()}" else it.toInt().toString() } ?: "-",
                        leftRank = awayPlayer.plusMinus.rank,
                        leftRankDisplay = awayPlayer.plusMinus.rankDisplay,
                        centerText = "+/-",
                        rightValue = homePlayer.plusMinus.value?.let { if (it >= 0) "+${it.toInt()}" else it.toInt().toString() } ?: "-",
                        rightRank = homePlayer.plusMinus.rank,
                        rightRankDisplay = homePlayer.plusMinus.rankDisplay,
                        advantage = 0,
                        usePlayerRanks = true
                    ),
                    ShareFiveColStat(
                        leftValue = awayPlayer.pointsPerGame.value?.formatStat(2) ?: "-",
                        leftRank = awayPlayer.pointsPerGame.rank,
                        leftRankDisplay = awayPlayer.pointsPerGame.rankDisplay,
                        centerText = "Pts/Game",
                        rightValue = homePlayer.pointsPerGame.value?.formatStat(2) ?: "-",
                        rightRank = homePlayer.pointsPerGame.rank,
                        rightRankDisplay = homePlayer.pointsPerGame.rankDisplay,
                        advantage = 0,
                        usePlayerRanks = true
                    ),
                    ShareFiveColStat(
                        leftValue = awayPlayer.gamesPlayed.value?.toInt()?.toString() ?: "-",
                        leftRank = awayPlayer.gamesPlayed.rank,
                        leftRankDisplay = awayPlayer.gamesPlayed.rankDisplay,
                        centerText = "Games",
                        rightValue = homePlayer.gamesPlayed.value?.toInt()?.toString() ?: "-",
                        rightRank = homePlayer.gamesPlayed.rank,
                        rightRankDisplay = homePlayer.gamesPlayed.rankDisplay,
                        advantage = 0,
                        usePlayerRanks = true
                    )
                ).take(9)
            ))
        }

        // Box 6: Recent Trend
        val awayTrend = parseNHLMonthTrend(matchup.awayTeam.stats)
        val homeTrend = parseNHLMonthTrend(matchup.homeTeam.stats)
        val rankAdv = { a: Int?, h: Int? ->
            if (a != null && h != null) when {
                a < h -> -1; h < a -> 1; else -> 0
            } else 0
        }
        add(ShareStatBox(
            title = "Recent Trend (Last 10 Weeks)",
            fiveColStats = listOfNotNull(
                ShareFiveColStat(
                    leftValue = awayTrend?.let { "${it.wins}-${it.losses}" } ?: "-",
                    leftRank = awayTrend?.recordRank,
                    leftRankDisplay = awayTrend?.recordRankDisplay,
                    centerText = "Record",
                    rightValue = homeTrend?.let { "${it.wins}-${it.losses}" } ?: "-",
                    rightRank = homeTrend?.recordRank,
                    rightRankDisplay = homeTrend?.recordRankDisplay,
                    advantage = rankAdv(awayTrend?.recordRank, homeTrend?.recordRank)
                ),
                ShareFiveColStat(
                    leftValue = awayTrend?.goalsFor?.formatStat(2) ?: "-",
                    leftRank = awayTrend?.goalsForRank,
                    leftRankDisplay = awayTrend?.goalsForRankDisplay,
                    centerText = "GF/G",
                    rightValue = homeTrend?.goalsFor?.formatStat(2) ?: "-",
                    rightRank = homeTrend?.goalsForRank,
                    rightRankDisplay = homeTrend?.goalsForRankDisplay,
                    advantage = rankAdv(awayTrend?.goalsForRank, homeTrend?.goalsForRank)
                ),
                ShareFiveColStat(
                    leftValue = awayTrend?.goalsAgainst?.formatStat(2) ?: "-",
                    leftRank = awayTrend?.goalsAgainstRank,
                    leftRankDisplay = awayTrend?.goalsAgainstRankDisplay,
                    centerText = "GA/G",
                    rightValue = homeTrend?.goalsAgainst?.formatStat(2) ?: "-",
                    rightRank = homeTrend?.goalsAgainstRank,
                    rightRankDisplay = homeTrend?.goalsAgainstRankDisplay,
                    advantage = rankAdv(awayTrend?.goalsAgainstRank, homeTrend?.goalsAgainstRank)
                ),
                ShareFiveColStat(
                    leftValue = awayTrend?.goalDiff?.let {
                        val sign = if (it > 0) "+" else ""
                        "$sign${it.formatStat(2)}"
                    } ?: "-",
                    leftRank = awayTrend?.goalDiffRank,
                    leftRankDisplay = awayTrend?.goalDiffRankDisplay,
                    centerText = "Goal Diff/G",
                    rightValue = homeTrend?.goalDiff?.let {
                        val sign = if (it > 0) "+" else ""
                        "$sign${it.formatStat(2)}"
                    } ?: "-",
                    rightRank = homeTrend?.goalDiffRank,
                    rightRankDisplay = homeTrend?.goalDiffRankDisplay,
                    advantage = rankAdv(awayTrend?.goalDiffRank, homeTrend?.goalDiffRank)
                ),
                if (awayTrend?.xgfPct != null || homeTrend?.xgfPct != null) {
                    ShareFiveColStat(
                        leftValue = awayTrend?.xgfPct.formatPct(1),
                        leftRank = awayTrend?.xgfPctRank,
                        leftRankDisplay = awayTrend?.xgfPctRankDisplay,
                        centerText = "xG% (5v5)",
                        rightValue = homeTrend?.xgfPct.formatPct(1),
                        rightRank = homeTrend?.xgfPctRank,
                        rightRankDisplay = homeTrend?.xgfPctRankDisplay,
                        advantage = rankAdv(awayTrend?.xgfPctRank, homeTrend?.xgfPctRank)
                    )
                } else null
            ).take(9)
        ))

        // Fill to exactly 6 boxes if needed
        while (size < 6) {
            add(ShareStatBox(title = "", fiveColStats = emptyList()))
        }
    }.take(6)
}

/**
 * NHL Post-game share image - uses same pattern as NBA but shorter
 */
@Composable
private fun NHLPostGameShareImage(
    matchup: NHLMatchup,
    modifier: Modifier = Modifier
) {
    val results = matchup.results ?: return
    val finalScore = results.finalScore
    val awayBox = results.teamBoxScore?.away
    val homeBox = results.teamBoxScore?.home
    val vsAvg = results.vsSeasonAvg

    val margin = kotlin.math.abs(finalScore.away - finalScore.home)
    val awayWon = !finalScore.homeWon
    val winner = if (finalScore.homeWon) matchup.homeTeam.abbreviation else matchup.awayTeam.abbreviation

    // Detect dark mode and use pure black/white
    val bg = MaterialTheme.colorScheme.background
    val isDark = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryTextColor = if (isDark) Color.LightGray else Color.DarkGray

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Score row: Away left, margin center, Home right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Away team and score (left)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = matchup.awayTeam.abbreviation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = if (awayWon) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = finalScore.away.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = if (awayWon) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Margin with winning team (center)
            Text(
                text = "$winner +$margin",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor
            )

            // Home team and score (right)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = finalScore.home.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = if (finalScore.homeWon) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = matchup.homeTeam.abbreviation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = if (finalScore.homeWon) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        // Location - full info
        matchup.location?.let { loc ->
            val locationText = loc.fullLocation ?: buildString {
                loc.stadium?.let { append(it) }
                if (loc.city != null || loc.state != null) {
                    if (isNotEmpty()) append(" - ")
                    loc.city?.let { append(it) }
                    loc.state?.let {
                        if (loc.city != null) append(", ")
                        append(it)
                    }
                }
            }
            if (locationText.isNotEmpty()) {
                Text(
                    text = locationText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Box score - same format as NBA
        if (awayBox != null && homeBox != null) {
            // Table header
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = matchup.awayTeam.abbreviation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "STAT (vs avg)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1.2f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = matchup.homeTeam.abbreviation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // All NHL box score stats with differentials (same pattern as NBA CompactStatRow)
            NHLCompactStatRow(awayBox.goals?.toString() ?: "-", "GOALS", homeBox.goals?.toString() ?: "-", vsAvg?.away?.goals?.difference, vsAvg?.home?.goals?.difference, awayBox.goals?.toDouble(), homeBox.goals?.toDouble(), true)
            NHLCompactStatRow(awayBox.sog?.toString() ?: "-", "SOG", homeBox.sog?.toString() ?: "-", vsAvg?.away?.shots?.difference, vsAvg?.home?.shots?.difference, awayBox.sog?.toDouble(), homeBox.sog?.toDouble(), true)
            NHLCompactStatRow(awayBox.powerPlayGoals?.toString() ?: "-", "PP GOALS", homeBox.powerPlayGoals?.toString() ?: "-", vsAvg?.away?.ppGoals?.difference, vsAvg?.home?.ppGoals?.difference, awayBox.powerPlayGoals?.toDouble(), homeBox.powerPlayGoals?.toDouble(), true)
            NHLCompactStatRow(awayBox.hits?.toString() ?: "-", "HITS", homeBox.hits?.toString() ?: "-", vsAvg?.away?.hits?.difference, vsAvg?.home?.hits?.difference, awayBox.hits?.toDouble(), homeBox.hits?.toDouble(), true)
            NHLCompactStatRow(awayBox.blocks?.toString() ?: "-", "BLOCKS", homeBox.blocks?.toString() ?: "-", vsAvg?.away?.blocks?.difference, vsAvg?.home?.blocks?.difference, awayBox.blocks?.toDouble(), homeBox.blocks?.toDouble(), true)
            NHLCompactStatRow(awayBox.pim?.toString() ?: "-", "PIM", homeBox.pim?.toString() ?: "-", vsAvg?.away?.pim?.difference, vsAvg?.home?.pim?.difference, awayBox.pim?.toDouble(), homeBox.pim?.toDouble(), false)
            NHLCompactStatRow(awayBox.takeaways?.toString() ?: "-", "TAKEAWAYS", homeBox.takeaways?.toString() ?: "-", vsAvg?.away?.takeaways?.difference, vsAvg?.home?.takeaways?.difference, awayBox.takeaways?.toDouble(), homeBox.takeaways?.toDouble(), true)
            NHLCompactStatRow(awayBox.giveaways?.toString() ?: "-", "GIVEAWAYS", homeBox.giveaways?.toString() ?: "-", vsAvg?.away?.giveaways?.difference, vsAvg?.home?.giveaways?.difference, awayBox.giveaways?.toDouble(), homeBox.giveaways?.toDouble(), false)
            NHLCompactStatRow(awayBox.faceoffWinPct?.let { (it * 100).formatStat(1) + "%" } ?: "-", "FO%", homeBox.faceoffWinPct?.let { (it * 100).formatStat(1) + "%" } ?: "-", pctDiffToDisplay(vsAvg?.away?.faceoffPct), pctDiffToDisplay(vsAvg?.home?.faceoffPct), awayBox.faceoffWinPct, homeBox.faceoffWinPct, true)
            NHLCompactStatRow(awayBox.savePct?.let { (it * 100).formatStat(1) + "%" } ?: "-", "SV%", homeBox.savePct?.let { (it * 100).formatStat(1) + "%" } ?: "-", pctDiffToDisplay(vsAvg?.away?.savePct), pctDiffToDisplay(vsAvg?.home?.savePct), awayBox.savePct, homeBox.savePct, true)
            if (awayBox.xgf != null || homeBox.xgf != null) {
                NHLCompactStatRow(awayBox.xgf?.formatStat(2) ?: "-", "xGF", homeBox.xgf?.formatStat(2) ?: "-", null, null, awayBox.xgf, homeBox.xgf, true)
            }
            if (awayBox.xga != null || homeBox.xga != null) {
                NHLCompactStatRow(awayBox.xga?.formatStat(2) ?: "-", "xGA", homeBox.xga?.formatStat(2) ?: "-", null, null, awayBox.xga, homeBox.xga, false)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Source footer
        Text(
            text = "fbrk.app  •  NHL API",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/**
 * Compact stat row for NHL box score - same pattern as NBA CompactStatRow
 */
@Composable
private fun NHLCompactStatRow(
    awayValue: String,
    label: String,
    homeValue: String,
    awayDiff: Double? = null,
    homeDiff: Double? = null,
    awayRaw: Double? = null,
    homeRaw: Double? = null,
    higherIsBetter: Boolean = true
) {
    // Determine edge
    val awayHasEdge = if (awayRaw != null && homeRaw != null) {
        if (higherIsBetter) awayRaw > homeRaw else awayRaw < homeRaw
    } else false
    val homeHasEdge = if (awayRaw != null && homeRaw != null) {
        if (higherIsBetter) homeRaw > awayRaw else homeRaw < awayRaw
    } else false

    // Detect dark mode and use pure black/white
    val bg = MaterialTheme.colorScheme.background
    val isDark = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryTextColor = if (isDark) Color.LightGray else Color.DarkGray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Away: edge indicator column
        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            if (awayHasEdge) {
                Text(text = "◀", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50), maxLines = 1)
            }
        }

        // Away: value column
        Text(
            text = awayValue,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = if (awayHasEdge) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Away: diff column
        Text(
            text = awayDiff?.let { diff -> "(${if (diff >= 0) "+" else ""}${diff.formatStat(1)})" } ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = if ((awayDiff ?: 0.0) >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
            maxLines = 1,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.Start
        )

        // Label column
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = secondaryTextColor,
            maxLines = 1,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        // Home: diff column
        Text(
            text = homeDiff?.let { diff -> "(${if (diff >= 0) "+" else ""}${diff.formatStat(1)})" } ?: "",
            style = MaterialTheme.typography.labelSmall,
            color = if ((homeDiff ?: 0.0) >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
            maxLines = 1,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Home: value column
        Text(
            text = homeValue,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = if (homeHasEdge) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.Start
        )

        // Home: edge indicator column
        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            if (homeHasEdge) {
                Text(text = "▶", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50), maxLines = 1)
            }
        }
    }
}

/**
 * NHL Record section showing W-L-OTL and points on line 1, conference rank on line 2
 */
@Composable
private fun NHLRecordSection(
    awayTeam: String,
    homeTeam: String,
    awayWins: Int?,
    awayLosses: Int?,
    awayOtLosses: Int?,
    awayPoints: Int?,
    awayConferenceRank: Int?,
    awayConference: String?,
    homeWins: Int?,
    homeLosses: Int?,
    homeOtLosses: Int?,
    homePoints: Int?,
    homeConferenceRank: Int?,
    homeConference: String?
) {
    fun formatConference(conf: String?): String {
        return when (conf?.lowercase()) {
            "east", "eastern" -> "Eastern"
            "west", "western" -> "Western"
            else -> "Conference"
        }
    }

    // Line 1: Record and points
    val awayRecordLine = if (awayWins != null && awayLosses != null) {
        val otl = awayOtLosses ?: 0
        val pts = awayPoints?.let { " ($it pts)" } ?: ""
        "$awayWins-$awayLosses-$otl$pts"
    } else null

    val homeRecordLine = if (homeWins != null && homeLosses != null) {
        val otl = homeOtLosses ?: 0
        val pts = homePoints?.let { " ($it pts)" } ?: ""
        "$homeWins-$homeLosses-$otl$pts"
    } else null

    // Line 2: Conference rank
    val awayConfRank = awayConferenceRank?.let { "${formatOrdinal(it)} / ${formatConference(awayConference)}" }
    val homeConfRank = homeConferenceRank?.let { "${formatOrdinal(it)} / ${formatConference(homeConference)}" }

    if (awayRecordLine != null || homeRecordLine != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Away team column (left aligned)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (awayConferenceRank != null) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = getNHLConferenceRankColor(awayConferenceRank),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = awayRecordLine ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
                if (awayConfRank != null) {
                    Text(
                        text = awayConfRank,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.padding(start = if (awayConferenceRank != null) 10.dp else 0.dp)
                    )
                }
            }

            // Home team column (right aligned)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = homeRecordLine ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                    if (homeConferenceRank != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = getNHLConferenceRankColor(homeConferenceRank),
                                    shape = CircleShape
                                )
                        )
                    }
                }
                if (homeConfRank != null) {
                    Text(
                        text = homeConfRank,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.padding(end = if (homeConferenceRank != null) 10.dp else 0.dp)
                    )
                }
            }
        }
    }
}

/**
 * Get color for NHL conference rank (16 teams per conference)
 */
private fun getNHLConferenceRankColor(rank: Int): Color {
    return when {
        rank <= 4 -> Color(0xFF4CAF50)   // Playoff seeded (1-4) - Green
        rank <= 8 -> Color(0xFF8BC34A)   // Wild card contention (5-8) - Light green
        rank <= 12 -> Color(0xFFFF8C00)  // Middle of pack (9-12) - Orange
        else -> Color(0xFFF44336)        // Bottom of conference (13-16) - Red
    }
}

/**
 * NHL Matchup content
 */
@Composable
private fun NHLMatchupContent(
    matchup: NHLMatchup,
    viewSelection: Int,
    onViewSelectionChange: (Int) -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(3.dp))

        // Betting Odds Section
        matchup.odds?.let { odds ->
            val hasOdds = odds.spread != null || odds.overUnder != null ||
                         odds.homeMoneyline != null || odds.awayMoneyline != null

            if (hasOdds) {
                NHLSectionHeader("Betting Odds")

                // Puck Line (spread)
                odds.spread?.let { spread ->
                    val homeSpread = if (spread > 0) "+$spread" else spread.toString()
                    val awaySpread = if (spread < 0) "+${-spread}" else (-spread).toString()
                    ThreeColumnRow(
                        leftText = awaySpread,
                        centerText = "Puck Line",
                        rightText = homeSpread
                    )
                }

                // Moneyline
                if (odds.homeMoneyline != null || odds.awayMoneyline != null) {
                    ThreeColumnRow(
                        leftText = odds.awayMoneyline?.toString() ?: "",
                        centerText = "Moneyline",
                        rightText = odds.homeMoneyline?.toString() ?: ""
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

        // Show completed game results or month trend
        if (matchup.gameCompleted && matchup.results != null) {
            NHLCompletedGameSection(matchup)
        } else {
            NHLMonthTrendSection(
                awayTeam = matchup.awayTeam.abbreviation,
                homeTeam = matchup.homeTeam.abbreviation,
                awayStats = matchup.awayTeam.stats,
                homeStats = matchup.homeTeam.stats
            )
        }

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
            0 -> NHLTeamStatsView(matchup)
            1 -> NHLVersusView(matchup, isAwayOffense = true)
            2 -> NHLVersusView(matchup, isAwayOffense = false)
        }

        // Player Stats Section
        Spacer(modifier = Modifier.height(12.dp))
        NHLPlayerStatsView(matchup)
    }
}

/**
 * NHL Team stats view
 */
@Composable
private fun NHLTeamStatsView(matchup: NHLMatchup) {
    NHLSectionHeader("Team Stats")

    Column {
        // Offensive Stats
        NHLSubsectionHeader("Offensive Stats")
        Spacer(modifier = Modifier.height(4.dp))

        matchup.comparisons?.sideBySide?.offense?.forEach { (_, stat) ->
            val awayValue = stat.away.value
            val awayRank = stat.away.rank
            val awayRankDisplay = stat.away.rankDisplay
            val homeValue = stat.home.value
            val homeRank = stat.home.rank
            val homeRankDisplay = stat.home.rankDisplay
            val label = stat.label

            val advantage = if (awayRank != null && homeRank != null) {
                when {
                    awayRank < homeRank -> -1
                    awayRank > homeRank -> 1
                    else -> 0
                }
            } else 0

            val isPct = label.contains("%")
            val awayText = if (isPct) awayValue.formatPct(1) else awayValue?.formatStat(2) ?: "-"
            val homeText = if (isPct) homeValue.formatPct(1) else homeValue?.formatStat(2) ?: "-"

            FiveColumnRowWithRanks(
                leftValue = awayText,
                leftRank = awayRank,
                leftRankDisplay = awayRankDisplay,
                centerText = label,
                rightValue = homeText,
                rightRank = homeRank,
                rightRankDisplay = homeRankDisplay,
                advantage = advantage,
                useNBARanks = false,
                useNHLRanks = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Defensive Stats
        NHLSubsectionHeader("Defensive Stats")
        Spacer(modifier = Modifier.height(4.dp))

        matchup.comparisons?.sideBySide?.defense?.forEach { (_, stat) ->
            val awayValue = stat.away.value
            val awayRank = stat.away.rank
            val awayRankDisplay = stat.away.rankDisplay
            val homeValue = stat.home.value
            val homeRank = stat.home.rank
            val homeRankDisplay = stat.home.rankDisplay
            val label = stat.label

            val advantage = if (awayRank != null && homeRank != null) {
                when {
                    awayRank < homeRank -> -1
                    awayRank > homeRank -> 1
                    else -> 0
                }
            } else 0

            val isPct = label.contains("%")
            val awayText = if (isPct) awayValue.formatPct(1) else awayValue?.formatStat(2) ?: "-"
            val homeText = if (isPct) homeValue.formatPct(1) else homeValue?.formatStat(2) ?: "-"

            FiveColumnRowWithRanks(
                leftValue = awayText,
                leftRank = awayRank,
                leftRankDisplay = awayRankDisplay,
                centerText = label,
                rightValue = homeText,
                rightRank = homeRank,
                rightRankDisplay = homeRankDisplay,
                advantage = advantage,
                useNBARanks = false,
                useNHLRanks = true
            )
        }
    }
}

/**
 * NHL Versus view
 */
@Composable
private fun NHLVersusView(
    matchup: NHLMatchup,
    isAwayOffense: Boolean
) {
    val offenseTeam = if (isAwayOffense) matchup.awayTeam else matchup.homeTeam
    val defenseTeam = if (isAwayOffense) matchup.homeTeam else matchup.awayTeam

    NHLSectionHeader("${offenseTeam.abbreviation} Offense vs ${defenseTeam.abbreviation} Defense")
    Spacer(modifier = Modifier.height(4.dp))

    matchup.comparisons?.let { comparisons ->
        val currentComparison = if (isAwayOffense) {
            comparisons.awayOffVsHomeDef
        } else {
            comparisons.homeOffVsAwayDef
        }

        Column {
            currentComparison.forEach { (_, stat) ->
                val advantage = stat.advantage ?: 0
                val offLabel = stat.offLabel

                val offValue = stat.offense.value
                val offRank = stat.offense.rank
                val offRankDisplay = stat.offense.rankDisplay

                val defValue = stat.defense.value
                val defRank = stat.defense.rank
                val defRankDisplay = stat.defense.rankDisplay

                if (offValue != null && defValue != null) {
                    val isPct = offLabel.contains("%")
                    FiveColumnRowWithRanks(
                        leftValue = if (isPct) (offValue * 100).formatStat(1) + "%" else offValue.formatStat(2),
                        leftRank = offRank,
                        leftRankDisplay = offRankDisplay,
                        centerText = offLabel,
                        rightValue = if (isPct) (defValue * 100).formatStat(1) + "%" else defValue.formatStat(2),
                        rightRank = defRank,
                        rightRankDisplay = defRankDisplay,
                        advantage = advantage,
                        useNBARanks = false,
                        useNHLRanks = true
                    )
                }
            }
        }
    }
}

/**
 * NHL Player stats view using shared component
 */
@Composable
private fun NHLPlayerStatsView(matchup: NHLMatchup) {
    NHLSectionHeader("Key Players")

    val awayPlayers = matchup.awayPlayers
    val homePlayers = matchup.homePlayers

    val playerCount = minOf(awayPlayers.size, homePlayers.size)

    if (playerCount == 0) return

    // Configure which stats to display for NHL players
    val statsConfig = listOf(
        PlayerStatConfig<NHLPlayerInfo>(
            label = "Games",
            decimals = 0,
            accessor = { player -> PlayerStatValue(player.gamesPlayed.value, player.gamesPlayed.rank, player.gamesPlayed.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "Goals",
            decimals = 0,
            accessor = { player -> PlayerStatValue(player.goals.value, player.goals.rank, player.goals.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "Assists",
            decimals = 0,
            accessor = { player -> PlayerStatValue(player.assists.value, player.assists.rank, player.assists.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "Points",
            decimals = 0,
            accessor = { player -> PlayerStatValue(player.points.value, player.points.rank, player.points.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "+/-",
            decimals = 0,
            accessor = { player -> PlayerStatValue(player.plusMinus.value, player.plusMinus.rank, player.plusMinus.rankDisplay) }
        ),
        PlayerStatConfig(
            label = "Pts/G",
            decimals = 2,
            accessor = { player -> PlayerStatValue(player.pointsPerGame.value, player.pointsPerGame.rank, player.pointsPerGame.rankDisplay) }
        )
    )

    for (i in 0 until playerCount) {
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
            usePlayerRanks = true
        )
    }
}

/**
 * NHL Month Trend section
 */
@Composable
private fun NHLMonthTrendSection(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject,
    homeStats: JsonObject
) {
    val awayTrend = parseNHLMonthTrend(awayStats)
    val homeTrend = parseNHLMonthTrend(homeStats)

    if (awayTrend == null && homeTrend == null) {
        return
    }

    NHLSectionHeader("Recent Trend (Last 10 Weeks)")
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
        advantage = recordAdvantage,
        useNBARanks = false,
        useNHLRanks = true
    )

    // Goals For per Game
    val gfAdvantage = when {
        awayTrend?.goalsForRank != null && homeTrend?.goalsForRank != null ->
            when {
                awayTrend.goalsForRank < homeTrend.goalsForRank -> -1
                homeTrend.goalsForRank < awayTrend.goalsForRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.goalsFor?.formatStat(2) ?: "-",
        leftRank = awayTrend?.goalsForRank,
        leftRankDisplay = awayTrend?.goalsForRankDisplay,
        centerText = "GF/G",
        rightValue = homeTrend?.goalsFor?.formatStat(2) ?: "-",
        rightRank = homeTrend?.goalsForRank,
        rightRankDisplay = homeTrend?.goalsForRankDisplay,
        advantage = gfAdvantage,
        useNBARanks = false,
        useNHLRanks = true
    )

    // Goals Against per Game (lower is better)
    val gaAdvantage = when {
        awayTrend?.goalsAgainstRank != null && homeTrend?.goalsAgainstRank != null ->
            when {
                awayTrend.goalsAgainstRank < homeTrend.goalsAgainstRank -> -1
                homeTrend.goalsAgainstRank < awayTrend.goalsAgainstRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.goalsAgainst?.formatStat(2) ?: "-",
        leftRank = awayTrend?.goalsAgainstRank,
        leftRankDisplay = awayTrend?.goalsAgainstRankDisplay,
        centerText = "GA/G",
        rightValue = homeTrend?.goalsAgainst?.formatStat(2) ?: "-",
        rightRank = homeTrend?.goalsAgainstRank,
        rightRankDisplay = homeTrend?.goalsAgainstRankDisplay,
        advantage = gaAdvantage,
        useNBARanks = false,
        useNHLRanks = true
    )

    // Goal Differential per Game
    val gdAdvantage = when {
        awayTrend?.goalDiffRank != null && homeTrend?.goalDiffRank != null ->
            when {
                awayTrend.goalDiffRank < homeTrend.goalDiffRank -> -1
                homeTrend.goalDiffRank < awayTrend.goalDiffRank -> 1
                else -> 0
            }
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.goalDiff?.let {
            val sign = if (it > 0) "+" else ""
            "$sign${it.formatStat(2)}"
        } ?: "-",
        leftRank = awayTrend?.goalDiffRank,
        leftRankDisplay = awayTrend?.goalDiffRankDisplay,
        centerText = "Goal Diff/G",
        rightValue = homeTrend?.goalDiff?.let {
            val sign = if (it > 0) "+" else ""
            "$sign${it.formatStat(2)}"
        } ?: "-",
        rightRank = homeTrend?.goalDiffRank,
        rightRankDisplay = homeTrend?.goalDiffRankDisplay,
        advantage = gdAdvantage,
        useNBARanks = false,
        useNHLRanks = true
    )

    // xG% (5v5)
    if (awayTrend?.xgfPct != null || homeTrend?.xgfPct != null) {
        val xgAdvantage = when {
            awayTrend?.xgfPctRank != null && homeTrend?.xgfPctRank != null ->
                when {
                    awayTrend.xgfPctRank < homeTrend.xgfPctRank -> -1
                    homeTrend.xgfPctRank < awayTrend.xgfPctRank -> 1
                    else -> 0
                }
            else -> 0
        }
        FiveColumnRowWithRanks(
            leftValue = awayTrend?.xgfPct.formatPct(1),
            leftRank = awayTrend?.xgfPctRank,
            leftRankDisplay = awayTrend?.xgfPctRankDisplay,
            centerText = "xG% (5v5)",
            rightValue = homeTrend?.xgfPct.formatPct(1),
            rightRank = homeTrend?.xgfPctRank,
            rightRankDisplay = homeTrend?.xgfPctRankDisplay,
            advantage = xgAdvantage,
            useNBARanks = false,
            useNHLRanks = true
        )
    }
}

/**
 * NHL Month Trend data class
 */
private data class NHLMonthTrend(
    val wins: Int,
    val losses: Int,
    val recordRank: Int?,
    val recordRankDisplay: String?,
    val goalsFor: Double?,
    val goalsForRank: Int?,
    val goalsForRankDisplay: String?,
    val goalsAgainst: Double?,
    val goalsAgainstRank: Int?,
    val goalsAgainstRankDisplay: String?,
    val goalDiff: Double?,
    val goalDiffRank: Int?,
    val goalDiffRankDisplay: String?,
    val xgfPct: Double?,
    val xgfPctRank: Int?,
    val xgfPctRankDisplay: String?
)

/**
 * Parse NHL month trend from stats JSON
 */
private fun parseNHLMonthTrend(stats: JsonObject): NHLMonthTrend? {
    val monthTrend = stats["monthTrend"] as? JsonObject ?: return null

    val record = monthTrend["record"] as? JsonObject
    val wins = (record?.get("wins") as? JsonPrimitive)?.intOrNull ?: 0
    val losses = (record?.get("losses") as? JsonPrimitive)?.intOrNull ?: 0
    val recordRank = (record?.get("rank") as? JsonPrimitive)?.intOrNull
    val recordRankDisplay = (record?.get("rankDisplay") as? JsonPrimitive)?.content

    val goalsForObj = monthTrend["goalsPerGame"] as? JsonObject
    val goalsFor = (goalsForObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val goalsForRank = (goalsForObj?.get("rank") as? JsonPrimitive)?.intOrNull
    val goalsForRankDisplay = (goalsForObj?.get("rankDisplay") as? JsonPrimitive)?.content

    val goalsAgainstObj = monthTrend["goalsAgainstPerGame"] as? JsonObject
    val goalsAgainst = (goalsAgainstObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val goalsAgainstRank = (goalsAgainstObj?.get("rank") as? JsonPrimitive)?.intOrNull
    val goalsAgainstRankDisplay = (goalsAgainstObj?.get("rankDisplay") as? JsonPrimitive)?.content

    val goalDiffObj = monthTrend["goalDiffPerGame"] as? JsonObject
    val goalDiff = (goalDiffObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val goalDiffRank = (goalDiffObj?.get("rank") as? JsonPrimitive)?.intOrNull
    val goalDiffRankDisplay = (goalDiffObj?.get("rankDisplay") as? JsonPrimitive)?.content

    val xgfPctObj = monthTrend["xgfPct"] as? JsonObject
    val xgfPct = (xgfPctObj?.get("value") as? JsonPrimitive)?.doubleOrNull
    val xgfPctRank = (xgfPctObj?.get("rank") as? JsonPrimitive)?.intOrNull
    val xgfPctRankDisplay = (xgfPctObj?.get("rankDisplay") as? JsonPrimitive)?.content

    return NHLMonthTrend(
        wins = wins,
        losses = losses,
        recordRank = recordRank,
        recordRankDisplay = recordRankDisplay,
        goalsFor = goalsFor,
        goalsForRank = goalsForRank,
        goalsForRankDisplay = goalsForRankDisplay,
        goalsAgainst = goalsAgainst,
        goalsAgainstRank = goalsAgainstRank,
        goalsAgainstRankDisplay = goalsAgainstRankDisplay,
        goalDiff = goalDiff,
        goalDiffRank = goalDiffRank,
        goalDiffRankDisplay = goalDiffRankDisplay,
        xgfPct = xgfPct,
        xgfPctRank = xgfPctRank,
        xgfPctRankDisplay = xgfPctRankDisplay
    )
}

/**
 * NHL Completed game section
 */
@Composable
private fun NHLCompletedGameSection(matchup: NHLMatchup) {
    val results = matchup.results ?: return
    val teamBoxScore = results.teamBoxScore
    val vsSeasonAvg = results.vsSeasonAvg

    if (teamBoxScore != null) {
        NHLSectionHeader("Box Score (vs Season Avg)")
        Spacer(modifier = Modifier.height(4.dp))

        val awayBox = teamBoxScore.away
        val homeBox = teamBoxScore.home

        // Helper to format difference string (appended after value for left/away side)
        fun formatDiff(diff: Double?): String {
            if (diff == null) return ""
            val prefix = if (diff >= 0) "+" else ""
            return " (${prefix}${diff.formatStat(1)})"
        }

        // Helper to format difference string (prepended before value for right/home side)
        fun formatDiffPrefix(diff: Double?): String {
            if (diff == null) return ""
            val prefix = if (diff >= 0) "+" else ""
            return "(${prefix}${diff.formatStat(1)}) "
        }

        // Goals with vs Season Avg
        if (awayBox?.goals != null || homeBox?.goals != null) {
            val awayVal = awayBox?.goals ?: 0
            val homeVal = homeBox?.goals ?: 0
            val awayDiff = vsSeasonAvg?.away?.goals?.difference
            val homeDiff = vsSeasonAvg?.home?.goals?.difference
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayDiff)}",
                centerText = "Goals",
                rightText = "${formatDiffPrefix(homeDiff)}$homeVal",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Shots on Goal with vs Season Avg
        if (awayBox?.sog != null || homeBox?.sog != null) {
            val awayVal = awayBox?.sog ?: 0
            val homeVal = homeBox?.sog ?: 0
            val awayDiff = vsSeasonAvg?.away?.shots?.difference
            val homeDiff = vsSeasonAvg?.home?.shots?.difference
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayDiff)}",
                centerText = "SOG",
                rightText = "${formatDiffPrefix(homeDiff)}$homeVal",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Power Play Goals with vs Season Avg
        if (awayBox?.powerPlayGoals != null || homeBox?.powerPlayGoals != null) {
            val awayVal = awayBox?.powerPlayGoals ?: 0
            val homeVal = homeBox?.powerPlayGoals ?: 0
            val awayDiff = vsSeasonAvg?.away?.ppGoals?.difference
            val homeDiff = vsSeasonAvg?.home?.ppGoals?.difference
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayDiff)}",
                centerText = "PP Goals",
                rightText = "${formatDiffPrefix(homeDiff)}$homeVal",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Hits with vs Season Avg
        if (awayBox?.hits != null || homeBox?.hits != null) {
            val awayVal = awayBox?.hits ?: 0
            val homeVal = homeBox?.hits ?: 0
            val awayDiff = vsSeasonAvg?.away?.hits?.difference
            val homeDiff = vsSeasonAvg?.home?.hits?.difference
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayDiff)}",
                centerText = "Hits",
                rightText = "${formatDiffPrefix(homeDiff)}$homeVal",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Blocks with vs Season Avg
        if (awayBox?.blocks != null || homeBox?.blocks != null) {
            val awayVal = awayBox?.blocks ?: 0
            val homeVal = homeBox?.blocks ?: 0
            val awayDiff = vsSeasonAvg?.away?.blocks?.difference
            val homeDiff = vsSeasonAvg?.home?.blocks?.difference
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayDiff)}",
                centerText = "Blocks",
                rightText = "${formatDiffPrefix(homeDiff)}$homeVal",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Penalty Minutes (lower is better) with vs Season Avg
        if (awayBox?.pim != null || homeBox?.pim != null) {
            val awayVal = awayBox?.pim ?: 0
            val homeVal = homeBox?.pim ?: 0
            val awayDiff = vsSeasonAvg?.away?.pim?.difference
            val homeDiff = vsSeasonAvg?.home?.pim?.difference
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayDiff)}",
                centerText = "PIM",
                rightText = "${formatDiffPrefix(homeDiff)}$homeVal",
                advantage = if (awayVal < homeVal) -1 else if (homeVal < awayVal) 1 else 0
            )
        }

        // Takeaways with vs Season Avg
        if (awayBox?.takeaways != null || homeBox?.takeaways != null) {
            val awayVal = awayBox?.takeaways ?: 0
            val homeVal = homeBox?.takeaways ?: 0
            val awayDiff = vsSeasonAvg?.away?.takeaways?.difference
            val homeDiff = vsSeasonAvg?.home?.takeaways?.difference
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayDiff)}",
                centerText = "Takeaways",
                rightText = "${formatDiffPrefix(homeDiff)}$homeVal",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Giveaways (lower is better) with vs Season Avg
        if (awayBox?.giveaways != null || homeBox?.giveaways != null) {
            val awayVal = awayBox?.giveaways ?: 0
            val homeVal = homeBox?.giveaways ?: 0
            val awayDiff = vsSeasonAvg?.away?.giveaways?.difference
            val homeDiff = vsSeasonAvg?.home?.giveaways?.difference
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayDiff)}",
                centerText = "Giveaways",
                rightText = "${formatDiffPrefix(homeDiff)}$homeVal",
                advantage = if (awayVal < homeVal) -1 else if (homeVal < awayVal) 1 else 0
            )
        }

        // Faceoff Win % with vs Season Avg
        if (awayBox?.faceoffWinPct != null || homeBox?.faceoffWinPct != null) {
            val awayVal = awayBox?.faceoffWinPct ?: 0.0
            val homeVal = homeBox?.faceoffWinPct ?: 0.0
            val awayDiff = pctDiffToDisplay(vsSeasonAvg?.away?.faceoffPct)
            val homeDiff = pctDiffToDisplay(vsSeasonAvg?.home?.faceoffPct)
            ThreeColumnRow(
                leftText = "${(awayVal * 100).formatStat(1)}%${formatDiff(awayDiff)}",
                centerText = "Faceoff %",
                rightText = "${formatDiffPrefix(homeDiff)}${(homeVal * 100).formatStat(1)}%",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Save % with vs Season Avg
        if (awayBox?.savePct != null || homeBox?.savePct != null) {
            val awayVal = awayBox?.savePct ?: 0.0
            val homeVal = homeBox?.savePct ?: 0.0
            val awayDiff = pctDiffToDisplay(vsSeasonAvg?.away?.savePct)
            val homeDiff = pctDiffToDisplay(vsSeasonAvg?.home?.savePct)
            ThreeColumnRow(
                leftText = "${(awayVal * 100).formatStat(1)}%${formatDiff(awayDiff)}",
                centerText = "Save %",
                rightText = "${formatDiffPrefix(homeDiff)}${(homeVal * 100).formatStat(1)}%",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // xGF (5v5) - higher is better
        if (awayBox?.xgf != null || homeBox?.xgf != null) {
            val awayVal = awayBox?.xgf ?: 0.0
            val homeVal = homeBox?.xgf ?: 0.0
            ThreeColumnRow(
                leftText = awayVal.formatStat(2),
                centerText = "xGF (5v5)",
                rightText = homeVal.formatStat(2),
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // xGA (5v5) - lower is better
        if (awayBox?.xga != null || homeBox?.xga != null) {
            val awayVal = awayBox?.xga ?: 0.0
            val homeVal = homeBox?.xga ?: 0.0
            ThreeColumnRow(
                leftText = awayVal.formatStat(2),
                centerText = "xGA (5v5)",
                rightText = homeVal.formatStat(2),
                advantage = if (awayVal < homeVal) -1 else if (homeVal < awayVal) 1 else 0
            )
        }

        // Period scores
        results.periodScores?.let { periods ->
            Spacer(modifier = Modifier.height(8.dp))
            NHLSubsectionHeader("Period Scoring")
            Spacer(modifier = Modifier.height(4.dp))

            periods.forEach { period ->
                val periodLabel = if (period.period <= 3) "Period ${period.period}" else "OT"
                ThreeColumnRow(
                    leftText = period.away.toString(),
                    centerText = periodLabel,
                    rightText = period.home.toString(),
                    advantage = when {
                        period.away > period.home -> -1
                        period.home > period.away -> 1
                        else -> 0
                    }
                )
            }
        }
    }
}

/**
 * NHL Section header
 */
@Composable
private fun NHLSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/**
 * NHL Subsection header
 */
@Composable
private fun NHLSubsectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    )
}

/**
 * Navigation badge for Stats/Charts selection
 */
@Composable
private fun NHLNavigationBadge(
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
 * Format ordinal numbers
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
 * NHL Playoff probability section showing chances for each team
 */
@Composable
private fun NHLPlayoffProbabilitySection(
    awayProb: PlayoffProbability?,
    homeProb: PlayoffProbability?
) {
    // Only show if at least one team has probability data
    if (awayProb != null || homeProb != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Away team probabilities
            NHLPlayoffProbabilityText(
                playoffProb = awayProb?.playoffProb,
                champProb = awayProb?.champProb,
                alignment = TextAlign.Start
            )

            // Home team probabilities
            NHLPlayoffProbabilityText(
                playoffProb = homeProb?.playoffProb,
                champProb = homeProb?.champProb,
                alignment = TextAlign.End
            )
        }
    }
}

/**
 * Convert a 0-1 scale percentage difference to display scale (percentage points).
 * Backend now stores all percentage diffs in 0-1 scale consistently.
 */
private fun pctDiffToDisplay(stat: com.joebad.fastbreak.data.model.NHLStatComparison?): Double? {
    return stat?.difference?.let { it * 100 }
}

/**
 * Format probability value as string
 */
private fun formatNHLProbability(prob: Double?): String {
    return if (prob != null) {
        if (prob >= 99.5) ">99%" else "${prob.toInt()}%"
    } else {
        "-"
    }
}

/**
 * Get color for playoff probability (>60% green shades, lower red shades)
 */
@Composable
private fun getPlayoffProbColor(prob: Double?): Color {
    if (prob == null) return Color.Gray
    val isDarkTheme = isSystemInDarkTheme()
    return when {
        prob >= 90 -> if (isDarkTheme) Color(0xFF1B5E20) else Color(0xFF1B5E20)  // Green - very likely
        prob >= 75 -> if (isDarkTheme) Color(0xFF2E7D32) else Color(0xFF2E7D32)  // Green - likely
        prob >= 60 -> if (isDarkTheme) Color(0xFF388E3C) else Color(0xFF4CAF50)  // Light green - good chances
        prob >= 40 -> Color(0xFFFF8C00)  // Orange - toss up
        prob >= 20 -> Color(0xFFE65100)  // Dark orange - unlikely
        else -> Color(0xFFC62828)        // Red - very unlikely
    }
}

/**
 * Get color for championship probability (>=5% green, lower red shades)
 */
@Composable
private fun getChampProbColor(prob: Double?): Color {
    if (prob == null) return Color.Gray
    val isDarkTheme = isSystemInDarkTheme()
    return when {
        prob >= 20 -> if (isDarkTheme) Color(0xFF1B5E20) else Color(0xFF1B5E20)  // Green - strong contender
        prob >= 10 -> if (isDarkTheme) Color(0xFF2E7D32) else Color(0xFF2E7D32)  // Green - solid contender
        prob >= 5 -> if (isDarkTheme) Color(0xFF388E3C) else Color(0xFF4CAF50)   // Light green - contender
        prob >= 2 -> Color(0xFFFF8C00)   // Orange - outside shot
        prob > 1 -> Color(0xFFE65100)    // Dark orange - long shot (>1%, not >=1%)
        else -> Color(0xFFC62828)        // Red - very unlikely (includes 1% and below)
    }
}

/**
 * Composable for displaying playoff and championship probability
 */
@Composable
private fun NHLPlayoffProbabilityText(
    playoffProb: Double?,
    champProb: Double?,
    alignment: TextAlign
) {
    val playoffColor = getPlayoffProbColor(playoffProb)
    val champColor = getChampProbColor(champProb)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignment == TextAlign.End) Arrangement.End else Arrangement.Start,
        modifier = Modifier.widthIn(min = 80.dp)
    ) {
        Text(
            text = "PO ",
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp),
            fontSize = 10.sp,
            color = textColor
        )
        Box(
            modifier = Modifier
                .background(playoffColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatNHLProbability(playoffProb),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Cup ",
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp),
            fontSize = 10.sp,
            color = textColor
        )
        Box(
            modifier = Modifier
                .background(champColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatNHLProbability(champProb),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 12.sp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

// ============================================================================
// Charts Tab
// ============================================================================

@Composable
private fun NHLChartsTab(
    awayTeam: String,
    homeTeam: String,
    matchup: NHLMatchup,
    source: String? = null,
    onCumXgfShareClick: ((() -> Unit)?) -> Unit = {},
    onXgVsPointsShareClick: ((()-> Unit) -> Unit)? = null
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CumulativeXgfPctChart(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayStats = matchup.awayTeam.stats,
            homeStats = matchup.homeTeam.stats,
            tenthXgfPctByWeek = matchup.tenthXgfPctByWeek,
            leagueCumXgStats = matchup.leagueCumXgStats,
            onShareClick = onCumXgfShareClick
        )

        XgVsPointsPctScatter(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayStats = matchup.awayTeam.stats,
            homeStats = matchup.homeTeam.stats,
            leagueStats = matchup.leagueXgVsPointsStats,
            onShareClick = onXgVsPointsShareClick
        )

        // Source attribution at bottom of scrollable content
        source?.let {
            Text(
                text = "Source: $it",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun CumulativeXgfPctChart(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject,
    homeStats: JsonObject,
    tenthXgfPctByWeek: JsonObject? = null,
    leagueCumXgStats: LeagueCumXgStats? = null,
    onShareClick: ((() -> Unit)?) -> Unit = {}
) {
    Text(
        text = "Cumulative xG% - Last 10 Weeks (5v5)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    val awayDataPoints = parseXgfPctByWeek(awayStats, "cumXgfPctByWeek")
    val homeDataPoints = parseXgfPctByWeek(homeStats, "cumXgfPctByWeek")

    if (awayDataPoints.isEmpty() && homeDataPoints.isEmpty()) {
        Text(
            text = "Cumulative xG% (last 10 weeks) data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val tenthPoints = parseTenthXgfPctByWeek(tenthXgfPctByWeek)
    val referenceLines = if (tenthPoints.isNotEmpty()) {
        listOf(
            HorizontalReferenceLine(
                yValue = tenthPoints,
                color = "#4CAF50",
                label = "#10"
            )
        )
    } else {
        emptyList()
    }

    val series = listOf(
        LineChartSeries(
            label = awayTeam,
            dataPoints = awayDataPoints,
            color = "#2196F3"
        ),
        LineChartSeries(
            label = homeTeam,
            dataPoints = homeDataPoints,
            color = "#FF5722"
        )
    )

    LineChartComponent(
        series = series,
        modifier = Modifier.fillMaxWidth(),
        yAxisTitle = "xG%",
        referenceLines = referenceLines,
        title = "Cumulative xG% (Last 10 Wks) - $awayTeam @ $homeTeam",
        source = "Natural Stat Trick",
        onShareClick = onShareClick,
        customYMin = leagueCumXgStats?.minCumXgPct?.toFloat(),
        customYMax = leagueCumXgStats?.maxCumXgPct?.toFloat()
    )
}

@Composable
private fun XgVsPointsPctScatter(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject,
    homeStats: JsonObject,
    leagueStats: LeagueXgVsPointsStats? = null,
    onShareClick: ((()-> Unit) -> Unit)? = null
) {
    var weekFilter by remember { mutableStateOf(0) }

    // Parse weekly xG% and Points% for each team
    val awayXg = parseWeeklyValues(awayStats, "weeklyXgfPct")
    val homeXg = parseWeeklyValues(homeStats, "weeklyXgfPct")
    val awayPts = parseWeeklyValues(awayStats, "weeklyPointsPct")
    val homePts = parseWeeklyValues(homeStats, "weeklyPointsPct")

    // Build scatter data points — only include weeks that have both xG% and Points%
    val allPoints = mutableListOf<ScatterPlotDataPoint>()

    for (week in 1..10) {
        val axg = awayXg[week]
        val apts = awayPts[week]
        if (axg != null && apts != null) {
            allPoints.add(
                ScatterPlotDataPoint(
                    label = "$awayTeam W$week",
                    x = apts,
                    y = axg,
                    sum = 0.0,
                    teamCode = awayTeam,
                    color = "#2196F3"
                )
            )
        }

        val hxg = homeXg[week]
        val hpts = homePts[week]
        if (hxg != null && hpts != null) {
            allPoints.add(
                ScatterPlotDataPoint(
                    label = "$homeTeam W$week",
                    x = hpts,
                    y = hxg,
                    sum = 0.0,
                    teamCode = homeTeam,
                    color = "#FF5722"
                )
            )
        }
    }

    // Apply week filter
    val filteredPoints = when (weekFilter) {
        1 -> allPoints.filter { pt ->
            val weekNum = pt.label.substringAfter("W").toIntOrNull() ?: 0
            weekNum in 6..10
        }
        2 -> allPoints.filter { pt ->
            val weekNum = pt.label.substringAfter("W").toIntOrNull() ?: 0
            weekNum in 1..5
        }
        else -> allPoints
    }

    Column {
        Text(
            text = "xG% vs Points% by Week (5v5)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Filter badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeamStatsNavBadge(
                    text = "All 10 Wks",
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

        if (filteredPoints.isEmpty()) {
            Text(
                text = "xG% vs Points% data not available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            QuadrantScatterPlot(
                data = filteredPoints,
                modifier = Modifier.fillMaxWidth(),
                title = "xG% vs Points% (Last 10 Wks) - $awayTeam @ $homeTeam",
                xAxisLabel = "Points %",
                yAxisLabel = "xG% 5v5",
                highlightedTeamCodes = setOf(awayTeam, homeTeam),
                quadrantTopRight = QuadrantConfig(label = "Elite", color = "#4CAF50", lightModeColor = "#4CAF50"),
                quadrantTopLeft = QuadrantConfig(label = "Unlucky", color = "#2196F3", lightModeColor = "#2196F3"),
                quadrantBottomLeft = QuadrantConfig(label = "Struggling", color = "#F44336", lightModeColor = "#F44336"),
                quadrantBottomRight = QuadrantConfig(label = "Overperforming", color = "#FF9800", lightModeColor = "#FF9800"),
                customCenterX = leagueStats?.avgPointsPct ?: 50.0,
                customCenterY = leagueStats?.avgXgPct ?: 50.0,
                customXMin = leagueStats?.minPointsPct,
                customXMax = leagueStats?.maxPointsPct,
                customYMin = leagueStats?.minXgPct,
                customYMax = leagueStats?.maxXgPct,
                source = "Natural Stat Trick / NHL API",
                onShareClick = onShareClick,
                teamLegendItems = listOf(
                    TeamLegendEntry(awayTeam, Color(0xFF2196F3)),
                    TeamLegendEntry(homeTeam, Color(0xFFFF5722))
                )
            )
        }
    }
}

/**
 * Parse weekly values (xG% or Points%) from stats JSON.
 * Returns a map of weekNum -> value.
 */
private fun parseWeeklyValues(stats: JsonObject, key: String): Map<Int, Double> {
    val result = mutableMapOf<Int, Double>()
    val weekData = stats[key]
    if (weekData is JsonObject) {
        weekData.forEach { (weekKey, value) ->
            val weekNum = weekKey.removePrefix("week-").toIntOrNull()
            val v = (value as? JsonPrimitive)?.doubleOrNull
            if (weekNum != null && v != null) {
                result[weekNum] = v
            }
        }
    }
    return result
}

// ============================================================================
// JSON Parsing Helpers for xG% Charts
// ============================================================================

private fun parseXgfPctByWeek(stats: JsonObject, key: String): List<LineChartDataPoint> {
    val dataPoints = mutableListOf<LineChartDataPoint>()

    val weekData = stats[key]
    if (weekData is JsonObject) {
        weekData.forEach { (weekKey, value) ->
            val weekNum = weekKey.removePrefix("week-").toIntOrNull()
            val xgfPct = (value as? JsonPrimitive)?.doubleOrNull
            if (weekNum != null && xgfPct != null) {
                dataPoints.add(LineChartDataPoint(x = weekNum.toDouble(), y = xgfPct))
            }
        }
    }

    return dataPoints.sortedBy { it.x }
}

private fun parseTenthXgfPctByWeek(data: JsonObject?): List<Pair<Double, Double>> {
    if (data == null) return emptyList()

    val points = mutableListOf<Pair<Double, Double>>()

    data.forEach { (weekKey, value) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val xgfPct = (value as? JsonPrimitive)?.doubleOrNull
        if (weekNum != null && xgfPct != null) {
            points.add(Pair(weekNum.toDouble(), xgfPct))
        }
    }

    return points.sortedBy { it.first }
}
