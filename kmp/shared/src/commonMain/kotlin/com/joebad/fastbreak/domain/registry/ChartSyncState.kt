package com.joebad.fastbreak.domain.registry

/**
 * Represents the synchronization state of an individual chart.
 * Single source of truth for chart readiness in the UI.
 */
sealed class ChartSyncState {
    /**
     * Chart is not being synced and has no pending operations.
     * May or may not have cached data.
     */
    data object Idle : ChartSyncState()

    /**
     * Chart is currently being downloaded.
     */
    data object Syncing : ChartSyncState()

    /**
     * Chart has been successfully synced and cached data is available.
     */
    data object Ready : ChartSyncState()

    /**
     * Chart sync failed with an error.
     */
    data class Failed(val error: String) : ChartSyncState()

    /**
     * Helper to check if the chart is ready for display.
     */
    val isReady: Boolean
        get() = this is Ready

    /**
     * Helper to check if the chart is currently syncing.
     */
    val isSyncing: Boolean
        get() = this is Syncing

    /**
     * Helper to check if the chart sync failed.
     */
    val isFailed: Boolean
        get() = this is Failed
}

/**
 * Aggregated sync state for all charts.
 * Replaces the current SyncProgress with a cleaner model.
 */
data class ChartSyncProgress(
    /**
     * Map of chart ID to its sync state.
     * Only charts that are being synced in this batch are included.
     */
    val chartStates: Map<String, ChartSyncState> = emptyMap(),

    /**
     * Total charts that need syncing in this batch.
     */
    val totalToSync: Int = 0,

    /**
     * Name of the chart currently being synced (for UI display).
     */
    val currentChartName: String = "",

    /**
     * Whether topics have been synced successfully.
     */
    val topicsSynced: Boolean = false
) {
    /**
     * Number of charts that have completed (success or failure).
     */
    val completedCount: Int
        get() = chartStates.values.count { it is ChartSyncState.Ready || it is ChartSyncState.Failed }

    /**
     * Number of charts that succeeded.
     */
    val successCount: Int
        get() = chartStates.values.count { it is ChartSyncState.Ready }

    /**
     * Number of charts that failed.
     */
    val failedCount: Int
        get() = chartStates.values.count { it is ChartSyncState.Failed }

    /**
     * Whether all charts have completed syncing.
     */
    val isComplete: Boolean
        get() = totalToSync == 0 || completedCount >= totalToSync

    /**
     * Progress percentage (0-100).
     */
    val percentage: Int
        get() = when {
            isComplete -> 100
            totalToSync > 0 -> ((completedCount.toFloat() / totalToSync) * 100).toInt()
            else -> 0
        }

    /**
     * Whether there were any failures.
     */
    val hasFailures: Boolean
        get() = failedCount > 0

    /**
     * List of failed charts with their error messages.
     */
    val failedCharts: List<Pair<String, String>>
        get() = chartStates.entries
            .filter { it.value is ChartSyncState.Failed }
            .map { it.key to (it.value as ChartSyncState.Failed).error }

    /**
     * Gets the sync state for a specific chart.
     */
    fun getChartState(chartId: String): ChartSyncState {
        return chartStates[chartId] ?: ChartSyncState.Idle
    }

    /**
     * Checks if a chart is currently syncing.
     */
    fun isChartSyncing(chartId: String): Boolean {
        return chartStates[chartId]?.isSyncing == true
    }

    /**
     * Checks if a chart is ready (successfully synced in this batch).
     */
    fun isChartReady(chartId: String): Boolean {
        val state = chartStates[chartId]
        // If chart is in our tracking and is Ready, it's ready
        // If chart is not in our tracking, it was already cached (not part of this sync batch)
        return state is ChartSyncState.Ready || state == null
    }

    /**
     * Returns a human-readable status message.
     */
    val statusMessage: String
        get() = when {
            totalToSync == 0 && !topicsSynced -> "No charts to sync"
            totalToSync == 0 && topicsSynced -> "Sync complete"
            isComplete && !hasFailures -> "Sync complete: $successCount/$totalToSync"
            isComplete && hasFailures -> "Sync complete: $successCount/$totalToSync successful"
            else -> "Syncing $currentChartName ($completedCount/$totalToSync)"
        }
}
