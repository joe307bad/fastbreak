package com.joebad.fastbreak.data.repository

import com.joebad.fastbreak.data.model.CachedChartData

/**
 * Interface for chart data caching operations.
 *
 * This abstraction allows swapping storage backends (SQLDelight, Realm, DataStore, etc.)
 * without changing consumers. Implementations must be thread-safe for concurrent access.
 */
interface ChartCache {
    /**
     * Saves chart data to the cache.
     * If data already exists for this chartId, it will be replaced.
     *
     * @param chartId The unique identifier for the chart
     * @param data The chart data to cache
     */
    fun saveChartData(chartId: String, data: CachedChartData)

    /**
     * Retrieves cached chart data.
     *
     * @param chartId The unique identifier for the chart
     * @return The cached data, or null if not found
     */
    fun getChartData(chartId: String): CachedChartData?

    /**
     * Gets the list of all cached chart IDs.
     *
     * @return List of chart IDs that have cached data
     */
    fun getAllChartIds(): List<String>

    /**
     * Deletes cached data for a specific chart.
     *
     * @param chartId The unique identifier for the chart to delete
     */
    fun deleteChartData(chartId: String)

    /**
     * Deletes all cached chart data.
     */
    fun clearAllChartData()

    /**
     * Checks if a specific chart has cached data.
     *
     * @param chartId The unique identifier for the chart
     * @return true if cached data exists for this chart
     */
    fun hasChartData(chartId: String): Boolean

    /**
     * Marks a chart as viewed by the user.
     *
     * @param chartId The unique identifier for the chart
     * @return true if the chart was found and marked as viewed
     */
    fun markChartAsViewed(chartId: String): Boolean

    /**
     * Marks all cached charts as viewed.
     *
     * @return The number of charts that were marked as viewed
     */
    fun markAllChartsAsViewed(): Int

    /**
     * Deletes charts that are not in the provided set of valid IDs.
     * Used to clean up orphaned cache entries after registry updates.
     *
     * @param validChartIds Set of chart IDs that should be kept
     * @return List of chart IDs that were deleted
     */
    fun deleteOrphanedCharts(validChartIds: Set<String>): List<String>

    /**
     * Estimates the total size of all cached chart data in bytes.
     *
     * @return Estimated cache size in bytes
     */
    fun estimateTotalCacheSize(): Long

    /**
     * Gets the total number of cached charts.
     *
     * @return Count of cached charts
     */
    fun getCachedChartCount(): Int
}
