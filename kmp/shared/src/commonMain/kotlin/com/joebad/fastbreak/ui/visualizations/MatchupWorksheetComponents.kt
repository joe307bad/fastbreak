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
 * Pre-computed rank colors for NBA team stats (1-30 scale, with 30+ clamped to darkest red)
 */
val nbaTeamRankColors: Map<Int, Color> = buildMap {
    put(0, Color.Transparent) // For null ranks

    // Darkest red color for rank 30
    val darkestRed = Color(139, 0, 0)

    for (rank in 1..30) {
        val color = when {
            rank <= 10 -> {
                // Green gradient (ranks 1-10): Top third
                val ratio = (rank - 1) / 9f
                val red = (0 + ratio * 140).toInt()
                val green = (120 + ratio * 40).toInt()
                val blue = 0
                Color(red, green, blue)
            }
            rank <= 20 -> {
                // Yellow-orange gradient (ranks 11-20): Middle third
                val ratio = (rank - 11) / 9f
                val red = (140 + ratio * 75).toInt()
                val green = (160 - ratio * 100).toInt()
                val blue = 0
                Color(red, green, blue)
            }
            else -> {
                // Red gradient (ranks 21-30): Bottom third
                val ratio = (rank - 21) / 9f
                val red = (215 - ratio * 76).toInt()
                val green = (60 - ratio * 60).toInt()
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
    useNBARanks: Boolean = true // true for NBA (30 teams), false for NFL (32 teams)
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
                    if (useNBARanks) getNBATeamRankColor(leftRank) else Color.Gray,
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
                    if (useNBARanks) getNBATeamRankColor(rightRank) else Color.Gray,
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
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
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
            useNBARanks = !usePlayerRanks
        )
    }

    Spacer(modifier = Modifier.height(4.dp))
}
