package com.joebad.fastbreak.data.model

import kotlinx.serialization.Serializable

/**
 * Response from the topics API containing sports narratives.
 */
@Serializable
data class TopicsResponse(
    val date: String,
    val narratives: List<Narrative>
)

/**
 * A single narrative/topic with supporting evidence and links.
 */
@Serializable
data class Narrative(
    val title: String,
    val summary: String,
    val summarySegments: List<TextSegment> = emptyList(),
    val league: String = "",  // "nba", "nfl", "nhl", "mlb", "mls"
    val chartEvidence: List<ChartReference> = emptyList(),
    val highlights: List<ChartHighlight> = emptyList(),
    val dataPoints: List<NarrativeDataPoint> = emptyList(),
    val links: List<LinkReference> = emptyList(),
    val statisticalContext: String = "",
    val statisticalContextSegments: List<TextSegment> = emptyList()
)

/**
 * Reference to a chart that supports the narrative.
 */
@Serializable
data class ChartReference(
    val chartName: String,
    val chartUrl: String,
    val relevance: String
)

/**
 * Highlight specification for a chart.
 */
@Serializable
data class ChartHighlight(
    val chartName: String,
    val highlightType: String,  // "player", "team", "conference", "division"
    val values: List<String>    // e.g., ["BOS", "MIL"] for teams
)

/**
 * A specific data point cited in the narrative.
 */
@Serializable
data class NarrativeDataPoint(
    val metric: String,
    val value: String,
    val chartName: String = "",
    val team: String = "",
    val id: String = ""  // Chart ID for navigation
)

/**
 * Reference to an external article or blog post.
 */
@Serializable
data class LinkReference(
    val title: String,
    val url: String,
    val type: String  // "news" or "blog"
)

/**
 * A text segment that can be plain text or a clickable link.
 */
@Serializable
data class TextSegment(
    val type: String,  // "text" or "link"
    val value: String,
    val url: String? = null
)
