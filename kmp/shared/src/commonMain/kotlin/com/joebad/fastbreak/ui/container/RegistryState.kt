package com.joebad.fastbreak.ui.container

import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.data.model.RegistryEntry
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import com.mohamedrejeb.calf.permissions.PermissionStatus
import kotlin.time.Instant

/**
 * State for the registry management system.
 * Used with Orbit MVI pattern.
 */
data class RegistryState(
    /**
     * Raw registry entries from the server (file_key -> RegistryEntry)
     * Used for determining which charts to sync
     */
    val registryEntries: Map<String, RegistryEntry>? = null,

    /**
     * The current registry containing all chart definitions (built from cached chart data)
     * Used by UI components to display charts
     */
    val registry: Registry? = null,

    /**
     * Whether the registry is currently being loaded
     */
    val isLoading: Boolean = false,

    /**
     * Whether a sync operation is in progress
     */
    val isSyncing: Boolean = false,

    /**
     * When the last sync was completed
     */
    val lastSyncTime: Instant? = null,

    /**
     * Current sync progress (null if not syncing)
     */
    val syncProgress: com.joebad.fastbreak.ui.diagnostics.SyncProgress? = null,

    /**
     * Current error message (null if no error)
     */
    val error: String? = null,

    /**
     * Network permission status - always Granted.
     * iOS will prompt for local network access automatically when needed.
     */
    val networkPermissionStatus: PermissionStatus = PermissionStatus.Granted,

    /**
     * Diagnostics information for the UI
     */
    val diagnostics: DiagnosticsInfo = DiagnosticsInfo()
)

/**
 * Side effects for registry operations.
 * These are one-time events that don't affect state.
 */
sealed interface RegistrySideEffect {
    /**
     * Show an error message to the user
     */
    data class ShowError(val message: String) : RegistrySideEffect

    /**
     * Sync completed successfully
     */
    data object SyncCompleted : RegistrySideEffect

    /**
     * Navigate to a specific chart
     */
    data class NavigateToChart(val chartId: String) : RegistrySideEffect
}
