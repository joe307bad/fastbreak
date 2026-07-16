package com.joebad.fastbreak.ui.teams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.data.model.Team
import com.joebad.fastbreak.data.model.TeamRoster
import com.joebad.fastbreak.ui.components.ColorPickerDialog
import com.joebad.fastbreak.ui.components.toHexString
import com.joebad.fastbreak.ui.theme.ColorSlot
import com.joebad.fastbreak.ui.theme.ThemeBrightness
import com.joebad.fastbreak.ui.theme.ThemeColorOverrides
import com.joebad.fastbreak.ui.theme.ThemeMode
import com.joebad.fastbreak.ui.theme.UseSecondaryBackground

/**
 * Bottom sheet for selecting teams with search functionality.
 * Displays teams grouped by sport with ability to pin/unpin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamSelectorBottomSheet(
    teamRosters: Map<String, TeamRoster>,
    pinnedTeamCodes: Set<String>,  // Set of "SPORT:CODE" strings
    onTeamToggle: (sport: String, teamCode: String, teamLabel: String, isPinned: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    // Filter teams based on search query
    val filteredRosters = remember(teamRosters, searchQuery) {
        if (searchQuery.isBlank()) {
            teamRosters
        } else {
            teamRosters.mapValues { (_, roster) ->
                roster.copy(
                    teams = roster.teams.filter { team ->
                        team.longLabel.contains(searchQuery, ignoreCase = true) ||
                        team.code.contains(searchQuery, ignoreCase = true) ||
                        team.conference.contains(searchQuery, ignoreCase = true) ||
                        team.division.contains(searchQuery, ignoreCase = true)
                    }
                )
            }.filterValues { it.teams.isNotEmpty() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 24.dp)
        ) {
            // Header
            Text(
                text = "pin teams",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("search teams...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Teams list
                if (filteredRosters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (teamRosters.isEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "loading teams...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = "no teams found",
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val scrollState = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        filteredRosters.forEach { (sport, roster) ->
                            // Sport header
                            Text(
                                text = sport,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                            )

                            // Teams
                            roster.teams.forEach { team ->
                                val teamKey = "${sport}:${team.code}"
                                val isPinned = pinnedTeamCodes.contains(teamKey)

                                TeamListItem(
                                    team = team,
                                    isPinned = isPinned,
                                    onClick = {
                                        onTeamToggle(sport, team.code, team.longLabel, !isPinned)
                                    }
                                )
                            }
                        }
                    }
                }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary footer
            val pinnedCount = pinnedTeamCodes.size
            Text(
                text = if (pinnedCount == 0) {
                    "no teams pinned"
                } else if (pinnedCount == 1) {
                    "1 team pinned"
                } else {
                    "$pinnedCount teams pinned"
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )
        }
    }
}

/**
 * Individual team list item with checkbox.
 */
@Composable
private fun TeamListItem(
    team: Team,
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isPinned) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = team.longLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPinned) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = "${team.conference} • ${team.division}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPinned) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }

            if (isPinned) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Bottom sheet for selecting a team theme.
 * Only one team can be selected at a time. Shows color swatches for each team.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorBottomSheet(
    teamRosters: Map<String, TeamRoster>,
    selectedTeamKey: String?,  // "SPORT:CODE" or null for default theme
    selectedTeamColors: Team? = null,  // The selected team's color data
    themeBrightness: ThemeBrightness = ThemeBrightness(),
    onBrightnessChange: (ColorSlot, Float) -> Unit = { _, _ -> },
    themeColorOverrides: ThemeColorOverrides = ThemeColorOverrides(),
    onColorOverrideChange: (ColorSlot, String?) -> Unit = { _, _ -> },
    onTeamSelect: (sport: String?, teamCode: String?) -> Unit,  // null to clear selection
    onDismiss: () -> Unit,
    currentTheme: ThemeMode = ThemeMode.LIGHT,
    useSecondaryBackground: UseSecondaryBackground = UseSecondaryBackground(),
    onThemeChange: (ThemeMode) -> Unit = {},
    onToggleSecondaryBackground: (ThemeMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedColorSlot by remember { mutableStateOf<ColorSlot?>(null) }
    var isBrightnessExpanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    // Filter teams based on search query
    val filteredRosters = remember(teamRosters, searchQuery) {
        if (searchQuery.isBlank()) {
            teamRosters
        } else {
            teamRosters.mapValues { (_, roster) ->
                roster.copy(
                    teams = roster.teams.filter { team ->
                        team.longLabel.contains(searchQuery, ignoreCase = true) ||
                        team.code.contains(searchQuery, ignoreCase = true) ||
                        team.conference.contains(searchQuery, ignoreCase = true) ||
                        team.division.contains(searchQuery, ignoreCase = true)
                    }
                )
            }.filterValues { it.teams.isNotEmpty() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 24.dp)
        ) {
            // Header
            Text(
                text = "team theme",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Light/Dark mode selector
            val isUsingSecondaryBg = when (currentTheme) {
                ThemeMode.LIGHT -> useSecondaryBackground.light
                ThemeMode.DARK -> useSecondaryBackground.dark
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = currentTheme == ThemeMode.LIGHT,
                    onClick = {
                        if (currentTheme == ThemeMode.LIGHT) {
                            // Already on light mode, toggle secondary background
                            onToggleSecondaryBackground(ThemeMode.LIGHT)
                        } else {
                            onThemeChange(ThemeMode.LIGHT)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(
                        text = if (currentTheme == ThemeMode.LIGHT && useSecondaryBackground.light) "light+" else "light"
                    )
                }
                SegmentedButton(
                    selected = currentTheme == ThemeMode.DARK,
                    onClick = {
                        if (currentTheme == ThemeMode.DARK) {
                            // Already on dark mode, toggle secondary background
                            onToggleSecondaryBackground(ThemeMode.DARK)
                        } else {
                            onThemeChange(ThemeMode.DARK)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(
                        text = if (currentTheme == ThemeMode.DARK && useSecondaryBackground.dark) "dark+" else "dark"
                    )
                }
            }

            // Show selected team's colors with collapsible brightness adjustment
            if (selectedTeamColors != null && selectedTeamKey != null) {
                Spacer(modifier = Modifier.height(16.dp))

                // Collapsible color adjustment section
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isBrightnessExpanded = !isBrightnessExpanded },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "customize colors",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isBrightnessExpanded) "▲" else "▼",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Expandable content
                        if (isBrightnessExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Selectable color swatches — tap one to open the color picker
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Light Primary
                                if (selectedTeamColors.lightPrimary != null) {
                                    SelectableColorSwatch(
                                        color = themeColorOverrides.lightPrimary?.let { parseHexColor(it) }
                                            ?: parseHexColor(selectedTeamColors.lightPrimary).adjustBrightness(themeBrightness.lightPrimary),
                                        brightness = 0f,
                                        isSelected = selectedColorSlot == ColorSlot.LIGHT_PRIMARY,
                                        label = "L1",
                                        onClick = { selectedColorSlot = ColorSlot.LIGHT_PRIMARY }
                                    )
                                }
                                // Light Secondary
                                if (selectedTeamColors.lightSecondary != null) {
                                    SelectableColorSwatch(
                                        color = themeColorOverrides.lightSecondary?.let { parseHexColor(it) }
                                            ?: parseHexColor(selectedTeamColors.lightSecondary).adjustBrightness(themeBrightness.lightSecondary),
                                        brightness = 0f,
                                        isSelected = selectedColorSlot == ColorSlot.LIGHT_SECONDARY,
                                        label = "L2",
                                        onClick = { selectedColorSlot = ColorSlot.LIGHT_SECONDARY }
                                    )
                                }

                                // Separator
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(24.dp)
                                        .drawWithContent {
                                            drawRect(Color.Gray.copy(alpha = 0.3f))
                                        }
                                )

                                // Dark Primary
                                if (selectedTeamColors.darkPrimary != null) {
                                    SelectableColorSwatch(
                                        color = themeColorOverrides.darkPrimary?.let { parseHexColor(it) }
                                            ?: parseHexColor(selectedTeamColors.darkPrimary).adjustBrightness(themeBrightness.darkPrimary),
                                        brightness = 0f,
                                        isSelected = selectedColorSlot == ColorSlot.DARK_PRIMARY,
                                        label = "D1",
                                        onClick = { selectedColorSlot = ColorSlot.DARK_PRIMARY }
                                    )
                                }
                                // Dark Secondary
                                if (selectedTeamColors.darkSecondary != null) {
                                    SelectableColorSwatch(
                                        color = themeColorOverrides.darkSecondary?.let { parseHexColor(it) }
                                            ?: parseHexColor(selectedTeamColors.darkSecondary).adjustBrightness(themeBrightness.darkSecondary),
                                        brightness = 0f,
                                        isSelected = selectedColorSlot == ColorSlot.DARK_SECONDARY,
                                        label = "D2",
                                        onClick = { selectedColorSlot = ColorSlot.DARK_SECONDARY }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "tap a color to customize it",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Color picker modal for the selected slot
                selectedColorSlot?.let { slot ->
                    val teamHex = when (slot) {
                        ColorSlot.LIGHT_PRIMARY -> selectedTeamColors.lightPrimary
                        ColorSlot.LIGHT_SECONDARY -> selectedTeamColors.lightSecondary
                        ColorSlot.DARK_PRIMARY -> selectedTeamColors.darkPrimary
                        ColorSlot.DARK_SECONDARY -> selectedTeamColors.darkSecondary
                    }
                    val overrideHex = when (slot) {
                        ColorSlot.LIGHT_PRIMARY -> themeColorOverrides.lightPrimary
                        ColorSlot.LIGHT_SECONDARY -> themeColorOverrides.lightSecondary
                        ColorSlot.DARK_PRIMARY -> themeColorOverrides.darkPrimary
                        ColorSlot.DARK_SECONDARY -> themeColorOverrides.darkSecondary
                    }
                    val brightnessValue = when (slot) {
                        ColorSlot.LIGHT_PRIMARY -> themeBrightness.lightPrimary
                        ColorSlot.LIGHT_SECONDARY -> themeBrightness.lightSecondary
                        ColorSlot.DARK_PRIMARY -> themeBrightness.darkPrimary
                        ColorSlot.DARK_SECONDARY -> themeBrightness.darkSecondary
                    }
                    val seedColor = overrideHex?.let { parseHexColor(it) }
                        ?: teamHex?.let { parseHexColor(it).adjustBrightness(brightnessValue) }
                        ?: Color.Gray
                    ColorPickerDialog(
                        initialColor = seedColor,
                        title = "customize color",
                        onReset = if (overrideHex != null) {
                            {
                                onColorOverrideChange(slot, null)
                                selectedColorSlot = null
                            }
                        } else null,
                        onDismiss = { selectedColorSlot = null },
                        onConfirm = { color ->
                            onColorOverrideChange(slot, color.toHexString())
                            selectedColorSlot = null
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("search teams...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Teams list
            if (filteredRosters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (teamRosters.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "loading teams...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "no teams found",
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredRosters.forEach { (sport, roster) ->
                        // Sport header
                        Text(
                            text = sport,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                        )

                        // Teams
                        roster.teams.forEach { team ->
                            val teamKey = "${sport}:${team.code}"
                            val isSelected = selectedTeamKey == teamKey

                            ThemeTeamListItem(
                                teamLabel = team.longLabel,
                                subtitle = "${team.conference} • ${team.division}",
                                isSelected = isSelected,
                                lightPrimary = team.lightPrimary,
                                lightSecondary = team.lightSecondary,
                                darkPrimary = team.darkPrimary,
                                darkSecondary = team.darkSecondary,
                                onClick = {
                                    onTeamSelect(sport, team.code)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Individual team list item for theme selection with color swatches.
 */
@Composable
private fun ThemeTeamListItem(
    teamLabel: String,
    subtitle: String,
    isSelected: Boolean,
    lightPrimary: String?,
    lightSecondary: String?,
    darkPrimary: String?,
    darkSecondary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = teamLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }

            // Color swatches
            if (lightPrimary != null || darkPrimary != null) {
                Spacer(modifier = Modifier.width(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Light mode colors
                    if (lightPrimary != null) {
                        ColorSwatch(color = parseHexColor(lightPrimary))
                    }
                    if (lightSecondary != null) {
                        ColorSwatch(color = parseHexColor(lightSecondary))
                    }

                    // Separator
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .drawWithContent {
                                drawRect(Color.Gray.copy(alpha = 0.3f))
                            }
                    )

                    // Dark mode colors
                    if (darkPrimary != null) {
                        ColorSwatch(color = parseHexColor(darkPrimary))
                    }
                    if (darkSecondary != null) {
                        ColorSwatch(color = parseHexColor(darkSecondary))
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Small circular color swatch.
 */
@Composable
private fun ColorSwatch(
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(16.dp)
            .drawWithContent {
                drawCircle(color = color)
            }
    )
}

/**
 * Selectable color swatch with brightness adjustment indicator.
 */
@Composable
private fun SelectableColorSwatch(
    color: Color,
    brightness: Float,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adjustedColor = color.adjustBrightness(brightness)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .drawWithContent {
                    // Draw the color circle
                    drawCircle(color = adjustedColor)
                    // Draw selection ring if selected
                    if (isSelected) {
                        drawCircle(
                            color = Color.White,
                            radius = size.minDimension / 2 - 2.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Adjust the brightness of a color.
 */
private fun Color.adjustBrightness(brightness: Float): Color {
    if (brightness == 0f) return this

    return if (brightness > 0) {
        // Lighten by blending with white
        Color(
            red = (this.red + (1f - this.red) * brightness).coerceIn(0f, 1f),
            green = (this.green + (1f - this.green) * brightness).coerceIn(0f, 1f),
            blue = (this.blue + (1f - this.blue) * brightness).coerceIn(0f, 1f),
            alpha = this.alpha
        )
    } else {
        // Darken by reducing towards black
        val factor = 1f + brightness
        Color(
            red = (this.red * factor).coerceIn(0f, 1f),
            green = (this.green * factor).coerceIn(0f, 1f),
            blue = (this.blue * factor).coerceIn(0f, 1f),
            alpha = this.alpha
        )
    }
}

/**
 * Parse hex color string to Compose Color.
 */
private fun parseHexColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        Color(("FF$cleanHex").toLong(16))
    } catch (e: Exception) {
        Color.Gray
    }
}
