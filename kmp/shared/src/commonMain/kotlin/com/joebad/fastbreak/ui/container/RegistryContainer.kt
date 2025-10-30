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
        reduce { state.copy(isLoading = true, error = null, syncProgress = null) }

        registryManager.checkAndUpdateRegistry()
            .onSuccess { registry ->
                reduce {
                    state.copy(
                        registry = registry,
                        isLoading = false,
                        diagnostics = loadDiagnostics()
                    )
                }

                // Sync chart data after loading registry (await completion)
                syncChartData()
            }
            .onFailure { error ->
                val errorMsg = error.message ?: "Unknown error"
                println("LoadRegistry failed with error: $errorMsg")

                val diagnostics = container.stateFlow.value.diagnostics.copy(
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

        println("✓ Setting isSyncing = true")
        reduce { state.copy(isSyncing = true, error = null, syncProgress = null) }

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
                        diagnostics = loadDiagnostics()
                    )
                }

                println("📊 Starting chart data synchronization...")
                // Sync chart data after refresh (await completion)
                syncChartData()
                println("✅ Chart synchronization complete")
            }
            refreshResult.isFailure -> {
                val error = refreshResult.exceptionOrNull()
                val errorMsg = error?.message ?: "Unknown error"
                println("❌ forceRefreshRegistry() FAILED with error: $errorMsg")
                println("Stack trace: ${error?.stackTraceToString()}")

                val diagnostics = container.stateFlow.value.diagnostics.copy(
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

        chartDataSynchronizer.synchronizeCharts(registry).collect { progress ->
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

                // Now clear the loading state but keep syncProgress visible
                intent {
                    reduce {
                        state.copy(
                            isSyncing = false,
                            lastSyncTime = Clock.System.now(),
                            diagnostics = loadDiagnostics().copy(
                                isSyncing = false,  // Explicitly set to false since loadDiagnostics() reads old state
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

                // Keep the completion message visible for 0.5 seconds, then clear syncProgress
                delay(500)
                intent {
                    reduce {
                        state.copy(syncProgress = null)
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
