package com.example.kmpapp.navigation

import com.arkivanov.decompose.ComponentContext
import com.example.kmpapp.viewmodel.DataVizViewModel

class DataVizComponent(
    componentContext: ComponentContext,
    val title: String,
    val onNavigateBack: () -> Unit
) : ComponentContext by componentContext {

    val viewModel = DataVizViewModel()

    init {
        // Load data when component is created
        viewModel.loadData()
    }
}
