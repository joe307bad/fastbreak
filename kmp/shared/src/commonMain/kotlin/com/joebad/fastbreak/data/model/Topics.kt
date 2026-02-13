package com.joebad.fastbreak.data.model

import kotlinx.serialization.Serializable

/**
 * Response from the topics API containing sports narratives.
 */
@Serializable
data class TopicsResponse(
    val date: String,
    val description: String = "",
    val descriptionSegments: List<TextSegment> = emptyList(),
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
 * A specific data point cited in the narrative.
 * Can be clicked to navigate to a chart with team/player filter pre-populated.
 */
@Serializable
data class NarrativeDataPoint(
    val metric: String,
    val value: String,
    val chartName: String = "",
    val team: String = "",
    val player: String = "",
    val id: String = "",  // Chart ID for navigation
    val vizType: String = ""  // Visualization type (e.g., "SCATTER_PLOT", "BAR_GRAPH")
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
 * A text segment that can be plain text, a clickable link, or a chart deep link.
 *
 * Types:
 * - "text": Plain text
 * - "link": External URL link (uses url field)
 * - "chart": Deep link to a chart (uses chartId and filters fields)
 */
@Serializable
data class TextSegment(
    val type: String,  // "text", "link", or "chart"
    val value: String,
    val url: String? = null,
    val chartId: String? = null,
    val filters: Map<String, String>? = null
)
