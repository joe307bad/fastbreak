package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext

class BracketComponent(
    componentContext: ComponentContext,
    val onNavigateBack: () -> Unit
) : ComponentContext by componentContext
