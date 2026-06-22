package com.joebad.fastbreak.data.model

import kotlin.time.Instant

/**
 * Chart cache row metadata without the large [data_json] payload.
 * Used when building the home-screen chart list to avoid Android CursorWindow limits.
 */
data class CachedChartMetadata(
    val chartId: String,
    val visualizationType: VizType,
    val lastUpdated: Instant,
    val cachedAt: Instant,
    val interval: String?,
    val viewed: Boolean,
    val subtitle: String = ""
)
