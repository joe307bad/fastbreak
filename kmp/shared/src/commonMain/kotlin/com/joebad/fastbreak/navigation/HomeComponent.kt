package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.Tag
import com.joebad.fastbreak.data.model.VizType

class HomeComponent(
    componentContext: ComponentContext,
    val onNavigateToDataViz: (String, Sport, VizType) -> Unit
) : ComponentContext by componentContext {

    private val _selectedSport = MutableValue(Sport.NBA)
    val selectedSport: Value<Sport> = _selectedSport

    private val _selectedTags = MutableValue<Set<String>>(emptySet())
    val selectedTags: Value<Set<String>> = _selectedTags

    fun selectSport(sport: Sport) {
        _selectedSport.value = sport
        // Clear tag filters when switching sports
        _selectedTags.value = emptySet()
    }

    /**
     * Toggles a tag filter with radio button behavior per layout.
     * Only one tag from each layout (left/right) can be selected at a time.
     *
     * @param tagLabel The label of the tag to toggle
     * @param allAvailableTags All available tags to determine layout grouping
     */
    fun toggleTag(tagLabel: String, allAvailableTags: List<Tag>) {
        val currentTags = _selectedTags.value

        // Find the layout of the clicked tag
        val clickedTag = allAvailableTags.find { it.label == tagLabel }
        if (clickedTag == null) {
            // Tag not found, do nothing
            return
        }

        val clickedLayout = clickedTag.layout

        // If the clicked tag is already selected, deselect it
        if (tagLabel in currentTags) {
            _selectedTags.value = currentTags - tagLabel
            return
        }

        // Remove any previously selected tag from the same layout
        val tagsInSameLayout = allAvailableTags
            .filter { it.layout == clickedLayout }
            .map { it.label }
            .toSet()

        val tagsWithoutSameLayout = currentTags - tagsInSameLayout

        // Add the new tag
        _selectedTags.value = tagsWithoutSameLayout + tagLabel
    }

    fun clearTagFilters() {
        _selectedTags.value = emptySet()
    }
}
