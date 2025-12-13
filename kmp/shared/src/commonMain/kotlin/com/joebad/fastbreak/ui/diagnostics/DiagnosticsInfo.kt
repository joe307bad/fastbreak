package com.joebad.fastbreak.ui.diagnostics

import kotlinx.datetime.Instant

/**
 * Diagnostics information for the app health UI.
 * This is a temporary model for Phase 2. Will be moved to RegistryState in Phase 6.
 */
data class DiagnosticsInfo(
    val lastRegistryFetch: Instant? = null,
    val lastCacheUpdate: Instant? = null,
    val cachedChartsCount: Int = 0,
    val registryVersion: String? = null,
    val totalCacheSize: Long = 0, // in bytes (estimated)
    val failedSyncs: Int = 0,
    val lastError: String? = null,
    val isStale: Boolean = false, // true if > 12 hours since last fetch
    val isSyncing: Boolean = false // true while actively syncing
)
