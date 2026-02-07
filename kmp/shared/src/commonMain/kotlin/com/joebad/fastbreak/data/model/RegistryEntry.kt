@file:UseSerializers(InstantSerializer::class)

package com.joebad.fastbreak.data.model

import com.joebad.fastbreak.data.serializers.InstantSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Represents a single entry in the registry response from the server.
 * The registry is returned as Map<String, RegistryEntry> where the key is the file path.
 * Only contains updatedAt, title, interval, and type - other metadata (sport, subtitle, visualizationType)
 * is contained in the chart JSON itself.
 */
@Serializable
data class RegistryEntry(
    /**
     * When this chart was last updated on the server
     */
    val updatedAt: Instant,

    /**
     * Display title for the chart
     */
    val title: String,

    /**
     * Update interval for the chart (e.g., "weekly", "daily")
     */
    val interval: String? = null,

    /**
     * Type of entry: null or "chart" for charts, "topics" for topics data
     * Used to distinguish between chart data and topics data
     */
    val type: String? = null
) {
    /**
     * Returns true if this is a chart entry (type is null or "chart")
     */
    val isChart: Boolean
        get() = type == null || type == "chart"

    /**
     * Returns true if this is a topics entry
     */
    val isTopics: Boolean
        get() = type == "topics"
}
