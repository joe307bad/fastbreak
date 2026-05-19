package com.joebad.fastbreak.domain.registry

import com.joebad.fastbreak.config.AppConfig
import com.joebad.fastbreak.data.model.RegistryEntry

/**
 * Result of checking the release ID compatibility between client and server.
 */
sealed class ReleaseIdCheckResult {
    /**
     * Client is compatible with the server's release ID.
     * Charts can be downloaded normally.
     */
    data object Compatible : ReleaseIdCheckResult()

    /**
     * Client needs to update to download new charts.
     * User should be prompted to update the app.
     */
    data class UpdateRequired(
        val serverReleaseId: String,
        val clientReleaseId: String
    ) : ReleaseIdCheckResult()

    /**
     * Dev mode bypasses release ID checking.
     * All charts are downloaded regardless of release ID.
     */
    data object DevModeBypass : ReleaseIdCheckResult()
}

/**
 * Checks if the client's built-in release ID is compatible with the server's release ID.
 *
 * The release ID format is "fb-{number}" where the number is monotonically increasing.
 * The client must have a release ID >= the server's release ID to download charts.
 *
 * Dev mode bypasses this check entirely.
 */
class ReleaseIdChecker {

    /**
     * Checks if the client can download charts based on the release ID.
     *
     * @param registryEntries The registry entries fetched from the server
     * @return ReleaseIdCheckResult indicating whether charts can be downloaded
     */
    fun checkReleaseId(registryEntries: Map<String, RegistryEntry>): ReleaseIdCheckResult {
        // Dev mode bypasses release ID checking
        if (AppConfig.DEV_MODE) {
            println("🔓 ReleaseIdChecker: Dev mode enabled, bypassing release ID check")
            return ReleaseIdCheckResult.DevModeBypass
        }

        // Find the system/releaseId entry
        val releaseIdEntry = registryEntries.entries.find { (_, entry) ->
            entry.isSystem && entry.releaseId != null
        }?.value

        val serverReleaseId = releaseIdEntry?.releaseId
        if (serverReleaseId == null) {
            // No releaseId on server = compatible (graceful degradation)
            println("ℹ️ ReleaseIdChecker: No release ID found on server, assuming compatible")
            return ReleaseIdCheckResult.Compatible
        }

        val clientReleaseId = AppConfig.BUILT_IN_RELEASE_ID
        println("🔍 ReleaseIdChecker: Comparing client=$clientReleaseId with server=$serverReleaseId")

        return if (isCompatible(clientReleaseId, serverReleaseId)) {
            println("✅ ReleaseIdChecker: Client is compatible")
            ReleaseIdCheckResult.Compatible
        } else {
            println("⚠️ ReleaseIdChecker: Update required (client=$clientReleaseId < server=$serverReleaseId)")
            ReleaseIdCheckResult.UpdateRequired(serverReleaseId, clientReleaseId)
        }
    }

    /**
     * Checks if the client release ID is compatible with the server release ID.
     * Client must be >= server to be compatible.
     *
     * @param clientId The client's built-in release ID (e.g., "fb-123")
     * @param serverId The server's release ID (e.g., "fb-150")
     * @return true if compatible, false if update required
     */
    private fun isCompatible(clientId: String, serverId: String): Boolean {
        // Extract numeric portion: "fb-123" -> 123
        val clientNum = clientId.removePrefix("fb-").toIntOrNull()
        val serverNum = serverId.removePrefix("fb-").toIntOrNull()

        // If either can't be parsed, assume compatible (graceful degradation)
        if (clientNum == null || serverNum == null) {
            println("⚠️ ReleaseIdChecker: Could not parse release IDs, assuming compatible")
            return true
        }

        // Client must be >= server release ID
        return clientNum >= serverNum
    }
}
