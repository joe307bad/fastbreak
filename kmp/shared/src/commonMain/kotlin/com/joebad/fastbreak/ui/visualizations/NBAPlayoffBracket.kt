package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*
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
import kotlin.math.round

// ============================================================================
// Local data types (mirroring NCAABracket's BracketTeam / BracketGame)
// ============================================================================

private data class NBABracketTeam(
    val seed: Int,
    val name: String,
    val seriesWins: Int? = null,
    val isWinner: Boolean = false
)

private data class NBABracketGame(
    val team1: NBABracketTeam?,
    val team2: NBABracketTeam?,
    val seriesSummary: String? = null,
    val sourceMatchup: PlayoffMatchupInfo? = null
)

private data class NBABracketConference(
    val name: String,
    val color: Color,
    val rounds: List<List<NBABracketGame>>  // 3 rounds: First Round (4), Semis (2), Finals (1)
)

private data class NBAMatchupSheetData(
    val matchup: PlayoffMatchupInfo,
    val conferenceName: String,
    val conferenceColor: Color,
    val roundName: String
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

private class BracketPositions(isPortrait: Boolean) {
    val centerX = 6f
    val centerY = 10f

    // Tuning knobs
    val armSpacingX = 0.9f     // Horizontal gap between rounds within an arm
    val r1PairGapY = 1.8f      // Vertical gap between two R1 games in same quadrant
    val halfSpreadY = if (isPortrait) 2.5f else 3.5f  // Conference spread from center
    val cfGapY = if (isPortrait) 0.8f else 1.5f       // CF-to-Finals gap (wider in landscape)

    // Per-conference: 4 R1 positions, 2 Semi positions, 1 CF position
    // R1 games: [0]=1v8(left-top), [1]=4v5(left-bot), [2]=3v6(right-top), [3]=2v7(right-bot)

    // East conference (top half, Y > centerY)
    private val eastMidY = centerY + halfSpreadY
    val eastR1X = listOf(centerX - 2 * armSpacingX, centerX - 2 * armSpacingX,
                         centerX + 2 * armSpacingX, centerX + 2 * armSpacingX)
    val eastR1Y = listOf(eastMidY + r1PairGapY / 2, eastMidY - r1PairGapY / 2,
                         eastMidY + r1PairGapY / 2, eastMidY - r1PairGapY / 2)
    val eastSemiX = listOf(centerX - armSpacingX, centerX + armSpacingX)
    val eastSemiY = listOf(eastMidY, eastMidY)
    val eastCFX = centerX
    val eastCFY = centerY + cfGapY

    // West conference (bottom half, Y < centerY) — mirrored vertically
    private val westMidY = centerY - halfSpreadY
    val westR1X = listOf(centerX - 2 * armSpacingX, centerX - 2 * armSpacingX,
                         centerX + 2 * armSpacingX, centerX + 2 * armSpacingX)
    val westR1Y = listOf(westMidY - r1PairGapY / 2, westMidY + r1PairGapY / 2,
                         westMidY - r1PairGapY / 2, westMidY + r1PairGapY / 2)
    val westSemiX = listOf(centerX - armSpacingX, centerX + armSpacingX)
    val westSemiY = listOf(westMidY, westMidY)
    val westCFX = centerX
    val westCFY = centerY - cfGapY

    val finalsX = centerX
    val finalsY = centerY
}

private const val BOX_OFFSET = 0.5f


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
    val conferences = remember(visualization) {
        visualization.conferences.map { conf ->
            convertConference(conf)
        }
    }

    val finalsGame = remember(visualization) {
        convertMatchupToGame(visualization.finals)
    }

    var selectedMatchup by remember { mutableStateOf<NBAMatchupSheetData?>(null) }

    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val lineColor = MaterialTheme.colorScheme.onBackground

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isPortrait = maxWidth < maxHeight
        val pos = remember(isPortrait) { BracketPositions(isPortrait) }

        // Canvas bounds from all node positions
        val allNodeX = pos.eastR1X + pos.westR1X + listOf(pos.finalsX)
        val allNodeY = pos.eastR1Y + pos.westR1Y + listOf(pos.eastCFY, pos.westCFY, pos.finalsY)
        val pad = 0.8f
        val xMin = allNodeX.min() - pad
        val xMax = allNodeX.max() + pad
        val yMin = allNodeY.min() - pad
        val yMax = allNodeY.max() + pad

        val xAxisModel = remember(isPortrait) {
            FloatLinearAxisModel(range = xMin..xMax, minViewExtent = xMax - xMin, maxViewExtent = xMax - xMin)
        }
        val yAxisModel = remember(isPortrait) {
            FloatLinearAxisModel(range = yMin..yMax, minViewExtent = yMax - yMin, maxViewExtent = yMax - yMin)
        }
        Box(modifier = Modifier.fillMaxSize()) {
            XYGraph(
                xAxisModel = xAxisModel,
                yAxisModel = yAxisModel,
                gestureConfig = GestureConfig(
                    panXEnabled = false, panYEnabled = false,
                    zoomXEnabled = false, zoomYEnabled = false
                ),
                xAxisStyle = rememberAxisStyle(
                    color = Color.Transparent,
                    tickPosition = io.github.koalaplot.core.xygraph.TickPosition.None
                ),
                yAxisStyle = rememberAxisStyle(
                    color = Color.Transparent,
                    tickPosition = io.github.koalaplot.core.xygraph.TickPosition.None
                ),
                xAxisLabels = {}, yAxisLabels = {},
                xAxisTitle = {}, yAxisTitle = {},
                horizontalMajorGridLineStyle = null,
                horizontalMinorGridLineStyle = null,
                verticalMajorGridLineStyle = null,
                verticalMinorGridLineStyle = null,
                modifier = Modifier.fillMaxSize().semantics { contentDescription = "chart" }
            ) {
                val noLine = LineStyle(brush = SolidColor(Color.Transparent), strokeWidth = 0.dp)
                val connLine = LineStyle(brush = SolidColor(lineColor), strokeWidth = 1.5.dp)

                // East conference (index 0), West conference (index 1)
                conferences.forEachIndexed { idx, conf ->
                    val r1X = if (idx == 0) pos.eastR1X else pos.westR1X
                    val r1Y = if (idx == 0) pos.eastR1Y else pos.westR1Y
                    val semiX = if (idx == 0) pos.eastSemiX else pos.westSemiX
                    val semiY = if (idx == 0) pos.eastSemiY else pos.westSemiY
                    val cfX = if (idx == 0) pos.eastCFX else pos.westCFX
                    val cfY = if (idx == 0) pos.eastCFY else pos.westCFY

                    // CONNECTORS: Left arm (R1 games 0,1 → Semi 0)
                    DrawArmConnectors(
                        topGameX = r1X[0], topGameY = r1Y[0],
                        botGameX = r1X[1], botGameY = r1Y[1],
                        targetX = semiX[0], targetY = semiY[0],
                        flowRight = true, lineStyle = connLine
                    )
                    // Right arm (R1 games 2,3 → Semi 1) — flows left
                    DrawArmConnectors(
                        topGameX = r1X[2], topGameY = r1Y[2],
                        botGameX = r1X[3], botGameY = r1Y[3],
                        targetX = semiX[1], targetY = semiY[1],
                        flowRight = false, lineStyle = connLine
                    )
                    // Semi 0 → CF: vertical toward center, then horizontal
                    LinePlot(data = listOf(
                        DefaultPoint(semiX[0], semiY[0]),
                        DefaultPoint(semiX[0], cfY)
                    ), lineStyle = connLine)
                    LinePlot(data = listOf(
                        DefaultPoint(semiX[0], cfY),
                        DefaultPoint(cfX, cfY)
                    ), lineStyle = connLine)
                    // Semi 1 → CF: vertical toward center, then horizontal
                    LinePlot(data = listOf(
                        DefaultPoint(semiX[1], semiY[1]),
                        DefaultPoint(semiX[1], cfY)
                    ), lineStyle = connLine)
                    LinePlot(data = listOf(
                        DefaultPoint(semiX[1], cfY),
                        DefaultPoint(cfX, cfY)
                    ), lineStyle = connLine)

                    // MATCHUP BOXES
                    val r1Games = conf.rounds.getOrNull(0) ?: emptyList()
                    val semiGames = conf.rounds.getOrNull(1) ?: emptyList()
                    val cfGames = conf.rounds.getOrNull(2) ?: emptyList()

                    // R1: 4 games
                    r1Games.forEachIndexed { gi, game ->
                        if (gi < 4) {
                            LinePlot(data = listOf(DefaultPoint(r1X[gi], r1Y[gi])),
                                lineStyle = noLine, symbol = {
                                    NBAMatchupBoxSymbol(game, conf.color, textColor, backgroundColor,
                                        onClick = { game.sourceMatchup?.let { selectedMatchup = NBAMatchupSheetData(it, conf.name, conf.color, "First Round") } })
                                })
                        }
                    }
                    // Semis: 2 games
                    semiGames.forEachIndexed { gi, game ->
                        if (gi < 2) {
                            LinePlot(data = listOf(DefaultPoint(semiX[gi], semiY[gi])),
                                lineStyle = noLine, symbol = {
                                    NBAMatchupBoxSymbol(game, conf.color, textColor, backgroundColor,
                                        onClick = { game.sourceMatchup?.let { selectedMatchup = NBAMatchupSheetData(it, conf.name, conf.color, "Conference Semifinals") } })
                                })
                        }
                    }
                    // CF: 1 game
                    cfGames.firstOrNull()?.let { game ->
                        LinePlot(data = listOf(DefaultPoint(cfX, cfY)),
                            lineStyle = noLine, symbol = {
                                NBAMatchupBoxSymbol(game, conf.color, textColor, backgroundColor,
                                    onClick = { game.sourceMatchup?.let { selectedMatchup = NBAMatchupSheetData(it, conf.name, conf.color, "Conference Finals") } })
                            })
                    }
                }

                // CF → Finals connectors
                LinePlot(data = listOf(
                    DefaultPoint(pos.eastCFX, pos.eastCFY),
                    DefaultPoint(pos.finalsX, pos.finalsY)
                ), lineStyle = connLine)
                LinePlot(data = listOf(
                    DefaultPoint(pos.westCFX, pos.westCFY),
                    DefaultPoint(pos.finalsX, pos.finalsY)
                ), lineStyle = connLine)

                // Finals matchup box
                LinePlot(
                    data = listOf(DefaultPoint(pos.finalsX, pos.finalsY)),
                    lineStyle = noLine,
                    symbol = {
                        NBAMatchupBoxSymbol(
                            game = finalsGame,
                            accentColor = Color(0xFFFFD700),
                            textColor = textColor,
                            backgroundColor = backgroundColor,
                            onClick = {
                                visualization.finals?.let { mu ->
                                    selectedMatchup = NBAMatchupSheetData(mu, "Finals", Color(0xFFFFD700), "NBA Finals")
                                }
                            }
                        )
                    }
                )
            }
        }
    }

    // Bottom sheet
    selectedMatchup?.let { data ->
        NBAPlayoffMatchupBottomSheet(data = data, onDismiss = { selectedMatchup = null })
    }
}

// ============================================================================
// Connector drawing: two games converge into one target
// ============================================================================

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun XYGraphScope<Float, Float>.DrawArmConnectors(
    topGameX: Float, topGameY: Float,
    botGameX: Float, botGameY: Float,
    targetX: Float, targetY: Float,
    flowRight: Boolean,
    lineStyle: LineStyle
) {
    // Vertical bar sits 30% of the way from games toward target
    val t = 0.3f
    val barX = topGameX + t * (targetX - topGameX)

    // Horizontal from each game to the vertical bar
    LinePlot(data = listOf(DefaultPoint(topGameX, topGameY), DefaultPoint(barX, topGameY)), lineStyle = lineStyle)
    LinePlot(data = listOf(DefaultPoint(botGameX, botGameY), DefaultPoint(barX, botGameY)), lineStyle = lineStyle)
    // Vertical bar
    LinePlot(data = listOf(DefaultPoint(barX, topGameY), DefaultPoint(barX, botGameY)), lineStyle = lineStyle)
    // Horizontal from bar to target
    LinePlot(data = listOf(DefaultPoint(barX, targetY), DefaultPoint(targetX, targetY)), lineStyle = lineStyle)
}

@Composable
private fun NBAMatchupBoxSymbol(
    game: NBABracketGame,
    accentColor: Color,
    textColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()

    Card(
        modifier = Modifier
            .width(110.dp)
            .semantics { contentDescription = "matchup-node" }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = if (isDarkTheme) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column {
            // Series summary header
            game.seriesSummary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp,
                    color = accentColor,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            game.team1?.let { team ->
                NBATeamRow(team = team, accentColor = accentColor, textColor = textColor)
            } ?: NBAEmptyRow()

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)

            game.team2?.let { team ->
                NBATeamRow(team = team, accentColor = accentColor, textColor = textColor)
            } ?: NBAEmptyRow()
        }
    }
}

@Composable
private fun NBATeamRow(team: NBABracketTeam, accentColor: Color, textColor: Color) {
    val rowBg = if (team.isWinner) accentColor.copy(alpha = 0.15f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = "${team.seed}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (team.isWinner) accentColor else textColor.copy(alpha = 0.6f),
                modifier = Modifier.width(16.dp)
            )
            Text(
                text = team.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (team.isWinner) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        team.seriesWins?.let { wins ->
            Text(
                text = "$wins",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (team.isWinner) FontWeight.Bold else FontWeight.Normal,
                color = if (team.isWinner) accentColor else textColor
            )
        }
    }
}

@Composable
private fun NBAEmptyRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.width(16.dp))
        Text("TBD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

// ============================================================================
// Data conversion
// ============================================================================

private fun convertConference(conf: PlayoffConferenceInfo): NBABracketConference {
    val color = parseNBAHexColor(conf.colorHex) ?: Color.Gray

    val expectedPerRound = listOf(4, 2, 1)
    val rounds = (0 until 3).map { roundIdx ->
        val roundInfo = conf.rounds.getOrNull(roundIdx)
        val expected = expectedPerRound[roundIdx]
        val games = roundInfo?.games?.map { convertMatchupToGame(it) } ?: emptyList()

        if (games.size < expected) {
            games + (games.size until expected).map { NBABracketGame(null, null) }
        } else {
            games
        }
    }

    return NBABracketConference(name = conf.name, color = color, rounds = rounds)
}

private fun convertMatchupToGame(matchup: PlayoffMatchupInfo?): NBABracketGame {
    if (matchup == null) return NBABracketGame(null, null)

    val status = matchup.gameStatus?.uppercase() ?: ""
    val isDecided = status == "FINAL"

    fun toTeam(info: BracketTeamInfo?, isWinnerByName: Boolean): NBABracketTeam? {
        if (info == null || info.name.equals("TBD", ignoreCase = true) || info.name.isBlank()) return null
        // Extract seriesWins from teamStats JSON if available, or check the info field
        return NBABracketTeam(
            seed = info.seed,
            name = info.name,
            seriesWins = info.score,  // R script stores seriesWins in the score field
            isWinner = isDecided && (isWinnerByName || info.isWinner)
        )
    }

    val t1Winner = matchup.winner == matchup.team1?.name
    val t2Winner = matchup.winner == matchup.team2?.name

    return NBABracketGame(
        team1 = toTeam(matchup.team1, t1Winner),
        team2 = toTeam(matchup.team2, t2Winner),
        seriesSummary = matchup.seriesSummary,
        sourceMatchup = matchup
    )
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
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
                        .padding(top = 36.dp)
                ) {
                    // Record section (reused from NCAA bracket)
                    if (t1 != null || t2 != null) {
                        BracketRecordSection(team1 = t1, team2 = t2)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Round and conference info
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(8.dp)
                            .background(data.conferenceColor, RoundedCornerShape(4.dp)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${data.roundName} • ${data.conferenceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Series summary
                    matchup.seriesSummary?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            textAlign = TextAlign.Center)
                    }

                    // Game scores row
                    if (matchup.games.isNotEmpty()) {
                        NBAGameScoresRow(matchup.games)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Stat comparisons - same pattern as NCAA bracket
                    if (comparisons != null && !hasTbdTeam) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TeamStatsNavBadge(
                                text = "Team",
                                isSelected = viewSelection == 0,
                                onClick = { viewSelection = 0 }
                            )
                            TeamStatsNavBadge(
                                text = "$t1Abbrev Off vs $t2Abbrev Def",
                                isSelected = viewSelection == 1,
                                onClick = { viewSelection = 1 }
                            )
                            TeamStatsNavBadge(
                                text = "$t2Abbrev Off vs $t1Abbrev Def",
                                isSelected = viewSelection == 2,
                                onClick = { viewSelection = 2 }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (viewSelection) {
                            0 -> BracketTeamStatsView(comparisons)
                            1 -> BracketOffenseVsDefenseView(
                                comparisons = comparisons.homeOffVsAwayDef,
                                offTeam = t1Name,
                                defTeam = t2Name
                            )
                            2 -> BracketOffenseVsDefenseView(
                                comparisons = comparisons.awayOffVsHomeDef,
                                offTeam = t2Name,
                                defTeam = t1Name
                            )
                        }
                    } else if (hasTbdTeam) {
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TeamStatsNavBadge(text = "Team", isSelected = true, onClick = {})
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Detailed comparison stats will be available when game data is loaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
