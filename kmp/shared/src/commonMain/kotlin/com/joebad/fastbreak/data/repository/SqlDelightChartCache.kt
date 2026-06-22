package com.joebad.fastbreak.data.repository

import com.joebad.fastbreak.data.model.CachedChartData
import com.joebad.fastbreak.data.model.CachedChartMetadata
import com.joebad.fastbreak.data.model.VizType
import com.joebad.fastbreak.db.FastbreakDatabase
import kotlin.time.Instant

/**
 * SQLDelight implementation of [ChartCache].
 *
 * Key improvements over the Settings-based approach:
 * - ACID transactions: atomic writes eliminate race conditions
 * - No index management: SQLite handles this internally
 * - No mutex needed: SQLite serializes writes at the DB level
 * - WAL mode: excellent concurrent read/write performance
 */
class SqlDelightChartCache(
    private val database: FastbreakDatabase
) : ChartCache {

    private val queries = database.chartCacheQueries

    override fun saveChartData(chartId: String, data: CachedChartData) {
        queries.upsertChart(
            id = chartId,
            data_json = data.dataJson,
            visualization_type = data.visualizationType.name,
            last_updated = data.lastUpdated.toEpochMilliseconds(),
            cached_at = data.cachedAt.toEpochMilliseconds(),
            interval = data.interval,
            viewed = if (data.viewed) 1L else 0L
        )
    }

    override fun getChartData(chartId: String): CachedChartData? {
        val row = queries.getChart(chartId).executeAsOneOrNull() ?: return null
        return CachedChartData(
            chartId = row.id,
            lastUpdated = Instant.fromEpochMilliseconds(row.last_updated),
            visualizationType = VizType.valueOf(row.visualization_type),
            cachedAt = Instant.fromEpochMilliseconds(row.cached_at),
            dataJson = row.data_json,
            interval = row.interval,
            viewed = row.viewed == 1L
        )
    }

    override fun getChartMetadata(chartId: String): CachedChartMetadata? {
        val row = queries.getChartMetadata(chartId).executeAsOneOrNull() ?: return null
        return CachedChartMetadata(
            chartId = row.id,
            visualizationType = VizType.valueOf(row.visualization_type),
            lastUpdated = Instant.fromEpochMilliseconds(row.last_updated),
            cachedAt = Instant.fromEpochMilliseconds(row.cached_at),
            interval = row.interval,
            viewed = row.viewed == 1L,
            subtitle = row.subtitle
        )
    }

    override fun getAllChartIds(): List<String> {
        return queries.getAllChartIds().executeAsList()
    }

    override fun deleteChartData(chartId: String) {
        queries.deleteChart(chartId)
    }

    override fun clearAllChartData() {
        queries.deleteAllCharts()
    }

    override fun hasChartData(chartId: String): Boolean {
        return queries.hasChart(chartId).executeAsOne()
    }

    override fun markChartAsViewed(chartId: String): Boolean {
        if (!hasChartData(chartId)) return false
        queries.markAsViewed(chartId)
        return true
    }

    override fun markAllChartsAsViewed(): Int {
        val count = getCachedChartCount()
        queries.markAllAsViewed()
        return count
    }

    override fun deleteOrphanedCharts(validChartIds: Set<String>): List<String> {
        val currentIds = getAllChartIds()
        val orphanedIds = currentIds.filter { it !in validChartIds }

        if (orphanedIds.isNotEmpty()) {
            database.transaction {
                orphanedIds.forEach { chartId ->
                    queries.deleteChart(chartId)
                }
            }
        }

        return orphanedIds
    }

    override fun estimateTotalCacheSize(): Long {
        return queries.getTotalCacheSize().executeAsOne()
    }

    override fun getCachedChartCount(): Int {
        return queries.getChartCount().executeAsOne().toInt()
    }
}
