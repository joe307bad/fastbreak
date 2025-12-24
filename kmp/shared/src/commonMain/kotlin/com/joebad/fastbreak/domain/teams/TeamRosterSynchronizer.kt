package com.joebad.fastbreak.domain.teams

import com.joebad.fastbreak.data.api.HttpClientFactory
import com.joebad.fastbreak.data.model.TeamRoster
import com.joebad.fastbreak.data.repository.TeamRosterRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

/**
 * Synchronizes team roster data from S3.
 * Downloads team rosters for all supported sports.
 */
class TeamRosterSynchronizer(
    private val teamRosterRepository: TeamRosterRepository,
    private val httpClient: HttpClient = HttpClientFactory.create(),
    private val isProd: Boolean = false  // Default to dev for now
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        private const val CLOUDFRONT_URL = "https://d2jyizt5xogu23.cloudfront.net"
        private val SUPPORTED_SPORTS = listOf("NFL", "NBA", "NHL", "MLB")
    }

    /**
     * Gets the S3 URL for a sport's team roster.
     */
    private fun getTeamRosterUrl(sport: String): String {
        val prefix = if (isProd) "" else "dev/"
        return "$CLOUDFRONT_URL/${prefix}teams/${sport.lowercase()}__teams.json"
    }

    /**
     * Downloads team roster for a specific sport.
     *
     * @param sport The sport identifier (e.g., "NFL", "NBA")
     * @return Result containing the TeamRoster or error
     */
    suspend fun downloadTeamRoster(sport: String): Result<TeamRoster> {
        return try {
            println("📥 Downloading team roster for $sport")
            val url = getTeamRosterUrl(sport)
            println("   URL: $url")

            val roster: TeamRoster = httpClient.get(url).body()
            println("✅ Downloaded ${roster.teams.size} teams for $sport")

            // Save to cache
            teamRosterRepository.saveTeamRoster(sport, roster)
            println("💾 Cached team roster for $sport")

            Result.success(roster)
        } catch (e: Exception) {
            println("❌ Failed to download team roster for $sport: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Downloads all team rosters in parallel.
     *
     * @return Map of sport to Result<TeamRoster>
     */
    suspend fun downloadAllTeamRosters(): Map<String, Result<TeamRoster>> = coroutineScope {
        println("📊 TeamRosterSynchronizer.downloadAllTeamRosters() - Starting")
        println("   Sports: ${SUPPORTED_SPORTS.joinToString()}")

        val results = SUPPORTED_SPORTS.associateWith { sport ->
            async {
                downloadTeamRoster(sport)
            }
        }

        results.mapValues { (_, deferred) ->
            deferred.await()
        }
    }

    /**
     * Gets a team roster from cache, or downloads if not available.
     *
     * @param sport The sport identifier
     * @return TeamRoster or null if unavailable
     */
    suspend fun getOrDownloadTeamRoster(sport: String): TeamRoster? {
        // Try cache first
        teamRosterRepository.getTeamRoster(sport)?.let {
            println("✓ Using cached team roster for $sport")
            return it
        }

        // Download if not cached
        println("📥 Team roster not cached for $sport, downloading...")
        return downloadTeamRoster(sport).getOrNull()
    }

    /**
     * Checks if a team roster needs updating.
     * Currently always returns false since rosters don't change frequently,
     * but could be extended to check timestamps.
     *
     * @param sport The sport identifier
     * @return true if the roster should be re-downloaded
     */
    fun needsUpdate(sport: String): Boolean {
        // For now, only download if not cached
        // Could extend to check lastUpdated timestamp
        return !teamRosterRepository.hasTeamRoster(sport)
    }

    /**
     * Gets all cached team rosters.
     *
     * @return Map of sport to TeamRoster
     */
    fun getAllCachedRosters(): Map<String, TeamRoster> {
        return SUPPORTED_SPORTS.mapNotNull { sport ->
            teamRosterRepository.getTeamRoster(sport)?.let { sport to it }
        }.toMap()
    }
}
