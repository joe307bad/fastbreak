package com.joebad.fastbreak.data.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.datetime.Clock
import kotlinx.serialization.KSerializer

class TTLCachedHttpClient(
    private val baseCachedClient: CachedHttpClient,
    private val ttlCache: TTLKotbaseApiCache
) {
    suspend fun <T> get(
        urlString: String,
        deserializer: KSerializer<T>,
        expirationStrategy: CacheExpirationStrategy
    ): TTLCachedResponse<T> {
        
        val currentTime = Clock.System.now()
        val cacheKey = ttlCache.generateCacheKey(urlString)
        
        // Check for existing cached data with metadata
        val cachedMetadata = ttlCache.getCacheMetadata(cacheKey)
        val existingData = if (cachedMetadata != null) {
            ttlCache.getCacheData<T>(cacheKey, deserializer)
        } else null
        
        val isExpired = cachedMetadata?.let { 
            currentTime > it.expiresAt 
        } ?: true
        
        return when {
            // Cache hit and not expired
            !isExpired && existingData != null -> {
                TTLCachedResponse(
                    data = existingData,
                    isSuccess = true,
                    isFromCache = true,
                    rawJson = null,
                    error = null,
                    cachedAt = cachedMetadata?.cachedAt,
                    expiresAt = cachedMetadata?.expiresAt,
                    isExpired = false,
                    isRefreshing = false
                )
            }
            
            // Cache expired but data exists - return stale + refresh
            isExpired && existingData != null -> {
                // Trigger background refresh
                CoroutineScope(Dispatchers.Default).async {
                    refreshCacheData(urlString, deserializer, expirationStrategy, cacheKey)
                }
                
                TTLCachedResponse(
                    data = existingData,
                    isSuccess = true,
                    isFromCache = true,
                    rawJson = null,
                    error = null,
                    cachedAt = cachedMetadata?.cachedAt,
                    expiresAt = cachedMetadata?.expiresAt,
                    isExpired = true,
                    isRefreshing = true
                )
            }
            
            // No cache data - fetch fresh
            else -> {
                fetchFreshData<T>(urlString, deserializer, expirationStrategy, cacheKey)
            }
        }
    }
    
    private suspend fun <T> refreshCacheData(
        urlString: String,
        deserializer: KSerializer<T>,
        expirationStrategy: CacheExpirationStrategy,
        cacheKey: String
    ) {
        try {
            val response = baseCachedClient.getTyped<T>(
                urlString = urlString,
                deserializer = deserializer
            )
            if (response.isSuccess && response.data != null) {
                val currentTime = Clock.System.now()
                val expiresAt = expirationStrategy.calculateExpirationTime(currentTime)
                
                ttlCache.storeCacheData<T>(cacheKey, response.data!!, deserializer)
                ttlCache.storeCacheMetadata(cacheKey, CacheMetadata(currentTime, expiresAt))
                
                println("Background refresh completed for: $urlString")
            }
        } catch (e: Exception) {
            println("Background refresh failed for $urlString: ${e.message}")
        }
    }
    
    private suspend fun <T> fetchFreshData(
        urlString: String,
        deserializer: KSerializer<T>,
        expirationStrategy: CacheExpirationStrategy,
        cacheKey: String
    ): TTLCachedResponse<T> {
        return try {
            val response = baseCachedClient.getTyped<T>(
                urlString = urlString,
                deserializer = deserializer
            )
            
            if (response.isSuccess && response.data != null) {
                val currentTime = Clock.System.now()
                val expiresAt = expirationStrategy.calculateExpirationTime(currentTime)
                
                // Store fresh data and metadata
                ttlCache.storeCacheData<T>(cacheKey, response.data!!, deserializer)
                ttlCache.storeCacheMetadata(cacheKey, CacheMetadata(currentTime, expiresAt))
                
                TTLCachedResponse(
                    data = response.data,
                    isSuccess = true,
                    isFromCache = response.isFromCache,
                    rawJson = response.rawJson,
                    error = null,
                    cachedAt = currentTime,
                    expiresAt = expiresAt,
                    isExpired = false,
                    isRefreshing = false
                )
            } else {
                TTLCachedResponse(
                    data = null,
                    isSuccess = false,
                    isFromCache = false,
                    rawJson = null,
                    error = response.error ?: "Unknown error",
                    cachedAt = null,
                    expiresAt = null,
                    isExpired = false,
                    isRefreshing = false
                )
            }
        } catch (e: Exception) {
            TTLCachedResponse(
                data = null,
                isSuccess = false,
                isFromCache = false,
                rawJson = null,
                error = "Unexpected error: ${e.message}",
                cachedAt = null,
                expiresAt = null,
                isExpired = false,
                isRefreshing = false
            )
        }
    }
    
    suspend fun <T> set(
        urlString: String,
        data: T,
        serializer: KSerializer<T>,
        expirationStrategy: CacheExpirationStrategy
    ) {
        try {
            val currentTime = Clock.System.now()
            val cacheKey = ttlCache.generateCacheKey(urlString)
            val expiresAt = expirationStrategy.calculateExpirationTime(currentTime)
            
            ttlCache.storeCacheData<T>(cacheKey, data, serializer)
            ttlCache.storeCacheMetadata(cacheKey, CacheMetadata(currentTime, expiresAt))
        } catch (e: Exception) {
            println("Error setting cache for $urlString: ${e.message}")
        }
    }
    
    suspend fun <T> updateProperty(
        urlString: String,
        deserializer: KSerializer<T>,
        expirationStrategy: CacheExpirationStrategy,
        updateFunction: (T?) -> T?
    ): Boolean {
        return try {
            val cacheKey = ttlCache.generateCacheKey(urlString)
            
            // Get existing cached data
            val existingData = ttlCache.getCacheData<T>(cacheKey, deserializer)
            
            // Apply the update function
            val updatedData = updateFunction(existingData)
            
            if (updatedData != null) {
                // Store the updated data with preserved expiration
                val existingMetadata = ttlCache.getCacheMetadata(cacheKey)
                val currentTime = Clock.System.now()
                val expiresAt = existingMetadata?.expiresAt ?: expirationStrategy.calculateExpirationTime(currentTime)
                
                ttlCache.storeCacheData<T>(cacheKey, updatedData, deserializer)
                ttlCache.storeCacheMetadata(cacheKey, CacheMetadata(currentTime, expiresAt))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            println("Error updating cache property for $urlString: ${e.message}")
            false
        }
    }
}