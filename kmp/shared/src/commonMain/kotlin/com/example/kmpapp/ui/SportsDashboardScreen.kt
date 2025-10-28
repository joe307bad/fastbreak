package com.example.kmpapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kmpapp.data.api.MockedDataApi

/**
 * Sports Dashboard with tabs for each sport and visualization type selection.
 */
@Composable
fun SportsDashboardScreen(
    onNavigateToVisualization: (MockedDataApi.Sport, MockedDataApi.VizType) -> Unit,
    onNavigateBack: () -> Unit,
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSportIndex by remember { mutableStateOf(0) }
    val sports = remember { MockedDataApi.Sport.entries }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sports Analytics Dashboard",
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sport Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedSportIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                sports.forEachIndexed { index, sport ->
                    Tab(
                        selected = selectedSportIndex == index,
                        onClick = { selectedSportIndex = index },
                        text = { Text(sport.name) }
                    )
                }
            }

            // Visualization Type Cards
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select Visualization Type",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val currentSport = sports[selectedSportIndex]

                // Scatter Plot Card
                VisualizationCard(
                    title = "Scatter Plot Analysis",
                    description = getScatterDescription(currentSport),
                    onClick = {
                        onNavigateToVisualization(currentSport, MockedDataApi.VizType.SCATTER)
                    }
                )

                // Bar Chart Card
                VisualizationCard(
                    title = "Bar Chart Comparison",
                    description = getBarDescription(currentSport),
                    onClick = {
                        onNavigateToVisualization(currentSport, MockedDataApi.VizType.BAR)
                    }
                )

                // Line Chart Card
                VisualizationCard(
                    title = "Line Chart Trends",
                    description = getLineDescription(currentSport),
                    onClick = {
                        onNavigateToVisualization(currentSport, MockedDataApi.VizType.LINE)
                    }
                )
            }
        }
    }
}

@Composable
private fun VisualizationCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getScatterDescription(sport: MockedDataApi.Sport): String {
    return when (sport) {
        MockedDataApi.Sport.NFL -> "QB Performance: Passer Rating vs EPA per Play"
        MockedDataApi.Sport.NBA -> "Player Efficiency: Usage Rate vs True Shooting %"
        MockedDataApi.Sport.MLB -> "Pitcher Performance: ERA vs WHIP"
        MockedDataApi.Sport.NHL -> "Goalie Performance: Save % vs Goals Against Avg"
    }
}

private fun getBarDescription(sport: MockedDataApi.Sport): String {
    return when (sport) {
        MockedDataApi.Sport.NFL -> "Team Total Yards Comparison (includes differentials)"
        MockedDataApi.Sport.NBA -> "Team Points Per Game (includes differentials)"
        MockedDataApi.Sport.MLB -> "Team Home Runs (includes differentials)"
        MockedDataApi.Sport.NHL -> "Team Goals Scored (includes differentials)"
    }
}

private fun getLineDescription(sport: MockedDataApi.Sport): String {
    return when (sport) {
        MockedDataApi.Sport.NFL -> "Season Win Progression - Top 4 Teams (18 weeks)"
        MockedDataApi.Sport.NBA -> "Season Win Progression - Top 4 Teams (82 games)"
        MockedDataApi.Sport.MLB -> "Season Win Progression - Top 4 Teams (162 games)"
        MockedDataApi.Sport.NHL -> "Season Points Progression - Top 4 Teams (82 games)"
    }
}
