package com.joebad.fastbreak.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents cached chart data stored locally.
 * Contains the serialized visualization data and metadata about when it was cached.
 */
@Serializable
data class CachedChartData(
    /**
     * The ID of the chart this data belongs to
     * Corresponds to ChartDefinition.id
     */
    val chartId: String,

    /**
     * When the chart data was last updated (from the chart definition)
     * Used to determine if cached data is stale
     */
    val lastUpdated: Instant,

    /**
     * The type of visualization this data represents
     */
    val visualizationType: VizType,

    /**
     * When this data was cached locally
     * Used for diagnostics and cache management
     */
    val cachedAt: Instant,

    /**
     * The serialized VisualizationType data as a JSON string
     * Will be one of: BarGraphVisualization, ScatterPlotVisualization, or LineChartVisualization
     * This allows flexible storage without complex type handling in multiplatform-settings
     */
    val dataJson: String
) {
    /**
     * Estimate the size of this cached data in bytes (approximate)
     */
    val estimatedSizeBytes: Long
        get() = dataJson.length.toLong()
}
