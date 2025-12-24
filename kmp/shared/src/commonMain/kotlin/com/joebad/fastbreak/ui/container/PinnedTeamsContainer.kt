package com.joebad.fastbreak.ui.container

import com.joebad.fastbreak.data.model.PinnedTeam
import com.joebad.fastbreak.data.model.TeamRoster
import com.joebad.fastbreak.data.repository.TeamRosterRepository
import com.joebad.fastbreak.domain.teams.TeamRosterSynchronizer
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.container

/**
 * State for pinned teams management.
 */
data class PinnedTeamsState(
    val teamRosters: Map<String, TeamRoster> = emptyMap(),  // Sport -> TeamRoster
    val pinnedTeams: List<PinnedTeam> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null
)

/**
 * Side effects for pinned teams.
 */
sealed interface PinnedTeamsSideEffect {
    data class ShowError(val message: String) : PinnedTeamsSideEffect
    data object SyncCompleted : PinnedTeamsSideEffect
}

/**
 * Container for managing pinned teams and team rosters.
 * Handles downloading team rosters and managing user's pinned teams.
 */
class PinnedTeamsContainer(
    private val teamRosterRepository: TeamRosterRepository,
    private val teamRosterSynchronizer: TeamRosterSynchronizer,
    private val scope: CoroutineScope
) : ContainerHost<PinnedTeamsState, PinnedTeamsSideEffect> {

    override val container: Container<PinnedTeamsState, PinnedTeamsSideEffect> =
        scope.container(PinnedTeamsState())

    init {
        // Load cached data on init
        println("═══════════════════════════════════════════════════════")
        println("🏈 PinnedTeamsContainer.init - Loading from cache")
        println("═══════════════════════════════════════════════════════")
        intent {
            val cachedRosters = teamRosterSynchronizer.getAllCachedRosters()
            val pinnedTeams = teamRosterRepository.getPinnedTeams()

            println("   📖 Cached rosters: ${cachedRosters.keys.joinToString()}")
            println("   📌 Pinned teams: ${pinnedTeams.size}")

            reduce {
                state.copy(
                    teamRosters = cachedRosters,
                    pinnedTeams = pinnedTeams
                )
            }
            println("   ✅ PinnedTeamsContainer.init complete")

            // If no cached data, log that we'll download
            if (cachedRosters.isEmpty()) {
                println("   📥 No cached data, will download team rosters...")
            }
        }

        // Download team rosters on init (will update state when complete)
        // This ensures teams are available even if user goes to settings before registry loads
        println("🚀 Triggering team rosters download from init")
        downloadTeamRosters()
    }

    /**
     * Downloads all team rosters from S3.
     * Called during app startup or when user refreshes.
     */
    fun downloadTeamRosters() = intent {
        println("🔄 PinnedTeamsContainer.downloadTeamRosters() - Starting")

        reduce {
            state.copy(
                isSyncing = true,
                error = null
            )
        }

        val results = teamRosterSynchronizer.downloadAllTeamRosters()

        // Separate successes and failures
        val successes = results.filterValues { it.isSuccess }
            .mapValues { (_, result) -> result.getOrNull()!! }
        val failures = results.filterValues { it.isFailure }

        println("✅ Successfully downloaded ${successes.size} team rosters")
        println("❌ Failed to download ${failures.size} team rosters")

        if (failures.isNotEmpty()) {
            failures.forEach { (sport, result) ->
                println("   ❌ $sport: ${result.exceptionOrNull()?.message}")
            }
        }

        reduce {
            state.copy(
                teamRosters = successes,
                isSyncing = false,
                error = if (failures.isNotEmpty()) {
                    "Failed to download ${failures.size} team rosters"
                } else null
            )
        }

        if (failures.isNotEmpty()) {
            postSideEffect(PinnedTeamsSideEffect.ShowError(
                "Failed to download team rosters for: ${failures.keys.joinToString()}"
            ))
        } else {
            postSideEffect(PinnedTeamsSideEffect.SyncCompleted)
        }

        println("   ✅ downloadTeamRosters complete")
    }

    /**
     * Gets or downloads team roster for a specific sport.
     *
     * @param sport The sport identifier
     */
    fun getOrDownloadTeamRoster(sport: String) = intent {
        // Check if already in state
        if (state.teamRosters.containsKey(sport)) {
            println("✓ Team roster for $sport already in state")
            return@intent
        }

        println("📥 Downloading team roster for $sport")

        reduce {
            state.copy(isLoading = true)
        }

        val roster = teamRosterSynchronizer.getOrDownloadTeamRoster(sport)

        if (roster != null) {
            reduce {
                state.copy(
                    teamRosters = state.teamRosters + (sport to roster),
                    isLoading = false
                )
            }
            println("✅ Added $sport team roster to state")
        } else {
            reduce {
                state.copy(
                    isLoading = false,
                    error = "Failed to load team roster for $sport"
                )
            }
            postSideEffect(PinnedTeamsSideEffect.ShowError("Failed to load team roster for $sport"))
        }
    }

    /**
     * Pins a team for filtering.
     *
     * @param sport The sport identifier
     * @param teamCode The team code
     * @param teamLabel The team label for display
     */
    fun pinTeam(sport: String, teamCode: String, teamLabel: String) = intent {
        println("📌 Pinning team: $sport - $teamCode ($teamLabel)")

        val pinnedAt = Clock.System.now()
        teamRosterRepository.pinTeam(sport, teamCode, teamLabel, pinnedAt)

        val updatedPinnedTeams = teamRosterRepository.getPinnedTeams()

        reduce {
            state.copy(pinnedTeams = updatedPinnedTeams)
        }

        println("✅ Team pinned successfully")
    }

    /**
     * Unpins a team.
     *
     * @param sport The sport identifier
     * @param teamCode The team code
     */
    fun unpinTeam(sport: String, teamCode: String) = intent {
        println("📍 Unpinning team: $sport - $teamCode")

        teamRosterRepository.unpinTeam(sport, teamCode)

        val updatedPinnedTeams = teamRosterRepository.getPinnedTeams()

        reduce {
            state.copy(pinnedTeams = updatedPinnedTeams)
        }

        println("✅ Team unpinned successfully")
    }

    /**
     * Checks if a team is currently pinned.
     *
     * @param sport The sport identifier
     * @param teamCode The team code
     * @return true if the team is pinned
     */
    fun isTeamPinned(sport: String, teamCode: String): Boolean {
        return container.stateFlow.value.pinnedTeams.any {
            it.sport == sport && it.teamCode == teamCode
        }
    }

    /**
     * Gets all pinned teams for a specific sport.
     *
     * @param sport The sport identifier
     * @return List of pinned teams for that sport
     */
    fun getPinnedTeamsForSport(sport: String): List<PinnedTeam> {
        return container.stateFlow.value.pinnedTeams.filter { it.sport == sport }
    }

    /**
     * Clears all pinned teams.
     */
    fun clearAllPinnedTeams() = intent {
        println("🗑️ Clearing all pinned teams")

        teamRosterRepository.clearAllPinnedTeams()

        reduce {
            state.copy(pinnedTeams = emptyList())
        }

        println("✅ All pinned teams cleared")
    }
}
