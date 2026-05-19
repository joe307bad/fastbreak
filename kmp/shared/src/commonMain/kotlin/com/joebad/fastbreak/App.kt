package com.joebad.fastbreak

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.platform.AppUpdatePrompt
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.navigation.RootComponent
import com.joebad.fastbreak.ui.DataVizScreen
import com.joebad.fastbreak.ui.DrawerMenu
import com.joebad.fastbreak.ui.HomeScreen
import com.joebad.fastbreak.ui.SettingsScreen
import com.joebad.fastbreak.ui.TopicsV2Screen
import com.joebad.fastbreak.ui.theme.AppTheme
import com.joebad.fastbreak.ui.theme.TeamThemeColors
import kotlinx.coroutines.launch

@Composable
fun App(rootComponent: RootComponent) {
    val themeMode by rootComponent.themeMode.subscribeAsState()
    val selectedTeamTheme by rootComponent.selectedTeamTheme.subscribeAsState()
    val themeBrightness by rootComponent.themeBrightness.subscribeAsState()
    val useSecondaryBackground by rootComponent.useSecondaryBackground.subscribeAsState()

    // Collect Orbit MVI state (Phase 6)
    val registryState by rootComponent.registryContainer.container.stateFlow.collectAsState()
    val pinnedTeamsState by rootComponent.pinnedTeamsContainer.container.stateFlow.collectAsState()

    // Compute team colors from selected theme
    val teamColors = remember(selectedTeamTheme, pinnedTeamsState.teamRosters) {
        selectedTeamTheme.theme?.let { theme ->
            pinnedTeamsState.teamRosters[theme.sport]?.teams?.find { it.code == theme.teamCode }?.let { team ->
                TeamThemeColors(
                    lightPrimary = team.lightPrimary,
                    lightSecondary = team.lightSecondary,
                    darkPrimary = team.darkPrimary,
                    darkSecondary = team.darkSecondary
                )
            }
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Track if user dismissed the update dialog (don't show again this session)
    var updateDialogDismissed by remember { mutableStateOf(false) }

    AppTheme(themeMode = themeMode, teamColors = teamColors, brightness = themeBrightness, useSecondaryBackground = useSecondaryBackground) {
        // Show update required dialog if needed
        if (registryState.updateRequired && !updateDialogDismissed) {
            AlertDialog(
                onDismissRequest = { updateDialogDismissed = true },
                title = {
                    Text(
                        text = "update available",
                        fontFamily = FontFamily.Monospace
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "a new version of fastbreak is available with updated charts and features.",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "update to see the latest data.",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { AppUpdatePrompt.openAppStore() }) {
                        Text(
                            text = "update now",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateDialogDismissed = true }) {
                        Text(
                            text = "later",
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            )
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerMenu(
                    currentTheme = themeMode,
                    onThemeChange = { newTheme ->
                        rootComponent.toggleTheme(newTheme)
                    },
                    registry = registryState.registry ?: com.joebad.fastbreak.data.model.Registry.empty(),
                    diagnostics = registryState.diagnostics,
                    onRefreshRegistry = { rootComponent.refreshRegistry() },
                    onNavigateToSettings = {
                        scope.launch { drawerState.close() }
                        rootComponent.navigateToSettings()
                    },
                    onChartClick = { chart ->
                        scope.launch { drawerState.close() }
                        // Mark the chart as viewed
                        rootComponent.markChartAsViewed(chart.id)
                        // Navigate to the chart from RootComponent directly
                        rootComponent.navigateToChart(
                            chart.id,
                            chart.sport,
                            chart.visualizationType
                        )
                    }
                )
            }
        ) {
            val stack by rootComponent.stack.subscribeAsState()

            Children(
                stack = stack,
                animation = stackAnimation(slide())
            ) {
                when (val child = it.instance) {
                    is RootComponent.Child.Home -> HomeScreen(
                        component = child.component,
                        registryState = registryState,
                        registryContainer = rootComponent.registryContainer,
                        onRefresh = { rootComponent.refreshRegistry() },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onNavigateToTopics = { rootComponent.navigateToTopics() },
                        onRequestPermission = { rootComponent.requestNetworkPermission() },
                        onCheckPermission = { rootComponent.checkNetworkPermission() },
                        onClearSyncProgress = { rootComponent.clearSyncProgress() },
                        onMarkChartAsViewed = { chartId -> rootComponent.markChartAsViewed(chartId) }
                    )
                    is RootComponent.Child.DataViz -> DataVizScreen(
                        component = child.component,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        pinnedTeams = pinnedTeamsState.pinnedTeams
                    )
                    is RootComponent.Child.Settings -> SettingsScreen(
                        component = child.component,
                        currentTheme = themeMode,
                        onThemeChange = { newTheme ->
                            rootComponent.toggleTheme(newTheme)
                        },
                        diagnostics = registryState.diagnostics,
                        onRefreshRegistry = { rootComponent.refreshRegistry() },
                        onResetData = { rootComponent.registryContainer.resetAllData() },
                        onMarkAllAsRead = { rootComponent.markAllAsRead() },
                        teamRosters = pinnedTeamsState.teamRosters,
                        pinnedTeams = pinnedTeamsState.pinnedTeams,
                        onPinTeam = { sport, teamCode, teamLabel ->
                            rootComponent.pinnedTeamsContainer.pinTeam(sport, teamCode, teamLabel)
                        },
                        onUnpinTeam = { sport, teamCode ->
                            rootComponent.pinnedTeamsContainer.unpinTeam(sport, teamCode)
                        },
                        selectedTeamTheme = selectedTeamTheme.theme,
                        onTeamThemeChange = { theme ->
                            rootComponent.setTeamTheme(theme)
                        },
                        themeBrightness = themeBrightness,
                        onBrightnessChange = { slot, value ->
                            rootComponent.setBrightness(slot, value)
                        },
                        useSecondaryBackground = useSecondaryBackground,
                        onToggleSecondaryBackground = { mode ->
                            rootComponent.toggleSecondaryBackground(mode)
                        }
                    )
                    is RootComponent.Child.TopicsV2 -> TopicsV2Screen(
                        component = child.component,
                        topics = rootComponent.registryContainer.getCachedTopics(),
                        onMarkTopicsAsViewed = { rootComponent.registryContainer.markTopicsAsViewed() }
                    )
                }
            }
        }
    }
}
