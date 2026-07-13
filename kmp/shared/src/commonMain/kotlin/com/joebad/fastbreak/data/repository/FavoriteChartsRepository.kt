package com.joebad.fastbreak.data.repository

import com.joebad.fastbreak.db.FastbreakDatabase
import kotlin.time.Clock

/**
 * Persists the user's favorited chart IDs in a dedicated SQLDelight table
 * ([favorite_charts]), separate from [chart_cache] payload storage so favorites
 * survive chart re-downloads.
 *
 * Order is most-recently-favorited first.
 */
class FavoriteChartsRepository(
    private val database: FastbreakDatabase
) {
    private val queries = database.favoriteChartsQueries

    fun getFavoriteChartIds(): List<String> {
        return queries.getAllFavoriteIdsOrdered().executeAsList()
    }

    fun isFavorite(chartId: String): Boolean {
        return queries.isFavorite(chartId).executeAsOne()
    }

    /**
     * Toggles favorite status. Returns the updated ordered list of favorite IDs.
     */
    fun toggleFavorite(chartId: String): List<String> {
        if (isFavorite(chartId)) {
            queries.removeFavorite(chartId)
        } else {
            queries.addFavorite(
                chart_id = chartId,
                favorited_at = Clock.System.now().toEpochMilliseconds()
            )
        }
        return getFavoriteChartIds()
    }

    fun clearAllFavorites() {
        queries.clearAllFavorites()
    }
}
