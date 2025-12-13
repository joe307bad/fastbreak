package com.joebad.fastbreak.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

    /**
     * Deserializes the cached JSON data back into the appropriate VisualizationType.
     *
     * @return The deserialized visualization data
     * @throws Exception if deserialization fails
     */
    fun deserialize(): VisualizationType {
        val json = Json { ignoreUnknownKeys = true }
        return when (visualizationType) {
            VizType.SCATTER_PLOT -> json.decodeFromString<ScatterPlotVisualization>(dataJson)
            VizType.BAR_GRAPH -> json.decodeFromString<BarGraphVisualization>(dataJson)
            VizType.LINE_CHART -> json.decodeFromString<LineChartVisualization>(dataJson)
        }
    }
}
