package com.joebad.fastbreak.data.repository

import com.joebad.fastbreak.data.model.RegistryEntry
import com.joebad.fastbreak.data.model.RegistryMetadata
import com.russhwolf.settings.Settings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for persisting and retrieving registry data and metadata.
 * Uses multiplatform-settings for cross-platform storage (SharedPreferences on Android, NSUserDefaults on iOS).
 */
class RegistryRepository(
    private val settings: Settings
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        private const val KEY_REGISTRY_ENTRIES = "registry_entries"
        private const val KEY_METADATA = "registry_metadata"
    }

    /**
     * Saves the registry entries to local storage.
     * The entries are serialized to JSON and stored as a string.
     *
     * @param entries Map of file_key to RegistryEntry
     */
    fun saveRegistryEntries(entries: Map<String, RegistryEntry>) {
        println("💾 RegistryRepository.saveRegistryEntries()")
        println("   Entries count: ${entries.size}")
        entries.forEach { (key, entry) ->
            println("   - $key: ${entry.title} (updatedAt: ${entry.updatedAt})")
        }
        // Why: previously caught SerializationException silently here, which let a
        // failed save look identical to a successful one. The caller (RegistryManager)
        // already wraps this in runCatching, so propagating the exception lets it
        // surface as a refresh failure instead of producing a "succeeded but cache
        // is empty" state.
        val jsonString = json.encodeToString(entries)
        println("   JSON size: ${jsonString.length} chars")
        settings.putString(KEY_REGISTRY_ENTRIES, jsonString)
        println("   ✅ Saved successfully")
    }

    /**
     * Retrieves the cached registry entries from local storage.
     *
     * @return The cached registry entries, or null if not found or corrupted
     */
    fun getRegistryEntries(): Map<String, RegistryEntry>? {
        println("📖 RegistryRepository.getRegistryEntries()")
        return try {
            val jsonString = settings.getStringOrNull(KEY_REGISTRY_ENTRIES)
            if (jsonString == null) {
                println("   No cached entries found (key not present)")
                return null
            }
            println("   Found cached JSON: ${jsonString.length} chars")
            val entries = json.decodeFromString<Map<String, RegistryEntry>>(jsonString)
            println("   ✅ Decoded ${entries.size} entries:")
            entries.forEach { (key, entry) ->
                println("   - $key: ${entry.title} (updatedAt: ${entry.updatedAt})")
            }
            entries
        } catch (e: SerializationException) {
            println("   ❌ Error reading registry entries: ${e.message}")
            null
        }
    }

    /**
     * Saves registry metadata (download time, version) to local storage.
     *
     * @param metadata The metadata to save
     */
    fun saveMetadata(metadata: RegistryMetadata) {
        println("💾 RegistryRepository.saveMetadata()")
        println("   lastDownloadTime: ${metadata.lastDownloadTime}")
        println("   registryVersion: ${metadata.registryVersion}")
        // Why: silent failure here was the most likely explanation for the "Stale"
        // indicator persisting after a refresh — metadata never lands, so
        // isRegistryStale() returns true even though entries were just fetched.
        // Propagate so it surfaces as a refresh failure.
        val jsonString = json.encodeToString(metadata)
        settings.putString(KEY_METADATA, jsonString)
        println("   ✅ Metadata saved successfully")
    }

    /**
     * Retrieves the cached registry metadata from local storage.
     *
     * @return The cached RegistryMetadata, or null if not found or corrupted
     */
    fun getMetadata(): RegistryMetadata? {
        println("📖 RegistryRepository.getMetadata()")
        return try {
            val jsonString = settings.getStringOrNull(KEY_METADATA)
            if (jsonString == null) {
                println("   No cached metadata found (key not present)")
                return null
            }
            val metadata = json.decodeFromString<RegistryMetadata>(jsonString)
            println("   ✅ Found cached metadata:")
            println("   lastDownloadTime: ${metadata.lastDownloadTime}")
            println("   registryVersion: ${metadata.registryVersion}")
            metadata
        } catch (e: SerializationException) {
            println("   ❌ Error reading registry metadata: ${e.message}")
            null
        }
    }

    /**
     * Clears all registry data and metadata from storage.
     * Useful for troubleshooting or resetting the app.
     */
    fun clearAll() {
        println("🗑️ RegistryRepository.clearAll()")
        settings.remove(KEY_REGISTRY_ENTRIES)
        settings.remove(KEY_METADATA)
        println("   ✅ Cleared registry entries and metadata")
    }

    /**
     * Checks if registry entries are currently cached.
     *
     * @return true if registry entries exist in storage
     */
    fun hasRegistryEntries(): Boolean {
        val hasEntries = settings.hasKey(KEY_REGISTRY_ENTRIES)
        println("📖 RegistryRepository.hasRegistryEntries() = $hasEntries")
        return hasEntries
    }
}
