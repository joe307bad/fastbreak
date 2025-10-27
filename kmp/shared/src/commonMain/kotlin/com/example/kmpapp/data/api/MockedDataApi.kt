package com.example.kmpapp.data.api

import com.example.kmpapp.data.model.DataPoint
import com.example.kmpapp.data.model.ScatterPlotData
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Mocked API that generates random scatter plot data for a 4-quadrant chart.
 * Data points are distributed across all four quadrants (x and y range from -10 to 10).
 */
class MockedDataApi {

    /**
     * Simulates fetching scatter plot data from an API.
     * Adds a delay to simulate network latency.
     */
    suspend fun fetchScatterPlotData(): ScatterPlotData {
        // Simulate network delay
        delay(1500)

        // Generate random data points distributed across 4 quadrants
        val points = generateRandomDataPoints(count = 20)

        return ScatterPlotData(
            points = points,
            title = "Performance vs Impact Analysis"
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

    private fun generateRandomDataPoints(count: Int): List<DataPoint> {
        val labels = listOf(
            "Alpha", "Beta", "Gamma", "Delta", "Epsilon",
            "Zeta", "Eta", "Theta", "Iota", "Kappa",
            "Lambda", "Mu", "Nu", "Xi", "Omicron",
            "Pi", "Rho", "Sigma", "Tau", "Upsilon"
        )

        return labels.take(count).mapIndexed { index, label ->
            DataPoint(
                x = Random.nextDouble(-10.0, 10.0),
                y = Random.nextDouble(-10.0, 10.0),
                label = label,
                id = "point_$index"
            )
        }
    }
}
