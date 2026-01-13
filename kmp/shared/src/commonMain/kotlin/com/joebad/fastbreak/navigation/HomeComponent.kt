package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.VizType

class HomeComponent(
    componentContext: ComponentContext,
    val onNavigateToDataViz: (String, Sport, VizType) -> Unit
) : ComponentContext by componentContext {

    private val _selectedSport = MutableValue(Sport.NFL)
    val selectedSport: Value<Sport> = _selectedSport

    private val _selectedTags = MutableValue<Set<String>>(emptySet())
    val selectedTags: Value<Set<String>> = _selectedTags

    fun selectSport(sport: Sport) {
        _selectedSport.value = sport
        // Clear tag filters when switching sports
        _selectedTags.value = emptySet()
    }

    fun toggleTag(tag: String) {
        val currentTags = _selectedTags.value
        _selectedTags.value = if (tag in currentTags) {
            currentTags - tag
        } else {
            currentTags + tag
        }
    }

    fun clearTagFilters() {
        _selectedTags.value = emptySet()
    }
}
