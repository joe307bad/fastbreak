package com.example.kmpapp.navigation

import com.arkivanov.decompose.ComponentContext
import com.example.kmpapp.data.api.MockedDataApi
import com.example.kmpapp.data.model.Sport

class DataVizComponent(
    componentContext: ComponentContext,
    val sport: Sport,
    val vizType: MockedDataApi.VizType,
    val onNavigateBack: () -> Unit
) : ComponentContext by componentContext {

    val api = MockedDataApi()
}
