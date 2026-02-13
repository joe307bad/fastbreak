package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.model.VizType
import com.joebad.fastbreak.ui.container.PinnedTeamsContainer
import com.joebad.fastbreak.ui.container.RegistryContainer
import com.joebad.fastbreak.ui.theme.ThemeMode
import com.joebad.fastbreak.ui.theme.ThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val themeRepository: ThemeRepository,
    val registryContainer: RegistryContainer,
    val pinnedTeamsContainer: PinnedTeamsContainer,
    private val chartDataRepository: com.joebad.fastbreak.data.repository.ChartDataRepository
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _themeMode = MutableValue(themeRepository.getInitialTheme())
    val themeMode: Value<ThemeMode> = _themeMode

    fun toggleTheme(mode: ThemeMode) {
        _themeMode.value = mode
        themeRepository.saveTheme(mode)
    }

    /**
     * Checks network permission status.
     * Delegates to RegistryContainer (Orbit MVI).
     */
    fun checkNetworkPermission() {
        registryContainer.checkNetworkPermission()
    }

    /**
     * Requests network permission.
     * Delegates to RegistryContainer (Orbit MVI).
     */
    fun requestNetworkPermission() {
        registryContainer.requestNetworkPermission()
    }

    /**
     * Loads the registry (initial load with 12-hour check).
     * Delegates to RegistryContainer (Orbit MVI).
     */
    fun loadRegistry() {
        registryContainer.loadRegistry()
    }

    /**
     * Triggers a registry refresh.
     * Delegates to RegistryContainer (Orbit MVI).
     */
    fun refreshRegistry() {
        println("🔵 RootComponent.refreshRegistry() called from UI")
        registryContainer.refreshRegistry()
    }

    /**
     * Clears the sync progress state.
     * Delegates to RegistryContainer (Orbit MVI).
     */
    fun clearSyncProgress() {
        registryContainer.clearSyncProgress()
    }

    /**
     * Marks a chart as viewed by the user.
     * Delegates to RegistryContainer (Orbit MVI).
     */
    fun markChartAsViewed(chartId: String) {
        registryContainer.markChartAsViewed(chartId)
    }

    val stack: Value<ChildStack<*, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Home,
        handleBackButton = true,
        childFactory = ::child
    )

    fun navigateToSettings() {
        navigation.push(Config.Settings)
    }

    fun navigateToTopics() {
        // Mark topics as viewed when navigating to topics screen
        registryContainer.markTopicsAsViewed()
        navigation.push(Config.Topics)
    }

    fun navigateToChart(
        chartId: String,
        sport: Sport,
        vizType: VizType,
        initialFilters: Map<String, String>? = null,
        fromTopics: Boolean = false
    ) {
        // First, pop any existing chart screens to avoid stacking charts
        while (stack.value.active.configuration is Config.DataViz) {
            navigation.pop()
        }

        // Store the sport so we can select it when navigating back
        val homeChild = stack.value.items.firstOrNull { it.configuration is Config.Home }?.instance
        if (homeChild is Child.Home) {
            homeChild.component.selectSport(sport)
        }

        // Now push the new chart
        navigation.push(Config.DataViz(chartId, sport, vizType, initialFilters, fromTopics))
    }

    fun navigateBackToHome() {
        // Get the current chart's sport before navigating back
        val currentConfig = stack.value.active.configuration
        if (currentConfig is Config.DataViz) {
            // Find the home component and select the sport
            val homeChild = stack.value.items.firstOrNull { it.configuration is Config.Home }?.instance
            if (homeChild is Child.Home) {
                homeChild.component.selectSport(currentConfig.sport)
            }
        }

        // Pop all screens until we're back at Home
        while (stack.value.active.configuration != Config.Home) {
            navigation.pop()
        }
    }

    private fun child(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.Home -> Child.Home(
                HomeComponent(
                    componentContext = componentContext,
                    onNavigateToDataViz = { chartId, sport, vizType ->
                        navigation.push(Config.DataViz(chartId, sport, vizType))
                    }
                )
            )
            is Config.DataViz -> Child.DataViz(
                DataVizComponent(
                    componentContext = componentContext,
                    chartId = config.chartId,
                    sport = config.sport,
                    vizType = config.vizType,
                    chartDataRepository = chartDataRepository,
                    registryContainer = registryContainer,
                    onNavigateBack = {
                        if (config.fromTopics) {
                            // Just pop back to Topics
                            navigation.pop()
                        } else {
                            navigateBackToHome()
                        }
                    },
                    initialFilters = config.initialFilters
                )
            )
            is Config.Settings -> Child.Settings(
                SettingsComponent(
                    componentContext = componentContext,
                    onNavigateBack = { navigation.pop() }
                )
            )
            is Config.Topics -> Child.Topics(
                TopicsComponent(
                    componentContext = componentContext,
                    onNavigateBack = { navigation.pop() },
                    onNavigateToChart = { chartId, sport, vizType, filters ->
                        navigateToChart(chartId, sport, vizType, filters, fromTopics = true)
                    }
                )
            )
        }

    sealed class Child {
        data class Home(val component: HomeComponent) : Child()
        data class DataViz(val component: DataVizComponent) : Child()
        data class Settings(val component: SettingsComponent) : Child()
        data class Topics(val component: TopicsComponent) : Child()
    }

    @Serializable
    sealed interface Config {
        @Serializable
        data object Home : Config

        @Serializable
        data class DataViz(
            val chartId: String,
            val sport: Sport,
            val vizType: VizType,
            val initialFilters: Map<String, String>? = null,
            val fromTopics: Boolean = false
        ) : Config

        @Serializable
        data object Settings : Config

        @Serializable
        data object Topics : Config
    }
}
