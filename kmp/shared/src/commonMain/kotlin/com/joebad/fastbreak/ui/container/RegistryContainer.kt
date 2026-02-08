package com.joebad.fastbreak.ui.container

import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.data.model.RegistryEntry
import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.domain.registry.ChartDataSynchronizer
import com.joebad.fastbreak.domain.registry.RegistryManager
import com.joebad.fastbreak.platform.NetworkPermissionChecker
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import com.mohamedrejeb.calf.permissions.PermissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
    private val chartDataSynchronizer: ChartDataSynchronizer,
    private val chartDataRepository: ChartDataRepository,
    private val scope: CoroutineScope,
    private val networkPermissionChecker: NetworkPermissionChecker = NetworkPermissionChecker(),
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
            val registry = buildRegistryFromCache()
            println("   📝 Updating state with cached data")
            println("   - registryEntries: ${cachedEntries?.size ?: 0} entries")
            println("   - registry: ${registry?.charts?.size ?: 0} charts")
            reduce {
                state.copy(
                    registryEntries = cachedEntries,
                    registry = registry
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
     * Builds a Registry object from cached chart data.
     * Used to reconstruct the registry for UI display after sync.
     */
    private fun buildRegistryFromCache(): Registry? {
        println("═══════════════════════════════════════════════════════")
        println("🔨 BUILD REGISTRY: Starting buildRegistryFromCache()")
        println("═══════════════════════════════════════════════════════")

        val chartIds = chartDataSynchronizer.getCachedChartIds()
        println("📦 BUILD REGISTRY: Found ${chartIds.size} cached chart ID(s):")
        chartIds.forEach { println("   - $it") }

        if (chartIds.isEmpty()) {
            println("⚠️  BUILD REGISTRY: No cached charts found, returning null")
            println("═══════════════════════════════════════════════════════")
            return null
        }

        val charts = chartIds.mapNotNull { chartId ->
            val cached = chartDataSynchronizer.getCachedChartData(chartId)
            if (cached == null) {
                println("   ⚠️ BUILD REGISTRY: No cached data for chart: $chartId")
                return@mapNotNull null
            }
            val chartDef = chartDataSynchronizer.buildChartDefinition(chartId, cached)
            if (chartDef != null) {
                println("   ✅ BUILD REGISTRY: Built '${chartDef.title}' (id=${chartDef.id}, type=${chartDef.visualizationType})")
            } else {
                println("   ⚠️ BUILD REGISTRY: Failed to build ChartDefinition for: $chartId")
            }
            chartDef
        }

        if (charts.isEmpty()) {
            println("⚠️  BUILD REGISTRY: No valid charts built, returning null")
            println("═══════════════════════════════════════════════════════")
            return null
        }

        val registry = Registry(
            version = "2.0",
            lastUpdated = Clock.System.now(),
            charts = charts
        )
        println("✅ BUILD REGISTRY: Complete - built ${registry.charts.size} chart(s):")
        registry.charts.forEach { println("   - ${it.title} (id=${it.id})") }
        println("═══════════════════════════════════════════════════════")
        return registry
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
        chartDataSynchronizer.markChartAsViewed(chartId)

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
     */
    fun loadRegistry() = intent {
        reduce {
            state.copy(
                isLoading = true,
                error = null,
                syncProgress = null,
                diagnostics = state.diagnostics.copy(
                    lastError = null,
                    isSyncing = true
                )
            )
        }

        // Download team rosters immediately (don't wait for registry)
        println("🏈 Downloading team rosters on startup...")
        pinnedTeamsContainer?.downloadTeamRosters()

        registryManager.checkAndUpdateRegistry()
            .onSuccess { entries ->
                reduce {
                    state.copy(
                        registryEntries = entries,
                        isLoading = false,
                        diagnostics = loadDiagnostics().copy(
                            isSyncing = true  // Keep syncing state active for chart data sync
                        )
                    )
                }

                // Sync chart data and topics after loading registry entries (await completion)
                try {
                    syncChartData(entries)
                    // Also sync topics data
                    chartDataSynchronizer.synchronizeTopics(entries)

                    // Update progress to show topics synced and clear sync state
                    reduce {
                        state.copy(
                            syncProgress = null,
                            isSyncing = false,
                            diagnostics = state.diagnostics.copy(isSyncing = false)
                        )
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

                    reduce {
                        state.copy(
                            registryEntries = cachedEntries,
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
                        chartDataSynchronizer.synchronizeTopics(cachedEntries)

                        // Clear sync state
                        reduce {
                            state.copy(
                                syncProgress = null,
                                isSyncing = false,
                                diagnostics = state.diagnostics.copy(isSyncing = false)
                            )
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
                syncProgress = com.joebad.fastbreak.ui.diagnostics.SyncProgress(
                    current = 0,
                    total = 1,  // Non-zero so charts stay disabled
                    currentChart = "Syncing...",
                    syncedChartIds = emptySet(),
                    syncingChartIds = emptySet(),
                    failedCharts = emptyList()
                ),
                diagnostics = state.diagnostics.copy(
                    lastError = null,
                    isSyncing = true
                )
            )
        }

        val refreshResult = registryManager.forceRefreshRegistry()

        when {
            refreshResult.isSuccess -> {
                val entries = refreshResult.getOrThrow()
                println("✅ forceRefreshRegistry() SUCCESS - Received ${entries.size} entries")

                reduce {
                    state.copy(
                        registryEntries = entries,
                        diagnostics = loadDiagnostics().copy(
                            isSyncing = true  // Keep syncing state active for chart data sync
                        )
                    )
                }

                // Sync chart data after refresh
                try {
                    syncChartData(entries)

                    // Force refresh topics by clearing cache first
                    chartDataSynchronizer.clearTopicsCache()
                    val topicsUpdatedAtBefore = chartDataSynchronizer.getTopicsUpdatedAt()
                    chartDataSynchronizer.synchronizeTopics(entries)
                    val topicsUpdatedAtAfter = chartDataSynchronizer.getTopicsUpdatedAt()
                    val topicsWereUpdated = topicsUpdatedAtAfter != topicsUpdatedAtBefore

                    // Show topics sync result briefly if topics were updated
                    if (topicsWereUpdated) {
                        reduce {
                            state.copy(
                                syncProgress = com.joebad.fastbreak.ui.diagnostics.SyncProgress(
                                    current = 0,
                                    total = 0,
                                    currentChart = "",
                                    syncedChartIds = emptySet(),
                                    syncingChartIds = emptySet(),
                                    failedCharts = emptyList(),
                                    topicsSynced = true
                                ),
                                isSyncing = true
                            )
                        }
                    }

                    // All done - clear sync state
                    reduce {
                        state.copy(
                            syncProgress = null,
                            isSyncing = false,
                            diagnostics = state.diagnostics.copy(isSyncing = false)
                        )
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

                    reduce {
                        state.copy(
                            registryEntries = cachedEntries,
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

                        val topicsUpdatedAtBefore = chartDataSynchronizer.getTopicsUpdatedAt()
                        chartDataSynchronizer.synchronizeTopics(cachedEntries)
                        val topicsUpdatedAtAfter = chartDataSynchronizer.getTopicsUpdatedAt()
                        val topicsWereUpdated = topicsUpdatedAtAfter != topicsUpdatedAtBefore

                        if (topicsWereUpdated) {
                            reduce {
                                state.copy(
                                    syncProgress = com.joebad.fastbreak.ui.diagnostics.SyncProgress(
                                        current = 0,
                                        total = 0,
                                        currentChart = "",
                                        syncedChartIds = emptySet(),
                                        syncingChartIds = emptySet(),
                                        failedCharts = emptyList(),
                                        topicsSynced = true
                                    ),
                                    isSyncing = true
                                )
                            }
                        }

                        // All done - clear sync state
                        reduce {
                            state.copy(
                                syncProgress = null,
                                isSyncing = false,
                                diagnostics = state.diagnostics.copy(isSyncing = false)
                            )
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
        try {
            chartDataSynchronizer.synchronizeCharts(entries).collect { progress ->
            // Skip showing progress if nothing needs syncing (everything cached)
            if (progress.total == 0) {
                // Everything is already cached, build registry from cache
                // Keep isSyncing = true - caller will clear after topics sync
                val registry = buildRegistryFromCache()
                intent {
                    reduce {
                        state.copy(
                            registry = registry,
                            isSyncing = true,  // Keep true - caller handles final state
                            lastSyncTime = Clock.System.now(),
                            syncProgress = null
                        )
                    }
                }.join()
                return@collect
            }

            if (progress.isComplete) {
                // Build registry from cached chart data
                val registry = buildRegistryFromCache()

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
                                    "Failed to sync ${progress.failedCharts.size} charts"
                                } else null,
                                failedSyncs = if (progress.hasFailures) {
                                    container.stateFlow.value.diagnostics.failedSyncs + progress.failedCharts.size
                                } else container.stateFlow.value.diagnostics.failedSyncs
                            )
                        )
                    }
                    postSideEffect(RegistrySideEffect.SyncCompleted)
                }.join()
            } else {
                // Normal progress update
                intent {
                    reduce {
                        state.copy(
                            syncProgress = progress,
                            isSyncing = true
                        )
                    }
                }.join()
            }
        }
        } catch (e: Exception) {
            println("❌ syncChartData() - Exception during flow collection: ${e.message}")
            e.printStackTrace()
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
        val cacheSize = if (chartCount == 0) 0L else chartDataRepository.estimateTotalCacheSize()

        // Get the most recent cache update time from all cached charts
        val lastCacheUpdate = if (chartCount == 0) {
            null  // No valid cache if registry is empty
        } else {
            chartDataSynchronizer.getCachedChartIds()
                .mapNotNull { chartDataSynchronizer.getChartCacheTime(it) }
                .maxOrNull()
        }

        return DiagnosticsInfo(
            lastRegistryFetch = metadata?.lastDownloadTime,
            lastCacheUpdate = lastCacheUpdate,
            cachedChartsCount = chartCount,
            registryVersion = metadata?.registryVersion,
            totalCacheSize = cacheSize,
            failedSyncs = currentState.diagnostics.failedSyncs,
            lastError = currentState.diagnostics.lastError,
            isStale = registryManager.isRegistryStale(),
            isSyncing = currentState.isSyncing,
            failedCharts = currentState.syncProgress?.failedCharts ?: emptyList()
        )
    }

    /**
     * Gets the cached topics data.
     *
     * @return The cached topics, or null if not found
     */
    fun getCachedTopics() = chartDataSynchronizer.getCachedTopics()

    /**
     * Gets the timestamp when topics were last updated.
     *
     * @return The last update timestamp, or null if not found
     */
    fun getTopicsUpdatedAt() = chartDataSynchronizer.getTopicsUpdatedAt()

    /**
     * Clears cached topics data to force a re-download on next sync.
     */
    fun clearTopicsCache() = chartDataSynchronizer.clearTopicsCache()
}
