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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.components.FabOption
import com.joebad.fastbreak.ui.components.MultiOptionFab
import com.joebad.fastbreak.ui.components.ShareFab
import io.github.koalaplot.core.gestures.GestureConfig
import io.github.koalaplot.core.line.LinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.rememberAxisStyle

// ============================================================================
// MLB postseason bracket.
//
// Structurally this is the NBA bracket with one difference that runs through
// everything: the top two seeds in each league get a bye, so a league opens
// with two Wild Card series instead of four first-round series, and every round
// has its own length (Wild Card best-of-3, Division best-of-5, LCS and World
// Series best-of-7).
//
//   AL       WC 4v5 ─┐                 ┌─ WC 3v6
//                    └─ DS (1 seed) ─┐ │
//                                    ├─┴ ALCS
//                                    │
//                              World Series
//                                    │
//                                    ├─┬ NLCS
//                    ┌─ DS (1 seed) ─┘ │
//   NL       WC 4v5 ─┘                 └─ WC 3v6
// ============================================================================

/** Matchups per round, used to pad short rounds into bracket slots. */
private val MLB_ROUND_SIZES = listOf(2, 2, 1)

private data class MLBMatchupSheetData(
    val matchup: PlayoffMatchupInfo,
    val leagueName: String,
    val leagueColor: Color,
    val roundName: String,
    val visualization: MLBPlayoffBracketVisualization? = null
)

/**
 * Coordinate system for the MLB bracket. Bounds match [PlayoffBracketPositions]
 * so matchup nodes land at the same on-screen scale as the NBA and NHL
 * brackets; only the node count per arm differs.
 */
private class MLBBracketPositions(isPortrait: Boolean) {
    val centerX = 6f
    val centerY = 10f

    private val armSpacingX = 0.9f
    private val wcOffsetY = 0.9f
    private val halfSpreadY = if (isPortrait) 2.5f else 3.5f
    private val lcsGapY = if (isPortrait) 0.8f else 1.5f

    // American League occupies the top half, National League the bottom.
    private val alMidY = centerY + halfSpreadY
    val alWcX = listOf(centerX - 2 * armSpacingX, centerX + 2 * armSpacingX)
    val alWcY = listOf(alMidY + wcOffsetY, alMidY + wcOffsetY)
    val alDsX = listOf(centerX - armSpacingX, centerX + armSpacingX)
    val alDsY = listOf(alMidY, alMidY)
    val alLcsX = centerX
    val alLcsY = centerY + lcsGapY

    private val nlMidY = centerY - halfSpreadY
    val nlWcX = listOf(centerX - 2 * armSpacingX, centerX + 2 * armSpacingX)
    val nlWcY = listOf(nlMidY - wcOffsetY, nlMidY - wcOffsetY)
    val nlDsX = listOf(centerX - armSpacingX, centerX + armSpacingX)
    val nlDsY = listOf(nlMidY, nlMidY)
    val nlLcsX = centerX
    val nlLcsY = centerY - lcsGapY

    val finalsX = centerX
    val finalsY = centerY

    fun allX(): List<Float> = alWcX + nlWcX + listOf(finalsX)
    fun allY(): List<Float> = alWcY + nlWcY + listOf(alLcsY, nlLcsY, finalsY)
}

// ============================================================================
// Main composable
// ============================================================================

@Composable
fun MLBPlayoffBracket(
    visualization: MLBPlayoffBracketVisualization,
    modifier: Modifier = Modifier,
    onNavigationToggleHandlerChanged: ((BracketNavigationToggleHandler?) -> Unit)? = null
) {
    val leagues = remember(visualization) {
        visualization.conferences.map { convertPlayoffConference(it, MLB_ROUND_SIZES) }
    }
    val worldSeries = remember(visualization) { convertPlayoffMatchupToGame(visualization.finals) }
    var selectedMatchup by remember { mutableStateOf<MLBMatchupSheetData?>(null) }
    var bracketCaptureRequested by remember { mutableStateOf(false) }

    val bracketLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isPortrait = maxWidth < maxHeight
        val pos = remember(isPortrait) { MLBBracketPositions(isPortrait) }

        Box(modifier = Modifier.fillMaxSize()) {
            MLBBracketCanvas(
                leagues = leagues,
                worldSeriesGame = worldSeries,
                pos = pos,
                onMatchupClick = { mu, leagueName, roundName, color ->
                    selectedMatchup = MLBMatchupSheetData(mu, leagueName, color, roundName, visualization)
                }
            )

            ShareFab(
                onClick = { bracketCaptureRequested = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }

    // Off-screen capture of the whole bracket. The bracket is laid out at a
    // fixed portrait size rather than reusing the on-screen one so the shared
    // image is identical regardless of the device it was shared from.
    if (bracketCaptureRequested) {
        val sharePos = remember { MLBBracketPositions(isPortrait = true) }

        LaunchedEffect(Unit) {
            // The off-screen bracket needs to compose, lay out and draw before
            // the layer has anything in it: one frame to draw, one for the
            // graph to settle, plus a beat for the first composition.
            kotlinx.coroutines.delay(50)
            withFrameNanos { }
            withFrameNanos { }
            try {
                val bitmap = bracketLayer.toImageBitmap()
                imageExporter.shareImage(bitmap, visualization.title)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bracketCaptureRequested = false
            }
        }

        CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
            Box(
                modifier = Modifier
                    .requiredWidth(760.dp)
                    .requiredHeight(1180.dp)
                    .offset { IntOffset(-10000, 0) }
                    .drawWithContent {
                        bracketLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(bracketLayer)
                    }
            ) {
                MLBBracketShareImage(
                    title = visualization.title,
                    subtitle = visualization.subtitle,
                    source = visualization.source,
                    leagues = leagues,
                    worldSeriesGame = worldSeries,
                    pos = sharePos
                )
            }
        }
    }

    selectedMatchup?.let { data ->
        MLBPlayoffMatchupBottomSheet(data = data, onDismiss = { selectedMatchup = null })
    }
}

// ============================================================================
// Bracket canvas
// ============================================================================

/**
 * Picks which arm a Division Series belongs on. The generator already emits the
 * two Division Series in bracket-slot order, but a league's bye seed never
 * appears in a Wild Card series, so this matches on *any* shared team rather
 * than all of them (which is what the NBA's semifinal placement can assume).
 */
private fun mlbDivisionArmIndex(
    dsGame: PlayoffBracketGame,
    wcGames: List<PlayoffBracketGame>,
    fallbackIndex: Int
): Int {
    val dsTeams = listOfNotNull(dsGame.team1?.abbreviation, dsGame.team2?.abbreviation).toSet()
    if (dsTeams.isEmpty()) return fallbackIndex

    fun armTeams(idx: Int): Set<String> {
        val g = wcGames.getOrNull(idx) ?: return emptySet()
        return listOfNotNull(g.team1?.abbreviation, g.team2?.abbreviation).toSet()
    }

    val inLeft = dsTeams.any { it in armTeams(0) }
    val inRight = dsTeams.any { it in armTeams(1) }
    return when {
        inLeft && !inRight -> 0
        inRight && !inLeft -> 1
        else -> fallbackIndex
    }
}

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun MLBBracketCanvas(
    leagues: List<PlayoffBracketConference>,
    worldSeriesGame: PlayoffBracketGame,
    pos: MLBBracketPositions,
    modifier: Modifier = Modifier,
    onMatchupClick: ((PlayoffMatchupInfo, String, String, Color) -> Unit)? = null
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val lineColor = MaterialTheme.colorScheme.onBackground

    val pad = 0.8f
    val xMin = pos.allX().min() - pad
    val xMax = pos.allX().max() + pad
    val yMin = pos.allY().min() - pad
    val yMax = pos.allY().max() + pad

    val xAxisModel = remember(pos) {
        FloatLinearAxisModel(range = xMin..xMax, minViewExtent = xMax - xMin, maxViewExtent = xMax - xMin)
    }
    val yAxisModel = remember(pos) {
        FloatLinearAxisModel(range = yMin..yMax, minViewExtent = yMax - yMin, maxViewExtent = yMax - yMin)
    }

    XYGraph(
        xAxisModel = xAxisModel, yAxisModel = yAxisModel,
        gestureConfig = GestureConfig(panXEnabled = false, panYEnabled = false,
            zoomXEnabled = false, zoomYEnabled = false),
        xAxisStyle = rememberAxisStyle(color = Color.Transparent,
            tickPosition = io.github.koalaplot.core.xygraph.TickPosition.None),
        yAxisStyle = rememberAxisStyle(color = Color.Transparent,
            tickPosition = io.github.koalaplot.core.xygraph.TickPosition.None),
        xAxisLabels = {}, yAxisLabels = {}, xAxisTitle = {}, yAxisTitle = {},
        horizontalMajorGridLineStyle = null, horizontalMinorGridLineStyle = null,
        verticalMajorGridLineStyle = null, verticalMinorGridLineStyle = null,
        modifier = modifier.fillMaxSize().semantics { contentDescription = "chart" }
    ) {
        val noLine = LineStyle(brush = SolidColor(Color.Transparent), strokeWidth = 0.dp)
        val connLine = LineStyle(brush = SolidColor(lineColor), strokeWidth = 0.5.dp)

        // PASS 0: league divider + labels
        val dividerLeft = pos.allX().min() - 0.3f
        val dividerRight = pos.allX().max() + 0.3f
        val dottedLine = LineStyle(
            brush = SolidColor(lineColor),
            strokeWidth = 0.5.dp,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
        )
        LinePlot(
            data = listOf(DefaultPoint(dividerLeft, pos.finalsY), DefaultPoint(dividerRight, pos.finalsY)),
            lineStyle = dottedLine
        )
        val labelOffset = 0.15f
        leagues.getOrNull(0)?.let { al ->
            LinePlot(data = listOf(DefaultPoint(dividerLeft, pos.finalsY + labelOffset)), lineStyle = noLine, symbol = {
                Text(mlbLeagueLabel(al.name), style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp, color = lineColor, fontWeight = FontWeight.Medium)
            })
        }
        leagues.getOrNull(1)?.let { nl ->
            LinePlot(data = listOf(DefaultPoint(dividerLeft, pos.finalsY - labelOffset)), lineStyle = noLine, symbol = {
                Text(mlbLeagueLabel(nl.name), style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.sp, color = lineColor, fontWeight = FontWeight.Medium)
            })
        }

        // PASS 1: connectors
        leagues.forEachIndexed { idx, _ ->
            val wcX = if (idx == 0) pos.alWcX else pos.nlWcX
            val wcY = if (idx == 0) pos.alWcY else pos.nlWcY
            val dsX = if (idx == 0) pos.alDsX else pos.nlDsX
            val dsY = if (idx == 0) pos.alDsY else pos.nlDsY
            val lcsX = if (idx == 0) pos.alLcsX else pos.nlLcsX
            val lcsY = if (idx == 0) pos.alLcsY else pos.nlLcsY

            for (arm in 0..1) {
                // Wild Card winner feeds the Division Series: elbow in, then across.
                val barX = wcX[arm] + 0.35f * (dsX[arm] - wcX[arm])
                LinePlot(data = listOf(DefaultPoint(wcX[arm], wcY[arm]), DefaultPoint(barX, wcY[arm])), lineStyle = connLine)
                LinePlot(data = listOf(DefaultPoint(barX, wcY[arm]), DefaultPoint(barX, dsY[arm])), lineStyle = connLine)
                LinePlot(data = listOf(DefaultPoint(barX, dsY[arm]), DefaultPoint(dsX[arm], dsY[arm])), lineStyle = connLine)

                // Division Series winner into the championship series.
                LinePlot(data = listOf(DefaultPoint(dsX[arm], dsY[arm]), DefaultPoint(dsX[arm], lcsY)), lineStyle = connLine)
                LinePlot(data = listOf(DefaultPoint(dsX[arm], lcsY), DefaultPoint(lcsX, lcsY)), lineStyle = connLine)
            }
        }
        LinePlot(data = listOf(DefaultPoint(pos.alLcsX, pos.alLcsY), DefaultPoint(pos.finalsX, pos.finalsY)), lineStyle = connLine)
        LinePlot(data = listOf(DefaultPoint(pos.nlLcsX, pos.nlLcsY), DefaultPoint(pos.finalsX, pos.finalsY)), lineStyle = connLine)

        // PASS 2: matchup nodes
        leagues.forEachIndexed { idx, league ->
            val wcX = if (idx == 0) pos.alWcX else pos.nlWcX
            val wcY = if (idx == 0) pos.alWcY else pos.nlWcY
            val dsX = if (idx == 0) pos.alDsX else pos.nlDsX
            val dsY = if (idx == 0) pos.alDsY else pos.nlDsY
            val lcsX = if (idx == 0) pos.alLcsX else pos.nlLcsX
            val lcsY = if (idx == 0) pos.alLcsY else pos.nlLcsY

            val wcGames = league.rounds.getOrNull(0) ?: emptyList()
            val dsGames = league.rounds.getOrNull(1) ?: emptyList()
            val lcsGames = league.rounds.getOrNull(2) ?: emptyList()

            wcGames.forEachIndexed { gi, game ->
                if (gi >= 2) return@forEachIndexed
                LinePlot(data = listOf(DefaultPoint(wcX[gi], wcY[gi])), lineStyle = noLine, symbol = {
                    PlayoffMatchupBoxSymbol(game, league.color, textColor, backgroundColor) {
                        game.sourceMatchup?.let {
                            onMatchupClick?.invoke(it, mlbLeagueName(league.name),
                                it.roundName ?: "Wild Card Series", league.color)
                        }
                    }
                })
            }

            val usedArms = mutableSetOf<Int>()
            dsGames.forEachIndexed { gi, game ->
                if (gi >= 2) return@forEachIndexed
                var armIdx = mlbDivisionArmIndex(game, wcGames, gi)
                if (armIdx in usedArms) armIdx = (0..1).first { it !in usedArms }
                usedArms.add(armIdx)
                LinePlot(data = listOf(DefaultPoint(dsX[armIdx], dsY[armIdx])), lineStyle = noLine, symbol = {
                    PlayoffMatchupBoxSymbol(game, league.color, textColor, backgroundColor) {
                        game.sourceMatchup?.let {
                            onMatchupClick?.invoke(it, mlbLeagueName(league.name),
                                it.roundName ?: "Division Series", league.color)
                        }
                    }
                })
            }

            lcsGames.firstOrNull()?.let { game ->
                LinePlot(data = listOf(DefaultPoint(lcsX, lcsY)), lineStyle = noLine, symbol = {
                    PlayoffMatchupBoxSymbol(game, league.color, textColor, backgroundColor) {
                        game.sourceMatchup?.let {
                            onMatchupClick?.invoke(it, mlbLeagueName(league.name),
                                it.roundName ?: "League Championship Series", league.color)
                        }
                    }
                })
            }
        }

        LinePlot(data = listOf(DefaultPoint(pos.finalsX, pos.finalsY)), lineStyle = noLine, symbol = {
            PlayoffMatchupBoxSymbol(worldSeriesGame, Color(0xFFFFD700), textColor, backgroundColor) {
                worldSeriesGame.sourceMatchup?.let {
                    onMatchupClick?.invoke(it, "World Series", it.roundName ?: "World Series", Color(0xFFFFD700))
                }
            }
        })
    }
}

/** Title-case league name for the matchup sheet header. */
private fun mlbLeagueName(name: String): String = when (name.uppercase()) {
    "AL" -> "American League"
    "NL" -> "National League"
    else -> name
}

/** All-caps league name for the divider label on the bracket canvas. */
private fun mlbLeagueLabel(name: String): String = mlbLeagueName(name).uppercase()

// ============================================================================
// Bracket share image
// ============================================================================

@Composable
private fun MLBBracketShareImage(
    title: String,
    subtitle: String,
    source: String?,
    leagues: List<PlayoffBracketConference>,
    worldSeriesGame: PlayoffBracketGame,
    pos: MLBBracketPositions
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val onBackground = MaterialTheme.colorScheme.onBackground
    val dimColor = onBackground.copy(alpha = 0.6f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = onBackground, maxLines = 1)
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = dimColor, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // No click handler: the shared image is a picture, not a surface.
            MLBBracketCanvas(
                leagues = leagues,
                worldSeriesGame = worldSeriesGame,
                pos = pos,
                onMatchupClick = null
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        // Sources take the whole first line so fbrk.app can sit on its own line
        // beneath rather than being squeezed into a stack of letters.
        Column(modifier = Modifier.fillMaxWidth()) {
            source?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                    color = dimColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth())
            }
            Text("fbrk.app", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                fontWeight = FontWeight.Bold, color = dimColor, maxLines = 1,
                textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ============================================================================
// Matchup bottom sheet
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MLBPlayoffMatchupBottomSheet(data: MLBMatchupSheetData, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var topNavSelection by remember { mutableIntStateOf(0) }
    var viewSelection by remember { mutableIntStateOf(0) }

    val matchup = data.matchup
    val comparisons = matchup.comparisons
    val bestOf = matchup.bestOf ?: 7

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

    var captureRequested by remember { mutableStateOf(false) }
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }
    var runDiffShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var weeklyShareCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 36.dp, bottom = 96.dp)
                    ) {
                        if (t1 != null || t2 != null) {
                            PlayoffMatchupRecordRow(
                                team1 = t1,
                                team2 = t2,
                                seriesStatus = playoffSeriesStatus(matchup, t1, t2),
                                seriesStatusColor = data.leagueColor
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(8.dp)
                                .background(data.leagueColor, RoundedCornerShape(4.dp)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(mlbRoundHeader(data), style = MaterialTheme.typography.bodySmall,
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
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                                    if (comparisons != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TeamStatsNavBadge("Team", viewSelection == 0) { viewSelection = 0 }
                                            TeamStatsNavBadge("$t1Abbrev Bat vs $t2Abbrev Pitch", viewSelection == 1) { viewSelection = 1 }
                                            TeamStatsNavBadge("$t2Abbrev Bat vs $t1Abbrev Pitch", viewSelection == 2) { viewSelection = 2 }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        when (viewSelection) {
                                            0 -> {
                                                MLBBracketTeamStatsView(comparisons)
                                                val t1Trend = parseMLBMonthTrend(t1?.teamStats)
                                                val t2Trend = parseMLBMonthTrend(t2?.teamStats)
                                                if (t1Trend != null || t2Trend != null) {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    MLBBracketTrendSection("One Month Trend", t1Abbrev, t2Abbrev, t1Trend, t2Trend)
                                                }
                                                val t1Po = parseMLBMonthTrend(t1?.teamStats, "playoffTrend")
                                                val t2Po = parseMLBMonthTrend(t2?.teamStats, "playoffTrend")
                                                if (t1Po != null || t2Po != null) {
                                                    val gp = maxOf(
                                                        (t1Po?.wins ?: 0) + (t1Po?.losses ?: 0),
                                                        (t2Po?.wins ?: 0) + (t2Po?.losses ?: 0)
                                                    )
                                                    val header = if (gp > 0) "Playoff Trend (Last $gp Games)" else "Playoff Trend"
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    MLBBracketTrendSection(header, t1Abbrev, t2Abbrev, t1Po, t2Po)
                                                }
                                            }
                                            1 -> MLBBracketOffVsDefView(comparisons.homeOffVsAwayDef, t1Abbrev, t2Abbrev)
                                            2 -> MLBBracketOffVsDefView(comparisons.awayOffVsHomeDef, t2Abbrev, t1Abbrev)
                                        }
                                    } else {
                                        Text("Comparison stats not available",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                    }

                                    matchup.regularSeasonHistory?.let { history ->
                                        Spacer(modifier = Modifier.height(16.dp))
                                        MLBBracketSeasonSeriesView(history)
                                    }
                                }
                                1 -> PlayoffSeriesResultsView(matchup.games, t1Abbrev, t2Abbrev, bestOf)
                                2 -> {
                                    val viz = data.visualization
                                    val t1Stats = t1?.teamStats
                                    val t2Stats = t2?.teamStats
                                    if (t1Stats != null && t2Stats != null) {
                                        MLBCumRunDiffChart(
                                            awayTeam = t1Abbrev,
                                            homeTeam = t2Abbrev,
                                            awayStats = t1Stats,
                                            homeStats = t2Stats,
                                            leagueCumRunDiffStats = viz?.leagueCumRunDiffStats,
                                            onShareClick = { cb -> runDiffShareCallback = cb }
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        MLBWeeklyPerformanceChart(
                                            awayTeam = t1Abbrev,
                                            homeTeam = t2Abbrev,
                                            awayStats = t1Stats,
                                            homeStats = t2Stats,
                                            leagueWeeklyStats = viz?.leagueWeeklyStats,
                                            onShareClick = { cb -> weeklyShareCallback = cb }
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

                    PinnedMatchupHeader(
                        awayTeam = t1Display,
                        homeTeam = t2Display,
                        awayScore = t1?.score,
                        homeScore = t2?.score,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }

            if (!hasTbdTeam) {
                when (topNavSelection) {
                    2 -> {
                        val chartOptions = listOfNotNull(
                            runDiffShareCallback?.let { cb ->
                                FabOption(Icons.Filled.TrendingUp, "Cumulative Run Diff") { cb() }
                            },
                            weeklyShareCallback?.let { cb ->
                                FabOption(Icons.Filled.Star, "Runs Scored vs Allowed") { cb() }
                            }
                        )
                        if (chartOptions.isNotEmpty()) {
                            MultiOptionFab(
                                options = chartOptions,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                            )
                        }
                    }
                    0 -> if (comparisons != null) {
                        ShareFab(
                            onClick = { captureRequested = true },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        )
                    }
                    else -> {}
                }
            }

            if (captureRequested && comparisons != null) {
                val shareTitle = "$t1Abbrev vs $t2Abbrev - ${data.roundName}"
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(50)
                    try {
                        val bmp = graphicsLayer.toImageBitmap()
                        imageExporter.shareImage(bmp, shareTitle)
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
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(graphicsLayer)
                            }
                    ) {
                        val nextGameDate = matchup.games
                            .firstOrNull { !it.completed && it.gameDate != null }
                            ?.gameDate?.let { formatBracketGameDate(it) }
                        val gameInfo = ShareGameInfo(
                            awayTeam = t1Abbrev,
                            homeTeam = t2Abbrev,
                            eventLabel = mlbRoundHeader(data),
                            formattedDate = nextGameDate?.let { "Next: $it" } ?: "",
                            source = data.visualization?.source ?: "ESPN",
                            awayRecord = t1?.wins?.let { w -> t1.losses?.let { l -> "$w-$l" } },
                            homeRecord = t2?.wins?.let { w -> t2.losses?.let { l -> "$w-$l" } },
                            awaySeed = t1?.seed?.takeIf { it > 0 },
                            homeSeed = t2?.seed?.takeIf { it > 0 },
                            seriesStatus = playoffSeriesStatus(matchup, t1, t2)
                        )

                        val statBoxes = buildList {
                            add(ShareStatBox(
                                title = "Batting",
                                fiveColStats = sideBySideShareRows(comparisons.sideBySide?.offense)
                            ))
                            add(ShareStatBox(
                                title = "Pitching",
                                fiveColStats = sideBySideShareRows(comparisons.sideBySide?.defense)
                            ))
                            add(ShareStatBox(
                                title = "$t1Abbrev Bat vs $t2Abbrev Pitch",
                                leftLabel = "$t1Abbrev Bat", middleLabel = "vs", rightLabel = "$t2Abbrev Pitch",
                                fiveColStats = offVsDefShareRows(comparisons.homeOffVsAwayDef)
                            ))
                            add(ShareStatBox(
                                title = "$t2Abbrev Bat vs $t1Abbrev Pitch",
                                leftLabel = "$t2Abbrev Bat", middleLabel = "vs", rightLabel = "$t1Abbrev Pitch",
                                leftColor = Team2Color, rightColor = Team1Color,
                                fiveColStats = offVsDefShareRows(comparisons.awayOffVsHomeDef)
                            ))

                            // Prefer the playoff trend when the postseason is under way,
                            // otherwise fall back to the regular-season month trend.
                            val t1Po = parseMLBMonthTrend(t1?.teamStats, "playoffTrend")
                            val t2Po = parseMLBMonthTrend(t2?.teamStats, "playoffTrend")
                            val t1Trend = parseMLBMonthTrend(t1?.teamStats)
                            val t2Trend = parseMLBMonthTrend(t2?.teamStats)
                            if (t1Po != null || t2Po != null) {
                                val gp = maxOf(
                                    (t1Po?.wins ?: 0) + (t1Po?.losses ?: 0),
                                    (t2Po?.wins ?: 0) + (t2Po?.losses ?: 0)
                                )
                                add(ShareStatBox(
                                    title = if (gp > 0) "Playoff Trend (Last $gp Games)" else "Playoff Trend",
                                    fiveColStats = mlbTrendShareRows(t1Po, t2Po)
                                ))
                            } else if (t1Trend != null || t2Trend != null) {
                                add(ShareStatBox(
                                    title = "One Month Trend",
                                    fiveColStats = mlbTrendShareRows(t1Trend, t2Trend)
                                ))
                            }

                            // Series results so far, then the regular-season head-to-head.
                            val seriesRows = matchup.games.mapIndexedNotNull { idx, g ->
                                if (!g.completed) return@mapIndexedNotNull null
                                val adv = when {
                                    g.team1?.winner == true -> -1
                                    g.team2?.winner == true -> 1
                                    else -> 0
                                }
                                val home = g.homeTeamAbbrev?.takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
                                ShareFiveColStat(
                                    "${g.team1?.score ?: "-"}", null, null,
                                    "Game ${idx + 1}$home",
                                    "${g.team2?.score ?: "-"}", null, null,
                                    adv
                                )
                            }
                            if (seriesRows.isNotEmpty()) {
                                add(ShareStatBox(
                                    title = "Series (Best of $bestOf)",
                                    fiveColStats = seriesRows.take(9)
                                ))
                            }

                            matchup.regularSeasonHistory?.let { history ->
                                val rows = mlbSeasonSeriesShareRows(history, t1Abbrev, t2Abbrev)
                                if (rows.isNotEmpty()) {
                                    add(ShareStatBox(
                                        title = "Season Series (${history.teamAWins}-${history.teamBWins})",
                                        fiveColStats = rows
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
        }
    }
}

/**
 * "Division Series • American League", but just "World Series" for the round
 * that has no league attached to it.
 */
private fun mlbRoundHeader(data: MLBMatchupSheetData): String =
    if (data.leagueName.equals(data.roundName, ignoreCase = true)) data.roundName
    else "${data.roundName} • ${data.leagueName}"

// ============================================================================
// Stat views
//
// MLB rate stats (AVG / OBP / SLG / OPS / fielding %) are three-decimal
// conventions, so these render at three decimals rather than reusing the
// two-decimal BracketTeamStatsView.
// ============================================================================

private fun rankAdvantage(left: Int?, right: Int?): Int =
    if (left != null && right != null) {
        when { left < right -> -1; left > right -> 1; else -> 0 }
    } else 0

@Composable
private fun MLBBracketStatSection(
    header: String,
    stats: Map<String, SideBySideStatComparison>
) {
    if (stats.isEmpty()) return
    SectionHeader(header)
    Spacer(modifier = Modifier.height(4.dp))
    stats.forEach { (_, stat) ->
        FiveColumnRowWithRanks(
            leftValue = stat.home.value?.bracketFormatStat(3) ?: "-",
            leftRank = stat.home.rank, leftRankDisplay = stat.home.rankDisplay,
            centerText = stat.label,
            rightValue = stat.away.value?.bracketFormatStat(3) ?: "-",
            rightRank = stat.away.rank, rightRankDisplay = stat.away.rankDisplay,
            advantage = rankAdvantage(stat.home.rank, stat.away.rank),
            useCBBRanks = false,
            rankColorFn = ::mlbRankColor
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun MLBBracketTeamStatsView(comparisons: MatchupComparisons) {
    val sideBySide = comparisons.sideBySide ?: return
    MLBBracketStatSection("Batting", sideBySide.offense)
    MLBBracketStatSection("Pitching", sideBySide.defense)
    MLBBracketStatSection("Fielding", sideBySide.overall)
}

@Composable
private fun MLBBracketOffVsDefView(
    comparisons: Map<String, MatchupStatComparison>,
    offTeam: String,
    defTeam: String
) {
    if (comparisons.isEmpty()) {
        Text("No batting vs pitching comparison available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        return
    }

    SectionHeader("$offTeam Batting vs $defTeam Pitching")
    Spacer(modifier = Modifier.height(4.dp))
    FiveColumnRowWithRanks(
        leftValue = offTeam, leftRank = null, leftRankDisplay = null,
        centerText = "", rightValue = defTeam, rightRank = null, rightRankDisplay = null,
        advantage = 0
    )
    comparisons.forEach { (_, stat) ->
        FiveColumnRowWithRanks(
            leftValue = stat.offense.value?.bracketFormatStat(3) ?: "-",
            leftRank = stat.offense.rank, leftRankDisplay = stat.offense.rankDisplay,
            centerText = "${stat.offLabel}\nvs ${stat.defLabel}",
            rightValue = stat.defense.value?.bracketFormatStat(3) ?: "-",
            rightRank = stat.defense.rank, rightRankDisplay = stat.defense.rankDisplay,
            advantage = stat.advantage ?: 0,
            useCBBRanks = false,
            rankColorFn = ::mlbRankColor
        )
    }
}

@Composable
private fun MLBBracketTrendSection(
    header: String,
    t1Abbrev: String,
    t2Abbrev: String,
    t1Trend: MLBMonthTrendData?,
    t2Trend: MLBMonthTrendData?
) {
    SectionHeader(header)
    Spacer(modifier = Modifier.height(4.dp))

    FiveColumnRowWithRanks(
        leftValue = t1Abbrev, leftRank = null, leftRankDisplay = null,
        centerText = "", rightValue = t2Abbrev, rightRank = null, rightRankDisplay = null,
        advantage = 0
    )

    FiveColumnRowWithRanks(
        leftValue = t1Trend?.let { "${it.wins}-${it.losses}" } ?: "-",
        leftRank = t1Trend?.recordRank, leftRankDisplay = t1Trend?.recordRankDisplay,
        centerText = "Record",
        rightValue = t2Trend?.let { "${it.wins}-${it.losses}" } ?: "-",
        rightRank = t2Trend?.recordRank, rightRankDisplay = t2Trend?.recordRankDisplay,
        advantage = rankAdvantage(t1Trend?.recordRank, t2Trend?.recordRank),
        rankColorFn = ::mlbRankColor
    )

    mlbTrendRows(t1Trend, t2Trend).forEach { row ->
        FiveColumnRowWithRanks(
            leftValue = row.leftValue, leftRank = row.leftRank, leftRankDisplay = row.leftRankDisplay,
            centerText = row.centerText,
            rightValue = row.rightValue, rightRank = row.rightRank, rightRankDisplay = row.rightRankDisplay,
            advantage = row.advantage,
            rankColorFn = ::mlbRankColor
        )
    }
}

@Composable
private fun MLBBracketSeasonSeriesView(history: RegularSeasonHistory) {
    val teamA = history.teamAAbbrev ?: return
    val teamB = history.teamBAbbrev ?: return

    SectionHeader("Season Series ($teamA ${history.teamAWins} - ${history.teamBWins} $teamB)")
    Spacer(modifier = Modifier.height(4.dp))

    history.games.forEach { game ->
        val aIsHome = game.homeAbbrev == teamA
        val aScore = if (aIsHome) game.homeScore else game.awayScore
        val bScore = if (aIsHome) game.awayScore else game.homeScore
        val advantage = when (game.winnerAbbrev) {
            teamA -> -1
            teamB -> 1
            else -> 0
        }
        val centerText = listOfNotNull(
            mlbFormatSeasonDate(game.gameDate),
            game.homeAbbrev?.let { "@ $it" }
        ).joinToString("\n")

        FiveColumnRowWithRanks(
            leftValue = aScore?.toString() ?: "-", leftRank = null, leftRankDisplay = null,
            centerText = centerText,
            rightValue = bScore?.toString() ?: "-", rightRank = null, rightRankDisplay = null,
            advantage = advantage
        )
    }
}

private fun mlbFormatSeasonDate(date: String?): String? {
    if (date.isNullOrBlank()) return null
    return try {
        val parts = date.take(10).split("-")
        if (parts.size != 3) return null
        val monthName = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )[parts[1].toInt() - 1]
        "$monthName ${parts[2].toInt()}"
    } catch (_: Exception) {
        null
    }
}

// ============================================================================
// Share row builders (shared between the sheet's share image and its views)
// ============================================================================

private fun mlbTrendRows(
    t1Trend: MLBMonthTrendData?,
    t2Trend: MLBMonthTrendData?
): List<ShareFiveColStat> = buildList {
    fun row(label: String, l: Double?, lr: Int?, lrd: String?, r: Double?, rr: Int?, rrd: String?) {
        if (l == null && r == null) return
        add(ShareFiveColStat(
            l?.bracketFormatStat(2) ?: "-", lr, lrd,
            label,
            r?.bracketFormatStat(2) ?: "-", rr, rrd,
            rankAdvantage(lr, rr)
        ))
    }
    row("Run Diff/G", t1Trend?.runDiffPerGame, t1Trend?.runDiffPerGameRank, t1Trend?.runDiffPerGameRankDisplay,
        t2Trend?.runDiffPerGame, t2Trend?.runDiffPerGameRank, t2Trend?.runDiffPerGameRankDisplay)
    row("Runs/G", t1Trend?.runsPerGame, t1Trend?.runsPerGameRank, t1Trend?.runsPerGameRankDisplay,
        t2Trend?.runsPerGame, t2Trend?.runsPerGameRank, t2Trend?.runsPerGameRankDisplay)
    row("Runs Allowed/G", t1Trend?.runsAllowedPerGame, t1Trend?.runsAllowedPerGameRank, t1Trend?.runsAllowedPerGameRankDisplay,
        t2Trend?.runsAllowedPerGame, t2Trend?.runsAllowedPerGameRank, t2Trend?.runsAllowedPerGameRankDisplay)
    row("Hits/G", t1Trend?.hitsPerGame, t1Trend?.hitsPerGameRank, t1Trend?.hitsPerGameRankDisplay,
        t2Trend?.hitsPerGame, t2Trend?.hitsPerGameRank, t2Trend?.hitsPerGameRankDisplay)
    row("HR/G", t1Trend?.hrsPerGame, t1Trend?.hrsPerGameRank, t1Trend?.hrsPerGameRankDisplay,
        t2Trend?.hrsPerGame, t2Trend?.hrsPerGameRank, t2Trend?.hrsPerGameRankDisplay)
}

private fun mlbTrendShareRows(
    t1Trend: MLBMonthTrendData?,
    t2Trend: MLBMonthTrendData?
): List<ShareFiveColStat> = buildList {
    add(ShareFiveColStat(
        t1Trend?.let { "${it.wins}-${it.losses}" } ?: "-",
        t1Trend?.recordRank, t1Trend?.recordRankDisplay,
        "Record",
        t2Trend?.let { "${it.wins}-${it.losses}" } ?: "-",
        t2Trend?.recordRank, t2Trend?.recordRankDisplay,
        rankAdvantage(t1Trend?.recordRank, t2Trend?.recordRank)
    ))
    addAll(mlbTrendRows(t1Trend, t2Trend))
}.take(9)

private fun sideBySideShareRows(
    stats: Map<String, SideBySideStatComparison>?
): List<ShareFiveColStat> =
    stats.orEmpty().mapNotNull { (_, stat) ->
        val left = stat.home.value?.bracketFormatStat(3) ?: return@mapNotNull null
        val right = stat.away.value?.bracketFormatStat(3) ?: return@mapNotNull null
        ShareFiveColStat(
            left, stat.home.rank, stat.home.rankDisplay,
            stat.label,
            right, stat.away.rank, stat.away.rankDisplay,
            rankAdvantage(stat.home.rank, stat.away.rank)
        )
    }.take(9)

private fun offVsDefShareRows(
    comparisons: Map<String, MatchupStatComparison>
): List<ShareFiveColStat> =
    comparisons.mapNotNull { (_, stat) ->
        val off = stat.offense.value?.bracketFormatStat(3) ?: return@mapNotNull null
        val def = stat.defense.value?.bracketFormatStat(3) ?: return@mapNotNull null
        ShareFiveColStat(
            off, stat.offense.rank, stat.offense.rankDisplay,
            "${stat.offLabel} vs ${stat.defLabel}",
            def, stat.defense.rank, stat.defense.rankDisplay,
            stat.advantage ?: 0
        )
    }.take(9)

private fun mlbSeasonSeriesShareRows(
    history: RegularSeasonHistory,
    t1Abbrev: String,
    t2Abbrev: String
): List<ShareFiveColStat> {
    val teamA = history.teamAAbbrev ?: t1Abbrev
    val teamB = history.teamBAbbrev ?: t2Abbrev
    return history.games.map { g ->
        val aIsHome = g.homeAbbrev == teamA
        val aScore = if (aIsHome) g.homeScore else g.awayScore
        val bScore = if (aIsHome) g.awayScore else g.homeScore
        val advantage = when (g.winnerAbbrev) {
            teamA -> -1
            teamB -> 1
            else -> 0
        }
        val center = listOfNotNull(
            mlbFormatSeasonDate(g.gameDate),
            g.homeAbbrev?.let { "@$it" }
        ).joinToString(" ")
        ShareFiveColStat(
            aScore?.toString() ?: "-", null, null,
            center,
            bScore?.toString() ?: "-", null, null,
            advantage
        )
    }.take(9)
}
