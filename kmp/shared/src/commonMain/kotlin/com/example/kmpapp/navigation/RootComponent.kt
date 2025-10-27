package com.example.kmpapp.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.example.kmpapp.ui.theme.ThemeMode
import com.example.kmpapp.ui.theme.ThemeRepository
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val themeRepository: ThemeRepository
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    private val _themeMode = MutableValue(themeRepository.getInitialTheme())
    val themeMode: Value<ThemeMode> = _themeMode

    fun toggleTheme(mode: ThemeMode) {
        _themeMode.value = mode
        themeRepository.saveTheme(mode)
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
                    onNavigateToDataViz = { title -> navigation.push(Config.DataViz(title)) }
                )
            )
            is Config.DataViz -> Child.DataViz(
                DataVizComponent(
                    componentContext = componentContext,
                    title = config.title,
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
        data class DataViz(val title: String) : Config
    }
}
