package com.example.kmpapp.ui

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
import com.example.kmpapp.data.api.MockedDataApi
import com.example.kmpapp.data.model.*
import com.example.kmpapp.navigation.DataVizComponent
import com.example.kmpapp.ui.visualizations.*
import kotlinx.coroutines.launch

@Composable
fun DataVizScreen(
    component: DataVizComponent,
    onMenuClick: () -> Unit = {}
) {
    var state by remember { mutableStateOf<DataVizState>(DataVizState.Loading) }
    val scope = rememberCoroutineScope()

    // Load data when component is first displayed
    LaunchedEffect(component.sport, component.vizType) {
        state = DataVizState.Loading
        try {
            val apiSport = when (component.sport) {
                Sport.NFL -> MockedDataApi.Sport.NFL
                Sport.NBA -> MockedDataApi.Sport.NBA
                Sport.MLB -> MockedDataApi.Sport.MLB
                Sport.NHL -> MockedDataApi.Sport.NHL
            }
            val data = component.api.fetchVisualizationData(apiSport, component.vizType)
            state = DataVizState.Success(data)
        } catch (e: Exception) {
            state = DataVizState.Error(e.message ?: "Unknown error")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val vizTypeName = when (component.vizType) {
                        MockedDataApi.VizType.SCATTER -> "Scatter Plot"
                        MockedDataApi.VizType.BAR -> "Bar Chart"
                        MockedDataApi.VizType.LINE -> "Line Chart"
                    }
                    Text(
                        text = "${component.sport.displayName} - $vizTypeName",
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
                            state = DataVizState.Loading
                            try {
                                val apiSport = when (component.sport) {
                                    Sport.NFL -> MockedDataApi.Sport.NFL
                                    Sport.NBA -> MockedDataApi.Sport.NBA
                                    Sport.MLB -> MockedDataApi.Sport.MLB
                                    Sport.NHL -> MockedDataApi.Sport.NHL
                                }
                                val data = component.api.fetchVisualizationData(apiSport, component.vizType)
                                state = DataVizState.Success(data)
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
                                title = "",
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
