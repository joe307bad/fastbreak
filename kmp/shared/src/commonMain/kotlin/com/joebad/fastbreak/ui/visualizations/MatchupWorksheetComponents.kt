package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.PlayoffChanceEntry
import com.joebad.fastbreak.data.model.RankingEntry
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.components.FabOption
import com.joebad.fastbreak.ui.components.MultiOptionFab

data class PlayoffExtraColumn(
    val label: String,
    val format: (PlayoffChanceEntry) -> String,
    val sortValue: (PlayoffChanceEntry) -> Double
)

private val MLB_STANDINGS_SECTION_ORDER = mapOf(
    "National" to listOf("NL East", "NL Central", "NL West", "Wild Card"),
    "American" to listOf("AL East", "AL Central", "AL West", "Wild Card")
)

internal fun formatPlayoffGamesBack(gamesBack: Double?): String {
    if (gamesBack == null || gamesBack <= 0.0) return "-"
    val rounded = kotlin.math.round(gamesBack * 10) / 10
    return if (kotlin.math.abs(rounded % 1.0) < 0.05) {
        rounded.toInt().toString()
    } else {
        val parts = rounded.toString().split('.')
        "${parts[0]}.${parts.getOrElse(1) { "0" }.padEnd(1, '0').take(1)}"
    }
}

// Wide enough for values like "12.5" in monospace bodySmall
private val PLAYOFF_GB_COLUMN_WIDTH = 32.dp
private val PLAYOFF_GB_COLUMN_WITH_SPACER = 36.dp

private fun mlbStandingsGamesBack(w: Int, l: Int, wRef: Int, lRef: Int): Double =
    ((wRef - w) + (l - lRef)) / 2.0

internal fun displayMlbStandingsGamesBack(
    sectionName: String,
    entry: PlayoffChanceEntry,
    sectionEntries: List<PlayoffChanceEntry>
): Double? {
    if (sectionName == "Wild Card") {
        return entry.gamesBackFromPlayoff
    }
    val leader = sectionEntries.firstOrNull { it.divisionRank == 1 }
        ?: sectionEntries.minByOrNull { it.divisionRank ?: Int.MAX_VALUE }
    val leaderWins = leader?.wins ?: return null
    val leaderLosses = leader?.losses ?: return null
    val wins = entry.wins ?: return null
    val losses = entry.losses ?: return null
    val gb = mlbStandingsGamesBack(wins, losses, leaderWins, leaderLosses)
    return if (gb <= 0.0) 0.0 else kotlin.math.round(gb * 10) / 10
}

private fun dedupeMlbPlayoffChanceEntries(entries: List<PlayoffChanceEntry>): List<PlayoffChanceEntry> {
    return entries
        .groupBy { it.team.uppercase() }
        .map { (_, group) ->
            group.firstOrNull { it.standingsSection != "Wild Card" } ?: group.first()
        }
}

private fun wildCardEntriesForConference(entries: List<PlayoffChanceEntry>): List<PlayoffChanceEntry> {
    return entries
        .filter { (it.divisionRank ?: Int.MAX_VALUE) > 1 }
        .sortedWith(
            compareByDescending<PlayoffChanceEntry> { it.winPct ?: 0.0 }
                .thenByDescending { it.wins ?: 0 }
                .thenBy { it.losses ?: Int.MAX_VALUE }
        )
}

private fun buildMlbStandingsConferenceGroups(
    entries: List<PlayoffChanceEntry>
): List<Pair<String, List<Pair<String, List<PlayoffChanceEntry>>>>>? {
    val uniqueEntries = dedupeMlbPlayoffChanceEntries(entries)
    if (uniqueEntries.none { !it.standingsSection.isNullOrBlank() && it.standingsSection != "Wild Card" }) {
        return null
    }
    return uniqueEntries.groupBy { it.conference ?: "Unknown" }
        .entries
        .sortedBy { it.key }
        .map { (conference, confEntries) ->
            val sectionOrder = MLB_STANDINGS_SECTION_ORDER[conference]
                ?: confEntries.mapNotNull { it.standingsSection }.distinct()
            val sections = sectionOrder.mapNotNull { section ->
                val sectionEntries = if (section == "Wild Card") {
                    wildCardEntriesForConference(confEntries)
                } else {
                    confEntries
                        .filter { it.standingsSection == section || it.division == section }
                        .sortedBy { it.divisionRank ?: Int.MAX_VALUE }
                }
                if (sectionEntries.isEmpty()) null else section to sectionEntries
            }
            conference to sections
        }
}

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
 * Get color for trend record rank (top 5 green, rest split orange/red)
 * Works for any league size.
 */
fun getTrendRecordRankColor(rank: Int?, totalTeams: Int = 30): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    if (rank <= 5) {
        // Green gradient for top 5
        val ratio = (rank - 1) / 4f
        val red = (0 + ratio * 60).toInt()
        val green = (150 + ratio * 30).toInt()
        return Color(red, green, 0)
    }
    val remaining = totalTeams - 5
    val midpoint = 5 + remaining / 2
    return if (rank <= midpoint) {
        // Orange gradient
        val ratio = (rank - 6).toFloat() / (midpoint - 6).coerceAtLeast(1).toFloat()
        val red = (255 - ratio * 55).toInt()
        val green = (140 - ratio * 40).toInt()
        Color(red, green, 0)
    } else {
        // Red gradient
        val ratio = (rank - midpoint - 1).toFloat() / (totalTeams - midpoint - 1).coerceAtLeast(1).toFloat()
        val red = (200 - ratio * 61).toInt()
        Color(red, 0, 0)
    }
}

/**
 * Pre-computed rank colors for NBA player stats
 * - Ranks 1-30: Green gradient (dark green to lighter green)
 * - Ranks 31-60: Orange to red gradient
 * - Ranks 61+: Dark red
 */
val nbaPlayerRankColors: Map<Int, Color> = buildMap {
    put(0, Color.Transparent) // For null ranks

    val darkGreen = Color(0, 120, 0)
    val darkOrange = Color(200, 120, 0)
    val darkRed = Color(139, 0, 0)

    for (rank in 1..100) {
        val color = when {
            rank <= 5 -> darkGreen
            rank <= 15 -> darkOrange
            rank <= 30 -> {
                // Gradient from dark orange to dark red (ranks 16-30)
                val ratio = (rank - 16) / 14f
                Color(
                    (200 + ratio * (139 - 200)).toInt(),
                    (120 - ratio * 120).toInt(),
                    0
                )
            }
            else -> darkRed
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
 * MLB player ranks (league-wide, hundreds of qualified players):
 * 1-30 green, 31-60 dark amber/yellow, 61-100 orange, 101+ red
 */
fun getMLBPlayerRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    return when {
        rank <= 30 -> {
            val ratio = (rank - 1) / 29f
            Color((0 + ratio * 50).toInt(), (150 - ratio * 35).toInt(), 0)
        }
        rank <= 60 -> {
            // Dark amber → burnt orange so white rank text stays readable
            val ratio = (rank - 31) / 29f
            Color((175 + ratio * 55).toInt(), (125 - ratio * 45).toInt(), 0)
        }
        rank <= 100 -> {
            val ratio = (rank - 61) / 39f
            Color((230 - ratio * 52).toInt(), (95 - ratio * 60).toInt(), 0)
        }
        else -> Color(178, 0, 0)
    }
}

/**
 * MLB team ranks (30 teams) for report card team/composite rankings.
 * Top 10 green, middle third orange, bottom third red.
 */
fun getMLBTeamRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    return when {
        rank <= 10 -> {
            val ratio = (rank - 1) / 9f
            Color((0 + ratio * 80).toInt(), (150 - ratio * 25).toInt(), (42 - ratio * 32).toInt())
        }
        rank <= 20 -> {
            val ratio = (rank - 11) / 9f
            Color(
                (200 - ratio * 45).toInt(),
                (95 - ratio * 35).toInt(),
                0
            )
        }
        else -> {
            val ratio = ((rank - 21).coerceAtMost(9)) / 9f
            Color((200 - ratio * 61).toInt(), (50 - ratio * 50).toInt(), 0)
        }
    }
}

/**
 * Pre-computed rank colors for CBB team stats (1-64 scale)
 * - Ranks 1-21: Green gradient
 * - Ranks 22-42: Orange gradient
 * - Ranks 43-64: Red gradient
 */
val cbbTeamRankColors: Map<Int, Color> = buildMap {
    put(0, Color.Transparent) // For null ranks

    // Darkest red color for rank 64+
    val darkestRed = Color(139, 0, 0)

    for (rank in 1..64) {
        val color = when {
            rank <= 21 -> {
                // Green gradient (ranks 1-21): Bright green to darker green
                val ratio = (rank - 1) / 20f
                val red = (0 + ratio * 80).toInt()
                val green = (150 + ratio * 30).toInt()
                val blue = 0
                Color(red, green, blue)
            }
            rank <= 42 -> {
                // Orange to dark orange gradient (ranks 22-42)
                val ratio = (rank - 22) / 20f
                val red = (255 - ratio * 55).toInt()
                val green = (140 - ratio * 40).toInt()
                val blue = 0
                Color(red, green, blue)
            }
            else -> {
                // Red to dark red gradient (ranks 43-64)
                val ratio = (rank - 43) / 21f
                val red = (200 - ratio * 61).toInt()
                val green = (0 + ratio * 0).toInt()
                val blue = 0
                Color(red, green, blue)
            }
        }
        put(rank, color)
    }

    // Add explicit mapping for ranks > 64 to use the darkest red
    for (rank in 65..100) {
        put(rank, darkestRed)
    }
}

/**
 * Get color for CBB team rank (1-64 scale)
 */
fun getCBBTeamRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    return cbbTeamRankColors[rank.coerceIn(1, 100)] ?: Color.Transparent
}

/**
 * Pre-computed rank colors for NHL team stats (1-32 scale)
 * - Ranks 1-10: Green gradient
 * - Ranks 11-20: Orange gradient
 * - Ranks 21-32: Red gradient
 */
val nhlTeamRankColors: Map<Int, Color> = buildMap {
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
                val green = 0
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
 * Get color for NHL team rank (1-32 scale)
 */
fun getNHLTeamRankColor(rank: Int?): Color {
    if (rank == null || rank <= 0) return Color.Transparent
    return nhlTeamRankColors[rank.coerceIn(1, 50)] ?: Color.Transparent
}

/**
 * Get color for AP rank (1-25 scale)
 * - Ranks 1-8: Green gradient
 * - Ranks 9-17: Orange gradient
 * - Ranks 18-25: Red gradient
 * - Unranked (null): Dark red
 */
fun getAPRankColor(rank: Int?): Color {
    // Unranked teams get dark red
    if (rank == null || rank <= 0) return Color(139, 0, 0)

    return when {
        rank <= 8 -> {
            // Green gradient (ranks 1-8)
            val ratio = (rank - 1) / 7f
            val red = (0 + ratio * 60).toInt()
            val green = (150 + ratio * 30).toInt()
            Color(red, green, 0)
        }
        rank <= 17 -> {
            // Orange gradient (ranks 9-17)
            val ratio = (rank - 9) / 8f
            val red = (255 - ratio * 55).toInt()
            val green = (140 - ratio * 40).toInt()
            Color(red, green, 0)
        }
        else -> {
            // Red gradient (ranks 18-25+)
            val ratio = ((rank - 18).coerceAtMost(7)) / 7f
            val red = (200 - ratio * 61).toInt()
            Color(red, 0, 0)
        }
    }
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
    centerSoftWrap: Boolean = false,
    leftSeasonHigh: com.joebad.fastbreak.data.model.SeasonHighEntry? = null,
    rightSeasonHigh: com.joebad.fastbreak.data.model.SeasonHighEntry? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val starColor = Color(0xFFE91E63)

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            if (leftSeasonHigh != null) {
                Box(
                    modifier = Modifier.size(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = starColor,
                        modifier = Modifier.requiredSize(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            } else if (advantage == -1) {
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
                fontSize = 10.sp,
                fontWeight = if (leftSeasonHigh != null) FontWeight.Bold else leftWeight,
                color = leftColor,
                maxLines = 1,
                softWrap = false
            )
        }

        Text(
            text = centerText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.Center,
            fontSize = 10.sp,
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
                fontSize = 10.sp,
                fontWeight = if (rightSeasonHigh != null) FontWeight.Bold else rightWeight,
                color = rightColor,
                maxLines = 1,
                softWrap = false
            )
            if (rightSeasonHigh != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier.size(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = starColor,
                        modifier = Modifier.requiredSize(10.dp)
                    )
                }
            } else if (advantage == 1) {
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
 * Shared rank badge used across matchup and report card rows.
 */
@Composable
fun MatchupRankBadge(
    rank: Int?,
    rankDisplay: String?,
    rankColorFn: ((Int?) -> Color)? = null,
    usePlayerRanks: Boolean = false,
    useCBBRanks: Boolean = false,
    useNHLRanks: Boolean = false,
    useNBARanks: Boolean = true,
    emptyPlaceholder: String = "-"
) {
    if (rank == null && rankDisplay.isNullOrBlank()) {
        if (emptyPlaceholder.isEmpty()) {
            Spacer(modifier = Modifier.width(32.dp))
            return
        }
    }
    val color = rankColorFn?.invoke(rank) ?: when {
        usePlayerRanks -> getNBAPlayerRankColor(rank)
        useCBBRanks -> getCBBTeamRankColor(rank)
        useNHLRanks -> getNHLTeamRankColor(rank)
        useNBARanks -> getNBATeamRankColor(rank)
        else -> Color.Gray
    }
    val badgeWidth = when (rankDisplay?.length ?: 0) {
        0, 1, 2 -> 32.dp
        3 -> 34.dp
        else -> 40.dp
    }
    Box(
        modifier = Modifier
            .width(badgeWidth)
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 3.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rankDisplay ?: emptyPlaceholder,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            softWrap = false
        )
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
    usePlayerRanks: Boolean = false, // true for player ranks (1-100+ scale), overrides useNBARanks
    useCBBRanks: Boolean = false, // true for CBB (64 teams), overrides useNBARanks
    useNHLRanks: Boolean = false, // true for NHL (32 teams), overrides useNBARanks
    onClick: (() -> Unit)? = null,
    rankColorFn: ((Int?) -> Color)? = null, // optional override for rank badge colors
    emptyRankPlaceholder: String = "-"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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

        MatchupRankBadge(
            rank = leftRank,
            rankDisplay = leftRankDisplay,
            rankColorFn = rankColorFn,
            usePlayerRanks = usePlayerRanks,
            useCBBRanks = useCBBRanks,
            useNHLRanks = useNHLRanks,
            useNBARanks = useNBARanks,
            emptyPlaceholder = emptyRankPlaceholder
        )

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

        MatchupRankBadge(
            rank = rightRank,
            rankDisplay = rightRankDisplay,
            rankColorFn = rankColorFn,
            usePlayerRanks = usePlayerRanks,
            useCBBRanks = useCBBRanks,
            useNHLRanks = useNHLRanks,
            useNBARanks = useNBARanks,
            emptyPlaceholder = emptyRankPlaceholder
        )

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
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val borderColor = if (isSelected) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
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
 * When final scores are provided, displays: "AWAY score +margin score HOME"
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
    awayScore: Int? = null,
    homeScore: Int? = null,
    modifier: Modifier = Modifier
) {
    val hasScore = awayScore != null && homeScore != null
    val homeWon = hasScore && homeScore!! > awayScore!!
    val margin = if (hasScore) kotlin.math.abs(homeScore!! - awayScore!!) else 0
    val isDarkTheme = isSystemInDarkTheme()
    val badgeBorderColor = if (isDarkTheme) Color.White else Color.Black

    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Away team + score (left)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                val awayWon = hasScore && !homeWon
                Text(
                    text = awayTeam,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                    fontSize = 11.sp,
                    fontWeight = if (awayWon) FontWeight.Bold else FontWeight.Normal,
                    color = if (awayWon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasScore) {
                    Spacer(modifier = Modifier.width(6.dp))
                    // Away score badge
                    Box(
                        modifier = Modifier
                            .border(1.dp, badgeBorderColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = awayScore.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                            fontSize = 11.sp,
                            fontWeight = if (awayWon) FontWeight.Bold else FontWeight.Normal,
                            color = if (awayWon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    // Margin if away won (outside badge)
                    if (awayWon) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+$margin",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            // Center spacer or @
            if (!hasScore) {
                Text(
                    text = "@",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }

            // Home team + score (right)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (hasScore) {
                    // Margin if home won (outside badge)
                    if (homeWon) {
                        Text(
                            text = "+$margin",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    // Home score badge
                    Box(
                        modifier = Modifier
                            .border(1.dp, badgeBorderColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = homeScore.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                            fontSize = 11.sp,
                            fontWeight = if (homeWon) FontWeight.Bold else FontWeight.Normal,
                            color = if (homeWon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = homeTeam,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                    fontSize = 11.sp,
                    fontWeight = if (homeWon) FontWeight.Bold else FontWeight.Normal,
                    color = if (homeWon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
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

        // Use ThreeColumnRow when no ranks are available, otherwise use FiveColumnRowWithRanks
        if (awayRank == null && homeRank == null) {
            ThreeColumnRow(
                leftText = awayText,
                centerText = config.label,
                rightText = homeText,
                advantage = advantage
            )
        } else {
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
    }

    Spacer(modifier = Modifier.height(4.dp))
}

private enum class ShareRange { FULL, TOP_HALF, BOTTOM_HALF, SPOTLIGHT }

/**
 * Window of entries centered on [anchor] used by the Spotlight share — the anchor
 * plus up to [radius] entries on each side, shifted to stay in bounds.
 */
private fun spotlightWindow(
    entries: List<RankingEntry>,
    anchor: Int,
    radius: Int = 5
): List<RankingEntry> {
    if (entries.isEmpty()) return entries
    val size = entries.size
    val center = anchor.coerceIn(0, size - 1)
    var start = center - radius
    var end = center + radius
    if (start < 0) { end -= start; start = 0 }
    if (end > size - 1) { start -= (end - (size - 1)); end = size - 1 }
    start = start.coerceAtLeast(0)
    return entries.subList(start, (end + 1).coerceAtMost(size))
}

private sealed interface PlayoffShareTarget {
    data object Full : PlayoffShareTarget
    data class Section(
        val label: String,
        val entries: List<PlayoffChanceEntry>,
        /** When set, share image renders this as a single standings section (e.g. Wild Card). */
        val standingsConference: String? = null,
        val standingsSection: String? = null
    ) : PlayoffShareTarget
}

@Composable
private fun shareImageThemeTextColors(): Pair<Color, Color> {
    val bg = MaterialTheme.colorScheme.background
    val isDark = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
    val textColor = if (isDark) Color.White else Color.Black
    val dimColor = if (isDark) Color.LightGray else Color.DarkGray
    return textColor to dimColor
}

private fun mlbWildCardShareLabel(conference: String): String =
    when (conference) {
        "American" -> "AL Wild Card"
        "National" -> "NL Wild Card"
        else -> "$conference Wild Card"
    }

private fun buildMlbDivisionShareFabOptions(
    standingsConferenceGroups: List<Pair<String, List<Pair<String, List<PlayoffChanceEntry>>>>>,
    onSelect: (PlayoffShareTarget) -> Unit
): List<FabOption> {
    return buildList {
        add(FabOption(Icons.Filled.Share, "Full List") { onSelect(PlayoffShareTarget.Full) })
        standingsConferenceGroups.forEach { (_, sections) ->
            sections.forEach { (sectionName, sectionEntries) ->
                if (sectionName != "Wild Card") {
                    add(FabOption(Icons.Filled.Share, sectionName) {
                        onSelect(PlayoffShareTarget.Section(sectionName, sectionEntries))
                    })
                }
            }
        }
        // Per-conference wild card races (after division options)
        standingsConferenceGroups.forEach { (conference, sections) ->
            val wildCardEntries = sections.firstOrNull { it.first == "Wild Card" }?.second
            if (!wildCardEntries.isNullOrEmpty()) {
                val label = mlbWildCardShareLabel(conference)
                add(FabOption(Icons.Filled.Share, label) {
                    onSelect(
                        PlayoffShareTarget.Section(
                            label = label,
                            entries = wildCardEntries,
                            standingsConference = conference,
                            standingsSection = "Wild Card"
                        )
                    )
                })
            }
        }
    }
}

private fun rankingEntryLabel(entry: RankingEntry): String =
    entry.player?.takeIf { it.isNotBlank() } ?: entry.team

private fun entriesArePlayerRankings(entries: List<RankingEntry>): Boolean =
    entries.any { !it.player.isNullOrBlank() }

@Composable
private fun StatRankingsShareImage(
    statLabel: String,
    subtitle: String,
    source: String,
    entries: List<RankingEntry>,
    rankColorFn: (Int?) -> Color,
    isPct: Boolean,
    highlightedTeams: Set<String> = emptySet()
) {
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onSurface
    val dimColor = onBg.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.primary
    val isPlayerRankings = entriesArePlayerRankings(entries)
    val nameHeader = if (isPlayerRankings) "PLAYER" else "TEAM"

    Column(
        modifier = Modifier
            .requiredWidth(340.dp)
            .background(bg)
            .padding(16.dp)
    ) {
        Text(statLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = onBg, maxLines = 1)
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = dimColor, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Header
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("RK", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = dimColor, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.width(4.dp))
            Text(nameHeader, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = dimColor, modifier = Modifier.weight(1f))
            Text("VALUE", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = dimColor, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
        }
        entries.forEach { entry ->
            val rankColor = rankColorFn(entry.rank)
            val isHighlighted = entry.team in highlightedTeams
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val y = size.height / 2
                        val startX = 32.dp.toPx() + 4.dp.toPx() + 40.dp.toPx()
                        val endX = size.width - 56.dp.toPx()
                        if (endX > startX) {
                            drawLine(
                                color = rankColor.copy(alpha = 0.25f),
                                start = androidx.compose.ui.geometry.Offset(startX, y),
                                end = androidx.compose.ui.geometry.Offset(endX, y),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                            )
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isHighlighted) {
                    Box(modifier = Modifier.size(5.dp).background(highlightColor, CircleShape))
                    Spacer(modifier = Modifier.width(3.dp))
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Box(
                    modifier = Modifier.width(32.dp).background(rankColor, RoundedCornerShape(3.dp)).padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(entry.rankDisplay, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (isPlayerRankings) {
                        "${rankingEntryLabel(entry)} (${entry.team})"
                    } else {
                        entry.team
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (isPlayerRankings) 11.sp else 12.sp,
                        fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.Medium
                    ),
                    color = if (isHighlighted) highlightColor else onBg,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isPct) formatPctValue(entry.value) else formatStatValue(entry.value),
                    fontSize = 12.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                    color = if (isHighlighted) highlightColor else onBg,
                    modifier = Modifier.width(56.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(source, fontSize = 9.sp, color = dimColor, maxLines = 1)
            Text("fbrk.app", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = dimColor)
        }
    }
}

/**
 * Static share image for playoff chances (rendered off-screen for capture)
 */
@Composable
private fun PlayoffChancesShareImage(
    title: String,
    subtitle: String,
    source: String,
    champLabel: String,
    entries: List<PlayoffChanceEntry>,
    probColorFn: (Double?) -> Color,
    champProbColorFn: (Double?) -> Color = probColorFn,
    highlightedTeams: Set<String> = emptySet(),
    playoffCutoff: Int = 0,
    playInCutoff: Int = 0,
    showPlayoffColumn: Boolean = true,
    extraColumns: List<PlayoffExtraColumn> = emptyList(),
    useStandingsLayout: Boolean = false,
    wildCardPlayoffCutoff: Int = 3,
    forcedStandingsConferenceGroups: List<Pair<String, List<Pair<String, List<PlayoffChanceEntry>>>>>? = null
) {
    val bg = MaterialTheme.colorScheme.background
    val (textColor, dimColor) = shareImageThemeTextColors()
    val highlightColor = MaterialTheme.colorScheme.primary

    val uniqueEntries = dedupeMlbPlayoffChanceEntries(entries)
    val standingsConferenceGroups = forcedStandingsConferenceGroups
        ?: if (useStandingsLayout) {
            buildMlbStandingsConferenceGroups(entries)
        } else {
            null
        }
    val showGamesBackColumn = useStandingsLayout &&
        uniqueEntries.any { it.gamesBackFromPlayoff != null }
    val hasConferenceData = uniqueEntries.any { it.conference != null }
    val conferenceGroups: List<Pair<String, List<PlayoffChanceEntry>>>? =
        if (standingsConferenceGroups != null) {
            null
        } else if (hasConferenceData) {
        uniqueEntries.groupBy { it.conference ?: "Unknown" }
            .entries
            .sortedBy { it.key }
            .map { (conf, teams) ->
                conf to teams.sortedByDescending { it.champProb ?: 0.0 }
            }
    } else null

    val extraWidth = if (extraColumns.isNotEmpty()) (38 * extraColumns.size).dp else 0.dp
    val gamesBackWidth = if (showGamesBackColumn) PLAYOFF_GB_COLUMN_WITH_SPACER else 0.dp
    Column(
        modifier = Modifier
            .requiredWidth(340.dp + extraWidth + gamesBackWidth)
            .background(bg)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = textColor, maxLines = 1)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = dimColor, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (standingsConferenceGroups != null) {
            standingsConferenceGroups.forEachIndexed { groupIndex, (confName, sections) ->
                if (groupIndex > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(confName.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = textColor.copy(alpha = 0.7f))
                sections.forEachIndexed { sectionIndex, (sectionName, sectionEntries) ->
                    if (sectionIndex > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(sectionName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = textColor.copy(alpha = 0.55f))
                    Spacer(modifier = Modifier.height(2.dp))
                    ShareImageHeaderRow(champLabel, textColor, extraColumns, showPlayoffColumn, showGamesBackColumn)
                    sectionEntries.forEachIndexed { index, entry ->
                        val seed = if (sectionName == "Wild Card") index + 1 else entry.divisionRank ?: (index + 1)
                        if (sectionName == "Wild Card" && showPlayoffColumn && wildCardPlayoffCutoff > 0 && seed == wildCardPlayoffCutoff + 1) {
                            ShareImageCutoffDivider("PLAYOFF CUTOFF", dimColor)
                        }
                        ShareImageTeamRow(
                            entry, seed, highlightedTeams, probColorFn, champProbColorFn, highlightColor, textColor, dimColor,
                            extraColumns, showPlayoffColumn, showGamesBackColumn,
                            gamesBack = if (showGamesBackColumn) {
                                displayMlbStandingsGamesBack(sectionName, entry, sectionEntries)
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        } else if (conferenceGroups != null) {
            conferenceGroups.forEachIndexed { groupIndex, (confName, confEntries) ->
                if (groupIndex > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                // Conference header
                Text(confName.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = textColor.copy(alpha = 0.7f))
                Spacer(modifier = Modifier.height(4.dp))
                // Column headers
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("#", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("TEAM", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.weight(1f))
                    extraColumns.forEach { col ->
                        Text(col.label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.End, modifier = Modifier.width(36.dp), maxLines = 1)
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    if (showPlayoffColumn) {
                        Text("PLAYOFF", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp), maxLines = 1)
                    }
                    Text(champLabel.uppercase(), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp), maxLines = 1)
                }
                confEntries.forEachIndexed { index, entry ->
                    val seed = index + 1
                    if (showPlayoffColumn && playoffCutoff > 0 && seed == playoffCutoff + 1) {
                        ShareImageCutoffDivider("PLAYOFF CUTOFF", dimColor)
                    }
                    if (showPlayoffColumn && playInCutoff > 0 && seed == playInCutoff + 1) {
                        ShareImageCutoffDivider("PLAY-IN CUTOFF", dimColor)
                    }
                    ShareImageTeamRow(entry, seed, highlightedTeams, probColorFn, champProbColorFn, highlightColor, textColor, dimColor, extraColumns, showPlayoffColumn)
                }
            }
        } else {
            // Flat list fallback
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(8.dp))
                Text("#", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.width(4.dp))
                Text("TEAM", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.weight(1f))
                extraColumns.forEach { col ->
                    Text(col.label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.End, modifier = Modifier.width(36.dp), maxLines = 1)
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (showPlayoffColumn) {
                    Text("PLAYOFF", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp), maxLines = 1)
                }
                Text(champLabel.uppercase(), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp), maxLines = 1)
            }
            entries.forEachIndexed { index, entry ->
                val seed = index + 1
                if (showPlayoffColumn && playoffCutoff > 0 && seed == playoffCutoff + 1) {
                    ShareImageCutoffDivider("PLAYOFF CUTOFF", dimColor)
                }
                if (showPlayoffColumn && playInCutoff > 0 && seed == playInCutoff + 1) {
                    ShareImageCutoffDivider("PLAY-IN CUTOFF", dimColor)
                }
                ShareImageTeamRow(entry, seed, highlightedTeams, probColorFn, champProbColorFn, highlightColor, textColor, dimColor, extraColumns, showPlayoffColumn)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(source, fontSize = 9.sp, color = dimColor, maxLines = 1)
            Text("fbrk.app", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = dimColor)
        }
    }
}

@Composable
private fun ShareImageHeaderRow(
    champLabel: String,
    textColor: Color,
    extraColumns: List<PlayoffExtraColumn>,
    showPlayoffColumn: Boolean,
    showGamesBackColumn: Boolean = false
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(8.dp))
        Text("#", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.width(4.dp))
        Text("TEAM", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.weight(1f))
        extraColumns.forEach { col ->
            Text(col.label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.End, modifier = Modifier.width(36.dp), maxLines = 1)
            Spacer(modifier = Modifier.width(2.dp))
        }
        if (showGamesBackColumn) {
            Text("GB", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.End, modifier = Modifier.width(PLAYOFF_GB_COLUMN_WIDTH), maxLines = 1)
            Spacer(modifier = Modifier.width(2.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (showPlayoffColumn) {
            Text("PLAYOFF", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp), maxLines = 1)
        }
        Text(champLabel.uppercase(), style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp), maxLines = 1)
    }
}

@Composable
private fun ShareImageTeamRow(
    entry: PlayoffChanceEntry,
    seed: Int,
    highlightedTeams: Set<String>,
    probColorFn: (Double?) -> Color,
    champProbColorFn: (Double?) -> Color,
    highlightColor: Color,
    textColor: Color,
    dimColor: Color,
    extraColumns: List<PlayoffExtraColumn> = emptyList(),
    showPlayoffColumn: Boolean = true,
    showGamesBackColumn: Boolean = false,
    gamesBack: Double? = null
) {
    val playoffColor = probColorFn(entry.playoffProb)
    val isHighlighted = entry.team in highlightedTeams
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isHighlighted) {
            Box(modifier = Modifier.size(5.dp).background(highlightColor, CircleShape))
            Spacer(modifier = Modifier.width(3.dp))
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("$seed", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            entry.team,
            fontSize = 12.sp,
            fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = if (isHighlighted) highlightColor else textColor,
            modifier = Modifier.weight(1f)
        )
        extraColumns.forEach { col ->
            Text(col.format(entry), fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, color = textColor, textAlign = TextAlign.End, modifier = Modifier.width(36.dp), maxLines = 1)
            Spacer(modifier = Modifier.width(2.dp))
        }
        if (showGamesBackColumn) {
            Text(formatPlayoffGamesBack(gamesBack ?: entry.gamesBackFromPlayoff), fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, color = textColor, textAlign = TextAlign.End, modifier = Modifier.width(PLAYOFF_GB_COLUMN_WIDTH), maxLines = 1)
            Spacer(modifier = Modifier.width(2.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (showPlayoffColumn) {
            Box(modifier = Modifier.width(50.dp).background(playoffColor, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
                Text(formatProb(entry.playoffProb), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        Box(modifier = Modifier.width(50.dp).background(champProbColorFn(entry.champProb), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Text(formatProb(entry.champProb), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun ShareImageCutoffDivider(label: String, dimColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).height(1.dp).drawBehind {
            drawLine(Color.Gray.copy(alpha = 0.4f), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        })
        Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = dimColor.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 4.dp))
        Box(modifier = Modifier.weight(1f).height(1.dp).drawBehind {
            drawLine(Color.Gray.copy(alpha = 0.4f), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
        })
    }
}

/**
 * Bottom sheet showing full league rankings for a stat
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatRankingsBottomSheet(
    statLabel: String,
    entries: List<RankingEntry>,
    onDismiss: () -> Unit,
    rankColorFn: (Int?) -> Color = ::getNBATeamRankColor,
    highlightedTeams: Set<String> = emptySet(),
    isPct: Boolean = false,
    subtitle: String = "",
    source: String = ""
) {
    var isReversed by remember { mutableStateOf(false) }
    val displayEntries = if (isReversed) entries.reversed() else entries
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTeams by remember { mutableStateOf(highlightedTeams) }
    val isPlayerRankings = entriesArePlayerRankings(entries)

    // Share capture state. Each range renders its own off-screen image into its
    // own graphics layer (keyed by range) so a half/spotlight capture can never
    // reuse a stale full-list frame left over in a shared layer.
    var shareRange by remember { mutableStateOf<ShareRange?>(null) }
    val imageExporter = remember { getImageExporter() }
    // Anchor for the Spotlight share: the first highlighted entry, else the middle.
    val spotlightAnchor = displayEntries.indexOfFirst { it.team in selectedTeams }
        .let { if (it >= 0) it else displayEntries.size / 2 }

    // Off-screen share image — one graphics layer per range (via key + remember)
    // so a slice never captures the full list's previously recorded frame.
    shareRange?.let { range ->
        key(range) {
            val rangeLayer = rememberGraphicsLayer()
            var layoutSize by remember { mutableStateOf(IntSize.Zero) }
            val mid = displayEntries.size / 2
            val shareEntries = when (range) {
                ShareRange.FULL -> displayEntries
                ShareRange.TOP_HALF -> displayEntries.take(mid.coerceAtLeast(1))
                ShareRange.BOTTOM_HALF -> {
                    val dropCount = mid.coerceAtMost(displayEntries.size - 1).coerceAtLeast(0)
                    displayEntries.drop(dropCount).ifEmpty { displayEntries }
                }
                ShareRange.SPOTLIGHT -> spotlightWindow(displayEntries, spotlightAnchor)
            }
            val rangeLabel = when (range) {
                ShareRange.TOP_HALF -> "Top Half"
                ShareRange.BOTTOM_HALF -> "Bottom Half"
                ShareRange.SPOTLIGHT -> "Spotlight"
                ShareRange.FULL -> ""
            }
            val rangeSubtitle = if (rangeLabel.isEmpty()) {
                subtitle
            } else {
                listOfNotNull(subtitle.takeIf { it.isNotBlank() }, rangeLabel).joinToString(" • ")
            }
            val shareTitle = if (rangeLabel.isNotEmpty()) "$statLabel - $rangeLabel" else statLabel

            LaunchedEffect(layoutSize) {
                if (layoutSize.width <= 0 || layoutSize.height <= 0) return@LaunchedEffect
                // Two frames: one for layout/draw of the sliced list, one to settle the layer.
                withFrameNanos { }
                withFrameNanos { }
                try {
                    val bitmap = rangeLayer.toImageBitmap()
                    imageExporter.shareImage(bitmap, shareTitle)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    shareRange = null
                }
            }

            CompositionLocalProvider(LocalDensity provides Density(4f, 1f)) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(unbounded = true)
                        .offset { IntOffset(-10000, 0) }
                        .onSizeChanged { size ->
                            if (size.width > 0 && size.height > 0) {
                                layoutSize = size
                            }
                        }
                        .drawWithContent {
                            rangeLayer.record(
                                size = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                            ) { this@drawWithContent.drawContent() }
                            drawLayer(rangeLayer)
                        }
                ) {
                    StatRankingsShareImage(
                        statLabel = statLabel,
                        subtitle = rangeSubtitle,
                        source = source,
                        entries = shareEntries,
                        rankColorFn = rankColorFn,
                        isPct = isPct,
                        highlightedTeams = selectedTeams
                    )
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Title with sort toggle caret
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = statLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isReversed) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isReversed) "Sort best first" else "Sort worst first",
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { isReversed = !isReversed },
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header row
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RK",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlayerRankings) "PLAYER" else "TEAM",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = if (isPlayerRankings) {
                                Modifier.weight(1f)
                            } else {
                                Modifier.width(40.dp)
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "VALUE",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(60.dp)
                        )
                    }

                    displayEntries.forEach { entry ->
                        val isSelected = entry.team in selectedTeams
                        val rankColor = rankColorFn(entry.rank)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelected) Modifier.background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        RoundedCornerShape(4.dp)
                                    ) else Modifier
                                )
                                .clickable {
                                    selectedTeams = if (entry.team in selectedTeams) selectedTeams - entry.team else selectedTeams + entry.team
                                }
                                .drawBehind {
                                    val y = size.height / 2
                                    val startX = 32.dp.toPx() + 6.dp.toPx() + 42.dp.toPx()
                                    val endX = startX + 12.dp.toPx()
                                    if (endX > startX) {
                                        drawLine(
                                            color = rankColor.copy(alpha = if (isSelected) 0.3f else 0.12f),
                                            start = androidx.compose.ui.geometry.Offset(startX, y),
                                            end = androidx.compose.ui.geometry.Offset(endX, y),
                                            strokeWidth = 1.dp.toPx(),
                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                                        )
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rank badge
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .background(rankColor, RoundedCornerShape(4.dp))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = entry.rankDisplay,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Team or player label — same monospace as the value column
                            Text(
                                text = if (isPlayerRankings) {
                                    "${rankingEntryLabel(entry)} (${entry.team})"
                                } else {
                                    entry.team
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                modifier = if (isPlayerRankings) {
                                    Modifier.weight(1f)
                                } else {
                                    Modifier.width(40.dp)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // Value
                            Text(
                                text = if (isPct) formatPctValue(entry.value) else formatStatValue(entry.value),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(60.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(72.dp)) // Space for FAB
                }
            }

            // Share FAB
            val shareOptions = buildList {
                add(FabOption(Icons.Filled.Share, "Full List") {
                    shareRange = ShareRange.FULL
                })
                add(FabOption(Icons.Filled.Share, "Top Half") {
                    shareRange = ShareRange.TOP_HALF
                })
                add(FabOption(Icons.Filled.Share, "Bottom Half") {
                    shareRange = ShareRange.BOTTOM_HALF
                })
                // Spotlight the highlighted entry and the ten players around it —
                // only useful when there's a highlight and more entries than the window.
                if (displayEntries.any { it.team in selectedTeams } && displayEntries.size > 11) {
                    add(FabOption(Icons.Filled.Star, "Spotlight") {
                        shareRange = ShareRange.SPOTLIGHT
                    })
                }
            }
            MultiOptionFab(
                options = shareOptions,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}

private fun formatStatValue(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    val str = rounded.toString()
    return if (str.contains('.')) {
        val parts = str.split('.')
        "${parts[0]}.${parts[1].padEnd(1, '0').take(2)}"
    } else str
}

private fun formatPctValue(value: Double): String {
    // Values already in percentage form (e.g., 48.5) or decimal (e.g., 0.485)
    val pctVal = if (value <= 1.0) value * 100 else value
    val rounded = kotlin.math.round(pctVal * 10) / 10
    val str = rounded.toString()
    return if (str.contains('.')) {
        val parts = str.split('.')
        "${parts[0]}.${parts[1].take(1)}%"
    } else "$str.0%"
}

private fun formatProb(prob: Double?): String {
    if (prob == null) return "-"
    return when {
        prob >= 99.5 -> ">99%"
        prob in 0.0001..0.9999 -> "<1%"
        else -> "${prob.toInt()}%"
    }
}

/**
 * Bottom sheet showing full league playoff and championship chances
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayoffChancesBottomSheet(
    title: String,
    champLabel: String,
    entries: List<PlayoffChanceEntry>,
    onDismiss: () -> Unit,
    probColorFn: (Double?) -> Color,
    champProbColorFn: (Double?) -> Color = probColorFn,
    highlightedTeams: Set<String> = emptySet(),
    subtitle: String = "",
    source: String = "",
    playoffCutoff: Int = 0,
    playInCutoff: Int = 0,
    showPlayoffColumn: Boolean = true,
    extraColumns: List<PlayoffExtraColumn> = emptyList(),
    useStandingsLayout: Boolean = false,
    wildCardPlayoffCutoff: Int = 3
) {
    var isReversed by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTeams by remember { mutableStateOf(highlightedTeams) }

    val hasConferenceData = entries.any { it.conference != null }
    val uniqueEntries = dedupeMlbPlayoffChanceEntries(entries)
    val standingsConferenceGroups = if (useStandingsLayout) {
        buildMlbStandingsConferenceGroups(entries)
    } else {
        null
    }
    val showGamesBackColumn = useStandingsLayout &&
        uniqueEntries.any { it.gamesBackFromPlayoff != null }

    // Sort by champProb during postseason (when showPlayoffColumn is false),
    // otherwise by first extra column if available, otherwise by champProb
    val sortSelector: (PlayoffChanceEntry) -> Double = if (!showPlayoffColumn) {
        { it.champProb ?: 0.0 }
    } else if (extraColumns.isNotEmpty()) {
        extraColumns.first().sortValue
    } else {
        { it.champProb ?: 0.0 }
    }

    // Build conference groups: list of (conference name, sorted teams)
    val conferenceGroups: List<Pair<String, List<PlayoffChanceEntry>>>? =
        if (standingsConferenceGroups != null) {
            null
        } else if (hasConferenceData) {
        uniqueEntries.groupBy { it.conference ?: "Unknown" }
            .entries
            .sortedBy { it.key }
            .map { (conf, teams) ->
                val sorted = if (isReversed) {
                    teams.sortedBy { sortSelector(it) }
                } else {
                    teams.sortedByDescending { sortSelector(it) }
                }
                conf to sorted
            }
    } else null

    val displayEntries = if (isReversed) uniqueEntries.reversed() else uniqueEntries

    // Share capture state
    var shareTarget by remember { mutableStateOf<PlayoffShareTarget?>(null) }
    val graphicsLayer = rememberGraphicsLayer()
    val imageExporter = remember { getImageExporter() }

    LaunchedEffect(shareTarget) {
        if (shareTarget != null) {
            kotlinx.coroutines.delay(100)
            try {
                val bitmap = graphicsLayer.toImageBitmap()
                val target = shareTarget!!
                val rangeLabel = when (target) {
                    is PlayoffShareTarget.Section -> target.label
                    PlayoffShareTarget.Full -> ""
                }
                val shareTitle = if (rangeLabel.isNotEmpty()) "$title - $rangeLabel" else title
                imageExporter.shareImage(bitmap, shareTitle)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                shareTarget = null
            }
        }
    }

    // Off-screen share image
    shareTarget?.let { target ->
        val shareEntries = when (target) {
            PlayoffShareTarget.Full -> uniqueEntries
            is PlayoffShareTarget.Section -> target.entries
        }
        val rangeSubtitle = when (target) {
            is PlayoffShareTarget.Section -> listOfNotNull(subtitle, target.label)
                .filter { it.isNotBlank() }
                .joinToString(" • ")
            PlayoffShareTarget.Full -> subtitle
        }
        val forcedStandingsGroups = when (target) {
            is PlayoffShareTarget.Section -> {
                val conference = target.standingsConference
                val section = target.standingsSection
                if (conference != null && section != null) {
                    listOf(conference to listOf(section to target.entries))
                } else {
                    null
                }
            }
            PlayoffShareTarget.Full -> null
        }
        CompositionLocalProvider(LocalDensity provides Density(4f, 1f)) {
            Box(
                modifier = Modifier
                    .wrapContentSize(unbounded = true)
                    .offset { IntOffset(-10000, 0) }
                    .drawWithContent {
                        graphicsLayer.record(
                            size = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                        ) { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
            ) {
                PlayoffChancesShareImage(
                    title = title,
                    subtitle = rangeSubtitle,
                    source = source,
                    champLabel = champLabel,
                    entries = shareEntries,
                    probColorFn = probColorFn,
                    champProbColorFn = champProbColorFn,
                    highlightedTeams = selectedTeams,
                    playoffCutoff = playoffCutoff,
                    playInCutoff = playInCutoff,
                    showPlayoffColumn = showPlayoffColumn,
                    extraColumns = extraColumns,
                    useStandingsLayout = useStandingsLayout,
                    wildCardPlayoffCutoff = wildCardPlayoffCutoff,
                    forcedStandingsConferenceGroups = forcedStandingsGroups
                )
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Title with sort toggle caret
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    if (standingsConferenceGroups == null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (isReversed) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isReversed) "Sort best first" else "Sort worst first",
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { isReversed = !isReversed },
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (standingsConferenceGroups != null) {
                        standingsConferenceGroups.forEachIndexed { groupIndex, (confName, sections) ->
                            if (groupIndex > 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            Text(
                                text = confName.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            sections.forEachIndexed { sectionIndex, (sectionName, sectionEntries) ->
                                if (sectionIndex > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }

                                Text(
                                    text = sectionName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )

                                PlayoffChancesHeaderRow(
                                    champLabel = champLabel,
                                    extraColumns = extraColumns,
                                    showPlayoffColumn = showPlayoffColumn,
                                    showGamesBackColumn = showGamesBackColumn
                                )

                                sectionEntries.forEachIndexed { index, entry ->
                                    val seed = if (sectionName == "Wild Card") {
                                        index + 1
                                    } else {
                                        entry.divisionRank ?: (index + 1)
                                    }
                                    if (sectionName == "Wild Card" &&
                                        showPlayoffColumn &&
                                        wildCardPlayoffCutoff > 0 &&
                                        seed == wildCardPlayoffCutoff + 1
                                    ) {
                                        PlayoffCutoffDivider()
                                    }
                                    PlayoffChanceTeamRow(
                                        entry = entry,
                                        seed = seed,
                                        isSelected = entry.team in selectedTeams,
                                        probColorFn = probColorFn,
                                        champProbColorFn = champProbColorFn,
                                        extraColumns = extraColumns,
                                        showPlayoffColumn = showPlayoffColumn,
                                        showGamesBackColumn = showGamesBackColumn,
                                        gamesBack = if (showGamesBackColumn) {
                                            displayMlbStandingsGamesBack(sectionName, entry, sectionEntries)
                                        } else {
                                            null
                                        },
                                        onToggle = {
                                            selectedTeams = if (entry.team in selectedTeams) {
                                                selectedTeams - entry.team
                                            } else {
                                                selectedTeams + entry.team
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else if (conferenceGroups != null) {
                        // Conference-grouped layout
                        conferenceGroups.forEachIndexed { groupIndex, (confName, confEntries) ->
                            if (groupIndex > 0) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Conference header
                            Text(
                                text = confName.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            // Column headers
                            PlayoffChancesHeaderRow(
                                champLabel = champLabel,
                                extraColumns = extraColumns,
                                showPlayoffColumn = showPlayoffColumn,
                                showGamesBackColumn = showGamesBackColumn
                            )

                            confEntries.forEachIndexed { index, entry ->
                                val seed = index + 1
                                // Playoff cutoff divider (only show during regular season)
                                if (showPlayoffColumn && playoffCutoff > 0 && seed == playoffCutoff + 1) {
                                    PlayoffCutoffDivider()
                                }
                                // Play-in cutoff divider (only show during regular season)
                                if (showPlayoffColumn && playInCutoff > 0 && seed == playInCutoff + 1) {
                                    PlayoffCutoffDivider("PLAY-IN CUTOFF")
                                }
                                PlayoffChanceTeamRow(
                                    entry = entry,
                                    seed = seed,
                                    isSelected = entry.team in selectedTeams,
                                    probColorFn = probColorFn,
                                    champProbColorFn = champProbColorFn,
                                    extraColumns = extraColumns,
                                    showPlayoffColumn = showPlayoffColumn,
                                    showGamesBackColumn = showGamesBackColumn,
                                    onToggle = {
                                        selectedTeams = if (entry.team in selectedTeams) selectedTeams - entry.team else selectedTeams + entry.team
                                    }
                                )
                            }
                        }
                    } else {
                        // Flat list fallback (no conference data)
                        PlayoffChancesHeaderRow(
                            champLabel = champLabel,
                            extraColumns = extraColumns,
                            showPlayoffColumn = showPlayoffColumn,
                            showGamesBackColumn = showGamesBackColumn
                        )
                        displayEntries.forEachIndexed { index, entry ->
                            val seed = index + 1
                            if (showPlayoffColumn && playoffCutoff > 0 && seed == playoffCutoff + 1) {
                                PlayoffCutoffDivider()
                            }
                            if (showPlayoffColumn && playInCutoff > 0 && seed == playInCutoff + 1) {
                                PlayoffCutoffDivider("PLAY-IN CUTOFF")
                            }
                            PlayoffChanceTeamRow(
                                entry = entry,
                                seed = seed,
                                isSelected = entry.team in selectedTeams,
                                probColorFn = probColorFn,
                                champProbColorFn = champProbColorFn,
                                extraColumns = extraColumns,
                                showPlayoffColumn = showPlayoffColumn,
                                showGamesBackColumn = showGamesBackColumn,
                                onToggle = {
                                    selectedTeams = if (entry.team in selectedTeams) selectedTeams - entry.team else selectedTeams + entry.team
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(72.dp)) // Space for FAB
                }
            }

            // Share FAB
            MultiOptionFab(
                options = when {
                    standingsConferenceGroups != null -> {
                        buildMlbDivisionShareFabOptions(standingsConferenceGroups) { shareTarget = it }
                    }
                    hasConferenceData && conferenceGroups != null -> {
                    listOf(
                        FabOption(Icons.Filled.Share, "Full List") { shareTarget = PlayoffShareTarget.Full },
                        FabOption(Icons.Filled.Share, conferenceGroups.firstOrNull()?.first ?: "First") {
                            conferenceGroups.firstOrNull()?.second?.let { entries ->
                                shareTarget = PlayoffShareTarget.Section(
                                    conferenceGroups.firstOrNull()?.first ?: "First",
                                    entries
                                )
                            }
                        },
                        FabOption(Icons.Filled.Share, conferenceGroups.lastOrNull()?.first ?: "Second") {
                            conferenceGroups.lastOrNull()?.second?.let { entries ->
                                shareTarget = PlayoffShareTarget.Section(
                                    conferenceGroups.lastOrNull()?.first ?: "Second",
                                    entries
                                )
                            }
                        }
                    )
                    }
                    else -> {
                    listOf(
                        FabOption(Icons.Filled.Share, "Full List") { shareTarget = PlayoffShareTarget.Full },
                        FabOption(Icons.Filled.Share, "Top Half") {
                            val mid = displayEntries.size / 2
                            shareTarget = PlayoffShareTarget.Section(
                                "Top Half",
                                displayEntries.take(mid)
                            )
                        },
                        FabOption(Icons.Filled.Share, "Bottom Half") {
                            val mid = displayEntries.size / 2
                            shareTarget = PlayoffShareTarget.Section(
                                "Bottom Half",
                                displayEntries.drop(mid)
                            )
                        }
                    )
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}

@Composable
private fun PlayoffChancesHeaderRow(
    champLabel: String,
    extraColumns: List<PlayoffExtraColumn> = emptyList(),
    showPlayoffColumn: Boolean = true,
    showGamesBackColumn: Boolean = false,
    gamesBack: Double? = null
) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "TEAM",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(40.dp)
        )
        extraColumns.forEach { col ->
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = col.label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp),
                maxLines = 1
            )
        }
        if (showGamesBackColumn) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "GB",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.End,
                modifier = Modifier.width(PLAYOFF_GB_COLUMN_WIDTH)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (showPlayoffColumn) {
            Text(
                text = "PLAYOFF",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(52.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = champLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp)
        )
    }
}

@Composable
private fun PlayoffCutoffDivider(label: String = "PLAYOFF CUTOFF") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .drawBehind {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    )
                }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .drawBehind {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.4f),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                    )
                }
        )
    }
}

@Composable
private fun PlayoffChanceTeamRow(
    entry: PlayoffChanceEntry,
    seed: Int,
    isSelected: Boolean,
    probColorFn: (Double?) -> Color,
    champProbColorFn: (Double?) -> Color,
    extraColumns: List<PlayoffExtraColumn> = emptyList(),
    showPlayoffColumn: Boolean = true,
    showGamesBackColumn: Boolean = false,
    gamesBack: Double? = null,
    onToggle: () -> Unit
) {
    val playoffColor = probColorFn(entry.playoffProb)
    val extraColumnsWidth = if (extraColumns.isNotEmpty()) (40 * extraColumns.size).dp else 0.dp
    val gamesBackWidth = if (showGamesBackColumn) PLAYOFF_GB_COLUMN_WITH_SPACER else 0.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    RoundedCornerShape(4.dp)
                ) else Modifier
            )
            .clickable { onToggle() }
            .drawBehind {
                if (showPlayoffColumn) {
                    val y = size.height / 2
                    val startX = 66.dp.toPx() + extraColumnsWidth.toPx() + gamesBackWidth.toPx()
                    val endX = startX + 12.dp.toPx()
                    if (endX > startX) {
                        drawLine(
                            color = playoffColor.copy(alpha = if (isSelected) 0.3f else 0.12f),
                            start = androidx.compose.ui.geometry.Offset(startX, y),
                            end = androidx.compose.ui.geometry.Offset(endX, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                        )
                    }
                }
            }
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Seed number
        Text(
            text = "$seed",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.width(20.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Team abbreviation
        Text(
            text = entry.team,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(40.dp)
        )

        // Extra stat columns
        extraColumns.forEach { col ->
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = col.format(entry),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }

        if (showGamesBackColumn) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatPlayoffGamesBack(gamesBack ?: entry.gamesBackFromPlayoff),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.width(PLAYOFF_GB_COLUMN_WIDTH),
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Playoff prob badge (only show during regular season)
        if (showPlayoffColumn) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .background(playoffColor, RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatProb(entry.playoffProb),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
        }

        // Champ prob badge
        Box(
            modifier = Modifier
                .width(52.dp)
                .background(champProbColorFn(entry.champProb), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatProb(entry.champProb),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}
