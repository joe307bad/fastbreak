package com.joebad.fastbreak.ui.visualizations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

data class FilterOption(
    val key: String,
    val displayName: String,
    val values: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBar(
    filters: List<FilterOption>,
    selectedFilters: Map<String, String>,
    onFilterChange: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (filters.isEmpty()) return

    var activeFilterKey by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Filter chips - parent is responsible for Row layout and scrolling
    filters.forEach { filter ->
        val selectedValue = selectedFilters[filter.key]
        FilterChip(
            selected = selectedValue != null,
            onClick = { activeFilterKey = filter.key },
            label = {
                Text(
                    text = selectedValue ?: filter.displayName,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            modifier = Modifier.height(28.dp)
        )
    }

    // Reset button - only show if any filters are active
    if (selectedFilters.isNotEmpty()) {
        IconButton(
            onClick = {
                // Clear all filters
                filters.forEach { filter ->
                    onFilterChange(filter.key, null)
                }
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear filters",
                modifier = Modifier.size(16.dp)
            )
        }
    }

    // Bottom sheet for filter selection
    if (activeFilterKey != null) {
        val activeFilter = filters.find { it.key == activeFilterKey }
        if (activeFilter != null) {
            val scrollState = rememberScrollState()

            ModalBottomSheet(
                onDismissRequest = { activeFilterKey = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.background,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.9f)
                ) {
                    // Sheet title
                    Text(
                        text = activeFilter.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Scrollable content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scrollState)
                    ) {
                        // "All" option
                        FilterOptionItem(
                            label = "All",
                            isSelected = selectedFilters[activeFilter.key] == null,
                            onClick = {
                                onFilterChange(activeFilter.key, null)
                                activeFilterKey = null
                            }
                        )

                        // Individual filter values
                        activeFilter.values.forEach { value ->
                            FilterOptionItem(
                                label = value,
                                isSelected = selectedFilters[activeFilter.key] == value,
                                onClick = {
                                    onFilterChange(activeFilter.key, value)
                                    activeFilterKey = null
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun FilterOptionItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
