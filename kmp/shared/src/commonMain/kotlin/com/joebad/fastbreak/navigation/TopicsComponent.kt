package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.VizType

class TopicsComponent(
    componentContext: ComponentContext,
    val onNavigateBack: () -> Unit,
    val onNavigateToChart: (chartId: String, sport: Sport, vizType: VizType, filters: Map<String, String>?) -> Unit = { _, _, _, _ -> },
    val getCollapsedIndices: () -> Set<Int> = { emptySet() },
    val saveCollapsedIndices: (Set<Int>) -> Unit = {},
    val getReadIndices: () -> Set<Int> = { emptySet() },
    val saveReadIndices: (Set<Int>) -> Unit = {}
) : ComponentContext by componentContext
