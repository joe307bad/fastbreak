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
import com.example.kmpapp.ui.visualizations.*
import kotlinx.coroutines.launch

/**
 * Unified visualization screen that displays any visualization type with its data table.
 * Supports: Scatter Plot, Bar Graph, Line Chart
 */
@Composable
fun UnifiedVisualizationScreen(
    sport: MockedDataApi.Sport,
    vizType: MockedDataApi.VizType,
    onNavigateBack: () -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<VizState>(VizState.Loading) }
    val api = remember { MockedDataApi() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sport, vizType) {
        state = VizState.Loading
        try {
            val data = api.fetchVisualizationData(sport, vizType)
            state = VizState.Success(data)
        } catch (e: Exception) {
            state = VizState.Error(e.message ?: "Unknown error")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${sport.name} - ${vizType.name}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val currentState = state) {
                is VizState.Loading -> LoadingContent()
                is VizState.Error -> ErrorContent(
                    message = currentState.message,
                    onRetry = {
                        scope.launch {
                            state = VizState.Loading
                            try {
                                val data = api.fetchVisualizationData(sport, vizType)
                                state = VizState.Success(data)
                            } catch (e: Exception) {
                                state = VizState.Error(e.message ?: "Unknown error")
                            }
                        }
                    }
                )
                is VizState.Success -> SuccessContent(currentState.data)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading visualization...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
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
                .padding(16.dp)
        ) {
        // Title Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = visualization.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = visualization.subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = visualization.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Visualization - with right padding in landscape so users can scroll without triggering pan gestures
        Row(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    when (visualization) {
                        is ScatterPlotVisualization -> {
                            ScatterPlotComponent(
                                data = visualization.dataPoints,
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
                            // TableVisualization or other types
                            Text(
                                text = "This visualization type is not yet supported",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            // Right padding area for scrolling (only in landscape)
            if (isLandscape) {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Data Table
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                DataTableComponent(
                    visualization = visualization,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        }
    }
}

private sealed interface VizState {
    data object Loading : VizState
    data class Success(val data: VisualizationType) : VizState
    data class Error(val message: String) : VizState
}
