package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*

/**
 * Generic data structures for sport-agnostic matchup share images
 */

/** Generic game information for share image */
data class ShareGameInfo(
    val awayTeam: String,
    val homeTeam: String,
    val eventLabel: String, // e.g., "Week 18" for NFL, "Jan 23" for NBA
    val formattedDate: String, // e.g., "Sat, Dec 28 7:00 PM"
    val source: String = "ESPN",
    val awayRecord: String? = null, // e.g., "25-15"
    val homeRecord: String? = null,
    val awayConferenceRank: Int? = null,
    val homeConferenceRank: Int? = null,
    val awayConference: String? = null, // e.g., "Eastern" or "Western"
    val homeConference: String? = null
)

/** Generic odds information */
data class ShareOdds(
    val awayMoneyline: String? = null,
    val homeMoneyline: String? = null,
    val awaySpread: String? = null,
    val homeSpread: String? = null,
    val overUnder: String? = null
)

/** A single stat row in three-column format */
data class ShareThreeColStat(
    val leftText: String,
    val centerText: String,
    val rightText: String,
    val advantage: Int = 0 // -1 for left (away), 0 for even, 1 for right (home)
)

/** A single stat row in five-column format with ranks */
data class ShareFiveColStat(
    val leftValue: String,
    val leftRank: Int? = null,
    val leftRankDisplay: String? = null,
    val centerText: String,
    val rightValue: String,
    val rightRank: Int? = null,
    val rightRankDisplay: String? = null,
    val advantage: Int = 0, // -1 for left (away), 0 for even, 1 for right (home)
    val usePlayerRanks: Boolean = false // true for player ranks, false for team ranks
)

/** A box/card in the share image containing stats */
data class ShareStatBox(
    val title: String,
    val threeColStats: List<ShareThreeColStat> = emptyList(),
    val fiveColStats: List<ShareFiveColStat> = emptyList(),
    // Three-part header for displaying team-specific labels
    val leftLabel: String? = null,   // e.g., "DET Off" in Team1Color
    val middleLabel: String? = null, // e.g., "vs" in neutral color
    val rightLabel: String? = null,  // e.g., "HOU Def" in Team2Color
    // Colors for each label (defaults: left=Team1Color, right=Team2Color)
    val leftColor: Color? = null,
    val rightColor: Color? = null
)

/**
 * Pre-computed rank colors for team stats (1-32 scale)
 */
private val shareTeamRankColors: Map<Int, Color> = buildMap {
    put(0, Color.Transparent)
    val darkestRed = Color(139, 0, 0)
    for (rank in 1..32) {
        val color = when {
            rank <= 10 -> {
                val ratio = (rank - 1) / 9f
                Color((0 + ratio * 140).toInt(), (120 + ratio * 40).toInt(), 0)
            }
            rank <= 20 -> {
                val ratio = (rank - 11) / 9f
                Color((140 + ratio * 75).toInt(), (160 - ratio * 100).toInt(), 0)
            }
            else -> {
                val ratio = (rank - 21) / 11f
                Color((215 - ratio * 76).toInt(), (60 - ratio * 60).toInt(), 0)
            }
        }
        put(rank, color)
    }
    for (rank in 33..50) put(rank, darkestRed)
}

/**
 * Pre-computed rank colors for player stats (1-80 scale)
 */
private val sharePlayerRankColors: Map<Int, Color> = buildMap {
    put(0, Color.Transparent)
    val darkestRed = Color(139, 0, 0)
    for (rank in 1..80) {
        val clampedRank = if (rank > 64) 64 else rank
        val color = when {
            clampedRank <= 21 -> {
                val ratio = (clampedRank - 1) / 20f
                Color((0 + ratio * 50).toInt(), (100 + ratio * 105).toInt(), (0 + ratio * 50).toInt())
            }
            clampedRank <= 43 -> {
                val ratio = (clampedRank - 22) / 21f
                Color((180 + ratio * 75).toInt(), (140 - ratio * 41).toInt(), (50 - ratio * 21).toInt())
            }
            else -> {
                val ratio = (clampedRank - 44) / 20f
                Color((205 - ratio * 66).toInt(), (92 - ratio * 92).toInt(), (92 - ratio * 92).toInt())
            }
        }
        put(rank, color)
    }
    for (rank in 81..300) put(rank, darkestRed)
}

private fun getShareTeamRankColor(rank: Int?): Color = shareTeamRankColors[rank ?: 0] ?: Color.Transparent
private fun getSharePlayerRankColor(rank: Int?): Color {
    if (rank == null || rank == 0) return Color.Transparent
    return sharePlayerRankColors[rank] ?: Color(139, 0, 0)
}

/**
 * Calculate appropriate text color (white or black) based on background color luminance
 */
private fun getContrastingTextColor(backgroundColor: Color): Color {
    // Calculate relative luminance using the standard formula
    val r = backgroundColor.red
    val g = backgroundColor.green
    val b = backgroundColor.blue

    // Relative luminance formula (ITU-R BT.709)
    val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b

    // Use white text for dark backgrounds, black for light backgrounds
    return if (luminance > 0.5f) Color.Black else Color.White
}

private fun Double.formatShare(decimals: Int): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        else -> 1000.0
    }
    val rounded = kotlin.math.round(this * multiplier) / multiplier
    return if (decimals == 0) rounded.toInt().toString() else rounded.toString()
}

/**
 * High-resolution shareable image composable in landscape orientation (4800x2400).
 * Layout: 2 rows x 3 columns grid of comparison blocks.
 *
 * Row 1: Away Off vs Home Def | Home Off vs Away Def | QB Comparison
 * Row 2: RB Comparison | WR Comparison | H2H + Common Opponents
 */
@Composable
fun MatchupShareImage(
    awayTeam: String,
    homeTeam: String,
    matchup: MatchupV2,
    weekLabel: String,
    formattedDate: String,
    source: String = "nflfastR / ESPN",
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val cardBackground = MaterialTheme.colorScheme.background

    val awayTeamData = matchup.teams[awayTeam.lowercase()]
    val homeTeamData = matchup.teams[homeTeam.lowercase()]
    val odds = matchup.odds

    Column(
        modifier = modifier
            .background(backgroundColor)
            .padding(40.dp)
    ) {
        // Compact header row with team names and odds
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Away team with moneyline and spread
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = awayTeam,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Team1Color
                )
                // Away moneyline and spread
                if (odds != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        odds.away_moneyline?.let { ml ->
                            Text(
                                text = "ML: $ml",
                                fontSize = 36.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                        // Calculate away spread from home spread
                        odds.home_spread?.let { homeSpread ->
                            val awaySpread = if (homeSpread.startsWith("-")) {
                                "+" + homeSpread.substring(1)
                            } else if (homeSpread.startsWith("+")) {
                                "-" + homeSpread.substring(1)
                            } else {
                                "-$homeSpread"
                            }
                            Text(
                                text = "Spread: $awaySpread",
                                fontSize = 36.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Center: Week, Date, and O/U
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$weekLabel • $formattedDate",
                    fontSize = 48.sp,
                    color = textColor.copy(alpha = 0.8f)
                )
                odds?.over_under?.let { ou ->
                    Text(
                        text = "O/U: $ou",
                        fontSize = 36.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }

            // Home team with moneyline and spread
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = homeTeam,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Team2Color
                )
                // Home moneyline and spread
                if (odds != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        odds.home_spread?.let { spread ->
                            Text(
                                text = "Spread: $spread",
                                fontSize = 36.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                        odds.home_moneyline?.let { ml ->
                            Text(
                                text = "ML: $ml",
                                fontSize = 36.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2x3 Grid of comparison blocks
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 1: Versus comparisons + QB
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Block 1: Away Off vs Home Def
                ShareComparisonCard(
                    title = "$awayTeam Off vs $homeTeam Def",
                    modifier = Modifier.weight(1f),
                    cardBackground = cardBackground,
                    leftLabel = "$awayTeam Off",
                    middleLabel = "vs",
                    rightLabel = "$homeTeam Def"
                ) {
                    if (awayTeamData != null && homeTeamData != null) {
                        ShareVersusStats(
                            offStats = awayTeamData.team_stats.current.offense,
                            defStats = homeTeamData.team_stats.current.defense,
                            textColor = textColor
                        )
                    }
                }

                // Block 2: Home Off vs Away Def
                ShareComparisonCard(
                    title = "$homeTeam Off vs $awayTeam Def",
                    modifier = Modifier.weight(1f),
                    cardBackground = cardBackground,
                    leftLabel = "$homeTeam Off",
                    middleLabel = "vs",
                    rightLabel = "$awayTeam Def",
                    leftColor = Team2Color,
                    rightColor = Team1Color
                ) {
                    if (awayTeamData != null && homeTeamData != null) {
                        ShareVersusStats(
                            offStats = homeTeamData.team_stats.current.offense,
                            defStats = awayTeamData.team_stats.current.defense,
                            textColor = textColor
                        )
                    }
                }

                // Block 3: QB Comparison
                val awayQB = awayTeamData?.players?.qb
                val homeQB = homeTeamData?.players?.qb
                ShareComparisonCard(
                    title = "QB Comparison",
                    modifier = Modifier.weight(1f),
                    cardBackground = cardBackground,
                    leftLabel = awayQB?.name ?: awayTeam,
                    middleLabel = "QB",
                    rightLabel = homeQB?.name ?: homeTeam
                ) {
                    if (awayQB != null && homeQB != null) {
                        ShareQBStats(awayQB, homeQB, textColor)
                    }
                }
            }

            // Row 2: RB, WR, H2H + Common Opponents
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Block 4: RB Comparison
                val awayRB = awayTeamData?.players?.rbs?.firstOrNull()
                val homeRB = homeTeamData?.players?.rbs?.firstOrNull()
                ShareComparisonCard(
                    title = "RB Comparison",
                    modifier = Modifier.weight(1f),
                    cardBackground = cardBackground,
                    leftLabel = awayRB?.name ?: awayTeam,
                    middleLabel = "RB",
                    rightLabel = homeRB?.name ?: homeTeam
                ) {
                    if (awayRB != null && homeRB != null) {
                        ShareRBStats(awayRB, homeRB, textColor)
                    }
                }

                // Block 5: WR Comparison
                val awayWR = awayTeamData?.players?.receivers?.firstOrNull()
                val homeWR = homeTeamData?.players?.receivers?.firstOrNull()
                ShareComparisonCard(
                    title = "WR Comparison",
                    modifier = Modifier.weight(1f),
                    cardBackground = cardBackground,
                    leftLabel = awayWR?.name ?: awayTeam,
                    middleLabel = "WR",
                    rightLabel = homeWR?.name ?: homeTeam
                ) {
                    if (awayWR != null && homeWR != null) {
                        ShareWRStats(awayWR, homeWR, textColor)
                    }
                }

                // Block 6: H2H + Common Opponents
                ShareComparisonCard(
                    title = "History",
                    modifier = Modifier.weight(1f),
                    cardBackground = cardBackground
                ) {
                    ShareHistoryContent(
                        awayTeam = awayTeam,
                        homeTeam = homeTeam,
                        h2hRecord = matchup.h2h_record,
                        commonOpponents = matchup.common_opponents,
                        textColor = textColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Source: $source",
                fontSize = 36.sp,
                color = textColor.copy(alpha = 0.6f)
            )
            Text(
                text = "fbrk.app",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Generic sport-agnostic shareable image composable in landscape orientation (3400x1800).
 * Layout: 2 rows x 3 columns grid of stat boxes.
 *
 * @param gameInfo Basic game information (teams, date, event label)
 * @param odds Betting odds information
 * @param statBoxes List of 6 stat boxes (row 1: boxes 0-2, row 2: boxes 3-5)
 */
@Composable
fun GenericMatchupShareImage(
    gameInfo: ShareGameInfo,
    odds: ShareOdds? = null,
    statBoxes: List<ShareStatBox>,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface
    val cardBackground = MaterialTheme.colorScheme.background

    Column(
        modifier = modifier
            .background(backgroundColor)
            .padding(24.dp)
    ) {
        // Compact header row with team names and odds
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Away team with moneyline and spread
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = gameInfo.awayTeam,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Team1Color
                )
                // Away moneyline and spread
                if (odds != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        odds.awayMoneyline?.let { ml ->
                            Text(
                                text = "ML: $ml",
                                fontSize = 36.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                        odds.awaySpread?.let { spread ->
                            Text(
                                text = "Spread: $spread",
                                fontSize = 36.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Center: Event, Date, and O/U
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${gameInfo.eventLabel} • ${gameInfo.formattedDate}",
                    fontSize = 42.sp,
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    softWrap = false
                )
                odds?.overUnder?.let { ou ->
                    Text(
                        text = "O/U: $ou",
                        fontSize = 36.sp,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }

            // Home team with moneyline and spread
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = gameInfo.homeTeam,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Team2Color
                )
                // Home moneyline and spread
                if (odds != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        odds.homeSpread?.let { spread ->
                            Text(
                                text = "Spread: $spread",
                                fontSize = 36.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                        odds.homeMoneyline?.let { ml ->
                            Text(
                                text = "ML: $ml",
                                fontSize = 36.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // Record and Conference Rank Row (for NBA only)
        if (gameInfo.awayRecord != null || gameInfo.homeRecord != null ||
            gameInfo.awayConferenceRank != null || gameInfo.homeConferenceRank != null) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Away team record and conference rank
                if (gameInfo.awayRecord != null || gameInfo.awayConferenceRank != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Colored rank indicator circle
                        gameInfo.awayConferenceRank?.let { rank ->
                            val rankColor = when {
                                rank <= 5 -> Color(0xFF4CAF50) // Green for top 5
                                rank <= 10 -> Color(0xFFFFC107) // Yellow for 6-10
                                else -> Color(0xFFF44336) // Red for 11+
                            }
                            Canvas(
                                modifier = Modifier.size(24.dp)
                            ) {
                                drawCircle(color = rankColor)
                            }
                        }

                        // Format: "wins - losses / rank in Conference"
                        val recordParts = gameInfo.awayRecord?.split("-")
                        val wins = recordParts?.getOrNull(0) ?: ""
                        val losses = recordParts?.getOrNull(1) ?: ""

                        val rankText = gameInfo.awayConferenceRank?.let { rank ->
                            val ordinal = when (rank) {
                                1 -> "1st"
                                2 -> "2nd"
                                3 -> "3rd"
                                else -> "${rank}th"
                            }
                            val confName = gameInfo.awayConference?.let { conf ->
                                if (conf.startsWith("East", ignoreCase = true)) "East" else "West"
                            } ?: ""
                            " / $ordinal in the $confName"
                        } ?: ""

                        Text(
                            text = "$wins - $losses$rankText",
                            fontSize = 36.sp,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }

                // Center divider or empty space
                Spacer(modifier = Modifier.width(20.dp))

                // Home team record and conference rank (mirrored layout)
                if (gameInfo.homeRecord != null || gameInfo.homeConferenceRank != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Format: "rank in Conference / wins - losses" (flipped order for home team)
                        val recordParts = gameInfo.homeRecord?.split("-")
                        val wins = recordParts?.getOrNull(0) ?: ""
                        val losses = recordParts?.getOrNull(1) ?: ""

                        val rankText = gameInfo.homeConferenceRank?.let { rank ->
                            val ordinal = when (rank) {
                                1 -> "1st"
                                2 -> "2nd"
                                3 -> "3rd"
                                else -> "${rank}th"
                            }
                            val confName = gameInfo.homeConference?.let { conf ->
                                if (conf.startsWith("East", ignoreCase = true)) "East" else "West"
                            } ?: ""
                            "$ordinal in the $confName / "
                        } ?: ""

                        Text(
                            text = "$rankText$wins - $losses",
                            fontSize = 36.sp,
                            color = textColor.copy(alpha = 0.8f)
                        )

                        // Colored rank indicator circle (on the right for home team)
                        gameInfo.homeConferenceRank?.let { rank ->
                            val rankColor = when {
                                rank <= 5 -> Color(0xFF4CAF50) // Green for top 5
                                rank <= 10 -> Color(0xFFFFC107) // Yellow for 6-10
                                else -> Color(0xFFF44336) // Red for 11+
                            }
                            Canvas(
                                modifier = Modifier.size(24.dp)
                            ) {
                                drawCircle(color = rankColor)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2x3 Grid of stat boxes
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Boxes 0-2
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                statBoxes.take(3).forEach { box ->
                    ShareComparisonCard(
                        title = box.title,
                        modifier = Modifier.weight(1f),
                        cardBackground = cardBackground,
                        leftLabel = box.leftLabel,
                        middleLabel = box.middleLabel,
                        rightLabel = box.rightLabel,
                        leftColor = box.leftColor ?: Team1Color,
                        rightColor = box.rightColor ?: Team2Color
                    ) {
                        GenericStatBoxContent(box, textColor)
                    }
                }
            }

            // Row 2: Boxes 3-5
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                statBoxes.drop(3).take(3).forEach { box ->
                    ShareComparisonCard(
                        title = box.title,
                        modifier = Modifier.weight(1f),
                        cardBackground = cardBackground,
                        leftLabel = box.leftLabel,
                        middleLabel = box.middleLabel,
                        rightLabel = box.rightLabel,
                        leftColor = box.leftColor ?: Team1Color,
                        rightColor = box.rightColor ?: Team2Color
                    ) {
                        GenericStatBoxContent(box, textColor)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Source: ${gameInfo.source}",
                fontSize = 36.sp,
                color = textColor.copy(alpha = 0.6f)
            )
            Text(
                text = "fbrk.app",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Renders the content of a stat box using three-column and five-column stats
 */
@Composable
private fun GenericStatBoxContent(
    box: ShareStatBox,
    textColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Render three-column stats
        box.threeColStats.forEach { stat ->
            ShareThreeColumnRow(
                leftText = stat.leftText,
                centerText = stat.centerText,
                rightText = stat.rightText,
                advantage = stat.advantage,
                textColor = textColor
            )
        }

        // Render five-column stats with ranks
        box.fiveColStats.forEach { stat ->
            ShareGenericFiveColumnRow(
                leftValue = stat.leftValue,
                leftRank = stat.leftRank,
                leftRankDisplay = stat.leftRankDisplay,
                centerText = stat.centerText,
                rightValue = stat.rightValue,
                rightRank = stat.rightRank,
                rightRankDisplay = stat.rightRankDisplay,
                advantage = stat.advantage,
                usePlayerRanks = stat.usePlayerRanks,
                textColor = textColor
            )
        }
    }
}

/**
 * Generic five-column row with support for both team and player rank colors
 */
@Composable
private fun ShareGenericFiveColumnRow(
    leftValue: String,
    leftRank: Int?,
    leftRankDisplay: String?,
    centerText: String,
    rightValue: String,
    rightRank: Int?,
    rightRankDisplay: String?,
    advantage: Int = 0,
    usePlayerRanks: Boolean = false,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left value with advantage indicator
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            if (advantage == -1) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Team1Color, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = leftValue,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }

        // Left rank badge
        ShareGenericRankBadge(
            rank = leftRank,
            rankDisplay = leftRankDisplay,
            usePlayerRanks = usePlayerRanks
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Center label
        Text(
            text = centerText,
            fontSize = 34.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.5f),
            maxLines = 1,
            softWrap = false
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Right rank badge
        ShareGenericRankBadge(
            rank = rightRank,
            rankDisplay = rightRankDisplay,
            usePlayerRanks = usePlayerRanks
        )

        // Right value with advantage indicator
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = rightValue,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.End
            )
            if (advantage == 1) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Team2Color, CircleShape)
                )
            }
        }
    }
}

/**
 * Generic rank badge that can display rank text and use appropriate colors
 */
@Composable
private fun ShareGenericRankBadge(
    rank: Int?,
    rankDisplay: String?,
    usePlayerRanks: Boolean
) {
    val backgroundColor = if (usePlayerRanks) {
        getSharePlayerRankColor(rank)
    } else {
        getShareTeamRankColor(rank)
    }

    Box(
        modifier = Modifier
            .width(70.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rankDisplay ?: rank?.toString() ?: "-",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun ShareComparisonCard(
    title: String,
    modifier: Modifier = Modifier,
    cardBackground: Color,
    leftLabel: String? = null,
    middleLabel: String? = null,
    rightLabel: String? = null,
    leftColor: Color = Team1Color,
    rightColor: Color = Team2Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header: either three-part label or simple title
        if (leftLabel != null && rightLabel != null) {
            // Three-part header: left team/player far left, vs center, right team/player far right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = leftLabel,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = leftColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = middleLabel ?: "vs",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Normal,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = rightLabel,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = rightColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Simple centered title
            Text(
                text = title,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Content
        content()
    }
}

@Composable
private fun SharePlayerHeader(
    awayName: String,
    homeName: String,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = awayName,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Team1Color,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "vs",
            fontSize = 34.sp,
            color = textColor.copy(alpha = 0.6f)
        )
        Text(
            text = homeName,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Team2Color,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ShareVersusStats(
    offStats: Map<String, StatValue>,
    defStats: Map<String, StatValue>,
    textColor: Color
) {
    // Extended list of stats for versus comparison: (offKey, defKey, label, decimals)
    val statsToShow = listOf(
        listOf("off_epa", "def_epa", "EPA", "3"),
        listOf("yards_per_game", "yards_allowed_per_game", "Total Yds/G", "1"),
        listOf("pass_yards_per_game", "pass_yards_allowed_per_game", "Pass Yds/G", "1"),
        listOf("rush_yards_per_game", "rush_yards_allowed_per_game", "Rush Yds/G", "1"),
        listOf("points_per_game", "points_allowed_per_game", "Pts/G", "1"),
        listOf("third_down_pct", "third_down_pct_def", "3rd Down %", "1"),
        listOf("interceptions_thrown", "interceptions_made", "INTs Thrown", "0"),
        listOf("fumbles_lost", "fumbles_forced", "Fumbles Lost", "0"),
        listOf("sacks_suffered", "sacks_made", "Sacks Allowed", "0"),
        listOf("touchdowns", "touchdowns_allowed", "Touchdowns", "0")
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        statsToShow.forEach { statConfig ->
            val (offKey, defKey, label, decimalsStr) = statConfig
            val decimals = decimalsStr.toInt()
            val offStat = offStats[offKey]
            val defStat = defStats[defKey]
            val offValue = offStat?.value
            val defValue = defStat?.value

            // For negative stats (INTs, fumbles, sacks), lower offense value is better
            val isInvertedStat = offKey.contains("interceptions_thrown") ||
                                offKey.contains("fumbles_lost") ||
                                offKey.contains("sacks_suffered")

            val advantage = if (offValue != null && defValue != null) {
                if (isInvertedStat) {
                    // Lower offense value beats higher defense value
                    when {
                        offValue < defValue -> -1
                        offValue > defValue -> 1
                        else -> 0
                    }
                } else {
                    // Higher offense value beats lower defense value
                    when {
                        offValue > defValue -> -1
                        offValue < defValue -> 1
                        else -> 0
                    }
                }
            } else 0

            ShareFiveColumnRow(
                leftValue = offValue?.formatShare(decimals) ?: "-",
                leftRank = offStat?.rank,
                centerText = label,
                rightValue = defValue?.formatShare(decimals) ?: "-",
                rightRank = defStat?.rank,
                advantage = advantage,
                usePlayerColors = false,
                textColor = textColor
            )
        }
    }
}

@Composable
private fun ShareQBStats(
    awayQB: QBPlayerStats,
    homeQB: QBPlayerStats,
    textColor: Color
) {
    // Extended list of QB stats
    val stats = listOf(
        Triple(awayQB.total_epa to homeQB.total_epa, "Total EPA", 2),
        Triple(awayQB.passing_yards to homeQB.passing_yards, "Pass Yards", 0),
        Triple(awayQB.passing_yards_per_game to homeQB.passing_yards_per_game, "Pass Yds/G", 1),
        Triple(awayQB.passing_tds to homeQB.passing_tds, "Pass TDs", 0),
        Triple(awayQB.completion_pct to homeQB.completion_pct, "Comp %", 1),
        Triple(awayQB.passing_cpoe to homeQB.passing_cpoe, "CPOE", 2),
        Triple(awayQB.pacr to homeQB.pacr, "PACR", 2),
        Triple(awayQB.interceptions to homeQB.interceptions, "INTs", 0)
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEach { (statPair, label, decimals) ->
            val (awayStat, homeStat) = statPair
            val awayValue = awayStat.value
            val homeValue = homeStat.value
            val isInverted = label == "INTs"
            val advantage = if (awayValue != null && homeValue != null) {
                if (isInverted) {
                    when {
                        awayValue < homeValue -> -1
                        awayValue > homeValue -> 1
                        else -> 0
                    }
                } else {
                    when {
                        awayValue > homeValue -> -1
                        awayValue < homeValue -> 1
                        else -> 0
                    }
                }
            } else 0

            ShareFiveColumnRow(
                leftValue = awayValue?.formatShare(decimals) ?: "-",
                leftRank = awayStat.rank,
                centerText = label,
                rightValue = homeValue?.formatShare(decimals) ?: "-",
                rightRank = homeStat.rank,
                advantage = advantage,
                usePlayerColors = false,
                textColor = textColor
            )
        }
    }
}

@Composable
private fun ShareRBStats(
    awayRB: RBPlayerStats,
    homeRB: RBPlayerStats,
    textColor: Color
) {
    // Extended list of RB stats
    val stats = listOf(
        Triple(awayRB.rushing_epa to homeRB.rushing_epa, "Rush EPA", 2),
        Triple(awayRB.rushing_yards to homeRB.rushing_yards, "Rush Yards", 0),
        Triple(awayRB.rushing_yards_per_game to homeRB.rushing_yards_per_game, "Rush Yds/G", 1),
        Triple(awayRB.rushing_tds to homeRB.rushing_tds, "Rush TDs", 0),
        Triple(awayRB.yards_per_carry to homeRB.yards_per_carry, "Yds/Carry", 1),
        Triple(awayRB.receptions to homeRB.receptions, "Receptions", 0),
        Triple(awayRB.receiving_yards to homeRB.receiving_yards, "Rec Yards", 0),
        Triple(awayRB.target_share to homeRB.target_share, "Target Share", 1)
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEach { (statPair, label, decimals) ->
            val (awayStat, homeStat) = statPair
            val awayValue = awayStat.value
            val homeValue = homeStat.value
            val advantage = if (awayValue != null && homeValue != null) {
                when {
                    awayValue > homeValue -> -1
                    awayValue < homeValue -> 1
                    else -> 0
                }
            } else 0

            ShareFiveColumnRow(
                leftValue = awayValue?.formatShare(decimals) ?: "-",
                leftRank = awayStat.rank,
                centerText = label,
                rightValue = homeValue?.formatShare(decimals) ?: "-",
                rightRank = homeStat.rank,
                advantage = advantage,
                usePlayerColors = true,
                textColor = textColor
            )
        }
    }
}

@Composable
private fun ShareWRStats(
    awayWR: ReceiverPlayerStats,
    homeWR: ReceiverPlayerStats,
    textColor: Color
) {
    // Extended list of WR stats
    val stats = listOf(
        Triple(awayWR.receiving_epa to homeWR.receiving_epa, "Rec EPA", 2),
        Triple(awayWR.receiving_yards to homeWR.receiving_yards, "Rec Yards", 0),
        Triple(awayWR.receiving_yards_per_game to homeWR.receiving_yards_per_game, "Rec Yds/G", 1),
        Triple(awayWR.receiving_tds to homeWR.receiving_tds, "Rec TDs", 0),
        Triple(awayWR.receptions to homeWR.receptions, "Receptions", 0),
        Triple(awayWR.yards_per_reception to homeWR.yards_per_reception, "Yds/Rec", 1),
        Triple(awayWR.catch_pct to homeWR.catch_pct, "Catch %", 1),
        Triple(awayWR.wopr to homeWR.wopr, "WOPR", 2)
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.forEach { (statPair, label, decimals) ->
            val (awayStat, homeStat) = statPair
            val awayValue = awayStat.value
            val homeValue = homeStat.value
            val advantage = if (awayValue != null && homeValue != null) {
                when {
                    awayValue > homeValue -> -1
                    awayValue < homeValue -> 1
                    else -> 0
                }
            } else 0

            ShareFiveColumnRow(
                leftValue = awayValue?.formatShare(decimals) ?: "-",
                leftRank = awayStat.rank,
                centerText = label,
                rightValue = homeValue?.formatShare(decimals) ?: "-",
                rightRank = homeStat.rank,
                advantage = advantage,
                usePlayerColors = true,
                textColor = textColor
            )
        }
    }
}

@Composable
private fun ShareHistoryContent(
    awayTeam: String,
    homeTeam: String,
    h2hRecord: List<H2HGame>,
    commonOpponents: CommonOpponents?,
    textColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // H2H Record
        if (h2hRecord.isNotEmpty()) {
            Text(
                text = "Head-to-Head",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = textColor.copy(alpha = 0.8f)
            )
            h2hRecord.take(3).forEach { h2hGame ->
                val advantage = when (h2hGame.winner.uppercase()) {
                    awayTeam -> -1
                    homeTeam -> 1
                    else -> 0
                }
                ShareThreeColumnRow(
                    leftText = if (h2hGame.winner == awayTeam) "W" else if (h2hGame.winner == homeTeam) "L" else "T",
                    centerText = "W${h2hGame.week}: ${h2hGame.finalScore}",
                    rightText = if (h2hGame.winner == homeTeam) "W" else if (h2hGame.winner == awayTeam) "L" else "T",
                    advantage = advantage,
                    textColor = textColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Common Opponents
        commonOpponents?.let { commonOpps ->
            if (commonOpps.isNotEmpty()) {
                Text(
                    text = "Common Opponents",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor.copy(alpha = 0.8f)
                )
                commonOpps.entries.take(4).forEach { (opponentCode, opponentData) ->
                    val awayGames = opponentData[awayTeam.lowercase()] ?: emptyList()
                    val homeGames = opponentData[homeTeam.lowercase()] ?: emptyList()

                    if (awayGames.isNotEmpty() || homeGames.isNotEmpty()) {
                        val awayGame = awayGames.firstOrNull()
                        val homeGame = homeGames.firstOrNull()
                        val awayResult = awayGame?.let { "${it.result} ${it.score}" } ?: "-"
                        val homeResult = homeGame?.let { "${it.result} ${it.score}" } ?: "-"

                        val advantage = when {
                            awayGame?.result == "W" && homeGame?.result == "L" -> -1
                            awayGame?.result == "L" && homeGame?.result == "W" -> 1
                            else -> 0
                        }

                        ShareThreeColumnRow(
                            leftText = awayResult,
                            centerText = opponentCode.uppercase(),
                            rightText = homeResult,
                            advantage = advantage,
                            textColor = textColor,
                            centerWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Three-column row matching worksheet styling (2x size for high-res)
 */
@Composable
private fun ShareThreeColumnRow(
    leftText: String,
    centerText: String,
    rightText: String,
    advantage: Int = 0,
    textColor: Color,
    centerWeight: FontWeight = FontWeight.Normal
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            if (advantage == -1) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Team1Color, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = leftText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }

        Text(
            text = centerText,
            fontSize = 34.sp,
            fontWeight = centerWeight,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            softWrap = false
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = rightText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            if (advantage == 1) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Team2Color, CircleShape)
                )
            }
        }
    }
}

/**
 * Five-column row with rank badges matching worksheet styling (2x size for high-res)
 * Layout: [value] [rank] [label] [rank] [value]
 */
@Composable
private fun ShareFiveColumnRow(
    leftValue: String,
    leftRank: Int?,
    centerText: String,
    rightValue: String,
    rightRank: Int?,
    advantage: Int = 0,
    usePlayerColors: Boolean = false,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left value with advantage indicator
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            if (advantage == -1) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Team1Color, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = leftValue,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }

        // Left rank badge
        ShareRankBadge(rank = leftRank, usePlayerColors = usePlayerColors)

        Spacer(modifier = Modifier.width(12.dp))

        // Center label
        Text(
            text = centerText,
            fontSize = 34.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.5f),
            maxLines = 1,
            softWrap = false
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Right rank badge
        ShareRankBadge(rank = rightRank, usePlayerColors = usePlayerColors)

        // Right value with advantage indicator
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = rightValue,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.End
            )
            if (advantage == 1) {
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Team2Color, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun ShareRankBadge(
    rank: Int?,
    usePlayerColors: Boolean
) {
    val backgroundColor = if (usePlayerColors) {
        getSharePlayerRankColor(rank)
    } else {
        getShareTeamRankColor(rank)
    }

    Box(
        modifier = Modifier
            .width(70.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rank?.toString() ?: "-",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}
