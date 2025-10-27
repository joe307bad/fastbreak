package com.example.kmpapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DataPoint(
    val x: Double,
    val y: Double,
    val label: String,
    val id: String = label
)

@Serializable
data class ScatterPlotData(
    val points: List<DataPoint>,
    val title: String = "4-Quadrant Scatter Plot"
)
