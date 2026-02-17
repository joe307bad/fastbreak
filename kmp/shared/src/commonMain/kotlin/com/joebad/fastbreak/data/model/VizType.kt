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
    LINE_CHART,
    TABLE,
    MATCHUP,
    MATCHUP_V2,
    NBA_MATCHUP,
    CBB_MATCHUP,
    HELLO_WORLD
}
