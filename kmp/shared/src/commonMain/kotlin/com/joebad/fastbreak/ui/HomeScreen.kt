package com.joebad.fastbreak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.data.api.MockedDataApi
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.navigation.HomeComponent
import com.joebad.fastbreak.ui.container.RegistryState

@Composable
fun HomeScreen(
    component: HomeComponent,
    registryState: RegistryState,
    registryContainer: com.joebad.fastbreak.ui.container.RegistryContainer,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit = {},
    onInitialLoad: () -> Unit,
    onRequestPermission: () -> Unit,
    onCheckPermission: () -> Unit,
    onClearSyncProgress: () -> Unit
) {
    val selectedSport by component.selectedSport.subscribeAsState()

    // Clear any stale completed sync progress when screen first appears
    // Only load registry if it hasn't been loaded yet
    LaunchedEffect(Unit) {
        // If there's a completed sync from a previous session, clear it immediately
        if (registryState.syncProgress?.isComplete == true && !registryState.isSyncing) {
            onClearSyncProgress()
        }

        // Only trigger initial load if registry hasn't been loaded yet
        // This prevents re-loading when navigating back to home screen
        if (registryState.registry == null && !registryState.isLoading && !registryState.isSyncing) {
            onInitialLoad()
        }
    }

    // Clear completed sync progress when navigating away
    DisposableEffect(Unit) {
        onDispose {
            // If sync is complete (not actively syncing), clear the progress
            if (registryState.syncProgress?.isComplete == true && !registryState.isSyncing) {
                onClearSyncProgress()
            }
        }
    }

    // Refresh state
    val isRefreshing = registryState.isLoading || registryState.isSyncing

    // Snackbar for errors
    val snackbarHostState = remember { SnackbarHostState() }

    // Observe side effects from the container
    LaunchedEffect(Unit) {
        registryContainer.container.sideEffectFlow.collect { sideEffect ->
            when (sideEffect) {
                is com.joebad.fastbreak.ui.container.RegistrySideEffect.ShowError -> {
                    // Truncate error message to 100 characters
                    val truncatedMessage = if (sideEffect.message.length > 100) {
                        sideEffect.message.take(100) + "..."
                    } else {
                        sideEffect.message
                    }

                    snackbarHostState.showSnackbar(
                        message = truncatedMessage,
                        actionLabel = "Dismiss",
                        duration = SnackbarDuration.Indefinite
                    )
                }
                is com.joebad.fastbreak.ui.container.RegistrySideEffect.SyncCompleted -> {
                    // Optional: Show success message
                }
                is com.joebad.fastbreak.ui.container.RegistrySideEffect.NavigateToChart -> {
                    // Handle navigation if needed
                }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    actionColor = MaterialTheme.colorScheme.error
                )
            }
        },
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
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
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

            // Filter charts for selected sport
            val chartsForSport = registryState.registry?.charts?.filter { chart ->
                chart.sport == selectedSport
            } ?: emptyList()

            // Check if entire registry is empty
            val registryIsEmpty = registryState.registry?.charts?.isEmpty() == true

            // Show different states
            when {
                registryState.isLoading && registryState.registry == null -> {
                    // Initial loading
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "loading registry...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                registryIsEmpty -> {
                    // Entire registry is empty - show error if present
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = "no charts available",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )

                            // Show error if present
                            registryState.error?.let { errorMsg ->
                                androidx.compose.material3.Card(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        text = "Error: $errorMsg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = onRefresh,
                                enabled = !isRefreshing
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                chartsForSport.isEmpty() && registryState.registry != null -> {
                    // No charts for this specific sport (but registry has charts)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "no ${selectedSport.displayName} charts available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                else -> {
                    // Display charts
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Sync progress indicator at the top
                        // Show during sync OR when complete (until cleared after 3 seconds)
                        // Don't show if total = 0 (nothing to sync, everything is cached)
                        if (registryState.syncProgress != null && registryState.syncProgress.total > 0) {
                            item {
                                SyncProgressIndicator(
                                    progress = registryState.syncProgress
                                )
                            }
                        }

                        items(chartsForSport) { chart ->
                            // Determine chart sync state
                            val isSyncing = registryState.syncProgress?.isChartSyncing(chart.id) == true
                            // If syncProgress is null, all charts are ready (sync complete)
                            // If syncProgress exists, check if this specific chart is ready
                            val isReady = registryState.syncProgress?.isChartReady(chart.id) ?: true

                            VisualizationItem(
                                title = chart.title,
                                description = chart.subtitle,
                                isSyncing = isSyncing,
                                isReady = isReady,
                                onClick = {
                                    // Only navigate if chart is ready
                                    if (isReady) {
                                        // Map VizType to MockedDataApi.VizType
                                        val apiVizType = when (chart.visualizationType) {
                                            com.joebad.fastbreak.data.model.VizType.SCATTER_PLOT ->
                                                MockedDataApi.VizType.SCATTER
                                            com.joebad.fastbreak.data.model.VizType.BAR_GRAPH ->
                                                MockedDataApi.VizType.BAR
                                            com.joebad.fastbreak.data.model.VizType.LINE_CHART ->
                                                MockedDataApi.VizType.LINE
                                        }
                                        component.onNavigateToDataViz(chart.id, selectedSport, apiVizType)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualizationItem(
    title: String,
    description: String,
    isSyncing: Boolean,
    isReady: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (isReady) 1f else 0.5f
    val clickableModifier = if (isReady) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }

            // Show loading indicator if syncing
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )
    }
}

@Composable
private fun SyncProgressIndicator(
    progress: com.joebad.fastbreak.ui.diagnostics.SyncProgress
) {
    val isComplete = progress.isComplete
    val hasFailures = progress.hasFailures

    // Determine card colors based on state
    val containerColor = when {
        isComplete && !hasFailures -> MaterialTheme.colorScheme.tertiaryContainer // Green
        isComplete && hasFailures -> MaterialTheme.colorScheme.errorContainer // Red
        else -> MaterialTheme.colorScheme.primaryContainer // Blue
    }

    val contentColor = when {
        isComplete && !hasFailures -> MaterialTheme.colorScheme.onTertiaryContainer
        isComplete && hasFailures -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Top row with status text and icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Icon based on state
                    when {
                        isComplete && !hasFailures -> Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        isComplete && hasFailures -> Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Failed",
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = when {
                            isComplete && !hasFailures -> "Sync completed successfully"
                            isComplete && hasFailures -> "Sync failed"
                            else -> "Syncing charts..."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Only show counter if there are charts to sync (total > 0)
                // Don't show counter in initial "preparing" state
                if (progress.total > 0 && !(progress.current == 0 && progress.total == 1)) {
                    Text(
                        text = "${progress.current}/${progress.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Always show progress bar
            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.percentage / 100f },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {}
            )

            // Show current chart name when syncing, or failure details when complete
            when {
                !isComplete && progress.currentChart.isNotEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progress.currentChart,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
                isComplete && hasFailures -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${progress.failedCharts.size} chart${if (progress.failedCharts.size > 1) "s" else ""} failed to sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
