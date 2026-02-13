package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.VizType
import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.ui.container.RegistryContainer

class DataVizComponent(
    componentContext: ComponentContext,
    val chartId: String,
    val sport: Sport,
    val vizType: VizType,
    val chartDataRepository: ChartDataRepository,
    val registryContainer: RegistryContainer,
    val onNavigateBack: () -> Unit,
    val initialFilters: Map<String, String>? = null
) : ComponentContext by componentContext
