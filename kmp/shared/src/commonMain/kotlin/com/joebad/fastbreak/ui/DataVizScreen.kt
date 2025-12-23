package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.navigation.DataVizComponent
import com.joebad.fastbreak.ui.visualizations.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun DataVizScreen(
    component: DataVizComponent,
    onMenuClick: () -> Unit = {}
) {
    var state by remember { mutableStateOf<DataVizState>(DataVizState.Loading) }
    var refreshTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    // Observe registry state for sync failures
    val registryState by component.registryContainer.container.stateFlow.collectAsState()

    // Load cached data when component is first displayed or when refresh is triggered
    LaunchedEffect(component.chartId, refreshTrigger) {
        state = DataVizState.Loading

        try {
            // First check if this chart failed during synchronization
            val syncProgress = registryState.syncProgress
            val failedChart = syncProgress?.failedCharts?.find { it.first == component.chartId }
            if (failedChart != null) {
                val (_, errorMessage) = failedChart
                state = DataVizState.Error("Failed to sync chart data: $errorMessage")
                return@LaunchedEffect
            }

            // Load from cache using chartId
            val cachedData = component.chartDataRepository.getChartData(component.chartId)
            if (cachedData != null) {
                // Data is cached, deserialize and show immediately
                val visualization = cachedData.deserialize()
                state = DataVizState.Success(visualization)
            } else {
                // No cached data available
                state = DataVizState.Error("Chart data not available. Please refresh.")
            }
        } catch (e: Exception) {
            state = DataVizState.Error(e.message ?: "Unknown error")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (val currentState = state) {
                        is DataVizState.Success -> currentState.data.title
                        else -> "Loading..."
                    }
                    Text(
                        text = titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = component.onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (val currentState = state) {
                is DataVizState.Loading -> LoadingContent()
                is DataVizState.Success -> SuccessContent(currentState.data)
                is DataVizState.Error -> ErrorContent(
                    message = currentState.message,
                    onRetry = {
                        scope.launch {
                            // Show loading state immediately
                            state = DataVizState.Loading

                            // Trigger a registry refresh which will re-sync the chart data
                            component.registryContainer.refreshRegistry()

                            // Wait for the sync to actually start (isSyncing becomes true)
                            component.registryContainer.container.stateFlow
                                .first { it.isSyncing }

                            // Now wait for sync to complete (isSyncing becomes false again)
                            component.registryContainer.container.stateFlow
                                .first { !it.isSyncing }

                            // Reload the chart data manually (same logic as LaunchedEffect)
                            try {
                                val currentRegistryState = component.registryContainer.container.stateFlow.value
                                val syncProgress = currentRegistryState.syncProgress
                                val failedChart = syncProgress?.failedCharts?.find { it.first == component.chartId }
                                if (failedChart != null) {
                                    val (_, errorMessage) = failedChart
                                    state = DataVizState.Error("Failed to sync chart data: $errorMessage")
                                    return@launch
                                }

                                val cachedData = component.chartDataRepository.getChartData(component.chartId)
                                if (cachedData != null) {
                                    val visualization = cachedData.deserialize()
                                    state = DataVizState.Success(visualization)
                                } else {
                                    state = DataVizState.Error("Chart data not available. Please refresh.")
                                }
                            } catch (e: Exception) {
                                state = DataVizState.Error(e.message ?: "Unknown error")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Loading data...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SuccessContent(visualization: VisualizationType) {
    // State for filters
    var selectedFilters by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Extract filter options and apply filtering
    val (filterOptions, filteredVisualization) = remember(visualization, selectedFilters) {
        extractFiltersAndApplyFiltering(visualization, selectedFilters)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Show filter bar if there are any filterable properties
        if (filterOptions.isNotEmpty()) {
            FilterBar(
                filters = filterOptions,
                selectedFilters = selectedFilters,
                onFilterChange = { key, value ->
                    selectedFilters = if (value == null) {
                        selectedFilters - key
                    } else {
                        selectedFilters + (key to value)
                    }
                }
            )
        }

        // Render the visualization with filtered data
        RenderVisualization(filteredVisualization)
    }
}

@Composable
private fun RenderVisualization(visualization: VisualizationType) {
    // TableVisualization has its own scrolling, so don't wrap it in verticalScroll
    if (visualization is TableVisualization) {
        // For tables: use Column with table taking most space, source at bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Table takes remaining space
            DataTableComponent(
                visualization = visualization,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Source attribution at bottom
            SourceAttribution(source = visualization.source)
        }
    } else if (visualization is MatchupVisualization) {
        // Matchup has its own dedicated screen with dropdown selector
        MatchupScreen(
            visualization = visualization,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // For charts: use vertical scroll to show chart + data table
        val scrollState = rememberScrollState()

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val isLandscape = maxWidth > maxHeight

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Visualization - with right padding in landscape so users can scroll without triggering pan gestures
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        when (visualization) {
                            is ScatterPlotVisualization -> {
                                KoalaQuadrantScatterPlot(
                                    data = visualization.dataPoints,
                                    title = visualization.title,
                                    xAxisLabel = visualization.xAxisLabel,
                                    yAxisLabel = visualization.yAxisLabel,
                                    invertYAxis = visualization.invertYAxis,
                                    quadrantTopRight = visualization.quadrantTopRight,
                                    quadrantTopLeft = visualization.quadrantTopLeft,
                                    quadrantBottomLeft = visualization.quadrantBottomLeft,
                                    quadrantBottomRight = visualization.quadrantBottomRight,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is BarGraphVisualization -> {
                                BarChartComponent(
                                    data = visualization.dataPoints,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is LineChartVisualization -> {
                                LineChartComponent(
                                    series = visualization.series,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is TableVisualization -> {
                                // Handled above
                            }
                            is MatchupVisualization -> {
                                // Matchup report cards - handled in DataTableComponent
                            }
                        }
                    }
                    // Right padding area for scrolling (only in landscape)
                    if (isLandscape) {
                        Spacer(modifier = Modifier.width(40.dp))
                    }
                }

                // Data table - full width, minimal padding
                DataTableComponent(
                    visualization = visualization,
                    modifier = Modifier.fillMaxWidth()
                )

                // Source attribution at bottom
                SourceAttribution(source = visualization.source)
            }
        }
    }
}

@Composable
private fun SourceAttribution(source: String?) {
    if (source != null) {
        Text(
            text = "Data source: $source",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

private sealed interface DataVizState {
    data object Loading : DataVizState
    data class Success(val data: VisualizationType) : DataVizState
    data class Error(val message: String) : DataVizState
}

/**
 * Extracts available filter options and returns filtered visualization data
 */
private fun extractFiltersAndApplyFiltering(
    visualization: VisualizationType,
    selectedFilters: Map<String, String>
): Pair<List<FilterOption>, VisualizationType> {
    return when (visualization) {
        is BarGraphVisualization -> {
            val filters = extractBarGraphFilters(visualization.dataPoints)
            val filtered = applyBarGraphFilters(visualization, selectedFilters)
            filters to filtered
        }
        is ScatterPlotVisualization -> {
            val filters = extractScatterPlotFilters(visualization.dataPoints)
            val filtered = applyScatterPlotFilters(visualization, selectedFilters)
            filters to filtered
        }
        is LineChartVisualization -> {
            val filters = extractLineChartFilters(visualization.series)
            val filtered = applyLineChartFilters(visualization, selectedFilters)
            filters to filtered
        }
        is MatchupVisualization -> {
            val filters = extractMatchupFilters(visualization.dataPoints)
            val filtered = applyMatchupFilters(visualization, selectedFilters)
            filters to filtered
        }
        else -> emptyList<FilterOption>() to visualization
    }
}

// Bar Graph filtering
private fun extractBarGraphFilters(dataPoints: List<BarGraphDataPoint>): List<FilterOption> {
    val filters = mutableListOf<FilterOption>()

    val divisions = dataPoints.mapNotNull { it.division }.distinct().sorted()
    if (divisions.isNotEmpty()) {
        filters.add(FilterOption("division", "Division", divisions))
    }

    val conferences = dataPoints.mapNotNull { it.conference }.distinct().sorted()
    if (conferences.isNotEmpty()) {
        filters.add(FilterOption("conference", "Conf", conferences))
    }

    return filters
}

private fun applyBarGraphFilters(
    visualization: BarGraphVisualization,
    selectedFilters: Map<String, String>
): BarGraphVisualization {
    if (selectedFilters.isEmpty()) return visualization

    val filteredDataPoints = visualization.dataPoints.filter { point ->
        selectedFilters.all { (key, value) ->
            when (key) {
                "division" -> point.division == value
                "conference" -> point.conference == value
                else -> true
            }
        }
    }

    return visualization.copy(dataPoints = filteredDataPoints)
}

// Scatter Plot filtering
private fun extractScatterPlotFilters(dataPoints: List<ScatterPlotDataPoint>): List<FilterOption> {
    val filters = mutableListOf<FilterOption>()

    val divisions = dataPoints.mapNotNull { it.division }.distinct().sorted()
    if (divisions.isNotEmpty()) {
        filters.add(FilterOption("division", "Division", divisions))
    }

    val conferences = dataPoints.mapNotNull { it.conference }.distinct().sorted()
    if (conferences.isNotEmpty()) {
        filters.add(FilterOption("conference", "Conf", conferences))
    }

    return filters
}

private fun applyScatterPlotFilters(
    visualization: ScatterPlotVisualization,
    selectedFilters: Map<String, String>
): ScatterPlotVisualization {
    if (selectedFilters.isEmpty()) return visualization

    val filteredDataPoints = visualization.dataPoints.filter { point ->
        selectedFilters.all { (key, value) ->
            when (key) {
                "division" -> point.division == value
                "conference" -> point.conference == value
                else -> true
            }
        }
    }

    return visualization.copy(dataPoints = filteredDataPoints)
}

// Line Chart filtering
private fun extractLineChartFilters(series: List<LineChartSeries>): List<FilterOption> {
    val filters = mutableListOf<FilterOption>()

    val divisions = series.mapNotNull { it.division }.distinct().sorted()
    if (divisions.isNotEmpty()) {
        filters.add(FilterOption("division", "Division", divisions))
    }

    val conferences = series.mapNotNull { it.conference }.distinct().sorted()
    if (conferences.isNotEmpty()) {
        filters.add(FilterOption("conference", "Conf", conferences))
    }

    return filters
}

private fun applyLineChartFilters(
    visualization: LineChartVisualization,
    selectedFilters: Map<String, String>
): LineChartVisualization {
    if (selectedFilters.isEmpty()) return visualization

    val filteredSeries = visualization.series.filter { series ->
        selectedFilters.all { (key, value) ->
            when (key) {
                "division" -> series.division == value
                "conference" -> series.conference == value
                else -> true
            }
        }
    }

    return visualization.copy(series = filteredSeries)
}

// Matchup filtering
private fun extractMatchupFilters(dataPoints: List<Matchup>): List<FilterOption> {
    val filters = mutableListOf<FilterOption>()

    val divisions = (dataPoints.mapNotNull { it.homeTeamDivision } +
            dataPoints.mapNotNull { it.awayTeamDivision }).distinct().sorted()
    if (divisions.isNotEmpty()) {
        filters.add(FilterOption("division", "Division", divisions))
    }

    val conferences = (dataPoints.mapNotNull { it.homeTeamConference } +
            dataPoints.mapNotNull { it.awayTeamConference }).distinct().sorted()
    if (conferences.isNotEmpty()) {
        filters.add(FilterOption("conference", "Conf", conferences))
    }

    return filters
}

private fun applyMatchupFilters(
    visualization: MatchupVisualization,
    selectedFilters: Map<String, String>
): MatchupVisualization {
    if (selectedFilters.isEmpty()) return visualization

    val filteredDataPoints = visualization.dataPoints.filter { matchup ->
        selectedFilters.all { (key, value) ->
            when (key) {
                "division" -> matchup.homeTeamDivision == value || matchup.awayTeamDivision == value
                "conference" -> matchup.homeTeamConference == value || matchup.awayTeamConference == value
                else -> true
            }
        }
    }

    return visualization.copy(dataPoints = filteredDataPoints)
}
