package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
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
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.components.FabOption
import com.joebad.fastbreak.ui.components.MultiOptionFab
import com.joebad.fastbreak.ui.components.ShareFab
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

@Composable
fun NHLPlayoffBracket(
    visualization: NHLPlayoffBracketVisualization,
    modifier: Modifier = Modifier,
    onNavigationToggleHandlerChanged: ((BracketNavigationToggleHandler?) -> Unit)? = null
) {
    val conferences = remember(visualization) { visualization.conferences.map { convertPlayoffConference(it) } }
    val finalsGame = remember(visualization) { convertPlayoffMatchupToGame(visualization.finals) }
    var selectedMatchup by remember { mutableStateOf<PlayoffMatchupSheetData?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isPortrait = maxWidth < maxHeight
        val pos = remember(isPortrait) { PlayoffBracketPositions(isPortrait) }

        Box(modifier = Modifier.fillMaxSize()) {
            PlayoffBracketCanvas(
                conferences = conferences, finalsGame = finalsGame, finalsLabel = "Stanley Cup Final",
                pos = pos,
                onMatchupClick = { mu, confName, roundName, color ->
                    selectedMatchup = PlayoffMatchupSheetData(mu, confName, color, roundName)
                }
            )
        }
    }

    selectedMatchup?.let { data ->
        NHLPlayoffMatchupBottomSheet(data = data, visualization = visualization, onDismiss = { selectedMatchup = null })
    }
}

// ============================================================================
// Bottom sheet
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NHLPlayoffMatchupBottomSheet(
    data: PlayoffMatchupSheetData,
    visualization: NHLPlayoffBracketVisualization,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var viewSelection by remember { mutableIntStateOf(0) }
    var topNavSelection by remember { mutableIntStateOf(0) }
    val matchup = data.matchup
    val comparisons = matchup.comparisons

    val t1 = matchup.team1; val t2 = matchup.team2
    val t1Name = t1?.name ?: "TBD"; val t2Name = t2?.name ?: "TBD"
    val t1Abbrev = t1?.abbreviation ?: t1Name.take(3).uppercase()
    val t2Abbrev = t2?.abbreviation ?: t2Name.take(3).uppercase()
    val t1Display = if (t1 != null && t1.seed > 0) "(${t1.seed}) $t1Name" else t1Name
    val t2Display = if (t2 != null && t2.seed > 0) "(${t2.seed}) $t2Name" else t2Name
    val hasTbdTeam = t1 == null || t2 == null ||
        t1.name.equals("TBD", ignoreCase = true) || t2.name.equals("TBD", ignoreCase = true)

    var captureRequested by remember { mutableStateOf(false) }
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }
    var netRatingShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var scatterShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 36.dp, bottom = 96.dp)) {
                        if (t1 != null || t2 != null) {
                            PlayoffMatchupRecordRow(
                                team1 = t1,
                                team2 = t2,
                                seriesStatus = playoffSeriesStatus(matchup, t1, t2),
                                seriesStatusColor = data.conferenceColor
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(data.conferenceColor, RoundedCornerShape(4.dp)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${data.roundName} • ${data.conferenceName}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (hasTbdTeam) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TbdMatchupRow()
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                                Text("vs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TbdMatchupRow()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Matchup will be determined by earlier round results",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        } else {
                            // Top nav: Comparisons | Series | Charts
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                TeamStatsNavBadge("Comparisons", topNavSelection == 0) { topNavSelection = 0 }
                                TeamStatsNavBadge("Series", topNavSelection == 1) { topNavSelection = 1 }
                                TeamStatsNavBadge("Charts", topNavSelection == 2) { topNavSelection = 2 }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            when (topNavSelection) {
                                0 -> {
                                    if (comparisons != null) {
                                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            TeamStatsNavBadge("Team", viewSelection == 0) { viewSelection = 0 }
                                            TeamStatsNavBadge("$t1Abbrev Off vs $t2Abbrev Def", viewSelection == 1) { viewSelection = 1 }
                                            TeamStatsNavBadge("$t2Abbrev Off vs $t1Abbrev Def", viewSelection == 2) { viewSelection = 2 }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        when (viewSelection) {
                                            0 -> BracketTeamStatsView(comparisons, rankColorFn = ::playoffRankColor)
                                            1 -> BracketOffenseVsDefenseView(comparisons.homeOffVsAwayDef, t1Name, t2Name, ::playoffRankColor)
                                            2 -> BracketOffenseVsDefenseView(comparisons.awayOffVsHomeDef, t2Name, t1Name, ::playoffRankColor)
                                        }
                                    } else {
                                        Text("Comparison stats not available", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                    }

                                    // Month trend
                                    val t1Trend = t1?.teamStats?.let { parseNHLMonthTrend(it) }
                                    val t2Trend = t2?.teamStats?.let { parseNHLMonthTrend(it) }
                                    if (t1Trend != null || t2Trend != null) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        SectionHeader("One Month Trend")
                                        Spacer(modifier = Modifier.height(4.dp))
                                        fun trendAdv(a: Int?, b: Int?): Int = if (a != null && b != null) when { a < b -> -1; b < a -> 1; else -> 0 } else 0
                                        val t1Rec = if (t1Trend != null) "${t1Trend.wins}-${t1Trend.losses}" else "-"
                                        val t2Rec = if (t2Trend != null) "${t2Trend.wins}-${t2Trend.losses}" else "-"
                                        FiveColumnRowWithRanks(leftValue = t1Rec, leftRank = t1Trend?.recordRank, leftRankDisplay = t1Trend?.recordRankDisplay,
                                            centerText = "Record", rightValue = t2Rec, rightRank = t2Trend?.recordRank, rightRankDisplay = t2Trend?.recordRankDisplay,
                                            advantage = trendAdv(t1Trend?.recordRank, t2Trend?.recordRank), rankColorFn = ::playoffRankColor)
                                        FiveColumnRowWithRanks(leftValue = t1Trend?.goalsFor?.bracketFormatStat(2) ?: "-",
                                            leftRank = t1Trend?.goalsForRank, leftRankDisplay = t1Trend?.goalsForRankDisplay,
                                            centerText = "Goals/Game", rightValue = t2Trend?.goalsFor?.bracketFormatStat(2) ?: "-",
                                            rightRank = t2Trend?.goalsForRank, rightRankDisplay = t2Trend?.goalsForRankDisplay,
                                            advantage = trendAdv(t1Trend?.goalsForRank, t2Trend?.goalsForRank), rankColorFn = ::playoffRankColor)
                                        FiveColumnRowWithRanks(leftValue = t1Trend?.goalsAgainst?.bracketFormatStat(2) ?: "-",
                                            leftRank = t1Trend?.goalsAgainstRank, leftRankDisplay = t1Trend?.goalsAgainstRankDisplay,
                                            centerText = "Goals Against/G", rightValue = t2Trend?.goalsAgainst?.bracketFormatStat(2) ?: "-",
                                            rightRank = t2Trend?.goalsAgainstRank, rightRankDisplay = t2Trend?.goalsAgainstRankDisplay,
                                            advantage = trendAdv(t1Trend?.goalsAgainstRank, t2Trend?.goalsAgainstRank), rankColorFn = ::playoffRankColor)
                                        val t1Diff = t1Trend?.goalDiff?.bracketFormatStat(2) ?: "-"
                                        val t2Diff = t2Trend?.goalDiff?.bracketFormatStat(2) ?: "-"
                                        FiveColumnRowWithRanks(leftValue = t1Diff,
                                            leftRank = t1Trend?.goalDiffRank, leftRankDisplay = t1Trend?.goalDiffRankDisplay,
                                            centerText = "Goal Diff/G", rightValue = t2Diff,
                                            rightRank = t2Trend?.goalDiffRank, rightRankDisplay = t2Trend?.goalDiffRankDisplay,
                                            advantage = trendAdv(t1Trend?.goalDiffRank, t2Trend?.goalDiffRank), rankColorFn = ::playoffRankColor)
                                    }

                                    // Regular season h2h
                                    matchup.regularSeasonHistory?.let { history ->
                                        Spacer(modifier = Modifier.height(16.dp))
                                        NHLRegularSeasonHistoryView(history)
                                    }
                                }
                                1 -> PlayoffSeriesResultsView(matchup.games, t1Abbrev, t2Abbrev)
                                2 -> {
                                    val t1Stats = t1?.teamStats
                                    val t2Stats = t2?.teamStats
                                    if (t1Stats != null && t2Stats != null) {
                                        CumulativeXgfPctChart(
                                            awayTeam = t1Abbrev, homeTeam = t2Abbrev,
                                            awayStats = t1Stats, homeStats = t2Stats,
                                            tenthXgfPctByWeek = visualization.tenthXgfPctByWeek,
                                            leagueCumXgStats = visualization.leagueCumXgStats,
                                            onShareClick = { cb -> netRatingShareCallback = cb }
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        XgVsPointsPctScatter(
                                            awayTeam = t1Abbrev, homeTeam = t2Abbrev,
                                            awayStats = t1Stats, homeStats = t2Stats,
                                            leagueStats = visualization.leagueXgVsPointsStats,
                                            onShareClick = { cb -> scatterShareCallback = cb }
                                        )
                                    } else {
                                        Text("Chart data not available", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }

                    PinnedMatchupHeader(awayTeam = t1Display, homeTeam = t2Display,
                        awayScore = t1?.score, homeScore = t2?.score,
                        modifier = Modifier.align(Alignment.TopCenter))
                }
            }

            // Share FAB
            if (!hasTbdTeam) {
                when (topNavSelection) {
                    2 -> {
                        val chartOptions = listOfNotNull(
                            netRatingShareCallback?.let { cb -> FabOption(Icons.Filled.TrendingUp, "Cumulative xG%") { cb() } },
                            scatterShareCallback?.let { cb -> FabOption(Icons.Filled.Star, "xG% vs Points%") { cb() } }
                        )
                        if (chartOptions.isNotEmpty()) MultiOptionFab(options = chartOptions, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
                    }
                    0 -> if (comparisons != null) ShareFab(onClick = { captureRequested = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
                    else -> {}
                }
            }

            // Off-screen capture
            if (captureRequested && comparisons != null) {
                val shareTitle = "$t1Abbrev vs $t2Abbrev - ${data.roundName}"
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(50)
                    try { val bmp = graphicsLayer.toImageBitmap(); imageExporter.shareImage(bmp, shareTitle) }
                    catch (e: Exception) { e.printStackTrace() } finally { captureRequested = false }
                }
                CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                    Box(modifier = Modifier.requiredWidth(3400.dp).requiredHeight(1900.dp).offset { IntOffset(-10000, 0) }
                        .drawWithContent { graphicsLayer.record { this@drawWithContent.drawContent() }; drawLayer(graphicsLayer) }) {
                        val comp = comparisons
                        val t1Trend = t1?.teamStats?.let { parseNHLMonthTrend(it) }
                        val t2Trend = t2?.teamStats?.let { parseNHLMonthTrend(it) }
                        val history = matchup.regularSeasonHistory
                        val gameInfo = ShareGameInfo(awayTeam = t1Abbrev, homeTeam = t2Abbrev,
                            eventLabel = "${data.roundName} • ${data.conferenceName}",
                            formattedDate = matchup.seriesSummary ?: "", source = "NHL API / ESPN",
                            awayRecord = t1?.wins?.let { w -> t1.losses?.let { l -> "$w-$l" } },
                            homeRecord = t2?.wins?.let { w -> t2.losses?.let { l -> "$w-$l" } })
                        val statBoxes = buildList {
                            add(ShareStatBox(title = "Offensive Stats", fiveColStats = comp.sideBySide?.offense?.mapNotNull { (_, stat) ->
                                val lv = stat.home.value?.bracketFormatStat(2) ?: return@mapNotNull null
                                val rv = stat.away.value?.bracketFormatStat(2) ?: return@mapNotNull null
                                val adv = if (stat.home.rank != null && stat.away.rank != null) when { stat.home.rank < stat.away.rank -> -1; stat.home.rank > stat.away.rank -> 1; else -> 0 } else 0
                                ShareFiveColStat(lv, stat.home.rank, stat.home.rankDisplay, stat.label, rv, stat.away.rank, stat.away.rankDisplay, adv)
                            }?.take(9) ?: emptyList()))
                            add(ShareStatBox(title = "Defensive Stats", fiveColStats = comp.sideBySide?.defense?.mapNotNull { (_, stat) ->
                                val lv = stat.home.value?.bracketFormatStat(2) ?: return@mapNotNull null
                                val rv = stat.away.value?.bracketFormatStat(2) ?: return@mapNotNull null
                                val adv = if (stat.home.rank != null && stat.away.rank != null) when { stat.home.rank < stat.away.rank -> -1; stat.home.rank > stat.away.rank -> 1; else -> 0 } else 0
                                ShareFiveColStat(lv, stat.home.rank, stat.home.rankDisplay, stat.label, rv, stat.away.rank, stat.away.rankDisplay, adv)
                            }?.take(9) ?: emptyList()))
                            add(ShareStatBox(title = "$t1Abbrev Off vs $t2Abbrev Def", leftLabel = "$t1Abbrev Off", middleLabel = "vs", rightLabel = "$t2Abbrev Def",
                                fiveColStats = comp.homeOffVsAwayDef.mapNotNull { (_, stat) ->
                                    val ov = stat.offense.value?.bracketFormatStat(2) ?: return@mapNotNull null
                                    val dv = stat.defense.value?.bracketFormatStat(2) ?: return@mapNotNull null
                                    ShareFiveColStat(ov, stat.offense.rank, stat.offense.rankDisplay, stat.offLabel, dv, stat.defense.rank, stat.defense.rankDisplay, stat.advantage ?: 0)
                                }.take(9)))
                            add(ShareStatBox(title = "$t2Abbrev Off vs $t1Abbrev Def", leftLabel = "$t2Abbrev Off", middleLabel = "vs", rightLabel = "$t1Abbrev Def",
                                leftColor = Team2Color, rightColor = Team1Color,
                                fiveColStats = comp.awayOffVsHomeDef.mapNotNull { (_, stat) ->
                                    val ov = stat.offense.value?.bracketFormatStat(2) ?: return@mapNotNull null
                                    val dv = stat.defense.value?.bracketFormatStat(2) ?: return@mapNotNull null
                                    ShareFiveColStat(ov, stat.offense.rank, stat.offense.rankDisplay, stat.offLabel, dv, stat.defense.rank, stat.defense.rankDisplay, stat.advantage ?: 0)
                                }.take(9)))
                            // Trend box
                            if (t1Trend != null || t2Trend != null) {
                                fun trendAdv(a: Int?, b: Int?): Int = if (a != null && b != null) when { a < b -> -1; b < a -> 1; else -> 0 } else 0
                                val trendStats = mutableListOf<ShareFiveColStat>()
                                val t1Rec = if (t1Trend != null) "${t1Trend.wins}-${t1Trend.losses}" else "-"
                                val t2Rec = if (t2Trend != null) "${t2Trend.wins}-${t2Trend.losses}" else "-"
                                trendStats.add(ShareFiveColStat(t1Rec, t1Trend?.recordRank, t1Trend?.recordRankDisplay, "Record", t2Rec, t2Trend?.recordRank, t2Trend?.recordRankDisplay, trendAdv(t1Trend?.recordRank, t2Trend?.recordRank)))
                                trendStats.add(ShareFiveColStat(t1Trend?.goalsFor?.bracketFormatStat(2) ?: "-", t1Trend?.goalsForRank, t1Trend?.goalsForRankDisplay, "Goals/Game", t2Trend?.goalsFor?.bracketFormatStat(2) ?: "-", t2Trend?.goalsForRank, t2Trend?.goalsForRankDisplay, trendAdv(t1Trend?.goalsForRank, t2Trend?.goalsForRank)))
                                trendStats.add(ShareFiveColStat(t1Trend?.goalsAgainst?.bracketFormatStat(2) ?: "-", t1Trend?.goalsAgainstRank, t1Trend?.goalsAgainstRankDisplay, "Goals Against/G", t2Trend?.goalsAgainst?.bracketFormatStat(2) ?: "-", t2Trend?.goalsAgainstRank, t2Trend?.goalsAgainstRankDisplay, trendAdv(t1Trend?.goalsAgainstRank, t2Trend?.goalsAgainstRank)))
                                trendStats.add(ShareFiveColStat(t1Trend?.goalDiff?.bracketFormatStat(2) ?: "-", t1Trend?.goalDiffRank, t1Trend?.goalDiffRankDisplay, "Goal Diff/G", t2Trend?.goalDiff?.bracketFormatStat(2) ?: "-", t2Trend?.goalDiffRank, t2Trend?.goalDiffRankDisplay, trendAdv(t1Trend?.goalDiffRank, t2Trend?.goalDiffRank)))
                                add(ShareStatBox(title = "One Month Trend", fiveColStats = trendStats))
                            }
                            // H2H box
                            if (history != null && history.games.isNotEmpty()) {
                                val h2hStats = history.games.mapIndexed { idx, g: RegularSeasonGame ->
                                    val homeS = "${g.homeScore ?: "-"}"
                                    val awayS = "${g.awayScore ?: "-"}"
                                    val label = "Game ${idx + 1} @ ${g.homeAbbrev ?: "?"}"
                                    val adv = when (g.winnerAbbrev) { t1Abbrev -> -1; t2Abbrev -> 1; else -> 0 }
                                    ShareFiveColStat(homeS, null, null, label, awayS, null, null, adv)
                                }.take(9)
                                add(ShareStatBox(title = "Season Series (${history.teamAWins}-${history.teamBWins})", fiveColStats = h2hStats))
                            }
                        }
                        val finalBoxes = statBoxes + List((6 - statBoxes.size).coerceAtLeast(0)) { ShareStatBox(title = "", fiveColStats = emptyList()) }
                        GenericMatchupShareImage(gameInfo = gameInfo, statBoxes = finalBoxes.take(6), modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

// ============================================================================
// Regular season h2h view (shared pattern with NBA)
// ============================================================================

@Composable
private fun NHLRegularSeasonHistoryView(history: RegularSeasonHistory) {
    val teamA = history.teamAAbbrev ?: return
    val teamB = history.teamBAbbrev ?: return
    val aWins = history.teamAWins
    val bWins = history.teamBWins

    SectionHeader("Season Series ($teamA $aWins - $bWins $teamB)")
    Spacer(modifier = Modifier.height(4.dp))

    history.games.forEachIndexed { idx, game ->
        val homeAbbrev = game.homeAbbrev ?: "?"
        val awayAbbrev = game.awayAbbrev ?: "?"
        val advantage = when (game.winnerAbbrev) {
            teamA -> -1; teamB -> 1; else -> 0
        }
        FiveColumnRowWithRanks(
            leftValue = "${game.homeScore ?: "-"}", leftRank = null, leftRankDisplay = null,
            centerText = "Game ${idx + 1}\n@ $homeAbbrev",
            rightValue = "${game.awayScore ?: "-"}", rightRank = null, rightRankDisplay = null,
            advantage = advantage
        )
        game.gameDate?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}
