package com.joebad.fastbreak.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.joebad.fastbreak.data.api.MockedDataApi
import com.joebad.fastbreak.data.model.Sport
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
    val registryContainer: RegistryContainer
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
     * Triggers a registry refresh.
     * Delegates to RegistryContainer (Orbit MVI).
     */
    fun refreshRegistry() {
        registryContainer.refreshRegistry()
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
