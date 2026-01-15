package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.platform.getImageExporter
import com.joebad.fastbreak.ui.QuadrantScatterPlot
import com.joebad.fastbreak.ui.components.ShareFab
import kotlin.math.round
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

// Team colors for advantage indicators
private val Team1Color = Color(0xFF2196F3) // Blue (away team)
private val Team2Color = Color(0xFFFF5722) // Deep Orange (home team)

/**
 * Pre-computed rank colors for team/QB stats (1-32 scale, with 32+ clamped to darkest red)
 * Computed once at initialization to eliminate calculation overhead during scrolling
 * Color gradient: Green (ranks 1-10, top third) -> Yellow/Orange (ranks 11-20, middle third) -> Red (ranks 21-32, bottom third)
 */
private val teamRankColors: Map<Int, Color> = buildMap {
    put(0, Color.Transparent) // For null ranks

    // Darkest red color for rank 32 (will be used for all ranks > 32)
    val darkestRed = Color(139, 0, 0)

    for (rank in 1..32) {
        val color = when {
            rank <= 10 -> {
                // Green gradient (ranks 1-10): Top third - bright green to yellow-green
                val ratio = (rank - 1) / 9f
                val red = (0 + ratio * 140).toInt()
                val green = (120 + ratio * 40).toInt()
                val blue = 0
                Color(red, green, blue)
            }
            rank <= 20 -> {
                // Yellow-orange gradient (ranks 11-20): Middle third - yellow to orange
                val ratio = (rank - 11) / 9f
                val red = (140 + ratio * 75).toInt()
                val green = (160 - ratio * 100).toInt()
                val blue = 0
                Color(red, green, blue)
            }
            else -> {
                // Red gradient (ranks 21-32): Bottom third - orange-red to darkest red
                val ratio = (rank - 21) / 11f
                val red = (215 - ratio * 76).toInt()
                val green = (60 - ratio * 60).toInt()
                val blue = 0
                Color(red, green, blue)
            }
        }
        put(rank, color)
    }

    // Add explicit mapping for ranks > 32 to use the darkest red
    for (rank in 33..50) {
        put(rank, darkestRed)
    }
}

/**
 * Pre-computed rank colors for player stats (1-80 scale, with 80+ clamped to darkest red)
 * Computed once at initialization to eliminate calculation overhead during scrolling
 */
private val playerRankColors: Map<Int, Color> = buildMap {
    put(0, Color.Transparent) // For null ranks

    // Darkest red color for rank 80 (will be used for all ranks > 80)
    val darkestRed = Color(139, 0, 0)

    for (rank in 1..80) {
        val clampedRank = if (rank > 64) 64 else rank
        val color = when {
            clampedRank <= 21 -> {
                val ratio = (clampedRank - 1) / 20f
                val red = (0 + ratio * 50).toInt()
                val green = (100 + ratio * 105).toInt()
                val blue = (0 + ratio * 50).toInt()
                Color(red, green, blue)
            }
            clampedRank <= 43 -> {
                val ratio = (clampedRank - 22) / 21f
                val red = (180 + ratio * 75).toInt()
                val green = (140 - ratio * 41).toInt()
                val blue = (50 - ratio * 21).toInt()
                Color(red, green, blue)
            }
            else -> {
                val ratio = (clampedRank - 44) / 20f
                val red = (205 - ratio * 66).toInt()
                val green = (92 - ratio * 92).toInt()
                val blue = (92 - ratio * 92).toInt()
                Color(red, green, blue)
            }
        }
        put(rank, color)
    }

    // Add explicit mapping for ranks > 80 to use the darkest red
    // Extend to 300 to cover all possible player ranks
    for (rank in 81..300) {
        put(rank, darkestRed)
    }
}

/**
 * Fast O(1) lookup for team rank colors - eliminates per-frame color calculations
 */
private fun getTeamRankColor(rank: Int?): Color {
    return teamRankColors[rank ?: 0] ?: Color.Transparent
}

/**
 * Fast O(1) lookup for player rank colors - eliminates per-frame color calculations
 * For ranks > 300, returns darkest red as fallback
 */
private fun getPlayerRankColor(rank: Int?): Color {
    if (rank == null || rank == 0) return Color.Transparent
    // Return darkest red for any rank not in the map (should cover ranks > 300)
    return playerRankColors[rank] ?: Color(139, 0, 0)
}

/**
 * Helper function to format a Double to display with appropriate precision
 * Shows up to 3 decimal places (thousandths) when values have that precision
 */
private fun Double.format(decimals: Int): String {
    // Always format to 3 decimal places to preserve precision from JSON
    val multiplier = 1000.0
    val rounded = kotlin.math.round(this * multiplier) / multiplier
    val formatted = rounded.toString()

    // If the string already has the right format, return it
    // Otherwise ensure we have up to 3 decimal places
    return if (formatted.contains('.')) {
        val parts = formatted.split('.')
        val decimalPart = parts[1].padEnd(3, '0').take(3)
        val result = "${parts[0]}.$decimalPart"
        // Remove trailing zeros
        result.trimEnd('0').trimEnd('.')
    } else {
        formatted
    }
}

/**
 * Format game datetime string to display format
 * Input: "2026-01-10T16:30:00-05:00"
 * Output: "Friday January 10, 2026 @ 4:30pm ET"
 */
private fun formatGameDateTime(datetime: String): String {
    return try {
        // Parse the ISO 8601 datetime string
        // Format: "2026-01-10T16:30:00-05:00"
        val parts = datetime.split("T")
        if (parts.size != 2) return datetime

        val datePart = parts[0]
        val timePart = parts[1].substringBefore("-").substringBefore("+")

        // Parse date components
        val dateComponents = datePart.split("-")
        if (dateComponents.size != 3) return datetime

        val year = dateComponents[0].toIntOrNull() ?: return datetime
        val month = dateComponents[1].toIntOrNull() ?: return datetime
        val day = dateComponents[2].toIntOrNull() ?: return datetime

        // Parse time components
        val timeComponents = timePart.split(":")
        if (timeComponents.size < 2) return datetime

        val hour = timeComponents[0].toIntOrNull() ?: return datetime
        val minute = timeComponents[1].toIntOrNull() ?: return datetime

        // Format day of week (simple calculation - not perfectly accurate but close enough)
        val dayOfWeek = getDayOfWeek(year, month, day)

        // Format month name
        val monthName = getMonthName(month)

        // Format time (12-hour with am/pm)
        val (hour12, ampm) = if (hour == 0) {
            Pair(12, "am")
        } else if (hour < 12) {
            Pair(hour, "am")
        } else if (hour == 12) {
            Pair(12, "pm")
        } else {
            Pair(hour - 12, "pm")
        }

        val minuteStr = if (minute == 0) "" else ":${minute.toString().padStart(2, '0')}"

        "$dayOfWeek $monthName $day, $year @ $hour12$minuteStr$ampm ET"
    } catch (e: Exception) {
        datetime
    }
}

/**
 * Get day of week name from date components
 */
private fun getDayOfWeek(year: Int, month: Int, day: Int): String {
    // Zeller's congruence algorithm
    val adjustedMonth = if (month < 3) month + 12 else month
    val adjustedYear = if (month < 3) year - 1 else year

    val q = day
    val m = adjustedMonth
    val k = adjustedYear % 100
    val j = adjustedYear / 100

    val h = (q + ((13 * (m + 1)) / 5) + k + (k / 4) + (j / 4) - (2 * j)) % 7

    // Zeller's congruence returns: 0=Saturday, 1=Sunday, 2=Monday, etc.
    return when (h % 7) {
        0 -> "Saturday"
        1 -> "Sunday"
        2 -> "Monday"
        3 -> "Tuesday"
        4 -> "Wednesday"
        5 -> "Thursday"
        6 -> "Friday"
        else -> ""
    }
}

/**
 * Get month name from month number
 */
private fun getMonthName(month: Int): String {
    return when (month) {
        1 -> "January"
        2 -> "February"
        3 -> "March"
        4 -> "April"
        5 -> "May"
        6 -> "June"
        7 -> "July"
        8 -> "August"
        9 -> "September"
        10 -> "October"
        11 -> "November"
        12 -> "December"
        else -> ""
    }
}

/**
 * Helper to safely get a JsonObject from another JsonObject
 */
private fun JsonObject.getObject(key: String): JsonObject? {
    return this[key]?.jsonObject
}

/**
 * Helper to safely get a String from a JsonObject
 */
private fun JsonObject.getString(key: String): String? {
    return this[key]?.jsonPrimitive?.contentOrNull
}

/**
 * Helper to safely get a Double from a JsonObject
 */
private fun JsonObject.getDouble(key: String): Double? {
    return this[key]?.jsonPrimitive?.doubleOrNull
}

/**
 * Helper to safely get an Int from a JsonObject
 */
private fun JsonObject.getInt(key: String): Int? {
    return this[key]?.jsonPrimitive?.intOrNull
}

/**
 * Generic three-column layout component for consistent compact formatting
 */
@Composable
private fun ThreeColumnRow(
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
 * Five-column layout component with color-coded rank boxes
 * Layout: [value] [rank] [label] [rank] [value]
 */
@Composable
private fun FiveColumnRowWithRanks(
    leftValue: String,
    leftRank: Int?,  // Numeric rank for color coding
    leftRankDisplay: String?,  // Display string with "T" prefix for ties
    centerText: String,
    rightValue: String,
    rightRank: Int?,  // Numeric rank for color coding
    rightRankDisplay: String?,  // Display string with "T" prefix for ties
    advantage: Int = 0,
    usePlayerRankColors: Boolean = false // true for player stats (64 scale), false for team stats (32 scale)
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
                    if (usePlayerRankColors) getPlayerRankColor(leftRank) else getTeamRankColor(leftRank),
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

        // Center label (no word breaks, more weight for longer labels)
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
                    if (usePlayerRankColors) getPlayerRankColor(rightRank) else getTeamRankColor(rightRank),
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
 * Navigation badge for Stats/Charts toggle
 */
@Composable
private fun NavigationBadge(
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

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * Matchup badge showing team abbreviations
 */
@Composable
private fun MatchupBadge(
    awayTeam: String,
    homeTeam: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = awayTeam,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
            Text(
                text = "vs",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = textColor.copy(alpha = 0.7f)
            )
            Text(
                text = homeTeam,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

/**
 * Screen for displaying MATCHUP_V2 comprehensive stats with tabs for Stats and Charts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchupWorksheet(
    visualization: MatchupV2Visualization,
    modifier: Modifier = Modifier,
    highlightedTeamCodes: Set<String> = emptySet()
) {
    // Graphics layer for capturing share image
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    val imageExporter = remember { getImageExporter() }
    var isCapturing by remember { mutableStateOf(false) }

    // Reorder matchups to put highlighted team matchups first, but sort by game time within each group
    val matchups = remember(visualization.dataPoints, highlightedTeamCodes) {
        // First, sort all matchups by game time (earliest first)
        val sortedByTime = visualization.dataPoints.toList().sortedBy { (_, matchup) ->
            // Parse the ISO 8601 datetime string for sorting
            // If null or invalid, put at the end
            matchup.game_datetime ?: "9999-12-31T23:59:59Z"
        }

        if (highlightedTeamCodes.isEmpty()) {
            sortedByTime
        } else {
            // Partition matchups into those with highlighted teams and those without
            // Matchup keys are in format "away-home" (e.g., "buf-kc")
            // Search for the 3-letter team code (case-insensitive) in the key
            val (pinnedMatchups, otherMatchups) = sortedByTime.partition { (key, _) ->
                val teams = key.split("-")
                highlightedTeamCodes.any { code ->
                    teams.any { team -> team.equals(code, ignoreCase = true) }
                }
            }
            // Put pinned matchups first (already sorted by time), followed by others (also sorted by time)
            pinnedMatchups + otherMatchups
        }
    }

    var selectedMatchupIndex by remember(highlightedTeamCodes) { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(0) }

    // Reset selected index when matchups list changes (e.g., data updated)
    LaunchedEffect(matchups.size) {
        if (selectedMatchupIndex >= matchups.size) {
            selectedMatchupIndex = 0
        }
    }

    if (matchups.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No matchups available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val (matchupKey, selectedMatchup) = matchups[selectedMatchupIndex]
    val teams = matchupKey.split("-")
    val awayTeam = teams[0].uppercase()
    val homeTeam = teams[1].uppercase()

    // Extract week label from visualization title (e.g., "Divisional Round Matchup Worksheets")
    val weekLabel = remember(visualization.title) {
        visualization.title
            .replace("Matchup Worksheets", "")
            .replace("Matchup Worksheet", "")
            .trim()
    }

    // Format the game date for display
    val formattedDate = remember(selectedMatchup.game_datetime) {
        selectedMatchup.game_datetime?.let { formatGameDateTime(it) } ?: ""
    }

    // Share title for the image
    val shareTitle = "$awayTeam vs $homeTeam - $weekLabel"

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
        // Badge-based navigation row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stats/Charts toggle badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NavigationBadge(
                    text = "Stats",
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBadge(
                    text = "Charts",
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }

            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .padding(horizontal = 8.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // Horizontally scrollable matchup badges
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                matchups.forEachIndexed { index, (key, _) ->
                    val teams = key.split("-")
                    MatchupBadge(
                        awayTeam = teams[0].uppercase(),
                        homeTeam = teams[1].uppercase(),
                        isSelected = selectedMatchupIndex == index,
                        onClick = { selectedMatchupIndex = index }
                    )
                }
            }
        }

        // Tab content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> StatsTab(
                    awayTeam = awayTeam,
                    homeTeam = homeTeam,
                    matchup = selectedMatchup
                )
                1 -> ChartsTab(
                    awayTeam = awayTeam,
                    homeTeam = homeTeam,
                    matchup = selectedMatchup
                )
            }
        }

            // Source attribution
            visualization.source?.let { source ->
                Text(
                    text = "Source: $source",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }

        // Share FAB positioned at bottom-end
        ShareFab(
            onClick = {
                coroutineScope.launch {
                    isCapturing = true
                    try {
                        val bitmap = graphicsLayer.toImageBitmap()
                        println("📸 Matchup Share: Captured bitmap size: ${bitmap.width}x${bitmap.height} (width x height)")
                        println("📸 Matchup Share: Expected landscape - width should be > height")
                        imageExporter.shareImage(bitmap, shareTitle)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isCapturing = false
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        // Off-screen shareable content for capture (wide landscape, high-res)
        // Using requiredSize to force exact dimensions regardless of parent constraints
        // Higher density (2.0) renders crisper text
        CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
            Box(
                modifier = Modifier
                    .requiredWidth(3400.dp)
                    .requiredHeight(1800.dp)
                    .offset { IntOffset(-10000, 0) }  // Off-screen
                    .drawWithCache {
                        onDrawWithContent {
                            graphicsLayer.record {
                                this@onDrawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                    }
            ) {
                MatchupShareImage(
                    awayTeam = awayTeam,
                    homeTeam = homeTeam,
                    matchup = selectedMatchup,
                    weekLabel = weekLabel,
                    formattedDate = formattedDate,
                    source = visualization.source ?: "nflfastR / ESPN",
                    modifier = Modifier.requiredWidth(3400.dp).requiredHeight(1800.dp)
                )
            }
        }
    }
}

/**
 * Sealed class representing different row types for virtualized LazyColumn
 * This enables proper virtualization by making each row a separate LazyColumn item
 */
private sealed class RowData(val key: String) {
    data class Spacer(val k: String, val height: Dp) : RowData(k)
    data class SectionHeader(val k: String, val text: String) : RowData(k)
    data class SubsectionHeader(val k: String, val text: String) : RowData(k)
    data class ThreeColumn(
        val k: String,
        val leftText: String,
        val centerText: String,
        val rightText: String,
        val advantage: Int = 0,
        val leftWeight: FontWeight = FontWeight.Medium,
        val centerWeight: FontWeight = FontWeight.Normal,
        val rightWeight: FontWeight = FontWeight.Medium,
        val centerMaxLines: Int = Int.MAX_VALUE,
        val centerOverflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        val centerSoftWrap: Boolean = false
    ) : RowData(k)
    data class FiveColumn(
        val k: String,
        val leftValue: String,
        val leftRank: Int?,  // Numeric rank for color coding
        val leftRankDisplay: String?,  // Display string with "T" prefix
        val centerText: String,
        val rightValue: String,
        val rightRank: Int?,  // Numeric rank for color coding
        val rightRankDisplay: String?,  // Display string with "T" prefix
        val advantage: Int,
        val usePlayerRankColors: Boolean
    ) : RowData(k)
    data class DateText(val k: String, val text: String) : RowData(k)
    data class ViewNavigation(val k: String, val awayTeam: String, val homeTeam: String) : RowData(k)
}

/**
 * Compact navigation badge for Team/Versus toggle within team stats section
 */
@Composable
private fun TeamStatsNavBadge(
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
            .background(backgroundColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
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

@Composable
private fun StatsTab(
    awayTeam: String,
    homeTeam: String,
    matchup: MatchupV2
) {
    val listState = rememberLazyListState()

    // State for Team/Versus toggle (0 = Team, 1 = Versus)
    var teamStatsView by remember { mutableStateOf(0) }
    // State for which versus comparison to show (0 = away off vs home def, 1 = home off vs away def)
    var versusComparison by remember { mutableStateOf(0) }

    // Extract team data from teams map
    val awayTeamData = matchup.teams[awayTeam.lowercase()]
    val homeTeamData = matchup.teams[homeTeam.lowercase()]

    if (awayTeamData == null || homeTeamData == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Team data not available")
        }
        return
    }

    // Extract team stats from structured data
    val awayTeamStats = awayTeamData.team_stats
    val homeTeamStats = homeTeamData.team_stats

    // Pre-compute odds display data to avoid string manipulation during scrolling
    data class OddsDisplayData(
        val awaySpread: String,
        val homeSpread: String,
        val awayMoneyline: String?,
        val homeMoneyline: String?,
        val overUnder: String?,
        val overUnderLeftText: String,
        val overUnderRightText: String
    )

    val oddsDisplay = remember(matchup.odds) {
        matchup.odds?.let { odds ->
            // Calculate away spread from home spread
            val (awaySpread, homeSpread) = odds.home_spread?.let { homeSpread ->
                val awaySpread = if (homeSpread.startsWith("-")) {
                    "+" + homeSpread.substring(1)
                } else if (homeSpread.startsWith("+")) {
                    "-" + homeSpread.substring(1)
                } else {
                    homeSpread
                }
                awaySpread to homeSpread
            } ?: ("" to "")

            // Determine which team is favored for O/U placement
            val homeFavored = odds.home_spread?.startsWith("-") == true ||
                            odds.home_moneyline?.startsWith("-") == true
            val awayFavored = odds.away_moneyline?.startsWith("-") == true

            val (ouLeftText, ouRightText) = odds.over_under?.let { ou ->
                if (awayFavored) ou to "" else "" to if (homeFavored) ou else ""
            } ?: ("" to "")

            OddsDisplayData(
                awaySpread = awaySpread,
                homeSpread = homeSpread,
                awayMoneyline = odds.away_moneyline,
                homeMoneyline = odds.home_moneyline,
                overUnder = odds.over_under,
                overUnderLeftText = ouLeftText,
                overUnderRightText = ouRightText
            )
        }
    }

    // Pre-compute ALL rows for virtualization - CRITICAL for performance
    // Now depends on teamStatsView and versusComparison state
    val allRowsData = remember(matchup, awayTeam, homeTeam, awayTeamStats, homeTeamStats, awayTeamData, homeTeamData, teamStatsView, versusComparison) {
        buildList {
            // Spacer
            add(RowData.Spacer("top_spacer", 8.dp))

            // Game date/time
            matchup.game_datetime?.let { datetime ->
                add(RowData.DateText("game_date", formatGameDateTime(datetime)))
                add(RowData.Spacer("date_spacer", 8.dp))
            }

            // Odds rows
            oddsDisplay?.let { odds ->
                val hasData = odds.homeSpread.isNotEmpty() || odds.homeMoneyline != null ||
                            odds.awayMoneyline != null || odds.overUnder != null
                if (hasData) {
                    add(RowData.SectionHeader("odds_header", "Betting Odds"))
                    if (odds.homeSpread.isNotEmpty()) {
                        add(RowData.ThreeColumn("odds_spread", odds.awaySpread, "Spread", odds.homeSpread))
                    }
                    if (odds.awayMoneyline != null && odds.homeMoneyline != null) {
                        add(RowData.ThreeColumn("odds_moneyline", odds.awayMoneyline, "Moneyline", odds.homeMoneyline))
                    }
                    if (odds.overUnder != null) {
                        add(RowData.ThreeColumn("odds_ou", odds.overUnderLeftText, "O/U", odds.overUnderRightText))
                    }
                    add(RowData.Spacer("odds_spacer", 12.dp))
                }
            }

            // View navigation row (Team / Versus options)
            add(RowData.ViewNavigation("view_nav", awayTeam, homeTeam))
            add(RowData.Spacer("nav_spacer", 8.dp))

            // Team Stats section - content depends on view selection
            if (teamStatsView == 0) {
                // TEAM VIEW: Side-by-side offense vs offense, defense vs defense
                val offensiveStatLabels = mapOf(
                    "off_epa" to "Offensive EPA",
                    "yards_per_game" to "Yards/Game",
                    "pass_yards_per_game" to "Pass Yds/Game",
                    "rush_yards_per_game" to "Rush Yds/Game",
                    "points_per_game" to "Points/Game",
                    "yards_per_play" to "Yards/Play",
                    "third_down_pct" to "3rd Down %",
                    "rushing_epa" to "Rushing EPA",
                    "receiving_epa" to "Receiving EPA",
                    "pacr" to "PACR",
                    "passing_first_downs" to "Pass 1st Downs",
                    "sacks_suffered" to "Sacks Suffered",
                    "touchdowns" to "Touchdowns",
                    "interceptions_thrown" to "Interceptions",
                    "fumbles_lost" to "Fumbles Lost"
                )

                add(RowData.SubsectionHeader("offensive_stats_header", "Offensive Stats"))

                offensiveStatLabels.forEach { (key, label) ->
                    val awayStat = awayTeamStats.current.offense[key]
                    val homeStat = homeTeamStats.current.offense[key]
                    if (awayStat != null && homeStat != null) {
                        val awayValue = awayStat.value
                    val awayRank = awayStat.rank
                    val awayRankDisplay = awayStat.rankDisplay
                    val homeValue = homeStat.value
                    val homeRank = homeStat.rank
                    val homeRankDisplay = homeStat.rankDisplay

                    val lowerIsBetter = key.contains("sacks_suffered") ||
                                      key.contains("interceptions_thrown") ||
                                      key.contains("fumbles_lost")
                    val advantage = if (awayValue != null && homeValue != null) {
                        if (lowerIsBetter) {
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

                    val decimals = if (key.contains("pct") || key.contains("per_game") || key.contains("per_play")) 1 else 2
                    val awayText = awayValue?.format(decimals) ?: "-"
                    val homeText = homeValue?.format(decimals) ?: "-"

                    add(RowData.FiveColumn("team_off_$key", awayText, awayRank, awayRankDisplay, label, homeText, homeRank, homeRankDisplay, advantage, false))
                }
            }

            add(RowData.Spacer("offensive_spacer", 8.dp))
            add(RowData.SubsectionHeader("defensive_stats_header", "Defensive Stats"))

            val defensiveStatLabels = mapOf(
                "def_epa" to "Defensive EPA",
                "yards_allowed_per_game" to "Yds Allowed/Game",
                "pass_yards_allowed_per_game" to "Pass Yds Allowed/Game",
                "rush_yards_allowed_per_game" to "Rush Yds Allowed/Game",
                "points_allowed_per_game" to "Pts Allowed/Game",
                "third_down_pct_def" to "3rd Down % Allowed",
                "sacks_made" to "Sacks",
                "interceptions_made" to "Interceptions",
                "fumbles_forced" to "Fumbles Forced",
                "touchdowns_allowed" to "TDs Allowed",
                "turnover_differential" to "Turnover Diff"
            )

            defensiveStatLabels.forEach { (key, label) ->
                val awayStat = awayTeamStats.current.defense[key]
                val homeStat = homeTeamStats.current.defense[key]
                if (awayStat != null && homeStat != null) {
                    val awayValue = awayStat.value
                    val awayRank = awayStat.rank
                    val awayRankDisplay = awayStat.rankDisplay
                    val homeValue = homeStat.value
                    val homeRank = homeStat.rank
                    val homeRankDisplay = homeStat.rankDisplay

                    val higherIsBetter = key == "sacks_made" || key == "interceptions_made" ||
                                       key == "fumbles_forced" || key == "turnover_differential"
                    val advantage = if (awayValue != null && homeValue != null) {
                        if (higherIsBetter) {
                            when {
                                awayValue > homeValue -> -1
                                awayValue < homeValue -> 1
                                else -> 0
                            }
                        } else {
                            when {
                                awayValue < homeValue -> -1
                                awayValue > homeValue -> 1
                                else -> 0
                            }
                        }
                    } else 0

                    val decimals = if (key.contains("pct") || key.contains("per_game")) 1
                                 else if (key == "turnover_differential") 0
                                 else 2
                    val awayText = awayValue?.format(decimals) ?: "-"
                    val homeText = homeValue?.format(decimals) ?: "-"

                    add(RowData.FiveColumn("team_def_$key", awayText, awayRank, awayRankDisplay, label, homeText, homeRank, homeRankDisplay, advantage, false))
                }
            }
            } else {
                // VERSUS VIEW: Offense vs Defense matchup comparisons
                val comparisons = matchup.comparisons
                if (comparisons != null) {
                    // Show the selected comparison: 0 = away off vs home def, 1 = home off vs away def
                    val currentComparison = if (versusComparison == 0) {
                        comparisons.awayOffVsHomeDef
                    } else {
                        comparisons.homeOffVsAwayDef
                    }

                    // Header showing which matchup we're viewing
                    val headerText = if (versusComparison == 0) {
                        "$awayTeam Offense vs $homeTeam Defense"
                    } else {
                        "$homeTeam Offense vs $awayTeam Defense"
                    }
                    add(RowData.SubsectionHeader("versus_header", headerText))
                    add(RowData.Spacer("versus_header_spacer", 4.dp))

                    // Display matchup stats with offense on left, defense on right
                    // Order stats logically
                    val statOrder = listOf(
                        "off_epa", "yards_per_game", "pass_yards_per_game", "rush_yards_per_game",
                        "points_per_game", "third_down_pct", "touchdowns",
                        "sacks_suffered", "interceptions_thrown", "fumbles_lost"
                    )

                    statOrder.forEach { statKey ->
                        val stat = currentComparison[statKey]
                        if (stat != null) {
                            val offValue = stat.offense.value
                            val offRank = stat.offense.rank
                            val offRankDisplay = stat.offense.rankDisplay
                            val defValue = stat.defense.value
                            val defRank = stat.defense.rank
                            val defRankDisplay = stat.defense.rankDisplay

                            // Determine advantage based on stat type
                            // For yards/points gained vs allowed: offense wants more, defense wants less allowed
                            // So if offense > defense allowed, offense has advantage
                            val lowerOffenseIsBetter = statKey.contains("sacks_suffered") ||
                                                       statKey.contains("interceptions_thrown") ||
                                                       statKey.contains("fumbles_lost")
                            val advantage = if (offValue != null && defValue != null) {
                                if (lowerOffenseIsBetter) {
                                    // Lower offense is better (e.g., fewer sacks suffered)
                                    // Advantage to offense if they suffer fewer than defense forces
                                    when {
                                        offValue < defValue -> -1  // offense advantage
                                        offValue > defValue -> 1   // defense advantage
                                        else -> 0
                                    }
                                } else {
                                    // Higher offense is better (e.g., more yards)
                                    // Advantage to offense if they gain more than defense allows
                                    when {
                                        offValue > defValue -> -1  // offense advantage
                                        offValue < defValue -> 1   // defense advantage
                                        else -> 0
                                    }
                                }
                            } else 0

                            val decimals = if (statKey.contains("pct") || statKey.contains("per_game") || statKey.contains("per_play")) 1 else 2
                            val offText = offValue?.format(decimals) ?: "-"
                            val defText = defValue?.format(decimals) ?: "-"

                            // Label shows both stat descriptions
                            val label = stat.offLabel.replace("/Game", "").replace("Off ", "")

                            add(RowData.FiveColumn(
                                "versus_$statKey",
                                offText, offRank, offRankDisplay,
                                label,
                                defText, defRank, defRankDisplay,
                                advantage, false
                            ))
                        }
                    }
                } else {
                    // Fallback if no comparisons data
                    add(RowData.SubsectionHeader("versus_no_data", "Comparison data not available"))
                }
            }

            add(RowData.Spacer("team_stats_spacer", 12.dp))

            // Player Stats - pre-compute all player comparison rows
            add(RowData.SectionHeader("player_stats_header", "Key Players"))

            // QB Comparison
            val awayQB = awayTeamData.players.qb
            val homeQB = homeTeamData.players.qb
            if (awayQB != null && homeQB != null) {
                add(RowData.ThreeColumn("qb_header", awayQB.name, "QB", homeQB.name, 0, FontWeight.Bold, FontWeight.Bold, FontWeight.Bold))

                val qbStatsConfig = listOf(
                    Triple({ qb: QBPlayerStats -> qb.total_epa }, "Total EPA", 2),
                    Triple({ qb: QBPlayerStats -> qb.passing_yards }, "Pass Yds", 0),
                    Triple({ qb: QBPlayerStats -> qb.passing_tds }, "Pass TDs", 0),
                    Triple({ qb: QBPlayerStats -> qb.completion_pct }, "Completion %", 1),
                    Triple({ qb: QBPlayerStats -> qb.passing_cpoe }, "Pass CPOE", 2),
                    Triple({ qb: QBPlayerStats -> qb.pacr }, "PACR", 2),
                    Triple({ qb: QBPlayerStats -> qb.passing_yards_per_game }, "Pass Yds/Game", 1),
                    Triple({ qb: QBPlayerStats -> qb.interceptions }, "INTs", 0)
                )

                qbStatsConfig.forEach { (accessor, label, decimals) ->
                    val awayStat = accessor(awayQB)
                    val homeStat = accessor(homeQB)
                    val awayValue = awayStat.value
                    val awayRank = awayStat.rank
                    val awayRankDisplay = awayStat.rankDisplay
                    val homeValue = homeStat.value
                    val homeRank = homeStat.rank
                    val homeRankDisplay = homeStat.rankDisplay
                    val lowerIsBetter = label == "INTs"
                    val advantage = if (awayValue != null && homeValue != null) {
                        if (lowerIsBetter) when {
                            awayValue < homeValue -> -1
                            awayValue > homeValue -> 1
                            else -> 0
                        } else when {
                            awayValue > homeValue -> -1
                            awayValue < homeValue -> 1
                            else -> 0
                        }
                    } else 0
                    val awayText = awayValue?.format(decimals) ?: "-"
                    val homeText = homeValue?.format(decimals) ?: "-"
                    add(RowData.FiveColumn("qb_$label", awayText, awayRank, awayRankDisplay, label, homeText, homeRank, homeRankDisplay, advantage, false))
                }
                add(RowData.Spacer("qb_spacer", 4.dp))
            }

            // RB Comparisons
            val awayRBs = awayTeamData.players.rbs
            val homeRBs = homeTeamData.players.rbs
            val rbCount = minOf(awayRBs.size, homeRBs.size)

            val rbStatsConfig = listOf(
                Triple({ rb: RBPlayerStats -> rb.rushing_epa }, "Rush EPA", 2),
                Triple({ rb: RBPlayerStats -> rb.rushing_yards }, "Rush Yds", 0),
                Triple({ rb: RBPlayerStats -> rb.rushing_tds }, "Rush TDs", 0),
                Triple({ rb: RBPlayerStats -> rb.yards_per_carry }, "Yds/Carry", 1),
                Triple({ rb: RBPlayerStats -> rb.rushing_yards_per_game }, "Rush Yds/Game", 1),
                Triple({ rb: RBPlayerStats -> rb.receptions }, "Receptions", 0),
                Triple({ rb: RBPlayerStats -> rb.receiving_yards }, "Rec Yds", 0),
                Triple({ rb: RBPlayerStats -> rb.receiving_tds }, "Rec TDs", 0),
                Triple({ rb: RBPlayerStats -> rb.receiving_yards_per_game }, "Rec Yds/Game", 1),
                Triple({ rb: RBPlayerStats -> rb.target_share }, "Target Share", 3)
            )

            for (i in 0 until rbCount) {
                val awayRB = awayRBs[i]
                val homeRB = homeRBs[i]
                add(RowData.ThreeColumn("rb${i}_header", awayRB.name, "RB", homeRB.name, 0, FontWeight.Bold, FontWeight.Bold, FontWeight.Bold))

                rbStatsConfig.forEach { (accessor, label, decimals) ->
                    val awayStat = accessor(awayRB)
                    val homeStat = accessor(homeRB)
                    val awayValue = awayStat.value
                    val awayRank = awayStat.rank
                    val awayRankDisplay = awayStat.rankDisplay
                    val homeValue = homeStat.value
                    val homeRank = homeStat.rank
                    val homeRankDisplay = homeStat.rankDisplay
                    val advantage = if (awayValue != null && homeValue != null) when {
                        awayValue > homeValue -> -1
                        awayValue < homeValue -> 1
                        else -> 0
                    } else 0
                    val awayText = awayValue?.let { if (decimals == 0) it.toInt().toString() else it.format(decimals) } ?: "-"
                    val homeText = homeValue?.let { if (decimals == 0) it.toInt().toString() else it.format(decimals) } ?: "-"
                    add(RowData.FiveColumn("rb${i}_$label", awayText, awayRank, awayRankDisplay, label, homeText, homeRank, homeRankDisplay, advantage, true))
                }
                add(RowData.Spacer("rb${i}_spacer", 4.dp))
            }

            // Receiver Comparisons
            val awayWRs = awayTeamData.players.receivers
            val homeWRs = homeTeamData.players.receivers
            val wrCount = minOf(awayWRs.size, homeWRs.size)

            val receiverStatsConfig = listOf(
                Triple({ rec: ReceiverPlayerStats -> rec.receiving_epa }, "Rec EPA", 2),
                Triple({ rec: ReceiverPlayerStats -> rec.receiving_yards }, "Rec Yds", 0),
                Triple({ rec: ReceiverPlayerStats -> rec.receiving_tds }, "Rec TDs", 0),
                Triple({ rec: ReceiverPlayerStats -> rec.receptions }, "Receptions", 0),
                Triple({ rec: ReceiverPlayerStats -> rec.yards_per_reception }, "Yds/Rec", 1),
                Triple({ rec: ReceiverPlayerStats -> rec.receiving_yards_per_game }, "Rec Yds/Game", 1),
                Triple({ rec: ReceiverPlayerStats -> rec.catch_pct }, "Catch %", 1),
                Triple({ rec: ReceiverPlayerStats -> rec.wopr }, "WOPR", 2),
                Triple({ rec: ReceiverPlayerStats -> rec.racr }, "RACR", 2),
                Triple({ rec: ReceiverPlayerStats -> rec.target_share }, "Target Share", 3),
                Triple({ rec: ReceiverPlayerStats -> rec.air_yards_share }, "Air Yards %", 1)
            )

            for (i in 0 until wrCount) {
                val awayWR = awayWRs[i]
                val homeWR = homeWRs[i]
                add(RowData.ThreeColumn("wr${i}_header", awayWR.name, "WR", homeWR.name, 0, FontWeight.Bold, FontWeight.Bold, FontWeight.Bold))

                receiverStatsConfig.forEach { (accessor, label, decimals) ->
                    val awayStat = accessor(awayWR)
                    val homeStat = accessor(homeWR)
                    val awayValue = awayStat.value
                    val awayRank = awayStat.rank
                    val awayRankDisplay = awayStat.rankDisplay
                    val homeValue = homeStat.value
                    val homeRank = homeStat.rank
                    val homeRankDisplay = homeStat.rankDisplay
                    val advantage = if (awayValue != null && homeValue != null) when {
                        awayValue > homeValue -> -1
                        awayValue < homeValue -> 1
                        else -> 0
                    } else 0
                    val awayText = awayValue?.let { if (decimals == 0) it.toInt().toString() else it.format(decimals) } ?: "-"
                    val homeText = homeValue?.let { if (decimals == 0) it.toInt().toString() else it.format(decimals) } ?: "-"
                    add(RowData.FiveColumn("wr${i}_$label", awayText, awayRank, awayRankDisplay, label, homeText, homeRank, homeRankDisplay, advantage, true))
                }
                add(RowData.Spacer("wr${i}_spacer", 4.dp))
            }

            add(RowData.Spacer("player_stats_spacer", 12.dp))

            // H2H Record
            if (matchup.h2h_record.isNotEmpty()) {
                add(RowData.SectionHeader("h2h_header", "Head-to-Head"))
                matchup.h2h_record.forEachIndexed { idx, h2hGame ->
                    val advantage = when (h2hGame.winner.uppercase()) {
                        awayTeam -> -1
                        homeTeam -> 1
                        else -> 0
                    }
                    val leftText = if (h2hGame.winner == awayTeam) "W" else if (h2hGame.winner == homeTeam) "L" else "T"
                    val centerText = "W${h2hGame.week}: ${h2hGame.finalScore}"
                    val rightText = if (h2hGame.winner == homeTeam) "W" else if (h2hGame.winner == awayTeam) "L" else "T"
                    add(RowData.ThreeColumn(
                        k = "h2h_$idx",
                        leftText = leftText,
                        centerText = centerText,
                        rightText = rightText,
                        advantage = advantage,
                        centerMaxLines = 2,
                        centerOverflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        centerSoftWrap = true
                    ))
                }
                add(RowData.Spacer("h2h_spacer", 12.dp))
            }

            // Common Opponents
            matchup.common_opponents?.let { commonOpps ->
                if (commonOpps.isNotEmpty()) {
                    add(RowData.SectionHeader("common_opp_header", "Common Opponents"))
                    commonOpps.forEach { (opponentCode, opponentData) ->
                        val awayGames = opponentData[awayTeam.lowercase()] ?: emptyList()
                        val homeGames = opponentData[homeTeam.lowercase()] ?: emptyList()

                        if (awayGames.isNotEmpty() || homeGames.isNotEmpty()) {
                            // Opponent header
                            add(RowData.ThreeColumn("opp_${opponentCode}_header", "", opponentCode.uppercase(), "", 0, FontWeight.Medium, FontWeight.Bold, FontWeight.Medium))

                            // Game rows
                            val maxGames = maxOf(awayGames.size, homeGames.size)
                            for (i in 0 until maxGames) {
                                val awayGame = awayGames.getOrNull(i)
                                val homeGame = homeGames.getOrNull(i)

                                val awayResult = awayGame?.result ?: ""
                                val awayScore = awayGame?.score ?: ""
                                val awayWeek = awayGame?.week

                                val homeResult = homeGame?.result ?: ""
                                val homeScore = homeGame?.score ?: ""
                                val homeWeek = homeGame?.week

                                val leftText = if (awayGame != null) "$awayResult $awayScore (W$awayWeek)" else ""
                                val rightText = if (homeGame != null) "$homeResult $homeScore (W$homeWeek)" else ""

                                val advantage = when {
                                    awayResult == "W" && homeResult == "L" -> -1
                                    awayResult == "L" && homeResult == "W" -> 1
                                    awayResult == "W" && homeResult == "W" -> 0
                                    awayResult == "L" && homeResult == "L" -> 0
                                    awayResult == "T" && homeResult != "" -> 0
                                    homeResult == "T" && awayResult != "" -> 0
                                    awayResult == "W" && homeResult == "" -> -1
                                    awayResult == "" && homeResult == "W" -> 1
                                    else -> 0
                                }

                                add(RowData.ThreeColumn("opp_${opponentCode}_game_$i", leftText, "", rightText, advantage))
                            }
                            add(RowData.Spacer("opp_${opponentCode}_spacer", 4.dp))
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface) // Match surface background
        ) {
            // Fixed top padding for header (just team names now)
            val topPadding = 28.dp
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding, bottom = 8.dp)
            ) {
                // Render all pre-computed rows as individual items for virtualization
                items(allRowsData.size, key = { allRowsData[it].key }) { index ->
                    when (val row = allRowsData[index]) {
                        is RowData.Spacer -> Spacer(modifier = Modifier.height(row.height))
                        is RowData.SectionHeader -> Text(
                            text = row.text,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        is RowData.SubsectionHeader -> Text(
                            text = row.text,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        is RowData.ThreeColumn -> ThreeColumnRow(
                            leftText = row.leftText,
                            centerText = row.centerText,
                            rightText = row.rightText,
                            advantage = row.advantage,
                            leftWeight = row.leftWeight,
                            centerWeight = row.centerWeight,
                            rightWeight = row.rightWeight,
                            centerMaxLines = row.centerMaxLines,
                            centerOverflow = row.centerOverflow,
                            centerSoftWrap = row.centerSoftWrap
                        )
                        is RowData.FiveColumn -> FiveColumnRowWithRanks(
                            leftValue = row.leftValue,
                            leftRank = row.leftRank,
                            leftRankDisplay = row.leftRankDisplay,
                            centerText = row.centerText,
                            rightValue = row.rightValue,
                            rightRank = row.rightRank,
                            rightRankDisplay = row.rightRankDisplay,
                            advantage = row.advantage,
                            usePlayerRankColors = row.usePlayerRankColors
                        )
                        is RowData.DateText -> Text(
                            text = row.text,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 8.dp)
                        )
                        is RowData.ViewNavigation -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TeamStatsNavBadge(
                                text = "Team",
                                isSelected = teamStatsView == 0,
                                onClick = { teamStatsView = 0 }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TeamStatsNavBadge(
                                text = "${row.awayTeam} Off vs ${row.homeTeam} Def",
                                isSelected = teamStatsView == 1 && versusComparison == 0,
                                onClick = { teamStatsView = 1; versusComparison = 0 }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TeamStatsNavBadge(
                                text = "${row.homeTeam} Off vs ${row.awayTeam} Def",
                                isSelected = teamStatsView == 1 && versusComparison == 1,
                                onClick = { teamStatsView = 1; versusComparison = 1 }
                            )
                        }
                    }
                }

            }
        }

        // Pinned header at the top with team names only
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
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
                    text = "VS",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 11.sp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
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
}


@Composable
private fun TeamHeader(
    awayTeam: String,
    homeTeam: String,
    modifier: Modifier = Modifier
) {
    ThreeColumnRow(
        leftText = awayTeam,
        centerText = "VS",
        rightText = homeTeam,
        modifier = modifier,
        leftWeight = FontWeight.Bold,
        centerWeight = FontWeight.Bold,
        rightWeight = FontWeight.Bold
    )
}

private data class StatRow(
    val label: String,
    val awayText: String,
    val awayRank: Int?,  // Numeric rank for color coding
    val awayRankDisplay: String?,  // Display string with "T" prefix
    val homeText: String,
    val homeRank: Int?,  // Numeric rank for color coding
    val homeRankDisplay: String?,  // Display string with "T" prefix
    val advantage: Int
)

@Composable
private fun TeamStatsComparison(
    awayTeam: String,
    homeTeam: String,
    awayStats: CurrentTeamStats,
    homeStats: CurrentTeamStats
) {
    val offensiveStatLabels = remember {
        mapOf(
            "off_epa" to "Offensive EPA",
            "yards_per_game" to "Yards/Game",
            "pass_yards_per_game" to "Pass Yds/Game",
            "rush_yards_per_game" to "Rush Yds/Game",
            "points_per_game" to "Points/Game",
            "yards_per_play" to "Yards/Play",
            "third_down_pct" to "3rd Down %",
            "rushing_epa" to "Rushing EPA",
            "receiving_epa" to "Receiving EPA",
            "pacr" to "PACR",
            "passing_first_downs" to "Pass 1st Downs",
            "sacks_suffered" to "Sacks Suffered",
            "touchdowns" to "Touchdowns",
            "interceptions_thrown" to "Interceptions",
            "fumbles_lost" to "Fumbles Lost"
        )
    }

    val defensiveStatLabels = remember {
        mapOf(
            "def_epa" to "Defensive EPA",
            "yards_allowed_per_game" to "Yds Allowed/Game",
            "pass_yards_allowed_per_game" to "Pass Yds Allowed/Game",
            "rush_yards_allowed_per_game" to "Rush Yds Allowed/Game",
            "points_allowed_per_game" to "Pts Allowed/Game",
            "third_down_pct_def" to "3rd Down % Allowed",
            "sacks_made" to "Sacks",
            "interceptions_made" to "Interceptions",
            "fumbles_forced" to "Fumbles Forced",
            "touchdowns_allowed" to "TDs Allowed",
            "turnover_differential" to "Turnover Diff"
        )
    }

    // Extract and cache all offensive stats
    val offensiveRows = remember(awayStats, homeStats) {
        offensiveStatLabels.mapNotNull { (key, label) ->
            val awayStat = awayStats.offense[key] ?: return@mapNotNull null
            val homeStat = homeStats.offense[key] ?: return@mapNotNull null

            val awayValue = awayStat.value
            val awayRank = awayStat.rank
            val awayRankDisplay = awayStat.rankDisplay
            val homeValue = homeStat.value
            val homeRank = homeStat.rank
            val homeRankDisplay = homeStat.rankDisplay

            val lowerIsBetter = key.contains("sacks_suffered") ||
                              key.contains("interceptions_thrown") ||
                              key.contains("fumbles_lost")
            val advantage = if (awayValue != null && homeValue != null) {
                if (lowerIsBetter) {
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

            val decimals = if (key.contains("pct") || key.contains("per_game") || key.contains("per_play")) 1 else 2
            val awayText = awayValue?.format(decimals) ?: "-"
            val homeText = homeValue?.format(decimals) ?: "-"

            StatRow(label, awayText, awayRank, awayRankDisplay, homeText, homeRank, homeRankDisplay, advantage)
        }
    }

    // Extract and cache all defensive stats
    val defensiveRows = remember(awayStats, homeStats) {
        defensiveStatLabels.mapNotNull { (key, label) ->
            val awayStat = awayStats.defense[key] ?: return@mapNotNull null
            val homeStat = homeStats.defense[key] ?: return@mapNotNull null

            val awayValue = awayStat.value
            val awayRank = awayStat.rank
            val awayRankDisplay = awayStat.rankDisplay
            val homeValue = homeStat.value
            val homeRank = homeStat.rank
            val homeRankDisplay = homeStat.rankDisplay

            val higherIsBetter = key == "sacks_made" || key == "interceptions_made" ||
                               key == "fumbles_forced" || key == "turnover_differential"
            val advantage = if (awayValue != null && homeValue != null) {
                if (higherIsBetter) {
                    when {
                        awayValue > homeValue -> -1
                        awayValue < homeValue -> 1
                        else -> 0
                    }
                } else {
                    when {
                        awayValue < homeValue -> -1
                        awayValue > homeValue -> 1
                        else -> 0
                    }
                }
            } else 0

            val decimals = if (key.contains("pct") || key.contains("per_game")) 1
                         else if (key == "turnover_differential") 0
                         else 2
            val awayText = awayValue?.format(decimals) ?: "-"
            val homeText = homeValue?.format(decimals) ?: "-"

            StatRow(label, awayText, awayRank, awayRankDisplay, homeText, homeRank, homeRankDisplay, advantage)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Offensive Stats Section
        Text(
            text = "Offensive Stats",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )

        if (offensiveRows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                offensiveRows.forEach { row ->
                    FiveColumnRowWithRanks(
                        leftValue = row.awayText,
                        leftRank = row.awayRank,
                        leftRankDisplay = row.awayRankDisplay,
                        centerText = row.label,
                        rightValue = row.homeText,
                        rightRank = row.homeRank,
                        rightRankDisplay = row.homeRankDisplay,
                        advantage = row.advantage
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Defensive Stats Section
        Text(
            text = "Defensive Stats",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )

        if (defensiveRows.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                defensiveRows.forEach { row ->
                    FiveColumnRowWithRanks(
                        leftValue = row.awayText,
                        leftRank = row.awayRank,
                        leftRankDisplay = row.awayRankDisplay,
                        centerText = row.label,
                        rightValue = row.homeText,
                        rightRank = row.homeRank,
                        rightRankDisplay = row.homeRankDisplay,
                        advantage = row.advantage
                    )
                }
            }
        }
    }
}

private data class PlayerComparison(
    val awayName: String,
    val homeName: String,
    val position: String,
    val stats: List<StatRow>
)

@Composable
private fun PlayerStatsComparison(
    awayTeam: String,
    homeTeam: String,
    awayPlayers: TeamPlayers,
    homePlayers: TeamPlayers
) {
    // Extract and cache all player data upfront
    val playerData = remember(awayPlayers, homePlayers) {
        data class AllPlayerData(
            val qb: PlayerComparison?,
            val rbs: List<PlayerComparison>,
            val receivers: List<PlayerComparison>
        )

        // QB Comparison
        val qbComparison = run {
            val awayQB = awayPlayers.qb
            val homeQB = homePlayers.qb
            if (awayQB == null || homeQB == null) return@run null

            val awayQBName = awayQB.name
            val homeQBName = homeQB.name

            val qbStatsConfig = listOf(
                Triple({ qb: QBPlayerStats -> qb.total_epa }, "Total EPA", 2),
                Triple({ qb: QBPlayerStats -> qb.passing_yards }, "Pass Yds", 0),
                Triple({ qb: QBPlayerStats -> qb.passing_tds }, "Pass TDs", 0),
                Triple({ qb: QBPlayerStats -> qb.completion_pct }, "Completion %", 1),
                Triple({ qb: QBPlayerStats -> qb.passing_cpoe }, "Pass CPOE", 2),
                Triple({ qb: QBPlayerStats -> qb.pacr }, "PACR", 2),
                Triple({ qb: QBPlayerStats -> qb.passing_yards_per_game }, "Pass Yds/Game", 1),
                Triple({ qb: QBPlayerStats -> qb.interceptions }, "INTs", 0)
            )

            val stats = qbStatsConfig.mapNotNull { (accessor, label, decimals) ->
                val awayStat = accessor(awayQB)
                val homeStat = accessor(homeQB)
                val awayValue = awayStat.value
                val awayRank = awayStat.rank
                val awayRankDisplay = awayStat.rankDisplay
                val homeValue = homeStat.value
                val homeRank = homeStat.rank
                val homeRankDisplay = homeStat.rankDisplay
                val lowerIsBetter = label == "INTs"
                val advantage = if (awayValue != null && homeValue != null) {
                    if (lowerIsBetter) when {
                        awayValue < homeValue -> -1
                        awayValue > homeValue -> 1
                        else -> 0
                    } else when {
                        awayValue > homeValue -> -1
                        awayValue < homeValue -> 1
                        else -> 0
                    }
                } else 0
                StatRow(label, awayValue?.format(decimals) ?: "-", awayRank, awayRankDisplay,
                       homeValue?.format(decimals) ?: "-", homeRank, homeRankDisplay, advantage)
            }
            PlayerComparison(awayQBName, homeQBName, "QB", stats)
        }

        // RB Comparisons
        val rbComparisons = run {
            val awayRBs = awayPlayers.rbs
            val homeRBs = homePlayers.rbs

            val rbStatsConfig = listOf(
                Triple({ rb: RBPlayerStats -> rb.rushing_epa }, "Rush EPA", 2),
                Triple({ rb: RBPlayerStats -> rb.rushing_yards }, "Rush Yds", 0),
                Triple({ rb: RBPlayerStats -> rb.rushing_tds }, "Rush TDs", 0),
                Triple({ rb: RBPlayerStats -> rb.yards_per_carry }, "Yds/Carry", 1),
                Triple({ rb: RBPlayerStats -> rb.rushing_yards_per_game }, "Rush Yds/Game", 1),
                Triple({ rb: RBPlayerStats -> rb.receptions }, "Receptions", 0),
                Triple({ rb: RBPlayerStats -> rb.receiving_yards }, "Rec Yds", 0),
                Triple({ rb: RBPlayerStats -> rb.receiving_tds }, "Rec TDs", 0),
                Triple({ rb: RBPlayerStats -> rb.receiving_yards_per_game }, "Rec Yds/Game", 1),
                Triple({ rb: RBPlayerStats -> rb.target_share }, "Target Share", 3)
            )

            (0 until minOf(awayRBs.size, homeRBs.size)).map { i ->
                val awayRB = awayRBs[i]
                val homeRB = homeRBs[i]
                val stats = rbStatsConfig.map { (accessor, label, decimals) ->
                    val awayStat = accessor(awayRB)
                    val homeStat = accessor(homeRB)
                    val awayValue = awayStat.value
                    val awayRank = awayStat.rank
                    val awayRankDisplay = awayStat.rankDisplay
                    val homeValue = homeStat.value
                    val homeRank = homeStat.rank
                    val homeRankDisplay = homeStat.rankDisplay
                    val advantage = if (awayValue != null && homeValue != null) when {
                        awayValue > homeValue -> -1
                        awayValue < homeValue -> 1
                        else -> 0
                    } else 0
                    val awayText = awayValue?.let { if (decimals == 0) it.toInt().toString() else it.format(decimals) } ?: "-"
                    val homeText = homeValue?.let { if (decimals == 0) it.toInt().toString() else it.format(decimals) } ?: "-"
                    StatRow(label, awayText, awayRank, awayRankDisplay, homeText, homeRank, homeRankDisplay, advantage)
                }
                PlayerComparison(awayRB.name, homeRB.name, "RB", stats)
            }
        }

        // Receiver Comparisons
        val receiverComparisons = run {
            val awayWRs = awayPlayers.receivers
            val homeWRs = homePlayers.receivers

            val receiverStatsConfig = listOf(
                Triple({ rec: ReceiverPlayerStats -> rec.receiving_epa }, "Rec EPA", 2),
                Triple({ rec: ReceiverPlayerStats -> rec.receiving_yards }, "Rec Yds", 0),
                Triple({ rec: ReceiverPlayerStats -> rec.receiving_tds }, "Rec TDs", 0),
                Triple({ rec: ReceiverPlayerStats -> rec.receptions }, "Receptions", 0),
                Triple({ rec: ReceiverPlayerStats -> rec.yards_per_reception }, "Yds/Rec", 1),
                Triple({ rec: ReceiverPlayerStats -> rec.receiving_yards_per_game }, "Rec Yds/Game", 1),
                Triple({ rec: ReceiverPlayerStats -> rec.catch_pct }, "Catch %", 1),
                Triple({ rec: ReceiverPlayerStats -> rec.wopr }, "WOPR", 2),
                Triple({ rec: ReceiverPlayerStats -> rec.racr }, "RACR", 2),
                Triple({ rec: ReceiverPlayerStats -> rec.target_share }, "Target Share", 3),
                Triple({ rec: ReceiverPlayerStats -> rec.air_yards_share }, "Air Yards %", 1)
            )

            (0 until minOf(awayWRs.size, homeWRs.size)).map { i ->
                val awayWR = awayWRs[i]
                val homeWR = homeWRs[i]
                val stats = receiverStatsConfig.map { (accessor, label, decimals) ->
                    val awayStat = accessor(awayWR)
                    val homeStat = accessor(homeWR)
                    val awayValue = awayStat.value
                    val awayRank = awayStat.rank
                    val awayRankDisplay = awayStat.rankDisplay
                    val homeValue = homeStat.value
                    val homeRank = homeStat.rank
                    val homeRankDisplay = homeStat.rankDisplay
                    val advantage = if (awayValue != null && homeValue != null) when {
                        awayValue > homeValue -> -1
                        awayValue < homeValue -> 1
                        else -> 0
                    } else 0
                    val awayText = awayValue?.let { if (decimals == 0) it.toInt().toString() else it.format(decimals) } ?: "-"
                    val homeText = homeValue?.let { if (decimals == 0) it.toInt().toString() else it.format(decimals) } ?: "-"
                    StatRow(label, awayText, awayRank, awayRankDisplay, homeText, homeRank, homeRankDisplay, advantage)
                }
                PlayerComparison(awayWR.name, homeWR.name, "WR", stats)
            }
        }

        AllPlayerData(qbComparison, rbComparisons, receiverComparisons)
    }

    // Now just render the cached data
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // QB
        playerData.qb?.let { qb ->
            ThreeColumnRow(
                leftText = qb.awayName,
                centerText = qb.position,
                rightText = qb.homeName,
                leftWeight = FontWeight.Bold,
                centerWeight = FontWeight.Bold,
                rightWeight = FontWeight.Bold
            )
            qb.stats.forEach { stat ->
                FiveColumnRowWithRanks(
                    leftValue = stat.awayText,
                    leftRank = stat.awayRank,
                    leftRankDisplay = stat.awayRankDisplay,
                    centerText = stat.label,
                    rightValue = stat.homeText,
                    rightRank = stat.homeRank,
                    rightRankDisplay = stat.homeRankDisplay,
                    advantage = stat.advantage
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // RBs
        playerData.rbs.forEach { rb ->
            ThreeColumnRow(
                leftText = rb.awayName,
                centerText = rb.position,
                rightText = rb.homeName,
                leftWeight = FontWeight.Bold,
                centerWeight = FontWeight.Bold,
                rightWeight = FontWeight.Bold
            )
            rb.stats.forEach { stat ->
                FiveColumnRowWithRanks(
                    leftValue = stat.awayText,
                    leftRank = stat.awayRank,
                    leftRankDisplay = stat.awayRankDisplay,
                    centerText = stat.label,
                    rightValue = stat.homeText,
                    rightRank = stat.homeRank,
                    rightRankDisplay = stat.homeRankDisplay,
                    advantage = stat.advantage,
                    usePlayerRankColors = true
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Receivers
        playerData.receivers.forEach { receiver ->
            ThreeColumnRow(
                leftText = receiver.awayName,
                centerText = receiver.position,
                rightText = receiver.homeName,
                leftWeight = FontWeight.Bold,
                centerWeight = FontWeight.Bold,
                rightWeight = FontWeight.Bold
            )
            receiver.stats.forEach { stat ->
                FiveColumnRowWithRanks(
                    leftValue = stat.awayText,
                    leftRank = stat.awayRank,
                    leftRankDisplay = stat.awayRankDisplay,
                    centerText = stat.label,
                    rightValue = stat.homeText,
                    rightRank = stat.homeRank,
                    rightRankDisplay = stat.homeRankDisplay,
                    advantage = stat.advantage,
                    usePlayerRankColors = true
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HeadToHeadComparison(
    awayTeam: String,
    homeTeam: String,
    h2hMatchups: List<H2HGame>
) {
    // Pre-compute all H2H row data to avoid string operations during composition
    data class H2HRowData(
        val leftText: String,
        val centerText: String,
        val rightText: String,
        val advantage: Int
    )

    val h2hRows = remember(h2hMatchups, awayTeam, homeTeam) {
        h2hMatchups.map { matchup ->
            // Determine advantage based on winner
            val advantage = when (matchup.winner.uppercase()) {
                awayTeam -> -1
                homeTeam -> 1
                else -> 0 // TIE or unknown
            }

            H2HRowData(
                leftText = if (matchup.winner == awayTeam) "W" else if (matchup.winner == homeTeam) "L" else "T",
                centerText = "W${matchup.week}: ${matchup.finalScore}",
                rightText = if (matchup.winner == homeTeam) "W" else if (matchup.winner == awayTeam) "L" else "T",
                advantage = advantage
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        h2hRows.forEach { row ->
            ThreeColumnRow(
                leftText = row.leftText,
                centerText = row.centerText,
                rightText = row.rightText,
                advantage = row.advantage,
                centerMaxLines = 1,
                centerOverflow = androidx.compose.ui.text.style.TextOverflow.Visible
            )
        }
    }
}

@Composable
private fun CommonOpponentsComparison(
    awayTeam: String,
    homeTeam: String,
    commonOpponents: CommonOpponents
) {
    // Pre-compute all common opponent row data to avoid string operations during composition
    data class CommonOpponentRowData(
        val leftText: String,
        val centerText: String,
        val rightText: String,
        val advantage: Int = 0,
        val centerWeight: FontWeight = FontWeight.Normal,
        val isHeader: Boolean = false
    )

    data class OpponentSection(
        val header: CommonOpponentRowData,
        val rows: List<CommonOpponentRowData>
    )

    val opponentSections = remember(commonOpponents, awayTeam, homeTeam) {
        commonOpponents.mapNotNull { (opponentCode, opponentData) ->
            val awayGames = opponentData[awayTeam.lowercase()] ?: emptyList()
            val homeGames = opponentData[homeTeam.lowercase()] ?: emptyList()

            if (awayGames.isEmpty() && homeGames.isEmpty()) return@mapNotNull null

            // Create header row
            val header = CommonOpponentRowData(
                leftText = "",
                centerText = opponentCode.uppercase(),
                rightText = "",
                advantage = 0,
                centerWeight = FontWeight.Bold,
                isHeader = true
            )

            // Get max number of games
            val maxGames = maxOf(awayGames.size, homeGames.size)

            // Create game rows
            val rows = (0 until maxGames).map { i ->
                val awayGame = awayGames.getOrNull(i)
                val homeGame = homeGames.getOrNull(i)

                val awayResult = awayGame?.result ?: ""
                val awayScore = awayGame?.score ?: ""
                val awayWeek = awayGame?.week

                val homeResult = homeGame?.result ?: ""
                val homeScore = homeGame?.score ?: ""
                val homeWeek = homeGame?.week

                // Format left side (away team)
                val leftText = if (awayGame != null) {
                    "$awayResult $awayScore (W$awayWeek)"
                } else ""

                // Format right side (home team)
                val rightText = if (homeGame != null) {
                    "$homeResult $homeScore (W$homeWeek)"
                } else ""

                // Determine advantage based on result
                val advantage = when {
                    // Both teams played - compare results
                    awayResult == "W" && homeResult == "L" -> -1
                    awayResult == "L" && homeResult == "W" -> 1
                    awayResult == "W" && homeResult == "W" -> 0
                    awayResult == "L" && homeResult == "L" -> 0
                    awayResult == "T" && homeResult != "" -> 0
                    homeResult == "T" && awayResult != "" -> 0
                    // One team played, one didn't - only show advantage for W, not for non-play
                    awayResult == "W" && homeResult == "" -> -1
                    awayResult == "" && homeResult == "W" -> 1
                    // Don't show advantage when comparing loss to non-play
                    else -> 0
                }

                CommonOpponentRowData(leftText, "", rightText, advantage)
            }

            OpponentSection(header, rows)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opponentSections.forEach { section ->
            // Opponent header
            ThreeColumnRow(
                leftText = section.header.leftText,
                centerText = section.header.centerText,
                rightText = section.header.rightText,
                centerWeight = section.header.centerWeight
            )

            // Game rows
            section.rows.forEach { row ->
                ThreeColumnRow(
                    leftText = row.leftText,
                    centerText = row.centerText,
                    rightText = row.rightText,
                    advantage = row.advantage
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ChartsTab(
    awayTeam: String,
    homeTeam: String,
    matchup: MatchupV2
) {
    val scrollState = rememberScrollState()

    // Extract team data from teams map
    val awayTeamData = matchup.teams[awayTeam.lowercase()]
    val homeTeamData = matchup.teams[homeTeam.lowercase()]

    if (awayTeamData == null || homeTeamData == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Team data not available")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cumulative EPA Chart
        CumulativeEPAChart(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayTeamData = awayTeamData,
            homeTeamData = homeTeamData
        )

        // Weekly EPA Scatter Plot (Offensive vs Defensive EPA)
        WeeklyEPAScatterPlot(
            awayTeam = awayTeam,
            homeTeam = homeTeam,
            awayTeamData = awayTeamData,
            homeTeamData = homeTeamData
        )
    }
}

@Composable
private fun CumulativeEPAChart(
    awayTeam: String,
    homeTeam: String,
    awayTeamData: TeamData,
    homeTeamData: TeamData
) {
    Text(
        text = "Cumulative EPA Over Season",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    val awayCumEPA = awayTeamData.team_stats.cum_epa_by_week
    val homeCumEPA = homeTeamData.team_stats.cum_epa_by_week

    if (awayCumEPA.isEmpty() || homeCumEPA.isEmpty()) {
        Text(
            text = "Cumulative EPA data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Parse data points for line chart
    val awayDataPoints = awayCumEPA.entries.mapNotNull { (weekKey, epaValue) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        if (weekNum != null) {
            LineChartDataPoint(x = weekNum.toDouble(), y = epaValue)
        } else null
    }.sortedBy { it.x }

    val homeDataPoints = homeCumEPA.entries.mapNotNull { (weekKey, epaValue) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        if (weekNum != null) {
            LineChartDataPoint(x = weekNum.toDouble(), y = epaValue)
        } else null
    }.sortedBy { it.x }

    // Create series with team colors
    val series = listOf(
        LineChartSeries(
            label = awayTeam,
            dataPoints = awayDataPoints,
            color = "#2196F3" // Blue for away team
        ),
        LineChartSeries(
            label = homeTeam,
            dataPoints = homeDataPoints,
            color = "#FF5722" // Deep Orange for home team
        )
    )

    LineChartComponent(
        series = series,
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        yAxisTitle = "Cumulative EPA"
    )

    // Add legend for team colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Team1Color, CircleShape)
            )
            Text(
                text = awayTeam,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Team2Color, CircleShape)
            )
            Text(
                text = homeTeam,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EPAByWeekChart(
    awayTeam: String,
    homeTeam: String,
    awayTeamData: TeamData,
    homeTeamData: TeamData
) {
    Text(
        text = "EPA Per Play by Week",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    val awayEPAByWeek = awayTeamData.team_stats.epa_by_week
    val homeEPAByWeek = homeTeamData.team_stats.epa_by_week

    if (awayEPAByWeek.isEmpty() || homeEPAByWeek.isEmpty()) {
        Text(
            text = "EPA by week data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Parse offensive EPA data points
    val awayOffDataPoints = awayEPAByWeek.entries.mapNotNull { (weekKey, epa) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val offEPA = epa.off
        if (weekNum != null && offEPA != null) {
            LineChartDataPoint(x = weekNum.toDouble(), y = offEPA)
        } else null
    }.sortedBy { it.x }

    val homeOffDataPoints = homeEPAByWeek.entries.mapNotNull { (weekKey, epa) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val offEPA = epa.off
        if (weekNum != null && offEPA != null) {
            LineChartDataPoint(x = weekNum.toDouble(), y = offEPA)
        } else null
    }.sortedBy { it.x }

    // Parse defensive EPA data points (negate for better visualization - lower is better)
    val awayDefDataPoints = awayEPAByWeek.entries.mapNotNull { (weekKey, epa) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val defEPA = epa.def
        if (weekNum != null && defEPA != null) {
            LineChartDataPoint(x = weekNum.toDouble(), y = -defEPA) // Negate defensive EPA
        } else null
    }.sortedBy { it.x }

    val homeDefDataPoints = homeEPAByWeek.entries.mapNotNull { (weekKey, epa) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val defEPA = epa.def
        if (weekNum != null && defEPA != null) {
            LineChartDataPoint(x = weekNum.toDouble(), y = -defEPA) // Negate defensive EPA
        } else null
    }.sortedBy { it.x }

    // Create series for both offense and defense
    val series = listOf(
        LineChartSeries(
            label = "$awayTeam Offense",
            dataPoints = awayOffDataPoints,
            color = "#2196F3" // Blue for away team offense
        ),
        LineChartSeries(
            label = "$homeTeam Offense",
            dataPoints = homeOffDataPoints,
            color = "#FF5722" // Deep Orange for home team offense
        ),
        LineChartSeries(
            label = "$awayTeam Defense",
            dataPoints = awayDefDataPoints,
            color = "#64B5F6" // Light Blue for away team defense
        ),
        LineChartSeries(
            label = "$homeTeam Defense",
            dataPoints = homeDefDataPoints,
            color = "#FF8A65" // Light Orange for home team defense
        )
    )

    LineChartComponent(
        series = series,
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        yAxisTitle = "EPA Per Play"
    )

    // Add note about defensive EPA
    Text(
        text = "Note: Defensive EPA is inverted (higher is better)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun WeeklyEPAScatterPlot(
    awayTeam: String,
    homeTeam: String,
    awayTeamData: TeamData,
    homeTeamData: TeamData
) {
    // State for selected week range
    var selectedWeekRange by remember { mutableStateOf<IntRange?>(null) }

    Text(
        text = "Weekly Offensive vs Defensive EPA",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    // Week range filter badges - horizontally scrollable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WeekRangeBadge(
            label = "All Weeks",
            isSelected = selectedWeekRange == null,
            onClick = { selectedWeekRange = null }
        )
        WeekRangeBadge(
            label = "Weeks 1-6",
            isSelected = selectedWeekRange == 1..6,
            onClick = { selectedWeekRange = 1..6 }
        )
        WeekRangeBadge(
            label = "Weeks 7-12",
            isSelected = selectedWeekRange == 7..12,
            onClick = { selectedWeekRange = 7..12 }
        )
        WeekRangeBadge(
            label = "Weeks 13-18",
            isSelected = selectedWeekRange == 13..18,
            onClick = { selectedWeekRange = 13..18 }
        )
    }

    val awayEPAByWeek = awayTeamData.team_stats.epa_by_week
    val homeEPAByWeek = homeTeamData.team_stats.epa_by_week

    if (awayEPAByWeek.isEmpty() || homeEPAByWeek.isEmpty()) {
        Text(
            text = "Weekly EPA data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Parse scatter plot data points
    val awayPoints = awayEPAByWeek.entries.mapNotNull { (weekKey, epa) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val offEPA = epa.off
        val defEPA = epa.def

        if (weekNum != null && offEPA != null && defEPA != null) {
            ScatterPlotDataPoint(
                label = "$awayTeam W$weekNum",
                x = offEPA,
                y = defEPA,
                sum = offEPA + defEPA,
                teamCode = awayTeam,
                color = "#2196F3" // Blue for away team
            )
        } else null
    }

    val homePoints = homeEPAByWeek.entries.mapNotNull { (weekKey, epa) ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val offEPA = epa.off
        val defEPA = epa.def

        if (weekNum != null && offEPA != null && defEPA != null) {
            ScatterPlotDataPoint(
                label = "$homeTeam W$weekNum",
                x = offEPA,
                y = defEPA,
                sum = offEPA + defEPA,
                teamCode = homeTeam,
                color = "#FF5722" // Deep Orange for home team
            )
        } else null
    }

    // Build highlighted labels set based on selected week range
    val weekRange = selectedWeekRange
    val highlightedLabels = if (weekRange != null) {
        (awayPoints + homePoints)
            .filter { point ->
                val weekMatch = "W(\\d+)".toRegex().find(point.label)
                weekMatch?.groupValues?.get(1)?.toIntOrNull()?.let { week ->
                    week in weekRange
                } ?: false
            }
            .map { it.label }
            .toSet()
    } else {
        emptySet()
    }

    QuadrantScatterPlot(
        data = awayPoints + homePoints,
        modifier = Modifier.fillMaxWidth(),
        title = "",
        xAxisLabel = "Offensive EPA",
        yAxisLabel = "Defensive EPA",
        invertYAxis = false,
        highlightedPlayerLabels = highlightedLabels,
        quadrantTopRight = QuadrantConfig(label = "Elite", color = "#4CAF50", lightModeColor = "#4CAF50"),
        quadrantTopLeft = QuadrantConfig(label = "Good Defense", color = "#FFEB3B", lightModeColor = "#FFEB3B"),
        quadrantBottomLeft = QuadrantConfig(label = "Poor", color = "#F44336", lightModeColor = "#F44336"),
        quadrantBottomRight = QuadrantConfig(label = "Good Offense", color = "#FF9800", lightModeColor = "#FF9800")
    )

    // Add legend for team colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Team1Color, CircleShape)
            )
            Text(
                text = awayTeam,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Team2Color, CircleShape)
            )
            Text(
                text = homeTeam,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CumulativeEPAChartV2(
    awayTeam: String,
    homeTeam: String,
    awayTeamStats: JsonObject,
    homeTeamStats: JsonObject
) {
    Text(
        text = "Cumulative EPA Over Season",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    // Extract cum_epa_by_week data
    val awayCumEPA = awayTeamStats.getObject("cum_epa_by_week")
    val homeCumEPA = homeTeamStats.getObject("cum_epa_by_week")

    if (awayCumEPA == null || homeCumEPA == null) {
        Text(
            text = "Cumulative EPA data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Parse data points for line chart
    val awayDataPoints = awayCumEPA.keys.mapNotNull { weekKey ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val epaValue = awayCumEPA.getDouble(weekKey)
        if (weekNum != null && epaValue != null) {
            LineChartDataPoint(x = weekNum.toDouble(), y = epaValue)
        } else null
    }.sortedBy { it.x }

    val homeDataPoints = homeCumEPA.keys.mapNotNull { weekKey ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val epaValue = homeCumEPA.getDouble(weekKey)
        if (weekNum != null && epaValue != null) {
            LineChartDataPoint(x = weekNum.toDouble(), y = epaValue)
        } else null
    }.sortedBy { it.x }

    // Create series with team colors
    val series = listOf(
        LineChartSeries(
            label = awayTeam,
            dataPoints = awayDataPoints,
            color = "#2196F3" // Blue for away team
        ),
        LineChartSeries(
            label = homeTeam,
            dataPoints = homeDataPoints,
            color = "#FF5722" // Deep Orange for home team
        )
    )

    LineChartComponent(
        series = series,
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        yAxisTitle = "Cumulative EPA"
    )

    // Add legend for team colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Team1Color, CircleShape)
            )
            Text(
                text = awayTeam,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Team2Color, CircleShape)
            )
            Text(
                text = homeTeam,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WeeklyEPAScatterPlotV2(
    awayTeam: String,
    homeTeam: String,
    awayTeamStats: JsonObject,
    homeTeamStats: JsonObject
) {
    // State for selected week range
    var selectedWeekRange by remember { mutableStateOf<IntRange?>(null) }

    Text(
        text = "Weekly Offensive vs Defensive EPA",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    // Week range filter badges - horizontally scrollable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WeekRangeBadge(
            label = "All Weeks",
            isSelected = selectedWeekRange == null,
            onClick = { selectedWeekRange = null }
        )
        WeekRangeBadge(
            label = "Weeks 1-6",
            isSelected = selectedWeekRange == 1..6,
            onClick = { selectedWeekRange = 1..6 }
        )
        WeekRangeBadge(
            label = "Weeks 7-12",
            isSelected = selectedWeekRange == 7..12,
            onClick = { selectedWeekRange = 7..12 }
        )
        WeekRangeBadge(
            label = "Weeks 13-18",
            isSelected = selectedWeekRange == 13..18,
            onClick = { selectedWeekRange = 13..18 }
        )
    }

    // Extract epa_by_week data
    val awayEPAByWeek = awayTeamStats.getObject("epa_by_week")
    val homeEPAByWeek = homeTeamStats.getObject("epa_by_week")

    if (awayEPAByWeek == null || homeEPAByWeek == null) {
        Text(
            text = "Weekly EPA data not available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // Parse scatter plot data points
    val awayPoints = awayEPAByWeek.keys.mapNotNull { weekKey ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val weekData = awayEPAByWeek.getObject(weekKey)
        val offEPA = weekData?.getDouble("off")
        val defEPA = weekData?.getDouble("def")

        if (weekNum != null && offEPA != null && defEPA != null) {
            ScatterPlotDataPoint(
                label = "$awayTeam W$weekNum",
                x = offEPA,
                y = defEPA,
                sum = offEPA + defEPA,
                teamCode = awayTeam,
                color = "#2196F3" // Blue for away team
            )
        } else null
    }

    val homePoints = homeEPAByWeek.keys.mapNotNull { weekKey ->
        val weekNum = weekKey.removePrefix("week-").toIntOrNull()
        val weekData = homeEPAByWeek.getObject(weekKey)
        val offEPA = weekData?.getDouble("off")
        val defEPA = weekData?.getDouble("def")

        if (weekNum != null && offEPA != null && defEPA != null) {
            ScatterPlotDataPoint(
                label = "$homeTeam W$weekNum",
                x = offEPA,
                y = defEPA,
                sum = offEPA + defEPA,
                teamCode = homeTeam,
                color = "#FF5722" // Deep Orange for home team
            )
        } else null
    }

    // Build highlighted labels set based on selected week range
    val weekRange = selectedWeekRange
    val highlightedLabels = if (weekRange != null) {
        (awayPoints + homePoints)
            .filter { point ->
                val weekMatch = "W(\\d+)".toRegex().find(point.label)
                weekMatch?.groupValues?.get(1)?.toIntOrNull()?.let { week ->
                    week in weekRange
                } ?: false
            }
            .map { it.label }
            .toSet()
    } else {
        emptySet()
    }

    QuadrantScatterPlot(
        data = awayPoints + homePoints,
        modifier = Modifier.fillMaxWidth(),
        title = "",
        xAxisLabel = "Offensive EPA",
        yAxisLabel = "Defensive EPA",
        invertYAxis = false,
        highlightedPlayerLabels = highlightedLabels,
        quadrantTopRight = QuadrantConfig(label = "Elite", color = "#4CAF50", lightModeColor = "#4CAF50"),
        quadrantTopLeft = QuadrantConfig(label = "Good Defense", color = "#FFEB3B", lightModeColor = "#FFEB3B"),
        quadrantBottomLeft = QuadrantConfig(label = "Poor", color = "#F44336", lightModeColor = "#F44336"),
        quadrantBottomRight = QuadrantConfig(label = "Good Offense", color = "#FF9800", lightModeColor = "#FF9800")
    )

    // Add legend for team colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Team1Color, CircleShape)
            )
            Text(
                text = awayTeam,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Team2Color, CircleShape)
            )
            Text(
                text = homeTeam,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun WeekRangeBadge(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 10.sp
        )
    }
}

