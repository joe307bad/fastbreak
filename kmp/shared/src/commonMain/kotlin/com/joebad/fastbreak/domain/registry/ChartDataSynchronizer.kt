package com.joebad.fastbreak.domain.registry

import com.joebad.fastbreak.data.api.MockedDataApi
import com.joebad.fastbreak.data.model.BarGraphVisualization
import com.joebad.fastbreak.data.model.CachedChartData
import com.joebad.fastbreak.data.model.ChartDefinition
import com.joebad.fastbreak.data.model.LineChartVisualization
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.data.model.ScatterPlotVisualization
import com.joebad.fastbreak.data.model.VizType
import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.ui.diagnostics.SyncProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Synchronizes chart data based on registry definitions.
 * Compares timestamps to determine which charts need updates and downloads them.
 */
class ChartDataSynchronizer(
    private val chartDataRepository: ChartDataRepository,
    private val mockedDataApi: MockedDataApi
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        /**
         * Maximum number of concurrent chart downloads
         */
        private const val MAX_CONCURRENT_DOWNLOADS = 3
    }

    /**
     * Synchronizes all charts in the registry that need updating.
     * Emits progress updates via Flow.
     * Continues syncing even if individual charts fail.
     *
     * @param registry The registry containing chart definitions
     * @return Flow of SyncProgress updates, including any errors
     */
    suspend fun synchronizeCharts(registry: Registry): Flow<SyncProgress> = flow {
        val chartsNeedingUpdate = registry.charts.filter { chart ->
            needsUpdate(chart)
        }

        if (chartsNeedingUpdate.isEmpty()) {
            // Nothing to update
            emit(
                SyncProgress(
                    current = 0,
                    total = 0,
                    currentChart = "",
                    failedCharts = emptyList()
                )
            )
            return@flow
        }

        val failedCharts = mutableListOf<Pair<String, String>>() // chartId to error message

        chartsNeedingUpdate.forEachIndexed { index, chartDef ->
            emit(
                SyncProgress(
                    current = index + 1,
                    total = chartsNeedingUpdate.size,
                    currentChart = chartDef.title,
                    failedCharts = failedCharts.toList()
                )
            )

            try {
                downloadAndCacheChart(chartDef)
            } catch (e: Exception) {
                // Log error but continue with other charts
                println("Failed to sync chart '${chartDef.title}': ${e.message}")
                failedCharts.add(chartDef.id to (e.message ?: "Unknown error"))
            }
        }

        // Emit final progress with all results
        emit(
            SyncProgress(
                current = chartsNeedingUpdate.size,
                total = chartsNeedingUpdate.size,
                currentChart = "",
                failedCharts = failedCharts.toList()
            )
        )
    }

    /**
     * Checks if a chart needs updating by comparing timestamps.
     *
     * @param chartDef The chart definition from the registry
     * @return true if the chart needs to be downloaded/updated
     */
    private fun needsUpdate(chartDef: ChartDefinition): Boolean {
        val cached = chartDataRepository.getChartData(chartDef.id) ?: return true

        // Update if the chart definition's lastUpdated is newer than cached version
        return chartDef.lastUpdated > cached.lastUpdated
    }

    /**
     * Downloads chart data from the mock API and caches it.
     * Handles serialization of different visualization types.
     *
     * @param chartDef The chart definition to download
     * @throws Exception if download or caching fails
     */
    private suspend fun downloadAndCacheChart(chartDef: ChartDefinition) {
        // Map data model types to MockedDataApi types
        val apiSport = when (chartDef.sport) {
            com.joebad.fastbreak.data.model.Sport.NFL -> MockedDataApi.Sport.NFL
            com.joebad.fastbreak.data.model.Sport.NBA -> MockedDataApi.Sport.NBA
            com.joebad.fastbreak.data.model.Sport.MLB -> MockedDataApi.Sport.MLB
            com.joebad.fastbreak.data.model.Sport.NHL -> MockedDataApi.Sport.NHL
        }

        val apiVizType = when (chartDef.visualizationType) {
            VizType.SCATTER_PLOT -> MockedDataApi.VizType.SCATTER
            VizType.BAR_GRAPH -> MockedDataApi.VizType.BAR
            VizType.LINE_CHART -> MockedDataApi.VizType.LINE
        }

        // Use MockedDataApi to generate data based on chart definition
        val vizData = mockedDataApi.fetchVisualizationData(
            sport = apiSport,
            vizType = apiVizType
        )

        // Serialize visualization data based on type
        val dataJson = when (vizData) {
            is ScatterPlotVisualization -> json.encodeToString(vizData)
            is BarGraphVisualization -> json.encodeToString(vizData)
            is LineChartVisualization -> json.encodeToString(vizData)
            else -> throw IllegalArgumentException("Unsupported visualization type: ${vizData::class.simpleName}")
        }

        // Create cached data entry
        val cachedData = CachedChartData(
            chartId = chartDef.id,
            lastUpdated = chartDef.lastUpdated,
            visualizationType = chartDef.visualizationType,
            cachedAt = Clock.System.now(),
            dataJson = dataJson
        )

        // Save to repository
        chartDataRepository.saveChartData(chartDef.id, cachedData)
    }

    /**
     * Gets the list of all cached chart IDs.
     * Useful for diagnostics.
     *
     * @return List of chart IDs that have cached data
     */
    fun getCachedChartIds(): List<String> {
        return chartDataRepository.getAllChartIds()
    }

    /**
     * Gets the time when a specific chart was cached.
     * Returns null if chart is not cached.
     *
     * @param chartId The chart ID to check
     * @return The timestamp when the chart was cached, or null
     */
    fun getChartCacheTime(chartId: String): Instant? {
        return chartDataRepository.getChartData(chartId)?.cachedAt
    }

    /**
     * Estimates the total size of all cached chart data in bytes.
     *
     * @return Estimated cache size in bytes
     */
    fun estimateCacheSize(): Long {
        return chartDataRepository.estimateTotalCacheSize()
    }

    /**
     * Clears all cached chart data.
     * Useful for troubleshooting or when the user wants to reset.
     */
    fun clearAllCache() {
        chartDataRepository.clearAllChartData()
    }

    /**
     * Gets cached chart data for a specific chart.
     * Returns null if not cached.
     *
     * @param chartId The chart ID to retrieve
     * @return The cached chart data, or null if not found
     */
    fun getCachedChartData(chartId: String): CachedChartData? {
        return chartDataRepository.getChartData(chartId)
    }

    /**
     * Checks if a specific chart has cached data.
     *
     * @param chartId The chart ID to check
     * @return true if the chart has cached data
     */
    fun hasChartData(chartId: String): Boolean {
        return chartDataRepository.hasChartData(chartId)
    }
}
