package com.joebad.fastbreak.data.repository

import com.joebad.fastbreak.data.model.CachedChartData
import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Serializes read-modify-write on the chart IDs index.
    // Why: parallel chart downloads race on the index (getAllChartIds -> add -> saveChartIds)
    // and last-write-wins drops charts from the index even though their values persist,
    // making them invisible to buildRegistryFromCache.
    private val indexMutex = Mutex()

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
    suspend fun saveChartData(chartId: String, data: CachedChartData) {
        indexMutex.withLock { saveChartDataLocked(chartId, data) }
    }

    // Why: the previous version caught SerializationException silently AND called
    // getAllChartIds() which itself swallows decode failures by returning emptyList().
    // Combined, a decode failure on the index produced a destructive
    // "wipe the index, persist only this chart" overwrite — the data keys stayed
    // but every previously-saved chart became invisible to buildRegistryFromCache.
    // Now we serialize everything up front and read the index via the throwing
    // variant; a decode failure aborts the write instead of corrupting the index,
    // and the exception surfaces in failedCharts.
    private fun saveChartDataLocked(chartId: String, data: CachedChartData) {
        val dataJson = json.encodeToString(data)
        val currentIds = readChartIdsIndexOrThrow().toMutableSet()
        val updatedIndexJson = if (currentIds.add(chartId)) {
            json.encodeToString(currentIds.toList())
        } else null

        settings.putString("$PREFIX_CHART$chartId", dataJson)
        if (updatedIndexJson != null) {
            settings.putString(KEY_CHART_IDS, updatedIndexJson)
        }
    }

    /**
     * Retrieves cached chart data from local storage.
     * Handles migration from legacy dev_ prefixed keys to new format.
     *
     * @param chartId The ID of the chart to retrieve
     * @return The cached chart data, or null if not found or corrupted
     */
    fun getChartData(chartId: String): CachedChartData? {
        return try {
            // First try the new key format
            val jsonString = settings.getStringOrNull("$PREFIX_CHART$chartId")
            if (jsonString != null) {
                return json.decodeFromString<CachedChartData>(jsonString)
            }

            // Check for legacy dev_ prefixed key and migrate if found.
            // Migration runs from needsUpdate() which executes serially before
            // parallel downloads start, so the unlocked path is safe here.
            val legacyChartId = "dev_$chartId"
            val legacyJsonString = settings.getStringOrNull("$PREFIX_CHART$legacyChartId")
            if (legacyJsonString != null) {
                val data = json.decodeFromString<CachedChartData>(legacyJsonString)
                saveChartDataLocked(chartId, data)
                deleteChartDataLocked(legacyChartId)
                return data
            }

            null
        } catch (e: SerializationException) {
            println("Error reading chart data for $chartId: ${e.message}")
            null
        }
    }

    /**
     * Gets the list of all cached chart IDs.
     * This list is stored separately to avoid iterating through all keys.
     *
     * Returns an empty list if the index can't be decoded — safe for diagnostic
     * and read-only callers. Code paths that MODIFY the index must use
     * [readChartIdsIndexOrThrow] instead, since treating a decode failure as
     * "empty" during a read-modify-write would silently wipe the index.
     *
     * @return List of chart IDs that have cached data
     */
    fun getAllChartIds(): List<String> {
        return try {
            readChartIdsIndexOrThrow()
        } catch (e: SerializationException) {
            println("Error reading chart IDs: ${e.message}")
            emptyList()
        }
    }

    /**
     * Reads the chart IDs index from storage, propagating decode failures.
     *
     * Used inside [saveChartDataLocked] and [deleteChartDataLocked] to protect
     * the index from being overwritten with partial data if the stored JSON is
     * unreadable. Callers that just want a best-effort read should use
     * [getAllChartIds] instead.
     */
    private fun readChartIdsIndexOrThrow(): List<String> {
        val jsonString = settings.getStringOrNull(KEY_CHART_IDS) ?: return emptyList()
        return json.decodeFromString<List<String>>(jsonString)
    }

    /**
     * Deletes cached data for a specific chart.
     * Also removes the chart ID from the cached IDs list.
     *
     * @param chartId The ID of the chart to delete
     */
    suspend fun deleteChartData(chartId: String) {
        indexMutex.withLock { deleteChartDataLocked(chartId) }
    }

    private fun deleteChartDataLocked(chartId: String) {
        val currentIds = readChartIdsIndexOrThrow().toMutableSet()
        val updatedIndexJson = if (currentIds.remove(chartId)) {
            json.encodeToString(currentIds.toList())
        } else null

        settings.remove("$PREFIX_CHART$chartId")
        if (updatedIndexJson != null) {
            settings.putString(KEY_CHART_IDS, updatedIndexJson)
        }
    }

    /**
     * Deletes all cached chart data.
     * Useful for clearing the cache or troubleshooting.
     */
    suspend fun clearAllChartData() {
        indexMutex.withLock {
            val chartIds = getAllChartIds()
            chartIds.forEach { chartId ->
                settings.remove("$PREFIX_CHART$chartId")
            }
            settings.remove(KEY_CHART_IDS)
        }
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
    suspend fun markChartAsViewed(chartId: String): Boolean {
        val cachedData = getChartData(chartId) ?: return false

        if (cachedData.viewed) {
            return true
        }

        val updatedData = cachedData.copy(viewed = true)
        saveChartData(chartId, updatedData)
        return true
    }

    /**
     * Marks all cached charts as viewed.
     * Used to clear all "new" indicators at once.
     *
     * @return The number of charts that were marked as viewed
     */
    suspend fun markAllChartsAsViewed(): Int {
        var count = 0
        getAllChartIds().forEach { chartId ->
            val cachedData = getChartData(chartId)
            if (cachedData != null && !cachedData.viewed) {
                val updatedData = cachedData.copy(viewed = true)
                saveChartData(chartId, updatedData)
                count++
            }
        }
        return count
    }
}
