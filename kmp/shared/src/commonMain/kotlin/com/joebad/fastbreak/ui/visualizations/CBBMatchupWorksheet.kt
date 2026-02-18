package com.joebad.fastbreak.ui.visualizations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.CBBMatchup
import com.joebad.fastbreak.data.model.CBBMatchupVisualization
import com.joebad.fastbreak.data.model.CBBStatValue
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.components.ShareFab
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
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
 * CBB Matchup Worksheet component with two-row navigation:
 * - First row: Date badges to filter games by date
 * - Second row: Matchup badges for the selected date
 */
@Composable
fun CBBMatchupWorksheet(
    visualization: CBBMatchupVisualization,
    modifier: Modifier = Modifier,
    pinnedTeams: List<com.joebad.fastbreak.data.model.PinnedTeam> = emptyList(),
    highlightedTeamCodes: Set<String> = emptySet(),
    onScheduleToggleHandlerChanged: ((ScheduleToggleHandler?) -> Unit)? = null
) {
    // Combine CBB pinned teams with highlighted team codes from deep links
    val cbbPinnedTeamCodes = remember(pinnedTeams, highlightedTeamCodes) {
        val pinned = pinnedTeams.filter { it.sport == "CBB" }.map { it.teamCode }.toSet()
        pinned + highlightedTeamCodes
    }

    // Group matchups by date in Eastern timezone and sort dates chronologically
    val matchupsByDate = remember(visualization.dataPoints, cbbPinnedTeamCodes) {
        visualization.dataPoints
            .groupBy { matchup ->
                val instant = Instant.parse(matchup.gameDate)
                instant.toLocalDateTime(TimeZone.of("America/New_York")).date
            }
            .mapValues { (_, matchups) ->
                matchups.sortedByDescending { matchup ->
                    val hasPinnedTeam = cbbPinnedTeamCodes.contains(matchup.awayTeam.abbreviation) ||
                                       cbbPinnedTeamCodes.contains(matchup.homeTeam.abbreviation)
                    if (hasPinnedTeam) 1 else 0
                }
            }
            .toList()
            .sortedBy { (date, _) -> date }
            .toMap()
    }

    val dates = remember(matchupsByDate) { matchupsByDate.keys.toList() }

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

    var selectedDateIndex by remember { mutableStateOf(initialDateIndex) }
    var selectedMatchupIndex by remember { mutableStateOf(0) }
    var isScheduleExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(isScheduleExpanded) {
        onScheduleToggleHandlerChanged?.invoke(
            ScheduleToggleHandler(
                isExpanded = isScheduleExpanded,
                toggle = { isScheduleExpanded = !isScheduleExpanded }
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            onScheduleToggleHandlerChanged?.invoke(null)
        }
    }

    val selectedDate = if (dates.isNotEmpty()) dates[selectedDateIndex] else null
    val matchupsForDate = selectedDate?.let { matchupsByDate[it] } ?: emptyList()

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

    var viewSelection by remember { mutableStateOf(0) }

    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    val imageExporter = remember { getImageExporter() }
    var isCapturing by remember { mutableStateOf(false) }

    val eventLabel = remember(selectedMatchup.gameDate, selectedMatchup.location) {
        val location = selectedMatchup.location?.fullLocation
        if (location != null && location.isNotBlank()) {
            "College Basketball • $location"
        } else {
            "College Basketball"
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
        "${selectedMatchup.awayTeam.abbreviation} @ ${selectedMatchup.homeTeam.abbreviation} - CBB Matchup"
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
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

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 20.dp)
                ) {
                    // Record and ranking section for CBB
                    CBBRecordSection(
                        awayTeam = selectedMatchup.awayTeam,
                        homeTeam = selectedMatchup.homeTeam
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    CBBMatchupContent(
                        matchup = selectedMatchup,
                        viewSelection = viewSelection,
                        onViewSelectionChange = { viewSelection = it }
                    )
                }

                PinnedMatchupHeader(
                    awayTeam = selectedMatchup.awayTeam.abbreviation,
                    homeTeam = selectedMatchup.homeTeam.abbreviation,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

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

        ShareFab(
            onClick = {
                if (!isCapturing) {
                    coroutineScope.launch {
                        isCapturing = true
                        try {
                            kotlinx.coroutines.delay(100)
                            val bitmap = graphicsLayer.toImageBitmap()
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

        // Off-screen shareable content for capture
        CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
            Box(
                modifier = Modifier
                    .requiredWidth(3400.dp)
                    .requiredHeight(1900.dp)
                    .offset { IntOffset(-10000, 0) }
                    .drawWithCache {
                        onDrawWithContent {
                            graphicsLayer.record {
                                this@onDrawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                    }
            ) {
                val gameInfo = ShareGameInfo(
                    awayTeam = selectedMatchup.awayTeam.abbreviation,
                    homeTeam = selectedMatchup.homeTeam.abbreviation,
                    eventLabel = eventLabel,
                    formattedDate = formattedDate,
                    source = visualization.source ?: "ESPN / Sports Reference",
                    awayRecord = selectedMatchup.awayTeam.wins?.let { w ->
                        selectedMatchup.awayTeam.losses?.let { l -> "$w-$l" }
                    },
                    homeRecord = selectedMatchup.homeTeam.wins?.let { w ->
                        selectedMatchup.homeTeam.losses?.let { l -> "$w-$l" }
                    },
                    awayConference = selectedMatchup.awayTeam.conference,
                    homeConference = selectedMatchup.homeTeam.conference,
                    // CBB-specific fields
                    awayApRank = selectedMatchup.awayTeam.apRank,
                    homeApRank = selectedMatchup.homeTeam.apRank,
                    awaySrsRank = selectedMatchup.awayTeam.srsRank,
                    homeSrsRank = selectedMatchup.homeTeam.srsRank,
                    isCBB = true
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

                // Build stat boxes for CBB (4 boxes - no player stats)
                val statBoxes = buildCBBStatBoxes(selectedMatchup)

                GenericMatchupShareImage(
                    gameInfo = gameInfo,
                    odds = odds,
                    statBoxes = statBoxes,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Build stat boxes for CBB share image
 */
private fun buildCBBStatBoxes(matchup: CBBMatchup): List<ShareStatBox> = buildList {
    // Box 1: Offensive Stats
    add(ShareStatBox(
        title = "Offensive Stats",
        fiveColStats = matchup.comparisons?.sideBySide?.offense?.mapNotNull { (key, stat) ->
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
                useCBBRanks = true
            )
        }?.take(9) ?: emptyList()
    ))

    // Box 2: Defensive Stats
    add(ShareStatBox(
        title = "Defensive Stats",
        fiveColStats = matchup.comparisons?.sideBySide?.defense?.mapNotNull { (key, stat) ->
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
                useCBBRanks = true
            )
        }?.take(9) ?: emptyList()
    ))

    // Box 3: Away Off vs Home Def
    add(ShareStatBox(
        title = "${matchup.awayTeam.abbreviation} Off vs ${matchup.homeTeam.abbreviation} Def",
        leftLabel = "${matchup.awayTeam.abbreviation} Off",
        middleLabel = "vs",
        rightLabel = "${matchup.homeTeam.abbreviation} Def",
        fiveColStats = matchup.comparisons?.awayOffVsHomeDef?.mapNotNull { (key, stat) ->
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
                useCBBRanks = true
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
        fiveColStats = matchup.comparisons?.homeOffVsAwayDef?.mapNotNull { (key, stat) ->
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
                useCBBRanks = true
            )
        }?.take(9) ?: emptyList()
    ))

    // Box 5: Overall/SRS Stats (CBB specific)
    val overallStats = matchup.comparisons?.sideBySide?.overall

    if (!overallStats.isNullOrEmpty()) {
        add(ShareStatBox(
            title = "Overall Ratings",
            fiveColStats = overallStats.mapNotNull { (key, stat) ->
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
                    useCBBRanks = true
                )
            }.take(9)
        ))
    }
}

/**
 * CBB Record and ranking section - three-line format with colored AP rank indicator
 * Line 1: [colored indicator] record
 * Line 2: #N AP / #N SRS
 * Line 3: Conference
 */
@Composable
private fun CBBRecordSection(
    awayTeam: com.joebad.fastbreak.data.model.CBBTeamInfo,
    homeTeam: com.joebad.fastbreak.data.model.CBBTeamInfo
) {
    val textStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 14.sp)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    // Build ranking info string for away team (AP / SRS only)
    val awayRankingParts = mutableListOf<String>()
    awayTeam.apRank?.let { awayRankingParts.add("#$it AP") }
    awayTeam.srsRank?.let { awayRankingParts.add("#$it SRS") }
    val awayRankingInfo = awayRankingParts.joinToString(" / ")

    // Build ranking info string for home team (AP / SRS only)
    val homeRankingParts = mutableListOf<String>()
    homeTeam.apRank?.let { homeRankingParts.add("#$it AP") }
    homeTeam.srsRank?.let { homeRankingParts.add("#$it SRS") }
    val homeRankingInfo = homeRankingParts.joinToString(" / ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Away team info (left-aligned)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            // Record with AP rank color indicator (shows red if unranked)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = getAPRankColor(awayTeam.apRank),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (awayTeam.wins != null && awayTeam.losses != null) {
                    Text(
                        text = "${awayTeam.wins}-${awayTeam.losses}",
                        style = textStyle,
                        fontSize = 11.sp,
                        color = textColor
                    )
                }
            }
            // AP / SRS on same line
            if (awayRankingInfo.isNotEmpty()) {
                Text(
                    text = awayRankingInfo,
                    style = textStyle,
                    fontSize = 11.sp,
                    color = textColor
                )
            }
            // Conference on separate line
            awayTeam.conference?.let {
                Text(
                    text = it,
                    style = textStyle,
                    fontSize = 11.sp,
                    color = textColor
                )
            }
        }

        // Home team info (right-aligned)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            // Record with AP rank color indicator (shows red if unranked)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (homeTeam.wins != null && homeTeam.losses != null) {
                    Text(
                        text = "${homeTeam.wins}-${homeTeam.losses}",
                        style = textStyle,
                        fontSize = 11.sp,
                        color = textColor
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = getAPRankColor(homeTeam.apRank),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
            // AP / SRS on same line
            if (homeRankingInfo.isNotEmpty()) {
                Text(
                    text = homeRankingInfo,
                    style = textStyle,
                    fontSize = 11.sp,
                    color = textColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
            // Conference on separate line
            homeTeam.conference?.let {
                Text(
                    text = it,
                    style = textStyle,
                    fontSize = 11.sp,
                    color = textColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
        }
    }
}

/**
 * CBB Matchup content - shows team stats and comparisons
 */
@Composable
private fun CBBMatchupContent(
    matchup: CBBMatchup,
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
                SectionHeader("Betting Odds")

                odds.spread?.let { spread ->
                    val homeSpread = if (spread > 0) "+$spread" else spread.toString()
                    val awaySpread = if (spread < 0) "+${-spread}" else (-spread).toString()
                    ThreeColumnRow(
                        leftText = awaySpread,
                        centerText = "Spread",
                        rightText = homeSpread
                    )
                }

                if (odds.homeMoneyline != null || odds.awayMoneyline != null) {
                    ThreeColumnRow(
                        leftText = odds.awayMoneyline ?: "",
                        centerText = "Moneyline",
                        rightText = odds.homeMoneyline ?: ""
                    )
                }

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

        when (viewSelection) {
            0 -> CBBTeamStatsView(matchup)
            1 -> CBBOffenseVsDefenseView(matchup, awayOffense = true)
            2 -> CBBOffenseVsDefenseView(matchup, awayOffense = false)
        }
    }
}

/**
 * CBB Team stats view - side by side comparison using shared FiveColumnRowWithRanks
 */
@Composable
private fun CBBTeamStatsView(matchup: CBBMatchup) {
    Column {
        // Offensive Stats
        SectionHeader("Offensive Stats")
        Spacer(modifier = Modifier.height(4.dp))

        matchup.comparisons?.sideBySide?.offense?.forEach { (key, stat) ->
            val awayValue = stat.away.value
            val awayRank = stat.away.rank
            val awayRankDisplay = stat.away.rankDisplay
            val homeValue = stat.home.value
            val homeRank = stat.home.rank
            val homeRankDisplay = stat.home.rankDisplay
            val label = stat.label

            // Use rank-based advantage (lower rank is better)
            val advantage = if (awayRank != null && homeRank != null) {
                when {
                    awayRank < homeRank -> -1  // away team has better rank
                    awayRank > homeRank -> 1   // home team has better rank
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
                useCBBRanks = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Defensive Stats
        SectionHeader("Defensive Stats")
        Spacer(modifier = Modifier.height(4.dp))

        matchup.comparisons?.sideBySide?.defense?.forEach { (key, stat) ->
            val awayValue = stat.away.value
            val awayRank = stat.away.rank
            val awayRankDisplay = stat.away.rankDisplay
            val homeValue = stat.home.value
            val homeRank = stat.home.rank
            val homeRankDisplay = stat.home.rankDisplay
            val label = stat.label

            // Use rank-based advantage (lower rank is better)
            val advantage = if (awayRank != null && homeRank != null) {
                when {
                    awayRank < homeRank -> -1
                    awayRank > homeRank -> 1
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
                useCBBRanks = true
            )
        }

        // Overall Stats (SRS, Net Rating, etc.)
        val overallStats = matchup.comparisons?.sideBySide?.overall

        if (!overallStats.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader("Overall Ratings")
            Spacer(modifier = Modifier.height(4.dp))

            overallStats.forEach { (key, stat) ->
                val awayValue = stat.away.value
                val awayRank = stat.away.rank
                val awayRankDisplay = stat.away.rankDisplay
                val homeValue = stat.home.value
                val homeRank = stat.home.rank
                val homeRankDisplay = stat.home.rankDisplay
                val label = stat.label

                // Use rank-based advantage (lower rank is better)
                val advantage = if (awayRank != null && homeRank != null) {
                    when {
                        awayRank < homeRank -> -1
                        awayRank > homeRank -> 1
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
                    useCBBRanks = true
                )
            }
        }
    }
}

/**
 * CBB Offense vs Defense view using shared FiveColumnRowWithRanks
 */
@Composable
private fun CBBOffenseVsDefenseView(matchup: CBBMatchup, awayOffense: Boolean) {
    val comparisons = if (awayOffense) {
        matchup.comparisons?.awayOffVsHomeDef
    } else {
        matchup.comparisons?.homeOffVsAwayDef
    }

    val offTeam = if (awayOffense) matchup.awayTeam else matchup.homeTeam
    val defTeam = if (awayOffense) matchup.homeTeam else matchup.awayTeam

    SectionHeader("${offTeam.abbreviation} Offense vs ${defTeam.abbreviation} Defense")
    Spacer(modifier = Modifier.height(4.dp))

    comparisons?.forEach { (key, stat) ->
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
                centerText = stat.offLabel,
                rightValue = defValue.formatStat(2),
                rightRank = defRank,
                rightRankDisplay = defRankDisplay,
                advantage = stat.advantage ?: 0,
                useCBBRanks = true
            )
        }
    }
}

