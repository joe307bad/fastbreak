package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.joebad.fastbreak.data.api.MockRegistryApi
import com.joebad.fastbreak.data.api.MockedDataApi
import com.joebad.fastbreak.data.model.Registry
import com.joebad.fastbreak.data.model.RegistryMetadata
import com.joebad.fastbreak.data.model.Sport
import com.joebad.fastbreak.data.repository.ChartDataRepository
import com.joebad.fastbreak.data.repository.RegistryRepository
import com.joebad.fastbreak.ui.diagnostics.DiagnosticsInfo
import com.joebad.fastbreak.ui.theme.ThemeMode
import com.joebad.fastbreak.ui.theme.ThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.hours

class RootComponent(
    componentContext: ComponentContext,
    private val themeRepository: ThemeRepository,
    private val registryRepository: RegistryRepository,
    private val chartDataRepository: ChartDataRepository
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()
    private val mockRegistryApi = MockRegistryApi()

    // Coroutine scope for async operations (temporary until Orbit MVI in Phase 6)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _themeMode = MutableValue(themeRepository.getInitialTheme())
    val themeMode: Value<ThemeMode> = _themeMode

    // Registry state (temporary until Orbit MVI in Phase 6)
    private val _registry = MutableValue(Registry.empty())
    val registry: Value<Registry> = _registry

    // Diagnostics state (temporary until Orbit MVI in Phase 6)
    private val _diagnostics = MutableValue(DiagnosticsInfo())
    val diagnostics: Value<DiagnosticsInfo> = _diagnostics

    init {
        // Load cached registry on startup
        loadCachedRegistry()
    }

    fun toggleTheme(mode: ThemeMode) {
        _themeMode.value = mode
        themeRepository.saveTheme(mode)
    }

    /**
     * Loads the cached registry and updates diagnostics.
     * Called on app startup.
     */
    private fun loadCachedRegistry() {
        val cachedRegistry = registryRepository.getRegistry()
        _registry.value = cachedRegistry ?: Registry.empty()
        updateDiagnostics()
    }

    /**
     * Refreshes the registry from the mock API.
     * Saves to repository and updates diagnostics.
     * (Temporary implementation for Phase 2 testing - will be replaced in Phase 6 with Orbit MVI)
     */
    fun refreshRegistry() {
        // Set syncing state
        _diagnostics.value = _diagnostics.value.copy(isSyncing = true)

        scope.launch {
            mockRegistryApi.fetchRegistry()
                .onSuccess { registry ->
                    // Save to repository
                    registryRepository.saveRegistry(registry)
                    registryRepository.saveMetadata(
                        RegistryMetadata(
                            lastDownloadTime = Clock.System.now(),
                            registryVersion = registry.version
                        )
                    )

                    // Update state
                    _registry.value = registry
                    updateDiagnostics()
                }
                .onFailure { error ->
                    // Update diagnostics with error
                    _diagnostics.value = _diagnostics.value.copy(
                        isSyncing = false,
                        lastError = error.message,
                        failedSyncs = _diagnostics.value.failedSyncs + 1
                    )
                }
        }
    }

    /**
     * Updates the diagnostics info based on current repository state.
     */
    private fun updateDiagnostics() {
        val metadata = registryRepository.getMetadata()
        val now = Clock.System.now()
        val chartCount = chartDataRepository.getCachedChartCount()
        val cacheSize = chartDataRepository.estimateTotalCacheSize()

        _diagnostics.value = DiagnosticsInfo(
            lastRegistryFetch = metadata?.lastDownloadTime,
            lastCacheUpdate = null, // Will be implemented in Phase 5
            cachedChartsCount = chartCount,
            registryVersion = metadata?.registryVersion,
            totalCacheSize = cacheSize,
            failedSyncs = _diagnostics.value.failedSyncs,
            lastError = null,
            isStale = metadata?.let { now - it.lastDownloadTime > 12.hours } ?: false,
            isSyncing = false
        )
    }

    val stack: Value<ChildStack<*, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Home,
        handleBackButton = true,
        childFactory = ::child
    )

    private fun child(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            is Config.Home -> Child.Home(
                HomeComponent(
                    componentContext = componentContext,
                    onNavigateToDataViz = { sport, vizType ->
                        navigation.push(Config.DataViz(sport, vizType))
                    }
                )
            )
            is Config.DataViz -> Child.DataViz(
                DataVizComponent(
                    componentContext = componentContext,
                    sport = config.sport,
                    vizType = config.vizType,
                    onNavigateBack = { navigation.pop() }
                )
            )
        }

    sealed class Child {
        data class Home(val component: HomeComponent) : Child()
        data class DataViz(val component: DataVizComponent) : Child()
    }

    @Serializable
    sealed interface Config {
        @Serializable
        data object Home : Config

        @Serializable
        data class DataViz(val sport: Sport, val vizType: MockedDataApi.VizType) : Config
    }
}
