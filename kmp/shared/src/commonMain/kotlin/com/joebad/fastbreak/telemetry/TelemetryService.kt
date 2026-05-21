package com.joebad.fastbreak.telemetry

import com.joebad.fastbreak.config.AppConfig
import com.joebad.fastbreak.data.api.HttpClientFactory
import com.joebad.fastbreak.platform.AppVersion
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Lightweight telemetry service for sending events to QuestDB.
 * Uses InfluxDB Line Protocol (ILP) over HTTP for simple, efficient ingestion.
 */
object TelemetryService {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val httpClient: HttpClient by lazy { HttpClientFactory.create() }

    private val isEnabled: Boolean
        get() = AppConfig.OTEL_ENDPOINT.isNotBlank()

    private val writeEndpoint: String
        get() = "${AppConfig.OTEL_ENDPOINT}/write"

    /**
     * Initialize the telemetry service.
     * Call this once during app startup.
     */
    fun initialize() {
        if (isEnabled) {
            println("📊 TelemetryService initialized with endpoint: ${AppConfig.OTEL_ENDPOINT}")
        } else {
            println("📊 TelemetryService disabled (no OTEL_ENDPOINT configured)")
        }
    }

    /**
     * Track app boot event.
     */
    fun trackAppBoot() {
        trackEvent(
            name = "app_boot",
            tags = mapOf(
                "app_version" to AppVersion.versionName,
                "build" to AppVersion.buildNumber,
                "dev_mode" to AppConfig.DEV_MODE.toString()
            )
        )
    }

    /**
     * Track sync started event.
     */
    fun trackSyncStarted(reason: String, chartCount: Int) {
        trackEvent(
            name = "sync_started",
            tags = mapOf("reason" to reason),
            fields = mapOf("chart_count" to chartCount.toLong())
        )
    }

    /**
     * Track sync completed event.
     */
    fun trackSyncCompleted(
        duration: Long,
        successCount: Int,
        failedCount: Int
    ) {
        trackEvent(
            name = "sync_completed",
            fields = mapOf(
                "duration_ms" to duration,
                "success_count" to successCount.toLong(),
                "failed_count" to failedCount.toLong()
            )
        )
    }

    /**
     * Track chart opened event.
     */
    fun trackChartOpened(
        chartId: String,
        chartTitle: String,
        sport: String,
        vizType: String
    ) {
        trackEvent(
            name = "chart_opened",
            tags = mapOf(
                "chart_id" to chartId,
                "sport" to sport,
                "viz_type" to vizType
            ),
            fields = mapOf("title" to chartTitle)
        )
    }

    /**
     * Track share button clicked event.
     */
    fun trackShareClicked(
        chartId: String,
        chartTitle: String,
        shareType: String
    ) {
        trackEvent(
            name = "share_clicked",
            tags = mapOf(
                "chart_id" to chartId,
                "share_type" to shareType
            ),
            fields = mapOf("title" to chartTitle)
        )
    }

    /**
     * Generic event tracking method.
     *
     * @param name The measurement/table name (use snake_case)
     * @param tags Indexed string values for filtering (low cardinality)
     * @param fields Non-indexed values (high cardinality, numbers, long strings)
     */
    fun trackEvent(
        name: String,
        tags: Map<String, String> = emptyMap(),
        fields: Map<String, Any> = mapOf("value" to 1L)
    ) {
        if (!isEnabled) return

        scope.launch {
            try {
                sendIlpEvent(name, tags, fields)
            } catch (e: Exception) {
                // Silently fail - telemetry should never crash the app
                println("📊 TelemetryService error: ${e.message}")
            }
        }
    }

    /**
     * Send event using InfluxDB Line Protocol format.
     * Format: measurement,tag1=v1,tag2=v2 field1=v1,field2=v2 timestamp_ns
     */
    private suspend fun sendIlpEvent(
        measurement: String,
        tags: Map<String, String>,
        fields: Map<String, Any>
    ) {
        val timestampNs = Clock.System.now().toEpochMilliseconds() * 1_000_000

        // Build ILP line
        val line = buildString {
            // Measurement name
            append(escapeIlpKey(measurement))

            // Common tags (always include app info)
            append(",app_version=").append(escapeIlpValue(AppVersion.versionName))
            append(",build=").append(escapeIlpValue(AppVersion.buildNumber))
            append(",env=").append(if (AppConfig.DEV_MODE) "dev" else "prod")

            // Custom tags
            tags.forEach { (key, value) ->
                append(",").append(escapeIlpKey(key)).append("=").append(escapeIlpValue(value))
            }

            // Fields (space separator)
            append(" ")

            val fieldsList = fields.entries.toList()
            fieldsList.forEachIndexed { index, (key, value) ->
                if (index > 0) append(",")
                append(escapeIlpKey(key)).append("=").append(formatIlpFieldValue(value))
            }

            // Timestamp in nanoseconds
            append(" ").append(timestampNs)
        }

        try {
            httpClient.post(writeEndpoint) {
                contentType(ContentType.Text.Plain)
                if (AppConfig.OTEL_AUTH_TOKEN.isNotBlank()) {
                    header("X-API-Key", AppConfig.OTEL_AUTH_TOKEN)
                }
                setBody(line)
            }
        } catch (e: Exception) {
            // Network errors are expected when offline
        }
    }

    /**
     * Escape special characters in ILP keys (measurement names, tag keys, field keys).
     */
    private fun escapeIlpKey(key: String): String {
        return key
            .replace(",", "\\,")
            .replace("=", "\\=")
            .replace(" ", "\\ ")
    }

    /**
     * Escape special characters in ILP tag values.
     */
    private fun escapeIlpValue(value: String): String {
        return value
            .replace(",", "\\,")
            .replace("=", "\\=")
            .replace(" ", "\\ ")
    }

    /**
     * Format field values according to ILP spec.
     * - Strings: quoted with escaped quotes
     * - Integers: suffixed with 'i'
     * - Floats: as-is
     * - Booleans: t/f
     */
    private fun formatIlpFieldValue(value: Any): String {
        return when (value) {
            is String -> "\"${value.replace("\"", "\\\"")}\""
            is Long -> "${value}i"
            is Int -> "${value}i"
            is Double -> value.toString()
            is Float -> value.toString()
            is Boolean -> if (value) "t" else "f"
            else -> "\"${value.toString().replace("\"", "\\\"")}\""
        }
    }
}
