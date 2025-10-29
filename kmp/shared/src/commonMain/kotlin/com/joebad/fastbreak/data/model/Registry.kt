package com.joebad.fastbreak.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents the complete registry of available charts.
 * This is the top-level structure returned from the registry API/mock.
 */
@Serializable
data class Registry(
    /**
     * Registry version string (e.g., "1.0", "1.1")
     * Used for schema migration if the registry format changes
     */
    val version: String,

    /**
     * When this registry was last updated
     * Used to track registry freshness
     */
    val lastUpdated: Instant,

    /**
     * List of all available charts across all sports
     */
    val charts: List<ChartDefinition>
) {
    /**
     * Get all charts for a specific sport
     */
    fun chartsForSport(sport: Sport): List<ChartDefinition> {
        return charts.filter { it.sport == sport }
    }

    /**
     * Find a chart by its ID
     */
    fun findChart(chartId: String): ChartDefinition? {
        return charts.find { it.id == chartId }
    }

    /**
     * Get total number of charts in the registry
     */
    val chartCount: Int
        get() = charts.size
}
