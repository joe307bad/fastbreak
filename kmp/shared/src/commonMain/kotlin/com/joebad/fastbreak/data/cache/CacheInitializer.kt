package com.joebad.fastbreak.data.cache

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotbase.Database
import kotlinx.serialization.json.Json

object CacheInitializer {
    /**
     * Creates and initializes a Kotbase database for caching.
     * Returns null if initialization fails.
     */
    private fun initializeDatabase(databaseName: String = "fastbreak_cache"): Database {
        return Database(databaseName)
    }

    /**
     * Creates and configures an HTTP client for API calls.
     * Returns null if initialization fails.
     */
    private fun initializeHttpClient(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
            }
        }
    }

    /**
     * Initializes both database and HTTP client.
     * Returns a pair of (database, httpClient), where either can be null if initialization failed.
     */
    fun initializeCache(databaseName: String = "fastbreak_cache"): Pair<Database, HttpClient> {
        val database = initializeDatabase(databaseName)
        val httpClient = initializeHttpClient()
        return Pair(database, httpClient)
    }
}