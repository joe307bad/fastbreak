package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext

class TopicsV2Component(
    componentContext: ComponentContext,
    val onNavigateBack: () -> Unit
) : ComponentContext by componentContext
