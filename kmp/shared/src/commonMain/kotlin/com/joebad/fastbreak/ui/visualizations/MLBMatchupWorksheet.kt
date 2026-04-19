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
                        // First row: Date badges — scroll so selected date is centered
                        val dateScrollState = rememberScrollState()
                        LaunchedEffect(selectedDateIndex) {
                            // Each badge is roughly 60dp wide + 6dp spacing
                            val badgeWidth = 66
                            val screenCenter = (screenWidth.value / 2).toInt()
                            val targetScroll = (selectedDateIndex * badgeWidth - screenCenter + badgeWidth / 2).coerceAtLeast(0)
                            dateScrollState.animateScrollTo(targetScroll)
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

                        val statBoxes = listOf(
                            buildStatBox("Batting Stats", comp.sideBySide?.offense),
                            buildStatBox("Pitching Stats", comp.sideBySide?.defense),
                            buildOvdBox("$awayAbbrev Bat vs $homeAbbrev Pitch", "$awayAbbrev Bat", "$homeAbbrev Pitch", comp.awayOffVsHomeDef),
                            buildOvdBox("$homeAbbrev Bat vs $awayAbbrev Pitch", "$homeAbbrev Bat", "$awayAbbrev Pitch", comp.homeOffVsAwayDef, Team2Color, Team1Color)
                        )
                        val finalBoxes = statBoxes + List((6 - statBoxes.size).coerceAtLeast(0)) { ShareStatBox(title = "", fiveColStats = emptyList()) }
                        GenericMatchupShareImage(gameInfo = gameInfo, statBoxes = finalBoxes.take(6), modifier = Modifier.fillMaxSize())
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
        // Record section (matches NHL/NBA pattern)
        MLBRecordSection(awayTeam = awayTeam, homeTeam = homeTeam)

        // Location + date/time
        matchup.location?.let { loc ->
            Text(listOfNotNull(loc.stadium, loc.city).joinToString(", "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp), textAlign = TextAlign.Center)
        }
        if (!matchup.gameCompleted) {
            val formatted = try { formatBracketGameDate(matchup.gameDate) } catch (_: Exception) { null }
            formatted?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }

        // Odds
        matchup.odds?.let { odds ->
            val parts = listOfNotNull(odds.details, odds.overUnder?.let { "O/U $it" })
            if (parts.isNotEmpty()) {
                Text(parts.joinToString("  •  "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = TextAlign.Center)
            }
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
                0 -> MLBTeamStatsView(comparisons, awayTeam.abbreviation, homeTeam.abbreviation)
                1 -> BracketOffenseVsDefenseView(comparisons.awayOffVsHomeDef, awayTeam.name, homeTeam.name, ::mlbRankColor)
                2 -> BracketOffenseVsDefenseView(comparisons.homeOffVsAwayDef, homeTeam.name, awayTeam.name, ::mlbRankColor)
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun MLBTeamStatsView(comparisons: MatchupComparisons, awayAbbrev: String, homeAbbrev: String) {
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

    // Pitching — MLB uses "pitching" key but MatchupComparisons maps it to different field
    // The R script outputs sideBySide.pitching but KMP's SideBySideComparison has offense/defense/overall
    // MLB puts pitching stats in "defense" since that's the second sideBySide section
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
    }
}

// MLB Record Section (matches NHL/NBA record section layout)
@Composable
private fun MLBRecordSection(awayTeam: MLBTeamInfo, homeTeam: MLBTeamInfo) {
    val textStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 14.sp)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Away team (left)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            awayTeam.record?.let {
                Text(it, style = textStyle, fontSize = 11.sp, color = textColor)
            }
            awayTeam.division?.let {
                Text(it, style = textStyle, fontSize = 11.sp, color = textColor)
            }
        }
        // Home team (right)
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            homeTeam.record?.let {
                Text(it, style = textStyle, fontSize = 11.sp, color = textColor, textAlign = TextAlign.End)
            }
            homeTeam.division?.let {
                Text(it, style = textStyle, fontSize = 11.sp, color = textColor, textAlign = TextAlign.End)
            }
        }
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
