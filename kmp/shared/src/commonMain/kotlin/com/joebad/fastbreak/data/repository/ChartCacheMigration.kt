package com.joebad.fastbreak.data.repository

import com.joebad.fastbreak.data.model.CachedChartData
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json

/**
 * Handles one-time migration from Settings (SharedPreferences/NSUserDefaults)
 * to SQLDelight database.
 *
 * This is a temporary class that should be removed once all users have migrated.
 */
class ChartCacheMigration(
    private val settings: Settings,
    private val chartCache: ChartCache
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        private const val KEY_MIGRATION_COMPLETE = "chart_cache_migration_v1_complete"
        private const val KEY_CHART_IDS = "all_chart_ids"
        private const val PREFIX_CHART = "chart_"
    }

    /**
     * Checks if migration is needed and performs it if so.
     * Safe to call multiple times - will only migrate once.
     */
    fun migrateIfNeeded() {
        if (isMigrationComplete()) {
            println("📦 Chart cache migration already complete")
            return
        }

        println("📦 Starting chart cache migration from Settings to SQLDelight...")

        try {
            val chartIds = getOldChartIds()
            println("   Found ${chartIds.size} charts to migrate")

            var migratedCount = 0
            var failedCount = 0

            chartIds.forEach { chartId ->
                try {
                    val cachedData = getOldChartData(chartId)
                    if (cachedData != null) {
                        chartCache.saveChartData(chartId, cachedData)
                        migratedCount++
                        println("   ✓ Migrated: $chartId")
                    }
                } catch (e: Exception) {
                    println("   ✗ Failed to migrate $chartId: ${e.message}")
                    failedCount++
                }
            }

            println("📦 Migration complete: $migratedCount migrated, $failedCount failed")

            // Mark migration as complete
            settings.putBoolean(KEY_MIGRATION_COMPLETE, true)

            // Clean up old data
            cleanupOldData(chartIds)

        } catch (e: Exception) {
            println("📦 Migration failed: ${e.message}")
            // Don't mark as complete - will retry on next launch
        }
    }

    private fun isMigrationComplete(): Boolean {
        return settings.getBoolean(KEY_MIGRATION_COMPLETE, false)
    }

    private fun getOldChartIds(): List<String> {
        return try {
            val jsonString = settings.getStringOrNull(KEY_CHART_IDS) ?: return emptyList()
            json.decodeFromString<List<String>>(jsonString)
        } catch (e: Exception) {
            println("   Error reading old chart IDs: ${e.message}")
            emptyList()
        }
    }

    private fun getOldChartData(chartId: String): CachedChartData? {
        return try {
            val jsonString = settings.getStringOrNull("$PREFIX_CHART$chartId") ?: return null
            json.decodeFromString<CachedChartData>(jsonString)
        } catch (e: Exception) {
            println("   Error reading old chart data for $chartId: ${e.message}")
            null
        }
    }

    private fun cleanupOldData(chartIds: List<String>) {
        println("   Cleaning up old Settings data...")
        settings.remove(KEY_CHART_IDS)
        chartIds.forEach { chartId ->
            settings.remove("$PREFIX_CHART$chartId")
        }
        println("   ✓ Old data cleaned up")
    }
}
