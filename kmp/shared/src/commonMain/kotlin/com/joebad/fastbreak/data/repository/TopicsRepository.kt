package com.joebad.fastbreak.data.repository

import com.joebad.fastbreak.data.model.TopicsResponse
import com.russhwolf.settings.Settings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Repository for persisting and retrieving topics data.
 * Uses multiplatform-settings for cross-platform storage.
 */
class TopicsRepository(
    private val settings: Settings
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        private const val KEY_TOPICS_DATA = "topics_data"
        private const val KEY_TOPICS_UPDATED_AT = "topics_updated_at"
    }

    /**
     * Saves topics data to local storage.
     *
     * @param topics The topics response to save
     * @param updatedAt The timestamp from the registry entry
     */
    fun saveTopics(topics: TopicsResponse, updatedAt: Instant) {
        println("💾 TopicsRepository.saveTopics()")
        println("   Date: ${topics.date}")
        println("   Narratives count: ${topics.narratives.size}")
        try {
            val jsonString = json.encodeToString(topics)
            settings.putString(KEY_TOPICS_DATA, jsonString)
            settings.putString(KEY_TOPICS_UPDATED_AT, updatedAt.toString())
            println("   ✅ Saved successfully")
        } catch (e: SerializationException) {
            println("   ❌ Error saving topics: ${e.message}")
        }
    }

    /**
     * Retrieves cached topics data from local storage.
     *
     * @return The cached topics, or null if not found or corrupted
     */
    fun getTopics(): TopicsResponse? {
        println("📖 TopicsRepository.getTopics()")
        return try {
            val jsonString = settings.getStringOrNull(KEY_TOPICS_DATA)
            if (jsonString == null) {
                println("   No cached topics found")
                return null
            }
            val topics = json.decodeFromString<TopicsResponse>(jsonString)
            println("   ✅ Found cached topics: ${topics.narratives.size} narratives")
            topics.narratives.forEachIndexed { i, n ->
                println("   [$i] league='${n.league}' title='${n.title.take(30)}'")
            }
            topics
        } catch (e: SerializationException) {
            println("   ❌ Error reading topics: ${e.message}")
            null
        }
    }

    /**
     * Gets the timestamp when topics were last updated.
     *
     * @return The last update timestamp, or null if not found
     */
    fun getUpdatedAt(): Instant? {
        return try {
            val timestampStr = settings.getStringOrNull(KEY_TOPICS_UPDATED_AT)
            timestampStr?.let { Instant.parse(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if topics need updating based on registry timestamp.
     *
     * @param registryUpdatedAt The timestamp from the registry entry
     * @return true if topics need to be downloaded
     */
    fun needsUpdate(registryUpdatedAt: Instant): Boolean {
        val cachedUpdatedAt = getUpdatedAt()
        if (cachedUpdatedAt == null) {
            println("🔍 Topics: No cached data, needs update")
            return true
        }
        val needsUpdate = registryUpdatedAt > cachedUpdatedAt
        println("🔍 Topics:")
        println("   Registry timestamp: $registryUpdatedAt")
        println("   Cached timestamp:   $cachedUpdatedAt")
        println("   Needs update: $needsUpdate")
        return needsUpdate
    }

    /**
     * Clears cached topics data.
     */
    fun clear() {
        println("🗑️ TopicsRepository.clear()")
        settings.remove(KEY_TOPICS_DATA)
        settings.remove(KEY_TOPICS_UPDATED_AT)
        println("   ✅ Cleared topics data")
    }
}
