package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.NBAMatchupVisualization
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * NBA Matchup Worksheet component with two-row navigation:
 * - First row: Date badges to filter games by date
 * - Second row: Matchup badges for the selected date
 */
@Composable
fun NBAMatchupWorksheet(
    visualization: NBAMatchupVisualization,
    modifier: Modifier = Modifier
) {
    // Group matchups by date and sort dates chronologically
    val matchupsByDate = remember(visualization.dataPoints) {
        visualization.dataPoints
            .groupBy { matchup ->
                // Parse ISO 8601 date and extract just the date part
                val instant = Instant.parse(matchup.gameDate)
                instant.toLocalDateTime(TimeZone.UTC).date
            }
            .toList()
            .sortedBy { (date, _) -> date }
            .toMap()
    }

    val dates = remember(matchupsByDate) { matchupsByDate.keys.toList() }

    // State for selected date and matchup
    var selectedDateIndex by remember { mutableStateOf(0) }
    var selectedMatchupIndex by remember { mutableStateOf(0) }

    // Reset matchup index when date changes
    LaunchedEffect(selectedDateIndex) {
        selectedMatchupIndex = 0
    }

    // Get matchups for selected date
    val selectedDate = if (dates.isNotEmpty()) dates[selectedDateIndex] else null
    val matchupsForDate = selectedDate?.let { matchupsByDate[it] } ?: emptyList()

    if (dates.isEmpty() || matchupsForDate.isEmpty()) {
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

    val selectedMatchup = matchupsForDate[selectedMatchupIndex]

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // First row: Date badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            dates.forEachIndexed { index, date ->
                DateBadge(
                    date = date,
                    isSelected = selectedDateIndex == index,
                    onClick = { selectedDateIndex = index }
                )
            }
        }

        // Second row: Matchup badges for selected date
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            matchupsForDate.forEachIndexed { index, matchup ->
                MatchupBadge(
                    awayTeam = matchup.awayTeam.abbreviation,
                    homeTeam = matchup.homeTeam.abbreviation,
                    isSelected = selectedMatchupIndex == index,
                    onClick = { selectedMatchupIndex = index }
                )
            }
        }

        // Placeholder for matchup content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selectedMatchup.gameName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatGameDate(selectedMatchup.gameDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Stats and charts will go here",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Date badge for filtering games by date
 */
@Composable
private fun DateBadge(
    date: LocalDate,
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = date.dayOfWeek.name.take(3),  // Mon, Tue, etc.
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                color = textColor.copy(alpha = 0.8f)
            )
            Text(
                text = "${date.month.name.take(3)} ${date.dayOfMonth}",  // Jan 23
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
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
                text = "@",
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
 * Format game date for display
 */
private fun formatGameDate(gameDate: String): String {
    return try {
        val instant = Instant.parse(gameDate)
        val dateTime = instant.toLocalDateTime(TimeZone.UTC)
        "${dateTime.month.name.take(3)} ${dateTime.dayOfMonth}, ${dateTime.year}"
    } catch (e: Exception) {
        gameDate
    }
}
