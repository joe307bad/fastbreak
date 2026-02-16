package com.joebad.fastbreak.ui.visualizations

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NCAABracket(
    modifier: Modifier = Modifier
) {
    // Regions arranged as 2x2 grid:
    // Row 0: East (0), South (1)
    // Row 1: Midwest (2), West (3)
    val regions = remember { generateBracketRegions() }
    val scope = rememberCoroutineScope()

    // Vertical pager for rows (0 = top row, 1 = bottom row)
    val verticalPagerState = rememberPagerState(pageCount = { 2 })
    // Horizontal pager states for each row
    val horizontalPagerStateRow0 = rememberPagerState(pageCount = { 2 })
    val horizontalPagerStateRow1 = rememberPagerState(pageCount = { 2 })

    // Keep horizontal pagers in sync - when one changes, update the other
    LaunchedEffect(horizontalPagerStateRow0.currentPage) {
        if (horizontalPagerStateRow1.currentPage != horizontalPagerStateRow0.currentPage) {
            horizontalPagerStateRow1.scrollToPage(horizontalPagerStateRow0.currentPage)
        }
    }

    LaunchedEffect(horizontalPagerStateRow1.currentPage) {
        if (horizontalPagerStateRow0.currentPage != horizontalPagerStateRow1.currentPage) {
            horizontalPagerStateRow0.scrollToPage(horizontalPagerStateRow1.currentPage)
        }
    }

    // Calculate current region index based on both pagers
    val currentRegionIndex by remember {
        derivedStateOf {
            val row = verticalPagerState.currentPage
            val col = if (row == 0) horizontalPagerStateRow0.currentPage else horizontalPagerStateRow1.currentPage
            row * 2 + col
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Mini bracket map navigation
        MiniMapNavigation(
            regions = regions,
            currentIndex = currentRegionIndex,
            onRegionClick = { index ->
                scope.launch {
                    val targetRow = index / 2
                    val targetCol = index % 2

                    // Navigate to the correct row and column (both pagers will sync)
                    verticalPagerState.scrollToPage(targetRow)
                    horizontalPagerStateRow0.scrollToPage(targetCol)
                    horizontalPagerStateRow1.scrollToPage(targetCol)
                }
            },
            modifier = Modifier.padding(16.dp)
        )

        // 2D swipeable grid: Vertical pager containing horizontal pagers
        VerticalPager(
            state = verticalPagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = verticalPagerState,
                snapAnimationSpec = snap()
            )
        ) { row ->
            val horizontalPagerState = if (row == 0) horizontalPagerStateRow0 else horizontalPagerStateRow1

            HorizontalPager(
                state = horizontalPagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = horizontalPagerState,
                    snapAnimationSpec = snap()
                )
            ) { col ->
                val regionIndex = row * 2 + col
                // South (index 1) and West (index 3) should be reversed (right to left)
                val isReversed = regionIndex == 1 || regionIndex == 3

                BracketRegionView(
                    region = regions[regionIndex],
                    isReversed = isReversed,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun MiniMapNavigation(
    regions: List<BracketRegion>,
    currentIndex: Int,
    onRegionClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 2x2 grid layout matching bracket structure
    // Top: East (0), South (1)
    // Bottom: Midwest (2), West (3)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "NCAA Tournament",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier.width(200.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left column: East (top), Midwest (bottom)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MiniRegionBox(
                    region = regions[0], // East
                    isSelected = currentIndex == 0,
                    onClick = { onRegionClick(0) }
                )
                MiniRegionBox(
                    region = regions[2], // Midwest
                    isSelected = currentIndex == 2,
                    onClick = { onRegionClick(2) }
                )
            }

            // Center: Final Four indicator
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "F4",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Right column: South (top), West (bottom)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MiniRegionBox(
                    region = regions[1], // South
                    isSelected = currentIndex == 1,
                    onClick = { onRegionClick(1) }
                )
                MiniRegionBox(
                    region = regions[3], // West
                    isSelected = currentIndex == 3,
                    onClick = { onRegionClick(3) }
                )
            }
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
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = region.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) region.color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BracketRegionView(
    region: BracketRegion,
    isReversed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 8.dp)
    ) {
        // Region header
        Text(
            text = "${region.name} Region",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = region.color,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )

        // Bracket layout - horizontal scroll for wide bracket
        val horizontalScrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScrollState)
        ) {
            // For reversed regions (South, West), display rounds in reverse order
            val displayRounds = if (isReversed) region.rounds.reversed() else region.rounds

            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                displayRounds.forEachIndexed { displayIndex, games ->
                    // Calculate the actual round index for spacing calculations
                    val actualRoundIndex = if (isReversed) {
                        region.rounds.lastIndex - displayIndex
                    } else {
                        displayIndex
                    }

                    BracketRound(
                        roundIndex = actualRoundIndex,
                        games = games,
                        regionColor = region.color,
                        isLastRound = if (isReversed) displayIndex == 0 else displayIndex == region.rounds.lastIndex,
                        isReversed = isReversed
                    )
                }
            }
        }
    }
}

@Composable
private fun BracketRound(
    roundIndex: Int,
    games: List<BracketGame>,
    regionColor: Color,
    isLastRound: Boolean,
    isReversed: Boolean = false
) {
    val roundNames = listOf("Round of 64", "Round of 32", "Sweet 16", "Elite 8")
    val roundName = roundNames.getOrElse(roundIndex) { "Round ${roundIndex + 1}" }

    // Calculate spacing - increases with each round to align bracket lines
    val verticalSpacing = when (roundIndex) {
        0 -> 4.dp
        1 -> 36.dp
        2 -> 100.dp
        3 -> 228.dp
        else -> 4.dp
    }

    val topPadding = when (roundIndex) {
        0 -> 0.dp
        1 -> 20.dp
        2 -> 52.dp
        3 -> 116.dp
        else -> 0.dp
    }

    Column(
        modifier = Modifier.width(140.dp)
    ) {
        // Round label
        Text(
            text = roundName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = if (isReversed) 0.dp else 4.dp, end = if (isReversed) 4.dp else 0.dp, bottom = 8.dp)
        )

        Column(
            modifier = Modifier.padding(top = topPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            games.forEach { game ->
                BracketMatchup(
                    game = game,
                    regionColor = regionColor,
                    showConnector = !isLastRound,
                    isReversed = isReversed
                )
            }
        }
    }
}

@Composable
private fun BracketMatchup(
    game: BracketGame,
    regionColor: Color,
    showConnector: Boolean,
    isReversed: Boolean = false
) {
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Connector line on left (for reversed regions)
        if (showConnector && isReversed) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(64.dp)
                    .drawBehind {
                        val strokeWidth = 2.dp.toPx()
                        val centerY = size.height / 2

                        drawLine(
                            color = lineColor,
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = strokeWidth
                        )
                    }
            )
        }

        // Matchup card
        Card(
            modifier = Modifier.width(120.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column {
                game.team1?.let { team ->
                    BracketTeamRow(
                        team = team,
                        regionColor = regionColor
                    )
                } ?: EmptyTeamRow()

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    thickness = 1.dp
                )

                game.team2?.let { team ->
                    BracketTeamRow(
                        team = team,
                        regionColor = regionColor
                    )
                } ?: EmptyTeamRow()
            }
        }

        // Connector line on right (for normal regions)
        if (showConnector && !isReversed) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(64.dp)
                    .drawBehind {
                        val strokeWidth = 2.dp.toPx()
                        val centerY = size.height / 2

                        drawLine(
                            color = lineColor,
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = strokeWidth
                        )
                    }
            )
        }
    }
}

@Composable
private fun BracketTeamRow(
    team: BracketTeam,
    regionColor: Color
) {
    val backgroundColor = if (team.isWinner) {
        regionColor.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 6.dp, vertical = 6.dp),
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
                color = if (team.isWinner) regionColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(16.dp)
            )

            Text(
                text = team.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (team.isWinner) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        team.score?.let { score ->
            Text(
                text = "$score",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (team.isWinner) FontWeight.Bold else FontWeight.Normal,
                color = if (team.isWinner) regionColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyTeamRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .height(20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "TBD",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
