package com.joebad.fastbreak.telemetry

import com.joebad.fastbreak.config.AppConfig
import com.joebad.fastbreak.data.api.HttpClientFactory
import com.joebad.fastbreak.platform.AppVersion
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Lightweight telemetry service for sending events to an OpenTelemetry Collector.
 * Uses OTLP/HTTP JSON format for cross-platform compatibility (iOS + Android).
 */
object TelemetryService {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val httpClient: HttpClient by lazy { HttpClientFactory.create() }
    private val json = Json { encodeDefaults = true }

    private val isEnabled: Boolean
        get() = AppConfig.OTEL_ENDPOINT.isNotBlank()

    private val traceEndpoint: String
        get() = "${AppConfig.OTEL_ENDPOINT}/v1/traces"

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
            name = "app.boot",
            attributes = mapOf(
                "app.version" to AppVersion.versionName,
                "app.build" to AppVersion.buildNumber,
                "app.dev_mode" to AppConfig.DEV_MODE.toString()
            )
        )
    }

    /**
     * Track sync started event.
     */
    fun trackSyncStarted(reason: String, chartCount: Int) {
        trackEvent(
            name = "sync.started",
            attributes = mapOf(
                "sync.reason" to reason,
                "sync.chart_count" to chartCount.toString()
            )
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
            name = "sync.completed",
            attributes = mapOf(
                "sync.duration_ms" to duration.toString(),
                "sync.success_count" to successCount.toString(),
                "sync.failed_count" to failedCount.toString()
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
            name = "chart.opened",
            attributes = mapOf(
                "chart.id" to chartId,
                "chart.title" to chartTitle,
                "chart.sport" to sport,
                "chart.viz_type" to vizType
            )
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
            name = "share.clicked",
            attributes = mapOf(
                "chart.id" to chartId,
                "chart.title" to chartTitle,
                "share.type" to shareType
            )
        )
    }

    /**
     * Generic event tracking method.
     */
    fun trackEvent(name: String, attributes: Map<String, String> = emptyMap()) {
        if (!isEnabled) return

        scope.launch {
            try {
                sendTrace(name, attributes)
            } catch (e: Exception) {
                // Silently fail - telemetry should never crash the app
                println("📊 TelemetryService error: ${e.message}")
            }
        }
    }

    private suspend fun sendTrace(eventName: String, attributes: Map<String, String>) {
        val now = Clock.System.now()
        val traceId = generateTraceId()
        val spanId = generateSpanId()

        val otlpRequest = OtlpTraceRequest(
            resourceSpans = listOf(
                ResourceSpans(
                    resource = Resource(
                        attributes = listOf(
                            Attribute("service.name", AttributeValue(AppConfig.OTEL_SERVICE_NAME)),
                            Attribute("service.version", AttributeValue(AppVersion.versionName)),
                            Attribute("deployment.environment", AttributeValue(
                                if (AppConfig.DEV_MODE) "development" else "production"
                            ))
                        )
                    ),
                    scopeSpans = listOf(
                        ScopeSpans(
                            scope = InstrumentationScope(
                                name = "fastbreak-mobile",
                                version = AppVersion.versionName
                            ),
                            spans = listOf(
                                Span(
                                    traceId = traceId,
                                    spanId = spanId,
                                    name = eventName,
                                    kind = 1, // SPAN_KIND_INTERNAL
                                    startTimeUnixNano = now.toEpochMilliseconds() * 1_000_000,
                                    endTimeUnixNano = now.toEpochMilliseconds() * 1_000_000,
                                    attributes = attributes.map { (k, v) ->
                                        Attribute(k, AttributeValue(v))
                                    }
                                )
                            )
                        )
                    )
                )
            )
        )

        try {
            httpClient.post(traceEndpoint) {
                contentType(ContentType.Application.Json)
                if (AppConfig.OTEL_AUTH_TOKEN.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer ${AppConfig.OTEL_AUTH_TOKEN}")
                }
                setBody(json.encodeToString(otlpRequest))
            }
        } catch (e: Exception) {
            // Network errors are expected when offline
        }
    }

    private fun generateTraceId(): String {
        return buildString {
            repeat(32) { append("0123456789abcdef".random()) }
        }
    }

    private fun generateSpanId(): String {
        return buildString {
            repeat(16) { append("0123456789abcdef".random()) }
        }
    }
}

// OTLP JSON Data Classes
@Serializable
private data class OtlpTraceRequest(
    val resourceSpans: List<ResourceSpans>
)

@Serializable
private data class ResourceSpans(
    val resource: Resource,
    val scopeSpans: List<ScopeSpans>
)

@Serializable
private data class Resource(
    val attributes: List<Attribute>
)

@Serializable
private data class ScopeSpans(
    val scope: InstrumentationScope,
    val spans: List<Span>
)

@Serializable
private data class InstrumentationScope(
    val name: String,
    val version: String
)

@Serializable
private data class Span(
    val traceId: String,
    val spanId: String,
    val name: String,
    val kind: Int,
    val startTimeUnixNano: Long,
    val endTimeUnixNano: Long,
    val attributes: List<Attribute>
)

@Serializable
private data class Attribute(
    val key: String,
    val value: AttributeValue
)

@Serializable
private data class AttributeValue(
    val stringValue: String
)
