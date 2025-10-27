package com.example.kmpapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.example.kmpapp.data.model.Sport
import com.example.kmpapp.data.model.Visualization
import com.example.kmpapp.data.model.VisualizationData
import com.example.kmpapp.navigation.HomeComponent

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
            val visualizations = VisualizationData.getVisualizationsForSport(selectedSport)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(visualizations) { visualization ->
                    VisualizationItem(
                        visualization = visualization,
                        onClick = { component.onNavigateToDataViz(visualization.title) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualizationItem(
    visualization: Visualization,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = visualization.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = visualization.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "last updated: ${visualization.lastUpdated}",
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
