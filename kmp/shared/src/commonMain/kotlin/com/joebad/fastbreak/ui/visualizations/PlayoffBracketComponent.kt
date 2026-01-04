package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*
import kotlin.math.absoluteValue
import kotlin.math.sign

@Composable
fun PlayoffBracketComponent(
    visualization: PlayoffBracketVisualization,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    var selectedMatchup by remember { mutableStateOf<PlayoffMatchup?>(null) }

    // Custom snap layout info provider with ultra-sensitive snapping
    val snapLayoutInfoProvider = remember(lazyListState) {
        object : SnapLayoutInfoProvider {
            override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float {
                return 0f // Instant snapping, no approach animation
            }

            override fun calculateSnapOffset(velocity: Float): Float {
                val layoutInfo = lazyListState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo

                if (visibleItems.isEmpty()) return 0f

                val viewportStart = layoutInfo.viewportStartOffset
                val snapOffset = 30f // Snap 30px before the left edge

                // Find the item closest to the snap position
                val closestItem = visibleItems.minByOrNull {
                    (it.offset - viewportStart - snapOffset).absoluteValue
                } ?: return 0f

                val distanceFromSnap = closestItem.offset - viewportStart - snapOffset

                // Ultra-sensitive threshold: if scrolled more than 50px in either direction, snap to next/prev
                val snapThreshold = 50f

                val targetItem = when {
                    // If we've scrolled left more than threshold, snap to next item
                    distanceFromSnap < -snapThreshold -> {
                        visibleItems.find { it.index == closestItem.index + 1 } ?: closestItem
                    }
                    // If we've scrolled right more than threshold, snap to previous item
                    distanceFromSnap > snapThreshold -> {
                        visibleItems.findLast { it.index == closestItem.index - 1 } ?: closestItem
                    }
                    // Otherwise snap to closest
                    else -> closestItem
                }

                return (targetItem.offset - viewportStart - snapOffset)
            }
        }
    }

    val snapBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider = snapLayoutInfoProvider)

    // Horizontally scrollable bracket with snap behavior and vertical scrolling support
    LazyRow(
        modifier = modifier.fillMaxSize(),
        state = lazyListState,
        flingBehavior = snapBehavior,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
        userScrollEnabled = true // Enable user scrolling
    ) {
        itemsIndexed(visualization.rounds) { roundIndex, round ->
            RoundColumn(
                round = round,
                roundIndex = roundIndex,
                isLastRound = roundIndex == visualization.rounds.size - 1,
                onMatchupClick = { matchup ->
                    selectedMatchup = matchup
                }
            )
        }
    }

    // TODO: MatchupAnalyticsSheet was removed
    // Show analytics bottom sheet when a matchup is selected
    // selectedMatchup?.let { matchup ->
    //     if (matchup.team1 != null && matchup.team2 != null) {
    //         val analytics = generateMatchupAnalytics(matchup)
    //         MatchupAnalyticsSheet(
    //             analytics = analytics,
    //             onDismiss = { selectedMatchup = null }
    //         )
    //     }
    // }
}

@Composable
private fun RoundColumn(
    round: PlayoffRound,
    roundIndex: Int,
    isLastRound: Boolean,
    onMatchupClick: (PlayoffMatchup) -> Unit,
    modifier: Modifier = Modifier
) {
    val matchupCardHeight = 80.dp
    val verticalSpacing = 12.dp
    val roundWidth = 220.dp
    val horizontalSpacing = 40.dp
    val conferenceDividerSpacing = 32.dp

    // Calculate vertical spacing multiplier for this round
    // Each round has double the spacing of the previous round
    val spacingMultiplier = (1 shl roundIndex).toFloat() // 1, 2, 4, 8...

    // Group matchups by conference
    val conferences = round.matchups
        .mapNotNull { it.team1?.conference ?: it.team2?.conference }
        .distinct()
        .sorted()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .width(roundWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(end = 16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            // Round label
            Text(
                text = round.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )

            conferences.forEachIndexed { confIndex, conference ->
                // Conference label
                Text(
                    text = conference,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )

                // Get matchups for this conference
                val conferenceMatchups = round.matchups.filter { matchup ->
                    matchup.team1?.conference == conference || matchup.team2?.conference == conference
                }

                // Matchups with increasing vertical spacing
                conferenceMatchups.forEachIndexed { matchupIndex, matchup ->
                    // Show teams only for first round
                    // For second round, only show top seeds (first matchup in each conference has the bye team)
                    // For later rounds, show blank
                    val displayMatchup = if (roundIndex == 0) {
                        matchup
                    } else if (roundIndex == 1) {
                        // Only show top seed (team1) for the first matchup in each conference
                        // All other positions should be TBD
                        if (matchupIndex == 0 && matchup.team1 != null) {
                            // Keep the top seed, set team2 to null
                            PlayoffMatchup(team1 = matchup.team1, team2 = null, winner = null)
                        } else {
                            PlayoffMatchup(team1 = null, team2 = null, winner = null)
                        }
                    } else {
                        PlayoffMatchup(team1 = null, team2 = null, winner = null)
                    }

                    MatchupCard(
                        matchup = displayMatchup,
                        onClick = { onMatchupClick(matchup) },
                        modifier = Modifier.width(roundWidth - 8.dp)
                    )

                    // Add spacer for next matchup (spacing increases for each round)
                    if (matchupIndex < conferenceMatchups.size - 1) {
                        Spacer(modifier = Modifier.height(verticalSpacing * spacingMultiplier))
                    }
                }

                // Add divider between conferences
                if (confIndex < conferences.size - 1) {
                    Spacer(modifier = Modifier.height(conferenceDividerSpacing))
                }
            }
        }

        // Add horizontal spacing between rounds
        if (!isLastRound) {
            Spacer(modifier = Modifier.width(horizontalSpacing))
        }
    }
}

@Composable
private fun MatchupCard(
    matchup: PlayoffMatchup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(
                enabled = matchup.team1 != null && matchup.team2 != null,
                onClick = onClick
            )
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        // Team 1
        TeamRow(
            teamName = matchup.team1?.name ?: "TBD",
            seed = matchup.team1?.seed,
            isWinner = matchup.winner == matchup.team1
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Team 2
        TeamRow(
            teamName = matchup.team2?.name ?: "TBD",
            seed = matchup.team2?.seed,
            isWinner = matchup.winner == matchup.team2
        )
    }
}

@Composable
private fun TeamRow(
    teamName: String,
    seed: Int?,
    isWinner: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Seed number
            if (seed != null) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            color = if (isWinner)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = seed.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWinner)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Team name
            Text(
                text = teamName,
                fontSize = 13.sp,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                color = if (isWinner)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Winner indicator
        if (isWinner) {
            Text(
                text = "✓",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}


// Generate fake matchup analytics for demo purposes
private fun generateMatchupAnalytics(matchup: PlayoffMatchup): MatchupAnalytics {
    val team1 = matchup.team1!!
    val team2 = matchup.team2!!

    // Generate fake weekly EPA data for 17 weeks
    val team1WeeklyEPA = (1..17).map { week ->
        val offEPA = 0.15 + (kotlin.random.Random.nextDouble() * 0.2 - 0.1)
        val defEPA = -0.10 + (kotlin.random.Random.nextDouble() * 0.15 - 0.075)
        WeeklyEPA(
            week = week,
            offensiveEPA = offEPA,
            defensiveEPA = defEPA,
            cumulativeEPA = (week * 0.05) + (kotlin.random.Random.nextDouble() * 2 - 1)
        )
    }

    val team2WeeklyEPA = (1..17).map { week ->
        val offEPA = 0.10 + (kotlin.random.Random.nextDouble() * 0.18 - 0.09)
        val defEPA = -0.05 + (kotlin.random.Random.nextDouble() * 0.12 - 0.06)
        WeeklyEPA(
            week = week,
            offensiveEPA = offEPA,
            defensiveEPA = defEPA,
            cumulativeEPA = (week * 0.03) + (kotlin.random.Random.nextDouble() * 1.5 - 0.75)
        )
    }

    return MatchupAnalytics(
        team1 = TeamAnalytics(
            name = team1.name,
            code = team1.code,
            seed = team1.seed,
            conference = team1.conference,
            record = "13-4",
            advancedStats = mapOf(
                "Off. Rating" to "118.5",
                "Def. Rating" to "108.2",
                "Net Rating" to "+10.3",
                "Pace" to "100.2",
                "eFG%" to "56.8%",
                "TOV%" to "12.4%",
                "ORB%" to "24.1%",
                "FT Rate" to "0.265"
            ),
            keyPlayers = listOf(
                PlayerStats(
                    name = "Player A",
                    position = "QB",
                    stats = mapOf(
                        "PPG" to "28.5",
                        "APG" to "6.8",
                        "RPG" to "5.2"
                    )
                ),
                PlayerStats(
                    name = "Player B",
                    position = "RB",
                    stats = mapOf(
                        "PPG" to "22.1",
                        "RPG" to "10.5",
                        "APG" to "2.3"
                    )
                )
            ),
            weeklyEPA = team1WeeklyEPA
        ),
        team2 = TeamAnalytics(
            name = team2.name,
            code = team2.code,
            seed = team2.seed,
            conference = team2.conference,
            record = "11-6",
            advancedStats = mapOf(
                "Off. Rating" to "115.2",
                "Def. Rating" to "110.8",
                "Net Rating" to "+4.4",
                "Pace" to "98.7",
                "eFG%" to "54.2%",
                "TOV%" to "13.8%",
                "ORB%" to "22.5%",
                "FT Rate" to "0.245"
            ),
            keyPlayers = listOf(
                PlayerStats(
                    name = "Player X",
                    position = "QB",
                    stats = mapOf(
                        "PPG" to "25.8",
                        "APG" to "7.2",
                        "RPG" to "4.8"
                    )
                ),
                PlayerStats(
                    name = "Player Y",
                    position = "WR",
                    stats = mapOf(
                        "PPG" to "19.5",
                        "RPG" to "8.2",
                        "APG" to "3.1"
                    )
                )
            ),
            weeklyEPA = team2WeeklyEPA
        ),
        odds = GameOdds(
            favorite = team1.code,
            spread = "-3.5",
            moneyline = "-165",
            overUnder = "48.5",
            source = "FanDuel"
        ),
        headToHead = listOf(
            HeadToHeadResult(
                date = "Week 12",
                team1Score = 27,
                team2Score = 24,
                location = "Home"
            ),
            HeadToHeadResult(
                date = "Week 5",
                team1Score = 21,
                team2Score = 28,
                location = "Away"
            )
        ),
        commonOpponents = listOf(
            CommonOpponentResult(
                opponent = "Kansas City",
                team1Result = GameResult(
                    date = "W14",
                    score = "W 31-28",
                    location = "H"
                ),
                team2Result = GameResult(
                    date = "W10",
                    score = "L 20-24",
                    location = "A"
                )
            ),
            CommonOpponentResult(
                opponent = "Buffalo",
                team1Result = GameResult(
                    date = "W9",
                    score = "W 28-21",
                    location = "A"
                ),
                team2Result = GameResult(
                    date = "W13",
                    score = "L 17-31",
                    location = "H"
                )
            ),
            CommonOpponentResult(
                opponent = "Miami",
                team1Result = GameResult(
                    date = "W6",
                    score = "W 35-14",
                    location = "H"
                ),
                team2Result = GameResult(
                    date = "W8",
                    score = "W 24-20",
                    location = "A"
                )
            )
        )
    )
}
