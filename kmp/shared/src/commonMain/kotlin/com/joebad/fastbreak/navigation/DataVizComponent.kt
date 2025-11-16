package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.joebad.fastbreak.data.api.MockedDataApi
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.ui.container.RegistryContainer

class DataVizComponent(
    componentContext: ComponentContext,
    val chartId: String,
    val sport: Sport,
    val vizType: MockedDataApi.VizType,
    val chartDataRepository: ChartDataRepository,
    val registryContainer: RegistryContainer,
    val onNavigateBack: () -> Unit
) : ComponentContext by componentContext
