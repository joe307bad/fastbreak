package com.joebad.fastbreak

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.navigation.RootComponent
import com.joebad.fastbreak.ui.DataVizScreen
import com.joebad.fastbreak.ui.DrawerMenu
import com.joebad.fastbreak.ui.HomeScreen
import com.joebad.fastbreak.ui.SettingsScreen
import com.joebad.fastbreak.ui.SplashScreen
import com.joebad.fastbreak.ui.TopicsScreen
import com.joebad.fastbreak.ui.bracket.BracketScreen
import com.joebad.fastbreak.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun App(rootComponent: RootComponent) {
    val themeMode by rootComponent.themeMode.subscribeAsState()

    // Splash screen state
    var showSplash by remember { mutableStateOf(true) }

    // Collect Orbit MVI state (Phase 6)
    val registryState by rootComponent.registryContainer.container.stateFlow.collectAsState()
    val pinnedTeamsState by rootComponent.pinnedTeamsContainer.container.stateFlow.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    AppTheme(themeMode = themeMode) {
        if (showSplash) {
            SplashScreen(
                onSplashComplete = { showSplash = false }
            )
        } else {
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
                        onInitialLoad = { rootComponent.loadRegistry() },
                        onRequestPermission = { rootComponent.requestNetworkPermission() },
                        onCheckPermission = { rootComponent.checkNetworkPermission() },
                        onClearSyncProgress = { rootComponent.clearSyncProgress() },
                        onMarkChartAsViewed = { chartId -> rootComponent.markChartAsViewed(chartId) },
                        onNavigateToBracket = { rootComponent.navigateToBracket() }
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
                        teamRosters = pinnedTeamsState.teamRosters,
                        pinnedTeams = pinnedTeamsState.pinnedTeams,
                        onPinTeam = { sport, teamCode, teamLabel ->
                            rootComponent.pinnedTeamsContainer.pinTeam(sport, teamCode, teamLabel)
                        },
                        onUnpinTeam = { sport, teamCode ->
                            rootComponent.pinnedTeamsContainer.unpinTeam(sport, teamCode)
                        }
                    )
                    is RootComponent.Child.Topics -> TopicsScreen(
                        component = child.component,
                        topics = rootComponent.registryContainer.getCachedTopics(),
                        topicsUpdatedAt = rootComponent.registryContainer.getTopicsUpdatedAt(),
                        onMenuClick = { scope.launch { drawerState.open() } }
                    )
                    is RootComponent.Child.Bracket -> BracketScreen(
                        onNavigateBack = { child.component.onNavigateBack() }
                    )
                }
            }
        }
        }
    }
}
