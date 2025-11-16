package com.joebad.fastbreak.ui.container

import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.domain.registry.ChartDataSynchronizer
import com.joebad.fastbreak.domain.registry.RegistryManager
import com.joebad.fastbreak.platform.NetworkPermissionChecker
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import com.mohamedrejeb.calf.permissions.PermissionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
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
    private val networkPermissionChecker: NetworkPermissionChecker = NetworkPermissionChecker()
) : ContainerHost<RegistryState, RegistrySideEffect> {

    override val container: Container<RegistryState, RegistrySideEffect> =
        scope.container(RegistryState())

    init {
        // Load initial diagnostics from cache without making network requests
        intent {
            val cachedRegistry = registryManager.getCachedRegistry()
            reduce {
                state.copy(registry = cachedRegistry)
            }
            // Now that registry is in state, load diagnostics
            reduce {
                state.copy(diagnostics = loadDiagnostics())
            }
        }
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
     * Loads the registry with 12-hour automatic update check.
     * Also triggers chart data synchronization after loading.
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

        registryManager.checkAndUpdateRegistry()
            .onSuccess { registry ->
                reduce {
                    state.copy(
                        registry = registry,
                        isLoading = false,
                        diagnostics = loadDiagnostics().copy(
                            isSyncing = true  // Keep syncing state active for chart data sync
                        )
                    )
                }

                // Sync chart data after loading registry (await completion)
                try {
                    syncChartData()
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

                // Try to fall back to cached registry
                val cachedRegistry = registryManager.getCachedRegistry()

                if (cachedRegistry != null) {
                    println("📦 Falling back to cached registry with ${cachedRegistry.charts.size} charts")

                    reduce {
                        state.copy(
                            registry = cachedRegistry,
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

                    // Continue with chart data sync using cached registry
                    try {
                        syncChartData()
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
     * Adds minimum display time for loading indicator (0.5s before + 0.5s after).
     */
    fun refreshRegistry() = intent {
        println("═══════════════════════════════════════════════════════")
        println("🔄 REFRESH BUTTON CLICKED - Starting refresh flow")
        println("═══════════════════════════════════════════════════════")

        // Guard: prevent concurrent refresh operations
        if (container.stateFlow.value.isSyncing) {
            println("⚠️  Refresh already in progress, ignoring duplicate request")
            return@intent
        }

        println("✓ Setting isSyncing = true and showing initial sync progress")
        reduce {
            state.copy(
                isSyncing = true,
                error = null,
                syncProgress = com.joebad.fastbreak.ui.diagnostics.SyncProgress(
                    current = 0,
                    total = 1,
                    currentChart = "Preparing to sync...",
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

        // Show loading indicator for minimum 0.5 seconds before starting
        println("⏳ Waiting 500ms before starting refresh...")
        delay(500)
        println("✓ Delay complete, calling forceRefreshRegistry()")

        val refreshResult = registryManager.forceRefreshRegistry()

        when {
            refreshResult.isSuccess -> {
                val registry = refreshResult.getOrThrow()
                println("✅ forceRefreshRegistry() SUCCESS - Registry loaded with ${registry.charts.size} charts")

                reduce {
                    state.copy(
                        registry = registry,
                        diagnostics = loadDiagnostics().copy(
                            isSyncing = true  // Keep syncing state active for chart data sync
                        )
                    )
                }

                println("📊 Starting chart data synchronization...")
                // Sync chart data after refresh (await completion)
                try {
                    syncChartData()
                    println("✅ Chart synchronization complete")
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

                // Try to fall back to cached registry
                val cachedRegistry = registryManager.getCachedRegistry()

                if (cachedRegistry != null) {
                    println("📦 Falling back to cached registry with ${cachedRegistry.charts.size} charts")

                    reduce {
                        state.copy(
                            registry = cachedRegistry,
                            diagnostics = loadDiagnostics().copy(
                                isSyncing = true,  // Keep syncing state active for chart data sync
                                failedSyncs = container.stateFlow.value.diagnostics.failedSyncs + 1,
                                lastError = errorMsg
                            )
                        )
                    }

                    // Show error toast even though we have cached data
                    postSideEffect(RegistrySideEffect.ShowError(errorMsg))

                    // Continue with chart data sync using cached registry
                    println("📊 Starting chart data synchronization with cached registry...")
                    try {
                        syncChartData()
                        println("✅ Chart synchronization complete")
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

                    // Keep loading indicator visible for 0.5s even on error
                    println("⏳ Waiting 500ms before hiding loading indicator...")
                    delay(500)

                    println("✓ Setting error state and isSyncing = false")
                    reduce {
                        state.copy(
                            isSyncing = false,
                            syncProgress = null,
                            error = errorMsg,
                            diagnostics = diagnostics
                        )
                    }
                    postSideEffect(RegistrySideEffect.ShowError(errorMsg))
                    println("═══════════════════════════════════════════════════════")
                    println("❌ REFRESH FLOW COMPLETE (WITH ERROR)")
                    println("═══════════════════════════════════════════════════════")
                }
            }
        }
    }

    /**
     * Synchronizes chart data based on registry definitions.
     * Compares timestamps and downloads charts that need updating.
     * Adds 0.5s delay after completion before hiding loading indicator.
     *
     * Note: This is a regular suspend function, not wrapped in intent{},
     * so it can be properly awaited when called from other intent blocks.
     */
    private suspend fun syncChartData() {
        val registry = container.stateFlow.value.registry ?: return

        try {
            chartDataSynchronizer.synchronizeCharts(registry).collect { progress ->
            // Skip showing progress if nothing needs syncing (everything cached)
            if (progress.total == 0) {
                // Everything is already cached, no need to show sync progress
                intent {
                    reduce {
                        state.copy(
                            isSyncing = false,
                            lastSyncTime = Clock.System.now(),
                            syncProgress = null,
                            diagnostics = loadDiagnostics().copy(
                                isSyncing = false  // Explicitly set to false
                            )
                        )
                    }
                }.join()
                return@collect
            }

            // When complete, keep loading visible for 0.5s before hiding
            if (progress.isComplete) {
                // Update progress but keep syncing state
                intent {
                    reduce {
                        state.copy(
                            syncProgress = progress,
                            isSyncing = true  // Keep true during delay
                        )
                    }
                }.join()

                // Keep loading indicator visible for minimum 0.5 seconds after completion
                delay(500)

                // Update diagnostics but keep syncing state active
                intent {
                    reduce {
                        state.copy(
                            isSyncing = true,  // Keep true until we clear syncProgress
                            lastSyncTime = Clock.System.now(),
                            diagnostics = loadDiagnostics().copy(
                                isSyncing = true,  // Keep true until we clear syncProgress
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

                // Keep the completion message visible for 0.5 seconds
                delay(500)

                // Now clear syncProgress and unlock buttons
                intent {
                    reduce {
                        state.copy(
                            syncProgress = null,
                            isSyncing = false,
                            diagnostics = state.diagnostics.copy(isSyncing = false)
                        )
                    }
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

        // Use registry chart count instead of cached chart count
        val chartCount = currentState.registry?.charts?.size ?: 0

        // If registry has no charts, show 0 bytes (cached data is orphaned/stale)
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
            isSyncing = currentState.isSyncing
        )
    }
}
