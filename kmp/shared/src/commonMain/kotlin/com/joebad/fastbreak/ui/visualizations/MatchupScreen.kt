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
    modifier: Modifier = Modifier
) {
    val matchups = visualization.dataPoints
    var selectedMatchupIndex by remember { mutableStateOf(0) }
    var dropdownExpanded by remember { mutableStateOf(false) }

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
                .padding(vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = "${selectedMatchup.awayTeam} @ ${selectedMatchup.homeTeam}",
                onValueChange = {},
                readOnly = true,
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
                            Text("${matchup.awayTeam} @ ${matchup.homeTeam}")
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with team names and edge summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Away team
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
                    Text(
                        text = "AWAY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // VS divider with edge counts
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "@",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Home team
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
                    Text(
                        text = "HOME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Edge summary bar
            EdgeSummaryBar(
                awayEdges = awayEdges,
                homeEdges = homeEdges,
                awayTeam = matchup.awayTeam,
                homeTeam = matchup.homeTeam,
                awayColor = awayColor,
                homeColor = homeColor
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
}

@Composable
private fun EdgeSummaryBar(
    awayEdges: Int,
    homeEdges: Int,
    awayTeam: String,
    homeTeam: String,
    awayColor: Color,
    homeColor: Color
) {
    val total = awayEdges + homeEdges
    val awayFraction = if (total > 0) awayEdges.toFloat() / total else 0.5f
    val homeFraction = if (total > 0) homeEdges.toFloat() / total else 0.5f

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Edge count labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$awayEdges edges",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = awayColor
            )
            Text(
                text = "$homeEdges edges",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = homeColor
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Visual bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            if (awayEdges > 0 || homeEdges == 0) {
                Box(
                    modifier = Modifier
                        .weight(if (total > 0) awayFraction else 0.5f)
                        .fillMaxHeight()
                        .background(awayColor)
                )
            }
            if (homeEdges > 0 || awayEdges == 0) {
                Box(
                    modifier = Modifier
                        .weight(if (total > 0) homeFraction else 0.5f)
                        .fillMaxHeight()
                        .background(homeColor)
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
        // Away value with edge indicator
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
                        color = if (edge == EdgeResult.AWAY) awayColor else Color.Transparent,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = awayValue,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (edge == EdgeResult.AWAY) FontWeight.Bold else FontWeight.Normal,
                color = if (edge == EdgeResult.AWAY) awayColor else MaterialTheme.colorScheme.onSurface
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

        // Home value with edge indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = homeValue,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (edge == EdgeResult.HOME) FontWeight.Bold else FontWeight.Normal,
                color = if (edge == EdgeResult.HOME) homeColor else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Edge indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (edge == EdgeResult.HOME) homeColor else Color.Transparent,
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
