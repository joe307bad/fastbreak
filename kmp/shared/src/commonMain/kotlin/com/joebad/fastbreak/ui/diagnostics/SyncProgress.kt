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
     * Set of chart IDs that have been successfully synced
     */
    val syncedChartIds: Set<String> = emptySet(),

    /**
     * Set of chart IDs currently being synced
     */
    val syncingChartIds: Set<String> = emptySet(),

    /**
     * List of charts that failed to sync (chartId to error message)
     */
    val failedCharts: List<Pair<String, String>> = emptyList(),

    /**
     * Whether topics have been synced successfully
     */
    val topicsSynced: Boolean = false
) {
    /**
     * Calculates the progress percentage (0-100)
     * Returns 100 when complete, even if total is 0
     */
    val percentage: Int
        get() = when {
            isComplete -> 100
            total > 0 -> ((current.toFloat() / total) * 100).toInt()
            else -> 0
        }

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
     * Returns the number of successful syncs (charts + topics if synced)
     */
    val successfulCount: Int
        get() = current - failedCharts.size + (if (topicsSynced) 1 else 0)

    /**
     * Returns the total count including topics if synced
     */
    val totalWithTopics: Int
        get() = total + (if (topicsSynced) 1 else 0)

    /**
     * Returns a human-readable status message
     */
    val statusMessage: String
        get() = when {
            total == 0 && !topicsSynced -> "No charts to sync"
            total == 0 && topicsSynced -> "Sync complete: 1/1"
            isComplete && !hasFailures -> "Sync complete: $totalWithTopics/$totalWithTopics"
            isComplete && hasFailures -> "Sync complete: $successfulCount/$totalWithTopics successful"
            else -> "Syncing $currentChart ($current/$total)"
        }

    /**
     * Checks if a specific chart has been synced
     */
    fun isChartSynced(chartId: String): Boolean {
        return syncedChartIds.contains(chartId)
    }

    /**
     * Checks if a specific chart is currently being synced
     */
    fun isChartSyncing(chartId: String): Boolean {
        return syncingChartIds.contains(chartId)
    }

    /**
     * Checks if a specific chart is ready (synced and not failed)
     */
    fun isChartReady(chartId: String): Boolean {
        val isFailed = failedCharts.any { it.first == chartId }
        return syncedChartIds.contains(chartId) && !isFailed
    }
}
