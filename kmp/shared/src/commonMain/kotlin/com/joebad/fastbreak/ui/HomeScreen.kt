package com.joebad.fastbreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.data.model.ChartDefinition
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.navigation.HomeComponent
import com.joebad.fastbreak.ui.components.TagBadge
import com.joebad.fastbreak.ui.components.TagFilterBar
import com.joebad.fastbreak.ui.container.RegistryState
import kotlin.time.Clock
import kotlin.time.Instant

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
    onClearSyncProgress: () -> Unit,
    onMarkChartAsViewed: (String) -> Unit = {}
) {
    val selectedSport by component.selectedSport.subscribeAsState()
    val selectedTags by component.selectedTags.subscribeAsState()

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
                // Calculate which sports have unviewed charts
                val sportsWithUnviewedCharts = remember(registryState.registry?.charts) {
                    registryState.registry?.charts
                        ?.filter { !it.viewed }
                        ?.map { it.sport }
                        ?.toSet()
                        ?: emptySet()
                }

                Sport.entries.forEach { sport ->
                    val hasUnviewedCharts = sport in sportsWithUnviewedCharts

                    Tab(
                        selected = selectedSport == sport,
                        onClick = { component.selectSport(sport) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = sport.displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                // Show blue dot if this sport has unviewed charts
                                if (hasUnviewedCharts) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                color = Color(0xFF2196F3), // Material Blue
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
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

            // Filter charts for selected sport and sort by sortOrder (nulls last), then alphabetically by title
            // Hide NFL Playoff Bracket until it's ready for production
            val chartsForSport = registryState.registry?.charts
                ?.filter { chart ->
                    chart.sport == selectedSport &&
                    !chart.title.contains("NFL Playoff Bracket", ignoreCase = true)
                }
                ?.sortedWith(
                    compareBy<ChartDefinition> { it.sortOrder ?: Int.MAX_VALUE }.thenBy { it.title }
                )
                ?: emptyList()

            // Collect all unique tags from charts for the selected sport
            val availableTags = remember(chartsForSport) {
                chartsForSport
                    .flatMap { it.tags ?: emptyList() }
                    .distinctBy { it.label }
                    .sortedBy { it.label }
            }

            // Filter charts by selected tags (if any tags are selected)
            val filteredCharts = remember(chartsForSport, selectedTags) {
                if (selectedTags.isEmpty()) {
                    chartsForSport
                } else {
                    chartsForSport.filter { chart ->
                        val chartTagLabels = chart.tags?.map { it.label } ?: emptyList()
                        // Show chart if it has ALL selected tags
                        selectedTags.all { selectedTag -> selectedTag in chartTagLabels }
                    }
                }
            }

            // Show tag filter bar if there are tags available
            if (availableTags.isNotEmpty()) {
                TagFilterBar(
                    availableTags = availableTags,
                    selectedTags = selectedTags,
                    onTagToggle = { tag -> component.toggleTag(tag, availableTags) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp)
                )
            }

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
                filteredCharts.isEmpty() && registryState.registry != null -> {
                    // No charts for this specific sport/tag combination (but registry has charts)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val message = if (selectedTags.isNotEmpty()) {
                            "no charts match selected filters"
                        } else {
                            "no ${selectedSport.displayName} charts available"
                        }
                        Text(
                            text = message,
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
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

                        // Hardcoded playoff bracket item for NFL - HIDDEN until ready for production
                        // Uncomment when ready to show:
                        // if (selectedSport == Sport.NFL) {
                        //     item {
                        //         VisualizationItem(
                        //             title = "NFL Playoff Bracket",
                        //             description = "Interactive playoff bracket with swipe navigation",
                        //             interval = null,
                        //             lastUpdated = Clock.System.now(),
                        //             viewed = true,
                        //             isSyncing = false,
                        //             isReady = true,
                        //             onClick = {
                        //                 component.onNavigateToDataViz("playoff-bracket-nfl", Sport.NFL, com.joebad.fastbreak.data.model.VizType.PLAYOFF_BRACKET)
                        //             }
                        //         )
                        //     }
                        // }

                        items(filteredCharts) { chart ->
                            // Determine chart sync state
                            val isSyncing = registryState.syncProgress?.isChartSyncing(chart.id) == true
                            // If syncProgress is null, all charts are ready (sync complete)
                            // If syncProgress exists, check if this specific chart is ready
                            val isReady = registryState.syncProgress?.isChartReady(chart.id) ?: true

                            VisualizationItem(
                                title = chart.title,
                                description = chart.subtitle,
                                interval = chart.interval,
                                lastUpdated = chart.lastUpdated,
                                viewed = chart.viewed,
                                isSyncing = isSyncing,
                                isReady = isReady,
                                tags = chart.tags ?: emptyList(),
                                onClick = {
                                    // Only navigate if chart is ready
                                    if (isReady) {
                                        // Mark chart as viewed before navigating
                                        onMarkChartAsViewed(chart.id)
                                        component.onNavigateToDataViz(chart.id, selectedSport, chart.visualizationType)
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
    interval: String?,
    lastUpdated: Instant,
    viewed: Boolean,
    isSyncing: Boolean,
    isReady: Boolean,
    tags: List<com.joebad.fastbreak.data.model.Tag>,
    onClick: () -> Unit
) {
    val alpha = if (isReady) 1f else 0.5f
    val clickableModifier = if (isReady) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    // Build the metadata line (interval + relative time)
    val metadataText = buildMetadataText(interval, lastUpdated)

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
                // Title row with unviewed indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)
                    )
                    // Show blue dot indicator for unviewed charts
                    if (!viewed && isReady) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = Color(0xFF2196F3), // Material Blue
                                    shape = CircleShape
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
                // Show metadata (interval + relative time) if available
                if (metadataText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = metadataText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                    )
                }
                // Show tags if available
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tags.forEach { tag ->
                            TagBadge(tag = tag)
                        }
                    }
                }
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

/**
 * Builds the metadata text combining interval and relative time.
 * Example outputs: "Weekly - updated 2h ago", "Daily - updated 5m ago", "updated just now"
 */
private fun buildMetadataText(interval: String?, lastUpdated: Instant): String {
    val parts = mutableListOf<String>()

    // Add interval if available
    if (interval != null) {
        parts.add(interval.replaceFirstChar { it.uppercase() })
    }

    // Add relative time
    parts.add("updated ${formatRelativeTime(lastUpdated)}")

    return parts.joinToString(" - ")
}

/**
 * Formats an instant as a relative time string.
 * Examples: "just now", "5m ago", "2h ago", "3d ago"
 */
private fun formatRelativeTime(instant: Instant): String {
    val now = Clock.System.now()
    val duration = now - instant

    val totalSeconds = duration.inWholeSeconds
    val totalMinutes = duration.inWholeMinutes
    val totalHours = duration.inWholeHours
    val totalDays = duration.inWholeDays

    return when {
        totalSeconds < 60 -> "just now"
        totalMinutes < 60 -> "${totalMinutes}m ago"
        totalHours < 24 -> "${totalHours}h ago"
        totalDays < 7 -> "${totalDays}d ago"
        else -> "${totalDays / 7}w ago"
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
        isComplete && !hasFailures -> Color(0xFF1B5E20).copy(alpha = 0.2f) // Light green
        isComplete && hasFailures -> MaterialTheme.colorScheme.errorContainer // Red
        else -> MaterialTheme.colorScheme.primaryContainer // Blue
    }

    val contentColor = when {
        isComplete && !hasFailures -> {
            // Use darker green in light mode, lighter green in dark mode
            if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
                Color(0xFF1B5E20) // Dark green for light mode
            } else {
                Color(0xFF81C784) // Light green for dark mode
            }
        }
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
