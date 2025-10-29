package com.joebad.fastbreak.data.repository

import com.joebad.fastbreak.data.model.Registry
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
        private const val KEY_REGISTRY = "registry_data"
        private const val KEY_METADATA = "registry_metadata"
    }

    /**
     * Saves the registry to local storage.
     * The registry is serialized to JSON and stored as a string.
     *
     * @param registry The registry to save
     */
    fun saveRegistry(registry: Registry) {
        try {
            val jsonString = json.encodeToString(registry)
            settings.putString(KEY_REGISTRY, jsonString)
        } catch (e: SerializationException) {
            // Log error but don't crash - this is a storage issue
            println("Error saving registry: ${e.message}")
        }
    }

    /**
     * Retrieves the cached registry from local storage.
     *
     * @return The cached Registry, or null if not found or corrupted
     */
    fun getRegistry(): Registry? {
        return try {
            val jsonString = settings.getStringOrNull(KEY_REGISTRY) ?: return null
            json.decodeFromString<Registry>(jsonString)
        } catch (e: SerializationException) {
            // Corrupted data - return null and let the caller handle it
            println("Error reading registry: ${e.message}")
            null
        }
    }

    /**
     * Saves registry metadata (download time, version) to local storage.
     *
     * @param metadata The metadata to save
     */
    fun saveMetadata(metadata: RegistryMetadata) {
        try {
            val jsonString = json.encodeToString(metadata)
            settings.putString(KEY_METADATA, jsonString)
        } catch (e: SerializationException) {
            println("Error saving registry metadata: ${e.message}")
        }
    }

    /**
     * Retrieves the cached registry metadata from local storage.
     *
     * @return The cached RegistryMetadata, or null if not found or corrupted
     */
    fun getMetadata(): RegistryMetadata? {
        return try {
            val jsonString = settings.getStringOrNull(KEY_METADATA) ?: return null
            json.decodeFromString<RegistryMetadata>(jsonString)
        } catch (e: SerializationException) {
            println("Error reading registry metadata: ${e.message}")
            null
        }
    }

    /**
     * Clears all registry data and metadata from storage.
     * Useful for troubleshooting or resetting the app.
     */
    fun clearAll() {
        settings.remove(KEY_REGISTRY)
        settings.remove(KEY_METADATA)
    }

    /**
     * Checks if a registry is currently cached.
     *
     * @return true if a registry exists in storage
     */
    fun hasRegistry(): Boolean {
        return settings.hasKey(KEY_REGISTRY)
    }
}
