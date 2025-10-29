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
     * The URL to fetch the chart data from
     * In production, this will be a real API endpoint
     * For now, contains a fake URL while using mock data
     */
    val url: String,

    /**
     * The type string passed to MockedDataApi ("scatter", "bar", "line")
     * Tells the mock API which type of data to generate
     * TODO: Remove this when switching to real data fetching via URL
     */
    val mockDataType: String
)
