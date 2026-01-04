package com.joebad.fastbreak.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.*
import com.joebad.fastbreak.navigation.DataVizComponent
import com.joebad.fastbreak.ui.visualizations.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun DataVizScreen(
    component: DataVizComponent,
    onMenuClick: () -> Unit = {},
    pinnedTeams: List<PinnedTeam> = emptyList()
) {
    var state by remember { mutableStateOf<DataVizState>(DataVizState.Loading) }
    var refreshTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showInfoSheet by remember { mutableStateOf(false) }

    // Observe registry state for sync failures
    val registryState by component.registryContainer.container.stateFlow.collectAsState()

    // Load cached data when component is first displayed or when refresh is triggered
    LaunchedEffect(component.chartId, refreshTrigger) {
        state = DataVizState.Loading

        try {
            // Check if this is the hardcoded playoff bracket
            if (component.chartId == "playoff-bracket-nfl") {
                // Create fake NFL playoff data
                val fakePlayoffData = createFakeNFLPlayoffData()
                state = DataVizState.Success(fakePlayoffData)
                return@LaunchedEffect
            }

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
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.testTag("menu-button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu"
                        )
                    }
                },
                actions = {
                    // Show info icon only when data is loaded and has a description
                    if (state is DataVizState.Success && (state as DataVizState.Success).data.description.isNotEmpty()) {
                        IconButton(onClick = { showInfoSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Chart Info"
                            )
                        }
                    }
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
                is DataVizState.Success -> SuccessContent(
                    visualization = currentState.data,
                    pinnedTeams = pinnedTeams
                )
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

    // Bottom sheet for chart description
    if (showInfoSheet && state is DataVizState.Success) {
        val visualization = (state as DataVizState.Success).data
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = visualization.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = visualization.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun SuccessContent(
    visualization: VisualizationType,
    pinnedTeams: List<PinnedTeam>
) {
    // State for filters and team highlighting
    var selectedFilters by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedTeamCodes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedPlayerLabels by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Get pinned teams for this sport
    val sportPinnedTeams = remember(pinnedTeams, visualization.sport) {
        pinnedTeams.filter { it.sport == visualization.sport }
    }

    // Extract filter options and calculate which teams match the filter criteria
    val (filterOptions, filterHighlightedTeamCodes) = remember(visualization, selectedFilters) {
        val result = extractFiltersAndCalculateHighlights(visualization, selectedFilters)
        println("🔍 DataVizScreen - Filter Options: ${result.first.map { "${it.key}=${it.values.size} options" }}")
        println("🔍 DataVizScreen - Selected Filters: $selectedFilters")
        println("🔍 DataVizScreen - Filter Highlights: ${result.second}")
        result
    }

    // Combine team code highlights with filter highlights
    // For MatchupV2Visualization, automatically include pinned teams
    val allHighlightedTeamCodes = remember(selectedTeamCodes, filterHighlightedTeamCodes, selectedPlayerLabels, sportPinnedTeams, visualization) {
        val pinnedTeamCodes = if (visualization is MatchupV2Visualization) {
            sportPinnedTeams.map { it.teamCode }.toSet()
        } else {
            emptySet()
        }
        val combined = selectedTeamCodes + filterHighlightedTeamCodes + pinnedTeamCodes
        println("🔍 DataVizScreen - Selected Team Codes: $selectedTeamCodes")
        println("🔍 DataVizScreen - Selected Player Labels: $selectedPlayerLabels")
        println("🔍 DataVizScreen - Pinned Team Codes: $pinnedTeamCodes")
        println("🔍 DataVizScreen - All Highlighted Team Codes: $combined")
        combined
    }

    // For matchups, we still need to apply filtering (not just highlighting)
    val displayVisualization = remember(visualization, selectedFilters) {
        if (visualization is MatchupVisualization && selectedFilters.isNotEmpty()) {
            applyMatchupFilters(visualization, selectedFilters)
        } else {
            visualization
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Show filter bar if there are any filterable properties or pinned teams
        // BUT hide it for MatchupV2Visualization (it handles pinned teams internally)
        if ((filterOptions.isNotEmpty() || sportPinnedTeams.isNotEmpty()) && visualization !is MatchupV2Visualization) {
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team filter badges (on the left)
                if (sportPinnedTeams.isNotEmpty()) {
                    sportPinnedTeams.forEach { pinnedTeam ->
                        val isSelected = selectedTeamCodes.contains(pinnedTeam.teamCode)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTeamCodes = if (isSelected) {
                                    selectedTeamCodes - pinnedTeam.teamCode
                                } else {
                                    selectedTeamCodes + pinnedTeam.teamCode
                                }
                            },
                            label = {
                                Text(
                                    text = pinnedTeam.teamCode,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                // Standard filters (division, conference)
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
            }
        }

        // Render the visualization with all data and highlighted teams
        RenderVisualization(
            visualization = displayVisualization,
            highlightedTeamCodes = allHighlightedTeamCodes,
            highlightedPlayerLabels = selectedPlayerLabels,
            onTeamClick = { label ->
                // For PLAYER scatter plots, highlight individual players by label
                // For TEAM scatter plots (and others), highlight teams by extracting team code
                val isPlayerSubject = (displayVisualization as? ScatterPlotVisualization)?.subject == "PLAYER"

                println("🖱️ onTeamClick - label: '$label', isPlayerSubject: $isPlayerSubject, subject: ${(displayVisualization as? ScatterPlotVisualization)?.subject}")

                if (isPlayerSubject) {
                    // Toggle player label selection
                    selectedPlayerLabels = if (selectedPlayerLabels.contains(label)) {
                        selectedPlayerLabels - label
                    } else {
                        selectedPlayerLabels + label
                    }
                    println("🖱️ onTeamClick - Updated selectedPlayerLabels: $selectedPlayerLabels")
                } else {
                    // Extract team code from label (e.g., "PHI Eagles" -> "PHI")
                    val teamCode = label.split(" ").firstOrNull()
                    if (teamCode != null) {
                        selectedTeamCodes = if (selectedTeamCodes.contains(teamCode)) {
                            selectedTeamCodes - teamCode
                        } else {
                            selectedTeamCodes + teamCode
                        }
                        println("🖱️ onTeamClick - Updated selectedTeamCodes: $selectedTeamCodes")
                    }
                }
            }
        )
    }
}

@Composable
private fun RenderVisualization(
    visualization: VisualizationType,
    highlightedTeamCodes: Set<String> = emptySet(),
    highlightedPlayerLabels: Set<String> = emptySet(),
    onTeamClick: (String) -> Unit = {}
) {
    println("📊 RenderVisualization - highlightedPlayerLabels: $highlightedPlayerLabels")
    println("📊 RenderVisualization - visualization type: ${visualization::class.simpleName}")

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
                onTeamClick = onTeamClick,
                highlightedTeamCodes = highlightedTeamCodes,
                highlightedPlayerLabels = highlightedPlayerLabels,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Source attribution at bottom
            SourceAttribution(source = visualization.source)
        }
    } else if (visualization is MatchupVisualization) {
        // TODO: Matchup screen was removed
        Box(modifier = Modifier.fillMaxSize())
    } else if (visualization is MatchupV2Visualization) {
        // MatchupV2 has its own dedicated screen with dropdown selector and tabs
        MatchupWorksheet(
            visualization = visualization,
            modifier = Modifier.fillMaxSize(),
            highlightedTeamCodes = highlightedTeamCodes
        )
    } else if (visualization is PlayoffBracketVisualization) {
        // Playoff bracket: horizontally scrollable with source pinned to bottom
        Box(modifier = Modifier.fillMaxSize()) {
            PlayoffBracketComponent(
                visualization = visualization,
                modifier = Modifier.fillMaxSize()
            )

            // Source attribution pinned to bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                SourceAttribution(source = visualization.source)
            }
        }
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
                                QuadrantScatterPlot(
                                    data = visualization.dataPoints,
                                    title = visualization.title,
                                    xAxisLabel = visualization.xAxisLabel,
                                    yAxisLabel = visualization.yAxisLabel,
                                    invertYAxis = visualization.invertYAxis,
                                    quadrantTopRight = visualization.quadrantTopRight,
                                    quadrantTopLeft = visualization.quadrantTopLeft,
                                    quadrantBottomLeft = visualization.quadrantBottomLeft,
                                    quadrantBottomRight = visualization.quadrantBottomRight,
                                    subject = visualization.subject,
                                    highlightedTeamCodes = highlightedTeamCodes,
                                    highlightedPlayerLabels = highlightedPlayerLabels,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is BarGraphVisualization -> {
                                BarChartComponent(
                                    data = visualization.dataPoints,
                                    highlightedTeamCodes = highlightedTeamCodes,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is LineChartVisualization -> {
                                LineChartComponent(
                                    series = visualization.series,
                                    highlightedTeamCodes = highlightedTeamCodes,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is PlayoffBracketVisualization -> {
                                PlayoffBracketComponent(
                                    visualization = visualization,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is TableVisualization -> {
                                // Handled above
                            }
                            is MatchupVisualization -> {
                                // Matchup report cards - handled in DataTableComponent
                            }
                            is MatchupV2Visualization -> {
                                // Handled by MatchupWorksheet below
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
                    modifier = Modifier.fillMaxWidth(),
                    onTeamClick = onTeamClick,
                    highlightedTeamCodes = highlightedTeamCodes,
                    highlightedPlayerLabels = highlightedPlayerLabels
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
 * Extracts available filter options and calculates which team codes match the filter criteria.
 * Returns filter options and a set of team codes that should be highlighted.
 */
private fun extractFiltersAndCalculateHighlights(
    visualization: VisualizationType,
    selectedFilters: Map<String, String>
): Pair<List<FilterOption>, Set<String>> {
    return when (visualization) {
        is BarGraphVisualization -> {
            val filters = extractBarGraphFilters(visualization.dataPoints)
            val highlights = calculateBarGraphHighlights(visualization.dataPoints, selectedFilters)
            filters to highlights
        }
        is ScatterPlotVisualization -> {
            val filters = extractScatterPlotFilters(visualization.dataPoints, visualization.subject)
            val highlights = calculateScatterPlotHighlights(visualization.dataPoints, selectedFilters, visualization.subject)
            filters to highlights
        }
        is LineChartVisualization -> {
            val filters = extractLineChartFilters(visualization.series)
            val highlights = calculateLineChartHighlights(visualization.series, selectedFilters)
            filters to highlights
        }
        is MatchupVisualization -> {
            val filters = extractMatchupFilters(visualization.dataPoints)
            // Matchups still use filtering, not highlighting
            filters to emptySet()
        }
        else -> emptyList<FilterOption>() to emptySet()
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

/**
 * Calculate which bar graph data points match the filter criteria.
 * Returns a set of team codes that should be highlighted.
 */
private fun calculateBarGraphHighlights(
    dataPoints: List<BarGraphDataPoint>,
    selectedFilters: Map<String, String>
): Set<String> {
    if (selectedFilters.isEmpty()) return emptySet()

    return dataPoints
        .filter { point ->
            selectedFilters.any { (key, value) ->
                when (key) {
                    "division" -> point.division?.equals(value, ignoreCase = true) == true
                    "conference" -> point.conference?.equals(value, ignoreCase = true) == true
                    else -> true
                }
            }
        }
        .mapNotNull { it.label.split(" ").firstOrNull() }
        .toSet()
}

// Scatter Plot filtering
private fun extractScatterPlotFilters(dataPoints: List<ScatterPlotDataPoint>, subject: String?): List<FilterOption> {
    val filters = mutableListOf<FilterOption>()

    // For TEAM scatter plots, extract team codes from labels (first word)
    // For PLAYER scatter plots, use teamCode property
    val teams = if (subject == "TEAM") {
        dataPoints.mapNotNull { it.label.split(" ").firstOrNull() }.distinct().sorted()
    } else {
        dataPoints.mapNotNull { it.teamCode }.distinct().sorted()
    }
    if (teams.isNotEmpty()) {
        filters.add(FilterOption("team", "Team", teams))
    }

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

/**
 * Calculate which scatter plot data points match the filter criteria.
 * Returns a set of identifiers (team codes) that should be highlighted.
 * If subject is "PLAYER", filters by teamCode and returns team codes.
 * If subject is "TEAM", filters by checking if team code appears in label and returns team codes.
 */
private fun calculateScatterPlotHighlights(
    dataPoints: List<ScatterPlotDataPoint>,
    selectedFilters: Map<String, String>,
    subject: String?
): Set<String> {
    println("🔍 calculateScatterPlotHighlights - Subject: $subject, Filters: $selectedFilters, DataPoints: ${dataPoints.size}")

    if (selectedFilters.isEmpty()) {
        println("🔍 calculateScatterPlotHighlights - No filters selected, returning empty set")
        return emptySet()
    }

    val matchedPoints = dataPoints.filter { point ->
        val matches = selectedFilters.any { (key, value) ->
            val result = when (key) {
                "team" -> {
                    if (subject == "TEAM") {
                        // For team scatter plots, check if team code appears in label
                        point.label.contains(value, ignoreCase = true)
                    } else {
                        // For player scatter plots (or default), match against teamCode
                        point.teamCode?.equals(value, ignoreCase = true) == true
                    }
                }
                "division" -> point.division?.equals(value, ignoreCase = true) == true
                "conference" -> point.conference?.equals(value, ignoreCase = true) == true
                else -> true
            }
            println("🔍   Point '${point.label}' - Filter $key=$value: teamCode=${point.teamCode}, division=${point.division}, conference=${point.conference} -> $result")
            result
        }
        matches
    }

    val highlights = matchedPoints.mapNotNull {
        if (subject == "TEAM") {
            // For team scatter plots, extract team code from label (first word)
            it.label.split(" ").firstOrNull()
        } else {
            // Return teamCode for player scatter plots
            it.teamCode
        }
    }.toSet()

    println("🔍 calculateScatterPlotHighlights - Matched ${matchedPoints.size} points, Highlights: $highlights")
    return highlights
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

/**
 * Calculate which line chart series match the filter criteria.
 * Returns a set of team codes that should be highlighted.
 */
private fun calculateLineChartHighlights(
    series: List<LineChartSeries>,
    selectedFilters: Map<String, String>
): Set<String> {
    if (selectedFilters.isEmpty()) return emptySet()

    return series
        .filter { s ->
            selectedFilters.any { (key, value) ->
                when (key) {
                    "division" -> s.division?.equals(value, ignoreCase = true) == true
                    "conference" -> s.conference?.equals(value, ignoreCase = true) == true
                    else -> true
                }
            }
        }
        .mapNotNull { it.label.split(" ").firstOrNull() }
        .toSet()
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

/**
 * Creates fake NFL playoff bracket data for demonstration
 */
private fun createFakeNFLPlayoffData(): PlayoffBracketVisualization {
    // AFC Teams
    val chiefs = PlayoffTeam("Kansas City Chiefs", "KC", 1, "AFC")
    val bills = PlayoffTeam("Buffalo Bills", "BUF", 2, "AFC")
    val ravens = PlayoffTeam("Baltimore Ravens", "BAL", 3, "AFC")
    val texans = PlayoffTeam("Houston Texans", "HOU", 4, "AFC")
    val steelers = PlayoffTeam("Pittsburgh Steelers", "PIT", 5, "AFC")
    val chargers = PlayoffTeam("Los Angeles Chargers", "LAC", 6, "AFC")
    val broncos = PlayoffTeam("Denver Broncos", "DEN", 7, "AFC")

    // NFC Teams
    val eagles = PlayoffTeam("Philadelphia Eagles", "PHI", 1, "NFC")
    val lions = PlayoffTeam("Detroit Lions", "DET", 2, "NFC")
    val rams = PlayoffTeam("Los Angeles Rams", "LAR", 3, "NFC")
    val buccaneers = PlayoffTeam("Tampa Bay Buccaneers", "TB", 4, "NFC")
    val vikings = PlayoffTeam("Minnesota Vikings", "MIN", 5, "NFC")
    val commanders = PlayoffTeam("Washington Commanders", "WSH", 6, "NFC")
    val packers = PlayoffTeam("Green Bay Packers", "GB", 7, "NFC")

    // Wild Card Round
    val wildCardRound = PlayoffRound(
        name = "Wild Card Round",
        matchups = listOf(
            PlayoffMatchup(texans, chargers, winner = texans),
            PlayoffMatchup(ravens, steelers, winner = ravens),
            PlayoffMatchup(bills, broncos, winner = bills),
            PlayoffMatchup(buccaneers, commanders, winner = buccaneers),
            PlayoffMatchup(rams, vikings, winner = rams),
            PlayoffMatchup(eagles, packers, winner = eagles)
        )
    )

    // Divisional Round
    val divisionalRound = PlayoffRound(
        name = "Divisional Round",
        matchups = listOf(
            PlayoffMatchup(chiefs, texans, winner = chiefs),
            PlayoffMatchup(ravens, bills, winner = bills),
            PlayoffMatchup(lions, commanders, winner = lions),
            PlayoffMatchup(eagles, rams, winner = eagles)
        )
    )

    // Conference Championships
    val conferenceChampionships = PlayoffRound(
        name = "Conference Championships",
        matchups = listOf(
            PlayoffMatchup(chiefs, bills, winner = chiefs),
            PlayoffMatchup(eagles, lions, winner = eagles)
        )
    )

    // Super Bowl
    val superBowl = PlayoffRound(
        name = "Super Bowl",
        matchups = listOf(
            PlayoffMatchup(chiefs, eagles, winner = null) // No winner yet
        )
    )

    return PlayoffBracketVisualization(
        sport = "NFL",
        visualizationType = "PLAYOFF_BRACKET",
        title = "NFL Playoff Bracket",
        subtitle = "2024-2025 Season",
        description = "Interactive NFL playoff bracket with swipe navigation between rounds",
        lastUpdated = kotlin.time.Clock.System.now(),
        source = "Fake Demo Data",
        rounds = listOf(wildCardRound, divisionalRound, conferenceChampionships, superBowl)
    )
}
