package com.example.kmpapp.viewmodel

import com.example.kmpapp.data.api.MockedDataApi
import com.example.kmpapp.data.model.ScatterPlotVisualization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Data Visualization screen.
 * Manages the state of scatter plot data fetching and display.
 */
class DataVizViewModel(
    private val api: MockedDataApi = MockedDataApi()
) {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<DataVizState>(DataVizState.Initial)
    val state: StateFlow<DataVizState> = _state.asStateFlow()

    /**
     * Loads scatter plot data from the mocked API.
     */
    fun loadData() {
        viewModelScope.launch {
            _state.value = DataVizState.Loading

            try {
                val data = api.fetchVisualizationData(
                    MockedDataApi.Sport.NFL,
                    MockedDataApi.VizType.SCATTER
                ) as ScatterPlotVisualization
                _state.value = DataVizState.Success(data)
            } catch (e: Exception) {
                _state.value = DataVizState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    /**
     * Retries loading data after an error.
     */
    fun retry() = loadData()
}

/**
 * State representation for the Data Visualization screen.
 */
sealed interface DataVizState {
    /**
     * Initial state before any data is loaded.
     */
    data object Initial : DataVizState

    /**
     * Loading state while data is being fetched.
     */
    data object Loading : DataVizState

    /**
     * Success state with loaded data.
     */
    data class Success(val data: ScatterPlotVisualization) : DataVizState

    /**
     * Error state when data loading fails.
     */
    data class Error(val message: String) : DataVizState
}

/**
 * Side effects for the Data Visualization screen.
 * Currently unused but available for future one-time events like navigation or toasts.
 */
sealed interface DataVizSideEffect
