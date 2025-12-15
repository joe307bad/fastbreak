package com.joebad.fastbreak.domain.registry

import com.joebad.fastbreak.data.api.RegistryApi
import com.joebad.fastbreak.data.model.RegistryEntry
import com.joebad.fastbreak.data.model.RegistryMetadata
import com.joebad.fastbreak.data.repository.RegistryRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Manages registry lifecycle: downloading, caching, and determining when updates are needed.
 * Implements the 12-hour update policy.
 */
class RegistryManager(
    private val registryApi: RegistryApi,
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
     * @return Result containing the registry entries (either fresh or cached) or an error
     */
    suspend fun checkAndUpdateRegistry(): Result<Map<String, RegistryEntry>> = runCatching {
        println("═══════════════════════════════════════════════════════")
        println("🔍 RegistryManager.checkAndUpdateRegistry()")
        println("═══════════════════════════════════════════════════════")

        val metadata = registryRepository.getMetadata()
        val now = Clock.System.now()

        val shouldDownload = metadata == null || now - metadata.lastDownloadTime > UPDATE_INTERVAL

        println("   Current time: $now")
        if (metadata != null) {
            println("   Last download: ${metadata.lastDownloadTime}")
            println("   Time since last download: ${now - metadata.lastDownloadTime}")
            println("   Update interval: $UPDATE_INTERVAL")
        } else {
            println("   No metadata found (first run or cleared)")
        }
        println("   Should download: $shouldDownload")

        if (shouldDownload) {
            println("📡 Registry is stale or missing, fetching from server...")
            // Attempt to download new registry
            try {
                val entries = registryApi.fetchRegistry().getOrThrow()
                println("   ✅ Fetched ${entries.size} entries from server")

                // Save registry entries and metadata
                println("   💾 Saving to cache...")
                registryRepository.saveRegistryEntries(entries)
                registryRepository.saveMetadata(
                    RegistryMetadata(
                        lastDownloadTime = now,
                        registryVersion = "2.0"
                    )
                )
                println("   ✅ Registry cached successfully")

                entries
            } catch (e: Exception) {
                // Network or API error - rethrow so Container can show error and handle fallback
                println("   ❌ Failed to fetch registry: ${e.message}")
                throw e
            }
        } else {
            println("📦 Registry is fresh, using cached version...")
            // Return cached registry entries (not stale yet)
            val cachedEntries = registryRepository.getRegistryEntries()
            if (cachedEntries != null) {
                println("   ✅ Using ${cachedEntries.size} cached entries")
                cachedEntries
            } else {
                // Metadata exists but registry is missing - corrupted state, force refresh
                println("   ⚠️ Registry metadata found but registry data missing - forcing refresh")
                forceRefreshRegistry().getOrThrow()
            }
        }
    }

    /**
     * Forces a registry refresh regardless of when it was last updated.
     * Throws an exception on network errors (Container handles fallback to cache).
     *
     * @return Result containing the fresh registry entries or an error
     */
    suspend fun forceRefreshRegistry(): Result<Map<String, RegistryEntry>> = runCatching {
        println("🔧 RegistryManager.forceRefreshRegistry() - Starting")

        try {
            println("🌐 Calling registryApi.fetchRegistry()...")
            val entries = registryApi.fetchRegistry().getOrThrow()
            println("✅ API call successful - Received ${entries.size} entries")

            // Save registry entries and metadata
            println("💾 Saving registry entries to repository...")
            registryRepository.saveRegistryEntries(entries)
            registryRepository.saveMetadata(
                RegistryMetadata(
                    lastDownloadTime = Clock.System.now(),
                    registryVersion = "2.0"
                )
            )
            println("✅ Registry entries saved successfully")

            entries
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
     * Gets the cached registry entries without checking for updates.
     *
     * @return The cached registry entries, or null if not found
     */
    fun getCachedRegistryEntries(): Map<String, RegistryEntry>? {
        println("📖 RegistryManager.getCachedRegistryEntries()")
        val entries = registryRepository.getRegistryEntries()
        println("   Result: ${entries?.size ?: 0} entries")
        return entries
    }

    /**
     * Checks if the cached registry is stale (>12 hours old).
     *
     * @return true if registry is stale or missing, false otherwise
     */
    fun isRegistryStale(): Boolean {
        val metadata = registryRepository.getMetadata()
        if (metadata == null) {
            println("🔍 RegistryManager.isRegistryStale() = true (no metadata)")
            return true
        }
        val now = Clock.System.now()
        val isStale = now - metadata.lastDownloadTime > UPDATE_INTERVAL
        println("🔍 RegistryManager.isRegistryStale() = $isStale (age: ${now - metadata.lastDownloadTime})")
        return isStale
    }

    /**
     * Clears all registry data and metadata from storage.
     * Useful for troubleshooting or resetting the app.
     */
    fun clearRegistry() {
        registryRepository.clearAll()
    }

    /**
     * Checks if registry entries are currently cached.
     *
     * @return true if registry entries exist in storage
     */
    fun hasRegistryEntries(): Boolean {
        return registryRepository.hasRegistryEntries()
    }
}
