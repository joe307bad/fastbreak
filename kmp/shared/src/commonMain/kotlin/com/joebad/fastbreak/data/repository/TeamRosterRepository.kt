package com.joebad.fastbreak.data.repository

import com.joebad.fastbreak.data.model.TeamRoster
import com.joebad.fastbreak.data.model.PinnedTeam
import com.russhwolf.settings.Settings
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for persisting and retrieving team roster data and pinned teams.
 * Stores team rosters separately for each sport and manages user's pinned teams.
 */
class TeamRosterRepository(
    private val settings: Settings
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        private const val PREFIX_ROSTER = "team_roster_"
        private const val KEY_PINNED_TEAMS = "pinned_teams"
    }

    /**
     * Saves team roster data for a specific sport.
     *
     * @param sport The sport identifier (e.g., "NFL", "NBA")
     * @param roster The team roster to save
     */
    fun saveTeamRoster(sport: String, roster: TeamRoster) {
        try {
            val jsonString = json.encodeToString(roster)
            settings.putString("$PREFIX_ROSTER$sport", jsonString)
        } catch (e: SerializationException) {
            println("Error saving team roster for $sport: ${e.message}")
        }
    }

    /**
     * Retrieves team roster data for a specific sport.
     *
     * @param sport The sport identifier
     * @return The team roster, or null if not found or corrupted
     */
    fun getTeamRoster(sport: String): TeamRoster? {
        return try {
            val jsonString = settings.getStringOrNull("$PREFIX_ROSTER$sport") ?: return null
            json.decodeFromString<TeamRoster>(jsonString)
        } catch (e: SerializationException) {
            println("Error reading team roster for $sport: ${e.message}")
            null
        }
    }

    /**
     * Checks if a team roster exists for a specific sport.
     *
     * @param sport The sport identifier
     * @return true if the roster exists
     */
    fun hasTeamRoster(sport: String): Boolean {
        return settings.hasKey("$PREFIX_ROSTER$sport")
    }

    /**
     * Deletes team roster for a specific sport.
     *
     * @param sport The sport identifier
     */
    fun deleteTeamRoster(sport: String) {
        settings.remove("$PREFIX_ROSTER$sport")
    }

    /**
     * Gets all pinned teams across all sports.
     *
     * @return List of pinned teams
     */
    fun getPinnedTeams(): List<PinnedTeam> {
        return try {
            val jsonString = settings.getStringOrNull(KEY_PINNED_TEAMS) ?: return emptyList()
            json.decodeFromString<List<PinnedTeam>>(jsonString)
        } catch (e: SerializationException) {
            println("Error reading pinned teams: ${e.message}")
            emptyList()
        }
    }

    /**
     * Saves the list of pinned teams.
     *
     * @param pinnedTeams List of pinned teams to save
     */
    private fun savePinnedTeams(pinnedTeams: List<PinnedTeam>) {
        try {
            val jsonString = json.encodeToString(pinnedTeams)
            settings.putString(KEY_PINNED_TEAMS, jsonString)
        } catch (e: SerializationException) {
            println("Error saving pinned teams: ${e.message}")
        }
    }

    /**
     * Adds a team to the pinned teams list.
     *
     * @param sport The sport identifier
     * @param teamCode The team code
     * @param teamLabel The team label for display
     * @param pinnedAt When the team was pinned
     */
    fun pinTeam(sport: String, teamCode: String, teamLabel: String, pinnedAt: Instant) {
        val currentPinned = getPinnedTeams().toMutableList()

        // Remove if already exists (to update pinnedAt time)
        currentPinned.removeAll { it.sport == sport && it.teamCode == teamCode }

        // Add to the list
        currentPinned.add(PinnedTeam(sport, teamCode, teamLabel, pinnedAt))

        savePinnedTeams(currentPinned)
    }

    /**
     * Removes a team from the pinned teams list.
     *
     * @param sport The sport identifier
     * @param teamCode The team code
     */
    fun unpinTeam(sport: String, teamCode: String) {
        val currentPinned = getPinnedTeams().toMutableList()
        currentPinned.removeAll { it.sport == sport && it.teamCode == teamCode }
        savePinnedTeams(currentPinned)
    }

    /**
     * Checks if a specific team is pinned.
     *
     * @param sport The sport identifier
     * @param teamCode The team code
     * @return true if the team is pinned
     */
    fun isTeamPinned(sport: String, teamCode: String): Boolean {
        return getPinnedTeams().any { it.sport == sport && it.teamCode == teamCode }
    }

    /**
     * Gets all pinned teams for a specific sport.
     *
     * @param sport The sport identifier
     * @return List of pinned teams for that sport
     */
    fun getPinnedTeamsForSport(sport: String): List<PinnedTeam> {
        return getPinnedTeams().filter { it.sport == sport }
    }

    /**
     * Clears all pinned teams.
     */
    fun clearAllPinnedTeams() {
        settings.remove(KEY_PINNED_TEAMS)
    }

    /**
     * Clears all team rosters and pinned teams.
     */
    fun clearAll() {
        // Clear all rosters
        listOf("NFL", "NBA", "NHL", "MLB").forEach { sport ->
            deleteTeamRoster(sport)
        }
        // Clear pinned teams
        clearAllPinnedTeams()
    }
}
