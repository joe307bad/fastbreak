package com.joebad.fastbreak.domain.registry

import com.joebad.fastbreak.data.api.MockRegistryApi
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.data.model.RegistryMetadata
import com.joebad.fastbreak.data.repository.RegistryRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Manages registry lifecycle: downloading, caching, and determining when updates are needed.
 * Implements the 12-hour update policy.
 */
class RegistryManager(
    private val mockRegistryApi: MockRegistryApi,
    private val registryRepository: RegistryRepository
) {
    companion object {
        /**
         * Registry update interval - registry will be refreshed after this duration
         */
        private val UPDATE_INTERVAL = 12.hours
    }

    /**
     * Checks if the registry needs updating (>12 hours old or missing) and updates if needed.
     * Throws an exception on network errors (Container handles fallback to cache).
     *
     * @return Result containing the registry (either fresh or cached) or an error
     */
    suspend fun checkAndUpdateRegistry(): Result<Registry> = runCatching {
        val metadata = registryRepository.getMetadata()
        val now = Clock.System.now()

        val shouldDownload = metadata == null || now - metadata.lastDownloadTime > UPDATE_INTERVAL

        if (shouldDownload) {
            // Attempt to download new registry
            try {
                val registry = mockRegistryApi.fetchRegistry().getOrThrow()

                // Save registry and metadata
                registryRepository.saveRegistry(registry)
                registryRepository.saveMetadata(
                    RegistryMetadata(
                        lastDownloadTime = now,
                        registryVersion = registry.version
                    )
                )

                registry
            } catch (e: Exception) {
                // Network or API error - rethrow so Container can show error and handle fallback
                println("Failed to fetch registry: ${e.message}")
                throw e
            }
        } else {
            // Return cached registry (not stale yet)
            val cachedRegistry = registryRepository.getRegistry()
            if (cachedRegistry != null) {
                cachedRegistry
            } else {
                // Metadata exists but registry is missing - corrupted state, force refresh
                println("Registry metadata found but registry data missing - forcing refresh")
                forceRefreshRegistry().getOrThrow()
            }
        }
    }

    /**
     * Forces a registry refresh regardless of when it was last updated.
     * Throws an exception on network errors (Container handles fallback to cache).
     *
     * @return Result containing the fresh registry or an error
     */
    suspend fun forceRefreshRegistry(): Result<Registry> = runCatching {
        println("🔧 RegistryManager.forceRefreshRegistry() - Starting")

        try {
            println("🌐 Calling mockRegistryApi.fetchRegistry()...")
            val registry = mockRegistryApi.fetchRegistry().getOrThrow()
            println("✅ API call successful - Received registry version: ${registry.version}")
            println("   Registry contains ${registry.charts.size} charts")
            println("   Last updated: ${registry.lastUpdated}")

            // Save registry and metadata
            println("💾 Saving registry to repository...")
            registryRepository.saveRegistry(registry)
            registryRepository.saveMetadata(
                RegistryMetadata(
                    lastDownloadTime = Clock.System.now(),
                    registryVersion = registry.version
                )
            )
            println("✅ Registry saved successfully")

            registry
        } catch (e: Exception) {
            // Network or API error - rethrow so Container can show error and handle fallback
            println("❌ API call FAILED with exception: ${e.message}")
            println("   Exception type: ${e::class.simpleName}")
            println("   Stack trace: ${e.stackTraceToString()}")
            throw e
        }
    }

    /**
     * Gets the current registry metadata (for diagnostics).
     *
     * @return The cached metadata, or null if not found
     */
    fun getMetadata(): RegistryMetadata? {
        return registryRepository.getMetadata()
    }

    /**
     * Gets the cached registry without checking for updates.
     *
     * @return The cached registry, or null if not found
     */
    fun getCachedRegistry(): Registry? {
        return registryRepository.getRegistry()
    }

    /**
     * Checks if the cached registry is stale (>12 hours old).
     *
     * @return true if registry is stale or missing, false otherwise
     */
    fun isRegistryStale(): Boolean {
        val metadata = registryRepository.getMetadata() ?: return true
        val now = Clock.System.now()
        return now - metadata.lastDownloadTime > UPDATE_INTERVAL
    }

    /**
     * Clears all registry data and metadata from storage.
     * Useful for troubleshooting or resetting the app.
     */
    fun clearRegistry() {
        registryRepository.clearAll()
    }

    /**
     * Checks if a registry is currently cached.
     *
     * @return true if a registry exists in storage
     */
    fun hasRegistry(): Boolean {
        return registryRepository.hasRegistry()
    }
}
