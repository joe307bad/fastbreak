package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.joebad.fastbreak.data.api.MockedDataApi
import com.joebad.fastbreak.data.model.Sport

class DataVizComponent(
    componentContext: ComponentContext,
    val sport: Sport,
    val vizType: MockedDataApi.VizType,
    val onNavigateBack: () -> Unit
) : ComponentContext by componentContext {

    val api = MockedDataApi()
}
