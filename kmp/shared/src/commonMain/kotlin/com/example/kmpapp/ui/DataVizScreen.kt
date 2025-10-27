package com.example.kmpapp.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kmpapp.data.model.ScatterPlotData
import com.example.kmpapp.navigation.DataVizComponent
import com.example.kmpapp.viewmodel.DataVizState
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Helper function for formatting numbers to specific decimal places
private fun Double.formatTo(decimals: Int): String {
    val multiplier = when (decimals) {
        1 -> 10.0
        2 -> 100.0
        else -> 1.0
    }
    val rounded = (this * multiplier).roundToInt() / multiplier
    return rounded.toString()
}

// Data class to hold a point with its standardized sum
private data class PointWithStandardizedSum(
    val label: String,
    val x: Double,
    val y: Double,
    val standardizedSum: Double
)

@Composable
fun DataVizScreen(
    component: DataVizComponent,
    onMenuClick: () -> Unit = {}
) {
    val state by component.viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = component.title,
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
            when (state) {
                is DataVizState.Initial -> InitialContent()
                is DataVizState.Loading -> LoadingContent()
                is DataVizState.Success -> SuccessContent((state as DataVizState.Success).data)
                is DataVizState.Error -> ErrorContent(
                    message = (state as DataVizState.Error).message,
                    onRetry = component.viewModel::retry
                )
            }
        }
    }
}

@Composable
private fun InitialContent() {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Initializing...",
            style = MaterialTheme.typography.bodyLarge
        )
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
private fun SuccessContent(data: ScatterPlotData) {
    val scrollState = rememberScrollState()

    // Calculate standardized sums and sort
    val sortedPoints = remember(data) {
        // Calculate means
        val meanX = data.points.map { it.x }.average()
        val meanY = data.points.map { it.y }.average()

        // Calculate standard deviations
        val stdX = sqrt(data.points.map { (it.x - meanX) * (it.x - meanX) }.average())
        val stdY = sqrt(data.points.map { (it.y - meanY) * (it.y - meanY) }.average())

        // Calculate standardized sums and create sorted list
        data.points.map { point ->
            val zX = if (stdX != 0.0) (point.x - meanX) / stdX else 0.0
            val zY = if (stdY != 0.0) (point.y - meanY) / stdY else 0.0
            PointWithStandardizedSum(
                label = point.label,
                x = point.x,
                y = point.y,
                standardizedSum = zX + zY
            )
        }.sortedByDescending { it.standardizedSum }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FourQuadrantScatterPlot(
            data = data.points,
            title = "",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Data table
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Data Points",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val horizontalScrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
            ) {
                // Table header
                Row(
                    modifier = Modifier
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(30.dp)
                    )
                    Text(
                        text = "Label",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(100.dp)
                    )
                    Text(
                        text = "Stand. Sum",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(90.dp)
                    )
                    Text(
                        text = "PFF",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(70.dp)
                    )
                    Text(
                        text = "EPA/play",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(80.dp)
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline,
                    thickness = 1.dp
                )

                // Table rows
                sortedPoints.forEachIndexed { index, point ->
                    Row(
                        modifier = Modifier
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(30.dp)
                        )
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            text = point.standardizedSum.formatTo(2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(90.dp)
                        )
                        Text(
                            text = point.x.formatTo(2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(70.dp)
                        )
                        Text(
                            text = point.y.formatTo(2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(80.dp)
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 1.dp
                    )
                }
            }
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
