package com.joebad.fastbreak.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeViewport
import com.joebad.fastbreak.data.model.BarGraphVisualization
import com.joebad.fastbreak.data.model.LineChartVisualization
import com.joebad.fastbreak.data.model.Matchup
import com.joebad.fastbreak.data.model.MatchupComparison
import com.joebad.fastbreak.data.model.MatchupVisualization
import com.joebad.fastbreak.data.model.ScatterPlotVisualization
import com.joebad.fastbreak.data.model.TableVisualization
import com.joebad.fastbreak.data.model.VisualizationType
import com.joebad.fastbreak.ui.BarChartComponent
import com.joebad.fastbreak.ui.DataTableComponent
import com.joebad.fastbreak.ui.FourQuadrantScatterPlot
import com.joebad.fastbreak.ui.LineChartComponent
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import fastbreak.webapp.generated.resources.Res
import fastbreak.webapp.generated.resources.special_elite
import fastbreak.webapp.generated.resources.jetbrains_mono
import fastbreak.webapp.generated.resources.logo
import kotlin.time.Instant

// CompositionLocal for typewriter font
private val LocalTypewriterFont = compositionLocalOf<FontFamily> { FontFamily.Monospace }

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

// External JS function for getting current time
@JsFun("() => Date.now()")
private external fun dateNow(): Double

// Format instant as relative time (e.g., "2h ago", "3d ago")
private fun formatRelativeTime(instant: kotlin.time.Instant): String {
    val nowMs = dateNow().toLong()
    val instantMs = instant.toEpochMilliseconds()
    val diffMs = nowMs - instantMs
    val minutes = (diffMs / 60000).toInt()
    val hours = (diffMs / 3600000).toInt()
    val days = (diffMs / 86400000).toInt()

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        else -> "${days / 30}mo ago"
    }
}

// Parse JSON into the appropriate visualization type based on visualizationType field
private fun parseVisualization(jsonContent: String): VisualizationType? {
    return try {
        val jsonElement = json.parseToJsonElement(jsonContent)
        val vizType = jsonElement.jsonObject["visualizationType"]?.jsonPrimitive?.content
        val title = jsonElement.jsonObject["title"]?.jsonPrimitive?.content ?: "unknown"

        println("Parsing chart: $title (type: $vizType)")

        when (vizType) {
            "SCATTER_PLOT" -> json.decodeFromString<ScatterPlotVisualization>(jsonContent)
            "BAR_GRAPH" -> json.decodeFromString<BarGraphVisualization>(jsonContent)
            "LINE_CHART" -> json.decodeFromString<LineChartVisualization>(jsonContent)
            "TABLE" -> json.decodeFromString<TableVisualization>(jsonContent)
            "MATCHUP" -> json.decodeFromString<MatchupVisualization>(jsonContent).also {
                println("  Successfully parsed MATCHUP with ${it.dataPoints.size} matchups")
            }
            else -> {
                println("Unknown visualization type: $vizType")
                null
            }
        }
    } catch (e: Exception) {
        println("Failed to parse chart: ${e.message}")
        e.printStackTrace()
        null
    }
}

// Open URL in new tab
@JsFun("(url) => window.open(url, '_blank')")
private external fun openUrl(url: String)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = document.getElementById("ComposeTarget") ?: return
    ComposeViewport(container) {
        FastbreakWebApp()
    }
}

@Composable
fun FastbreakWebApp() {
    var isDarkMode by remember { mutableStateOf(true) }
    val typewriterFont = FontFamily(Font(Res.font.special_elite))

    CompositionLocalProvider(LocalTypewriterFont provides typewriterFont) {
        MaterialTheme(
            colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                ChartGallery(
                    isDarkMode = isDarkMode,
                    onToggleTheme = { isDarkMode = !isDarkMode }
                )
            }
        }
    }
}

@Composable
fun ChartGallery(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    val charts: List<VisualizationType> = remember {
        BundledChartData.charts.values.mapNotNull { jsonContent ->
            parseVisualization(jsonContent)
        }.also { chartList ->
            println("Parsed ${chartList.size} charts")
            chartList.forEach { chart ->
                println("  - ${chart.title} (${chart::class.simpleName})")
            }
        }
    }

    // Group charts by sport
    val sportGroups = remember(charts) {
        charts.groupBy { it.sport.uppercase() }
    }

    // Sort sports with NFL first, then alphabetically
    val sports = remember(sportGroups) {
        sportGroups.keys.sortedWith(compareBy { if (it == "NFL") "0" else it })
    }
    var selectedSport by remember { mutableStateOf(sports.firstOrNull() ?: "NFL") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, top = 12.dp, end = 0.dp, bottom = 12.dp)
    ) {
        // Header with logo, title, and controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // App logo
                Image(
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = "Fastbreak",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Text(
                    text = "fastbreak",
                    fontFamily = FontFamily(Font(Res.font.jetbrains_mono)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Controls: ? icon + Theme toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Question mark icon button
                Text(
                    text = "?",
                    fontFamily = LocalTypewriterFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showAboutDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // Theme toggle (smaller)
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onToggleTheme() },
                    modifier = Modifier.scale(0.7f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        // Sport tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sports.forEach { sport ->
                val isSelected = sport == selectedSport
                Box(
                    modifier = Modifier
                        .clickable { selectedSport = sport }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = sport,
                        fontFamily = LocalTypewriterFont.current,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val filteredCharts = sportGroups[selectedSport] ?: emptyList()

        // Separate matchup visualizations from regular charts
        val regularCharts = filteredCharts.filterNot { it is MatchupVisualization }
        val matchupVisualizations = filteredCharts.filterIsInstance<MatchupVisualization>()

        println("Selected sport: $selectedSport, Total: ${filteredCharts.size}, Regular: ${regularCharts.size}, Matchups: ${matchupVisualizations.size}")

        if (filteredCharts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No charts available",
                    fontFamily = LocalTypewriterFont.current,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 32.dp)
            ) {
                // Regular chart cards
                items(regularCharts) { viz ->
                    ChartCard(viz)
                }

                // Matchup section - spans full width
                matchupVisualizations.forEach { matchupViz ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MatchupSection(matchupViz)
                    }
                }
            }
        }
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = "Fastbreak",
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = "fastbreak",
                    fontFamily = FontFamily(Font(Res.font.jetbrains_mono)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "A fast dashboard for advanced sports analytics conversations.",
                    fontFamily = LocalTypewriterFont.current,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Goals
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "• Fast load speeds",
                        fontFamily = LocalTypewriterFont.current,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Chart interactivity",
                        fontFamily = LocalTypewriterFont.current,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Conversation starter",
                        fontFamily = LocalTypewriterFont.current,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Free and no ads",
                        fontFamily = LocalTypewriterFont.current,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Links inline
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "iOS (Coming soon)",
                        fontFamily = LocalTypewriterFont.current,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Android (Coming soon)",
                        fontFamily = LocalTypewriterFont.current,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "GitHub",
                        fontFamily = LocalTypewriterFont.current,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { openUrl("https://github.com/joe307bad/fastbreak") }
                    )
                    Text(
                        text = "Discord",
                        fontFamily = LocalTypewriterFont.current,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { openUrl("https://discord.gg/KtqmASc6jn") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    fontFamily = LocalTypewriterFont.current
                )
            }
        }
    )
}

@Composable
fun ChartCard(viz: VisualizationType) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Title
        Text(
            text = viz.title,
            fontFamily = LocalTypewriterFont.current,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Subtitle
        Text(
            text = viz.subtitle,
            fontFamily = LocalTypewriterFont.current,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )

        // Render the appropriate chart component based on type
        // All charts use consistent fixed height to ensure legend is visible
        val chartModifier = Modifier.fillMaxWidth().height(400.dp)

        when (viz) {
            is ScatterPlotVisualization -> {
                FourQuadrantScatterPlot(
                    data = viz.dataPoints,
                    title = viz.title,
                    xAxisLabel = viz.xAxisLabel,
                    yAxisLabel = viz.yAxisLabel,
                    invertYAxis = viz.invertYAxis,
                    quadrantTopRight = viz.quadrantTopRight,
                    quadrantTopLeft = viz.quadrantTopLeft,
                    quadrantBottomLeft = viz.quadrantBottomLeft,
                    quadrantBottomRight = viz.quadrantBottomRight,
                    modifier = chartModifier
                )
            }
            is BarGraphVisualization -> {
                BarChartComponent(
                    data = viz.dataPoints,
                    modifier = chartModifier
                )
            }
            is LineChartVisualization -> {
                LineChartComponent(
                    series = viz.series,
                    modifier = chartModifier
                )
            }
            is TableVisualization -> {
                DataTableComponent(
                    visualization = viz,
                    modifier = chartModifier
                )
            }
            is MatchupVisualization -> {
                DataTableComponent(
                    visualization = viz,
                    modifier = chartModifier
                )
            }
        }

        // Footer: source and last updated
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            viz.source?.let { source ->
                Text(
                    text = "src: $source",
                    fontFamily = LocalTypewriterFont.current,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatRelativeTime(viz.lastUpdated),
                fontFamily = LocalTypewriterFont.current,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Section displaying matchup report cards in a grid layout
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatchupSection(visualization: MatchupVisualization) {
    val matchups = visualization.dataPoints

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp, end = 32.dp)
    ) {
        // Section header
        Text(
            text = visualization.title,
            fontFamily = LocalTypewriterFont.current,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = visualization.subtitle,
            fontFamily = LocalTypewriterFont.current,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Grid of matchup cards
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            matchups.forEach { matchup ->
                MatchupCard(matchup)
            }
        }

        // Footer with source and last updated
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            visualization.source?.let { source ->
                Text(
                    text = "src: $source",
                    fontFamily = LocalTypewriterFont.current,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatRelativeTime(visualization.lastUpdated),
                fontFamily = LocalTypewriterFont.current,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Individual matchup card showing team comparison
 */
@Composable
fun MatchupCard(matchup: Matchup) {
    val awayColor = Color(0xFF2196F3) // Blue
    val homeColor = Color(0xFFFF5722) // Deep Orange

    // Calculate edges
    var awayEdges = 0
    var homeEdges = 0

    matchup.comparisons.forEach { comparison ->
        val awayNumeric = comparison.awayValueAsDouble()
        val homeNumeric = comparison.homeValueAsDouble()

        if (awayNumeric != null && homeNumeric != null) {
            val awayIsBetter = if (comparison.inverted) {
                awayNumeric < homeNumeric
            } else {
                awayNumeric > homeNumeric
            }
            val homeIsBetter = if (comparison.inverted) {
                homeNumeric < awayNumeric
            } else {
                homeNumeric > awayNumeric
            }

            if (awayIsBetter) awayEdges++
            if (homeIsBetter) homeEdges++
        }
    }

    Column(
        modifier = Modifier
            .width(320.dp)
            .padding(16.dp)
    ) {
        // Team header
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
                    fontFamily = LocalTypewriterFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = awayColor
                )
                Text(
                    text = "AWAY",
                    fontFamily = LocalTypewriterFont.current,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "@",
                fontFamily = LocalTypewriterFont.current,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Home team
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = matchup.homeTeam,
                    fontFamily = LocalTypewriterFont.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = homeColor
                )
                Text(
                    text = "HOME",
                    fontFamily = LocalTypewriterFont.current,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Edge summary bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$awayEdges edges",
                fontFamily = LocalTypewriterFont.current,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = awayColor
            )
            Text(
                text = "$homeEdges edges",
                fontFamily = LocalTypewriterFont.current,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = homeColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Visual edge bar
        val total = awayEdges + homeEdges
        val awayFraction = if (total > 0) awayEdges.toFloat() / total else 0.5f
        val homeFraction = if (total > 0) homeEdges.toFloat() / total else 0.5f

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
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

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))

        // Comparison rows
        matchup.comparisons.forEach { comparison ->
            MatchupComparisonRow(
                comparison = comparison,
                awayColor = awayColor,
                homeColor = homeColor
            )
        }
    }
}

@Composable
private fun MatchupComparisonRow(
    comparison: MatchupComparison,
    awayColor: Color,
    homeColor: Color
) {
    val awayValue = comparison.awayValueDisplay()
    val homeValue = comparison.homeValueDisplay()
    val awayNumeric = comparison.awayValueAsDouble()
    val homeNumeric = comparison.homeValueAsDouble()

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
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Away value
        Text(
            text = awayValue,
            fontFamily = LocalTypewriterFont.current,
            fontSize = 11.sp,
            fontWeight = if (awayIsBetter) FontWeight.Bold else FontWeight.Normal,
            color = if (awayIsBetter) awayColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(50.dp)
        )

        // Stat title
        Text(
            text = comparison.title,
            fontFamily = LocalTypewriterFont.current,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        // Home value
        Text(
            text = homeValue,
            fontFamily = LocalTypewriterFont.current,
            fontSize = 11.sp,
            fontWeight = if (homeIsBetter) FontWeight.Bold else FontWeight.Normal,
            color = if (homeIsBetter) homeColor else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(50.dp)
        )
    }
}
