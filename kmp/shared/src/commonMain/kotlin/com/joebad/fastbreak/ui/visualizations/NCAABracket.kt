package com.joebad.fastbreak.ui.visualizations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.BracketGameInfo
import com.joebad.fastbreak.data.model.BracketRegionInfo
import com.joebad.fastbreak.data.model.NCAABracketVisualization
import io.github.koalaplot.core.gestures.GestureConfig
import io.github.koalaplot.core.line.LinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.XYGraphScope
import io.github.koalaplot.core.xygraph.rememberAxisStyle
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round

// Data classes
data class BracketTeam(
    val seed: Int,
    val name: String,
    val score: Int? = null,
    val isWinner: Boolean = false
)

data class BracketGame(
    val team1: BracketTeam?,
    val team2: BracketTeam?,
    val gameNumber: Int,
    val sourceGameInfo: BracketGameInfo? = null
)

data class BracketRegion(
    val name: String,
    val color: Color,
    val rounds: List<List<BracketGame>>
)

data class MatchupSheetData(
    val game: BracketGame,
    val gameInfo: BracketGameInfo? = null,  // Actual game data with comparisons
    val regionName: String,
    val roundName: String,
    val regionColor: Color
)

/**
 * Data class to hold bracket navigation toggle state and handler
 */
data class BracketNavigationToggleHandler(
    val isExpanded: Boolean,
    val toggle: () -> Unit
)

// Region quadrant positions in the unified coordinate system
private data class QuadrantBounds(
    val xMin: Float,
    val xMax: Float,
    val yMin: Float,
    val yMax: Float
)

// Round X positions with spacing (2 units between rounds)
// Rounds at: 1, 3, 5, 7 for left regions (shifted right by 1 to avoid cutoff)
// Rounds at: 5, 7, 9, 11 for right regions (reversed: 11, 9, 7, 5)
private const val ROUND_SPACING = 2f
private val ROUND_X_POSITIONS = listOf(1f, 3f, 5f, 7f)

// Quadrant layout:
// East (top-left)     | South (top-right, reversed)
//                 F4
// Midwest (bottom-left) | West (bottom-right, reversed)
private val QUADRANT_BOUNDS = listOf(
    QuadrantBounds(xMin = 0f, xMax = 8f, yMin = 16.5f, yMax = 33.5f),       // East (0)
    QuadrantBounds(xMin = 4f, xMax = 12f, yMin = 16.5f, yMax = 33.5f),      // South (1)
    QuadrantBounds(xMin = 0f, xMax = 8f, yMin = -0.5f, yMax = 16.5f),       // Midwest (2)
    QuadrantBounds(xMin = 4f, xMax = 12f, yMin = -0.5f, yMax = 16.5f),      // West (3)
    QuadrantBounds(xMin = 2f, xMax = 10f, yMin = 4f, yMax = 29f)            // Final Four (4) - centered
)

@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
fun NCAABracket(
    visualization: NCAABracketVisualization? = null,
    modifier: Modifier = Modifier,
    onNavigationToggleHandlerChanged: ((BracketNavigationToggleHandler?) -> Unit)? = null
) {
    // Use visualization data if available, otherwise use mock data
    val regions = remember(visualization) {
        visualization?.let { viz ->
            viz.regions.mapNotNull { regionInfo ->
                convertRegionInfoToBracketRegion(regionInfo)
            }
        } ?: generateBracketRegions()
    }

    var currentQuadrant by remember { mutableIntStateOf(0) }
    var isNavigationExpanded by remember { mutableStateOf(true) }
    var selectedMatchup by remember { mutableStateOf<MatchupSheetData?>(null) }

    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val lineColor = MaterialTheme.colorScheme.onBackground

    // Expose navigation toggle handler to parent
    LaunchedEffect(isNavigationExpanded) {
        onNavigationToggleHandlerChanged?.invoke(
            BracketNavigationToggleHandler(
                isExpanded = isNavigationExpanded,
                toggle = { isNavigationExpanded = !isNavigationExpanded }
            )
        )
    }

    // Cleanup when leaving
    DisposableEffect(Unit) {
        onDispose {
            onNavigationToggleHandlerChanged?.invoke(null)
        }
    }

    // Full coordinate system for entire bracket
    // X: 0-12 (left regions: 0-4.5, right regions: 7.5-12)
    // Y: 0-33 (bottom regions: 0-16, top regions: 17-33)
    val xAxisModel = remember {
        FloatLinearAxisModel(
            range = -0.5f..12.5f,
            minViewExtent = 6.5f,  // One quadrant width
            maxViewExtent = 13f    // Full bracket width
        )
    }

    val yAxisModel = remember {
        FloatLinearAxisModel(
            range = -0.5f..33.5f,
            minViewExtent = 17f,   // One quadrant height
            maxViewExtent = 34f    // Full bracket height
        )
    }

    // Navigate to quadrant when selection changes
    LaunchedEffect(currentQuadrant) {
        val bounds = QUADRANT_BOUNDS[currentQuadrant]
        xAxisModel.setViewRange(bounds.xMin..bounds.xMax)
        yAxisModel.setViewRange(bounds.yMin..bounds.yMax)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        // Use Box for overlay layout - bracket fills entire space, navigation overlays on top
        Box(modifier = Modifier.fillMaxSize()) {
            // Single unified bracket visualization (fills entire space)
            XYGraph(
                xAxisModel = xAxisModel,
                yAxisModel = yAxisModel,
                gestureConfig = GestureConfig(
                    panXEnabled = true,
                    panYEnabled = true,
                    zoomXEnabled = true,
                    zoomYEnabled = true
                ),
                xAxisStyle = rememberAxisStyle(
                    color = Color.Transparent,
                    tickPosition = io.github.koalaplot.core.xygraph.TickPosition.None
                ),
                yAxisStyle = rememberAxisStyle(
                    color = Color.Transparent,
                    tickPosition = io.github.koalaplot.core.xygraph.TickPosition.None
                ),
                xAxisLabels = { },
                yAxisLabels = { },
                xAxisTitle = { },
                yAxisTitle = { },
                horizontalMajorGridLineStyle = null,
                horizontalMinorGridLineStyle = null,
                verticalMajorGridLineStyle = null,
                verticalMinorGridLineStyle = null,
                modifier = Modifier.fillMaxSize().semantics { contentDescription = "chart" }
            ) {
                // Calculate zoom level from current view extent
                // When zoomed in (extent ~8), show details. When zoomed out (extent ~13), show dots.
                val currentXRange = xAxisModel.viewRange.value
                val currentXExtent = currentXRange.endInclusive - currentXRange.start
                // Threshold: show details when viewing roughly one quadrant or less
                // Default quadrant extent is 8, so use 9.5 to give margin for panning
                val isZoomedIn = currentXExtent <= 9.5f

                // FIRST PASS: Draw all connector lines (behind nodes)
                // Draw region connectors
                regions.forEachIndexed { index, region ->
                    val isReversed = index == 1 || index == 3  // South and West are reversed
                    val xOffset = if (index == 1 || index == 3) 4f else 0f
                    val yOffset = if (index == 0 || index == 1) 17f else 0f

                    DrawRegionConnectors(
                        isReversed = isReversed,
                        xOffset = xOffset,
                        yOffset = yOffset,
                        lineColor = lineColor
                    )
                }

                // Draw Final Four connectors
                DrawFinalFourConnectors(
                    regions = regions,
                    lineColor = lineColor,
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    isLandscape = isLandscape,
                    visualization = visualization,
                    drawConnectorsOnly = true,
                    onMatchupClick = { _, _, _, _ -> }
                )

                // SECOND PASS: Draw all matchup boxes (on top of connectors)
                // Draw region matchup boxes
                regions.forEachIndexed { index, region ->
                    val isReversed = index == 1 || index == 3  // South and West are reversed
                    val xOffset = if (index == 1 || index == 3) 4f else 0f
                    val yOffset = if (index == 0 || index == 1) 17f else 0f

                    DrawRegionMatchupBoxes(
                        region = region,
                        isReversed = isReversed,
                        xOffset = xOffset,
                        yOffset = yOffset,
                        showDetails = isZoomedIn,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        onMatchupClick = { game, roundName ->
                            selectedMatchup = MatchupSheetData(game, game.sourceGameInfo, region.name, roundName, region.color)
                        }
                    )
                }

                // Draw Final Four matchup boxes
                DrawFinalFourConnectors(
                    regions = regions,
                    lineColor = lineColor,
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    isLandscape = isLandscape,
                    visualization = visualization,
                    drawConnectorsOnly = false,
                    onMatchupClick = { game, regionName, roundName, color ->
                        selectedMatchup = MatchupSheetData(game, game.sourceGameInfo, regionName, roundName, color)
                    }
                )
            }

            // Collapsible bracket navigation (overlays on top of bracket)
            AnimatedVisibility(
                visible = isNavigationExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    MiniMapNavigation(
                        regions = regions,
                        currentIndex = currentQuadrant,
                        onRegionClick = { index -> currentQuadrant = index },
                        onFinalFourClick = { currentQuadrant = 4 },
                        isFinalFourSelected = currentQuadrant == 4,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    // Bottom sheet for matchup details
    selectedMatchup?.let { matchup ->
        MatchupBottomSheet(
            matchupData = matchup,
            onDismiss = { selectedMatchup = null }
        )
    }
}

@Composable
private fun MiniMapNavigation(
    regions: List<BracketRegion>,
    currentIndex: Int,
    onRegionClick: (Int) -> Unit,
    onFinalFourClick: () -> Unit,
    isFinalFourSelected: Boolean,
    modifier: Modifier = Modifier
) {
    // Need at least 4 regions for the bracket layout
    if (regions.size < 4) return

    val eastRegion = regions.getOrNull(0) ?: return
    val southRegion = regions.getOrNull(1) ?: return
    val midwestRegion = regions.getOrNull(2) ?: return
    val westRegion = regions.getOrNull(3) ?: return

    Row(
        modifier = modifier.width(180.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left column: East (top), Midwest (bottom)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MiniRegionBox(
                region = eastRegion,
                isSelected = currentIndex == 0,
                onClick = { onRegionClick(0) }
            )
            MiniRegionBox(
                region = midwestRegion,
                isSelected = currentIndex == 2,
                onClick = { onRegionClick(2) }
            )
        }

        // Center: Final Four indicator (clickable)
        val f4BackgroundColor = if (isFinalFourSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        val f4TextColor = if (isFinalFourSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

        Box(
            modifier = Modifier
                .width(28.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(f4BackgroundColor)
                .clickable(onClick = onFinalFourClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "F4",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = f4TextColor
            )
        }

        // Right column: South (top), West (bottom)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MiniRegionBox(
                region = southRegion,
                isSelected = currentIndex == 1,
                onClick = { onRegionClick(1) }
            )
            MiniRegionBox(
                region = westRegion,
                isSelected = currentIndex == 3,
                onClick = { onRegionClick(3) }
            )
        }
    }
}

@Composable
private fun MiniRegionBox(
    region: BracketRegion,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        region.color.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderColor = if (isSelected) {
        region.color
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(27.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = region.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) region.color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Draws a single region's content at the specified offset
 * @param showDetails When true, shows full matchup boxes. When false, shows simplified dots.
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun XYGraphScope<Float, Float>.DrawRegionContent(
    region: BracketRegion,
    regionName: String,
    isReversed: Boolean,
    xOffset: Float,
    yOffset: Float,
    showDetails: Boolean,
    lineColor: Color,
    textColor: Color,
    backgroundColor: Color,
    onMatchupClick: (BracketGame, String) -> Unit
) {
    // Y positions for each round (centered vertically within region)
    // Order: top to bottom so #1 seed (index 0) appears at the top
    val round1Positions = listOf(15f, 13f, 11f, 9f, 7f, 5f, 3f, 1f)
    val round2Positions = listOf(14f, 10f, 6f, 2f)
    val round3Positions = listOf(12f, 4f)
    val round4Positions = listOf(8f)

    val allPositions = listOf(round1Positions, round2Positions, round3Positions, round4Positions)

    // Draw connecting lines between rounds (only Round of 64 → Round of 32 → Sweet 16)
    // Elite 8 connectors are not drawn here since Elite 8 is rendered in the Final Four section
    for (roundIndex in 0 until 2) {
        val currentPositions = allPositions.getOrNull(roundIndex) ?: continue
        val nextPositions = allPositions.getOrNull(roundIndex + 1) ?: continue

        // For reversed regions, rounds go 3,2,1,0 instead of 0,1,2,3
        // Use ROUND_X_POSITIONS for proper spacing (0, 1.5, 3, 4.5)
        val currentXIndex = if (isReversed) 3 - roundIndex else roundIndex
        val nextXIndex = if (isReversed) 2 - roundIndex else roundIndex + 1
        val currentX = xOffset + (ROUND_X_POSITIONS.getOrNull(currentXIndex) ?: continue)
        val nextX = xOffset + (ROUND_X_POSITIONS.getOrNull(nextXIndex) ?: continue)

        for (i in nextPositions.indices) {
            val topY = yOffset + (currentPositions.getOrNull(i * 2) ?: continue)
            val bottomY = yOffset + (currentPositions.getOrNull(i * 2 + 1) ?: continue)
            val midY = yOffset + (nextPositions.getOrNull(i) ?: continue)

            // Horizontal line from top game
            LinePlot(
                data = listOf(
                    DefaultPoint(currentX + if (isReversed) -0.4f else 0.4f, topY),
                    DefaultPoint((currentX + nextX) / 2, topY)
                ),
                lineStyle = LineStyle(
                    brush = SolidColor(lineColor),
                    strokeWidth = 1.5.dp
                )
            )

            // Horizontal line from bottom game
            LinePlot(
                data = listOf(
                    DefaultPoint(currentX + if (isReversed) -0.4f else 0.4f, bottomY),
                    DefaultPoint((currentX + nextX) / 2, bottomY)
                ),
                lineStyle = LineStyle(
                    brush = SolidColor(lineColor),
                    strokeWidth = 1.5.dp
                )
            )

            // Vertical connector
            LinePlot(
                data = listOf(
                    DefaultPoint((currentX + nextX) / 2, topY),
                    DefaultPoint((currentX + nextX) / 2, bottomY)
                ),
                lineStyle = LineStyle(
                    brush = SolidColor(lineColor),
                    strokeWidth = 1.5.dp
                )
            )

            // Horizontal line to next round
            LinePlot(
                data = listOf(
                    DefaultPoint((currentX + nextX) / 2, midY),
                    DefaultPoint(nextX + if (isReversed) 0.4f else -0.4f, midY)
                ),
                lineStyle = LineStyle(
                    brush = SolidColor(lineColor),
                    strokeWidth = 1.5.dp
                )
            )
        }
    }

    // Round names for display
    val roundNames = listOf("Round of 64", "Round of 32", "Sweet 16", "Elite 8")

    // Draw matchup boxes for each round (skip Elite 8 / round 4 - it's drawn separately in F4 section)
    region.rounds.dropLast(1).forEachIndexed { roundIndex, games ->
        val positions = allPositions.getOrNull(roundIndex) ?: return@forEachIndexed
        val xPosIndex = if (isReversed) 3 - roundIndex else roundIndex
        var x = xOffset + (ROUND_X_POSITIONS.getOrNull(xPosIndex) ?: return@forEachIndexed)

        // Shift Sweet 16 nodes away from center for better spacing
        if (roundIndex == 2) {
            x += if (isReversed) 0.7f else -0.7f
        }
        val roundName = roundNames.getOrNull(roundIndex) ?: "Round ${roundIndex + 1}"

        games.forEachIndexed { gameIndex, game ->
            val y = yOffset + (positions.getOrNull(gameIndex) ?: return@forEachIndexed)
            DrawMatchupBox(
                game = game,
                x = x,
                y = y,
                regionColor = region.color,
                textColor = textColor,
                backgroundColor = backgroundColor,
                showDetails = showDetails,
                onClick = { onMatchupClick(game, roundName) }
            )
        }
    }
}

/**
 * Draws only the connector lines for a region (Round of 64 → Round of 32 → Sweet 16)
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun XYGraphScope<Float, Float>.DrawRegionConnectors(
    isReversed: Boolean,
    xOffset: Float,
    yOffset: Float,
    lineColor: Color
) {
    val round1Positions = listOf(15f, 13f, 11f, 9f, 7f, 5f, 3f, 1f)
    val round2Positions = listOf(14f, 10f, 6f, 2f)
    val round3Positions = listOf(12f, 4f)
    val allPositions = listOf(round1Positions, round2Positions, round3Positions)

    for (roundIndex in 0 until 2) {
        val currentPositions = allPositions.getOrNull(roundIndex) ?: continue
        val nextPositions = allPositions.getOrNull(roundIndex + 1) ?: continue

        val currentXIndex = if (isReversed) 3 - roundIndex else roundIndex
        val nextXIndex = if (isReversed) 2 - roundIndex else roundIndex + 1
        val currentX = xOffset + (ROUND_X_POSITIONS.getOrNull(currentXIndex) ?: continue)
        val nextX = xOffset + (ROUND_X_POSITIONS.getOrNull(nextXIndex) ?: continue)

        for (i in nextPositions.indices) {
            val topY = yOffset + (currentPositions.getOrNull(i * 2) ?: continue)
            val bottomY = yOffset + (currentPositions.getOrNull(i * 2 + 1) ?: continue)
            val midY = yOffset + (nextPositions.getOrNull(i) ?: continue)

            // For Sweet 16 (roundIndex == 1), use larger offset to stop before shifted dots
            val nextRoundOffset = if (roundIndex == 1) 1.0f else 0.4f

            LinePlot(
                data = listOf(
                    DefaultPoint(currentX + if (isReversed) -0.4f else 0.4f, topY),
                    DefaultPoint((currentX + nextX) / 2, topY)
                ),
                lineStyle = LineStyle(brush = SolidColor(lineColor), strokeWidth = 1.5.dp)
            )
            LinePlot(
                data = listOf(
                    DefaultPoint(currentX + if (isReversed) -0.4f else 0.4f, bottomY),
                    DefaultPoint((currentX + nextX) / 2, bottomY)
                ),
                lineStyle = LineStyle(brush = SolidColor(lineColor), strokeWidth = 1.5.dp)
            )
            LinePlot(
                data = listOf(
                    DefaultPoint((currentX + nextX) / 2, topY),
                    DefaultPoint((currentX + nextX) / 2, bottomY)
                ),
                lineStyle = LineStyle(brush = SolidColor(lineColor), strokeWidth = 1.5.dp)
            )
            LinePlot(
                data = listOf(
                    DefaultPoint((currentX + nextX) / 2, midY),
                    DefaultPoint(nextX + if (isReversed) nextRoundOffset else -nextRoundOffset, midY)
                ),
                lineStyle = LineStyle(brush = SolidColor(lineColor), strokeWidth = 1.5.dp)
            )
        }
    }
}

/**
 * Draws only the matchup boxes for a region (Round of 64, Round of 32, Sweet 16)
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun XYGraphScope<Float, Float>.DrawRegionMatchupBoxes(
    region: BracketRegion,
    isReversed: Boolean,
    xOffset: Float,
    yOffset: Float,
    showDetails: Boolean,
    textColor: Color,
    backgroundColor: Color,
    onMatchupClick: (BracketGame, String) -> Unit
) {
    val round1Positions = listOf(15f, 13f, 11f, 9f, 7f, 5f, 3f, 1f)
    val round2Positions = listOf(14f, 10f, 6f, 2f)
    val round3Positions = listOf(12f, 4f)
    val allPositions = listOf(round1Positions, round2Positions, round3Positions)
    val roundNames = listOf("Round of 64", "Round of 32", "Sweet 16")

    region.rounds.dropLast(1).forEachIndexed { roundIndex, games ->
        val positions = allPositions.getOrNull(roundIndex) ?: return@forEachIndexed
        val xPosIndex = if (isReversed) 3 - roundIndex else roundIndex
        var x = xOffset + (ROUND_X_POSITIONS.getOrNull(xPosIndex) ?: return@forEachIndexed)

        if (roundIndex == 2) {
            x += if (isReversed) 0.7f else -0.7f
        }
        val roundName = roundNames.getOrNull(roundIndex) ?: "Round ${roundIndex + 1}"

        games.forEachIndexed { gameIndex, game ->
            val y = yOffset + (positions.getOrNull(gameIndex) ?: return@forEachIndexed)
            DrawMatchupBox(
                game = game,
                x = x,
                y = y,
                regionColor = region.color,
                textColor = textColor,
                backgroundColor = backgroundColor,
                showDetails = showDetails,
                onClick = { onMatchupClick(game, roundName) }
            )
        }
    }
}

/**
 * Draws Final Four connector lines, Elite 8, semifinals, and championship matchups
 * These matchups are always visible with full details regardless of zoom level
 * In landscape mode, the Final Four games are arranged horizontally side by side
 * @param drawConnectorsOnly When true, only draws connector lines. When false, only draws matchup boxes.
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun XYGraphScope<Float, Float>.DrawFinalFourConnectors(
    regions: List<BracketRegion>,
    lineColor: Color,
    textColor: Color,
    backgroundColor: Color,
    isLandscape: Boolean,
    visualization: NCAABracketVisualization? = null,
    drawConnectorsOnly: Boolean = false,
    onMatchupClick: (BracketGame, String, String, Color) -> Unit
) {
    // Need at least 4 regions to draw Final Four connectors
    if (regions.size < 4) return

    // Elite 8 positions (now separated - left regions at x=7, right regions at x=5)
    // - East: x=7 (0 + ROUND_X_POSITIONS[3]), y=25 (17 + 8)
    // - South: x=5 (4 + ROUND_X_POSITIONS[0]), y=25
    // - Midwest: x=7, y=8 (0 + 8)
    // - West: x=5, y=8

    val centerX = 6f
    val topY = 25f   // y position for East/South Elite 8 (portrait)
    val bottomY = 8f // y position for Midwest/West Elite 8 (portrait)

    // Elite 8 matchup positions (region index to position)
    // Positioned horizontally - left regions on left, right regions on right
    // This allows Sweet 16 to connect straight to Elite 8 without crossing
    val elite8LeftX = 4.2f   // For East and Midwest (left side regions)
    val elite8RightX = 7.8f  // For South and West (right side regions)

    // Y positions for Elite 8
    val elite8TopY = 25f     // East/South
    val elite8BottomY = 8f   // Midwest/West

    val elite8Positions = listOf(
        Triple(0, elite8LeftX, elite8TopY),       // East - left side, top row
        Triple(1, elite8RightX, elite8TopY),      // South - right side, top row
        Triple(2, elite8LeftX, elite8BottomY),    // Midwest - left side, bottom row
        Triple(3, elite8RightX, elite8BottomY)    // West - right side, bottom row
    )

    // Final Four positions - different layout for portrait vs landscape
    // Portrait: stacked vertically with championship in center
    // Landscape: arranged horizontally in the center (between Elite 8 rows)
    val finalFourY = 16.5f  // Center Y position for all Final Four games

    val semifinal1X = if (isLandscape) 4.0f else centerX   // Left semifinal (slightly from center)
    val semifinal1Y = if (isLandscape) finalFourY else 19f
    val semifinal2X = if (isLandscape) 8.0f else centerX   // Right semifinal (slightly from center)
    val semifinal2Y = if (isLandscape) finalFourY else 14f
    val championshipX = centerX  // Always at center (6.0)
    val championshipY = 16.5f

    // Use actual Final Four data from visualization if available, otherwise create from Elite 8 winners
    val finalFour = visualization?.finalFour

    // Helper to convert BracketGameInfo to BracketGame
    fun convertGameInfo(gameInfo: BracketGameInfo?): BracketGame? {
        if (gameInfo == null) return null
        val gameStatus = gameInfo.gameStatus?.uppercase() ?: ""
        val isGameDecided = gameStatus == "FINAL" || gameStatus == "IN_PROGRESS"

        val team1 = if (isTbdTeam(gameInfo.team1)) null else gameInfo.team1?.let { t ->
            BracketTeam(
                seed = t.seed,
                name = t.name,
                score = t.score,
                isWinner = isGameDecided && (gameInfo.winner == t.name ||
                    (t.score != null && gameInfo.team2?.score != null && t.score > gameInfo.team2.score))
            )
        }
        val team2 = if (isTbdTeam(gameInfo.team2)) null else gameInfo.team2?.let { t ->
            BracketTeam(
                seed = t.seed,
                name = t.name,
                score = t.score,
                isWinner = isGameDecided && (gameInfo.winner == t.name ||
                    (t.score != null && gameInfo.team1?.score != null && t.score > gameInfo.team1.score))
            )
        }
        return BracketGame(team1 = team1, team2 = team2, gameNumber = gameInfo.gameNumber ?: 0, sourceGameInfo = gameInfo)
    }

    // Try to use visualization Final Four data, fall back to mock data from Elite 8 winners
    val topSemifinal: BracketGame
    val bottomSemifinal: BracketGame
    val championshipGame: BracketGame

    if (finalFour?.semifinal1 != null || finalFour?.semifinal2 != null || finalFour?.championship != null) {
        // Use actual visualization data
        topSemifinal = convertGameInfo(finalFour?.semifinal1) ?: BracketGame(null, null, 0)
        bottomSemifinal = convertGameInfo(finalFour?.semifinal2) ?: BracketGame(null, null, 0)
        championshipGame = convertGameInfo(finalFour?.championship) ?: BracketGame(null, null, 0)
    } else {
        // Fall back to deriving from Elite 8 winners (mock data for preview)
        val eastElite8 = regions.getOrNull(0)?.rounds?.lastOrNull()?.firstOrNull()
        val southElite8 = regions.getOrNull(1)?.rounds?.lastOrNull()?.firstOrNull()
        val midwestElite8 = regions.getOrNull(2)?.rounds?.lastOrNull()?.firstOrNull()
        val westElite8 = regions.getOrNull(3)?.rounds?.lastOrNull()?.firstOrNull()

        val eastWinner = eastElite8?.let { game -> if (game.team1?.isWinner == true) game.team1 else game.team2 }
        val southWinner = southElite8?.let { game -> if (game.team1?.isWinner == true) game.team1 else game.team2 }
        val midwestWinner = midwestElite8?.let { game -> if (game.team1?.isWinner == true) game.team1 else game.team2 }
        val westWinner = westElite8?.let { game -> if (game.team1?.isWinner == true) game.team1 else game.team2 }

        topSemifinal = BracketGame(
            team1 = eastWinner?.copy(score = null, isWinner = false),
            team2 = southWinner?.copy(score = null, isWinner = false),
            gameNumber = 0
        )
        bottomSemifinal = BracketGame(
            team1 = midwestWinner?.copy(score = null, isWinner = false),
            team2 = westWinner?.copy(score = null, isWinner = false),
            gameNumber = 0
        )

        val topSemifinalWinner = if (topSemifinal.team1?.isWinner == true) topSemifinal.team1 else topSemifinal.team2
        val bottomSemifinalWinner = if (bottomSemifinal.team1?.isWinner == true) bottomSemifinal.team1 else bottomSemifinal.team2

        championshipGame = BracketGame(
            team1 = topSemifinalWinner?.copy(score = null, isWinner = false),
            team2 = bottomSemifinalWinner?.copy(score = null, isWinner = false),
            gameNumber = 0
        )
    }

    // Line style for connector lines
    val connectorLineStyle = LineStyle(brush = SolidColor(lineColor), strokeWidth = 1.5.dp)

    // Sweet 16 positions for each region (matching the shifted positions in DrawRegionContent)
    // Left regions (East/Midwest): x = 5 - 0.7 = 4.3
    // Right regions (South/West): x = 4 + 3 + 0.7 = 7.7
    val sweet16LeftX = 4.3f
    val sweet16RightX = 7.7f

    // Sweet 16 Y positions: round3Positions = [12, 4] + yOffset
    // East/South: yOffset = 17, so y = 29, 21
    // Midwest/West: yOffset = 0, so y = 12, 4
    val sweet16TopPositions = listOf(
        Triple(0, sweet16LeftX, 29f),   // East top
        Triple(0, sweet16LeftX, 21f),   // East bottom
        Triple(1, sweet16RightX, 29f),  // South top
        Triple(1, sweet16RightX, 21f),  // South bottom
        Triple(2, sweet16LeftX, 12f),   // Midwest top
        Triple(2, sweet16LeftX, 4f),    // Midwest bottom
        Triple(3, sweet16RightX, 12f),  // West top
        Triple(3, sweet16RightX, 4f)    // West bottom
    )

    // Draw connector lines only when drawConnectorsOnly is true
    if (drawConnectorsOnly) {
    // Draw Sweet 16 → Elite 8 connector lines
    elite8Positions.forEach { (regionIndex, elite8X, elite8Y) ->
        val isLeftRegion = regionIndex == 0 || regionIndex == 2
        // Get the two Sweet 16 Y positions for this region
        val sweet16Games = sweet16TopPositions.filter { it.first == regionIndex }
        if (sweet16Games.size >= 2) {
            val sweet16TopY = sweet16Games[0].third
            val sweet16BottomY = sweet16Games[1].third

            // Offset to stop lines before reaching dots
            val dotOffset = 0.7f
            // Align connector x with Sweet 16 dots
            val connectorX = if (isLeftRegion) sweet16LeftX else sweet16RightX

            // Vertical connector from top Sweet 16 Y down to Elite 8 (stop short of both dots)
            LinePlot(
                data = listOf(
                    DefaultPoint(connectorX, sweet16TopY - dotOffset),
                    DefaultPoint(connectorX, elite8Y + dotOffset)
                ),
                lineStyle = connectorLineStyle
            )

            // Vertical connector from bottom Sweet 16 Y up to Elite 8 (stop short of both dots)
            LinePlot(
                data = listOf(
                    DefaultPoint(connectorX, sweet16BottomY + dotOffset),
                    DefaultPoint(connectorX, elite8Y - dotOffset)
                ),
                lineStyle = connectorLineStyle
            )
        }
    }

    // Elite 8 → Final Four horizontal connectors
    // Top row: East (left) and South (right) connect horizontally, then down to semifinal 1
    LinePlot(
        data = listOf(
            DefaultPoint(elite8LeftX + 0.5f, elite8TopY),
            DefaultPoint(elite8RightX - 0.5f, elite8TopY)
        ),
        lineStyle = connectorLineStyle
    )
    // Vertical from top Elite 8 connector to Semifinal 1
    LinePlot(
        data = listOf(
            DefaultPoint(centerX, elite8TopY),
            DefaultPoint(centerX, semifinal1Y + 0.5f)
        ),
        lineStyle = connectorLineStyle
    )

    // Bottom row: Midwest (left) and West (right) connect horizontally, then up to semifinal 2
    LinePlot(
        data = listOf(
            DefaultPoint(elite8LeftX + 0.5f, elite8BottomY),
            DefaultPoint(elite8RightX - 0.5f, elite8BottomY)
        ),
        lineStyle = connectorLineStyle
    )
    // Vertical from bottom Elite 8 connector to Semifinal 2
    LinePlot(
        data = listOf(
            DefaultPoint(centerX, elite8BottomY),
            DefaultPoint(centerX, semifinal2Y - 0.5f)
        ),
        lineStyle = connectorLineStyle
    )

    // Final Four → Championship connector lines
    val champMidY = championshipY
    // Semifinal 1 to championship
    LinePlot(
        data = listOf(
            DefaultPoint(semifinal1X, semifinal1Y - 0.5f),
            DefaultPoint(semifinal1X, champMidY)
        ),
        lineStyle = connectorLineStyle
    )
    // Semifinal 2 to championship
    LinePlot(
        data = listOf(
            DefaultPoint(semifinal2X, semifinal2Y + 0.5f),
            DefaultPoint(semifinal2X, champMidY)
        ),
        lineStyle = connectorLineStyle
    )
    // Horizontal connector to championship
    LinePlot(
        data = listOf(
            DefaultPoint(semifinal1X, champMidY),
            DefaultPoint(semifinal2X, champMidY)
        ),
        lineStyle = connectorLineStyle
    )
    } // end if (drawConnectorsOnly)

    // Draw matchup boxes only when drawConnectorsOnly is false
    if (!drawConnectorsOnly) {
    // Use transparent line style to ensure no lines are drawn between symbol points
    val noLineStyle = LineStyle(brush = SolidColor(Color.Transparent), strokeWidth = 0.dp)

    // Draw Elite 8 matchup boxes
    elite8Positions.forEach { (regionIndex, x, y) ->
        val region = regions.getOrNull(regionIndex) ?: return@forEach
        val elite8Game = region.rounds.lastOrNull()?.firstOrNull() ?: return@forEach

        LinePlot(
            data = listOf(DefaultPoint(x, y)),
            lineStyle = noLineStyle,
            symbol = {
                MatchupBoxSymbol(
                    game = elite8Game,
                    regionColor = region.color,
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    onClick = { onMatchupClick(elite8Game, region.name, "Elite 8", region.color) }
                )
            }
        )
    }

    // Draw Final Four semifinal matchup boxes
    val primaryColor = MaterialTheme.colorScheme.primary

    // Semifinal 1 (East vs South) - left in landscape, top in portrait
    LinePlot(
        data = listOf(DefaultPoint(semifinal1X, semifinal1Y)),
        lineStyle = noLineStyle,
        symbol = {
            MatchupBoxSymbol(
                game = topSemifinal,
                regionColor = primaryColor,
                textColor = textColor,
                backgroundColor = backgroundColor,
                onClick = { onMatchupClick(topSemifinal, "Final Four", "Semifinal", primaryColor) }
            )
        }
    )

    // Semifinal 2 (Midwest vs West) - right in landscape, bottom in portrait
    LinePlot(
        data = listOf(DefaultPoint(semifinal2X, semifinal2Y)),
        lineStyle = noLineStyle,
        symbol = {
            MatchupBoxSymbol(
                game = bottomSemifinal,
                regionColor = primaryColor,
                textColor = textColor,
                backgroundColor = backgroundColor,
                onClick = { onMatchupClick(bottomSemifinal, "Final Four", "Semifinal", primaryColor) }
            )
        }
    )

    // Championship game (center between semifinals)
    LinePlot(
        data = listOf(DefaultPoint(championshipX, championshipY)),
        lineStyle = noLineStyle,
        symbol = {
            MatchupBoxSymbol(
                game = championshipGame,
                regionColor = Color(0xFFFFD700), // Gold for championship
                textColor = textColor,
                backgroundColor = backgroundColor,
                onClick = { onMatchupClick(championshipGame, "Final Four", "Championship", Color(0xFFFFD700)) }
            )
        }
    )
    } // end if (!drawConnectorsOnly)
}

/**
 * Draws a single matchup box at the specified coordinates
 * @param showDetails When true, shows full matchup card. When false, shows a simple dot.
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun XYGraphScope<Float, Float>.DrawMatchupBox(
    game: BracketGame,
    x: Float,
    y: Float,
    regionColor: Color,
    textColor: Color,
    backgroundColor: Color,
    showDetails: Boolean,
    onClick: () -> Unit
) {
    // Use transparent line style to prevent any line rendering
    val noLineStyle = LineStyle(brush = SolidColor(Color.Transparent), strokeWidth = 0.dp)

    LinePlot(
        data = listOf(DefaultPoint(x, y)),
        lineStyle = noLineStyle,
        symbol = {
            if (showDetails) {
                MatchupBoxSymbol(
                    game = game,
                    regionColor = regionColor,
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    onClick = onClick
                )
            } else {
                MatchupDotSymbol(regionColor = regionColor, onClick = onClick)
            }
        }
    )
}

/**
 * Simple dot symbol for zoomed-out view
 */
@Composable
private fun MatchupDotSymbol(regionColor: Color, onClick: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(regionColor.copy(alpha = 0.7f))
            .border(1.dp, regionColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    )
}

/**
 * The visual symbol for a matchup box (detailed view)
 */
@Composable
private fun MatchupBoxSymbol(
    game: BracketGame,
    regionColor: Color,
    textColor: Color,
    backgroundColor: Color,
    onClick: () -> Unit = {}
) {
    val isDarkTheme = isSystemInDarkTheme()

    Card(
        modifier = Modifier
            .width(100.dp)
            .semantics { contentDescription = "matchup-node" }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = if (isDarkTheme) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column {
            game.team1?.let { team ->
                MatchupTeamRow(
                    team = team,
                    regionColor = regionColor,
                    textColor = textColor
                )
            } ?: EmptyMatchupRow()

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                thickness = 0.5.dp
            )

            game.team2?.let { team ->
                MatchupTeamRow(
                    team = team,
                    regionColor = regionColor,
                    textColor = textColor
                )
            } ?: EmptyMatchupRow()
        }
    }
}

@Composable
private fun MatchupTeamRow(
    team: BracketTeam,
    regionColor: Color,
    textColor: Color
) {
    val rowBackground = if (team.isWinner) {
        regionColor.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "${team.seed}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (team.isWinner) regionColor else textColor.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.width(18.dp)
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

        team.score?.let { score ->
            Text(
                text = "$score",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (team.isWinner) FontWeight.Bold else FontWeight.Normal,
                color = if (team.isWinner) regionColor else textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyMatchupRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1,
                modifier = Modifier.width(18.dp)
            )

            Text(
                text = "TBD",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}

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
 * Bottom sheet showing CBB-style matchup comparison
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchupBottomSheet(
    matchupData: MatchupSheetData,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var viewSelection by remember { mutableIntStateOf(0) }

    val gameInfo = matchupData.gameInfo
    val comparisons = gameInfo?.comparisons

    // Get full team info from gameInfo if available, otherwise use basic game data
    val team1Info = gameInfo?.team1
    val team2Info = gameInfo?.team2

    // Team names for display - use full info if available
    val team1Name = team1Info?.name ?: matchupData.game.team1?.name ?: "TBD"
    val team2Name = team2Info?.name ?: matchupData.game.team2?.name ?: "TBD"

    // Seeds for display
    val team1Seed = team1Info?.seed ?: matchupData.game.team1?.seed
    val team2Seed = team2Info?.seed ?: matchupData.game.team2?.seed

    // Team display names with seeds
    val team1Display = if (team1Seed != null && team1Name != "TBD") "($team1Seed) $team1Name" else team1Name
    val team2Display = if (team2Seed != null && team2Name != "TBD") "($team2Seed) $team2Name" else team2Name

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            // Main content area with pinned header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 36.dp)
                ) {
                    // Record section (like CBB)
                    if (team1Info != null || team2Info != null) {
                        BracketRecordSection(
                            team1 = team1Info,
                            team2 = team2Info
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Round, region, and game date info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(matchupData.regionColor, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${matchupData.roundName} • ${matchupData.regionName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Game date and time
                    gameInfo?.gameDate?.let { dateStr ->
                        val formattedDate = formatBracketGameDate(dateStr)
                        if (formattedDate != null) {
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    // Check if we have TBD teams
                    val team1 = matchupData.game.team1
                    val team2 = matchupData.game.team2
                    val hasTbdTeam = team1 == null || team2 == null

                    // Show content based on whether we have comparison data
                    if (comparisons != null && !hasTbdTeam) {
                        Spacer(modifier = Modifier.height(4.dp))

                        // View Navigation tabs (same as CBB)
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
                                onClick = { viewSelection = 0 }
                            )
                            TeamStatsNavBadge(
                                text = "$team1Name Off vs $team2Name Def",
                                isSelected = viewSelection == 1,
                                onClick = { viewSelection = 1 }
                            )
                            TeamStatsNavBadge(
                                text = "$team2Name Off vs $team1Name Def",
                                isSelected = viewSelection == 2,
                                onClick = { viewSelection = 2 }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Note: In R comparisons, home=team1, away=team2
                        // homeOffVsAwayDef = team1's offense vs team2's defense
                        // awayOffVsHomeDef = team2's offense vs team1's defense
                        when (viewSelection) {
                            0 -> BracketTeamStatsView(comparisons)
                            1 -> BracketOffenseVsDefenseView(
                                comparisons = comparisons.homeOffVsAwayDef,
                                offTeam = team1Name,
                                defTeam = team2Name
                            )
                            2 -> BracketOffenseVsDefenseView(
                                comparisons = comparisons.awayOffVsHomeDef,
                                offTeam = team2Name,
                                defTeam = team1Name
                            )
                        }
                    } else if (hasTbdTeam) {
                        // TBD matchup - show placeholder with team cards
                        Spacer(modifier = Modifier.height(12.dp))

                        if (team1 != null) {
                            MatchupSheetTeamRow(
                                team = team1,
                                regionColor = matchupData.regionColor
                            )
                        } else {
                            TbdMatchupRow()
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "vs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (team2 != null) {
                            MatchupSheetTeamRow(
                                team = team2,
                                regionColor = matchupData.regionColor
                            )
                        } else {
                            TbdMatchupRow()
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Matchup will be determined by earlier round results",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        // Both teams known but no comparison data - show team info in CBB style
                        Spacer(modifier = Modifier.height(4.dp))

                        // Still show navigation tabs structure for consistency
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TeamStatsNavBadge(
                                text = "Team",
                                isSelected = true,
                                onClick = { }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Show team info cards similar to CBB
                        MatchupSheetTeamRow(
                            team = team1!!,
                            regionColor = matchupData.regionColor
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "vs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        MatchupSheetTeamRow(
                            team = team2!!,
                            regionColor = matchupData.regionColor
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Detailed comparison stats will be available when game data is loaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Pinned header at top (overlays content)
                PinnedMatchupHeader(
                    awayTeam = team1Display,
                    homeTeam = team2Display,
                    awayScore = matchupData.game.team1?.score,
                    homeScore = matchupData.game.team2?.score,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

/**
 * Team stats view for bracket matchup - side by side comparison using FiveColumnRowWithRanks
 * Note: In comparisons, "home" = team1 and "away" = team2
 * Header displays team1 on LEFT, team2 on RIGHT
 * So we use home values for LEFT column and away values for RIGHT column
 */
@Composable
private fun BracketTeamStatsView(comparisons: com.joebad.fastbreak.data.model.MatchupComparisons) {
    Column {
        // Offensive Stats
        val offenseStats = comparisons.sideBySide?.offense
        if (!offenseStats.isNullOrEmpty()) {
            SectionHeader("Offensive Stats")
            Spacer(modifier = Modifier.height(4.dp))

            offenseStats.forEach { (key, stat) ->
                // home = team1 (left), away = team2 (right)
                val leftValue = stat.home.value
                val leftRank = stat.home.rank
                val leftRankDisplay = stat.home.rankDisplay
                val rightValue = stat.away.value
                val rightRank = stat.away.rank
                val rightRankDisplay = stat.away.rankDisplay
                val label = stat.label

                // Use rank-based advantage (lower rank is better)
                // Positive = right (team2) has advantage, Negative = left (team1) has advantage
                val advantage = if (leftRank != null && rightRank != null) {
                    when {
                        leftRank < rightRank -> -1  // team1 (left) has better rank
                        leftRank > rightRank -> 1   // team2 (right) has better rank
                        else -> 0
                    }
                } else 0

                val leftText = leftValue?.formatStat(2) ?: "-"
                val rightText = rightValue?.formatStat(2) ?: "-"

                FiveColumnRowWithRanks(
                    leftValue = leftText,
                    leftRank = leftRank,
                    leftRankDisplay = leftRankDisplay,
                    centerText = label,
                    rightValue = rightText,
                    rightRank = rightRank,
                    rightRankDisplay = rightRankDisplay,
                    advantage = advantage,
                    useCBBRanks = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Defensive Stats
        val defenseStats = comparisons.sideBySide?.defense
        if (!defenseStats.isNullOrEmpty()) {
            SectionHeader("Defensive Stats")
            Spacer(modifier = Modifier.height(4.dp))

            defenseStats.forEach { (key, stat) ->
                // home = team1 (left), away = team2 (right)
                val leftValue = stat.home.value
                val leftRank = stat.home.rank
                val leftRankDisplay = stat.home.rankDisplay
                val rightValue = stat.away.value
                val rightRank = stat.away.rank
                val rightRankDisplay = stat.away.rankDisplay
                val label = stat.label

                val advantage = if (leftRank != null && rightRank != null) {
                    when {
                        leftRank < rightRank -> -1
                        leftRank > rightRank -> 1
                        else -> 0
                    }
                } else 0

                val leftText = leftValue?.formatStat(2) ?: "-"
                val rightText = rightValue?.formatStat(2) ?: "-"

                FiveColumnRowWithRanks(
                    leftValue = leftText,
                    leftRank = leftRank,
                    leftRankDisplay = leftRankDisplay,
                    centerText = label,
                    rightValue = rightText,
                    rightRank = rightRank,
                    rightRankDisplay = rightRankDisplay,
                    advantage = advantage,
                    useCBBRanks = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Overall Stats
        val overallStats = comparisons.sideBySide?.overall
        if (!overallStats.isNullOrEmpty()) {
            SectionHeader("Overall Ratings")
            Spacer(modifier = Modifier.height(4.dp))

            overallStats.forEach { (key, stat) ->
                // home = team1 (left), away = team2 (right)
                val leftValue = stat.home.value
                val leftRank = stat.home.rank
                val leftRankDisplay = stat.home.rankDisplay
                val rightValue = stat.away.value
                val rightRank = stat.away.rank
                val rightRankDisplay = stat.away.rankDisplay
                val label = stat.label

                val advantage = if (leftRank != null && rightRank != null) {
                    when {
                        leftRank < rightRank -> -1
                        leftRank > rightRank -> 1
                        else -> 0
                    }
                } else 0

                val leftText = leftValue?.formatStat(2) ?: "-"
                val rightText = rightValue?.formatStat(2) ?: "-"

                FiveColumnRowWithRanks(
                    leftValue = leftText,
                    leftRank = leftRank,
                    leftRankDisplay = leftRankDisplay,
                    centerText = label,
                    rightValue = rightText,
                    rightRank = rightRank,
                    rightRankDisplay = rightRankDisplay,
                    advantage = advantage,
                    useCBBRanks = true
                )
            }
        }
    }
}

/**
 * Offense vs Defense view for bracket matchup
 */
@Composable
private fun BracketOffenseVsDefenseView(
    comparisons: Map<String, com.joebad.fastbreak.data.model.MatchupStatComparison>,
    offTeam: String,
    defTeam: String
) {
    if (comparisons.isEmpty()) {
        Text(
            text = "No offense vs defense comparison available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        return
    }

    SectionHeader("$offTeam Offense vs $defTeam Defense")
    Spacer(modifier = Modifier.height(4.dp))

    comparisons.forEach { (key, stat) ->
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

/**
 * Record section showing seed, record, and rankings for bracket matchup
 * Matches CBBRecordSection styling with colored AP rank indicator
 * Line 1: [colored indicator] record
 * Line 2: #N AP / #N SRS
 * Line 3: Conference
 */
@Composable
private fun BracketRecordSection(
    team1: com.joebad.fastbreak.data.model.BracketTeamInfo?,
    team2: com.joebad.fastbreak.data.model.BracketTeamInfo?
) {
    val textStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 14.sp)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    // Build ranking info for team1 (AP / SRS)
    val team1RankingParts = mutableListOf<String>()
    team1?.apRank?.let { team1RankingParts.add("#$it AP") }
    team1?.srsRank?.let { team1RankingParts.add("#$it SRS") }
    val team1RankingInfo = team1RankingParts.joinToString(" / ")

    // Build ranking info for team2 (AP / SRS)
    val team2RankingParts = mutableListOf<String>()
    team2?.apRank?.let { team2RankingParts.add("#$it AP") }
    team2?.srsRank?.let { team2RankingParts.add("#$it SRS") }
    val team2RankingInfo = team2RankingParts.joinToString(" / ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Team 1 info (left-aligned)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            // Record with AP rank color indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = getAPRankColor(team1?.apRank),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (team1?.wins != null && team1.losses != null) {
                    Text(
                        text = "${team1.wins}-${team1.losses}",
                        style = textStyle,
                        fontSize = 11.sp,
                        color = textColor
                    )
                }
            }
            // AP / SRS rankings
            if (team1RankingInfo.isNotEmpty()) {
                Text(
                    text = team1RankingInfo,
                    style = textStyle,
                    fontSize = 11.sp,
                    color = textColor
                )
            }
            // Conference
            team1?.conference?.let {
                Text(
                    text = it,
                    style = textStyle,
                    fontSize = 11.sp,
                    color = textColor
                )
            }
        }

        // Team 2 info (right-aligned)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            // Record with AP rank color indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (team2?.wins != null && team2.losses != null) {
                    Text(
                        text = "${team2.wins}-${team2.losses}",
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
                            color = getAPRankColor(team2?.apRank),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
            // AP / SRS rankings
            if (team2RankingInfo.isNotEmpty()) {
                Text(
                    text = team2RankingInfo,
                    style = textStyle,
                    fontSize = 11.sp,
                    color = textColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }
            // Conference
            team2?.conference?.let {
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

@Composable
private fun MatchupSheetTeamRow(
    team: BracketTeam,
    regionColor: Color
) {
    val rowBackground = if (team.isWinner) {
        regionColor.copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBackground)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Seed badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (team.isWinner) regionColor else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${team.seed}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (team.isWinner) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Team name
            Text(
                text = team.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (team.isWinner) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Score
        team.score?.let { score ->
            Text(
                text = "$score",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (team.isWinner) regionColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Winner indicator
        if (team.isWinner) {
            Text(
                text = "W",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = regionColor
            )
        }
    }
}

/**
 * TBD row shown when a team is not yet determined
 */
@Composable
private fun TbdMatchupRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Placeholder seed badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "To Be Determined",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

// Generate bracket data - order: East, South, Midwest, West (for 2x2 layout)
private fun generateBracketRegions(): List<BracketRegion> {
    return listOf(
        createRegion("East", Color(0xFF1565C0), eastTeams),
        createRegion("South", Color(0xFF2E7D32), southTeams),
        createRegion("Midwest", Color(0xFFF57F17), midwestTeams),
        createRegion("West", Color(0xFFC62828), westTeams)
    )
}

private fun createRegion(name: String, color: Color, teams: List<Pair<Int, String>>): BracketRegion {
    val round1 = mutableListOf<BracketGame>()
    val round1Winners = mutableListOf<BracketTeam>()

    for (i in 0 until 8) {
        val t1 = teams[i * 2]
        val t2 = teams[i * 2 + 1]
        val score1 = (65..85).random()
        val score2 = (55..75).random()
        val team1Wins = t1.first < t2.first || (i == 2 || i == 5)

        val team1 = BracketTeam(t1.first, t1.second, score1, team1Wins)
        val team2 = BracketTeam(t2.first, t2.second, score2, !team1Wins)

        round1.add(BracketGame(team1, team2, i + 1))
        round1Winners.add(if (team1Wins) team1.copy(score = null, isWinner = false) else team2.copy(score = null, isWinner = false))
    }

    val round2 = mutableListOf<BracketGame>()
    val round2Winners = mutableListOf<BracketTeam>()

    for (i in 0 until 4) {
        val t1 = round1Winners[i * 2]
        val t2 = round1Winners[i * 2 + 1]
        val score1 = (60..80).random()
        val score2 = (55..75).random()
        val team1Wins = t1.seed < t2.seed

        val team1 = t1.copy(score = score1, isWinner = team1Wins)
        val team2 = t2.copy(score = score2, isWinner = !team1Wins)

        round2.add(BracketGame(team1, team2, i + 1))
        round2Winners.add(if (team1Wins) team1.copy(score = null, isWinner = false) else team2.copy(score = null, isWinner = false))
    }

    val round3 = mutableListOf<BracketGame>()
    val round3Winners = mutableListOf<BracketTeam>()

    for (i in 0 until 2) {
        val t1 = round2Winners[i * 2]
        val t2 = round2Winners[i * 2 + 1]
        val score1 = (55..75).random()
        val score2 = (50..70).random()
        val team1Wins = t1.seed < t2.seed

        val team1 = t1.copy(score = score1, isWinner = team1Wins)
        val team2 = t2.copy(score = score2, isWinner = !team1Wins)

        round3.add(BracketGame(team1, team2, i + 1))
        round3Winners.add(if (team1Wins) team1.copy(score = null, isWinner = false) else team2.copy(score = null, isWinner = false))
    }

    val t1 = round3Winners[0]
    val t2 = round3Winners[1]
    val score1 = (50..70).random()
    val score2 = (45..65).random()
    val team1Wins = t1.seed < t2.seed

    val elite8 = listOf(
        BracketGame(
            t1.copy(score = score1, isWinner = team1Wins),
            t2.copy(score = score2, isWinner = !team1Wins),
            1
        )
    )

    return BracketRegion(
        name = name,
        color = color,
        rounds = listOf(round1, round2, round3, elite8)
    )
}

private val eastTeams = listOf(
    1 to "UConn", 16 to "Stetson",
    8 to "FAU", 9 to "Northwestern",
    5 to "San Diego St", 12 to "UAB",
    4 to "Auburn", 13 to "Yale",
    6 to "BYU", 11 to "Duquesne",
    3 to "Illinois", 14 to "Morehead St",
    7 to "Washington St", 10 to "Drake",
    2 to "Iowa St", 15 to "S Dakota St"
)

private val southTeams = listOf(
    1 to "Houston", 16 to "Longwood",
    8 to "Nebraska", 9 to "Texas A&M",
    5 to "Wisconsin", 12 to "Jms Madison",
    4 to "Duke", 13 to "Vermont",
    6 to "Texas Tech", 11 to "NC State",
    3 to "Kentucky", 14 to "Oakland",
    7 to "Florida", 10 to "Colorado",
    2 to "Marquette", 15 to "W Kentucky"
)

private val midwestTeams = listOf(
    1 to "Purdue", 16 to "Grambling",
    8 to "Utah St", 9 to "TCU",
    5 to "Gonzaga", 12 to "McNeese",
    4 to "Kansas", 13 to "Samford",
    6 to "S Carolina", 11 to "Oregon",
    3 to "Creighton", 14 to "Akron",
    7 to "Texas", 10 to "Colorado St",
    2 to "Tennessee", 15 to "St Peter's"
)

private val westTeams = listOf(
    1 to "N Carolina", 16 to "Wagner",
    8 to "Miss St", 9 to "Michigan St",
    5 to "St Mary's", 12 to "Gr Canyon",
    4 to "Alabama", 13 to "Charleston",
    6 to "Clemson", 11 to "New Mexico",
    3 to "Baylor", 14 to "Colgate",
    7 to "Dayton", 10 to "Nevada",
    2 to "Arizona", 15 to "Long Beach"
)

// Region color mapping
private val regionColors = mapOf(
    "East" to Color(0xFF1565C0),
    "South" to Color(0xFF2E7D32),
    "Midwest" to Color(0xFFF57F17),
    "West" to Color(0xFFC62828)
)

/**
 * Parses a hex color string to a Compose Color
 */
private fun parseHexColor(hexColor: String): Color? {
    return try {
        val hex = hexColor.removePrefix("#")
        when (hex.length) {
            6 -> {
                val r = hex.substring(0, 2).toInt(16)
                val g = hex.substring(2, 4).toInt(16)
                val b = hex.substring(4, 6).toInt(16)
                Color(r, g, b)
            }
            8 -> {
                val a = hex.substring(0, 2).toInt(16)
                val r = hex.substring(2, 4).toInt(16)
                val g = hex.substring(4, 6).toInt(16)
                val b = hex.substring(6, 8).toInt(16)
                Color(r, g, b, a)
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Helper to check if a team represents TBD (not yet determined)
 */
private fun isTbdTeam(team: com.joebad.fastbreak.data.model.BracketTeamInfo?): Boolean {
    if (team == null) return true
    if (team.name.equals("TBD", ignoreCase = true)) return true
    if (team.name.isBlank()) return true
    if (team.seed <= 0) return true
    return false
}

/**
 * Converts a BracketRegionInfo from the visualization to the local BracketRegion type
 * Ensures all 4 rounds exist (Round of 64, Round of 32, Sweet 16, Elite 8) with TBD placeholders
 */
private fun convertRegionInfoToBracketRegion(regionInfo: BracketRegionInfo): BracketRegion? {
    val color = parseHexColor(regionInfo.colorHex) ?: regionColors[regionInfo.name] ?: Color.Gray

    // Expected games per round: Round 1 = 8, Round 2 = 4, Sweet 16 = 2, Elite 8 = 1
    val expectedGamesPerRound = listOf(8, 4, 2, 1)

    // Build all 4 rounds, creating TBD placeholders for missing rounds/games
    val rounds = (0 until 4).map { roundIndex ->
        val roundInfo = regionInfo.rounds.getOrNull(roundIndex)
        val expectedGames = expectedGamesPerRound[roundIndex]

        if (roundInfo == null || roundInfo.games.isEmpty()) {
            // No data for this round - create TBD placeholder games
            (0 until expectedGames).map { gameIndex ->
                BracketGame(
                    team1 = null,
                    team2 = null,
                    gameNumber = roundIndex * 100 + gameIndex + 1  // Unique game numbers
                )
            }
        } else {
            // Process existing games from data
            val games = roundInfo.games.mapIndexed { index, gameInfo ->
                // Check game status to determine if we should show winner highlighting
                val gameStatus = gameInfo.gameStatus?.uppercase() ?: ""
                val isGameDecided = gameStatus == "FINAL" || gameStatus == "IN_PROGRESS"

                // Show team if data exists, otherwise TBD
                // For scheduled games, show teams but without winner highlighting
                val team1 = if (isTbdTeam(gameInfo.team1)) {
                    null
                } else {
                    gameInfo.team1?.let { t ->
                        BracketTeam(
                            seed = t.seed,
                            name = t.name,
                            score = t.score,
                            // Only show winner for decided games
                            isWinner = isGameDecided && (gameInfo.winner == t.name || (t.score != null && gameInfo.team2?.score != null && t.score > gameInfo.team2.score))
                        )
                    }
                }

                val team2 = if (isTbdTeam(gameInfo.team2)) {
                    null
                } else {
                    gameInfo.team2?.let { t ->
                        BracketTeam(
                            seed = t.seed,
                            name = t.name,
                            score = t.score,
                            // Only show winner for decided games
                            isWinner = isGameDecided && (gameInfo.winner == t.name || (t.score != null && gameInfo.team1?.score != null && t.score > gameInfo.team1.score))
                        )
                    }
                }

                BracketGame(
                    team1 = team1,
                    team2 = team2,
                    gameNumber = gameInfo.gameNumber ?: (index + 1),
                    sourceGameInfo = gameInfo
                )
            }

            // Pad with TBD games if we have fewer games than expected
            val paddedGames = if (games.size < expectedGames) {
                games + (games.size until expectedGames).map { gameIndex ->
                    BracketGame(
                        team1 = null,
                        team2 = null,
                        gameNumber = roundIndex * 100 + gameIndex + 1
                    )
                }
            } else {
                games
            }

            // For Round 1 only, sort games so #1 seed matchup appears first
            if (roundIndex == 0) {
                paddedGames.sortedBy { game ->
                    val seed1 = game.team1?.seed ?: 99
                    val seed2 = game.team2?.seed ?: 99
                    minOf(seed1, seed2)
                }
            } else {
                paddedGames
            }
        }
    }

    return BracketRegion(
        name = regionInfo.name,
        color = color,
        rounds = rounds
    )
}

/**
 * Format a bracket game date string (ISO 8601) into a readable date and time.
 * Returns null if the date can't be parsed.
 */
private fun formatBracketGameDate(gameDate: String): String? {
    return try {
        val instant = Instant.parse(gameDate)
        val eastern = TimeZone.of("America/New_York")
        val dt = instant.toLocalDateTime(eastern)
        val month = dt.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        val day = dt.dayOfMonth
        val hour = if (dt.hour % 12 == 0) 12 else dt.hour % 12
        val minute = dt.minute.toString().padStart(2, '0')
        val amPm = if (dt.hour < 12) "AM" else "PM"
        "$month $day · $hour:$minute $amPm ET"
    } catch (_: Exception) {
        null
    }
}

