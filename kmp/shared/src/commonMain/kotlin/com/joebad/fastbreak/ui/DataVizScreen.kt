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
                    IconButton(onClick = component.onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu"
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
                            FourQuadrantScatterPlot(
                                data = visualization.dataPoints,
                                title = visualization.title,
                                xAxisLabel = visualization.xAxisLabel,
                                yAxisLabel = visualization.yAxisLabel,
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
                        else -> {
                            Text(
                                text = "This visualization type is not yet supported",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp)
                            )
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
        }
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
