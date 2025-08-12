package com.joebad.fastbreak.data.cache

/**
 * Generic cache interface for API GET request responses.
 * Uses full URL as the cache key and stores JSON string responses.
 */
interface ApiCache {
    /**
     * Retrieves cached response for the given URL.
     * @param url The full URL that was used for the GET request
     * @return The cached JSON response string, or null if not found
     */
    suspend fun get(url: String): String?
    
    /**
     * Stores a response for the given URL.
     * @param url The full URL that was used for the GET request
     * @param jsonResponse The JSON response string to cache
     */
    suspend fun put(url: String, jsonResponse: String)
    
    /**
     * Removes a cached response for the given URL.
     * @param url The full URL to remove from cache
     */
    suspend fun remove(url: String)
    
    /**
     * Clears all cached responses.
     */
    suspend fun clear()
    
    /**
     * Checks if a response is cached for the given URL.
     * @param url The full URL to check
     * @return true if cached, false otherwise
     */
    suspend fun contains(url: String): Boolean
}