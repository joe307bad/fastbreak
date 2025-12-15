package com.joebad.fastbreak.data.api

import com.joebad.fastbreak.config.AppConfig
import com.joebad.fastbreak.data.model.RegistryEntry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

/**
 * API for fetching registry data from the server.
 * Fetches data from CloudFront CDN.
 */
class RegistryApi(
    private val httpClient: HttpClient = HttpClientFactory.create()
) {
    companion object {
        const val BASE_URL = "https://d2jyizt5xogu23.cloudfront.net"
        private const val REGISTRY_ENDPOINT = "/registry"
        private const val DEV_PREFIX = "dev/"
    }

    /**
     * Fetches the registry from the server.
     * Filters entries based on dev_mode setting:
     * - dev_mode=true: only entries with "dev/" prefix
     * - dev_mode=false: only entries without "dev/" prefix
     * @return Result containing Map of file_key to RegistryEntry
     */
    suspend fun fetchRegistry(): Result<Map<String, RegistryEntry>> = runCatching {
        val url = "$BASE_URL$REGISTRY_ENDPOINT"
        val devMode = AppConfig.DEV_MODE
        println("🌐 RegistryApi.fetchRegistry() - Making HTTP GET request")
        println("   URL: $url")
        println("   Dev Mode: $devMode")

        try {
            val response = httpClient.get(url)
            println("✅ HTTP request successful - Status: ${response.status}")

            // Parse as Map<String, RegistryEntry>
            val registryMap: Map<String, RegistryEntry> = response.body()
            println("✅ Response parsed successfully")
            println("   Total entries: ${registryMap.size}")

            // Filter based on dev_mode
            val filteredEntries = registryMap.filter { (key, _) ->
                val isDevEntry = key.startsWith(DEV_PREFIX)
                if (devMode) isDevEntry else !isDevEntry
            }
            println("   Filtered entries (dev_mode=$devMode): ${filteredEntries.size}")
            filteredEntries.forEach { (key, entry) ->
                println("   - $key: ${entry.title}")
            }

            filteredEntries
        } catch (e: Exception) {
            println("❌ HTTP request FAILED")
            println("   Error: ${e.message}")
            println("   Exception type: ${e::class.simpleName}")

            // Create a detailed error message including the endpoint
            val errorMessage = buildString {
                append("Network request failed\n")
                append("Endpoint: $REGISTRY_ENDPOINT\n")
                if (e.message != null) {
                    append("Error: ${e.message}")
                }
            }

            throw Exception(errorMessage, e)
        }
    }
}
