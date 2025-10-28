package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.joebad.fastbreak.data.api.MockedDataApi
import com.joebad.fastbreak.data.model.Sport

class HomeComponent(
    componentContext: ComponentContext,
    val onNavigateToDataViz: (Sport, MockedDataApi.VizType) -> Unit
) : ComponentContext by componentContext {

    private val _selectedSport = MutableValue(Sport.NFL)
    val selectedSport: Value<Sport> = _selectedSport

    fun selectSport(sport: Sport) {
        _selectedSport.value = sport
    }
}
