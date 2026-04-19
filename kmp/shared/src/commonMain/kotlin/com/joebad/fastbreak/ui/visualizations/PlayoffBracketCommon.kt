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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import io.github.koalaplot.core.line.LinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.XYGraphScope
import io.github.koalaplot.core.xygraph.rememberAxisStyle
import io.github.koalaplot.core.gestures.GestureConfig

// ============================================================================
// Shared data types for any sport's playoff bracket
// ============================================================================

internal data class PlayoffBracketTeam(
    val seed: Int,
    val name: String,
    val abbreviation: String,
    val seriesWins: Int? = null,
    val isWinner: Boolean = false
)

internal data class PlayoffBracketGame(
    val team1: PlayoffBracketTeam?,
    val team2: PlayoffBracketTeam?,
    val seriesSummary: String? = null,
    val nextGameDate: String? = null,
    val sourceMatchup: PlayoffMatchupInfo? = null
)

internal data class PlayoffBracketConference(
    val name: String,
    val color: Color,
    val rounds: List<List<PlayoffBracketGame>>
)

internal data class PlayoffMatchupSheetData(
    val matchup: PlayoffMatchupInfo,
    val conferenceName: String,
    val conferenceColor: Color,
    val roundName: String
)

// ============================================================================
// Scalable coordinate system (shared across NBA/NHL)
// ============================================================================

internal class PlayoffBracketPositions(isPortrait: Boolean) {
    val centerX = 6f
    val centerY = 10f

    val armSpacingX = 0.9f
    val r1PairGapY = 1.8f
    val halfSpreadY = if (isPortrait) 2.5f else 3.5f
    val cfGapY = if (isPortrait) 0.8f else 1.5f

    private val eastMidY = centerY + halfSpreadY
    val eastR1X = listOf(centerX - 2 * armSpacingX, centerX - 2 * armSpacingX,
                         centerX + 2 * armSpacingX, centerX + 2 * armSpacingX)
    val eastR1Y = listOf(eastMidY + r1PairGapY / 2, eastMidY - r1PairGapY / 2,
                         eastMidY + r1PairGapY / 2, eastMidY - r1PairGapY / 2)
    val eastSemiX = listOf(centerX - armSpacingX, centerX + armSpacingX)
    val eastSemiY = listOf(eastMidY, eastMidY)
    val eastCFX = centerX
    val eastCFY = centerY + cfGapY

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

    fun allX(includeIdx: Int = 0): List<Float> = eastR1X + westR1X + listOf(finalsX)
    fun allY(): List<Float> = eastR1Y + westR1Y + listOf(eastCFY, westCFY, finalsY)
}

// ============================================================================
// Data conversion (generic, works for any sport using PlayoffMatchupInfo)
// ============================================================================

internal fun convertPlayoffConference(conf: PlayoffConferenceInfo): PlayoffBracketConference {
    val color = parsePlayoffHexColor(conf.colorHex) ?: Color.Gray
    val expectedPerRound = listOf(4, 2, 1)
    val rounds = (0 until 3).map { roundIdx ->
        val roundInfo = conf.rounds.getOrNull(roundIdx)
        val expected = expectedPerRound[roundIdx]
        val games = roundInfo?.games?.map { convertPlayoffMatchupToGame(it) } ?: emptyList()
        if (games.size < expected) games + (games.size until expected).map { PlayoffBracketGame(null, null) }
        else games
    }
    return PlayoffBracketConference(name = conf.name, color = color, rounds = rounds)
}

internal fun convertPlayoffMatchupToGame(matchup: PlayoffMatchupInfo?): PlayoffBracketGame {
    if (matchup == null) return PlayoffBracketGame(null, null)
    val status = matchup.gameStatus?.uppercase() ?: ""
    val isDecided = status == "FINAL"

    fun toTeam(info: BracketTeamInfo?, isWinnerByName: Boolean): PlayoffBracketTeam? {
        if (info == null || info.name.equals("TBD", ignoreCase = true) || info.name.isBlank()) return null
        return PlayoffBracketTeam(
            seed = info.seed, name = info.name,
            abbreviation = info.abbreviation ?: info.name.take(3).uppercase(),
            seriesWins = info.seriesWins,
            isWinner = isDecided && (isWinnerByName || info.isWinner)
        )
    }

    val nextDate = matchup.games.firstOrNull { !it.completed && it.gameDate != null }?.gameDate

    return PlayoffBracketGame(
        team1 = toTeam(matchup.team1, matchup.winner == matchup.team1?.name),
        team2 = toTeam(matchup.team2, matchup.winner == matchup.team2?.name),
        seriesSummary = matchup.seriesSummary,
        nextGameDate = nextDate,
        sourceMatchup = matchup
    )
}

// Compact date for matchup nodes: "Sat, Apr 18 · 7:30"
internal fun formatBracketNodeDate(gameDate: String): String? {
    return try {
        val instant = kotlinx.datetime.Instant.parse(gameDate)
        val eastern = kotlinx.datetime.TimeZone.of("America/New_York")
        val dt = instant.toLocalDateTime(eastern)
        val dow = dt.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val month = dt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val day = dt.dayOfMonth
        if (dt.hour == 0 && dt.minute == 0) {
            "$dow, $month $day"
        } else {
            val hour = if (dt.hour % 12 == 0) 12 else dt.hour % 12
            val minute = dt.minute.toString().padStart(2, '0')
            "$dow, $month $day · $hour:$minute"
        }
    } catch (_: Exception) { null }
}

internal fun parsePlayoffHexColor(hexColor: String): Color? {
    return try {
        val hex = hexColor.removePrefix("#")
        when (hex.length) {
            6 -> Color(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
            else -> null
        }
    } catch (_: Exception) { null }
}

// ============================================================================
// Bracket canvas rendering (shared composable)
// ============================================================================

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
internal fun PlayoffBracketCanvas(
    conferences: List<PlayoffBracketConference>,
    finalsGame: PlayoffBracketGame,
    finalsLabel: String,
    pos: PlayoffBracketPositions,
    onMatchupClick: (PlayoffMatchupInfo, String, String, Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val lineColor = MaterialTheme.colorScheme.onBackground

    val allNodeX = pos.allX()
    val allNodeY = pos.allY()
    val pad = 0.8f
    val xMin = allNodeX.min() - pad
    val xMax = allNodeX.max() + pad
    val yMin = allNodeY.min() - pad
    val yMax = allNodeY.max() + pad

    val xAxisModel = remember(pos) {
        FloatLinearAxisModel(range = xMin..xMax, minViewExtent = xMax - xMin, maxViewExtent = xMax - xMin)
    }
    val yAxisModel = remember(pos) {
        FloatLinearAxisModel(range = yMin..yMax, minViewExtent = yMax - yMin, maxViewExtent = yMax - yMin)
    }

    XYGraph(
        xAxisModel = xAxisModel, yAxisModel = yAxisModel,
        gestureConfig = GestureConfig(panXEnabled = false, panYEnabled = false, zoomXEnabled = false, zoomYEnabled = false),
        xAxisStyle = rememberAxisStyle(color = Color.Transparent, tickPosition = io.github.koalaplot.core.xygraph.TickPosition.None),
        yAxisStyle = rememberAxisStyle(color = Color.Transparent, tickPosition = io.github.koalaplot.core.xygraph.TickPosition.None),
        xAxisLabels = {}, yAxisLabels = {}, xAxisTitle = {}, yAxisTitle = {},
        horizontalMajorGridLineStyle = null, horizontalMinorGridLineStyle = null,
        verticalMajorGridLineStyle = null, verticalMinorGridLineStyle = null,
        modifier = modifier.fillMaxSize().semantics { contentDescription = "chart" }
    ) {
        val noLine = LineStyle(brush = SolidColor(Color.Transparent), strokeWidth = 0.dp)
        val connLine = LineStyle(brush = SolidColor(lineColor), strokeWidth = 0.5.dp)

        // PASS 0: Conference divider line + labels
        val allNodeX = pos.allX()
        val dividerLeft = allNodeX.min() - 0.3f
        val dividerRight = allNodeX.max() + 0.3f
        val dottedLine = LineStyle(
            brush = SolidColor(lineColor),
            strokeWidth = 0.5.dp,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
        )
        LinePlot(
            data = listOf(DefaultPoint(dividerLeft, pos.finalsY), DefaultPoint(dividerRight, pos.finalsY)),
            lineStyle = dottedLine
        )
        // Conference labels at left edge
        val eastConf = conferences.getOrNull(0)
        val westConf = conferences.getOrNull(1)
        val labelOffset = 0.15f
        if (eastConf != null) {
            LinePlot(data = listOf(DefaultPoint(dividerLeft, pos.finalsY + labelOffset)), lineStyle = noLine, symbol = {
                Text(eastConf.name.uppercase(), style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp, color = lineColor, fontWeight = FontWeight.Medium)
            })
        }
        if (westConf != null) {
            LinePlot(data = listOf(DefaultPoint(dividerLeft, pos.finalsY - labelOffset)), lineStyle = noLine, symbol = {
                Text(westConf.name.uppercase(), style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp, color = lineColor, fontWeight = FontWeight.Medium)
            })
        }

        // PASS 1: All connectors
        conferences.forEachIndexed { idx, _ ->
            val r1X = if (idx == 0) pos.eastR1X else pos.westR1X
            val r1Y = if (idx == 0) pos.eastR1Y else pos.westR1Y
            val semiX = if (idx == 0) pos.eastSemiX else pos.westSemiX
            val semiY = if (idx == 0) pos.eastSemiY else pos.westSemiY
            val cfX = if (idx == 0) pos.eastCFX else pos.westCFX
            val cfY = if (idx == 0) pos.eastCFY else pos.westCFY

            DrawPlayoffArmConnectors(r1X[0], r1Y[0], r1X[1], r1Y[1], semiX[0], semiY[0], flowRight = true, lineStyle = connLine)
            DrawPlayoffArmConnectors(r1X[2], r1Y[2], r1X[3], r1Y[3], semiX[1], semiY[1], flowRight = false, lineStyle = connLine)

            val semiEdge = if (semiY[0] > cfY) -0.5f else 0.5f
            LinePlot(data = listOf(DefaultPoint(semiX[0], semiY[0]), DefaultPoint(semiX[0], cfY)), lineStyle = connLine)
            LinePlot(data = listOf(DefaultPoint(semiX[0], cfY), DefaultPoint(cfX, cfY)), lineStyle = connLine)
            LinePlot(data = listOf(DefaultPoint(semiX[1], semiY[1]), DefaultPoint(semiX[1], cfY)), lineStyle = connLine)
            LinePlot(data = listOf(DefaultPoint(semiX[1], cfY), DefaultPoint(cfX, cfY)), lineStyle = connLine)
        }
        LinePlot(data = listOf(DefaultPoint(pos.eastCFX, pos.eastCFY), DefaultPoint(pos.finalsX, pos.finalsY)), lineStyle = connLine)
        LinePlot(data = listOf(DefaultPoint(pos.westCFX, pos.westCFY), DefaultPoint(pos.finalsX, pos.finalsY)), lineStyle = connLine)

        // PASS 2: All matchup nodes
        conferences.forEachIndexed { idx, conf ->
            val r1X = if (idx == 0) pos.eastR1X else pos.westR1X
            val r1Y = if (idx == 0) pos.eastR1Y else pos.westR1Y
            val semiX = if (idx == 0) pos.eastSemiX else pos.westSemiX
            val semiY = if (idx == 0) pos.eastSemiY else pos.westSemiY
            val cfX = if (idx == 0) pos.eastCFX else pos.westCFX
            val cfY = if (idx == 0) pos.eastCFY else pos.westCFY

            val r1Games = conf.rounds.getOrNull(0) ?: emptyList()
            val semiGames = conf.rounds.getOrNull(1) ?: emptyList()
            val cfGames = conf.rounds.getOrNull(2) ?: emptyList()

            r1Games.forEachIndexed { gi, game ->
                if (gi < 4) LinePlot(data = listOf(DefaultPoint(r1X[gi], r1Y[gi])), lineStyle = noLine, symbol = {
                    PlayoffMatchupBoxSymbol(game, conf.color, textColor, backgroundColor) {
                        game.sourceMatchup?.let { onMatchupClick(it, conf.name, "First Round", conf.color) }
                    }
                })
            }
            semiGames.forEachIndexed { gi, game ->
                if (gi < 2) LinePlot(data = listOf(DefaultPoint(semiX[gi], semiY[gi])), lineStyle = noLine, symbol = {
                    PlayoffMatchupBoxSymbol(game, conf.color, textColor, backgroundColor) {
                        game.sourceMatchup?.let { onMatchupClick(it, conf.name, "Conference Semifinals", conf.color) }
                    }
                })
            }
            cfGames.firstOrNull()?.let { game ->
                LinePlot(data = listOf(DefaultPoint(cfX, cfY)), lineStyle = noLine, symbol = {
                    PlayoffMatchupBoxSymbol(game, conf.color, textColor, backgroundColor) {
                        game.sourceMatchup?.let { onMatchupClick(it, conf.name, "Conference Finals", conf.color) }
                    }
                })
            }
        }

        // Finals node
        LinePlot(data = listOf(DefaultPoint(pos.finalsX, pos.finalsY)), lineStyle = noLine, symbol = {
            PlayoffMatchupBoxSymbol(finalsGame, Color(0xFFFFD700), textColor, backgroundColor) {
                finalsGame.sourceMatchup?.let { onMatchupClick(it, "Finals", finalsLabel, Color(0xFFFFD700)) }
            }
        })
    }
}

// ============================================================================
// Connector drawing
// ============================================================================

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
internal fun XYGraphScope<Float, Float>.DrawPlayoffArmConnectors(
    topGameX: Float, topGameY: Float,
    botGameX: Float, botGameY: Float,
    targetX: Float, targetY: Float,
    flowRight: Boolean,
    lineStyle: LineStyle
) {
    val t = 0.3f
    val barX = topGameX + t * (targetX - topGameX)
    LinePlot(data = listOf(DefaultPoint(topGameX, topGameY), DefaultPoint(barX, topGameY)), lineStyle = lineStyle)
    LinePlot(data = listOf(DefaultPoint(botGameX, botGameY), DefaultPoint(barX, botGameY)), lineStyle = lineStyle)
    LinePlot(data = listOf(DefaultPoint(barX, topGameY), DefaultPoint(barX, botGameY)), lineStyle = lineStyle)
    LinePlot(data = listOf(DefaultPoint(barX, targetY), DefaultPoint(targetX, targetY)), lineStyle = lineStyle)
}

// ============================================================================
// Matchup box rendering
// ============================================================================

@Composable
internal fun PlayoffMatchupBoxSymbol(
    game: PlayoffBracketGame,
    accentColor: Color,
    textColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.width(110.dp).semantics { contentDescription = "matchup-node" }.clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onBackground)
    ) {
        Column {
            game.nextGameDate?.let { dateStr ->
                formatBracketNodeDate(dateStr)?.let { formatted ->
                    Text(formatted, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onBackground, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp),
                        textAlign = TextAlign.Center)
                }
            }

            game.team1?.let { PlayoffTeamRow(it, accentColor, textColor) } ?: PlayoffEmptyRow()
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
            game.team2?.let { PlayoffTeamRow(it, accentColor, textColor) } ?: PlayoffEmptyRow()
        }
    }
}

@Composable
internal fun PlayoffTeamRow(team: PlayoffBracketTeam, accentColor: Color, textColor: Color) {
    val rowBg = if (team.isWinner) accentColor.copy(alpha = 0.15f) else Color.Transparent
    Row(
        modifier = Modifier.fillMaxWidth().background(rowBg).padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text("${team.seed}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = if (team.isWinner) accentColor else textColor.copy(alpha = 0.6f), modifier = Modifier.width(16.dp))
            Text(team.abbreviation, style = MaterialTheme.typography.labelSmall,
                fontWeight = if (team.isWinner) FontWeight.Bold else FontWeight.Normal, color = textColor, maxLines = 1)
        }
        val wins = team.seriesWins ?: 0
        Text("$wins", style = MaterialTheme.typography.labelSmall,
            fontWeight = if (team.isWinner) FontWeight.Bold else FontWeight.Normal,
            color = if (team.isWinner) accentColor else textColor)
    }
}

@Composable
internal fun PlayoffEmptyRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.width(16.dp))
        Text("TBD", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

// ============================================================================
// Series results view (shared across sports)
// ============================================================================

@Composable
internal fun PlayoffSeriesResultsView(
    games: List<PlayoffSeriesGameInfo>,
    t1Abbrev: String,
    t2Abbrev: String,
    bestOf: Int = 7
) {
    SectionHeader("Best of $bestOf")
    Spacer(modifier = Modifier.height(4.dp))

    FiveColumnRowWithRanks(
        leftValue = t1Abbrev, leftRank = null, leftRankDisplay = null,
        centerText = "", rightValue = t2Abbrev, rightRank = null, rightRankDisplay = null, advantage = 0
    )

    for (idx in 0 until bestOf) {
        val game = games.getOrNull(idx)
        if (game != null) {
            val t1Won = game.team1?.winner == true
            val t2Won = game.team2?.winner == true
            val isCompleted = game.completed
            val dateLabel = game.gameDate?.let { formatBracketGameDate(it) }
            val homeLabel = game.homeTeamAbbrev?.let { "@ $it" } ?: ""
            val advantage = when { t1Won -> -1; t2Won -> 1; else -> 0 }
            val centerText = "Game ${idx + 1}" + if (homeLabel.isNotEmpty()) "\n$homeLabel" else ""

            FiveColumnRowWithRanks(
                leftValue = if (isCompleted) "${game.team1?.score ?: "-"}" else "-",
                leftRank = null, leftRankDisplay = null,
                centerText = centerText,
                rightValue = if (isCompleted) "${game.team2?.score ?: "-"}" else "-",
                rightRank = null, rightRankDisplay = null,
                advantage = advantage
            )
            val oddsLabel = if (!isCompleted) game.odds?.let { formatPlayoffGameOddsLine(it) } else null
            val scheduleLine = listOfNotNull(dateLabel, oddsLabel).joinToString(" · ")
            if (scheduleLine.isNotEmpty()) {
                val color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                Text(scheduleLine, style = MaterialTheme.typography.labelSmall, color = color,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 10.sp)
            }
        } else {
            FiveColumnRowWithRanks(
                leftValue = "-", leftRank = null, leftRankDisplay = null,
                centerText = "Game ${idx + 1}",
                rightValue = "-", rightRank = null, rightRankDisplay = null, advantage = 0
            )
        }
    }
}

/**
 * Compact spread + total label for an upcoming playoff game,
 * e.g. "BOS -4.5 · O/U 213.5". Returns null if there's no usable data.
 */
internal fun formatPlayoffGameOddsLine(odds: PlayoffSeriesGameOdds): String? {
    val parts = mutableListOf<String>()
    odds.details?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    odds.overUnder?.let { ou ->
        val rendered = if (ou == ou.toInt().toDouble()) ou.toInt().toString() else ou.toString()
        parts.add("O/U $rendered")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

// ============================================================================
// Series status + matchup record row (shared across NBA/NHL)
// ============================================================================

/**
 * Returns a human-readable series-state line for a playoff matchup.
 * Examples: "Series tied, 0-0", "BOS leads series, 2-1", "BOS wins series, 4-2".
 */
internal fun playoffSeriesStatus(
    matchup: PlayoffMatchupInfo,
    t1: BracketTeamInfo?,
    t2: BracketTeamInfo?
): String {
    val t1Wins = t1?.seriesWins ?: 0
    val t2Wins = t2?.seriesWins ?: 0
    val bestOf = matchup.bestOf ?: 7
    val needed = bestOf / 2 + 1
    val t1Abbrev = t1?.abbreviation ?: t1?.name?.take(3)?.uppercase() ?: "T1"
    val t2Abbrev = t2?.abbreviation ?: t2?.name?.take(3)?.uppercase() ?: "T2"

    return when {
        t1Wins >= needed -> "$t1Abbrev wins series, $t1Wins-$t2Wins"
        t2Wins >= needed -> "$t2Abbrev wins series, $t2Wins-$t1Wins"
        t1Wins == t2Wins -> "Series tied, $t1Wins-$t2Wins"
        t1Wins > t2Wins -> "$t1Abbrev leads series, $t1Wins-$t2Wins"
        else -> "$t2Abbrev leads series, $t2Wins-$t1Wins"
    }
}

/**
 * Row rendered at the top of a playoff matchup bottom sheet:
 * team1 record on the left, series status in the middle, team2 record on the right.
 */
@Composable
internal fun PlayoffMatchupRecordRow(
    team1: BracketTeamInfo?,
    team2: BracketTeamInfo?,
    seriesStatus: String,
    seriesStatusColor: Color
) {
    val textStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 14.sp)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (team1?.wins != null && team1.losses != null) {
                Text(
                    text = "${team1.wins}-${team1.losses}",
                    style = textStyle,
                    fontSize = 11.sp,
                    color = textColor
                )
            }
        }

        Box(modifier = Modifier.weight(2f), contentAlignment = Alignment.Center) {
            Text(
                text = seriesStatus,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = seriesStatusColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (team2?.wins != null && team2.losses != null) {
                Text(
                    text = "${team2.wins}-${team2.losses}",
                    style = textStyle,
                    fontSize = 11.sp,
                    color = textColor,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// ============================================================================
// Rank color function (30-team scale, shared across NBA/NHL)
// ============================================================================

internal fun playoffRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    return when {
        rank <= 5 -> {
            val ratio = (rank - 1) / 4f
            Color(red = (0 + ratio * 80).toInt(), green = (150 - ratio * 25).toInt(), blue = (42 - ratio * 32).toInt())
        }
        rank <= 15 -> {
            val ratio = (rank - 6) / 9f
            Color(red = (255 - ratio * 55).toInt(), green = (160 - ratio * 60).toInt(), blue = 0)
        }
        else -> {
            val ratio = ((rank - 16).coerceAtMost(14)) / 14f
            Color(red = (200 - ratio * 61).toInt(), green = (50 - ratio * 50).toInt(), blue = 0)
        }
    }
}
