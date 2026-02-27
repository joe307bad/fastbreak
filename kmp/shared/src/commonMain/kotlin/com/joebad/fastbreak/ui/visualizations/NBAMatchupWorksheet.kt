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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import com.joebad.fastbreak.data.model.PlayoffProbability
import com.joebad.fastbreak.data.model.ScatterPlotDataPoint
import com.joebad.fastbreak.data.model.ScatterPlotQuadrants
import com.joebad.fastbreak.data.model.QuadrantConfig
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.QuadrantScatterPlot
import com.joebad.fastbreak.ui.TeamLegendEntry
import com.joebad.fastbreak.ui.components.MultiOptionFab
import com.joebad.fastbreak.ui.components.FabOption
import com.joebad.fastbreak.ui.components.ShareFab
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.math.round

/**
 * Capture target for on-demand share image generation.
 * Off-screen content is only composed when actively capturing.
 */
private enum class NbaCaptureTarget {
    PRE_GAME,
    POST_GAME
}

/**
 * Data class to hold capture state and title for sharing
 */
private data class CaptureRequest(
    val target: NbaCaptureTarget,
    val title: String
)

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

    // Calculate initial date index based on highlighted teams (from deep links) or current date
    // Find the first date that contains a matchup with the highlighted team, or default to today
    val initialDateIndex = remember(dates, matchupsByDate, highlightedTeamCodes) {
        if (highlightedTeamCodes.isNotEmpty()) {
            // If there are highlighted teams, find the first date with those teams
            dates.indexOfFirst { date ->
                matchupsByDate[date]?.any { matchup ->
                    highlightedTeamCodes.contains(matchup.awayTeam.abbreviation) ||
                    highlightedTeamCodes.contains(matchup.homeTeam.abbreviation)
                } == true
            }.takeIf { it >= 0 } ?: 0
        } else {
            // Default to today's date
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

    // On-demand capture: graphics layer and content only exist during capture
    // This avoids stale layer state when app is backgrounded on iOS
    var captureRequest by remember { mutableStateOf<CaptureRequest?>(null) }
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }

    // Share callbacks from charts (set by chart components)
    var netRatingShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var offDefRatingShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

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

                            PlayoffProbabilitySection(
                                awayTeam = selectedMatchup.awayTeam.abbreviation,
                                homeTeam = selectedMatchup.homeTeam.abbreviation,
                                awayProb = selectedMatchup.awayTeam.playoffProbability,
                                homeProb = selectedMatchup.homeTeam.playoffProbability
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            NBAMatchupContent(
                                matchup = selectedMatchup,
                                viewSelection = viewSelection,
                                onViewSelectionChange = { viewSelection = it }
                            )
                        }

                        // Pinned header (team abbreviations + final score if available)
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
                        NBAChartsTab(
                            awayTeam = selectedMatchup.awayTeam.abbreviation,
                            homeTeam = selectedMatchup.homeTeam.abbreviation,
                            matchup = selectedMatchup,
                            quadrantConfig = visualization.scatterPlotQuadrants,
                            onNetRatingShareClick = { callback -> netRatingShareCallback = callback },
                            onOffDefRatingShareClick = { callback -> offDefRatingShareCallback = callback }
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

        // Detect theme mode for chart sharing
        val isDark = MaterialTheme.colorScheme.background == Color.Black ||
                     MaterialTheme.colorScheme.background == Color(0xFF0A0A0A)
        val textColor = MaterialTheme.colorScheme.onBackground

        // Build FAB options based on current tab and results availability
        val hasResults = selectedMatchup.results != null

        when {
            // Charts tab: show chart share options
            selectedTab == 1 -> {
                val chartOptions = listOf(
                    FabOption(
                        icon = Icons.Filled.TrendingUp,
                        label = "Cumulative Net Rating",
                        onClick = { netRatingShareCallback?.invoke() }
                    ),
                    FabOption(
                        icon = Icons.Filled.Star,
                        label = "Off Rating vs Def Rating",
                        onClick = { offDefRatingShareCallback?.invoke() }
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
                        onClick = { captureRequest = CaptureRequest(NbaCaptureTarget.PRE_GAME, "$shareTitle - Pre Game") }
                    ),
                    FabOption(
                        icon = Icons.Filled.Check,
                        label = "Post Game",
                        onClick = { captureRequest = CaptureRequest(NbaCaptureTarget.POST_GAME, "$shareTitle - Results") }
                    )
                )
                MultiOptionFab(
                    options = statsOptions,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
            // Stats tab without results: immediately share Pre Game
            else -> {
                ShareFab(
                    onClick = { captureRequest = CaptureRequest(NbaCaptureTarget.PRE_GAME, "$shareTitle - Pre Game") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
        }

        // On-demand capture: off-screen content only composed when capturing
        // This prevents stale graphics layer state on iOS app resume
        captureRequest?.let { request ->
            val (captureWidth, captureHeight) = when (request.target) {
                NbaCaptureTarget.PRE_GAME -> 3400.dp to 1900.dp
                NbaCaptureTarget.POST_GAME -> 400.dp to 540.dp
            }

            // Capture after content is drawn
            LaunchedEffect(request) {
                // Wait for next frame to ensure content is fully rendered
                kotlinx.coroutines.delay(50)  // Small delay for layout
                try {
                    val bitmap = graphicsLayer.toImageBitmap()
                    println("📸 NBA Matchup Share: Captured bitmap size: ${bitmap.width}x${bitmap.height}")
                    imageExporter.shareImage(bitmap, request.title)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    captureRequest = null
                }
            }

            when (request.target) {
                NbaCaptureTarget.PRE_GAME -> {
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
                // Build stat boxes from NBA matchup data
                val gameInfo = ShareGameInfo(
                    awayTeam = selectedMatchup.awayTeam.abbreviation,
                    homeTeam = selectedMatchup.homeTeam.abbreviation,
                    eventLabel = eventLabel,
                    formattedDate = formattedDate,
                    source = "hoopR / ESPN / PlayoffStatus.com",
                    awayRecord = selectedMatchup.awayTeam.wins?.let { w ->
                        selectedMatchup.awayTeam.losses?.let { l -> "$w-$l" }
                    },
                    homeRecord = selectedMatchup.homeTeam.wins?.let { w ->
                        selectedMatchup.homeTeam.losses?.let { l -> "$w-$l" }
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

                NbaCaptureTarget.POST_GAME -> {
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
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            // Post game results content
                            if (selectedMatchup.gameCompleted && selectedMatchup.results != null) {
                                PostGameShareContent(
                                    matchup = selectedMatchup,
                                    formattedDate = formattedDate,
                                    eventLabel = eventLabel
                                )
                            } else {
                                // Placeholder for games not yet completed
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Game not yet completed",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Post game share content - TWO FONT SIZES ONLY: titleLarge for header, bodyMedium for stats
 */
@Composable
private fun PostGameShareContent(
    matchup: NBAMatchup,
    formattedDate: String,
    eventLabel: String
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
        modifier = Modifier
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

        // Date and location - SMALL SIZE (bodyMedium)
        Text(
            text = formattedDate,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        matchup.location?.let { loc ->
            Text(
                text = loc.fullLocation ?: "${loc.stadium ?: ""}, ${loc.city ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Full box score table
        if (awayBox != null && homeBox != null) {
            // Table header - slightly larger (bodyLarge)
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

            // All stats - SMALL SIZE (bodyMedium), bold the team with edge
            CompactStatRow(awayBox.fgm?.toString() ?: "-", awayBox.fga?.toString() ?: "-", "FGM-A", homeBox.fgm?.toString() ?: "-", homeBox.fga?.toString() ?: "-")
            CompactStatRow(awayBox.fgPct?.formatStat(1) ?: "-", "FG%", homeBox.fgPct?.formatStat(1) ?: "-", vsAvg?.away?.fieldGoalPct?.difference, vsAvg?.home?.fieldGoalPct?.difference, awayBox.fgPct, homeBox.fgPct, true)
            CompactStatRow(awayBox.fg3m?.toString() ?: "-", awayBox.fg3a?.toString() ?: "-", "3PM-A", homeBox.fg3m?.toString() ?: "-", homeBox.fg3a?.toString() ?: "-")
            CompactStatRow(awayBox.fg3Pct?.formatStat(1) ?: "-", "3P%", homeBox.fg3Pct?.formatStat(1) ?: "-", vsAvg?.away?.threePtPct?.difference, vsAvg?.home?.threePtPct?.difference, awayBox.fg3Pct, homeBox.fg3Pct, true)
            CompactStatRow(awayBox.ftm?.toString() ?: "-", awayBox.fta?.toString() ?: "-", "FTM-A", homeBox.ftm?.toString() ?: "-", homeBox.fta?.toString() ?: "-")
            CompactStatRow(awayBox.ftPct?.formatStat(1) ?: "-", "FT%", homeBox.ftPct?.formatStat(1) ?: "-", vsAvg?.away?.freeThrowPct?.difference, vsAvg?.home?.freeThrowPct?.difference, awayBox.ftPct, homeBox.ftPct, true)

            CompactStatRow(awayBox.oreb?.toString() ?: "-", "OREB", homeBox.oreb?.toString() ?: "-", vsAvg?.away?.offRebounds?.difference, vsAvg?.home?.offRebounds?.difference, awayBox.oreb?.toDouble(), homeBox.oreb?.toDouble(), true)
            CompactStatRow(awayBox.dreb?.toString() ?: "-", "DREB", homeBox.dreb?.toString() ?: "-", null, null, awayBox.dreb?.toDouble(), homeBox.dreb?.toDouble(), true)
            CompactStatRow(awayBox.reb?.toString() ?: "-", "REB", homeBox.reb?.toString() ?: "-", vsAvg?.away?.rebounds?.difference, vsAvg?.home?.rebounds?.difference, awayBox.reb?.toDouble(), homeBox.reb?.toDouble(), true)
            CompactStatRow(awayBox.ast?.toString() ?: "-", "AST", homeBox.ast?.toString() ?: "-", vsAvg?.away?.assists?.difference, vsAvg?.home?.assists?.difference, awayBox.ast?.toDouble(), homeBox.ast?.toDouble(), true)
            CompactStatRow(awayBox.stl?.toString() ?: "-", "STL", homeBox.stl?.toString() ?: "-", vsAvg?.away?.steals?.difference, vsAvg?.home?.steals?.difference, awayBox.stl?.toDouble(), homeBox.stl?.toDouble(), true)
            CompactStatRow(awayBox.blk?.toString() ?: "-", "BLK", homeBox.blk?.toString() ?: "-", vsAvg?.away?.blocks?.difference, vsAvg?.home?.blocks?.difference, awayBox.blk?.toDouble(), homeBox.blk?.toDouble(), true)
            CompactStatRow(awayBox.tov?.toString() ?: "-", "TO", homeBox.tov?.toString() ?: "-", vsAvg?.away?.turnovers?.difference, vsAvg?.home?.turnovers?.difference, awayBox.tov?.toDouble(), homeBox.tov?.toDouble(), false)
            CompactStatRow(awayBox.pf?.toString() ?: "-", "PF", homeBox.pf?.toString() ?: "-", null, null, awayBox.pf?.toDouble(), homeBox.pf?.toDouble(), false)

            CompactStatRow(awayBox.ptsPaint?.toString() ?: "-", "PTS PAINT", homeBox.ptsPaint?.toString() ?: "-", null, null, awayBox.ptsPaint?.toDouble(), homeBox.ptsPaint?.toDouble(), true)
            CompactStatRow(awayBox.ptsFb?.toString() ?: "-", "FAST BRK", homeBox.ptsFb?.toString() ?: "-", null, null, awayBox.ptsFb?.toDouble(), homeBox.ptsFb?.toDouble(), true)
            CompactStatRow(awayBox.ptsOffTov?.toString() ?: "-", "PTS OFF TO", homeBox.ptsOffTov?.toString() ?: "-", null, null, awayBox.ptsOffTov?.toDouble(), homeBox.ptsOffTov?.toDouble(), true)
            CompactStatRow(awayBox.largestLead?.toString() ?: "-", "LRG LEAD", homeBox.largestLead?.toString() ?: "-", null, null, awayBox.largestLead?.toDouble(), homeBox.largestLead?.toDouble(), true)

            CompactStatRow(awayBox.tsPct?.formatStat(1) ?: "-", "TS%", homeBox.tsPct?.formatStat(1) ?: "-", vsAvg?.away?.tsPct?.difference, vsAvg?.home?.tsPct?.difference, awayBox.tsPct, homeBox.tsPct, true)
            CompactStatRow(awayBox.efgPct?.formatStat(1) ?: "-", "EFG%", homeBox.efgPct?.formatStat(1) ?: "-", vsAvg?.away?.efgPct?.difference, vsAvg?.home?.efgPct?.difference, awayBox.efgPct, homeBox.efgPct, true)
            CompactStatRow(awayBox.astTovRatio?.formatStat(2) ?: "-", "AST/TO", homeBox.astTovRatio?.formatStat(2) ?: "-", null, null, awayBox.astTovRatio, homeBox.astTovRatio, true)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Source footer - SMALL SIZE
        Text(
            text = "fbrk.app  •  hoopR / ESPN",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/**
 * Compact stat row for box score with edge indicator - columns: edge|value|diff | label | diff|value|edge
 */
@Composable
private fun CompactStatRow(
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
 * Compact stat row for made-attempted stats (e.g., FGM-FGA) - matching column alignment
 */
@Composable
private fun CompactStatRow(
    awayMade: String,
    awayAttempted: String,
    label: String,
    homeMade: String,
    homeAttempted: String
) {
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
        // Away: empty edge column
        Spacer(modifier = Modifier.width(16.dp))

        // Away: value column
        Text(
            text = "$awayMade-$awayAttempted",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Away: empty diff column
        Spacer(modifier = Modifier.weight(0.6f))

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

        // Home: empty diff column
        Spacer(modifier = Modifier.weight(0.6f))

        Spacer(modifier = Modifier.width(4.dp))

        // Home: value column
        Text(
            text = "$homeMade-$homeAttempted",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            maxLines = 1,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(0.8f),
            textAlign = TextAlign.Start
        )

        // Home: empty edge column
        Spacer(modifier = Modifier.width(16.dp))
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

        // Show completed game results or one month trend based on game status
        if (matchup.gameCompleted && matchup.results != null) {
            CompletedGameSection(matchup)
        } else {
            OneMonthTrendSection(
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
 * Playoff probability section showing chances for each team
 */
@Composable
private fun PlayoffProbabilitySection(
    awayTeam: String,
    homeTeam: String,
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
            PlayoffProbabilityText(
                playoffProb = awayProb?.playoffProb,
                champProb = awayProb?.champProb,
                alignment = TextAlign.Start
            )

            // Home team probabilities
            PlayoffProbabilityText(
                playoffProb = homeProb?.playoffProb,
                champProb = homeProb?.champProb,
                alignment = TextAlign.End
            )
        }
    }
}

/**
 * Get color for probability value - dark red/orange (0-5%) to dark green (>5%)
 */
private fun getProbabilityColor(prob: Double?): Color {
    if (prob == null) return Color.Gray
    val p = prob.coerceIn(0.0, 100.0)
    return when {
        p <= 5.0 -> {
            // Dark red to orange (0% to 5%)
            val t = (p / 5.0).toFloat()  // 0 to 1
            Color(
                red = 0.7f + 0.2f * t,  // 0.7 to 0.9 (dark red to orange-red)
                green = 0.1f + 0.4f * t,  // 0.1 to 0.5 (adding orange)
                blue = 0f,
                alpha = 1f
            )
        }
        else -> {
            // Dark green for anything > 5%
            Color(0xFF228B22)  // Forest green
        }
    }
}

/**
 * Format probability value as string
 */
private fun formatProbability(prob: Double?): String {
    return if (prob != null) {
        if (prob >= 99.5) ">99%" else "${prob.toInt()}%"
    } else {
        "-"
    }
}

/**
 * Composable for displaying playoff and championship probability with colors
 */
@Composable
private fun PlayoffProbabilityText(
    playoffProb: Double?,
    champProb: Double?,
    alignment: TextAlign
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignment == TextAlign.End) Arrangement.End else Arrangement.Start,
        modifier = Modifier.widthIn(min = 80.dp)
    ) {
        Text(
            text = "PO:",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = formatProbability(playoffProb),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = getProbabilityColor(playoffProb)
        )
        Text(
            text = " Ch:",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = formatProbability(champProb),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = getProbabilityColor(champProb)
        )
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
    quadrantConfig: ScatterPlotQuadrants? = null,
    onNetRatingShareClick: ((() -> Unit)?) -> Unit = {},
    onOffDefRatingShareClick: ((()-> Unit) -> Unit)? = null
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
            tenthNetRatingByWeek = matchup.tenthNetRatingByWeek,
            onShareClick = onNetRatingShareClick
        )

        // Weekly Efficiency Scatter Plot
        WeeklyEfficiencyScatterPlot(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayStats = matchup.awayTeam.stats,
            homeStats = matchup.homeTeam.stats,
            leagueStats = matchup.leagueEfficiencyStats,
            quadrantConfig = quadrantConfig,
            onShareClick = onOffDefRatingShareClick
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
    tenthNetRatingByWeek: JsonObject? = null,
    onShareClick: ((() -> Unit)?) -> Unit = {}
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
            .fillMaxWidth(),
        yAxisTitle = "Net Rating",
        referenceLines = referenceLines,
        title = "Cumulative Net Rating - $awayTeam @ $homeTeam",
        source = "hoopR / ESPN",
        onShareClick = onShareClick
    )
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
    quadrantConfig: ScatterPlotQuadrants? = null,
    onShareClick: ((()-> Unit) -> Unit)? = null
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

            QuadrantScatterPlot(
                data = scatterData,
                modifier = Modifier
                    .fillMaxWidth(),
                title = "Weekly Off vs Def Rating - $awayTeam @ $homeTeam",
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
                regressionData = scatterData,
                source = "hoopR / ESPN",
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
 * Completed game section showing box score and vs season average comparisons
 * Final score is displayed in the pinned header
 */
@Composable
private fun CompletedGameSection(
    matchup: com.joebad.fastbreak.data.model.NBAMatchup
) {
    val results = matchup.results ?: return
    val teamBoxScore = results.teamBoxScore
    val vsSeasonAvg = results.vsSeasonAvg

    // Team Box Score Section (combined with vs Season Average)
    if (teamBoxScore != null) {
        SectionHeader("Box Score (vs Season Avg)")
        Spacer(modifier = Modifier.height(4.dp))

        val awayBox = teamBoxScore.away
        val homeBox = teamBoxScore.home
        val awayComps = vsSeasonAvg?.away
        val homeComps = vsSeasonAvg?.home

        // Helper to format difference string
        fun formatDiff(stat: com.joebad.fastbreak.data.model.NBAStatComparison?): String {
            val diff = stat?.difference ?: return ""
            val prefix = if (diff >= 0) "+" else ""
            return " (${prefix}${diff.formatStat(1)})"
        }

        // FG%
        if (awayBox?.fgPct != null || homeBox?.fgPct != null) {
            val awayVal = awayBox?.fgPct ?: 0.0
            val homeVal = homeBox?.fgPct ?: 0.0
            ThreeColumnRow(
                leftText = "${awayBox?.fgm ?: 0}/${awayBox?.fga ?: 0} ${awayVal.formatStat(0)}%${formatDiff(awayComps?.fieldGoalPct)}",
                centerText = "FG",
                rightText = "${homeBox?.fgm ?: 0}/${homeBox?.fga ?: 0} ${homeVal.formatStat(0)}%${formatDiff(homeComps?.fieldGoalPct)}",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // 3P%
        if (awayBox?.fg3Pct != null || homeBox?.fg3Pct != null) {
            val awayVal = awayBox?.fg3Pct ?: 0.0
            val homeVal = homeBox?.fg3Pct ?: 0.0
            ThreeColumnRow(
                leftText = "${awayBox?.fg3m ?: 0}/${awayBox?.fg3a ?: 0} ${awayVal.formatStat(0)}%${formatDiff(awayComps?.threePtPct)}",
                centerText = "3PT",
                rightText = "${homeBox?.fg3m ?: 0}/${homeBox?.fg3a ?: 0} ${homeVal.formatStat(0)}%${formatDiff(homeComps?.threePtPct)}",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // FT%
        if (awayBox?.ftPct != null || homeBox?.ftPct != null) {
            val awayVal = awayBox?.ftPct ?: 0.0
            val homeVal = homeBox?.ftPct ?: 0.0
            ThreeColumnRow(
                leftText = "${awayBox?.ftm ?: 0}/${awayBox?.fta ?: 0} ${awayVal.formatStat(0)}%${formatDiff(awayComps?.freeThrowPct)}",
                centerText = "FT",
                rightText = "${homeBox?.ftm ?: 0}/${homeBox?.fta ?: 0} ${homeVal.formatStat(0)}%${formatDiff(homeComps?.freeThrowPct)}",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Rebounds
        if (awayBox?.reb != null || homeBox?.reb != null) {
            val awayVal = awayBox?.reb ?: 0
            val homeVal = homeBox?.reb ?: 0
            ThreeColumnRow(
                leftText = "$awayVal (${awayBox?.oreb ?: 0}/${awayBox?.dreb ?: 0})${formatDiff(awayComps?.rebounds)}",
                centerText = "REB",
                rightText = "$homeVal (${homeBox?.oreb ?: 0}/${homeBox?.dreb ?: 0})${formatDiff(homeComps?.rebounds)}",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Assists
        if (awayBox?.ast != null || homeBox?.ast != null) {
            val awayVal = awayBox?.ast ?: 0
            val homeVal = homeBox?.ast ?: 0
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayComps?.assists)}",
                centerText = "AST",
                rightText = "$homeVal${formatDiff(homeComps?.assists)}",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Steals
        if (awayBox?.stl != null || homeBox?.stl != null) {
            val awayVal = awayBox?.stl ?: 0
            val homeVal = homeBox?.stl ?: 0
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayComps?.steals)}",
                centerText = "STL",
                rightText = "$homeVal${formatDiff(homeComps?.steals)}",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Blocks
        if (awayBox?.blk != null || homeBox?.blk != null) {
            val awayVal = awayBox?.blk ?: 0
            val homeVal = homeBox?.blk ?: 0
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayComps?.blocks)}",
                centerText = "BLK",
                rightText = "$homeVal${formatDiff(homeComps?.blocks)}",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Turnovers (lower is better)
        if (awayBox?.tov != null || homeBox?.tov != null) {
            val awayVal = awayBox?.tov ?: 0
            val homeVal = homeBox?.tov ?: 0
            ThreeColumnRow(
                leftText = "$awayVal${formatDiff(awayComps?.turnovers)}",
                centerText = "TOV",
                rightText = "$homeVal${formatDiff(homeComps?.turnovers)}",
                advantage = if (awayVal < homeVal) -1 else if (homeVal < awayVal) 1 else 0
            )
        }

        // Points in Paint
        if (awayBox?.ptsPaint != null || homeBox?.ptsPaint != null) {
            val awayVal = awayBox?.ptsPaint ?: 0
            val homeVal = homeBox?.ptsPaint ?: 0
            ThreeColumnRow(
                leftText = awayVal.toString(),
                centerText = "Paint",
                rightText = homeVal.toString(),
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // Fast Break Points
        if (awayBox?.ptsFb != null || homeBox?.ptsFb != null) {
            val awayVal = awayBox?.ptsFb ?: 0
            val homeVal = homeBox?.ptsFb ?: 0
            ThreeColumnRow(
                leftText = awayVal.toString(),
                centerText = "Fast Break",
                rightText = homeVal.toString(),
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // TS%
        if (awayBox?.tsPct != null || homeBox?.tsPct != null) {
            val awayVal = awayBox?.tsPct ?: 0.0
            val homeVal = homeBox?.tsPct ?: 0.0
            ThreeColumnRow(
                leftText = "${awayVal.formatStat(1)}%${formatDiff(awayComps?.tsPct)}",
                centerText = "TS%",
                rightText = "${homeVal.formatStat(1)}%${formatDiff(homeComps?.tsPct)}",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        // eFG%
        if (awayBox?.efgPct != null || homeBox?.efgPct != null) {
            val awayVal = awayBox?.efgPct ?: 0.0
            val homeVal = homeBox?.efgPct ?: 0.0
            ThreeColumnRow(
                leftText = "${awayVal.formatStat(1)}%${formatDiff(awayComps?.efgPct)}",
                centerText = "eFG%",
                rightText = "${homeVal.formatStat(1)}%${formatDiff(homeComps?.efgPct)}",
                advantage = if (awayVal > homeVal) -1 else if (homeVal > awayVal) 1 else 0
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
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
