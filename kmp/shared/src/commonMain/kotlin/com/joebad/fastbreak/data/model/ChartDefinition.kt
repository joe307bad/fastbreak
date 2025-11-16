package com.joebad.fastbreak.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

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
    val url: String
)
