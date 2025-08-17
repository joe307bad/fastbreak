package com.joebad.fastbreak.data.cache

import kotbase.Database
import kotbase.MutableDocument
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class TTLKotbaseApiCache(private val database: Database) {
    private val dataCollection = database.getCollection("ttl_cache_data") 
        ?: database.createCollection("ttl_cache_data")
    private val metadataCollection = database.getCollection("ttl_cache_metadata") 
        ?: database.createCollection("ttl_cache_metadata")
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    fun <T> storeCacheData(key: String, data: T, serializer: KSerializer<T>) {
        try {
            val jsonString = json.encodeToString(serializer, data)
            val doc = MutableDocument(key)
                .setString("data", jsonString)
                .setLong("timestamp", Clock.System.now().epochSeconds)
            dataCollection.save(doc)
        } catch (e: Exception) {
            println("Error storing cache data for key $key: ${e.message}")
        }
    }
    
    fun <T> getCacheData(key: String, deserializer: KSerializer<T>): T? {
        return try {
            dataCollection.getDocument(key)?.getString("data")?.let { jsonString ->
                json.decodeFromString(deserializer, jsonString)
            }
        } catch (e: Exception) {
            println("Error retrieving cache data for key $key: ${e.message}")
            null
        }
    }
    
    fun storeCacheMetadata(key: String, metadata: CacheMetadata) {
        try {
            val doc = MutableDocument("${key}_metadata")
                .setString("cachedAt", metadata.cachedAt.toString())
                .setString("expiresAt", metadata.expiresAt.toString())
            metadataCollection.save(doc)
        } catch (e: Exception) {
            println("Error storing cache metadata for key $key: ${e.message}")
        }
    }
    
    fun getCacheMetadata(key: String): CacheMetadata? {
        return try {
            metadataCollection.getDocument("${key}_metadata")?.let { doc ->
                val cachedAt = doc.getString("cachedAt")?.let { Instant.parse(it) }
                val expiresAt = doc.getString("expiresAt")?.let { Instant.parse(it) }
                
                if (cachedAt != null && expiresAt != null) {
                    CacheMetadata(cachedAt, expiresAt)
                } else null
            }
        } catch (e: Exception) {
            println("Error retrieving cache metadata for key $key: ${e.message}")
            null
        }
    }
    
    fun generateCacheKey(url: String): String {
        return url.replace(Regex("[^a-zA-Z0-9]"), "_")
    }
}