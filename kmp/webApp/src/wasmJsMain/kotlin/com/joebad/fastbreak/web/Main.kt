package com.joebad.fastbreak.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
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

        when (vizType) {
            "SCATTER_PLOT" -> json.decodeFromString<ScatterPlotVisualization>(jsonContent)
            "BAR_GRAPH" -> json.decodeFromString<BarGraphVisualization>(jsonContent)
            "LINE_CHART" -> json.decodeFromString<LineChartVisualization>(jsonContent)
            "TABLE" -> json.decodeFromString<TableVisualization>(jsonContent)
            else -> {
                println("Unknown visualization type: $vizType")
                null
            }
        }
    } catch (e: Exception) {
        println("Failed to parse chart: ${e.message}")
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
                items(filteredCharts) { viz ->
                    ChartCard(viz)
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
