package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

// Team colors for advantage indicators
internal val Team1Color = Color(0xFF2196F3) // Blue (away team)
internal val Team2Color = Color(0xFFFF5722) // Deep Orange (home team)

/**
 * Pre-computed rank colors for NBA team stats (1-30 scale)
 * - Ranks 1-10: Green gradient
 * - Ranks 11-20: Orange to dark orange gradient
 * - Ranks 21-30: Red to dark red gradient
 */
val nbaTeamRankColors: Map<Int, Color> = buildMap {
    put(0, Color.Transparent) // For null ranks

    // Darkest red color for rank 30+
    val darkestRed = Color(139, 0, 0)

    for (rank in 1..30) {
        val color = when {
            rank <= 10 -> {
                // Green gradient (ranks 1-10): Bright green to darker green
                val ratio = (rank - 1) / 9f
                val red = (0 + ratio * 80).toInt()
                val green = (150 + ratio * 30).toInt()
                val blue = 0
                Color(red, green, blue)
            }
            rank <= 20 -> {
                // Orange to dark orange gradient (ranks 11-20)
                val ratio = (rank - 11) / 9f
                val red = (255 - ratio * 55).toInt()
                val green = (140 - ratio * 40).toInt()
                val blue = 0
                Color(red, green, blue)
            }
            else -> {
                // Red to dark red gradient (ranks 21-30)
                val ratio = (rank - 21) / 9f
                val red = (200 - ratio * 61).toInt()
                val green = (0 + ratio * 0).toInt()
                val blue = 0
                Color(red, green, blue)
            }
        }
        put(rank, color)
    }

    // Add explicit mapping for ranks > 30 to use the darkest red
    for (rank in 31..50) {
        put(rank, darkestRed)
    }
}

/**
 * Get color for NBA team rank (1-30 scale)
 */
fun getNBATeamRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    return nbaTeamRankColors[rank.coerceIn(1, 50)] ?: Color.Transparent
}

/**
 * Pre-computed rank colors for NBA player stats
 * - Ranks 1-30: Green gradient (dark green to lighter green)
 * - Ranks 31-60: Orange to red gradient
 * - Ranks 61+: Dark red
 */
val nbaPlayerRankColors: Map<Int, Color> = buildMap {
    put(0, Color.Transparent) // For null ranks

    // Dark red for ranks 61+
    val darkestRed = Color(139, 0, 0)

    for (rank in 1..100) {
        val color = when {
            rank <= 30 -> {
                // Green gradient (ranks 1-30): Dark green to lighter green
                val ratio = (rank - 1) / 29f
                val red = (0 + ratio * 100).toInt()
                val green = (100 + ratio * 80).toInt()
                val blue = 0
                Color(red, green, blue)
            }
            rank <= 60 -> {
                // Orange to red gradient (ranks 31-60)
                val ratio = (rank - 31) / 29f
                val red = (200 + ratio * 15).toInt()
                val green = (180 - ratio * 180).toInt()
                val blue = 0
                Color(red, green, blue)
            }
            else -> {
                // Dark red for ranks 61+
                darkestRed
            }
        }
        put(rank, color)
    }
}

/**
 * Get color for NBA player rank (1-100+ scale)
 */
fun getNBAPlayerRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    return nbaPlayerRankColors[rank.coerceIn(1, 100)] ?: nbaPlayerRankColors[100]!!
}

/**
 * Get color for conference rank (1-15 scale)
 * Light green for 1st, dark red for 15th
 */
fun getConferenceRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent

    // Clamp to 1-15 range
    val clampedRank = rank.coerceIn(1, 15)

    // Interpolate from light green (1st) to dark red (15th)
    // Using 15 steps
    val ratio = (clampedRank - 1) / 14f  // 0.0 for rank 1, 1.0 for rank 15

    // Start: Light green (#66BB6A - rgb(102, 187, 106))
    // End: Dark red (#C62828 - rgb(198, 40, 40))
    val startR = 102
    val startG = 187
    val startB = 106

    val endR = 198
    val endG = 40
    val endB = 40

    val r = (startR + ratio * (endR - startR)).toInt()
    val g = (startG + ratio * (endG - startG)).toInt()
    val b = (startB + ratio * (endB - startB)).toInt()

    return Color(r, g, b)
}

/**
 * Generic three-column layout component for consistent compact formatting
 */
@Composable
fun ThreeColumnRow(
    leftText: String,
    centerText: String,
    rightText: String,
    modifier: Modifier = Modifier,
    leftWeight: FontWeight = FontWeight.Medium,
    centerWeight: FontWeight = FontWeight.Normal,
    rightWeight: FontWeight = FontWeight.Medium,
    leftColor: Color = MaterialTheme.colorScheme.onSurface,
    centerColor: Color = MaterialTheme.colorScheme.primary,
    rightColor: Color = MaterialTheme.colorScheme.onSurface,
    advantage: Int = 0, // -1 for left (away team), 0 for even, 1 for right (home team)
    centerMaxLines: Int = Int.MAX_VALUE,
    centerOverflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
    centerSoftWrap: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
                        .size(6.dp)
                        .background(Team1Color, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = leftText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = leftWeight,
                color = leftColor
            )
        }

        Text(
            text = centerText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            fontWeight = centerWeight,
            color = centerColor,
            maxLines = centerMaxLines,
            overflow = centerOverflow,
            softWrap = centerSoftWrap
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = rightText,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = rightWeight,
                color = rightColor
            )
            if (advantage == 1) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Team2Color, CircleShape)
                )
            }
        }
    }
}

/**
 * Five-column layout with stat values and rank indicators
 */
@Composable
fun FiveColumnRowWithRanks(
    leftValue: String,
    leftRank: Int?,
    leftRankDisplay: String?,
    centerText: String,
    rightValue: String,
    rightRank: Int?,
    rightRankDisplay: String?,
    advantage: Int = 0,
    useNBARanks: Boolean = true, // true for NBA (30 teams), false for NFL (32 teams)
    usePlayerRanks: Boolean = false // true for player ranks (1-100+ scale), overrides useNBARanks
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                        .size(6.dp)
                        .background(Team1Color, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = leftValue,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Left rank box
        Box(
            modifier = Modifier
                .width(32.dp)
                .background(
                    when {
                        usePlayerRanks -> getNBAPlayerRankColor(leftRank)
                        useNBARanks -> getNBATeamRankColor(leftRank)
                        else -> Color.Gray
                    },
                    RoundedCornerShape(4.dp)
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = leftRankDisplay ?: "-",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Center label
        Text(
            text = centerText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Right rank box
        Box(
            modifier = Modifier
                .width(32.dp)
                .background(
                    when {
                        usePlayerRanks -> getNBAPlayerRankColor(rightRank)
                        useNBARanks -> getNBATeamRankColor(rightRank)
                        else -> Color.Gray
                    },
                    RoundedCornerShape(4.dp)
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rightRankDisplay ?: "-",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Right value with advantage indicator
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = rightValue,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            if (advantage == 1) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Team2Color, CircleShape)
                )
            }
        }
    }
}

/**
 * Section header text - matches MatchupWorksheet styling
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = modifier.padding(bottom = 4.dp)
    )
}

/**
 * Compact navigation badge for Team/Versus toggle within team stats section
 */
@Composable
fun TeamStatsNavBadge(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * Pinned header showing team matchup at the top
 */
@Composable
fun PinnedMatchupHeader(
    awayTeam: String,
    homeTeam: String,
    awayWins: Int? = null,
    awayLosses: Int? = null,
    awayConferenceRank: Int? = null,
    awayConference: String? = null,
    homeWins: Int? = null,
    homeLosses: Int? = null,
    homeConferenceRank: Int? = null,
    homeConference: String? = null,
    modifier: Modifier = Modifier
) {
    // Helper to format conference name
    fun formatConference(conf: String?): String {
        return when (conf?.lowercase()) {
            "east" -> "East"
            "west" -> "West"
            else -> "Conf"
        }
    }

    // Build record strings
    val awayRecord = if (awayWins != null && awayLosses != null && awayConferenceRank != null) {
        "$awayWins-$awayLosses / ${formatOrdinal(awayConferenceRank)} / ${formatConference(awayConference)}"
    } else {
        null
    }

    val homeRecord = if (homeWins != null && homeLosses != null && homeConferenceRank != null) {
        "$homeWins-$homeLosses / ${formatOrdinal(homeConferenceRank)} / ${formatConference(homeConference)}"
    } else {
        null
    }

    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 12.dp)
        ) {
            // First row: Team abbreviations
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = awayTeam,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                Text(
                    text = "@",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Text(
                    text = homeTeam,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }

            // Second row: Records and conference ranks (if available)
            if (awayRecord != null || homeRecord != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Away team record with conference rank indicator
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        if (awayConferenceRank != null) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = getConferenceRankColor(awayConferenceRank),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = awayRecord ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 10.sp),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }

                    // Home team record with conference rank indicator
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = homeRecord ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 10.sp),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                        if (homeConferenceRank != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = getConferenceRankColor(homeConferenceRank),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper function to format ordinal numbers (1st, 2nd, 3rd, etc.)
 */
private fun formatOrdinal(number: Int): String {
    return when {
        number % 100 in 11..13 -> "${number}th"
        number % 10 == 1 -> "${number}st"
        number % 10 == 2 -> "${number}nd"
        number % 10 == 3 -> "${number}rd"
        else -> "${number}th"
    }
}

/**
 * Generic player stat value structure for sport-agnostic player comparisons
 */
data class PlayerStatValue(
    val value: Double?,
    val rank: Int?,
    val rankDisplay: String?
)

/**
 * Configuration for a single player stat comparison row
 */
data class PlayerStatConfig<T>(
    val label: String,
    val decimals: Int,
    val accessor: (T) -> PlayerStatValue,
    val lowerIsBetter: Boolean = false
)

/**
 * Sport-agnostic player comparison section
 * Compares two players stat-by-stat with ranks displayed
 */
@Composable
fun <T> PlayerComparisonSection(
    awayPlayerName: String,
    homePlayerName: String,
    awayPlayerStats: T,
    homePlayerStats: T,
    positionLabel: String,
    statsConfig: List<PlayerStatConfig<T>>,
    usePlayerRanks: Boolean = true // false for team-level player ranks (e.g., NBA 30 teams)
) {
    // Player header
    ThreeColumnRow(
        leftText = awayPlayerName,
        centerText = positionLabel,
        rightText = homePlayerName,
        leftWeight = FontWeight.Bold,
        centerWeight = FontWeight.Bold,
        rightWeight = FontWeight.Bold,
        advantage = 0
    )

    // Stats rows
    statsConfig.forEach { config ->
        val awayStat = config.accessor(awayPlayerStats)
        val homeStat = config.accessor(homePlayerStats)
        val awayValue = awayStat.value
        val awayRank = awayStat.rank
        val awayRankDisplay = awayStat.rankDisplay
        val homeValue = homeStat.value
        val homeRank = homeStat.rank
        val homeRankDisplay = homeStat.rankDisplay

        // Calculate advantage based on values
        val advantage = if (awayValue != null && homeValue != null) {
            if (config.lowerIsBetter) when {
                awayValue < homeValue -> -1
                awayValue > homeValue -> 1
                else -> 0
            } else when {
                awayValue > homeValue -> -1
                awayValue < homeValue -> 1
                else -> 0
            }
        } else 0

        // Format values
        val awayText = awayValue?.let {
            if (config.decimals == 0) it.toInt().toString()
            else {
                val multiplier = when (config.decimals) {
                    1 -> 10.0
                    2 -> 100.0
                    3 -> 1000.0
                    else -> 10.0
                }
                val rounded = kotlin.math.round(it * multiplier) / multiplier
                val str = rounded.toString()
                if (str.contains('.')) {
                    val parts = str.split('.')
                    val decimalPart = parts[1].padEnd(config.decimals, '0').take(config.decimals)
                    "${parts[0]}.$decimalPart"
                } else {
                    "$str.${"0".repeat(config.decimals)}"
                }
            }
        } ?: "-"

        val homeText = homeValue?.let {
            if (config.decimals == 0) it.toInt().toString()
            else {
                val multiplier = when (config.decimals) {
                    1 -> 10.0
                    2 -> 100.0
                    3 -> 1000.0
                    else -> 10.0
                }
                val rounded = kotlin.math.round(it * multiplier) / multiplier
                val str = rounded.toString()
                if (str.contains('.')) {
                    val parts = str.split('.')
                    val decimalPart = parts[1].padEnd(config.decimals, '0').take(config.decimals)
                    "${parts[0]}.$decimalPart"
                } else {
                    "$str.${"0".repeat(config.decimals)}"
                }
            }
        } ?: "-"

        FiveColumnRowWithRanks(
            leftValue = awayText,
            leftRank = awayRank,
            leftRankDisplay = awayRankDisplay,
            centerText = config.label,
            rightValue = homeText,
            rightRank = homeRank,
            rightRankDisplay = homeRankDisplay,
            advantage = advantage,
            useNBARanks = !usePlayerRanks,
            usePlayerRanks = usePlayerRanks
        )
    }

    Spacer(modifier = Modifier.height(4.dp))
}
