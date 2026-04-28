package com.joebad.fastbreak.data.model

import kotlinx.serialization.Serializable

/**
 * Response from the topics API (v2 schema produced by Fastbreak.Daily generate-and-enrich-topics).
 *
 * Top-level shape:
 *   {
 *     "date": "2026-04-28T17:27:03Z",
 *     "version": 2,
 *     "topics": [ Topic, ... ]
 *   }
 */
@Serializable
data class TopicsResponse(
    val date: String,
    val version: Int = 2,
    val info: String = "",
    val infoSegments: List<TextSegment> = emptyList(),
    val topics: List<Topic> = emptyList()
)

/**
 * A single news summary, with structured segments (text + team/player links) and
 * supporting data points (one stat per team/player mentioned).
 */
@Serializable
data class Topic(
    val league: String,
    val category: String = "",
    val summary: String,
    val summarySegments: List<TextSegment> = emptyList(),
    val teams: List<String> = emptyList(),
    val players: List<String> = emptyList(),
    val dataPoints: List<TopicDataPoint> = emptyList()
)

/**
 * A single stat tied to a team or player.
 *
 * - subject: team abbrev (e.g. "BOS") or player name (e.g. "Connor McDavid")
 * - subjectType: "team" or "player"
 * - name: human-readable stat name (e.g. "PPG", "Month Trend: Net Rating")
 * - value: stringified scalar value
 * - rank: optional rank string (may be null/absent)
 * - source: chart id (e.g. "nba__matchup_stats") used for chart deep-linking
 * - vizType: chart visualization type (e.g. "NBA_MATCHUP", "SCATTER_PLOT")
 */
@Serializable
data class TopicDataPoint(
    val subject: String,
    val subjectType: String,
    val name: String,
    val value: String,
    val rank: String? = null,
    val source: String,
    val vizType: String = ""
)

/**
 * A text segment from the summary. Either plain text or a link.
 *
 * Types:
 * - "text": Plain text (use value)
 * - "link": External URL link — Wikipedia for players, ESPN team page for teams (use value + url)
 */
@Serializable
data class TextSegment(
    val type: String,
    val value: String,
    val url: String? = null
)
