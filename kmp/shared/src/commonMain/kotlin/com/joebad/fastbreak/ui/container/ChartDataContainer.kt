package com.joebad.fastbreak.ui.container

import com.joebad.fastbreak.data.model.BarGraphVisualization
import com.joebad.fastbreak.data.model.LineChartVisualization
import com.joebad.fastbreak.data.model.ScatterPlotVisualization
import com.joebad.fastbreak.domain.registry.ChartDataSynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

/**
 * Orbit MVI Container for individual chart data.
 * Handles loading and displaying a specific chart's data.
 */
class ChartDataContainer(
    private val chartId: String,
    private val chartDataSynchronizer: ChartDataSynchronizer,
    private val registryContainer: RegistryContainer,
    private val scope: CoroutineScope
) : ContainerHost<ChartDataState, ChartDataSideEffect> {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    override val container: Container<ChartDataState, ChartDataSideEffect> =
        scope.container(ChartDataState())

    init {
        // Load chart data on initialization
        loadChartData()
    }

    /**
     * Loads the chart data from cache or triggers a sync if needed.
     */
    fun loadChartData() = intent {
        reduce { state.copy(isLoading = true, error = null) }

        // Get chart definition from registry
        val registry = registryContainer.container.stateFlow.value.registry
        val chartDef = registry?.findChart(chartId)

        if (chartDef == null) {
            reduce {
                state.copy(
                    isLoading = false,
                    error = "Chart not found in registry"
                )
            }
            postSideEffect(ChartDataSideEffect.ShowError("Chart not found"))
            return@intent
        }

        // Try to load from cache
        val cached = chartDataSynchronizer.getCachedChartData(chartId)
        if (cached != null) {
            try {
                // Deserialize based on visualization type
                val vizData = when (cached.visualizationType) {
                    com.joebad.fastbreak.data.model.VizType.SCATTER_PLOT -> {
                        json.decodeFromString<ScatterPlotVisualization>(cached.dataJson)
                    }
                    com.joebad.fastbreak.data.model.VizType.BAR_GRAPH -> {
                        json.decodeFromString<BarGraphVisualization>(cached.dataJson)
                    }
                    com.joebad.fastbreak.data.model.VizType.LINE_CHART -> {
                        json.decodeFromString<LineChartVisualization>(cached.dataJson)
                    }
                }

                reduce {
                    state.copy(
                        chartDefinition = chartDef,
                        visualizationData = vizData,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                reduce {
                    state.copy(
                        isLoading = false,
                        error = "Failed to load chart data: ${e.message}"
                    )
                }
                postSideEffect(ChartDataSideEffect.ShowError(e.message ?: "Failed to load chart data"))
            }
        } else {
            // No cached data - this shouldn't happen if sync worked correctly
            reduce {
                state.copy(
                    chartDefinition = chartDef,
                    isLoading = false,
                    error = "Chart data not available. Try refreshing."
                )
            }
            postSideEffect(ChartDataSideEffect.ShowError("Chart data not available"))
        }
    }

    /**
     * Refreshes the chart data by triggering a registry refresh.
     */
    fun refreshChartData() = intent {
        registryContainer.refreshRegistry()
        // After registry refreshes, it will sync chart data, so we can reload
        loadChartData()
    }
}
