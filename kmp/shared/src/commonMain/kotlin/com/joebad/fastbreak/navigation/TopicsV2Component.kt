package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.VizType
import kotlin.time.Instant

class TopicsV2Component(
    componentContext: ComponentContext,
    val onNavigateBack: () -> Unit,
    val onNavigateToChart: (chartId: String, sport: Sport, vizType: VizType, filters: Map<String, String>?) -> Unit = { _, _, _, _ -> },
    val getFontSize: () -> Float? = { null },
    val saveFontSize: (Float) -> Unit = {},
    val getUpdatedAt: () -> Instant? = { null }
) : ComponentContext by componentContext
