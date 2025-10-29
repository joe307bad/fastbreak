package com.joebad.fastbreak.ui.diagnostics

/**
 * Progress information for chart data synchronization.
 * Used to track and display sync progress in the UI.
 */
data class SyncProgress(
    /**
     * Current chart being processed (1-indexed)
     */
    val current: Int,

    /**
     * Total number of charts to sync
     */
    val total: Int,

    /**
     * Display name of the chart currently being synced
     * Empty string when sync is complete
     */
    val currentChart: String,

    /**
     * List of charts that failed to sync (chartId to error message)
     */
    val failedCharts: List<Pair<String, String>> = emptyList()
) {
    /**
     * Calculates the progress percentage (0-100)
     */
    val percentage: Int
        get() = if (total > 0) ((current.toFloat() / total) * 100).toInt() else 0

    /**
     * Returns true if the sync is complete
     * Complete when: all charts processed (current >= total) OR no charts to sync (total == 0)
     */
    val isComplete: Boolean
        get() = current >= total || total == 0

    /**
     * Returns true if there were any failures
     */
    val hasFailures: Boolean
        get() = failedCharts.isNotEmpty()

    /**
     * Returns the number of successful syncs
     */
    val successfulCount: Int
        get() = current - failedCharts.size

    /**
     * Returns a human-readable status message
     */
    val statusMessage: String
        get() = when {
            total == 0 -> "No charts to sync"
            isComplete && !hasFailures -> "Sync complete: $total charts"
            isComplete && hasFailures -> "Sync complete: $successfulCount/$total successful"
            else -> "Syncing $currentChart ($current/$total)"
        }
}
