package com.joebad.fastbreak.ui.container

import com.joebad.fastbreak.config.AppConfig
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.data.model.RegistryEntry
import com.joebad.fastbreak.data.repository.ChartCache
import com.joebad.fastbreak.domain.registry.ChartSyncManager
import com.joebad.fastbreak.domain.registry.ChartSyncProgress
import com.joebad.fastbreak.domain.registry.ReleaseIdCheckResult
import com.joebad.fastbreak.domain.registry.ReleaseIdChecker
import com.joebad.fastbreak.domain.registry.RegistryManager
import com.joebad.fastbreak.platform.NetworkPermissionChecker
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

/**
 * Orbit MVI Container for registry management.
 * Handles registry loading, refreshing, and chart data synchronization.
 */
class RegistryContainer(
    private val registryManager: RegistryManager,
    private val chartSyncManager: ChartSyncManager,
    private val chartCache: ChartCache,
    private val scope: CoroutineScope,
    private val networkPermissionChecker: NetworkPermissionChecker = NetworkPermissionChecker(),
    private val releaseIdChecker: ReleaseIdChecker = ReleaseIdChecker(),
    val pinnedTeamsContainer: PinnedTeamsContainer? = null  // Optional dependency
) : ContainerHost<RegistryState, RegistrySideEffect> {

    override val container: Container<RegistryState, RegistrySideEffect> =
        scope.container(RegistryState())

    init {
        // Load initial state from cache without making network requests
        println("═══════════════════════════════════════════════════════")
        println("🚀 RegistryContainer.init - Loading from cache")
        println("═══════════════════════════════════════════════════════")
        intent {
            println("   📖 Getting cached registry entries...")
            val cachedEntries = registryManager.getCachedRegistryEntries()
            println("   📖 Building registry from cache...")
            val registry = buildRegistry(cachedEntries)
            println("   📝 Updating state with cached data")
            println("   - registryEntries: ${cachedEntries?.size ?: 0} entries")
            println("   - registry: ${registry?.charts?.size ?: 0} charts")
            // Also load topics viewed state
            val topicsViewed = chartSyncManager.hasTopicsBeenViewed()
            println("   - topicsViewed: $topicsViewed")
            reduce {
                state.copy(
                    registryEntries = cachedEntries,
                    registry = registry,
                    topicsViewed = topicsViewed
                )
            }
            // Now that registry is in state, load diagnostics
            println("   📊 Loading diagnostics...")
            reduce {
                state.copy(diagnostics = loadDiagnostics())
            }
            println("   ✅ RegistryContainer.init complete")
        }
    }

    /**
     * Builds a Registry for UI display.
     *
     * Prefers [entries] (the authoritative server list) when available — every
     * chart entry produces a Registry.charts item, with cached chart data
     * folded in where present and a placeholder otherwise. This makes the UI's
     * chart list a function of "what the server says exists", not "what
     * happens to be in the on-disk index right now", so a partial cache or
     * inconsistent index can no longer make charts disappear.
     *
     * Falls back to cache-only reconstruction when entries aren't available
     * (e.g. the init path before any successful refresh).
     */
    private fun buildRegistry(entries: Map<String, RegistryEntry>?): Registry? {
        if (entries != null) {
            return chartSyncManager.buildRegistryFromEntries(entries)
        }

        val chartIds = chartSyncManager.getCachedChartIds()
        if (chartIds.isEmpty()) return null

        val charts = chartIds.mapNotNull { chartId ->
            val cached = chartSyncManager.getCachedChartData(chartId) ?: return@mapNotNull null
            chartSyncManager.buildChartDefinition(chartId, cached)
        }

        if (charts.isEmpty()) return null

        return Registry(
            version = "2.0",
            lastUpdated = Clock.System.now(),
            charts = charts
        )
    }

    // Note: We don't auto-load in init to avoid triggering network requests
    // before iOS local network permission is granted. Call loadRegistry()
    // explicitly when ready (e.g., when HomeScreen first displays).

    /**
     * Checks the network permission status and updates state.
     * Should be called before making network requests.
     */
    fun checkNetworkPermission() = intent {
        val status = networkPermissionChecker.checkPermission()
        reduce {
            state.copy(networkPermissionStatus = status)
        }
    }

    /**
     * Requests network permission and updates state with result.
     * On iOS, triggers the local network permission dialog.
     */
    fun requestNetworkPermission() = intent {
        val status = networkPermissionChecker.requestPermission()
        reduce {
            state.copy(networkPermissionStatus = status)
        }
    }

    /**
     * Clears the sync progress state.
     * Used when navigating away from home screen to prevent showing completed state on return.
     */
    fun clearSyncProgress() = intent {
        reduce {
            state.copy(syncProgress = null)
        }
    }

    /**
     * Marks a chart as viewed by the user.
     * Updates both the cache and the UI state.
     *
     * @param chartId The ID of the chart to mark as viewed
     */
    fun markChartAsViewed(chartId: String) = intent {
        // Mark as viewed in the cache
        chartSyncManager.markChartAsViewed(chartId)

        // Update the state to reflect the change immediately
        val currentRegistry = state.registry ?: return@intent
        val updatedCharts = currentRegistry.charts.map { chart ->
            if (chart.id == chartId) {
                chart.copy(viewed = true)
            } else {
                chart
            }
        }
        val updatedRegistry = currentRegistry.copy(charts = updatedCharts)

        reduce {
            state.copy(registry = updatedRegistry)
        }
    }

    /**
     * Loads the registry with 12-hour automatic update check.
     * Also triggers chart data synchronization and team roster download after loading.
     * Auto-syncs if: 1) no cache present, or 2) cache is > 24 hours old.
     */
    fun loadRegistry() = intent {
        // Check if auto-sync is needed
        val chartCount = chartCache.getCachedChartCount()
        val latestCacheTime = chartSyncManager.getCachedChartIds()
            .mapNotNull { chartSyncManager.getChartCacheTime(it) }
            .maxOrNull()
        val cacheAgeHours = latestCacheTime?.let {
            (Clock.System.now() - it).inWholeHours
        }

        val shouldAutoSync = chartCount == 0 || (cacheAgeHours != null && cacheAgeHours > 24)

        if (shouldAutoSync) {
            val reason = if (chartCount == 0) "no cache" else "cache ${cacheAgeHours}h old"
            println("🔄 Auto-sync triggered: $reason")
            refreshRegistry()
            return@intent
        }

        reduce {
            state.copy(
                isLoading = true,
                error = null,
                syncProgress = null,
                diagnostics = state.diagnostics.copy(
                    lastError = null,
                    isSyncing = true,
                    failedCharts = emptyList()
                )
            )
        }

        // Download team rosters immediately (don't wait for registry)
        println("🏈 Downloading team rosters on startup...")
        pinnedTeamsContainer?.downloadTeamRosters()

        registryManager.checkAndUpdateRegistry()
            .onSuccess { entries ->
                // Seed the registry with one entry per server-known chart (placeholders
                // for ones not yet cached) so the UI can render the full list with
                // per-chart spinners during sync instead of waiting for buildRegistry
                // to fire on the final isComplete emit.
                val seededRegistry = buildRegistry(entries)
                reduce {
                    state.copy(
                        registryEntries = entries,
                        registry = seededRegistry,
                        isLoading = false,
                        diagnostics = loadDiagnostics().copy(
                            isSyncing = true  // Keep syncing state active for chart data sync
                        )
                    )
                }

                // Check release ID compatibility before syncing charts
                val releaseIdResult = releaseIdChecker.checkReleaseId(entries)
                when (releaseIdResult) {
                    is ReleaseIdCheckResult.UpdateRequired -> {
                        println("⚠️ App update required: client=${releaseIdResult.clientReleaseId}, server=${releaseIdResult.serverReleaseId}")
                        reduce {
                            state.copy(
                                updateRequired = true,
                                serverReleaseId = releaseIdResult.serverReleaseId,
                                isSyncing = false,
                                syncProgress = null,
                                diagnostics = state.diagnostics.copy(isSyncing = false)
                            )
                        }
                        postSideEffect(RegistrySideEffect.UpdateRequired(
                            serverReleaseId = releaseIdResult.serverReleaseId,
                            clientReleaseId = releaseIdResult.clientReleaseId
                        ))
                        // Skip chart sync - user needs to update the app
                        return@onSuccess
                    }
                    is ReleaseIdCheckResult.Compatible,
                    is ReleaseIdCheckResult.DevModeBypass -> {
                        // Continue with chart sync
                    }
                }

                // Sync chart data and topics after loading registry entries (await completion)
                try {
                    syncChartData(entries)
                    // Also sync topics data
                    chartSyncManager.synchronizeTopics(entries)

                    // Update progress to show topics synced and clear sync state
                    // Also update topicsViewed state (may have been reset if new topics were downloaded)
                    reduce {
                        state.copy(
                            syncProgress = null,
                            isSyncing = false,
                            topicsViewed = chartSyncManager.hasTopicsBeenViewed(),
                            diagnostics = state.diagnostics.copy(isSyncing = false)
                        )
                    }

                    // Show toast if any charts failed to sync
                    if (state.diagnostics.failedCharts.isNotEmpty()) {
                        val count = state.diagnostics.failedCharts.size
                        postSideEffect(RegistrySideEffect.ShowError(
                            "Failed to sync $count chart${if (count > 1) "s" else ""}"
                        ))
                    }
                } catch (e: Exception) {
                    println("❌ Chart synchronization failed: ${e.message}")
                    // Ensure isSyncing is cleared even on exception
                    reduce {
                        state.copy(
                            isSyncing = false,
                            syncProgress = null,
                            diagnostics = state.diagnostics.copy(
                                isSyncing = false,
                                lastError = "Chart sync failed: ${e.message}"
                            )
                        )
                    }
                }
            }
            .onFailure { error ->
                val errorMsg = error.message ?: "Unknown error"
                println("LoadRegistry failed with error: $errorMsg")

                // Try to fall back to cached registry entries
                val cachedEntries = registryManager.getCachedRegistryEntries()

                if (cachedEntries != null) {
                    println("📦 Falling back to cached registry with ${cachedEntries.size} entries")

                    val seededRegistry = buildRegistry(cachedEntries)
                    reduce {
                        state.copy(
                            registryEntries = cachedEntries,
                            registry = seededRegistry,
                            isLoading = false,
                            diagnostics = loadDiagnostics().copy(
                                isSyncing = true,  // Keep syncing state active for chart data sync
                                failedSyncs = container.stateFlow.value.diagnostics.failedSyncs + 1,
                                lastError = errorMsg
                            )
                        )
                    }

                    // Show error toast even though we have cached data
                    postSideEffect(RegistrySideEffect.ShowError(errorMsg))

                    // Continue with chart data sync using cached entries
                    try {
                        syncChartData(cachedEntries)
                        // Also sync topics data
                        chartSyncManager.synchronizeTopics(cachedEntries)

                        // Clear sync state and update topicsViewed
                        reduce {
                            state.copy(
                                syncProgress = null,
                                isSyncing = false,
                                topicsViewed = chartSyncManager.hasTopicsBeenViewed(),
                                diagnostics = state.diagnostics.copy(isSyncing = false)
                            )
                        }

                        // Show toast if any charts failed to sync
                        if (state.diagnostics.failedCharts.isNotEmpty()) {
                            val count = state.diagnostics.failedCharts.size
                            postSideEffect(RegistrySideEffect.ShowError(
                                "Failed to sync $count chart${if (count > 1) "s" else ""}"
                            ))
                        }
                    } catch (e: Exception) {
                        println("❌ Chart synchronization failed: ${e.message}")
                        // Ensure isSyncing is cleared even on exception
                        reduce {
                            state.copy(
                                isSyncing = false,
                                syncProgress = null,
                                diagnostics = state.diagnostics.copy(
                                    isSyncing = false,
                                    lastError = "Chart sync failed: ${e.message}"
                                )
                            )
                        }
                    }
                } else {
                    println("❌ No cached registry available")

                    val diagnostics = container.stateFlow.value.diagnostics.copy(
                        isSyncing = false,
                        failedSyncs = container.stateFlow.value.diagnostics.failedSyncs + 1,
                        lastError = errorMsg
                    )

                    reduce {
                        state.copy(
                            isLoading = false,
                            error = errorMsg,
                            diagnostics = diagnostics
                        )
                    }
                    postSideEffect(RegistrySideEffect.ShowError(errorMsg))
                }
            }
    }

    /**
     * Forces a registry refresh regardless of staleness.
     * Prevents concurrent refresh operations.
     */
    fun refreshRegistry() = intent {
        println("🔄 REFRESH - Starting refresh flow")

        // Guard: prevent concurrent refresh operations
        if (container.stateFlow.value.isSyncing) {
            println("⚠️  Refresh already in progress, ignoring duplicate request")
            return@intent
        }

        // Set initial sync state - syncProgress with total=1 keeps charts disabled
        // until sync completes (isChartReady returns false when chart not in syncedChartIds)
        reduce {
            state.copy(
                isSyncing = true,
                error = null,
                syncProgress = ChartSyncProgress(
                    chartStates = emptyMap(),
                    totalToSync = 1,  // Non-zero so charts stay disabled
                    currentChartName = ""  // Empty - "Syncing charts..." is already shown above
                ),
                diagnostics = state.diagnostics.copy(
                    lastError = null,
                    isSyncing = true,
                    failedCharts = emptyList()
                )
            )
        }

        val refreshResult = registryManager.forceRefreshRegistry()

        when {
            refreshResult.isSuccess -> {
                val entries = refreshResult.getOrThrow()
                println("✅ forceRefreshRegistry() SUCCESS - Received ${entries.size} entries")

                // Seed the registry with one entry per server-known chart (placeholders
                // for ones not yet cached) so the UI can render the full list with
                // per-chart spinners during sync instead of waiting for buildRegistry
                // to fire on the final isComplete emit.
                val seededRegistry = buildRegistry(entries)
                reduce {
                    state.copy(
                        registryEntries = entries,
                        registry = seededRegistry,
                        diagnostics = loadDiagnostics().copy(
                            isSyncing = true  // Keep syncing state active for chart data sync
                        )
                    )
                }

                // Check release ID compatibility before syncing charts
                val releaseIdResult = releaseIdChecker.checkReleaseId(entries)
                when (releaseIdResult) {
                    is ReleaseIdCheckResult.UpdateRequired -> {
                        println("⚠️ App update required: client=${releaseIdResult.clientReleaseId}, server=${releaseIdResult.serverReleaseId}")
                        reduce {
                            state.copy(
                                updateRequired = true,
                                serverReleaseId = releaseIdResult.serverReleaseId,
                                isSyncing = false,
                                syncProgress = null,
                                diagnostics = state.diagnostics.copy(isSyncing = false)
                            )
                        }
                        postSideEffect(RegistrySideEffect.UpdateRequired(
                            serverReleaseId = releaseIdResult.serverReleaseId,
                            clientReleaseId = releaseIdResult.clientReleaseId
                        ))
                        // Skip chart sync - user needs to update the app
                        return@intent
                    }
                    is ReleaseIdCheckResult.Compatible,
                    is ReleaseIdCheckResult.DevModeBypass -> {
                        // Continue with chart sync
                    }
                }

                // Sync chart data after refresh
                try {
                    syncChartData(entries)

                    // Sync topics - only downloads if server has newer data
                    // (synchronizeTopics compares timestamps internally)
                    val topicsUpdatedAtBefore = chartSyncManager.getTopicsUpdatedAt()
                    chartSyncManager.synchronizeTopics(entries)
                    val topicsUpdatedAtAfter = chartSyncManager.getTopicsUpdatedAt()
                    val topicsWereUpdated = topicsUpdatedAtAfter != topicsUpdatedAtBefore

                    // Show topics sync result briefly if topics were updated
                    if (topicsWereUpdated) {
                        reduce {
                            state.copy(
                                syncProgress = ChartSyncProgress(
                                    chartStates = emptyMap(),
                                    totalToSync = 0,
                                    currentChartName = "",
                                    topicsSynced = true
                                ),
                                isSyncing = true
                            )
                        }
                    }

                    // All done - clear sync state and update topicsViewed
                    reduce {
                        state.copy(
                            syncProgress = null,
                            isSyncing = false,
                            topicsViewed = chartSyncManager.hasTopicsBeenViewed(),
                            diagnostics = state.diagnostics.copy(isSyncing = false)
                        )
                    }

                    // Show toast if any charts failed to sync
                    if (state.diagnostics.failedCharts.isNotEmpty()) {
                        val count = state.diagnostics.failedCharts.size
                        postSideEffect(RegistrySideEffect.ShowError(
                            "Failed to sync $count chart${if (count > 1) "s" else ""}"
                        ))
                    }

                    // Download team rosters in background
                    pinnedTeamsContainer?.downloadTeamRosters()
                } catch (e: Exception) {
                    println("❌ Chart synchronization failed: ${e.message}")
                    // Ensure isSyncing is cleared even on exception
                    reduce {
                        state.copy(
                            isSyncing = false,
                            syncProgress = null,
                            diagnostics = state.diagnostics.copy(
                                isSyncing = false,
                                lastError = "Chart sync failed: ${e.message}"
                            )
                        )
                    }
                }
            }
            refreshResult.isFailure -> {
                val error = refreshResult.exceptionOrNull()
                val errorMsg = error?.message ?: "Unknown error"
                println("❌ forceRefreshRegistry() FAILED with error: $errorMsg")
                println("Stack trace: ${error?.stackTraceToString()}")

                // Try to fall back to cached registry entries
                val cachedEntries = registryManager.getCachedRegistryEntries()

                if (cachedEntries != null) {
                    println("📦 Falling back to cached registry with ${cachedEntries.size} entries")

                    val seededRegistry = buildRegistry(cachedEntries)
                    reduce {
                        state.copy(
                            registryEntries = cachedEntries,
                            registry = seededRegistry,
                            diagnostics = loadDiagnostics().copy(
                                isSyncing = true,  // Keep syncing state active for chart data sync
                                failedSyncs = container.stateFlow.value.diagnostics.failedSyncs + 1,
                                lastError = errorMsg
                            )
                        )
                    }

                    // Show error toast even though we have cached data
                    postSideEffect(RegistrySideEffect.ShowError(errorMsg))

                    // Continue with chart data sync using cached entries
                    try {
                        syncChartData(cachedEntries)

                        val topicsUpdatedAtBefore = chartSyncManager.getTopicsUpdatedAt()
                        chartSyncManager.synchronizeTopics(cachedEntries)
                        val topicsUpdatedAtAfter = chartSyncManager.getTopicsUpdatedAt()
                        val topicsWereUpdated = topicsUpdatedAtAfter != topicsUpdatedAtBefore

                        if (topicsWereUpdated) {
                            reduce {
                                state.copy(
                                    syncProgress = ChartSyncProgress(
                                        chartStates = emptyMap(),
                                        totalToSync = 0,
                                        currentChartName = "",
                                        topicsSynced = true
                                    ),
                                    isSyncing = true
                                )
                            }
                        }

                        // All done - clear sync state and update topicsViewed
                        reduce {
                            state.copy(
                                syncProgress = null,
                                isSyncing = false,
                                topicsViewed = chartSyncManager.hasTopicsBeenViewed(),
                                diagnostics = state.diagnostics.copy(isSyncing = false)
                            )
                        }

                        // Show toast if any charts failed to sync
                        if (state.diagnostics.failedCharts.isNotEmpty()) {
                            val count = state.diagnostics.failedCharts.size
                            postSideEffect(RegistrySideEffect.ShowError(
                                "Failed to sync $count chart${if (count > 1) "s" else ""}"
                            ))
                        }

                        pinnedTeamsContainer?.downloadTeamRosters()
                    } catch (e: Exception) {
                        println("❌ Chart synchronization failed: ${e.message}")
                        // Ensure isSyncing is cleared even on exception
                        reduce {
                            state.copy(
                                isSyncing = false,
                                syncProgress = null,
                                diagnostics = state.diagnostics.copy(
                                    isSyncing = false,
                                    lastError = "Chart sync failed: ${e.message}"
                                )
                            )
                        }
                    }
                } else {
                    reduce {
                        state.copy(
                            isSyncing = false,
                            syncProgress = null,
                            error = errorMsg,
                            diagnostics = state.diagnostics.copy(
                                isSyncing = false,
                                failedSyncs = state.diagnostics.failedSyncs + 1,
                                lastError = errorMsg
                            )
                        )
                    }
                    postSideEffect(RegistrySideEffect.ShowError(errorMsg))
                }
            }
        }
    }

    /**
     * Synchronizes chart data based on registry entries.
     * Compares timestamps and downloads charts that need updating.
     * Builds Registry from cached data after sync completes.
     * Keeps isSyncing = true - caller is responsible for clearing sync state.
     */
    private suspend fun syncChartData(entries: Map<String, RegistryEntry>) {
        var progressCollectorJob: Job? = null
        try {
            // Launch a coroutine to collect sync progress updates from StateFlow
            progressCollectorJob = scope.launch {
                chartSyncManager.syncProgress.collect { progress ->
                    if (progress == null) return@collect

                    // Skip showing progress if nothing needs syncing (everything cached)
                    // Don't launch an intent here - let the main flow handle state updates
                    // after synchronizeCharts() returns. Launching intents from the collector
                    // creates a race condition when the collector is cancelled.
                    if (progress.totalToSync == 0) {
                        return@collect
                    }

                    // Rebuild registry on every emit so each chart's completion
                    // immediately propagates to the UI (placeholder → real data)
                    val registry = buildRegistry(entries)

                    if (progress.isComplete) {
                        // Capture failed charts before clearing syncProgress
                        val completedFailedCharts = progress.failedCharts

                        // Update state with registry - keep isSyncing true for caller
                        intent {
                            reduce {
                                state.copy(
                                    registry = registry,
                                    syncProgress = null,
                                    isSyncing = true,  // Caller clears this after topics sync
                                    lastSyncTime = Clock.System.now(),
                                    diagnostics = loadDiagnostics().copy(
                                        isSyncing = true,
                                        lastError = if (progress.hasFailures) {
                                            "Failed to sync ${progress.failedCount} charts"
                                        } else null,
                                        failedSyncs = if (progress.hasFailures) {
                                            container.stateFlow.value.diagnostics.failedSyncs + progress.failedCount
                                        } else container.stateFlow.value.diagnostics.failedSyncs,
                                        failedCharts = completedFailedCharts
                                    )
                                )
                            }
                            postSideEffect(RegistrySideEffect.SyncCompleted)
                        }.join()
                    } else {
                        // Normal progress update — also fold in the freshly-rebuilt
                        // registry so newly-cached charts become clickable without
                        // waiting for the final emit.
                        intent {
                            reduce {
                                state.copy(
                                    registry = registry,
                                    syncProgress = progress,
                                    isSyncing = true
                                )
                            }
                        }.join()
                    }
                }
            }

            // Call synchronizeCharts - this suspends until all downloads complete
            // While running, it updates the StateFlow which the collector above observes
            chartSyncManager.synchronizeCharts(entries)

            // Sync complete - cancel the collector
            progressCollectorJob.cancel()

            // Build final registry and update state
            val finalRegistry = buildRegistry(entries)
            val finalProgress = chartSyncManager.syncProgress.value

            intent {
                reduce {
                    state.copy(
                        registry = finalRegistry,
                        syncProgress = null,
                        isSyncing = true,  // Caller clears this after topics sync
                        lastSyncTime = Clock.System.now(),
                        diagnostics = loadDiagnostics().copy(
                            isSyncing = true,
                            lastError = if (finalProgress?.hasFailures == true) {
                                "Failed to sync ${finalProgress.failedCount} charts"
                            } else null,
                            failedSyncs = if (finalProgress?.hasFailures == true) {
                                container.stateFlow.value.diagnostics.failedSyncs + finalProgress.failedCount
                            } else container.stateFlow.value.diagnostics.failedSyncs,
                            failedCharts = finalProgress?.failedCharts ?: emptyList()
                        )
                    )
                }
                postSideEffect(RegistrySideEffect.SyncCompleted)
            }.join()

            // Clear sync progress in manager
            chartSyncManager.clearSyncProgress()

        } catch (e: Exception) {
            println("❌ syncChartData() - Exception during sync: ${e.message}")
            e.printStackTrace()
            progressCollectorJob?.cancel()
            // Ensure isSyncing is cleared even on exception
            intent {
                reduce {
                    state.copy(
                        isSyncing = false,
                        syncProgress = null,
                        diagnostics = state.diagnostics.copy(
                            isSyncing = false,
                            lastError = "Sync error: ${e.message}"
                        )
                    )
                }
            }.join()
        }
    }

    /**
     * Loads diagnostics information from repositories.
     */
    private fun loadDiagnostics(): DiagnosticsInfo {
        val currentState = container.stateFlow.value
        val metadata = registryManager.getMetadata()

        // Use registry entries count or cached chart count
        val chartCount = currentState.registryEntries?.size
            ?: currentState.registry?.charts?.size
            ?: 0

        // If no charts, show 0 bytes (cached data is orphaned/stale)
        val cacheSize = if (chartCount == 0) 0L else chartCache.estimateTotalCacheSize()

        // Get the most recent cache update time from all cached charts
        val lastCacheUpdate = if (chartCount == 0) {
            null  // No valid cache if registry is empty
        } else {
            chartSyncManager.getCachedChartIds()
                .mapNotNull { chartSyncManager.getChartCacheTime(it) }
                .maxOrNull()
        }

        return DiagnosticsInfo(
            lastRegistryFetch = metadata?.lastDownloadTime,
            lastCacheUpdate = lastCacheUpdate,
            cachedChartsCount = chartCount,
            registryVersion = metadata?.registryVersion,
            registryPrefix = if (AppConfig.DEV_MODE) "dev/" else "prod/",
            totalCacheSize = cacheSize,
            failedSyncs = currentState.diagnostics.failedSyncs,
            lastError = currentState.diagnostics.lastError,
            isStale = registryManager.isRegistryStale(),
            isSyncing = currentState.isSyncing,
            failedCharts = currentState.syncProgress?.failedCharts ?: currentState.diagnostics.failedCharts
        )
    }

    /**
     * Gets the cached topics data.
     *
     * @return The cached topics, or null if not found
     */
    fun getCachedTopics() = chartSyncManager.getCachedTopics()

    /**
     * Gets the timestamp when topics were last updated.
     *
     * @return The last update timestamp, or null if not found
     */
    fun getTopicsUpdatedAt() = chartSyncManager.getTopicsUpdatedAt()

    /**
     * Clears cached topics data to force a re-download on next sync.
     */
    fun clearTopicsCache() = chartSyncManager.clearTopicsCache()

    /**
     * Marks topics as viewed by the user.
     * Updates both the cache and the UI state.
     */
    fun markTopicsAsViewed() = intent {
        chartSyncManager.markTopicsAsViewed()
        reduce {
            state.copy(topicsViewed = true)
        }
    }

    /**
     * Checks if topics have been viewed.
     *
     * @return true if topics have been viewed
     */
    fun hasTopicsBeenViewed(): Boolean = chartSyncManager.hasTopicsBeenViewed()

    /**
     * Gets the collapsed narrative indices for the current topics.
     */
    fun getCollapsedIndices(): Set<Int> = chartSyncManager.getCollapsedIndices()

    /**
     * Saves the collapsed narrative indices for the current topics.
     */
    fun saveCollapsedIndices(indices: Set<Int>) = chartSyncManager.saveCollapsedIndices(indices)

    /**
     * Gets the set of narratives that have been read (collapsed at least once).
     */
    fun getReadIndices(): Set<Int> = chartSyncManager.getReadIndices()

    /**
     * Saves the set of narratives that have been read (collapsed at least once).
     * Also checks if all narratives have been read and updates topicsViewed state.
     */
    fun saveReadIndices(indices: Set<Int>) = intent {
        chartSyncManager.saveReadIndices(indices)
        // Check if all narratives have been read and update topicsViewed state
        if (chartSyncManager.areAllNarrativesRead() && !state.topicsViewed) {
            chartSyncManager.markTopicsAsViewed()
            reduce {
                state.copy(topicsViewed = true)
            }
        }
    }

    /**
     * Checks if all narratives have been read.
     */
    fun areAllNarrativesRead(): Boolean = chartSyncManager.areAllNarrativesRead()

    /**
     * Persists the user's preferred font size for the topics screen.
     */
    fun saveTopicsFontSize(size: Float) = chartSyncManager.saveTopicsFontSize(size)

    /**
     * Retrieves the persisted font size, or null if the user hasn't picked one.
     */
    fun getTopicsFontSize(): Float? = chartSyncManager.getTopicsFontSize()

    /**
     * Marks all charts and topics as read, clearing notification indicators.
     * Updates the UI state to reflect the change immediately.
     *
     * @return The number of charts that were marked as read
     */
    fun markAllAsRead() = intent {
        val chartsMarked = chartSyncManager.markAllAsRead()

        // Update all charts in the registry to viewed = true
        val currentRegistry = state.registry ?: return@intent
        val updatedCharts = currentRegistry.charts.map { chart ->
            chart.copy(viewed = true)
        }
        val updatedRegistry = currentRegistry.copy(charts = updatedCharts)

        reduce {
            state.copy(
                registry = updatedRegistry,
                topicsViewed = true
            )
        }

        println("✅ Marked $chartsMarked chart(s) and topics as read via RegistryContainer")
    }

    /**
     * Resets all cached chart data, topics, and registry.
     * This is a destructive operation that requires the user to re-sync all data.
     * Also clears pinned teams if available.
     */
    fun resetAllData() = intent {
        println("🗑️ RegistryContainer.resetAllData() - Clearing all cached data")

        // Clear chart data cache
        chartSyncManager.clearAllCache()
        println("   ✅ Chart data cache cleared")

        // Clear topics cache
        chartSyncManager.clearTopicsCache()
        println("   ✅ Topics cache cleared")

        // Clear registry metadata
        registryManager.clearRegistry()
        println("   ✅ Registry metadata cleared")

        // Clear pinned teams if container is available
        pinnedTeamsContainer?.clearAllPinnedTeams()
        println("   ✅ Pinned teams cleared")

        // Reset state to initial, preserving correct registry prefix
        reduce {
            RegistryState(
                diagnostics = DiagnosticsInfo(
                    registryPrefix = if (AppConfig.DEV_MODE) "dev/" else "prod/"
                )
            )
        }

        println("✅ All data reset complete")
    }
}
