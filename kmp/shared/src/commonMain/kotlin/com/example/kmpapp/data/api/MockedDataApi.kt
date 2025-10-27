package com.example.kmpapp.data.api

import com.example.kmpapp.data.model.DataPoint
import com.example.kmpapp.data.model.ScatterPlotData
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Mocked API that generates quarterback analytics data for a 4-quadrant scatter plot.
 * X-axis: PFF Offense Grade (0-100)
 * Y-axis: EPA (Expected Points Added) per play
 */
class MockedDataApi {

    /**
     * Simulates fetching QB scatter plot data from an API.
     * Adds a delay to simulate network latency.
     */
    suspend fun fetchScatterPlotData(): ScatterPlotData {
        // Simulate network delay
        delay(1500)

        // Generate QB data points
        val points = generateQBDataPoints()

        return ScatterPlotData(
            points = points,
            title = "QB Performance: PFF Grade vs Expected Points Added"
        )
    }

    /**
     * Simulates fetching data that results in an error.
     * Used to test error handling.
     */
    suspend fun fetchScatterPlotDataWithError(): ScatterPlotData {
        delay(1000)
        throw Exception("Failed to fetch data: Network error")
    }

    private fun generateQBDataPoints(): List<DataPoint> {
        // Real NFL QB names for realistic data
        val qbs = listOf(
            "P. Mahomes", "J. Allen", "L. Jackson", "J. Burrow",
            "J. Herbert", "T. Tagovailoa", "D. Prescott", "J. Hurts",
            "G. Smith", "K. Cousins", "A. Rodgers", "M. Stafford",
            "D. Jones", "R. Wilson", "D. Carr", "J. Goff",
            "B. Young", "C. Stroud", "A. Richardson", "B. Mayfield",
            "S. Darnold", "J. Love", "G. Minshew", "T. Bridgewater"
        )

        return qbs.mapIndexed { index, qbName ->
            // PFF Grade typically ranges from 50-95 for NFL QBs
            // Elite QBs: 85-95, Good: 75-84, Average: 65-74, Below: 50-64
            val pffGrade = when (index % 4) {
                0 -> Random.nextDouble(85.0, 95.0)  // Elite
                1 -> Random.nextDouble(75.0, 85.0)  // Good
                2 -> Random.nextDouble(65.0, 75.0)  // Average
                else -> Random.nextDouble(55.0, 65.0) // Below average
            }

            // EPA per play typically ranges from -0.15 to 0.35
            // Elite: 0.15 to 0.35, Good: 0.05 to 0.15, Average: -0.05 to 0.05, Below: -0.15 to -0.05
            val epaPerPlay = when (index % 4) {
                0 -> Random.nextDouble(0.15, 0.35)
                1 -> Random.nextDouble(0.05, 0.15)
                2 -> Random.nextDouble(-0.05, 0.05)
                else -> Random.nextDouble(-0.15, -0.05)
            }

            DataPoint(
                x = pffGrade,
                y = epaPerPlay,
                label = qbName,
                id = "qb_$index"
            )
        }
    }
}
