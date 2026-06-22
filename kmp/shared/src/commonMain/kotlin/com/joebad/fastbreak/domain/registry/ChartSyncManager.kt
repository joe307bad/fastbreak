package com.joebad.fastbreak.domain.registry

import com.joebad.fastbreak.data.api.HttpClientFactory
import com.joebad.fastbreak.data.api.RegistryApi
import com.joebad.fastbreak.data.model.BarGraphVisualization
import com.joebad.fastbreak.data.model.CBBMatchupVisualization
import com.joebad.fastbreak.data.model.CachedChartData
import com.joebad.fastbreak.data.model.CachedChartMetadata
import com.joebad.fastbreak.data.model.ChartDefinition
import com.joebad.fastbreak.data.model.HelloWorldVisualization
import com.joebad.fastbreak.data.model.LineChartVisualization
import com.joebad.fastbreak.data.model.MatchupVisualization
import com.joebad.fastbreak.data.model.MatchupV2Visualization
import com.joebad.fastbreak.data.model.MLBMatchupVisualization
import com.joebad.fastbreak.data.model.MLBTeamReportCardVisualization
import com.joebad.fastbreak.data.model.NBAMatchupVisualization
import com.joebad.fastbreak.data.model.NBAPlayoffBracketVisualization
import com.joebad.fastbreak.data.model.NCAABracketVisualization
import com.joebad.fastbreak.data.model.NHLMatchupVisualization
import com.joebad.fastbreak.data.model.NHLPlayoffBracketVisualization
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.data.model.RegistryEntry
import com.joebad.fastbreak.data.model.ScatterPlotVisualization
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.TableVisualization
import com.joebad.fastbreak.data.model.TopicsResponse
import com.joebad.fastbreak.data.model.VizType
import com.joebad.fastbreak.data.repository.ChartCache
import com.joebad.fastbreak.data.repository.TopicsRepository
import com.joebad.fastbreak.logging.SentryLogger
import com.joebad.fastbreak.logging.SpanStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Manages chart data synchronization with clean state management.
 *
 * Key improvements over ChartDataSynchronizer:
 * - Uses [ChartCache] interface (SQLDelight) for atomic writes
 * - Exposes sync state via StateFlow for reactive UI updates
 * - No verification retry needed - SQLite writes are immediately visible
 * - Simpler state machine with [ChartSyncState] sealed class
 */
class ChartSyncManager(
    private val chartCache: ChartCache,
    private val topicsRepository: TopicsRepository,
    private val httpClient: HttpClient = HttpClientFactory.create()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        coerceInputValues = true
    }

    companion object {
        private const val MAX_CONCURRENT_DOWNLOADS = 3
    }

    // StateFlow for reactive sync progress updates
    private val _syncProgress = MutableStateFlow<ChartSyncProgress?>(null)
    val syncProgress: StateFlow<ChartSyncProgress?> = _syncProgress.asStateFlow()

    /**
     * Charts that failed to download in this app session.
     * Skipped on automatic sync (e.g. returning to home); cleared on manual refresh.
     */
    private val failedCharts = mutableMapOf<String, String>()

    /** Failed charts from the current session: chartId to error message. */
    fun getFailedCharts(): Map<String, String> = failedCharts.toMap()

    /** Clears session failure state so all charts are retried (manual refresh). */
    fun clearFailedCharts() {
        if (failedCharts.isNotEmpty()) {
            println("🔄 Clearing ${failedCharts.size} failed chart(s) for retry")
            failedCharts.clear()
        }
    }

    /**
     * Synchronizes all charts that need updating.
     * Updates state via StateFlow for UI consumption.
     *
     * @param registryEntries Map of file_key to RegistryEntry
     * @param retryFailed When true, previously failed charts are retried (manual refresh).
     *                    When false, they are skipped until the user refreshes.
     */
    suspend fun synchronizeCharts(
        registryEntries: Map<String, RegistryEntry>,
        retryFailed: Boolean = false
    ): ChartSyncResult {
        println("📊 ChartSyncManager.synchronizeCharts() - Starting")
        println("   Total entries in registry: ${registryEntries.size}")

        val chartEntries = registryEntries.filter { (_, entry) -> entry.isChart && !entry.isSystem }
        println("   Chart entries (excluding topics): ${chartEntries.size}")

        val allNeedingUpdate = chartEntries.filter { (fileKey, entry) ->
            needsUpdate(fileKey, entry)
        }

        val skippedFailed = if (retryFailed) {
            emptyMap()
        } else {
            allNeedingUpdate.filter { (fileKey, _) ->
                fileKeyToChartId(fileKey) in failedCharts
            }
        }

        val chartsNeedingUpdate = if (retryFailed) {
            allNeedingUpdate
        } else {
            allNeedingUpdate.filter { (fileKey, _) ->
                fileKeyToChartId(fileKey) !in failedCharts
            }
        }

        println("   Charts needing update: ${allNeedingUpdate.size}")
        allNeedingUpdate.forEach { (fileKey, entry) ->
            println("     - $fileKey (${entry.title})")
        }
        if (skippedFailed.isNotEmpty()) {
            println("   Skipping ${skippedFailed.size} previously failed chart(s):")
            skippedFailed.forEach { (fileKey, entry) ->
                val chartId = fileKeyToChartId(fileKey)
                println("     - $fileKey (${entry.title}): ${failedCharts[chartId]}")
            }
        }

        if (chartsNeedingUpdate.isEmpty()) {
            println("✓ All charts already synced, nothing to update")
            cleanupOrphanedCharts(chartEntries)
            _syncProgress.value = ChartSyncProgress(
                chartStates = emptyMap(),
                totalToSync = 0
            )
            return ChartSyncResult(attemptedCount = 0, newFailures = emptyList())
        }

        // Initialize sync progress with all charts in Idle state
        val initialStates = chartsNeedingUpdate.mapKeys { fileKeyToChartId(it.key) }
            .mapValues { ChartSyncState.Idle as ChartSyncState }

        _syncProgress.value = ChartSyncProgress(
            chartStates = initialStates,
            totalToSync = chartsNeedingUpdate.size
        )

        println("🔄 Starting parallel download of ${chartsNeedingUpdate.size} charts...")

        // Download charts with concurrency limit
        val semaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)

        supervisorScope {
            val jobs = chartsNeedingUpdate.map { (fileKey, entry) ->
                async {
                    val chartId = fileKeyToChartId(fileKey)
                    semaphore.acquire()

                    try {
                        // Update state to syncing
                        updateChartState(chartId, ChartSyncState.Syncing, entry.title)

                        println("⬇️  Downloading chart: $fileKey (${entry.title})")
                        downloadAndCacheChart(fileKey, entry)
                        println("✅ Successfully downloaded: $fileKey")

                        // Update state to ready
                        updateChartState(chartId, ChartSyncState.Ready)

                    } catch (e: CancellationException) {
                        println("⚠️ Chart $chartId cancelled: ${e.message}")
                        throw e
                    } catch (e: Exception) {
                        val errorMessage = e.message ?: "Unknown error"
                        println("❌ Failed to sync chart '$fileKey': $errorMessage")
                        println("   Exception: ${e::class.simpleName}")
                        failedCharts[chartId] = errorMessage
                        updateChartState(chartId, ChartSyncState.Failed(errorMessage))
                    } finally {
                        semaphore.release()
                    }
                }
            }

            // Wait for all downloads
            jobs.forEach { job ->
                try {
                    job.await()
                } catch (e: CancellationException) {
                    println("⚠️ Job await caught CancellationException: ${e.message}")
                } catch (e: Exception) {
                    println("⚠️ Job await caught unexpected exception: ${e::class.simpleName}: ${e.message}")
                }
            }
        }

        val finalProgress = _syncProgress.value
        println("✅ All chart downloads complete")
        println("   Successfully synced: ${finalProgress?.successCount ?: 0}")
        println("   Failed: ${finalProgress?.failedCount ?: 0}")

        // Clean up orphaned charts
        cleanupOrphanedCharts(chartEntries)

        val newFailures = finalProgress?.failedCharts ?: emptyList()
        println("📊 ChartSyncManager.synchronizeCharts() - Complete")
        return ChartSyncResult(
            attemptedCount = chartsNeedingUpdate.size,
            newFailures = newFailures
        )
    }

    private fun updateChartState(chartId: String, state: ChartSyncState, chartName: String? = null) {
        val current = _syncProgress.value ?: return
        _syncProgress.value = current.copy(
            chartStates = current.chartStates + (chartId to state),
            currentChartName = chartName ?: current.currentChartName
        )
    }

    /**
     * Clears the sync progress state.
     * Called when sync is complete and UI should show normal state.
     */
    fun clearSyncProgress() {
        _syncProgress.value = null
    }

    private fun fileKeyToChartId(fileKey: String): String {
        return fileKey
            .removePrefix("dev/")
            .removePrefix("prod/")
            .removeSuffix(".json")
            .replace("/", "_")
    }

    private fun needsUpdate(fileKey: String, entry: RegistryEntry): Boolean {
        val chartId = fileKeyToChartId(fileKey)
        val cached = chartCache.getChartData(chartId)

        if (cached == null) {
            println("🔍 Chart $chartId: No cached data, needs update")
            return true
        }

        val needsUpdate = entry.updatedAt > cached.lastUpdated
        println("🔍 Chart $chartId:")
        println("   Registry timestamp: ${entry.updatedAt}")
        println("   Cached timestamp:   ${cached.lastUpdated}")
        println("   Needs update: $needsUpdate")

        return needsUpdate
    }

    private suspend fun downloadAndCacheChart(fileKey: String, entry: RegistryEntry) {
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
            val rawJson = httpClient.get(chartDataUrl).bodyAsText()
            val requestDurationMs = (Clock.System.now() - startTime).toDouble(DurationUnit.MILLISECONDS)

            println("   Request duration: ${requestDurationMs.toLong()}ms")

            val jsonElement = json.parseToJsonElement(rawJson)
            val vizTypeString = jsonElement.jsonObject["visualizationType"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Chart JSON missing visualizationType field")

            val vizType = VizType.valueOf(vizTypeString)
            println("   Visualization type: $vizType")

            // Deserialize based on visualization type (validates JSON structure)
            when (vizType) {
                VizType.SCATTER_PLOT -> json.decodeFromString<ScatterPlotVisualization>(rawJson)
                VizType.BAR_GRAPH -> json.decodeFromString<BarGraphVisualization>(rawJson)
                VizType.LINE_CHART -> json.decodeFromString<LineChartVisualization>(rawJson)
                VizType.TABLE -> json.decodeFromString<TableVisualization>(rawJson)
                VizType.MATCHUP -> json.decodeFromString<MatchupVisualization>(rawJson)
                VizType.MATCHUP_V2 -> json.decodeFromString<MatchupV2Visualization>(rawJson)
                VizType.NBA_MATCHUP -> json.decodeFromString<NBAMatchupVisualization>(rawJson)
                VizType.NHL_MATCHUP -> json.decodeFromString<NHLMatchupVisualization>(rawJson)
                VizType.MLB_MATCHUP -> json.decodeFromString<MLBMatchupVisualization>(rawJson)
                VizType.MLB_TEAM_REPORT_CARD -> json.decodeFromString<MLBTeamReportCardVisualization>(rawJson)
                VizType.CBB_MATCHUP -> json.decodeFromString<CBBMatchupVisualization>(rawJson)
                VizType.NCAA_BRACKET -> json.decodeFromString<NCAABracketVisualization>(rawJson)
                VizType.NBA_PLAYOFF_BRACKET -> json.decodeFromString<NBAPlayoffBracketVisualization>(rawJson)
                VizType.NHL_PLAYOFF_BRACKET -> json.decodeFromString<NHLPlayoffBracketVisualization>(rawJson)
                VizType.HELLO_WORLD -> json.decodeFromString<HelloWorldVisualization>(rawJson)
            }

            val chartId = fileKeyToChartId(fileKey)
            val existingCached = chartCache.getChartData(chartId)

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

            println("💾 Caching chart $chartId with timestamp: ${entry.updatedAt}")

            // SQLite write is atomic - no verification retry needed!
            chartCache.saveChartData(chartId, cachedData)
            failedCharts.remove(chartId)
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

    private fun cleanupOrphanedCharts(registryEntries: Map<String, RegistryEntry>) {
        println("═══════════════════════════════════════════════════════")
        println("🧹 CLEANUP: Starting orphaned charts cleanup")
        println("═══════════════════════════════════════════════════════")

        val validChartIds = registryEntries.keys.map { fileKeyToChartId(it) }.toSet()
        val orphanedIds = chartCache.deleteOrphanedCharts(validChartIds)

        if (orphanedIds.isEmpty()) {
            println("✅ CLEANUP: No orphaned charts found")
        } else {
            println("✅ CLEANUP: Removed ${orphanedIds.size} orphaned chart(s):")
            orphanedIds.forEach { println("   - $it") }
        }

        println("═══════════════════════════════════════════════════════")
    }

    // ========== Registry Building ==========

    /**
     * Builds a Registry from server entries with cached data.
     */
    fun buildRegistryFromEntries(entries: Map<String, RegistryEntry>): Registry? {
        val chartEntries = entries.filter { (_, entry) -> entry.isChart && !entry.isSystem }
        if (chartEntries.isEmpty()) return null

        val charts = chartEntries.mapNotNull { (fileKey, entry) ->
            val chartId = fileKeyToChartId(fileKey)
            val metadata = chartCache.getChartMetadata(chartId)
            if (metadata != null) {
                buildChartDefinitionFromMetadata(chartId, entry, metadata)
            } else {
                buildPlaceholderChartDefinition(chartId, entry)
            }
        }

        if (charts.isEmpty()) return null

        return Registry(
            version = "2.0",
            lastUpdated = Clock.System.now(),
            charts = charts
        )
    }

    /**
     * Builds ChartDefinition from cached chart data.
     */
    fun buildChartDefinition(chartId: String, cached: CachedChartData): ChartDefinition? {
        val vizData = cached.deserialize()

        return ChartDefinition(
            id = chartId,
            sport = Sport.valueOf(vizData.sport),
            title = vizData.title,
            subtitle = vizData.subtitle,
            lastUpdated = cached.lastUpdated,
            visualizationType = cached.visualizationType,
            url = "",
            interval = cached.interval,
            cachedAt = cached.cachedAt,
            viewed = cached.viewed,
            tags = vizData.tags,
            sortOrder = vizData.sortOrder
        )
    }

    private fun buildChartDefinitionFromMetadata(
        chartId: String,
        entry: RegistryEntry,
        metadata: CachedChartMetadata
    ): ChartDefinition? {
        val sport = extractSportFromChartId(chartId) ?: return null
        return ChartDefinition(
            id = chartId,
            sport = sport,
            title = entry.title,
            subtitle = metadata.subtitle,
            lastUpdated = metadata.lastUpdated,
            visualizationType = metadata.visualizationType,
            url = "",
            interval = metadata.interval ?: entry.interval,
            cachedAt = metadata.cachedAt,
            viewed = metadata.viewed,
            tags = null,
            sortOrder = null
        )
    }

    private fun buildPlaceholderChartDefinition(chartId: String, entry: RegistryEntry): ChartDefinition? {
        val sport = extractSportFromChartId(chartId) ?: return null
        return ChartDefinition(
            id = chartId,
            sport = sport,
            title = entry.title,
            subtitle = "",
            lastUpdated = entry.updatedAt,
            visualizationType = VizType.HELLO_WORLD,
            url = "",
            interval = entry.interval,
            cachedAt = null,
            viewed = true, // suppress unread indicator until real data arrives
            tags = null,
            sortOrder = null
        )
    }

    private fun extractSportFromChartId(chartId: String): Sport? {
        val prefix = chartId.substringBefore("__", missingDelimiterValue = "")
        if (prefix.isEmpty()) return null
        return Sport.entries.firstOrNull { it.name.equals(prefix, ignoreCase = true) }
    }

    // ========== Convenience Methods ==========

    fun getCachedChartIds(): List<String> = chartCache.getAllChartIds()

    fun getCachedChartData(chartId: String): CachedChartData? = chartCache.getChartData(chartId)

    fun hasChartData(chartId: String): Boolean = chartCache.hasChartData(chartId)

    fun getChartCacheTime(chartId: String): Instant? = chartCache.getChartMetadata(chartId)?.cachedAt

    fun estimateCacheSize(): Long = chartCache.estimateTotalCacheSize()

    suspend fun clearAllCache() {
        failedCharts.clear()
        chartCache.clearAllChartData()
    }

    fun markChartAsViewed(chartId: String): Boolean = chartCache.markChartAsViewed(chartId)

    fun markAllChartsAsViewed(): Int = chartCache.markAllChartsAsViewed()

    // ========== Topics Methods ==========

    suspend fun synchronizeTopics(registryEntries: Map<String, RegistryEntry>) {
        println("📰 ChartSyncManager.synchronizeTopics() - Starting")

        val topicsEntries = registryEntries.filter { (_, entry) -> entry.isTopics }
        println("   Topics entries found: ${topicsEntries.size}")

        if (topicsEntries.isEmpty()) {
            println("   No topics entries in registry, skipping")
            return
        }

        topicsEntries.forEach { (fileKey, entry) ->
            println("   Processing: $fileKey (${entry.title})")

            if (!topicsRepository.needsUpdate(entry.updatedAt)) {
                println("   ✅ Topics already up to date, skipping download")
                return@forEach
            }

            try {
                val topicsUrl = "${RegistryApi.BASE_URL}/$fileKey"
                println("   🌐 Fetching topics from: $topicsUrl")

                val startTime = Clock.System.now()
                val rawJson = httpClient.get(topicsUrl).bodyAsText()
                val requestDurationMs = (Clock.System.now() - startTime).toDouble(DurationUnit.MILLISECONDS)

                println("   Request duration: ${requestDurationMs.toLong()}ms")

                val topicsResponse = json.decodeFromString<TopicsResponse>(rawJson)
                println("   ✅ Parsed topics: ${topicsResponse.topics.size} topics")

                topicsRepository.saveTopics(topicsResponse, entry.updatedAt)
                topicsRepository.resetViewed()
                topicsRepository.clearCollapsedAndReadState()
                println("   ✅ Topics saved to cache (marked as unviewed, collapsed state cleared)")
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

        println("📰 ChartSyncManager.synchronizeTopics() - Complete")
    }

    fun getCachedTopics(): TopicsResponse? = topicsRepository.getTopics()

    fun getTopicsUpdatedAt(): Instant? = topicsRepository.getUpdatedAt()

    fun clearTopicsCache() {
        println("🗑️ ChartSyncManager.clearTopicsCache()")
        topicsRepository.clear()
    }

    fun hasTopicsBeenViewed(): Boolean = topicsRepository.hasBeenViewed()

    fun markTopicsAsViewed() = topicsRepository.markAsViewed()

    fun getCollapsedIndices(): Set<Int> {
        val topics = topicsRepository.getTopics() ?: return emptySet()
        return topicsRepository.getCollapsedIndices(topics.date)
    }

    fun saveCollapsedIndices(indices: Set<Int>) {
        val topics = topicsRepository.getTopics() ?: return
        topicsRepository.saveCollapsedIndices(indices, topics.date)
    }

    fun getReadIndices(): Set<Int> {
        val topics = topicsRepository.getTopics() ?: return emptySet()
        return topicsRepository.getReadIndices(topics.date)
    }

    fun saveReadIndices(indices: Set<Int>) {
        val topics = topicsRepository.getTopics() ?: return
        topicsRepository.saveReadIndices(indices, topics.date)
    }

    fun saveTopicsFontSize(size: Float) = topicsRepository.saveFontSize(size)

    fun getTopicsFontSize(): Float? = topicsRepository.getFontSize()

    fun areAllNarrativesRead(): Boolean {
        val topics = topicsRepository.getTopics() ?: return true
        if (topics.topics.isEmpty()) return true
        val readIndices = topicsRepository.getReadIndices(topics.date)
        return readIndices.size >= topics.topics.size
    }

    /**
     * Marks all charts and topics as read.
     */
    fun markAllAsRead(): Int {
        val chartsMarked = chartCache.markAllChartsAsViewed()
        topicsRepository.markAsViewed()
        println("✅ Marked $chartsMarked chart(s) and topics as read")
        return chartsMarked
    }
}
