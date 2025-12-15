package com.joebad.fastbreak

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.joebad.fastbreak.navigation.RootComponent
import com.joebad.fastbreak.ui.DataVizScreen
import com.joebad.fastbreak.ui.DrawerMenu
import com.joebad.fastbreak.ui.HomeScreen
import com.joebad.fastbreak.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun App(rootComponent: RootComponent) {
    val themeMode by rootComponent.themeMode.subscribeAsState()

    // Collect Orbit MVI state (Phase 6)
    val registryState by rootComponent.registryContainer.container.stateFlow.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    AppTheme(themeMode = themeMode) {
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
                    onRefreshRegistry = { rootComponent.refreshRegistry() }
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
                        onInitialLoad = { rootComponent.loadRegistry() },
                        onRequestPermission = { rootComponent.requestNetworkPermission() },
                        onCheckPermission = { rootComponent.checkNetworkPermission() },
                        onClearSyncProgress = { rootComponent.clearSyncProgress() },
                        onMarkChartAsViewed = { chartId -> rootComponent.markChartAsViewed(chartId) }
                    )
                    is RootComponent.Child.DataViz -> DataVizScreen(
                        component = child.component,
                        onMenuClick = { scope.launch { drawerState.open() } }
                    )
                }
            }
        }
    }
}
