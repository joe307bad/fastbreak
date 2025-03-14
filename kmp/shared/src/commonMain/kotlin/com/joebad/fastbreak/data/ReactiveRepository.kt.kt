import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Repository for managing Fastbreak state with reactive updates.
 * Uses Kotlin Flows for cross-platform (Android/iOS) reactivity.
 */
class ReactiveRepository(
    private val api: FastbreakApi,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    // Private mutable state flow that we update internally
    private val _fastbreakState = MutableStateFlow<FastbreakState?>(null)

    // Public immutable state flow that composables can collect from
    val fastbreakState: StateFlow<FastbreakState?> = _fastbreakState.asStateFlow()

    init {
        // Initialize with the current state when repository is created
        refreshDailyFastbreakState()
    }

    /**
     * Fetches the latest daily fastbreak state and updates the observable.
     * @return The fetched state
     */
    fun refreshDailyFastbreakState() {
        coroutineScope.launch {
            try {
                val latestState = api.getDailyFastbreakState()
                _fastbreakState.value = latestState
            } catch (e: Exception) {
                // Handle errors appropriately
                // You might want to emit error states or log the error
                println("Error fetching fastbreak state: ${e.message}")
            }
        }
    }

    /**
     * Updates a specific part of the fastbreak state.
     * This is an example of how to modify the state based on user actions.
     */
    fun updateFastbreakProgress(progress: Float) {
        coroutineScope.launch {
            _fastbreakState.value = _fastbreakState.value?.copy(progress = progress)

            // Optionally persist the change to backend
            // api.updateFastbreakProgress(progress)
        }
    }

    /**
     * Completes the current fastbreak challenge.
     */
    fun completeFastbreak() {
        coroutineScope.launch {
            _fastbreakState.value?.let { currentState ->
                // Create a new state that represents completion
                val completedState = currentState.copy(
                    isCompleted = true,
                    progress = 1.0f
                )
                _fastbreakState.value = completedState

                // Sync with backend
                api.completeFastbreak(completedState.id)
            }
        }
    }
}

/**
 * A data class representing the state of the daily fastbreak challenge.
 * This is just an example - adjust fields as needed for your application.
 */
data class FastbreakState(
    val id: String,
    val date: String,
    val title: String,
    val description: String,
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val points: Int = 0
)

/**
 * Interface for the Fastbreak API.
 * Implement this interface with your actual API client.
 */
interface FastbreakApi {
    suspend fun getDailyFastbreakState(): FastbreakState
    suspend fun updateFastbreakProgress(progress: Float)
    suspend fun completeFastbreak(id: String)
}