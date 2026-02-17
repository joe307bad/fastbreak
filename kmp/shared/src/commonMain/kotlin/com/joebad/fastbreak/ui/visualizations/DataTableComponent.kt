package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.model.*
import kotlin.math.roundToInt

/**
 * Helper function to format doubles to a specific number of decimal places.
 */
private fun Double.formatTo(decimals: Int): String {
    val multiplier = when (decimals) {
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
    val rounded = (this * multiplier).roundToInt() / multiplier
    return rounded.toString()
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
 * Helper function to format conference ranking (e.g., "6th / East")
 */
private fun formatConferenceRank(rank: Int?, conference: String?): String {
    if (rank == null) return "-"
    val confName = when (conference?.lowercase()) {
        "east", "eastern" -> "East"
        "west", "western" -> "West"
        else -> conference ?: "Conf"
    }
    return "${formatOrdinal(rank)} / $confName"
}

/**
 * Generic data table component that displays data from any visualization type.
 */
@Composable
fun DataTableComponent(
    visualization: VisualizationType,
    modifier: Modifier = Modifier,
    onTeamClick: (String) -> Unit = {},
    highlightedTeamCodes: Set<String> = emptySet(),
    highlightedPlayerLabels: Set<String> = emptySet()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        when (visualization) {
            is ScatterPlotVisualization -> ScatterDataTable(
                data = visualization.dataPoints,
                invertYAxis = visualization.invertYAxis,
                xColumnLabel = visualization.xColumnLabel,
                yColumnLabel = visualization.yColumnLabel,
                quadrantTopRight = visualization.quadrantTopRight,
                quadrantTopLeft = visualization.quadrantTopLeft,
                quadrantBottomLeft = visualization.quadrantBottomLeft,
                quadrantBottomRight = visualization.quadrantBottomRight,
                subject = visualization.subject,
                onTeamClick = onTeamClick,
                highlightedTeamCodes = highlightedTeamCodes,
                highlightedPlayerLabels = highlightedPlayerLabels
            )
            is BarGraphVisualization -> BarDataTable(
                data = visualization.dataPoints,
                onTeamClick = onTeamClick,
                highlightedTeamCodes = highlightedTeamCodes
            )
            is LineChartVisualization -> LineDataTable(
                series = visualization.series,
                onTeamClick = onTeamClick,
                highlightedTeamCodes = highlightedTeamCodes
            )
            is TableVisualization -> GenericDataTable(
                data = visualization.dataPoints,
                onTeamClick = onTeamClick,
                highlightedTeamCodes = highlightedTeamCodes
            )
            is MatchupVisualization -> MatchupReportCards(visualization.dataPoints)
            is MatchupV2Visualization -> {
                // MatchupV2 doesn't need a data table - it's handled by MatchupWorksheet
            }
            is NBAMatchupVisualization -> {
                // NBAMatchup doesn't need a data table - it's handled by NBAMatchupWorksheet
            }
            is CBBMatchupVisualization -> {
                // CBBMatchup doesn't need a data table - it's handled by CBBMatchupWorksheet
            }
            is HelloWorldVisualization -> {
                // HelloWorld doesn't need a data table
            }
        }
    }
}

// Helper to parse hex color string to Compose Color
private fun parseHexColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    return Color(("FF$cleanHex").toLong(16))
}

@Composable
private fun ScatterDataTable(
    data: List<ScatterPlotDataPoint>,
    invertYAxis: Boolean = false,
    xColumnLabel: String? = null,
    yColumnLabel: String? = null,
    quadrantTopRight: QuadrantConfig? = null,
    quadrantTopLeft: QuadrantConfig? = null,
    quadrantBottomLeft: QuadrantConfig? = null,
    quadrantBottomRight: QuadrantConfig? = null,
    subject: String? = null,
    onTeamClick: (String) -> Unit = {},
    highlightedTeamCodes: Set<String> = emptySet(),
    highlightedPlayerLabels: Set<String> = emptySet()
) {
    val horizontalScrollState = rememberScrollState()

    // Sort state - null sortColumn means default sort (score)
    var sortColumn by remember { mutableStateOf<String?>(null) }
    var sortAscending by remember { mutableStateOf(false) }

    // Calculate averages for quadrant determination (using all data)
    val avgX = data.map { it.x }.average()
    val avgY = data.map { it.y }.average()

    // Resolve quadrant colors (use config or defaults)
    val topRightColor = quadrantTopRight?.let { parseHexColor(it.color) } ?: Color(0xFF4CAF50)
    val topLeftColor = quadrantTopLeft?.let { parseHexColor(it.color) } ?: Color(0xFF2196F3)
    val bottomLeftColor = quadrantBottomLeft?.let { parseHexColor(it.color) } ?: Color(0xFFFF9800)
    val bottomRightColor = quadrantBottomRight?.let { parseHexColor(it.color) } ?: Color(0xFFF44336)

    // Calculate rank based on default score sort (always descending)
    val scoreRankMap = remember(data, invertYAxis) {
        val sortedByScore = if (invertYAxis) {
            data.sortedByDescending { it.x - it.y }
        } else {
            data.sortedByDescending { it.sum }
        }
        sortedByScore.mapIndexed { index, point -> point to (index + 1) }.toMap()
    }

    // Sort data based on selected column
    val sortedData = remember(data, sortColumn, sortAscending) {
        val sorted = when (sortColumn) {
            "label" -> if (sortAscending) data.sortedBy { it.label } else data.sortedByDescending { it.label }
            "winPct" -> if (sortAscending) {
                data.sortedBy { point ->
                    if (point.wins != null && point.losses != null) {
                        val total = point.wins + point.losses
                        if (total > 0) point.wins.toDouble() / total else 0.0
                    } else 0.0
                }
            } else {
                data.sortedByDescending { point ->
                    if (point.wins != null && point.losses != null) {
                        val total = point.wins + point.losses
                        if (total > 0) point.wins.toDouble() / total else 0.0
                    } else 0.0
                }
            }
            "confRank" -> if (sortAscending) {
                data.sortedBy { it.conferenceRank ?: Int.MAX_VALUE }
            } else {
                data.sortedByDescending { it.conferenceRank ?: Int.MIN_VALUE }
            }
            "team" -> if (sortAscending) data.sortedBy { it.teamCode ?: "" } else data.sortedByDescending { it.teamCode ?: "" }
            "x" -> if (sortAscending) data.sortedBy { it.x } else data.sortedByDescending { it.x }
            "y" -> if (sortAscending) data.sortedBy { it.y } else data.sortedByDescending { it.y }
            "score", null -> {
                // Default sort by score (descending)
                if (invertYAxis) {
                    if (sortAscending) data.sortedBy { it.x - it.y } else data.sortedByDescending { it.x - it.y }
                } else {
                    if (sortAscending) data.sortedBy { it.sum } else data.sortedByDescending { it.sum }
                }
            }
            else -> data
        }
        sorted
    }

    // Helper to get quadrant color for a point
    fun getQuadrantColor(point: ScatterPlotDataPoint): Color {
        // When invertYAxis is true, "good Y" means LOWER values
        val isGoodY = if (invertYAxis) point.y < avgY else point.y >= avgY
        return when {
            point.x >= avgX && isGoodY -> topRightColor
            point.x < avgX && isGoodY -> topLeftColor
            point.x < avgX && !isGoodY -> bottomLeftColor
            else -> bottomRightColor
        }
    }

    // Check if any data point has a team code
    val hasTeamCodes = data.any { it.teamCode != null }

    // Check if any data point has win/loss records
    val hasRecords = data.any { it.wins != null && it.losses != null }

    // Check if any data point has conference rankings
    val hasConfRanks = data.any { it.conferenceRank != null }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TableHeader(
                text = "Rank",
                width = 50.dp,
                sortable = true,
                isCurrentSort = sortColumn == null || sortColumn == "score",
                sortAscending = sortAscending,
                onClick = {
                    when {
                        sortColumn == null && !sortAscending -> sortAscending = true
                        sortColumn == null && sortAscending -> { sortColumn = null; sortAscending = false }
                        else -> { sortColumn = null; sortAscending = false }
                    }
                }
            )
            TableHeader(
                text = "Player/Team",
                width = 110.dp,
                sortable = true,
                isCurrentSort = sortColumn == "label",
                sortAscending = sortAscending,
                onClick = {
                    when {
                        sortColumn == "label" && !sortAscending -> sortAscending = true
                        sortColumn == "label" && sortAscending -> { sortColumn = null; sortAscending = false }
                        else -> { sortColumn = "label"; sortAscending = false }
                    }
                }
            )
            if (hasRecords) {
                TableHeader(
                    text = "Win%",
                    width = 60.dp,
                    sortable = true,
                    isCurrentSort = sortColumn == "winPct",
                    sortAscending = sortAscending,
                    onClick = {
                        when {
                            sortColumn == "winPct" && !sortAscending -> sortAscending = true
                            sortColumn == "winPct" && sortAscending -> { sortColumn = null; sortAscending = false }
                            else -> { sortColumn = "winPct"; sortAscending = false }
                        }
                    }
                )
            }
            if (hasConfRanks) {
                TableHeader(
                    text = "Conf Rank",
                    width = 100.dp,
                    sortable = true,
                    isCurrentSort = sortColumn == "confRank",
                    sortAscending = sortAscending,
                    onClick = {
                        when {
                            sortColumn == "confRank" && !sortAscending -> sortAscending = true
                            sortColumn == "confRank" && sortAscending -> { sortColumn = null; sortAscending = false }
                            else -> { sortColumn = "confRank"; sortAscending = false }
                        }
                    }
                )
            }
            if (hasTeamCodes) {
                TableHeader(
                    text = "Team",
                    width = 50.dp,
                    sortable = true,
                    isCurrentSort = sortColumn == "team",
                    sortAscending = sortAscending,
                    onClick = {
                        when {
                            sortColumn == "team" && !sortAscending -> sortAscending = true
                            sortColumn == "team" && sortAscending -> { sortColumn = null; sortAscending = false }
                            else -> { sortColumn = "team"; sortAscending = false }
                        }
                    }
                )
            }
            TableHeader(
                text = xColumnLabel ?: "X Value",
                width = 80.dp,
                sortable = true,
                isCurrentSort = sortColumn == "x",
                sortAscending = sortAscending,
                onClick = {
                    when {
                        sortColumn == "x" && !sortAscending -> sortAscending = true
                        sortColumn == "x" && sortAscending -> { sortColumn = null; sortAscending = false }
                        else -> { sortColumn = "x"; sortAscending = false }
                    }
                }
            )
            TableHeader(
                text = yColumnLabel ?: "Y Value",
                width = 80.dp,
                sortable = true,
                isCurrentSort = sortColumn == "y",
                sortAscending = sortAscending,
                onClick = {
                    when {
                        sortColumn == "y" && !sortAscending -> sortAscending = true
                        sortColumn == "y" && sortAscending -> { sortColumn = null; sortAscending = false }
                        else -> { sortColumn = "y"; sortAscending = false }
                    }
                }
            )
            TableHeader(
                text = "Score",
                width = 80.dp,
                sortable = true,
                isCurrentSort = sortColumn == "score" || sortColumn == null,
                sortAscending = sortAscending,
                onClick = {
                    when {
                        (sortColumn == "score" || sortColumn == null) && !sortAscending -> sortAscending = true
                        (sortColumn == "score" || sortColumn == null) && sortAscending -> { sortColumn = null; sortAscending = false }
                        else -> { sortColumn = "score"; sortAscending = false }
                    }
                }
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )

        // Data rows sorted by score (accounting for Y-axis inversion)
        sortedData.forEach { point ->
            // Calculate display score based on inversion
            val score = if (invertYAxis) point.x - point.y else point.sum
            val quadrantColor = getQuadrantColor(point)

            // Get rank from scoreRankMap
            val rank = scoreRankMap[point] ?: 0

            // Check if this team is highlighted
            // For TEAM scatter plots, extract team code from label (first word)
            // For PLAYER scatter plots, use the teamCode property
            // Check if this row should be highlighted
            val isHighlighted = when {
                // If player labels are selected, check if this player is in the set
                highlightedPlayerLabels.isNotEmpty() -> {
                    highlightedPlayerLabels.contains(point.label)
                }
                // For team-based highlighting, extract and check team code
                highlightedTeamCodes.isNotEmpty() -> {
                    val teamCode = if (subject == "TEAM") {
                        point.label.split(" ").firstOrNull() ?: ""
                    } else {
                        point.teamCode ?: ""
                    }
                    highlightedTeamCodes.contains(teamCode)
                }
                else -> false
            }

            // Calculate win percentage
            val winPct = if (point.wins != null && point.losses != null) {
                val totalGames = point.wins + point.losses
                if (totalGames > 0) {
                    (point.wins.toDouble() / totalGames * 100).formatTo(1)
                } else {
                    "-"
                }
            } else {
                "-"
            }

            // Format conference ranking
            val confRank = formatConferenceRank(point.conferenceRank, point.conference)

            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(rank.toString(), 50.dp)
                // Team name with colored dot (clickable)
                Row(
                    modifier = Modifier
                        .width(110.dp)
                        .clickable { onTeamClick(point.label) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(quadrantColor, CircleShape)
                    )
                    Text(
                        text = point.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasRecords) {
                    TableCell(winPct, 60.dp)
                }
                if (hasConfRanks) {
                    TableCell(confRank, 100.dp)
                }
                if (hasTeamCodes) {
                    TableCell(point.teamCode ?: "-", 50.dp)
                }
                TableCell(point.x.formatTo(2), 80.dp)
                TableCell(point.y.formatTo(2), 80.dp)
                TableCell(score.formatTo(2), 80.dp)
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun BarDataTable(
    data: List<BarGraphDataPoint>,
    onTeamClick: (String) -> Unit = {},
    highlightedTeamCodes: Set<String> = emptySet()
) {
    val horizontalScrollState = rememberScrollState()

    // Sort state - null sortColumn means default sort (value)
    var sortColumn by remember { mutableStateOf<String?>(null) }
    var sortAscending by remember { mutableStateOf(false) }

    // Check if any data point has win/loss records
    val hasRecords = data.any { it.wins != null && it.losses != null }

    // Check if any data point has conference rankings
    val hasConfRanks = data.any { it.conferenceRank != null }

    // Calculate rank based on default value sort (always descending)
    val valueRankMap = remember(data) {
        val sortedByValue = data.sortedByDescending { it.value }
        sortedByValue.mapIndexed { index, point -> point to (index + 1) }.toMap()
    }

    // Sort data based on selected column
    val sortedData = remember(data, sortColumn, sortAscending) {
        when (sortColumn) {
            "label" -> if (sortAscending) data.sortedBy { it.label } else data.sortedByDescending { it.label }
            "winPct" -> if (sortAscending) {
                data.sortedBy { point ->
                    if (point.wins != null && point.losses != null) {
                        val total = point.wins + point.losses
                        if (total > 0) point.wins.toDouble() / total else 0.0
                    } else 0.0
                }
            } else {
                data.sortedByDescending { point ->
                    if (point.wins != null && point.losses != null) {
                        val total = point.wins + point.losses
                        if (total > 0) point.wins.toDouble() / total else 0.0
                    } else 0.0
                }
            }
            "confRank" -> if (sortAscending) {
                data.sortedBy { it.conferenceRank ?: Int.MAX_VALUE }
            } else {
                data.sortedByDescending { it.conferenceRank ?: Int.MIN_VALUE }
            }
            "value", null -> if (sortAscending) data.sortedBy { it.value } else data.sortedByDescending { it.value }
            else -> data
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TableHeader(
                text = "Rank",
                width = 50.dp,
                sortable = true,
                isCurrentSort = sortColumn == null || sortColumn == "value",
                sortAscending = sortAscending,
                onClick = {
                    when {
                        sortColumn == null && !sortAscending -> sortAscending = true
                        sortColumn == null && sortAscending -> { sortColumn = null; sortAscending = false }
                        else -> { sortColumn = null; sortAscending = false }
                    }
                }
            )
            TableHeader(
                text = "Team",
                width = 80.dp,
                sortable = true,
                isCurrentSort = sortColumn == "label",
                sortAscending = sortAscending,
                onClick = {
                    when {
                        sortColumn == "label" && !sortAscending -> sortAscending = true
                        sortColumn == "label" && sortAscending -> { sortColumn = null; sortAscending = false }
                        else -> { sortColumn = "label"; sortAscending = false }
                    }
                }
            )
            if (hasRecords) {
                TableHeader(
                    text = "Win%",
                    width = 60.dp,
                    sortable = true,
                    isCurrentSort = sortColumn == "winPct",
                    sortAscending = sortAscending,
                    onClick = {
                        when {
                            sortColumn == "winPct" && !sortAscending -> sortAscending = true
                            sortColumn == "winPct" && sortAscending -> { sortColumn = null; sortAscending = false }
                            else -> { sortColumn = "winPct"; sortAscending = false }
                        }
                    }
                )
            }
            if (hasConfRanks) {
                TableHeader(
                    text = "Conf Rank",
                    width = 100.dp,
                    sortable = true,
                    isCurrentSort = sortColumn == "confRank",
                    sortAscending = sortAscending,
                    onClick = {
                        when {
                            sortColumn == "confRank" && !sortAscending -> sortAscending = true
                            sortColumn == "confRank" && sortAscending -> { sortColumn = null; sortAscending = false }
                            else -> { sortColumn = "confRank"; sortAscending = false }
                        }
                    }
                )
            }
            TableHeader(
                text = "Value",
                width = 100.dp,
                sortable = true,
                isCurrentSort = sortColumn == "value" || sortColumn == null,
                sortAscending = sortAscending,
                onClick = {
                    when {
                        (sortColumn == "value" || sortColumn == null) && !sortAscending -> sortAscending = true
                        (sortColumn == "value" || sortColumn == null) && sortAscending -> { sortColumn = null; sortAscending = false }
                        else -> { sortColumn = "value"; sortAscending = false }
                    }
                }
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )

        // Data rows
        sortedData.forEach { point ->
            val teamCode = point.label.split(" ").firstOrNull() ?: ""
            val isHighlighted = highlightedTeamCodes.contains(teamCode)

            // Get rank from valueRankMap
            val rank = valueRankMap[point] ?: 0

            // Calculate win percentage
            val winPct = if (point.wins != null && point.losses != null) {
                val totalGames = point.wins + point.losses
                if (totalGames > 0) {
                    (point.wins.toDouble() / totalGames * 100).formatTo(1)
                } else {
                    "-"
                }
            } else {
                "-"
            }

            // Format conference ranking
            val confRank = formatConferenceRank(point.conferenceRank, point.conference)

            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TableCell(rank.toString(), 50.dp)
                // Clickable team name
                Text(
                    text = point.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(80.dp)
                        .clickable { onTeamClick(point.label) }
                        .padding(vertical = 4.dp)
                )
                if (hasRecords) {
                    TableCell(winPct, 60.dp)
                }
                if (hasConfRanks) {
                    TableCell(confRank, 100.dp)
                }
                TableCell(point.value.formatTo(1), 100.dp)
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun LineDataTable(
    series: List<LineChartSeries>,
    onTeamClick: (String) -> Unit = {},
    highlightedTeamCodes: Set<String> = emptySet()
) {
    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScrollState)
    ) {
        // Header - show series names (clickable)
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TableHeader("Week/Game", 80.dp)
            series.forEach { s ->
                val teamCode = s.label.split(" ").firstOrNull() ?: ""
                val isHighlighted = highlightedTeamCodes.contains(teamCode)

                Text(
                    text = s.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(100.dp)
                        .clickable { onTeamClick(s.label) }
                        .padding(vertical = 4.dp)
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )

        // Data rows - show values for each week/game
        val maxPoints = series.maxOfOrNull { it.dataPoints.size } ?: 0
        val sampledIndices = if (maxPoints > 20) {
            // Sample every Nth point to keep table manageable
            val step = maxPoints / 20
            (0 until maxPoints step step).toList()
        } else {
            (0 until maxPoints).toList()
        }

        sampledIndices.forEach { pointIndex ->
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val xValue = series.firstOrNull()?.dataPoints?.getOrNull(pointIndex)?.x?.toInt() ?: pointIndex
                TableCell(xValue.toString(), 80.dp)

                series.forEach { s ->
                    val point = s.dataPoints.getOrNull(pointIndex)
                    val value = point?.y?.formatTo(1) ?: "-"
                    TableCell(value, 100.dp)
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun TableHeader(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    sortable: Boolean = false,
    isCurrentSort: Boolean = false,
    sortAscending: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .width(width)
            .clickable(enabled = sortable, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrentSort) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            fontWeight = if (isCurrentSort) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (sortable && isCurrentSort) {
            Text(
                text = if (sortAscending) "▲" else "▼",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

@Composable
private fun TableCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}

/**
 * Generic data table with pinned header row and pinned left column.
 * Uses synchronized scroll states for smooth scrolling experience.
 */
@Composable
private fun GenericDataTable(
    data: List<TableDataPoint>,
    onTeamClick: (String) -> Unit = {},
    highlightedTeamCodes: Set<String> = emptySet()
) {
    if (data.isEmpty()) {
        Text("No data available", modifier = Modifier.padding(8.dp))
        return
    }

    // Get column headers from first data point
    val columns = data.firstOrNull()?.columns ?: emptyList()

    // Scroll states for synchronized scrolling
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val headerHorizontalScrollState = rememberScrollState()
    val leftColumnVerticalScrollState = rememberScrollState()

    // Sync horizontal scroll between header and data area
    LaunchedEffect(horizontalScrollState) {
        snapshotFlow { horizontalScrollState.value }
            .collect { value ->
                if (headerHorizontalScrollState.value != value) {
                    headerHorizontalScrollState.scrollTo(value)
                }
            }
    }

    LaunchedEffect(headerHorizontalScrollState) {
        snapshotFlow { headerHorizontalScrollState.value }
            .collect { value ->
                if (horizontalScrollState.value != value) {
                    horizontalScrollState.scrollTo(value)
                }
            }
    }

    // Sync vertical scroll between left column and data area
    LaunchedEffect(verticalScrollState) {
        snapshotFlow { verticalScrollState.value }
            .collect { value ->
                if (leftColumnVerticalScrollState.value != value) {
                    leftColumnVerticalScrollState.scrollTo(value)
                }
            }
    }

    LaunchedEffect(leftColumnVerticalScrollState) {
        snapshotFlow { leftColumnVerticalScrollState.value }
            .collect { value ->
                if (verticalScrollState.value != value) {
                    verticalScrollState.scrollTo(value)
                }
            }
    }

    val labelColumnWidth = 100.dp
    val dataColumnWidth = 90.dp
    val rowHeight = 36.dp
    val headerHeight = 40.dp
    val backgroundColor = MaterialTheme.colorScheme.background
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Column(modifier = Modifier.fillMaxSize()) {
        // Top row: pinned corner + scrollable header (fixed height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
        ) {
            // Top-left corner: pinned "Team" header
            Box(
                modifier = Modifier
                    .width(labelColumnWidth)
                    .fillMaxHeight()
                    .background(backgroundColor),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Team",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // Vertical divider between pinned column and scrollable area
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = dividerColor,
                thickness = 1.dp
            )

            // Top-right: scrollable column headers
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(headerHorizontalScrollState)
                    .background(backgroundColor),
                verticalAlignment = Alignment.CenterVertically
            ) {
                columns.forEach { column ->
                    Box(
                        modifier = Modifier
                            .width(dataColumnWidth)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = column.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        // Header divider
        HorizontalDivider(color = dividerColor, thickness = 1.dp)

        // Bottom row: pinned left column + scrollable data (takes remaining space)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)  // Take remaining vertical space
        ) {
            // Bottom-left: pinned team names column (vertically scrollable)
            Column(
                modifier = Modifier
                    .width(labelColumnWidth)
                    .fillMaxHeight()
                    .verticalScroll(leftColumnVerticalScrollState)
                    .background(backgroundColor)
            ) {
                data.forEach { point ->
                    val teamCode = point.label.split(" ").firstOrNull() ?: ""
                    val isHighlighted = highlightedTeamCodes.contains(teamCode)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .clickable { onTeamClick(point.label) }
                            .background(backgroundColor)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isHighlighted) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                }
            }

            // Vertical divider between pinned column and scrollable area
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                color = dividerColor,
                thickness = 1.dp
            )

            // Bottom-right: scrollable data area (both directions)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(horizontalScrollState)
                    .verticalScroll(verticalScrollState)
            ) {
                data.forEach { point ->
                    Row(
                        modifier = Modifier.height(rowHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        point.columns.forEach { column ->
                            Box(
                                modifier = Modifier
                                    .width(dataColumnWidth)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = column.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                }
            }
        }
    }
}

/**
 * Matchup Report Cards - displays a list of matchups with comparison stats
 */
@Composable
private fun MatchupReportCards(matchups: List<Matchup>) {
    if (matchups.isEmpty()) {
        Text("No matchups available", modifier = Modifier.padding(8.dp))
        return
    }

    val verticalScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(verticalScrollState)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        matchups.forEach { matchup ->
            MatchupCard(matchup)
        }
    }
}

@Composable
private fun MatchupCard(matchup: Matchup) {
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val highlightColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp)
    ) {
        // Matchup header: Away @ Home
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = matchup.awayTeam,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "  @  ",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = matchup.homeTeam,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Game time if available
        matchup.gameTime?.let { time ->
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = dividerColor, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // Comparison rows
        matchup.comparisons.forEach { comparison ->
            ComparisonRow(
                comparison = comparison,
                awayTeam = matchup.awayTeam,
                homeTeam = matchup.homeTeam,
                highlightColor = highlightColor
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    comparison: MatchupComparison,
    awayTeam: String,
    homeTeam: String,
    highlightColor: Color
) {
    val awayValue = comparison.awayValueDisplay()
    val homeValue = comparison.homeValueDisplay()

    // Determine which team has the better value (if numeric)
    val awayNumeric = comparison.awayValueAsDouble()
    val homeNumeric = comparison.homeValueAsDouble()

    // Use the inverted property to determine if lower is better
    val awayIsBetter = when {
        awayNumeric == null || homeNumeric == null -> false
        comparison.inverted -> awayNumeric < homeNumeric
        else -> awayNumeric > homeNumeric
    }

    val homeIsBetter = when {
        awayNumeric == null || homeNumeric == null -> false
        comparison.inverted -> homeNumeric < awayNumeric
        else -> homeNumeric > awayNumeric
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Away team value
        Text(
            text = awayValue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (awayIsBetter) FontWeight.Bold else FontWeight.Normal,
            color = if (awayIsBetter) highlightColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(60.dp)
        )

        // Stat title (centered)
        Text(
            text = comparison.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Home team value
        Text(
            text = homeValue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (homeIsBetter) FontWeight.Bold else FontWeight.Normal,
            color = if (homeIsBetter) highlightColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(60.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
