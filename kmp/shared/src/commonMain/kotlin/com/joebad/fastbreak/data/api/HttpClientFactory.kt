package com.joebad.fastbreak.data.api

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Factory for creating configured HTTP clients for API requests.
 */
object HttpClientFactory {
    /**
     * Creates a configured HttpClient with JSON serialization support,
     * timeout configuration, and logging.
     */
    fun create(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                })
            }

            // Configure timeouts for better error handling
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000 // 30 seconds
                connectTimeoutMillis = 15_000 // 15 seconds
                socketTimeoutMillis = 30_000 // 30 seconds
            }

            // Retry on transient failures. Cold-start on a fresh install often
            // hits DNS/connect timeouts on the first few requests; without
            // retryOnException those charts fail silently and only get
            // downloaded on the next refresh tap.
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 2)
                retryOnException(maxRetries = 3, retryOnTimeout = true)
                exponentialDelay()
            }
        }
    }
}
