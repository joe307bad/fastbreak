package com.example.kmpapp.navigation

import com.arkivanov.decompose.ComponentContext

class HomeComponent(
    componentContext: ComponentContext,
    val onNavigateToDataViz: () -> Unit
) : ComponentContext by componentContext
