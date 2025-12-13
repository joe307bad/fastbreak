package com.joebad.fastbreak.ui.container

import com.joebad.fastbreak.data.model.ChartDefinition
import com.joebad.fastbreak.data.model.VisualizationType

/**
 * State for individual chart data screen.
 * Used with Orbit MVI pattern.
 */
data class ChartDataState(
    /**
     * The chart definition metadata
     */
    val chartDefinition: ChartDefinition? = null,

    /**
     * The actual visualization data
     */
    val visualizationData: VisualizationType? = null,

    /**
     * Whether the data is currently being loaded
     */
    val isLoading: Boolean = false,

    /**
     * Current error message (null if no error)
     */
    val error: String? = null
)

/**
 * Side effects for chart data operations.
 * These are one-time events that don't affect state.
 */
sealed interface ChartDataSideEffect {
    /**
     * Show an error message to the user
     */
    data class ShowError(val message: String) : ChartDataSideEffect
}
