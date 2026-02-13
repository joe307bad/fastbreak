package com.joebad.fastbreak.domain.registry

import com.joebad.fastbreak.data.api.HttpClientFactory
import com.joebad.fastbreak.data.api.RegistryApi
import com.joebad.fastbreak.data.model.BarGraphVisualization
import com.joebad.fastbreak.data.model.CachedChartData
import com.joebad.fastbreak.data.model.ChartDefinition
import com.joebad.fastbreak.data.model.LineChartVisualization
import com.joebad.fastbreak.data.model.MatchupVisualization
import com.joebad.fastbreak.data.model.MatchupV2Visualization
import com.joebad.fastbreak.data.model.NBAMatchupVisualization
import com.joebad.fastbreak.data.model.RegistryEntry
import com.joebad.fastbreak.data.model.ScatterPlotVisualization
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.TableVisualization
import com.joebad.fastbreak.data.model.TopicsResponse
import com.joebad.fastbreak.data.model.VizType
import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.data.repository.TopicsRepository
import com.joebad.fastbreak.logging.SentryLogger
import com.joebad.fastbreak.logging.SpanStatus
import com.joebad.fastbreak.ui.diagnostics.SyncProgress
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Synchronizes chart data based on registry entries.
 * Compares timestamps to determine which charts need updates and downloads them.
 */
class ChartDataSynchronizer(
    private val chartDataRepository: ChartDataRepository,
    private val topicsRepository: TopicsRepository,
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
     * @param registryEntries Map of file_key to RegistryEntry
     * @return Flow of SyncProgress updates, including any errors
     */
    suspend fun synchronizeCharts(registryEntries: Map<String, RegistryEntry>): Flow<SyncProgress> = channelFlow {
        println("📊 ChartDataSynchronizer.synchronizeCharts() - Starting")
        println("   Total entries in registry: ${registryEntries.size}")

        // Filter to only chart entries (exclude topics and other non-chart types)
        val chartEntries = registryEntries.filter { (_, entry) -> entry.isChart }
        println("   Chart entries (excluding topics): ${chartEntries.size}")

        val chartsNeedingUpdate = chartEntries.filter { (fileKey, entry) ->
            needsUpdate(fileKey, entry)
        }

        println("   Charts needing update: ${chartsNeedingUpdate.size}")
        chartsNeedingUpdate.forEach { (fileKey, entry) ->
            println("     - $fileKey (${entry.title})")
        }

        if (chartsNeedingUpdate.isEmpty()) {
            println("✓ All charts already synced, nothing to update")

            // Clean up orphaned charts even when nothing needs updating
            println("🔧 SYNC: About to call cleanupOrphanedCharts() with ${chartEntries.size} chart entries (early path)")
            cleanupOrphanedCharts(chartEntries)
            println("🔧 SYNC: cleanupOrphanedCharts() completed (early path)")

            // Nothing to update - all charts already synced
            val allChartIds = chartEntries.keys.map { fileKeyToChartId(it) }.toSet()
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
        chartEntries.forEach { (fileKey, _) ->
            val chartId = fileKeyToChartId(fileKey)
            if (!chartsNeedingUpdate.containsKey(fileKey)) {
                syncedCharts.add(chartId)
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
                    currentChart = syncingCharts.firstOrNull()?.let { chartId ->
                        chartsNeedingUpdate.entries.find { fileKeyToChartId(it.key) == chartId }?.value?.title ?: ""
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
            val jobs = chartsNeedingUpdate.map { (fileKey, entry) ->
                async {
                    val chartId = fileKeyToChartId(fileKey)
                    // Acquire semaphore permit to limit concurrency
                    semaphore.acquire()

                    try {
                        // Mark as syncing
                        stateMutex.withLock {
                            syncingCharts.add(chartId)
                        }
                        emitProgress()

                        println("⬇️  Downloading chart: $fileKey (${entry.title})")
                        // Download chart data
                        downloadAndCacheChart(fileKey, entry)
                        println("✅ Successfully downloaded: $fileKey")

                        // Mark as synced
                        stateMutex.withLock {
                            syncedCharts.add(chartId)
                        }
                    } catch (e: Exception) {
                        // Log error but continue with other charts
                        println("❌ Failed to sync chart '${entry.title}': ${e.message}")
                        println("   Exception: ${e::class.simpleName}")
                        stateMutex.withLock {
                            failedCharts.add(chartId to (e.message ?: "Unknown error"))
                        }
                    } finally {
                        // Remove from syncing and update progress
                        stateMutex.withLock {
                            syncingCharts.remove(chartId)
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

        // Clean up cached charts that are no longer in the registry
        println("🔧 SYNC: About to call cleanupOrphanedCharts() with ${chartEntries.size} chart entries")
        cleanupOrphanedCharts(chartEntries)
        println("🔧 SYNC: cleanupOrphanedCharts() completed")

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
     * Converts a file key to a chart ID.
     * e.g., "dev/nfl__team_tier_list.json" -> "nfl__team_tier_list"
     * Note: We remove the dev/ prefix to match the F# server's chart ID format,
     * which is used in Topics data points.
     */
    private fun fileKeyToChartId(fileKey: String): String {
        return fileKey
            .removePrefix("dev/")
            .removeSuffix(".json")
            .replace("/", "_")
    }

    /**
     * Checks if a chart needs updating by comparing timestamps.
     *
     * @param fileKey The file key from the registry
     * @param entry The registry entry with updatedAt timestamp
     * @return true if the chart needs to be downloaded/updated
     */
    private fun needsUpdate(fileKey: String, entry: RegistryEntry): Boolean {
        val chartId = fileKeyToChartId(fileKey)
        val cached = chartDataRepository.getChartData(chartId)

        if (cached == null) {
            println("🔍 Chart $chartId: No cached data, needs update")
            return true
        }

        // Update if the registry entry's updatedAt is newer than cached version
        val needsUpdate = entry.updatedAt > cached.lastUpdated
        println("🔍 Chart $chartId:")
        println("   Registry timestamp: ${entry.updatedAt}")
        println("   Cached timestamp:   ${cached.lastUpdated}")
        println("   Needs update: $needsUpdate")

        return needsUpdate
    }

    /**
     * Downloads chart data from the CDN and caches it.
     * Parses visualizationType from the JSON to determine how to deserialize.
     *
     * @param fileKey The file key (path) of the chart
     * @param entry The registry entry with metadata
     * @throws Exception if download or caching fails
     */
    private suspend fun downloadAndCacheChart(fileKey: String, entry: RegistryEntry) {
        // Build URL: base URL + file key (file key already includes dev/ if needed)
        val chartDataUrl = "${RegistryApi.BASE_URL}/$fileKey"
        println("🌐 Fetching chart data from: $chartDataUrl")

        val startTime = Clock.System.now()
        val spanId = SentryLogger.startSpan("http.client", "GET /$fileKey")

        SentryLogger.addBreadcrumb(
            category = "network",
            message = "Fetching chart: ${entry.title}",
            data = mapOf("fileKey" to fileKey, "url" to chartDataUrl)
        )

        try {
            // First, fetch raw JSON to determine visualization type
            val rawJson = httpClient.get(chartDataUrl).bodyAsText()
            val requestDurationMs = (Clock.System.now() - startTime).toDouble(DurationUnit.MILLISECONDS)

            println("   Request duration: ${requestDurationMs.toLong()}ms")

            val jsonElement = json.parseToJsonElement(rawJson)
            val vizTypeString = jsonElement.jsonObject["visualizationType"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Chart JSON missing visualizationType field")

            val vizType = VizType.valueOf(vizTypeString)
            println("   Visualization type: $vizType")

            // Deserialize based on visualization type
            val vizData = when (vizType) {
                VizType.SCATTER_PLOT -> json.decodeFromString<ScatterPlotVisualization>(rawJson)
                VizType.BAR_GRAPH -> json.decodeFromString<BarGraphVisualization>(rawJson)
                VizType.LINE_CHART -> json.decodeFromString<LineChartVisualization>(rawJson)
                VizType.TABLE -> json.decodeFromString<TableVisualization>(rawJson)
                VizType.MATCHUP -> json.decodeFromString<MatchupVisualization>(rawJson)
                VizType.MATCHUP_V2 -> json.decodeFromString<MatchupV2Visualization>(rawJson)
                VizType.NBA_MATCHUP -> json.decodeFromString<NBAMatchupVisualization>(rawJson)
            }

            val chartId = fileKeyToChartId(fileKey)

            // When a chart is downloaded, it's because there's NEW data (needsUpdate returned true)
            // So we mark it as unviewed so the user sees the unread indicator
            val existingCached = chartDataRepository.getChartData(chartId)

            if (existingCached != null) {
                println("   🔄 Updated chart $chartId: resetting viewed=false (was viewed=${existingCached.viewed})")
            } else {
                println("   🆕 New chart $chartId: viewed=false")
            }

            // Create cached data entry with viewed=false since this is new/updated data
            val cachedData = CachedChartData(
                chartId = chartId,
                lastUpdated = entry.updatedAt,
                visualizationType = vizType,
                cachedAt = Clock.System.now(),
                dataJson = rawJson,
                interval = entry.interval,
                viewed = false
            )

            println("💾 Caching chart $chartId with timestamp: ${entry.updatedAt} (viewed=false, marking as unread)")

            // Save to repository
            chartDataRepository.saveChartData(chartId, cachedData)
            println("✅ Chart $chartId saved to cache")

            SentryLogger.finishSpan(spanId, SpanStatus.OK)

            SentryLogger.addBreadcrumb(
                category = "network",
                message = "Chart downloaded: ${entry.title}",
                data = mapOf(
                    "chartId" to chartId,
                    "fileKey" to fileKey,
                    "durationMs" to requestDurationMs.toLong(),
                    "sizeBytes" to rawJson.length,
                    "vizType" to vizType.name
                )
            )
        } catch (e: Exception) {
            val requestDurationMs = (Clock.System.now() - startTime).toDouble(DurationUnit.MILLISECONDS)

            SentryLogger.finishSpan(spanId, SpanStatus.ERROR)

            SentryLogger.captureException(
                throwable = e,
                extras = mapOf(
                    "fileKey" to fileKey,
                    "chartTitle" to entry.title,
                    "url" to chartDataUrl,
                    "durationMs" to requestDurationMs.toLong()
                )
            )

            throw e
        }
    }

    /**
     * Builds ChartDefinition from cached chart data.
     * Used to reconstruct the chart definition from stored data.
     */
    fun buildChartDefinition(chartId: String, cached: CachedChartData): ChartDefinition? {
        val vizData = cached.deserialize() ?: return null

        return ChartDefinition(
            id = chartId,
            sport = Sport.valueOf(vizData.sport),
            title = vizData.title,
            subtitle = vizData.subtitle,
            lastUpdated = cached.lastUpdated,
            visualizationType = cached.visualizationType,
            url = "", // URL not needed once cached
            interval = cached.interval,
            cachedAt = cached.cachedAt,
            viewed = cached.viewed,
            tags = vizData.tags,
            sortOrder = vizData.sortOrder
        )
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

    /**
     * Marks a chart as viewed by the user.
     *
     * @param chartId The chart ID to mark as viewed
     * @return true if the chart was successfully marked as viewed
     */
    fun markChartAsViewed(chartId: String): Boolean {
        return chartDataRepository.markChartAsViewed(chartId)
    }

    /**
     * Removes cached charts that are no longer present in the registry.
     * This cleanup ensures we don't keep stale chart data that's been removed from the server.
     *
     * @param registryEntries Map of file_key to RegistryEntry from the current registry
     */
    private fun cleanupOrphanedCharts(registryEntries: Map<String, RegistryEntry>) {
        println("═══════════════════════════════════════════════════════")
        println("🧹 CLEANUP: Starting orphaned charts cleanup")
        println("═══════════════════════════════════════════════════════")

        // Get all currently cached chart IDs
        val cachedChartIds = chartDataRepository.getAllChartIds()
        println("📦 CLEANUP: Found ${cachedChartIds.size} cached chart(s):")
        cachedChartIds.forEach { println("   - $it") }

        // Build set of valid chart IDs from the registry
        val validChartIds = registryEntries.keys.map { fileKeyToChartId(it) }.toSet()
        println("📋 CLEANUP: Registry has ${validChartIds.size} valid chart(s):")
        validChartIds.forEach { println("   - $it") }

        // Find orphaned charts (cached but not in registry)
        val orphanedChartIds = cachedChartIds.filter { it !in validChartIds }

        if (orphanedChartIds.isEmpty()) {
            println("✅ CLEANUP: No orphaned charts found - all cached charts are valid")
            println("═══════════════════════════════════════════════════════")
            return
        }

        println("⚠️  CLEANUP: Found ${orphanedChartIds.size} orphaned chart(s) to remove:")
        orphanedChartIds.forEach { chartId ->
            println("   🗑️  Removing: $chartId")
            chartDataRepository.deleteChartData(chartId)
            println("   ✅ Deleted: $chartId")
        }

        // Verify deletion
        val remainingChartIds = chartDataRepository.getAllChartIds()
        println("📦 CLEANUP: After deletion, ${remainingChartIds.size} chart(s) remain in cache:")
        remainingChartIds.forEach { println("   - $it") }

        println("✅ CLEANUP: Complete - removed ${orphanedChartIds.size} orphaned chart(s)")
        println("═══════════════════════════════════════════════════════")
    }

    /**
     * Synchronizes topics data from registry entries.
     * Finds entries with type="topics" and downloads if needed.
     *
     * @param registryEntries Map of file_key to RegistryEntry
     */
    suspend fun synchronizeTopics(registryEntries: Map<String, RegistryEntry>) {
        println("📰 ChartDataSynchronizer.synchronizeTopics() - Starting")

        // Find topics entries
        val topicsEntries = registryEntries.filter { (_, entry) -> entry.isTopics }
        println("   Topics entries found: ${topicsEntries.size}")

        if (topicsEntries.isEmpty()) {
            println("   No topics entries in registry, skipping")
            return
        }

        // Process each topics entry (usually just one)
        topicsEntries.forEach { (fileKey, entry) ->
            println("   Processing: $fileKey (${entry.title})")

            // Check if we need to update
            if (!topicsRepository.needsUpdate(entry.updatedAt)) {
                println("   ✅ Topics already up to date, skipping download")
                return@forEach
            }

            // Download topics data
            try {
                val topicsUrl = "${RegistryApi.BASE_URL}/$fileKey"
                println("   🌐 Fetching topics from: $topicsUrl")

                val startTime = Clock.System.now()
                val rawJson = httpClient.get(topicsUrl).bodyAsText()
                val requestDurationMs = (Clock.System.now() - startTime).toDouble(DurationUnit.MILLISECONDS)

                println("   Request duration: ${requestDurationMs.toLong()}ms")

                val topicsResponse = json.decodeFromString<TopicsResponse>(rawJson)
                println("   ✅ Parsed topics: ${topicsResponse.narratives.size} narratives")

                // Save to repository and reset viewed state since we have new topics
                topicsRepository.saveTopics(topicsResponse, entry.updatedAt)
                topicsRepository.resetViewed()
                println("   ✅ Topics saved to cache (marked as unviewed)")
            } catch (e: Exception) {
                println("   ❌ Failed to sync topics: ${e.message}")
                SentryLogger.captureException(
                    throwable = e,
                    extras = mapOf(
                        "fileKey" to fileKey,
                        "action" to "sync_topics"
                    )
                )
            }
        }

        println("📰 ChartDataSynchronizer.synchronizeTopics() - Complete")
    }

    /**
     * Gets the cached topics data.
     *
     * @return The cached topics, or null if not found
     */
    fun getCachedTopics(): TopicsResponse? {
        return topicsRepository.getTopics()
    }

    /**
     * Gets the timestamp when topics were last updated.
     *
     * @return The last update timestamp, or null if not found
     */
    fun getTopicsUpdatedAt(): Instant? {
        return topicsRepository.getUpdatedAt()
    }

    /**
     * Clears cached topics data to force a re-download.
     */
    fun clearTopicsCache() {
        println("🗑️ ChartDataSynchronizer.clearTopicsCache()")
        topicsRepository.clear()
    }

    /**
     * Checks if topics have been viewed by the user.
     *
     * @return true if topics have been viewed, false otherwise
     */
    fun hasTopicsBeenViewed(): Boolean {
        return topicsRepository.hasBeenViewed()
    }

    /**
     * Marks topics as viewed by the user.
     */
    fun markTopicsAsViewed() {
        topicsRepository.markAsViewed()
    }
}
