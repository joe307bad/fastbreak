import com.joebad.fastbreak.data.dailyFastbreak.FastbreakSelectionState
import kotlinx.serialization.Serializable


@Serializable
data class InitializeProfileResponse(
    val userId: String,
    val userName: String,
    val lockedFastBreakCard: FastbreakSelectionState? = null
)