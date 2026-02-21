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
        coerceInputValues = true
    }

    companion object {
        private const val KEY_TOPICS_DATA = "topics_data"
        private const val KEY_TOPICS_UPDATED_AT = "topics_updated_at"
        private const val KEY_TOPICS_VIEWED = "topics_viewed"
        private const val KEY_COLLAPSED_INDICES = "topics_collapsed_indices"
        private const val KEY_READ_INDICES = "topics_read_indices"
        private const val KEY_TOPICS_DATE = "topics_date"
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
        return try {
            val jsonString = settings.getStringOrNull(KEY_TOPICS_DATA) ?: return null
            json.decodeFromString<TopicsResponse>(jsonString)
        } catch (e: SerializationException) {
            println("❌ TopicsRepository: Error reading topics: ${e.message}")
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
        settings.remove(KEY_TOPICS_VIEWED)
        settings.remove(KEY_COLLAPSED_INDICES)
        settings.remove(KEY_READ_INDICES)
        settings.remove(KEY_TOPICS_DATE)
        println("   ✅ Cleared topics data")
    }

    /**
     * Checks if topics have been viewed by the user.
     *
     * @return true if topics have been viewed, false otherwise
     */
    fun hasBeenViewed(): Boolean {
        return settings.getBoolean(KEY_TOPICS_VIEWED, false)
    }

    /**
     * Marks topics as viewed by the user.
     */
    fun markAsViewed() {
        println("👁️ TopicsRepository.markAsViewed()")
        settings.putBoolean(KEY_TOPICS_VIEWED, true)
    }

    /**
     * Resets the viewed state (called when new topics are downloaded).
     */
    fun resetViewed() {
        println("🔄 TopicsRepository.resetViewed()")
        settings.putBoolean(KEY_TOPICS_VIEWED, false)
    }

    /**
     * Saves the collapsed narrative indices.
     *
     * @param indices The set of collapsed narrative indices
     * @param topicsDate The date of the current topics (used to invalidate on refresh)
     */
    fun saveCollapsedIndices(indices: Set<Int>, topicsDate: String) {
        val indicesStr = indices.joinToString(",")
        settings.putString(KEY_COLLAPSED_INDICES, indicesStr)
        settings.putString(KEY_TOPICS_DATE, topicsDate)
    }

    /**
     * Retrieves the collapsed narrative indices.
     *
     * @param topicsDate The date of the current topics
     * @return The set of collapsed indices, or empty set if none or date mismatch
     */
    fun getCollapsedIndices(topicsDate: String): Set<Int> {
        val savedDate = settings.getStringOrNull(KEY_TOPICS_DATE) ?: return emptySet()
        if (savedDate != topicsDate) {
            // Topics changed, clear old state
            clearCollapsedAndReadState()
            return emptySet()
        }
        val indicesStr = settings.getStringOrNull(KEY_COLLAPSED_INDICES) ?: return emptySet()
        return indicesStr.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    /**
     * Saves the set of narratives that have been "read" (collapsed at least once).
     *
     * @param indices The set of read narrative indices
     * @param topicsDate The date of the current topics
     */
    fun saveReadIndices(indices: Set<Int>, topicsDate: String) {
        val indicesStr = indices.joinToString(",")
        settings.putString(KEY_READ_INDICES, indicesStr)
        settings.putString(KEY_TOPICS_DATE, topicsDate)
    }

    /**
     * Retrieves the set of narratives that have been "read" (collapsed at least once).
     *
     * @param topicsDate The date of the current topics
     * @return The set of read indices, or empty set if none or date mismatch
     */
    fun getReadIndices(topicsDate: String): Set<Int> {
        val savedDate = settings.getStringOrNull(KEY_TOPICS_DATE) ?: return emptySet()
        if (savedDate != topicsDate) {
            // Topics changed, clear old state
            clearCollapsedAndReadState()
            return emptySet()
        }
        val indicesStr = settings.getStringOrNull(KEY_READ_INDICES) ?: return emptySet()
        return indicesStr.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    /**
     * Clears collapsed and read state (called when new topics are downloaded).
     */
    fun clearCollapsedAndReadState() {
        println("🗑️ TopicsRepository.clearCollapsedAndReadState()")
        settings.remove(KEY_COLLAPSED_INDICES)
        settings.remove(KEY_READ_INDICES)
        settings.remove(KEY_TOPICS_DATE)
    }
}
