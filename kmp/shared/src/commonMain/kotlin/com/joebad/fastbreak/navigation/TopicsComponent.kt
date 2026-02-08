package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext

class TopicsComponent(
    componentContext: ComponentContext,
    val onNavigateBack: () -> Unit
) : ComponentContext by componentContext
