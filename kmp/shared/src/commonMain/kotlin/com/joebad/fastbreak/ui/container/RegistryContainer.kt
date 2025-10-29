package com.joebad.fastbreak.ui.container

import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.domain.registry.ChartDataSynchronizer
import com.joebad.fastbreak.domain.registry.RegistryManager
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import kotlinx.coroutines.CoroutineScope
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
    private val scope: CoroutineScope
) : ContainerHost<RegistryState, RegistrySideEffect> {

    override val container: Container<RegistryState, RegistrySideEffect> =
        scope.container(RegistryState())

    init {
        // Load registry on initialization
        loadRegistry()
    }

    /**
     * Loads the registry with 12-hour automatic update check.
     * Also triggers chart data synchronization after loading.
     */
    fun loadRegistry() = intent {
        reduce { state.copy(isLoading = true, error = null) }

        registryManager.checkAndUpdateRegistry()
            .onSuccess { registry ->
                reduce {
                    state.copy(
                        registry = registry,
                        isLoading = false,
                        diagnostics = loadDiagnostics()
                    )
                }

                // Sync chart data after loading registry
                syncChartData()
            }
            .onFailure { error ->
                val diagnostics = container.stateFlow.value.diagnostics.copy(
                    failedSyncs = container.stateFlow.value.diagnostics.failedSyncs + 1,
                    lastError = error.message
                )
                reduce {
                    state.copy(
                        isLoading = false,
                        error = error.message,
                        diagnostics = diagnostics
                    )
                }
                postSideEffect(RegistrySideEffect.ShowError(error.message ?: "Failed to load registry"))
            }
    }

    /**
     * Forces a registry refresh regardless of staleness.
     * Prevents concurrent refresh operations.
     */
    fun refreshRegistry() = intent {
        // Guard: prevent concurrent refresh operations
        if (container.stateFlow.value.isSyncing) {
            println("Refresh already in progress, ignoring duplicate request")
            return@intent
        }

        reduce { state.copy(isSyncing = true, error = null) }

        registryManager.forceRefreshRegistry()
            .onSuccess { registry ->
                reduce {
                    state.copy(
                        registry = registry,
                        diagnostics = loadDiagnostics()
                    )
                }

                // Sync chart data after refresh
                syncChartData()
            }
            .onFailure { error ->
                val diagnostics = container.stateFlow.value.diagnostics.copy(
                    failedSyncs = container.stateFlow.value.diagnostics.failedSyncs + 1,
                    lastError = error.message
                )
                reduce {
                    state.copy(
                        isSyncing = false,
                        error = error.message,
                        diagnostics = diagnostics
                    )
                }
                postSideEffect(RegistrySideEffect.ShowError(error.message ?: "Failed to refresh registry"))
            }
    }

    /**
     * Synchronizes chart data based on registry definitions.
     * Compares timestamps and downloads charts that need updating.
     */
    private suspend fun syncChartData() = intent {
        val registry = container.stateFlow.value.registry ?: return@intent

        chartDataSynchronizer.synchronizeCharts(registry).collect { progress ->
            reduce {
                state.copy(
                    syncProgress = progress,
                    isSyncing = !progress.isComplete
                )
            }

            // When complete, update diagnostics with final cache info
            if (progress.isComplete) {
                reduce {
                    state.copy(
                        lastSyncTime = Clock.System.now(),
                        diagnostics = loadDiagnostics().copy(
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
            }
        }
    }

    /**
     * Loads diagnostics information from repositories.
     */
    private fun loadDiagnostics(): DiagnosticsInfo {
        val currentState = container.stateFlow.value
        val metadata = registryManager.getMetadata()
        val chartCount = chartDataRepository.getCachedChartCount()
        val cacheSize = chartDataRepository.estimateTotalCacheSize()

        // Get the most recent cache update time from all cached charts
        val lastCacheUpdate = chartDataSynchronizer.getCachedChartIds()
            .mapNotNull { chartDataSynchronizer.getChartCacheTime(it) }
            .maxOrNull()

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
