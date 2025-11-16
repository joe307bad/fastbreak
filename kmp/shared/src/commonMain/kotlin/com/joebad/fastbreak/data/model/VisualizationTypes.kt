package com.joebad.fastbreak.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// Base interface for all visualization types
sealed interface VisualizationType {
    val title: String
    val subtitle: String
    val description: String
    val lastUpdated: Instant
}

// Bar Graph data structures
@Serializable
data class BarGraphDataPoint(
    val label: String,
    val value: Double
)

@Serializable
data class BarGraphVisualization(
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    val dataPoints: List<BarGraphDataPoint>
) : VisualizationType

// Scatter Plot data structures
@Serializable
data class ScatterPlotDataPoint(
    val label: String,
    val x: Double,
    val y: Double,
    val sum: Double
)

@Serializable
data class ScatterPlotVisualization(
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    val xAxisLabel: String,
    val yAxisLabel: String,
    val dataPoints: List<ScatterPlotDataPoint>
) : VisualizationType

// Line Chart data structures
@Serializable
data class LineChartDataPoint(
    val x: Double,
    val y: Double
)

@Serializable
data class LineChartSeries(
    val label: String,
    val dataPoints: List<LineChartDataPoint>
)

@Serializable
data class LineChartVisualization(
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    val series: List<LineChartSeries>
) : VisualizationType

// Table View data structures
@Serializable
data class TableColumn(
    val label: String,
    val value: String
)

@Serializable
data class TableDataPoint(
    val label: String,
    val columns: List<TableColumn>
)

@Serializable
data class TableVisualization(
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    val dataPoints: List<TableDataPoint>
) : VisualizationType
