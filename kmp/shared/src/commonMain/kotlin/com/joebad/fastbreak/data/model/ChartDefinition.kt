@file:UseSerializers(InstantSerializer::class)

package com.joebad.fastbreak.data.model

import com.joebad.fastbreak.data.serializers.InstantSerializer
import com.joebad.fastbreak.data.serializers.TagListSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Represents a single chart definition from the registry.
 * Contains metadata about the chart and instructions for fetching its data.
 */
@Serializable
data class ChartDefinition(
    /**
     * Unique identifier for this chart (e.g., "nfl-efficiency-scatter")
     */
    val id: String,

    /**
     * The sport this chart belongs to (NFL, NBA, MLB, NHL)
     */
    val sport: Sport,

    /**
     * Display title for the chart
     */
    val title: String,

    /**
     * Subtitle or description for the chart
     */
    val subtitle: String,

    /**
     * When this chart's data was last updated
     * Used to determine if cached data is stale
     */
    val lastUpdated: Instant,

    /**
     * The type of visualization (SCATTER_PLOT, BAR_GRAPH, LINE_CHART)
     */
    val visualizationType: VizType,

    /**
     * The URL to fetch the chart data from (relative path like "/nfl__team_tier_list.json")
     * Will be combined with base URL to fetch actual chart data
     */
    val url: String,

    /**
     * Update interval for the chart (e.g., "weekly", "daily")
     */
    val interval: String? = null,

    /**
     * When this chart was cached locally
     * Used to show relative time since last fetch
     */
    val cachedAt: Instant? = null,

    /**
     * Whether the user has viewed this chart
     * Used to show "new" indicator on unviewed charts
     */
    val viewed: Boolean = false,

    /**
     * Tags for filtering charts with label, layout, and color
     * Extracted from the visualization data when cached
     */
    @Serializable(with = TagListSerializer::class)
    val tags: List<Tag>? = null,

    /**
     * Optional sort order for controlling list position
     * Lower values appear first, null values sorted after explicit values
     */
    val sortOrder: Int? = null
)
