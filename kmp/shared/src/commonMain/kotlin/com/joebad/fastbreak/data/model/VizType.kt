package com.joebad.fastbreak.data.model

import kotlinx.serialization.Serializable

/**
 * Enum representing the types of visualizations supported by the registry system.
 * Maps to the existing VisualizationType sealed interface implementations.
 */
@Serializable
enum class VizType {
    SCATTER_PLOT,
    BAR_GRAPH,
    LINE_CHART;

    /**
     * Maps to the mockDataType string used by MockedDataApi.
     * @return "scatter", "bar", or "line"
     */
    fun toMockDataType(): String {
        return when (this) {
            SCATTER_PLOT -> "scatter"
            BAR_GRAPH -> "bar"
            LINE_CHART -> "line"
        }
    }

    companion object {
        /**
         * Creates a VizType from a mockDataType string.
         * @param mockDataType "scatter", "bar", or "line"
         * @return The corresponding VizType
         * @throws IllegalArgumentException if the type is not recognized
         */
        fun fromMockDataType(mockDataType: String): VizType {
            return when (mockDataType.lowercase()) {
                "scatter" -> SCATTER_PLOT
                "bar" -> BAR_GRAPH
                "line" -> LINE_CHART
                else -> throw IllegalArgumentException("Unknown mockDataType: $mockDataType")
            }
        }
    }
}
