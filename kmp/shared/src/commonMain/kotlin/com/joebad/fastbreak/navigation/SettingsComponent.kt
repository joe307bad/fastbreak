package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext

class SettingsComponent(
    componentContext: ComponentContext,
    val onNavigateBack: () -> Unit,
    val onNavigateToBracketPrototype: () -> Unit = {}
) : ComponentContext by componentContext
