@file:UseSerializers(InstantSerializer::class)

package com.joebad.fastbreak.data.model

import com.joebad.fastbreak.data.serializers.InstantSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Base interface for all visualization types
sealed interface VisualizationType {
    val sport: String
    val visualizationType: String
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
    override val sport: String,
    override val visualizationType: String,
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
data class QuadrantConfig(
    val color: String,
    val label: String
)

@Serializable
data class ScatterPlotVisualization(
    override val sport: String,
    override val visualizationType: String,
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    val xAxisLabel: String,
    val yAxisLabel: String,
    val xColumnLabel: String? = null,
    val yColumnLabel: String? = null,
    val invertYAxis: Boolean = false,
    val quadrantTopRight: QuadrantConfig? = null,
    val quadrantTopLeft: QuadrantConfig? = null,
    val quadrantBottomLeft: QuadrantConfig? = null,
    val quadrantBottomRight: QuadrantConfig? = null,
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
    override val sport: String,
    override val visualizationType: String,
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
    override val sport: String,
    override val visualizationType: String,
    override val title: String,
    override val subtitle: String,
    override val description: String,
    override val lastUpdated: Instant,
    val dataPoints: List<TableDataPoint>
) : VisualizationType
