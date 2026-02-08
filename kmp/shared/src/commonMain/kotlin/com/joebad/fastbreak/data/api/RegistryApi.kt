package com.joebad.fastbreak.data.api

import com.joebad.fastbreak.config.AppConfig
import com.joebad.fastbreak.data.model.RegistryEntry
import com.joebad.fastbreak.logging.SentryLogger
import com.joebad.fastbreak.logging.SentryLevel
import com.joebad.fastbreak.logging.SpanStatus
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.DurationUnit

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

        val startTime = Clock.System.now()
        val spanId = SentryLogger.startSpan("http.client", "GET /registry")

        SentryLogger.addBreadcrumb(
            category = "network",
            message = "Fetching registry",
            data = mapOf("url" to url, "devMode" to devMode)
        )

        try {
            val response = httpClient.get(url)
            val requestDurationMs = (Clock.System.now() - startTime).toDouble(DurationUnit.MILLISECONDS)

            println("✅ HTTP request successful - Status: ${response.status}")
            println("   Request duration: ${requestDurationMs.toLong()}ms")

            SentryLogger.addBreadcrumb(
                category = "network",
                message = "Registry fetch completed",
                data = mapOf(
                    "status" to response.status.value,
                    "durationMs" to requestDurationMs.toLong()
                )
            )

            // Parse as JsonObject, handling individual entry errors gracefully
            val jsonObject: JsonObject = response.body()
            val registryMap = jsonObject.entries.mapNotNull { (key, jsonElement) ->
                try {
                    // Try to deserialize this entry
                    val entryObject = jsonElement.jsonObject

                    val titleStr = entryObject["title"]?.jsonPrimitive?.contentOrNull ?: run {
                        println("⚠️ Skipping entry '$key': missing required field 'title'")
                        return@mapNotNull null
                    }
                    val updatedAtStr = entryObject["updatedAt"]?.jsonPrimitive?.contentOrNull ?: run {
                        println("⚠️ Skipping entry '$key': missing required field 'updatedAt'")
                        return@mapNotNull null
                    }
                    val updatedAtInstant = kotlin.time.Instant.parse(updatedAtStr)

                    val entry = RegistryEntry(
                        title = titleStr,
                        updatedAt = updatedAtInstant,
                        interval = entryObject["interval"]?.jsonPrimitive?.contentOrNull,
                        type = entryObject["type"]?.jsonPrimitive?.contentOrNull
                    )
                    key to entry
                } catch (e: Exception) {
                    println("⚠️ Failed to parse registry entry '$key': ${e.message}")
                    SentryLogger.captureException(
                        throwable = e,
                        extras = mapOf(
                            "entryKey" to key,
                            "action" to "parse_registry_entry"
                        )
                    )
                    null
                }
            }.toMap()
            println("✅ Response parsed successfully")
            println("   Total entries: ${jsonObject.size}")
            println("   Valid entries: ${registryMap.size}")

            // Filter based on dev_mode
            val filteredEntries = registryMap.filter { (key, _) ->
                val isDevEntry = key.startsWith(DEV_PREFIX)
                if (devMode) isDevEntry else !isDevEntry
            }
            println("   Filtered entries (dev_mode=$devMode): ${filteredEntries.size}")
            filteredEntries.forEach { (key, entry) ->
                println("   - $key: ${entry.title}")
            }

            SentryLogger.finishSpan(spanId, SpanStatus.OK)

            SentryLogger.addBreadcrumb(
                category = "network",
                message = "Registry loaded successfully",
                data = mapOf(
                    "totalEntries" to registryMap.size,
                    "filteredEntries" to filteredEntries.size,
                    "durationMs" to requestDurationMs.toLong()
                )
            )

            filteredEntries
        } catch (e: Exception) {
            val requestDurationMs = (Clock.System.now() - startTime).toDouble(DurationUnit.MILLISECONDS)

            println("❌ HTTP request FAILED")
            println("   Error: ${e.message}")
            println("   Exception type: ${e::class.simpleName}")
            println("   Request duration: ${requestDurationMs.toLong()}ms")

            SentryLogger.finishSpan(spanId, SpanStatus.ERROR)

            SentryLogger.captureException(
                throwable = e,
                extras = mapOf(
                    "endpoint" to REGISTRY_ENDPOINT,
                    "url" to url,
                    "durationMs" to requestDurationMs.toLong()
                )
            )

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
