package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.QuadrantScatterPlot
import com.joebad.fastbreak.ui.TeamLegendEntry
import com.joebad.fastbreak.ui.components.FabOption
import com.joebad.fastbreak.ui.components.MultiOptionFab
import com.joebad.fastbreak.ui.components.ShareFab
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.round

private const val NFL_SOURCE = "nflverse"

private enum class NflCaptureTarget {
    PRE_GAME,
    POST_GAME,
    QUARTERBACKS,
    RUSHERS,
    RECEIVERS,
    DEFENDERS
}

private fun Double.formatNflStat(decimals: Int = 1): String {
    val multiplier = when (decimals) { 0 -> 1.0; 1 -> 10.0; 2 -> 100.0; 3 -> 1000.0; else -> 10.0 }
    val rounded = round(this * multiplier) / multiplier
    return when (decimals) {
        0 -> rounded.toInt().toString()
        else -> {
            val str = rounded.toString()
            if (str.contains('.')) {
                val parts = str.split('.')
                "${parts[0]}.${parts[1].padEnd(decimals, '0').take(decimals)}"
            } else {
                "$str.${"0".repeat(decimals)}"
            }
        }
    }
}

private fun Double?.orDash(decimals: Int = 1): String = this?.formatNflStat(decimals) ?: "-"

private fun Double?.signed(decimals: Int = 2): String {
    if (this == null) return "-"
    return if (this >= 0) "+${formatNflStat(decimals)}" else formatNflStat(decimals)
}

// The stat catalog uses very different magnitudes — EPA/play sits near 0.1 while
// yards/game sits near 350 — so precision is chosen per label rather than
// globally, which is what the MLB card's fixed 3 decimals gets wrong here.
private fun nflDecimalsFor(label: String): Int = when {
    label.contains("EPA") -> 3
    label.contains("Yards/Play") -> 2
    label.contains("Turnover") || label.contains("Sacks") || label.contains("Takeaways") -> 2
    else -> 1
}

// Ranks run 1..32. Reuses the shared NFL palette by asking FiveColumnRowWithRanks
// for the 32-team scale rather than defining another gradient here.
private fun nflRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    return when {
        rank <= 5 -> { val r = (rank - 1) / 4f; Color((0 + r * 80).toInt(), (150 - r * 25).toInt(), (42 - r * 32).toInt()) }
        rank <= 16 -> { val r = (rank - 6) / 10f; Color((255 - r * 55).toInt(), (160 - r * 60).toInt(), 0) }
        else -> { val r = ((rank - 17).coerceAtMost(15)) / 15f; Color((200 - r * 61).toInt(), (50 - r * 50).toInt(), 0) }
    }
}

private fun rankAdvantage(away: Int?, home: Int?): Int {
    if (away == null || home == null) return 0
    return when {
        away < home -> -1
        away > home -> 1
        else -> 0
    }
}

// ============================================================================
// Worksheet
// ============================================================================

@Composable
fun NFLMatchupWorksheet(
    visualization: NFLMatchupVisualization,
    modifier: Modifier = Modifier,
    pinnedTeams: List<PinnedTeam> = emptyList(),
    highlightedTeamCodes: Set<String> = emptySet(),
    onScheduleToggleHandlerChanged: ((ScheduleToggleHandler?) -> Unit)? = null
) {
    val nflPinnedTeamCodes = remember(pinnedTeams, highlightedTeamCodes) {
        pinnedTeams.filter { it.sport == "NFL" }.map { it.teamCode }.toSet() + highlightedTeamCodes
    }

    val matchupsByDate = remember(visualization.dataPoints, nflPinnedTeamCodes) {
        visualization.dataPoints
            .groupBy { matchup ->
                try {
                    val instant = Instant.parse(matchup.gameDate)
                    val dt = instant.toLocalDateTime(TimeZone.of("America/New_York"))
                    LocalDate(dt.year, dt.monthNumber, dt.dayOfMonth)
                } catch (_: Exception) {
                    LocalDate(2000, 1, 1)
                }
            }
            .entries.sortedBy { it.key }.associate { it.toPair() }
            .mapValues { (_, matchups) ->
                val pinned = matchups.filter { m ->
                    nflPinnedTeamCodes.any { code ->
                        m.homeTeam.abbreviation.equals(code, ignoreCase = true) ||
                            m.awayTeam.abbreviation.equals(code, ignoreCase = true)
                    }
                }
                pinned + matchups.filter { it !in pinned }
            }
    }

    val dates = matchupsByDate.keys.toList()

    // Same rule as the MLB card: open on today or the next day with games, but a
    // deep-linked team wins — its next game, else its most recent one.
    val initialDateIndex = remember(dates, matchupsByDate, highlightedTeamCodes) {
        val todayDate = try {
            val today = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.of("America/New_York"))
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
            candidateIndices.firstOrNull { dates[it] >= todayDate }
                ?: candidateIndices.lastOrNull { dates[it] < todayDate }
                ?: 0
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

    var selectedTab by remember { mutableIntStateOf(0) }

    var isScheduleExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(isScheduleExpanded) {
        onScheduleToggleHandlerChanged?.invoke(
            ScheduleToggleHandler(isExpanded = isScheduleExpanded, toggle = { isScheduleExpanded = !isScheduleExpanded })
        )
    }
    DisposableEffect(Unit) { onDispose { onScheduleToggleHandlerChanged?.invoke(null) } }

    var captureTarget by remember { mutableStateOf<NflCaptureTarget?>(null) }
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }

    var cumPointDiffShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var weeklyPerfShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isScheduleExpanded,
                    enter = androidx.compose.animation.expandVertically(),
                    exit = androidx.compose.animation.shrinkVertically()
                ) {
                    Column {
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

                if (selectedMatchup != null) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                        when (selectedTab) {
                            0 -> NFLMatchupContent(matchup = selectedMatchup, modifier = Modifier.fillMaxSize())
                            1 -> NFLChartsTab(
                                awayTeam = selectedMatchup.awayTeam.abbreviation,
                                homeTeam = selectedMatchup.homeTeam.abbreviation,
                                matchup = selectedMatchup,
                                leagueCumPointDiffStats = visualization.leagueCumPointDiffStats,
                                leagueWeeklyStats = visualization.leagueWeeklyStats,
                                onCumPointDiffShareClick = { callback -> cumPointDiffShareCallback = callback },
                                onWeeklyPerfShareClick = { callback -> weeklyPerfShareCallback = callback }
                            )
                        }

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
                        Text(
                            "Select a matchup",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (selectedMatchup != null) {
                when {
                    selectedTab == 1 -> {
                        MultiOptionFab(
                            options = listOf(
                                FabOption(
                                    icon = Icons.Filled.Star,
                                    label = "Cumulative Point Diff",
                                    onClick = { cumPointDiffShareCallback?.invoke() }
                                ),
                                FabOption(
                                    icon = Icons.Filled.PlayArrow,
                                    label = "Weekly Performance",
                                    onClick = { weeklyPerfShareCallback?.invoke() }
                                )
                            ),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        )
                    }
                    selectedTab == 0 && selectedMatchup.comparisons != null -> {
                        val hasResults = selectedMatchup.gameCompleted && selectedMatchup.results?.teamBoxScore != null
                        val highlights = selectedMatchup.results?.playerHighlights
                        if (hasResults) {
                            val options = buildList {
                                add(FabOption(icon = Icons.Filled.PlayArrow, label = "Pre Game",
                                    onClick = { captureTarget = NflCaptureTarget.PRE_GAME }))
                                add(FabOption(icon = Icons.Filled.Check, label = "Post Game",
                                    onClick = { captureTarget = NflCaptureTarget.POST_GAME }))
                                if (highlights?.away?.quarterback != null || highlights?.home?.quarterback != null) {
                                    add(FabOption(icon = Icons.Filled.Star, label = "Quarterbacks",
                                        onClick = { captureTarget = NflCaptureTarget.QUARTERBACKS }))
                                }
                                if (!highlights?.away?.rushers.isNullOrEmpty() || !highlights?.home?.rushers.isNullOrEmpty()) {
                                    add(FabOption(icon = Icons.Filled.Star, label = "Rushers",
                                        onClick = { captureTarget = NflCaptureTarget.RUSHERS }))
                                }
                                if (!highlights?.away?.receivers.isNullOrEmpty() || !highlights?.home?.receivers.isNullOrEmpty()) {
                                    add(FabOption(icon = Icons.Filled.Star, label = "Receivers",
                                        onClick = { captureTarget = NflCaptureTarget.RECEIVERS }))
                                }
                                if (!highlights?.away?.defenders.isNullOrEmpty() || !highlights?.home?.defenders.isNullOrEmpty()) {
                                    add(FabOption(icon = Icons.Filled.Star, label = "Defense",
                                        onClick = { captureTarget = NflCaptureTarget.DEFENDERS }))
                                }
                            }
                            MultiOptionFab(
                                options = options,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                            )
                        } else {
                            ShareFab(
                                onClick = { captureTarget = NflCaptureTarget.PRE_GAME },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                            )
                        }
                    }
                }
            }

            captureTarget?.let { target ->
                val mu = selectedMatchup ?: return@let
                val matchupLabel = "${mu.awayTeam.abbreviation} @ ${mu.homeTeam.abbreviation}"
                val shareTitle = when (target) {
                    NflCaptureTarget.PRE_GAME -> matchupLabel
                    NflCaptureTarget.POST_GAME -> "$matchupLabel - Results"
                    NflCaptureTarget.QUARTERBACKS -> "$matchupLabel - Quarterbacks"
                    NflCaptureTarget.RUSHERS -> "$matchupLabel - Rushers"
                    NflCaptureTarget.RECEIVERS -> "$matchupLabel - Receivers"
                    NflCaptureTarget.DEFENDERS -> "$matchupLabel - Defense"
                }
                val captureWidth = if (target == NflCaptureTarget.PRE_GAME) 3400.dp else 420.dp

                LaunchedEffect(target) {
                    kotlinx.coroutines.delay(50)
                    try {
                        val bmp = graphicsLayer.toImageBitmap()
                        imageExporter.shareImage(bmp, shareTitle)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        captureTarget = null
                    }
                }

                CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                    Box(
                        modifier = Modifier
                            .requiredWidth(captureWidth)
                            .wrapContentSize(unbounded = true)
                            .offset { IntOffset(-10000, 0) }
                            .drawWithContent {
                                graphicsLayer.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
                                    this@drawWithContent.drawContent()
                                }
                                drawLayer(graphicsLayer)
                            }
                            .then(
                                if (target == NflCaptureTarget.PRE_GAME) Modifier
                                else Modifier.background(MaterialTheme.colorScheme.background)
                            )
                    ) {
                        when (target) {
                            NflCaptureTarget.PRE_GAME -> NFLPreGameShareContent(mu, captureWidth)
                            NflCaptureTarget.POST_GAME -> if (mu.gameCompleted && mu.results != null) {
                                NFLPostGameShareContent(mu, Modifier.requiredWidth(captureWidth))
                            }
                            else -> NFLPlayerShareContent(mu, target, Modifier.requiredWidth(captureWidth))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Stats tab content
// ============================================================================

@Composable
private fun NFLMatchupContent(matchup: NFLMatchup, modifier: Modifier = Modifier) {
    var viewSelection by remember { mutableIntStateOf(0) }
    val comparisons = matchup.comparisons
    val homeTeam = matchup.homeTeam
    val awayTeam = matchup.awayTeam

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 8.dp, end = 8.dp, top = 36.dp)
    ) {
        NFLMatchupMetaRows(matchup = matchup, awayTeam = awayTeam, homeTeam = homeTeam)

        if (matchup.results?.teamBoxScore != null) {
            Spacer(modifier = Modifier.height(8.dp))
            NFLBoxScoreSection(results = matchup.results)

            matchup.results.playerHighlights?.let { highlights ->
                if (highlights.away?.quarterback != null || highlights.home?.quarterback != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NFLQuarterbackSection(highlights)
                }
                if (!highlights.away?.rushers.isNullOrEmpty() || !highlights.home?.rushers.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NFLRushersSection(highlights)
                }
                if (!highlights.away?.receivers.isNullOrEmpty() || !highlights.home?.receivers.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NFLReceiversSection(highlights)
                }
                if (!highlights.away?.defenders.isNullOrEmpty() || !highlights.home?.defenders.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NFLDefendersSection(highlights)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (comparisons != null) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TeamStatsNavBadge("Team", viewSelection == 0) { viewSelection = 0 }
                TeamStatsNavBadge("${awayTeam.abbreviation} Off vs ${homeTeam.abbreviation} Def", viewSelection == 1) { viewSelection = 1 }
                TeamStatsNavBadge("${homeTeam.abbreviation} Off vs ${awayTeam.abbreviation} Def", viewSelection == 2) { viewSelection = 2 }
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (viewSelection) {
                0 -> NFLTeamStatsView(comparisons, awayTeam, homeTeam)
                1 -> BracketOffenseVsDefenseView(comparisons.awayOffVsHomeDef, awayTeam.name, homeTeam.name, ::nflRankColor)
                2 -> BracketOffenseVsDefenseView(comparisons.homeOffVsAwayDef, homeTeam.name, awayTeam.name, ::nflRankColor)
            }
        }

        if (matchup.h2h != null && matchup.h2h.totalGames > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            NFLH2HSection(h2h = matchup.h2h)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun NFLStatComparisonRows(stats: Map<String, SideBySideStatComparison>) {
    stats.forEach { (_, stat) ->
        val decimals = nflDecimalsFor(stat.label)
        FiveColumnRowWithRanks(
            leftValue = stat.away.value.orDash(decimals),
            leftRank = stat.away.rank,
            leftRankDisplay = stat.away.rankDisplay,
            centerText = stat.label,
            rightValue = stat.home.value.orDash(decimals),
            rightRank = stat.home.rank,
            rightRankDisplay = stat.home.rankDisplay,
            advantage = rankAdvantage(stat.away.rank, stat.home.rank),
            useNBARanks = false,
            rankColorFn = ::nflRankColor
        )
    }
}

@Composable
private fun NFLTeamStatsView(comparisons: MatchupComparisons, awayTeam: NFLTeamInfo, homeTeam: NFLTeamInfo) {
    val sideBySide = comparisons.sideBySide ?: return

    if (sideBySide.offense.isNotEmpty()) {
        SectionHeader("Offense")
        Spacer(modifier = Modifier.height(4.dp))
        NFLStatComparisonRows(sideBySide.offense)
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (sideBySide.defense.isNotEmpty()) {
        SectionHeader("Defense")
        Spacer(modifier = Modifier.height(4.dp))
        NFLStatComparisonRows(sideBySide.defense)
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (sideBySide.overall.isNotEmpty()) {
        SectionHeader("Overall")
        Spacer(modifier = Modifier.height(4.dp))
        NFLStatComparisonRows(sideBySide.overall)
        Spacer(modifier = Modifier.height(8.dp))
    }

    NFLRecentFormSection(awayTeam = awayTeam, homeTeam = homeTeam)
}

@Composable
private fun NFLMatchupMetaRows(matchup: NFLMatchup, awayTeam: NFLTeamInfo, homeTeam: NFLTeamInfo) {
    val textStyle = MaterialTheme.typography.labelSmall
    val recordColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    val oddsColor = MaterialTheme.colorScheme.primary

    val oddsLine = matchup.odds?.let { odds ->
        listOfNotNull(
            odds.details?.takeIf { it.isNotBlank() },
            odds.overUnder?.let { "O/U ${it.formatNflStat(1)}" }
        ).joinToString(" • ").takeIf { it.isNotBlank() }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            awayTeam.record ?: "", style = textStyle, fontSize = 11.sp, color = recordColor,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Start
        )
        Text(
            oddsLine ?: "", style = textStyle, fontSize = 11.sp, color = oddsColor,
            maxLines = 1, softWrap = false, textAlign = TextAlign.Center
        )
        Text(
            homeTeam.record ?: "", style = textStyle, fontSize = 11.sp, color = recordColor,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End
        )
    }

    // Week / round label, plus the venue — NFL games are one a week, so which
    // week this is carries more meaning than it does in a daily sport.
    val weekLabel = listOfNotNull(
        matchup.season?.let { season ->
            if (matchup.seasonType == "REG") matchup.week?.let { "$season Week $it" } else "$season ${matchup.seasonTypeLabel ?: matchup.seasonType}"
        },
        matchup.location?.stadium
    ).joinToString(" • ").takeIf { it.isNotBlank() }

    if (weekLabel != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(weekLabel, style = textStyle, fontSize = 11.sp, color = metaColor, maxLines = 1)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            awayTeam.division ?: "", style = textStyle, fontSize = 11.sp, color = metaColor,
            modifier = Modifier.weight(1f), textAlign = TextAlign.Start
        )
        Text(
            homeTeam.division ?: "", style = textStyle, fontSize = 11.sp, color = metaColor,
            modifier = Modifier.weight(1f), textAlign = TextAlign.End
        )
    }
}

// ============================================================================
// Recent form
// ============================================================================

private data class NFLRecentFormStat(
    val value: Double?,
    val rank: Int?,
    val rankDisplay: String?
)

private data class NFLRecentFormData(
    val wins: Int,
    val losses: Int,
    val ties: Int,
    val recordRank: Int?,
    val recordRankDisplay: String?,
    val pointsPerGame: NFLRecentFormStat,
    val pointsAllowedPerGame: NFLRecentFormStat,
    val pointDiffPerGame: NFLRecentFormStat,
    val yardsPerGame: NFLRecentFormStat,
    val turnoverDiffPerGame: NFLRecentFormStat
)

private fun parseNflStatEntry(parent: JsonObject?, key: String): NFLRecentFormStat {
    val obj = parent?.get(key) as? JsonObject ?: return NFLRecentFormStat(null, null, null)
    return NFLRecentFormStat(
        value = (obj["value"] as? JsonPrimitive)?.doubleOrNull,
        rank = (obj["rank"] as? JsonPrimitive)?.content?.toIntOrNull(),
        rankDisplay = (obj["rankDisplay"] as? JsonPrimitive)?.content
    )
}

private fun parseNflRecentForm(stats: JsonObject?): NFLRecentFormData? {
    val recentForm = stats?.get("recentForm") as? JsonObject ?: return null
    val record = recentForm["record"] as? JsonObject
    return NFLRecentFormData(
        wins = (record?.get("wins") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
        losses = (record?.get("losses") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
        ties = (record?.get("ties") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
        recordRank = (record?.get("rank") as? JsonPrimitive)?.content?.toIntOrNull(),
        recordRankDisplay = (record?.get("rankDisplay") as? JsonPrimitive)?.content,
        pointsPerGame = parseNflStatEntry(recentForm, "pointsPerGame"),
        pointsAllowedPerGame = parseNflStatEntry(recentForm, "pointsAllowedPerGame"),
        pointDiffPerGame = parseNflStatEntry(recentForm, "pointDiffPerGame"),
        yardsPerGame = parseNflStatEntry(recentForm, "yardsPerGame"),
        turnoverDiffPerGame = parseNflStatEntry(recentForm, "turnoverDiffPerGame")
    )
}

private fun NFLRecentFormData?.recordText(): String {
    if (this == null) return "-"
    return if (ties > 0) "$wins-$losses-$ties" else "$wins-$losses"
}

@Composable
private fun NFLRecentFormRow(
    label: String,
    away: NFLRecentFormStat?,
    home: NFLRecentFormStat?,
    decimals: Int = 1,
    signed: Boolean = false
) {
    FiveColumnRowWithRanks(
        leftValue = if (signed) away?.value.signed(decimals) else away?.value.orDash(decimals),
        leftRank = away?.rank,
        leftRankDisplay = away?.rankDisplay,
        centerText = label,
        rightValue = if (signed) home?.value.signed(decimals) else home?.value.orDash(decimals),
        rightRank = home?.rank,
        rightRankDisplay = home?.rankDisplay,
        advantage = rankAdvantage(away?.rank, home?.rank),
        useNBARanks = false,
        rankColorFn = ::nflRankColor
    )
}

@Composable
private fun NFLRecentFormSection(awayTeam: NFLTeamInfo, homeTeam: NFLTeamInfo) {
    val awayForm = parseNflRecentForm(awayTeam.stats)
    val homeForm = parseNflRecentForm(homeTeam.stats)
    if (awayForm == null && homeForm == null) return

    val games = maxOf(
        (awayTeam.stats?.get("recentForm") as? JsonObject)?.let { (it["gamesPlayed"] as? JsonPrimitive)?.content?.toIntOrNull() } ?: 0,
        (homeTeam.stats?.get("recentForm") as? JsonObject)?.let { (it["gamesPlayed"] as? JsonPrimitive)?.content?.toIntOrNull() } ?: 0
    )

    SectionHeader(if (games > 0) "Last $games Games" else "Recent Form")
    Spacer(modifier = Modifier.height(4.dp))

    FiveColumnRowWithRanks(
        leftValue = awayForm.recordText(),
        leftRank = awayForm?.recordRank,
        leftRankDisplay = awayForm?.recordRankDisplay,
        centerText = "Record",
        rightValue = homeForm.recordText(),
        rightRank = homeForm?.recordRank,
        rightRankDisplay = homeForm?.recordRankDisplay,
        advantage = rankAdvantage(awayForm?.recordRank, homeForm?.recordRank),
        useNBARanks = false,
        rankColorFn = ::nflRankColor
    )
    NFLRecentFormRow("Point Diff/G", awayForm?.pointDiffPerGame, homeForm?.pointDiffPerGame, 1, signed = true)
    NFLRecentFormRow("Points/G", awayForm?.pointsPerGame, homeForm?.pointsPerGame, 1)
    NFLRecentFormRow("Points Allowed/G", awayForm?.pointsAllowedPerGame, homeForm?.pointsAllowedPerGame, 1)
    NFLRecentFormRow("Yards/G", awayForm?.yardsPerGame, homeForm?.yardsPerGame, 1)
    NFLRecentFormRow("Turnover Diff/G", awayForm?.turnoverDiffPerGame, homeForm?.turnoverDiffPerGame, 2, signed = true)
}

// ============================================================================
// Box score
// ============================================================================

private fun nflDiffSuffix(stat: NFLVsSeasonAvgStat?, decimals: Int = 1): String {
    val diff = stat?.difference ?: return ""
    val prefix = if (diff >= 0) "+" else ""
    return " (${prefix}${diff.formatNflStat(decimals)})"
}

private fun nflDiffPrefix(stat: NFLVsSeasonAvgStat?, decimals: Int = 1): String {
    val diff = stat?.difference ?: return ""
    val prefix = if (diff >= 0) "+" else ""
    return "(${prefix}${diff.formatNflStat(decimals)}) "
}

private fun nflValueAdvantage(away: Double?, home: Double?, higher: Boolean = true): Int {
    if (away == null || home == null) return 0
    return if (higher) {
        when { away > home -> -1; home > away -> 1; else -> 0 }
    } else {
        when { away < home -> -1; home < away -> 1; else -> 0 }
    }
}

@Composable
private fun NFLBoxScoreRow(
    label: String,
    awayValue: Double?,
    homeValue: Double?,
    awayDiff: NFLVsSeasonAvgStat? = null,
    homeDiff: NFLVsSeasonAvgStat? = null,
    decimals: Int = 0,
    higherIsBetter: Boolean = true,
    awaySeasonHigh: SeasonHighEntry? = null,
    homeSeasonHigh: SeasonHighEntry? = null
) {
    ThreeColumnRow(
        leftText = "${awayValue.orDash(decimals)}${nflDiffSuffix(awayDiff, decimals)}",
        centerText = label,
        rightText = "${nflDiffPrefix(homeDiff, decimals)}${homeValue.orDash(decimals)}",
        advantage = nflValueAdvantage(awayValue, homeValue, higherIsBetter),
        leftSeasonHigh = awaySeasonHigh,
        rightSeasonHigh = homeSeasonHigh
    )
}

@Composable
private fun NFLBoxScoreSection(results: NFLGameResults) {
    val awayBox = results.teamBoxScore?.away ?: return
    val homeBox = results.teamBoxScore?.home ?: return
    val vsAway = results.vsSeasonAvg?.away
    val vsHome = results.vsSeasonAvg?.home
    val awaySH = results.seasonHighs?.away
    val homeSH = results.seasonHighs?.home

    SectionHeader("Box Score (vs Season Avg)")
    Spacer(modifier = Modifier.height(4.dp))

    NFLBoxScoreRow("PTS", awayBox.points, homeBox.points, vsAway?.points, vsHome?.points,
        awaySeasonHigh = awaySH?.get("points"), homeSeasonHigh = homeSH?.get("points"))
    NFLBoxScoreRow("Total Yds", awayBox.totalYards, homeBox.totalYards, vsAway?.totalYards, vsHome?.totalYards,
        awaySeasonHigh = awaySH?.get("totalYards"), homeSeasonHigh = homeSH?.get("totalYards"))
    NFLBoxScoreRow("Pass Yds", awayBox.passYards, homeBox.passYards, vsAway?.passYards, vsHome?.passYards,
        awaySeasonHigh = awaySH?.get("passYards"), homeSeasonHigh = homeSH?.get("passYards"))
    NFLBoxScoreRow("Rush Yds", awayBox.rushYards, homeBox.rushYards, vsAway?.rushYards, vsHome?.rushYards,
        awaySeasonHigh = awaySH?.get("rushYards"), homeSeasonHigh = homeSH?.get("rushYards"))
    NFLBoxScoreRow("Yds/Play", awayBox.yardsPerPlay, homeBox.yardsPerPlay, vsAway?.yardsPerPlay, vsHome?.yardsPerPlay, decimals = 2)
    NFLBoxScoreRow("1st Downs", awayBox.firstDowns, homeBox.firstDowns, vsAway?.firstDowns, vsHome?.firstDowns)

    // Conversions read better as "6/15" than as two separate rows.
    ThreeColumnRow(
        leftText = "${awayBox.thirdDownConv.orDash(0)}/${awayBox.thirdDownAtt.orDash(0)}${nflDiffSuffix(vsAway?.thirdDownPct, 1)}",
        centerText = "3rd Down",
        rightText = "${nflDiffPrefix(vsHome?.thirdDownPct, 1)}${homeBox.thirdDownConv.orDash(0)}/${homeBox.thirdDownAtt.orDash(0)}",
        advantage = nflValueAdvantage(awayBox.thirdDownPct, homeBox.thirdDownPct)
    )
    ThreeColumnRow(
        leftText = "${awayBox.fourthDownConv.orDash(0)}/${awayBox.fourthDownAtt.orDash(0)}",
        centerText = "4th Down",
        rightText = "${homeBox.fourthDownConv.orDash(0)}/${homeBox.fourthDownAtt.orDash(0)}",
        advantage = nflValueAdvantage(awayBox.fourthDownConv, homeBox.fourthDownConv)
    )
    ThreeColumnRow(
        leftText = "${awayBox.redZoneTds.orDash(0)}/${awayBox.redZoneTrips.orDash(0)}",
        centerText = "Red Zone",
        rightText = "${homeBox.redZoneTds.orDash(0)}/${homeBox.redZoneTrips.orDash(0)}",
        advantage = nflValueAdvantage(awayBox.redZoneTds, homeBox.redZoneTds)
    )

    NFLBoxScoreRow("Explosive", awayBox.explosivePlays, homeBox.explosivePlays, vsAway?.explosivePlays, vsHome?.explosivePlays)
    NFLBoxScoreRow("Success %", awayBox.successRate, homeBox.successRate, vsAway?.successRate, vsHome?.successRate, decimals = 1)
    NFLBoxScoreRow("EPA", awayBox.epa, homeBox.epa, decimals = 2)
    NFLBoxScoreRow("Turnovers", awayBox.turnovers, homeBox.turnovers, vsAway?.turnovers, vsHome?.turnovers, higherIsBetter = false)
    NFLBoxScoreRow("Takeaways", awayBox.takeaways, homeBox.takeaways, vsAway?.takeaways, vsHome?.takeaways)
    NFLBoxScoreRow("Sacks", awayBox.sacks, homeBox.sacks, vsAway?.sacks, vsHome?.sacks)
    NFLBoxScoreRow("Sacks Allowed", awayBox.sacksAllowed, homeBox.sacksAllowed, vsAway?.sacksAllowed, vsHome?.sacksAllowed, higherIsBetter = false)
    NFLBoxScoreRow("Penalties", awayBox.penalties, homeBox.penalties, vsAway?.penalties, vsHome?.penalties, higherIsBetter = false)
    NFLBoxScoreRow("Penalty Yds", awayBox.penaltyYards, homeBox.penaltyYards, vsAway?.penaltyYards, vsHome?.penaltyYards, higherIsBetter = false)
    NFLBoxScoreRow("Time of Poss", awayBox.timeOfPossession, homeBox.timeOfPossession, vsAway?.timeOfPossession, vsHome?.timeOfPossession, decimals = 1)

    Spacer(modifier = Modifier.height(6.dp))
}

// ============================================================================
// Player highlight sections
// ============================================================================

private fun nflPlayerLabel(name: String?, position: String?): String {
    if (name.isNullOrBlank()) return "-"
    return if (position.isNullOrBlank()) name else "$name ($position)"
}

private fun intAdv(away: Int?, home: Int?, higher: Boolean = true): Int =
    nflValueAdvantage(away?.toDouble(), home?.toDouble(), higher)

@Composable
private fun NFLQuarterbackSection(
    highlights: NFLGamePlayerHighlightsWrapper,
    showHeader: Boolean = true
) {
    if (showHeader) {
        SectionHeader("Quarterbacks")
        Spacer(modifier = Modifier.height(4.dp))
    }
    val away = highlights.away?.quarterback
    val home = highlights.home?.quarterback

    ThreeColumnRow(
        leftText = nflPlayerLabel(away?.name, away?.position),
        centerText = "QB",
        rightText = nflPlayerLabel(home?.name, home?.position),
        leftWeight = FontWeight.Bold,
        rightWeight = FontWeight.Bold
    )
    ThreeColumnRow(
        leftText = if (away?.completions != null) "${away.completions}/${away.attempts ?: "-"}" else "-",
        centerText = "C/ATT",
        rightText = if (home?.completions != null) "${home.completions}/${home.attempts ?: "-"}" else "-",
        advantage = intAdv(away?.completions, home?.completions)
    )
    ThreeColumnRow(
        leftText = away?.passYards?.toString() ?: "-",
        centerText = "Pass Yds",
        rightText = home?.passYards?.toString() ?: "-",
        advantage = intAdv(away?.passYards, home?.passYards)
    )
    ThreeColumnRow(
        leftText = away?.passTds?.toString() ?: "-",
        centerText = "TD",
        rightText = home?.passTds?.toString() ?: "-",
        advantage = intAdv(away?.passTds, home?.passTds)
    )
    ThreeColumnRow(
        leftText = away?.interceptions?.toString() ?: "-",
        centerText = "INT",
        rightText = home?.interceptions?.toString() ?: "-",
        advantage = intAdv(away?.interceptions, home?.interceptions, higher = false)
    )
    ThreeColumnRow(
        leftText = away?.sacksTaken?.toString() ?: "-",
        centerText = "Sacked",
        rightText = home?.sacksTaken?.toString() ?: "-",
        advantage = intAdv(away?.sacksTaken, home?.sacksTaken, higher = false)
    )
    ThreeColumnRow(
        leftText = away?.rushYards?.toString() ?: "-",
        centerText = "Rush Yds",
        rightText = home?.rushYards?.toString() ?: "-",
        advantage = intAdv(away?.rushYards, home?.rushYards)
    )
    ThreeColumnRow(
        leftText = away?.epa.orDash(1),
        centerText = "Pass EPA",
        rightText = home?.epa.orDash(1),
        advantage = nflValueAdvantage(away?.epa, home?.epa)
    )
}

@Composable
private fun NFLRushersSection(
    highlights: NFLGamePlayerHighlightsWrapper,
    showHeader: Boolean = true
) {
    if (showHeader) {
        SectionHeader("Rushers")
        Spacer(modifier = Modifier.height(4.dp))
    }
    val awayList = highlights.away?.rushers.orEmpty()
    val homeList = highlights.home?.rushers.orEmpty()
    val rows = maxOf(awayList.size, homeList.size)
    if (rows == 0) return

    for (i in 0 until rows) {
        val away = awayList.getOrNull(i)
        val home = homeList.getOrNull(i)
        if (i > 0) Spacer(modifier = Modifier.height(4.dp))
        ThreeColumnRow(
            leftText = nflPlayerLabel(away?.name, away?.position),
            centerText = if (i == 0) "RB" else "RB${i + 1}",
            rightText = nflPlayerLabel(home?.name, home?.position),
            leftWeight = FontWeight.Bold,
            rightWeight = FontWeight.Bold
        )
        ThreeColumnRow(
            leftText = if (away?.carries != null) "${away.rushYards ?: 0} (${away.carries})" else "-",
            centerText = "Yds (Att)",
            rightText = if (home?.carries != null) "${home.rushYards ?: 0} (${home.carries})" else "-",
            advantage = intAdv(away?.rushYards, home?.rushYards)
        )
        ThreeColumnRow(
            leftText = away?.yardsPerCarry.orDash(1),
            centerText = "YPC",
            rightText = home?.yardsPerCarry.orDash(1),
            advantage = nflValueAdvantage(away?.yardsPerCarry, home?.yardsPerCarry)
        )
        ThreeColumnRow(
            leftText = away?.rushTds?.toString() ?: "-",
            centerText = "TD",
            rightText = home?.rushTds?.toString() ?: "-",
            advantage = intAdv(away?.rushTds, home?.rushTds)
        )
    }
}

@Composable
private fun NFLReceiversSection(
    highlights: NFLGamePlayerHighlightsWrapper,
    showHeader: Boolean = true
) {
    if (showHeader) {
        SectionHeader("Receivers")
        Spacer(modifier = Modifier.height(4.dp))
    }
    val awayList = highlights.away?.receivers.orEmpty()
    val homeList = highlights.home?.receivers.orEmpty()
    val rows = maxOf(awayList.size, homeList.size)
    if (rows == 0) return

    for (i in 0 until rows) {
        val away = awayList.getOrNull(i)
        val home = homeList.getOrNull(i)
        if (i > 0) Spacer(modifier = Modifier.height(4.dp))
        ThreeColumnRow(
            leftText = nflPlayerLabel(away?.name, away?.position),
            centerText = "WR${i + 1}",
            rightText = nflPlayerLabel(home?.name, home?.position),
            leftWeight = FontWeight.Bold,
            rightWeight = FontWeight.Bold
        )
        ThreeColumnRow(
            leftText = if (away?.receptions != null) "${away.recYards ?: 0} (${away.receptions}/${away.targets ?: "-"})" else "-",
            centerText = "Yds (Rec/Tgt)",
            rightText = if (home?.receptions != null) "${home.recYards ?: 0} (${home.receptions}/${home.targets ?: "-"})" else "-",
            advantage = intAdv(away?.recYards, home?.recYards)
        )
        ThreeColumnRow(
            leftText = away?.recTds?.toString() ?: "-",
            centerText = "TD",
            rightText = home?.recTds?.toString() ?: "-",
            advantage = intAdv(away?.recTds, home?.recTds)
        )
    }
}

@Composable
private fun NFLDefendersSection(
    highlights: NFLGamePlayerHighlightsWrapper,
    showHeader: Boolean = true
) {
    if (showHeader) {
        SectionHeader("Defense")
        Spacer(modifier = Modifier.height(4.dp))
    }
    val awayList = highlights.away?.defenders.orEmpty()
    val homeList = highlights.home?.defenders.orEmpty()
    val rows = maxOf(awayList.size, homeList.size)
    if (rows == 0) return

    for (i in 0 until rows) {
        val away = awayList.getOrNull(i)
        val home = homeList.getOrNull(i)
        if (i > 0) Spacer(modifier = Modifier.height(4.dp))
        ThreeColumnRow(
            leftText = nflPlayerLabel(away?.name, away?.position),
            centerText = "D${i + 1}",
            rightText = nflPlayerLabel(home?.name, home?.position),
            leftWeight = FontWeight.Bold,
            rightWeight = FontWeight.Bold
        )
        ThreeColumnRow(
            leftText = away?.tackles?.toString() ?: "-",
            centerText = "Tackles",
            rightText = home?.tackles?.toString() ?: "-",
            advantage = intAdv(away?.tackles, home?.tackles)
        )
        ThreeColumnRow(
            leftText = away?.sacks.orDash(1),
            centerText = "Sacks",
            rightText = home?.sacks.orDash(1),
            advantage = nflValueAdvantage(away?.sacks, home?.sacks)
        )
        // Collapsed into one row: takeaways and pass breakups are rarely all
        // non-zero, and three more rows per player makes the section unreadable.
        ThreeColumnRow(
            leftText = "${away?.interceptions ?: 0}/${away?.passesDefensed ?: 0}/${away?.forcedFumbles ?: 0}",
            centerText = "INT/PD/FF",
            rightText = "${home?.interceptions ?: 0}/${home?.passesDefensed ?: 0}/${home?.forcedFumbles ?: 0}",
            advantage = intAdv(
                (away?.interceptions ?: 0) + (away?.forcedFumbles ?: 0),
                (home?.interceptions ?: 0) + (home?.forcedFumbles ?: 0)
            )
        )
    }
}

// ============================================================================
// Head-to-head
// ============================================================================

@Composable
private fun NFLH2HSection(h2h: NFLH2H) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionHeader("Head-to-Head (${h2h.teamAWins}-${h2h.teamBWins})")
    Spacer(modifier = Modifier.height(4.dp))

    h2h.series.forEach { series ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = series.dateRange, fontSize = 11.sp, color = mutedColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
// Charts tab
// ============================================================================

private data class NFLWeeklyPerformance(
    val weekNum: Int,
    val pointsScored: Double,
    val pointsAllowed: Double
)

private fun parseCumPointDiffByWeek(stats: JsonObject?): List<LineChartDataPoint> {
    val cum = stats?.get("cumPointDiffByWeek") as? JsonObject ?: return emptyList()
    return cum.mapNotNull { (weekKey, value) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val diff = (value as? JsonPrimitive)?.doubleOrNull
        if (weekNum != null && diff != null) LineChartDataPoint(x = weekNum.toDouble(), y = diff) else null
    }.sortedBy { it.x }
}

private fun parseNflPerformanceByWeek(stats: JsonObject?): List<NFLWeeklyPerformance> {
    val perf = stats?.get("performanceByWeek") as? JsonObject ?: return emptyList()
    return perf.mapNotNull { (weekKey, value) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val obj = value as? JsonObject
        val scored = (obj?.get("pointsScored") as? JsonPrimitive)?.doubleOrNull
        val allowed = (obj?.get("pointsAllowed") as? JsonPrimitive)?.doubleOrNull
        if (weekNum != null && scored != null && allowed != null) {
            NFLWeeklyPerformance(weekNum, scored, allowed)
        } else null
    }.sortedBy { it.weekNum }
}

@Composable
private fun NFLChartsTab(
    awayTeam: String,
    homeTeam: String,
    matchup: NFLMatchup,
    leagueCumPointDiffStats: LeagueCumPointDiffStats? = null,
    leagueWeeklyStats: LeagueWeeklyPointStats? = null,
    onCumPointDiffShareClick: ((() -> Unit)?) -> Unit = {},
    onWeeklyPerfShareClick: ((() -> Unit)?) -> Unit = {}
) {
    val awayStats = matchup.awayTeam.stats
    val homeStats = matchup.homeTeam.stats

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 36.dp)
    ) {
        NFLCumPointDiffChart(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayStats = awayStats,
            homeStats = homeStats,
            leagueCumPointDiffStats = leagueCumPointDiffStats,
            onShareClick = onCumPointDiffShareClick
        )

        Spacer(modifier = Modifier.height(24.dp))

        NFLWeeklyPerformanceChart(
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
private fun NFLCumPointDiffChart(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject?,
    homeStats: JsonObject?,
    leagueCumPointDiffStats: LeagueCumPointDiffStats? = null,
    onShareClick: ((() -> Unit)?) -> Unit = {}
) {
    Text(
        text = "Cumulative Point Differential Over Season",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    val awayDataPoints = parseCumPointDiffByWeek(awayStats)
    val homeDataPoints = parseCumPointDiffByWeek(homeStats)

    if (awayDataPoints.isEmpty() && homeDataPoints.isEmpty()) {
        Text(
            text = "Cumulative point differential data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val title = "$awayTeam vs $homeTeam - Cumulative Point Diff"

    val top10DataPoints = (leagueCumPointDiffStats?.top10ByWeek)?.mapNotNull { (weekKey, value) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val threshold = (value as? JsonPrimitive)?.doubleOrNull
        if (weekNum != null && threshold != null) LineChartDataPoint(x = weekNum.toDouble(), y = threshold) else null
    }?.sortedBy { it.x }.orEmpty()

    val seriesList = mutableListOf(
        LineChartSeries(label = awayTeam, dataPoints = awayDataPoints, color = "#2196F3"),
        LineChartSeries(label = homeTeam, dataPoints = homeDataPoints, color = "#FF5722")
    )
    if (top10DataPoints.isNotEmpty()) {
        seriesList.add(
            LineChartSeries(label = "Top 10", dataPoints = top10DataPoints, color = "#4CAF50", dashed = true)
        )
    }

    ShareableChartContainer(
        title = title,
        source = NFL_SOURCE,
        showShareButton = false,
        onShareClick = onShareClick,
        modifier = Modifier.fillMaxWidth().height(310.dp).padding(8.dp)
    ) {
        LineChartComponent(
            series = seriesList,
            yAxisTitle = "Point Diff",
            title = title,
            source = NFL_SOURCE,
            // League-wide bounds so the axis is identical on every matchup.
            customYMin = leagueCumPointDiffStats?.minCumPointDiff?.toFloat(),
            customYMax = leagueCumPointDiffStats?.maxCumPointDiff?.toFloat(),
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun NFLWeeklyPerformanceChart(
    awayTeam: String,
    homeTeam: String,
    awayStats: JsonObject?,
    homeStats: JsonObject?,
    leagueWeeklyStats: LeagueWeeklyPointStats? = null,
    onShareClick: ((() -> Unit)?) -> Unit = {}
) {
    // An NFL season is 18 weeks, so the filters are full season / second half /
    // first half rather than the MLB card's rolling 10-week windows.
    var weekFilter by remember { mutableIntStateOf(0) }

    val awayPerf = parseNflPerformanceByWeek(awayStats)
    val homePerf = parseNflPerformanceByWeek(homeStats)

    if (awayPerf.isEmpty() && homePerf.isEmpty()) {
        Text(
            text = "Weekly performance data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val maxWeek = maxOf(
        awayPerf.maxOfOrNull { it.weekNum } ?: 0,
        homePerf.maxOfOrNull { it.weekNum } ?: 0
    )
    val midpoint = maxWeek / 2

    fun filterPerf(list: List<NFLWeeklyPerformance>) = when (weekFilter) {
        1 -> list.filter { it.weekNum > midpoint }
        2 -> list.filter { it.weekNum <= midpoint }
        else -> list
    }

    val filteredAwayPerf = filterPerf(awayPerf)
    val filteredHomePerf = filterPerf(homePerf)

    Column {
        Text(
            text = "Weekly Performance (Points Scored vs Allowed)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeamStatsNavBadge("Full Season", weekFilter == 0) { weekFilter = 0 }
                TeamStatsNavBadge("2nd Half", weekFilter == 1) { weekFilter = 1 }
                TeamStatsNavBadge("1st Half", weekFilter == 2) { weekFilter = 2 }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val title = "$awayTeam vs $homeTeam - Weekly Performance"

        val scatterData = mutableListOf<ScatterPlotDataPoint>()
        filteredAwayPerf.forEach { perf ->
            scatterData.add(
                ScatterPlotDataPoint(
                    label = "$awayTeam W${perf.weekNum}",
                    x = perf.pointsScored,
                    y = perf.pointsAllowed,
                    sum = perf.pointsScored - perf.pointsAllowed,
                    teamCode = awayTeam,
                    color = "#2196F3"
                )
            )
        }
        filteredHomePerf.forEach { perf ->
            scatterData.add(
                ScatterPlotDataPoint(
                    label = "$homeTeam W${perf.weekNum}",
                    x = perf.pointsScored,
                    y = perf.pointsAllowed,
                    sum = perf.pointsScored - perf.pointsAllowed,
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
                modifier = Modifier.fillMaxWidth().height(540.dp).padding(8.dp),
                title = title,
                xAxisLabel = "Points Scored",
                yAxisLabel = "Points Allowed",
                invertYAxis = true,
                highlightedTeamCodes = setOf(awayTeam, homeTeam),
                showShareButton = false,
                onShareClick = onShareClick,
                quadrantTopRight = QuadrantConfig(label = "Dominant", color = "#4CAF50", lightModeColor = "#4CAF50"),
                quadrantTopLeft = QuadrantConfig(label = "Defensive", color = "#2196F3", lightModeColor = "#2196F3"),
                quadrantBottomLeft = QuadrantConfig(label = "Outplayed", color = "#F44336", lightModeColor = "#F44336"),
                quadrantBottomRight = QuadrantConfig(label = "Shootout", color = "#FF9800", lightModeColor = "#FF9800"),
                customCenterX = leagueWeeklyStats?.avgPointsScored,
                customCenterY = leagueWeeklyStats?.avgPointsAllowed,
                customXMin = leagueWeeklyStats?.minPointsScored,
                customXMax = leagueWeeklyStats?.maxPointsScored,
                customYMin = leagueWeeklyStats?.minPointsAllowed,
                customYMax = leagueWeeklyStats?.maxPointsAllowed,
                regressionData = scatterData,
                source = NFL_SOURCE,
                teamLegendItems = listOf(
                    TeamLegendEntry(awayTeam, Color(0xFF2196F3)),
                    TeamLegendEntry(homeTeam, Color(0xFFFF5722))
                )
            )
        }
    }
}

// ============================================================================
// Share images
// ============================================================================

private fun buildNflRecentFormShareBox(mu: NFLMatchup): ShareStatBox {
    val away = parseNflRecentForm(mu.awayTeam.stats)
    val home = parseNflRecentForm(mu.homeTeam.stats)

    fun row(label: String, a: NFLRecentFormStat?, h: NFLRecentFormStat?, decimals: Int, signed: Boolean = false) =
        ShareFiveColStat(
            if (signed) a?.value.signed(decimals) else a?.value.orDash(decimals), a?.rank, a?.rankDisplay,
            label,
            if (signed) h?.value.signed(decimals) else h?.value.orDash(decimals), h?.rank, h?.rankDisplay,
            rankAdvantage(a?.rank, h?.rank)
        )

    return ShareStatBox(
        title = "Recent Form",
        fiveColStats = listOf(
            ShareFiveColStat(
                away.recordText(), away?.recordRank, away?.recordRankDisplay,
                "Record",
                home.recordText(), home?.recordRank, home?.recordRankDisplay,
                rankAdvantage(away?.recordRank, home?.recordRank)
            ),
            row("Point Diff/G", away?.pointDiffPerGame, home?.pointDiffPerGame, 1, signed = true),
            row("Points/G", away?.pointsPerGame, home?.pointsPerGame, 1),
            row("Points Allowed/G", away?.pointsAllowedPerGame, home?.pointsAllowedPerGame, 1),
            row("Yards/G", away?.yardsPerGame, home?.yardsPerGame, 1),
            row("Turnover Diff/G", away?.turnoverDiffPerGame, home?.turnoverDiffPerGame, 2, signed = true)
        )
    )
}

private fun buildNflH2HShareBox(mu: NFLMatchup): ShareStatBox {
    val h2h = mu.h2h
    return if (h2h != null && h2h.totalGames > 0) {
        ShareStatBox(
            title = "H2H (${h2h.teamAWins}-${h2h.teamBWins})",
            threeColStats = h2h.series.map { series ->
                ShareThreeColStat(
                    "${series.teamAWins}W-${series.teamBWins}L",
                    series.dateRange,
                    "${series.teamBWins}W-${series.teamAWins}L",
                    when {
                        series.teamAWins > series.teamBWins -> -1
                        series.teamBWins > series.teamAWins -> 1
                        else -> 0
                    }
                )
            }
        )
    } else {
        ShareStatBox(title = "H2H", threeColStats = listOf(ShareThreeColStat("", "No recent meetings", "", 0)))
    }
}

@Composable
private fun NFLPreGameShareContent(mu: NFLMatchup, captureWidth: androidx.compose.ui.unit.Dp) {
    val comp = mu.comparisons ?: return
    val awayAbbrev = mu.awayTeam.abbreviation
    val homeAbbrev = mu.homeTeam.abbreviation

    val shareDate = try {
        formatBracketGameDate(mu.gameDate) ?: mu.gameDate.substringBefore("T")
    } catch (_: Exception) {
        mu.gameDate.substringBefore("T")
    }
    val shareOddsLine = mu.odds?.let { odds ->
        listOfNotNull(
            odds.details?.takeIf { it.isNotBlank() },
            odds.overUnder?.let { "O/U ${it.formatNflStat(1)}" }
        ).joinToString(" • ")
    } ?: ""

    val gameInfo = ShareGameInfo(
        awayTeam = awayAbbrev,
        homeTeam = homeAbbrev,
        eventLabel = shareDate,
        formattedDate = shareOddsLine,
        source = NFL_SOURCE,
        awayRecord = mu.awayTeam.record,
        homeRecord = mu.homeTeam.record,
        recordsBelowTeamName = true
    )

    fun buildStatBox(title: String, stats: Map<String, SideBySideStatComparison>?): ShareStatBox =
        ShareStatBox(
            title = title,
            fiveColStats = stats?.map { entry ->
                val stat = entry.value
                val decimals = nflDecimalsFor(stat.label)
                ShareFiveColStat(
                    stat.away.value.orDash(decimals), stat.away.rank, stat.away.rankDisplay,
                    stat.label,
                    stat.home.value.orDash(decimals), stat.home.rank, stat.home.rankDisplay,
                    rankAdvantage(stat.away.rank, stat.home.rank)
                )
            }?.take(9) ?: emptyList()
        )

    fun buildOvdBox(
        title: String,
        leftLabel: String,
        rightLabel: String,
        stats: Map<String, MatchupStatComparison>,
        lc: Color = Team1Color,
        rc: Color = Team2Color
    ): ShareStatBox = ShareStatBox(
        title = title,
        leftLabel = leftLabel,
        middleLabel = "vs",
        rightLabel = rightLabel,
        leftColor = lc,
        rightColor = rc,
        fiveColStats = stats.map { entry ->
            val s = entry.value
            val decimals = nflDecimalsFor(s.offLabel)
            ShareFiveColStat(
                s.offense.value.orDash(decimals), s.offense.rank, s.offense.rankDisplay,
                s.offLabel,
                s.defense.value.orDash(decimals), s.defense.rank, s.defense.rankDisplay,
                s.advantage ?: 0
            )
        }.take(9)
    )

    val statBoxes = listOf(
        buildStatBox("Offense", comp.sideBySide?.offense),
        buildStatBox("Defense", comp.sideBySide?.defense),
        buildStatBox("Overall", comp.sideBySide?.overall),
        buildOvdBox("$awayAbbrev Off vs $homeAbbrev Def", "$awayAbbrev Off", "$homeAbbrev Def", comp.awayOffVsHomeDef),
        buildOvdBox("$homeAbbrev Off vs $awayAbbrev Def", "$homeAbbrev Off", "$awayAbbrev Def", comp.homeOffVsAwayDef, Team2Color, Team1Color),
        buildNflRecentFormShareBox(mu),
        buildNflH2HShareBox(mu)
    )
    val finalBoxes = statBoxes + List((6 - statBoxes.size).coerceAtLeast(0)) {
        ShareStatBox(title = "", fiveColStats = emptyList())
    }

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

@Composable
private fun NFLShareHeader(mu: NFLMatchup, textColor: Color, secondaryTextColor: Color) {
    val results = mu.results
    val awayWon = results?.homeWon == false
    val homeWon = results?.homeWon == true

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    mu.awayTeam.abbreviation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = if (awayWon) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
                mu.awayTeam.record?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "${results?.awayScore ?: "-"}",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                fontWeight = if (awayWon) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
        Text(
            results?.let { "${it.winner} +${it.margin}" } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor,
            maxLines = 1
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${results?.homeScore ?: "-"}",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                fontWeight = if (homeWon) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    mu.homeTeam.abbreviation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    fontWeight = if (homeWon) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
                mu.homeTeam.record?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = secondaryTextColor, maxLines = 1)
                }
            }
        }
    }

    val subtitle = listOfNotNull(
        try { formatBracketGameDate(mu.gameDate) } catch (_: Exception) { null },
        mu.season?.let { season ->
            if (mu.seasonType == "REG") mu.week?.let { "Week $it" } else mu.seasonTypeLabel ?: mu.seasonType
        }
    ).joinToString(" • ")
    if (subtitle.isNotBlank()) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryTextColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun NFLPostGameShareContent(matchup: NFLMatchup, modifier: Modifier = Modifier) {
    val results = matchup.results ?: return
    val bg = MaterialTheme.colorScheme.background
    val isDark = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryTextColor = if (isDark) Color.LightGray else Color.DarkGray

    Column(modifier = modifier.padding(12.dp)) {
        NFLShareHeader(matchup, textColor, secondaryTextColor)
        Spacer(modifier = Modifier.height(6.dp))
        if (results.teamBoxScore != null) {
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
            NFLBoxScoreSection(results = results)
        }
    }
}

@Composable
private fun NFLPlayerShareContent(
    matchup: NFLMatchup,
    target: NflCaptureTarget,
    modifier: Modifier = Modifier
) {
    val highlights = matchup.results?.playerHighlights ?: return
    val bg = MaterialTheme.colorScheme.background
    val isDark = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryTextColor = if (isDark) Color.LightGray else Color.DarkGray

    Column(modifier = modifier.padding(12.dp)) {
        NFLShareHeader(matchup, textColor, secondaryTextColor)
        Spacer(modifier = Modifier.height(8.dp))
        when (target) {
            NflCaptureTarget.QUARTERBACKS -> NFLQuarterbackSection(highlights)
            NflCaptureTarget.RUSHERS -> NFLRushersSection(highlights)
            NflCaptureTarget.RECEIVERS -> NFLReceiversSection(highlights)
            NflCaptureTarget.DEFENDERS -> NFLDefendersSection(highlights)
            else -> Unit
        }
    }
}
