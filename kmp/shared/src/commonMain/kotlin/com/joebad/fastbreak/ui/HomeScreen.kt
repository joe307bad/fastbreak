package com.joebad.fastbreak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.data.api.MockedDataApi
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.navigation.HomeComponent

@Composable
fun HomeScreen(
    component: HomeComponent,
    onMenuClick: () -> Unit = {}
) {
    val selectedSport by component.selectedSport.subscribeAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("fastbreak") },
                navigationIcon = {
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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Scrollable tabs
            ScrollableTabRow(
                selectedTabIndex = Sport.entries.indexOf(selectedSport),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                edgePadding = 16.dp,
                divider = {},
                indicator = { tabPositions ->
                    if (tabPositions.isNotEmpty()) {
                        val currentTabPosition = tabPositions[Sport.entries.indexOf(selectedSport)]
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(align = androidx.compose.ui.Alignment.BottomStart)
                                .offset(x = currentTabPosition.left)
                                .width(currentTabPosition.width)
                        ) {
                            HorizontalDivider(
                                thickness = 3.dp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            ) {
                Sport.entries.forEach { sport ->
                    Tab(
                        selected = selectedSport == sport,
                        onClick = { component.selectSport(sport) },
                        text = {
                            Text(
                                text = sport.displayName,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.onBackground,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )

            // Visualization list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Scatter Plot
                item {
                    VisualizationItem(
                        title = "Scatter Plot Analysis",
                        description = "Compare two metrics across teams or players",
                        onClick = { component.onNavigateToDataViz(selectedSport, MockedDataApi.VizType.SCATTER) }
                    )
                }

                // Bar Chart
                item {
                    VisualizationItem(
                        title = "Bar Chart Comparison",
                        description = "View and compare individual performance metrics",
                        onClick = { component.onNavigateToDataViz(selectedSport, MockedDataApi.VizType.BAR) }
                    )
                }

                // Line Chart
                item {
                    VisualizationItem(
                        title = "Line Chart Trends",
                        description = "Track performance trends over time",
                        onClick = { component.onNavigateToDataViz(selectedSport, MockedDataApi.VizType.LINE) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualizationItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )
    }
}
