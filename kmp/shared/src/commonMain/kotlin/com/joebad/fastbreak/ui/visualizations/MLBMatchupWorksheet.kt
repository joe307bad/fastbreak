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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.components.ShareFab
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

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
    var selectedDateIndex by remember { mutableIntStateOf(
        dates.indexOfFirst { date ->
            try {
                val today = kotlin.time.Clock.System.now()
                    .toLocalDateTime(TimeZone.of("America/New_York"))
                val todayDate = LocalDate(today.year, today.monthNumber, today.dayOfMonth)
                date >= todayDate
            } catch (_: Exception) { false }
        }.coerceAtLeast(0)
    ) }

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
    var captureRequested by remember { mutableStateOf(false) }
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }

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
                                // Charts tab — placeholder for now
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Charts coming soon", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
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
            if (selectedMatchup?.comparisons != null) {
                ShareFab(
                    onClick = { captureRequested = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                )
            }

            // Off-screen capture
            if (captureRequested && selectedMatchup != null) {
                val mu = selectedMatchup
                val shareTitle = "${mu.awayTeam.abbreviation} @ ${mu.homeTeam.abbreviation}"

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(50)
                    try { val bmp = graphicsLayer.toImageBitmap(); imageExporter.shareImage(bmp, shareTitle) }
                    catch (e: Exception) { e.printStackTrace() } finally { captureRequested = false }
                }

                CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                    Box(modifier = Modifier.requiredWidth(3400.dp).requiredHeight(1400.dp).offset { IntOffset(-10000, 0) }
                        .drawWithContent { graphicsLayer.record { this@drawWithContent.drawContent() }; drawLayer(graphicsLayer) }) {
                        val comp = mu.comparisons ?: return@Box
                        val awayAbbrev = mu.awayTeam.abbreviation
                        val homeAbbrev = mu.homeTeam.abbreviation
                        val gameInfo = ShareGameInfo(
                            awayTeam = awayAbbrev, homeTeam = homeAbbrev,
                            eventLabel = mu.awayTeam.division ?: "", formattedDate = "",
                            source = "ESPN",
                            awayRecord = mu.awayTeam.record, homeRecord = mu.homeTeam.record
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

                        // Build trend box
                        val awayTrend = parseMLBMonthTrend(mu.awayTeam.stats)
                        val homeTrend = parseMLBMonthTrend(mu.homeTeam.stats)
                        val trendRankAdv = { a: Int?, h: Int? ->
                            if (a != null && h != null) when {
                                a < h -> -1; h < a -> 1; else -> 0
                            } else 0
                        }
                        val trendBox = ShareStatBox(
                            title = "One Month Trend",
                            fiveColStats = listOfNotNull(
                                ShareFiveColStat(
                                    awayTrend?.let { "${it.wins}-${it.losses}" } ?: "-",
                                    awayTrend?.recordRank, awayTrend?.recordRankDisplay,
                                    "Record",
                                    homeTrend?.let { "${it.wins}-${it.losses}" } ?: "-",
                                    homeTrend?.recordRank, homeTrend?.recordRankDisplay,
                                    trendRankAdv(awayTrend?.recordRank, homeTrend?.recordRank)
                                ),
                                ShareFiveColStat(
                                    awayTrend?.runDiffPerGame?.let { if (it >= 0) "+${it.formatStat(2)}" else it.formatStat(2) } ?: "-",
                                    awayTrend?.runDiffPerGameRank, awayTrend?.runDiffPerGameRankDisplay,
                                    "Run Diff/G",
                                    homeTrend?.runDiffPerGame?.let { if (it >= 0) "+${it.formatStat(2)}" else it.formatStat(2) } ?: "-",
                                    homeTrend?.runDiffPerGameRank, homeTrend?.runDiffPerGameRankDisplay,
                                    trendRankAdv(awayTrend?.runDiffPerGameRank, homeTrend?.runDiffPerGameRank)
                                ),
                                ShareFiveColStat(
                                    awayTrend?.runsPerGame?.formatStat(2) ?: "-",
                                    awayTrend?.runsPerGameRank, awayTrend?.runsPerGameRankDisplay,
                                    "Runs/G",
                                    homeTrend?.runsPerGame?.formatStat(2) ?: "-",
                                    homeTrend?.runsPerGameRank, homeTrend?.runsPerGameRankDisplay,
                                    trendRankAdv(awayTrend?.runsPerGameRank, homeTrend?.runsPerGameRank)
                                ),
                                ShareFiveColStat(
                                    awayTrend?.runsAllowedPerGame?.formatStat(2) ?: "-",
                                    awayTrend?.runsAllowedPerGameRank, awayTrend?.runsAllowedPerGameRankDisplay,
                                    "RA/G",
                                    homeTrend?.runsAllowedPerGame?.formatStat(2) ?: "-",
                                    homeTrend?.runsAllowedPerGameRank, homeTrend?.runsAllowedPerGameRankDisplay,
                                    trendRankAdv(awayTrend?.runsAllowedPerGameRank, homeTrend?.runsAllowedPerGameRank)
                                ),
                                ShareFiveColStat(
                                    awayTrend?.hitsPerGame?.formatStat(2) ?: "-",
                                    awayTrend?.hitsPerGameRank, awayTrend?.hitsPerGameRankDisplay,
                                    "Hits/G",
                                    homeTrend?.hitsPerGame?.formatStat(2) ?: "-",
                                    homeTrend?.hitsPerGameRank, homeTrend?.hitsPerGameRankDisplay,
                                    trendRankAdv(awayTrend?.hitsPerGameRank, homeTrend?.hitsPerGameRank)
                                ),
                                ShareFiveColStat(
                                    awayTrend?.hrsPerGame?.formatStat(2) ?: "-",
                                    awayTrend?.hrsPerGameRank, awayTrend?.hrsPerGameRankDisplay,
                                    "HR/G",
                                    homeTrend?.hrsPerGame?.formatStat(2) ?: "-",
                                    homeTrend?.hrsPerGameRank, homeTrend?.hrsPerGameRankDisplay,
                                    trendRankAdv(awayTrend?.hrsPerGameRank, homeTrend?.hrsPerGameRank)
                                )
                            )
                        )

                        // Build H2H box
                        val h2h = mu.h2h
                        val h2hBox = if (h2h != null && h2h.totalGames > 0) {
                            ShareStatBox(
                                title = "H2H (${h2h.teamAWins}-${h2h.teamBWins})",
                                threeColStats = h2h.series.map { series ->
                                    val awayWon = series.teamAWins > series.teamBWins
                                    val homeWon = series.teamBWins > series.teamAWins
                                    ShareThreeColStat(
                                        leftText = "${series.teamAWins}W-${series.teamBWins}L",
                                        centerText = series.dateRange,
                                        rightText = "${series.teamBWins}W-${series.teamAWins}L",
                                        advantage = when {
                                            awayWon -> -1
                                            homeWon -> 1
                                            else -> 0
                                        }
                                    )
                                }
                            )
                        } else {
                            ShareStatBox(title = "H2H", threeColStats = listOf(
                                ShareThreeColStat("", "No games yet", "", 0)
                            ))
                        }

                        val statBoxes = listOf(
                            buildStatBox("Batting Stats", comp.sideBySide?.offense),
                            buildStatBox("Pitching Stats", comp.sideBySide?.defense),
                            trendBox,
                            buildOvdBox("$awayAbbrev Bat vs $homeAbbrev Pitch", "$awayAbbrev Bat", "$homeAbbrev Pitch", comp.awayOffVsHomeDef),
                            buildOvdBox("$homeAbbrev Bat vs $awayAbbrev Pitch", "$homeAbbrev Bat", "$awayAbbrev Pitch", comp.homeOffVsAwayDef, Team2Color, Team1Color),
                            h2hBox
                        )
                        val finalBoxes = statBoxes + List((6 - statBoxes.size).coerceAtLeast(0)) { ShareStatBox(title = "", fiveColStats = emptyList()) }
                        GenericMatchupShareImage(gameInfo = gameInfo, statBoxes = finalBoxes.take(6), modifier = Modifier.fillMaxSize(), rowSpacing = 48.dp, firstRowWeight = 1.5f, secondRowWeight = 1f)
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
    val awayAbbrev = awayTeam.abbreviation
    val homeAbbrev = homeTeam.abbreviation

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

// Three-row meta section laid out as a 3-column grid (away | center | home).
//   Row 1: stadium/city + date/time (centered, spans all columns)
//   Row 2: away record | (center) | home record
//   Row 3: away division | odds | home division
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

    val formattedDate = if (!matchup.gameCompleted) {
        try { formatBracketGameDate(matchup.gameDate) } catch (_: Exception) { null }
    } else null
    val stadiumLine = matchup.location?.let { loc ->
        listOfNotNull(loc.stadium, loc.city).joinToString(", ").takeIf { it.isNotBlank() }
    }
    val locationLine = listOfNotNull(stadiumLine, formattedDate).joinToString(" • ").takeIf { it.isNotBlank() }
    val oddsLine = matchup.odds?.let { odds ->
        listOfNotNull(odds.details?.takeIf { it.isNotBlank() }, odds.overUnder?.let { "O/U $it" })
            .joinToString(" • ").takeIf { it.isNotBlank() }
    }

    @Composable
    fun ThreeColumnRow(
        left: String?,
        center: String?,
        right: String?,
        leftColor: Color,
        centerColor: Color,
        rightColor: Color
    ) {
        if (left == null && center == null && right == null) return
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                left ?: "",
                style = textStyle, fontSize = 11.sp, color = leftColor,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Start
            )
            Text(
                center ?: "",
                style = textStyle, fontSize = 11.sp, color = centerColor,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
            Text(
                right ?: "",
                style = textStyle, fontSize = 11.sp, color = rightColor,
                modifier = Modifier.weight(1f), textAlign = TextAlign.End
            )
        }
    }

    // Row 1: stadium/city + date/time, centered across the full width
    locationLine?.let {
        Text(
            it,
            style = textStyle, fontSize = 11.sp, color = metaColor,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            textAlign = TextAlign.Center
        )
    }

    // Row 2: records (away left, home right), center reserved for alignment
    ThreeColumnRow(
        left = awayTeam.record, center = null, right = homeTeam.record,
        leftColor = recordColor, centerColor = recordColor, rightColor = recordColor
    )

    // Row 3: division (away left, home right), odds in the center
    ThreeColumnRow(
        left = awayTeam.division, center = oddsLine, right = homeTeam.division,
        leftColor = recordColor, centerColor = oddsColor, rightColor = recordColor
    )
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
