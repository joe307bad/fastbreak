package com.joebad.fastbreak.domain.registry

import com.joebad.fastbreak.data.api.HttpClientFactory
import com.joebad.fastbreak.data.model.BarGraphVisualization
import com.joebad.fastbreak.data.model.CachedChartData
import com.joebad.fastbreak.data.model.ChartDefinition
import com.joebad.fastbreak.data.model.LineChartVisualization
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.data.model.ScatterPlotVisualization
import com.joebad.fastbreak.data.model.VizType
import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.ui.diagnostics.SyncProgress
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
    private val httpClient: HttpClient = HttpClientFactory.create()
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
     * Uses channelFlow to support concurrent emissions from parallel downloads.
     *
     * @param registry The registry containing chart definitions
     * @return Flow of SyncProgress updates, including any errors
     */
    suspend fun synchronizeCharts(registry: Registry): Flow<SyncProgress> = channelFlow {
        println("📊 ChartDataSynchronizer.synchronizeCharts() - Starting")
        println("   Total charts in registry: ${registry.charts.size}")

        val chartsNeedingUpdate = registry.charts.filter { chart ->
            needsUpdate(chart)
        }

        println("   Charts needing update: ${chartsNeedingUpdate.size}")
        chartsNeedingUpdate.forEach { chart ->
            println("     - ${chart.id} (${chart.title})")
        }

        if (chartsNeedingUpdate.isEmpty()) {
            println("✓ All charts already synced, nothing to update")
            // Nothing to update - all charts already synced
            val allChartIds = registry.charts.map { it.id }.toSet()
            send(
                SyncProgress(
                    current = 0,
                    total = 0,
                    currentChart = "",
                    syncedChartIds = allChartIds,
                    syncingChartIds = emptySet(),
                    failedCharts = emptyList()
                )
            )
            return@channelFlow
        }

        println("🔄 Starting parallel download of ${chartsNeedingUpdate.size} charts...")

        // Thread-safe tracking for parallel downloads
        val failedCharts = mutableListOf<Pair<String, String>>() // chartId to error message
        val syncedCharts = mutableSetOf<String>() // Successfully synced chart IDs
        val syncingCharts = mutableSetOf<String>() // Currently syncing chart IDs

        // Mutex for thread-safe access to shared state
        val stateMutex = Mutex()

        // Add already-cached charts to synced set
        registry.charts.forEach { chart ->
            if (!chartsNeedingUpdate.contains(chart)) {
                syncedCharts.add(chart.id)
            }
        }

        // Use semaphore to limit concurrent downloads
        val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
        var completedCount = 0

        // Helper to emit current progress with thread-safe state snapshot
        suspend fun emitProgress() {
            val snapshot = stateMutex.withLock {
                SyncProgress(
                    current = completedCount,
                    total = chartsNeedingUpdate.size,
                    currentChart = syncingCharts.firstOrNull()?.let { id ->
                        chartsNeedingUpdate.find { it.id == id }?.title ?: ""
                    } ?: "",
                    syncedChartIds = syncedCharts.toSet(),
                    syncingChartIds = syncingCharts.toSet(),
                    failedCharts = failedCharts.toList()
                )
            }
            // Emit outside the mutex lock using channelFlow's thread-safe send
            send(snapshot)
        }

        // Launch all downloads in parallel with concurrency limit
        coroutineScope {
            val jobs = chartsNeedingUpdate.map { chartDef ->
                async {
                    // Acquire semaphore permit to limit concurrency
                    semaphore.acquire()

                    try {
                        // Mark as syncing
                        stateMutex.withLock {
                            syncingCharts.add(chartDef.id)
                        }
                        emitProgress()

                        println("⬇️  Downloading chart: ${chartDef.id} (${chartDef.title})")
                        // Download chart data
                        downloadAndCacheChart(chartDef)
                        println("✅ Successfully downloaded: ${chartDef.id}")

                        // Mark as synced
                        stateMutex.withLock {
                            syncedCharts.add(chartDef.id)
                        }
                    } catch (e: Exception) {
                        // Log error but continue with other charts
                        println("❌ Failed to sync chart '${chartDef.title}': ${e.message}")
                        println("   Exception: ${e::class.simpleName}")
                        stateMutex.withLock {
                            failedCharts.add(chartDef.id to (e.message ?: "Unknown error"))
                        }
                    } finally {
                        // Remove from syncing and update progress
                        stateMutex.withLock {
                            syncingCharts.remove(chartDef.id)
                            completedCount++
                        }

                        // Release semaphore permit
                        semaphore.release()

                        // Emit progress update
                        emitProgress()
                    }
                }
            }

            // Wait for all downloads to complete
            jobs.forEach { it.await() }
        }

        println("✅ All chart downloads complete")
        println("   Successfully synced: ${syncedCharts.size}")
        println("   Failed: ${failedCharts.size}")

        // Emit final progress with all results
        val finalSnapshot = stateMutex.withLock {
            SyncProgress(
                current = chartsNeedingUpdate.size,
                total = chartsNeedingUpdate.size,
                currentChart = "",
                syncedChartIds = syncedCharts.toSet(),
                syncingChartIds = emptySet(),
                failedCharts = failedCharts.toList()
            )
        }
        send(finalSnapshot)
        println("📊 ChartDataSynchronizer.synchronizeCharts() - Complete")
    }

    /**
     * Checks if a chart needs updating by comparing timestamps.
     *
     * @param chartDef The chart definition from the registry
     * @return true if the chart needs to be downloaded/updated
     */
    private fun needsUpdate(chartDef: ChartDefinition): Boolean {
        val cached = chartDataRepository.getChartData(chartDef.id)

        if (cached == null) {
            println("🔍 Chart ${chartDef.id}: No cached data, needs update")
            return true
        }

        // Update if the chart definition's lastUpdated is newer than cached version
        val needsUpdate = chartDef.lastUpdated > cached.lastUpdated
        println("🔍 Chart ${chartDef.id}:")
        println("   Registry timestamp: ${chartDef.lastUpdated}")
        println("   Cached timestamp:   ${cached.lastUpdated}")
        println("   Needs update: $needsUpdate")

        return needsUpdate
    }

    /**
     * Downloads chart data from the mock server and caches it.
     * Handles serialization of different visualization types.
     *
     * @param chartDef The chart definition to download
     * @throws Exception if download or caching fails
     */
    private suspend fun downloadAndCacheChart(chartDef: ChartDefinition) {
        // Use localhost:8080 base URL with the relative path from the chart definition
        val chartDataUrl = "http://localhost:8080${chartDef.url}"

        // Fetch chart data from server based on visualization type
        val vizData = when (chartDef.visualizationType) {
            VizType.SCATTER_PLOT -> httpClient.get(chartDataUrl).body<ScatterPlotVisualization>()
            VizType.BAR_GRAPH -> httpClient.get(chartDataUrl).body<BarGraphVisualization>()
            VizType.LINE_CHART -> httpClient.get(chartDataUrl).body<LineChartVisualization>()
        }

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

        println("💾 Caching chart ${chartDef.id} with timestamp: ${chartDef.lastUpdated}")

        // Save to repository
        chartDataRepository.saveChartData(chartDef.id, cachedData)
        println("✅ Chart ${chartDef.id} saved to cache")
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
