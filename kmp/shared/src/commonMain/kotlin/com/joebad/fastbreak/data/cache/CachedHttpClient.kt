package com.joebad.fastbreak.data.cache

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HTTP client wrapper that adds caching for GET requests only.
 * Uses ApiCache to store and retrieve JSON responses based on full URL.
 */
class CachedHttpClient(
    private val httpClient: HttpClient,
    private val apiCache: ApiCache
) {
    
    /**
     * Performs a cached GET request.
     * First checks cache, then makes HTTP request if cache miss.
     * 
     * @param urlString The complete URL for the GET request
     * @param useCache Whether to use cache (default: true)
     * @param block Additional request configuration
     * @return CachedResponse containing the JSON data and cache status
     */
    suspend fun get(
        urlString: String,
        useCache: Boolean = true,
        block: HttpRequestBuilder.() -> Unit = {}
    ): CachedResponse {
        return withContext(Dispatchers.Default) {
            val fullUrl = buildFullUrl(urlString, block)
            
            // Check cache first if enabled
            if (useCache) {
                apiCache.get(fullUrl)?.let { cachedResponse ->
                    return@withContext CachedResponse(
                        data = cachedResponse,
                        isFromCache = true,
                        httpStatus = null
                    )
                }
            }
            
            // Cache miss or cache disabled, make HTTP request
            try {
                val response = httpClient.get {
                    url(urlString)
                    block()
                }
                
                when (response.status) {
                    HttpStatusCode.OK -> {
                        val jsonData = response.body<String>()
                        
                        // Store in cache
                        if (useCache) {
                            apiCache.put(fullUrl, jsonData)
                        }
                        
                        CachedResponse(
                            data = jsonData,
                            isFromCache = false,
                            httpStatus = response.status
                        )
                    }
                    else -> {
                        CachedResponse(
                            data = null,
                            isFromCache = false,
                            httpStatus = response.status,
                            error = "HTTP ${response.status.value}: ${response.status.description}"
                        )
                    }
                }
            } catch (e: Exception) {
                CachedResponse(
                    data = null,
                    isFromCache = false,
                    httpStatus = null,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Performs a cached GET request and attempts to parse as JSON of type T.
     * 
     * @param urlString The complete URL for the GET request
     * @param useCache Whether to use cache (default: true)
     * @param block Additional request configuration
     * @return CachedResponse containing the parsed data and cache status
     */
    suspend fun <T> getTyped(
        urlString: String,
        useCache: Boolean = true,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
        block: HttpRequestBuilder.() -> Unit = {}
    ): CachedTypedResponse<T> {
        return withContext(Dispatchers.Default) {
            val fullUrl = buildFullUrl(urlString, block)
            
            // Check cache first if enabled
            if (useCache) {
                apiCache.get(fullUrl)?.let { cachedResponse ->
                    return@withContext try {
                        val parsedData = kotlinx.serialization.json.Json.decodeFromString(deserializer, cachedResponse)
                        CachedTypedResponse(
                            data = parsedData,
                            rawJson = cachedResponse,
                            isFromCache = true,
                            httpStatus = null
                        )
                    } catch (e: Exception) {
                        CachedTypedResponse<T>(
                            data = null,
                            rawJson = cachedResponse,
                            isFromCache = true,
                            httpStatus = null,
                            error = "JSON parsing error: ${e.message}"
                        )
                    }
                }
            }
            
            // Cache miss or cache disabled, make HTTP request
            try {
                val response = httpClient.get {
                    url(urlString)
                    block()
                }
                
                when (response.status) {
                    HttpStatusCode.OK -> {
                        val jsonData = response.body<String>()
                        
                        // Store in cache
                        if (useCache) {
                            apiCache.put(fullUrl, jsonData)
                        }
                        
                        try {
                            val parsedData = kotlinx.serialization.json.Json.decodeFromString(deserializer, jsonData)
                            CachedTypedResponse(
                                data = parsedData,
                                rawJson = jsonData,
                                isFromCache = false,
                                httpStatus = response.status
                            )
                        } catch (e: Exception) {
                            CachedTypedResponse<T>(
                                data = null,
                                rawJson = jsonData,
                                isFromCache = false,
                                httpStatus = response.status,
                                error = "JSON parsing error: ${e.message}"
                            )
                        }
                    }
                    else -> {
                        CachedTypedResponse<T>(
                            data = null,
                            rawJson = null,
                            isFromCache = false,
                            httpStatus = response.status,
                            error = "HTTP ${response.status.value}: ${response.status.description}"
                        )
                    }
                }
            } catch (e: Exception) {
                CachedTypedResponse<T>(
                    data = null,
                    rawJson = null,
                    isFromCache = false,
                    httpStatus = null,
                    error = "Network error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Invalidates cache for specific URL
     */
    suspend fun invalidateCache(urlString: String, block: HttpRequestBuilder.() -> Unit = {}) {
        val fullUrl = buildFullUrl(urlString, block)
        apiCache.remove(fullUrl)
    }
    
    /**
     * Clears all cached responses
     */
    suspend fun clearCache() {
        apiCache.clear()
    }
    
    /**
     * Builds the full URL including query parameters for cache key generation
     */
    internal fun buildFullUrl(baseUrl: String, block: HttpRequestBuilder.() -> Unit): String {
        val builder = HttpRequestBuilder().apply {
            url(baseUrl)
            block()
        }
        
        val params = builder.url.parameters
        return if (params.isEmpty()) {
            baseUrl
        } else {
            val queryString = params.entries()
                .sortedBy { it.key } // Sort for consistent cache keys
                .joinToString("&") { (key, values) ->
                    values.joinToString("&") { value -> "$key=$value" }
                }
            "$baseUrl?$queryString"
        }
    }
}

/**
 * Response wrapper for cached HTTP requests
 */
data class CachedResponse(
    val data: String?,
    val isFromCache: Boolean,
    val httpStatus: HttpStatusCode?,
    val error: String? = null
) {
    val isSuccess: Boolean get() = data != null && error == null
}

/**
 * Response wrapper for cached HTTP requests with typed data
 */
data class CachedTypedResponse<T>(
    val data: T?,
    val rawJson: String?,
    val isFromCache: Boolean,
    val httpStatus: HttpStatusCode?,
    val error: String? = null
) {
    val isSuccess: Boolean get() = data != null && error == null
}