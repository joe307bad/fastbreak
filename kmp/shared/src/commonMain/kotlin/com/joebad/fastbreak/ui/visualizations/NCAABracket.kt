package com.joebad.fastbreak.ui.visualizations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.koalaplot.core.gestures.GestureConfig
import io.github.koalaplot.core.line.LinePlot
import io.github.koalaplot.core.style.LineStyle
import io.github.koalaplot.core.util.ExperimentalKoalaPlotApi
import io.github.koalaplot.core.xygraph.DefaultPoint
import io.github.koalaplot.core.xygraph.FloatLinearAxisModel
import io.github.koalaplot.core.xygraph.XYGraph
import io.github.koalaplot.core.xygraph.XYGraphScope
import io.github.koalaplot.core.xygraph.rememberAxisStyle

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
    val gameNumber: Int
)

data class BracketRegion(
    val name: String,
    val color: Color,
    val rounds: List<List<BracketGame>>
)

data class MatchupSheetData(
    val game: BracketGame,
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
    modifier: Modifier = Modifier,
    onNavigationToggleHandlerChanged: ((BracketNavigationToggleHandler?) -> Unit)? = null
) {
    val regions = remember { generateBracketRegions() }
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

        Column(modifier = Modifier.fillMaxSize()) {
            // Collapsible bracket navigation (toggle controlled from top app bar)
            AnimatedVisibility(
                visible = isNavigationExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MiniMapNavigation(
                        regions = regions,
                        currentIndex = currentQuadrant,
                        onRegionClick = { index -> currentQuadrant = index },
                        onFinalFourClick = { currentQuadrant = 4 },
                        isFinalFourSelected = currentQuadrant == 4
                    )
                }
            }

            // Single unified bracket visualization
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
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                // Calculate zoom level from current view extent
                // When zoomed in (extent ~8), show details. When zoomed out (extent ~13), show dots.
                val currentXRange = xAxisModel.viewRange.value
                val currentXExtent = currentXRange.endInclusive - currentXRange.start
                // Threshold: show details when viewing roughly one quadrant or less
                // Default quadrant extent is 8, so use 9.5 to give margin for panning
                val isZoomedIn = currentXExtent <= 9.5f

                // Draw all four regions
                // Show full details when zoomed in, dots when zoomed out
                regions.forEachIndexed { index, region ->
                    val isReversed = index == 1 || index == 3  // South and West are reversed
                    val xOffset = if (index == 1 || index == 3) 4f else 0f
                    val yOffset = if (index == 0 || index == 1) 17f else 0f

                    DrawRegionContent(
                        region = region,
                        regionName = region.name,
                        isReversed = isReversed,
                        xOffset = xOffset,
                        yOffset = yOffset,
                        showDetails = isZoomedIn,
                        lineColor = lineColor,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        onMatchupClick = { game, roundName ->
                            selectedMatchup = MatchupSheetData(game, region.name, roundName, region.color)
                        }
                    )
                }

                // Draw Final Four connector lines, Elite 8, semifinals, and championship
                DrawFinalFourConnectors(
                    regions = regions,
                    lineColor = lineColor,
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    isLandscape = isLandscape,
                    onMatchupClick = { game, regionName, roundName, color ->
                        selectedMatchup = MatchupSheetData(game, regionName, roundName, color)
                    }
                )
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
                region = regions[0],
                isSelected = currentIndex == 0,
                onClick = { onRegionClick(0) }
            )
            MiniRegionBox(
                region = regions[2],
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
                region = regions[1],
                isSelected = currentIndex == 1,
                onClick = { onRegionClick(1) }
            )
            MiniRegionBox(
                region = regions[3],
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
    val round1Positions = listOf(1f, 3f, 5f, 7f, 9f, 11f, 13f, 15f)
    val round2Positions = listOf(2f, 6f, 10f, 14f)
    val round3Positions = listOf(4f, 12f)
    val round4Positions = listOf(8f)

    val allPositions = listOf(round1Positions, round2Positions, round3Positions, round4Positions)

    // Draw connecting lines between rounds
    for (roundIndex in 0 until 3) {
        val currentPositions = allPositions[roundIndex]
        val nextPositions = allPositions[roundIndex + 1]

        // For reversed regions, rounds go 3,2,1,0 instead of 0,1,2,3
        // Use ROUND_X_POSITIONS for proper spacing (0, 1.5, 3, 4.5)
        val currentX = xOffset + if (isReversed) ROUND_X_POSITIONS[3 - roundIndex] else ROUND_X_POSITIONS[roundIndex]
        val nextX = xOffset + if (isReversed) ROUND_X_POSITIONS[2 - roundIndex] else ROUND_X_POSITIONS[roundIndex + 1]

        for (i in nextPositions.indices) {
            val topY = yOffset + currentPositions[i * 2]
            val bottomY = yOffset + currentPositions[i * 2 + 1]
            val midY = yOffset + nextPositions[i]

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
        val positions = allPositions[roundIndex]
        val x = xOffset + if (isReversed) ROUND_X_POSITIONS[3 - roundIndex] else ROUND_X_POSITIONS[roundIndex]
        val roundName = roundNames[roundIndex]

        games.forEachIndexed { gameIndex, game ->
            val y = yOffset + positions[gameIndex]
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
 */
@OptIn(ExperimentalKoalaPlotApi::class)
@Composable
private fun XYGraphScope<Float, Float>.DrawFinalFourConnectors(
    regions: List<BracketRegion>,
    lineColor: Color,
    textColor: Color,
    backgroundColor: Color,
    isLandscape: Boolean,
    onMatchupClick: (BracketGame, String, String, Color) -> Unit
) {
    // Elite 8 positions (now separated - left regions at x=7, right regions at x=5)
    // - East: x=7 (0 + ROUND_X_POSITIONS[3]), y=25 (17 + 8)
    // - South: x=5 (4 + ROUND_X_POSITIONS[0]), y=25
    // - Midwest: x=7, y=8 (0 + 8)
    // - West: x=5, y=8

    val centerX = 6f
    val topY = 25f   // y position for East/South Elite 8 (portrait)
    val bottomY = 8f // y position for Midwest/West Elite 8 (portrait)

    // Elite 8 matchup positions (region index to position)
    // Portrait: stacked vertically in pairs at center
    // Landscape: spread horizontally, positioned in the center of Sweet 16 area (same Y as portrait)
    val elite8Positions = if (isLandscape) {
        // Landscape: Elite 8 spread horizontally at Sweet 16 center positions
        // Top row at Y=25 (center of top Sweet 16), bottom row at Y=8 (center of bottom Sweet 16)
        listOf(
            Triple(0, 4.5f, topY),      // East - left, in top Sweet 16 center
            Triple(1, 7.5f, topY),      // South - right, in top Sweet 16 center
            Triple(2, 4.5f, bottomY),   // Midwest - left, in bottom Sweet 16 center
            Triple(3, 7.5f, bottomY)    // West - right, in bottom Sweet 16 center
        )
    } else {
        // Portrait: stacked vertically at center in pairs
        listOf(
            Triple(0, centerX, topY + 2f),      // East (top pair, upper)
            Triple(1, centerX, topY - 2f),      // South (top pair, lower)
            Triple(2, centerX, bottomY + 2f),   // Midwest (bottom pair, upper)
            Triple(3, centerX, bottomY - 2f)    // West (bottom pair, lower)
        )
    }

    // Final Four positions - different layout for portrait vs landscape
    // Portrait: stacked vertically with championship in center
    // Landscape: arranged horizontally in the center (between Elite 8 rows)
    val finalFourY = 16.5f  // Center Y position for all Final Four games

    val semifinal1X = if (isLandscape) 4.5f else centerX   // Left semifinal
    val semifinal1Y = if (isLandscape) finalFourY else 19f
    val semifinal2X = if (isLandscape) 7.5f else centerX   // Right semifinal
    val semifinal2Y = if (isLandscape) finalFourY else 14f
    val championshipX = centerX  // Always at center (6.0)
    val championshipY = 16.5f

    // Create Final Four semifinal games from Elite 8 winners
    val eastElite8 = regions[0].rounds.last().first()
    val southElite8 = regions[1].rounds.last().first()
    val midwestElite8 = regions[2].rounds.last().first()
    val westElite8 = regions[3].rounds.last().first()

    // Get winners from Elite 8 games
    val eastWinner = if (eastElite8.team1?.isWinner == true) eastElite8.team1 else eastElite8.team2
    val southWinner = if (southElite8.team1?.isWinner == true) southElite8.team1 else southElite8.team2
    val midwestWinner = if (midwestElite8.team1?.isWinner == true) midwestElite8.team1 else midwestElite8.team2
    val westWinner = if (westElite8.team1?.isWinner == true) westElite8.team1 else westElite8.team2

    // Final Four semifinal games
    val topSemifinal = BracketGame(
        team1 = eastWinner?.copy(score = 72, isWinner = true),
        team2 = southWinner?.copy(score = 68, isWinner = false),
        gameNumber = 1
    )
    val bottomSemifinal = BracketGame(
        team1 = midwestWinner?.copy(score = 75, isWinner = true),
        team2 = westWinner?.copy(score = 70, isWinner = false),
        gameNumber = 2
    )

    // Championship game from semifinal winners
    val topSemifinalWinner = if (topSemifinal.team1?.isWinner == true) topSemifinal.team1 else topSemifinal.team2
    val bottomSemifinalWinner = if (bottomSemifinal.team1?.isWinner == true) bottomSemifinal.team1 else bottomSemifinal.team2

    val championshipGame = BracketGame(
        team1 = topSemifinalWinner?.copy(score = 68, isWinner = true),
        team2 = bottomSemifinalWinner?.copy(score = 64, isWinner = false),
        gameNumber = 1
    )

    // Draw connector lines FIRST (so matchup boxes appear on top)
    if (isLandscape) {
        // Landscape: Elite 8 in center of Sweet 16 areas, Final Four in center
        // Top row (Y=25): East(4.5) - South(7.5) in Sweet 16 center
        // Middle (Y=16.5): Semi1(4.5) - Champ(6) - Semi2(7.5)
        // Bottom row (Y=8): Midwest(4.5) - West(7.5) in Sweet 16 center

        // Horizontal line connecting top Elite 8 pair (East - South)
        LinePlot(
            data = listOf(
                DefaultPoint(4.0f, topY),
                DefaultPoint(8.0f, topY)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )

        // Horizontal line connecting bottom Elite 8 pair (Midwest - West)
        LinePlot(
            data = listOf(
                DefaultPoint(4.0f, bottomY),
                DefaultPoint(8.0f, bottomY)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )

        // Vertical connector from top Elite 8 row down to Final Four
        LinePlot(
            data = listOf(
                DefaultPoint(centerX, topY),
                DefaultPoint(centerX, finalFourY)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )

        // Vertical connector from bottom Elite 8 row up to Final Four
        LinePlot(
            data = listOf(
                DefaultPoint(centerX, bottomY),
                DefaultPoint(centerX, finalFourY)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )

        // Horizontal line connecting Final Four (Semi1 - Champ - Semi2)
        LinePlot(
            data = listOf(
                DefaultPoint(semifinal1X, finalFourY),
                DefaultPoint(semifinal2X, finalFourY)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )
    } else {
        // Portrait: Elite 8 pairs stacked vertically at center
        // Vertical line connecting East to South (top pair)
        LinePlot(
            data = listOf(
                DefaultPoint(centerX, topY + 2f),
                DefaultPoint(centerX, topY - 2f)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )

        // Vertical line connecting Midwest to West (bottom pair)
        LinePlot(
            data = listOf(
                DefaultPoint(centerX, bottomY + 2f),
                DefaultPoint(centerX, bottomY - 2f)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )

        // Connector from top Elite 8 pair to top semifinal
        LinePlot(
            data = listOf(
                DefaultPoint(centerX, topY - 2f),
                DefaultPoint(centerX, semifinal1Y)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )

        // Connector from bottom Elite 8 pair to bottom semifinal
        LinePlot(
            data = listOf(
                DefaultPoint(centerX, bottomY + 2f),
                DefaultPoint(centerX, semifinal2Y)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )

        // Championship connector (vertical line between the two Final Four semifinals)
        LinePlot(
            data = listOf(
                DefaultPoint(centerX, semifinal2Y),
                DefaultPoint(centerX, championshipY)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )
        LinePlot(
            data = listOf(
                DefaultPoint(centerX, championshipY),
                DefaultPoint(centerX, semifinal1Y)
            ),
            lineStyle = LineStyle(
                brush = SolidColor(lineColor),
                strokeWidth = 2.dp
            )
        )
    }

    // Draw Elite 8 matchup boxes AFTER lines (so they appear on top)
    elite8Positions.forEach { (regionIndex, x, y) ->
        val region = regions[regionIndex]
        val elite8Game = region.rounds.last().first() // Elite 8 is the last round (round 4)

        LinePlot(
            data = listOf(DefaultPoint(x, y)),
            lineStyle = null,
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
        lineStyle = null,
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
        lineStyle = null,
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
        lineStyle = null,
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
    LinePlot(
        data = listOf(DefaultPoint(x, y)),
        lineStyle = null,
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
    Card(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .height(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "TBD",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

/**
 * Bottom sheet showing matchup details
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchupBottomSheet(
    matchupData: MatchupSheetData,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = matchupData.roundName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = matchupData.regionColor
                    )
                    Text(
                        text = matchupData.regionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Game #${matchupData.game.gameNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Team 1
            matchupData.game.team1?.let { team ->
                MatchupSheetTeamRow(
                    team = team,
                    regionColor = matchupData.regionColor
                )
            }

            // VS divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "vs",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Team 2
            matchupData.game.team2?.let { team ->
                MatchupSheetTeamRow(
                    team = team,
                    regionColor = matchupData.regionColor
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
