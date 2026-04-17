package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.components.FabOption
import com.joebad.fastbreak.ui.components.MultiOptionFab
import com.joebad.fastbreak.ui.components.ShareFab
import com.joebad.fastbreak.ui.visualizations.BracketTeamStatsView
import com.joebad.fastbreak.ui.visualizations.BracketOffenseVsDefenseView
import com.joebad.fastbreak.ui.visualizations.BracketRecordSection
import com.joebad.fastbreak.ui.visualizations.TbdMatchupRow
import io.github.koalaplot.core.line.LinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.XYGraphScope
import io.github.koalaplot.core.xygraph.rememberAxisStyle
import io.github.koalaplot.core.gestures.GestureConfig
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.math.round

// ============================================================================
// Local data types (mirroring NCAABracket's BracketTeam / BracketGame)
// ============================================================================

private data class NBAMatchupSheetData(
    val matchup: PlayoffMatchupInfo,
    val conferenceName: String,
    val conferenceColor: Color,
    val roundName: String,
    val visualization: NBAPlayoffBracketVisualization? = null
)

// ============================================================================
// Scalable coordinate system
//
// Layout: East on top, West on bottom. Each conference has a left arm and
// a right arm that converge through semis to the conference finals at centerX.
// The two conference finals then meet at the NBA Finals in the center.
//
//       ┌─ 1v8 ─┐           ┌─ 3v6 ─┐
//       └─ 4v5 ─┘→ Semi 0   └─ 2v7 ─┘→ Semi 1
//                    └── East CF ──┘
//                         │
//                     NBA Finals
//                         │
//                    ┌── West CF ──┐
//       ┌─ 1v8 ─┐→ Semi 0   ┌─ 3v6 ─┐→ Semi 1
//       └─ 4v5 ─┘           └─ 2v7 ─┘
// ============================================================================

// ============================================================================
// Main composable
// ============================================================================

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun NBAPlayoffBracket(
    visualization: NBAPlayoffBracketVisualization,
    modifier: Modifier = Modifier,
    onNavigationToggleHandlerChanged: ((BracketNavigationToggleHandler?) -> Unit)? = null
) {
    val conferences = remember(visualization) { visualization.conferences.map { convertPlayoffConference(it) } }
    val finalsGame = remember(visualization) { convertPlayoffMatchupToGame(visualization.finals) }
    var selectedMatchup by remember { mutableStateOf<NBAMatchupSheetData?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isPortrait = maxWidth < maxHeight
        val pos = remember(isPortrait) { PlayoffBracketPositions(isPortrait) }

        Box(modifier = Modifier.fillMaxSize()) {
            PlayoffBracketCanvas(
                conferences = conferences,
                finalsGame = finalsGame,
                finalsLabel = "NBA Finals",
                pos = pos,
                onMatchupClick = { mu, confName, roundName, color ->
                    selectedMatchup = NBAMatchupSheetData(mu, confName, color, roundName, visualization)
                }
            )
        }
    }

    // Bottom sheet
    selectedMatchup?.let { data ->
        NBAPlayoffMatchupBottomSheet(data = data, onDismiss = { selectedMatchup = null })
    }
}

@Composable
private fun NBASeriesResultsView(
    games: List<PlayoffSeriesGameInfo>,
    t1Abbrev: String,
    t2Abbrev: String
) {
    SectionHeader("Best of 7")
    Spacer(modifier = Modifier.height(4.dp))

    // Column headers
    FiveColumnRowWithRanks(
        leftValue = t1Abbrev, leftRank = null, leftRankDisplay = null,
        centerText = "", rightValue = t2Abbrev, rightRank = null, rightRankDisplay = null,
        advantage = 0
    )

    // Always show 7 game slots
    for (idx in 0 until 7) {
        val game = games.getOrNull(idx)

        if (game != null) {
            val t1Score = game.team1?.score
            val t2Score = game.team2?.score
            val t1Won = game.team1?.winner == true
            val t2Won = game.team2?.winner == true
            val isCompleted = game.completed
            val dateLabel = game.gameDate?.let { formatBracketGameDate(it) }
            val homeLabel = game.homeTeamAbbrev?.let { "@ $it" } ?: ""

            val advantage = when {
                t1Won -> -1
                t2Won -> 1
                else -> 0
            }

            val centerText = "Game ${idx + 1}" + if (homeLabel.isNotEmpty()) "\n$homeLabel" else ""

            if (isCompleted) {
                FiveColumnRowWithRanks(
                    leftValue = "${t1Score ?: "-"}",
                    leftRank = null, leftRankDisplay = null,
                    centerText = centerText,
                    rightValue = "${t2Score ?: "-"}",
                    rightRank = null, rightRankDisplay = null,
                    advantage = advantage
                )
                dateLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        fontSize = 10.sp)
                }
            } else {
                FiveColumnRowWithRanks(
                    leftValue = "-",
                    leftRank = null, leftRankDisplay = null,
                    centerText = centerText,
                    rightValue = "-",
                    rightRank = null, rightRankDisplay = null,
                    advantage = 0
                )
                val oddsLabel = game.odds?.let { formatPlayoffGameOddsLine(it) }
                val scheduleLine = listOfNotNull(dateLabel, oddsLabel).joinToString(" · ")
                if (scheduleLine.isNotEmpty()) {
                    Text(scheduleLine, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        fontSize = 10.sp)
                }
            }
        } else {
            FiveColumnRowWithRanks(
                leftValue = "-",
                leftRank = null, leftRankDisplay = null,
                centerText = "Game ${idx + 1}",
                rightValue = "-",
                rightRank = null, rightRankDisplay = null,
                advantage = 0
            )
        }
    }
}

private val monthTrendJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private fun parseMonthTrend(teamStats: JsonObject?): MonthTrendStats? {
    val element = teamStats?.get("monthTrend") ?: return null
    return try {
        monthTrendJson.decodeFromJsonElement<MonthTrendStats>(element)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun NBAMonthTrendView(
    t1Abbrev: String,
    t2Abbrev: String,
    t1Trend: MonthTrendStats,
    t2Trend: MonthTrendStats
) {
    val gp = t1Trend.gamesPlayed.coerceAtLeast(t2Trend.gamesPlayed)
    SectionHeader("Last Month (Last $gp Games)")
    Spacer(modifier = Modifier.height(4.dp))

    // Column headers
    FiveColumnRowWithRanks(
        leftValue = t1Abbrev, leftRank = null, leftRankDisplay = null,
        centerText = "", rightValue = t2Abbrev, rightRank = null, rightRankDisplay = null,
        advantage = 0
    )

    // Record row
    val r1 = t1Trend.record
    val r2 = t2Trend.record
    if (r1 != null && r2 != null) {
        val leftText = "${r1.wins}-${r1.losses}"
        val rightText = "${r2.wins}-${r2.losses}"
        val advantage = when {
            r1.rank != null && r2.rank != null && r1.rank < r2.rank -> -1
            r1.rank != null && r2.rank != null && r1.rank > r2.rank -> 1
            else -> 0
        }
        FiveColumnRowWithRanks(
            leftValue = leftText,
            leftRank = r1.rank, leftRankDisplay = r1.rankDisplay,
            centerText = "Record",
            rightValue = rightText,
            rightRank = r2.rank, rightRankDisplay = r2.rankDisplay,
            advantage = advantage,
            useCBBRanks = false,
            rankColorFn = ::nbaPlayoffRankColor
        )
    }

    val rows = listOf(
        "Net Rating" to (t1Trend.netRating to t2Trend.netRating),
        "Offensive Rating" to (t1Trend.offensiveRating to t2Trend.offensiveRating),
        "Defensive Rating" to (t1Trend.defensiveRating to t2Trend.defensiveRating),
        "Points/Game" to (t1Trend.pointsPerGame to t2Trend.pointsPerGame),
        "Assists/Game" to (t1Trend.assistsPerGame to t2Trend.assistsPerGame),
        "Turnovers/Game" to (t1Trend.turnoversPerGame to t2Trend.turnoversPerGame),
        "Turnover Diff" to (t1Trend.turnoverDiff to t2Trend.turnoverDiff)
    )
    rows.forEach { (label, pair) ->
        val (s1, s2) = pair
        if (s1 == null && s2 == null) return@forEach
        val advantage = when {
            s1?.rank != null && s2?.rank != null && s1.rank < s2.rank -> -1
            s1?.rank != null && s2?.rank != null && s1.rank > s2.rank -> 1
            else -> 0
        }
        FiveColumnRowWithRanks(
            leftValue = s1?.value?.bracketFormatStat(2) ?: "-",
            leftRank = s1?.rank, leftRankDisplay = s1?.rankDisplay,
            centerText = label,
            rightValue = s2?.value?.bracketFormatStat(2) ?: "-",
            rightRank = s2?.rank, rightRankDisplay = s2?.rankDisplay,
            advantage = advantage,
            useCBBRanks = false,
            rankColorFn = ::nbaPlayoffRankColor
        )
    }
}

@Composable
private fun NBARegularSeasonHistoryView(history: RegularSeasonHistory) {
    val teamA = history.teamAAbbrev ?: "A"
    val teamB = history.teamBAbbrev ?: "B"

    SectionHeader("Regular Season Series")
    Spacer(modifier = Modifier.height(4.dp))

    val recordAdvantage = when {
        history.teamAWins > history.teamBWins -> -1
        history.teamBWins > history.teamAWins -> 1
        else -> 0
    }
    FiveColumnRowWithRanks(
        leftValue = history.teamAWins.toString(),
        leftRank = null, leftRankDisplay = null,
        centerText = "Record",
        rightValue = history.teamBWins.toString(),
        rightRank = null, rightRankDisplay = null,
        advantage = recordAdvantage
    )

    if (history.games.isEmpty()) return

    Spacer(modifier = Modifier.height(8.dp))

    FiveColumnRowWithRanks(
        leftValue = teamA,
        leftRank = null, leftRankDisplay = null,
        centerText = "",
        rightValue = teamB,
        rightRank = null, rightRankDisplay = null,
        advantage = 0
    )

    history.games.forEach { game ->
        val aIsHome = game.homeAbbrev == teamA
        val aScore = if (aIsHome) game.homeScore else game.awayScore
        val bScore = if (aIsHome) game.awayScore else game.homeScore
        val advantage = when (game.winnerAbbrev) {
            teamA -> -1
            teamB -> 1
            else -> 0
        }
        val homeLabel = game.homeAbbrev?.let { "@ $it" }.orEmpty()
        val dateLabel = formatRegularSeasonDate(game.gameDate)
        val centerText = buildString {
            if (dateLabel != null) append(dateLabel)
            if (homeLabel.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(homeLabel)
            }
        }

        FiveColumnRowWithRanks(
            leftValue = aScore?.toString() ?: "-",
            leftRank = null, leftRankDisplay = null,
            centerText = centerText,
            rightValue = bScore?.toString() ?: "-",
            rightRank = null, rightRankDisplay = null,
            advantage = advantage
        )
    }
}

private fun formatRegularSeasonDate(date: String?): String? {
    if (date.isNullOrBlank()) return null
    return try {
        val parts = date.take(10).split("-")
        if (parts.size != 3) return null
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        val monthName = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )[month - 1]
        "$monthName $day"
    } catch (_: Exception) {
        null
    }
}

private fun nbaPlayoffRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    return when {
        rank <= 5 -> {
            // Green gradient: bright green (#00962A) → darker green (#507D32)
            val ratio = (rank - 1) / 4f
            Color(
                red = (0 + ratio * 80).toInt(),
                green = (150 - ratio * 25).toInt(),
                blue = (42 - ratio * 32).toInt()
            )
        }
        rank <= 15 -> {
            // Orange gradient: bright orange (#FFA000) → dark orange (#C86400)
            val ratio = (rank - 6) / 9f
            Color(
                red = (255 - ratio * 55).toInt(),
                green = (160 - ratio * 60).toInt(),
                blue = 0
            )
        }
        else -> {
            // Red gradient: red (#C83232) → dark red (#8B0000)
            val ratio = ((rank - 16).coerceAtMost(14)) / 14f
            Color(
                red = (200 - ratio * 61).toInt(),
                green = (50 - ratio * 50).toInt(),
                blue = 0
            )
        }
    }
}

private fun parseNBAHexColor(hexColor: String): Color? {
    return try {
        val hex = hexColor.removePrefix("#")
        when (hex.length) {
            6 -> Color(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
            else -> null
        }
    } catch (_: Exception) { null }
}

// ============================================================================
// Bottom sheet for detailed matchup stats
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NBAPlayoffMatchupBottomSheet(data: NBAMatchupSheetData, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var viewSelection by remember { mutableIntStateOf(0) }
    val matchup = data.matchup
    val comparisons = matchup.comparisons

    val t1 = matchup.team1
    val t2 = matchup.team2
    val t1Name = t1?.name ?: "TBD"
    val t2Name = t2?.name ?: "TBD"
    val t1Abbrev = t1?.abbreviation ?: t1Name.take(3).uppercase()
    val t2Abbrev = t2?.abbreviation ?: t2Name.take(3).uppercase()
    val t1Display = if (t1 != null && t1.seed > 0) "(${t1.seed}) $t1Name" else t1Name
    val t2Display = if (t2 != null && t2.seed > 0) "(${t2.seed}) $t2Name" else t2Name

    val hasTbdTeam = t1 == null || t2 == null ||
        t1.name.equals("TBD", ignoreCase = true) || t2.name.equals("TBD", ignoreCase = true)

    // 0 = Comparisons, 1 = Series, 2 = Charts
    var topNavSelection by remember { mutableIntStateOf(0) }

    // Share capture state
    var captureRequested by remember { mutableStateOf(false) }

    // Chart share callbacks (set by chart composables)
    var netRatingShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var offDefRatingShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 36.dp, bottom = 96.dp)
                ) {
                    // Record row: team1 record (left) | series status (center) | team2 record (right)
                    if (t1 != null || t2 != null) {
                        PlayoffMatchupRecordRow(
                            team1 = t1,
                            team2 = t2,
                            seriesStatus = playoffSeriesStatus(matchup, t1, t2),
                            seriesStatusColor = data.conferenceColor
                        )
                    }

                    // Round and conference info
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp)
                            .background(data.conferenceColor, RoundedCornerShape(4.dp)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${data.roundName} • ${data.conferenceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (hasTbdTeam) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TbdMatchupRow()
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center) {
                            Text("vs", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TbdMatchupRow()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Matchup will be determined by earlier round results",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    } else {
                        // Top-level nav: Comparisons | Series | Charts
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TeamStatsNavBadge("Comparisons", topNavSelection == 0) { topNavSelection = 0 }
                            TeamStatsNavBadge("Series", topNavSelection == 1) { topNavSelection = 1 }
                            TeamStatsNavBadge("Charts", topNavSelection == 2) { topNavSelection = 2 }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (topNavSelection) {
                            0 -> {
                                // Comparisons sub-nav
                                if (comparisons != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TeamStatsNavBadge("Team", viewSelection == 0) { viewSelection = 0 }
                                        TeamStatsNavBadge("$t1Abbrev Off vs $t2Abbrev Def", viewSelection == 1) { viewSelection = 1 }
                                        TeamStatsNavBadge("$t2Abbrev Off vs $t1Abbrev Def", viewSelection == 2) { viewSelection = 2 }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    when (viewSelection) {
                                        0 -> {
                                            BracketTeamStatsView(comparisons, rankColorFn = ::nbaPlayoffRankColor)
                                            val t1Trend = parseMonthTrend(t1?.teamStats)
                                            val t2Trend = parseMonthTrend(t2?.teamStats)
                                            if (t1Trend != null && t2Trend != null) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                NBAMonthTrendView(t1Abbrev, t2Abbrev, t1Trend, t2Trend)
                                            }
                                        }
                                        1 -> BracketOffenseVsDefenseView(comparisons.homeOffVsAwayDef, t1Name, t2Name, ::nbaPlayoffRankColor)
                                        2 -> BracketOffenseVsDefenseView(comparisons.awayOffVsHomeDef, t2Name, t1Name, ::nbaPlayoffRankColor)
                                    }
                                } else {
                                    Text("Comparison stats not available",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                }

                                matchup.regularSeasonHistory?.let { history ->
                                    Spacer(modifier = Modifier.height(16.dp))
                                    NBARegularSeasonHistoryView(history)
                                }
                            }
                            1 -> {
                                // Series Results view
                                NBASeriesResultsView(
                                    games = matchup.games,
                                    t1Abbrev = t1Abbrev,
                                    t2Abbrev = t2Abbrev
                                )
                            }
                            2 -> {
                                // Charts view
                                val viz = data.visualization
                                val t1Stats = t1?.teamStats
                                val t2Stats = t2?.teamStats
                                if (t1Stats != null && t2Stats != null) {
                                    CumulativeNetRatingChart(
                                        awayTeam = t1Abbrev,
                                        homeTeam = t2Abbrev,
                                        awayStats = t1Stats,
                                        homeStats = t2Stats,
                                        tenthNetRatingByWeek = viz?.tenthNetRatingByWeek,
                                        leagueCumNetRatingStats = viz?.leagueCumNetRatingStats,
                                        onShareClick = { callback -> netRatingShareCallback = callback }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    WeeklyEfficiencyScatterPlot(
                                        awayTeam = t1Abbrev,
                                        homeTeam = t2Abbrev,
                                        awayStats = t1Stats,
                                        homeStats = t2Stats,
                                        leagueStats = viz?.leagueEfficiencyStats,
                                        quadrantConfig = viz?.scatterPlotQuadrants,
                                        onShareClick = { callback -> offDefRatingShareCallback = callback }
                                    )
                                } else {
                                    Text("Chart data not available",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }

                // Pinned header (reused from NCAA bracket components)
                PinnedMatchupHeader(
                    awayTeam = t1Display,
                    homeTeam = t2Display,
                    awayScore = t1?.score,
                    homeScore = t2?.score,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

        // Share FAB
        if (!hasTbdTeam) {
            when (topNavSelection) {
                2 -> {
                    // Charts tab: multi-option FAB for chart sharing
                    val chartOptions = listOfNotNull(
                        netRatingShareCallback?.let { cb ->
                            FabOption(
                                icon = Icons.Filled.TrendingUp,
                                label = "Cumulative Net Rating",
                                onClick = { cb() }
                            )
                        },
                        offDefRatingShareCallback?.let { cb ->
                            FabOption(
                                icon = Icons.Filled.Star,
                                label = "Off vs Def Rating",
                                onClick = { cb() }
                            )
                        }
                    )
                    if (chartOptions.isNotEmpty()) {
                        MultiOptionFab(
                            options = chartOptions,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        )
                    }
                }
                0 -> {
                    // Comparisons tab: share matchup worksheet
                    if (comparisons != null) {
                        ShareFab(
                            onClick = { captureRequested = true },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        )
                    }
                }
                else -> {}
            }
        }

        // Off-screen capture content
        if (captureRequested) {
            val shareTitle = "$t1Abbrev vs $t2Abbrev - ${data.roundName}"

            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(50)
                try {
                    val bitmap = graphicsLayer.toImageBitmap()
                    imageExporter.shareImage(bitmap, shareTitle)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    captureRequested = false
                }
            }

            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                Box(
                    modifier = Modifier
                        .requiredWidth(3400.dp)
                        .requiredHeight(1400.dp)
                        .offset { IntOffset(-10000, 0) }
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                ) {
                    val nextGameDate = matchup.games
                        .firstOrNull { !it.completed && it.gameDate != null }
                        ?.gameDate
                        ?.let { formatBracketGameDate(it) }
                    val gameInfo = ShareGameInfo(
                        awayTeam = t1Abbrev,
                        homeTeam = t2Abbrev,
                        eventLabel = "${data.roundName} • ${data.conferenceName}",
                        formattedDate = nextGameDate?.let { "Next: $it" } ?: "",
                        source = "hoopR / ESPN",
                        awayRecord = t1?.wins?.let { w -> t1.losses?.let { l -> "$w-$l" } },
                        homeRecord = t2?.wins?.let { w -> t2.losses?.let { l -> "$w-$l" } },
                        awaySeed = t1?.seed?.takeIf { it > 0 },
                        homeSeed = t2?.seed?.takeIf { it > 0 },
                        seriesStatus = playoffSeriesStatus(matchup, t1, t2)
                    )

                    val comp = comparisons ?: return@Box
                    val statBoxes = buildList {
                        // Box 1: Offensive Stats
                        add(ShareStatBox(
                            title = "Offensive Stats",
                            fiveColStats = comp.sideBySide?.offense?.mapNotNull { (_, stat) ->
                                val isPct = stat.label.contains("%")
                                val leftVal = stat.home.value?.let { if (isPct) (it * 100).bracketFormatStat(1) else it.bracketFormatStat(2) } ?: return@mapNotNull null
                                val rightVal = stat.away.value?.let { if (isPct) (it * 100).bracketFormatStat(1) else it.bracketFormatStat(2) } ?: return@mapNotNull null
                                val adv = if (stat.home.rank != null && stat.away.rank != null) {
                                    when { stat.home.rank < stat.away.rank -> -1; stat.home.rank > stat.away.rank -> 1; else -> 0 }
                                } else 0
                                ShareFiveColStat(leftVal, stat.home.rank, stat.home.rankDisplay, stat.label, rightVal, stat.away.rank, stat.away.rankDisplay, adv)
                            }?.take(9) ?: emptyList()
                        ))

                        // Box 2: Defensive Stats
                        add(ShareStatBox(
                            title = "Defensive Stats",
                            fiveColStats = comp.sideBySide?.defense?.mapNotNull { (_, stat) ->
                                val isPct = stat.label.contains("%")
                                val leftVal = stat.home.value?.let { if (isPct) (it * 100).bracketFormatStat(1) else it.bracketFormatStat(2) } ?: return@mapNotNull null
                                val rightVal = stat.away.value?.let { if (isPct) (it * 100).bracketFormatStat(1) else it.bracketFormatStat(2) } ?: return@mapNotNull null
                                val adv = if (stat.home.rank != null && stat.away.rank != null) {
                                    when { stat.home.rank < stat.away.rank -> -1; stat.home.rank > stat.away.rank -> 1; else -> 0 }
                                } else 0
                                ShareFiveColStat(leftVal, stat.home.rank, stat.home.rankDisplay, stat.label, rightVal, stat.away.rank, stat.away.rankDisplay, adv)
                            }?.take(9) ?: emptyList()
                        ))

                        // Box 3: T1 Off vs T2 Def
                        add(ShareStatBox(
                            title = "$t1Abbrev Off vs $t2Abbrev Def",
                            leftLabel = "$t1Abbrev Off",
                            middleLabel = "vs",
                            rightLabel = "$t2Abbrev Def",
                            fiveColStats = comp.homeOffVsAwayDef.mapNotNull { (_, stat) ->
                                val isPct = stat.offLabel.contains("%")
                                val offVal = stat.offense.value?.let { if (isPct) (it * 100).bracketFormatStat(1) else it.bracketFormatStat(2) } ?: return@mapNotNull null
                                val defVal = stat.defense.value?.let { if (isPct) (it * 100).bracketFormatStat(1) else it.bracketFormatStat(2) } ?: return@mapNotNull null
                                ShareFiveColStat(offVal, stat.offense.rank, stat.offense.rankDisplay, stat.offLabel, defVal, stat.defense.rank, stat.defense.rankDisplay, stat.advantage ?: 0)
                            }.take(9)
                        ))

                        // Box 4: T2 Off vs T1 Def
                        add(ShareStatBox(
                            title = "$t2Abbrev Off vs $t1Abbrev Def",
                            leftLabel = "$t2Abbrev Off",
                            middleLabel = "vs",
                            rightLabel = "$t1Abbrev Def",
                            leftColor = Team2Color,
                            rightColor = Team1Color,
                            fiveColStats = comp.awayOffVsHomeDef.mapNotNull { (_, stat) ->
                                val isPct = stat.offLabel.contains("%")
                                val offVal = stat.offense.value?.let { if (isPct) (it * 100).bracketFormatStat(1) else it.bracketFormatStat(2) } ?: return@mapNotNull null
                                val defVal = stat.defense.value?.let { if (isPct) (it * 100).bracketFormatStat(1) else it.bracketFormatStat(2) } ?: return@mapNotNull null
                                ShareFiveColStat(offVal, stat.offense.rank, stat.offense.rankDisplay, stat.offLabel, defVal, stat.defense.rank, stat.defense.rankDisplay, stat.advantage ?: 0)
                            }.take(9)
                        ))

                        // Box 5: 1-month trend
                        val t1Trend = parseMonthTrend(t1?.teamStats)
                        val t2Trend = parseMonthTrend(t2?.teamStats)
                        if (t1Trend != null && t2Trend != null) {
                            val gp = maxOf(t1Trend.gamesPlayed, t2Trend.gamesPlayed)
                            val trendRows = buildList<ShareFiveColStat> {
                                val r1 = t1Trend.record
                                val r2 = t2Trend.record
                                if (r1 != null && r2 != null) {
                                    val adv = if (r1.rank != null && r2.rank != null) {
                                        when { r1.rank < r2.rank -> -1; r1.rank > r2.rank -> 1; else -> 0 }
                                    } else 0
                                    add(ShareFiveColStat(
                                        "${r1.wins}-${r1.losses}", r1.rank, r1.rankDisplay,
                                        "Record",
                                        "${r2.wins}-${r2.losses}", r2.rank, r2.rankDisplay,
                                        adv
                                    ))
                                }
                                fun stat(label: String, s1: MonthTrendStat?, s2: MonthTrendStat?) {
                                    if (s1 == null && s2 == null) return
                                    val adv = if (s1?.rank != null && s2?.rank != null) {
                                        when { s1.rank < s2.rank -> -1; s1.rank > s2.rank -> 1; else -> 0 }
                                    } else 0
                                    add(ShareFiveColStat(
                                        s1?.value?.bracketFormatStat(2) ?: "-",
                                        s1?.rank, s1?.rankDisplay,
                                        label,
                                        s2?.value?.bracketFormatStat(2) ?: "-",
                                        s2?.rank, s2?.rankDisplay,
                                        adv
                                    ))
                                }
                                stat("Net Rating", t1Trend.netRating, t2Trend.netRating)
                                stat("Off Rating", t1Trend.offensiveRating, t2Trend.offensiveRating)
                                stat("Def Rating", t1Trend.defensiveRating, t2Trend.defensiveRating)
                                stat("Points/Game", t1Trend.pointsPerGame, t2Trend.pointsPerGame)
                                stat("Assists/Game", t1Trend.assistsPerGame, t2Trend.assistsPerGame)
                                stat("Turnovers/Game", t1Trend.turnoversPerGame, t2Trend.turnoversPerGame)
                                stat("Turnover Diff", t1Trend.turnoverDiff, t2Trend.turnoverDiff)
                            }
                            if (trendRows.isNotEmpty()) {
                                add(ShareStatBox(
                                    title = "Last $gp Games",
                                    fiveColStats = trendRows.take(9)
                                ))
                            }
                        }

                        // Box 6: Regular-season head-to-head
                        matchup.regularSeasonHistory?.let { history ->
                            val teamA = history.teamAAbbrev ?: t1Abbrev
                            val teamB = history.teamBAbbrev ?: t2Abbrev
                            val historyRows = buildList<ShareFiveColStat> {
                                val recAdv = when {
                                    history.teamAWins > history.teamBWins -> -1
                                    history.teamBWins > history.teamAWins -> 1
                                    else -> 0
                                }
                                add(ShareFiveColStat(
                                    history.teamAWins.toString(), null, null,
                                    "Series",
                                    history.teamBWins.toString(), null, null,
                                    recAdv
                                ))
                                history.games.forEach { g ->
                                    val aIsHome = g.homeAbbrev == teamA
                                    val aScore = if (aIsHome) g.homeScore else g.awayScore
                                    val bScore = if (aIsHome) g.awayScore else g.homeScore
                                    val adv = when (g.winnerAbbrev) {
                                        teamA -> -1
                                        teamB -> 1
                                        else -> 0
                                    }
                                    val homeLabel = g.homeAbbrev?.let { "@$it" }.orEmpty()
                                    val dateLabel = formatRegularSeasonDate(g.gameDate).orEmpty()
                                    val center = listOf(dateLabel, homeLabel)
                                        .filter { it.isNotEmpty() }
                                        .joinToString(" ")
                                    add(ShareFiveColStat(
                                        aScore?.toString() ?: "-", null, null,
                                        center,
                                        bScore?.toString() ?: "-", null, null,
                                        adv
                                    ))
                                }
                            }
                            if (historyRows.isNotEmpty()) {
                                add(ShareStatBox(
                                    title = "Regular Season Series",
                                    fiveColStats = historyRows.take(9)
                                ))
                            }
                        }
                    }

                    val finalBoxes = statBoxes + List((6 - statBoxes.size).coerceAtLeast(0)) {
                        ShareStatBox(title = "", fiveColStats = emptyList())
                    }

                    GenericMatchupShareImage(
                        gameInfo = gameInfo,
                        statBoxes = finalBoxes.take(6),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        } // end outer Box
    }
}

@Composable
private fun NBAGameScoresRow(games: List<PlayoffSeriesGameInfo>) {
    val completedGames = games.filter { it.completed }
    if (completedGames.isEmpty()) return

    SectionHeader("Game Results")
    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for ((idx, game) in completedGames.withIndex()) {
            Surface(shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("G${idx + 1}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    game.team1?.let { t ->
                        Text("${t.abbreviation ?: "?"} ${t.score ?: "-"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (t.winner) FontWeight.Bold else FontWeight.Normal)
                    }
                    game.team2?.let { t ->
                        Text("${t.abbreviation ?: "?"} ${t.score ?: "-"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (t.winner) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
