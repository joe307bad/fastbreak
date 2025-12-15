package com.joebad.fastbreak.data.repository

import com.joebad.fastbreak.data.model.CachedChartData
import com.russhwolf.settings.Settings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for persisting and retrieving cached chart data.
 * Each chart's data is stored separately as a JSON string.
 * Uses multiplatform-settings for cross-platform storage.
 */
class ChartDataRepository(
    private val settings: Settings
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        private const val KEY_CHART_IDS = "all_chart_ids"
        private const val PREFIX_CHART = "chart_"
    }

    /**
     * Saves chart data to local storage.
     * Also updates the list of cached chart IDs.
     *
     * @param chartId The ID of the chart
     * @param data The cached chart data to save
     */
    fun saveChartData(chartId: String, data: CachedChartData) {
        try {
            val jsonString = json.encodeToString(data)
            settings.putString("$PREFIX_CHART$chartId", jsonString)

            // Add to chart IDs list if not already present
            val currentIds = getAllChartIds().toMutableSet()
            if (currentIds.add(chartId)) {
                saveChartIds(currentIds.toList())
            }
        } catch (e: SerializationException) {
            println("Error saving chart data for $chartId: ${e.message}")
        }
    }

    /**
     * Retrieves cached chart data from local storage.
     *
     * @param chartId The ID of the chart to retrieve
     * @return The cached chart data, or null if not found or corrupted
     */
    fun getChartData(chartId: String): CachedChartData? {
        return try {
            val jsonString = settings.getStringOrNull("$PREFIX_CHART$chartId") ?: return null
            json.decodeFromString<CachedChartData>(jsonString)
        } catch (e: SerializationException) {
            println("Error reading chart data for $chartId: ${e.message}")
            null
        }
    }

    /**
     * Gets the list of all cached chart IDs.
     * This list is stored separately to avoid iterating through all keys.
     *
     * @return List of chart IDs that have cached data
     */
    fun getAllChartIds(): List<String> {
        return try {
            val jsonString = settings.getStringOrNull(KEY_CHART_IDS) ?: return emptyList()
            json.decodeFromString<List<String>>(jsonString)
        } catch (e: SerializationException) {
            println("Error reading chart IDs: ${e.message}")
            emptyList()
        }
    }

    /**
     * Saves the list of chart IDs to storage.
     * This is called internally when charts are added or removed.
     *
     * @param ids List of chart IDs to save
     */
    private fun saveChartIds(ids: List<String>) {
        try {
            val jsonString = json.encodeToString(ids)
            settings.putString(KEY_CHART_IDS, jsonString)
        } catch (e: SerializationException) {
            println("Error saving chart IDs: ${e.message}")
        }
    }

    /**
     * Deletes cached data for a specific chart.
     * Also removes the chart ID from the cached IDs list.
     *
     * @param chartId The ID of the chart to delete
     */
    fun deleteChartData(chartId: String) {
        settings.remove("$PREFIX_CHART$chartId")

        // Remove from chart IDs list
        val currentIds = getAllChartIds().toMutableSet()
        if (currentIds.remove(chartId)) {
            saveChartIds(currentIds.toList())
        }
    }

    /**
     * Deletes all cached chart data.
     * Useful for clearing the cache or troubleshooting.
     */
    fun clearAllChartData() {
        val chartIds = getAllChartIds()
        chartIds.forEach { chartId ->
            settings.remove("$PREFIX_CHART$chartId")
        }
        settings.remove(KEY_CHART_IDS)
    }

    /**
     * Gets the total number of cached charts.
     *
     * @return Count of cached charts
     */
    fun getCachedChartCount(): Int {
        return getAllChartIds().size
    }

    /**
     * Estimates the total size of all cached chart data in bytes.
     * This is an approximation based on the JSON string lengths.
     *
     * @return Estimated cache size in bytes
     */
    fun estimateTotalCacheSize(): Long {
        var totalSize = 0L
        getAllChartIds().forEach { chartId ->
            getChartData(chartId)?.let { data ->
                totalSize += data.estimatedSizeBytes
            }
        }
        return totalSize
    }

    /**
     * Checks if a specific chart has cached data.
     *
     * @param chartId The ID of the chart to check
     * @return true if the chart has cached data
     */
    fun hasChartData(chartId: String): Boolean {
        return settings.hasKey("$PREFIX_CHART$chartId")
    }

    /**
     * Marks a chart as viewed by the user.
     * Updates the cached data with viewed = true.
     *
     * @param chartId The ID of the chart to mark as viewed
     * @return true if the chart was successfully marked as viewed
     */
    fun markChartAsViewed(chartId: String): Boolean {
        val cachedData = getChartData(chartId) ?: return false

        // Only update if not already viewed
        if (cachedData.viewed) {
            return true
        }

        val updatedData = cachedData.copy(viewed = true)
        saveChartData(chartId, updatedData)
        return true
    }
}
