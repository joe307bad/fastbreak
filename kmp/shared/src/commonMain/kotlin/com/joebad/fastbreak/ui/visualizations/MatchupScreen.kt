package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.model.Matchup
import com.joebad.fastbreak.data.model.MatchupComparison
import com.joebad.fastbreak.data.model.MatchupVisualization

/**
 * Screen for displaying matchup report cards with a dropdown selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchupScreen(
    visualization: MatchupVisualization,
    modifier: Modifier = Modifier,
    highlightedTeamCodes: Set<String> = emptySet()
) {
    // Filter matchups by highlighted teams if any are selected
    val matchups = remember(visualization.dataPoints, highlightedTeamCodes) {
        if (highlightedTeamCodes.isEmpty()) {
            visualization.dataPoints
        } else {
            visualization.dataPoints.filter { matchup ->
                highlightedTeamCodes.any { code ->
                    matchup.homeTeam.contains(code, ignoreCase = true) ||
                    matchup.awayTeam.contains(code, ignoreCase = true)
                }
            }
        }
    }

    var selectedMatchupIndex by remember { mutableStateOf(0) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Reset selected index when filtered matchups change
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

    val selectedMatchup = matchups[selectedMatchupIndex]
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Dropdown selector
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            OutlinedTextField(
                value = "${selectedMatchup.homeTeam} vs ${selectedMatchup.awayTeam}",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Select Matchup") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                matchups.forEachIndexed { index, matchup ->
                    DropdownMenuItem(
                        text = {
                            Text("${matchup.homeTeam} vs ${matchup.awayTeam}")
                        },
                        onClick = {
                            selectedMatchupIndex = index
                            dropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        // Matchup content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            MatchupComparisonCard(matchup = selectedMatchup)
        }

        // Source attribution
        visualization.source?.let { source ->
            Text(
                text = "Data source: $source",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun MatchupComparisonCard(matchup: Matchup) {
    val comparisons = matchup.comparisons

    // Calculate edges
    var awayEdges = 0
    var homeEdges = 0

    comparisons.forEach { comparison ->
        val edge = calculateEdge(comparison)
        when (edge) {
            EdgeResult.AWAY -> awayEdges++
            EdgeResult.HOME -> homeEdges++
            EdgeResult.NONE -> {}
        }
    }

    val awayColor = Color(0xFF2196F3) // Blue
    val homeColor = Color(0xFFFF5722) // Deep Orange
    val neutralColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Header with team names and edge summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Home team (left side)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = matchup.homeTeam,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = homeColor
                )
            }

            // VS divider
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "vs",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Away team (right side)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = matchup.awayTeam,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = awayColor
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Edge summary bar (home team on left, away team on right)
        EdgeSummaryBar(
            homeEdges = homeEdges,
            awayEdges = awayEdges,
            homeTeam = matchup.homeTeam,
            awayTeam = matchup.awayTeam,
            homeColor = homeColor,
            awayColor = awayColor
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(12.dp))

        // Comparison rows
        comparisons.forEach { comparison ->
            ComparisonRow(
                comparison = comparison,
                awayColor = awayColor,
                homeColor = homeColor,
                neutralColor = neutralColor
            )
        }
    }
}

@Composable
private fun EdgeSummaryBar(
    homeEdges: Int,
    awayEdges: Int,
    homeTeam: String,
    awayTeam: String,
    homeColor: Color,
    awayColor: Color
) {
    val total = homeEdges + awayEdges
    val homeFraction = if (total > 0) homeEdges.toFloat() / total else 0.5f
    val awayFraction = if (total > 0) awayEdges.toFloat() / total else 0.5f

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Edge count labels (home on left, away on right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$homeEdges edges",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = homeColor
            )
            Text(
                text = "$awayEdges edges",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = awayColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Visual bar (home on left, away on right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            if (homeEdges > 0 || awayEdges == 0) {
                Box(
                    modifier = Modifier
                        .weight(if (total > 0) homeFraction else 0.5f)
                        .fillMaxHeight()
                        .background(homeColor)
                )
            }
            if (awayEdges > 0 || homeEdges == 0) {
                Box(
                    modifier = Modifier
                        .weight(if (total > 0) awayFraction else 0.5f)
                        .fillMaxHeight()
                        .background(awayColor)
                )
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    comparison: MatchupComparison,
    awayColor: Color,
    homeColor: Color,
    neutralColor: Color
) {
    val edge = calculateEdge(comparison)
    val awayValue = comparison.awayValueDisplay()
    val homeValue = comparison.homeValueDisplay()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Home value with edge indicator (left side)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.weight(1f)
        ) {
            // Edge indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (edge == EdgeResult.HOME) homeColor else Color.Transparent,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = homeValue,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (edge == EdgeResult.HOME) FontWeight.Bold else FontWeight.Normal,
                color = if (edge == EdgeResult.HOME) homeColor else MaterialTheme.colorScheme.onSurface
            )
        }

        // Stat title
        Text(
            text = comparison.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1.5f)
                .padding(horizontal = 8.dp)
        )

        // Away value with edge indicator (right side)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = awayValue,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (edge == EdgeResult.AWAY) FontWeight.Bold else FontWeight.Normal,
                color = if (edge == EdgeResult.AWAY) awayColor else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Edge indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (edge == EdgeResult.AWAY) awayColor else Color.Transparent,
                        shape = CircleShape
                    )
            )
        }
    }
}

private enum class EdgeResult {
    AWAY, HOME, NONE
}

private fun calculateEdge(comparison: MatchupComparison): EdgeResult {
    val awayNumeric = comparison.awayValueAsDouble()
    val homeNumeric = comparison.homeValueAsDouble()

    if (awayNumeric == null || homeNumeric == null) {
        return EdgeResult.NONE
    }

    // Use the inverted property to determine if lower is better
    return when {
        comparison.inverted && awayNumeric < homeNumeric -> EdgeResult.AWAY
        comparison.inverted && homeNumeric < awayNumeric -> EdgeResult.HOME
        !comparison.inverted && awayNumeric > homeNumeric -> EdgeResult.AWAY
        !comparison.inverted && homeNumeric > awayNumeric -> EdgeResult.HOME
        else -> EdgeResult.NONE
    }
}
