package com.joebad.fastbreak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joebad.fastbreak.data.model.PinnedTeam
import com.joebad.fastbreak.data.model.TeamRoster
import com.joebad.fastbreak.navigation.SettingsComponent
import com.joebad.fastbreak.platform.AppVersion
import com.joebad.fastbreak.ui.container.RegistryState
import com.joebad.fastbreak.ui.diagnostics.SyncStatusRow
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import com.joebad.fastbreak.ui.teams.TeamSelectorBottomSheet
import com.joebad.fastbreak.ui.teams.ThemeSelectorBottomSheet
import com.joebad.fastbreak.ui.theme.ColorSlot
import com.joebad.fastbreak.ui.theme.SelectedTeamTheme
import com.joebad.fastbreak.ui.theme.ThemeBrightness
import com.joebad.fastbreak.ui.theme.ThemeMode
import com.joebad.fastbreak.ui.theme.UseSecondaryBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    component: SettingsComponent,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    diagnostics: DiagnosticsInfo,
    onRefreshRegistry: () -> Unit,
    onResetData: () -> Unit = {},
    onMarkAllAsRead: () -> Unit = {},
    teamRosters: Map<String, TeamRoster> = emptyMap(),
    pinnedTeams: List<PinnedTeam> = emptyList(),
    onPinTeam: (sport: String, teamCode: String, teamLabel: String) -> Unit = { _, _, _ -> },
    onUnpinTeam: (sport: String, teamCode: String) -> Unit = { _, _ -> },
    selectedTeamTheme: SelectedTeamTheme? = null,
    onTeamThemeChange: (SelectedTeamTheme?) -> Unit = {},
    themeBrightness: ThemeBrightness = ThemeBrightness(),
    onBrightnessChange: (ColorSlot, Float) -> Unit = { _, _ -> },
    useSecondaryBackground: UseSecondaryBackground = UseSecondaryBackground(),
    onToggleSecondaryBackground: (ThemeMode) -> Unit = {}
) {
    var showTeamSelector by remember { mutableStateOf(false) }
    var showThemeSelector by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    // Convert pinned teams to a set of "SPORT:CODE" keys for quick lookup
    val pinnedTeamCodes = remember(pinnedTeams) {
        pinnedTeams.map { "${it.sport}:${it.teamCode}" }.toSet()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("settings") },
                navigationIcon = {
                    IconButton(onClick = component.onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Theme selector section
            Column {
                Text(
                    text = "theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = currentTheme == ThemeMode.LIGHT,
                        onClick = { onThemeChange(ThemeMode.LIGHT) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(text = "light")
                    }
                    SegmentedButton(
                        selected = currentTheme == ThemeMode.DARK,
                        onClick = { onThemeChange(ThemeMode.DARK) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(text = "dark")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showThemeSelector = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "team colors",
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Show selected team and reset button (only when a team theme is selected)
                if (selectedTeamTheme != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedTeamTheme.teamCode,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = { onTeamThemeChange(null) }
                        ) {
                            Text(
                                text = "reset theme",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Pinned teams section
            Column {
                Text(
                    text = "pinned teams",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (pinnedTeams.isEmpty()) {
                    Text(
                        text = "no teams pinned",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        pinnedTeams.forEach { pinnedTeam ->
                            PinnedTeamChip(
                                pinnedTeam = pinnedTeam,
                                onRemove = { onUnpinTeam(pinnedTeam.sport, pinnedTeam.teamCode) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { showTeamSelector = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "manage teams",
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Sync status section
            Column {
                Text(
                    text = "sync status",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                SyncStatusRow(diagnostics = diagnostics)

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onRefreshRegistry,
                    enabled = !diagnostics.isSyncing || diagnostics.lastError != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "refresh registry",
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showResetConfirmation = true },
                    enabled = !diagnostics.isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = "reset chart registry",
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Mark all as read section
            Column {
                Text(
                    text = "notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onMarkAllAsRead,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "mark all as read",
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Version info section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "version",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "v${AppVersion.versionName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "build ${AppVersion.buildNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Team selector bottom sheet
    if (showTeamSelector) {
        TeamSelectorBottomSheet(
            teamRosters = teamRosters,
            pinnedTeamCodes = pinnedTeamCodes,
            onTeamToggle = { sport, teamCode, teamLabel, isPinned ->
                if (isPinned) {
                    onPinTeam(sport, teamCode, teamLabel)
                } else {
                    onUnpinTeam(sport, teamCode)
                }
            },
            onDismiss = { showTeamSelector = false }
        )
    }

    // Theme selector bottom sheet
    if (showThemeSelector) {
        val selectedTeamKey = selectedTeamTheme?.let { "${it.sport}:${it.teamCode}" }
        // Find selected team's colors for brightness adjustment UI
        val selectedTeamColors = selectedTeamTheme?.let { theme ->
            teamRosters[theme.sport]?.teams?.find { it.code == theme.teamCode }
        }
        ThemeSelectorBottomSheet(
            teamRosters = teamRosters,
            selectedTeamKey = selectedTeamKey,
            selectedTeamColors = selectedTeamColors,
            themeBrightness = themeBrightness,
            onBrightnessChange = onBrightnessChange,
            onTeamSelect = { sport, teamCode ->
                if (sport != null && teamCode != null) {
                    onTeamThemeChange(SelectedTeamTheme(sport, teamCode))
                } else {
                    onTeamThemeChange(null)
                }
            },
            onDismiss = { showThemeSelector = false },
            currentTheme = currentTheme,
            useSecondaryBackground = useSecondaryBackground,
            onThemeChange = onThemeChange,
            onToggleSecondaryBackground = onToggleSecondaryBackground
        )
    }

    // Reset confirmation bottom sheet
    if (showResetConfirmation) {
        ModalBottomSheet(
            onDismissRequest = { showResetConfirmation = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "reset all data?",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = "this will delete all cached charts, topics, team rosters, and pinned teams. you will need to sync again to restore data.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        onResetData()
                        showResetConfirmation = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        text = "reset all data",
                        fontFamily = FontFamily.Monospace
                    )
                }

                OutlinedButton(
                    onClick = { showResetConfirmation = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "cancel",
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Chip displaying a pinned team with remove button.
 */
@Composable
private fun PinnedTeamChip(
    pinnedTeam: PinnedTeam,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    AssistChip(
        onClick = onRemove,
        label = {
            Text(
                text = pinnedTeam.teamLabel,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(16.dp)
            )
        },
        modifier = modifier
    )
}
