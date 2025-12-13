package com.joebad.fastbreak.data.api

import com.joebad.fastbreak.data.model.Registry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

/**
 * API for fetching registry data from the server.
 * Fetches data from CloudFront CDN.
 */
class MockRegistryApi(
    private val httpClient: HttpClient = HttpClientFactory.create()
) {
    companion object {
        private const val BASE_URL = "https://d2jyizt5xogu23.cloudfront.net/dev"
        private const val REGISTRY_ENDPOINT = "/registry.json"
    }

    /**
     * Fetches the registry from the mock server.
     * @return Result containing the Registry or an error
     */
    suspend fun fetchRegistry(): Result<Registry> = runCatching {
        val url = "$BASE_URL$REGISTRY_ENDPOINT"
        println("🌐 MockRegistryApi.fetchRegistry() - Making HTTP GET request")
        println("   URL: $url")

        try {
            val response = httpClient.get(url)
            println("✅ HTTP request successful - Status: ${response.status}")

            val registry: Registry = response.body()
            println("✅ Response parsed successfully")
            println("   Version: ${registry.version}")
            println("   Last Updated: ${registry.lastUpdated}")
            println("   Charts count: ${registry.charts.size}")

            registry
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
