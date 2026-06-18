package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.components.FabOption
import com.joebad.fastbreak.ui.components.MultiOptionFab
import com.joebad.fastbreak.ui.components.ShareFab
import com.joebad.fastbreak.ui.QuadrantScatterPlot
import com.joebad.fastbreak.ui.TeamLegendEntry
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private enum class MlbCaptureTarget { PRE_GAME, POST_GAME }

private fun Double.formatStat(decimals: Int = 1): String {
    val multiplier = when (decimals) { 0 -> 1.0; 1 -> 10.0; 2 -> 100.0; 3 -> 1000.0; else -> 10.0 }
    val rounded = round(this * multiplier) / multiplier
    return when (decimals) {
        0 -> rounded.toInt().toString()
        else -> {
            val str = rounded.toString()
            if (str.contains('.')) { val parts = str.split('.'); "${parts[0]}.${parts[1].padEnd(decimals, '0').take(decimals)}" }
            else "$str.${"0".repeat(decimals)}"
        }
    }
}

private fun parseMlbSlashStat(value: String?): Double? {
    val trimmed = value?.trim()?.takeIf { it.isNotEmpty() && it != "-" } ?: return null
    return (if (trimmed.startsWith(".")) "0$trimmed" else trimmed).toDoubleOrNull()
}

@Composable
fun MLBMatchupWorksheet(
    visualization: MLBMatchupVisualization,
    modifier: Modifier = Modifier,
    pinnedTeams: List<PinnedTeam> = emptyList(),
    highlightedTeamCodes: Set<String> = emptySet(),
    onScheduleToggleHandlerChanged: ((ScheduleToggleHandler?) -> Unit)? = null
) {
    val mlbPinnedTeamCodes = remember(pinnedTeams, highlightedTeamCodes) {
        val pinned = pinnedTeams.filter { it.sport == "MLB" }.map { it.teamCode }.toSet()
        pinned + highlightedTeamCodes
    }

    // Group matchups by LocalDate in Eastern timezone, sorted, pinned first
    val matchupsByDate = remember(visualization.dataPoints, mlbPinnedTeamCodes) {
        visualization.dataPoints
            .groupBy { matchup ->
                try {
                    val instant = Instant.parse(matchup.gameDate)
                    val dt = instant.toLocalDateTime(TimeZone.of("America/New_York"))
                    LocalDate(dt.year, dt.monthNumber, dt.dayOfMonth)
                } catch (_: Exception) { LocalDate(2000, 1, 1) }
            }
            .entries.sortedBy { it.key }.associate { it.toPair() }
            .mapValues { (_, matchups) ->
                val pinned = matchups.filter { m ->
                    mlbPinnedTeamCodes.any { code ->
                        m.homeTeam.abbreviation.equals(code, ignoreCase = true) ||
                        m.awayTeam.abbreviation.equals(code, ignoreCase = true)
                    }
                }
                val rest = matchups.filter { it !in pinned }
                pinned + rest
            }
    }

    val dates = matchupsByDate.keys.toList()
    // Pick the initial date.
    //   * No deep-link team highlight: earliest date today-or-later (existing behavior).
    //   * Deep-link highlighted team: prefer the next future game for that team,
    //     else the most recently completed past game.
    val initialDateIndex = remember(dates, matchupsByDate, highlightedTeamCodes) {
        val todayDate = try {
            val today = kotlin.time.Clock.System.now()
                .toLocalDateTime(TimeZone.of("America/New_York"))
            LocalDate(today.year, today.monthNumber, today.dayOfMonth)
        } catch (_: Exception) { null }
        if (highlightedTeamCodes.isNotEmpty() && todayDate != null) {
            val candidateIndices = dates.mapIndexedNotNull { idx, date ->
                val hasTeam = matchupsByDate[date]?.any { matchup ->
                    highlightedTeamCodes.any { code ->
                        matchup.homeTeam.abbreviation.equals(code, ignoreCase = true) ||
                        matchup.awayTeam.abbreviation.equals(code, ignoreCase = true)
                    }
                } == true
                if (hasTeam) idx else null
            }
            val futureIdx = candidateIndices.firstOrNull { dates[it] >= todayDate }
            val pastIdx = candidateIndices.lastOrNull { dates[it] < todayDate }
            futureIdx ?: pastIdx ?: 0
        } else if (todayDate != null) {
            dates.indexOfFirst { it >= todayDate }.coerceAtLeast(0)
        } else {
            0
        }
    }
    var selectedDateIndex by remember { mutableIntStateOf(initialDateIndex) }

    val currentDate = dates.getOrNull(selectedDateIndex)
    val currentMatchups = currentDate?.let { matchupsByDate[it] } ?: emptyList()
    var selectedMatchupIndex by remember(currentDate) { mutableIntStateOf(0) }
    val selectedMatchup = currentMatchups.getOrNull(selectedMatchupIndex)

    // Stats/Charts tab
    var selectedTab by remember { mutableIntStateOf(0) }

    // Schedule toggle
    var isScheduleExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(isScheduleExpanded) {
        onScheduleToggleHandlerChanged?.invoke(
            ScheduleToggleHandler(isExpanded = isScheduleExpanded, toggle = { isScheduleExpanded = !isScheduleExpanded })
        )
    }
    DisposableEffect(Unit) { onDispose { onScheduleToggleHandlerChanged?.invoke(null) } }

    // Share state
    var captureTarget by remember { mutableStateOf<MlbCaptureTarget?>(null) }
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }

    // Chart share callbacks (for Charts tab MultiOptionFab)
    var cumRunDiffShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var weeklyPerfShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp)
            ) {
                // Schedule header (collapsible)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isScheduleExpanded,
                    enter = androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.shrinkVertically()
                ) {
                    Column {
                        // First row: Date badges — scroll today's date into center on initial load only
                        val dateScrollState = rememberScrollState()
                        LaunchedEffect(Unit) {
                            val badgeWidth = 66
                            val screenCenter = (screenWidth.value / 2).toInt()
                            val targetScroll = (selectedDateIndex * badgeWidth - screenCenter + badgeWidth / 2).coerceAtLeast(0)
                            dateScrollState.scrollTo(targetScroll)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(dateScrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dates.forEachIndexed { index, date ->
                                DateBadge(
                                    date = date,
                                    isSelected = selectedDateIndex == index,
                                    onClick = { selectedDateIndex = index; selectedMatchupIndex = 0 }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Second row: Stats/Charts toggle + Matchup badges
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TeamStatsNavBadge("Stats", selectedTab == 0) { selectedTab = 0 }
                                TeamStatsNavBadge("Charts", selectedTab == 1) { selectedTab = 1 }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                currentMatchups.forEachIndexed { index, matchup ->
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
                if (selectedMatchup != null) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        when (selectedTab) {
                            0 -> MLBMatchupContent(matchup = selectedMatchup, modifier = Modifier.fillMaxSize())
                            1 -> {
                                // Charts tab
                                MLBChartsTab(
                                    awayTeam = selectedMatchup.awayTeam.abbreviation,
                                    homeTeam = selectedMatchup.homeTeam.abbreviation,
                                    matchup = selectedMatchup,
                                    leagueCumRunDiffStats = visualization.leagueCumRunDiffStats,
                                    leagueWeeklyStats = visualization.leagueWeeklyStats,
                                    onCumRunDiffShareClick = { callback -> cumRunDiffShareCallback = callback },
                                    onWeeklyPerfShareClick = { callback -> weeklyPerfShareCallback = callback }
                                )
                            }
                        }

                        // Pinned header (shared with NBA/NHL)
                        if (selectedTab == 0) {
                            PinnedMatchupHeader(
                                awayTeam = selectedMatchup.awayTeam.abbreviation,
                                homeTeam = selectedMatchup.homeTeam.abbreviation,
                                awayScore = selectedMatchup.results?.awayScore,
                                homeScore = selectedMatchup.results?.homeScore,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a matchup", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Share FAB
            if (selectedMatchup != null) {
                when {
                    // Charts tab: show chart share options
                    selectedTab == 1 -> {
                        val chartOptions = listOf(
                            FabOption(
                                icon = Icons.Filled.Star,
                                label = "Cumulative Run Diff",
                                onClick = { cumRunDiffShareCallback?.invoke() }
                            ),
                            FabOption(
                                icon = Icons.Filled.PlayArrow,
                                label = "Weekly Performance",
                                onClick = { weeklyPerfShareCallback?.invoke() }
                            )
                        )
                        MultiOptionFab(
                            options = chartOptions,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        )
                    }
                    // Stats tab with results: show Pre Game and Post Game options
                    selectedTab == 0 && selectedMatchup.comparisons != null -> {
                        val hasResults = selectedMatchup.gameCompleted && selectedMatchup.results?.teamBoxScore != null
                        if (hasResults) {
                            MultiOptionFab(
                                options = listOf(
                                    FabOption(icon = Icons.Filled.PlayArrow, label = "Pre Game",
                                        onClick = { captureTarget = MlbCaptureTarget.PRE_GAME }),
                                    FabOption(icon = Icons.Filled.Check, label = "Post Game",
                                        onClick = { captureTarget = MlbCaptureTarget.POST_GAME })
                                ),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                            )
                        } else {
                            ShareFab(
                                onClick = { captureTarget = MlbCaptureTarget.PRE_GAME },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                            )
                        }
                    }
                }
            }

            // Off-screen capture
            captureTarget?.let { target ->
                val mu = selectedMatchup ?: return@let
                val shareTitle = "${mu.awayTeam.abbreviation} @ ${mu.homeTeam.abbreviation}" +
                    if (target == MlbCaptureTarget.POST_GAME) " - Results" else ""

                val captureWidth = when (target) {
                    MlbCaptureTarget.PRE_GAME -> 3400.dp
                    MlbCaptureTarget.POST_GAME -> 420.dp
                }

                LaunchedEffect(target) {
                    kotlinx.coroutines.delay(50)
                    try { val bmp = graphicsLayer.toImageBitmap(); imageExporter.shareImage(bmp, shareTitle) }
                    catch (e: Exception) { e.printStackTrace() } finally { captureTarget = null }
                }

                when (target) {
                    MlbCaptureTarget.PRE_GAME -> {
                CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                    Box(modifier = Modifier
                        .requiredWidth(captureWidth)
                        .wrapContentSize(unbounded = true)
                        .offset { IntOffset(-10000, 0) }
                        .drawWithContent {
                            graphicsLayer.record(
                                size = IntSize(size.width.toInt(), size.height.toInt())
                            ) { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        }) {
                        val comp = mu.comparisons ?: return@Box
                        val awayAbbrev = mu.awayTeam.abbreviation
                        val homeAbbrev = mu.homeTeam.abbreviation
                        val shareDate = try {
                            formatBracketGameDate(mu.gameDate) ?: mu.gameDate.substringBefore("T")
                        } catch (_: Exception) { mu.gameDate.substringBefore("T") }
                        val shareOddsLine = mu.odds?.let { odds ->
                            listOfNotNull(odds.details?.takeIf { it.isNotBlank() }, odds.overUnder?.let { "O/U ${it.toInt()}" })
                                .joinToString(" • ")
                        } ?: ""
                        val gameInfo = ShareGameInfo(
                            awayTeam = awayAbbrev, homeTeam = homeAbbrev,
                            eventLabel = shareDate, formattedDate = shareOddsLine,
                            source = "ESPN",
                            awayRecord = mu.awayTeam.record, homeRecord = mu.homeTeam.record,
                            recordsBelowTeamName = true
                        )

                        fun buildStatBox(title: String, stats: Map<String, SideBySideStatComparison>?): ShareStatBox {
                            return ShareStatBox(title = title, fiveColStats = stats?.map { entry ->
                                val stat = entry.value
                                val adv = if (stat.away.rank != null && stat.home.rank != null) when {
                                    stat.away.rank < stat.home.rank -> -1; stat.away.rank > stat.home.rank -> 1; else -> 0
                                } else 0
                                ShareFiveColStat(
                                    stat.away.value?.formatStat(3) ?: "-", stat.away.rank, stat.away.rankDisplay,
                                    stat.label,
                                    stat.home.value?.formatStat(3) ?: "-", stat.home.rank, stat.home.rankDisplay, adv)
                            }?.take(9) ?: emptyList())
                        }

                        fun buildOvdBox(title: String, leftLabel: String, rightLabel: String,
                                        stats: Map<String, MatchupStatComparison>,
                                        lc: Color = Team1Color, rc: Color = Team2Color): ShareStatBox {
                            return ShareStatBox(title = title, leftLabel = leftLabel, middleLabel = "vs", rightLabel = rightLabel,
                                leftColor = lc, rightColor = rc,
                                fiveColStats = stats.map { entry ->
                                    val s = entry.value
                                    ShareFiveColStat(
                                        s.offense.value?.formatStat(3) ?: "-", s.offense.rank, s.offense.rankDisplay,
                                        s.offLabel,
                                        s.defense.value?.formatStat(3) ?: "-", s.defense.rank, s.defense.rankDisplay, s.advantage ?: 0)
                                }.take(9))
                        }

                        val statBoxes = buildList {
                            add(buildStatBox("Batting Stats", comp.sideBySide?.offense))
                            add(buildStatBox("Pitching Stats", comp.sideBySide?.defense))
                            add(buildMLBTrendShareBox(mu))
                            add(buildOvdBox("$awayAbbrev Bat vs $homeAbbrev Pitch", "$awayAbbrev Bat", "$homeAbbrev Pitch", comp.awayOffVsHomeDef))
                            add(buildOvdBox("$homeAbbrev Bat vs $awayAbbrev Pitch", "$homeAbbrev Bat", "$awayAbbrev Pitch", comp.homeOffVsAwayDef, Team2Color, Team1Color))
                            add(buildMLBH2HShareBox(mu))
                        }
                        val finalBoxes = statBoxes + List((6 - statBoxes.size).coerceAtLeast(0)) { ShareStatBox(title = "", fiveColStats = emptyList()) }
                        GenericMatchupShareImage(
                            gameInfo = gameInfo,
                            statBoxes = finalBoxes.take(6),
                            modifier = Modifier.requiredWidth(captureWidth).wrapContentHeight(),
                            rowSpacing = 48.dp,
                            firstRowWeight = 1.5f,
                            secondRowWeight = 1f,
                            dynamicHeight = true
                        )
                    }
                }
                    }
                    MlbCaptureTarget.POST_GAME -> {
                        CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                            Box(modifier = Modifier
                                .requiredWidth(captureWidth)
                                .wrapContentSize(unbounded = true)
                                .offset { IntOffset(-10000, 0) }
                                .drawWithContent {
                                    graphicsLayer.record(
                                        size = IntSize(size.width.toInt(), size.height.toInt())
                                    ) { this@drawWithContent.drawContent() }
                                    drawLayer(graphicsLayer)
                                }
                                .background(MaterialTheme.colorScheme.background)) {
                                if (mu.gameCompleted && mu.results != null) {
                                    MLBPostGameShareContent(
                                        matchup = mu,
                                        modifier = Modifier.requiredWidth(captureWidth)
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

@Composable
private fun MLBMatchupContent(matchup: MLBMatchup, modifier: Modifier = Modifier) {
    var viewSelection by remember { mutableIntStateOf(0) }
    val comparisons = matchup.comparisons
    val homeTeam = matchup.homeTeam
    val awayTeam = matchup.awayTeam

    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, top = 36.dp)) {
        // Record / location / odds — three rows, all using the same
        // away-left / home-right horizontal alignment as the record row.
        MLBMatchupMetaRows(matchup = matchup, awayTeam = awayTeam, homeTeam = homeTeam)

        // Post-game box score (if available)
        if (matchup.results?.teamBoxScore != null) {
            Spacer(modifier = Modifier.height(8.dp))
            MLBBoxScoreSection(results = matchup.results, awayAbbrev = awayTeam.abbreviation, homeAbbrev = homeTeam.abbreviation)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stat comparison tabs
        if (comparisons != null) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TeamStatsNavBadge("Team", viewSelection == 0) { viewSelection = 0 }
                TeamStatsNavBadge("${awayTeam.abbreviation} Bat vs ${homeTeam.abbreviation} Pitch", viewSelection == 1) { viewSelection = 1 }
                TeamStatsNavBadge("${homeTeam.abbreviation} Bat vs ${awayTeam.abbreviation} Pitch", viewSelection == 2) { viewSelection = 2 }
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (viewSelection) {
                0 -> MLBTeamStatsView(comparisons, awayTeam, homeTeam)
                1 -> BracketOffenseVsDefenseView(comparisons.awayOffVsHomeDef, awayTeam.name, homeTeam.name, ::mlbRankColor)
                2 -> BracketOffenseVsDefenseView(comparisons.homeOffVsAwayDef, homeTeam.name, awayTeam.name, ::mlbRankColor)
            }
        }

        // Head-to-Head
        if (matchup.h2h != null && matchup.h2h.totalGames > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            MLBH2HSection(h2h = matchup.h2h)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun MLBTeamStatsView(comparisons: MatchupComparisons, awayTeam: MLBTeamInfo, homeTeam: MLBTeamInfo) {
    val sideBySide = comparisons.sideBySide ?: return

    // Batting (offense)
    if (sideBySide.offense.isNotEmpty()) {
        SectionHeader("Batting")
        Spacer(modifier = Modifier.height(4.dp))
        sideBySide.offense.forEach { (_, stat) ->
            val advantage = if (stat.away.rank != null && stat.home.rank != null) {
                when { stat.away.rank < stat.home.rank -> -1; stat.away.rank > stat.home.rank -> 1; else -> 0 }
            } else 0
            FiveColumnRowWithRanks(
                leftValue = stat.away.value?.formatStat(3) ?: "-",
                leftRank = stat.away.rank, leftRankDisplay = stat.away.rankDisplay,
                centerText = stat.label,
                rightValue = stat.home.value?.formatStat(3) ?: "-",
                rightRank = stat.home.rank, rightRankDisplay = stat.home.rankDisplay,
                advantage = advantage, rankColorFn = ::mlbRankColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Pitching
    if (sideBySide.defense.isNotEmpty()) {
        SectionHeader("Pitching")
        Spacer(modifier = Modifier.height(4.dp))
        sideBySide.defense.forEach { (_, stat) ->
            val advantage = if (stat.away.rank != null && stat.home.rank != null) {
                when { stat.away.rank < stat.home.rank -> -1; stat.away.rank > stat.home.rank -> 1; else -> 0 }
            } else 0
            FiveColumnRowWithRanks(
                leftValue = stat.away.value?.formatStat(3) ?: "-",
                leftRank = stat.away.rank, leftRankDisplay = stat.away.rankDisplay,
                centerText = stat.label,
                rightValue = stat.home.value?.formatStat(3) ?: "-",
                rightRank = stat.home.rank, rightRankDisplay = stat.home.rankDisplay,
                advantage = advantage, rankColorFn = ::mlbRankColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Fielding (overall)
    if (sideBySide.overall.isNotEmpty()) {
        SectionHeader("Fielding")
        Spacer(modifier = Modifier.height(4.dp))
        sideBySide.overall.forEach { (_, stat) ->
            val advantage = if (stat.away.rank != null && stat.home.rank != null) {
                when { stat.away.rank < stat.home.rank -> -1; stat.away.rank > stat.home.rank -> 1; else -> 0 }
            } else 0
            FiveColumnRowWithRanks(
                leftValue = stat.away.value?.formatStat(3) ?: "-",
                leftRank = stat.away.rank, leftRankDisplay = stat.away.rankDisplay,
                centerText = stat.label,
                rightValue = stat.home.value?.formatStat(3) ?: "-",
                rightRank = stat.home.rank, rightRankDisplay = stat.home.rankDisplay,
                advantage = advantage, rankColorFn = ::mlbRankColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // One Month Trend
    MLBOneMonthTrendSection(awayTeam = awayTeam, homeTeam = homeTeam)
}

@Composable
private fun MLBMatchupMetaRows(
    matchup: MLBMatchup,
    awayTeam: MLBTeamInfo,
    homeTeam: MLBTeamInfo
) {
    val textStyle = MaterialTheme.typography.labelSmall
    val recordColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    val oddsColor = MaterialTheme.colorScheme.primary

    val oddsLine = matchup.odds?.let { odds ->
        listOfNotNull(odds.details?.takeIf { it.isNotBlank() }, odds.overUnder?.let { "O/U $it" })
            .joinToString(" • ").takeIf { it.isNotBlank() }
    }

    // Row 1: record | odds | record
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            awayTeam.record ?: "",
            style = textStyle, fontSize = 11.sp, color = recordColor,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Start
        )
        Text(
            oddsLine ?: "",
            style = textStyle, fontSize = 11.sp, color = oddsColor,
            maxLines = 1, softWrap = false,
            textAlign = TextAlign.Center
        )
        Text(
            homeTeam.record ?: "",
            style = textStyle, fontSize = 11.sp, color = recordColor,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End
        )
    }

    // Row 2: division | | division
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            awayTeam.division ?: "",
            style = textStyle, fontSize = 11.sp, color = metaColor,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Start
        )
        Text(
            homeTeam.division ?: "",
            style = textStyle, fontSize = 11.sp, color = metaColor,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End
        )
    }
}

// MLB rank colors (30 teams)
private fun mlbRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    return when {
        rank <= 5 -> { val r = (rank - 1) / 4f; Color((0 + r * 80).toInt(), (150 - r * 25).toInt(), (42 - r * 32).toInt()) }
        rank <= 15 -> { val r = (rank - 6) / 9f; Color((255 - r * 55).toInt(), (160 - r * 60).toInt(), 0) }
        else -> { val r = ((rank - 16).coerceAtMost(14)) / 14f; Color((200 - r * 61).toInt(), (50 - r * 50).toInt(), 0) }
    }
}

// ============================================================================
// One Month Trend
// ============================================================================

private data class MLBMonthTrendData(
    val wins: Int,
    val losses: Int,
    val recordRank: Int?,
    val recordRankDisplay: String?,
    val runsPerGame: Double?,
    val runsPerGameRank: Int?,
    val runsPerGameRankDisplay: String?,
    val runsAllowedPerGame: Double?,
    val runsAllowedPerGameRank: Int?,
    val runsAllowedPerGameRankDisplay: String?,
    val runDiffPerGame: Double?,
    val runDiffPerGameRank: Int?,
    val runDiffPerGameRankDisplay: String?,
    val hitsPerGame: Double?,
    val hitsPerGameRank: Int?,
    val hitsPerGameRankDisplay: String?,
    val hrsPerGame: Double?,
    val hrsPerGameRank: Int?,
    val hrsPerGameRankDisplay: String?
)

private fun parseMLBMonthTrend(stats: JsonObject?): MLBMonthTrendData? {
    if (stats == null) return null
    val monthTrend = stats["monthTrend"]
    if (monthTrend !is JsonObject) return null

    val record = monthTrend["record"] as? JsonObject
    val wins = (record?.get("wins") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    val losses = (record?.get("losses") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    val recordRank = (record?.get("rank") as? JsonPrimitive)?.content?.toIntOrNull()
    val recordRankDisplay = (record?.get("rankDisplay") as? JsonPrimitive)?.content

    fun parseStat(key: String): Triple<Double?, Int?, String?> {
        val obj = monthTrend[key] as? JsonObject ?: return Triple(null, null, null)
        val value = (obj["value"] as? JsonPrimitive)?.doubleOrNull
        val rank = (obj["rank"] as? JsonPrimitive)?.content?.toIntOrNull()
        val rankDisplay = (obj["rankDisplay"] as? JsonPrimitive)?.content
        return Triple(value, rank, rankDisplay)
    }

    val rpg = parseStat("runsPerGame")
    val rapg = parseStat("runsAllowedPerGame")
    val rdpg = parseStat("runDiffPerGame")
    val hpg = parseStat("hitsPerGame")
    val hrpg = parseStat("hrsPerGame")

    return MLBMonthTrendData(
        wins = wins, losses = losses, recordRank = recordRank, recordRankDisplay = recordRankDisplay,
        runsPerGame = rpg.first, runsPerGameRank = rpg.second, runsPerGameRankDisplay = rpg.third,
        runsAllowedPerGame = rapg.first, runsAllowedPerGameRank = rapg.second, runsAllowedPerGameRankDisplay = rapg.third,
        runDiffPerGame = rdpg.first, runDiffPerGameRank = rdpg.second, runDiffPerGameRankDisplay = rdpg.third,
        hitsPerGame = hpg.first, hitsPerGameRank = hpg.second, hitsPerGameRankDisplay = hpg.third,
        hrsPerGame = hrpg.first, hrsPerGameRank = hrpg.second, hrsPerGameRankDisplay = hrpg.third
    )
}

// ============================================================================
// Chart Data Parsing Helpers
// ============================================================================

// Weekly performance data for scatter plot
private data class MLBWeeklyPerformance(
    val weekNum: Int,
    val runsScored: Double,
    val runsAllowed: Double
)

// Helper to parse cumulative run differential by week (for line chart)
private fun parseCumRunDiffByWeek(stats: JsonObject?): List<LineChartDataPoint> {
    val dataPoints = mutableListOf<LineChartDataPoint>()
    if (stats == null) return dataPoints

    val cumRunDiff = stats["cumRunDiffByWeek"]
    if (cumRunDiff is JsonObject) {
        cumRunDiff.forEach { (weekKey, value) ->
            val weekNum = weekKey.removePrefix("week-").toIntOrNull()
            val runDiff = (value as? JsonPrimitive)?.doubleOrNull
            if (weekNum != null && runDiff != null) {
                dataPoints.add(LineChartDataPoint(x = weekNum.toDouble(), y = runDiff))
            }
        }
    }
    return dataPoints.sortedBy { it.x }
}

// Helper to parse weekly performance data (for scatter plot)
private fun parsePerformanceByWeek(stats: JsonObject?): List<MLBWeeklyPerformance> {
    val perfList = mutableListOf<MLBWeeklyPerformance>()
    if (stats == null) return perfList

    val perf = stats["performanceByWeek"]
    if (perf is JsonObject) {
        perf.forEach { (weekKey, value) ->
            val weekNum = weekKey.removePrefix("week-").toIntOrNull()
            if (weekNum != null && value is JsonObject) {
                val runsScored = (value["runsScored"] as? JsonPrimitive)?.doubleOrNull
                val runsAllowed = (value["runsAllowed"] as? JsonPrimitive)?.doubleOrNull
                if (runsScored != null && runsAllowed != null) {
                    perfList.add(MLBWeeklyPerformance(weekNum, runsScored, runsAllowed))
                }
            }
        }
    }
    return perfList.sortedBy { it.weekNum }
}

@Composable
private fun MLBOneMonthTrendSection(awayTeam: MLBTeamInfo, homeTeam: MLBTeamInfo) {
    val awayTrend = parseMLBMonthTrend(awayTeam.stats)
    val homeTrend = parseMLBMonthTrend(homeTeam.stats)

    if (awayTrend == null && homeTrend == null) return

    val rankAdv = { a: Int?, h: Int? ->
        if (a != null && h != null) when {
            a < h -> -1; h < a -> 1; else -> 0
        } else 0
    }

    SectionHeader("One Month Trend")
    Spacer(modifier = Modifier.height(4.dp))

    // Record
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.let { "${it.wins}-${it.losses}" } ?: "-",
        leftRank = awayTrend?.recordRank, leftRankDisplay = awayTrend?.recordRankDisplay,
        centerText = "Record",
        rightValue = homeTrend?.let { "${it.wins}-${it.losses}" } ?: "-",
        rightRank = homeTrend?.recordRank, rightRankDisplay = homeTrend?.recordRankDisplay,
        advantage = rankAdv(awayTrend?.recordRank, homeTrend?.recordRank),
        rankColorFn = ::mlbRankColor
    )

    // Run Diff/Game
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.runDiffPerGame?.let { if (it >= 0) "+${it.formatStat(2)}" else it.formatStat(2) } ?: "-",
        leftRank = awayTrend?.runDiffPerGameRank, leftRankDisplay = awayTrend?.runDiffPerGameRankDisplay,
        centerText = "Run Diff/G",
        rightValue = homeTrend?.runDiffPerGame?.let { if (it >= 0) "+${it.formatStat(2)}" else it.formatStat(2) } ?: "-",
        rightRank = homeTrend?.runDiffPerGameRank, rightRankDisplay = homeTrend?.runDiffPerGameRankDisplay,
        advantage = rankAdv(awayTrend?.runDiffPerGameRank, homeTrend?.runDiffPerGameRank),
        rankColorFn = ::mlbRankColor
    )

    // Runs/Game
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.runsPerGame?.formatStat(2) ?: "-",
        leftRank = awayTrend?.runsPerGameRank, leftRankDisplay = awayTrend?.runsPerGameRankDisplay,
        centerText = "Runs/G",
        rightValue = homeTrend?.runsPerGame?.formatStat(2) ?: "-",
        rightRank = homeTrend?.runsPerGameRank, rightRankDisplay = homeTrend?.runsPerGameRankDisplay,
        advantage = rankAdv(awayTrend?.runsPerGameRank, homeTrend?.runsPerGameRank),
        rankColorFn = ::mlbRankColor
    )

    // Runs Allowed/Game
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.runsAllowedPerGame?.formatStat(2) ?: "-",
        leftRank = awayTrend?.runsAllowedPerGameRank, leftRankDisplay = awayTrend?.runsAllowedPerGameRankDisplay,
        centerText = "RA/G",
        rightValue = homeTrend?.runsAllowedPerGame?.formatStat(2) ?: "-",
        rightRank = homeTrend?.runsAllowedPerGameRank, rightRankDisplay = homeTrend?.runsAllowedPerGameRankDisplay,
        advantage = rankAdv(awayTrend?.runsAllowedPerGameRank, homeTrend?.runsAllowedPerGameRank),
        rankColorFn = ::mlbRankColor
    )

    // Hits/Game
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.hitsPerGame?.formatStat(2) ?: "-",
        leftRank = awayTrend?.hitsPerGameRank, leftRankDisplay = awayTrend?.hitsPerGameRankDisplay,
        centerText = "Hits/G",
        rightValue = homeTrend?.hitsPerGame?.formatStat(2) ?: "-",
        rightRank = homeTrend?.hitsPerGameRank, rightRankDisplay = homeTrend?.hitsPerGameRankDisplay,
        advantage = rankAdv(awayTrend?.hitsPerGameRank, homeTrend?.hitsPerGameRank),
        rankColorFn = ::mlbRankColor
    )

    // HR/Game
    FiveColumnRowWithRanks(
        leftValue = awayTrend?.hrsPerGame?.formatStat(2) ?: "-",
        leftRank = awayTrend?.hrsPerGameRank, leftRankDisplay = awayTrend?.hrsPerGameRankDisplay,
        centerText = "HR/G",
        rightValue = homeTrend?.hrsPerGame?.formatStat(2) ?: "-",
        rightRank = homeTrend?.hrsPerGameRank, rightRankDisplay = homeTrend?.hrsPerGameRankDisplay,
        advantage = rankAdv(awayTrend?.hrsPerGameRank, homeTrend?.hrsPerGameRank),
        rankColorFn = ::mlbRankColor
    )
}

// ============================================================================
// Charts Tab
// ============================================================================

@Composable
private fun MLBChartsTab(
    awayTeam: String,
    homeTeam: String,
    matchup: MLBMatchup,
    leagueCumRunDiffStats: LeagueCumRunDiffStats? = null,
    leagueWeeklyStats: LeagueWeeklyStats? = null,
    onCumRunDiffShareClick: ((() -> Unit)?) -> Unit = {},
    onWeeklyPerfShareClick: ((() -> Unit)?) -> Unit = {}
) {
    val awayStats = matchup.awayTeam.stats
    val homeStats = matchup.homeTeam.stats

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 36.dp)
    ) {
        // Chart 1: Cumulative Run Differential
        MLBCumRunDiffChart(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayStats = awayStats,
            homeStats = homeStats,
            leagueCumRunDiffStats = leagueCumRunDiffStats,
            onShareClick = onCumRunDiffShareClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Chart 2: Weekly Performance (Runs Scored vs Runs Allowed)
        MLBWeeklyPerformanceChart(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayStats = awayStats,
            homeStats = homeStats,
            leagueWeeklyStats = leagueWeeklyStats,
            onShareClick = onWeeklyPerfShareClick
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun MLBCumRunDiffChart(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject?,
    homeStats: JsonObject?,
    leagueCumRunDiffStats: LeagueCumRunDiffStats? = null,
    onShareClick: ((() -> Unit)?) -> Unit = {}
) {
    Text(
        text = "Cumulative Run Differential Over Season",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    val awayDataPoints = parseCumRunDiffByWeek(awayStats)
    val homeDataPoints = parseCumRunDiffByWeek(homeStats)

    if (awayDataPoints.isEmpty() && homeDataPoints.isEmpty()) {
        Text(
            text = "Cumulative run differential data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val title = "$awayTeam vs $homeTeam - Cumulative Run Diff"

    // Parse top 10 threshold by week (historical series)
    val top10DataPoints = mutableListOf<LineChartDataPoint>()
    val top10ByWeek = leagueCumRunDiffStats?.top10ByWeek
    if (top10ByWeek != null) {
        for ((weekKey, value) in top10ByWeek) {
            val weekNum = weekKey.removePrefix("week-").toIntOrNull()
            val threshold = (value as? JsonPrimitive)?.doubleOrNull
            if (weekNum != null && threshold != null) {
                top10DataPoints.add(LineChartDataPoint(x = weekNum.toDouble(), y = threshold))
            }
        }
    }
    val sortedTop10 = top10DataPoints.sortedBy { it.x }

    // Build series list - include top 10 if we have data
    val seriesList = mutableListOf(
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
    if (sortedTop10.isNotEmpty()) {
        seriesList.add(
            LineChartSeries(
                label = "Top 10",
                dataPoints = sortedTop10,
                color = "#4CAF50", // Green
                dashed = true
            )
        )
    }

    ShareableChartContainer(
        title = title,
        source = "ESPN",
        showShareButton = false,
        onShareClick = onShareClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp)
            .padding(8.dp)
    ) {
        LineChartComponent(
            series = seriesList,
            yAxisTitle = "Run Diff",
            title = title,
            source = "ESPN",
            // Use league-wide stats for consistent Y-axis scaling across all matchups
            customYMin = leagueCumRunDiffStats?.minCumRunDiff?.toFloat(),
            customYMax = leagueCumRunDiffStats?.maxCumRunDiff?.toFloat(),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun MLBWeeklyPerformanceChart(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject?,
    homeStats: JsonObject?,
    leagueWeeklyStats: LeagueWeeklyStats? = null,
    onShareClick: ((() -> Unit)?) -> Unit = {}
) {
    // State for week filter: 0 = Last 10 weeks (all), 1 = Last 5 weeks, 2 = Prior 5 weeks
    var weekFilter by remember { mutableStateOf(0) }

    // Parse all weekly performance data
    val awayPerf = parsePerformanceByWeek(awayStats)
    val homePerf = parsePerformanceByWeek(homeStats)

    if (awayPerf.isEmpty() && homePerf.isEmpty()) {
        Text(
            text = "Weekly performance data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Get the max week number to determine week ranges
    val maxWeek = maxOf(
        awayPerf.maxOfOrNull { it.weekNum } ?: 0,
        homePerf.maxOfOrNull { it.weekNum } ?: 0
    )

    // Filter data based on selection
    val filteredAwayPerf = when (weekFilter) {
        0 -> awayPerf.filter { it.weekNum > maxWeek - 10 } // Last 10 weeks (all)
        1 -> awayPerf.filter { it.weekNum > maxWeek - 5 } // Last 5 weeks
        2 -> awayPerf.filter { it.weekNum <= maxWeek - 5 && it.weekNum > maxWeek - 10 } // Prior 5 weeks
        else -> awayPerf
    }

    val filteredHomePerf = when (weekFilter) {
        0 -> homePerf.filter { it.weekNum > maxWeek - 10 } // Last 10 weeks (all)
        1 -> homePerf.filter { it.weekNum > maxWeek - 5 } // Last 5 weeks
        2 -> homePerf.filter { it.weekNum <= maxWeek - 5 && it.weekNum > maxWeek - 10 } // Prior 5 weeks
        else -> homePerf
    }

    Column {
        // Title
        Text(
            text = "Weekly Performance (Runs Scored vs Allowed)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Week filter badges
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

        val title = "$awayTeam vs $homeTeam - Weekly Performance"

        // Convert to scatter plot data points
        // X = Runs Scored, Y = Runs Allowed
        // Better teams: high X (more runs), low Y (fewer runs allowed) -> bottom-right quadrant
        val scatterData = mutableListOf<ScatterPlotDataPoint>()

        filteredAwayPerf.forEach { perf ->
            scatterData.add(
                ScatterPlotDataPoint(
                    label = "$awayTeam W${perf.weekNum}",
                    x = perf.runsScored,
                    y = perf.runsAllowed,
                    sum = perf.runsScored - perf.runsAllowed, // Run differential
                    teamCode = awayTeam,
                    color = "#2196F3"
                )
            )
        }

        filteredHomePerf.forEach { perf ->
            scatterData.add(
                ScatterPlotDataPoint(
                    label = "$homeTeam W${perf.weekNum}",
                    x = perf.runsScored,
                    y = perf.runsAllowed,
                    sum = perf.runsScored - perf.runsAllowed, // Run differential
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
            QuadrantScatterPlot(
                data = scatterData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(540.dp)
                    .padding(8.dp),
                title = title,
                xAxisLabel = "Runs Scored / Game",
                yAxisLabel = "Runs Allowed / Game",
                invertYAxis = true, // Lower runs allowed is better, so invert
                highlightedTeamCodes = setOf(awayTeam, homeTeam),
                showShareButton = false,
                onShareClick = onShareClick,
                // Quadrant labels for MLB (with inverted Y)
                // Top = good defense (low runs allowed), Bottom = poor defense (high runs allowed)
                quadrantTopRight = QuadrantConfig(label = "Dominant", color = "#4CAF50", lightModeColor = "#4CAF50"),
                quadrantTopLeft = QuadrantConfig(label = "Defensive", color = "#2196F3", lightModeColor = "#2196F3"),
                quadrantBottomLeft = QuadrantConfig(label = "Struggling", color = "#F44336", lightModeColor = "#F44336"),
                quadrantBottomRight = QuadrantConfig(label = "Offensive", color = "#FF9800", lightModeColor = "#FF9800"),
                // Use league-wide stats for consistent scaling across all matchups
                customCenterX = leagueWeeklyStats?.avgRunsScored,
                customCenterY = leagueWeeklyStats?.avgRunsAllowed,
                customXMin = leagueWeeklyStats?.minRunsScored,
                customXMax = leagueWeeklyStats?.maxRunsScored,
                customYMin = leagueWeeklyStats?.minRunsAllowed,
                customYMax = leagueWeeklyStats?.maxRunsAllowed,
                regressionData = scatterData,
                source = "ESPN",
                teamLegendItems = listOf(
                    TeamLegendEntry(awayTeam, Color(0xFF2196F3)),
                    TeamLegendEntry(homeTeam, Color(0xFFFF5722))
                )
            )
        }
    }
}

// ============================================================================
// Head-to-Head Section
// ============================================================================

@Composable
private fun MLBH2HSection(h2h: MLBH2H) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionHeader("Head-to-Head (${h2h.teamAWins}-${h2h.teamBWins})")
    Spacer(modifier = Modifier.height(4.dp))

    h2h.series.forEach { series ->
        // Series row: away wins | date range | home wins
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Away team wins in this series
            val awayWon = series.teamAWins > series.teamBWins
            val homeWon = series.teamBWins > series.teamAWins
            Text(
                text = "${series.teamAWins}W",
                fontSize = 13.sp,
                fontWeight = if (awayWon) FontWeight.Bold else FontWeight.Normal,
                color = if (awayWon) Color(0xFF4CAF50) else textColor,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Start
            )

            // Date range + game scores
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = series.dateRange,
                    fontSize = 11.sp,
                    color = mutedColor
                )
                // Individual game scores
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    series.games.forEach { game ->
                        val isAwayTeamA = game.awayTeam == h2h.teamA
                        val teamAScore = if (isAwayTeamA) game.awayScore else game.homeScore
                        val teamBScore = if (isAwayTeamA) game.homeScore else game.awayScore
                        val winColor = if (game.winner == h2h.teamA) Team1Color else Team2Color
                        Text(
                            text = "$teamAScore-$teamBScore",
                            fontSize = 11.sp,
                            color = winColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Home team wins in this series
            Text(
                text = "${series.teamBWins}W",
                fontSize = 13.sp,
                fontWeight = if (homeWon) FontWeight.Bold else FontWeight.Normal,
                color = if (homeWon) Color(0xFF4CAF50) else textColor,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

// ============================================================================
// Post-game box score section
// ============================================================================

@Composable
private fun MLBBoxScoreSection(results: MLBGameResults, awayAbbrev: String, homeAbbrev: String) {
    val awayBox = results.teamBoxScore?.away ?: return
    val homeBox = results.teamBoxScore?.home ?: return
    val vsAvg = results.vsSeasonAvg
    val awaySH = results.seasonHighs?.away
    val homeSH = results.seasonHighs?.home

    fun formatDiff(stat: MLBVsSeasonAvgStat?): String {
        val diff = stat?.difference ?: return ""
        val prefix = if (diff >= 0) "+" else ""
        return " (${prefix}${diff.formatStat(1)})"
    }

    fun formatDiffPrefix(stat: MLBVsSeasonAvgStat?): String {
        val diff = stat?.difference ?: return ""
        val prefix = if (diff >= 0) "+" else ""
        return "(${prefix}${diff.formatStat(1)}) "
    }

    fun intAdv(a: Int?, h: Int?, higher: Boolean = true): Int {
        if (a == null || h == null) return 0
        return if (higher) { when { a > h -> -1; h > a -> 1; else -> 0 } }
        else { when { a < h -> -1; h < a -> 1; else -> 0 } }
    }

    SectionHeader("Box Score (vs Season Avg)")
    Spacer(modifier = Modifier.height(4.dp))

    // Runs
    ThreeColumnRow(
        leftText = "${awayBox.runs ?: "-"}${formatDiff(vsAvg?.away?.runs)}",
        centerText = "R", rightText = "${formatDiffPrefix(vsAvg?.home?.runs)}${homeBox.runs ?: "-"}",
        advantage = intAdv(awayBox.runs, homeBox.runs),
        leftSeasonHigh = awaySH?.get("runs"), rightSeasonHigh = homeSH?.get("runs")
    )
    // Hits
    ThreeColumnRow(
        leftText = "${awayBox.hits ?: "-"}${formatDiff(vsAvg?.away?.hits)}",
        centerText = "H", rightText = "${formatDiffPrefix(vsAvg?.home?.hits)}${homeBox.hits ?: "-"}",
        advantage = intAdv(awayBox.hits, homeBox.hits)
    )
    // Home Runs
    ThreeColumnRow(
        leftText = "${awayBox.homeRuns ?: "-"}${formatDiff(vsAvg?.away?.homeRuns)}",
        centerText = "HR", rightText = "${formatDiffPrefix(vsAvg?.home?.homeRuns)}${homeBox.homeRuns ?: "-"}",
        advantage = intAdv(awayBox.homeRuns, homeBox.homeRuns)
    )
    // RBI
    ThreeColumnRow(
        leftText = "${awayBox.rbis ?: "-"}", centerText = "RBI", rightText = "${homeBox.rbis ?: "-"}",
        advantage = intAdv(awayBox.rbis, homeBox.rbis)
    )
    // Doubles
    ThreeColumnRow(
        leftText = "${awayBox.doubles ?: "-"}", centerText = "2B", rightText = "${homeBox.doubles ?: "-"}",
        advantage = intAdv(awayBox.doubles, homeBox.doubles)
    )
    // Walks
    ThreeColumnRow(
        leftText = "${awayBox.walks ?: "-"}${formatDiff(vsAvg?.away?.walksBatting)}",
        centerText = "BB", rightText = "${formatDiffPrefix(vsAvg?.home?.walksBatting)}${homeBox.walks ?: "-"}",
        advantage = intAdv(awayBox.walks, homeBox.walks)
    )
    // Strikeouts (lower is better for batters)
    ThreeColumnRow(
        leftText = "${awayBox.strikeouts ?: "-"}${formatDiff(vsAvg?.away?.strikeoutsBatting)}",
        centerText = "K", rightText = "${formatDiffPrefix(vsAvg?.home?.strikeoutsBatting)}${homeBox.strikeouts ?: "-"}",
        advantage = intAdv(awayBox.strikeouts, homeBox.strikeouts, higher = false)
    )
    // Stolen Bases
    ThreeColumnRow(
        leftText = "${awayBox.stolenBases ?: "-"}", centerText = "SB", rightText = "${homeBox.stolenBases ?: "-"}",
        advantage = intAdv(awayBox.stolenBases, homeBox.stolenBases)
    )
    // LOB (lower is better)
    ThreeColumnRow(
        leftText = "${awayBox.runnersLOB ?: "-"}", centerText = "LOB", rightText = "${homeBox.runnersLOB ?: "-"}",
        advantage = intAdv(awayBox.runnersLOB, homeBox.runnersLOB, higher = false)
    )
    // Slash line
    ThreeColumnRow(leftText = awayBox.avg ?: "-", centerText = "AVG", rightText = homeBox.avg ?: "-")
    ThreeColumnRow(leftText = awayBox.obp ?: "-", centerText = "OBP", rightText = homeBox.obp ?: "-")
    ThreeColumnRow(leftText = awayBox.slg ?: "-", centerText = "SLG", rightText = homeBox.slg ?: "-")
    ThreeColumnRow(leftText = awayBox.ops ?: "-", centerText = "OPS", rightText = homeBox.ops ?: "-")
    // Pitching
    ThreeColumnRow(
        leftText = "${awayBox.pitchingStrikeouts ?: "-"}", centerText = "K (P)", rightText = "${homeBox.pitchingStrikeouts ?: "-"}",
        advantage = intAdv(awayBox.pitchingStrikeouts, homeBox.pitchingStrikeouts)
    )
    ThreeColumnRow(
        leftText = "${awayBox.pitchingWalks ?: "-"}", centerText = "BB (P)", rightText = "${homeBox.pitchingWalks ?: "-"}",
        advantage = intAdv(awayBox.pitchingWalks, homeBox.pitchingWalks, higher = false)
    )
    ThreeColumnRow(leftText = "${awayBox.pitches ?: "-"}", centerText = "Pitches", rightText = "${homeBox.pitches ?: "-"}")

    Spacer(modifier = Modifier.height(6.dp))
}

// ============================================================================
// Share image helpers
// ============================================================================

private fun buildMLBTrendShareBox(mu: MLBMatchup): ShareStatBox {
    val awayTrend = parseMLBMonthTrend(mu.awayTeam.stats)
    val homeTrend = parseMLBMonthTrend(mu.homeTeam.stats)
    val adv = { a: Int?, h: Int? ->
        if (a != null && h != null) when { a < h -> -1; h < a -> 1; else -> 0 } else 0
    }
    return ShareStatBox(title = "One Month Trend", fiveColStats = listOfNotNull(
        ShareFiveColStat(awayTrend?.let { "${it.wins}-${it.losses}" } ?: "-", awayTrend?.recordRank, awayTrend?.recordRankDisplay, "Record",
            homeTrend?.let { "${it.wins}-${it.losses}" } ?: "-", homeTrend?.recordRank, homeTrend?.recordRankDisplay, adv(awayTrend?.recordRank, homeTrend?.recordRank)),
        ShareFiveColStat(awayTrend?.runDiffPerGame?.let { if (it >= 0) "+${it.formatStat(2)}" else it.formatStat(2) } ?: "-", awayTrend?.runDiffPerGameRank, awayTrend?.runDiffPerGameRankDisplay, "Run Diff/G",
            homeTrend?.runDiffPerGame?.let { if (it >= 0) "+${it.formatStat(2)}" else it.formatStat(2) } ?: "-", homeTrend?.runDiffPerGameRank, homeTrend?.runDiffPerGameRankDisplay, adv(awayTrend?.runDiffPerGameRank, homeTrend?.runDiffPerGameRank)),
        ShareFiveColStat(awayTrend?.runsPerGame?.formatStat(2) ?: "-", awayTrend?.runsPerGameRank, awayTrend?.runsPerGameRankDisplay, "Runs/G",
            homeTrend?.runsPerGame?.formatStat(2) ?: "-", homeTrend?.runsPerGameRank, homeTrend?.runsPerGameRankDisplay, adv(awayTrend?.runsPerGameRank, homeTrend?.runsPerGameRank)),
        ShareFiveColStat(awayTrend?.runsAllowedPerGame?.formatStat(2) ?: "-", awayTrend?.runsAllowedPerGameRank, awayTrend?.runsAllowedPerGameRankDisplay, "RA/G",
            homeTrend?.runsAllowedPerGame?.formatStat(2) ?: "-", homeTrend?.runsAllowedPerGameRank, homeTrend?.runsAllowedPerGameRankDisplay, adv(awayTrend?.runsAllowedPerGameRank, homeTrend?.runsAllowedPerGameRank)),
        ShareFiveColStat(awayTrend?.hitsPerGame?.formatStat(2) ?: "-", awayTrend?.hitsPerGameRank, awayTrend?.hitsPerGameRankDisplay, "Hits/G",
            homeTrend?.hitsPerGame?.formatStat(2) ?: "-", homeTrend?.hitsPerGameRank, homeTrend?.hitsPerGameRankDisplay, adv(awayTrend?.hitsPerGameRank, homeTrend?.hitsPerGameRank)),
        ShareFiveColStat(awayTrend?.hrsPerGame?.formatStat(2) ?: "-", awayTrend?.hrsPerGameRank, awayTrend?.hrsPerGameRankDisplay, "HR/G",
            homeTrend?.hrsPerGame?.formatStat(2) ?: "-", homeTrend?.hrsPerGameRank, homeTrend?.hrsPerGameRankDisplay, adv(awayTrend?.hrsPerGameRank, homeTrend?.hrsPerGameRank))
    ))
}

private fun buildMLBH2HShareBox(mu: MLBMatchup): ShareStatBox {
    val h2h = mu.h2h
    return if (h2h != null && h2h.totalGames > 0) {
        ShareStatBox(title = "H2H (${h2h.teamAWins}-${h2h.teamBWins})", threeColStats = h2h.series.map { series ->
            ShareThreeColStat(
                "${series.teamAWins}W-${series.teamBWins}L", series.dateRange, "${series.teamBWins}W-${series.teamAWins}L",
                when { series.teamAWins > series.teamBWins -> -1; series.teamBWins > series.teamAWins -> 1; else -> 0 }
            )
        })
    } else {
        ShareStatBox(title = "H2H", threeColStats = listOf(ShareThreeColStat("", "No games yet", "", 0)))
    }
}

// ============================================================================
// Post-game share content
// ============================================================================

@Composable
private fun MLBPostGameShareContent(
    matchup: MLBMatchup,
    modifier: Modifier = Modifier
) {
    val results = matchup.results ?: return
    val awayBox = results.teamBoxScore?.away
    val homeBox = results.teamBoxScore?.home
    val vsAvg = results.vsSeasonAvg
    val awaySH = results.seasonHighs?.away
    val homeSH = results.seasonHighs?.home
    val awayWon = results.homeWon == false
    val homeWon = results.homeWon == true

    val bg = MaterialTheme.colorScheme.background
    val isDark = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryTextColor = if (isDark) Color.LightGray else Color.DarkGray
    val edgeColor = Color(0xFF4CAF50)
    val starColor = Color(0xFFE91E63)
    val diffUp = Color(0xFF4CAF50)
    val diffDown = Color(0xFFF44336)

    Column(modifier = modifier.padding(12.dp)) {
        // Score row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(matchup.awayTeam.abbreviation, style = MaterialTheme.typography.bodyLarge, color = textColor, fontWeight = if (awayWon) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    matchup.awayTeam.record?.let { record ->
                        Text(record, style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, maxLines = 1)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("${results.awayScore}", style = MaterialTheme.typography.bodyLarge, color = textColor, fontWeight = if (awayWon) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
            Text("${results.winner} +${results.margin}", style = MaterialTheme.typography.bodyMedium, color = secondaryTextColor, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${results.homeScore}", style = MaterialTheme.typography.bodyLarge, color = textColor, fontWeight = if (homeWon) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                Spacer(modifier = Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(matchup.homeTeam.abbreviation, style = MaterialTheme.typography.bodyLarge, color = textColor, fontWeight = if (homeWon) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    matchup.homeTeam.record?.let { record ->
                        Text(record, style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, maxLines = 1)
                    }
                }
            }
        }

        // Date
        val shareDate = try { formatBracketGameDate(matchup.gameDate) } catch (_: Exception) { null }
        shareDate?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = secondaryTextColor, modifier = Modifier.align(Alignment.CenterHorizontally), maxLines = 1)
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (awayBox != null && homeBox != null) {
            // Header
            Text(
                text = "STAT (vs avg)",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Reusable stat row with edge indicators, vs-avg diffs, and season high stars
            @Composable
            fun StatRow(away: String, label: String, home: String, awayN: Double? = null, homeN: Double? = null, higher: Boolean = true,
                        awayDiff: Double? = null, homeDiff: Double? = null,
                        awaySHEntry: SeasonHighEntry? = null, homeSHEntry: SeasonHighEntry? = null) {
                val aEdge = if (awayN != null && homeN != null) { if (higher) awayN > homeN else awayN < homeN } else false
                val hEdge = if (awayN != null && homeN != null) { if (higher) homeN > awayN else homeN < awayN } else false
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Away edge / season high indicator (star replaces edge when season high)
                    Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.CenterEnd) {
                        when {
                            awaySHEntry != null -> Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = starColor,
                                modifier = Modifier.size(16.dp)
                            )
                            aEdge -> Text("◀", style = MaterialTheme.typography.bodyMedium, color = edgeColor, maxLines = 1)
                        }
                    }
                    // Away value
                    Text(
                        away,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        fontWeight = if (aEdge || awaySHEntry != null) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Away diff
                    Text(
                        text = awayDiff?.let { "(${if (it >= 0) "+" else ""}${it.formatStat(1)})" } ?: "",
                        style = MaterialTheme.typography.labelSmall, color = if ((awayDiff ?: 0.0) >= 0) diffUp else diffDown,
                        maxLines = 1, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Start
                    )
                    // Label
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = secondaryTextColor, maxLines = 1, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    // Home diff
                    Text(
                        text = homeDiff?.let { "(${if (it >= 0) "+" else ""}${it.formatStat(1)})" } ?: "",
                        style = MaterialTheme.typography.labelSmall, color = if ((homeDiff ?: 0.0) >= 0) diffUp else diffDown,
                        maxLines = 1, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Home value
                    Text(
                        home,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        fontWeight = if (hEdge || homeSHEntry != null) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Start
                    )
                    // Home edge / season high indicator (star replaces edge when season high)
                    Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.CenterStart) {
                        when {
                            homeSHEntry != null -> Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = starColor,
                                modifier = Modifier.size(16.dp)
                            )
                            hEdge -> Text("▶", style = MaterialTheme.typography.bodyMedium, color = edgeColor, maxLines = 1)
                        }
                    }
                }
            }

            StatRow("${awayBox.runs ?: "-"}", "R", "${homeBox.runs ?: "-"}", awayBox.runs?.toDouble(), homeBox.runs?.toDouble(), true,
                vsAvg?.away?.runs?.difference, vsAvg?.home?.runs?.difference,
                awaySH?.get("runs"), homeSH?.get("runs"))
            StatRow("${awayBox.hits ?: "-"}", "H", "${homeBox.hits ?: "-"}", awayBox.hits?.toDouble(), homeBox.hits?.toDouble(), true,
                vsAvg?.away?.hits?.difference, vsAvg?.home?.hits?.difference)
            StatRow("${awayBox.homeRuns ?: "-"}", "HR", "${homeBox.homeRuns ?: "-"}", awayBox.homeRuns?.toDouble(), homeBox.homeRuns?.toDouble(), true,
                vsAvg?.away?.homeRuns?.difference, vsAvg?.home?.homeRuns?.difference)
            StatRow("${awayBox.rbis ?: "-"}", "RBI", "${homeBox.rbis ?: "-"}", awayBox.rbis?.toDouble(), homeBox.rbis?.toDouble(), true)
            StatRow("${awayBox.doubles ?: "-"}", "2B", "${homeBox.doubles ?: "-"}", awayBox.doubles?.toDouble(), homeBox.doubles?.toDouble(), true)
            StatRow("${awayBox.walks ?: "-"}", "BB", "${homeBox.walks ?: "-"}", awayBox.walks?.toDouble(), homeBox.walks?.toDouble(), true,
                vsAvg?.away?.walksBatting?.difference, vsAvg?.home?.walksBatting?.difference)
            StatRow("${awayBox.strikeouts ?: "-"}", "K", "${homeBox.strikeouts ?: "-"}", awayBox.strikeouts?.toDouble(), homeBox.strikeouts?.toDouble(), false,
                vsAvg?.away?.strikeoutsBatting?.difference, vsAvg?.home?.strikeoutsBatting?.difference)
            StatRow("${awayBox.stolenBases ?: "-"}", "SB", "${homeBox.stolenBases ?: "-"}", awayBox.stolenBases?.toDouble(), homeBox.stolenBases?.toDouble(), true)
            StatRow("${awayBox.runnersLOB ?: "-"}", "LOB", "${homeBox.runnersLOB ?: "-"}", awayBox.runnersLOB?.toDouble(), homeBox.runnersLOB?.toDouble(), false)
            StatRow(awayBox.avg ?: "-", "AVG", homeBox.avg ?: "-", parseMlbSlashStat(awayBox.avg), parseMlbSlashStat(homeBox.avg))
            StatRow(awayBox.obp ?: "-", "OBP", homeBox.obp ?: "-", parseMlbSlashStat(awayBox.obp), parseMlbSlashStat(homeBox.obp))
            StatRow(awayBox.slg ?: "-", "SLG", homeBox.slg ?: "-", parseMlbSlashStat(awayBox.slg), parseMlbSlashStat(homeBox.slg))
            StatRow(awayBox.ops ?: "-", "OPS", homeBox.ops ?: "-", parseMlbSlashStat(awayBox.ops), parseMlbSlashStat(homeBox.ops))
            StatRow("${awayBox.pitchingStrikeouts ?: "-"}", "K (P)", "${homeBox.pitchingStrikeouts ?: "-"}", awayBox.pitchingStrikeouts?.toDouble(), homeBox.pitchingStrikeouts?.toDouble(), true)
            StatRow("${awayBox.pitchingWalks ?: "-"}", "BB (P)", "${homeBox.pitchingWalks ?: "-"}", awayBox.pitchingWalks?.toDouble(), homeBox.pitchingWalks?.toDouble(), false)
            StatRow("${awayBox.pitches ?: "-"}", "Pitches", "${homeBox.pitches ?: "-"}", awayBox.pitches?.toDouble(), homeBox.pitches?.toDouble(), false)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("fbrk.app  •  ESPN", style = MaterialTheme.typography.bodyMedium, color = secondaryTextColor, modifier = Modifier.align(Alignment.CenterHorizontally), maxLines = 1)
    }
}
